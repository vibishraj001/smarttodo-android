package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.R
import com.example.model.ReminderLeadTime
import com.example.domain.model.Task
import com.example.utils.DateTimeUtils

interface ReminderScheduler {
    fun scheduleReminder(task: Task, leadTime: ReminderLeadTime)
    fun cancelReminder(taskId: String)
    fun syncAllReminders(tasks: List<Task>, notificationsEnabled: Boolean, leadTime: ReminderLeadTime)
}

class AndroidTaskReminderScheduler(
    private val context: Context
) : ReminderScheduler {

    private val appContext = context.applicationContext

    init {
        ensureNotificationChannel()
    }

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.notification_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun scheduleReminder(task: Task, leadTime: ReminderLeadTime) {
        if (task.id.isBlank() || task.isCompleted || task.dueDate == null || task.dueTime.isNullOrBlank()) {
            cancelReminder(task.id)
            return
        }

        val dueMillis = DateTimeUtils.computeDueDateTimeMillis(task.dueDate, task.dueTime)
            ?: return
        val triggerMillis = dueMillis - (leadTime.minutesBefore * 60_000L)
        val nowMillis = System.currentTimeMillis()

        // If the reminder window has already passed (or due time passed), cancel stale alarms
        if (dueMillis <= nowMillis) {
            cancelReminder(task.id)
            return
        }

        val effectiveTrigger = maxOf(triggerMillis, nowMillis + 5_000L)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return

        val pendingIntent = buildPendingIntent(
            taskId = task.id,
            title = task.title,
            dueTimeDisplay = DateTimeUtils.formatDisplayTime(task.dueTime) ?: task.dueTime,
            priorityLabel = task.priorityEnum().displayName
        )

        // Play-policy compliant scheduling without requiring restricted SCHEDULE_EXACT_ALARM
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            effectiveTrigger,
            pendingIntent
        )
    }

    override fun cancelReminder(taskId: String) {
        if (taskId.isBlank()) return
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        val intent = Intent(appContext, TaskReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCodeForTask(taskId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    override fun syncAllReminders(
        tasks: List<Task>,
        notificationsEnabled: Boolean,
        leadTime: ReminderLeadTime
    ) {
        for (task in tasks) {
            if (!notificationsEnabled || task.isCompleted || task.dueDate == null || task.dueTime.isNullOrBlank()) {
                cancelReminder(task.id)
            } else {
                scheduleReminder(task, leadTime)
            }
        }
    }

    private fun buildPendingIntent(
        taskId: String,
        title: String,
        dueTimeDisplay: String,
        priorityLabel: String
    ): PendingIntent {
        val intent = Intent(appContext, TaskReminderReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_DUE_TIME, dueTimeDisplay)
            putExtra(EXTRA_PRIORITY, priorityLabel)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCodeForTask(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "smarttodo_task_reminders"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_DUE_TIME = "extra_due_time"
        const val EXTRA_PRIORITY = "extra_priority"

        fun requestCodeForTask(taskId: String): Int = taskId.hashCode() and 0x7FFFFFFF
    }
}
