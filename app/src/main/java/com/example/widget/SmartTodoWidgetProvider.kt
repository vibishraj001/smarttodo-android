package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.domain.RecurrenceCalculator
import com.example.domain.model.TaskHistoryAction
import com.example.notifications.AndroidTaskReminderScheduler
import com.example.repository.FirestoreTaskRepository
import com.example.utils.DateTimeUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmartTodoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val appContext = context.applicationContext
        val currentUser = safeCurrentUser()
        val cachedSnapshot = SmartTodoWidgetStore.readSnapshot(appContext)
        if (currentUser == null || (cachedSnapshot.isSignedIn && cachedSnapshot.userId != currentUser.uid)) {
            SmartTodoWidgetStore.clear(appContext)
        }
        renderWidgets(appContext, appWidgetManager, appWidgetIds)

        if (currentUser != null) {
            refreshFromFirestoreAsync(appContext, currentUser.uid)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_REFRESH -> {
                val uid = safeCurrentUser()?.uid
                if (uid == null) {
                    SmartTodoWidgetStore.clear(appContext)
                    updateAllWidgets(appContext)
                } else {
                    refreshFromFirestoreAsync(appContext, uid)
                }
            }
            ACTION_ITEM_CLICK -> {
                val itemAction = intent.getStringExtra(EXTRA_ITEM_ACTION)
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                when (itemAction) {
                    ITEM_ACTION_COMPLETE -> {
                        if (!taskId.isNullOrBlank()) {
                            completeTaskFromWidgetAsync(appContext, taskId)
                        }
                    }
                    ITEM_ACTION_OPEN -> {
                        val openIntent = Intent(appContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            if (!taskId.isNullOrBlank()) {
                                putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.WIDGET_ACTION_EDIT_TASK)
                                putExtra(MainActivity.EXTRA_WIDGET_TASK_ID, taskId)
                            }
                        }
                        appContext.startActivity(openIntent)
                    }
                }
            }
        }
    }

    private fun refreshFromFirestoreAsync(appContext: Context, userId: String) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                val repo = FirestoreTaskRepository(appContext)
                val result = repo.fetchTasksOnce(userId)
                result.onSuccess { tasks ->
                    SmartTodoWidgetStore.saveTasks(appContext, userId, tasks)
                }
                updateAllWidgets(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "Widget Firestore refresh failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun completeTaskFromWidgetAsync(appContext: Context, taskId: String) {
        val uid = safeCurrentUser()?.uid ?: run {
            SmartTodoWidgetStore.clear(appContext)
            updateAllWidgets(appContext)
            return
        }
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                val cachedSnapshot = SmartTodoWidgetStore.readSnapshot(appContext)
                if (cachedSnapshot.isSignedIn && cachedSnapshot.userId != uid) {
                    SmartTodoWidgetStore.clear(appContext)
                    updateAllWidgets(appContext)
                    return@launch
                }
                val cachedTask = cachedSnapshot.pendingTasks.firstOrNull { it.id == taskId }
                val taskTitle = cachedTask?.title?.takeIf { it.isNotBlank() } ?: "Task"

                // Optimistic local update for immediate tactile feedback on the home screen
                SmartTodoWidgetStore.markTaskCompletedOptimistically(appContext, taskId)
                AndroidTaskReminderScheduler(appContext).cancelReminder(taskId)
                updateAllWidgets(appContext)

                val repo = FirestoreTaskRepository(appContext)
                val writeResult = repo.setTaskCompleted(uid, taskId, isCompleted = true)
                if (writeResult.isSuccess) {
                    repo.recordTaskHistory(
                        userId = uid,
                        taskId = taskId,
                        taskTitle = taskTitle,
                        action = TaskHistoryAction.COMPLETED,
                        details = "Completed from Home Screen Widget"
                    )
                    val nextOccurrence = cachedTask?.let { RecurrenceCalculator.buildNextOccurrence(it) }
                    if (nextOccurrence != null) {
                        repo.createTask(
                            userId = uid,
                            title = nextOccurrence.title,
                            description = nextOccurrence.description,
                            dueDate = nextOccurrence.dueDate,
                            dueTime = nextOccurrence.dueTime,
                            priority = nextOccurrence.priority,
                            category = nextOccurrence.category,
                            startDate = nextOccurrence.startDate,
                            startTime = nextOccurrence.startTime,
                            deadline = nextOccurrence.deadline,
                            listId = nextOccurrence.listId,
                            isStarred = nextOccurrence.isStarred,
                            recurrenceType = nextOccurrence.recurrenceType,
                            recurrenceInterval = nextOccurrence.recurrenceInterval,
                            recurrenceUnit = nextOccurrence.recurrenceUnit,
                            subtasks = nextOccurrence.subtasks,
                            sortOrder = nextOccurrence.sortOrder
                        ).onSuccess { spawned ->
                            val nextLabel = DateTimeUtils.formatDisplayDate(spawned.dueDate)
                            repo.recordTaskHistory(
                                userId = uid,
                                taskId = spawned.id,
                                taskTitle = spawned.title,
                                action = TaskHistoryAction.RECURRENCE_ADVANCED,
                                details = "Next occurrence scheduled for $nextLabel"
                            )
                        }
                        repo.fetchTasksOnce(uid).onSuccess { tasks ->
                            SmartTodoWidgetStore.saveTasks(appContext, uid, tasks)
                        }
                        updateAllWidgets(appContext)
                    }
                } else {
                    // Revert to authoritative Firestore state if write failed
                    repo.fetchTasksOnce(uid).onSuccess { tasks ->
                        SmartTodoWidgetStore.saveTasks(appContext, uid, tasks)
                    }
                    updateAllWidgets(appContext)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Widget complete task failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmartTodoWidget"
        const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH"
        const val ACTION_ITEM_CLICK = "com.example.widget.ACTION_ITEM_CLICK"
        const val EXTRA_ITEM_ACTION = "extra_item_action"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val ITEM_ACTION_COMPLETE = "complete"
        const val ITEM_ACTION_OPEN = "open"

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private fun safeCurrentUser() = try {
            Firebase.auth.currentUser
        } catch (_: Exception) {
            null
        }

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext) ?: return
            val component = ComponentName(appContext, SmartTodoWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                renderWidgets(appContext, manager, ids)
            }
        }

        fun isPinSupported(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val manager = AppWidgetManager.getInstance(context.applicationContext) ?: return false
            return manager.isRequestPinAppWidgetSupported
        }

        fun requestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext) ?: return false
            if (!manager.isRequestPinAppWidgetSupported) return false
            val component = ComponentName(appContext, SmartTodoWidgetProvider::class.java)
            return manager.requestPinAppWidget(component, null, null)
        }

        @Suppress("DEPRECATION")
        private fun renderWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val snapshot = SmartTodoWidgetStore.readSnapshot(context)

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_smarttodo)

                // Header tap -> open SmartTodo Home
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openAppPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 1,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_header_container, openAppPendingIntent)
                views.setOnClickPendingIntent(R.id.widget_empty_view, openAppPendingIntent)

                // Add Task (+) button -> open SmartTodo directly on New Task screen
                val addTaskIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.WIDGET_ACTION_ADD_TASK)
                }
                val addTaskPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 2,
                    addTaskIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_add_button, addTaskPendingIntent)

                // Refresh button -> sync from Firestore
                val refreshIntent = Intent(context, SmartTodoWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + 3,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

                // Subtitle, Progress, and Empty View state
                if (!snapshot.isSignedIn) {
                    views.setTextViewText(
                        R.id.widget_subtitle,
                        context.getString(R.string.widget_signed_out_subtitle)
                    )
                    views.setProgressBar(R.id.widget_progress_bar, 100, 0, false)
                    views.setViewVisibility(R.id.widget_progress_bar, View.GONE)
                    views.setTextViewText(
                        R.id.widget_empty_title,
                        context.getString(R.string.widget_signed_out_title)
                    )
                    views.setTextViewText(
                        R.id.widget_empty_subtitle,
                        context.getString(R.string.widget_signed_out_message)
                    )
                } else {
                    val subtitle = when {
                        snapshot.totalCount == 0 -> context.getString(R.string.widget_empty_title)
                        snapshot.pendingCount == 0 -> "All ${snapshot.totalCount} tasks done • 100%"
                        snapshot.overdueCount > 0 ->
                            "${snapshot.pendingCount} pending • ${snapshot.overdueCount} overdue"
                        else ->
                            "${snapshot.pendingCount} pending • ${snapshot.completionPercent}% done"
                    }
                    views.setTextViewText(R.id.widget_subtitle, subtitle)
                    views.setViewVisibility(R.id.widget_progress_bar, View.VISIBLE)
                    views.setProgressBar(R.id.widget_progress_bar, 100, snapshot.completionPercent, false)
                    views.setTextViewText(
                        R.id.widget_empty_title,
                        context.getString(R.string.widget_empty_title)
                    )
                    views.setTextViewText(
                        R.id.widget_empty_subtitle,
                        context.getString(R.string.widget_empty_subtitle)
                    )
                }

                // Collection click template (explicit Intent + FLAG_MUTABLE is API 34+ compliant)
                val itemClickTemplate = Intent(context, SmartTodoWidgetProvider::class.java).apply {
                    action = ACTION_ITEM_CLICK
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                val itemTemplatePendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + 4,
                    itemClickTemplate,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setPendingIntentTemplate(R.id.widget_task_list, itemTemplatePendingIntent)

                // Populate task list using zero-latency RemoteCollectionItems on API 31+,
                // falling back to RemoteViewsService on API 24-30
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val itemsBuilder = RemoteViews.RemoteCollectionItems.Builder()
                        .setHasStableIds(true)
                        .setViewTypeCount(1)
                    if (snapshot.isSignedIn) {
                        snapshot.pendingTasks.forEachIndexed { index, task ->
                            itemsBuilder.addItem(
                                task.id.hashCode().toLong().takeIf { it != 0L } ?: index.toLong(),
                                SmartTodoWidgetRowBinder.buildRowRemoteViews(context, task)
                            )
                        }
                    }
                    views.setRemoteAdapter(R.id.widget_task_list, itemsBuilder.build())
                } else {
                    val serviceIntent = Intent(context, SmartTodoWidgetService::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                    }
                    views.setRemoteAdapter(R.id.widget_task_list, serviceIntent)
                }
                views.setEmptyView(R.id.widget_task_list, R.id.widget_empty_view)

                appWidgetManager.updateAppWidget(appWidgetId, views)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_task_list)
                }
            }
        }
    }
}
