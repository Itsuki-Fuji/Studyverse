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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChapterStatus
import com.example.data.model.DeadlineCategory
import com.example.data.model.PriorityLevel
import com.example.data.model.UiState
import com.example.ui.components.AiQuizModal
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.formatMillisDate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.StudyverseViewModel

@Composable
fun ExamPrepScreen(viewModel: StudyverseViewModel) {
    val deadlinesUi by viewModel.deadlinesState.collectAsStateWithLifecycle()
    val subjectsUi by viewModel.subjectsState.collectAsStateWithLifecycle()
    val chaptersUi by viewModel.chaptersState.collectAsStateWithLifecycle()
    val examSummaryState by viewModel.examSummaryState.collectAsStateWithLifecycle()
    val quizState by viewModel.generatedQuiz.collectAsStateWithLifecycle()

    val deadlines = (deadlinesUi as? UiState.Success)?.data.orEmpty()
    val subjects = (subjectsUi as? UiState.Success)?.data.orEmpty()
    val chapters = (chaptersUi as? UiState.Success)?.data.orEmpty()

    val now = System.currentTimeMillis()
    val exams = deadlines.filter {
        !it.completed && DeadlineCategory.safeParse(it.category).isExam
    }.sortedBy { it.dueAtMillis }

    val revisionQueue = chapters.filter {
        it.nextRevisionMillis == null || it.nextRevisionMillis <= now + 86_400_000L * 2
    }.sortedBy { it.nextRevisionMillis ?: Long.MAX_VALUE }

    var showAddExam by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddExam = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Exam")
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Exam Prep & Spaced Revision", style = MaterialTheme.typography.headlineMedium)
            }

            // Next Exam Hero Countdown
            item {
                val nextExam = exams.firstOrNull()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("NEXT EXAMINATION COUNTDOWN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        if (nextExam == null) {
                            Text("No exams scheduled", style = MaterialTheme.typography.titleLarge)
                            Text("Tap + to add an internal or external exam date and unlock countdowns & AI revision plans.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            val daysLeft = ((nextExam.dueAtMillis - now) / 86_400_000L).coerceAtLeast(0L)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(nextExam.title, style = MaterialTheme.typography.titleLarge)
                                    Text("${nextExam.subjectName} • ${formatMillisDate(nextExam.dueAtMillis)}", style = MaterialTheme.typography.bodyMedium)
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        "${daysLeft}d",
                                        style = MaterialTheme.typography.displayMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Subject-wise Exam Prep Cards
            if (subjects.isNotEmpty()) {
                item {
                    Text("Subject Readiness & Mock Tests", style = MaterialTheme.typography.titleLarge)
                }
                items(subjects, key = { it.id }) { sub ->
                    val subChaps = chapters.filter { it.subjectId == sub.id }
                    val doneCount = subChaps.count { it.status == ChapterStatus.COMPLETED.name }
                    val pct = if (subChaps.isEmpty()) 0f else doneCount.toFloat() / subChaps.size
                    val weakChaps = subChaps.filter { it.status != ChapterStatus.COMPLETED.name }

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
                                Text(sub.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${(pct * 100).toInt()}% Ready", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50))
                            )
                            if (weakChaps.isNotEmpty()) {
                                Text(
                                    "Weak / Incomplete: ${weakChaps.take(3).joinToString { it.title }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = WarningAmber
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.generateLastMinuteExamSummary(sub, subChaps) }) {
                                    Icon(Icons.Default.HistoryEdu, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Revision Sheet")
                                }
                                Button(onClick = {
                                    val topicText = "${sub.name}: ${subChaps.joinToString { "${it.title} (${it.topics.joinToString()})" }}"
                                    viewModel.generateQuizFromSource(topicText, 5)
                                }) {
                                    Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Mock Test")
                                }
                            }
                        }
                    }
                }
            }

            // Spaced Repetition Revision Reminders
            item {
                Text("Spaced Repetition Queue", style = MaterialTheme.typography.titleLarge)
            }
            if (revisionQueue.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "All Revisions Up to Date",
                        subtitle = "Chapters you study or complete will automatically enter spaced repetition intervals (1d, 3d, 7d, 14d)."
                    )
                }
            } else {
                items(revisionQueue.take(10), key = { "rev_${it.id}" }) { chap ->
                    val subName = subjects.firstOrNull { it.id == chap.subjectId }?.name ?: "Subject"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(chap.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "$subName • Revised ${chap.revisionCount}x",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = { viewModel.markChapterRevised(chap) }) {
                                Text("Revise Now")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddExam) {
        var title by remember { mutableStateOf("") }
        var selectedSubId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "general") }
        var selectedSubName by remember { mutableStateOf(subjects.firstOrNull()?.name ?: "General") }
        var examCat by remember { mutableStateOf(DeadlineCategory.EXTERNAL_EXAM) }
        var daysUntil by remember { mutableStateOf("10") }

        AlertDialog(
            onDismissRequest = { showAddExam = false },
            title = { Text("Schedule Exam") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Exam Name (e.g. Midterm / Final)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = examCat == DeadlineCategory.INTERNAL_EXAM,
                            onClick = { examCat = DeadlineCategory.INTERNAL_EXAM },
                            label = { Text("Internal") }
                        )
                        FilterChip(
                            selected = examCat == DeadlineCategory.EXTERNAL_EXAM,
                            onClick = { examCat = DeadlineCategory.EXTERNAL_EXAM },
                            label = { Text("External") }
                        )
                    }
                    if (subjects.isNotEmpty()) {
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
                    OutlinedTextField(
                        value = daysUntil,
                        onValueChange = { daysUntil = it.filter { c -> c.isDigit() } },
                        label = { Text("Days Until Exam") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val d = (daysUntil.toLongOrNull() ?: 7L).coerceAtLeast(1L)
                        viewModel.addDeadline(
                            subjectId = selectedSubId,
                            subjectName = selectedSubName,
                            title = title,
                            description = "Exam countdown & prep",
                            category = examCat,
                            dueAtMillis = System.currentTimeMillis() + d * 86_400_000L,
                            priority = PriorityLevel.HIGH,
                            reminderDaysBefore = 3L
                        )
                        showAddExam = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Add Exam") }
            },
            dismissButton = { TextButton(onClick = { showAddExam = false }) { Text("Cancel") } }
        )
    }

    examSummaryState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.clearExamSummary() },
            title = { Text("Last-Minute Revision Sheet") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    when (state) {
                        is UiState.Loading -> CircularProgressIndicator()
                        is UiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                        is UiState.Success -> Text(state.data, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.clearExamSummary() }) { Text("Close") } }
        )
    }

    quizState?.let { state ->
        AiQuizModal(quizState = state, onDismiss = { viewModel.clearGeneratedQuiz() })
    }
}
