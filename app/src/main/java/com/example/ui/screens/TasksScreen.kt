package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.domain.model.Task
import com.example.domain.model.TaskList
import com.example.model.TaskFilter
import com.example.model.TaskSortOption
import com.example.model.UiState
import com.example.ui.components.ConfirmDeleteTaskDialog
import com.example.ui.components.EmptyTasksView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingStateView
import com.example.ui.components.ManageTaskListsDialog
import com.example.ui.components.QuickAddTaskBar
import com.example.ui.components.SectionEyebrow
import com.example.ui.components.TaskCard
import com.example.ui.components.TaskListSwitcherRow
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityMediumColor

@Composable
fun TasksScreen(
    tasksState: UiState<List<Task>>,
    taskLists: List<TaskList>,
    selectedListId: String?,
    selectedFilter: TaskFilter,
    selectedSort: TaskSortOption,
    searchQuery: String,
    onSelectList: (String?) -> Unit,
    onCreateList: (name: String, colorHex: String) -> Unit,
    onRenameList: (listId: String, newName: String, colorHex: String) -> Unit,
    onDeleteList: (TaskList) -> Unit,
    onQuickAddTask: (title: String, dueToday: Boolean, isStarred: Boolean) -> Unit,
    onFilterSelected: (TaskFilter) -> Unit,
    onSortSelected: (TaskSortOption) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleTask: (Task) -> Unit,
    onToggleStar: (Task) -> Unit,
    onToggleSubtask: (Task, String) -> Unit,
    onReorderTasks: (List<Task>) -> Unit,
    onEditTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onOpenHistory: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showManageListsDialog by remember { mutableStateOf(false) }
    var taskPendingDelete by remember { mutableStateOf<Task?>(null) }

    val listsById = remember(taskLists) { taskLists.associateBy { it.id } }
    val activeListName = selectedListId?.let { listsById[it]?.name }
    val isDragReorderEnabled = selectedSort == TaskSortOption.MANUAL_ORDER && searchQuery.isBlank()

    val lazyListState = rememberLazyListState()
    val localOrder = remember { mutableStateListOf<Task>() }
    var draggedTaskId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val currentTasks = (tasksState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(currentTasks, draggedTaskId) {
        if (draggedTaskId == null) {
            localOrder.clear()
            localOrder.addAll(currentTasks)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            // Editorial Top Header + History Icon + Pill Sort Trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    SectionEyebrow(
                        text = activeListName ?: "WORKSPACE"
                    )
                    Text(
                        text = stringResource(R.string.nav_tasks),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        IconButton(
                            onClick = onOpenHistory,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("open_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = stringResource(R.string.task_history_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Box {
                        Surface(
                            onClick = { sortMenuExpanded = true },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.testTag("sort_dropdown_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedSort.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            TaskSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (option == selectedSort) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    trailingIcon = if (option == selectedSort) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        onSortSelected(option)
                                        sortMenuExpanded = false
                                    },
                                    modifier = Modifier.testTag("sort_option_${option.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // Multiple Task Lists Switcher
            TaskListSwitcherRow(
                taskLists = taskLists,
                selectedListId = selectedListId,
                onSelectList = onSelectList,
                onManageListsClick = { showManageListsDialog = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Seamless Filled Pill Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_tasks_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_clear_search),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("task_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Pill Filter Chips Row with Contextual Icons
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_filter_chips")
            ) {
                items(TaskFilter.entries) { filter ->
                    val selected = filter == selectedFilter
                    val chipIcon = when (filter) {
                        TaskFilter.STARRED -> Icons.Rounded.Star
                        TaskFilter.TODAY -> Icons.Rounded.Today
                        TaskFilter.OVERDUE -> Icons.Rounded.WarningAmber
                        else -> null
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterSelected(filter) },
                        leadingIcon = chipIcon?.let { icon ->
                            {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = when {
                                        selected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        filter == TaskFilter.STARRED -> PriorityMediumColor
                                        filter == TaskFilter.OVERDUE -> PriorityHighColor
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            )
                        },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Task List Content
            when (tasksState) {
                is UiState.Loading -> LoadingStateView()
                is UiState.Error -> ErrorStateView(message = tasksState.message, onRetry = onRetry)
                is UiState.Success -> {
                    val displayedTasks = if (isDragReorderEnabled) localOrder else tasksState.data
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("tasks_list"),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 8.dp,
                            bottom = 104.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "quick_add") {
                            QuickAddTaskBar(onQuickAdd = onQuickAddTask)
                        }

                        if (displayedTasks.isEmpty()) {
                            item(key = "empty_state") {
                                val isFiltered = searchQuery.isNotBlank() ||
                                    selectedFilter != TaskFilter.ALL ||
                                    selectedListId != null
                                EmptyTasksView(
                                    title = if (isFiltered) {
                                        stringResource(R.string.empty_search_title)
                                    } else {
                                        stringResource(R.string.empty_tasks_title)
                                    },
                                    subtitle = if (isFiltered) {
                                        stringResource(R.string.empty_search_subtitle)
                                    } else {
                                        stringResource(R.string.empty_tasks_subtitle)
                                    }
                                )
                            }
                        } else {
                            item(key = "count") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${displayedTasks.size} ${if (displayedTasks.size == 1) "TASK" else "TASKS"}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isDragReorderEnabled) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DragHandle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = "Drag handles to reorder",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            itemsIndexed(
                                items = displayedTasks,
                                key = { _, item -> item.id }
                            ) { _, task ->
                                val isDraggingThis = draggedTaskId == task.id
                                val dragModifier = if (isDragReorderEnabled) {
                                    Modifier.pointerInput(task.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggedTaskId = task.id
                                                dragOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                val snapshot = localOrder.toList()
                                                draggedTaskId = null
                                                dragOffsetY = 0f
                                                onReorderTasks(snapshot)
                                            },
                                            onDragCancel = {
                                                draggedTaskId = null
                                                dragOffsetY = 0f
                                                localOrder.clear()
                                                localOrder.addAll(currentTasks)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y
                                                val currentIndex = localOrder.indexOfFirst { it.id == task.id }
                                                if (currentIndex >= 0) {
                                                    val thresholdPx = 150f
                                                    if (dragOffsetY > thresholdPx && currentIndex < localOrder.lastIndex) {
                                                        val moved = localOrder.removeAt(currentIndex)
                                                        localOrder.add(currentIndex + 1, moved)
                                                        dragOffsetY -= thresholdPx
                                                    } else if (dragOffsetY < -thresholdPx && currentIndex > 0) {
                                                        val moved = localOrder.removeAt(currentIndex)
                                                        localOrder.add(currentIndex - 1, moved)
                                                        dragOffsetY += thresholdPx
                                                    }
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }

                                TaskCard(
                                    task = task,
                                    taskList = task.listId?.let { listsById[it] },
                                    onToggleComplete = { onToggleTask(task) },
                                    onToggleStar = { onToggleStar(task) },
                                    onToggleSubtask = { subId -> onToggleSubtask(task, subId) },
                                    onEditClick = { onEditTask(task) },
                                    onDeleteClick = { taskPendingDelete = task },
                                    showDragHandle = isDragReorderEnabled,
                                    dragHandleModifier = dragModifier,
                                    isDragging = isDraggingThis,
                                    modifier = Modifier
                                        .zIndex(if (isDraggingThis) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (isDraggingThis) dragOffsetY else 0f
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManageListsDialog) {
        ManageTaskListsDialog(
            taskLists = taskLists,
            onCreateList = onCreateList,
            onRenameList = onRenameList,
            onDeleteList = onDeleteList,
            onDismiss = { showManageListsDialog = false }
        )
    }

    taskPendingDelete?.let { pending ->
        ConfirmDeleteTaskDialog(
            taskTitle = pending.title,
            onConfirm = {
                onDeleteTask(pending)
                taskPendingDelete = null
            },
            onDismiss = { taskPendingDelete = null }
        )
    }
}
