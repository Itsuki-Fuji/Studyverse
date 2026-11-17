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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.StudyverseRepository
import com.example.data.model.SessionType
import com.example.data.model.UiState
import com.example.ui.components.StudyProgressRing
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.PomodoroPhase
import com.example.ui.viewmodel.StudyverseViewModel

@Composable
fun FocusTimerScreen(viewModel: StudyverseViewModel) {
    val focusState by viewModel.focusState.collectAsStateWithLifecycle()
    val subjectsUi by viewModel.subjectsState.collectAsStateWithLifecycle()
    val sessionsUi by viewModel.sessionsState.collectAsStateWithLifecycle()

    val subjects = (subjectsUi as? UiState.Success)?.data.orEmpty()
    val todayKey = StudyverseRepository.todayDateKey()
    val todayPomodoros = (sessionsUi as? UiState.Success)?.data.orEmpty()
        .filter { it.dateKey == todayKey && it.sessionType == SessionType.POMODORO.name }

    var showCustomize by remember { mutableStateOf(false) }

    val mins = focusState.remainingSeconds / 60
    val secs = focusState.remainingSeconds % 60
    val timeFormatted = "%02d:%02d".format(mins, secs)
    val progress = 1f - (focusState.remainingSeconds.toFloat() / focusState.totalSeconds.coerceAtLeast(1))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Pomodoro Focus", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Automatic session logging & streak sync",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = { viewModel.toggleAmbientSound() }) {
                    Icon(
                        if (focusState.ambientSound) Icons.Default.MusicNote else Icons.Default.MusicOff,
                        contentDescription = "Ambient Focus Sound",
                        tint = if (focusState.ambientSound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.toggleDistractionFree() }) {
                    Icon(
                        if (focusState.distractionFree) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Distraction-Free Mode"
                    )
                }
                IconButton(onClick = { showCustomize = true }) {
                    Icon(Icons.Default.Tune, contentDescription = "Customize Timer")
                }
            }
        }

        // Phase Switcher
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PomodoroPhase.entries.forEachIndexed { idx, phase ->
                SegmentedButton(
                    selected = focusState.phase == phase,
                    onClick = { viewModel.switchPomodoroPhase(phase) },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = PomodoroPhase.entries.size)
                ) {
                    Text(
                        when (phase) {
                            PomodoroPhase.FOCUS -> "Focus ${focusState.focusMinutes}m"
                            PomodoroPhase.SHORT_BREAK -> "Short ${focusState.shortBreakMinutes}m"
                            PomodoroPhase.LONG_BREAK -> "Long ${focusState.longBreakMinutes}m"
                        },
                        maxLines = 1
                    )
                }
            }
        }

        // Animated Focus Ring
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
            StudyProgressRing(
                progress = progress,
                size = 230.dp,
                strokeWidth = 14.dp,
                centerLabel = ""
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 46.sp
                    ),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = focusState.phase.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (focusState.ambientSound && focusState.running) {
                    Text(
                        "Ambient Sound On",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // Controls
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { viewModel.resetTimer() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
                Spacer(Modifier.width(6.dp))
                Text("Reset")
            }
            Button(
                onClick = { viewModel.startOrPauseTimer() },
                modifier = Modifier
                    .height(50.dp)
                    .testTag("pomodoro_start_pause_button")
            ) {
                Icon(
                    if (focusState.running) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (focusState.running) "Pause" else "Start"
                )
                Spacer(Modifier.width(6.dp))
                Text(if (focusState.running) "Pause" else "Start Focus")
            }
            if (focusState.phase == PomodoroPhase.FOCUS && focusState.remainingSeconds < focusState.totalSeconds) {
                FilledTonalButton(onClick = { viewModel.completeSessionEarlyAndLog() }) {
                    Icon(Icons.Default.Check, contentDescription = "Complete & Log")
                }
            }
        }

        if (!focusState.distractionFree) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Focus Target", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = focusState.taskTitle,
                        onValueChange = { viewModel.updateFocusTaskTitle(it) },
                        label = { Text("What are you working on?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (subjects.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = focusState.selectedSubjectId == "general",
                                    onClick = { viewModel.selectPomodoroSubject("general", "General Focus") },
                                    label = { Text("General") }
                                )
                            }
                            items(subjects) { sub ->
                                FilterChip(
                                    selected = focusState.selectedSubjectId == sub.id,
                                    onClick = { viewModel.selectPomodoroSubject(sub.id, sub.name) },
                                    label = { Text(sub.name) }
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Today's Focus Stats", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${todayPomodoros.size} completed Pomodoros • ${todayPomodoros.sumOf { it.durationMinutes }} mins logged",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "${todayPomodoros.size}",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    if (showCustomize) {
        var fStr by remember { mutableStateOf(focusState.focusMinutes.toString()) }
        var sStr by remember { mutableStateOf(focusState.shortBreakMinutes.toString()) }
        var lStr by remember { mutableStateOf(focusState.longBreakMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { showCustomize = false },
            title = { Text("Customize Timer Durations") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = fStr,
                        onValueChange = { fStr = it.filter { c -> c.isDigit() } },
                        label = { Text("Focus Duration (mins)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sStr,
                        onValueChange = { sStr = it.filter { c -> c.isDigit() } },
                        label = { Text("Short Break (mins)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lStr,
                        onValueChange = { lStr = it.filter { c -> c.isDigit() } },
                        label = { Text("Long Break (mins)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setCustomTimerDurations(
                        fStr.toIntOrNull() ?: 25,
                        sStr.toIntOrNull() ?: 5,
                        lStr.toIntOrNull() ?: 15
                    )
                    showCustomize = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showCustomize = false }) { Text("Cancel") } }
        )
    }
}
