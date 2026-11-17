package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.data.model.DeadlineCategory
import com.example.data.model.PriorityLevel
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PriorityPill
import com.example.ui.components.formatMillisDateTime
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.StudyverseViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeadlinesScreen(viewModel: StudyverseViewModel) {
    val deadlinesUi by viewModel.deadlinesState.collectAsStateWithLifecycle()
    val subjectsUi by viewModel.subjectsState.collectAsStateWithLifecycle()

    val deadlines = (deadlinesUi as? UiState.Success)?.data.orEmpty()
    val subjects = (subjectsUi as? UiState.Success)?.data.orEmpty()

    var filter by remember { mutableStateOf("ACTIVE") } // ACTIVE, OVERDUE, EXAMS, COMPLETED
    var showAddDialog by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val filtered = when (filter) {
        "OVERDUE" -> deadlines.filter { !it.completed && it.dueAtMillis < now }
        "EXAMS" -> deadlines.filter { !it.completed && DeadlineCategory.safeParse(it.category).isExam }
        "COMPLETED" -> deadlines.filter { it.completed }
        else -> deadlines.filter { !it.completed }
    }.sortedBy { it.dueAtMillis }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_deadline_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Deadline")
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
                Text("Deadlines & Reminders", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val overdueCount = deadlines.count { !it.completed && it.dueAtMillis < now }
                    items(
                        listOf(
                            "ACTIVE" to "Active (${deadlines.count { !it.completed }})",
                            "OVERDUE" to "Overdue ($overdueCount)",
                            "EXAMS" to "Exams",
                            "COMPLETED" to "Completed"
                        )
                    ) { (key, label) ->
                        FilterChip(
                            selected = filter == key,
                            onClick = { filter = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Deadlines in This View",
                        subtitle = "Track assignments, projects, practicals, and exams with 1-day, 3-day, or 1-week smart alerts.",
                        actionLabel = "Add Deadline",
                        onAction = { showAddDialog = true }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { dl ->
                    val isOverdue = !dl.completed && dl.dueAtMillis < now
                    val isUrgent = !dl.completed && !isOverdue && (dl.dueAtMillis - now) < 86_400_000L * 2
                    val borderColor = when {
                        isOverdue -> DangerCoral
                        isUrgent -> WarningAmber
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(if (isOverdue || isUrgent) 1.5.dp else 1.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.toggleDeadlineCompleted(dl) }) {
                                Icon(
                                    imageVector = if (dl.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Complete Deadline",
                                    tint = if (dl.completed) SuccessEmerald else MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        dl.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        textDecoration = if (dl.completed) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    if (isOverdue) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Default.Warning, contentDescription = "Overdue", tint = DangerCoral, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(
                                    "${dl.subjectName} • ${DeadlineCategory.safeParse(dl.category).label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        formatMillisDateTime(dl.dueAtMillis),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOverdue) DangerCoral else if (isUrgent) WarningAmber else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        "${dl.reminderDaysBefore}d prior",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            PriorityPill(PriorityLevel.safeParse(dl.priority))
                            IconButton(onClick = { viewModel.deleteDeadline(dl.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Deadline")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var selectedSubId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "general") }
        var selectedSubName by remember { mutableStateOf(subjects.firstOrNull()?.name ?: "General") }
        var category by remember { mutableStateOf(DeadlineCategory.ASSIGNMENT) }
        var priority by remember { mutableStateOf(PriorityLevel.HIGH) }
        var dueDays by remember { mutableStateOf("2") }
        var reminderDays by remember { mutableLongStateOf(1L) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Academic Deadline") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (subjects.isNotEmpty()) {
                        Text("Subject", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            subjects.forEach { sub ->
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
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DeadlineCategory.entries.forEach { cat ->
                            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dueDays,
                            onValueChange = { dueDays = it.filter { c -> c.isDigit() } },
                            label = { Text("Due in Days") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text("Notify Me Before", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1L to "1 Day", 3L to "3 Days", 7L to "1 Week").forEach { (d, lbl) ->
                            FilterChip(selected = reminderDays == d, onClick = { reminderDays = d }, label = { Text(lbl) })
                        }
                    }
                    Text("Priority", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PriorityLevel.entries.forEach { p ->
                            FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.label) })
                        }
                    }
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Details (optional)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val days = (dueDays.toLongOrNull() ?: 2L).coerceAtLeast(0L)
                        viewModel.addDeadline(
                            subjectId = selectedSubId,
                            subjectName = selectedSubName,
                            title = title,
                            description = description,
                            category = category,
                            dueAtMillis = System.currentTimeMillis() + days * 86_400_000L,
                            priority = priority,
                            reminderDaysBefore = reminderDays
                        )
                        showAddDialog = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}
