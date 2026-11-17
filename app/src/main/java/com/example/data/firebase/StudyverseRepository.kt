package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AiChatMessage
import com.example.data.model.Chapter
import com.example.data.model.ChapterStatus
import com.example.data.model.DarkModePref
import com.example.data.model.DeadlineCategory
import com.example.data.model.DeadlineItem
import com.example.data.model.FlashcardDeck
import com.example.data.model.GroupMessage
import com.example.data.model.PriorityLevel
import com.example.data.model.StudyGroup
import com.example.data.model.StudyHabit
import com.example.data.model.StudyNote
import com.example.data.model.StudySession
import com.example.data.model.StudySlot
import com.example.data.model.Subject
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudyverseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Studyverse data.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun <T> observeUserSubcollection(
        userId: String,
        collectionName: String,
        clazz: Class<T>
    ): Flow<List<T>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Authenticated user mismatch." }
        val path = "users/$uid/$collectionName"
        userDoc(uid).collection(collectionName)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Authenticated user mismatch." }
        val path = "users/$uid"
        userDoc(uid).snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else null
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeSubjects(userId: String): Flow<List<Subject>> =
        observeUserSubcollection(userId, "subjects", Subject::class.java)

    fun observeChapters(userId: String): Flow<List<Chapter>> =
        observeUserSubcollection(userId, "chapters", Chapter::class.java)

    fun observeStudySessions(userId: String): Flow<List<StudySession>> =
        observeUserSubcollection(userId, "study_sessions", StudySession::class.java)

    fun observeDeadlines(userId: String): Flow<List<DeadlineItem>> =
        observeUserSubcollection(userId, "deadlines", DeadlineItem::class.java)

    fun observeNotes(userId: String): Flow<List<StudyNote>> =
        observeUserSubcollection(userId, "notes", StudyNote::class.java)

    fun observeHabits(userId: String): Flow<List<StudyHabit>> =
        observeUserSubcollection(userId, "habits", StudyHabit::class.java)

    fun observeFlashcardDecks(userId: String): Flow<List<FlashcardDeck>> =
        observeUserSubcollection(userId, "flashcard_decks", FlashcardDeck::class.java)

    fun observeAiMessages(userId: String): Flow<List<AiChatMessage>> =
        observeUserSubcollection(userId, "ai_messages", AiChatMessage::class.java)
            .map { list -> list.sortedBy { it.createdAt ?: Timestamp(0, 0) } }

    fun observeStudyGroups(userId: String): Flow<List<StudyGroup>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Authenticated user mismatch." }
        val path = "study_groups"
        db.collection("study_groups")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    StudyGroup::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeGroupMessages(groupId: String, userId: String): Flow<List<GroupMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Authenticated user mismatch." }
        val path = "study_groups/$groupId/messages"
        db.collection("study_groups").document(groupId).collection("messages")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    GroupMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun ensureUserProfile(): Result<Unit> {
        var path = "users"
        return try {
            val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
            val uid = user.uid
            path = "users/$uid"
            val docRef = userDoc(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to (user.displayName?.take(100)?.ifBlank { "Student" } ?: "Student"),
                    "email" to user.email?.take(150),
                    "photoUrl" to user.photoUrl?.toString()?.take(600),
                    "course" to "",
                    "academicYear" to "",
                    "institution" to "",
                    "dailyStudyTargetMinutes" to 180L,
                    "preferredStudySlot" to StudySlot.MORNING.name,
                    "notificationsEnabled" to true,
                    "darkModePref" to DarkModePref.SYSTEM.name,
                    "currentStreak" to 0L,
                    "longestStreak" to 0L,
                    "lastStudyDate" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
                seedStarterHabits(uid)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private suspend fun seedStarterHabits(uid: String) {
        val starterHabits = listOf(
            Triple("Study for 2 hours", 120L, "Focus"),
            Triple("Revise today's topics", 30L, "Revision"),
            Triple("Complete pending assignments", 45L, "Deadlines"),
            Triple("Read for 30 minutes", 30L, "Reading")
        )
        starterHabits.forEach { (title, mins, cat) ->
            val ref = userDoc(uid).collection("habits").document()
            ref.set(
                mapOf(
                    "userId" to uid,
                    "title" to title,
                    "targetMinutes" to mins,
                    "category" to cat,
                    "streak" to 0L,
                    "completedDates" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun updateProfile(
        displayName: String,
        course: String,
        academicYear: String,
        institution: String,
        dailyStudyTargetMinutes: Long,
        preferredStudySlot: StudySlot,
        notificationsEnabled: Boolean,
        darkModePref: DarkModePref
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val cleanName = displayName.trim().take(100).ifBlank { "Student" }
            val clampedTarget = dailyStudyTargetMinutes.coerceIn(15L, 960L)
            userDoc(uid).update(
                mapOf(
                    "displayName" to cleanName,
                    "course" to course.trim().take(120),
                    "academicYear" to academicYear.trim().take(60),
                    "institution" to institution.trim().take(150),
                    "dailyStudyTargetMinutes" to clampedTarget,
                    "preferredStudySlot" to preferredStudySlot.name,
                    "notificationsEnabled" to notificationsEnabled,
                    "darkModePref" to darkModePref.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordStudyStreakToday(currentStreak: Long, longestStreak: Long, lastStudyDate: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val today = todayDateKey()
            if (lastStudyDate == today) return Result.success(Unit)
            val yesterday = yesterdayDateKey()
            val newStreak = if (lastStudyDate == yesterday) currentStreak + 1L else 1L
            val newLongest = maxOf(longestStreak, newStreak)
            userDoc(uid).update(
                mapOf(
                    "currentStreak" to newStreak,
                    "longestStreak" to newLongest,
                    "lastStudyDate" to today,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- SUBJECTS ---
    suspend fun addSubject(subject: Subject): Result<String> {
        var path = "users/subjects"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("subjects").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to subject.name.trim().take(100).ifBlank { "Untitled Subject" },
                "code" to subject.code.trim().take(30),
                "colorHex" to subject.colorHex.take(12).ifBlank { "#8C141E" },
                "iconName" to subject.iconName.take(40).ifBlank { "MenuBook" },
                "priority" to PriorityLevel.safeParse(subject.priority).name,
                "difficulty" to subject.difficulty,
                "targetHoursPerWeek" to subject.targetHoursPerWeek.coerceIn(0.5, 100.0),
                "notes" to subject.notes.take(4000),
                "examDateMillis" to subject.examDateMillis,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSubject(subject: Subject): Result<Unit> {
        var path = "users/subjects"
        return try {
            val uid = requireUserId()
            require(subject.id.isNotBlank())
            val docRef = userDoc(uid).collection("subjects").document(subject.id)
            path = docRef.path
            val updates = mutableMapOf<String, Any>(
                "name" to subject.name.trim().take(100).ifBlank { "Untitled Subject" },
                "code" to subject.code.trim().take(30),
                "colorHex" to subject.colorHex.take(12),
                "iconName" to subject.iconName.take(40),
                "priority" to subject.priority,
                "difficulty" to subject.difficulty,
                "targetHoursPerWeek" to subject.targetHoursPerWeek.coerceIn(0.5, 100.0),
                "notes" to subject.notes.take(4000),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            subject.examDateMillis?.let { updates["examDateMillis"] = it }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSubject(subjectId: String): Result<Unit> {
        var path = "users/subjects/$subjectId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("subjects").document(subjectId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- CHAPTERS ---
    suspend fun addChapter(chapter: Chapter): Result<String> {
        var path = "users/chapters"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("chapters").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "subjectId" to chapter.subjectId,
                "title" to chapter.title.trim().take(160).ifBlank { "Chapter" },
                "topics" to chapter.topics.take(40).map { it.take(100) },
                "status" to ChapterStatus.safeParse(chapter.status).name,
                "revisionCount" to chapter.revisionCount.coerceIn(0L, 1000L),
                "lastRevisedMillis" to chapter.lastRevisedMillis,
                "nextRevisionMillis" to chapter.nextRevisionMillis,
                "estimatedMinutes" to chapter.estimatedMinutes.coerceIn(5L, 1200L),
                "backlogPriority" to PriorityLevel.safeParse(chapter.backlogPriority).name,
                "inBacklog" to chapter.inBacklog,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateChapterStatus(chapterId: String, status: ChapterStatus, inBacklog: Boolean): Result<Unit> {
        var path = "users/chapters/$chapterId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("chapters").document(chapterId)
            path = docRef.path
            val now = System.currentTimeMillis()
            val updates = mutableMapOf<String, Any>(
                "status" to status.name,
                "inBacklog" to (inBacklog && status != ChapterStatus.COMPLETED),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (status == ChapterStatus.COMPLETED) {
                updates["lastRevisedMillis"] = now
                updates["nextRevisionMillis"] = now + 86_400_000L * 2 // First spaced repetition in 2 days
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markChapterRevised(chapter: Chapter): Result<Unit> {
        var path = "users/chapters/${chapter.id}"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("chapters").document(chapter.id)
            path = docRef.path
            val newCount = (chapter.revisionCount + 1L).coerceAtMost(1000L)
            val intervalDays = when (newCount) {
                1L -> 1L
                2L -> 3L
                3L -> 7L
                4L -> 14L
                else -> 30L
            }
            val now = System.currentTimeMillis()
            docRef.update(
                mapOf(
                    "revisionCount" to newCount,
                    "lastRevisedMillis" to now,
                    "nextRevisionMillis" to (now + intervalDays * 86_400_000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleChapterBacklog(chapterId: String, inBacklog: Boolean, priority: PriorityLevel): Result<Unit> {
        var path = "users/chapters/$chapterId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("chapters").document(chapterId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "inBacklog" to inBacklog,
                    "backlogPriority" to priority.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteChapter(chapterId: String): Result<Unit> {
        var path = "users/chapters/$chapterId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("chapters").document(chapterId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- STUDY SESSIONS ---
    suspend fun addStudySession(session: StudySession): Result<String> {
        var path = "users/study_sessions"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("study_sessions").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "subjectId" to session.subjectId.ifBlank { "general" },
                "subjectName" to session.subjectName.trim().take(100).ifBlank { "General" },
                "title" to session.title.trim().take(160).ifBlank { "Study Session" },
                "dateKey" to session.dateKey.take(10).ifBlank { todayDateKey() },
                "startHour" to session.startHour.coerceIn(0L, 23L),
                "startMinute" to session.startMinute.coerceIn(0L, 59L),
                "durationMinutes" to session.durationMinutes.coerceIn(1L, 600L),
                "sessionType" to session.sessionType,
                "completed" to session.completed,
                "aiGenerated" to session.aiGenerated,
                "notes" to session.notes.take(1000),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateStudySession(
        sessionId: String,
        dateKey: String,
        startHour: Long,
        startMinute: Long,
        durationMinutes: Long,
        title: String,
        completed: Boolean
    ): Result<Unit> {
        var path = "users/study_sessions/$sessionId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("study_sessions").document(sessionId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "dateKey" to dateKey.take(10),
                    "startHour" to startHour.coerceIn(0L, 23L),
                    "startMinute" to startMinute.coerceIn(0L, 59L),
                    "durationMinutes" to durationMinutes.coerceIn(1L, 600L),
                    "title" to title.trim().take(160).ifBlank { "Study Session" },
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleSessionCompleted(sessionId: String, completed: Boolean): Result<Unit> {
        var path = "users/study_sessions/$sessionId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("study_sessions").document(sessionId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStudySession(sessionId: String): Result<Unit> {
        var path = "users/study_sessions/$sessionId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("study_sessions").document(sessionId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- DEADLINES ---
    suspend fun addDeadline(deadline: DeadlineItem): Result<String> {
        var path = "users/deadlines"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("deadlines").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "subjectId" to deadline.subjectId.ifBlank { "general" },
                "subjectName" to deadline.subjectName.trim().take(100).ifBlank { "General" },
                "title" to deadline.title.trim().take(160).ifBlank { "Task" },
                "description" to deadline.description.take(2000),
                "category" to DeadlineCategory.safeParse(deadline.category).name,
                "dueAtMillis" to deadline.dueAtMillis.coerceAtLeast(1L),
                "priority" to PriorityLevel.safeParse(deadline.priority).name,
                "reminderDaysBefore" to deadline.reminderDaysBefore.coerceIn(0L, 30L),
                "completed" to deadline.completed,
                "completedAtMillis" to deadline.completedAtMillis,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleDeadlineCompleted(deadlineId: String, completed: Boolean): Result<Unit> {
        var path = "users/deadlines/$deadlineId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("deadlines").document(deadlineId)
            path = docRef.path
            val updates = mutableMapOf<String, Any>(
                "completed" to completed,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (completed) {
                updates["completedAtMillis"] = System.currentTimeMillis()
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDeadline(deadlineId: String): Result<Unit> {
        var path = "users/deadlines/$deadlineId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("deadlines").document(deadlineId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- NOTES ---
    suspend fun addNote(note: StudyNote): Result<String> {
        var path = "users/notes"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("notes").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "subjectId" to note.subjectId.ifBlank { "general" },
                "subjectName" to note.subjectName.trim().take(100).ifBlank { "General" },
                "title" to note.title.trim().take(200).ifBlank { "Untitled Note" },
                "content" to note.content.take(30000),
                "aiSummary" to note.aiSummary.take(8000),
                "tags" to note.tags.take(15).map { it.take(40) },
                "pinned" to note.pinned,
                "bookmarked" to note.bookmarked,
                "attachmentName" to note.attachmentName.take(200),
                "attachmentUri" to note.attachmentUri.take(600),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateNote(note: StudyNote): Result<Unit> {
        var path = "users/notes/${note.id}"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("notes").document(note.id)
            path = docRef.path
            docRef.update(
                mapOf(
                    "subjectId" to note.subjectId.ifBlank { "general" },
                    "subjectName" to note.subjectName.trim().take(100).ifBlank { "General" },
                    "title" to note.title.trim().take(200).ifBlank { "Untitled Note" },
                    "content" to note.content.take(30000),
                    "aiSummary" to note.aiSummary.take(8000),
                    "tags" to note.tags.take(15).map { it.take(40) },
                    "pinned" to note.pinned,
                    "bookmarked" to note.bookmarked,
                    "attachmentName" to note.attachmentName.take(200),
                    "attachmentUri" to note.attachmentUri.take(600),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(noteId: String): Result<Unit> {
        var path = "users/notes/$noteId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("notes").document(noteId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- HABITS ---
    suspend fun addHabit(title: String, targetMinutes: Long, category: String): Result<String> {
        var path = "users/habits"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("habits").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "title" to title.trim().take(120).ifBlank { "Study Habit" },
                    "targetMinutes" to targetMinutes.coerceIn(1L, 720L),
                    "category" to category.take(40).ifBlank { "Study" },
                    "streak" to 0L,
                    "completedDates" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleHabitToday(habit: StudyHabit): Result<Unit> {
        var path = "users/habits/${habit.id}"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("habits").document(habit.id)
            path = docRef.path
            val today = todayDateKey()
            val updatedDates = if (today in habit.completedDates) {
                habit.completedDates.filterNot { it == today }
            } else {
                (habit.completedDates + today).distinct().takeLast(366)
            }
            val newStreak = computeHabitStreak(updatedDates)
            docRef.update(
                mapOf(
                    "completedDates" to updatedDates,
                    "streak" to newStreak,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteHabit(habitId: String): Result<Unit> {
        var path = "users/habits/$habitId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("habits").document(habitId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- FLASHCARD DECKS ---
    suspend fun addFlashcardDeck(subjectId: String, title: String, cards: List<Pair<String, String>>): Result<String> {
        var path = "users/flashcard_decks"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("flashcard_decks").document()
            path = docRef.path
            val cardMaps = cards.take(50).map { (q, a) -> mapOf("front" to q, "back" to a) }
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "subjectId" to subjectId.ifBlank { "general" },
                    "title" to title.trim().take(160).ifBlank { "Flashcards" },
                    "cards" to cardMaps,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteFlashcardDeck(deckId: String): Result<Unit> {
        var path = "users/flashcard_decks/$deckId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("flashcard_decks").document(deckId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- AI CHAT MESSAGES ---
    suspend fun addAiMessage(role: String, text: String, mode: String, subjectName: String): Result<Unit> {
        var path = "users/ai_messages"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("ai_messages").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "role" to role,
                    "text" to text.take(30000).ifBlank { "..." },
                    "mode" to mode.take(40),
                    "subjectName" to subjectName.take(100),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAiMessages(messageIds: List<String>): Result<Unit> {
        var path = "users/ai_messages"
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            messageIds.take(100).forEach { id ->
                batch.delete(userDoc(uid).collection("ai_messages").document(id))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- COLLABORATIVE STUDY GROUPS ---
    suspend fun createStudyGroup(
        name: String,
        subjectTag: String,
        description: String,
        dailyGoalMinutes: Long
    ): Result<String> {
        var path = "study_groups"
        return try {
            val uid = requireUserId()
            val groupRef = db.collection("study_groups").document()
            path = groupRef.path
            val inviteCode = generateInviteCode()
            groupRef.set(
                mapOf(
                    "name" to name.trim().take(100).ifBlank { "Study Squad" },
                    "subjectTag" to subjectTag.trim().take(60).ifBlank { "General" },
                    "description" to description.trim().take(500),
                    "inviteCode" to inviteCode,
                    "ownerId" to uid,
                    "memberUids" to listOf(uid),
                    "dailyGoalMinutes" to dailyGoalMinutes.coerceIn(15L, 720L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("group_invites").document(inviteCode).set(
                mapOf(
                    "groupId" to groupRef.id,
                    "groupName" to name.trim().take(100).ifBlank { "Study Squad" },
                    "ownerId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(inviteCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinStudyGroupByCode(rawCode: String): Result<String> {
        val code = rawCode.trim().uppercase(Locale.US)
        var path = "group_invites/$code"
        return try {
            val uid = requireUserId()
            require(code.matches(Regex("^[A-Z0-9\\-]{4,24}$"))) { "Invalid invite code format." }
            val inviteSnap = db.collection("group_invites").document(code).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("No study group found for code $code"))
            }
            val groupId = inviteSnap.getString("groupId")
                ?: return Result.failure(IllegalStateException("Malformed invite record"))
            path = "study_groups/$groupId"
            db.collection("study_groups").document(groupId).update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(inviteSnap.getString("groupName") ?: "Study Group")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendGroupMessage(
        group: StudyGroup,
        senderName: String,
        text: String,
        resourceTitle: String = "",
        resourceSummary: String = ""
    ): Result<Unit> {
        var path = "study_groups/${group.id}/messages"
        return try {
            val uid = requireUserId()
            val msgRef = db.collection("study_groups").document(group.id).collection("messages").document()
            path = msgRef.path
            val payload = mutableMapOf<String, Any>(
                "groupId" to group.id,
                "senderId" to uid,
                "senderName" to senderName.trim().take(100).ifBlank { "Student" },
                "text" to text.trim().take(4000).ifBlank { "Shared a study resource" },
                "memberUids" to group.memberUids.take(50),
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (resourceTitle.isNotBlank()) payload["resourceTitle"] = resourceTitle.take(160)
            if (resourceSummary.isNotBlank()) payload["resourceSummary"] = resourceSummary.take(4000)
            msgRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun computeHabitStreak(dates: List<String>): Long {
        if (dates.isEmpty()) return 0L
        val sorted = dates.distinct().sortedDescending()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = todayDateKey()
        val yesterday = yesterdayDateKey()
        if (sorted.first() != today && sorted.first() != yesterday) return 0L
        var streak = 1L
        var prevTime = sdf.parse(sorted.first())?.time ?: return 1L
        for (i in 1 until sorted.size) {
            val curTime = sdf.parse(sorted[i])?.time ?: break
            val diffDays = ((prevTime - curTime) / 86_400_000L)
            if (diffDays == 1L) {
                streak++
                prevTime = curTime
            } else break
        }
        return streak
    }

    companion object {
        fun todayDateKey(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        fun yesterdayDateKey(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - 86_400_000L))

        fun dateKeyWithOffset(daysOffset: Int): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() + daysOffset * 86_400_000L))
    }
}
