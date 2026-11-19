package com.example.utils

import com.example.domain.model.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeUtils {

    private val isoTimeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    fun todayUtcMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val localCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return utcDateMillis(
            year = localCal.get(Calendar.YEAR),
            monthZeroBased = localCal.get(Calendar.MONTH),
            dayOfMonth = localCal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun utcDateMillis(year: Int, monthZeroBased: Int, dayOfMonth: Int): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, monthZeroBased, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return utcCal.timeInMillis
    }

    fun normalizeToUtcStartOfDay(utcMillis: Long): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return utcCal.timeInMillis
    }

    fun formatFullDateHeader(nowMillis: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        return formatter.format(Date(nowMillis))
    }

    fun formatHistoryTimestamp(epochMillis: Long): String {
        if (epochMillis <= 0L) return "Just now"
        val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return formatter.format(Date(epochMillis))
    }

    fun formatIsoDateUtc(utcMillis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date(normalizeToUtcStartOfDay(utcMillis)))
    }

    fun parseIsoDateToUtcMillis(isoDate: String?): Long? {
        if (isoDate.isNullOrBlank()) return null
        val datePart = isoDate.take(10)
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(datePart) ?: return null
            normalizeToUtcStartOfDay(parsed.time)
        } catch (_: Exception) {
            null
        }
    }

    fun formatRfc3339Local(epochMillis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        return formatter.format(Date(epochMillis))
    }

    fun formatRfc3339Utc(epochMillis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date(epochMillis))
    }

    fun getGreetingType(nowMillis: Long = System.currentTimeMillis()): GreetingPeriod {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (cal.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> GreetingPeriod.MORNING
            in 12..16 -> GreetingPeriod.AFTERNOON
            else -> GreetingPeriod.EVENING
        }
    }

    fun isValidIsoTime(value: String): Boolean = isoTimeRegex.matches(value)

    fun formatTime(hour: Int, minute: Int): String =
        String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    fun parseHourMinute(isoTime: String?): Pair<Int, Int>? {
        if (isoTime.isNullOrBlank() || !isValidIsoTime(isoTime)) return null
        val parts = isoTime.split(":")
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h to m
    }

    fun formatDisplayTime(isoTime: String?): String? {
        val (hour, minute) = parseHourMinute(isoTime) ?: return null
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
    }

    fun formatDisplayDate(
        dueDateMillis: Long?,
        nowMillis: Long = System.currentTimeMillis(),
        emptyLabel: String = "No due date"
    ): String {
        if (dueDateMillis == null) return emptyLabel
        val normalizedDue = normalizeToUtcStartOfDay(dueDateMillis)
        val today = todayUtcMillis(nowMillis)
        val oneDayMs = 86_400_000L

        return when (normalizedDue) {
            today -> "Today"
            today + oneDayMs -> "Tomorrow"
            today - oneDayMs -> "Yesterday"
            else -> {
                val todayCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = today
                }
                val targetCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = normalizedDue
                }
                val sameYear = todayCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)
                val pattern = if (sameYear) "MMM d" else "MMM d, yyyy"
                SimpleDateFormat(pattern, Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(normalizedDue))
            }
        }
    }

    fun isTaskToday(
        task: Task,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val due = task.dueDate ?: return false
        return normalizeToUtcStartOfDay(due) == todayUtcMillis(nowMillis)
    }

    fun isTaskOverdue(
        task: Task,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (task.isCompleted) return false
        val due = task.dueDate ?: task.deadline ?: return false
        val normalizedDue = normalizeToUtcStartOfDay(due)
        val today = todayUtcMillis(nowMillis)
        if (normalizedDue < today) return true
        if (normalizedDue > today) return false

        val dueMillis = computeDueDateTimeMillis(normalizedDue, task.dueTime) ?: return false
        return dueMillis < nowMillis
    }

    fun isTaskUpcoming(
        task: Task,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (task.isCompleted) return false
        val due = task.dueDate ?: return false
        return normalizeToUtcStartOfDay(due) > todayUtcMillis(nowMillis)
    }

    fun computeDueDateTimeMillis(dueDateMillis: Long?, isoTime: String?): Long? {
        if (dueDateMillis == null) return null
        val (hour, minute) = parseHourMinute(isoTime) ?: return null
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = dueDateMillis
        }
        val localCal = Calendar.getInstance().apply {
            clear()
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                hour,
                minute,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }
        return localCal.timeInMillis
    }
}

enum class GreetingPeriod {
    MORNING,
    AFTERNOON,
    EVENING
}
