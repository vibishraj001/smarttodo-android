package com.example.workspace

import com.example.domain.RecurrenceCalculator
import com.example.domain.model.Task
import com.example.utils.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WorkspaceCalendarEvent(
    val id: String,
    val summary: String,
    val description: String?,
    val startDateUtcMillis: Long?,
    val startTime: String?,
    val displayTimeRange: String,
    val htmlLink: String?
)

data class WorkspaceEmailItem(
    val id: String,
    val subject: String,
    val from: String,
    val date: String,
    val snippet: String
)

data class WorkspaceChatSpace(
    val name: String,
    val displayName: String,
    val type: String
)

data class CalendarSyncResult(
    val eventId: String,
    val htmlLink: String?
)

interface WorkspaceApiClient {
    suspend fun syncTaskToCalendar(accessToken: String, task: Task): Result<CalendarSyncResult>
    suspend fun fetchUpcomingCalendarEvents(accessToken: String): Result<List<WorkspaceCalendarEvent>>
    suspend fun fetchRecentGmailMessages(accessToken: String): Result<List<WorkspaceEmailItem>>
    suspend fun fetchChatSpaces(accessToken: String): Result<List<WorkspaceChatSpace>>
    suspend fun postTaskToChatSpace(accessToken: String, spaceName: String, task: Task): Result<String>
}

/**
 * Direct REST client for Google Calendar v3, Gmail v1, and Google Chat v1 APIs using OkHttp.
 */
class GoogleWorkspaceClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : WorkspaceApiClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun syncTaskToCalendar(
        accessToken: String,
        task: Task
    ): Result<CalendarSyncResult> = withContext(Dispatchers.IO) {
        try {
            val payload = buildCalendarEventJson(task).toString()
            val existingId = task.calendarEventId?.takeIf { it.isNotBlank() }
            val url = if (existingId != null) {
                "$CALENDAR_EVENTS_URL/${URLEncoder.encode(existingId, "UTF-8")}"
            } else {
                CALENDAR_EVENTS_URL
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")

            val body = payload.toRequestBody(jsonMediaType)
            val request = if (existingId != null) {
                requestBuilder.patch(body).build()
            } else {
                requestBuilder.post(body).build()
            }

            val responseJson = executeJsonRequest(request)
            val eventId = responseJson.optString("id").ifBlank { existingId ?: "" }
            val link = responseJson.optString("htmlLink").takeIf { it.isNotBlank() }
            Result.success(CalendarSyncResult(eventId = eventId, htmlLink = link))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchUpcomingCalendarEvents(
        accessToken: String
    ): Result<List<WorkspaceCalendarEvent>> = withContext(Dispatchers.IO) {
        try {
            val timeMin = URLEncoder.encode(
                DateTimeUtils.formatRfc3339Utc(System.currentTimeMillis()),
                "UTF-8"
            )
            val url = "$CALENDAR_EVENTS_URL?timeMin=$timeMin&singleEvents=true&orderBy=startTime&maxResults=15"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .get()
                .build()

            val json = executeJsonRequest(request)
            val items = json.optJSONArray("items") ?: JSONArray()
            val events = mutableListOf<WorkspaceCalendarEvent>()
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val summary = obj.optString("summary").ifBlank { "(No title)" }
                val desc = obj.optString("description").takeIf { it.isNotBlank() }
                val startObj = obj.optJSONObject("start")
                val dateStr = startObj?.optString("date")?.takeIf { it.isNotBlank() }
                val dateTimeStr = startObj?.optString("dateTime")?.takeIf { it.isNotBlank() }

                val startDateMillis = DateTimeUtils.parseIsoDateToUtcMillis(dateStr ?: dateTimeStr)
                val startTime = extractTimeFromDateTime(dateTimeStr)
                val display = when {
                    startDateMillis != null && startTime != null ->
                        "${DateTimeUtils.formatDisplayDate(startDateMillis)} • ${DateTimeUtils.formatDisplayTime(startTime) ?: startTime}"
                    startDateMillis != null ->
                        "${DateTimeUtils.formatDisplayDate(startDateMillis)} • All day"
                    else -> "Scheduled"
                }

                events.add(
                    WorkspaceCalendarEvent(
                        id = id,
                        summary = summary,
                        description = desc,
                        startDateUtcMillis = startDateMillis,
                        startTime = startTime,
                        displayTimeRange = display,
                        htmlLink = obj.optString("htmlLink").takeIf { it.isNotBlank() }
                    )
                )
            }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchRecentGmailMessages(
        accessToken: String
    ): Result<List<WorkspaceEmailItem>> = withContext(Dispatchers.IO) {
        try {
            val listReq = Request.Builder()
                .url("$GMAIL_BASE_URL/messages?maxResults=10&q=in:inbox")
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .get()
                .build()
            val listJson = executeJsonRequest(listReq)
            val messages = listJson.optJSONArray("messages") ?: JSONArray()
            val results = mutableListOf<WorkspaceEmailItem>()

            val count = minOf(messages.length(), 10)
            for (i in 0 until count) {
                val msgId = messages.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
                    ?: continue
                val detailUrl = "$GMAIL_BASE_URL/messages/$msgId?format=metadata" +
                    "&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=Date"
                val detailReq = Request.Builder()
                    .url(detailUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Accept", "application/json")
                    .get()
                    .build()
                val detailJson = executeJsonRequest(detailReq)
                val headers = detailJson.optJSONObject("payload")?.optJSONArray("headers") ?: JSONArray()
                var subject = "(No subject)"
                var from = ""
                var date = ""
                for (h in 0 until headers.length()) {
                    val header = headers.optJSONObject(h) ?: continue
                    when (header.optString("name")) {
                        "Subject" -> subject = header.optString("value").ifBlank { "(No subject)" }
                        "From" -> from = header.optString("value")
                        "Date" -> date = header.optString("value")
                    }
                }
                results.add(
                    WorkspaceEmailItem(
                        id = msgId,
                        subject = subject,
                        from = from.substringBefore("<").trim().ifBlank { from },
                        date = date.substringBefore("+").substringBefore("-").trim(),
                        snippet = detailJson.optString("snippet")
                    )
                )
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchChatSpaces(
        accessToken: String
    ): Result<List<WorkspaceChatSpace>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$CHAT_BASE_URL/spaces?pageSize=20")
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .get()
                .build()
            val json = executeJsonRequest(req)
            val spacesArr = json.optJSONArray("spaces") ?: JSONArray()
            val spaces = mutableListOf<WorkspaceChatSpace>()
            for (i in 0 until spacesArr.length()) {
                val obj = spacesArr.optJSONObject(i) ?: continue
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                val displayName = obj.optString("displayName")
                    .ifBlank { name.removePrefix("spaces/") }
                val type = obj.optString("spaceType").ifBlank { obj.optString("type", "SPACE") }
                spaces.add(WorkspaceChatSpace(name = name, displayName = displayName, type = type))
            }
            Result.success(spaces)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun postTaskToChatSpace(
        accessToken: String,
        spaceName: String,
        task: Task
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sanitizedSpace = spaceName.trim().removePrefix("/")
            val textBuilder = StringBuilder().apply {
                append("*SmartTodo Task*: ${task.title}")
                if (task.isStarred) append(" ⭐")
                append("\nPriority: ${task.priority} • Category: ${task.category}")
                task.dueDate?.let { due ->
                    append("\nDue: ${DateTimeUtils.formatDisplayDate(due)}")
                    DateTimeUtils.formatDisplayTime(task.dueTime)?.let { append(" at $it") }
                }
                task.deadline?.let { dl ->
                    append("\nDeadline: ${DateTimeUtils.formatDisplayDate(dl)}")
                }
                if (!task.description.isNullOrBlank()) {
                    append("\n\n${task.description}")
                }
                if (task.subtasks.isNotEmpty()) {
                    append("\n\nSubtasks (${task.completedSubtaskCount}/${task.subtasks.size}):")
                    task.subtasks.take(10).forEach { sub ->
                        val mark = if (sub.isCompleted) "[x]" else "[ ]"
                        append("\n$mark ${sub.title}")
                    }
                }
            }
            val bodyJson = JSONObject().put("text", textBuilder.toString())
            val req = Request.Builder()
                .url("$CHAT_BASE_URL/$sanitizedSpace/messages")
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()
            val resp = executeJsonRequest(req)
            Result.success(resp.optString("name", "sent"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildCalendarEventJson(task: Task): JSONObject {
        val descParts = mutableListOf<String>()
        if (!task.description.isNullOrBlank()) descParts.add(task.description)
        descParts.add("Priority: ${task.priority} | Category: ${task.category}")
        RecurrenceCalculator.formatRecurrenceLabel(
            task.recurrenceTypeEnum(),
            task.recurrenceInterval,
            task.recurrenceUnitEnum()
        )?.let { descParts.add("Recurrence: $it") }
        if (task.subtasks.isNotEmpty()) {
            descParts.add(
                "Subtasks:\n" + task.subtasks.joinToString("\n") {
                    "${if (it.isCompleted) "[x]" else "[ ]"} ${it.title}"
                }
            )
        }

        val targetDate = task.startDate ?: task.dueDate ?: DateTimeUtils.todayUtcMillis()
        val targetTime = task.startTime ?: task.dueTime

        val event = JSONObject()
            .put("summary", task.title)
            .put("description", descParts.joinToString("\n\n"))

        if (!targetTime.isNullOrBlank()) {
            val startEpoch = DateTimeUtils.computeDueDateTimeMillis(targetDate, targetTime)
                ?: System.currentTimeMillis()
            val endEpoch = startEpoch + TimeUnit.HOURS.toMillis(1)
            event.put("start", JSONObject().put("dateTime", DateTimeUtils.formatRfc3339Local(startEpoch)))
            event.put("end", JSONObject().put("dateTime", DateTimeUtils.formatRfc3339Local(endEpoch)))
        } else {
            val startDateStr = DateTimeUtils.formatIsoDateUtc(targetDate)
            val endDateStr = DateTimeUtils.formatIsoDateUtc(targetDate + TimeUnit.DAYS.toMillis(1))
            event.put("start", JSONObject().put("date", startDateStr))
            event.put("end", JSONObject().put("date", endDateStr))
        }
        return event
    }

    private fun extractTimeFromDateTime(dateTimeStr: String?): String? {
        if (dateTimeStr.isNullOrBlank() || !dateTimeStr.contains("T")) return null
        val afterT = dateTimeStr.substringAfter("T")
        val hhmm = afterT.take(5)
        return if (DateTimeUtils.isValidIsoTime(hhmm)) hhmm else null
    }

    private fun executeJsonRequest(request: Request): JSONObject {
        httpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val apiMessage = try {
                    JSONObject(rawBody).optJSONObject("error")?.optString("message")
                } catch (_: Exception) {
                    null
                }
                throw IOException(
                    apiMessage?.takeIf { it.isNotBlank() }
                        ?: "Google API request failed (HTTP ${response.code})."
                )
            }
            return if (rawBody.isBlank()) JSONObject() else JSONObject(rawBody)
        }
    }

    private companion object {
        const val CALENDAR_EVENTS_URL =
            "https://www.googleapis.com/calendar/v3/calendars/primary/events"
        const val GMAIL_BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me"
        const val CHAT_BASE_URL = "https://chat.googleapis.com/v1"
    }
}
