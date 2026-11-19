package com.example.domain

import com.example.domain.model.Subtask
import com.example.domain.model.TaskList
import com.example.utils.DateTimeUtils

data class TaskValidationResult(
    val titleError: String? = null,
    val descriptionError: String? = null,
    val dueDateError: String? = null,
    val dueTimeError: String? = null,
    val startTimeError: String? = null,
    val deadlineError: String? = null,
    val recurrenceError: String? = null,
    val subtaskError: String? = null
) {
    val isValid: Boolean
        get() = titleError == null &&
            descriptionError == null &&
            dueDateError == null &&
            dueTimeError == null &&
            startTimeError == null &&
            deadlineError == null &&
            recurrenceError == null &&
            subtaskError == null

    fun firstErrorMessage(): String? =
        titleError ?: descriptionError ?: dueDateError ?: dueTimeError
            ?: startTimeError ?: deadlineError ?: recurrenceError ?: subtaskError
}

object TaskValidator {
    const val MAX_TITLE_LENGTH = 120
    const val MAX_DESCRIPTION_LENGTH = 1000
    const val MAX_SUBTASKS = 50

    fun validate(
        title: String,
        description: String?,
        dueDate: Long?,
        dueTime: String?,
        startDate: Long? = null,
        startTime: String? = null,
        deadline: Long? = null,
        recurrenceInterval: Int = 1,
        subtasks: List<Subtask> = emptyList()
    ): TaskValidationResult {
        val trimmedTitle = title.trim()
        val titleError = when {
            trimmedTitle.isEmpty() -> "Task title is required."
            trimmedTitle.length > MAX_TITLE_LENGTH ->
                "Title must be $MAX_TITLE_LENGTH characters or fewer."
            else -> null
        }

        val descriptionError = when {
            (description?.trim()?.length ?: 0) > MAX_DESCRIPTION_LENGTH ->
                "Description must be $MAX_DESCRIPTION_LENGTH characters or fewer."
            else -> null
        }

        val dueDateError = when {
            dueDate != null && dueDate < 0L -> "Please select a valid due date."
            else -> null
        }

        val normalizedTime = dueTime?.trim()?.takeIf { it.isNotEmpty() }
        val dueTimeError = when {
            normalizedTime != null && !DateTimeUtils.isValidIsoTime(normalizedTime) ->
                "Please select a valid due time."
            normalizedTime != null && dueDate == null ->
                "Select a due date when setting a reminder time."
            else -> null
        }

        val normalizedStartTime = startTime?.trim()?.takeIf { it.isNotEmpty() }
        val startTimeError = when {
            startDate != null && startDate < 0L -> "Please select a valid start date."
            normalizedStartTime != null && !DateTimeUtils.isValidIsoTime(normalizedStartTime) ->
                "Please select a valid start time."
            normalizedStartTime != null && startDate == null ->
                "Select a start date when setting a start time."
            else -> null
        }

        val deadlineError = when {
            deadline != null && deadline < 0L -> "Please select a valid deadline."
            startDate != null && deadline != null && deadline < startDate ->
                "Deadline cannot be earlier than start date."
            else -> null
        }

        val recurrenceError = when {
            recurrenceInterval !in 1..365 -> "Recurrence interval must be between 1 and 365."
            else -> null
        }

        val subtaskError = when {
            subtasks.size > MAX_SUBTASKS -> "A task can have at most $MAX_SUBTASKS subtasks."
            subtasks.any { it.title.trim().length > MAX_TITLE_LENGTH } ->
                "Each subtask must be $MAX_TITLE_LENGTH characters or fewer."
            else -> null
        }

        return TaskValidationResult(
            titleError = titleError,
            descriptionError = descriptionError,
            dueDateError = dueDateError,
            dueTimeError = dueTimeError,
            startTimeError = startTimeError,
            deadlineError = deadlineError,
            recurrenceError = recurrenceError,
            subtaskError = subtaskError
        )
    }

    fun validateListName(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "List name is required."
            trimmed.length > TaskList.MAX_NAME_LENGTH ->
                "List name must be ${TaskList.MAX_NAME_LENGTH} characters or fewer."
            else -> null
        }
    }
}
