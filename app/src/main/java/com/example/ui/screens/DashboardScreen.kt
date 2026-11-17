package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.firebase.StudyverseRepository
import com.example.data.model.DeadlineCategory
import com.example.data.model.PriorityLevel
import com.example.data.model.UiState
import com.example.ui.components.PriorityPill
import com.example.ui.components.StudyProgressRing
import com.example.ui.components.WeeklyBarChart
import com.example.ui.components.formatMillisDate
import com.example.ui.components.formatMinutesCompact
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.StudyverseViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DailyQuotes = listOf(
    "Small daily improvements over time lead to stunning mastery.",
    "Focus is saying no to a hundred good distractions and yes to one clear goal.",
    "You don't have to be extreme, just consistent.",
    "Every completed chapter is one less worry on exam morning.",
    "Discipline turns academic goals into daily habits."
)

@Composable
fun DashboardScreen(
    viewModel: StudyverseViewModel,
    onNavigateRoute: (String) -> Unit
) {
    val profileUi by viewModel.userProfile.collectAsStateWithLifecycle()
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val sessionsUi by viewModel.sessionsState.collectAsStateWithLifecycle()
    val deadlinesUi by viewModel.deadlinesState.collectAsStateWithLifecycle()
    val chaptersUi by viewModel.chaptersState.collectAsStateWithLifecycle()
    val subjectsUi by viewModel.subjectsState.collectAsStateWithLifecycle()

    val profile = (profileUi as? UiState.Success)?.data
    val sessions = (sessionsUi as? UiState.Success)?.data.orEmpty()
    val deadlines = (deadlinesUi as? UiState.Success)?.data.orEmpty()
    val chapters = (chaptersUi as? UiState.Success)?.data.orEmpty()
    val subjects = (subjectsUi as? UiState.Success)?.data.orEmpty()

    val todayKey = StudyverseRepository.todayDateKey()
    val todaySessions = sessions.filter { it.dateKey == todayKey }.sortedBy { it.startHour * 60 + it.startMinute }
    val pendingDeadlines = deadlines.filter { !it.completed }.sortedBy { it.dueAtMillis }
    val upcomingExams = deadlines.filter {
        !it.completed && DeadlineCategory.safeParse(it.category).isExam
    }.sortedBy { it.dueAtMillis }
    val revisionDueCount = chapters.count {
        it.nextRevisionMillis != null && it.nextRevisionMillis <= System.currentTimeMillis() + 86_400_000L
    }

    val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hourOfDay < 12 -> "Good morning"
        hourOfDay < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val studentName = profile?.displayName?.split(" ")?.firstOrNull().orEmpty().ifBlank { "Scholar" }
    val quoteOfDay = remember {
        val dayIdx = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % DailyQuotes.size
        DailyQuotes[dayIdx]
    }

    var quickAiQuery by remember { mutableStateOf("") }
    var showBriefingDialog by remember { mutableStateOf(false) }

    val weeklyBars = remember(sessions) {
        val dayFormat = SimpleDateFormat("EEE", Locale.US)
        (-6..0).map { offset ->
            val key = StudyverseRepository.dateKeyWithOffset(offset)
            val label = dayFormat.format(Date(System.currentTimeMillis() + offset * 86_400_000L))
            val mins = sessions.filter { it.dateKey == key && it.completed }.sumOf { it.durationMinutes.toInt() }
            label to mins
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Greeting & Daily Briefing Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_dashboard_hero),
                    contentDescription = "Dashboard banner",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xEB5E0B14), Color(0xBF8F4B08))
                            )
                        )
                )
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$greeting, $studentName",
                                style = MaterialTheme.typography.displayMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${todaySessions.count { !it.completed }} sessions • ${pendingDeadlines.size} active deadlines",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showBriefingDialog = true }
                                .testTag("daily_briefing_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFFFD166), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Briefing", style = MaterialTheme.typography.labelLarge, color = Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    // Quick Access to AI Tutor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onNavigateRoute("ai_assistant") }
                                .testTag("dashboard_quick_ai_button"),
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFE08D), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Ask Studyverse AI Tutor any doubt...",
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { onNavigateRoute("focus_timer") },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = "Focus", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Focus")
                        }
                    }
                }
            }
        }

        // 2. Quick Hub Shortcuts (Exam Prep, Habits, Backlog, Study Groups)
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    AssistChip(
                        onClick = { onNavigateRoute("exam_prep") },
                        label = { Text("Exam Prep ($revisionDueCount due)") },
                        leadingIcon = { Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                item {
                    AssistChip(
                        onClick = { onNavigateRoute("habits") },
                        label = { Text("Habits & Badges") },
                        leadingIcon = { Icon(Icons.Default.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                item {
                    AssistChip(
                        onClick = { onNavigateRoute("study_groups") },
                        label = { Text("Study Groups") },
                        leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                item {
                    AssistChip(
                        onClick = { onNavigateRoute("planner") },
                        label = { Text("AI Scheduler") },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }

        // 3. Bento KPI Grid (Daily Goal Ring, Streak, Subjects, Weekly Hours)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val goalPct = (stats.todayMinutesDone.toFloat() / stats.todayGoalMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
                        StudyProgressRing(
                            progress = goalPct,
                            size = 64.dp,
                            strokeWidth = 7.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Daily Goal", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "${formatMinutesCompact(stats.todayMinutesDone)} / ${formatMinutesCompact(stats.todayGoalMinutes)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Score: ${stats.dailyProductivityScore}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${stats.currentStreak} Day Streak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Week: ${formatMinutesCompact(stats.weeklyMinutesDone)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${stats.totalSubjects} Subjects • ${stats.syllabusProgressPercent}% Syllabus",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // 4. Visual Productivity Overview (7-day Bar Chart)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("7-Day Productivity Overview", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { onNavigateRoute("analytics") }) {
                            Text("Full Analytics")
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    WeeklyBarChart(bars = weeklyBars)
                }
            }
        }

        // 5. Today's Study Schedule
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Today's Study Schedule", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { onNavigateRoute("planner") }) { Text("Planner") }
                    }
                    if (todaySessions.isEmpty()) {
                        Text(
                            "No sessions scheduled for today yet. Tap Planner to generate an AI timetable!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        todaySessions.take(5).forEach { session ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.toggleSessionCompleted(session) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (session.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Toggle session",
                                    tint = if (session.completed) SuccessEmerald else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        session.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        textDecoration = if (session.completed) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    Text(
                                        "${session.subjectName} • %02d:%02d • ${session.durationMinutes}m".format(session.startHour, session.startMinute),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Pending Deadlines & Upcoming Exams
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Deadlines", style = MaterialTheme.typography.titleMedium)
                            if (stats.overdueDeadlinesCount > 0) {
                                Icon(Icons.Default.Warning, contentDescription = "Overdue", tint = DangerCoral, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (pendingDeadlines.isEmpty()) {
                            Text("All caught up!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            pendingDeadlines.take(3).forEach { dl ->
                                val isOverdue = dl.dueAtMillis < System.currentTimeMillis()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateRoute("deadlines") },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(dl.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            formatMillisDate(dl.dueAtMillis),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isOverdue) DangerCoral else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    PriorityPill(PriorityLevel.safeParse(dl.priority))
                                }
                            }
                        }
                        TextButton(onClick = { onNavigateRoute("deadlines") }, contentPadding = PaddingValues(0.dp)) {
                            Text("Manage Deadlines")
                        }
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Upcoming Exams", style = MaterialTheme.typography.titleMedium)
                        if (upcomingExams.isEmpty()) {
                            Text("No exams added yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            upcomingExams.take(3).forEach { exam ->
                                val daysLeft = ((exam.dueAtMillis - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0L)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateRoute("exam_prep") },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(exam.subjectName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(exam.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "${daysLeft}d",
                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { onNavigateRoute("exam_prep") }, contentPadding = PaddingValues(0.dp)) {
                            Text("Exam Prep Mode")
                        }
                    }
                }
            }
        }

        // 7. Motivational Quote of the Day
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FormatQuote, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Quote of the Day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        Text(quoteOfDay, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (showBriefingDialog) {
        AlertDialog(
            onDismissRequest = { showBriefingDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = WarningAmber)
                    Spacer(Modifier.width(8.dp))
                    Text("Daily Morning Briefing")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Academic Goal: ${formatMinutesCompact(stats.todayGoalMinutes)} (${formatMinutesCompact(stats.todayMinutesDone)} completed)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("• Today's Planned Sessions: ${todaySessions.size} (${todaySessions.count { !it.completed }} remaining)")
                    Text("• Urgent Deadlines: ${pendingDeadlines.take(3).joinToString { it.title }.ifBlank { "None" }}")
                    Text("• Topics Due for Spaced Revision: $revisionDueCount")
                    Text("• Next Exam: ${upcomingExams.firstOrNull()?.let { "${it.title} (${((it.dueAtMillis - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0L)} days)" } ?: "None scheduled"}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showBriefingDialog = false
                    onNavigateRoute("planner")
                }) { Text("Open Today's Plan") }
            },
            dismissButton = {
                TextButton(onClick = { showBriefingDialog = false }) { Text("Close") }
            }
        )
    }
}
