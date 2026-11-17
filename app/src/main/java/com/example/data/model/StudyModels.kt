package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class PriorityLevel(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low");

    companion object {
        fun safeParse(raw: String?): PriorityLevel =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class DifficultyLevel(val label: String) {
    HARD("Hard"),
    MEDIUM("Medium"),
    EASY("Easy");

    companion object {
        fun safeParse(raw: String?): DifficultyLevel =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class ChapterStatus(val label: String) {
    NOT_STARTED("Not Started"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed");

    companion object {
        fun safeParse(raw: String?): ChapterStatus =
            entries.firstOrNull { it.name == raw } ?: NOT_STARTED
    }
}

enum class SessionType(val label: String) {
    STUDY("Study"),
    REVISION("Revision"),
    POMODORO("Pomodoro"),
    PRACTICE("Practice");

    companion object {
        fun safeParse(raw: String?): SessionType =
            entries.firstOrNull { it.name == raw } ?: STUDY
    }
}

enum class DeadlineCategory(val label: String) {
    ASSIGNMENT("Assignment"),
    PROJECT("Project"),
    HOMEWORK("Homework"),
    PRACTICAL("Practical Record"),
    INTERNAL_EXAM("Internal Exam"),
    EXTERNAL_EXAM("External Exam"),
    PERSONAL("Personal Task");

    val isExam: Boolean get() = this == INTERNAL_EXAM || this == EXTERNAL_EXAM

    companion object {
        fun safeParse(raw: String?): DeadlineCategory =
            entries.firstOrNull { it.name == raw } ?: ASSIGNMENT
    }
}

enum class StudySlot(val label: String, val startHour: Int) {
    MORNING("Morning (7 AM)", 7),
    AFTERNOON("Afternoon (2 PM)", 14),
    EVENING("Evening (6 PM)", 18),
    NIGHT("Night (9 PM)", 21);

    companion object {
        fun safeParse(raw: String?): StudySlot =
            entries.firstOrNull { it.name == raw } ?: MORNING
    }
}

enum class DarkModePref(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode");

    companion object {
        fun safeParse(raw: String?): DarkModePref =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class AiLearningMode(val title: String, val tagline: String, val systemInstruction: String) {
    ELI5(
        "Explain Like I'm 5",
        "Ultra-simple analogies & crystal-clear basics",
        "Explain concepts as if teaching a bright beginner. Use simple everyday analogies, short sentences, and avoid dense jargon."
    ),
    EXAM_PREP(
        "Exam Preparation",
        "Scoring points, key definitions & structured answers",
        "Focus on exam-oriented answers: structured bullet points, key definitions, formulas, common examiner traps, and high-yield revision takeaways."
    ),
    DEEP_LEARNING(
        "Deep Learning",
        "Thorough derivations, edge cases & mastery",
        "Provide a rigorous, comprehensive explanation covering underlying intuition, formal reasoning, edge cases, and real-world applications."
    ),
    QUICK_REVISION(
        "Quick Revision",
        "TL;DR cheat-sheets & memory hooks",
        "Give a compact high-density revision sheet with TL;DR bullets, mnemonics, and must-remember formulas."
    ),
    QUIZ_ME(
        "Quiz Me",
        "Active recall questions & feedback",
        "Act as an interactive quiz coach. Ask 3 targeted questions with options or prompts, wait for or evaluate answers, and explain why each answer is right or wrong."
    ),
    HOMEWORK_HELPER(
        "Homework Helper",
        "Step-by-step hints without spoiling",
        "Use the Socratic method. Guide the student step-by-step with hints, leading questions, and partial scaffolding rather than giving away the final answer immediately."
    )
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Student",
    val email: String = "",
    val photoUrl: String? = null,
    val course: String = "",
    val academicYear: String = "",
    val institution: String = "",
    val dailyStudyTargetMinutes: Long = 180L,
    val preferredStudySlot: String = StudySlot.MORNING.name,
    val notificationsEnabled: Boolean = true,
    val darkModePref: String = DarkModePref.SYSTEM.name,
    val currentStreak: Long = 0L,
    val longestStreak: Long = 0L,
    val lastStudyDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Subject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val code: String = "",
    val colorHex: String = "#8C141E",
    val iconName: String = "MenuBook",
    val priority: String = PriorityLevel.MEDIUM.name,
    val difficulty: String = DifficultyLevel.MEDIUM.name,
    val targetHoursPerWeek: Double = 5.0,
    val notes: String = "",
    val examDateMillis: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Chapter(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val title: String = "",
    val topics: List<String> = emptyList(),
    val status: String = ChapterStatus.NOT_STARTED.name,
    val revisionCount: Long = 0L,
    val lastRevisedMillis: Long? = null,
    val nextRevisionMillis: Long? = null,
    val estimatedMinutes: Long = 60L,
    val backlogPriority: String = PriorityLevel.MEDIUM.name,
    val inBacklog: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudySession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val title: String = "",
    val dateKey: String = "",
    val startHour: Long = 9L,
    val startMinute: Long = 0L,
    val durationMinutes: Long = 45L,
    val sessionType: String = SessionType.STUDY.name,
    val completed: Boolean = false,
    val aiGenerated: Boolean = false,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DeadlineItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = DeadlineCategory.ASSIGNMENT.name,
    val dueAtMillis: Long = 0L,
    val priority: String = PriorityLevel.MEDIUM.name,
    val reminderDaysBefore: Long = 1L,
    val completed: Boolean = false,
    val completedAtMillis: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudyNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val title: String = "",
    val content: String = "",
    val aiSummary: String = "",
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val bookmarked: Boolean = false,
    val attachmentName: String = "",
    val attachmentUri: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudyHabit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val targetMinutes: Long = 30L,
    val category: String = "Study",
    val streak: Long = 0L,
    val completedDates: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FlashcardDeck(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val title: String = "",
    val cards: List<Map<String, String>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AiChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "USER",
    val text: String = "",
    val mode: String = AiLearningMode.DEEP_LEARNING.title,
    val subjectName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudyGroup(
    @DocumentId val id: String = "",
    val name: String = "",
    val subjectTag: String = "",
    val description: String = "",
    val inviteCode: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val dailyGoalMinutes: Long = 120L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GroupMessage(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val resourceTitle: String = "",
    val resourceSummary: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class GeneratedQuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class GeneratedScheduleBlock(
    val subjectName: String,
    val title: String,
    val dayOffset: Int,
    val startHour: Int,
    val startMinute: Int,
    val durationMinutes: Int,
    val sessionType: SessionType
)

data class ExtractedSyllabusChapter(
    val title: String,
    val topics: List<String>,
    val estimatedMinutes: Int
)

data class ExtractedSyllabusSubject(
    val subjectName: String,
    val chapters: List<ExtractedSyllabusChapter>
)
