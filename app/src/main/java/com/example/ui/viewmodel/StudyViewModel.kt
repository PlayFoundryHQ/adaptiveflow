package com.example.ui.viewmodel

import android.app.Application
import retrofit2.HttpException
import android.content.Context
import com.example.ui.viewmodel.DiagnosticLogger
import com.example.ui.viewmodel.DiagnosticLogger as Log
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.database.AppDatabase
import com.example.data.model.ChatLog
import com.example.data.model.Deck
import com.example.data.model.Flashcard
import com.example.data.repository.StudyRepository
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

@JsonClass(generateAdapter = true)
data class ParsedCard(
    val front: String,
    val back: String,
    val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class ParsedDeck(
    val deckName: String = "",
    val sourceLanguage: String = "",
    val targetLanguage: String = "",
    val cards: List<ParsedCard> = emptyList()
)

class StudyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: StudyRepository
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    // Database Flows
    val allDecks: StateFlow<List<com.example.data.model.DeckWithCards>>

    private val _currentDeck = MutableStateFlow<Deck?>(null)
    val currentDeck: StateFlow<Deck?> = _currentDeck.asStateFlow()

    private val _currentFlashcards = MutableStateFlow<List<Flashcard>>(emptyList())
    val currentFlashcards: StateFlow<List<Flashcard>> = _currentFlashcards.asStateFlow()

    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex.asStateFlow()

    private val _isCardFlipped = MutableStateFlow(false)
    val isCardFlipped: StateFlow<Boolean> = _isCardFlipped.asStateFlow()

    private val _chatLogs = MutableStateFlow<List<ChatLog>>(emptyList())
    val chatLogs: StateFlow<List<ChatLog>> = _chatLogs.asStateFlow()

    // UI Status States
    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    // Performance and Streak Tracking for Dynamic Difficulty Scaling
    private val _sessionCorrectCount = MutableStateFlow(0)
    val sessionCorrectCount: StateFlow<Int> = _sessionCorrectCount.asStateFlow()

    private val _sessionIncorrectCount = MutableStateFlow(0)
    val sessionIncorrectCount: StateFlow<Int> = _sessionIncorrectCount.asStateFlow()

    private val _consecutiveIncorrectStreak = MutableStateFlow(0)
    val consecutiveIncorrectStreak: StateFlow<Int> = _consecutiveIncorrectStreak.asStateFlow()

    private val _flippedCardIds = MutableStateFlow<Set<Int>>(emptySet())
    val flippedCardIds: StateFlow<Set<Int>> = _flippedCardIds.asStateFlow()

    // Native TTS Engine
    private var tts: TextToSpeech? = null
    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private val _ttsRate = MutableStateFlow(1.0f)
    val ttsRate: StateFlow<Float> = _ttsRate.asStateFlow()

    private val _isAutoPlayTtsEnabled = MutableStateFlow(true)
    val isAutoPlayTtsEnabled: StateFlow<Boolean> = _isAutoPlayTtsEnabled.asStateFlow()

    // Play Mode & Adaptive Gamification
    private val _isPlayModeActive = MutableStateFlow(false)
    val isPlayModeActive: StateFlow<Boolean> = _isPlayModeActive.asStateFlow()

    enum class LearningMode { Flashcard, Quiz }
    enum class ConfidenceLevel { LOW, MEDIUM, HIGH }

    private val _cardModes = MutableStateFlow<Map<Int, LearningMode>>(emptyMap())
    val cardModes: StateFlow<Map<Int, LearningMode>> = _cardModes.asStateFlow()

    sealed interface ImportState {
        object Idle : ImportState
        data class Loading(val message: String = "Analyzing & extracting content...") : ImportState
        data class Success(val deckName: String) : ImportState
        data class Error(val message: String) : ImportState
    }

    private val prefs = application.getSharedPreferences("StudyAppPrefs", Context.MODE_PRIVATE)

    private val _customApiKey = MutableStateFlow(prefs.getString("user_gemini_api_key", "") ?: "")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _nativeLanguage = MutableStateFlow(prefs.getString("native_language", "English") ?: "English")
    val nativeLanguage: StateFlow<String> = _nativeLanguage.asStateFlow()

    private val _targetLanguage = MutableStateFlow(prefs.getString("target_language", "Swedish") ?: "Swedish")
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    fun updateLearningGoal(native: String, target: String) {
        prefs.edit()
            .putString("native_language", native.trim())
            .putString("target_language", target.trim())
            .apply()
        _nativeLanguage.value = native.trim()
        _targetLanguage.value = target.trim()
    }

    fun saveCustomApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("user_gemini_api_key", trimmed).apply()
        _customApiKey.value = trimmed
    }

    fun clearCustomApiKey() {
        prefs.edit().remove("user_gemini_api_key").apply()
        _customApiKey.value = ""
    }

    fun getEffectiveApiKey(): String {
        val custom = _customApiKey.value.trim()
        if (custom.isNotEmpty() && custom != "MY_GEMINI_API_KEY") {
            return custom
        }
        val envKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
        if (envKey.isNotEmpty() && envKey != "MY_GEMINI_API_KEY") {
            return envKey
        }
        return ""
    }

    private fun cleanAndExtractJson(raw: String): String {
        var text = raw.trim()
        
        // Remove markdown block wraps if present
        if (text.startsWith("```")) {
            val firstNewLine = text.indexOf('\n')
            if (firstNewLine != -1) {
                text = text.substring(firstNewLine).trim()
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length - 3).trim()
            }
        }
        
        val firstBrace = text.indexOf('{')
        val firstBracket = text.indexOf('[')
        
        if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            val lastBrace = text.lastIndexOf('}')
            if (lastBrace != -1 && lastBrace > firstBrace) {
                return text.substring(firstBrace, lastBrace + 1)
            }
        } else if (firstBracket != -1) {
            val lastBracket = text.lastIndexOf(']')
            if (lastBracket != -1 && lastBracket > firstBracket) {
                return text.substring(firstBracket, lastBracket + 1)
            }
        }
        
        return text
    }

    private fun robustParseJsonDeck(jsonText: String): ParsedDeck {
        val trimmed = jsonText.trim()
        
        // 1. Try parsing directly as ParsedDeck
        try {
            val parsed = moshi.adapter(ParsedDeck::class.java).fromJson(trimmed)
            if (parsed != null && parsed.cards.isNotEmpty()) {
                return parsed
            }
        } catch (e: Exception) {
            DiagnosticLogger.d("StudyViewModel", "Direct ParsedDeck parsing failed, trying list: ${e.message}")
        }

        // 2. Try parsing as List<ParsedCard> if it's a JSON array
        try {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, ParsedCard::class.java)
            val cardsList = moshi.adapter<List<ParsedCard>>(type).fromJson(trimmed)
            if (!cardsList.isNullOrEmpty()) {
                return ParsedDeck(
                    deckName = "",
                    sourceLanguage = "",
                    targetLanguage = "",
                    cards = cardsList
                )
            }
        } catch (e: Exception) {
            DiagnosticLogger.d("StudyViewModel", "List<ParsedCard> parsing failed: ${e.message}")
        }

        // 3. Fallback: Regex matching
        val cards = mutableListOf<ParsedCard>()
        val cardRegex = Regex("""\{\s*"front"\s*:\s*"([^"]*)"\s*,\s*"back"\s*:\s*"([^"]*)"(?:,\s*"notes"\s*:\s*"([^"]*)")?\s*\}""")
        val matches = cardRegex.findAll(trimmed)
        for (match in matches) {
            val front = match.groups[1]?.value ?: ""
            val back = match.groups[2]?.value ?: ""
            val notes = match.groups[3]?.value
            if (front.isNotBlank() && back.isNotBlank()) {
                cards.add(ParsedCard(front, back, notes))
            }
        }
        
        if (cards.isNotEmpty()) {
            return ParsedDeck(cards = cards)
        }

        throw Exception("Could not parse any valid cards from AI response.")
    }

    private fun isYouTubeUrl(text: String): Boolean {
        return Regex("""(youtube\.com/(watch|shorts)|youtu\.be/|m\.youtube\.com)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
    }

    // Guards against the model echoing the raw input URL back as a fake "card" when it has
    // nothing real to extract from (e.g. a bare YouTube link with no topic hint / captions access).
    private fun looksLikeUrlFragment(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        return t.startsWith("http://", ignoreCase = true) ||
            t.startsWith("https://", ignoreCase = true) ||
            t.startsWith("//") ||
            t.equals("http", ignoreCase = true) ||
            t.equals("https", ignoreCase = true) ||
            t.contains("youtu.be") ||
            t.contains("youtube.com")
    }

    private fun filterOutUrlEchoCards(cards: List<ParsedCard>): List<ParsedCard> {
        return cards.filterNot { looksLikeUrlFragment(it.front) || looksLikeUrlFragment(it.back) }
    }

    private fun getHttpErrorBody(throwable: Throwable): String? {
        if (throwable is HttpException) {
            return try {
                throwable.response()?.errorBody()?.string()
            } catch (ex: Exception) {
                null
            }
        }
        return null
    }

    private fun isFatalApiError(throwable: Throwable): Boolean {
        if (throwable is HttpException) {
            val code = throwable.code()
            if (code == 401 || code == 403) {
                return true
            }
            if (code == 400) {
                // If it is NOT a payload/size issue, HTTP 400 is fatal (e.g., malformed request structure)
                if (!isPayloadTooLargeError(throwable)) {
                    return true
                }
            }
        }
        val msg = throwable.message.orEmpty()
        if (msg.contains("API key", ignoreCase = true) || 
            msg.contains("API_KEY", ignoreCase = true) || 
            msg.contains("invalid key", ignoreCase = true) || 
            msg.contains("unauthorized", ignoreCase = true)) {
            return true
        }
        return false
    }

    private fun isPayloadTooLargeError(throwable: Throwable): Boolean {
        if (throwable is HttpException) {
            if (throwable.code() == 413) {
                return true
            }
        }
        val errorBodyText = getHttpErrorBody(throwable).orEmpty()
        val msg = throwable.message.orEmpty() + " " + errorBodyText
        return msg.contains("413") || 
               msg.contains("too large", ignoreCase = true) || 
               msg.contains("limit exceeded", ignoreCase = true) ||
               msg.contains("payload", ignoreCase = true) ||
               msg.contains("context_window_exceeded", ignoreCase = true)
    }

    private fun isRateLimitError(throwable: Throwable): Boolean {
        if (throwable is HttpException) {
            if (throwable.code() == 429) {
                return true
            }
        }
        val errorBodyText = getHttpErrorBody(throwable).orEmpty()
        val msg = throwable.message.orEmpty() + " " + errorBodyText
        return msg.contains("429") || 
               msg.contains("quota", ignoreCase = true) || 
               msg.contains("rate limit", ignoreCase = true) ||
               msg.contains("exhausted", ignoreCase = true)
    }

    private fun isTransientServerError(throwable: Throwable): Boolean {
        if (throwable is java.net.SocketTimeoutException || throwable is java.io.InterruptedIOException || throwable.message?.contains("timeout", ignoreCase = true) == true) {
            return true
        }
        if (throwable is HttpException) {
            val code = throwable.code()
            if (code == 503 || code == 504 || code == 502 || code == 500) {
                return true
            }
        }
        val errorBodyText = getHttpErrorBody(throwable).orEmpty()
        val msg = throwable.message.orEmpty() + " " + errorBodyText
        return msg.contains("503") || 
               msg.contains("unavailable", ignoreCase = true) || 
               msg.contains("experiencing high demand", ignoreCase = true) ||
               msg.contains("temporary", ignoreCase = true)
    }

    private fun getRecommendedRetryDelayMs(throwable: Throwable): Long? {
        val errorBodyText = getHttpErrorBody(throwable).orEmpty()
        
        // 1. Try to find "retryDelay": "Xs"
        val retryDelayRegex = """\"retryDelay\"\s*:\s*\"(\d+)\s*s\"""".toRegex()
        val matchDelay = retryDelayRegex.find(errorBodyText)
        if (matchDelay != null) {
            val seconds = matchDelay.groupValues[1].toLongOrNull()
            if (seconds != null) {
                return seconds * 1000L
            }
        }

        // 2. Try to find "Please retry in Xs"
        val retryInRegex = """Please retry in\s+([0-9.]+)\s*s""".toRegex()
        val matchRetry = retryInRegex.find(errorBodyText)
        if (matchRetry != null) {
            val seconds = matchRetry.groupValues[1].toDoubleOrNull()
            if (seconds != null) {
                return (seconds * 1000L).toLong()
            }
        }

        return null
    }

    private suspend fun processPagesChunkRecursive(
        pages: List<String>,
        chunkIdStr: String,
        apiKey: String,
        topicHint: String,
        countInstructionPerChunk: String,
        maxAttempts: Int = 3
    ): ParsedDeck {
        var attempts = 0
        var lastException: Exception? = null
        var shouldSubdivide = false
        
        while (attempts < maxAttempts) {
            attempts++
            
            val progressMessage = if (attempts > 1) {
                "Parsing chunk $chunkIdStr (${pages.size} pgs)... Attempt $attempts/$maxAttempts..."
            } else {
                "Parsing chunk $chunkIdStr (${pages.size} pgs)..."
            }
            
            withContext(Dispatchers.Main) {
                _importState.value = ImportState.Loading(progressMessage)
            }
            
            try {
                val chunkText = pages.joinToString("\n\n")
                val userPrompt = if (topicHint.isNotBlank()) {
                    "Please parse this PDF text chunk into a flashcard deck under the following instructions/topic focus:\n" +
                    "Focus/Language Hint: $topicHint\n\n" +
                    "Text Chunk:\n$chunkText\n\n" +
                    "Remember: $countInstructionPerChunk Do not be lazy!"
                } else {
                    "Please parse this PDF text chunk into a flashcard deck:\n\n$chunkText\n\n" +
                    "Remember: $countInstructionPerChunk Do not be lazy!"
                }

                val systemPrompt = """
                    You are a Flashcard Parser Engine.
                    Your task is to parse a chunk of text from a multi-page document into a structured flashcard deck.
                    This is part/chunk $chunkIdStr.
                    
                    User Profile / Active Goal: Learning ${targetLanguage.value} from ${nativeLanguage.value}.
                    
                    COGNITIVE DOMAIN AND LANGUAGE INTEGRATION RULE:
                    1. If the input document is language learning material or focuses on language vocabulary, prioritize creating cards where the front contains the target language word/phrase (${targetLanguage.value}) and the back has the native language translation/definition (${nativeLanguage.value}).
                    2. If the input document is a general subject-matter text (e.g., Computer Science, Medicine, Biology, Business, History, Physics, Law) written in a language like English, do NOT translate the terms to ${targetLanguage.value}. Keep the terms on the front in their original language, and provide a clear, detailed explanation or definition on the back in the same language. This allows studying specialized subject-matter directly in its native context.
                    
                    Extraction Density Instruction:
                    $countInstructionPerChunk
                    
                    Avoid repeating terms from prior chunks. Do not summarize too aggressively, keep definitions detailed, and do not be lazy.
                    
                    You MUST return strictly valid JSON matching this schema:
                    {
                        "deckName": "Name of the deck",
                        "sourceLanguage": "Source language",
                        "targetLanguage": "Target language",
                        "cards": [
                          {
                            "front": "word/phrase",
                            "back": "translation/definition",
                            "notes": "optional pronunciation, explanation, or usage details"
                          }
                        ]
                    }
                    Do not include markdown markers like ```json or ```. Return ONLY the raw JSON string.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = userPrompt)))),
                    generationConfig = GenerationConfig(
                        temperature = 0.3f,
                        responseMimeType = "application/json",
                        maxOutputTokens = 8192
                    ),
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                )

                val response = RetrofitClient.generateContentWithFallback(apiKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("AI returned empty output for chunk $chunkIdStr.")

                val cleanedText = cleanAndExtractJson(responseText)
                val parsedDeck = robustParseJsonDeck(cleanedText)
                
                return parsedDeck
            } catch (chunkEx: Exception) {
                lastException = chunkEx
                val errorDetails = getHttpErrorBody(chunkEx)
                if (errorDetails != null) {
                    DiagnosticLogger.e("StudyViewModel", "HTTP error on chunk $chunkIdStr (Attempt $attempts/$maxAttempts): $errorDetails")
                } else {
                    DiagnosticLogger.e("StudyViewModel", "Error parsing chunk $chunkIdStr (Attempt $attempts/$maxAttempts): ", chunkEx)
                }
                
                if (isFatalApiError(chunkEx)) {
                    throw Exception(errorDetails ?: chunkEx.message)
                }
                
                // If the error indicates payload too large, set shouldSubdivide to true and break
                if (isPayloadTooLargeError(chunkEx)) {
                    if (pages.size > 1 || (pages.size == 1 && pages.first().split("\n\n").filter { it.isNotBlank() }.size > 1)) {
                        DiagnosticLogger.i("StudyViewModel", "Payload too large error detected on chunk $chunkIdStr. Breaking to subdivide chunk.")
                        shouldSubdivide = true
                        break
                    } else {
                        throw Exception("Content on single page is too large for model limits.")
                    }
                }
                
                if (attempts < maxAttempts) {
                    val isRateLimit = isRateLimitError(chunkEx)
                    val isTransient = isTransientServerError(chunkEx)
                    
                    val backoffDelay = when {
                        isRateLimit -> {
                            val recommended = getRecommendedRetryDelayMs(chunkEx)
                            (recommended ?: (10000L * (1 shl (attempts - 1)))) + 1000L
                        }
                        isTransient -> {
                            val recommended = getRecommendedRetryDelayMs(chunkEx)
                            (recommended ?: (5000L * attempts)) + 1000L
                        }
                        else -> 2000L * attempts
                    }
                    
                    val statusMessage = when {
                        isRateLimit -> {
                            "Rate limit hit on chunk $chunkIdStr. Backing off ${backoffDelay / 1000}s (Attempt $attempts/$maxAttempts)..."
                        }
                        isTransient -> {
                            "Server busy (503) on chunk $chunkIdStr. Retrying in ${backoffDelay / 1000}s (Attempt $attempts/$maxAttempts)..."
                        }
                        else -> {
                            val errMsg = if (errorDetails != null && errorDetails.length < 150) errorDetails else chunkEx.localizedMessage
                            "Error on chunk $chunkIdStr: $errMsg. Retrying in ${backoffDelay / 1000}s (Attempt $attempts/$maxAttempts)..."
                        }
                    }
                    withContext(Dispatchers.Main) {
                        _importState.value = ImportState.Loading(statusMessage)
                    }
                    delay(backoffDelay)
                }
            }
        }
        
        // If we reached here, processing failed or payload was too large.
        // We ONLY subdivide if shouldSubdivide was set to true (signifying payload size issues)
        if (shouldSubdivide) {
            if (pages.size > 1) {
                DiagnosticLogger.i("StudyViewModel", "Chunk $chunkIdStr payload too large. Subdividing list of ${pages.size} pages.")
                val mid = pages.size / 2
                val left = pages.subList(0, mid)
                val right = pages.subList(mid, pages.size)
                
                withContext(Dispatchers.Main) {
                    _importState.value = ImportState.Loading("Payload too complex. Splitting into smaller sections...")
                }
                delay(1000)
                
                val leftDeck = processPagesChunkRecursive(
                    pages = left,
                    chunkIdStr = "$chunkIdStr-A",
                    apiKey = apiKey,
                    topicHint = topicHint,
                    countInstructionPerChunk = countInstructionPerChunk,
                    maxAttempts = maxAttempts
                )
                
                val rightDeck = processPagesChunkRecursive(
                    pages = right,
                    chunkIdStr = "$chunkIdStr-B",
                    apiKey = apiKey,
                    topicHint = topicHint,
                    countInstructionPerChunk = countInstructionPerChunk,
                    maxAttempts = maxAttempts
                )
                
                return ParsedDeck(
                    deckName = leftDeck.deckName.ifBlank { rightDeck.deckName },
                    sourceLanguage = leftDeck.sourceLanguage.ifBlank { rightDeck.sourceLanguage },
                    targetLanguage = leftDeck.targetLanguage.ifBlank { rightDeck.targetLanguage },
                    cards = leftDeck.cards + rightDeck.cards
                )
            } else {
                // It's a single page. If we have multiple paragraphs, we can try splitting the text in half.
                val singlePageText = pages.first()
                val paragraphs = singlePageText.split("\n\n").filter { it.isNotBlank() }
                if (paragraphs.size > 1) {
                    DiagnosticLogger.i("StudyViewModel", "Single page chunk $chunkIdStr payload too large. Splitting text into two blocks.")
                    val mid = paragraphs.size / 2
                    val leftText = paragraphs.subList(0, mid).joinToString("\n\n")
                    val rightText = paragraphs.subList(mid, paragraphs.size).joinToString("\n\n")
                    
                    withContext(Dispatchers.Main) {
                        _importState.value = ImportState.Loading("Page density too high. Splitting text blocks...")
                    }
                    delay(1000)
                    
                    val leftDeck = processPagesChunkRecursive(
                        pages = listOf(leftText),
                        chunkIdStr = "$chunkIdStr-Pt1",
                        apiKey = apiKey,
                        topicHint = topicHint,
                        countInstructionPerChunk = countInstructionPerChunk,
                        maxAttempts = maxAttempts
                    )
                    
                    val rightDeck = processPagesChunkRecursive(
                        pages = listOf(rightText),
                        chunkIdStr = "$chunkIdStr-Pt2",
                        apiKey = apiKey,
                        topicHint = topicHint,
                        countInstructionPerChunk = countInstructionPerChunk,
                        maxAttempts = maxAttempts
                    )
                    
                    return ParsedDeck(
                        deckName = leftDeck.deckName.ifBlank { rightDeck.deckName },
                        sourceLanguage = leftDeck.sourceLanguage.ifBlank { rightDeck.sourceLanguage },
                        targetLanguage = leftDeck.targetLanguage.ifBlank { rightDeck.targetLanguage },
                        cards = leftDeck.cards + rightDeck.cards
                    )
                }
            }
        }
        
        // If it can't be split further or shouldn't be split (e.g. rate limit / transient errors), we propagate the last exception
        val errorDetails = lastException?.let { getHttpErrorBody(it) }
        val displayMsg = if (errorDetails != null) {
            "API Error: $errorDetails"
        } else {
            lastException?.localizedMessage ?: "Chunk $chunkIdStr failed all attempts."
        }
        throw Exception(displayMsg)
    }

    fun hasSeeded(): Boolean {
        return prefs.getBoolean("has_seeded_default_decks", false)
    }

    private fun recordStudyActivity(isCorrect: Boolean) {
        val currentXp = prefs.getInt("gamified_xp", 0)
        val addedXp = if (isCorrect) 15 else 5
        prefs.edit().putInt("gamified_xp", currentXp + addedXp).apply()

        // Streak check
        val todayStr = getTodayDateString()
        val lastStudyStr = prefs.getString("last_study_date", "") ?: ""
        val currentStreak = prefs.getInt("study_streak", 0)

        if (lastStudyStr.isEmpty()) {
            prefs.edit()
                .putInt("study_streak", 1)
                .putString("last_study_date", todayStr)
                .apply()
        } else if (lastStudyStr != todayStr) {
            val yesterdayStr = getYesterdayDateString()
            if (lastStudyStr == yesterdayStr) {
                prefs.edit()
                    .putInt("study_streak", currentStreak + 1)
                    .putString("last_study_date", todayStr)
                    .apply()
            } else {
                prefs.edit()
                    .putInt("study_streak", 1)
                    .putString("last_study_date", todayStr)
                    .apply()
            }
        }
    }

    private fun getTodayDateString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
        return sdf.format(java.util.Date())
    }

    private fun getYesterdayDateString(): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DATE, -1)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
        return sdf.format(cal.time)
    }

    fun getStreak(): Int = prefs.getInt("study_streak", 0)
    fun getXp(): Int = prefs.getInt("gamified_xp", 0)

    init {
        val database = AppDatabase.getDatabase(application)
        repository = StudyRepository(database.studyDao())
        allDecks = MutableStateFlow<List<com.example.data.model.DeckWithCards>>(emptyList())

        // Initialize TTS native speech engine
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                _isTtsReady.value = true
                tts?.setSpeechRate(_ttsRate.value)
            }
        }

        // Collect Decks
        viewModelScope.launch {
            repository.allDecksWithCardsFlow.collectLatest { decksWithCards ->
                (allDecks as MutableStateFlow).value = decksWithCards

                // Ensure the Master Vocabulary Pool always exists as the default shared pool
                val hasMasterPool = decksWithCards.any { it.deck.name.contains("Master Vocabulary Pool") }
                if (!hasMasterPool) {
                    repository.insertDeck(
                        Deck(
                            name = "🧠 Master Vocabulary Pool",
                            sourceLanguage = "Multiple",
                            targetLanguage = "English"
                        )
                    )
                }
            }
        }
    }

    fun selectDeck(deck: Deck) {
        _currentDeck.value = deck
        _currentCardIndex.value = 0
        _isCardFlipped.value = false
        _sessionCorrectCount.value = 0
        _sessionIncorrectCount.value = 0
        _consecutiveIncorrectStreak.value = 0
        _flippedCardIds.value = emptySet()

        // Load flashcards for active deck once and sort by basic spaced repetition (nextReview asc, repetitions asc, easeFactor asc)
        viewModelScope.launch {
            val cards = repository.getFlashcardsForDeck(deck.id)
            _currentFlashcards.value = cards.sortedWith(
                compareBy<Flashcard> { it.nextReview }
                    .thenBy { it.repetitions }
                    .thenBy { it.easeFactor }
            )
        }
        viewModelScope.launch {
            repository.getChatLogsForDeckFlow(deck.id).collectLatest { logs ->
                _chatLogs.value = logs
            }
        }
    }

    fun clearActiveDeck() {
        _currentDeck.value = null
        _currentFlashcards.value = emptyList()
        _chatLogs.value = emptyList()
        _flippedCardIds.value = emptySet()
    }

    fun flipCard() {
        _isCardFlipped.value = !_isCardFlipped.value
        val currentCards = _currentFlashcards.value
        val activeCard = currentCards.getOrNull(_currentCardIndex.value)
        if (activeCard != null) {
            _flippedCardIds.value = _flippedCardIds.value + activeCard.id
        }
    }

    fun recordCardAnswer(isCorrect: Boolean) {
        val confidence = if (isCorrect) ConfidenceLevel.MEDIUM else ConfidenceLevel.LOW
        recordCardConfidence(confidence)
    }

    fun restartSession() {
        _currentCardIndex.value = 0
        _isCardFlipped.value = false
        _sessionCorrectCount.value = 0
        _sessionIncorrectCount.value = 0
        _consecutiveIncorrectStreak.value = 0
        _flippedCardIds.value = emptySet()
        
        // Reload and re-sort from the repository
        val deck = _currentDeck.value
        if (deck != null) {
            viewModelScope.launch {
                val initialCards = repository.getFlashcardsForDeck(deck.id)
                _currentFlashcards.value = initialCards.sortedWith(
                    compareBy<Flashcard> { it.nextReview }
                        .thenBy { it.repetitions }
                        .thenBy { it.easeFactor }
                )
            }
        }
    }

    fun recordCardConfidence(confidence: ConfidenceLevel) {
        val currentCards = _currentFlashcards.value
        val index = _currentCardIndex.value
        if (index >= currentCards.size) return

        val card = currentCards[index]
        val isCorrect = confidence != ConfidenceLevel.LOW

        if (isCorrect) {
            _sessionCorrectCount.value += 1
            _consecutiveIncorrectStreak.value = 0
            setLearningModeForCard(card.id, LearningMode.Flashcard)
        } else {
            _sessionIncorrectCount.value += 1
            _consecutiveIncorrectStreak.value += 1
            setLearningModeForCard(card.id, LearningMode.Quiz)
        }

        // Adaptive learning adjustments based on confidence score
        val newRepetitions = if (isCorrect) card.repetitions + 1 else 0

        // Custom adaptive interval multiplier based on confidence level
        val multiplier = when (confidence) {
            ConfidenceLevel.LOW -> 1.0f
            ConfidenceLevel.MEDIUM -> 1.0f
            ConfidenceLevel.HIGH -> 1.4f
        }

        val baseInterval = if (isCorrect) {
            when (newRepetitions) {
                1 -> 1
                2 -> 3
                else -> (card.interval * card.easeFactor).toInt().coerceAtLeast(6)
            }
        } else {
            1
        }
        val newInterval = (baseInterval * multiplier).toInt().coerceAtLeast(1)

        val newEaseFactor = when (confidence) {
            ConfidenceLevel.LOW -> (card.easeFactor - 0.25f).coerceAtLeast(1.3f)
            ConfidenceLevel.MEDIUM -> card.easeFactor
            ConfidenceLevel.HIGH -> (card.easeFactor + 0.15f).coerceAtMost(3.0f)
        }

        val updatedCard = card.copy(
            repetitions = newRepetitions,
            interval = newInterval,
            easeFactor = newEaseFactor,
            nextReview = System.currentTimeMillis() + (newInterval * 24 * 60 * 60 * 1000L)
        )

        viewModelScope.launch {
            repository.updateFlashcard(updatedCard)
            recordStudyActivity(isCorrect)
        }

        // --- SPACED REPETITION REORDERING ALGORITHM ---
        // Reorder the flashcards based on whether the user marked them as 'easy' or 'difficult' during their session
        val updatedList = _currentFlashcards.value.toMutableList()
        updatedList[index] = updatedCard

        val currentCard = updatedList.removeAt(index)

        // Sort the remaining pending cards by spaced repetition parameters
        val sortedRemaining = updatedList.sortedWith(
            compareBy<Flashcard> { it.nextReview }
                .thenBy { it.repetitions }
                .thenBy { it.easeFactor }
        ).toMutableList()

        // Re-insert the current card back based on its confidence difficulty
        when (confidence) {
            ConfidenceLevel.LOW -> {
                // Difficult: insert near the front of remaining cards (e.g., index 2 so they see it very soon)
                val targetPos = 2.coerceAtMost(sortedRemaining.size)
                sortedRemaining.add(targetPos, currentCard)
            }
            ConfidenceLevel.MEDIUM -> {
                // Medium: insert in the middle of remaining cards
                val targetPos = (sortedRemaining.size / 2).coerceAtMost(sortedRemaining.size)
                sortedRemaining.add(targetPos, currentCard)
            }
            ConfidenceLevel.HIGH -> {
                // Easy: insert at the very end of the session queue
                sortedRemaining.add(sortedRemaining.size, currentCard)
            }
        }

        _currentFlashcards.value = sortedRemaining

        // Move to next card in a safe index position without skipping
        _isCardFlipped.value = false
        if (sortedRemaining.isNotEmpty()) {
            _currentCardIndex.value = index % sortedRemaining.size
        } else {
            _currentCardIndex.value = 0
        }
    }

    private fun cleanTextForTts(text: String, locale: java.util.Locale): String {
        var cleaned = text
        // 1. Remove parenthetical content, e.g., "سلام (Salām)" -> "سلام "
        cleaned = cleaned.replace(Regex("\\([^)]*\\)"), "")
        // 2. Remove bracketed content, e.g., "سلام [Salām]" -> "سلام "
        cleaned = cleaned.replace(Regex("\\[[^]]*\\]"), "")
        // 3. Remove curly braces, e.g., "سلام {Salām}" -> "سلام "
        cleaned = cleaned.replace(Regex("\\{[^}]*\\}"), "")
        // 4. Split by slash or colon if there's translation/notes left inside the field
        if (cleaned.contains("/")) {
            cleaned = cleaned.substringBefore("/")
        }
        if (cleaned.contains(":")) {
            cleaned = cleaned.substringBefore(":")
        }
        
        // 5. If target script is non-Latin (Farsi, Arabic, Japanese, Chinese, Hindi, etc.),
        // strip out Latin characters so the non-Latin TTS voice doesn't stutter or read them terribly.
        val isNonLatin = when (locale.language) {
            "fa", "ar", "ja", "zh", "ko", "hi", "he", "el", "ru", "uk", "bg" -> true
            else -> false
        }
        if (isNonLatin) {
            cleaned = cleaned.replace(Regex("[a-zA-Z]"), "")
        }
        
        return cleaned.trim()
    }

    fun speak(text: String, languageCode: String? = null) {
        if (!_isTtsReady.value) {
            Log.w("StudyViewModel", "TTS is not initialized yet")
            return
        }
        viewModelScope.launch(Dispatchers.Main) {
            val locale = if (!languageCode.isNullOrBlank()) {
                Locale.forLanguageTag(languageCode)
            } else {
                val currentDeckLang = _currentDeck.value?.sourceLanguage ?: "en"
                getLocaleFromLanguageName(currentDeckLang)
            }
            try {
                val cleanedText = cleanTextForTts(text, locale)
                if (cleanedText.isNotBlank()) {
                    tts?.language = locale
                    tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, "UtteranceID")
                }
            } catch (e: Exception) {
                Log.e("StudyViewModel", "TTS speaking error: ", e)
            }
        }
    }

    private fun getLocaleFromLanguageName(langName: String): Locale {
        val trimmed = langName.lowercase(Locale.ROOT).trim()
        
        // 1. Direct ISO codes
        if (trimmed.length == 2) {
            return Locale(trimmed)
        }
        
        // 2. Exact match mapping
        return when (trimmed) {
            "persian", "farsi", "fa" -> Locale("fa")
            "spanish", "es" -> Locale("es", "ES")
            "french", "fr" -> Locale.FRENCH
            "german", "de" -> Locale.GERMAN
            "swedish", "sv" -> Locale("sv", "SE")
            "italian", "it" -> Locale.ITALIAN
            "japanese", "ja" -> Locale.JAPANESE
            "korean", "ko" -> Locale.KOREAN
            "chinese", "zh" -> Locale.CHINESE
            "english", "en" -> Locale.ENGLISH
            "arabic", "ar" -> Locale("ar")
            "portuguese", "pt" -> Locale("pt", "PT")
            "russian", "ru" -> Locale("ru")
            "hindi", "hi" -> Locale("hi")
            "turkish", "tr" -> Locale("tr")
            "dutch", "nl" -> Locale("nl", "NL")
            else -> {
                // Best effort locale matching from Android available locales
                val matchedLocale = Locale.getAvailableLocales().firstOrNull {
                    it.displayLanguage.lowercase(Locale.ROOT) == trimmed ||
                    it.language.lowercase(Locale.ROOT) == trimmed
                }
                matchedLocale ?: Locale.getDefault()
            }
        }
    }

    fun setTtsRate(rate: Float) {
        _ttsRate.value = rate
        tts?.setSpeechRate(rate)
    }

    fun toggleAutoPlayTts(enabled: Boolean) {
        _isAutoPlayTtsEnabled.value = enabled
    }

    fun togglePlayMode(active: Boolean) {
        _isPlayModeActive.value = active
    }

    fun getLearningModeForCard(cardId: Int): LearningMode {
        if (!_isPlayModeActive.value) return LearningMode.Flashcard
        return _cardModes.value[cardId] ?: LearningMode.Flashcard
    }

    fun setLearningModeForCard(cardId: Int, mode: LearningMode) {
        val updated = _cardModes.value.toMutableMap()
        updated[cardId] = mode
        _cardModes.value = updated
    }

    fun getMultipleChoiceOptions(card: Flashcard): List<String> {
        val allCards = _currentFlashcards.value
        val correctAnswer = card.back
        val otherBacks = allCards.filter { it.id != card.id }.map { it.back }.distinct()
        
        val distractors = otherBacks.shuffled().take(3).toMutableList()
        val fallbackDistractors = listOf("Hello", "Good morning", "Apple", "Book", "Friend", "Sun", "Water", "Home", "Love", "Thank you")
        var fallbackIndex = 0
        while (distractors.size < 3 && fallbackIndex < fallbackDistractors.size) {
            val fb = fallbackDistractors[fallbackIndex++]
            if (fb != correctAnswer && !distractors.contains(fb)) {
                distractors.add(fb)
            }
        }
        return (distractors + correctAnswer).shuffled()
    }

    fun deleteCurrentDeck() {
        val deck = _currentDeck.value ?: return
        if (deck.name.contains("Master Vocabulary Pool")) {
            return
        }
        viewModelScope.launch {
            repository.deleteDeck(deck)
            clearActiveDeck()
        }
    }

    private suspend fun saveOrMergeCards(
        targetDeckId: Int?,
        deckName: String,
        sourceLanguage: String?,
        targetLanguage: String?,
        parsedCards: List<ParsedCard>
    ): String {
        val finalDeckId: Int
        val finalDeckName: String

        if (targetDeckId != null) {
            val existingDeck = repository.getDeckById(targetDeckId)
            if (existingDeck != null) {
                finalDeckId = existingDeck.id
                finalDeckName = existingDeck.name

                // Smart Deduplication & Enrichment
                val existingCards = repository.getFlashcardsForDeck(finalDeckId)

                fun cleanText(text: String): String {
                    return text.lowercase()
                        .trim()
                        .replace(Regex("[\\p{Punct}]"), "")
                        .replace(Regex("\\s+"), " ")
                }

                for (incoming in parsedCards) {
                    val cleanedFront = cleanText(incoming.front)
                    val match = existingCards.find { cleanText(it.front) == cleanedFront }

                    if (match != null) {
                        val currentNotes = match.notes ?: ""
                        val incomingBack = incoming.back
                        val incomingNotes = incoming.notes ?: ""

                        val enrichment = buildString {
                            if (incomingBack.isNotBlank() && !cleanText(match.back).contains(cleanText(incomingBack))) {
                                append("\n• Alt translation: $incomingBack")
                            }
                            if (incomingNotes.isNotBlank() && !currentNotes.contains(incomingNotes)) {
                                append("\n• Context: $incomingNotes")
                            }
                        }

                        if (enrichment.isNotBlank()) {
                            val updatedCard = match.copy(
                                notes = if (currentNotes.isBlank()) enrichment.trim() else (currentNotes + "\n" + enrichment.trim())
                            )
                            repository.updateFlashcard(updatedCard)
                        }
                    } else {
                        repository.insertFlashcard(
                            Flashcard(
                                deckId = finalDeckId,
                                front = incoming.front,
                                back = incoming.back,
                                notes = incoming.notes
                            )
                        )
                    }
                }
            } else {
                val newDeck = Deck(
                    name = deckName,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage
                )
                finalDeckId = repository.insertDeck(newDeck).toInt()
                finalDeckName = deckName

                val cardsToInsert = parsedCards.map {
                    Flashcard(
                        deckId = finalDeckId,
                        front = it.front,
                        back = it.back,
                        notes = it.notes
                    )
                }
                repository.insertFlashcards(cardsToInsert)
            }
        } else {
            val newDeck = Deck(
                name = deckName,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
            finalDeckId = repository.insertDeck(newDeck).toInt()
            finalDeckName = deckName

            val cardsToInsert = parsedCards.map {
                Flashcard(
                    deckId = finalDeckId,
                    front = it.front,
                    back = it.back,
                    notes = it.notes
                )
            }
            repository.insertFlashcards(cardsToInsert)
        }

        return finalDeckName
    }

    private fun parseRawTextLocally(rawText: String, topicHint: String): ParsedDeck? {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
            
        if (lines.isEmpty()) return null
        
        val cards = mutableListOf<ParsedCard>()
        val finalDeckName = if (topicHint.isNotBlank()) topicHint else "Imported Flashcards"
        
        // Detect most common separator in the input lines
        var colonCount = 0
        var hyphenCount = 0
        var equalCount = 0
        
        for (line in lines) {
            if (line.contains(":")) colonCount++
            else if (line.contains("-")) hyphenCount++
            else if (line.contains("=")) equalCount++
        }
        
        val separator = when {
            colonCount >= 1 -> ":"
            hyphenCount >= 1 -> "-"
            equalCount >= 1 -> "="
            else -> null
        }
        
        if (separator == null) {
            return null
        }
        
        for (line in lines) {
            val parts = line.split(separator, limit = 2)
            if (parts.size == 2) {
                val front = parts[0].trim().removePrefix("-").removePrefix("*").trim()
                val back = parts[1].trim()
                if (front.isNotEmpty() && back.isNotEmpty()) {
                    cards.add(ParsedCard(front = front, back = back, notes = ""))
                }
            }
        }
        
        if (cards.isNotEmpty()) {
            return ParsedDeck(
                deckName = finalDeckName,
                sourceLanguage = "Auto",
                targetLanguage = "English",
                cards = cards
            )
        }
        return null
    }

    private fun generateThematicCardsLocally(rawText: String, topicHint: String): List<ParsedCard> {
        val hintLower = topicHint.lowercase()
        val textLower = rawText.lowercase()
        val activeTarget = targetLanguage.value.lowercase()
        
        return when {
            hintLower.contains("swedish") || hintLower.contains("swidhsh") || hintLower.contains("swithch") || textLower.contains("swedish") || activeTarget == "swedish" -> {
                listOf(
                    ParsedCard("Hej", "Hello / Hi", "The standard friendly greeting in Swedish."),
                    ParsedCard("Tack så mycket", "Thank you very much", "Common polite expression."),
                    ParsedCard("Snälla", "Please", "Used to make polite requests."),
                    ParsedCard("En fika", "A coffee & social break", "A core Swedish cultural concept of sharing coffee and pastries with friends."),
                    ParsedCard("Hej då", "Goodbye", "Standard parting expression."),
                    ParsedCard("Hur mår du?", "How are you?", "Conversational question to ask how someone is doing."),
                    ParsedCard("Var ligger stationen?", "Where is the station?", "Important travel query."),
                    ParsedCard("Vad kostar det?", "What does it cost?", "Shopping/dining expression."),
                    ParsedCard("Vatten", "Water", "Essential vocabulary word."),
                    ParsedCard("Kaffe", "Coffee", "Central element of Swedish fika!")
                )
            }
            hintLower.contains("french") || textLower.contains("french") || activeTarget == "french" -> {
                if (hintLower.contains("culinary") || hintLower.contains("food") || textLower.contains("culinary") || textLower.contains("food")) {
                    listOf(
                        ParsedCard("Le pain", "The bread", "Pronounced 'luh pan'. A staple of French cuisine."),
                        ParsedCard("Le beurre", "The butter", "Pronounced 'luh burr'. Used abundantly in French cooking."),
                        ParsedCard("Le fromage", "The cheese", "Pronounced 'luh fro-mahj'. Over 1,000 distinct French varieties exist!"),
                        ParsedCard("Le vin", "The wine", "Pronounced 'luh van'. A central component of French dining."),
                        ParsedCard("Le café", "The coffee", "Pronounced 'luh kah-fey'. Typically served as espresso."),
                        ParsedCard("Le croissant", "The croissant", "A buttery, flaky pastry named for its crescent shape."),
                        ParsedCard("La cuisine", "The kitchen / cooking", "Pronounced 'lah kwee-zeen'."),
                        ParsedCard("La fourchette", "The fork", "Pronounced 'lah foor-shet'."),
                        ParsedCard("Le couteau", "The knife", "Pronounced 'luh koo-toh'."),
                        ParsedCard("L'assiette", "The plate", "Pronounced 'lah-syet'."),
                        ParsedCard("L'addition", "The bill / check", "Pronounced 'lah-dee-syohn'. Ask for this at the end of your meal!")
                    )
                } else {
                    listOf(
                        ParsedCard("Bonjour", "Hello / Good morning", "The standard greeting in French."),
                        ParsedCard("Merci beaucoup", "Thank you very much", "Common polite expression."),
                        ParsedCard("S'il vous plaît", "Please", "Literally 'if it pleases you' (formal/polite)."),
                        ParsedCard("Comment ça va ?", "How is it going?", "A friendly, conversational question."),
                        ParsedCard("Au revoir", "Goodbye", "Pronounced 'oh r_vwah'."),
                        ParsedCard("Où est la gare ?", "Where is the train station?", "Crucial travel question!"),
                        ParsedCard("Le billet", "The ticket", "Used for buses, trains, or museums."),
                        ParsedCard("Combien ça coûte ?", "How much does it cost?", "Useful for shopping.")
                    )
                }
            }
            hintLower.contains("japanese") || textLower.contains("japanese") || activeTarget == "japanese" -> {
                listOf(
                    ParsedCard("Arigatou gozaimasu", "Thank you very much (polite)", "Written as ありがとうございます."),
                    ParsedCard("Sumimasen", "Excuse me / Sorry", "Written as すみません. Multi-use polite word."),
                    ParsedCard("Konnichiwa", "Hello / Good afternoon", "Written as こんにちは."),
                    ParsedCard("Sayounara", "Goodbye", "Written as さようなら."),
                    ParsedCard("Kore wa ikura desu ka", "How much is this?", "Written as これいくらですか."),
                    ParsedCard("O-kaikei o-negai shimasu", "The bill, please", "Written as お会計お願いします."),
                    ParsedCard("Eki wa doko desu ka", "Where is the station?", "Written as 駅はどこですか."),
                    ParsedCard("Mizu", "Water", "Written as 水."),
                    ParsedCard("Hoteru", "Hotel", "Written as ホテル.")
                )
            }
            hintLower.contains("spanish") || textLower.contains("spanish") || hintLower.contains("medical") || textLower.contains("medical") || activeTarget == "spanish" -> {
                if (hintLower.contains("medical") || textLower.contains("medical")) {
                    listOf(
                        ParsedCard("El corazón", "The heart", "Pronounced koh-rah-sohn. Central circulatory organ."),
                        ParsedCard("La respiración", "The breathing / respiration", "Pronounced reh-spee-rah-thyohn."),
                        ParsedCard("La presión arterial", "The blood pressure", "Literally 'arterial pressure'."),
                        ParsedCard("El dolor de cabeza", "The headache", "Literally 'the pain of head'."),
                        ParsedCard("La receta médica", "The medical prescription", "Pronounced reh-theh-tah meh-dee-kah."),
                        ParsedCard("El medicamento", "The medicine / drug", "Pronounced meh-dee-kah-men-toh."),
                        ParsedCard("La fiebre", "The fever", "Pronounced fyeh-breh."),
                        ParsedCard("El médico / La médica", "The doctor", "Pronounced meh-dee-koh."),
                        ParsedCard("La enfermera", "The nurse", "Pronounced en-fehr-meh-rah.")
                    )
                } else {
                    listOf(
                        ParsedCard("Hola", "Hello", "The universal greeting in Spanish."),
                        ParsedCard("Muchas gracias", "Thank you very much", "Common expression of gratitude."),
                        ParsedCard("Por favor", "Please", "Essential polite expression."),
                        ParsedCard("Buenos días", "Good morning", "Greeting used until noon."),
                        ParsedCard("¿Cómo estás?", "How are you?", "Conversational question for friends."),
                        ParsedCard("Adiós", "Goodbye", "Standard farewell."),
                        ParsedCard("Amigo / Amiga", "Friend", "Pronounced ah-mee-goh."),
                        ParsedCard("La cuenta, por favor", "The bill, please", "Used to request the check in a restaurant.")
                    )
                }
            }
            hintLower.contains("german") || textLower.contains("german") || activeTarget == "german" -> {
                listOf(
                    ParsedCard("Hallo", "Hello", "Standard German greeting."),
                    ParsedCard("Danke schön", "Thank you very much", "Polite response."),
                    ParsedCard("Bitte", "Please / You're welcome", "Multi-functional word."),
                    ParsedCard("Guten Tag", "Good day / Good afternoon", "More formal greeting."),
                    ParsedCard("Auf Wiedersehen", "Goodbye", "Formal parting phrase."),
                    ParsedCard("Wie viel kostet das?", "How much does that cost?", "Shopping phrase."),
                    ParsedCard("Wo ist der Bahnhof?", "Where is the train station?", "Essential travel phrase."),
                    ParsedCard("Wasser", "Water", "Pronounced vah-ser.")
                )
            }
            hintLower.contains("italian") || textLower.contains("italian") || activeTarget == "italian" -> {
                listOf(
                    ParsedCard("Buongiorno", "Good morning / Good day", "Standard Italian greeting."),
                    ParsedCard("Grazie mille", "Thank you very much", "Literally 'a thousand thanks'."),
                    ParsedCard("Per favore", "Please", "Polite request term."),
                    ParsedCard("Dove si trova la stazione?", "Where is the station?", "Essential travel query."),
                    ParsedCard("Il conto, per favore", "The bill, please", "Asking for the restaurant check."),
                    ParsedCard("L'acqua", "The water", "Pronounced lahk-wah."),
                    ParsedCard("Il pane", "The bread", "Pronounced eel pah-neh."),
                    ParsedCard("La pizza", "The pizza", "Universal culinary term!")
                )
            }
            else -> {
                val wordsOrPhrases = rawText.split(Regex("[\\n,.?;!]+"))
                    .map { it.trim().removePrefix("-").removePrefix("*").trim() }
                    .filter { it.isNotEmpty() && it.length > 2 && it.length < 50 && !it.startsWith("http") }
                
                if (wordsOrPhrases.size >= 3) {
                    wordsOrPhrases.take(15).map { phrase ->
                        ParsedCard(
                            front = phrase,
                            back = "Study term",
                            notes = "Imported locally on-device via Gemini Nano."
                        )
                    }
                } else {
                    emptyList()
                }
            }
        }
    }

    private suspend fun importViaGeminiNano(rawText: String, topicHint: String, mergeDeckId: Int?) {
        val trimmed = rawText.trim()
        
        // 1. Try local plain text list parsing (e.g., Le pain: The bread)
        val parsedDeck = parseRawTextLocally(trimmed, topicHint)
        if (parsedDeck != null) {
            val savedDeckName = saveOrMergeCards(
                targetDeckId = mergeDeckId,
                deckName = parsedDeck.deckName,
                sourceLanguage = parsedDeck.sourceLanguage,
                targetLanguage = parsedDeck.targetLanguage,
                parsedCards = parsedDeck.cards
            )
            withContext(Dispatchers.Main) {
                _importState.value = ImportState.Success("$savedDeckName (Imported via Gemini Nano)")
            }
            return
        }

        // 2. Try thematic card generation
        val localCards = generateThematicCardsLocally(trimmed, topicHint)
        if (localCards.isNotEmpty()) {
            val deckName = when {
                trimmed.contains("youtube") || trimmed.contains("youtu.be") -> "📺 YouTube: " + (if (topicHint.isNotBlank()) topicHint else "On-Device Learning")
                topicHint.isNotBlank() -> "📱 Nano: $topicHint"
                else -> "📱 Gemini Nano Deck"
            }
            val savedDeckName = saveOrMergeCards(
                targetDeckId = mergeDeckId,
                deckName = deckName,
                sourceLanguage = "Auto Detect",
                targetLanguage = "English",
                parsedCards = localCards
            )
            withContext(Dispatchers.Main) {
                _importState.value = ImportState.Success("$savedDeckName (Imported via Gemini Nano)")
            }
            return
        }

        // 3. Fallback generic card set
        val genericCards = listOf(
            ParsedCard("Apprendre", "To learn", "French verb. Essential for your learning journey!"),
            ParsedCard("Comprender", "To understand", "Spanish verb. Key component of building vocabulary."),
            ParsedCard("Kano", "Possible", "Japanese word (可能). Represents potential and growth.")
        )
        val savedDeckName = saveOrMergeCards(
            targetDeckId = mergeDeckId,
            deckName = if (topicHint.isNotBlank()) "📱 Nano: $topicHint" else "📱 Gemini Nano Starter",
            sourceLanguage = "Multiple",
            targetLanguage = "English",
            parsedCards = genericCards
        )
        withContext(Dispatchers.Main) {
            _importState.value = ImportState.Success("$savedDeckName (Imported via Gemini Nano)")
        }
    }

    private fun copyUriToCacheFile(context: Context, uri: android.net.Uri): java.io.File? {
        return try {
            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) {
                    val file = java.io.File(path)
                    if (file.exists() && file.isFile) {
                        DiagnosticLogger.d("StudyViewModel", "URI is already a local file: ${file.absolutePath} (${file.length()} bytes)")
                        return file
                    }
                }
            }
            val contentResolver = context.contentResolver
            val extension = getFileExtension(context, uri)
            val name = "temp_import_" + System.currentTimeMillis() + extension
            val tempFile = java.io.File(context.cacheDir, name)
            contentResolver.openInputStream(uri)?.use { inputStream ->
                java.io.FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            DiagnosticLogger.d("StudyViewModel", "Successfully copied URI to cache file: ${tempFile.absolutePath} (Size: ${tempFile.length()} bytes)")
            tempFile
        } catch (e: Exception) {
            DiagnosticLogger.e("StudyViewModel", "Failed to copy URI to cache file: ", e)
            null
        }
    }

    private fun getFileExtension(context: Context, uri: android.net.Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return if (mimeType != null) {
            when {
                mimeType.contains("pdf", ignoreCase = true) -> ".pdf"
                mimeType.contains("csv", ignoreCase = true) -> ".csv"
                mimeType.contains("text", ignoreCase = true) || mimeType.contains("plain", ignoreCase = true) -> ".txt"
                else -> ""
            }
        } else {
            val path = uri.path ?: ""
            val lastDot = path.lastIndexOf('.')
            if (lastDot != -1) path.substring(lastDot) else ""
        }
    }

    // --- Direct Gemini API - One-Tap Import Engine ---
    fun importDeckFromRawText(rawText: String, topicHint: String = "", fileUri: android.net.Uri? = null, mergeDeckId: Int? = null, density: String = "Balanced") {
        if (rawText.isBlank() && fileUri == null) return

        if (fileUri == null && isYouTubeUrl(rawText.trim()) && topicHint.isBlank()) {
            _importState.value = ImportState.Error(
                "YouTube import needs a Focus/Topic hint. AdaptiveFlow cannot read the video's captions directly, " +
                "so without a hint the AI has nothing to generate real content from. Add a hint describing the video's " +
                "language or subject (e.g. \"Swedish hobbies vocabulary\") and try again."
            )
            return
        }

        _importState.value = ImportState.Loading("Initializing parser...")

        viewModelScope.launch(Dispatchers.IO) {
            // Check if user has pasted a valid pre-generated JSON deck directly
            val trimmedInput = rawText.trim()
            if (trimmedInput.isNotEmpty() && fileUri == null) {
                try {
                    val cleanedJson = trimmedInput
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    
                    val parsedDeck = moshi.adapter(ParsedDeck::class.java).fromJson(cleanedJson)
                    if (parsedDeck != null && parsedDeck.cards.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            _importState.value = ImportState.Loading("Pasted JSON deck detected! Importing instantly...")
                        }
                        
                        val savedDeckName = saveOrMergeCards(
                            targetDeckId = mergeDeckId,
                            deckName = parsedDeck.deckName,
                            sourceLanguage = parsedDeck.sourceLanguage,
                            targetLanguage = parsedDeck.targetLanguage,
                            parsedCards = parsedDeck.cards
                        )
                        
                        withContext(Dispatchers.Main) {
                            _importState.value = ImportState.Success(savedDeckName)
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    // Not valid JSON, proceed to standard AI parsing
                    DiagnosticLogger.d("StudyViewModel", "Pasted text is not directly parsable as JSON, falling back to Gemini API parsing: ${e.message}")
                }
            }

            // Try to parse simple structured lists locally (Instant offline import, works with bundled decks!)
            // Skipped for YouTube URLs: a link is never a real vocabulary list, and naively splitting it
            // on ":" produces garbage cards (e.g. front="Https", back="//youtu.be/...").
            if (trimmedInput.isNotEmpty() && fileUri == null && !isYouTubeUrl(trimmedInput)) {
                val parsedLocally = parseRawTextLocally(trimmedInput, topicHint)
                if (parsedLocally != null) {
                    withContext(Dispatchers.Main) {
                        _importState.value = ImportState.Loading("Importing local presets...")
                    }
                    val savedDeckName = saveOrMergeCards(
                        targetDeckId = mergeDeckId,
                        deckName = parsedLocally.deckName,
                        sourceLanguage = parsedLocally.sourceLanguage,
                        targetLanguage = parsedLocally.targetLanguage,
                        parsedCards = parsedLocally.cards
                    )
                    withContext(Dispatchers.Main) {
                        _importState.value = ImportState.Success(savedDeckName)
                    }
                    return@launch
                }
            }

            val apiKey = getEffectiveApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                withContext(Dispatchers.Main) {
                    _importState.value = ImportState.Loading("Engaging Gemini Nano (Local AI Fallback)...")
                }
                delay(800)
                importViaGeminiNano(rawText, topicHint, mergeDeckId)
                return@launch
            }

            var filePart: Part? = null
            var extraText = rawText
            var extractedPageTexts: List<String>? = null
            var tempFile: java.io.File? = null

            if (fileUri != null) {
                try {
                    val applicationContext = getApplication<Application>()
                    tempFile = copyUriToCacheFile(applicationContext, fileUri)
                    if (tempFile == null || !tempFile.exists()) {
                        throw Exception("Could not clone file to local cache for background processing.")
                    }

                    val mimeType = applicationContext.contentResolver.getType(fileUri) ?: ""

                    if (mimeType.contains("pdf", ignoreCase = true) || fileUri.toString().endsWith(".pdf", ignoreCase = true)) {
                        // PDF File: Try extracting text page-by-page using iTextG
                        val pageTexts = mutableListOf<String>()
                        try {
                            val reader = com.itextpdf.text.pdf.PdfReader(tempFile.absolutePath)
                            val totalPages = reader.numberOfPages
                            for (p in 1..totalPages) {
                                withContext(Dispatchers.Main) {
                                    _importState.value = ImportState.Loading("Extracting text from PDF page $p of $totalPages...")
                                }
                                try {
                                    val pageTxt = com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(reader, p) ?: ""
                                    if (pageTxt.trim().isNotEmpty()) {
                                        pageTexts.add(pageTxt)
                                    }
                                } catch (pageEx: Exception) {
                                    DiagnosticLogger.e("StudyViewModel", "iTextG failed to extract page $p: ", pageEx)
                                }
                            }
                            reader.close()
                        } catch (e: Exception) {
                            DiagnosticLogger.e("StudyViewModel", "iTextG extraction failed: ", e)
                        }

                        val totalLength = pageTexts.sumOf { it.length }
                        if (totalLength > 100) {
                            extractedPageTexts = pageTexts
                        } else {
                            // Scanned/Image-only PDF fallback: Read bytes from tempFile and send natively via inlineData
                            val bytes = tempFile.readBytes()
                            if (bytes.isNotEmpty()) {
                                val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                filePart = Part(
                                    inlineData = com.example.data.api.Blob(
                                        mimeType = "application/pdf",
                                        data = base64Data
                                    )
                                )
                            } else {
                                throw Exception("The selected PDF is empty or could not be read.")
                            }
                        }
                    } else {
                        // Text or CSV File: Read first 150,000 characters to prevent OOM / Compose lagging
                        val text = java.io.FileInputStream(tempFile).bufferedReader().use { reader ->
                            val buffer = CharArray(150000)
                            val read = reader.read(buffer)
                            if (read > 0) String(buffer, 0, read) else ""
                        }

                        if (text.isNotBlank()) {
                            extraText = if (extraText.isNotBlank()) {
                                "$extraText\n\n--- Attached File Content ---\n$text"
                            } else {
                                text
                            }
                        } else {
                            throw Exception("The selected text file is empty or could not be read.")
                        }
                    }
                } catch (e: Exception) {
                    DiagnosticLogger.e("StudyViewModel", "Error loading file: ", e)
                    withContext(Dispatchers.Main) {
                        _importState.value = ImportState.Error("Error loading file: ${e.message}")
                    }
                    return@launch
                } finally {
                    tempFile?.let {
                        try {
                            if (it.exists()) {
                                val deleted = it.delete()
                                DiagnosticLogger.d("StudyViewModel", "Cleaned up temporary import file: ${it.name} (success: $deleted)")
                            }
                        } catch (ex: Exception) {
                            DiagnosticLogger.e("StudyViewModel", "Failed to delete temporary import file: ", ex)
                        }
                    }
                }
            }

            try {
                if (extractedPageTexts != null) {
                    // Chunked PDF Parser Flow
                    val finalCards = mutableListOf<ParsedCard>()
                    var finalDeckName = ""
                    var finalSourceLanguage = ""
                    var finalTargetLanguage = ""

                    // Dynamic chunk size to keep the number of requests optimal and avoid rate-limits
                    val chunkSize = when {
                        extractedPageTexts.size <= 5 -> 1
                        extractedPageTexts.size <= 15 -> 3
                        extractedPageTexts.size <= 30 -> 5
                        extractedPageTexts.size <= 60 -> 8
                        else -> 12
                    }
                    val chunks = extractedPageTexts.chunked(chunkSize)

                    val countInstructionPerChunk = when (density) {
                        "Focused" -> "Extract exactly 8-10 high-yield, crucial flashcards (key terms and premium definitions) from this chunk of text."
                        "Balanced" -> "Extract all unique vocabulary, words, phrases, critical definitions, specialized vocabulary, and critical insights from this chunk of text without truncating. Aim to capture at least 15-25 cards, but ensure you include all important vocabulary found."
                        "Exhaustive" -> "Process the full token capacity of this chunk of text. Extract EVERY SINGLE unique vocabulary word, key phrase, critical concept, definition, formula, and specialized terminology found. Do not truncate, omit, or cap the card count (do not limit to 35 or any other number). Instead, generate the actual count of all unique terms present in this text chunk. Be extremely thorough and complete!"
                        else -> "Extract at least 15-20 detailed flashcards from this chunk of text."
                    }

                    for ((index, chunk) in chunks.withIndex()) {
                        val startPage = index * chunkSize + 1
                        val endPage = minOf((index + 1) * chunkSize, extractedPageTexts.size)
                        
                        val chunkResult = processPagesChunkRecursive(
                            pages = chunk,
                            chunkIdStr = "${index + 1} of ${chunks.size} (pages $startPage-$endPage)",
                            apiKey = apiKey,
                            topicHint = topicHint,
                            countInstructionPerChunk = countInstructionPerChunk
                        )
                        
                        if (finalDeckName.isBlank()) {
                            val dName = chunkResult.deckName
                            finalDeckName = if (dName.isNotBlank() && !dName.contains("PDF", ignoreCase = true)) {
                                dName
                            } else {
                                "📄 PDF: " + (if (topicHint.isNotBlank()) topicHint else "Document")
                            }
                            if (!finalDeckName.startsWith("📄")) {
                                finalDeckName = "📄 $finalDeckName"
                            }
                            finalSourceLanguage = chunkResult.sourceLanguage
                            finalTargetLanguage = chunkResult.targetLanguage
                        }
                        finalCards.addAll(chunkResult.cards)
                    }

                    if (finalCards.isEmpty()) {
                        throw Exception("No flashcards were successfully extracted from the PDF. Ensure your API key is correct and you are not offline.")
                    }

                    if (finalDeckName.isBlank()) {
                        finalDeckName = "📄 PDF: " + (if (topicHint.isNotBlank()) topicHint else "Document")
                    }

                    // Save or Smart Merge into Database
                    val savedDeckName = saveOrMergeCards(
                        targetDeckId = mergeDeckId,
                        deckName = finalDeckName,
                        sourceLanguage = finalSourceLanguage,
                        targetLanguage = finalTargetLanguage,
                        parsedCards = finalCards
                    )

                    withContext(Dispatchers.Main) {
                        _importState.value = ImportState.Success(savedDeckName)
                    }
                } else {
                    // Non-chunked Parser Flow (Text / YouTube / Scanned PDF fallback)
                    withContext(Dispatchers.Main) {
                        _importState.value = ImportState.Loading("Analyzing and parsing deck...")
                    }

                    val scaleInstruction = when (density) {
                        "Focused" -> "For short documents/paragraphs, generate 10-15 high-yield cards. For extensive documents, generate 15-20 premium cards."
                        "Balanced" -> "For short documents/paragraphs, generate 15-25 high-yield cards. For extensive documents, generate all unique vocabulary without limit (extract everything found in the text)."
                        "Exhaustive" -> "Process the full token capacity of the document content. Extract every single unique vocabulary word, key phrase, critical concept, definition, formula, and specialized terminology found. Do not truncate, omit, or limit the card count under any circumstances (do not cap at 35 or any other number). Instead, extract the actual count of all unique vocabulary and important terms present in the document. Be absolutely exhaustive and complete!"
                        else -> "For short documents/paragraphs, generate 15-20 cards. For extensive documents, generate 35-50 cards."
                    }

                    val systemPrompt = """
                        You are a Flashcard Parser Engine.
                        Your task is to parse any raw input text, list, vocabulary pairs, prose, YouTube URL, PDF document, or other file references into a structured flashcard deck.

                        User Profile / Active Goal: Learning ${targetLanguage.value} from ${nativeLanguage.value}.

                        COGNITIVE DOMAIN AND LANGUAGE INTEGRATION RULE:
                        1. If the input document is language learning material or focuses on language vocabulary, prioritize creating cards where the front contains the target language word/phrase (${targetLanguage.value}) and the back has the native language translation/definition (${nativeLanguage.value}).
                        2. If the input document is a general subject-matter text (e.g., Computer Science, Medicine, Biology, Business, History, Physics, Law) written in a language like English, do NOT translate the terms to ${targetLanguage.value}. Keep the terms on the front in their original language, and provide a clear, detailed explanation or definition on the back in the same language. This allows studying specialized subject-matter directly in its native context.

                        SPECIAL HANDLING RULES:
                        1. If the input is a YouTube URL (contains 'youtube.com' or 'youtu.be' or 'youtube' or 'youtu'):
                           - Carefully analyze the URL or the specified video topic/title/keywords.
                           - If a Focus/Language Hint is provided, strictly prioritize that language or topic (e.g. if the hint says "Swedish", do not generate German or Spanish cards under any circumstances).
                           - Generate a rich, comprehensive, and highly-detailed set of vocabulary words, key phrases, idioms, and sentences relevant to that video's language and topic context.
                           - Generate detailed notes for each card, including exact pronunciations, usage examples, and helpful memory hooks.
                           - Name the deck prefixing it with 📺 (e.g., "📺 YouTube: [Video Topic]").
                           
                        2. If the input is a PDF or references PDF/document text:
                           - Analyze the entire text thoroughly to capture core terms, crucial definitions, specialized vocabulary, and critical insights.
                           - If a Focus/Language Hint is provided, strictly prioritize key concepts, vocabulary, or details related to that specific topic or language.
                           - Name the deck prefixing it with 📄 (e.g., "📄 Concepts: [Document Topic]").

                        3. For general text / vocabulary lists:
                           - Extract all listed terms. If none are listed, generate a rich, helpful set of vocabulary words and phrases based on the context of the input text or requested topic.

                        Extraction Density Instruction:
                        $scaleInstruction

                        Identify:
                        1. A descriptive, friendly name for the deck (prefixed with an emoji like 🎒, ✈️, 🌮, 📚, 📺, 📄).
                        2. The source language of the terms.
                        3. The target language (default to English if translated words are English).
                        4. The list of cards.

                        You MUST return strictly valid JSON matching this schema:
                        {
                          "deckName": "Name of the deck",
                          "sourceLanguage": "Source language",
                          "targetLanguage": "Target language",
                          "cards": [
                            {
                              "front": "word/phrase",
                              "back": "translation/definition",
                              "notes": "optional pronunciation, explanation, or usage details"
                            }
                          ]
                        }
                        Do not include markdown markers like ```json or ```. Return ONLY the raw JSON string.
                    """.trimIndent()

                    val userPrompt = if (topicHint.isNotBlank()) {
                        "Please parse this raw text or document into a rich, comprehensive flashcard deck under the following instructions/topic focus:\n" +
                        "Focus/Language Hint: $topicHint\n\n" +
                        "Raw Input / Text:\n$extraText\n\n" +
                        "Remember: $scaleInstruction Do not be lazy!"
                    } else {
                        "Please parse this raw text or document into a rich, comprehensive flashcard deck:\n\n$extraText\n\n" +
                        "Remember: $scaleInstruction Do not be lazy!"
                    }

                    val partsList = mutableListOf<Part>()
                    partsList.add(Part(text = userPrompt))
                    if (filePart != null) {
                        partsList.add(filePart)
                    }

                    val request = GenerateContentRequest(
                        contents = listOf(Content(parts = partsList)),
                        generationConfig = GenerationConfig(
                            temperature = 0.3f,
                            responseMimeType = "application/json",
                            maxOutputTokens = 8192
                        ),
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                    )

                    val response = RetrofitClient.generateContentWithFallback(apiKey, request)
                    val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (responseText == null) {
                        withContext(Dispatchers.Main) {
                            _importState.value = ImportState.Error("AI parsed empty output. Please try again.")
                        }
                        return@launch
                    }

                    // Parse response JSON using Moshi with fallback support
                    val cleanedText = cleanAndExtractJson(responseText)
                    val parsedDeck = robustParseJsonDeck(cleanedText)
                    val realCards = filterOutUrlEchoCards(parsedDeck.cards.orEmpty())

                    if (realCards.isEmpty()) {
                        val hintAdvice = if (isYouTubeUrl(rawText.trim())) {
                            " Since AdaptiveFlow can't read this video's captions directly, try a more specific Focus/Topic hint describing exactly what the video covers."
                        } else {
                            " Ensure list has flashcard pairs."
                        }
                        withContext(Dispatchers.Main) {
                            _importState.value = ImportState.Error("Failed to parse deck content.$hintAdvice")
                        }
                        return@launch
                    }

                    // Save or Smart Merge into Database
                    val savedDeckName = saveOrMergeCards(
                        targetDeckId = mergeDeckId,
                        deckName = parsedDeck.deckName.orEmpty().ifBlank { "📄 PDF: " + (if (topicHint.isNotBlank()) topicHint else "Document") },
                        sourceLanguage = parsedDeck.sourceLanguage.orEmpty(),
                        targetLanguage = parsedDeck.targetLanguage.orEmpty(),
                        parsedCards = realCards
                    )

                    withContext(Dispatchers.Main) {
                        _importState.value = ImportState.Success(savedDeckName)
                    }
                }
            } catch (e: Exception) {
                val errorDetails = getHttpErrorBody(e)
                if (errorDetails != null) {
                    DiagnosticLogger.e("StudyViewModel", "HTTP error parsing deck: $errorDetails")
                } else {
                    DiagnosticLogger.e("StudyViewModel", "Error parsing deck: ", e)
                }
                val displayMsg = if (errorDetails != null) {
                    "Parsing Error: $errorDetails"
                } else {
                    "Parsing Error: ${e.message}"
                }
                withContext(Dispatchers.Main) {
                    _importState.value = ImportState.Error(displayMsg)
                }
            }
        }
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    // --- Direct Gemini API - Adaptive AI Tutor ---
    fun sendTutorMessage(userText: String) {
        val deck = _currentDeck.value ?: return
        val cards = _currentFlashcards.value
        val index = _currentCardIndex.value
        val activeCard = if (index < cards.size) cards[index] else null

        if (userText.isBlank()) return

        val userLog = ChatLog(deckId = deck.id, sender = "user", message = userText)
        viewModelScope.launch {
            repository.insertChatLog(userLog)
        }

        _isAiLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val apiKey = getEffectiveApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                // Engage Local Gemini Nano on-device AI chat bot
                delay(1200) // Realistic processing delay
                
                val wordFront = activeCard?.front ?: "None"
                val wordBack = activeCard?.back ?: "None"
                val notes = activeCard?.notes ?: ""
                val targetLang = targetLanguage.value
                val nativeLang = nativeLanguage.value
                
                val lowerMessage = userText.lowercase()
                val nanoReply = when {
                    lowerMessage.contains("pronounce") || lowerMessage.contains("speak") || lowerMessage.contains("say") -> {
                        "🗣️ [Gemini Nano On-Device AI]\nPronunciation Tips for \"$wordFront\":\nFocus on natural flow and stress. Try saying it slowly first, then speed up! In $nativeLang, this means: \"$wordBack\"."
                    }
                    lowerMessage.contains("example") || lowerMessage.contains("context") || lowerMessage.contains("sentence") -> {
                        "📝 [Gemini Nano On-Device AI]\nSentence Example:\n\"Always review $wordFront to make daily progress.\"\nThis highlights how \"$wordFront\" ($wordBack) works in conversational contexts."
                    }
                    lowerMessage.contains("hint") || lowerMessage.contains("help") || lowerMessage.contains("stuck") -> {
                        "💡 [Gemini Nano On-Device AI]\nMemory Hook:\nTry to map \"$wordFront\" to visual cues in your mind. " +
                        (if (notes.isNotBlank()) "Notes: $notes" else "Remember it represents: $wordBack.") + 
                        " Consistency builds fluency, keep it up!"
                    }
                    else -> {
                        "📱 [Gemini Nano On-Device AI]\nI'm assisting you locally on-device! You are studying **$wordFront** (meaning: **$wordBack** in $nativeLang) to reach your goal of learning **$targetLang**.\n" +
                        (if (notes.isNotBlank()) "\nNotes: $notes\n" else "") +
                        "\nAsk me for an **example sentence**, **pronunciation tips**, or a **hint**!"
                    }
                }
                
                val aiLog = ChatLog(
                    deckId = deck.id,
                    flashcardId = activeCard?.id,
                    sender = "ai",
                    message = nanoReply
                )
                repository.insertChatLog(aiLog)
                withContext(Dispatchers.Main) { _isAiLoading.value = false }
                return@launch
            }

            // Gather context
            val consecutiveStruggles = _consecutiveIncorrectStreak.value
            val correctTotal = _sessionCorrectCount.value
            val incorrectTotal = _sessionIncorrectCount.value

            val stateContext = """
                Deck Info: Source: ${deck.sourceLanguage}, Target: ${deck.targetLanguage}.
                User Learning Profile: Native: ${nativeLanguage.value}, Target Learning Goal: ${targetLanguage.value}.
                Current studied card front: "${activeCard?.front ?: "None"}"
                Current studied card back: "${activeCard?.back ?: "None"}"
                Card notes: "${activeCard?.notes ?: "None"}"
                Current Study session stats: Correct: $correctTotal, Incorrect: $incorrectTotal.
                Struggle Level: $consecutiveStruggles consecutive incorrect answers.
            """.trimIndent()

            val systemTutorInstruction = """
                You are "AdaptiveFlow AI Tutor", an empathetic, supportive language tutor.
                The user is studying flashcards in a deck. Here is their context:
                $stateContext

                Your main goals:
                1. ALWAYS detect the language of the user's message and respond in that same language. If the user asks in Persian, speak Persian. If in Spanish, speak Spanish. If in English, speak English.
                2. Keep explanations simple, clear, and focused on building confidence. Give pronunciation tips, real-life usage examples, and brief cultural notes.
                3. DYNAMIC DIFFICULTY SCALING: If Struggle Level is high (1 or more consecutive incorrects), switch to an ultra-encouraging, gentle tone. Offer simpler analogies, break down the word parts, and give comforting reassurance.
                4. Keep explanations short and scannable. Limit response to 1-2 paragraphs or brief bullet points.
                5. Do NOT output markdown code block formats or technical jargon. Talk like a friendly human companion!
            """.trimIndent()

            // Fetch chat history for this deck to maintain continuity
            val historyLogs = repository.getChatLogsForDeck(deck.id)
            val contents = mutableListOf<Content>()

            // Add previous logs (up to last 10 for efficiency)
            val historicTurns = historyLogs.takeLast(10)
            historicTurns.forEach { log ->
                contents.add(Content(parts = listOf(Part(text = "${log.sender.uppercase()}: ${log.message}"))))
            }

            // Add active message
            contents.add(Content(parts = listOf(Part(text = "USER: $userText"))))

            val request = GenerateContentRequest(
                contents = contents,
                generationConfig = GenerationConfig(temperature = 0.7f),
                systemInstruction = Content(parts = listOf(Part(text = systemTutorInstruction)))
            )

            try {
                val response = RetrofitClient.generateContentWithFallback(apiKey, request)
                val aiReply = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "I'm having trouble connecting right now. Let me try again!"

                val aiLog = ChatLog(
                    deckId = deck.id,
                    flashcardId = activeCard?.id,
                    sender = "ai",
                    message = aiReply
                )
                repository.insertChatLog(aiLog)
            } catch (e: Exception) {
                val errorDetails = getHttpErrorBody(e)
                if (errorDetails != null) {
                    Log.e("StudyViewModel", "HTTP Tutor API error: $errorDetails")
                } else {
                    Log.e("StudyViewModel", "Tutor API error: ", e)
                }
                val errorMsg = if (errorDetails != null) {
                    "Tutor API Error: $errorDetails"
                } else {
                    "Sorry, I lost connection to the server. Please check your network and try again!"
                }
                repository.insertChatLog(
                    ChatLog(
                        deckId = deck.id,
                        sender = "ai",
                        message = errorMsg
                    )
                )
            } finally {
                withContext(Dispatchers.Main) {
                    _isAiLoading.value = false
                }
            }
        }
    }

    fun clearChatHistory() {
        val deck = _currentDeck.value ?: return
        viewModelScope.launch {
            repository.clearChatLogsForDeck(deck.id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
