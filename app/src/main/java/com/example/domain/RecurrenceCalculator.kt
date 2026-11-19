package com.example.domain

import com.example.domain.model.RecurrenceType
import com.example.domain.model.RecurrenceUnit
import com.example.domain.model.Task
import com.example.utils.DateTimeUtils
import java.util.Calendar
import java.util.TimeZone

/**
 * Pure domain calculator for recurring tasks (Daily, Weekly, Monthly, Yearly, and Custom).
 */
object RecurrenceCalculator {

    fun nextOccurrenceDateMillis(
        baseDateUtcMillis: Long?,
        recurrenceType: RecurrenceType,
        interval: Int = 1,
        unit: RecurrenceUnit = RecurrenceUnit.DAYS,
        todayUtcMillis: Long = DateTimeUtils.todayUtcMillis()
    ): Long? {
        if (recurrenceType == RecurrenceType.NONE) return null

        val anchor = DateTimeUtils.normalizeToUtcStartOfDay(baseDateUtcMillis ?: todayUtcMillis)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = anchor
        }
        val safeInterval = interval.coerceIn(1, 365)

        when (recurrenceType) {
            RecurrenceType.NONE -> return null
            RecurrenceType.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RecurrenceType.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RecurrenceType.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RecurrenceType.YEARLY -> cal.add(Calendar.YEAR, 1)
            RecurrenceType.CUSTOM -> {
                when (unit) {
                    RecurrenceUnit.DAYS -> cal.add(Calendar.DAY_OF_YEAR, safeInterval)
                    RecurrenceUnit.WEEKS -> cal.add(Calendar.WEEK_OF_YEAR, safeInterval)
                    RecurrenceUnit.MONTHS -> cal.add(Calendar.MONTH, safeInterval)
                    RecurrenceUnit.YEARS -> cal.add(Calendar.YEAR, safeInterval)
                }
            }
        }
        return DateTimeUtils.normalizeToUtcStartOfDay(cal.timeInMillis)
    }

    /**
     * Generates the next occurrence task when [completedTask] is marked completed.
     * Shifts `startDate` and `deadline` by the exact same day delta as `dueDate` when present,
     * resets all subtask completion states, and clears `calendarEventId`.
     */
    fun buildNextOccurrence(
        completedTask: Task,
        todayUtcMillis: Long = DateTimeUtils.todayUtcMillis()
    ): Task? {
        val type = completedTask.recurrenceTypeEnum()
        if (type == RecurrenceType.NONE) return null

        val currentAnchor = completedTask.dueDate ?: completedTask.startDate ?: todayUtcMillis
        val nextDue = nextOccurrenceDateMillis(
            baseDateUtcMillis = currentAnchor,
            recurrenceType = type,
            interval = completedTask.recurrenceInterval,
            unit = completedTask.recurrenceUnitEnum(),
            todayUtcMillis = todayUtcMillis
        ) ?: return null

        val deltaMillis = nextDue - DateTimeUtils.normalizeToUtcStartOfDay(currentAnchor)
        val nextStart = completedTask.startDate?.let {
            DateTimeUtils.normalizeToUtcStartOfDay(it + deltaMillis)
        }
        val nextDeadline = completedTask.deadline?.let {
            DateTimeUtils.normalizeToUtcStartOfDay(it + deltaMillis)
        }

        return completedTask.copy(
            id = "",
            dueDate = if (completedTask.dueDate != null || completedTask.startDate == null) nextDue else null,
            startDate = nextStart,
            deadline = nextDeadline,
            isCompleted = false,
            subtasks = completedTask.subtasks.map { it.copy(isCompleted = false) },
            calendarEventId = null
        )
    }

    fun formatRecurrenceLabel(
        recurrenceType: RecurrenceType,
        interval: Int = 1,
        unit: RecurrenceUnit = RecurrenceUnit.DAYS
    ): String? {
        return when (recurrenceType) {
            RecurrenceType.NONE -> null
            RecurrenceType.DAILY -> "Daily"
            RecurrenceType.WEEKLY -> "Weekly"
            RecurrenceType.MONTHLY -> "Monthly"
            RecurrenceType.YEARLY -> "Yearly"
            RecurrenceType.CUSTOM -> {
                val safeInterval = interval.coerceIn(1, 365)
                if (safeInterval == 1) {
                    "Every ${unit.singularName}"
                } else {
                    "Every $safeInterval ${unit.displayName.lowercase()}"
                }
            }
        }
    }
}
