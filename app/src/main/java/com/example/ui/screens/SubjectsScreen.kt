package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Chapter
import com.example.data.model.ChapterStatus
import com.example.data.model.DifficultyLevel
import com.example.data.model.PriorityLevel
import com.example.data.model.Subject
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PriorityPill
import com.example.ui.components.SubjectIconOptions
import com.example.ui.components.formatMillisDate
import com.example.ui.components.parseHexColor
import com.example.ui.components.subjectIconForName
import com.example.ui.theme.SubjectPalette
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.StudyverseViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectsScreen(viewModel: StudyverseViewModel) {
    val subjectsUi by viewModel.subjectsState.collectAsStateWithLifecycle()
    val chaptersUi by viewModel.chaptersState.collectAsStateWithLifecycle()
    val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()

    val subjects = (subjectsUi as? UiState.Success)?.data.orEmpty()
    val allChapters = (chaptersUi as? UiState.Success)?.data.orEmpty()

    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var editingSubject by remember { mutableStateOf<Subject?>(null) }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var showSyllabusImporter by remember { mutableStateOf(false) }

    // Keep selectedSubject synced with latest snapshot
    val activeSubject = selectedSubject?.let { sel -> subjects.firstOrNull { it.id == sel.id } }

    if (activeSubject != null) {
        SubjectDetailView(
            subject = activeSubject,
            chapters = allChapters.filter { it.subjectId == activeSubject.id },
            onBack = { selectedSubject = null },
            onEditSubject = {
                editingSubject = activeSubject
                showSubjectDialog = true
            },
            onDeleteSubject = {
                viewModel.deleteSubject(activeSubject.id)
                selectedSubject = null
            },
            onAddChapter = { title, topics, mins, backlog, prio ->
                viewModel.addChapter(activeSubject.id, title, topics, mins, backlog, prio)
            },
            onCycleChapter = { viewModel.cycleChapterStatus(it) },
            onToggleBacklog = { ch -> viewModel.toggleBacklog(ch, !ch.inBacklog) },
            onDeleteChapter = { viewModel.deleteChapter(it) }
        )
    } else {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editingSubject = null
                        showSubjectDialog = true
                    },
                    modifier = Modifier.testTag("add_subject_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Subject")
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("My Subjects", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "${subjects.size} enrolled courses & syllabus progress",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showSyllabusImporter = true },
                            modifier = Modifier.testTag("import_syllabus_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("AI Syllabus")
                        }
                    }
                }

                if (subjectsUi is UiState.Loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (subjects.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Subjects Yet",
                            subtitle = "Add your courses manually or import your syllabus with AI to auto-generate chapters and topics.",
                            actionLabel = "Add First Subject",
                            onAction = {
                                editingSubject = null
                                showSubjectDialog = true
                            }
                        )
                    }
                } else {
                    items(subjects, key = { it.id }) { subject ->
                        val chaps = allChapters.filter { it.subjectId == subject.id }
                        val progress = if (chaps.isEmpty()) 0f else {
                            val score = chaps.sumOf {
                                when (ChapterStatus.safeParse(it.status)) {
                                    ChapterStatus.COMPLETED -> 1.0
                                    ChapterStatus.IN_PROGRESS -> 0.5
                                    ChapterStatus.NOT_STARTED -> 0.0
                                }
                            }
                            (score / chaps.size).toFloat().coerceIn(0f, 1f)
                        }
                        val subColor = parseHexColor(subject.colorHex)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { selectedSubject = subject }
                                .testTag("subject_card_${subject.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = subColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                subjectIconForName(subject.iconName),
                                                contentDescription = subject.name,
                                                tint = subColor
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(subject.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            buildString {
                                                if (subject.code.isNotBlank()) append("${subject.code} • ")
                                                append("${subject.targetHoursPerWeek}h/wk • ${DifficultyLevel.safeParse(subject.difficulty).label}")
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    PriorityPill(PriorityLevel.safeParse(subject.priority))
                                }

                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${chaps.count { it.status == ChapterStatus.COMPLETED.name }}/${chaps.size} chapters completed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "${(progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = subColor
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(50)),
                                        color = subColor
                                    )
                                }

                                if (subject.examDateMillis != null) {
                                    Text(
                                        "Exam Date: ${formatMillisDate(subject.examDateMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSubjectDialog) {
        SubjectEditorDialog(
            initial = editingSubject,
            onDismiss = { showSubjectDialog = false },
            onSave = { name, code, color, icon, priority, difficulty, targetHrs, notes, examDays ->
                val examMillis = examDays?.let { System.currentTimeMillis() + it * 86_400_000L }
                if (editingSubject == null) {
                    viewModel.addSubject(name, code, color, icon, priority, difficulty, targetHrs, notes, examMillis)
                } else {
                    viewModel.updateSubject(
                        editingSubject!!.copy(
                            name = name,
                            code = code,
                            colorHex = color,
                            iconName = icon,
                            priority = priority.name,
                            difficulty = difficulty.name,
                            targetHoursPerWeek = targetHrs,
                            notes = notes,
                            examDateMillis = examMillis ?: editingSubject!!.examDateMillis
                        )
                    )
                }
                showSubjectDialog = false
            }
        )
    }

    if (showSyllabusImporter) {
        SyllabusImporterDialog(
            loading = aiLoading,
            onDismiss = { showSyllabusImporter = false },
            onImport = { text, bitmap ->
                viewModel.importSyllabusWithAi(text, bitmap)
                showSyllabusImporter = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectDetailView(
    subject: Subject,
    chapters: List<Chapter>,
    onBack: () -> Unit,
    onEditSubject: () -> Unit,
    onDeleteSubject: () -> Unit,
    onAddChapter: (String, String, Long, Boolean, PriorityLevel) -> Unit,
    onCycleChapter: (Chapter) -> Unit,
    onToggleBacklog: (Chapter) -> Unit,
    onDeleteChapter: (String) -> Unit
) {
    var showAddChapter by remember { mutableStateOf(false) }
    val subColor = parseHexColor(subject.colorHex)

    androidx.activity.compose.BackHandler(onBack = onBack)

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddChapter = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Chapter")
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(subject.name, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${subject.code.ifBlank { "Course" }} • ${subject.targetHoursPerWeek}h/wk",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onEditSubject) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Subject")
                    }
                    IconButton(onClick = onDeleteSubject) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Subject", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (subject.notes.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = subColor.copy(alpha = 0.09f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Personal Subject Notes", style = MaterialTheme.typography.labelLarge, color = subColor)
                            Spacer(Modifier.height(4.dp))
                            Text(subject.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                Text("Chapters & Syllabus Topics", style = MaterialTheme.typography.titleLarge)
            }

            if (chapters.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Chapters Added",
                        subtitle = "Tap + to add chapters and topics, and track your completion status.",
                        actionLabel = "Add Chapter",
                        onAction = { showAddChapter = true }
                    )
                }
            } else {
                items(chapters, key = { it.id }) { chap ->
                    val status = ChapterStatus.safeParse(chap.status)
                    val statusIcon = when (status) {
                        ChapterStatus.COMPLETED -> Icons.Default.CheckCircle
                        ChapterStatus.IN_PROGRESS -> Icons.Default.HourglassTop
                        ChapterStatus.NOT_STARTED -> Icons.Default.RadioButtonUnchecked
                    }
                    val statusColor = when (status) {
                        ChapterStatus.COMPLETED -> SuccessEmerald
                        ChapterStatus.IN_PROGRESS -> WarningAmber
                        ChapterStatus.NOT_STARTED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onCycleChapter(chap) }) {
                                    Icon(statusIcon, contentDescription = status.label, tint = statusColor)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(chap.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${status.label} • Est. ${chap.estimatedMinutes}m • Revisions: ${chap.revisionCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor
                                    )
                                }
                                IconButton(onClick = { onToggleBacklog(chap) }) {
                                    Icon(
                                        Icons.Default.BookmarkAdd,
                                        contentDescription = "Toggle Backlog",
                                        tint = if (chap.inBacklog) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteChapter(chap.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Chapter")
                                }
                            }
                            if (chap.topics.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    chap.topics.forEach { topic ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                topic,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
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

    if (showAddChapter) {
        var title by remember { mutableStateOf("") }
        var topicsCsv by remember { mutableStateOf("") }
        var estMins by remember { mutableStateOf("60") }
        var inBacklog by remember { mutableStateOf(false) }
        var backlogPrio by remember { mutableStateOf(PriorityLevel.MEDIUM) }

        AlertDialog(
            onDismissRequest = { showAddChapter = false },
            title = { Text("Add Chapter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Chapter / Unit Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = topicsCsv,
                        onValueChange = { topicsCsv = it },
                        label = { Text("Topics (comma separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = estMins,
                        onValueChange = { estMins = it.filter { c -> c.isDigit() } },
                        label = { Text("Estimated Study Minutes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = inBacklog,
                            onClick = { inBacklog = !inBacklog },
                            label = { Text("Mark as Backlog") }
                        )
                    }
                    if (inBacklog) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PriorityLevel.entries.forEach { p ->
                                FilterChip(
                                    selected = backlogPrio == p,
                                    onClick = { backlogPrio = p },
                                    label = { Text(p.label) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddChapter(title, topicsCsv, estMins.toLongOrNull() ?: 60L, inBacklog, backlogPrio)
                        showAddChapter = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddChapter = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SubjectEditorDialog(
    initial: Subject?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, PriorityLevel, DifficultyLevel, Double, String, Long?) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var code by remember { mutableStateOf(initial?.code ?: "") }
    var colorHex by remember { mutableStateOf(initial?.colorHex ?: SubjectPalette.first()) }
    var iconName by remember { mutableStateOf(initial?.iconName ?: "MenuBook") }
    var priority by remember { mutableStateOf(PriorityLevel.safeParse(initial?.priority)) }
    var difficulty by remember { mutableStateOf(DifficultyLevel.safeParse(initial?.difficulty)) }
    var targetHours by remember { mutableStateOf((initial?.targetHoursPerWeek ?: 5.0).toString()) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var examInDays by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Subject" else "Edit Subject") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("subject_name_input")
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Course Code (e.g. CS301)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Color & Icon", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SubjectPalette) { hex ->
                        val c = parseHexColor(hex)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(
                                    if (colorHex == hex) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SubjectIconOptions) { (key, icon) ->
                        FilterChip(
                            selected = iconName == key,
                            onClick = { iconName = key },
                            label = { Icon(icon, contentDescription = key, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PriorityLevel.entries.forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.label) })
                    }
                }
                Text("Difficulty", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DifficultyLevel.entries.forEach { d ->
                        FilterChip(selected = difficulty == d, onClick = { difficulty = d }, label = { Text(d.label) })
                    }
                }
                OutlinedTextField(
                    value = targetHours,
                    onValueChange = { targetHours = it },
                    label = { Text("Weekly Study Target (Hours)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = examInDays,
                    onValueChange = { examInDays = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Exam in N days (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Personal Notes") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        code,
                        colorHex,
                        iconName,
                        priority,
                        difficulty,
                        targetHours.toDoubleOrNull() ?: 5.0,
                        notes,
                        examInDays.toLongOrNull()
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_subject_button")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SyllabusImporterDialog(
    loading: Boolean,
    onDismiss: () -> Unit,
    onImport: (String, Bitmap?) -> Unit
) {
    val context = LocalContext.current
    var rawText by remember { mutableStateOf("") }
    var attachedImage by remember { mutableStateOf<Bitmap?>(null) }
    var attachedLabel by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                attachedImage = bmp
                attachedLabel = "Syllabus photo attached"
            } catch (_: Exception) {
            }
        }
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val content = reader.readText().take(12000)
                    rawText = content
                    attachedLabel = "Document loaded"
                }
            } catch (_: Exception) {
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("AI Syllabus Importer")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Paste your syllabus outline or attach an image/text document. Gemini AI will extract subjects, chapters, and topics automatically.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        label = { Text("Photo") },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { docPicker.launch(arrayOf("text/*", "application/pdf")) },
                        label = { Text("Document") },
                        leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
                if (attachedLabel.isNotBlank()) {
                    Text(attachedLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("Paste Syllabus Text") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(rawText, attachedImage) },
                enabled = !loading && (rawText.isNotBlank() || attachedImage != null)
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Extract & Import")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
