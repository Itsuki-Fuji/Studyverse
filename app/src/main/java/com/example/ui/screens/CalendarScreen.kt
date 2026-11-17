package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.StudyverseRepository
import com.example.data.model.DeadlineCategory
import com.example.data.model.StudySession
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.formatMillisDateTime
import com.example.ui.components.parseHexColor
import com.example.ui.viewmodel.StudyverseViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CalendarScreen(viewModel: StudyverseViewModel) {
    val sessionsUi by viewModel.sessionsState.collectAsStateWithLifecycle()
    val deadlinesUi by viewModel.deadlinesState.collectAsStateWithLifecycle()
    val subjectsUi by viewModel.subjectsState.collectAsStateWithLifecycle()

    val sessions = (sessionsUi as? UiState.Success)?.data.orEmpty()
    val deadlines = (deadlinesUi as? UiState.Success)?.data.orEmpty()
    val subjects = (subjectsUi as? UiState.Success)?.data.orEmpty()

    var viewMode by remember { mutableIntStateOf(1) } // 0 = Day, 1 = Week, 2 = Month
    var selectedDateKey by remember { mutableStateOf(StudyverseRepository.todayDateKey()) }
    var reschedulingSession by remember { mutableStateOf<StudySession?>(null) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val weekDates = remember {
        (0..6).map { StudyverseRepository.dateKeyWithOffset(it) }
    }

    val monthGrid = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        (1..maxDays).map { day ->
            cal.set(Calendar.DAY_OF_MONTH, day)
            day to sdf.format(cal.time)
        }
    }

    val visibleDateKeys = when (viewMode) {
        0 -> setOf(selectedDateKey)
        1 -> weekDates.toSet()
        else -> setOf(selectedDateKey)
    }

    val filteredSessions = sessions.filter { it.dateKey in visibleDateKeys }
        .sortedWith(compareBy({ it.dateKey }, { it.startHour }))
    val filteredDeadlines = deadlines.filter { dl ->
        sdf.format(Date(dl.dueAtMillis)) in visibleDateKeys
    }.sortedBy { it.dueAtMillis }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Smart Calendar", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("Day", "Week", "Month").forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = viewMode == idx,
                        onClick = { viewMode = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3)
                    ) { Text(label) }
                }
            }
        }

        if (viewMode == 0 || viewMode == 1) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    weekDates.forEach { key ->
                        val dateObj = sdf.parse(key) ?: Date()
                        val dayLabel = SimpleDateFormat("EEE", Locale.US).format(dateObj)
                        val dayNum = SimpleDateFormat("d", Locale.US).format(dateObj)
                        val isSelected = selectedDateKey == key
                        val hasEvents = sessions.any { it.dateKey == key } ||
                            deadlines.any { sdf.format(Date(it.dueAtMillis)) == key }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedDateKey = key },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    dayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    dayNum,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                if (hasEvents) {
                                    Spacer(Modifier.height(3.dp))
                                    Box(
                                        Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        monthGrid.chunked(7).forEach { weekRow ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                weekRow.forEach { (dayNum, key) ->
                                    val isSel = key == selectedDateKey
                                    val hasEvents = sessions.any { it.dateKey == key } ||
                                        deadlines.any { sdf.format(Date(it.dueAtMillis)) == key }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSel) MaterialTheme.colorScheme.primary
                                                else if (hasEvents) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                            )
                                            .clickable { selectedDateKey = key },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            dayNum.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                repeat(7 - weekRow.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                if (viewMode == 1) "7-Day Schedule & Deadlines" else "Events for $selectedDateKey",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (filteredSessions.isEmpty() && filteredDeadlines.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Clear Schedule",
                    subtitle = "No study blocks or deadlines on this date. Use Study Planner or Deadlines to add events."
                )
            }
        } else {
            items(filteredDeadlines, key = { "dl_${it.id}" }) { dl ->
                val sub = subjects.firstOrNull { it.id == dl.subjectId }
                val color = parseHexColor(sub?.colorHex ?: "#8C141E")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, contentDescription = null, tint = color)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${DeadlineCategory.safeParse(dl.category).label}: ${dl.title}", style = MaterialTheme.typography.titleMedium)
                            Text("${dl.subjectName} • ${formatMillisDateTime(dl.dueAtMillis)}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            items(filteredSessions, key = { "sess_${it.id}" }) { session ->
                val sub = subjects.firstOrNull { it.id == session.subjectId }
                val color = parseHexColor(sub?.colorHex ?: "#C98A12")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${session.subjectName} • ${session.dateKey} at %02d:%02d (${session.durationMinutes}m)".format(
                                    session.startHour, session.startMinute
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { reschedulingSession = session }) {
                            Icon(Icons.Default.DragIndicator, contentDescription = "Move Session")
                        }
                    }
                }
            }
        }
    }

    reschedulingSession?.let { target ->
        var dayOffset by remember { mutableIntStateOf(0) }
        var hourStr by remember { mutableStateOf(target.startHour.toString()) }
        AlertDialog(
            onDismissRequest = { reschedulingSession = null },
            title = { Text("Move '${target.title}'") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pick new day & time slot:", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Today", 1 to "Tomorrow", 2 to "+2d", 3 to "+3d").forEach { (off, label) ->
                            FilterChip(selected = dayOffset == off, onClick = { dayOffset = off }, label = { Text(label) })
                        }
                    }
                    OutlinedTextField(
                        value = hourStr,
                        onValueChange = { hourStr = it.filter { c -> c.isDigit() } },
                        label = { Text("New Start Hour (0 - 23)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.moveSessionToDateAndHour(
                        target,
                        StudyverseRepository.dateKeyWithOffset(dayOffset),
                        (hourStr.toIntOrNull() ?: 16).coerceIn(0, 23)
                    )
                    reschedulingSession = null
                }) { Text("Move Session") }
            },
            dismissButton = { TextButton(onClick = { reschedulingSession = null }) { Text("Cancel") } }
        )
    }
}
