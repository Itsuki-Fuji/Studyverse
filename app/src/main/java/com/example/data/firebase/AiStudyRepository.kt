package com.example.data.firebase

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AiChatMessage
import com.example.data.model.AiLearningMode
import com.example.data.model.Chapter
import com.example.data.model.DeadlineItem
import com.example.data.model.ExtractedSyllabusChapter
import com.example.data.model.ExtractedSyllabusSubject
import com.example.data.model.GeneratedQuizQuestion
import com.example.data.model.GeneratedScheduleBlock
import com.example.data.model.SessionType
import com.example.data.model.Subject
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiStudyRepo"

class AiStudyRepository {

    private val chatModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val scheduleModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "blocks" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "subjectName" to Schema.string(description = "Name of the subject"),
                            "title" to Schema.string(description = "Specific chapter or study task title"),
                            "dayOffset" to Schema.integer(description = "Days from today (0 = today, 1 = tomorrow, up to 6)"),
                            "startHour" to Schema.integer(description = "24-hour start hour between 6 and 22"),
                            "startMinute" to Schema.integer(description = "Start minute (0 or 30)"),
                            "durationMinutes" to Schema.integer(description = "Duration in minutes (25, 45, 50, or 60)"),
                            "sessionType" to Schema.enumeration(listOf("STUDY", "REVISION", "POMODORO", "PRACTICE"))
                        )
                    ),
                    description = "Personalized study blocks including Pomodoro breaks spacing"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val syllabusModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "subjects" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "subjectName" to Schema.string(),
                            "chapters" to Schema.array(
                                items = Schema.obj(
                                    properties = mapOf(
                                        "title" to Schema.string(),
                                        "topics" to Schema.array(items = Schema.string()),
                                        "estimatedMinutes" to Schema.integer()
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val quizModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "questions" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "question" to Schema.string(),
                            "options" to Schema.array(items = Schema.string()),
                            "correctIndex" to Schema.integer(),
                            "explanation" to Schema.string()
                        )
                    )
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val flashcardsModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "deckTitle" to Schema.string(),
                "cards" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "front" to Schema.string(),
                            "back" to Schema.string()
                        )
                    )
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    fun streamTutorResponse(
        prompt: String,
        mode: AiLearningMode,
        subjectContext: String,
        history: List<AiChatMessage>,
        attachedImage: Bitmap? = null,
        attachedDocumentText: String? = null
    ): Flow<String> = flow {
        val systemGuide = buildString {
            appendLine("You are Studyverse AI, an encouraging, intelligent personal academic tutor.")
            appendLine("Learning Mode: ${mode.title} — ${mode.systemInstruction}")
            if (subjectContext.isNotBlank()) {
                appendLine("Student's active subject context: $subjectContext")
            }
            appendLine("Keep formatting clean with clear headings, bullet points, and step-by-step clarity.")
        }

        val chatHistory = history.takeLast(8).map { msg ->
            content(if (msg.role == "MODEL") "model" else "user") {
                text(msg.text)
            }
        }

        val fullPrompt = buildString {
            appendLine(systemGuide)
            if (!attachedDocumentText.isNullOrBlank()) {
                appendLine("\n[Attached Document Content]:\n${attachedDocumentText.take(12000)}\n")
            }
            appendLine("Student Request: $prompt")
        }

        val inputContent = content("user") {
            if (attachedImage != null) {
                image(attachedImage)
            }
            text(fullPrompt)
        }

        val chat = chatModel.startChat(history = chatHistory)
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(inputContent).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("AI tutor returned an empty response.")
        }
    }

    suspend fun generateSmartSchedule(
        subjects: List<Subject>,
        chapters: List<Chapter>,
        deadlines: List<DeadlineItem>,
        dailyHours: Double,
        preferredStartHour: Int,
        horizonDays: Int
    ): Result<List<GeneratedScheduleBlock>> {
        return try {
            val subjectSummary = subjects.joinToString("\n") { sub ->
                val pendingChaps = chapters.filter { it.subjectId == sub.id && it.status != "COMPLETED" }
                "- ${sub.name} (priority=${sub.priority}, difficulty=${sub.difficulty}, targetHrs/wk=${sub.targetHoursPerWeek}, pendingChapters=${pendingChaps.take(4).joinToString { it.title }})"
            }
            val deadlineSummary = deadlines.filter { !it.completed }.take(8).joinToString("\n") { dl ->
                val daysLeft = ((dl.dueAtMillis - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0L)
                "- ${dl.title} (${dl.subjectName}, category=${dl.category}, priority=${dl.priority}, dueInDays=$daysLeft)"
            }
            val prompt = """
                Create an optimal ${horizonDays}-day student study schedule starting today (dayOffset 0..${(horizonDays - 1).coerceIn(0, 6)}).
                Available study hours per day: $dailyHours hours.
                Preferred starting hour: $preferredStartHour:00.
                Use Pomodoro-friendly blocks (25, 45, or 50 minutes) with natural breaks between sessions.
                Prioritize high-priority subjects, urgent deadlines, and incomplete chapters.
                
                Subjects:
                ${subjectSummary.ifBlank { "General Study" }}
                
                Upcoming Deadlines:
                ${deadlineSummary.ifBlank { "None" }}
            """.trimIndent()

            val response = withTimeout(60_000L) {
                scheduleModel.generateContent(prompt)
            }
            val json = JSONObject(response.text ?: throw IllegalStateException("Empty AI response"))
            val arr = json.getJSONArray("blocks")
            val result = mutableListOf<GeneratedScheduleBlock>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    GeneratedScheduleBlock(
                        subjectName = obj.optString("subjectName", "General"),
                        title = obj.optString("title", "Study Focus"),
                        dayOffset = obj.optInt("dayOffset", 0).coerceIn(0, 6),
                        startHour = obj.optInt("startHour", preferredStartHour).coerceIn(6, 22),
                        startMinute = if (obj.optInt("startMinute", 0) >= 30) 30 else 0,
                        durationMinutes = obj.optInt("durationMinutes", 45).coerceIn(15, 120),
                        sessionType = SessionType.safeParse(obj.optString("sessionType", "STUDY"))
                    )
                )
            }
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Schedule generation timed out", e)
            Result.failure(Exception("Schedule generation timed out after 60s. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Schedule generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun extractSyllabus(
        rawText: String,
        image: Bitmap? = null
    ): Result<List<ExtractedSyllabusSubject>> {
        return try {
            val prompt = """
                Extract a structured academic syllabus from the provided material.
                Identify each subject, its chapters/units, key topics under each chapter (3-6 topics), and realistic study time in minutes.
                
                Material:
                ${rawText.take(16000)}
            """.trimIndent()

            val input = content {
                if (image != null) image(image)
                text(prompt)
            }
            val response = withTimeout(60_000L) {
                syllabusModel.generateContent(input)
            }
            val json = JSONObject(response.text ?: throw IllegalStateException("Empty syllabus response"))
            val subsArr = json.getJSONArray("subjects")
            val subjects = mutableListOf<ExtractedSyllabusSubject>()
            for (i in 0 until subsArr.length()) {
                val sObj = subsArr.getJSONObject(i)
                val chArr = sObj.getJSONArray("chapters")
                val chapters = mutableListOf<ExtractedSyllabusChapter>()
                for (j in 0 until chArr.length()) {
                    val cObj = chArr.getJSONObject(j)
                    val topicsArr = cObj.optJSONArray("topics")
                    val topics = mutableListOf<String>()
                    if (topicsArr != null) {
                        for (k in 0 until topicsArr.length()) {
                            topics.add(topicsArr.getString(k))
                        }
                    }
                    chapters.add(
                        ExtractedSyllabusChapter(
                            title = cObj.optString("title", "Unit ${j + 1}"),
                            topics = topics,
                            estimatedMinutes = cObj.optInt("estimatedMinutes", 60).coerceIn(15, 360)
                        )
                    )
                }
                subjects.add(
                    ExtractedSyllabusSubject(
                        subjectName = sObj.optString("subjectName", "Imported Subject"),
                        chapters = chapters
                    )
                )
            }
            Result.success(subjects)
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Syllabus extraction timed out. Please try a shorter section."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Syllabus extraction failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateQuiz(topicOrContent: String, count: Int = 5): Result<List<GeneratedQuizQuestion>> {
        return try {
            val prompt = "Create $count multiple-choice practice quiz questions (4 options each) with clear explanations for:\n${topicOrContent.take(12000)}"
            val response = withTimeout(60_000L) {
                quizModel.generateContent(prompt)
            }
            val json = JSONObject(response.text ?: throw IllegalStateException("Empty quiz response"))
            val arr = json.getJSONArray("questions")
            val list = mutableListOf<GeneratedQuizQuestion>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val optsArr = obj.getJSONArray("options")
                val opts = (0 until optsArr.length()).map { optsArr.getString(it) }
                list.add(
                    GeneratedQuizQuestion(
                        question = obj.getString("question"),
                        options = opts,
                        correctIndex = obj.optInt("correctIndex", 0).coerceIn(0, (opts.size - 1).coerceAtLeast(0)),
                        explanation = obj.optString("explanation", "")
                    )
                )
            }
            Result.success(list)
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Quiz generation timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Quiz generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateFlashcards(titleHint: String, sourceText: String): Result<Pair<String, List<Pair<String, String>>>> {
        return try {
            val prompt = "Create 6-10 concise, high-yield study flashcards (question/concept on front, clear answer/formula on back) for '$titleHint':\n${sourceText.take(12000)}"
            val response = withTimeout(60_000L) {
                flashcardsModel.generateContent(prompt)
            }
            val json = JSONObject(response.text ?: throw IllegalStateException("Empty flashcards response"))
            val deckTitle = json.optString("deckTitle", titleHint).ifBlank { titleHint }
            val arr = json.getJSONArray("cards")
            val cards = mutableListOf<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                cards.add(obj.getString("front") to obj.getString("back"))
            }
            Result.success(deckTitle to cards)
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Flashcard generation timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Flashcard generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun summarizeOrGenerateGuide(instruction: String): Result<String> {
        return try {
            val response = withTimeout(60_000L) {
                chatModel.generateContent(instruction.take(15000))
            }
            val text = response.text
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("AI returned an empty summary."))
            } else {
                Result.success(text)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("AI summary timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI summary failed", e)
            Result.failure(e)
        }
    }
}
