package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AiLearningMode
import com.example.data.model.UiState
import com.example.ui.components.AiQuizModal
import com.example.ui.viewmodel.StudyverseViewModel
import java.util.Locale

@Composable
fun AiAssistantScreen(viewModel: StudyverseViewModel) {
    val context = LocalContext.current
    val messagesUi by viewModel.aiMessagesState.collectAsStateWithLifecycle()
    val subjectsUi by viewModel.subjectsState.collectAsStateWithLifecycle()
    val streamingText by viewModel.aiStreamingText.collectAsStateWithLifecycle()
    val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()
    val aiError by viewModel.aiError.collectAsStateWithLifecycle()
    val quizState by viewModel.generatedQuiz.collectAsStateWithLifecycle()
    val ttsEnabled by viewModel.ttsEnabled.collectAsStateWithLifecycle()

    val messages = (messagesUi as? UiState.Success)?.data.orEmpty()
    val subjects = (subjectsUi as? UiState.Success)?.data.orEmpty()

    var selectedMode by remember { mutableStateOf(AiLearningMode.DEEP_LEARNING) }
    var selectedSubject by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    var attachedImage by remember { mutableStateOf<Bitmap?>(null) }
    var attachedDocText by remember { mutableStateOf<String?>(null) }
    var attachedDocName by remember { mutableStateOf<String?>(null) }
    var listeningVoice by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Text-to-Speech setup
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
            }
        }
        tts = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    // Speak latest model reply when TTS is enabled and streaming finishes
    var lastSpokenId by remember { mutableStateOf("") }
    LaunchedEffect(messages.size, ttsEnabled) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
            val last = messages.last()
            if (ttsEnabled && last.role == "MODEL" && last.id != lastSpokenId) {
                lastSpokenId = last.id
                tts?.speak(last.text.take(800), TextToSpeech.QUEUE_FLUSH, null, "ai_reply")
            }
        }
    }

    // Voice SpeechRecognizer
    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }
    DisposableEffect(speechRecognizer) {
        onDispose { speechRecognizer?.destroy() }
    }

    val startVoiceInput = {
        if (speechRecognizer == null) {
            viewModel.showMessage("Voice recognition not available on this device.")
        } else {
            listeningVoice = true
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listeningVoice = false }
                override fun onError(error: Int) {
                    listeningVoice = false
                    viewModel.showMessage("Voice input cancelled or didn't catch audio.")
                }
                override fun onResults(results: Bundle?) {
                    listeningVoice = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        inputText = spoken
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechRecognizer.startListening(intent)
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceInput()
        else viewModel.showMessage("Microphone permission is needed for voice questions.")
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                attachedImage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (_: Exception) {
                viewModel.showMessage("Could not load selected image.")
            }
        }
    }

    val docPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    attachedDocText = reader.readText().take(12000)
                    attachedDocName = uri.lastPathSegment ?: "Document"
                }
            } catch (_: Exception) {
                viewModel.showMessage("Could not read document text.")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header + Learning Modes
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("AI Study Assistant", style = MaterialTheme.typography.titleLarge)
                            Text(selectedMode.tagline, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row {
                        IconButton(onClick = {
                            val next = !ttsEnabled
                            viewModel.setTtsEnabled(next)
                            if (!next) tts?.stop()
                        }) {
                            Icon(
                                if (ttsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "Toggle Spoken Replies",
                                tint = if (ttsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.clearAiConversation() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat")
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AiLearningMode.entries) { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.title) }
                        )
                    }
                }
                if (subjects.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedSubject.isBlank(),
                                onClick = { selectedSubject = "" },
                                label = { Text("All Subjects") }
                            )
                        }
                        items(subjects) { sub ->
                            FilterChip(
                                selected = selectedSubject == sub.name,
                                onClick = { selectedSubject = sub.name },
                                label = { Text(sub.name) }
                            )
                        }
                    }
                }
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && streamingText == null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("How can I help you study today?", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Ask academic doubts, paste code or math problems, attach textbook photos/documents, or tap a quick starter below:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(
                                    listOf(
                                        "Explain recursion with an everyday analogy",
                                        "Give me 5 exam-ready revision points on Thermodynamics",
                                        "Help me solve this step by step"
                                    )
                                ) { prompt ->
                                    AssistChip(
                                        onClick = {
                                            viewModel.sendAiTutorMessage(prompt, selectedMode, selectedSubject)
                                        },
                                        label = { Text(prompt) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "USER"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isUser) 18.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 18.dp
                        ),
                        tonalElevation = if (isUser) 0.dp else 2.dp,
                        modifier = Modifier.widthIn(max = 330.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (!isUser) {
                                Text(
                                    msg.mode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(
                                msg.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            if (!isUser) {
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AssistChip(
                                        onClick = { viewModel.generateQuizFromSource(msg.text) },
                                        label = { Text("Quiz Me") },
                                        leadingIcon = { Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                    AssistChip(
                                        onClick = {
                                            viewModel.generateFlashcardsFromSource(
                                                subjectId = subjects.firstOrNull { it.name == selectedSubject }?.id ?: "general",
                                                title = selectedSubject.ifBlank { "AI Study Deck" },
                                                sourceText = msg.text
                                            )
                                        },
                                        label = { Text("Flashcards") },
                                        leadingIcon = { Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (streamingText != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier.widthIn(max = 330.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Thinking...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(streamingText.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            if (aiError != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            aiError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Attachment Preview + Input Bar
        Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (attachedImage != null || attachedDocName != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        attachedImage?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Attached photo",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        attachedDocName?.let { name ->
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                                Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp))
                            }
                        }
                        IconButton(onClick = {
                            attachedImage = null
                            attachedDocText = null
                            attachedDocName = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Attachment", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Default.Image, contentDescription = "Attach Image")
                    }
                    IconButton(onClick = { docPicker.launch(arrayOf("text/*", "application/pdf")) }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach Document")
                    }
                    IconButton(onClick = {
                        if (listeningVoice) {
                            speechRecognizer?.stopListening()
                            listeningVoice = false
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startVoiceInput()
                        } else {
                            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) {
                        Icon(
                            if (listeningVoice) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = if (listeningVoice) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask a doubt...") },
                        maxLines = 3,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_prompt_input")
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            val prompt = inputText
                            val img = attachedImage
                            val doc = attachedDocText
                            inputText = ""
                            attachedImage = null
                            attachedDocText = null
                            attachedDocName = null
                            viewModel.sendAiTutorMessage(prompt, selectedMode, selectedSubject, img, doc)
                        },
                        enabled = !aiLoading && (inputText.isNotBlank() || attachedImage != null || attachedDocText != null),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("ai_send_button")
                    ) {
                        if (aiLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    quizState?.let { state ->
        AiQuizModal(quizState = state, onDismiss = { viewModel.clearGeneratedQuiz() })
    }
}
