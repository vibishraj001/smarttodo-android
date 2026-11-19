package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.TaskValidator
import com.example.domain.model.RecurrenceType
import com.example.domain.model.RecurrenceUnit
import com.example.domain.model.Subtask
import com.example.domain.model.Task
import com.example.domain.model.TaskCategory
import com.example.domain.model.TaskList
import com.example.domain.model.TaskPriority
import com.example.ui.components.EditorSectionCard
import com.example.ui.components.ManageTaskListsDialog
import com.example.ui.components.RecurrenceSelectorSection
import com.example.ui.components.SchedulePickerTile
import com.example.ui.components.StartAndDeadlineSection
import com.example.ui.components.SubtasksEditorSection
import com.example.ui.components.TaskListSelectorSection
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.PriorityMediumColor
import com.example.utils.DateTimeUtils
import java.util.Calendar

data class TaskEditorPayload(
    val title: String,
    val description: String?,
    val dueDate: Long?,
    val dueTime: String?,
    val startDate: Long?,
    val startTime: String?,
    val deadline: Long?,
    val priority: TaskPriority,
    val category: TaskCategory,
    val listId: String?,
    val isStarred: Boolean,
    val recurrenceType: RecurrenceType,
    val recurrenceInterval: Int,
    val recurrenceUnit: RecurrenceUnit,
    val subtasks: List<Subtask>
)

private enum class ActiveDateField { DUE_DATE, START_DATE, DEADLINE }
private enum class ActiveTimeField { DUE_TIME, START_TIME }

