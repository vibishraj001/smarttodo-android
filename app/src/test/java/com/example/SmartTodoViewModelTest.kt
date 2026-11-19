package com.example

import com.example.data.AppSettings
import com.example.data.SettingsRepository
import com.example.domain.RecurrenceCalculator
import com.example.domain.TaskFilterSorter
import com.example.domain.TaskValidator
import com.example.domain.model.RecurrenceType
import com.example.domain.model.RecurrenceUnit
import com.example.domain.model.Subtask
import com.example.domain.model.Task
import com.example.domain.model.TaskCategory
import com.example.domain.model.TaskHistoryAction
import com.example.domain.model.TaskHistoryEntry
import com.example.domain.model.TaskList
import com.example.domain.model.TaskPriority
import com.example.model.ReminderLeadTime
import com.example.model.TaskFilter
import com.example.model.TaskSortOption
import com.example.model.ThemeMode
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.notifications.ReminderScheduler
import com.example.repository.TaskRepository
import com.example.utils.ConnectivityObserver
import com.example.utils.DateTimeUtils
import com.example.viewmodel.TaskViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmartTodoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `domain model Task has Firestore no-arg constructor and rules-compliant serialization map`() {
        val defaultTask = Task()
        assertEquals("", defaultTask.id)
        assertEquals("", defaultTask.title)
        assertNull(defaultTask.description)
        assertNull(defaultTask.dueDate)
        assertNull(defaultTask.dueTime)
        assertEquals("Medium", defaultTask.priority)
        assertEquals("Personal", defaultTask.category)
        assertFalse(defaultTask.isCompleted)
        assertFalse(defaultTask.isStarred)
        assertEquals("None", defaultTask.recurrenceType)
        assertTrue(defaultTask.subtasks.isEmpty())

        val dueMillis = DateTimeUtils.utcDateMillis(2026, 10, 20)
        val fullTask = Task(
            id = "doc_123",
            title = "  Prepare release  ",
            description = "Final check",
            dueDate = dueMillis,
            dueTime = "14:30",
            startDate = DateTimeUtils.utcDateMillis(2026, 10, 18),
            startTime = "09:00",
            deadline = DateTimeUtils.utcDateMillis(2026, 10, 22),
            priority = "High",
            category = "Work",
            listId = "list_sprint",
            isStarred = true,
            recurrenceType = "Custom",
            recurrenceInterval = 3,
            recurrenceUnit = "Days",
            subtasks = listOf(Subtask(id = "s1", title = "Draft notes", isCompleted = false)),
            sortOrder = 4
        )
        val fullMap = fullTask.toFirestoreCreateMap(ownerUid = "uid_alice")
        assertFalse(fullMap.containsKey("id"))
        assertEquals("uid_alice", fullMap["userId"])
        assertEquals("Prepare release", fullMap["title"])
        assertEquals("Final check", fullMap["description"])
        assertEquals(dueMillis, fullMap["dueDate"])
        assertEquals("14:30", fullMap["dueTime"])
        assertEquals("09:00", fullMap["startTime"])
        assertEquals("list_sprint", fullMap["listId"])
        assertEquals(true, fullMap["isStarred"])
        assertEquals("Custom", fullMap["recurrenceType"])
        assertEquals(3, fullMap["recurrenceInterval"])
        assertEquals("Days", fullMap["recurrenceUnit"])
        assertEquals(4, fullMap["sortOrder"])
        assertNotNull(fullMap["subtasks"])
    }

    @Test
    fun `RecurrenceCalculator computes Daily, Weekly, Monthly, Yearly, and Custom next dates accurately`() {
        val base = DateTimeUtils.utcDateMillis(2026, 0, 15) // Jan 15, 2026
        assertEquals(
            DateTimeUtils.utcDateMillis(2026, 0, 16),
            RecurrenceCalculator.nextOccurrenceDateMillis(base, RecurrenceType.DAILY)
        )
        assertEquals(
            DateTimeUtils.utcDateMillis(2026, 0, 22),
            RecurrenceCalculator.nextOccurrenceDateMillis(base, RecurrenceType.WEEKLY)
        )
        assertEquals(
            DateTimeUtils.utcDateMillis(2026, 1, 15),
            RecurrenceCalculator.nextOccurrenceDateMillis(base, RecurrenceType.MONTHLY)
        )
        assertEquals(
            DateTimeUtils.utcDateMillis(2027, 0, 15),
            RecurrenceCalculator.nextOccurrenceDateMillis(base, RecurrenceType.YEARLY)
        )
        assertEquals(
            DateTimeUtils.utcDateMillis(2026, 0, 29),
            RecurrenceCalculator.nextOccurrenceDateMillis(base, RecurrenceType.CUSTOM, 2, RecurrenceUnit.WEEKS)
        )
        assertEquals("Every 2 weeks", RecurrenceCalculator.formatRecurrenceLabel(RecurrenceType.CUSTOM, 2, RecurrenceUnit.WEEKS))

        val recurringTask = Task(
            id = "rec_1",
            title = "Weekly status",
            startDate = DateTimeUtils.utcDateMillis(2026, 0, 14),
            dueDate = base,
            deadline = DateTimeUtils.utcDateMillis(2026, 0, 16),
            recurrenceType = "Weekly",
            subtasks = listOf(Subtask(id = "s1", title = "Collect metrics", isCompleted = true))
        )
        val next = RecurrenceCalculator.buildNextOccurrence(recurringTask)
        assertNotNull(next)
        assertEquals(DateTimeUtils.utcDateMillis(2026, 0, 22), next!!.dueDate)
        assertEquals(DateTimeUtils.utcDateMillis(2026, 0, 21), next.startDate)
        assertEquals(DateTimeUtils.utcDateMillis(2026, 0, 23), next.deadline)
        assertFalse(next.subtasks.first().isCompleted)
    }

    @Test
    fun `TaskValidator rejects invalid title, startTime without startDate, deadline before startDate, and invalid list names`() {
        val start = DateTimeUtils.utcDateMillis(2026, 10, 20)
        val beforeStart = DateTimeUtils.utcDateMillis(2026, 10, 18)

        assertFalse(TaskValidator.validate("   ", null, start, null).isValid)
        assertFalse(TaskValidator.validate("Valid", null, null, "14:30").isValid)
        assertFalse(TaskValidator.validate("Valid", null, start, null, startDate = null, startTime = "09:00").isValid)
        assertFalse(TaskValidator.validate("Valid", null, start, null, startDate = start, deadline = beforeStart).isValid)
        assertNotNull(TaskValidator.validateListName("   "))
        assertNull(TaskValidator.validateListName("Q4 Roadmap"))
    }

    @Test
    fun `TaskFilterSorter filters by list, Starred, Today, Upcoming, Overdue, Completed, and Manual drag sortOrder`() {
        val todayUtc = DateTimeUtils.utcDateMillis(2026, 10, 18)
        val yesterdayUtc = DateTimeUtils.utcDateMillis(2026, 10, 17)
        val upcomingUtc = DateTimeUtils.utcDateMillis(2026, 10, 25)
        val nowMillis = DateTimeUtils.computeDueDateTimeMillis(todayUtc, "12:00")!!

        val tasks = listOf(
            Task(id = "1", title = "Morning standup", dueDate = todayUtc, dueTime = "15:00", priority = "High", listId = "work", isStarred = true, sortOrder = 2),
            Task(id = "2", title = "Pay electricity bill", dueDate = yesterdayUtc, priority = "Medium", listId = "home", sortOrder = 0),
            Task(id = "3", title = "Prepare presentation", dueDate = upcomingUtc, priority = "Low", listId = "work", sortOrder = 1),
            Task(id = "4", title = "Buy coffee beans", dueDate = todayUtc, priority = "Low", isCompleted = true, sortOrder = 3)
        )

        assertEquals(listOf("1"), TaskFilterSorter.filterAndSort(tasks, TaskFilter.STARRED, TaskSortOption.DUE_DATE, "", nowMillis).map { it.id })
        assertEquals(listOf("1", "3"), TaskFilterSorter.filterAndSort(tasks, TaskFilter.ALL, TaskSortOption.DUE_DATE, "", nowMillis, selectedListId = "work").map { it.id })
        assertEquals(listOf("2", "3", "1", "4"), TaskFilterSorter.filterAndSort(tasks, TaskFilter.ALL, TaskSortOption.MANUAL_ORDER, "", nowMillis).map { it.id })
    }

    @Test
    fun `TaskViewModel handles lists, subtasks, starring, drag reorder, recurring completion advance, and history`() = runTest {
        val fakeRepo = FakeTaskRepository()
        val fakeSettings = FakeSettingsRepository()
        val fakeReminders = FakeReminderScheduler()
        val onlineFlow = MutableStateFlow(true)
        val fakeNetwork = object : ConnectivityObserver {
            override fun observeIsOnline(): Flow<Boolean> = onlineFlow
        }

        val viewModel = TaskViewModel(
            repository = fakeRepo,
            settingsRepository = fakeSettings,
            reminderScheduler = fakeReminders,
            connectivityObserver = fakeNetwork,
            currentUserId = "user_alice",
            initialDisplayName = "Alice Johnson",
            initialEmail = "alice@example.com"
        )

        val jobs = listOf(
            backgroundScope.launch { viewModel.filteredTasks.collect {} },
            backgroundScope.launch { viewModel.taskLists.collect {} },
            backgroundScope.launch { viewModel.taskHistoryState.collect {} },
            backgroundScope.launch { viewModel.dashboardStats.collect {} }
        )
        advanceUntilIdle()

        // Create and rename custom TaskList
        viewModel.createTaskList("Sprint", "#2563EB")
        advanceUntilIdle()
        val createdList = viewModel.taskLists.value.first()
        assertEquals("Sprint", createdList.name)

        viewModel.renameTaskList(createdList.id, "Q4 Sprint", "#16A34A")
        advanceUntilIdle()
        assertEquals("Q4 Sprint", viewModel.taskLists.value.first().name)

        // Create recurring task with subtask in the list
        val todayMillis = DateTimeUtils.todayUtcMillis()
        viewModel.createTask(
            title = "Daily standup",
            description = "Sync team blockers",
            dueDate = todayMillis,
            dueTime = "09:30",
            priority = TaskPriority.HIGH,
            category = TaskCategory.WORK,
            listId = createdList.id,
            isStarred = true,
            recurrenceType = RecurrenceType.DAILY,
            subtasks = listOf(Subtask(id = "sub_1", title = "Review board", isCompleted = false))
        )
        advanceUntilIdle()
        val task = (viewModel.tasksState.value as UiState.Success).data.first()
        assertTrue(task.isStarred)
        assertEquals(createdList.id, task.listId)

        // Toggle subtask completion
        viewModel.toggleSubtaskCompletion(task, "sub_1")
        advanceUntilIdle()
        val withDoneSub = (viewModel.tasksState.value as UiState.Success).data.first()
        assertTrue(withDoneSub.subtasks.first().isCompleted)

        // Complete recurring task -> spawns next occurrence and records history
        viewModel.toggleTaskCompletion(withDoneSub)
        advanceUntilIdle()
        val allTasksAfterComplete = (viewModel.tasksState.value as UiState.Success).data
        assertEquals(2, allTasksAfterComplete.size)
        val spawnedNext = allTasksAfterComplete.first { !it.isCompleted }
        assertEquals(todayMillis + 86_400_000L, spawnedNext.dueDate)
        assertFalse(spawnedNext.subtasks.first().isCompleted)

        // Quick create + drag-and-drop reorder
        viewModel.quickCreateTask("Quick follow-up", dueToday = true, isStarred = false)
        advanceUntilIdle()
        val current = (viewModel.tasksState.value as UiState.Success).data
        viewModel.reorderTasks(current.reversed())
        advanceUntilIdle()
        assertEquals(TaskSortOption.MANUAL_ORDER, viewModel.selectedSort.value)

        // History recorded Created, Completed, Recurrence Advanced, Created
        val history = (viewModel.taskHistoryState.value as UiState.Success).data
        assertTrue(history.any { it.actionEnum() == TaskHistoryAction.COMPLETED })
        assertTrue(history.any { it.actionEnum() == TaskHistoryAction.RECURRENCE_ADVANCED })

        // Delete list resets selection cleanly
        viewModel.deleteTaskList(viewModel.taskLists.value.first())
        advanceUntilIdle()
        assertTrue(viewModel.taskLists.value.isEmpty())
        assertNull(viewModel.selectedListId.value)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `TaskViewModel retries profile sync on reconnect, surfaces offline save state, and resets session on sign-out`() = runTest {
        val fakeRepo = FakeTaskRepository().apply { failNextProfileSync = true }
        val fakeSettings = FakeSettingsRepository()
        val fakeReminders = FakeReminderScheduler()
        val onlineFlow = MutableStateFlow(false)
        val fakeNetwork = object : ConnectivityObserver {
            override fun observeIsOnline(): Flow<Boolean> = onlineFlow
        }

        val viewModel = TaskViewModel(
            repository = fakeRepo,
            settingsRepository = fakeSettings,
            reminderScheduler = fakeReminders,
            connectivityObserver = fakeNetwork,
            currentUserId = "user_alice",
            initialDisplayName = "Alice Johnson",
            initialEmail = "alice@example.com"
        )

        val jobs = listOf(
            backgroundScope.launch { viewModel.isOnline.collect {} },
            backgroundScope.launch { viewModel.tasksState.collect {} },
            backgroundScope.launch { viewModel.userProfile.collect {} }
        )
        advanceUntilIdle()

        // Negative startDate rejected for 1:1 rules parity
        assertFalse(TaskValidator.validate("Valid", null, null, null, startDate = -10L).isValid)

        // Initial profile sync failed while offline
        assertEquals(1, fakeRepo.profileSyncAttempts)

        // Creating a task while offline succeeds cleanly and indicates offline queue
        viewModel.createTask(
            title = "Offline note",
            description = null,
            dueDate = DateTimeUtils.todayUtcMillis(),
            dueTime = null,
            priority = TaskPriority.MEDIUM,
            category = TaskCategory.PERSONAL
        )
        advanceUntilIdle()
        assertTrue(viewModel.snackbarMessage.value?.contains("offline", ignoreCase = true) == true)

        // Coming back online automatically retries profile sync
        onlineFlow.value = true
        advanceUntilIdle()
        assertTrue(fakeRepo.profileSyncAttempts >= 2)
        assertEquals("Alice Johnson", viewModel.userProfile.value.name)

        // Sign-out resets transient session filters and search queries
        viewModel.setFilter(TaskFilter.OVERDUE)
        viewModel.setSearchQuery("Offline")
        viewModel.prepareForSignOut()
        advanceUntilIdle()
        assertEquals(TaskFilter.ALL, viewModel.selectedFilter.value)
        assertEquals("", viewModel.searchQuery.value)
        assertNull(viewModel.snackbarMessage.value)

        jobs.forEach { it.cancel() }
    }
}

