package com.example.widget

import android.content.Context
import com.example.domain.TaskFilterSorter
import com.example.domain.model.Task
import com.example.model.TaskFilter
import com.example.model.TaskSortOption
import org.json.JSONArray
import org.json.JSONObject

data class WidgetTaskSnapshot(
    val isSignedIn: Boolean = false,
    val userId: String = "",
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val pendingCount: Int = 0,
    val overdueCount: Int = 0,
    val pendingTasks: List<Task> = emptyList()
) {
    val completionPercent: Int
        get() = if (totalCount <= 0) 0 else ((completedCount * 100f) / totalCount).toInt().coerceIn(0, 100)
}

object SmartTodoWidgetStore {
    private const val PREFS_NAME = "smarttodo_widget_cache"
    private const val KEY_IS_SIGNED_IN = "is_signed_in"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_TOTAL_COUNT = "total_count"
    private const val KEY_COMPLETED_COUNT = "completed_count"
    private const val KEY_PENDING_COUNT = "pending_count"
    private const val KEY_OVERDUE_COUNT = "overdue_count"
    private const val KEY_TASKS_JSON = "pending_tasks_json"
    private const val MAX_WIDGET_TASKS = 25

    fun saveTasks(
        context: Context,
        userId: String,
        tasks: List<Task>,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (userId.isBlank()) {
            clear(context)
            return
        }
        val stats = TaskFilterSorter.computeDashboardStats(tasks, nowMillis)
        val pendingSorted = TaskFilterSorter.filterAndSort(
            tasks = tasks,
            filter = TaskFilter.PENDING,
            sortOption = TaskSortOption.DUE_DATE,
            searchQuery = "",
            nowMillis = nowMillis
        ).take(MAX_WIDGET_TASKS)

        val jsonArray = JSONArray()
        pendingSorted.forEach { task ->
            jsonArray.put(taskToJson(task))
        }

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_SIGNED_IN, true)
            .putString(KEY_USER_ID, userId)
            .putInt(KEY_TOTAL_COUNT, stats.totalTasks)
            .putInt(KEY_COMPLETED_COUNT, stats.completedTasks)
            .putInt(KEY_PENDING_COUNT, stats.pendingTasks)
            .putInt(KEY_OVERDUE_COUNT, stats.overdueTasks)
            .putString(KEY_TASKS_JSON, jsonArray.toString())
            .apply()
    }

    fun markTaskCompletedOptimistically(context: Context, taskId: String) {
        val current = readSnapshot(context)
        if (!current.isSignedIn || taskId.isBlank()) return
        val remaining = current.pendingTasks.filterNot { it.id == taskId }
        if (remaining.size == current.pendingTasks.size) return

        val jsonArray = JSONArray()
        remaining.forEach { jsonArray.put(taskToJson(it)) }

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_COMPLETED_COUNT, (current.completedCount + 1).coerceAtMost(current.totalCount))
            .putInt(KEY_PENDING_COUNT, (current.pendingCount - 1).coerceAtLeast(0))
            .putString(KEY_TASKS_JSON, jsonArray.toString())
            .apply()
    }

    fun readSnapshot(context: Context): WidgetTaskSnapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isSignedIn = prefs.getBoolean(KEY_IS_SIGNED_IN, false)
        val userId = prefs.getString(KEY_USER_ID, "") ?: ""
        if (!isSignedIn || userId.isBlank()) {
            return WidgetTaskSnapshot(isSignedIn = false)
        }

        val tasksJson = prefs.getString(KEY_TASKS_JSON, "[]") ?: "[]"
        val parsedTasks = mutableListOf<Task>()
        try {
            val array = JSONArray(tasksJson)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                val title = obj.optString("title", "")
                if (id.isNotBlank() && title.isNotBlank()) {
                    parsedTasks.add(
                        Task(
                            id = id,
                            title = title,
                            description = obj.optString("description").takeIf { it.isNotBlank() },
                            dueDate = if (obj.has("dueDate") && !obj.isNull("dueDate")) {
                                obj.optLong("dueDate")
                            } else {
                                null
                            },
                            dueTime = obj.optString("dueTime").takeIf { it.isNotBlank() },
                            priority = obj.optString("priority", "Medium"),
                            category = obj.optString("category", "Personal"),
                            isCompleted = false,
                            createdAt = obj.optLong("createdAt", 0L),
                            updatedAt = obj.optLong("updatedAt", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Return empty task list if cached JSON is malformed
        }

        return WidgetTaskSnapshot(
            isSignedIn = true,
            userId = userId,
            totalCount = prefs.getInt(KEY_TOTAL_COUNT, parsedTasks.size),
            completedCount = prefs.getInt(KEY_COMPLETED_COUNT, 0),
            pendingCount = prefs.getInt(KEY_PENDING_COUNT, parsedTasks.size),
            overdueCount = prefs.getInt(KEY_OVERDUE_COUNT, 0),
            pendingTasks = parsedTasks
        )
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun taskToJson(task: Task): JSONObject = JSONObject().apply {
        put("id", task.id)
        put("title", task.title)
        if (!task.description.isNullOrBlank()) put("description", task.description)
        if (task.dueDate != null) put("dueDate", task.dueDate)
        if (!task.dueTime.isNullOrBlank()) put("dueTime", task.dueTime)
        put("priority", task.priority)
        put("category", task.category)
        put("createdAt", task.createdAt)
        put("updatedAt", task.updatedAt)
    }
}
