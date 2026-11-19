package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.RecurrenceCalculator
import com.example.domain.TaskValidator
import com.example.domain.model.RecurrenceType
import com.example.domain.model.RecurrenceUnit
import com.example.domain.model.Subtask
import com.example.domain.model.TaskList
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.utils.DateTimeUtils
import java.util.UUID

@Composable
fun EditorSectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskListSelectorSection(
    taskLists: List<TaskList>,
    selectedListId: String?,
    onSelectList: (String?) -> Unit,
    onCreateListClick: () -> Unit
) {
    EditorSectionCard(
        title = stringResource(R.string.field_list_label),
        icon = Icons.Rounded.FolderOpen
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedListId == null,
                onClick = { onSelectList(null) },
                label = { Text(stringResource(R.string.field_no_list)) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.testTag("editor_list_none")
            )
            taskLists.forEach { list ->
                val color = parseHexColorOrDefault(list.colorHex, MaterialTheme.colorScheme.primary)
                FilterChip(
                    selected = selectedListId == list.id,
                    onClick = { onSelectList(list.id) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    },
                    label = { Text(list.name) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("editor_list_${list.id}")
                )
            }
            FilterChip(
                selected = false,
                onClick = onCreateListClick,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("New List") },
                shape = RoundedCornerShape(50),
                modifier = Modifier.testTag("editor_new_list_button")
            )
        }
    }
}

