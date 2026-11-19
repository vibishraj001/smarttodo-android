package com.example.model

import androidx.annotation.Keep
import com.example.domain.model.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TaskFilter(val label: String) {
    ALL("All"),
    STARRED("Starred"),
    TODAY("Today"),
    UPCOMING("Upcoming"),
    PENDING("Pending"),
    OVERDUE("Overdue"),
    COMPLETED("Completed")
}

enum class TaskSortOption(val label: String) {
    DUE_DATE("Due date"),
    MANUAL_ORDER("Custom order"),
    PRIORITY("Priority"),
    RECENTLY_CREATED("Recently created"),
    ALPHABETICAL("Alphabetical")
}

enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System");

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class ReminderLeadTime(val minutesBefore: Int, val label: String) {
    AT_TIME(0, "At due time"),
    TEN_MINUTES(10, "10 min before"),
    FIFTEEN_MINUTES(15, "15 min before"),
    THIRTY_MINUTES(30, "30 min before"),
    ONE_HOUR(60, "1 hour before");

    companion object {
        fun fromMinutes(minutes: Int?): ReminderLeadTime =
            entries.firstOrNull { it.minutesBefore == minutes } ?: FIFTEEN_MINUTES
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DashboardStats(
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val pendingTasks: Int = 0,
    val overdueTasks: Int = 0,
    val starredTasks: Int = 0,
    val completionProgress: Float = 0f,
    val todayTasks: List<Task> = emptyList()
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
