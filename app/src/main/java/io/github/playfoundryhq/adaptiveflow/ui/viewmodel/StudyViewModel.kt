package io.github.playfoundryhq.adaptiveflow.ui.viewmodel

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.playfoundryhq.adaptiveflow.ai.Prompts
import io.github.playfoundryhq.adaptiveflow.data.ai.AiClient
import io.github.playfoundryhq.adaptiveflow.data.ai.AiException
import io.github.playfoundryhq.adaptiveflow.data.ai.AiProvider
import io.github.playfoundryhq.adaptiveflow.data.ai.AiProviderId
import io.github.playfoundryhq.adaptiveflow.data.ai.AiTurn
import io.github.playfoundryhq.adaptiveflow.data.backup.DeckExporter
import io.github.playfoundryhq.adaptiveflow.data.database.AppDatabase
import io.github.playfoundryhq.adaptiveflow.data.model.ChatLog
import io.github.playfoundryhq.adaptiveflow.data.model.Deck
import io.github.playfoundryhq.adaptiveflow.data.model.DeckWithCards
import io.github.playfoundryhq.adaptiveflow.data.model.Flashcard
import io.github.playfoundryhq.adaptiveflow.data.pdf.PdfTextExtractor
import io.github.playfoundryhq.adaptiveflow.data.repository.StudyRepository
import io.github.playfoundryhq.adaptiveflow.data.settings.SettingsStore
import io.github.playfoundryhq.adaptiveflow.domain.SrsScheduler
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.DiagnosticLogger as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Locale

@JsonClass(generateAdapter = true)
data class ParsedCard(val front: String, val back: String, val notes: String? = null)

@JsonClass(generateAdapter = true)
data class ParsedDeck(
    val deckName: String = "",
    val sourceLanguage: String = "",
    val targetLanguage: String = "",
    val cards: List<ParsedCard> = emptyList(),
)

private const val MASTER_POOL_NAME = "🧠 Master Vocabulary Pool"
private const val KEY_ACTIVE_DECK = "activeDeckId"

class StudyViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repository: StudyRepository
    private val settings = SettingsStore(application)
    private val aiClient = AiClient()
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    // ---- DB-backed state ----
    val allDecks: StateFlow<List<DeckWithCards>>

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

    // ---- UI status ----
    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    // ---- Session performance ----
    private val _sessionCorrectCount = MutableStateFlow(0)
    val sessionCorrectCount: StateFlow<Int> = _sessionCorrectCount.asStateFlow()

    private val _sessionIncorrectCount = MutableStateFlow(0)
    val sessionIncorrectCount: StateFlow<Int> = _sessionIncorrectCount.asStateFlow()

    private val _consecutiveIncorrectStreak = MutableStateFlow(0)
    val consecutiveIncorrectStreak: StateFlow<Int> = _consecutiveIncorrectStreak.asStateFlow()

    private val _flippedCardIds = MutableStateFlow<Set<Int>>(emptySet())
    val flippedCardIds: StateFlow<Set<Int>> = _flippedCardIds.asStateFlow()

    // ---- TTS ----
    private var tts: TextToSpeech? = null
    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private val _ttsRate = MutableStateFlow(settings.ttsRate)
    val ttsRate: StateFlow<Float> = _ttsRate.asStateFlow()

    private val _isAutoPlayTtsEnabled = MutableStateFlow(settings.ttsAutoPlay)
    val isAutoPlayTtsEnabled: StateFlow<Boolean> = _isAutoPlayTtsEnabled.asStateFlow()

    // ---- Play mode ----
    private val _isPlayModeActive = MutableStateFlow(false)
    val isPlayModeActive: StateFlow<Boolean> = _isPlayModeActive.asStateFlow()

    enum class LearningMode { Flashcard, Quiz }
    enum class ConfidenceLevel { LOW, MEDIUM, HIGH }

    private val _cardModes = MutableStateFlow<Map<Int, LearningMode>>(emptyMap())
    val cardModes: StateFlow<Map<Int, LearningMode>> = _cardModes.asStateFlow()

    sealed interface ImportState {
        data object Idle : ImportState
        data class Loading(val message: String = "Working…") : ImportState
        data class Success(val deckName: String) : ImportState
        data class Error(val message: String) : ImportState
    }

    // ---- AI provider + keys ----
    private val _aiProviderId = MutableStateFlow(settings.providerId)
    val aiProviderId: StateFlow<AiProviderId> = _aiProviderId.asStateFlow()

    /** Key for the currently selected provider (drives "key configured?" UI). */
    private val _customApiKey = MutableStateFlow(settings.activeApiKey())
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _nativeLanguage = MutableStateFlow(settings.nativeLanguage)
    val nativeLanguage: StateFlow<String> = _nativeLanguage.asStateFlow()

    private val _targetLanguage = MutableStateFlow(settings.targetLanguage)
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    fun updateLearningGoal(native: String, target: String) {
        settings.nativeLanguage = native
        settings.targetLanguage = target
        _nativeLanguage.value = native.trim()
        _targetLanguage.value = target.trim()
    }

    fun setAiProvider(id: AiProviderId) {
        settings.providerId = id
        _aiProviderId.value = id
        _customApiKey.value = settings.apiKeyFor(id)
    }

    fun apiKeyFor(id: AiProviderId): String = settings.apiKeyFor(id)

    fun saveApiKey(id: AiProviderId, key: String) {
        settings.setApiKeyFor(id, key)
        if (id == _aiProviderId.value) _customApiKey.value = settings.apiKeyFor(id)
    }

    /** Saves the key for the *currently selected* provider (legacy call site). */
    fun saveCustomApiKey(key: String) = saveApiKey(_aiProviderId.value, key)

    fun clearCustomApiKey() = saveApiKey(_aiProviderId.value, "")

    /** The active provider's key, or "" if none configured. */
    fun getEffectiveApiKey(): String = settings.activeApiKey()

    /** Null when the selected provider has no key configured. */
    private fun activeProvider(): AiProvider? {
        val key = settings.activeApiKey()
        return if (key.isEmpty()) null
        else aiClient.provider(_aiProviderId.value, key)
    }

    private fun noKeyMessage(): String {
        val name = _aiProviderId.value.displayName
        return "AI features need an API key. Add your $name key in Settings, or paste a plain " +
            "\"word: meaning\" list to import offline."
    }

    /** Turns an [AiException] into a short, actionable sentence for the user. */
    private fun friendlyAiError(e: AiException): String {
        val name = _aiProviderId.value.displayName
        val other = if (_aiProviderId.value == AiProviderId.GEMINI) "DeepSeek" else "Gemini"
        val base = e.message?.trim().orEmpty()
        return when (e.kind) {
            AiException.Kind.AUTH ->
                "$name rejected the request${if (base.isNotEmpty()) " ($base)" else ""}. " +
                    "Check the key or your account balance in Settings, or switch to $other."
            AiException.Kind.RATE_LIMIT ->
                "$name is rate-limiting requests. Wait a moment and try again."
            AiException.Kind.NETWORK ->
                "Couldn't reach $name — check your connection and try again."
            AiException.Kind.PAYLOAD_TOO_LARGE ->
                "That input is too large for $name in one go. Split it into smaller parts."
            AiException.Kind.TRANSIENT ->
                "$name is temporarily unavailable. Try again shortly."
            AiException.Kind.EMPTY ->
                "$name returned nothing usable. Try rephrasing, or switch to $other."
            else ->
                base.ifEmpty { "$name couldn't complete that request." }
        }
    }

    // ---- JSON extraction / repair ----

    private fun cleanAndExtractJson(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            val firstNewLine = text.indexOf('\n')
            if (firstNewLine != -1) text = text.substring(firstNewLine).trim()
            if (text.endsWith("```")) text = text.substring(0, text.length - 3).trim()
        }
        val firstBrace = text.indexOf('{')
        val firstBracket = text.indexOf('[')
        if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            val lastBrace = text.lastIndexOf('}')
            if (lastBrace > firstBrace) return text.substring(firstBrace, lastBrace + 1)
        } else if (firstBracket != -1) {
            val lastBracket = text.lastIndexOf(']')
            if (lastBracket > firstBracket) return text.substring(firstBracket, lastBracket + 1)
        }
        return text
    }

    private fun robustParseJsonDeck(jsonText: String): ParsedDeck {
        val trimmed = jsonText.trim()
        runCatching {
            moshi.adapter(ParsedDeck::class.java).fromJson(trimmed)?.takeIf { it.cards.isNotEmpty() }
        }.getOrNull()?.let { return it }

        runCatching {
            val type = Types.newParameterizedType(List::class.java, ParsedCard::class.java)
            moshi.adapter<List<ParsedCard>>(type).fromJson(trimmed)?.takeIf { it.isNotEmpty() }
        }.getOrNull()?.let { return ParsedDeck(cards = it) }

        throw AiException("The AI response was not valid flashcard JSON.", AiException.Kind.EMPTY)
    }

    private fun isYouTubeUrl(text: String): Boolean =
        Regex("""(youtube\.com/(watch|shorts)|youtu\.be/|m\.youtube\.com)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)

    private fun looksLikeUrlFragment(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("http", true) || t.startsWith("//") ||
            t.equals("http", true) || t.equals("https", true) ||
            t.contains("youtu.be") || t.contains("youtube.com")
    }

    private fun filterOutUrlEchoCards(cards: List<ParsedCard>): List<ParsedCard> =
        cards.filterNot { looksLikeUrlFragment(it.front) || looksLikeUrlFragment(it.back) }

    // ---- PDF chunk parsing with recursive subdivision ----

    private suspend fun processPagesChunkRecursive(
        pages: List<String>,
        chunkLabel: String,
        provider: AiProvider,
        topicHint: String,
        densityInstruction: String,
        maxAttempts: Int = 3,
    ): ParsedDeck {
        var attempts = 0
        var lastError: Exception? = null
        var shouldSubdivide = false

        while (attempts < maxAttempts) {
            attempts++
            setImportMessage(
                if (attempts > 1) "Parsing chunk $chunkLabel (${pages.size} pgs)… attempt $attempts/$maxAttempts"
                else "Parsing chunk $chunkLabel (${pages.size} pgs)…"
            )
            try {
                val chunkText = pages.joinToString("\n\n")
                val system = Prompts.importSystem(_nativeLanguage.value, _targetLanguage.value, densityInstruction, chunkLabel)
                val user = Prompts.importUser("Document text chunk:\n$chunkText", topicHint, densityInstruction)
                val responseText = provider.generateStructured(system, user)
                return robustParseJsonDeck(cleanAndExtractJson(responseText))
            } catch (e: Exception) {
                lastError = e
                Log.e("StudyViewModel", "chunk $chunkLabel attempt $attempts/$maxAttempts failed", e)
                val kind = (e as? AiException)?.kind

                if (kind == AiException.Kind.AUTH || kind == AiException.Kind.BAD_REQUEST) throw e

                if (kind == AiException.Kind.PAYLOAD_TOO_LARGE) {
                    val canSplit = pages.size > 1 ||
                        (pages.size == 1 && pages.first().split("\n\n").count { it.isNotBlank() } > 1)
                    if (canSplit) { shouldSubdivide = true; break }
                    throw AiException("A single page is too dense for the model.", AiException.Kind.PAYLOAD_TOO_LARGE)
                }

                if (attempts < maxAttempts) {
                    val backoff = when (kind) {
                        AiException.Kind.RATE_LIMIT ->
                            ((e as AiException).retryAfterMs ?: (10_000L * (1 shl (attempts - 1)))) + 1000L
                        AiException.Kind.TRANSIENT, AiException.Kind.NETWORK ->
                            ((e as? AiException)?.retryAfterMs ?: (5_000L * attempts)) + 1000L
                        else -> 2_000L * attempts
                    }
                    setImportMessage("Retrying chunk $chunkLabel in ${backoff / 1000}s (attempt $attempts/$maxAttempts)…")
                    delay(backoff)
                }
            }
        }

        if (shouldSubdivide) {
            setImportMessage("Content too dense — splitting into smaller sections…")
            delay(600)
            val (left, right) = if (pages.size > 1) {
                val mid = pages.size / 2
                pages.subList(0, mid) to pages.subList(mid, pages.size)
            } else {
                val paras = pages.first().split("\n\n").filter { it.isNotBlank() }
                val mid = paras.size / 2
                listOf(paras.subList(0, mid).joinToString("\n\n")) to listOf(paras.subList(mid, paras.size).joinToString("\n\n"))
            }
            val l = processPagesChunkRecursive(left, "$chunkLabel·A", provider, topicHint, densityInstruction, maxAttempts)
            val r = processPagesChunkRecursive(right, "$chunkLabel·B", provider, topicHint, densityInstruction, maxAttempts)
            return ParsedDeck(
                deckName = l.deckName.ifBlank { r.deckName },
                sourceLanguage = l.sourceLanguage.ifBlank { r.sourceLanguage },
                targetLanguage = l.targetLanguage.ifBlank { r.targetLanguage },
                cards = l.cards + r.cards,
            )
        }
        throw Exception(lastError?.message ?: "Chunk $chunkLabel failed all attempts.")
    }

    // ---- Gamification (interim SharedPreferences via SettingsStore) ----

    fun hasSeeded(): Boolean = settings.hasSeeded
    fun getStreak(): Int = settings.streak
    fun getXp(): Int = settings.xp

    private fun recordStudyActivity(isCorrect: Boolean) {
        settings.xp += if (isCorrect) 15 else 5
        val today = dateString(0)
        val last = settings.lastStudyDate
        when {
            last.isEmpty() -> { settings.streak = 1; settings.lastStudyDate = today }
            last == today -> Unit
            last == dateString(-1) -> { settings.streak += 1; settings.lastStudyDate = today }
            else -> { settings.streak = 1; settings.lastStudyDate = today }
        }
    }

    private fun dateString(dayOffset: Int): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DATE, dayOffset) }
        return java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(cal.time)
    }

    // ---- init ----

    init {
        val database = AppDatabase.getDatabase(application)
        repository = StudyRepository(database.studyDao())

        allDecks = repository.allDecksWithCardsFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                _isTtsReady.value = true
                tts?.setSpeechRate(_ttsRate.value)
            }
        }

        // Seed the shared Master Vocabulary Pool exactly once (guarded by a
        // persisted flag + name check — no insert-inside-collect race).
        viewModelScope.launch {
            if (!settings.hasSeeded) {
                val exists = repository.allDecksFlowSnapshot().any { it.name == MASTER_POOL_NAME }
                if (!exists) {
                    repository.insertDeck(Deck(name = MASTER_POOL_NAME, sourceLanguage = "Multiple", targetLanguage = "English"))
                }
                settings.hasSeeded = true
            }
        }

        // Restore an in-progress study session after process death. The
        // reordered queue and per-session counters are ephemeral by design —
        // we just reopen the same deck with a fresh queue.
        savedState.get<Int>(KEY_ACTIVE_DECK)?.let { deckId ->
            viewModelScope.launch {
                repository.getDeckById(deckId)?.let { selectDeck(it) }
                    ?: savedState.remove<Int>(KEY_ACTIVE_DECK)
            }
        }
    }

    // ---- deck selection / session ----

    private val srsComparator = compareBy<Flashcard> { it.nextReview }.thenBy { it.repetitions }.thenBy { it.easeFactor }

    fun selectDeck(deck: Deck) {
        savedState[KEY_ACTIVE_DECK] = deck.id
        _currentDeck.value = deck
        _currentCardIndex.value = 0
        _isCardFlipped.value = false
        _sessionCorrectCount.value = 0
        _sessionIncorrectCount.value = 0
        _consecutiveIncorrectStreak.value = 0
        _flippedCardIds.value = emptySet()
        _cardModes.value = emptyMap()

        viewModelScope.launch {
            _currentFlashcards.value = repository.getFlashcardsForDeck(deck.id).sortedWith(srsComparator)
        }
        viewModelScope.launch {
            repository.getChatLogsForDeckFlow(deck.id).collectLatest { _chatLogs.value = it }
        }
    }

    fun clearActiveDeck() {
        savedState.remove<Int>(KEY_ACTIVE_DECK)
        _currentDeck.value = null
        _currentFlashcards.value = emptyList()
        _chatLogs.value = emptyList()
        _flippedCardIds.value = emptySet()
    }

    fun flipCard() {
        _isCardFlipped.value = !_isCardFlipped.value
        _currentFlashcards.value.getOrNull(_currentCardIndex.value)?.let {
            _flippedCardIds.value = _flippedCardIds.value + it.id
        }
    }

    fun recordCardAnswer(isCorrect: Boolean) =
        recordCardConfidence(if (isCorrect) ConfidenceLevel.MEDIUM else ConfidenceLevel.LOW)

    fun restartSession() {
        _currentCardIndex.value = 0
        _isCardFlipped.value = false
        _sessionCorrectCount.value = 0
        _sessionIncorrectCount.value = 0
        _consecutiveIncorrectStreak.value = 0
        _flippedCardIds.value = emptySet()
        _currentDeck.value?.let { deck ->
            viewModelScope.launch {
                _currentFlashcards.value = repository.getFlashcardsForDeck(deck.id).sortedWith(srsComparator)
            }
        }
    }

    fun recordCardConfidence(confidence: ConfidenceLevel) {
        val cards = _currentFlashcards.value
        val index = _currentCardIndex.value
        if (index >= cards.size) return
        val card = cards[index]
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

        val grade = when (confidence) {
            ConfidenceLevel.LOW -> SrsScheduler.Grade.AGAIN
            ConfidenceLevel.MEDIUM -> SrsScheduler.Grade.GOOD
            ConfidenceLevel.HIGH -> SrsScheduler.Grade.EASY
        }
        val srs = SrsScheduler.next(
            SrsScheduler.State(card.repetitions, card.interval, card.easeFactor), grade,
        )
        val updated = card.copy(
            repetitions = srs.repetitions,
            interval = srs.intervalDays,
            easeFactor = srs.easeFactor,
            nextReview = System.currentTimeMillis() + srs.intervalDays.toLong() * 24 * 60 * 60 * 1000L,
        )

        viewModelScope.launch {
            repository.updateFlashcard(updated)
            recordStudyActivity(isCorrect)
        }

        // Re-order the in-session queue.
        val remaining = _currentFlashcards.value.toMutableList().also { it[index] = updated }
        val current = remaining.removeAt(index)
        val sorted = remaining.sortedWith(srsComparator).toMutableList()
        val pos = when (confidence) {
            ConfidenceLevel.LOW -> 2.coerceAtMost(sorted.size)
            ConfidenceLevel.MEDIUM -> (sorted.size / 2)
            ConfidenceLevel.HIGH -> sorted.size
        }
        sorted.add(pos, current)
        _currentFlashcards.value = sorted
        _isCardFlipped.value = false
        _currentCardIndex.value = if (sorted.isEmpty()) 0 else index % sorted.size
    }

    // ---- TTS ----

    private fun cleanTextForTts(text: String, locale: Locale): String {
        var s = text
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\[[^]]*\\]"), "")
            .replace(Regex("\\{[^}]*\\}"), "")
        if (s.contains("/")) s = s.substringBefore("/")
        if (s.contains(":")) s = s.substringBefore(":")
        val nonLatin = locale.language in setOf("fa", "ar", "ja", "zh", "ko", "hi", "he", "el", "ru", "uk", "bg")
        if (nonLatin) s = s.replace(Regex("[a-zA-Z]"), "")
        return s.trim()
    }

    fun speak(text: String, languageCode: String? = null) {
        if (!_isTtsReady.value) return
        viewModelScope.launch(Dispatchers.Main) {
            val locale = if (!languageCode.isNullOrBlank()) Locale.forLanguageTag(languageCode)
            else localeFromLanguageName(_currentDeck.value?.sourceLanguage ?: "en")
            runCatching {
                val cleaned = cleanTextForTts(text, locale)
                if (cleaned.isNotBlank()) {
                    tts?.language = locale
                    tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "af-utterance")
                }
            }.onFailure { Log.e("StudyViewModel", "TTS error", it) }
        }
    }

    /** Non-deprecated replacement for `Locale(lang)` / `Locale(lang, region)`. */
    private fun locale(language: String, region: String? = null): Locale =
        Locale.Builder().setLanguage(language).apply { region?.let { setRegion(it) } }.build()

    private fun localeFromLanguageName(name: String): Locale {
        val t = name.lowercase(Locale.ROOT).trim()
        if (t.length == 2) return locale(t)
        return when (t) {
            "persian", "farsi" -> locale("fa")
            "spanish" -> locale("es", "ES")
            "french" -> Locale.FRENCH
            "german" -> Locale.GERMAN
            "swedish" -> locale("sv", "SE")
            "italian" -> Locale.ITALIAN
            "japanese" -> Locale.JAPANESE
            "korean" -> Locale.KOREAN
            "chinese" -> Locale.CHINESE
            "english" -> Locale.ENGLISH
            "arabic" -> locale("ar")
            "portuguese" -> locale("pt", "PT")
            "russian" -> locale("ru")
            "hindi" -> locale("hi")
            "turkish" -> locale("tr")
            "dutch" -> locale("nl", "NL")
            else -> Locale.getAvailableLocales().firstOrNull {
                it.displayLanguage.lowercase(Locale.ROOT) == t || it.language.lowercase(Locale.ROOT) == t
            } ?: Locale.getDefault()
        }
    }

    fun setTtsRate(rate: Float) {
        _ttsRate.value = rate
        settings.ttsRate = rate
        tts?.setSpeechRate(rate)
    }

    fun toggleAutoPlayTts(enabled: Boolean) {
        _isAutoPlayTtsEnabled.value = enabled
        settings.ttsAutoPlay = enabled
    }

    fun togglePlayMode(active: Boolean) { _isPlayModeActive.value = active }

    fun getLearningModeForCard(cardId: Int): LearningMode =
        if (!_isPlayModeActive.value) LearningMode.Flashcard
        else _cardModes.value[cardId] ?: LearningMode.Flashcard

    fun setLearningModeForCard(cardId: Int, mode: LearningMode) {
        _cardModes.value = _cardModes.value.toMutableMap().apply { put(cardId, mode) }
    }

    /** Distractors are drawn only from the same deck — no bundled English list. */
    fun getMultipleChoiceOptions(card: Flashcard): List<String> {
        val others = _currentFlashcards.value
            .filter { it.id != card.id && it.back.isNotBlank() && it.back != card.back }
            .map { it.back }.distinct().shuffled().take(3)
        return (others + card.back).distinct().shuffled()
    }

    fun deleteCurrentDeck() {
        val deck = _currentDeck.value ?: return
        if (deck.name == MASTER_POOL_NAME) return
        viewModelScope.launch {
            repository.deleteDeck(deck)
            clearActiveDeck()
        }
    }

    // ---- export ----

    enum class ExportFormat(val extension: String, val mime: String) {
        JSON("json", "application/json"),
        CSV("csv", "text/csv"),
    }

    /** Suggested file name for the active deck's export, without extension. */
    fun exportFileName(): String =
        DeckExporter.safeFileName(_currentDeck.value?.name ?: "deck")

    /** Serialises the active deck. Returns null if no deck is open or it's empty. */
    suspend fun buildDeckExport(format: ExportFormat): String? {
        val deck = _currentDeck.value ?: return null
        val cards = repository.getFlashcardsForDeck(deck.id)
        if (cards.isEmpty()) return null
        return when (format) {
            ExportFormat.JSON -> DeckExporter.toJson(deck, cards)
            ExportFormat.CSV -> DeckExporter.toCsv(cards)
        }
    }

    // ---- save / merge ----

    private suspend fun saveOrMergeCards(
        targetDeckId: Int?,
        deckName: String,
        sourceLanguage: String?,
        targetLanguage: String?,
        parsedCards: List<ParsedCard>,
    ): String {
        fun norm(s: String) = s.lowercase().trim()
            .replace(Regex("[\\p{Punct}]"), "").replace(Regex("\\s+"), " ")

        val existingDeck = targetDeckId?.let { repository.getDeckById(it) }
        if (existingDeck != null) {
            val existingCards = repository.getFlashcardsForDeck(existingDeck.id)
            for (incoming in parsedCards) {
                val match = existingCards.find { norm(it.front) == norm(incoming.front) }
                if (match == null) {
                    repository.insertFlashcard(Flashcard(deckId = existingDeck.id, front = incoming.front, back = incoming.back, notes = incoming.notes))
                } else {
                    val currentNotes = match.notes ?: ""
                    val enrichment = buildString {
                        if (incoming.back.isNotBlank() && !norm(match.back).contains(norm(incoming.back)))
                            append("\n• Alt: ${incoming.back}")
                        val n = incoming.notes ?: ""
                        if (n.isNotBlank() && !currentNotes.contains(n)) append("\n• $n")
                    }.trim()
                    if (enrichment.isNotEmpty()) {
                        repository.updateFlashcard(match.copy(
                            notes = if (currentNotes.isBlank()) enrichment else "$currentNotes\n$enrichment"
                        ))
                    }
                }
            }
            return existingDeck.name
        }

        val newDeckId = repository.insertDeck(Deck(name = deckName, sourceLanguage = sourceLanguage, targetLanguage = targetLanguage)).toInt()
        repository.insertFlashcards(parsedCards.map { Flashcard(deckId = newDeckId, front = it.front, back = it.back, notes = it.notes) })
        return deckName
    }

    // ---- honest offline "word: meaning" list parsing ----

    private fun parseRawTextLocally(rawText: String, topicHint: String): ParsedDeck? {
        val lines = rawText.lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
        if (lines.isEmpty()) return null

        var colon = 0; var hyphen = 0; var eq = 0
        for (l in lines) when {
            l.contains(":") -> colon++
            l.contains(" - ") || l.contains(" – ") -> hyphen++
            l.contains("=") -> eq++
        }
        val sep = when {
            colon >= 1 -> ":"
            hyphen >= 1 -> "-"
            eq >= 1 -> "="
            else -> return null
        }
        val cards = lines.mapNotNull { line ->
            val parts = line.split(sep, limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val front = parts[0].trim().removePrefix("-").removePrefix("*").trim()
            val back = parts[1].trim()
            if (front.isEmpty() || back.isEmpty()) null else ParsedCard(front, back, "")
        }
        if (cards.isEmpty()) return null
        return ParsedDeck(
            deckName = topicHint.ifBlank { "📋 Imported list" },
            sourceLanguage = "Auto",
            targetLanguage = "English",
            cards = cards,
        )
    }

    // ---- file helpers ----

    private fun copyUriToCacheFile(context: Context, uri: android.net.Uri): File? = runCatching {
        if (uri.scheme == "file") {
            uri.path?.let { File(it) }?.takeIf { it.isFile }?.let { return it }
        }
        val ext = fileExtension(context, uri)
        val out = File(context.cacheDir, "import_${System.currentTimeMillis()}$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out.takeIf { it.length() > 0 }
    }.onFailure { Log.e("StudyViewModel", "copyUriToCacheFile failed", it) }.getOrNull()

    private fun fileExtension(context: Context, uri: android.net.Uri): String {
        val mime = context.contentResolver.getType(uri).orEmpty()
        return when {
            mime.contains("pdf", true) -> ".pdf"
            mime.contains("csv", true) -> ".csv"
            mime.contains("text", true) || mime.contains("plain", true) -> ".txt"
            uri.path?.substringAfterLast('.', "")?.isNotEmpty() == true -> ".${uri.path!!.substringAfterLast('.')}"
            else -> ""
        }
    }

    // ---- IMPORT ----

    fun importDeckFromRawText(
        rawText: String,
        topicHint: String = "",
        fileUri: android.net.Uri? = null,
        mergeDeckId: Int? = null,
        density: String = "Balanced",
    ) {
        if (rawText.isBlank() && fileUri == null) return

        if (fileUri == null && isYouTubeUrl(rawText.trim()) && topicHint.isBlank()) {
            _importState.value = ImportState.Error(
                "A YouTube link alone isn't enough — AdaptiveFlow can't read the video's captions. " +
                    "Add a Focus/Topic hint (e.g. \"Swedish hobbies vocabulary\") describing what it covers."
            )
            return
        }

        _importState.value = ImportState.Loading("Preparing…")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val input = rawText.trim()

                // A .json / .txt file dropped in the picker that is itself an
                // exported deck: read it here so it re-imports offline like paste.
                val fileText: String? = if (fileUri != null) {
                    val ctx = getApplication<Application>()
                    val mime = ctx.contentResolver.getType(fileUri).orEmpty()
                    val looksBinary = mime.contains("pdf", true) || fileUri.toString().endsWith(".pdf", true)
                    if (looksBinary) null else runCatching {
                        ctx.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { it.readText() }?.take(500_000)
                    }.getOrNull()
                } else null

                // 1. A ready-made JSON deck — pasted, or a loaded export file.
                val jsonSource = (fileText ?: input).takeIf { it.isNotBlank() }
                if (jsonSource != null) {
                    val cleaned = jsonSource.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    runCatching { moshi.adapter(ParsedDeck::class.java).fromJson(cleaned) }
                        .getOrNull()?.takeIf { it.cards.isNotEmpty() }?.let { deck ->
                            setImportMessage("JSON deck detected — importing offline…")
                            val name = saveOrMergeCards(mergeDeckId, deck.deckName.ifBlank { "📋 Imported deck" }, deck.sourceLanguage, deck.targetLanguage, deck.cards)
                            _importState.value = ImportState.Success(name)
                            return@launch
                        }
                }

                // 2. Honest offline: a plain "word: meaning" list.
                if (input.isNotEmpty() && fileUri == null && !isYouTubeUrl(input)) {
                    parseRawTextLocally(input, topicHint)?.let { local ->
                        setImportMessage("Importing list offline…")
                        val name = saveOrMergeCards(mergeDeckId, local.deckName, local.sourceLanguage, local.targetLanguage, local.cards)
                        _importState.value = ImportState.Success(name)
                        return@launch
                    }
                }

                // 3. Everything else needs a real AI provider.
                val provider = activeProvider()
                if (provider == null) {
                    _importState.value = ImportState.Error(noKeyMessage())
                    return@launch
                }

                var attachedText = rawText
                var pdfPageTexts: List<String>? = null
                var pdfBytes: ByteArray? = null

                if (fileUri != null) {
                    val ctx = getApplication<Application>()
                    val tempFile = copyUriToCacheFile(ctx, fileUri)
                        ?: throw Exception("Could not read the selected file.")
                    try {
                        val mime = ctx.contentResolver.getType(fileUri).orEmpty()
                        val isPdf = mime.contains("pdf", true) || fileUri.toString().endsWith(".pdf", true)
                        if (isPdf) {
                            val pages = PdfTextExtractor.extractPages(tempFile) { p, total ->
                                setImportMessage("Extracting text from PDF page $p of $total…")
                            }
                            if (pages.sumOf { it.length } > 100) {
                                pdfPageTexts = pages
                            } else if (provider.supportsPdfBytes) {
                                pdfBytes = tempFile.readBytes().takeIf { it.isNotEmpty() }
                                    ?: throw Exception("The PDF is empty or unreadable.")
                            } else {
                                throw Exception(
                                    "This looks like a scanned PDF with no selectable text. " +
                                        "${_aiProviderId.value.displayName} can't read those — switch to Gemini in Settings, or use a text-based PDF."
                                )
                            }
                        } else {
                            val text = (fileText ?: tempFile.inputStream().bufferedReader().use { it.readText() }).take(150_000)
                            if (text.isBlank()) throw Exception("The selected text file is empty.")
                            attachedText = if (attachedText.isBlank()) text else "$attachedText\n\n--- Attached file ---\n$text"
                        }
                    } finally {
                        runCatching { tempFile.delete() }
                    }
                }

                val densityInstruction = densityInstruction(density, chunked = pdfPageTexts != null)

                if (pdfPageTexts != null) {
                    val chunkSize = when {
                        pdfPageTexts.size <= 5 -> 1
                        pdfPageTexts.size <= 15 -> 3
                        pdfPageTexts.size <= 30 -> 5
                        pdfPageTexts.size <= 60 -> 8
                        else -> 12
                    }
                    val chunks = pdfPageTexts.chunked(chunkSize)
                    val cards = mutableListOf<ParsedCard>()
                    var deckName = ""
                    var srcLang = ""
                    var tgtLang = ""
                    chunks.forEachIndexed { i, chunk ->
                        val startPage = i * chunkSize + 1
                        val endPage = minOf((i + 1) * chunkSize, pdfPageTexts!!.size)
                        val result = processPagesChunkRecursive(
                            chunk, "${i + 1}/${chunks.size} (p$startPage–$endPage)", provider, topicHint, densityInstruction
                        )
                        if (deckName.isBlank()) {
                            deckName = result.deckName.takeIf { it.isNotBlank() && !it.contains("PDF", true) }
                                ?.let { if (it.startsWith("📄")) it else "📄 $it" }
                                ?: "📄 " + topicHint.ifBlank { "Document" }
                            srcLang = result.sourceLanguage
                            tgtLang = result.targetLanguage
                        }
                        cards += result.cards
                    }
                    if (cards.isEmpty()) throw Exception("No flashcards could be extracted from the PDF.")
                    val name = saveOrMergeCards(mergeDeckId, deckName, srcLang, tgtLang, filterOutUrlEchoCards(cards))
                    _importState.value = ImportState.Success(name)
                    return@launch
                }

                // Single-shot: text / YouTube / scanned-PDF-bytes
                setImportMessage("Analysing and building your deck…")
                val system = Prompts.importSystem(_nativeLanguage.value, _targetLanguage.value, densityInstruction)
                val user = Prompts.importUser(attachedText, topicHint, densityInstruction)
                val responseText = provider.generateStructured(system, user, pdfBytes = pdfBytes)
                val parsed = robustParseJsonDeck(cleanAndExtractJson(responseText))
                val realCards = filterOutUrlEchoCards(parsed.cards)
                if (realCards.isEmpty()) {
                    _importState.value = ImportState.Error(
                        if (isYouTubeUrl(rawText.trim()))
                            "Couldn't build a deck from that video — try a more specific Focus/Topic hint."
                        else "Couldn't extract any flashcards. Check your input has real content."
                    )
                    return@launch
                }
                val name = saveOrMergeCards(
                    mergeDeckId,
                    parsed.deckName.ifBlank { "📄 " + topicHint.ifBlank { "Import" } },
                    parsed.sourceLanguage, parsed.targetLanguage, realCards,
                )
                _importState.value = ImportState.Success(name)
            } catch (e: AiException) {
                Log.e("StudyViewModel", "import AI error (${e.kind})", e)
                _importState.value = ImportState.Error(friendlyAiError(e))
            } catch (e: Exception) {
                Log.e("StudyViewModel", "import failed", e)
                _importState.value = ImportState.Error(e.message ?: "Import failed.")
            }
        }
    }

    private fun densityInstruction(density: String, chunked: Boolean): String = when (density) {
        "Focused" -> if (chunked) "Extract 8–10 high-yield cards from this chunk."
        else "For short input generate 10–15 cards; for long input 15–20 premium cards."
        "Exhaustive" -> "Extract every unique term, phrase, definition and formula. Do not cap the count."
        else -> if (chunked) "Extract all unique vocabulary and key definitions — aim for 15–25 cards, don't truncate."
        else "For short input generate 15–25 cards; for long input extract everything found."
    }

    fun resetImportState() { _importState.value = ImportState.Idle }

    // ---- TUTOR ----

    fun sendTutorMessage(userText: String) {
        val deck = _currentDeck.value ?: return
        if (userText.isBlank()) return
        val cards = _currentFlashcards.value
        val activeCard = cards.getOrNull(_currentCardIndex.value)

        viewModelScope.launch { repository.insertChatLog(ChatLog(deckId = deck.id, sender = "user", message = userText)) }
        _isAiLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val provider = activeProvider()
            if (provider == null) {
                repository.insertChatLog(ChatLog(deckId = deck.id, sender = "ai", message = noKeyMessage()))
                withContext(Dispatchers.Main) { _isAiLoading.value = false }
                return@launch
            }
            try {
                val history = repository.getChatLogsForDeck(deck.id).takeLast(10).map {
                    AiTurn(if (it.sender == "user") AiTurn.Role.USER else AiTurn.Role.MODEL, it.message)
                }
                val system = Prompts.tutorSystem(
                    _nativeLanguage.value, _targetLanguage.value,
                    deck.sourceLanguage, deck.targetLanguage,
                    activeCard?.front, activeCard?.back, activeCard?.notes,
                    _sessionCorrectCount.value, _sessionIncorrectCount.value, _consecutiveIncorrectStreak.value,
                )
                val reply = provider.chat(system, history, userText)
                repository.insertChatLog(ChatLog(deckId = deck.id, flashcardId = activeCard?.id, sender = "ai", message = reply))
            } catch (e: AiException) {
                Log.e("StudyViewModel", "tutor error (${e.kind})", e)
                repository.insertChatLog(ChatLog(deckId = deck.id, sender = "ai", message = friendlyAiError(e)))
            } catch (e: Exception) {
                Log.e("StudyViewModel", "tutor error", e)
                repository.insertChatLog(ChatLog(deckId = deck.id, sender = "ai", message = "Lost connection to the tutor — check your network and try again."))
            } finally {
                withContext(Dispatchers.Main) { _isAiLoading.value = false }
            }
        }
    }

    fun clearChatHistory() {
        val deck = _currentDeck.value ?: return
        viewModelScope.launch { repository.clearChatLogsForDeck(deck.id) }
    }

    // ---- import status helpers ----

    /** StateFlow writes are thread-safe; no Main-dispatcher hop needed. */
    private fun setImportMessage(msg: String) { _importState.value = ImportState.Loading(msg) }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
