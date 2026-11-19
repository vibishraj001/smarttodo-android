package com.example.domain.model

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

enum class TaskHistoryAction(val displayName: String) {
    CREATED("Created"),
    UPDATED("Updated"),
    COMPLETED("Completed"),
    REOPENED("Reopened"),
    DELETED("Deleted"),
    RECURRENCE_ADVANCED("Recurrence Advanced"),
    SYNCED_TO_CALENDAR("Synced to Calendar"),
    IMPORTED_FROM_GMAIL("Imported from Gmail"),
    IMPORTED_FROM_CALENDAR("Imported from Calendar"),
    SHARED_TO_CHAT("Shared to Chat");

    companion object {
        fun fromRaw(raw: String?): TaskHistoryAction =
            entries.firstOrNull {
                it.displayName.equals(raw, ignoreCase = true) ||
                    it.name.equals(raw, ignoreCase = true)
            } ?: UPDATED
    }
}

@Keep
@IgnoreExtraProperties
data class TaskHistoryEntry(
    @DocumentId
    val id: String = "",
    val taskId: String = "",
    val taskTitle: String = "",
    val action: String = TaskHistoryAction.CREATED.displayName,
    val details: String? = null,
    val timestamp: Long = 0L
) {
    fun actionEnum(): TaskHistoryAction = TaskHistoryAction.fromRaw(action)

    fun toFirestoreCreateMap(ownerUid: String): Map<String, Any> {
        val cleanDetails = details?.trim()?.take(300)?.takeIf { it.isNotEmpty() }
        val map = linkedMapOf<String, Any?>(
            "userId" to ownerUid,
            "taskId" to taskId.trim().take(128).ifBlank { "unknown" },
            "taskTitle" to taskTitle.trim().take(120).ifBlank { "Untitled task" },
            "action" to actionEnum().displayName,
            "details" to cleanDetails,
            "timestamp" to FieldValue.serverTimestamp()
        )
        return map.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
    }

    companion object {
        fun fromDocument(doc: DocumentSnapshot): TaskHistoryEntry? {
            if (!doc.exists()) return null
            val title = doc.getString("taskTitle") ?: return null
            val rawTimestamp = doc.getTimestamp(
                "timestamp",
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.toDate()?.time ?: (doc.get("timestamp") as? Number)?.toLong() ?: 0L

            return TaskHistoryEntry(
                id = doc.id,
                taskId = doc.getString("taskId") ?: "",
                taskTitle = title,
                action = TaskHistoryAction.fromRaw(doc.getString("action")).displayName,
                details = doc.getString("details")?.takeIf { it.isNotBlank() },
                timestamp = rawTimestamp
            )
        }
    }
}
