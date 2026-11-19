package com.example.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.authentication.GoogleAuthManager
import com.example.authentication.authStateFlow
import com.example.data.AppSettings
import com.example.data.SettingsDataStore
import com.example.domain.model.TaskCategory
import com.example.domain.model.TaskHistoryAction
import com.example.domain.model.TaskPriority
import com.example.model.UiState
import com.example.notifications.AndroidTaskReminderScheduler
import com.example.repository.FirestoreTaskRepository
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingStateView
import com.example.ui.components.OfflineStatusBanner
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CompletedScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TaskEditorScreen
import com.example.ui.screens.TaskHistoryScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.WorkspaceHubScreen
import com.example.utils.DateTimeUtils
import com.example.utils.NetworkMonitor
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.TaskViewModel
import com.example.viewmodel.WorkspaceViewModel
import com.example.widget.SmartTodoWidgetProvider
import com.example.widget.SmartTodoWidgetStore
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

sealed interface WidgetDeepLink {
    val eventId: Long
    data class AddTask(override val eventId: Long) : WidgetDeepLink
    data class EditTask(val taskId: String, override val eventId: Long) : WidgetDeepLink
}

object SmartTodoRoutes {
    const val HOME = "home"
    const val TASKS = "tasks"
    const val COMPLETED = "completed"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val WORKSPACE = "workspace"
    const val TASK_EDITOR_PATTERN = "task_editor?taskId={taskId}"

    fun taskEditor(taskId: String? = null): String =
        if (taskId.isNullOrBlank()) "task_editor" else "task_editor?taskId=$taskId"
}

private enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(SmartTodoRoutes.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    TASKS(SmartTodoRoutes.TASKS, R.string.nav_tasks, Icons.Rounded.TaskAlt, Icons.Outlined.TaskAlt),
    COMPLETED(
        SmartTodoRoutes.COMPLETED,
        R.string.nav_completed,
        Icons.Rounded.CheckCircle,
        Icons.Outlined.CheckCircle
    ),
    SETTINGS(
        SmartTodoRoutes.SETTINGS,
        R.string.nav_settings,
        Icons.Rounded.Settings,
        Icons.Outlined.Settings
    )
}

@Composable
fun SmartTodoApp(
    settingsViewModel: SettingsViewModel,
    auth: FirebaseAuth = Firebase.auth,
    pendingWidgetDeepLink: WidgetDeepLink? = null,
    onConsumeWidgetDeepLink: () -> Unit = {}
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var suppressAutoSignIn by remember { mutableStateOf(false) }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            suppressAutoSignIn = suppressAutoSignIn,
            onAuthSuccess = { suppressAutoSignIn = false }
        )
    } else {
        key(user.uid) {
            AuthenticatedSmartTodoShell(
                currentUserId = user.uid,
                displayName = user.displayName.orEmpty(),
                email = user.email.orEmpty(),
                settings = settings,
                settingsViewModel = settingsViewModel,
                pendingWidgetDeepLink = pendingWidgetDeepLink,
                onConsumeWidgetDeepLink = onConsumeWidgetDeepLink,
                onUserSignedOut = { suppressAutoSignIn = true }
            )
        }
    }
}

@Composable
private fun AuthenticatedSmartTodoShell(
    currentUserId: String,
    displayName: String,
    email: String,
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    pendingWidgetDeepLink: WidgetDeepLink?,
    onConsumeWidgetDeepLink: () -> Unit,
    onUserSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val taskViewModel: TaskViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                TaskViewModel(
                    repository = FirestoreTaskRepository(app),
                    settingsRepository = SettingsDataStore(app),
                    reminderScheduler = AndroidTaskReminderScheduler(app),
                    connectivityObserver = NetworkMonitor(app),
                    currentUserId = currentUserId,
                    initialDisplayName = displayName,
                    initialEmail = email,
                    onTasksSynced = { uid, tasks ->
                        SmartTodoWidgetStore.saveTasks(app, uid, tasks)
                        SmartTodoWidgetProvider.updateAllWidgets(app)
                    },
                    onSignOutClearWidget = {
                        SmartTodoWidgetStore.clear(app)
                        SmartTodoWidgetProvider.updateAllWidgets(app)
                    }
                )
            }
        }
    )

    val workspaceViewModel: WorkspaceViewModel = viewModel(key = "workspace_$currentUserId")

    LaunchedEffect(currentUserId) {
        taskViewModel.ensureSessionActive()
    }

    AuthenticatedSmartTodoContent(
        taskViewModel = taskViewModel,
        workspaceViewModel = workspaceViewModel,
        settingsViewModel = settingsViewModel,
        settings = settings,
        pendingWidgetDeepLink = pendingWidgetDeepLink,
        onConsumeWidgetDeepLink = onConsumeWidgetDeepLink,
        onSignOutConfirmed = {
            onUserSignedOut()
            GoogleAuthManager.signOut(
                credentialManager = credentialManager,
                scope = scope,
                onBeforeSignOut = {
                    workspaceViewModel.disconnect()
                    taskViewModel.prepareForSignOut()
                }
            )
        }
    )
}

