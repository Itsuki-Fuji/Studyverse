package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.AiStudyRepository
import com.example.data.firebase.StudyverseRepository
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.model.DarkModePref
import com.example.data.prefs.UserPreferencesStore
import com.example.notifications.ReminderScheduler
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.StudyverseApp
import com.example.ui.theme.StudyverseTheme
import com.example.ui.viewmodel.StudyverseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure App Check with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            StudyverseRoot()
        }
    }
}

@Composable
fun StudyverseRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        StudyverseTheme {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: StudyverseViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        StudyverseViewModel(
                            repo = StudyverseRepository(db),
                            aiRepo = AiStudyRepository(),
                            prefsStore = UserPreferencesStore(app),
                            reminderScheduler = ReminderScheduler(app),
                            currentUserId = uid
                        )
                    }
                }
            )
            val darkPref by viewModel.darkModePref.collectAsStateWithLifecycle()
            val darkOverride = when (darkPref) {
                DarkModePref.SYSTEM -> null
                DarkModePref.LIGHT -> false
                DarkModePref.DARK -> true
            }
            StudyverseTheme(darkModeOverride = darkOverride) {
                StudyverseApp(viewModel = viewModel)
            }
        }
    }
}
