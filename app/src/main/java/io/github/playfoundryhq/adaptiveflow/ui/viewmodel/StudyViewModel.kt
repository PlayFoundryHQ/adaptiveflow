package io.github.playfoundryhq.adaptiveflow.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.playfoundryhq.adaptiveflow.AdaptiveFlowApp
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.playfoundryhq.adaptiveflow.ai.Prompts
import io.github.playfoundryhq.adaptiveflow.data.ai.AiException
import io.github.playfoundryhq.adaptiveflow.data.ai.AiProvider
import io.github.playfoundryhq.adaptiveflow.data.ai.AiProviderId
import io.github.playfoundryhq.adaptiveflow.data.ai.AiTurn
import io.github.playfoundryhq.adaptiveflow.data.backup.DeckExporter
import io.github.playfoundryhq.adaptiveflow.data.model.ChatLog
import io.github.playfoundryhq.adaptiveflow.data.model.Deck
import io.github.playfoundryhq.adaptiveflow.data.model.DeckWithCards
import io.github.playfoundryhq.adaptiveflow.data.model.Flashcard
import io.github.playfoundryhq.adaptiveflow.data.model.Progress
import io.github.playfoundryhq.adaptiveflow.data.pdf.PdfTextExtractor
import io.github.playfoundryhq.adaptiveflow.di.AppContainer
import io.github.playfoundryhq.adaptiveflow.domain.DeckMerge
import io.github.playfoundryhq.adaptiveflow.domain.ImportPipeline
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
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val repository = container.repository
    private val settings = container.settings
    private val aiClient = container.aiClient
    private val tts = container.tts
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val importPipeline = ImportPipeline(
        appContext = getApplication(),
        repository = repository,
        moshi = moshi,
        scope = viewModelScope,
        activeProvider = ::activeProvider,
        providerDisplayName = { _aiProviderId.value.displayName },
        noKeyMessage = ::noKeyMessage,
        friendlyError = ::friendlyAiError,
        nativeLanguage = { _nativeLanguage.value },
        targetLanguage = { _targetLanguage.value },
    )
    val importState = importPipeline.state

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


    // ---- Session performance ----
    private val _sessionCorrectCount = MutableStateFlow(0)
    val sessionCorrectCount: StateFlow<Int> = _sessionCorrectCount.asStateFlow()

    private val _sessionIncorrectCount = MutableStateFlow(0)
    val sessionIncorrectCount: StateFlow<Int> = _sessionIncorrectCount.asStateFlow()

    private val _consecutiveIncorrectStreak = MutableStateFlow(0)
    val consecutiveIncorrectStreak: StateFlow<Int> = _consecutiveIncorrectStreak.asStateFlow()

    private val _flippedCardIds = MutableStateFlow<Set<Long>>(emptySet())
    val flippedCardIds: StateFlow<Set<Long>> = _flippedCardIds.asStateFlow()

    // ---- TTS (owned by the app-scoped TtsController) ----
    val isTtsReady: StateFlow<Boolean> = tts.isReady
    val ttsRate: StateFlow<Float> = tts.rate
    val isAutoPlayTtsEnabled: StateFlow<Boolean> = tts.autoPlay
    val isSpeaking: StateFlow<Boolean> = tts.isSpeaking
    val ttsMissingLanguage: StateFlow<java.util.Locale?> = tts.missingLanguage

    // ---- Play mode ----
    private val _isPlayModeActive = MutableStateFlow(false)
    val isPlayModeActive: StateFlow<Boolean> = _isPlayModeActive.asStateFlow()

    enum class LearningMode { Flashcard, Quiz }
    enum class ConfidenceLevel { LOW, MEDIUM, HIGH }

    private val _cardModes = MutableStateFlow<Map<Long, LearningMode>>(emptyMap())
    val cardModes: StateFlow<Map<Long, LearningMode>> = _cardModes.asStateFlow()

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


    val progress: StateFlow<Progress> = repository.progressFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Progress())

    private suspend fun recordStudyActivity(isCorrect: Boolean) {
        val today = dateString(0)
        val yesterday = dateString(-1)
        repository.updateProgress { p ->
            val (streak, date) = when {
                p.lastStudyDate.isEmpty() -> 1 to today
                p.lastStudyDate == today -> p.streak to today
                p.lastStudyDate == yesterday -> (p.streak + 1) to today
                else -> 1 to today
            }
            p.copy(
                xp = p.xp + if (isCorrect) 15 else 5,
                streak = streak,
                lastStudyDate = date,
            )
        }
    }

    private fun dateString(dayOffset: Int): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DATE, dayOffset) }
        return java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(cal.time)
    }

    // ---- init ----

    init {
        allDecks = repository.allDecksWithCardsFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        viewModelScope.launch {
            // One-shot: lift pre-v2 gamification counters out of SharedPreferences.
            if (!settings.progressMigratedToRoom) {
                repository.updateProgress {
                    it.copy(
                        xp = maxOf(it.xp, settings.xp),
                        streak = maxOf(it.streak, settings.streak),
                        lastStudyDate = it.lastStudyDate.ifEmpty { settings.lastStudyDate },
                        seeded = it.seeded || settings.hasSeeded,
                    )
                }
                settings.progressMigratedToRoom = true
            }

            // Seed the shared Master Vocabulary Pool exactly once.
            if (!repository.getProgress().seeded) {
                val exists = repository.allDecksFlowSnapshot().any { it.name == MASTER_POOL_NAME }
                if (!exists) {
                    repository.insertDeck(Deck(name = MASTER_POOL_NAME, sourceLanguage = "Multiple", targetLanguage = "English"))
                }
                repository.updateProgress { it.copy(seeded = true) }
            }
        }

        // Restore an in-progress study session after process death. The
        // reordered queue and per-session counters are ephemeral by design —
        // we just reopen the same deck with a fresh queue.
        savedState.get<Long>(KEY_ACTIVE_DECK)?.let { deckId ->
            viewModelScope.launch {
                repository.getDeckById(deckId)?.let { selectDeck(it) }
                    ?: savedState.remove<Long>(KEY_ACTIVE_DECK)
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
        savedState.remove<Long>(KEY_ACTIVE_DECK)
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

    // ---- TTS (delegates to the app-scoped TtsController) ----

    /** Bind the TTS engine — call when a study session opens, not at startup. */
    fun prepareTts() = tts.prepare()

    fun speak(text: String, languageCode: String? = null) =
        tts.speak(text, languageCode, _currentDeck.value?.sourceLanguage)

    fun setTtsRate(rate: Float) = tts.setRate(rate)

    fun toggleAutoPlayTts(enabled: Boolean) = tts.setAutoPlay(enabled)

    fun stopSpeaking() = tts.stop()

    fun togglePlayMode(active: Boolean) { _isPlayModeActive.value = active }

    fun getLearningModeForCard(cardId: Long): LearningMode =
        if (!_isPlayModeActive.value) LearningMode.Flashcard
        else _cardModes.value[cardId] ?: LearningMode.Flashcard

    fun setLearningModeForCard(cardId: Long, mode: LearningMode) {
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

    // ---- import (delegates to ImportPipeline) ----

    fun importDeckFromRawText(
        rawText: String,
        topicHint: String = "",
        fileUri: android.net.Uri? = null,
        mergeDeckId: Long? = null,
        density: String = "Balanced",
    ) = importPipeline.import(rawText, topicHint, fileUri, mergeDeckId, density)

    fun confirmMerge() = importPipeline.confirmMerge()
    fun cancelMerge() = importPipeline.cancelMerge()
    fun undoLastImport() = importPipeline.undoLastImport()
    fun resetImportState() = importPipeline.reset()

    override fun onCleared() {
        super.onCleared()
        tts.stop() // the controller is app-scoped; just stop any in-flight utterance
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AdaptiveFlowApp
                StudyViewModel(app, createSavedStateHandle(), app.container)
            }
        }
    }
}
