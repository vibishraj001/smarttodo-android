package com.example.domain.model

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import java.util.UUID

enum class TaskPriority(val displayName: String, val sortWeight: Int) {
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1);

    companion object {
        val ALLOWED_VALUES: Set<String> = entries.map { it.displayName }.toSet()

        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull {
                it.displayName.equals(raw, ignoreCase = true) ||
                    it.name.equals(raw, ignoreCase = true)
            } ?: MEDIUM
    }
}

enum class TaskCategory(val displayName: String) {
    PERSONAL("Personal"),
    WORK("Work"),
    STUDY("Study"),
    OTHER("Other");

    companion object {
        val ALLOWED_VALUES: Set<String> = entries.map { it.displayName }.toSet()

        fun fromRaw(raw: String?): TaskCategory =
            entries.firstOrNull {
                it.displayName.equals(raw, ignoreCase = true) ||
                    it.name.equals(raw, ignoreCase = true)
            } ?: PERSONAL
    }
}

enum class RecurrenceType(val displayName: String) {
    NONE("None"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
    CUSTOM("Custom");

    companion object {
        fun fromRaw(raw: String?): RecurrenceType =
            entries.firstOrNull {
                it.displayName.equals(raw, ignoreCase = true) ||
                    it.name.equals(raw, ignoreCase = true)
            } ?: NONE
    }
}

enum class RecurrenceUnit(val displayName: String, val singularName: String) {
    DAYS("Days", "day"),
    WEEKS("Weeks", "week"),
    MONTHS("Months", "month"),
    YEARS("Years", "year");

    companion object {
        fun fromRaw(raw: String?): RecurrenceUnit =
            entries.firstOrNull {
                it.displayName.equals(raw, ignoreCase = true) ||
                    it.name.equals(raw, ignoreCase = true)
            } ?: DAYS
    }
}

@Keep
@IgnoreExtraProperties
data class Subtask(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    @get:PropertyName("isCompleted")
    @PropertyName("isCompleted")
    val isCompleted: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id.ifBlank { UUID.randomUUID().toString() },
        "title" to title.trim().take(120),
        "isCompleted" to isCompleted
    )

