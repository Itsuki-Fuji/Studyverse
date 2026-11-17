package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.firebase.signOut
import com.example.data.model.DarkModePref
import com.example.data.model.StudySlot
import com.example.data.model.UiState
import com.example.notifications.ReminderScheduler
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.StudyverseViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSettingsScreen(viewModel: StudyverseViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val reminderScheduler = remember(context) { ReminderScheduler(context) }

    val profileUi by viewModel.userProfile.collectAsStateWithLifecycle()
    val darkModePref by viewModel.darkModePref.collectAsStateWithLifecycle()
    val profile = (profileUi as? UiState.Success)?.data

    var displayName by remember(profile) { mutableStateOf(profile?.displayName ?: "") }
    var course by remember(profile) { mutableStateOf(profile?.course ?: "") }
    var academicYear by remember(profile) { mutableStateOf(profile?.academicYear ?: "") }
    var institution by remember(profile) { mutableStateOf(profile?.institution ?: "") }
    var dailyTarget by remember(profile) { mutableStateOf((profile?.dailyStudyTargetMinutes ?: 180L).toString()) }
    var preferredSlot by remember(profile) { mutableStateOf(StudySlot.safeParse(profile?.preferredStudySlot)) }
    var notifEnabled by remember(profile) { mutableStateOf(profile?.notificationsEnabled ?: true) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            reminderScheduler.showImmediateNotification(
                777,
                "Studyverse Notifications Active",
                "You'll receive timely reminders before study sessions & deadlines."
            )
        } else {
            viewModel.showMessage("Notification permission denied in system settings.")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Profile & Settings", style = MaterialTheme.typography.headlineMedium)
        }

        // Student Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!profile?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile?.photoUrl,
                            contentDescription = "Profile photo",
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile avatar",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile?.displayName ?: "Student", style = MaterialTheme.typography.titleLarge)
                        Text(profile?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = SuccessEmerald, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cloud Synced with Firestore", style = MaterialTheme.typography.labelSmall, color = SuccessEmerald)
                        }
                    }
                }
            }
        }

        // Academic Details
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Academic Profile", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = course, onValueChange = { course = it }, label = { Text("Course / Major") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = academicYear, onValueChange = { academicYear = it }, label = { Text("Year / Grade") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = dailyTarget,
                            onValueChange = { dailyTarget = it.filter { c -> c.isDigit() } },
                            label = { Text("Daily Target (mins)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(value = institution, onValueChange = { institution = it }, label = { Text("College / School") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Text("Preferred Study Slot", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StudySlot.entries.forEach { slot ->
                            FilterChip(
                                selected = preferredSlot == slot,
                                onClick = { preferredSlot = slot },
                                label = { Text(slot.label) }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.updateProfile(
                                displayName = displayName,
                                course = course,
                                academicYear = academicYear,
                                institution = institution,
                                dailyTargetMinutes = (dailyTarget.toLongOrNull() ?: 180L).coerceIn(15L, 960L),
                                preferredSlot = preferredSlot,
                                notificationsEnabled = notifEnabled,
                                darkModePref = darkModePref
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save Profile & Targets") }
                }
            }
        }

        // Theme & Notifications
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Appearance & Reminders", style = MaterialTheme.typography.titleMedium)
                    Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkModePref.entries.forEach { pref ->
                            FilterChip(
                                selected = darkModePref == pref,
                                onClick = { viewModel.setDarkModePref(pref) },
                                label = { Text(pref.label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Study & Deadline Alerts", style = MaterialTheme.typography.titleMedium)
                            Text("Notify before scheduled sessions & deadlines", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = notifEnabled, onCheckedChange = { notifEnabled = it })
                    }
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !reminderScheduler.hasNotificationPermission()) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                reminderScheduler.showImmediateNotification(
                                    777,
                                    "Studyverse Reminder Check",
                                    "Notifications are working properly!"
                                )
                                viewModel.showMessage("Test notification sent!")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enable / Test Notifications")
                    }
                }
            }
        }

        // Sign Out
        item {
            OutlinedButton(
                onClick = { signOut(credentialManager, scope) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out")
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }
}