@Composable
internal fun AuthenticatedSmartTodoContent(
    taskViewModel: TaskViewModel,
    workspaceViewModel: WorkspaceViewModel,
    settingsViewModel: SettingsViewModel,
    settings: AppSettings,
    pendingWidgetDeepLink: WidgetDeepLink? = null,
    onConsumeWidgetDeepLink: () -> Unit = {},
    onSignOutConfirmed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    fun navigateSingleTop(route: String) {
        snackbarHostState.currentSnackbarData?.dismiss()
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(pendingWidgetDeepLink, currentRoute) {
        if (currentRoute == null) return@LaunchedEffect
        when (val link = pendingWidgetDeepLink) {
            is WidgetDeepLink.AddTask -> {
                navigateSingleTop(SmartTodoRoutes.taskEditor(null))
                onConsumeWidgetDeepLink()
            }
            is WidgetDeepLink.EditTask -> {
                navigateSingleTop(SmartTodoRoutes.taskEditor(link.taskId))
                onConsumeWidgetDeepLink()
            }
            null -> Unit
        }
    }

    val tasksState by taskViewModel.tasksState.collectAsStateWithLifecycle()
    val taskLists by taskViewModel.taskLists.collectAsStateWithLifecycle()
    val selectedListId by taskViewModel.selectedListId.collectAsStateWithLifecycle()
    val filteredTasks by taskViewModel.filteredTasks.collectAsStateWithLifecycle()
    val completedTasks by taskViewModel.completedTasks.collectAsStateWithLifecycle()
    val taskHistoryState by taskViewModel.taskHistoryState.collectAsStateWithLifecycle()
    val dashboardStats by taskViewModel.dashboardStats.collectAsStateWithLifecycle()
    val userProfile by taskViewModel.userProfile.collectAsStateWithLifecycle()
    val selectedFilter by taskViewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedSort by taskViewModel.selectedSort.collectAsStateWithLifecycle()
    val searchQuery by taskViewModel.searchQuery.collectAsStateWithLifecycle()
    val completedSearchQuery by taskViewModel.completedSearchQuery.collectAsStateWithLifecycle()
    val isOnline by taskViewModel.isOnline.collectAsStateWithLifecycle()
    val isSaving by taskViewModel.isSaving.collectAsStateWithLifecycle()
    val snackbarMessage by taskViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val workspaceState by workspaceViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(msg)
        taskViewModel.clearSnackbar()
    }

    val isTopLevelRoute = TopLevelDestination.entries.any { it.route == currentRoute }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (isTopLevelRoute && !useNavigationRail) {
                    Column {
                        androidx.compose.material3.HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            modifier = Modifier.testTag("bottom_navigation_bar")
                        ) {
                            TopLevelDestination.entries.forEach { destination ->
                                val selected = currentRoute == destination.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(destination.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                            contentDescription = stringResource(destination.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(destination.labelRes),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                                                else androidx.compose.ui.text.font.FontWeight.Medium
                                            )
                                        )
                                    },
                                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_${destination.route}")
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                if (isTopLevelRoute && currentRoute != SmartTodoRoutes.SETTINGS) {
                    FloatingActionButton(
                        onClick = { navigateSingleTop(SmartTodoRoutes.taskEditor(null)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(20.dp),
                        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 6.dp
                        ),
                        modifier = Modifier.testTag("add_task_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.cd_add_task)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelRoute && useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("side_navigation_rail")
                    ) {
                        TopLevelDestination.entries.forEach { destination ->
                            val selected = currentRoute == destination.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(destination.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = stringResource(destination.labelRes)
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                                modifier = Modifier.testTag("rail_${destination.route}")
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    OfflineStatusBanner(isOnline = isOnline)

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = SmartTodoRoutes.HOME,
                            enterTransition = {
                                androidx.compose.animation.fadeIn(
                                    animationSpec = androidx.compose.animation.core.tween(180)
                                )
                            },
                            exitTransition = {
                                androidx.compose.animation.fadeOut(
                                    animationSpec = androidx.compose.animation.core.tween(150)
                                )
                            }
                        ) {
                            composable(SmartTodoRoutes.HOME) {
                                HomeScreen(
                                    tasksState = tasksState,
                                    taskLists = taskLists,
                                    stats = dashboardStats,
                                    profile = userProfile,
                                    isWorkspaceConnected = workspaceState.isConnected,
                                    onQuickAddTask = { title, dueToday, starred ->
                                        taskViewModel.quickCreateTask(title, dueToday, starred)
                                    },
                                    onToggleTask = { taskViewModel.toggleTaskCompletion(it) },
                                    onToggleStar = { taskViewModel.toggleTaskStarred(it) },
                                    onToggleSubtask = { task, subId ->
                                        taskViewModel.toggleSubtaskCompletion(task, subId)
                                    },
                                    onEditTask = { task ->
                                        navigateSingleTop(SmartTodoRoutes.taskEditor(task.id))
                                    },
                                    onDeleteTask = { taskViewModel.deleteTask(it) },
                                    onNavigateToFilter = { filter ->
                                        taskViewModel.setFilter(filter)
                                        navigateTopLevel(SmartTodoRoutes.TASKS)
                                    },
                                    onOpenWorkspace = {
                                        navigateSingleTop(SmartTodoRoutes.WORKSPACE)
                                    },
                                    onOpenHistory = {
                                        navigateSingleTop(SmartTodoRoutes.HISTORY)
                                    },
                                    onRetry = { taskViewModel.retryObserving() }
                                )
                            }

                            composable(SmartTodoRoutes.TASKS) {
                                TasksScreen(
                                    tasksState = filteredTasks,
                                    taskLists = taskLists,
                                    selectedListId = selectedListId,
                                    selectedFilter = selectedFilter,
                                    selectedSort = selectedSort,
                                    searchQuery = searchQuery,
                                    onSelectList = { taskViewModel.selectList(it) },
                                    onCreateList = { name, hex -> taskViewModel.createTaskList(name, hex) },
                                    onRenameList = { id, name, hex -> taskViewModel.renameTaskList(id, name, hex) },
                                    onDeleteList = { taskViewModel.deleteTaskList(it) },
                                    onQuickAddTask = { title, dueToday, starred ->
                                        taskViewModel.quickCreateTask(title, dueToday, starred)
                                    },
                                    onFilterSelected = { taskViewModel.setFilter(it) },
                                    onSortSelected = { taskViewModel.setSortOption(it) },
                                    onSearchQueryChange = { taskViewModel.setSearchQuery(it) },
                                    onToggleTask = { taskViewModel.toggleTaskCompletion(it) },
                                    onToggleStar = { taskViewModel.toggleTaskStarred(it) },
                                    onToggleSubtask = { task, subId ->
                                        taskViewModel.toggleSubtaskCompletion(task, subId)
                                    },
                                    onReorderTasks = { taskViewModel.reorderTasks(it) },
                                    onEditTask = { task ->
                                        navigateSingleTop(SmartTodoRoutes.taskEditor(task.id))
                                    },
                                    onDeleteTask = { taskViewModel.deleteTask(it) },
                                    onOpenHistory = {
                                        navigateSingleTop(SmartTodoRoutes.HISTORY)
                                    },
                                    onRetry = { taskViewModel.retryObserving() }
                                )
                            }

                            composable(SmartTodoRoutes.COMPLETED) {
                                CompletedScreen(
                                    completedState = completedTasks,
                                    searchQuery = completedSearchQuery,
                                    onSearchQueryChange = { taskViewModel.setCompletedSearchQuery(it) },
                                    onReopenTask = { taskViewModel.toggleTaskCompletion(it) },
                                    onEditTask = { task ->
                                        navigateSingleTop(SmartTodoRoutes.taskEditor(task.id))
                                    },
                                    onDeleteTask = { taskViewModel.deleteTask(it) },
                                    onRetry = { taskViewModel.retryObserving() },
                                    taskLists = taskLists,
                                    onToggleSubtask = { task, subId ->
                                        taskViewModel.toggleSubtaskCompletion(task, subId)
                                    }
                                )
                            }

                            composable(SmartTodoRoutes.SETTINGS) {
                                SettingsScreen(
                                    profile = userProfile,
                                    settings = settings,
                                    isOnline = isOnline,
                                    isWorkspaceConnected = workspaceState.isConnected,
                                    onThemeModeSelected = { settingsViewModel.setThemeMode(it) },
                                    onNotificationsEnabledChange = {
                                        settingsViewModel.setNotificationsEnabled(it)
                                    },
                                    onReminderLeadTimeSelected = {
                                        settingsViewModel.setReminderLeadTime(it)
                                    },
                                    onOpenWorkspace = {
                                        navigateSingleTop(SmartTodoRoutes.WORKSPACE)
                                    },
                                    onOpenHistory = {
                                        navigateSingleTop(SmartTodoRoutes.HISTORY)
                                    },
                                    onSignOutConfirmed = onSignOutConfirmed,
                                    onShowMessage = { taskViewModel.showMessage(it) }
                                )
                            }

                            composable(SmartTodoRoutes.HISTORY) {
                                TaskHistoryScreen(
                                    historyState = taskHistoryState,
                                    onClearHistory = { taskViewModel.clearHistory() },
                                    onRetry = { taskViewModel.retryObserving() },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(SmartTodoRoutes.WORKSPACE) {
                                val allTasks = (tasksState as? UiState.Success)?.data.orEmpty()
                                WorkspaceHubScreen(
                                    workspaceState = workspaceState,
                                    tasks = allTasks,
                                    onConnectingChange = { workspaceViewModel.setConnecting(it) },
                                    onTokenObtained = { token ->
                                        workspaceViewModel.onAccessTokenObtained(token)
                                        taskViewModel.showMessage("Google Workspace connected")
                                    },
                                    onAuthError = { err -> workspaceViewModel.onAuthError(err) },
                                    onDisconnect = {
                                        workspaceViewModel.disconnect()
                                        taskViewModel.showMessage("Google Workspace disconnected")
                                    },
                                    onRefreshAll = { workspaceViewModel.refreshAll() },
                                    onSyncTaskToCalendar = { task ->
                                        workspaceViewModel.syncTaskToCalendar(task) { res ->
                                            res.fold(
                                                onSuccess = { syncRes ->
                                                    if (syncRes.eventId.isNotBlank()) {
                                                        taskViewModel.markTaskSyncedToCalendar(task, syncRes.eventId)
                                                    }
                                                    taskViewModel.showMessage("Synced \"${task.title}\" to Google Calendar")
                                                },
                                                onFailure = { err ->
                                                    taskViewModel.showMessage(
                                                        err.message ?: "Could not sync task to Google Calendar."
                                                    )
                                                }
                                            )
                                        }
                                    },
                                    onImportCalendarEvent = { event ->
                                        val safeTitle = event.summary.trim()
                                            .ifBlank { "Calendar event" }
                                            .take(120)
                                        taskViewModel.createTask(
                                            title = safeTitle,
                                            description = event.description?.take(1000),
                                            dueDate = event.startDateUtcMillis ?: DateTimeUtils.todayUtcMillis(),
                                            dueTime = event.startTime,
                                            priority = TaskPriority.MEDIUM,
                                            category = TaskCategory.WORK,
                                            historyAction = TaskHistoryAction.IMPORTED_FROM_CALENDAR,
                                            historyDetails = "Imported from Google Calendar (${event.displayTimeRange})"
                                        )
                                    },
                                    onImportEmailAsTask = { email ->
                                        val safeSubject = email.subject.trim()
                                            .ifBlank { "(No subject)" }
                                            .take(120)
                                        val desc = buildString {
                                            if (email.from.isNotBlank()) append("From: ${email.from}\n")
                                            if (email.date.isNotBlank()) append("Date: ${email.date}\n\n")
                                            append(email.snippet)
                                        }.take(1000)
                                        taskViewModel.createTask(
                                            title = safeSubject,
                                            description = desc,
                                            dueDate = DateTimeUtils.todayUtcMillis(),
                                            dueTime = null,
                                            priority = TaskPriority.MEDIUM,
                                            category = TaskCategory.WORK,
                                            historyAction = TaskHistoryAction.IMPORTED_FROM_GMAIL,
                                            historyDetails = "Imported from Gmail (${email.from})"
                                        )
                                    },
                                    onShareTaskToChat = { space, task ->
                                        workspaceViewModel.postTaskToChatSpace(space, task) { res ->
                                            res.fold(
                                                onSuccess = {
                                                    taskViewModel.recordWorkspaceHistory(
                                                        taskId = task.id,
                                                        taskTitle = task.title,
                                                        action = TaskHistoryAction.SHARED_TO_CHAT,
                                                        details = "Shared to ${space.displayName}"
                                                    )
                                                    taskViewModel.showMessage("Shared to ${space.displayName}")
                                                },
                                                onFailure = { err ->
                                                    taskViewModel.showMessage(
                                                        err.message ?: "Could not post message to Google Chat."
                                                    )
                                                }
                                            )
                                        }
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = SmartTodoRoutes.TASK_EDITOR_PATTERN,
                                arguments = listOf(
                                    navArgument("taskId") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) { backStackEntry ->
                                val taskId = backStackEntry.arguments?.getString("taskId")
                                val existingTask = remember(taskId, tasksState) {
                                    taskId?.let { taskViewModel.findTaskById(it) }
                                }
                                if (taskId != null && existingTask == null) {
                                    when (tasksState) {
                                        is UiState.Loading -> LoadingStateView()
                                        else -> ErrorStateView(
                                            message = stringResource(R.string.error_task_not_found),
                                            onRetry = { navController.popBackStack() }
                                        )
                                    }
                                } else {
                                    TaskEditorScreen(
                                        existingTask = existingTask,
                                        taskLists = taskLists,
                                        defaultListId = selectedListId,
                                        isSaving = isSaving,
                                        onSave = { payload ->
                                            if (existingTask != null) {
                                                taskViewModel.updateTask(
                                                    taskId = existingTask.id,
                                                    title = payload.title,
                                                    description = payload.description,
                                                    dueDate = payload.dueDate,
                                                    dueTime = payload.dueTime,
                                                    priority = payload.priority,
                                                    category = payload.category,
                                                    isCompleted = existingTask.isCompleted,
                                                    startDate = payload.startDate,
                                                    startTime = payload.startTime,
                                                    deadline = payload.deadline,
                                                    listId = payload.listId,
                                                    isStarred = payload.isStarred,
                                                    recurrenceType = payload.recurrenceType,
                                                    recurrenceInterval = payload.recurrenceInterval,
                                                    recurrenceUnit = payload.recurrenceUnit,
                                                    subtasks = payload.subtasks,
                                                    sortOrder = existingTask.sortOrder,
                                                    calendarEventId = existingTask.calendarEventId,
                                                    onComplete = { ok ->
                                                        if (ok) navController.popBackStack()
                                                    }
                                                )
                                            } else {
                                                taskViewModel.createTask(
                                                    title = payload.title,
                                                    description = payload.description,
                                                    dueDate = payload.dueDate,
                                                    dueTime = payload.dueTime,
                                                    priority = payload.priority,
                                                    category = payload.category,
                                                    startDate = payload.startDate,
                                                    startTime = payload.startTime,
                                                    deadline = payload.deadline,
                                                    listId = payload.listId,
                                                    isStarred = payload.isStarred,
                                                    recurrenceType = payload.recurrenceType,
                                                    recurrenceInterval = payload.recurrenceInterval,
                                                    recurrenceUnit = payload.recurrenceUnit,
                                                    subtasks = payload.subtasks,
                                                    onComplete = { ok ->
                                                        if (ok) navController.popBackStack()
                                                    }
                                                )
                                            }
                                        },
                                        onCreateList = { name, hex -> taskViewModel.createTaskList(name, hex) },
                                        onRenameList = { id, name, hex -> taskViewModel.renameTaskList(id, name, hex) },
                                        onDeleteList = { taskViewModel.deleteTaskList(it) },
                                        onOpenWorkspace = { navigateSingleTop(SmartTodoRoutes.WORKSPACE) },
                                        onCancel = { navController.popBackStack() },
                                        onPermissionDeniedMessage = { taskViewModel.showMessage(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