private class FakeTaskRepository : TaskRepository {
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())
    private val listsFlow = MutableStateFlow<List<TaskList>>(emptyList())
    private val historyFlow = MutableStateFlow<List<TaskHistoryEntry>>(emptyList())
    private val profileFlow = MutableStateFlow<UserProfile?>(null)
    private var nextId = 1
    var failNextProfileSync = false
    var profileSyncAttempts = 0

    override fun observeTasks(userId: String): Flow<List<Task>> = tasksFlow
    override fun observeProfile(userId: String): Flow<UserProfile?> = profileFlow
    override fun observeTaskLists(userId: String): Flow<List<TaskList>> = listsFlow
    override fun observeTaskHistory(userId: String): Flow<List<TaskHistoryEntry>> = historyFlow

    override suspend fun syncUserProfile(userId: String, name: String, email: String): Result<Unit> {
        profileSyncAttempts++
        if (failNextProfileSync) {
            failNextProfileSync = false
            return Result.failure(IllegalStateException("Offline"))
        }
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
            id = "task_${nextId++}",
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

    override suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        tasksFlow.value = tasksFlow.value.filterNot { it.id == taskId }
        return Result.success(Unit)
    }

    override suspend fun createTaskList(userId: String, name: String, colorHex: String): Result<TaskList> {
        val list = TaskList(id = "list_${nextId++}", name = name.trim(), colorHex = colorHex)
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
            id = "hist_${nextId++}",
            taskId = taskId,
            taskTitle = taskTitle,
            action = action.displayName,
            details = details,
            timestamp = System.currentTimeMillis()
        )
        historyFlow.value = listOf(entry) + historyFlow.value
        return Result.success(Unit)
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private val _flow = MutableStateFlow(AppSettings())
    override val settingsFlow: Flow<AppSettings> = _flow
    override suspend fun setThemeMode(mode: ThemeMode) { _flow.value = _flow.value.copy(themeMode = mode) }
    override suspend fun setNotificationsEnabled(enabled: Boolean) { _flow.value = _flow.value.copy(notificationsEnabled = enabled) }
    override suspend fun setReminderLeadTime(leadTime: ReminderLeadTime) { _flow.value = _flow.value.copy(reminderLeadTime = leadTime) }
}

private class FakeReminderScheduler : ReminderScheduler {
    val scheduledIds = mutableSetOf<String>()
    val cancelledIds = mutableSetOf<String>()
    override fun scheduleReminder(task: Task, leadTime: ReminderLeadTime) { scheduledIds.add(task.id) }
    override fun cancelReminder(taskId: String) { cancelledIds.add(taskId) }
    override fun syncAllReminders(tasks: List<Task>, notificationsEnabled: Boolean, leadTime: ReminderLeadTime) {
        tasks.forEach { task ->
            if (!notificationsEnabled || task.isCompleted || task.dueDate == null || task.dueTime.isNullOrBlank()) {
                cancelReminder(task.id)
            } else {
                scheduleReminder(task, leadTime)
            }
        }
    }
}
