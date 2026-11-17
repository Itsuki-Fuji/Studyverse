package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.StudyGroup
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.StudyverseViewModel

@Composable
fun StudyGroupsScreen(viewModel: StudyverseViewModel) {
    val groupsUi by viewModel.studyGroupsState.collectAsStateWithLifecycle()
    val messagesUi by viewModel.selectedGroupMessagesState.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val notesUi by viewModel.notesState.collectAsStateWithLifecycle()

    val groups = (groupsUi as? UiState.Success)?.data.orEmpty()
    val notes = (notesUi as? UiState.Success)?.data.orEmpty()
    val activeGroup = groups.firstOrNull { it.id == selectedGroupId }

    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }

    if (activeGroup != null) {
        androidx.activity.compose.BackHandler { viewModel.selectStudyGroup(null) }
        GroupChatDetail(
            group = activeGroup,
            messages = (messagesUi as? UiState.Success)?.data.orEmpty(),
            notes = notes,
            currentUid = viewModel.currentUserId,
            onBack = { viewModel.selectStudyGroup(null) },
            onSend = { text, resTitle, resBody ->
                viewModel.sendGroupMessage(activeGroup, text, resTitle, resBody)
            }
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Study Groups", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Collaborate on doubts, share notes, and hit daily group study goals",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create Group")
                    }
                    OutlinedButton(onClick = { showJoin = true }) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Join via Code")
                    }
                }
            }

            if (groups.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "Study Better Together",
                        subtitle = "Create a group to get a shareable 6-character invite code, or join a classmate's squad.",
                        actionLabel = "Create Study Group",
                        onAction = { showCreate = true }
                    )
                }
            } else {
                items(groups, key = { it.id }) { group ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { viewModel.selectStudyGroup(group.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${group.subjectTag} • ${group.memberUids.size} members • Goal ${group.dailyGoalMinutes}m/d",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                                Text(
                                    group.inviteCode,
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var tag by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var goal by remember { mutableStateOf("120") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create Study Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tag, onValueChange = { tag = it }, label = { Text("Subject Focus *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = goal, onValueChange = { goal = it.filter { c -> c.isDigit() } }, label = { Text("Daily Group Goal (mins)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createStudyGroup(name, tag, desc, goal.toLongOrNull() ?: 120L)
                        showCreate = false
                    },
                    enabled = name.isNotBlank() && tag.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    if (showJoin) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoin = false },
            title = { Text("Join Study Group") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Enter 6-char Invite Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.joinStudyGroup(code)
                        showJoin = false
                    },
                    enabled = code.trim().length >= 4
                ) { Text("Join") }
            },
            dismissButton = { TextButton(onClick = { showJoin = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GroupChatDetail(
    group: StudyGroup,
    messages: List<com.example.data.model.GroupMessage>,
    notes: List<com.example.data.model.StudyNote>,
    currentUid: String,
    onBack: () -> Unit,
    onSend: (String, String, String) -> Unit
) {
    var msgText by remember { mutableStateOf("") }
    var showShareNote by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Invite: ${group.inviteCode} • ${group.memberUids.size} members", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { showShareNote = true }) {
                    Icon(Icons.Default.Share, contentDescription = "Share Note")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { m ->
                val isMe = m.senderId == currentUid
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(m.senderName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(2.dp))
                        Text(m.text, style = MaterialTheme.typography.bodyMedium)
                        if (m.resourceTitle.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Shared Resource: ${m.resourceTitle}", style = MaterialTheme.typography.labelLarge)
                                    if (m.resourceSummary.isNotBlank()) {
                                        Text(m.resourceSummary.take(400), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = msgText,
                    onValueChange = { msgText = it },
                    placeholder = { Text("Discuss doubts with your group...") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        onSend(msgText, "", "")
                        msgText = ""
                    },
                    enabled = msgText.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }

    if (showShareNote) {
        AlertDialog(
            onDismissRequest = { showShareNote = false },
            title = { Text("Share a Study Note") },
            text = {
                if (notes.isEmpty()) {
                    Text("You have no notes yet. Create one in the Notes tab first.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        notes.take(5).forEach { note ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSend("Shared a note for ${note.subjectName}", note.title, note.aiSummary.ifBlank { note.content })
                                        showShareNote = false
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(note.title, style = MaterialTheme.typography.titleMedium)
                                    Text(note.subjectName, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showShareNote = false }) { Text("Close") } }
        )
    }
}
