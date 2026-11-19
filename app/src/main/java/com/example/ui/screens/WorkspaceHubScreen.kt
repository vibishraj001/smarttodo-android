package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.Task
import com.example.ui.components.SectionEyebrow
import com.example.ui.theme.HeroDarkEnd
import com.example.ui.theme.HeroDarkStart
import com.example.ui.theme.HeroGlowBlue
import com.example.ui.theme.HeroGlowCyan
import com.example.ui.theme.HeroInkPrimary
import com.example.ui.theme.HeroInkSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PriorityLowColor
import com.example.viewmodel.WorkspaceUiState
import com.example.workspace.GoogleWorkspaceAuthManager
import com.example.workspace.WorkspaceAuthResult
import com.example.workspace.WorkspaceCalendarEvent
import com.example.workspace.WorkspaceChatSpace
import com.example.workspace.WorkspaceEmailItem
import kotlinx.coroutines.launch

private enum class WorkspaceTab(val labelRes: Int) {
    CALENDAR(R.string.workspace_tab_calendar),
    GMAIL(R.string.workspace_tab_gmail),
    CHAT(R.string.workspace_tab_chat)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceHubScreen(
    workspaceState: WorkspaceUiState,
    tasks: List<Task>,
    onConnectingChange: (Boolean) -> Unit,
    onTokenObtained: (String) -> Unit,
    onAuthError: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshAll: () -> Unit,
    onSyncTaskToCalendar: (Task) -> Unit,
    onImportCalendarEvent: (WorkspaceCalendarEvent) -> Unit,
    onImportEmailAsTask: (WorkspaceEmailItem) -> Unit,
    onShareTaskToChat: (WorkspaceChatSpace, Task) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(WorkspaceTab.CALENDAR) }
    var selectedChatSpace by remember { mutableStateOf<WorkspaceChatSpace?>(null) }

