package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppSettings
import com.example.data.SettingsDataStore
import com.example.data.SettingsRepository
import com.example.domain.model.RecurrenceType
import com.example.domain.model.RecurrenceUnit
import com.example.domain.model.Subtask
import com.example.domain.model.Task
import com.example.domain.model.TaskHistoryAction
import com.example.domain.model.TaskHistoryEntry
import com.example.domain.model.TaskList
import com.example.model.ReminderLeadTime
import com.example.model.TaskSortOption
import com.example.model.ThemeMode
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.navigation.AuthenticatedSmartTodoContent
import com.example.navigation.WidgetDeepLink
import com.example.notifications.AndroidTaskReminderScheduler
import com.example.notifications.ReminderScheduler
import com.example.notifications.TaskReminderReceiver
import com.example.repository.TaskRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.TaskEditorScreen
import com.example.ui.theme.SmartTodoTheme
import com.example.utils.ConnectivityObserver
import com.example.utils.DateTimeUtils
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.TaskViewModel
import com.example.viewmodel.WorkspaceViewModel
import com.example.workspace.CalendarSyncResult
import com.example.workspace.WorkspaceApiClient
import com.example.workspace.WorkspaceCalendarEvent
import com.example.workspace.WorkspaceChatSpace
import com.example.workspace.WorkspaceEmailItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-mdpi")
class SmartTodoEndToEndTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun authScreen_rendersBrandingHighlightsAndEnabledSignInButton() {
        composeTestRule.setContent {
            SmartTodoTheme(themeMode = ThemeMode.LIGHT) {
                AuthScreen(
                    suppressAutoSignIn = true,
                    onAuthSuccess = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("SmartTodo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun fullAppShell_endToEndTaskLifecycle_createEditCompleteReopenRecurringDeleteAndOfflineBanner() {
        val fakeRepo = E2EFakeTaskRepository()
        val fakeSettings = E2EFakeSettingsRepository()
        val fakeReminders = E2EFakeReminderScheduler()
        val onlineFlow = MutableStateFlow(true)
        val taskViewModel = createTaskViewModel(fakeRepo, fakeSettings, fakeReminders, onlineFlow)
        val workspaceViewModel = WorkspaceViewModel(E2EFakeWorkspaceApiClient())
        val settingsViewModel = SettingsViewModel(fakeSettings)

        composeTestRule.setContent {
            val settings by settingsViewModel.settings.collectAsState()
            SmartTodoTheme(themeMode = settings.themeMode) {
                AuthenticatedSmartTodoContent(
                    taskViewModel = taskViewModel,
                    workspaceViewModel = workspaceViewModel,
                    settingsViewModel = settingsViewModel,
                    settings = settings
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Home screen loads & offline banner reacts to connectivity
        composeTestRule.onNodeWithTag("home_greeting").assertIsDisplayed()
        onlineFlow.value = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("offline_banner").assertIsDisplayed()
        onlineFlow.value = true
        composeTestRule.waitForIdle()

        // 2. Quick-add a starred task from Home (scroll past QuickAdd so it sits cleanly above the FAB)
        composeTestRule.onNodeWithTag("home_screen_list")
            .performScrollToNode(hasTestTag("home_see_all_today_button"))
        composeTestRule.onNodeWithTag("quick_add_input").performTextInput("Buy groceries")
        composeTestRule.onNodeWithTag("quick_add_star_toggle").performClick()
        composeTestRule.onNodeWithTag("quick_add_submit_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, fakeRepo.tasksSnapshot().size)
        val quickTask = fakeRepo.tasksSnapshot().first()
        assertEquals("Buy groceries", quickTask.title)
        assertTrue(quickTask.isStarred)

        // 3. Tap FAB -> create full recurring task with description, priority, category, custom recurrence, subtasks
        composeTestRule.onNodeWithTag("add_task_fab").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("task_title_input").performTextInput("Sprint retrospective")
        composeTestRule.onNodeWithTag("task_description_input").performTextInput("Review velocity and blockers")
        composeTestRule.onNodeWithTag("editor_star_toggle").performClick()
        composeTestRule.onNodeWithTag("priority_chip_high").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("category_chip_work").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("recurrence_chip_custom").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recurrence_interval_plus").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("recurrence_unit_weeks").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("add_subtask_input").performScrollTo().performTextInput("Collect metrics")
        composeTestRule.onNodeWithTag("add_subtask_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_subtask_input").performScrollTo().performTextInput("Share slides")
        composeTestRule.onNodeWithTag("add_subtask_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("save_task_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Returned to Home and task persisted with all attributes
        composeTestRule.onNodeWithTag("home_screen_list").performScrollToIndex(0)
        composeTestRule.onNodeWithTag("home_greeting").assertIsDisplayed()
        assertEquals(2, fakeRepo.tasksSnapshot().size)
        val recurringTask = fakeRepo.tasksSnapshot().first { it.title == "Sprint retrospective" }
        assertEquals("High", recurringTask.priority)
        assertEquals("Work", recurringTask.category)
        assertEquals("Custom", recurringTask.recurrenceType)
        assertEquals(2, recurringTask.recurrenceInterval)
        assertEquals("Weeks", recurringTask.recurrenceUnit)
        assertEquals(2, recurringTask.subtasks.size)
        assertTrue(recurringTask.isStarred)

        // 4. Navigate to Tasks tab, expand subtask drawer, and edit task
        composeTestRule.onNodeWithTag("nav_tasks").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tasks_list")
            .performScrollToNode(hasTestTag("task_card_${recurringTask.id}"))
        composeTestRule.onNodeWithTag("task_subtasks_pill_${recurringTask.id}").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tasks_list")
            .performScrollToNode(hasText("Collect metrics"))
        composeTestRule.onNodeWithText("Collect metrics").assertIsDisplayed()

        composeTestRule.onNodeWithTag("tasks_list")
            .performScrollToNode(hasTestTag("task_card_${quickTask.id}"))
        composeTestRule.onNodeWithTag("task_card_${quickTask.id}").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("task_title_input").performTextClearance()
        composeTestRule.onNodeWithTag("task_title_input").performTextInput("Buy organic groceries")
        composeTestRule.onNodeWithTag("save_task_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("Buy organic groceries", fakeRepo.tasksSnapshot().first { it.id == quickTask.id }.title)
        composeTestRule.mainClock.advanceTimeBy(4_500L)
        composeTestRule.waitForIdle()

        // 5. Complete non-recurring task -> verify in Completed tab -> search, clear search, and reopen
        composeTestRule.onNodeWithTag("tasks_list")
            .performScrollToNode(hasTestTag("task_toggle_${quickTask.id}"))
        composeTestRule.onNodeWithTag("task_toggle_${quickTask.id}").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_completed").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("completed_tasks_list")
            .performScrollToNode(hasTestTag("task_card_${quickTask.id}"))
        composeTestRule.onNodeWithTag("completed_search_input").performTextInput("organic")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("task_card_${quickTask.id}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("completed_clear_search_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("completed_tasks_list")
            .performScrollToNode(hasTestTag("task_reopen_${quickTask.id}"))
        composeTestRule.onNodeWithTag("task_reopen_${quickTask.id}").performClick()
        composeTestRule.waitForIdle()
        assertFalse(fakeRepo.tasksSnapshot().first { it.id == quickTask.id }.isCompleted)

        // 6. Complete recurring task -> spawns next occurrence automatically
        composeTestRule.onNodeWithTag("nav_tasks").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tasks_list")
            .performScrollToNode(hasTestTag("task_toggle_${recurringTask.id}"))
        composeTestRule.onNodeWithTag("task_toggle_${recurringTask.id}").performClick()
        composeTestRule.waitForIdle()

        assertEquals(3, fakeRepo.tasksSnapshot().size)
        val spawned = fakeRepo.tasksSnapshot().first { it.title == "Sprint retrospective" && !it.isCompleted }
        val recurringDueDate = requireNotNull(recurringTask.dueDate)
        assertEquals(recurringDueDate + 14 * 86_400_000L, spawned.dueDate)
        assertTrue(spawned.subtasks.all { !it.isCompleted })
        composeTestRule.mainClock.advanceTimeBy(4_500L)
        composeTestRule.waitForIdle()

        // 7. Delete a task via delete button + confirmation dialog
        composeTestRule.onNodeWithTag("tasks_list")
            .performScrollToNode(hasTestTag("task_delete_${quickTask.id}"))
        composeTestRule.onNodeWithTag("task_delete_${quickTask.id}").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_delete_button").performClick()
        composeTestRule.waitForIdle()
        assertNull(fakeRepo.tasksSnapshot().firstOrNull { it.id == quickTask.id })
    }

    @Test
    fun tasksScreen_listsCrudSearchFilterSortAndGlobalOrderPreservationOnFilteredReorder() {
        val fakeRepo = E2EFakeTaskRepository()
        val fakeSettings = E2EFakeSettingsRepository()
        val fakeReminders = E2EFakeReminderScheduler()
        val onlineFlow = MutableStateFlow(true)
        val today = DateTimeUtils.todayUtcMillis()

        runBlocking {
            fakeRepo.createTask("u1", "Alpha Work", null, today, null, "High", "Work", listId = null, sortOrder = 0)
            fakeRepo.createTask("u1", "Beta Personal", null, today + 86_400_000L, null, "Low", "Personal", isStarred = true, sortOrder = 1)
            fakeRepo.createTask("u1", "Gamma Starred", null, today, null, "Medium", "Work", isStarred = true, sortOrder = 2)
        }

        val taskViewModel = createTaskViewModel(fakeRepo, fakeSettings, fakeReminders, onlineFlow)
        val workspaceViewModel = WorkspaceViewModel(E2EFakeWorkspaceApiClient())
        val settingsViewModel = SettingsViewModel(fakeSettings)

        composeTestRule.setContent {
            val settings by settingsViewModel.settings.collectAsState()
            SmartTodoTheme(themeMode = settings.themeMode) {
                AuthenticatedSmartTodoContent(
                    taskViewModel = taskViewModel,
                    workspaceViewModel = workspaceViewModel,
                    settingsViewModel = settingsViewModel,
                    settings = settings
                )
            }
        }
        composeTestRule.waitForIdle()

        // Navigate to Tasks
        composeTestRule.onNodeWithTag("nav_tasks").performClick()
        composeTestRule.waitForIdle()

        // Manage Task Lists Dialog: validate empty name, create list, rename list
        composeTestRule.onNodeWithTag("task_lists_switcher_row")
            .performScrollToNode(hasTestTag("manage_lists_chip"))
        composeTestRule.onNodeWithTag("manage_lists_chip").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("save_list_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("List name is required.").assertIsDisplayed()

        composeTestRule.onNodeWithTag("list_name_input").performTextInput("Product Launch")
        composeTestRule.onNodeWithTag("save_list_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, fakeRepo.listsSnapshot().size)
        val listId = fakeRepo.listsSnapshot().first().id

        composeTestRule.onNodeWithTag("rename_list_$listId").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("list_name_input").performTextClearance()
        composeTestRule.onNodeWithTag("list_name_input").performTextInput("Q4 Launch")
        composeTestRule.onNodeWithTag("save_list_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Q4 Launch", fakeRepo.listsSnapshot().first().name)

        composeTestRule.onNodeWithText("Done").performClick()
        composeTestRule.waitForIdle()

        // Return to All Lists, verify search + clear search
        composeTestRule.onNodeWithTag("list_chip_all").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("task_search_input").performTextInput("Gamma")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tasks_list")
            .performScrollToNode(hasText("Gamma Starred"))
        composeTestRule.onNodeWithText("Gamma Starred").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_search_button").performClick()
        composeTestRule.waitForIdle()

        // Switch sort to Manual Order -> verify drag handle displays
        composeTestRule.onNodeWithTag("sort_dropdown_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sort_option_manual_order").performClick()
        composeTestRule.waitForIdle()
        assertEquals(TaskSortOption.MANUAL_ORDER, taskViewModel.selectedSort.value)
        composeTestRule.onNodeWithTag("tasks_list")
            .performScrollToNode(hasText("Drag handles to reorder"))
        composeTestRule.onNodeWithText("Drag handles to reorder").assertIsDisplayed()

        // Filtered reorder of only starred tasks preserves strictly unique global sortOrder (no collisions!)
        val starredSubset = fakeRepo.tasksSnapshot().filter { it.isStarred }.reversed()
        taskViewModel.reorderTasks(starredSubset)
        composeTestRule.waitForIdle()
        val ordersAfterSubsetDrag = fakeRepo.tasksSnapshot().map { it.sortOrder }.sorted()
        assertEquals(listOf(0, 1, 2), ordersAfterSubsetDrag)

        // Delete list cleanly via dialog
        composeTestRule.onNodeWithTag("task_lists_switcher_row")
            .performScrollToNode(hasTestTag("manage_lists_chip"))
        composeTestRule.onNodeWithTag("manage_lists_chip").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("delete_list_$listId").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_delete_list_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(fakeRepo.listsSnapshot().isEmpty())
        assertNull(taskViewModel.selectedListId.value)
    }

    @Test
    fun taskEditorScreen_dateAndTimePickersClearButtonsAndStaleListReset() {
        var listsState by mutableStateOf(listOf(TaskList(id = "l1", name = "Work", colorHex = "#2563EB")))
        var savedPayload: com.example.ui.screens.TaskEditorPayload? = null

        composeTestRule.setContent {
            SmartTodoTheme(themeMode = ThemeMode.LIGHT) {
                TaskEditorScreen(
                    existingTask = null,
                    taskLists = listsState,
                    defaultListId = "l1",
                    isSaving = false,
                    onSave = { savedPayload = it },
                    onCreateList = { _, _ -> },
                    onRenameList = { _, _, _ -> },
                    onDeleteList = { deleted -> listsState = listsState.filterNot { it.id == deleted.id } },
                    onOpenWorkspace = {},
                    onCancel = {},
                    onPermissionDeniedMessage = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Open and confirm due time picker
        composeTestRule.onNodeWithTag("due_time_picker_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("time_picker_confirm_button").performClick()
        composeTestRule.waitForIdle()

        // Open and confirm start date picker
        composeTestRule.onNodeWithTag("start_date_picker_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("date_picker_confirm_button").performClick()
        composeTestRule.waitForIdle()

        // Delete currently selected list inside editor dialog -> listId must automatically reset to null
        composeTestRule.onNodeWithTag("editor_new_list_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("delete_list_l1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_delete_list_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Done").performClick()
        composeTestRule.waitForIdle()

        // Fill title and save
        composeTestRule.onNodeWithTag("task_title_input").performScrollTo().performTextInput("Scheduled milestone")
        composeTestRule.onNodeWithTag("save_task_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        val payload = requireNotNull(savedPayload)
        assertEquals("Scheduled milestone", payload.title)
        assertNotNull(payload.dueTime)
        assertNotNull(payload.startDate)
        assertNull(payload.listId)
    }

    @Test
    fun taskHistoryScreen_filtersCategoriesAndClearsHistoryEndToEnd() {
        val fakeRepo = E2EFakeTaskRepository()
        val fakeSettings = E2EFakeSettingsRepository()
        val fakeReminders = E2EFakeReminderScheduler()
        val onlineFlow = MutableStateFlow(true)
        runBlocking {
            fakeRepo.recordTaskHistory("u1", "t1", "Ship v2.0", TaskHistoryAction.CREATED, "High priority")
            fakeRepo.recordTaskHistory("u1", "t1", "Ship v2.0", TaskHistoryAction.COMPLETED)
            fakeRepo.recordTaskHistory("u1", "t1", "Ship v2.0", TaskHistoryAction.SYNCED_TO_CALENDAR, "Google Calendar")
        }

        val taskViewModel = createTaskViewModel(fakeRepo, fakeSettings, fakeReminders, onlineFlow)
        val workspaceViewModel = WorkspaceViewModel(E2EFakeWorkspaceApiClient())
        val settingsViewModel = SettingsViewModel(fakeSettings)

        composeTestRule.setContent {
            val settings by settingsViewModel.settings.collectAsState()
            SmartTodoTheme(themeMode = settings.themeMode) {
                AuthenticatedSmartTodoContent(
                    taskViewModel = taskViewModel,
                    workspaceViewModel = workspaceViewModel,
                    settingsViewModel = settingsViewModel,
                    settings = settings
                )
            }
        }
        composeTestRule.waitForIdle()

        // Open History from Home quick shortcut
        composeTestRule.onNodeWithTag("home_screen_list")
            .performScrollToNode(hasTestTag("home_history_shortcut"))
        composeTestRule.onNodeWithTag("home_history_shortcut").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("task_history_list").assertIsDisplayed()

        // Filter to Recurring -> should show filter-specific empty state
        composeTestRule.onNodeWithTag("history_filter_recurring").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No recurring activity").assertIsDisplayed()

        // Filter to Workspace -> should show Calendar entry
        composeTestRule.onNodeWithTag("history_filter_workspace").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Google Calendar").assertIsDisplayed()

        // Clear history with confirmation dialog
        composeTestRule.onNodeWithTag("clear_history_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_clear_history_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(fakeRepo.historySnapshot().isEmpty())

        // Back button returns to Home
        composeTestRule.onNodeWithTag("history_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").performScrollToIndex(0)
        composeTestRule.onNodeWithTag("home_greeting").assertIsDisplayed()
    }

    @Test
    fun workspaceHubScreen_calendarGmailChatSyncImportShareAndDisconnectEndToEnd() {
        val fakeRepo = E2EFakeTaskRepository()
        val fakeSettings = E2EFakeSettingsRepository()
        val fakeReminders = E2EFakeReminderScheduler()
        val fakeWorkspaceApi = E2EFakeWorkspaceApiClient()
        val onlineFlow = MutableStateFlow(true)
        val today = DateTimeUtils.todayUtcMillis()

        val seededTask = runBlocking {
            fakeRepo.createTask("u1", "Design review", "Check tokens", today, "14:00", "High", "Work").getOrThrow()
        }

        val taskViewModel = createTaskViewModel(fakeRepo, fakeSettings, fakeReminders, onlineFlow)
        val workspaceViewModel = WorkspaceViewModel(fakeWorkspaceApi)
        val settingsViewModel = SettingsViewModel(fakeSettings)

        composeTestRule.setContent {
            val settings by settingsViewModel.settings.collectAsState()
            SmartTodoTheme(themeMode = settings.themeMode) {
                AuthenticatedSmartTodoContent(
                    taskViewModel = taskViewModel,
                    workspaceViewModel = workspaceViewModel,
                    settingsViewModel = settingsViewModel,
                    settings = settings
                )
            }
        }
        composeTestRule.waitForIdle()

        // Open Workspace Hub from Home
        composeTestRule.onNodeWithTag("home_screen_list")
            .performScrollToNode(hasTestTag("home_workspace_shortcut"))
        composeTestRule.onNodeWithTag("home_workspace_shortcut").performClick()
        composeTestRule.waitForIdle()

        // Disconnected state renders connect button
        composeTestRule.onNodeWithTag("workspace_connect_button").assertIsDisplayed()

        // Obtain OAuth token -> loads Calendar, Gmail, Chat
        workspaceViewModel.onAccessTokenObtained("valid_workspace_token")
        composeTestRule.waitForIdle()

        // Calendar Tab: Sync task to Calendar + Import calendar event
        composeTestRule.onNodeWithTag("workspace_hub_list")
            .performScrollToNode(hasTestTag("workspace_sync_task_${seededTask.id}"))
        composeTestRule.onNodeWithTag("workspace_sync_task_${seededTask.id}").performClick()
        composeTestRule.waitForIdle()
        assertEquals("cal_evt_synced_1", fakeRepo.tasksSnapshot().first { it.id == seededTask.id }.calendarEventId)

        composeTestRule.onNodeWithTag("workspace_hub_list")
            .performScrollToNode(hasTestTag("workspace_import_event_evt_1"))
        composeTestRule.onNodeWithTag("workspace_import_event_evt_1").performClick()
        composeTestRule.waitForIdle()
        assertTrue(fakeRepo.tasksSnapshot().any { it.title == "Quarterly Planning" })
        // Let transient Snackbar auto-dismiss before interacting with bottom action buttons
        composeTestRule.mainClock.advanceTimeBy(4_500L)
        composeTestRule.waitForIdle()

        // Gmail Tab: Import email as task
        composeTestRule.onNodeWithTag("workspace_hub_list")
            .performScrollToNode(hasTestTag("workspace_tab_gmail"))
        composeTestRule.onNodeWithTag("workspace_tab_gmail").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workspace_hub_list")
            .performScrollToNode(hasTestTag("workspace_import_email_mail_1"))
        composeTestRule.onNodeWithTag("workspace_import_email_mail_1").performClick()
        composeTestRule.waitForIdle()
        assertTrue(fakeRepo.tasksSnapshot().any { it.title == "Invoice approval needed" })
        composeTestRule.mainClock.advanceTimeBy(4_500L)
        composeTestRule.waitForIdle()

        // Chat Tab: Select second space & Share task to Chat
        composeTestRule.onNodeWithTag("workspace_hub_list")
            .performScrollToNode(hasTestTag("workspace_tab_chat"))
        composeTestRule.onNodeWithTag("workspace_tab_chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workspace_hub_list")
            .performScrollToNode(hasTestTag("workspace_chat_space_spaces/eng"))
        composeTestRule.onNodeWithTag("workspace_chat_space_spaces/eng").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workspace_hub_list")
            .performScrollToNode(hasTestTag("workspace_send_chat_${seededTask.id}"))
        composeTestRule.onNodeWithTag("workspace_send_chat_${seededTask.id}").performClick()
        composeTestRule.waitForIdle()
        assertEquals("spaces/eng" to seededTask.id, fakeWorkspaceApi.postedChatMessages.lastOrNull())

        // Disconnect & Navigate Back
        composeTestRule.onNodeWithTag("workspace_hub_list")
            .performScrollToNode(hasTestTag("workspace_disconnect_button"))
        composeTestRule.onNodeWithTag("workspace_disconnect_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workspace_connect_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("workspace_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").performScrollToIndex(0)
        composeTestRule.onNodeWithTag("home_greeting").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_persistsThemeAndReminderPreferencesInRealDataStoreAndTriggersSignOut() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val realDataStore = SettingsDataStore(context)
        val fakeRepo = E2EFakeTaskRepository()
        val fakeReminders = E2EFakeReminderScheduler()
        val onlineFlow = MutableStateFlow(true)
        var signOutTriggered = false

        val taskViewModel = createTaskViewModel(fakeRepo, realDataStore, fakeReminders, onlineFlow)
        val workspaceViewModel = WorkspaceViewModel(E2EFakeWorkspaceApiClient())
        val settingsViewModel = SettingsViewModel(realDataStore)

        composeTestRule.setContent {
            val settings by settingsViewModel.settings.collectAsState()
            SmartTodoTheme(themeMode = settings.themeMode) {
                AuthenticatedSmartTodoContent(
                    taskViewModel = taskViewModel,
                    workspaceViewModel = workspaceViewModel,
                    settingsViewModel = settingsViewModel,
                    settings = settings,
                    onSignOutConfirmed = { signOutTriggered = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Navigate to Settings tab
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_profile_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_profile_email").assertIsDisplayed()

        // Switch theme to Dark -> verify real DataStore persisted it
        composeTestRule.onNodeWithTag("settings_screen_list").performScrollToIndex(2)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("theme_option_dark").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(5_000L) {
            ShadowLooper.idleMainLooper()
            settingsViewModel.settings.value.themeMode == ThemeMode.DARK
        }
        val persistedDark = runBlocking { realDataStore.settingsFlow.first() }
        assertEquals(ThemeMode.DARK, persistedDark.themeMode)

        // Select 30 min reminder lead time -> verify real DataStore persisted it
        composeTestRule.onNodeWithTag("settings_screen_list").performScrollToIndex(3)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lead_time_30").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(5_000L) {
            ShadowLooper.idleMainLooper()
            settingsViewModel.settings.value.reminderLeadTime == ReminderLeadTime.THIRTY_MINUTES
        }
        val persistedLead = runBlocking { realDataStore.settingsFlow.first() }
        assertEquals(ReminderLeadTime.THIRTY_MINUTES, persistedLead.reminderLeadTime)

        // Tap Pin Widget button (safely shows guidance message on headless launcher)
        composeTestRule.onNodeWithTag("settings_screen_list")
            .performScrollToNode(hasTestTag("pin_widget_button"))
        composeTestRule.onNodeWithTag("pin_widget_button").performClick()
        composeTestRule.waitForIdle()

        // Sign out confirmation dialog -> triggers sign-out callback
        composeTestRule.onNodeWithTag("settings_screen_list")
            .performScrollToNode(hasTestTag("sign_out_button"))
        composeTestRule.onNodeWithTag("sign_out_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_sign_out_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(signOutTriggered)

        // Reset DataStore back to default System theme for test hygiene
        runBlocking {
            realDataStore.setThemeMode(ThemeMode.SYSTEM)
            realDataStore.setReminderLeadTime(ReminderLeadTime.FIFTEEN_MINUTES)
        }
    }

    @Test
    fun widgetDeepLinksAndNotificationReceiver_openTargetTaskInEditorCleanly() {
        val fakeRepo = E2EFakeTaskRepository()
        val fakeSettings = E2EFakeSettingsRepository()
        val fakeReminders = E2EFakeReminderScheduler()
        val onlineFlow = MutableStateFlow(true)
        val seeded = runBlocking {
            fakeRepo.createTask("u1", "Deep-linked task", "From widget", DateTimeUtils.todayUtcMillis(), "11:00", "High", "Work").getOrThrow()
        }

        val taskViewModel = createTaskViewModel(fakeRepo, fakeSettings, fakeReminders, onlineFlow)
        val workspaceViewModel = WorkspaceViewModel(E2EFakeWorkspaceApiClient())
        val settingsViewModel = SettingsViewModel(fakeSettings)
        var deepLinkState by mutableStateOf<WidgetDeepLink?>(WidgetDeepLink.EditTask(seeded.id, 1L))

        composeTestRule.setContent {
            val settings by settingsViewModel.settings.collectAsState()
            SmartTodoTheme(themeMode = settings.themeMode) {
                AuthenticatedSmartTodoContent(
                    taskViewModel = taskViewModel,
                    workspaceViewModel = workspaceViewModel,
                    settingsViewModel = settingsViewModel,
                    settings = settings,
                    pendingWidgetDeepLink = deepLinkState,
                    onConsumeWidgetDeepLink = { deepLinkState = null }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Deep link opened Edit Task screen pre-populated with seeded task
        assertNull(deepLinkState)
        assertTrue(composeTestRule.onAllNodesWithText("Deep-linked task").fetchSemanticsNodes().isNotEmpty())

        // Back returns to Home, then AddTask deep link opens New Task editor
        composeTestRule.onNodeWithTag("editor_back_button").performClick()
        composeTestRule.waitForIdle()
        deepLinkState = WidgetDeepLink.AddTask(2L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New Task").assertIsDisplayed()

        // TaskReminderReceiver handles broadcast safely without throwing
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = TaskReminderReceiver()
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra(AndroidTaskReminderScheduler.EXTRA_TASK_ID, seeded.id)
            putExtra(AndroidTaskReminderScheduler.EXTRA_TASK_TITLE, seeded.title)
            putExtra(AndroidTaskReminderScheduler.EXTRA_DUE_TIME, "11:00 AM")
            putExtra(AndroidTaskReminderScheduler.EXTRA_PRIORITY, "High")
        }
        receiver.onReceive(context, intent)
    }

    private fun createTaskViewModel(
        repo: TaskRepository,
        settingsRepo: SettingsRepository,
        reminders: ReminderScheduler,
        onlineFlow: MutableStateFlow<Boolean>
    ): TaskViewModel = TaskViewModel(
        repository = repo,
        settingsRepository = settingsRepo,
        reminderScheduler = reminders,
        connectivityObserver = object : ConnectivityObserver {
            override fun observeIsOnline(): Flow<Boolean> = onlineFlow
        },
        currentUserId = "u1",
        initialDisplayName = "Alex Rivera",
        initialEmail = "alex@example.com"
    )
}

private class E2EFakeTaskRepository : TaskRepository {
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())
    private val listsFlow = MutableStateFlow<List<TaskList>>(emptyList())
    private val historyFlow = MutableStateFlow<List<TaskHistoryEntry>>(emptyList())
    private val profileFlow = MutableStateFlow<UserProfile?>(null)
    private var seq = 1

    fun tasksSnapshot(): List<Task> = tasksFlow.value
    fun listsSnapshot(): List<TaskList> = listsFlow.value
    fun historySnapshot(): List<TaskHistoryEntry> = historyFlow.value

    override fun observeTasks(userId: String): Flow<List<Task>> = tasksFlow
    override fun observeProfile(userId: String): Flow<UserProfile?> = profileFlow
    override fun observeTaskLists(userId: String): Flow<List<TaskList>> = listsFlow
    override fun observeTaskHistory(userId: String): Flow<List<TaskHistoryEntry>> = historyFlow

    override suspend fun syncUserProfile(userId: String, name: String, email: String): Result<Unit> {
        profileFlow.value = UserProfile(userId = userId, name = name, email = email)
        return Result.success(Unit)
    }

    override suspend fun createTask(
        userId: String,
        title: String,
        description: String?,
        dueDate: Long?,
        dueTime: String?,
        priority: String,
        category: String,
        startDate: Long?,
        startTime: String?,
        deadline: Long?,
        listId: String?,
        isStarred: Boolean,
        recurrenceType: String,
        recurrenceInterval: Int,
        recurrenceUnit: String,
        subtasks: List<Subtask>,
        sortOrder: Int,
        calendarEventId: String?
    ): Result<Task> {
        val now = System.currentTimeMillis()
        val task = Task(
            id = "e2e_task_${seq++}",
            title = title.trim(),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            dueDate = dueDate,
            dueTime = dueTime?.trim()?.takeIf { it.isNotEmpty() },
            startDate = startDate,
            startTime = startTime,
            deadline = deadline,
            priority = priority,
            category = category,
            listId = listId,
            isCompleted = false,
            isStarred = isStarred,
            recurrenceType = recurrenceType,
            recurrenceInterval = recurrenceInterval,
            recurrenceUnit = recurrenceUnit,
            subtasks = subtasks,
            sortOrder = sortOrder,
            calendarEventId = calendarEventId,
            createdAt = now,
            updatedAt = now
        )
        tasksFlow.value = tasksFlow.value + task
        return Result.success(task)
    }

    override suspend fun updateTask(
        userId: String,
        taskId: String,
        title: String,
        description: String?,
        dueDate: Long?,
        dueTime: String?,
        priority: String,
        category: String,
        isCompleted: Boolean,
        startDate: Long?,
        startTime: String?,
        deadline: Long?,
        listId: String?,
        isStarred: Boolean,
        recurrenceType: String,
        recurrenceInterval: Int,
        recurrenceUnit: String,
        subtasks: List<Subtask>,
        sortOrder: Int,
        calendarEventId: String?
    ): Result<Unit> {
        tasksFlow.value = tasksFlow.value.map { existing ->
            if (existing.id == taskId) {
                existing.copy(
                    title = title.trim(),
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    dueDate = dueDate,
                    dueTime = dueTime,
                    startDate = startDate,
                    startTime = startTime,
                    deadline = deadline,
                    priority = priority,
                    category = category,
                    listId = listId,
                    isCompleted = isCompleted,
                    isStarred = isStarred,
                    recurrenceType = recurrenceType,
                    recurrenceInterval = recurrenceInterval,
                    recurrenceUnit = recurrenceUnit,
                    subtasks = subtasks,
                    sortOrder = sortOrder,
                    calendarEventId = calendarEventId,
                    updatedAt = System.currentTimeMillis()
                )
            } else existing
        }
        return Result.success(Unit)
    }

    override suspend fun setTaskCompleted(userId: String, taskId: String, isCompleted: Boolean): Result<Unit> {
        tasksFlow.value = tasksFlow.value.map { if (it.id == taskId) it.copy(isCompleted = isCompleted) else it }
        return Result.success(Unit)
    }

    override suspend fun setTaskStarred(userId: String, taskId: String, isStarred: Boolean): Result<Unit> {
        tasksFlow.value = tasksFlow.value.map { if (it.id == taskId) it.copy(isStarred = isStarred) else it }
        return Result.success(Unit)
    }

    override suspend fun updateTaskSubtasks(userId: String, taskId: String, subtasks: List<Subtask>): Result<Unit> {
        tasksFlow.value = tasksFlow.value.map { if (it.id == taskId) it.copy(subtasks = subtasks) else it }
        return Result.success(Unit)
    }

    override suspend fun reorderTasks(userId: String, orderedTaskIds: List<String>): Result<Unit> {
        val indexById = orderedTaskIds.withIndex().associate { it.value to it.index }
        tasksFlow.value = tasksFlow.value.map { task ->
            indexById[task.id]?.let { task.copy(sortOrder = it) } ?: task
        }
        return Result.success(Unit)
    }

    override suspend fun setTaskCalendarEventId(userId: String, taskId: String, calendarEventId: String?): Result<Unit> {
        tasksFlow.value = tasksFlow.value.map { if (it.id == taskId) it.copy(calendarEventId = calendarEventId) else it }
        return Result.success(Unit)
    }

    override suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        tasksFlow.value = tasksFlow.value.filterNot { it.id == taskId }
        return Result.success(Unit)
    }

    override suspend fun createTaskList(userId: String, name: String, colorHex: String): Result<TaskList> {
        val list = TaskList(id = "e2e_list_${seq++}", name = name.trim(), colorHex = colorHex)
        listsFlow.value = listsFlow.value + list
        return Result.success(list)
    }

    override suspend fun renameTaskList(userId: String, listId: String, newName: String, colorHex: String): Result<Unit> {
        listsFlow.value = listsFlow.value.map { if (it.id == listId) it.copy(name = newName.trim(), colorHex = colorHex) else it }
        return Result.success(Unit)
    }

    override suspend fun deleteTaskList(userId: String, listId: String): Result<Unit> {
        listsFlow.value = listsFlow.value.filterNot { it.id == listId }
        tasksFlow.value = tasksFlow.value.map { if (it.listId == listId) it.copy(listId = null) else it }
        return Result.success(Unit)
    }

    override suspend fun recordTaskHistory(
        userId: String,
        taskId: String,
        taskTitle: String,
        action: TaskHistoryAction,
        details: String?
    ): Result<Unit> {
        val entry = TaskHistoryEntry(
            id = "e2e_hist_${seq++}",
            taskId = taskId,
            taskTitle = taskTitle,
            action = action.displayName,
            details = details,
            timestamp = System.currentTimeMillis()
        )
        historyFlow.value = listOf(entry) + historyFlow.value
        return Result.success(Unit)
    }

    override suspend fun clearTaskHistory(userId: String): Result<Unit> {
        historyFlow.value = emptyList()
        return Result.success(Unit)
    }
}

private class E2EFakeSettingsRepository : SettingsRepository {
    private val _flow = MutableStateFlow(AppSettings())
    override val settingsFlow: Flow<AppSettings> = _flow
    override suspend fun setThemeMode(mode: ThemeMode) { _flow.value = _flow.value.copy(themeMode = mode) }
    override suspend fun setNotificationsEnabled(enabled: Boolean) { _flow.value = _flow.value.copy(notificationsEnabled = enabled) }
    override suspend fun setReminderLeadTime(leadTime: ReminderLeadTime) { _flow.value = _flow.value.copy(reminderLeadTime = leadTime) }
}

private class E2EFakeReminderScheduler : ReminderScheduler {
    override fun scheduleReminder(task: Task, leadTime: ReminderLeadTime) = Unit
    override fun cancelReminder(taskId: String) = Unit
    override fun syncAllReminders(tasks: List<Task>, notificationsEnabled: Boolean, leadTime: ReminderLeadTime) = Unit
}

private class E2EFakeWorkspaceApiClient : WorkspaceApiClient {
    val postedChatMessages = mutableListOf<Pair<String, String>>()

    override suspend fun syncTaskToCalendar(accessToken: String, task: Task): Result<CalendarSyncResult> =
        Result.success(CalendarSyncResult(eventId = "cal_evt_synced_1", htmlLink = "https://calendar.google.com/event"))

    override suspend fun fetchUpcomingCalendarEvents(accessToken: String): Result<List<WorkspaceCalendarEvent>> =
        Result.success(
            listOf(
                WorkspaceCalendarEvent(
                    id = "evt_1",
                    summary = "Quarterly Planning",
                    description = "Align Q4 OKRs",
                    startDateUtcMillis = DateTimeUtils.todayUtcMillis(),
                    startTime = "15:00",
                    displayTimeRange = "Today • 3:00 PM",
                    htmlLink = null
                )
            )
        )

    override suspend fun fetchRecentGmailMessages(accessToken: String): Result<List<WorkspaceEmailItem>> =
        Result.success(
            listOf(
                WorkspaceEmailItem(
                    id = "mail_1",
                    subject = "Invoice approval needed",
                    from = "Finance Team",
                    date = "Mon, 19 Nov",
                    snippet = "Please approve Q4 vendor invoice #421."
                )
            )
        )

    override suspend fun fetchChatSpaces(accessToken: String): Result<List<WorkspaceChatSpace>> =
        Result.success(
            listOf(
                WorkspaceChatSpace(name = "spaces/general", displayName = "General", type = "SPACE"),
                WorkspaceChatSpace(name = "spaces/eng", displayName = "Engineering", type = "SPACE")
            )
        )

    override suspend fun postTaskToChatSpace(accessToken: String, spaceName: String, task: Task): Result<String> {
        postedChatMessages.add(spaceName to task.id)
        return Result.success("spaces/eng/messages/1")
    }
}
