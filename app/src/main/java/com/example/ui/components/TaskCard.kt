package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.RecurrenceCalculator
import com.example.domain.model.Task
import com.example.domain.model.TaskList
import com.example.domain.model.TaskPriority
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityHighContainer
import com.example.ui.theme.PriorityHighDarkContainer
import com.example.ui.theme.PriorityHighDarkText
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.PriorityLowContainer
import com.example.ui.theme.PriorityLowDarkContainer
import com.example.ui.theme.PriorityLowDarkText
import com.example.ui.theme.PriorityMediumColor
import com.example.ui.theme.PriorityMediumContainer
import com.example.ui.theme.PriorityMediumDarkContainer
import com.example.ui.theme.PriorityMediumDarkText
import com.example.utils.DateTimeUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: Task,
    onToggleComplete: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleStar: (() -> Unit)? = null,
    onToggleSubtask: ((String) -> Unit)? = null,
    taskList: TaskList? = null,
    showReopenAction: Boolean = false,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    isDragging: Boolean = false
) {
    val isOverdue = DateTimeUtils.isTaskOverdue(task)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val priorityEnum = task.priorityEnum()
    val priorityStyle = priorityBadgeColors(priorityEnum, isDark)
    val formattedDueDate = task.dueDate?.let { DateTimeUtils.formatDisplayDate(it) }
    val formattedDueTime = DateTimeUtils.formatDisplayTime(task.dueTime)
    val formattedStartDate = task.startDate?.let { DateTimeUtils.formatDisplayDate(it) }
    val formattedStartTime = DateTimeUtils.formatDisplayTime(task.startTime)
    val formattedDeadline = task.deadline?.let { DateTimeUtils.formatDisplayDate(it) }
    val recurrenceLabel = RecurrenceCalculator.formatRecurrenceLabel(
        task.recurrenceTypeEnum(),
        task.recurrenceInterval,
        task.recurrenceUnitEnum()
    )
    var subtasksExpanded by remember(task.id) { mutableStateOf(false) }

    val cardScale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardDragScale"
    )
    val cardContainerColor by animateColorAsState(
        targetValue = when {
            isDragging -> MaterialTheme.colorScheme.surfaceContainerHigh
            task.isCompleted -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surface
        },
        label = "cardContainerColor"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1.0f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(cardScale)
            .testTag("task_card_${task.id}")
            .clickable(onClick = onEditClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = when {
                isDragging -> 10.dp
                task.isCompleted -> 0.dp
                isDark -> 0.dp
                else -> 1.5.dp
            }
        ),
        border = BorderStroke(
            width = if (isDragging) 1.5.dp else 1.dp,
            color = when {
                isDragging -> MaterialTheme.colorScheme.primary
                isOverdue -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                task.isCompleted -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Row: Priority Accent / Drag Handle + Tactile Checkbox + Title/Description + Primary Trailing Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 10.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (showDragHandle) {
                    Box(
                        modifier = dragHandleModifier
                            .size(44.dp)
                            .testTag("task_drag_${task.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = stringResource(R.string.cd_drag_reorder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, start = 6.dp, end = 2.dp)
                            .width(3.5.dp)
                            .height(26.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    task.isCompleted -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                    isOverdue -> MaterialTheme.colorScheme.error
                                    else -> priorityStyle.accent
                                }
                            )
                    )
                }

                // Tactile priority-ring circular checkbox (full 48.dp touch target)
                IconButton(
                    onClick = onToggleComplete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("task_toggle_${task.id}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .scale(checkScale)
                            .clip(CircleShape)
                            .background(
                                if (task.isCompleted) MaterialTheme.colorScheme.primary
                                else priorityStyle.accent.copy(alpha = 0.08f)
                            )
                            .border(
                                width = if (task.isCompleted) 0.dp else 2.dp,
                                color = if (task.isCompleted) Color.Transparent else priorityStyle.accent.copy(alpha = 0.8f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.isCompleted) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.cd_toggle_task),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 10.dp, end = 4.dp)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (task.isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (task.isCompleted) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val desc = task.description
                    if (!desc.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (showReopenAction && task.isCompleted) {
                    IconButton(
                        onClick = onToggleComplete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("task_reopen_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Restore,
                            contentDescription = stringResource(R.string.action_reopen),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (onToggleStar != null) {
                    IconButton(
                        onClick = onToggleStar,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("task_star_${task.id}")
                    ) {
                        Icon(
                            imageVector = if (task.isStarred) Icons.Rounded.Star else Icons.Outlined.StarBorder,
                            contentDescription = stringResource(
                                if (task.isStarred) R.string.cd_unstar_task else R.string.cd_star_task
                            ),
                            tint = if (task.isStarred) PriorityMediumColor else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            // Bottom Row: Crisp Metadata Pills + Compact Delete Action (aligned to right edge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (taskList != null) {
                        ListBadgePill(taskList = taskList)
                    }

                    if (formattedDueDate != null) {
                        MetaChip(
                            icon = Icons.Rounded.CalendarToday,
                            label = if (formattedDueTime != null) "$formattedDueDate · $formattedDueTime" else formattedDueDate,
                            containerColor = if (isOverdue) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (isOverdue) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            useMonospace = true
                        )
                    } else if (formattedDueTime != null) {
                        MetaChip(
                            icon = Icons.Rounded.AccessTime,
                            label = formattedDueTime,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            useMonospace = true
                        )
                    }

                    if (formattedStartDate != null) {
                        val startText = if (formattedStartTime != null) {
                            "Start $formattedStartDate · $formattedStartTime"
                        } else {
                            "Start $formattedStartDate"
                        }
                        MetaChip(
                            icon = Icons.Rounded.PlayArrow,
                            label = startText,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            useMonospace = true
                        )
                    }

                    if (formattedDeadline != null) {
                        MetaChip(
                            icon = Icons.Rounded.Flag,
                            label = "Due by $formattedDeadline",
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            useMonospace = true
                        )
                    }

                    if (recurrenceLabel != null) {
                        MetaChip(
                            icon = Icons.Rounded.Repeat,
                            label = recurrenceLabel,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    if (task.subtasks.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clickable { subtasksExpanded = !subtasksExpanded }
                                .testTag("task_subtasks_pill_${task.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Checklist,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${task.completedSubtaskCount}/${task.subtasks.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = if (subtasksExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (!task.calendarEventId.isNullOrBlank()) {
                        MetaChip(
                            icon = Icons.Rounded.EventAvailable,
                            label = "Calendar",
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Priority Pill
                    Surface(
                        color = priorityStyle.container,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(priorityStyle.accent)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = priorityEnum.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = priorityStyle.content
                            )
                        }
                    }

                    // Category Pill
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = task.categoryEnum().displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("task_delete_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_task),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Smoothly Animated Inline Subtask Checklist Drawer
            AnimatedVisibility(
                visible = subtasksExpanded && task.subtasks.isNotEmpty(),
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        task.subtasks.forEach { subtask ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = onToggleSubtask != null) {
                                        onToggleSubtask?.invoke(subtask.id)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (subtask.isCompleted) {
                                        Icons.Rounded.CheckBox
                                    } else {
                                        Icons.Rounded.CheckBoxOutlineBlank
                                    },
                                    contentDescription = null,
                                    tint = if (subtask.isCompleted) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = subtask.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (subtask.isCompleted) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    textDecoration = if (subtask.isCompleted) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListBadgePill(taskList: TaskList) {
    val dotColor = parseHexColorOrDefault(taskList.colorHex, MaterialTheme.colorScheme.primary)
    Surface(
        color = dotColor.copy(alpha = 0.13f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = taskList.name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = dotColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetaChip(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    useMonospace: Boolean = false
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = if (useMonospace) {
                    MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.5.sp
                    )
                } else {
                    MaterialTheme.typography.labelSmall
                },
                color = contentColor
            )
        }
    }
}

fun parseHexColorOrDefault(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

private data class PriorityStyle(
    val accent: Color,
    val container: Color,
    val content: Color
)

private fun priorityBadgeColors(priority: TaskPriority, isDark: Boolean): PriorityStyle {
    return when (priority) {
        TaskPriority.HIGH -> PriorityStyle(
            accent = PriorityHighColor,
            container = if (isDark) PriorityHighDarkContainer else PriorityHighContainer,
            content = if (isDark) PriorityHighDarkText else PriorityHighColor
        )
        TaskPriority.MEDIUM -> PriorityStyle(
            accent = PriorityMediumColor,
            container = if (isDark) PriorityMediumDarkContainer else PriorityMediumContainer,
            content = if (isDark) PriorityMediumDarkText else PriorityMediumColor
        )
        TaskPriority.LOW -> PriorityStyle(
            accent = PriorityLowColor,
            container = if (isDark) PriorityLowDarkContainer else PriorityLowContainer,
            content = if (isDark) PriorityLowDarkText else PriorityLowColor
        )
    }
}
