package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AiStudyRepository
import com.example.data.firebase.StudyverseRepository
import com.example.data.model.AiChatMessage
import com.example.data.model.AiLearningMode
import com.example.data.model.Chapter
import com.example.data.model.ChapterStatus
import com.example.data.model.DarkModePref
import com.example.data.model.DeadlineCategory
import com.example.data.model.DeadlineItem
import com.example.data.model.DifficultyLevel
import com.example.data.model.FlashcardDeck
import com.example.data.model.GeneratedQuizQuestion
import com.example.data.model.GroupMessage
import com.example.data.model.PriorityLevel
import com.example.data.model.SessionType
import com.example.data.model.StudyGroup
import com.example.data.model.StudyHabit
import com.example.data.model.StudyNote
import com.example.data.model.StudySession
import com.example.data.model.StudySlot
import com.example.data.model.Subject
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.prefs.UserPreferencesStore
import com.example.notifications.AmbientSoundPlayer
import com.example.notifications.ReminderScheduler
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "StudyverseVM"
private const val STOP_TIMEOUT_MILLIS = 5000L

enum class PomodoroPhase(val label: String) {
    FOCUS("Focus Session"),
    SHORT_BREAK("Short Break"),
    LONG_BREAK("Long Break")
}

data class FocusTimerState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val remainingSeconds: Int = 25 * 60,
    val totalSeconds: Int = 25 * 60,
    val running: Boolean = false,
    val distractionFree: Boolean = false,
    val ambientSound: Boolean = false,
    val completedSessionsToday: Int = 0,
    val selectedSubjectId: String = "general",
    val selectedSubjectName: String = "General Focus",
    val taskTitle: String = "Deep Focus Session"
)

data class StudyverseDashboardStats(
    val totalSubjects: Int = 0,
    val todayMinutesDone: Int = 0,
    val todayGoalMinutes: Int = 180,
    val weeklyMinutesDone: Int = 0,
    val currentStreak: Int = 0,
    val pendingDeadlinesCount: Int = 0,
    val overdueDeadlinesCount: Int = 0,
    val syllabusProgressPercent: Int = 0,
    val dailyProductivityScore: Int = 0
)

