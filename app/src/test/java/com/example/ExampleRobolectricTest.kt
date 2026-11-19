package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.Task
import com.example.model.DashboardStats
import com.example.model.ReminderLeadTime
import com.example.model.TaskFilter
import com.example.model.TaskSortOption
import com.example.model.ThemeMode
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.notifications.AndroidTaskReminderScheduler
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.TaskEditorScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.SmartTodoTheme
import com.example.utils.DateTimeUtils
import com.example.widget.SmartTodoWidgetProvider
import com.example.widget.SmartTodoWidgetStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app name string resource matches SmartTodo`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SmartTodo", appName)
    }

    @Test
    fun `HomeScreen renders greeting, stats, and today task`() {
        val todayTask = Task(
            id = "t1",
            title = "Review architecture docs",
            dueDate = DateTimeUtils.todayUtcMillis(),
            dueTime = "16:00",
            priority = "High",
            category = "Work"
        )
        val stats = DashboardStats(
            totalTasks = 3,
            completedTasks = 1,
            pendingTasks = 2,
            overdueTasks = 0,
            starredTasks = 1,
            completionProgress = 1f / 3f,
            todayTasks = listOf(todayTask)
        )

        composeTestRule.setContent {
            SmartTodoTheme(themeMode = ThemeMode.LIGHT) {
                HomeScreen(
                    tasksState = UiState.Success(listOf(todayTask)),
                    taskLists = emptyList(),
                    stats = stats,
                    profile = UserProfile(userId = "u1", name = "Alex Rivera", email = "alex@example.com"),
                    isWorkspaceConnected = false,
                    onQuickAddTask = { _, _, _ -> },
                    onToggleTask = {},
                    onToggleStar = {},
                    onToggleSubtask = { _, _ -> },
                    onEditTask = {},
                    onDeleteTask = {},
                    onNavigateToFilter = {},
                    onOpenWorkspace = {},
                    onOpenHistory = {},
                    onRetry = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_greeting").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_progress_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_screen_list")
            .performScrollToNode(hasText("Review architecture docs"))
        composeTestRule.onNodeWithText("Review architecture docs").assertIsDisplayed()
    }

    @Test
    fun `TaskEditorScreen validates empty title and submits valid task`() {
        var savedTitle: String? = null

        composeTestRule.setContent {
            SmartTodoTheme(themeMode = ThemeMode.LIGHT) {
                TaskEditorScreen(
                    existingTask = null,
                    taskLists = emptyList(),
                    defaultListId = null,
                    isSaving = false,
                    onSave = { payload -> savedTitle = payload.title },
                    onCreateList = { _, _ -> },
                    onRenameList = { _, _, _ -> },
                    onDeleteList = {},
                    onOpenWorkspace = {},
                    onCancel = {},
                    onPermissionDeniedMessage = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("save_task_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("task_title_input").performScrollTo()
        composeTestRule.onNodeWithText("Task title is required.").assertIsDisplayed()
        assertEquals(null, savedTitle)

        composeTestRule.onNodeWithTag("task_title_input").performTextInput("Prepare demo build")
        composeTestRule.onNodeWithTag("save_task_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("Prepare demo build", savedTitle)
    }

    @Test
    fun `TasksScreen triggers filter selection, star toggle, and delete confirmation dialog`() {
        var selectedFilterCaptured: TaskFilter? = null
        var starredTaskId: String? = null
        var deletedTaskId: String? = null
        val sampleTask = Task(
            id = "t_del",
            title = "Renew gym membership",
            dueDate = DateTimeUtils.todayUtcMillis(),
            priority = "Low",
            category = "Personal"
        )

        composeTestRule.setContent {
            SmartTodoTheme(themeMode = ThemeMode.LIGHT) {
                TasksScreen(
                    tasksState = UiState.Success(listOf(sampleTask)),
                    taskLists = emptyList(),
                    selectedListId = null,
                    selectedFilter = TaskFilter.ALL,
                    selectedSort = TaskSortOption.DUE_DATE,
                    searchQuery = "",
                    onSelectList = {},
                    onCreateList = { _, _ -> },
                    onRenameList = { _, _, _ -> },
                    onDeleteList = {},
                    onQuickAddTask = { _, _, _ -> },
                    onFilterSelected = { selectedFilterCaptured = it },
                    onSortSelected = {},
                    onSearchQueryChange = {},
                    onToggleTask = {},
                    onToggleStar = { starredTaskId = it.id },
                    onToggleSubtask = { _, _ -> },
                    onReorderTasks = {},
                    onEditTask = {},
                    onDeleteTask = { deletedTaskId = it.id },
                    onOpenHistory = {},
                    onRetry = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("task_filter_chips")
            .performScrollToNode(hasTestTag("filter_chip_overdue"))
        composeTestRule.onNodeWithTag("filter_chip_overdue").performClick()
        composeTestRule.waitForIdle()
        assertEquals(TaskFilter.OVERDUE, selectedFilterCaptured)

        composeTestRule.onNodeWithTag("tasks_list")
            .performScrollToNode(hasTestTag("task_card_t_del"))
        composeTestRule.onNodeWithTag("task_star_t_del").performClick()
        composeTestRule.waitForIdle()
        assertEquals("t_del", starredTaskId)

        composeTestRule.onNodeWithTag("task_delete_t_del").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_delete_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("t_del", deletedTaskId)
    }

    @Test
    fun `AndroidTaskReminderScheduler schedules and cancels without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduler = AndroidTaskReminderScheduler(context)
        val futureTask = Task(
            id = "rem_1",
            title = "Dentist appointment",
            dueDate = DateTimeUtils.utcDateMillis(2099, 11, 31),
            dueTime = "10:30",
            priority = "Medium",
            category = "Personal",
            isCompleted = false
        )
        scheduler.scheduleReminder(futureTask, ReminderLeadTime.FIFTEEN_MINUTES)
        scheduler.cancelReminder(futureTask.id)
        assertTrue(true)
    }

    @Test
    fun `SmartTodoWidgetStore persists tasks, supports optimistic completion, and clears on sign-out`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SmartTodoWidgetStore.clear(context)

        val todayMillis = DateTimeUtils.todayUtcMillis()
        val tasks = listOf(
            Task(
                id = "w1",
                title = "Send weekly update",
                dueDate = todayMillis,
                dueTime = "17:00",
                priority = "High",
                category = "Work",
                isCompleted = false
            ),
            Task(
                id = "w2",
                title = "Done item",
                dueDate = todayMillis,
                priority = "Low",
                category = "Personal",
                isCompleted = true
            )
        )

        SmartTodoWidgetStore.saveTasks(context, "uid_widget_test", tasks)
        val saved = SmartTodoWidgetStore.readSnapshot(context)
        assertTrue(saved.isSignedIn)
        assertEquals(2, saved.totalCount)
        assertEquals(1, saved.completedCount)
        assertEquals(1, saved.pendingCount)
        assertEquals(50, saved.completionPercent)
        assertEquals(listOf("w1"), saved.pendingTasks.map { it.id })

        SmartTodoWidgetStore.markTaskCompletedOptimistically(context, "w1")
        val afterComplete = SmartTodoWidgetStore.readSnapshot(context)
        assertEquals(2, afterComplete.completedCount)
        assertEquals(0, afterComplete.pendingCount)
        assertEquals(100, afterComplete.completionPercent)
        assertTrue(afterComplete.pendingTasks.isEmpty())

        SmartTodoWidgetProvider.updateAllWidgets(context)

        SmartTodoWidgetStore.clear(context)
        val cleared = SmartTodoWidgetStore.readSnapshot(context)
        assertFalse(cleared.isSignedIn)
        assertTrue(cleared.pendingTasks.isEmpty())
    }
}
