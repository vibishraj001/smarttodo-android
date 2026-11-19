package com.example.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.model.Task
import com.example.domain.model.TaskPriority
import com.example.utils.DateTimeUtils

internal object SmartTodoWidgetRowBinder {
    fun buildRowRemoteViews(context: Context, task: Task): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_smarttodo_item)
        views.setTextViewText(R.id.widget_item_title, task.title)

        val priorityDrawable = when (task.priorityEnum()) {
            TaskPriority.HIGH -> R.drawable.bg_widget_priority_high
            TaskPriority.MEDIUM -> R.drawable.bg_widget_priority_medium
            TaskPriority.LOW -> R.drawable.bg_widget_priority_low
        }
        views.setImageViewResource(R.id.widget_item_priority_bar, priorityDrawable)

        val isOverdue = DateTimeUtils.isTaskOverdue(task)
        val metaParts = buildList {
            if (task.dueDate != null) {
                add(DateTimeUtils.formatDisplayDate(task.dueDate))
            }
            DateTimeUtils.formatDisplayTime(task.dueTime)?.let { add(it) }
            add(task.categoryEnum().displayName)
        }
        val metaText = metaParts.joinToString(" • ")
        views.setTextViewText(R.id.widget_item_meta, metaText)
        views.setTextColor(
            R.id.widget_item_meta,
            ContextCompat.getColor(
                context,
                if (isOverdue) R.color.widget_overdue else R.color.widget_text_secondary
            )
        )

        val completeFillIn = Intent().apply {
            putExtra(SmartTodoWidgetProvider.EXTRA_ITEM_ACTION, SmartTodoWidgetProvider.ITEM_ACTION_COMPLETE)
            putExtra(SmartTodoWidgetProvider.EXTRA_TASK_ID, task.id)
        }
        views.setOnClickFillInIntent(R.id.widget_item_checkbox, completeFillIn)

        val openFillIn = Intent().apply {
            putExtra(SmartTodoWidgetProvider.EXTRA_ITEM_ACTION, SmartTodoWidgetProvider.ITEM_ACTION_OPEN)
            putExtra(SmartTodoWidgetProvider.EXTRA_TASK_ID, task.id)
        }
        views.setOnClickFillInIntent(R.id.widget_item_content, openFillIn)

        return views
    }
}

class SmartTodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return SmartTodoRemoteViewsFactory(applicationContext)
    }
}

private class SmartTodoRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<Task> = emptyList()

    override fun onCreate() {
        tasks = SmartTodoWidgetStore.readSnapshot(context).pendingTasks
    }

    override fun onDataSetChanged() {
        tasks = SmartTodoWidgetStore.readSnapshot(context).pendingTasks
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        val task = tasks.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_smarttodo_item)
        return SmartTodoWidgetRowBinder.buildRowRemoteViews(context, task)
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return tasks.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}
