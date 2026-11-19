package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.Task
import com.example.model.UiState
import com.example.ui.components.ConfirmDeleteTaskDialog
import com.example.ui.components.EmptyTasksView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingStateView
import com.example.ui.components.SectionEyebrow
import com.example.ui.components.TaskCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PriorityLowColor

@Composable
fun CompletedScreen(
    completedState: UiState<List<Task>>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onReopenTask: (Task) -> Unit,
    onEditTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    taskLists: List<com.example.domain.model.TaskList> = emptyList(),
    onToggleSubtask: ((Task, String) -> Unit)? = null
) {
    var taskPendingDelete by remember { mutableStateOf<Task?>(null) }
    val listsById = remember(taskLists) { taskLists.associateBy { it.id } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
            ) {
                SectionEyebrow(text = "ARCHIVE")
                Text(
                    text = stringResource(R.string.nav_completed),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                val count = (completedState as? UiState.Success)?.data?.size ?: 0
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PriorityLowColor.copy(alpha = 0.09f),
                    border = BorderStroke(1.dp, PriorityLowColor.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(PriorityLowColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = PriorityLowColor,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (count == 1) "1 task accomplished" else "$count tasks accomplished",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap reopen on any card to move it back to active tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = PriorityLowColor
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.search_tasks_hint)) },
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
                            modifier = Modifier.testTag("completed_clear_search_button")
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
                    .testTag("completed_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            when (completedState) {
                is UiState.Loading -> LoadingStateView()
                is UiState.Error -> ErrorStateView(message = completedState.message, onRetry = onRetry)
                is UiState.Success -> {
                    val tasks = completedState.data
                    if (tasks.isEmpty()) {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            EmptyTasksView(
                                title = if (searchQuery.isNotBlank()) {
                                    stringResource(R.string.empty_search_title)
                                } else {
                                    stringResource(R.string.empty_completed_title)
                                },
                                subtitle = if (searchQuery.isNotBlank()) {
                                    stringResource(R.string.empty_search_subtitle)
                                } else {
                                    stringResource(R.string.empty_completed_subtitle)
                                }
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("completed_tasks_list"),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 6.dp,
                                bottom = 104.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = tasks,
                                key = { it.id }
                            ) { task ->
                                TaskCard(
                                    task = task,
                                    taskList = task.listId?.let { listsById[it] },
                                    onToggleComplete = { onReopenTask(task) },
                                    onToggleSubtask = onToggleSubtask?.let { handler ->
                                        { subId -> handler(task, subId) }
                                    },
                                    onEditClick = { onEditTask(task) },
                                    onDeleteClick = { taskPendingDelete = task },
                                    showReopenAction = true
                                )
                            }
                        }
                    }
                }
            }
        }
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
