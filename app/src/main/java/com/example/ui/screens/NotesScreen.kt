package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FlashcardDeck
import com.example.data.model.StudyNote
import com.example.data.model.UiState
import com.example.notifications.PdfExporter
import com.example.ui.components.AiQuizModal
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FlashcardViewerModal
import com.example.ui.viewmodel.StudyverseViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(viewModel: StudyverseViewModel) {
    val context = LocalContext.current
    val notesUi by viewModel.notesState.collectAsStateWithLifecycle()
    val subjectsUi by viewModel.subjectsState.collectAsStateWithLifecycle()
    val decksUi by viewModel.flashcardDecksState.collectAsStateWithLifecycle()
    val quizState by viewModel.generatedQuiz.collectAsStateWithLifecycle()

    val notes = (notesUi as? UiState.Success)?.data.orEmpty()
    val subjects = (subjectsUi as? UiState.Success)?.data.orEmpty()
    val decks = (decksUi as? UiState.Success)?.data.orEmpty()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Notes, 1 = Flashcard Decks
    var searchQuery by remember { mutableStateOf("") }
    var subjectFilter by remember { mutableStateOf("ALL") }
    var editingNote by remember { mutableStateOf<StudyNote?>(null) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var viewingDeck by remember { mutableStateOf<FlashcardDeck?>(null) }
    var pendingPdfNote by remember { mutableStateOf<StudyNote?>(null) }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val note = pendingPdfNote
        if (uri != null && note != null) {
            val fullBody = buildString {
                appendLine(note.content)
                if (note.aiSummary.isNotBlank()) {
                    appendLine("\n\n--- AI SUMMARY ---\n")
                    appendLine(note.aiSummary)
                }
            }
            PdfExporter.exportStudyMaterialToUri(
                context = context,
                uri = uri,
                title = note.title,
                subtitle = "Subject: ${note.subjectName}",
                body = fullBody
            ).onSuccess {
                viewModel.showMessage("Exported '${note.title}' to PDF!")
            }.onFailure {
                viewModel.showMessage(it.localizedMessage ?: "PDF export failed")
            }
        }
        pendingPdfNote = null
    }

    val filteredNotes = notes.filter { n ->
        val matchesQuery = searchQuery.isBlank() ||
            n.title.contains(searchQuery, ignoreCase = true) ||
            n.content.contains(searchQuery, ignoreCase = true) ||
            n.tags.any { it.contains(searchQuery, ignoreCase = true) }
        val matchesSub = subjectFilter == "ALL" || n.subjectId == subjectFilter
        matchesQuery && matchesSub
    }.sortedWith(compareByDescending<StudyNote> { it.pinned }.thenByDescending { it.bookmarked })

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        editingNote = null
                        showNoteEditor = true
                    },
                    modifier = Modifier.testTag("add_note_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note")
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
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Notebook (${notes.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Flashcards (${decks.size})") })
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text("Search notes, tags, or topics...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (subjects.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    FilterChip(selected = subjectFilter == "ALL", onClick = { subjectFilter = "ALL" }, label = { Text("All") })
                                }
                                items(subjects) { sub ->
                                    FilterChip(
                                        selected = subjectFilter == sub.id,
                                        onClick = { subjectFilter = sub.id },
                                        label = { Text(sub.name) }
                                    )
                                }
                            }
                        }
                    }

                    if (filteredNotes.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "Your Digital Notebook",
                                subtitle = "Create structured study notes, attach files, generate AI summaries, convert notes to flashcards or quizzes, and export as PDF.",
                                actionLabel = "Create Note",
                                onAction = {
                                    editingNote = null
                                    showNoteEditor = true
                                }
                            )
                        }
                    } else {
                        items(filteredNotes, key = { it.id }) { note ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(note.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(note.subjectName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { viewModel.toggleNotePinned(note) }) {
                                            Icon(
                                                Icons.Default.PushPin,
                                                contentDescription = "Pin",
                                                tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { viewModel.toggleNoteBookmarked(note) }) {
                                            Icon(
                                                if (note.bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = "Bookmark",
                                                tint = if (note.bookmarked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = {
                                            editingNote = note
                                            showNoteEditor = true
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Note")
                                        }
                                        IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Note")
                                        }
                                    }

                                    Text(
                                        note.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (note.attachmentName.isNotBlank()) {
                                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(note.attachmentName, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }

                                    if (note.aiSummary.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text("AI Summary", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.height(2.dp))
                                                Text(note.aiSummary, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }

                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AssistChip(
                                            onClick = { viewModel.summarizeNoteWithAi(note) },
                                            label = { Text("AI Summarize") },
                                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                        )
                                        AssistChip(
                                            onClick = { viewModel.generateFlashcardsFromSource(note.subjectId, note.title, note.content) },
                                            label = { Text("To Flashcards") },
                                            leadingIcon = { Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                        )
                                        AssistChip(
                                            onClick = { viewModel.generateQuizFromSource("${note.title}\n${note.content}") },
                                            label = { Text("Quiz") },
                                            leadingIcon = { Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                        )
                                        AssistChip(
                                            onClick = {
                                                pendingPdfNote = note
                                                pdfExportLauncher.launch("${note.title.replace(" ", "_")}.pdf")
                                            },
                                            label = { Text("Export PDF") },
                                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (decks.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Flashcard Decks Yet",
                                subtitle = "Convert any note or AI Tutor explanation into interactive flip flashcards with one tap!"
                            )
                        }
                    } else {
                        items(decks, key = { it.id }) { deck ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Style, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(deck.title, style = MaterialTheme.typography.titleMedium)
                                        Text("${deck.cards.size} cards", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Button(onClick = { viewingDeck = deck }) { Text("Study") }
                                    IconButton(onClick = { viewModel.deleteFlashcardDeck(deck.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Deck")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNoteEditor) {
        NoteEditorDialog(
            initial = editingNote,
            subjects = subjects,
            onDismiss = { showNoteEditor = false },
            onSave = { subId, subName, title, content, tagsCsv, attName, attUri ->
                viewModel.saveNote(
                    existingId = editingNote?.id,
                    subjectId = subId,
                    subjectName = subName,
                    title = title,
                    content = content,
                    tagsCsv = tagsCsv,
                    pinned = editingNote?.pinned ?: false,
                    bookmarked = editingNote?.bookmarked ?: false,
                    attachmentName = attName,
                    attachmentUri = attUri,
                    aiSummary = editingNote?.aiSummary ?: ""
                )
                showNoteEditor = false
            }
        )
    }

    viewingDeck?.let { deck ->
        FlashcardViewerModal(deck = deck, onDismiss = { viewingDeck = null })
    }

    quizState?.let { state ->
        AiQuizModal(quizState = state, onDismiss = { viewModel.clearGeneratedQuiz() })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteEditorDialog(
    initial: StudyNote?,
    subjects: List<com.example.data.model.Subject>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var tagsCsv by remember { mutableStateOf(initial?.tags?.joinToString(", ") ?: "") }
    var selectedSubId by remember { mutableStateOf(initial?.subjectId ?: subjects.firstOrNull()?.id ?: "general") }
    var selectedSubName by remember { mutableStateOf(initial?.subjectName ?: subjects.firstOrNull()?.name ?: "General") }
    var attachmentName by remember { mutableStateOf(initial?.attachmentName ?: "") }
    var attachmentUri by remember { mutableStateOf(initial?.attachmentUri ?: "") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            attachmentUri = uri.toString()
            attachmentName = "Attached Image"
        }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            attachmentUri = uri.toString()
            attachmentName = uri.lastPathSegment ?: "Attached Document"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Study Note" else "Edit Note") },
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
                    label = { Text("Note Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (subjects.isNotEmpty()) {
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
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content & Key Takeaways") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagsCsv,
                    onValueChange = { tagsCsv = it },
                    label = { Text("Tags (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        label = { Text("Add Image") }
                    )
                    AssistChip(
                        onClick = { docPicker.launch(arrayOf("application/pdf", "text/*")) },
                        label = { Text("Attach PDF") }
                    )
                }
                if (attachmentName.isNotBlank()) {
                    Text("Attached: $attachmentName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedSubId, selectedSubName, title, content, tagsCsv, attachmentName, attachmentUri) },
                enabled = title.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
