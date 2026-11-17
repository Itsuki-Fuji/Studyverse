package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.data.firebase.StudyverseRepository
import com.example.data.model.UiState
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.StudyverseViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HabitsScreen(viewModel: StudyverseViewModel) {
    val habitsUi by viewModel.habitsState.collectAsStateWithLifecycle()
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()

    val habits = (habitsUi as? UiState.Success)?.data.orEmpty()
    val todayKey = StudyverseRepository.todayDateKey()
    val weekDays = remember {
        val fmt = SimpleDateFormat("EEEEE", Locale.US)
        (-6..0).map { off ->
            val key = StudyverseRepository.dateKeyWithOffset(off)
            val lbl = fmt.format(Date(System.currentTimeMillis() + off * 86_400_000L))
            lbl to key
        }
    }

    var showAddHabit by remember { mutableStateOf(false) }

    val badges = remember(stats, habits) {
        listOf(
            Triple("First Spark", "Complete 1+ focus hour", stats.weeklyMinutesDone >= 60),
            Triple("Streak Builder", "Maintain a 3-day streak", stats.currentStreak >= 3),
            Triple("Habit Hero", "Complete 3+ habits today", habits.count { todayKey in it.completedDates } >= 3),
            Triple("Syllabus Master", "50%+ overall syllabus", stats.syllabusProgressPercent >= 50)
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddHabit = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Habit")
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Habits & Motivation", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Build rock-solid study consistency every day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Achievement Badges
            item {
                Text("Achievement Milestones", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(badges) { (title, desc, unlocked) ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (unlocked) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.width(155.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = if (unlocked) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(desc, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    if (unlocked) "UNLOCKED" else "LOCKED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (unlocked) SuccessEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Daily Habit Checklist & Weekly Grid", style = MaterialTheme.typography.titleMedium)
            }

            items(habits, key = { it.id }) { habit ->
                val doneToday = todayKey in habit.completedDates
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.toggleHabitToday(habit) }) {
                                Icon(
                                    if (doneToday) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Toggle Habit",
                                    tint = if (doneToday) SuccessEmerald else MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(habit.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${habit.category} • ${habit.targetMinutes}m target",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                                Text("${habit.streak}d", style = MaterialTheme.typography.labelLarge)
                            }
                            IconButton(onClick = { viewModel.deleteHabit(habit.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Habit")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weekDays.forEach { (lbl, dateKey) ->
                                val completed = dateKey in habit.completedDates
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(lbl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (completed) SuccessEmerald
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddHabit) {
        var title by remember { mutableStateOf("") }
        var mins by remember { mutableStateOf("30") }
        var cat by remember { mutableStateOf("Study") }

        AlertDialog(
            onDismissRequest = { showAddHabit = false },
            title = { Text("New Study Habit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Habit (e.g. Practice Math 30 mins)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mins,
                        onValueChange = { mins = it.filter { c -> c.isDigit() } },
                        label = { Text("Target Minutes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cat,
                        onValueChange = { cat = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addHabit(title, mins.toLongOrNull() ?: 30L, cat)
                        showAddHabit = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAddHabit = false }) { Text("Cancel") } }
        )
    }
}