private val SubtasksStateSaver = listSaver<List<Subtask>, Any>(
    save = { list ->
        list.flatMap { sub -> listOf(sub.id, sub.title, sub.isCompleted) }
    },
    restore = { flat ->
        flat.chunked(3).mapNotNull { chunk ->
            if (chunk.size == 3) {
                Subtask(
                    id = chunk[0] as? String ?: return@mapNotNull null,
                    title = chunk[1] as? String ?: return@mapNotNull null,
                    isCompleted = chunk[2] as? Boolean ?: false
                )
            } else null
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(
    existingTask: Task?,
    taskLists: List<TaskList>,
    defaultListId: String?,
    isSaving: Boolean,
    onSave: (TaskEditorPayload) -> Unit,
    onCreateList: (name: String, colorHex: String) -> Unit,
    onRenameList: (listId: String, newName: String, colorHex: String) -> Unit,
    onDeleteList: (TaskList) -> Unit,
    onOpenWorkspace: () -> Unit,
    onCancel: () -> Unit,
    onPermissionDeniedMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isEditing = existingTask != null

    var title by rememberSaveable(existingTask?.id) { mutableStateOf(existingTask?.title ?: "") }
    var description by rememberSaveable(existingTask?.id) { mutableStateOf(existingTask?.description ?: "") }
    var dueDate by rememberSaveable(existingTask?.id) {
        mutableStateOf(if (isEditing) existingTask.dueDate else DateTimeUtils.todayUtcMillis())
    }
    var dueTime by rememberSaveable(existingTask?.id) { mutableStateOf(existingTask?.dueTime) }
    var startDate by rememberSaveable(existingTask?.id) { mutableStateOf(existingTask?.startDate) }
    var startTime by rememberSaveable(existingTask?.id) { mutableStateOf(existingTask?.startTime) }
    var deadline by rememberSaveable(existingTask?.id) { mutableStateOf(existingTask?.deadline) }
    var priority by rememberSaveable(existingTask?.id) {
        mutableStateOf(existingTask?.priorityEnum() ?: TaskPriority.MEDIUM)
    }
    var category by rememberSaveable(existingTask?.id) {
        mutableStateOf(existingTask?.categoryEnum() ?: TaskCategory.PERSONAL)
    }
    var listId by rememberSaveable(existingTask?.id) {
        mutableStateOf(if (isEditing) existingTask.listId else defaultListId)
    }
    var isStarred by rememberSaveable(existingTask?.id) {
        mutableStateOf(existingTask?.isStarred ?: false)
    }
    var recurrenceType by rememberSaveable(existingTask?.id) {
        mutableStateOf(existingTask?.recurrenceTypeEnum() ?: RecurrenceType.NONE)
    }
    var recurrenceInterval by rememberSaveable(existingTask?.id) {
        mutableIntStateOf(existingTask?.recurrenceInterval ?: 1)
    }
    var recurrenceUnit by rememberSaveable(existingTask?.id) {
        mutableStateOf(existingTask?.recurrenceUnitEnum() ?: RecurrenceUnit.DAYS)
    }
    var subtasks by rememberSaveable(existingTask?.id, stateSaver = SubtasksStateSaver) {
        mutableStateOf(existingTask?.subtasks ?: emptyList())
    }

    LaunchedEffect(taskLists) {
        if (listId != null && taskLists.none { it.id == listId }) {
            listId = null
        }
    }

    var titleError by remember { mutableStateOf<String?>(null) }
    var descriptionError by remember { mutableStateOf<String?>(null) }
    var dueDateError by remember { mutableStateOf<String?>(null) }
    var dueTimeError by remember { mutableStateOf<String?>(null) }
    var startTimeError by remember { mutableStateOf<String?>(null) }
    var deadlineError by remember { mutableStateOf<String?>(null) }
    var recurrenceError by remember { mutableStateOf<String?>(null) }
    var subtaskError by remember { mutableStateOf<String?>(null) }

    var activeDateField by remember { mutableStateOf<ActiveDateField?>(null) }
    var activeTimeField by remember { mutableStateOf<ActiveTimeField?>(null) }
    var showManageListsDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            onPermissionDeniedMessage(
                "Notifications are disabled. You can enable reminders anytime in Settings."
            )
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun submitForm() {
        val cleanDescription = description.trim().takeIf { it.isNotEmpty() }
        val validation = TaskValidator.validate(
            title = title,
            description = cleanDescription,
            dueDate = dueDate,
            dueTime = dueTime,
            startDate = startDate,
            startTime = startTime,
            deadline = deadline,
            recurrenceInterval = recurrenceInterval,
            subtasks = subtasks
        )
        titleError = validation.titleError
        descriptionError = validation.descriptionError
        dueDateError = validation.dueDateError
        dueTimeError = validation.dueTimeError
        startTimeError = validation.startTimeError
        deadlineError = validation.deadlineError
        recurrenceError = validation.recurrenceError
        subtaskError = validation.subtaskError
        if (validation.isValid) {
            onSave(
                TaskEditorPayload(
                    title = title,
                    description = cleanDescription,
                    dueDate = dueDate,
                    dueTime = dueTime,
                    startDate = startDate,
                    startTime = startTime,
                    deadline = deadline,
                    priority = priority,
                    category = category,
                    listId = listId,
                    isStarred = isStarred,
                    recurrenceType = recurrenceType,
                    recurrenceInterval = recurrenceInterval,
                    recurrenceUnit = recurrenceUnit,
                    subtasks = subtasks
                )
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(
                            if (isEditing) R.string.edit_task_title else R.string.add_task_title
                        ),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isStarred = !isStarred },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("editor_star_toggle")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isStarred) PriorityMediumColor.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isStarred) Icons.Rounded.Star else Icons.Outlined.StarBorder,
                                contentDescription = stringResource(
                                    if (isStarred) R.string.cd_unstar_task else R.string.cd_star_task
                                ),
                                tint = if (isStarred) PriorityMediumColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
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
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Unified Primary Title & Notes Card
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (titleError != null || descriptionError != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                if (titleError != null) titleError = null
                            },
                            label = { Text(stringResource(R.string.field_title_label)) },
                            placeholder = { Text(stringResource(R.string.field_title_placeholder)) },
                            isError = titleError != null,
                            textStyle = MaterialTheme.typography.titleMedium,
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = titleError ?: "")
                                    Text(
                                        text = "${title.trim().length}/${TaskValidator.MAX_TITLE_LENGTH}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                    )
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                errorBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("task_title_input")
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = {
                                description = it
                                if (descriptionError != null) descriptionError = null
                            },
                            label = { Text(stringResource(R.string.field_description_label)) },
                            placeholder = { Text(stringResource(R.string.field_description_placeholder)) },
                            isError = descriptionError != null,
                            supportingText = descriptionError?.let { msg -> { Text(msg) } },
                            minLines = 3,
                            maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                errorBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("task_description_input")
                        )
                    }
                }

                // Due Date & Reminder Card
                EditorSectionCard(
                    title = stringResource(R.string.field_due_date_label),
                    icon = Icons.Rounded.CalendarToday
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SchedulePickerTile(
                            label = stringResource(R.string.field_due_date_label),
                            valueText = DateTimeUtils.formatDisplayDate(dueDate),
                            icon = Icons.Rounded.CalendarToday,
                            isSet = dueDate != null,
                            isError = dueDateError != null,
                            onClick = { activeDateField = ActiveDateField.DUE_DATE },
                            onClear = {
                                dueDate = null
                                dueTime = null
                                dueDateError = null
                                dueTimeError = null
                            },
                            clearContentDescription = stringResource(R.string.action_clear_date),
                            clearTag = "clear_due_date_button",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("due_date_picker_button")
                        )
                        SchedulePickerTile(
                            label = stringResource(R.string.field_due_time_label),
                            valueText = DateTimeUtils.formatDisplayTime(dueTime)
                                ?: stringResource(R.string.field_no_time_set),
                            icon = Icons.Rounded.AccessTime,
                            isSet = dueTime != null,
                            isError = dueTimeError != null,
                            onClick = { activeTimeField = ActiveTimeField.DUE_TIME },
                            onClear = {
                                dueTime = null
                                dueTimeError = null
                            },
                            clearContentDescription = stringResource(R.string.action_clear_time),
                            clearTag = "clear_due_time_button",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("due_time_picker_button")
                        )
                    }

                    val scheduleError = dueDateError ?: dueTimeError
                    if (scheduleError != null) {
                        Text(
                            text = scheduleError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .testTag("schedule_error_text")
                        )
                    } else if (dueTime != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reminder will notify before ${DateTimeUtils.formatDisplayTime(dueTime)}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Priority & Category Card
                EditorSectionCard(
                    title = "${stringResource(R.string.field_priority_label)} & ${stringResource(R.string.field_category_label)}",
                    icon = Icons.AutoMirrored.Rounded.Label
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(TaskPriority.LOW, TaskPriority.MEDIUM, TaskPriority.HIGH).forEach { option ->
                                val dotColor = when (option) {
                                    TaskPriority.HIGH -> PriorityHighColor
                                    TaskPriority.MEDIUM -> PriorityMediumColor
                                    TaskPriority.LOW -> PriorityLowColor
                                }
                                FilterChip(
                                    selected = priority == option,
                                    onClick = { priority = option },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                        )
                                    },
                                    label = { Text(option.displayName) },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = dotColor.copy(alpha = 0.16f),
                                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.testTag("priority_chip_${option.name.lowercase()}")
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TaskCategory.entries.forEach { option ->
                                FilterChip(
                                    selected = category == option,
                                    onClick = { category = option },
                                    label = { Text(option.displayName) },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier.testTag("category_chip_${option.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // Custom Task List Selector
                TaskListSelectorSection(
                    taskLists = taskLists,
                    selectedListId = listId,
                    onSelectList = { listId = it },
                    onCreateListClick = { showManageListsDialog = true }
                )

                // Start Date, Start Time, & Hard Deadline
                StartAndDeadlineSection(
                    startDate = startDate,
                    startTime = startTime,
                    deadline = deadline,
                    startTimeError = startTimeError,
                    deadlineError = deadlineError,
                    onPickStartDate = { activeDateField = ActiveDateField.START_DATE },
                    onClearStartDate = {
                        startDate = null
                        startTime = null
                        startTimeError = null
                    },
                    onPickStartTime = { activeTimeField = ActiveTimeField.START_TIME },
                    onClearStartTime = {
                        startTime = null
                        startTimeError = null
                    },
                    onPickDeadline = { activeDateField = ActiveDateField.DEADLINE },
                    onClearDeadline = {
                        deadline = null
                        deadlineError = null
                    }
                )

                // Recurrence Section
                RecurrenceSelectorSection(
                    recurrenceType = recurrenceType,
                    recurrenceInterval = recurrenceInterval,
                    recurrenceUnit = recurrenceUnit,
                    onRecurrenceTypeChange = {
                        recurrenceType = it
                        recurrenceError = null
                    },
                    onIntervalChange = {
                        recurrenceInterval = it
                        recurrenceError = null
                    },
                    onUnitChange = { recurrenceUnit = it },
                    recurrenceError = recurrenceError
                )

                // Subtasks Section
                SubtasksEditorSection(
                    subtasks = subtasks,
                    onSubtasksChange = {
                        subtasks = it
                        subtaskError = null
                    },
                    subtaskError = subtaskError
                )

                // Google Workspace Shortcut Card when editing an existing task
                if (isEditing) {
                    EditorSectionCard(
                        title = stringResource(R.string.workspace_title),
                        icon = Icons.Rounded.Hub
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = onOpenWorkspace,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("editor_calendar_sync_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EventAvailable,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (!existingTask.calendarEventId.isNullOrBlank()) "Calendar Synced" else "Calendar Sync",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            OutlinedButton(
                                onClick = onOpenWorkspace,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("editor_share_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Forum,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.workspace_share_chat),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("cancel_task_button")
                    ) {
                        Text(text = stringResource(R.string.action_cancel))
                    }

                    Button(
                        onClick = { submitForm() },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(54.dp)
                            .testTag("save_task_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    if (isEditing) R.string.action_update else R.string.action_save
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Unified DatePickerDialog for Due Date, Start Date, and Deadline
    activeDateField?.let { field ->
        val currentMillis = when (field) {
            ActiveDateField.DUE_DATE -> dueDate
            ActiveDateField.START_DATE -> startDate
            ActiveDateField.DEADLINE -> deadline
        } ?: DateTimeUtils.todayUtcMillis()

        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = currentMillis)
        DatePickerDialog(
            onDismissRequest = { activeDateField = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val normalized = DateTimeUtils.normalizeToUtcStartOfDay(millis)
                            when (field) {
                                ActiveDateField.DUE_DATE -> {
                                    dueDate = normalized
                                    dueDateError = null
                                }
                                ActiveDateField.START_DATE -> {
                                    startDate = normalized
                                    deadlineError = null
                                }
                                ActiveDateField.DEADLINE -> {
                                    deadline = normalized
                                    deadlineError = null
                                }
                            }
                        }
                        activeDateField = null
                    },
                    modifier = Modifier.testTag("date_picker_confirm_button")
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(
                    onClick = { activeDateField = null },
                    modifier = Modifier.testTag("date_picker_cancel_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Unified TimePicker Dialog for Due Time and Start Time
    activeTimeField?.let { field ->
        val currentIso = when (field) {
            ActiveTimeField.DUE_TIME -> dueTime
            ActiveTimeField.START_TIME -> startTime
        }
        val defaultHourMinute = remember(field, currentIso) {
            DateTimeUtils.parseHourMinute(currentIso) ?: run {
                val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
                cal.get(Calendar.HOUR_OF_DAY) to 0
            }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = defaultHourMinute.first,
            initialMinute = defaultHourMinute.second,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { activeTimeField = null },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    stringResource(
                        if (field == ActiveTimeField.DUE_TIME) R.string.field_due_time_label
                        else R.string.field_start_time_label
                    )
                )
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val formatted = DateTimeUtils.formatTime(timePickerState.hour, timePickerState.minute)
                        when (field) {
                            ActiveTimeField.DUE_TIME -> {
                                if (dueDate == null) {
                                    dueDate = DateTimeUtils.todayUtcMillis()
                                    dueDateError = null
                                }
                                dueTime = formatted
                                dueTimeError = null
                                requestNotificationPermissionIfNeeded()
                            }
                            ActiveTimeField.START_TIME -> {
                                if (startDate == null) {
                                    startDate = DateTimeUtils.todayUtcMillis()
                                }
                                startTime = formatted
                                startTimeError = null
                            }
                        }
                        activeTimeField = null
                    },
                    modifier = Modifier.testTag("time_picker_confirm_button")
                ) { Text("Set time") }
            },
            dismissButton = {
                TextButton(
                    onClick = { activeTimeField = null },
                    modifier = Modifier.testTag("time_picker_cancel_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showManageListsDialog) {
        ManageTaskListsDialog(
            taskLists = taskLists,
            onCreateList = onCreateList,
            onRenameList = onRenameList,
            onDeleteList = { deleted ->
                if (listId == deleted.id) {
                    listId = null
                }
                onDeleteList(deleted)
            },
            onDismiss = { showManageListsDialog = false }
        )
    }
}