@Composable
fun SchedulePickerTile(
    label: String,
    valueText: String,
    icon: ImageVector,
    isSet: Boolean,
    isError: Boolean,
    onClick: () -> Unit,
    onClear: (() -> Unit)?,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
    clearTag: String = ""
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            when {
                isError -> MaterialTheme.colorScheme.error
                isSet -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
        ),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSet) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = if (isSet) JetBrainsMonoFamily else MaterialTheme.typography.bodyMedium.fontFamily
                    ),
                    color = if (isSet) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSet && onClear != null) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .size(48.dp)
                        .let { if (clearTag.isNotEmpty()) it.testTag(clearTag) else it }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = clearContentDescription,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StartAndDeadlineSection(
    startDate: Long?,
    startTime: String?,
    deadline: Long?,
    startTimeError: String?,
    deadlineError: String?,
    onPickStartDate: () -> Unit,
    onClearStartDate: () -> Unit,
    onPickStartTime: () -> Unit,
    onClearStartTime: () -> Unit,
    onPickDeadline: () -> Unit,
    onClearDeadline: () -> Unit
) {
    EditorSectionCard(
        title = "Start Schedule & Deadline",
        icon = Icons.Rounded.Schedule
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SchedulePickerTile(
                label = stringResource(R.string.field_start_date_label),
                valueText = DateTimeUtils.formatDisplayDate(startDate, emptyLabel = stringResource(R.string.field_not_set)),
                icon = Icons.Rounded.PlayArrow,
                isSet = startDate != null,
                isError = false,
                onClick = onPickStartDate,
                onClear = onClearStartDate,
                clearContentDescription = stringResource(R.string.action_clear_date),
                clearTag = "clear_start_date_button",
                modifier = Modifier
                    .weight(1f)
                    .testTag("start_date_picker_button")
            )
            SchedulePickerTile(
                label = stringResource(R.string.field_start_time_label),
                valueText = DateTimeUtils.formatDisplayTime(startTime) ?: stringResource(R.string.field_not_set),
                icon = Icons.Rounded.AccessTime,
                isSet = startTime != null,
                isError = startTimeError != null,
                onClick = onPickStartTime,
                onClear = onClearStartTime,
                clearContentDescription = stringResource(R.string.action_clear_time),
                clearTag = "clear_start_time_button",
                modifier = Modifier
                    .weight(1f)
                    .testTag("start_time_picker_button")
            )
        }

        SchedulePickerTile(
            label = stringResource(R.string.field_deadline_label),
            valueText = DateTimeUtils.formatDisplayDate(deadline, emptyLabel = stringResource(R.string.field_not_set)),
            icon = Icons.Rounded.Flag,
            isSet = deadline != null,
            isError = deadlineError != null,
            onClick = onPickDeadline,
            onClear = onClearDeadline,
            clearContentDescription = stringResource(R.string.action_clear_date),
            clearTag = "clear_deadline_button",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("deadline_picker_button")
        )

        val err = startTimeError ?: deadlineError
        if (err != null) {
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrenceSelectorSection(
    recurrenceType: RecurrenceType,
    recurrenceInterval: Int,
    recurrenceUnit: RecurrenceUnit,
    onRecurrenceTypeChange: (RecurrenceType) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onUnitChange: (RecurrenceUnit) -> Unit,
    recurrenceError: String? = null
) {
    val activeLabel = RecurrenceCalculator.formatRecurrenceLabel(recurrenceType, recurrenceInterval, recurrenceUnit)
    EditorSectionCard(
        title = stringResource(R.string.field_recurrence_label),
        icon = Icons.Rounded.Repeat,
        trailing = activeLabel?.let { label ->
            {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecurrenceType.entries.forEach { option ->
                FilterChip(
                    selected = recurrenceType == option,
                    onClick = { onRecurrenceTypeChange(option) },
                    label = { Text(option.displayName) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.testTag("recurrence_chip_${option.name.lowercase()}")
                )
            }
        }

        if (recurrenceType == RecurrenceType.CUSTOM) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    if (recurrenceError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Repeat every",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (recurrenceInterval > 1) onIntervalChange(recurrenceInterval - 1) },
                                enabled = recurrenceInterval > 1,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("recurrence_interval_minus")
                            ) {
                                Icon(Icons.Rounded.Remove, contentDescription = "Decrease interval")
                            }
                            Text(
                                text = recurrenceInterval.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .testTag("recurrence_interval_value")
                            )
                            IconButton(
                                onClick = { if (recurrenceInterval < 365) onIntervalChange(recurrenceInterval + 1) },
                                enabled = recurrenceInterval < 365,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("recurrence_interval_plus")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Increase interval")
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RecurrenceUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = recurrenceUnit == unit,
                                onClick = { onUnitChange(unit) },
                                label = { Text(unit.displayName) },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("recurrence_unit_${unit.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        if (recurrenceError != null) {
            Text(
                text = recurrenceError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .testTag("recurrence_error_text")
            )
        }
    }
}

@Composable
fun SubtasksEditorSection(
    subtasks: List<Subtask>,
    onSubtasksChange: (List<Subtask>) -> Unit,
    subtaskError: String? = null
) {
    var newSubtaskTitle by remember { mutableStateOf("") }
    var editingSubtaskId by remember { mutableStateOf<String?>(null) }
    var editingSubtaskText by remember { mutableStateOf("") }
    var limitReachedMessage by remember { mutableStateOf<String?>(null) }

    fun addSubtask() {
        val clean = newSubtaskTitle.trim()
        if (clean.isEmpty()) return
        if (subtasks.size >= TaskValidator.MAX_SUBTASKS) {
            limitReachedMessage = "Maximum ${TaskValidator.MAX_SUBTASKS} subtasks per task."
            return
        }
        limitReachedMessage = null
        onSubtasksChange(
            subtasks + Subtask(
                id = UUID.randomUUID().toString(),
                title = clean.take(TaskValidator.MAX_TITLE_LENGTH),
                isCompleted = false
            )
        )
        newSubtaskTitle = ""
    }

    val completedCount = subtasks.count { it.isCompleted }

    EditorSectionCard(
        title = stringResource(R.string.field_subtasks_label),
        icon = Icons.Rounded.Checklist,
        trailing = {
            Text(
                text = "$completedCount/${subtasks.size}",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        if (subtasks.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { completedCount.toFloat() / subtasks.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                strokeCap = StrokeCap.Round
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newSubtaskTitle,
                onValueChange = { newSubtaskTitle = it },
                placeholder = { Text(stringResource(R.string.add_subtask_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addSubtask() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_subtask_input")
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (newSubtaskTitle.isNotBlank()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = newSubtaskTitle.isNotBlank()) { addSubtask() }
                    .testTag("add_subtask_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_add_subtask),
                        tint = if (newSubtaskTitle.isNotBlank()) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

            val displayedSubtaskError = subtaskError ?: limitReachedMessage
            if (displayedSubtaskError != null) {
                Text(
                    text = displayedSubtaskError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .testTag("subtask_error_text")
                )
            }

        if (subtasks.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    subtasks.forEachIndexed { index, subtask ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                        val isEditingThis = editingSubtaskId == subtask.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    onSubtasksChange(
                                        subtasks.map {
                                            if (it.id == subtask.id) it.copy(isCompleted = !it.isCompleted) else it
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("subtask_toggle_${subtask.id}")
                            ) {
                                Icon(
                                    imageVector = if (subtask.isCompleted) {
                                        Icons.Rounded.CheckBox
                                    } else {
                                        Icons.Rounded.CheckBoxOutlineBlank
                                    },
                                    contentDescription = "Toggle subtask",
                                    tint = if (subtask.isCompleted) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            if (isEditingThis) {
                                fun commitSubtaskEdit() {
                                    val clean = editingSubtaskText.trim()
                                    if (clean.isNotEmpty()) {
                                        onSubtasksChange(
                                            subtasks.map {
                                                if (it.id == subtask.id) {
                                                    it.copy(title = clean.take(TaskValidator.MAX_TITLE_LENGTH))
                                                } else it
                                            }
                                        )
                                    }
                                    editingSubtaskId = null
                                }
                                OutlinedTextField(
                                    value = editingSubtaskText,
                                    onValueChange = { editingSubtaskText = it },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { commitSubtaskEdit() }),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("edit_subtask_input_${subtask.id}")
                                )
                                IconButton(
                                    onClick = { commitSubtaskEdit() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("save_subtask_edit_${subtask.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Save subtask",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                Text(
                                    text = subtask.title,
                                    style = MaterialTheme.typography.bodyMedium,
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
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        editingSubtaskId = subtask.id
                                        editingSubtaskText = subtask.title
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("edit_subtask_${subtask.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.cd_edit_subtask),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        limitReachedMessage = null
                                        onSubtasksChange(subtasks.filterNot { it.id == subtask.id })
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("delete_subtask_${subtask.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.cd_delete_subtask),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
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
