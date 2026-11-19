package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.TaskHistoryAction
import com.example.domain.model.TaskHistoryEntry
import com.example.model.UiState
import com.example.ui.components.EmptyTasksView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingStateView
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.PriorityMediumColor
import com.example.utils.DateTimeUtils

private enum class HistoryFilter(val label: String) {
    ALL("All"),
    COMPLETED("Completed"),
    EDITS("Created & Edited"),
    RECURRING("Recurring"),
    WORKSPACE("Workspace")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryScreen(
    historyState: UiState<List<TaskHistoryEntry>>,
    onClearHistory: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var showClearDialog by remember { mutableStateOf(false) }

    val hasEntries = (historyState as? UiState.Success)?.data?.isNotEmpty() == true

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.task_history_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("history_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                actions = {
                    if (hasEntries) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(R.string.action_clear_history),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(HistoryFilter.entries) { filter ->
                        val selected = selectedFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFilter = filter },
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
                            modifier = Modifier.testTag("history_filter_${filter.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (historyState) {
                    is UiState.Loading -> LoadingStateView()
                    is UiState.Error -> ErrorStateView(message = historyState.message, onRetry = onRetry)
                    is UiState.Success -> {
                        val filtered = remember(historyState.data, selectedFilter) {
                            historyState.data.filter { entry ->
                                val act = entry.actionEnum()
                                when (selectedFilter) {
                                    HistoryFilter.ALL -> true
                                    HistoryFilter.COMPLETED ->
                                        act == TaskHistoryAction.COMPLETED || act == TaskHistoryAction.REOPENED
                                    HistoryFilter.EDITS ->
                                        act == TaskHistoryAction.CREATED || act == TaskHistoryAction.UPDATED || act == TaskHistoryAction.DELETED
                                    HistoryFilter.RECURRING ->
                                        act == TaskHistoryAction.RECURRENCE_ADVANCED
                                    HistoryFilter.WORKSPACE ->
                                        act == TaskHistoryAction.SYNCED_TO_CALENDAR ||
                                            act == TaskHistoryAction.IMPORTED_FROM_GMAIL ||
                                            act == TaskHistoryAction.IMPORTED_FROM_CALENDAR ||
                                            act == TaskHistoryAction.SHARED_TO_CHAT
                                }
                            }
                        }

                        if (filtered.isEmpty()) {
                            val isFilterEmpty = historyState.data.isNotEmpty() && selectedFilter != HistoryFilter.ALL
                            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                EmptyTasksView(
                                    title = if (isFilterEmpty) {
                                        "No ${selectedFilter.label.lowercase()} activity"
                                    } else {
                                        stringResource(R.string.task_history_empty_title)
                                    },
                                    subtitle = if (isFilterEmpty) {
                                        "Try another activity filter to view past task updates."
                                    } else {
                                        stringResource(R.string.task_history_empty_subtitle)
                                    }
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("task_history_list")
                            ) {
                                itemsIndexed(filtered, key = { _, it -> it.id }) { index, entry ->
                                    TimelineHistoryRow(
                                        entry = entry,
                                        isFirst = index == 0,
                                        isLast = index == filtered.lastIndex
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.clear_history_dialog_title)) },
            text = { Text(stringResource(R.string.clear_history_dialog_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearHistory()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_clear_history_button")
                ) {
                    Text(stringResource(R.string.action_clear_history))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun TimelineHistoryRow(
    entry: TaskHistoryEntry,
    isFirst: Boolean,
    isLast: Boolean
) {
    val (icon, tint) = historyActionVisuals(entry.actionEnum())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Vertical Timeline Rail & Colored Event Node
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(38.dp)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(
                        if (isFirst) Color.Transparent
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(17.dp)
                )
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(
                        if (isLast) Color.Transparent
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)),
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = tint.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = entry.action,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = tint,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = DateTimeUtils.formatHistoryTimestamp(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = entry.taskTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.details.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = entry.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun historyActionVisuals(action: TaskHistoryAction): Pair<ImageVector, Color> = when (action) {
    TaskHistoryAction.CREATED -> Icons.Rounded.AddTask to MaterialTheme.colorScheme.primary
    TaskHistoryAction.UPDATED -> Icons.Rounded.Edit to MaterialTheme.colorScheme.secondary
    TaskHistoryAction.COMPLETED -> Icons.Rounded.CheckCircle to PriorityLowColor
    TaskHistoryAction.REOPENED -> Icons.Rounded.Restore to PriorityMediumColor
    TaskHistoryAction.DELETED -> Icons.Rounded.DeleteOutline to PriorityHighColor
    TaskHistoryAction.RECURRENCE_ADVANCED -> Icons.Rounded.Repeat to MaterialTheme.colorScheme.tertiary
    TaskHistoryAction.SYNCED_TO_CALENDAR,
    TaskHistoryAction.IMPORTED_FROM_CALENDAR -> Icons.Rounded.EventAvailable to MaterialTheme.colorScheme.primary
    TaskHistoryAction.IMPORTED_FROM_GMAIL -> Icons.Rounded.Email to PriorityHighColor
    TaskHistoryAction.SHARED_TO_CHAT -> Icons.Rounded.Forum to PriorityLowColor
}
