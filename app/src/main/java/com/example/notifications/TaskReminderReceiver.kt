package com.example.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(AndroidTaskReminderScheduler.EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(AndroidTaskReminderScheduler.EXTRA_TASK_TITLE)
            ?: context.getString(R.string.app_name)
        val dueTime = intent.getStringExtra(AndroidTaskReminderScheduler.EXTRA_DUE_TIME) ?: ""
        val priority = intent.getStringExtra(AndroidTaskReminderScheduler.EXTRA_PRIORITY) ?: ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        AndroidTaskReminderScheduler(context).ensureNotificationChannel()

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.WIDGET_ACTION_EDIT_TASK)
            putExtra(MainActivity.EXTRA_WIDGET_TASK_ID, taskId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            AndroidTaskReminderScheduler.requestCodeForTask(taskId),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = buildString {
            if (dueTime.isNotBlank()) append("Due at $dueTime")
            if (priority.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append("$priority priority")
            }
            if (isEmpty()) append("Tap to view your task in SmartTodo")
        }

        val notification = NotificationCompat.Builder(
            context,
            AndroidTaskReminderScheduler.CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification_task)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                AndroidTaskReminderScheduler.requestCodeForTask(taskId),
                notification
            )
        } catch (_: SecurityException) {
            // Safely ignore if permission was revoked right before broadcast
        }
    }
}