    val activeSpace = selectedChatSpace
        ?.takeIf { sel -> workspaceState.chatSpaces.any { it.name == sel.name } }
        ?: workspaceState.chatSpaces.firstOrNull()

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            when (val res = GoogleWorkspaceAuthManager.handleAuthorizationIntentResult(context, activityResult.data)) {
                is WorkspaceAuthResult.Authorized -> onTokenObtained(res.accessToken)
                is WorkspaceAuthResult.Error -> onAuthError(res.message)
                is WorkspaceAuthResult.NeedsConsent -> onAuthError("Additional consent required.")
            }
        } else {
            onAuthError("Google Workspace authorization was cancelled.")
        }
    }

    fun startAuthorization() {
        onConnectingChange(true)
        scope.launch {
            when (val res = GoogleWorkspaceAuthManager.authorizeWorkspace(context)) {
                is WorkspaceAuthResult.Authorized -> onTokenObtained(res.accessToken)
                is WorkspaceAuthResult.NeedsConsent -> {
                    consentLauncher.launch(IntentSenderRequest.Builder(res.intentSender).build())
                }
                is WorkspaceAuthResult.Error -> onAuthError(res.message)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.workspace_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("workspace_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                actions = {
                    if (workspaceState.isConnected) {
                        IconButton(
                            onClick = onRefreshAll,
                            modifier = Modifier.testTag("workspace_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh Workspace data"
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
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .testTag("workspace_hub_list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Signature Midnight Workspace Hero Card
                item(key = "connection_card") {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(colors = listOf(HeroDarkStart, HeroDarkEnd))
                                )
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(HeroGlowBlue.copy(alpha = 0.3f), Color.Transparent),
                                            center = Offset(size.width * 0.9f, size.height * 0.15f),
                                            radius = size.width * 0.6f
                                        ),
                                        radius = size.width * 0.6f,
                                        center = Offset(size.width * 0.9f, size.height * 0.15f)
                                    )
                                }
                                .padding(20.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.White.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (workspaceState.isConnected) Icons.Rounded.CloudDone else Icons.Rounded.Sync,
                                            contentDescription = null,
                                            tint = if (workspaceState.isConnected) PriorityLowColor else HeroGlowCyan,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (workspaceState.isConnected) {
                                                stringResource(R.string.workspace_connected_badge)
                                            } else {
                                                stringResource(R.string.workspace_title)
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = HeroInkPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.workspace_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = HeroInkSecondary
                                        )
                                    }
                                }

                                workspaceState.authError?.let { err ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = err,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }

                                if (!workspaceState.isConnected) {
                                    Button(
                                        onClick = { startAuthorization() },
                                        enabled = !workspaceState.isConnecting,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = HeroGlowCyan,
                                            contentColor = HeroDarkStart
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("workspace_connect_button")
                                    ) {
                                        if (workspaceState.isConnecting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = HeroDarkStart,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                        }
                                        Text(
                                            text = stringResource(R.string.workspace_connect_button),
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = onDisconnect,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HeroInkPrimary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("workspace_disconnect_button")
                                    ) {
                                        Text(stringResource(R.string.workspace_disconnect_button))
                                    }
                                }
                            }
                        }
                    }
                }

                if (workspaceState.isConnected) {
                    item(key = "workspace_tabs") {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("workspace_tabs_row")
                        ) {
                            val tabs = listOf(
                                WorkspaceTab.CALENDAR to Icons.Rounded.CalendarMonth,
                                WorkspaceTab.GMAIL to Icons.Rounded.Email,
                                WorkspaceTab.CHAT to Icons.Rounded.Forum
                            )
                            tabs.forEachIndexed { index, (tab, icon) ->
                                SegmentedButton(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                                    icon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("workspace_tab_${tab.name.lowercase()}")
                                ) {
                                    Text(stringResource(tab.labelRes))
                                }
                            }
                        }
                    }

                    when (selectedTab) {
                        WorkspaceTab.CALENDAR -> {
                            item(key = "cal_sync_header") {
                                SectionEyebrow(text = "Sync Tasks to Google Calendar")
                            }
                            val pendingTasks = tasks.filter { !it.isCompleted }.take(10)
                            if (pendingTasks.isEmpty()) {
                                item(key = "cal_no_tasks") {
                                    WorkspaceInfoBanner("No pending tasks to sync right now.")
                                }
                            } else {
                                items(pendingTasks, key = { "sync_${it.id}" }) { task ->
                                    val isSynced = !task.calendarEventId.isNullOrBlank()
                                    Card(
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = task.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (isSynced) "Linked with Google Calendar" else "Ready to add to primary calendar",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            FilledTonalButton(
                                                onClick = { onSyncTaskToCalendar(task) },
                                                enabled = !workspaceState.isSyncingAction,
                                                shape = RoundedCornerShape(50),
                                                modifier = Modifier.testTag("workspace_sync_task_${task.id}")
                                            ) {
                                                Icon(
                                                    imageVector = if (isSynced) Icons.Rounded.CheckCircle else Icons.Rounded.EventAvailable,
                                                    contentDescription = null,
                                                    tint = if (isSynced) PriorityLowColor else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (isSynced) "Update" else "Sync")
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "cal_events_header") {
                                Spacer(modifier = Modifier.height(6.dp))
                                SectionEyebrow(text = "Upcoming Calendar Events")
                            }
                            if (workspaceState.isLoadingCalendar) {
                                item(key = "cal_loading") { WorkspaceLoadingRow() }
                            } else if (workspaceState.calendarError != null) {
                                item(key = "cal_error") { WorkspaceInfoBanner(workspaceState.calendarError, isError = true) }
                            } else if (workspaceState.calendarEvents.isEmpty()) {
                                item(key = "cal_empty") { WorkspaceInfoBanner("No upcoming events found in your primary Google Calendar.") }
                            } else {
                                items(workspaceState.calendarEvents, key = { "evt_${it.id}" }) { event ->
                                    Card(
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = event.summary,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = event.displayTimeRange,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            OutlinedButton(
                                                onClick = { onImportCalendarEvent(event) },
                                                shape = RoundedCornerShape(50),
                                                modifier = Modifier.testTag("workspace_import_event_${event.id}")
                                            ) {
                                                Icon(Icons.Rounded.AddTask, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(stringResource(R.string.workspace_import_task))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        WorkspaceTab.GMAIL -> {
                            item(key = "gmail_header") {
                                SectionEyebrow(text = "Turn Gmail Messages into Tasks")
                            }
                            if (workspaceState.isLoadingGmail) {
                                item(key = "gmail_loading") { WorkspaceLoadingRow() }
                            } else if (workspaceState.gmailError != null) {
                                item(key = "gmail_error") { WorkspaceInfoBanner(workspaceState.gmailError, isError = true) }
                            } else if (workspaceState.emails.isEmpty()) {
                                item(key = "gmail_empty") { WorkspaceInfoBanner("No recent inbox messages found.") }
                            } else {
                                items(workspaceState.emails, key = { "mail_${it.id}" }) { email ->
                                    Card(
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = email.subject,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "From: ${email.from}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (email.snippet.isNotBlank()) {
                                                Text(
                                                    text = email.snippet,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                FilledTonalButton(
                                                    onClick = { onImportEmailAsTask(email) },
                                                    shape = RoundedCornerShape(50),
                                                    modifier = Modifier.testTag("workspace_import_email_${email.id}")
                                                ) {
                                                    Icon(Icons.Rounded.AddTask, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(stringResource(R.string.workspace_import_task))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        WorkspaceTab.CHAT -> {
                            item(key = "chat_header") {
                                SectionEyebrow(text = "Share Tasks to Google Chat")
                            }
                            if (workspaceState.isLoadingChat) {
                                item(key = "chat_loading") { WorkspaceLoadingRow() }
                            } else if (workspaceState.chatError != null) {
                                item(key = "chat_error") { WorkspaceInfoBanner(workspaceState.chatError, isError = true) }
                            } else if (workspaceState.chatSpaces.isEmpty()) {
                                item(key = "chat_empty") {
                                    WorkspaceInfoBanner("No Google Chat spaces found for this Google account.")
                                }
                            } else {
                                item(key = "chat_spaces_picker") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "1. Select a Google Chat Space",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        workspaceState.chatSpaces.forEach { space ->
                                            val isSelected = activeSpace?.name == space.name
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable { selectedChatSpace = space }
                                                    .testTag("workspace_chat_space_${space.name}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Forum,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = space.displayName,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                        ),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.CheckCircle,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item(key = "chat_tasks_label") {
                                    Text(
                                        text = "2. Send a Task Update to ${activeSpace?.displayName ?: "Space"}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val shareableTasks = tasks.filter { !it.isCompleted }.take(10)
                                if (shareableTasks.isEmpty()) {
                                    item(key = "chat_no_tasks") {
                                        WorkspaceInfoBanner("No pending tasks to share right now.")
                                    }
                                } else {
                                    items(shareableTasks, key = { "chat_${it.id}" }) { task ->
                                        Card(
                                            shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = task.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${task.priority} · ${task.category}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Button(
                                                    onClick = {
                                                        activeSpace?.let { space ->
                                                            onShareTaskToChat(space, task)
                                                        }
                                                    },
                                                    enabled = activeSpace != null && !workspaceState.isSyncingAction,
                                                    shape = RoundedCornerShape(50),
                                                    modifier = Modifier.testTag("workspace_send_chat_${task.id}")
                                                ) {
                                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Send")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text("Loading from Google Workspace…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WorkspaceInfoBanner(message: String, isError: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}
