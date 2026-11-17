package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.StudyverseRepository
import com.example.data.model.ChapterStatus
import com.example.data.model.PriorityLevel
import com.example.data.model.SessionType
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PriorityPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.StudyverseViewModel

@Composable
fun PlannerScreen(viewModel: StudyverseViewModel) {
    val sessionsUi by viewModel.sessionsState.collectAsStateWithLifecycle()
    val subjectsUi by viewModel.subjectsState.collectAsStateWithLifecycle()
    val chaptersUi by viewModel.chaptersState.collectAsStateWithLifecycle()
    val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()

    val sessions = (sessionsUi as? UiState.Success)?.data.orEmpty()
    val subjects = (subjectsUi as? UiState.Success)?.data.orEmpty()
    val chapters = (chaptersUi as? UiState.Success)?.data.orEmpty()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAiDialog by remember { mutableStateOf(false) }
    var showAddSessionDialog by remember { mutableStateOf(false) }

    val todayKey = StudyverseRepository.todayDateKey()
    val backlogChapters = chapters.filter { it.inBacklog && it.status != ChapterStatus.COMPLETED.name }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { showAddSessionDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Study Session")
                }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("AI Study Planner") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Backlog (${backlogChapters.size})") })
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Smart AI Study Scheduler", style = MaterialTheme.typography.titleLarge)
                                }
                                Text(
                                    "Generates personalized daily, weekly, or monthly schedules factoring in subject difficulty, priorities, exam dates, and Pomodoro breaks.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { showAiDialog = true },
                                        enabled = !aiLoading,
                                        modifier = Modifier.testTag("generate_ai_schedule_button")
                                    ) {
                                        if (aiLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        else Text("Generate AI Plan")
                                    }
                                    OutlinedButton(onClick = { viewModel.rescheduleUnfinishedSessions() }) {
                                        Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Reschedule Missed")
                                    }
                                }
                            }
                        }
                    }

                    val grouped = sessions.sortedWith(compareBy({ it.dateKey }, { it.startHour })).groupBy { it.dateKey }
                    if (grouped.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Study Sessions Yet",
                                subtitle = "Let Gemini AI craft a balanced timetable around your subjects & deadlines, or tap + to add one manually.",
                                actionLabel = "Generate Smart Schedule",
                                onAction = { showAiDialog = true }
                            )
                        }
                    } else {
                        grouped.forEach { (dateKey, daySessions) ->
                            item {
                                Text(
                                    text = if (dateKey == todayKey) "Today • $dateKey" else dateKey,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (dateKey == todayKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            items(daySessions, key = { it.id }) { session ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { viewModel.toggleSessionCompleted(session) }) {
                                            Icon(
                                                imageVector = if (session.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = "Complete",
                                                tint = if (session.completed) SuccessEmerald else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    session.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    textDecoration = if (session.completed) TextDecoration.LineThrough else TextDecoration.None
                                                )
                                                if (session.aiGenerated) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            "AI",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                "${session.subjectName} • %02d:%02d • ${session.durationMinutes}m • ${SessionType.safeParse(session.sessionType).label}".format(
                                                    session.startHour, session.startMinute
                                                ),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Session")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Backlog Manager Tab
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Backlog Manager", style = MaterialTheme.typography.titleLarge)
                                val totalBacklogMins = backlogChapters.sumOf { it.estimatedMinutes.toInt() }
                                Text(
                                    "${backlogChapters.size} unfinished chapters • Est. ${totalBacklogMins / 60}h ${totalBacklogMins % 60}m remaining",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Button(
                                    onClick = { viewModel.distributeBacklogAcrossDays(5) },
                                    enabled = backlogChapters.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Distribute Across Next 5 Days")
                                }
                            }
                        }
                    }

                    if (backlogChapters.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "Zero Backlog!",
                                subtitle = "Mark unfinished chapters from any subject as backlog to prioritize and auto-distribute them across available study days."
                            )
                        }
                    } else {
                        items(backlogChapters, key = { it.id }) { chap ->
                            val subName = subjects.firstOrNull { it.id == chap.subjectId }?.name ?: "Subject"
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(chap.title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "$subName • Est. ${chap.estimatedMinutes}m",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    PriorityPill(PriorityLevel.safeParse(chap.backlogPriority))
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(onClick = { viewModel.setChapterStatus(chap, ChapterStatus.COMPLETED) }) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Mark Completed", tint = SuccessEmerald)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAiDialog) {
        var dailyHours by remember { mutableStateOf("3.0") }
        var startHour by remember { mutableStateOf("9") }
        var horizonDays by remember { mutableIntStateOf(3) }

        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = { Text("Configure AI Schedule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dailyHours,
                        onValueChange = { dailyHours = it },
                        label = { Text("Available Study Hours / Day") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = startHour,
                        onValueChange = { startHour = it.filter { c -> c.isDigit() } },
                        label = { Text("Preferred Start Hour (6 - 21)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Schedule Horizon", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "Daily", 3 to "3 Days", 7 to "Weekly").forEach { (days, label) ->
                            FilterChip(
                                selected = horizonDays == days,
                                onClick = { horizonDays = days },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.generateAiStudySchedule(
                        dailyHours = (dailyHours.toDoubleOrNull() ?: 3.0).coerceIn(0.5, 14.0),
                        preferredStartHour = (startHour.toIntOrNull() ?: 9).coerceIn(6, 21),
                        horizonDays = horizonDays
                    )
                    showAiDialog = false
                }) { Text("Generate") }
            },
            dismissButton = { TextButton(onClick = { showAiDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddSessionDialog) {
        var title by remember { mutableStateOf("") }
        var selectedSubId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "general") }
        var selectedSubName by remember { mutableStateOf(subjects.firstOrNull()?.name ?: "General") }
        var dayOffset by remember { mutableIntStateOf(0) }
        var startHour by remember { mutableStateOf("17") }
        var duration by remember { mutableStateOf("45") }
        var type by remember { mutableStateOf(SessionType.STUDY) }

        AlertDialog(
            onDismissRequest = { showAddSessionDialog = false },
            title = { Text("Add Study Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Topic / Session Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (subjects.isNotEmpty()) {
                        Text("Subject", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            subjects.take(3).forEach { sub ->
                                FilterChip(
                                    selected = selectedSubId == sub.id,
                                    onClick = {
                                        selectedSubId = sub.id
                                        selectedSubName = sub.name
                                    },
                                    label = { Text(sub.name) }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Today", 1 to "Tomorrow", 2 to "+2 Days").forEach { (off, label) ->
                            FilterChip(selected = dayOffset == off, onClick = { dayOffset = off }, label = { Text(label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startHour,
                            onValueChange = { startHour = it.filter { c -> c.isDigit() } },
                            label = { Text("Hour (0-23)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = duration,
                            onValueChange = { duration = it.filter { c -> c.isDigit() } },
                            label = { Text("Minutes") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SessionType.entries.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addStudySession(
                            subjectId = selectedSubId,
                            subjectName = selectedSubName,
                            title = title,
                            dateKey = StudyverseRepository.dateKeyWithOffset(dayOffset),
                            startHour = (startHour.toIntOrNull() ?: 17).coerceIn(0, 23),
                            startMinute = 0,
                            durationMinutes = (duration.toIntOrNull() ?: 45).coerceIn(5, 360),
                            sessionType = type
                        )
                        showAddSessionDialog = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddSessionDialog = false }) { Text("Cancel") } }
        )
    }
}
