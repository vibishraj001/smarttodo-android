package com.example.domain.model

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class TaskList(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val colorHex: String = DEFAULT_COLOR_HEX,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun toFirestoreCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "name" to name.trim().take(MAX_NAME_LENGTH),
        "colorHex" to normalizeColorHex(colorHex),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        const val MAX_NAME_LENGTH = 60
        const val DEFAULT_COLOR_HEX = "#2563EB"
        private val HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

        val PRESET_COLORS: List<String> = listOf(
            "#2563EB", // Cobalt Blue
            "#7C3AED", // Electric Violet
            "#0D9488", // Teal
            "#16A34A", // Emerald
            "#D97706", // Amber
            "#DC2626", // Crimson
            "#DB2777", // Rose
            "#475569"  // Slate
        )

        fun normalizeColorHex(hex: String?): String =
            if (hex != null && HEX_REGEX.matches(hex)) hex.uppercase() else DEFAULT_COLOR_HEX

        fun fromDocument(doc: DocumentSnapshot): TaskList? {
            if (!doc.exists()) return null
            val name = doc.getString("name")?.takeIf { it.isNotBlank() } ?: return null
            val created = doc.getTimestamp(
                "createdAt",
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.toDate()?.time ?: (doc.get("createdAt") as? Number)?.toLong() ?: 0L
            val updated = doc.getTimestamp(
                "updatedAt",
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.toDate()?.time ?: (doc.get("updatedAt") as? Number)?.toLong() ?: created

            return TaskList(
                id = doc.id,
                name = name,
                colorHex = normalizeColorHex(doc.getString("colorHex")),
                createdAt = created,
                updatedAt = updated
            )
        }
    }
}