    companion object {
        fun fromMap(raw: Any?): Subtask? {
            val map = raw as? Map<*, *> ?: return null
            val title = (map["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val id = (map["id"] as? String)?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString()
            val completed = (map["isCompleted"] as? Boolean) ?: false
            return Subtask(id = id, title = title, isCompleted = completed)
        }
    }
}

/**
 * Firestore-compatible Task domain model stored under `/users/{userId}/tasks/{taskId}`.
 *
 * Includes default values for every property so Kotlin generates a public no-argument constructor
 * required by Firestore deserialization (`DocumentSnapshot.toObject(Task::class.java)`).
 */
@Keep
@IgnoreExtraProperties
data class Task(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val dueDate: Long? = null,
    val dueTime: String? = null,
    val startDate: Long? = null,
    val startTime: String? = null,
    val deadline: Long? = null,
    val priority: String = TaskPriority.MEDIUM.displayName,
    val category: String = TaskCategory.PERSONAL.displayName,
    val listId: String? = null,
    @get:PropertyName("isCompleted")
    @PropertyName("isCompleted")
    val isCompleted: Boolean = false,
    @get:PropertyName("isStarred")
    @PropertyName("isStarred")
    val isStarred: Boolean = false,
    val recurrenceType: String = RecurrenceType.NONE.displayName,
    val recurrenceInterval: Int = 1,
    val recurrenceUnit: String = RecurrenceUnit.DAYS.displayName,
    val subtasks: List<Subtask> = emptyList(),
    val sortOrder: Int = 0,
    val calendarEventId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun priorityEnum(): TaskPriority = TaskPriority.fromRaw(priority)

    fun categoryEnum(): TaskCategory = TaskCategory.fromRaw(category)

    fun recurrenceTypeEnum(): RecurrenceType = RecurrenceType.fromRaw(recurrenceType)

    fun recurrenceUnitEnum(): RecurrenceUnit = RecurrenceUnit.fromRaw(recurrenceUnit)

    val isRecurring: Boolean
        get() = recurrenceTypeEnum() != RecurrenceType.NONE

    val completedSubtaskCount: Int
        get() = subtasks.count { it.isCompleted }

    /**
     * Builds a Firestore-rules-compliant creation map:
     * - Omits `@DocumentId` `id` and any `null` optional fields.
     * - Normalizes enum strings and bounds intervals/subtasks.
     * - Uses `FieldValue.serverTimestamp()` for `createdAt` and `updatedAt`.
     */
    fun toFirestoreCreateMap(ownerUid: String): Map<String, Any> {
        val cleanDescription = description?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }
        val cleanDueTime = dueTime?.trim()?.takeIf { it.isNotEmpty() }
        val cleanStartTime = startTime?.trim()?.takeIf { it.isNotEmpty() }
        val cleanListId = listId?.trim()?.take(128)?.takeIf { it.isNotEmpty() }
        val cleanCalendarId = calendarEventId?.trim()?.take(256)?.takeIf { it.isNotEmpty() }
        val normalizedPriority = priorityEnum().displayName
        val normalizedCategory = categoryEnum().displayName
        val normalizedRecurrence = recurrenceTypeEnum().displayName
        val normalizedUnit = recurrenceUnitEnum().displayName
        val boundedInterval = recurrenceInterval.coerceIn(1, 365)
        val boundedOrder = sortOrder.coerceIn(0, 1_000_000)
        val cleanSubtasks = subtasks
            .filter { it.title.isNotBlank() }
            .take(50)
            .map { it.toFirestoreMap() }

        val rawMap = linkedMapOf<String, Any?>(
            "userId" to ownerUid,
            "title" to title.trim().take(120),
            "description" to cleanDescription,
            "dueDate" to dueDate,
            "dueTime" to cleanDueTime,
            "startDate" to startDate,
            "startTime" to cleanStartTime,
            "deadline" to deadline,
            "priority" to normalizedPriority,
            "category" to normalizedCategory,
            "listId" to cleanListId,
            "isCompleted" to isCompleted,
            "isStarred" to isStarred,
            "recurrenceType" to normalizedRecurrence,
            "recurrenceInterval" to boundedInterval,
            "recurrenceUnit" to normalizedUnit,
            "subtasks" to cleanSubtasks,
            "sortOrder" to boundedOrder,
            "calendarEventId" to cleanCalendarId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return rawMap.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
    }

    companion object {
        /**
         * Safely extracts a [Task] from a Firestore [DocumentSnapshot], handling both
         * Firestore `Timestamp` fields (with `ServerTimestampBehavior.ESTIMATE`) and numeric
         * epoch milliseconds without reflection or type-cast failures.
         */
        fun fromDocument(doc: DocumentSnapshot): Task? {
            if (!doc.exists()) return null
            val title = doc.getString("title") ?: return null
            val rawCreated = doc.getTimestamp(
                "createdAt",
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.toDate()?.time ?: (doc.get("createdAt") as? Number)?.toLong() ?: 0L

            val rawUpdated = doc.getTimestamp(
                "updatedAt",
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.toDate()?.time ?: (doc.get("updatedAt") as? Number)?.toLong() ?: rawCreated

            val rawSubtasks = (doc.get("subtasks") as? List<*>)
                ?.mapNotNull { Subtask.fromMap(it) }
                ?: emptyList()

            return Task(
                id = doc.id,
                title = title,
                description = doc.getString("description")?.takeIf { it.isNotBlank() },
                dueDate = (doc.get("dueDate") as? Number)?.toLong(),
                dueTime = doc.getString("dueTime")?.takeIf { it.isNotBlank() },
                startDate = (doc.get("startDate") as? Number)?.toLong(),
                startTime = doc.getString("startTime")?.takeIf { it.isNotBlank() },
                deadline = (doc.get("deadline") as? Number)?.toLong(),
                priority = TaskPriority.fromRaw(doc.getString("priority")).displayName,
                category = TaskCategory.fromRaw(doc.getString("category")).displayName,
                listId = doc.getString("listId")?.takeIf { it.isNotBlank() },
                isCompleted = doc.getBoolean("isCompleted") ?: false,
                isStarred = doc.getBoolean("isStarred") ?: false,
                recurrenceType = RecurrenceType.fromRaw(doc.getString("recurrenceType")).displayName,
                recurrenceInterval = ((doc.get("recurrenceInterval") as? Number)?.toInt() ?: 1).coerceIn(1, 365),
                recurrenceUnit = RecurrenceUnit.fromRaw(doc.getString("recurrenceUnit")).displayName,
                subtasks = rawSubtasks,
                sortOrder = ((doc.get("sortOrder") as? Number)?.toInt() ?: 0).coerceIn(0, 1_000_000),
                calendarEventId = doc.getString("calendarEventId")?.takeIf { it.isNotBlank() },
                createdAt = rawCreated,
                updatedAt = rawUpdated
            )
        }
    }
}
