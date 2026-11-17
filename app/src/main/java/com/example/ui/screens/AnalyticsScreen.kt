package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.StudyverseRepository
import com.example.data.model.ChapterStatus
import com.example.data.model.UiState
import com.example.ui.components.StudyProgressRing
import com.example.ui.components.SubjectDonutChart
import com.example.ui.components.WeeklyBarChart
import com.example.ui.components.formatMinutesCompact
import com.example.ui.components.parseHexColor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.StudyverseViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnalyticsScreen(viewModel: StudyverseViewModel) {
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val subjectsUi by viewModel.subjectsState.collectAsStateWithLifecycle()
    val chaptersUi by viewModel.chaptersState.collectAsStateWithLifecycle()
    val sessionsUi by viewModel.sessionsState.collectAsStateWithLifecycle()
    val deadlinesUi by viewModel.deadlinesState.collectAsStateWithLifecycle()
    val aiInsights by viewModel.aiInsights.collectAsStateWithLifecycle()

    val subjects = (subjectsUi as? UiState.Success)?.data.orEmpty()
    val chapters = (chaptersUi as? UiState.Success)?.data.orEmpty()
    val sessions = (sessionsUi as? UiState.Success)?.data.orEmpty()
    val deadlines = (deadlinesUi as? UiState.Success)?.data.orEmpty()

    var rangeMode by remember { mutableIntStateOf(0) } // 0 = Weekly, 1 = Monthly

    val barData = remember(sessions, rangeMode) {
        if (rangeMode == 0) {
            val fmt = SimpleDateFormat("EEE", Locale.US)
            (-6..0).map { off ->
                val key = StudyverseRepository.dateKeyWithOffset(off)
                val lbl = fmt.format(Date(System.currentTimeMillis() + off * 86_400_000L))
                val mins = sessions.filter { it.dateKey == key && it.completed }.sumOf { it.durationMinutes.toInt() }
                lbl to mins
            }
        } else {
            (0..3).reversed().map { weekIdx ->
                val startOff = -((weekIdx + 1) * 7 - 1)
                val endOff = -(weekIdx * 7)
                val keys = (startOff..endOff).map { StudyverseRepository.dateKeyWithOffset(it) }.toSet()
                val mins = sessions.filter { it.dateKey in keys && it.completed }.sumOf { it.durationMinutes.toInt() }
                "W-${weekIdx + 1}" to mins
            }
        }
    }

    val subjectSlices = remember(subjects, sessions) {
        subjects.map { sub ->
            val mins = sessions.filter { it.subjectId == sub.id && it.completed }.sumOf { it.durationMinutes.toInt() }
            Triple(sub.name, mins, parseHexColor(sub.colorHex))
        }.filter { it.second > 0 }.sortedByDescending { it.second }
    }

    val weakSubjects = remember(subjects, chapters, sessions) {
        subjects.filter { sub ->
            val chaps = chapters.filter { it.subjectId == sub.id }
            val incomplete = chaps.count { it.status != ChapterStatus.COMPLETED.name }
            val mins = sessions.filter { it.subjectId == sub.id && it.completed }.sumOf { it.durationMinutes.toInt() }
            incomplete > 0 || mins < 60
        }
    }

    val completedTasks = deadlines.count { it.completed }
    val pendingTasks = deadlines.count { !it.completed }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Analytics & AI Coach", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Track study consistency, subject balance, and performance insights",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Top KPI Row
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        StudyProgressRing(
                            progress = stats.dailyProductivityScore / 100f,
                            size = 64.dp,
                            strokeWidth = 7.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Productivity", style = MaterialTheme.typography.labelLarge)
                            Text("${stats.dailyProductivityScore}/100", style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily))
                            Text("${stats.currentStreak}d streak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tasks & Syllabus", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "$completedTasks Done • $pendingTasks Left",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Syllabus: ${stats.syllabusProgressPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        LinearProgressIndicator(
                            progress = { stats.syllabusProgressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
                        )
                    }
                }
            }
        }

        // Weekly / Monthly Hours Bar Chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Study Hours Trend", style = MaterialTheme.typography.titleMedium)
                        SingleChoiceSegmentedButtonRow {
                            listOf("Weekly", "Monthly").forEachIndexed { idx, label ->
                                SegmentedButton(
                                    selected = rangeMode == idx,
                                    onClick = { rangeMode = idx },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2)
                                ) { Text(label) }
                            }
                        }
                    }
                    WeeklyBarChart(bars = barData)
                }
            }
        }

        // Subject Time Distribution Donut Chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Subject-Wise Distribution", style = MaterialTheme.typography.titleMedium)
                    SubjectDonutChart(slices = subjectSlices)
                }
            }
        }

        // Weak Subjects & Attention Needed
        if (weakSubjects.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
                            Spacer(Modifier.width(8.dp))
                            Text("Needs More Attention", style = MaterialTheme.typography.titleMedium)
                        }
                        weakSubjects.take(4).forEach { sub ->
                            val inc = chapters.count { it.subjectId == sub.id && it.status != ChapterStatus.COMPLETED.name }
                            val mins = sessions.filter { it.subjectId == sub.id && it.completed }.sumOf { it.durationMinutes.toInt() }
                            Text(
                                "• ${sub.name}: $inc incomplete chapters, ${formatMinutesCompact(mins)} logged",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // AI Productivity Coach
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("AI Productivity Coach", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "Get personalized analysis of your consistency, procrastination patterns, most productive days, and next-week study strategy.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = { viewModel.generateProductivityCoachInsights() }) {
                        Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Analyze My Routine")
                    }

                    when (val ins = aiInsights) {
                        is UiState.Loading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Evaluating your study patterns...")
                            }
                        }
                        is UiState.Error -> Text(ins.message, color = MaterialTheme.colorScheme.error)
                        is UiState.Success -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    ins.data,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                        null -> {}
                    }
                }
            }
        }
    }
}
