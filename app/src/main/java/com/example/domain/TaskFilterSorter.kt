package com.example.domain

import com.example.domain.model.Task
import com.example.model.DashboardStats
import com.example.model.TaskFilter
import com.example.model.TaskSortOption
import com.example.utils.DateTimeUtils

object TaskFilterSorter {

    fun filterAndSort(
        tasks: List<Task>,
        filter: TaskFilter,
        sortOption: TaskSortOption,
        searchQuery: String,
        nowMillis: Long = System.currentTimeMillis(),
        selectedListId: String? = null
    ): List<Task> {
        val trimmedQuery = searchQuery.trim()

        val filtered = tasks.filter { task ->
            val matchesList = selectedListId == null || task.listId == selectedListId
            val matchesFilter = when (filter) {
                TaskFilter.ALL -> true
                TaskFilter.STARRED -> task.isStarred
                TaskFilter.TODAY -> DateTimeUtils.isTaskToday(task, nowMillis)
                TaskFilter.UPCOMING -> DateTimeUtils.isTaskUpcoming(task, nowMillis)
                TaskFilter.COMPLETED -> task.isCompleted
                TaskFilter.PENDING -> !task.isCompleted
                TaskFilter.OVERDUE -> DateTimeUtils.isTaskOverdue(task, nowMillis)
            }
            val matchesSearch = if (trimmedQuery.isEmpty()) {
                true
            } else {
                task.title.contains(trimmedQuery, ignoreCase = true) ||
                    (task.description?.contains(trimmedQuery, ignoreCase = true) == true) ||
                    task.subtasks.any { it.title.contains(trimmedQuery, ignoreCase = true) }
            }
            matchesList && matchesFilter && matchesSearch
        }

        return sortTasks(filtered, sortOption)
    }

    fun sortTasks(
        tasks: List<Task>,
        sortOption: TaskSortOption
    ): List<Task> {
        return when (sortOption) {
            TaskSortOption.MANUAL_ORDER -> tasks.sortedWith(
                compareBy<Task> { it.isCompleted }
                    .thenBy { it.sortOrder }
                    .thenByDescending { it.createdAt }
            )
            TaskSortOption.DUE_DATE -> tasks.sortedWith(
                compareBy<Task> { it.isCompleted }
                    .thenBy { it.dueDate ?: Long.MAX_VALUE }
                    .thenBy { it.dueTime ?: "23:59" }
                    .thenByDescending { it.priorityEnum().sortWeight }
            )
            TaskSortOption.PRIORITY -> tasks.sortedWith(
                compareBy<Task> { it.isCompleted }
                    .thenByDescending { it.priorityEnum().sortWeight }
                    .thenBy { it.dueDate ?: Long.MAX_VALUE }
                    .thenBy { it.dueTime ?: "23:59" }
            )
            TaskSortOption.RECENTLY_CREATED -> tasks.sortedWith(
                compareByDescending<Task> { it.createdAt }
                    .thenBy { it.title.lowercase() }
            )
            TaskSortOption.ALPHABETICAL -> tasks.sortedWith(
                compareBy<Task> { it.title.lowercase() }
                    .thenBy { it.dueDate ?: Long.MAX_VALUE }
            )
        }
    }

    fun computeDashboardStats(
        tasks: List<Task>,
        nowMillis: Long = System.currentTimeMillis()
    ): DashboardStats {
        val total = tasks.size
        val completed = tasks.count { it.isCompleted }
        val pending = tasks.count { !it.isCompleted }
        val overdue = tasks.count { DateTimeUtils.isTaskOverdue(it, nowMillis) }
        val starred = tasks.count { it.isStarred && !it.isCompleted }
        val progress = if (total == 0) 0f else completed.toFloat() / total.toFloat()

        val todayTasks = sortTasks(
            tasks = tasks.filter { DateTimeUtils.isTaskToday(it, nowMillis) },
            sortOption = TaskSortOption.DUE_DATE
        )

        return DashboardStats(
            totalTasks = total,
            completedTasks = completed,
            pendingTasks = pending,
            overdueTasks = overdue,
            starredTasks = starred,
            completionProgress = progress,
            todayTasks = todayTasks
        )
    }
}