class StudyverseViewModel(
    private val repo: StudyverseRepository,
    private val aiRepo: AiStudyRepository,
    private val prefsStore: UserPreferencesStore,
    private val reminderScheduler: ReminderScheduler,
    val currentUserId: String
) : ViewModel() {

    private val soundPlayer = AmbientSoundPlayer()
    private var timerJob: Job? = null

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _aiStreamingText = MutableStateFlow<String?>(null)
    val aiStreamingText: StateFlow<String?> = _aiStreamingText.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _aiInsights = MutableStateFlow<UiState<String>?>(null)
    val aiInsights: StateFlow<UiState<String>?> = _aiInsights.asStateFlow()

    private val _generatedQuiz = MutableStateFlow<UiState<List<GeneratedQuizQuestion>>?>(null)
    val generatedQuiz: StateFlow<UiState<List<GeneratedQuizQuestion>>?> = _generatedQuiz.asStateFlow()

    private val _examSummaryState = MutableStateFlow<UiState<String>?>(null)
    val examSummaryState: StateFlow<UiState<String>?> = _examSummaryState.asStateFlow()

    private val _focusState = MutableStateFlow(FocusTimerState())
    val focusState: StateFlow<FocusTimerState> = _focusState.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    init {
        viewModelScope.launch {
            repo.ensureUserProfile().onFailure { err ->
                Log.w(TAG, "Profile init failed: ${err.message}")
            }
            val focus = prefsStore.focusMinutes.first()
            val shortB = prefsStore.shortBreakMinutes.first()
            val longB = prefsStore.longBreakMinutes.first()
            val ambient = prefsStore.ambientSoundEnabled.first()
            _focusState.value = _focusState.value.copy(
                focusMinutes = focus,
                shortBreakMinutes = shortB,
                longBreakMinutes = longB,
                remainingSeconds = focus * 60,
                totalSeconds = focus * 60,
                ambientSound = ambient
            )
        }
    }

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val userProfile: StateFlow<UiState<UserProfile?>> =
        repo.observeUserProfile(currentUserId).toUiState("profile")

    val subjectsState: StateFlow<UiState<List<Subject>>> =
        repo.observeSubjects(currentUserId).toUiState("subjects")

    val chaptersState: StateFlow<UiState<List<Chapter>>> =
        repo.observeChapters(currentUserId).toUiState("chapters")

    val sessionsState: StateFlow<UiState<List<StudySession>>> =
        repo.observeStudySessions(currentUserId).toUiState("study sessions")

    val deadlinesState: StateFlow<UiState<List<DeadlineItem>>> =
        repo.observeDeadlines(currentUserId).toUiState("deadlines")

    val notesState: StateFlow<UiState<List<StudyNote>>> =
        repo.observeNotes(currentUserId).toUiState("notes")

    val habitsState: StateFlow<UiState<List<StudyHabit>>> =
        repo.observeHabits(currentUserId).toUiState("habits")

    val flashcardDecksState: StateFlow<UiState<List<FlashcardDeck>>> =
        repo.observeFlashcardDecks(currentUserId).toUiState("flashcard decks")

    val aiMessagesState: StateFlow<UiState<List<AiChatMessage>>> =
        repo.observeAiMessages(currentUserId).toUiState("AI messages")

    val studyGroupsState: StateFlow<UiState<List<StudyGroup>>> =
        repo.observeStudyGroups(currentUserId).toUiState("study groups")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedGroupMessagesState: StateFlow<UiState<List<GroupMessage>>> =
        _selectedGroupId.flatMapLatest { groupId ->
            if (groupId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repo.observeGroupMessages(groupId, currentUserId)
                    .map<List<GroupMessage>, UiState<List<GroupMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load chat"))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val darkModePref: StateFlow<DarkModePref> =
        prefsStore.darkModePref.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DarkModePref.SYSTEM)

    val ttsEnabled: StateFlow<Boolean> =
        prefsStore.ttsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dashboardStats: StateFlow<StudyverseDashboardStats> = combine(
        userProfile,
        subjectsState,
        chaptersState,
        sessionsState,
        deadlinesState
    ) { profUi, subUi, chapUi, sessUi, dlUi ->
        val profile = (profUi as? UiState.Success)?.data
        val subjects = (subUi as? UiState.Success)?.data.orEmpty()
        val chapters = (chapUi as? UiState.Success)?.data.orEmpty()
        val sessions = (sessUi as? UiState.Success)?.data.orEmpty()
        val deadlines = (dlUi as? UiState.Success)?.data.orEmpty()

        val todayKey = StudyverseRepository.todayDateKey()
        val weekKeys = (0..6).map { StudyverseRepository.dateKeyWithOffset(-it) }.toSet()

        val todayDone = sessions.filter { it.completed && it.dateKey == todayKey }.sumOf { it.durationMinutes.toInt() }
        val weekDone = sessions.filter { it.completed && it.dateKey in weekKeys }.sumOf { it.durationMinutes.toInt() }
        val goalMins = (profile?.dailyStudyTargetMinutes ?: 180L).toInt().coerceAtLeast(15)

        val now = System.currentTimeMillis()
        val pendingDl = deadlines.count { !it.completed }
        val overdueDl = deadlines.count { !it.completed && it.dueAtMillis < now }

        val syllabusPct = if (chapters.isEmpty()) 0 else {
            val done = chapters.count { it.status == ChapterStatus.COMPLETED.name }
            val inProg = chapters.count { it.status == ChapterStatus.IN_PROGRESS.name }
            (((done + inProg * 0.5) / chapters.size) * 100).toInt().coerceIn(0, 100)
        }

        val todaySessions = sessions.filter { it.dateKey == todayKey }
        val sessCompletionRate = if (todaySessions.isEmpty()) (todayDone.toFloat() / goalMins).coerceIn(0f, 1f)
        else todaySessions.count { it.completed }.toFloat() / todaySessions.size
        val goalRate = (todayDone.toFloat() / goalMins).coerceIn(0f, 1f)
        val prodScore = (((sessCompletionRate * 0.5f) + (goalRate * 0.5f)) * 100).toInt().coerceIn(0, 100)

        StudyverseDashboardStats(
            totalSubjects = subjects.size,
            todayMinutesDone = todayDone,
            todayGoalMinutes = goalMins,
            weeklyMinutesDone = weekDone,
            currentStreak = (profile?.currentStreak ?: 0L).toInt(),
            pendingDeadlinesCount = pendingDl,
            overdueDeadlinesCount = overdueDl,
            syllabusProgressPercent = syllabusPct,
            dailyProductivityScore = prodScore
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StudyverseDashboardStats()
    )

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun selectStudyGroup(groupId: String?) {
        _selectedGroupId.value = groupId
    }

    // --- PROFILE & SETTINGS ---
    fun updateProfile(
        displayName: String,
        course: String,
        academicYear: String,
        institution: String,
        dailyTargetMinutes: Long,
        preferredSlot: StudySlot,
        notificationsEnabled: Boolean,
        darkModePref: DarkModePref
    ) {
        viewModelScope.launch {
            prefsStore.setDarkModePref(darkModePref)
            repo.updateProfile(
                displayName = displayName,
                course = course,
                academicYear = academicYear,
                institution = institution,
                dailyStudyTargetMinutes = dailyTargetMinutes,
                preferredStudySlot = preferredSlot,
                notificationsEnabled = notificationsEnabled,
                darkModePref = darkModePref
            ).onSuccess {
                _snackbarMessage.value = "Profile & preferences saved"
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    fun setDarkModePref(pref: DarkModePref) {
        viewModelScope.launch {
            prefsStore.setDarkModePref(pref)
            val profile = (userProfile.value as? UiState.Success)?.data ?: return@launch
            repo.updateProfile(
                displayName = profile.displayName,
                course = profile.course,
                academicYear = profile.academicYear,
                institution = profile.institution,
                dailyStudyTargetMinutes = profile.dailyStudyTargetMinutes,
                preferredStudySlot = StudySlot.safeParse(profile.preferredStudySlot),
                notificationsEnabled = profile.notificationsEnabled,
                darkModePref = pref
            )
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsStore.setTtsEnabled(enabled) }
    }

    // --- SUBJECTS & CHAPTERS ---
    fun addSubject(
        name: String,
        code: String,
        colorHex: String,
        iconName: String,
        priority: PriorityLevel,
        difficulty: DifficultyLevel,
        targetHoursPerWeek: Double,
        notes: String,
        examDateMillis: Long?
    ) {
        viewModelScope.launch {
            repo.addSubject(
                Subject(
                    userId = currentUserId,
                    name = name,
                    code = code,
                    colorHex = colorHex,
                    iconName = iconName,
                    priority = priority.name,
                    difficulty = difficulty.name,
                    targetHoursPerWeek = targetHoursPerWeek,
                    notes = notes,
                    examDateMillis = examDateMillis
                )
            ).onSuccess { newId ->
                _snackbarMessage.value = "Added $name"
                if (examDateMillis != null) {
                    repo.addDeadline(
                        DeadlineItem(
                            userId = currentUserId,
                            subjectId = newId,
                            subjectName = name,
                            title = "$name Examination",
                            description = "Scheduled exam for $name",
                            category = DeadlineCategory.EXTERNAL_EXAM.name,
                            dueAtMillis = examDateMillis,
                            priority = PriorityLevel.HIGH.name,
                            reminderDaysBefore = 3L
                        )
                    )
                }
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "Could not add subject"
            }
        }
    }

    fun updateSubject(subject: Subject) {
        viewModelScope.launch {
            repo.updateSubject(subject)
                .onSuccess { _snackbarMessage.value = "Updated ${subject.name}" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to update subject" }
        }
    }

    fun deleteSubject(subjectId: String) {
        viewModelScope.launch {
            repo.deleteSubject(subjectId)
                .onSuccess { _snackbarMessage.value = "Subject removed" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to remove subject" }
        }
    }

    fun addChapter(
        subjectId: String,
        title: String,
        topicsCsv: String,
        estimatedMinutes: Long,
        inBacklog: Boolean,
        backlogPriority: PriorityLevel
    ) {
        viewModelScope.launch {
            val topics = topicsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
            repo.addChapter(
                Chapter(
                    userId = currentUserId,
                    subjectId = subjectId,
                    title = title,
                    topics = topics,
                    status = ChapterStatus.NOT_STARTED.name,
                    estimatedMinutes = estimatedMinutes,
                    inBacklog = inBacklog,
                    backlogPriority = backlogPriority.name
                )
            ).onSuccess {
                _snackbarMessage.value = "Added chapter $title"
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "Failed to add chapter"
            }
        }
    }

    fun cycleChapterStatus(chapter: Chapter) {
        val next = when (ChapterStatus.safeParse(chapter.status)) {
            ChapterStatus.NOT_STARTED -> ChapterStatus.IN_PROGRESS
            ChapterStatus.IN_PROGRESS -> ChapterStatus.COMPLETED
            ChapterStatus.COMPLETED -> ChapterStatus.NOT_STARTED
        }
        viewModelScope.launch {
            repo.updateChapterStatus(chapter.id, next, chapter.inBacklog)
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to update chapter" }
        }
    }

    fun setChapterStatus(chapter: Chapter, status: ChapterStatus) {
        viewModelScope.launch {
            repo.updateChapterStatus(chapter.id, status, chapter.inBacklog)
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to update chapter" }
        }
    }

    fun markChapterRevised(chapter: Chapter) {
        viewModelScope.launch {
            repo.markChapterRevised(chapter)
                .onSuccess { _snackbarMessage.value = "Revision logged! Next spaced repetition scheduled." }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to update revision" }
        }
    }

    fun toggleBacklog(chapter: Chapter, inBacklog: Boolean, priority: PriorityLevel = PriorityLevel.safeParse(chapter.backlogPriority)) {
        viewModelScope.launch {
            repo.toggleChapterBacklog(chapter.id, inBacklog, priority)
                .onSuccess { _snackbarMessage.value = if (inBacklog) "Added to Backlog" else "Removed from Backlog" }
        }
    }

    fun deleteChapter(chapterId: String) {
        viewModelScope.launch { repo.deleteChapter(chapterId) }
    }

    // --- SYLLABUS IMPORTER (AI) ---
    fun importSyllabusWithAi(rawText: String, image: Bitmap?) {
        if (rawText.isBlank() && image == null) {
            _snackbarMessage.value = "Paste syllabus text or attach an image/PDF first."
            return
        }
        viewModelScope.launch {
            _aiLoading.value = true
            aiRepo.extractSyllabus(rawText, image)
                .onSuccess { extracted ->
                    var count = 0
                    val existingSubjects = (subjectsState.value as? UiState.Success)?.data.orEmpty()
                    extracted.forEachIndexed { idx, subItem ->
                        val existing = existingSubjects.firstOrNull {
                            it.name.equals(subItem.subjectName, ignoreCase = true)
                        }
                        val subjectId = existing?.id ?: run {
                            val palette = listOf("#8C141E", "#C98A12", "#6B232D", "#B45309", "#C81E32")
                            repo.addSubject(
                                Subject(
                                    userId = currentUserId,
                                    name = subItem.subjectName,
                                    colorHex = palette[idx % palette.size],
                                    iconName = "AutoStories",
                                    priority = PriorityLevel.MEDIUM.name,
                                    difficulty = DifficultyLevel.MEDIUM.name,
                                    targetHoursPerWeek = 5.0
                                )
                            ).getOrNull()
                        }
                        if (!subjectId.isNullOrBlank()) {
                            subItem.chapters.forEach { chap ->
                                repo.addChapter(
                                    Chapter(
                                        userId = currentUserId,
                                        subjectId = subjectId,
                                        title = chap.title,
                                        topics = chap.topics,
                                        estimatedMinutes = chap.estimatedMinutes.toLong()
                                    )
                                )
                                count++
                            }
                        }
                    }
                    _snackbarMessage.value = "Imported ${extracted.size} subject(s) & $count chapter(s) via AI!"
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Syllabus extraction failed"
                }
            _aiLoading.value = false
        }
    }

    // --- STUDY SESSIONS & AI SCHEDULER ---
    fun addStudySession(
        subjectId: String,
        subjectName: String,
        title: String,
        dateKey: String,
        startHour: Int,
        startMinute: Int,
        durationMinutes: Int,
        sessionType: SessionType,
        scheduleReminder: Boolean = true
    ) {
        viewModelScope.launch {
            repo.addStudySession(
                StudySession(
                    userId = currentUserId,
                    subjectId = subjectId,
                    subjectName = subjectName,
                    title = title,
                    dateKey = dateKey,
                    startHour = startHour.toLong(),
                    startMinute = startMinute.toLong(),
                    durationMinutes = durationMinutes.toLong(),
                    sessionType = sessionType.name,
                    completed = false,
                    aiGenerated = false
                )
            ).onSuccess { id ->
                _snackbarMessage.value = "Scheduled $title"
                if (scheduleReminder) {
                    scheduleSessionNotification(id, title, subjectName, dateKey, startHour, startMinute)
                }
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "Failed to schedule session"
            }
        }
    }

    fun generateAiStudySchedule(dailyHours: Double, preferredStartHour: Int, horizonDays: Int) {
        val subjects = (subjectsState.value as? UiState.Success)?.data.orEmpty()
        val chapters = (chaptersState.value as? UiState.Success)?.data.orEmpty()
        val deadlines = (deadlinesState.value as? UiState.Success)?.data.orEmpty()

        viewModelScope.launch {
            _aiLoading.value = true
            aiRepo.generateSmartSchedule(
                subjects = subjects,
                chapters = chapters,
                deadlines = deadlines,
                dailyHours = dailyHours,
                preferredStartHour = preferredStartHour,
                horizonDays = horizonDays
            ).onSuccess { blocks ->
                blocks.forEach { block ->
                    val matchedSub = subjects.firstOrNull {
                        it.name.equals(block.subjectName, ignoreCase = true)
                    }
                    val dateKey = StudyverseRepository.dateKeyWithOffset(block.dayOffset)
                    val res = repo.addStudySession(
                        StudySession(
                            userId = currentUserId,
                            subjectId = matchedSub?.id ?: "general",
                            subjectName = matchedSub?.name ?: block.subjectName,
                            title = block.title,
                            dateKey = dateKey,
                            startHour = block.startHour.toLong(),
                            startMinute = block.startMinute.toLong(),
                            durationMinutes = block.durationMinutes.toLong(),
                            sessionType = block.sessionType.name,
                            completed = false,
                            aiGenerated = true
                        )
                    )
                    res.getOrNull()?.let { id ->
                        scheduleSessionNotification(
                            id,
                            block.title,
                            block.subjectName,
                            dateKey,
                            block.startHour,
                            block.startMinute
                        )
                    }
                }
                _snackbarMessage.value = "AI generated ${blocks.size} personalized study blocks!"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "AI schedule generation failed"
            }
            _aiLoading.value = false
        }
    }

    fun rescheduleUnfinishedSessions() {
        val sessions = (sessionsState.value as? UiState.Success)?.data.orEmpty()
        val today = StudyverseRepository.todayDateKey()
        val unfinishedPast = sessions.filter { !it.completed && it.dateKey < today }
        if (unfinishedPast.isEmpty()) {
            _snackbarMessage.value = "No unfinished past sessions to reschedule."
            return
        }
        viewModelScope.launch {
            unfinishedPast.forEachIndexed { idx, session ->
                val newDate = StudyverseRepository.dateKeyWithOffset((idx / 2).coerceAtMost(6))
                val newHour = if (idx % 2 == 0) 17L else 19L
                repo.updateStudySession(
                    sessionId = session.id,
                    dateKey = newDate,
                    startHour = newHour,
                    startMinute = 0L,
                    durationMinutes = session.durationMinutes,
                    title = session.title,
                    completed = false
                )
            }
            _snackbarMessage.value = "Rescheduled ${unfinishedPast.size} unfinished session(s)!"
        }
    }

    fun distributeBacklogAcrossDays(daysAvailable: Int = 5) {
        val chapters = (chaptersState.value as? UiState.Success)?.data.orEmpty()
        val subjects = (subjectsState.value as? UiState.Success)?.data.orEmpty()
        val backlog = chapters.filter { it.inBacklog && it.status != ChapterStatus.COMPLETED.name }
            .sortedBy {
                when (PriorityLevel.safeParse(it.backlogPriority)) {
                    PriorityLevel.HIGH -> 0
                    PriorityLevel.MEDIUM -> 1
                    PriorityLevel.LOW -> 2
                }
            }
        if (backlog.isEmpty()) {
            _snackbarMessage.value = "Your backlog is empty!"
            return
        }
        viewModelScope.launch {
            backlog.forEachIndexed { index, chapter ->
                val subject = subjects.firstOrNull { it.id == chapter.subjectId }
                val dayOffset = index % daysAvailable.coerceIn(1, 7)
                val slotHour = 16 + (index / daysAvailable) % 4
                repo.addStudySession(
                    StudySession(
                        userId = currentUserId,
                        subjectId = chapter.subjectId,
                        subjectName = subject?.name ?: "Backlog",
                        title = "Backlog: ${chapter.title}",
                        dateKey = StudyverseRepository.dateKeyWithOffset(dayOffset),
                        startHour = slotHour.toLong(),
                        startMinute = 0L,
                        durationMinutes = chapter.estimatedMinutes.coerceIn(25L, 90L),
                        sessionType = SessionType.STUDY.name,
                        completed = false,
                        aiGenerated = false
                    )
                )
            }
            _snackbarMessage.value = "Distributed ${backlog.size} backlog chapters across $daysAvailable days!"
        }
    }

    fun toggleSessionCompleted(session: StudySession) {
        val newDone = !session.completed
        viewModelScope.launch {
            repo.toggleSessionCompleted(session.id, newDone).onSuccess {
                if (newDone) {
                    val prof = (userProfile.value as? UiState.Success)?.data
                    repo.recordStudyStreakToday(
                        prof?.currentStreak ?: 0L,
                        prof?.longestStreak ?: 0L,
                        prof?.lastStudyDate ?: ""
                    )
                }
            }
        }
    }

    fun moveSessionToDateAndHour(session: StudySession, newDateKey: String, newHour: Int) {
        viewModelScope.launch {
            repo.updateStudySession(
                sessionId = session.id,
                dateKey = newDateKey,
                startHour = newHour.toLong(),
                startMinute = session.startMinute,
                durationMinutes = session.durationMinutes,
                title = session.title,
                completed = session.completed
            ).onSuccess {
                _snackbarMessage.value = "Moved '${session.title}' to $newDateKey at ${newHour}:00"
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch { repo.deleteStudySession(sessionId) }
    }

    // --- DEADLINES & NOTIFICATIONS ---
    fun addDeadline(
        subjectId: String,
        subjectName: String,
        title: String,
        description: String,
        category: DeadlineCategory,
        dueAtMillis: Long,
        priority: PriorityLevel,
        reminderDaysBefore: Long
    ) {
        viewModelScope.launch {
            repo.addDeadline(
                DeadlineItem(
                    userId = currentUserId,
                    subjectId = subjectId,
                    subjectName = subjectName,
                    title = title,
                    description = description,
                    category = category.name,
                    dueAtMillis = dueAtMillis,
                    priority = priority.name,
                    reminderDaysBefore = reminderDaysBefore
                )
            ).onSuccess { id ->
                _snackbarMessage.value = "Added deadline: $title"
                val reminderTime = dueAtMillis - reminderDaysBefore * 86_400_000L
                reminderScheduler.scheduleReminder(
                    idSeed = "dl_$id",
                    triggerAtMillis = reminderTime,
                    title = "Upcoming ${category.label}: $title",
                    body = "$subjectName due in $reminderDaysBefore day(s)"
                )
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "Failed to add deadline"
            }
        }
    }

    fun toggleDeadlineCompleted(deadline: DeadlineItem) {
        viewModelScope.launch {
            repo.toggleDeadlineCompleted(deadline.id, !deadline.completed)
        }
    }

    fun deleteDeadline(deadlineId: String) {
        viewModelScope.launch { repo.deleteDeadline(deadlineId) }
    }

    // --- NOTES & DOCUMENTS ---
    fun saveNote(
        existingId: String?,
        subjectId: String,
        subjectName: String,
        title: String,
        content: String,
        tagsCsv: String,
        pinned: Boolean,
        bookmarked: Boolean,
        attachmentName: String,
        attachmentUri: String,
        aiSummary: String = ""
    ) {
        val tags = tagsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        viewModelScope.launch {
            if (existingId.isNullOrBlank()) {
                repo.addNote(
                    StudyNote(
                        userId = currentUserId,
                        subjectId = subjectId,
                        subjectName = subjectName,
                        title = title,
                        content = content,
                        aiSummary = aiSummary,
                        tags = tags,
                        pinned = pinned,
                        bookmarked = bookmarked,
                        attachmentName = attachmentName,
                        attachmentUri = attachmentUri
                    )
                ).onSuccess { _snackbarMessage.value = "Note saved" }
            } else {
                repo.updateNote(
                    StudyNote(
                        id = existingId,
                        userId = currentUserId,
                        subjectId = subjectId,
                        subjectName = subjectName,
                        title = title,
                        content = content,
                        aiSummary = aiSummary,
                        tags = tags,
                        pinned = pinned,
                        bookmarked = bookmarked,
                        attachmentName = attachmentName,
                        attachmentUri = attachmentUri
                    )
                ).onSuccess { _snackbarMessage.value = "Note updated" }
            }
        }
    }

    fun toggleNotePinned(note: StudyNote) {
        viewModelScope.launch { repo.updateNote(note.copy(pinned = !note.pinned)) }
    }

    fun toggleNoteBookmarked(note: StudyNote) {
        viewModelScope.launch { repo.updateNote(note.copy(bookmarked = !note.bookmarked)) }
    }

    fun summarizeNoteWithAi(note: StudyNote) {
        viewModelScope.launch {
            _aiLoading.value = true
            aiRepo.summarizeOrGenerateGuide(
                "Summarize these study notes concisely with key takeaways, definitions, and a 3-bullet quick recap:\nTitle: ${note.title}\nSubject: ${note.subjectName}\n\n${note.content}"
            ).onSuccess { summary ->
                repo.updateNote(note.copy(aiSummary = summary))
                _snackbarMessage.value = "AI summary saved to note!"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to summarize note"
            }
            _aiLoading.value = false
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch { repo.deleteNote(noteId) }
    }

    // --- FLASHCARDS & QUIZZES ---
    fun generateFlashcardsFromSource(subjectId: String, title: String, sourceText: String) {
        viewModelScope.launch {
            _aiLoading.value = true
            aiRepo.generateFlashcards(title, sourceText)
                .onSuccess { (deckTitle, cards) ->
                    repo.addFlashcardDeck(subjectId, deckTitle, cards)
                    _snackbarMessage.value = "Created flashcard deck '$deckTitle' (${cards.size} cards)!"
                }
                .onFailure {
                    _snackbarMessage.value = it.localizedMessage ?: "Flashcard generation failed"
                }
            _aiLoading.value = false
        }
    }

    fun deleteFlashcardDeck(deckId: String) {
        viewModelScope.launch { repo.deleteFlashcardDeck(deckId) }
    }

    fun generateQuizFromSource(sourceText: String, count: Int = 5) {
        viewModelScope.launch {
            _generatedQuiz.value = UiState.Loading
            aiRepo.generateQuiz(sourceText, count)
                .onSuccess { _generatedQuiz.value = UiState.Success(it) }
                .onFailure { _generatedQuiz.value = UiState.Error(it.localizedMessage ?: "Quiz failed") }
        }
    }

    fun clearGeneratedQuiz() {
        _generatedQuiz.value = null
    }

    // --- EXAM PREP & AI INSIGHTS ---
    fun generateLastMinuteExamSummary(subject: Subject, chapters: List<Chapter>) {
        viewModelScope.launch {
            _examSummaryState.value = UiState.Loading
            val chapList = chapters.joinToString("\n") { "- ${it.title} (${it.status}): ${it.topics.joinToString()}" }
            aiRepo.summarizeOrGenerateGuide(
                "Create a Last-Minute High-Yield Exam Revision Guide for '${subject.name}'. Include key concepts, common exam questions, memory tricks, and focus areas for incomplete chapters:\n$chapList\nSubject Notes: ${subject.notes}"
            ).onSuccess {
                _examSummaryState.value = UiState.Success(it)
            }.onFailure {
                _examSummaryState.value = UiState.Error(it.localizedMessage ?: "Failed to generate exam guide")
            }
        }
    }

    fun clearExamSummary() {
        _examSummaryState.value = null
    }

    fun generateProductivityCoachInsights() {
        val subjects = (subjectsState.value as? UiState.Success)?.data.orEmpty()
        val chapters = (chaptersState.value as? UiState.Success)?.data.orEmpty()
        val sessions = (sessionsState.value as? UiState.Success)?.data.orEmpty()
        val deadlines = (deadlinesState.value as? UiState.Success)?.data.orEmpty()
        val stats = dashboardStats.value

        viewModelScope.launch {
            _aiInsights.value = UiState.Loading
            val prompt = buildString {
                appendLine("You are Studyverse AI Productivity Coach. Analyze this student's real data and give concise, actionable insights:")
                appendLine("1. Weekly study minutes: ${stats.weeklyMinutesDone}m | Today: ${stats.todayMinutesDone}/${stats.todayGoalMinutes}m | Streak: ${stats.currentStreak} days")
                appendLine("2. Syllabus completion: ${stats.syllabusProgressPercent}% | Overdue deadlines: ${stats.overdueDeadlinesCount} | Pending: ${stats.pendingDeadlinesCount}")
                appendLine("3. Subjects & minutes logged:")
                subjects.forEach { s ->
                    val mins = sessions.filter { it.subjectId == s.id && it.completed }.sumOf { it.durationMinutes }
                    val incomp = chapters.count { it.subjectId == s.id && it.status != "COMPLETED" }
                    appendLine("- ${s.name} (priority=${s.priority}, logged=${mins}m, incompleteChapters=$incomp)")
                }
                val unfinished = sessions.count { !it.completed }
                appendLine("4. Incomplete scheduled sessions: $unfinished")
                appendLine("Answer clearly with: (a) Subjects needing attention, (b) Procrastination/consistency patterns, (c) Most productive timing & realistic schedule tips for next week.")
            }
            aiRepo.summarizeOrGenerateGuide(prompt)
                .onSuccess { _aiInsights.value = UiState.Success(it) }
                .onFailure { _aiInsights.value = UiState.Error(it.localizedMessage ?: "Coach analysis failed") }
        }
    }

    // --- AI TUTOR STREAMING CHAT ---
    fun sendAiTutorMessage(
        prompt: String,
        mode: AiLearningMode,
        subjectName: String,
        attachedImage: Bitmap? = null,
        attachedDocText: String? = null
    ) {
        if (prompt.isBlank() && attachedImage == null && attachedDocText.isNullOrBlank()) return
        val history = (aiMessagesState.value as? UiState.Success)?.data.orEmpty()
        val effectivePrompt = prompt.ifBlank { "Please explain and summarize the attached study material." }
        viewModelScope.launch {
            _aiError.value = null
            _aiLoading.value = true
            _aiStreamingText.value = ""
            repo.addAiMessage("USER", effectivePrompt, mode.title, subjectName)

            val accumulated = StringBuilder()
            try {
                aiRepo.streamTutorResponse(
                    prompt = effectivePrompt,
                    mode = mode,
                    subjectContext = subjectName,
                    history = history,
                    attachedImage = attachedImage,
                    attachedDocumentText = attachedDocText
                ).collect { fullSoFar ->
                    accumulated.clear()
                    accumulated.append(fullSoFar)
                    _aiStreamingText.value = fullSoFar
                }
                if (accumulated.isNotBlank()) {
                    repo.addAiMessage("MODEL", accumulated.toString(), mode.title, subjectName)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Tutor error", e)
                _aiError.value = e.localizedMessage ?: "AI Assistant failed to respond."
            } finally {
                _aiStreamingText.value = null
                _aiLoading.value = false
            }
        }
    }

    fun clearAiConversation() {
        val ids = (aiMessagesState.value as? UiState.Success)?.data?.map { it.id }.orEmpty()
        if (ids.isEmpty()) return
        viewModelScope.launch { repo.clearAiMessages(ids) }
    }

    // --- HABITS ---
    fun addHabit(title: String, targetMinutes: Long, category: String) {
        viewModelScope.launch {
            repo.addHabit(title, targetMinutes, category)
                .onSuccess { _snackbarMessage.value = "Habit added!" }
        }
    }

    fun toggleHabitToday(habit: StudyHabit) {
        viewModelScope.launch { repo.toggleHabitToday(habit) }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch { repo.deleteHabit(habitId) }
    }

    // --- STUDY GROUPS ---
    fun createStudyGroup(name: String, subjectTag: String, description: String, dailyGoalMinutes: Long) {
        viewModelScope.launch {
            repo.createStudyGroup(name, subjectTag, description, dailyGoalMinutes)
                .onSuccess { code -> _snackbarMessage.value = "Created group! Invite Code: $code" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Could not create group" }
        }
    }

    fun joinStudyGroup(inviteCode: String) {
        viewModelScope.launch {
            repo.joinStudyGroupByCode(inviteCode)
                .onSuccess { name -> _snackbarMessage.value = "Joined $name!" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Invalid invite code" }
        }
    }

    fun sendGroupMessage(group: StudyGroup, text: String, resourceTitle: String = "", resourceSummary: String = "") {
        val senderName = (userProfile.value as? UiState.Success)?.data?.displayName ?: "Student"
        viewModelScope.launch {
            repo.sendGroupMessage(group, senderName, text, resourceTitle, resourceSummary)
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to send message" }
        }
    }

    // --- POMODORO FOCUS TIMER ---
    fun selectPomodoroSubject(subjectId: String, subjectName: String) {
        _focusState.value = _focusState.value.copy(
            selectedSubjectId = subjectId,
            selectedSubjectName = subjectName
        )
    }

    fun updateFocusTaskTitle(title: String) {
        _focusState.value = _focusState.value.copy(taskTitle = title)
    }

    fun switchPomodoroPhase(phase: PomodoroPhase) {
        pauseTimer()
        val cur = _focusState.value
        val mins = when (phase) {
            PomodoroPhase.FOCUS -> cur.focusMinutes
            PomodoroPhase.SHORT_BREAK -> cur.shortBreakMinutes
            PomodoroPhase.LONG_BREAK -> cur.longBreakMinutes
        }
        _focusState.value = cur.copy(
            phase = phase,
            remainingSeconds = mins * 60,
            totalSeconds = mins * 60,
            running = false
        )
    }

    fun setCustomTimerDurations(focus: Int, shortBreak: Int, longBreak: Int) {
        viewModelScope.launch { prefsStore.setTimerDurations(focus, shortBreak, longBreak) }
        pauseTimer()
        val cur = _focusState.value
        val activeMins = when (cur.phase) {
            PomodoroPhase.FOCUS -> focus
            PomodoroPhase.SHORT_BREAK -> shortBreak
            PomodoroPhase.LONG_BREAK -> longBreak
        }
        _focusState.value = cur.copy(
            focusMinutes = focus,
            shortBreakMinutes = shortBreak,
            longBreakMinutes = longBreak,
            remainingSeconds = activeMins * 60,
            totalSeconds = activeMins * 60,
            running = false
        )
    }

    fun toggleDistractionFree() {
        _focusState.value = _focusState.value.copy(distractionFree = !_focusState.value.distractionFree)
    }

    fun toggleAmbientSound() {
        val next = !_focusState.value.ambientSound
        _focusState.value = _focusState.value.copy(ambientSound = next)
        viewModelScope.launch { prefsStore.setAmbientSound(next) }
        if (next && _focusState.value.running) {
            soundPlayer.startAmbient(viewModelScope)
        } else if (!next) {
            soundPlayer.stopAmbient()
        }
    }

    fun startOrPauseTimer() {
        if (_focusState.value.running) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        _focusState.value = _focusState.value.copy(running = true)
        if (_focusState.value.ambientSound) {
            soundPlayer.startAmbient(viewModelScope)
        }
        timerJob = viewModelScope.launch {
            while (_focusState.value.remainingSeconds > 0 && _focusState.value.running) {
                delay(1000L)
                val next = (_focusState.value.remainingSeconds - 1).coerceAtLeast(0)
                _focusState.value = _focusState.value.copy(remainingSeconds = next)
            }
            if (_focusState.value.remainingSeconds == 0 && _focusState.value.running) {
                onTimerCompleted()
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        soundPlayer.stopAmbient()
        _focusState.value = _focusState.value.copy(running = false)
    }

    fun resetTimer() {
        pauseTimer()
        val cur = _focusState.value
        val mins = when (cur.phase) {
            PomodoroPhase.FOCUS -> cur.focusMinutes
            PomodoroPhase.SHORT_BREAK -> cur.shortBreakMinutes
            PomodoroPhase.LONG_BREAK -> cur.longBreakMinutes
        }
        _focusState.value = cur.copy(remainingSeconds = mins * 60, totalSeconds = mins * 60)
    }

    fun completeSessionEarlyAndLog() {
        val cur = _focusState.value
        val elapsedMins = ((cur.totalSeconds - cur.remainingSeconds) / 60).coerceAtLeast(1)
        pauseTimer()
        if (cur.phase == PomodoroPhase.FOCUS) {
            logCompletedFocusSession(elapsedMins)
        }
        resetTimer()
    }

    private fun onTimerCompleted() {
        val cur = _focusState.value
        pauseTimer()
        soundPlayer.playCompletionChime(viewModelScope)
        reminderScheduler.showImmediateNotification(
            9001,
            "${cur.phase.label} Complete!",
            if (cur.phase == PomodoroPhase.FOCUS) "Logged ${cur.focusMinutes}m for ${cur.selectedSubjectName}."
            else "Break finished—ready for your next focus sprint?"
        )
        if (cur.phase == PomodoroPhase.FOCUS) {
            logCompletedFocusSession(cur.focusMinutes)
            val nextCount = cur.completedSessionsToday + 1
            val nextPhase = if (nextCount % 4 == 0) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
            val nextMins = if (nextPhase == PomodoroPhase.LONG_BREAK) cur.longBreakMinutes else cur.shortBreakMinutes
            _focusState.value = cur.copy(
                phase = nextPhase,
                completedSessionsToday = nextCount,
                remainingSeconds = nextMins * 60,
                totalSeconds = nextMins * 60,
                running = false
            )
        } else {
            _focusState.value = cur.copy(
                phase = PomodoroPhase.FOCUS,
                remainingSeconds = cur.focusMinutes * 60,
                totalSeconds = cur.focusMinutes * 60,
                running = false
            )
        }
    }

    private fun logCompletedFocusSession(minutes: Int) {
        val cur = _focusState.value
        val cal = java.util.Calendar.getInstance()
        viewModelScope.launch {
            repo.addStudySession(
                StudySession(
                    userId = currentUserId,
                    subjectId = cur.selectedSubjectId,
                    subjectName = cur.selectedSubjectName,
                    title = cur.taskTitle.ifBlank { "Pomodoro Focus" },
                    dateKey = StudyverseRepository.todayDateKey(),
                    startHour = cal.get(java.util.Calendar.HOUR_OF_DAY).toLong(),
                    startMinute = cal.get(java.util.Calendar.MINUTE).toLong(),
                    durationMinutes = minutes.toLong(),
                    sessionType = SessionType.POMODORO.name,
                    completed = true,
                    aiGenerated = false
                )
            ).onSuccess {
                val prof = (userProfile.value as? UiState.Success)?.data
                repo.recordStudyStreakToday(
                    prof?.currentStreak ?: 0L,
                    prof?.longestStreak ?: 0L,
                    prof?.lastStudyDate ?: ""
                )
                _snackbarMessage.value = "Logged ${minutes}m focus session for ${cur.selectedSubjectName}!"
            }
        }
    }

    private fun scheduleSessionNotification(
        sessionId: String,
        title: String,
        subjectName: String,
        dateKey: String,
        hour: Int,
        minute: Int
    ) {
        try {
            val parts = dateKey.split("-")
            if (parts.size != 3) return
            val cal = java.util.Calendar.getInstance().apply {
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), hour, minute, 0)
            }
            val remindAt = cal.timeInMillis - 10 * 60_000L // 10 minutes before
            reminderScheduler.scheduleReminder(
                idSeed = "sess_$sessionId",
                triggerAtMillis = remindAt,
                title = "Study Session in 10 mins",
                body = "$title ($subjectName)"
            )
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        soundPlayer.stopAmbient()
    }
}
