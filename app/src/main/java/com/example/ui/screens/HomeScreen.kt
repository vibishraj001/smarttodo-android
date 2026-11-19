package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.domain.model.Task
import com.example.domain.model.TaskList
import com.example.model.DashboardStats
import com.example.model.TaskFilter
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.ConfirmDeleteTaskDialog
import com.example.ui.components.EmptyTasksView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingStateView
import com.example.ui.components.QuickAddTaskBar
import com.example.ui.components.TaskCard
import com.example.ui.theme.HeroDarkEnd
import com.example.ui.theme.HeroDarkStart
import com.example.ui.theme.HeroGlowBlue
import com.example.ui.theme.HeroGlowCyan
import com.example.ui.theme.HeroInkPrimary
import com.example.ui.theme.HeroInkSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.PriorityMediumColor
import com.example.ui.theme.SectionEyebrowStyle
import com.example.utils.DateTimeUtils
import com.example.utils.GreetingPeriod
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    tasksState: UiState<List<Task>>,
    taskLists: List<TaskList>,
    stats: DashboardStats,
    profile: UserProfile,
    isWorkspaceConnected: Boolean,
    onQuickAddTask: (title: String, dueToday: Boolean, isStarred: Boolean) -> Unit,
    onToggleTask: (Task) -> Unit,
    onToggleStar: (Task) -> Unit,
    onToggleSubtask: (Task, String) -> Unit,
    onEditTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onNavigateToFilter: (TaskFilter) -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenHistory: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var taskPendingDelete by remember { mutableStateOf<Task?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val listsById = remember(taskLists) { taskLists.associateBy { it.id } }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentTimeMillis = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val greetingLabel = when (DateTimeUtils.getGreetingType(currentTimeMillis)) {
        GreetingPeriod.MORNING -> stringResource(R.string.greeting_morning)
        GreetingPeriod.AFTERNOON -> stringResource(R.string.greeting_afternoon)
        GreetingPeriod.EVENING -> stringResource(R.string.greeting_evening)
    }
    val firstName = profile.name.trim().substringBefore(" ").takeIf { it.isNotBlank() }
    val greetingText = if (firstName != null) "$greetingLabel, $firstName" else greetingLabel
    val todayHeader = remember(currentTimeMillis) {
        DateTimeUtils.formatFullDateHeader(currentTimeMillis)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = stats.completionProgress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "completionProgress"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .testTag("home_screen_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Editorial Top Header
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = stringResource(R.string.app_name).uppercase(),
                                style = SectionEyebrowStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "·",
                                style = SectionEyebrowStyle,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = todayHeader.uppercase(),
                                style = SectionEyebrowStyle.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("home_date")
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = greetingText,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("home_greeting")
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Signature Monogram Avatar
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.name.firstOrNull()?.uppercase() ?: "S",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Signature Midnight Focus Hero Card with Atmospheric Glow & Progress Ring
            item(key = "progress_card") {
                val pct = (stats.completionProgress * 100f).roundToInt()
                val statusHint = when {
                    stats.totalTasks == 0 -> "Capture your first priority below"
                    stats.overdueTasks > 0 -> "${stats.overdueTasks} overdue ${if (stats.overdueTasks == 1) "task needs" else "tasks need"} attention"
                    stats.pendingTasks == 0 -> "Every task complete — great momentum"
                    else -> "${stats.pendingTasks} remaining in your workspace"
                }

                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_progress_card")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(HeroDarkStart, HeroDarkEnd)
                                )
                            )
                            .drawBehind {
                                // Soft ambient cobalt radial glow (top-right)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            HeroGlowBlue.copy(alpha = 0.34f),
                                            Color.Transparent
                                        ),
                                        center = Offset(size.width * 0.88f, size.height * 0.16f),
                                        radius = size.width * 0.62f
                                    ),
                                    radius = size.width * 0.62f,
                                    center = Offset(size.width * 0.88f, size.height * 0.16f)
                                )
                                // Subtle cyan rim highlight (bottom-left)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            HeroGlowCyan.copy(alpha = 0.18f),
                                            Color.Transparent
                                        ),
                                        center = Offset(size.width * 0.12f, size.height * 0.95f),
                                        radius = size.width * 0.45f
                                    ),
                                    radius = size.width * 0.45f,
                                    center = Offset(size.width * 0.12f, size.height * 0.95f)
                                )
                            }
                            .padding(22.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.progress_title).uppercase(),
                                        style = SectionEyebrowStyle,
                                        color = HeroGlowCyan
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${stats.completedTasks} of ${stats.totalTasks} done",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = HeroInkPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = statusHint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HeroInkSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // Custom Canvas Progress Ring with Monospace Percentage
                                Box(
                                    modifier = Modifier.size(72.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val strokeWidth = 7.dp.toPx()
                                        val inset = strokeWidth / 2f
                                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                                        drawArc(
                                            color = Color.White.copy(alpha = 0.14f),
                                            startAngle = -90f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            topLeft = Offset(inset, inset),
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                        )
                                        drawArc(
                                            brush = Brush.sweepGradient(
                                                colors = listOf(HeroGlowCyan, HeroGlowBlue, HeroGlowCyan)
                                            ),
                                            startAngle = -90f,
                                            sweepAngle = (animatedProgress * 360f).coerceAtLeast(if (pct > 0) 6f else 0f),
                                            useCenter = false,
                                            topLeft = Offset(inset, inset),
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                        )
                                    }
                                    Text(
                                        text = "$pct%",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = HeroInkPrimary,
                                        modifier = Modifier.testTag("home_progress_percent")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(CircleShape)
                                    .testTag("home_progress_bar"),
                                color = HeroGlowCyan,
                                trackColor = Color.White.copy(alpha = 0.14f),
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            // Bento 2x2 Metrics Grid
            item(key = "stats_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardStatCard(
                            label = stringResource(R.string.stat_total),
                            count = stats.totalTasks,
                            icon = Icons.AutoMirrored.Rounded.Assignment,
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = { onNavigateToFilter(TaskFilter.ALL) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stat_total_card")
                        )
                        DashboardStatCard(
                            label = stringResource(R.string.stat_completed),
                            count = stats.completedTasks,
                            icon = Icons.Rounded.CheckCircle,
                            accentColor = PriorityLowColor,
                            onClick = { onNavigateToFilter(TaskFilter.COMPLETED) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stat_completed_card")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardStatCard(
                            label = stringResource(R.string.stat_pending),
                            count = stats.pendingTasks,
                            icon = Icons.Rounded.PendingActions,
                            accentColor = PriorityMediumColor,
                            onClick = { onNavigateToFilter(TaskFilter.PENDING) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stat_pending_card")
                        )
                        DashboardStatCard(
                            label = stringResource(R.string.stat_overdue),
                            count = stats.overdueTasks,
                            icon = Icons.Rounded.WarningAmber,
                            accentColor = PriorityHighColor,
                            onClick = { onNavigateToFilter(TaskFilter.OVERDUE) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stat_overdue_card")
                        )
                    }
                }
            }

            // Sleek Quick Access Pill Strip: Starred, Google Workspace, Task History
            item(key = "quick_shortcuts") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeShortcutPill(
                        label = "Starred",
                        badgeText = stats.starredTasks.toString(),
                        icon = Icons.Rounded.Star,
                        accent = PriorityMediumColor,
                        onClick = { onNavigateToFilter(TaskFilter.STARRED) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_starred_shortcut")
                    )
                    HomeShortcutPill(
                        label = "Workspace",
                        showLiveDot = isWorkspaceConnected,
                        icon = Icons.Rounded.Hub,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = onOpenWorkspace,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_workspace_shortcut")
                    )
                    HomeShortcutPill(
                        label = "History",
                        icon = Icons.Rounded.History,
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = onOpenHistory,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_history_shortcut")
                    )
                }
            }

            // Quick Capture Bar
            item(key = "quick_add") {
                QuickAddTaskBar(onQuickAdd = onQuickAddTask)
            }

            // Today's Tasks Section Header
            item(key = "today_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.todays_tasks_header),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (stats.todayTasks.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stats.todayTasks.size.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { onNavigateToFilter(TaskFilter.TODAY) },
                        modifier = Modifier.testTag("home_see_all_today_button")
                    ) {
                        Text(text = stringResource(R.string.see_all))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Today's Tasks State
            when (tasksState) {
                is UiState.Loading -> {
                    item(key = "loading") {
                        LoadingStateView(modifier = Modifier.height(180.dp))
                    }
                }
                is UiState.Error -> {
                    item(key = "error") {
                        ErrorStateView(message = tasksState.message, onRetry = onRetry)
                    }
                }
                is UiState.Success -> {
                    if (stats.todayTasks.isEmpty()) {
                        item(key = "empty_today") {
                            EmptyTasksView(
                                title = stringResource(R.string.empty_today_title),
                                subtitle = stringResource(R.string.empty_today_subtitle)
                            )
                        }
                    } else {
                        items(
                            items = stats.todayTasks,
                            key = { it.id }
                        ) { task ->
                            TaskCard(
                                task = task,
                                taskList = task.listId?.let { listsById[it] },
                                onToggleComplete = { onToggleTask(task) },
                                onToggleStar = { onToggleStar(task) },
                                onToggleSubtask = { subId -> onToggleSubtask(task, subId) },
                                onEditClick = { onEditTask(task) },
                                onDeleteClick = { taskPendingDelete = task }
                            )
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

@Composable
private fun HomeShortcutPill(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    showLiveDot: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (showLiveDot) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(PriorityLowColor)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (badgeText != null) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = accent
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardStatCard(
    label: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)),
        shadowElevation = 1.dp,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
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
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
