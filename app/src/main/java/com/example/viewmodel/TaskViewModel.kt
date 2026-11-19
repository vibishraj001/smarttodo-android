package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppSettings
import com.example.data.SettingsRepository
import com.example.data.toUserFriendlyFirestoreMessage
import com.example.domain.RecurrenceCalculator
import com.example.domain.TaskFilterSorter
import com.example.domain.model.RecurrenceType
import com.example.domain.model.RecurrenceUnit
import com.example.domain.model.Subtask
import com.example.domain.model.Task
import com.example.domain.model.TaskCategory
import com.example.domain.model.TaskHistoryAction
import com.example.domain.model.TaskHistoryEntry
import com.example.domain.model.TaskList
import com.example.domain.model.TaskPriority
import com.example.model.DashboardStats
import com.example.model.TaskFilter
import com.example.model.TaskSortOption
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.notifications.ReminderScheduler
import com.example.repository.TaskRepository
import com.example.utils.ConnectivityObserver
import com.example.utils.DateTimeUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TaskViewModel(
    private val repository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val connectivityObserver: ConnectivityObserver,
    private val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String,
    private val onTasksSynced: (String, List<Task>) -> Unit = { _, _ -> },
    private val onSignOutClearWidget: () -> Unit = {}
) : ViewModel() {

    private val sessionVersion = MutableStateFlow(1)

    private val _selectedFilter = MutableStateFlow(TaskFilter.ALL)
    val selectedFilter: StateFlow<TaskFilter> = _selectedFilter.asStateFlow()

    private val _selectedSort = MutableStateFlow(TaskSortOption.DUE_DATE)
    val selectedSort: StateFlow<TaskSortOption> = _selectedSort.asStateFlow()

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _completedSearchQuery = MutableStateFlow("")
    val completedSearchQuery: StateFlow<String> = _completedSearchQuery.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    val isOnline: StateFlow<Boolean> = connectivityObserver.observeIsOnline()
        .catch { emit(true) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = true
        )

    private val appSettings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AppSettings()
        )

    val tasksState: StateFlow<UiState<List<Task>>> = sessionVersion
        .flatMapLatest { version ->
            if (version <= 0) {
                flowOf(UiState.Loading)
            } else {
                repository.observeTasks(currentUserId)
                    .map<List<Task>, UiState<List<Task>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = unwrapFirestoreError(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing tasks", rootError)
                        emit(
                            UiState.Error(
                                toUserFriendlyFirestoreMessage(
                                    rootError,
                                    "Failed to load tasks. Please try again."
                                )
                            )
                        )
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val taskLists: StateFlow<List<TaskList>> = sessionVersion
        .flatMapLatest { version ->
            if (version <= 0) {
                flowOf(emptyList())
            } else {
                repository.observeTaskLists(currentUserId)
                    .catch { error ->
                        val firestoreError = unwrapFirestoreError(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing task lists", firestoreError ?: error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val taskHistoryState: StateFlow<UiState<List<TaskHistoryEntry>>> = sessionVersion
        .flatMapLatest { version ->
            if (version <= 0) {
                flowOf(UiState.Loading)
            } else {
                repository.observeTaskHistory(currentUserId)
                    .map<List<TaskHistoryEntry>, UiState<List<TaskHistoryEntry>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = unwrapFirestoreError(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(
                            UiState.Error(
                                toUserFriendlyFirestoreMessage(
                                    firestoreError ?: error,
                                    "Failed to load task history."
                                )
                            )
                        )
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val fallbackProfile = UserProfile(
        userId = currentUserId,
        name = initialDisplayName.ifBlank { "SmartTodo User" },
        email = initialEmail
    )

    val userProfile: StateFlow<UserProfile> = sessionVersion
        .flatMapLatest { version ->
            if (version <= 0) {
                flowOf(fallbackProfile)
            } else {
                repository.observeProfile(currentUserId)
                    .map { remote -> remote ?: fallbackProfile }
                    .catch { error ->
                        val firestoreError = unwrapFirestoreError(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(fallbackProfile)
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = fallbackProfile
        )

    val dashboardStats: StateFlow<DashboardStats> = tasksState
        .map { state ->
            when (state) {
                is UiState.Success -> TaskFilterSorter.computeDashboardStats(state.data)
                else -> DashboardStats()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DashboardStats()
        )

    val filteredTasks: StateFlow<UiState<List<Task>>> = combine(
        tasksState,
        selectedFilter,
        selectedSort,
        searchQuery,
        selectedListId
    ) { state, filter, sort, query, listId ->
        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> state
            is UiState.Success -> UiState.Success(
                TaskFilterSorter.filterAndSort(
                    tasks = state.data,
                    filter = filter,
                    sortOption = sort,
                    searchQuery = query,
                    selectedListId = listId
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    val completedTasks: StateFlow<UiState<List<Task>>> = combine(
        tasksState,
        completedSearchQuery
    ) { state, query ->
        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> state
            is UiState.Success -> UiState.Success(
                TaskFilterSorter.filterAndSort(
                    tasks = state.data,
                    filter = TaskFilter.COMPLETED,
                    sortOption = TaskSortOption.RECENTLY_CREATED,
                    searchQuery = query
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    private var profileSynced: Boolean = false

    init {
        viewModelScope.launch {
            syncProfileIfNeeded()
        }

        // Automatically retry initial profile sync once connectivity returns after being offline
        viewModelScope.launch {
            connectivityObserver.observeIsOnline()
                .catch { emit(true) }
                .distinctUntilChanged()
                .drop(1)
                .collect { online ->
                    if (online && !profileSynced && sessionVersion.value > 0) {
                        syncProfileIfNeeded()
                    }
                }
        }

        viewModelScope.launch {
            combine(tasksState, appSettings) { state, settings ->
                if (state is UiState.Success) state.data to settings else null
            }.collect { pair ->
                if (pair != null) {
                    val (tasks, settings) = pair
                    onTasksSynced(currentUserId, tasks)
                    reminderScheduler.syncAllReminders(
                        tasks = tasks,
                        notificationsEnabled = settings.notificationsEnabled,
                        leadTime = settings.reminderLeadTime
                    )
                }
            }
        }
    }

    private suspend fun syncProfileIfNeeded() {
        val result = repository.syncUserProfile(
            userId = currentUserId,
            name = initialDisplayName.ifBlank { "SmartTodo User" },
            email = initialEmail
        )
        if (result.isSuccess) {
            profileSynced = true
        }
    }

    fun findTaskById(taskId: String): Task? {
        val state = tasksState.value
        return if (state is UiState.Success) {
            state.data.firstOrNull { it.id == taskId }
        } else {
            null
        }
    }

    fun setFilter(filter: TaskFilter) {
        _selectedFilter.value = filter
    }

    fun setSortOption(sortOption: TaskSortOption) {
        _selectedSort.value = sortOption
    }

    fun selectList(listId: String?) {
        _selectedListId.value = listId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCompletedSearchQuery(query: String) {
        _completedSearchQuery.value = query
    }

    fun retryObserving() {
        val current = sessionVersion.value
        sessionVersion.value = if (current <= 0) 1 else current + 1
    }

    fun ensureSessionActive() {
        if (sessionVersion.value <= 0) {
            sessionVersion.value = 1
            viewModelScope.launch {
                syncProfileIfNeeded()
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun quickCreateTask(
        title: String,
        dueToday: Boolean = true,
        isStarred: Boolean = false,
        listId: String? = _selectedListId.value
    ) {
        val clean = title.trim()
        if (clean.isEmpty()) {
            _snackbarMessage.value = "Enter a task title first"
            return
        }
        createTask(
            title = clean,
            description = null,
            dueDate = if (dueToday) DateTimeUtils.todayUtcMillis() else null,
            dueTime = null,
            priority = TaskPriority.MEDIUM,
            category = TaskCategory.PERSONAL,
            listId = listId,
            isStarred = isStarred
        )
    }

    fun createTask(
        title: String,
        description: String?,
        dueDate: Long?,
        dueTime: String?,
        priority: TaskPriority,
        category: TaskCategory,
        startDate: Long? = null,
        startTime: String? = null,
        deadline: Long? = null,
        listId: String? = _selectedListId.value,
        isStarred: Boolean = false,
        recurrenceType: RecurrenceType = RecurrenceType.NONE,
        recurrenceInterval: Int = 1,
        recurrenceUnit: RecurrenceUnit = RecurrenceUnit.DAYS,
        subtasks: List<Subtask> = emptyList(),
        historyAction: TaskHistoryAction = TaskHistoryAction.CREATED,
        historyDetails: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            val nextSortOrder = ((tasksState.value as? UiState.Success)?.data?.maxOfOrNull { it.sortOrder } ?: -1) + 1
            val result = repository.createTask(
                userId = currentUserId,
                title = title,
                description = description,
                dueDate = dueDate,
                dueTime = dueTime,
                priority = priority.displayName,
                category = category.displayName,
                startDate = startDate,
                startTime = startTime,
                deadline = deadline,
                listId = listId,
                isStarred = isStarred,
                recurrenceType = recurrenceType.displayName,
                recurrenceInterval = recurrenceInterval,
                recurrenceUnit = recurrenceUnit.displayName,
                subtasks = subtasks,
                sortOrder = nextSortOrder
            )
            _isSaving.value = false
            result.fold(
                onSuccess = { createdTask ->
                    val settings = appSettings.value
                    if (settings.notificationsEnabled &&
                        createdTask.dueDate != null &&
                        !createdTask.dueTime.isNullOrBlank()
                    ) {
                        reminderScheduler.scheduleReminder(createdTask, settings.reminderLeadTime)
                    }
                    repository.recordTaskHistory(
                        userId = currentUserId,
                        taskId = createdTask.id,
                        taskTitle = createdTask.title,
                        action = historyAction,
                        details = historyDetails ?: buildCreateHistoryDetails(createdTask)
                    )
                    _snackbarMessage.value = if (isOnline.value) {
                        "Task created"
                    } else {
                        "Task saved offline • Will sync when online"
                    }
                    onComplete(true)
                },
                onFailure = { error ->
                    _snackbarMessage.value = toUserFriendlyFirestoreMessage(
                        error,
                        "Could not save task. Please try again."
                    )
                    onComplete(false)
                }
            )
        }
    }

    fun updateTask(
        taskId: String,
        title: String,
        description: String?,
        dueDate: Long?,
        dueTime: String?,
        priority: TaskPriority,
        category: TaskCategory,
        isCompleted: Boolean,
        startDate: Long? = null,
        startTime: String? = null,
        deadline: Long? = null,
        listId: String? = null,
        isStarred: Boolean = false,
        recurrenceType: RecurrenceType = RecurrenceType.NONE,
        recurrenceInterval: Int = 1,
        recurrenceUnit: RecurrenceUnit = RecurrenceUnit.DAYS,
        subtasks: List<Subtask> = emptyList(),
        sortOrder: Int = 0,
        calendarEventId: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateTask(
                userId = currentUserId,
                taskId = taskId,
                title = title,
                description = description,
                dueDate = dueDate,
                dueTime = dueTime,
                priority = priority.displayName,
                category = category.displayName,
                isCompleted = isCompleted,
                startDate = startDate,
                startTime = startTime,
                deadline = deadline,
                listId = listId,
                isStarred = isStarred,
                recurrenceType = recurrenceType.displayName,
                recurrenceInterval = recurrenceInterval,
                recurrenceUnit = recurrenceUnit.displayName,
                subtasks = subtasks,
                sortOrder = sortOrder,
                calendarEventId = calendarEventId
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    val settings = appSettings.value
                    if (!settings.notificationsEnabled || isCompleted || dueDate == null || dueTime.isNullOrBlank()) {
                        reminderScheduler.cancelReminder(taskId)
                    } else {
                        reminderScheduler.scheduleReminder(
                            Task(
                                id = taskId,
                                title = title.trim(),
                                description = description?.trim()?.takeIf { it.isNotEmpty() },
                                dueDate = dueDate,
                                dueTime = dueTime.trim(),
                                priority = priority.displayName,
                                category = category.displayName,
                                isCompleted = isCompleted
                            ),
                            settings.reminderLeadTime
                        )
                    }
                    repository.recordTaskHistory(
                        userId = currentUserId,
                        taskId = taskId,
                        taskTitle = title.trim(),
                        action = TaskHistoryAction.UPDATED,
                        details = "Priority: ${priority.displayName} • ${subtasks.size} subtasks"
                    )
                    _snackbarMessage.value = if (isOnline.value) {
                        "Task updated"
                    } else {
                        "Changes saved offline • Will sync when online"
                    }
                    onComplete(true)
                },
                onFailure = { error ->
                    _snackbarMessage.value = toUserFriendlyFirestoreMessage(
                        error,
                        "Could not update task. Please try again."
                    )
                    onComplete(false)
                }
            )
        }
    }

    fun toggleTaskCompletion(task: Task) {
        val targetCompleted = !task.isCompleted
        viewModelScope.launch {
            val result = repository.setTaskCompleted(
                userId = currentUserId,
                taskId = task.id,
                isCompleted = targetCompleted
            )
            result.fold(
                onSuccess = {
                    if (targetCompleted) {
                        reminderScheduler.cancelReminder(task.id)
                        repository.recordTaskHistory(
                            userId = currentUserId,
                            taskId = task.id,
                            taskTitle = task.title,
                            action = TaskHistoryAction.COMPLETED
                        )
                        // Automatically spawn next occurrence for recurring tasks
                        val nextOccurrence = RecurrenceCalculator.buildNextOccurrence(task)
                        if (nextOccurrence != null) {
                            val nextRes = repository.createTask(
                                userId = currentUserId,
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
                            )
                            nextRes.onSuccess { spawned ->
                                val settings = appSettings.value
                                if (settings.notificationsEnabled &&
                                    spawned.dueDate != null &&
                                    !spawned.dueTime.isNullOrBlank()
                                ) {
                                    reminderScheduler.scheduleReminder(spawned, settings.reminderLeadTime)
                                }
                                val nextLabel = DateTimeUtils.formatDisplayDate(spawned.dueDate)
                                repository.recordTaskHistory(
                                    userId = currentUserId,
                                    taskId = spawned.id,
                                    taskTitle = spawned.title,
                                    action = TaskHistoryAction.RECURRENCE_ADVANCED,
                                    details = "Next occurrence scheduled for $nextLabel"
                                )
                                _snackbarMessage.value = "Completed • Next occurrence set for $nextLabel"
                            }.onFailure {
                                _snackbarMessage.value = "Completed \"${task.title}\""
                            }
                        } else {
                            _snackbarMessage.value = "Completed \"${task.title}\""
                        }
                    } else {
                        val settings = appSettings.value
                        if (settings.notificationsEnabled &&
                            task.dueDate != null &&
                            !task.dueTime.isNullOrBlank()
                        ) {
                            reminderScheduler.scheduleReminder(
                                task.copy(isCompleted = false),
                                settings.reminderLeadTime
                            )
                        }
                        repository.recordTaskHistory(
                            userId = currentUserId,
                            taskId = task.id,
                            taskTitle = task.title,
                            action = TaskHistoryAction.REOPENED
                        )
                        _snackbarMessage.value = "Reopened \"${task.title}\""
                    }
                },
                onFailure = { error ->
                    _snackbarMessage.value = toUserFriendlyFirestoreMessage(
                        error,
                        "Could not update task status."
                    )
                }
            )
        }
    }

    fun toggleTaskStarred(task: Task) {
        val nextStarred = !task.isStarred
        viewModelScope.launch {
            repository.setTaskStarred(currentUserId, task.id, nextStarred).onFailure { error ->
                _snackbarMessage.value = toUserFriendlyFirestoreMessage(
                    error,
                    "Could not update star status."
                )
            }
        }
    }

    fun toggleSubtaskCompletion(task: Task, subtaskId: String) {
        val updatedSubtasks = task.subtasks.map { sub ->
            if (sub.id == subtaskId) sub.copy(isCompleted = !sub.isCompleted) else sub
        }
        viewModelScope.launch {
            repository.updateTaskSubtasks(currentUserId, task.id, updatedSubtasks).onFailure { error ->
                _snackbarMessage.value = toUserFriendlyFirestoreMessage(
                    error,
                    "Could not update subtask."
                )
            }
        }
    }

    fun reorderTasks(reorderedTasks: List<Task>) {
        if (reorderedTasks.isEmpty()) return
        _selectedSort.value = TaskSortOption.MANUAL_ORDER
        val reorderedIds = reorderedTasks.map { it.id }
        val fullList = (tasksState.value as? UiState.Success)?.data.orEmpty()
        val mergedIds = if (fullList.size > reorderedIds.size) {
            val subsetSet = reorderedIds.toSet()
            val sortedFull = TaskFilterSorter.sortTasks(fullList, TaskSortOption.MANUAL_ORDER)
            var subsetCursor = 0
            sortedFull.map { task ->
                if (task.id in subsetSet && subsetCursor < reorderedIds.size) {
                    reorderedIds[subsetCursor++]
                } else {
                    task.id
                }
            }
        } else {
            reorderedIds
        }
        viewModelScope.launch {
            repository.reorderTasks(currentUserId, mergedIds).onFailure { error ->
                _snackbarMessage.value = toUserFriendlyFirestoreMessage(
                    error,
                    "Could not save task order."
                )
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            val result = repository.deleteTask(currentUserId, task.id)
            result.fold(
                onSuccess = {
                    reminderScheduler.cancelReminder(task.id)
                    repository.recordTaskHistory(
                        userId = currentUserId,
                        taskId = task.id,
                        taskTitle = task.title,
                        action = TaskHistoryAction.DELETED
                    )
                    _snackbarMessage.value = "Deleted \"${task.title}\""
                },
                onFailure = { error ->
                    _snackbarMessage.value = toUserFriendlyFirestoreMessage(
                        error,
                        "Could not delete task."
                    )
                }
            )
        }
    }

    // --- Task Lists CRUD ---

    fun createTaskList(name: String, colorHex: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.createTaskList(currentUserId, name, colorHex).fold(
                onSuccess = { created ->
                    _selectedListId.value = created.id
                    _snackbarMessage.value = "Created list \"${created.name}\""
                    onComplete(true)
                },
                onFailure = { error ->
                    _snackbarMessage.value = toUserFriendlyFirestoreMessage(
                        error,
                        "Could not create list."
                    )
                    onComplete(false)
                }
            )
        }
    }

    fun renameTaskList(
        listId: String,
        newName: String,
        colorHex: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.renameTaskList(currentUserId, listId, newName, colorHex).fold(
                onSuccess = {
                    _snackbarMessage.value = "Renamed list to \"${newName.trim()}\""
                    onComplete(true)
                },
                onFailure = { error ->
                    _snackbarMessage.value = toUserFriendlyFirestoreMessage(
                        error,
                        "Could not rename list."
                    )
                    onComplete(false)
                }
            )
        }
    }

    fun deleteTaskList(list: TaskList) {
        viewModelScope.launch {
            repository.deleteTaskList(currentUserId, list.id).fold(
                onSuccess = {
                    if (_selectedListId.value == list.id) {
                        _selectedListId.value = null
                    }
                    _snackbarMessage.value = "Deleted list \"${list.name}\""
                },
                onFailure = { error ->
                    _snackbarMessage.value = toUserFriendlyFirestoreMessage(
                        error,
                        "Could not delete list."
                    )
                }
            )
        }
    }

    // --- Workspace Sync & History Helpers ---

    fun markTaskSyncedToCalendar(task: Task, calendarEventId: String) {
        viewModelScope.launch {
            repository.setTaskCalendarEventId(currentUserId, task.id, calendarEventId)
            repository.recordTaskHistory(
                userId = currentUserId,
                taskId = task.id,
                taskTitle = task.title,
                action = TaskHistoryAction.SYNCED_TO_CALENDAR,
                details = "Synced to Google Calendar"
            )
        }
    }

    fun recordWorkspaceHistory(
        taskId: String,
        taskTitle: String,
        action: TaskHistoryAction,
        details: String? = null
    ) {
        viewModelScope.launch {
            repository.recordTaskHistory(
                userId = currentUserId,
                taskId = taskId,
                taskTitle = taskTitle,
                action = action,
                details = details
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearTaskHistory(currentUserId).fold(
                onSuccess = { _snackbarMessage.value = "Task history cleared" },
                onFailure = { error ->
                    _snackbarMessage.value = toUserFriendlyFirestoreMessage(
                        error,
                        "Could not clear task history."
                    )
                }
            )
        }
    }

    fun prepareForSignOut() {
        val state = tasksState.value
        if (state is UiState.Success) {
            state.data.forEach { reminderScheduler.cancelReminder(it.id) }
        }
        onSignOutClearWidget()
        profileSynced = false
        _isSaving.value = false
        _selectedFilter.value = TaskFilter.ALL
        _selectedSort.value = TaskSortOption.DUE_DATE
        _selectedListId.value = null
        _searchQuery.value = ""
        _completedSearchQuery.value = ""
        _snackbarMessage.value = null
        sessionVersion.value = 0
    }

    private fun buildCreateHistoryDetails(task: Task): String {
        val parts = mutableListOf(task.priority)
        task.dueDate?.let { parts.add("Due ${DateTimeUtils.formatDisplayDate(it)}") }
        RecurrenceCalculator.formatRecurrenceLabel(
            task.recurrenceTypeEnum(),
            task.recurrenceInterval,
            task.recurrenceUnitEnum()
        )?.let { parts.add(it) }
        return parts.joinToString(" • ")
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "TaskViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
