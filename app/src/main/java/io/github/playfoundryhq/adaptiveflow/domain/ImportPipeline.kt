package io.github.playfoundryhq.adaptiveflow.domain

import android.content.Context
import android.net.Uri
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.github.playfoundryhq.adaptiveflow.ai.Prompts
import io.github.playfoundryhq.adaptiveflow.data.ai.AiException
import io.github.playfoundryhq.adaptiveflow.data.ai.AiProvider
import io.github.playfoundryhq.adaptiveflow.data.model.Deck
import io.github.playfoundryhq.adaptiveflow.data.model.Flashcard
import io.github.playfoundryhq.adaptiveflow.data.pdf.PdfTextExtractor
import io.github.playfoundryhq.adaptiveflow.data.repository.StudyRepository
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.DiagnosticLogger as Log
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.ParsedCard
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.ParsedDeck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

const val MASTER_POOL_NAME = "🧠 Master Vocabulary Pool"

/**
 * Everything that turns pasted text / a file / a YouTube link into a deck:
 * offline JSON + "word: meaning" parsing, AI parsing with chunked PDF handling
 * and recursive subdivision, and the staged, undoable merge into an existing
 * deck. Extracted from [StudyViewModel] so that god-file shrinks and this flow
 * is testable in isolation.
 */
class ImportPipeline(
    private val appContext: Context,
    private val repository: StudyRepository,
    private val moshi: Moshi,
    private val scope: CoroutineScope,
    private val activeProvider: () -> AiProvider?,
    private val providerDisplayName: () -> String,
    private val noKeyMessage: () -> String,
    private val friendlyError: (AiException) -> String,
    private val nativeLanguage: () -> String,
    private val targetLanguage: () -> String,
) {

    sealed interface ImportState {
        data object Idle : ImportState
        data class Loading(val message: String = "Working…") : ImportState
        /** A merge is staged and waiting for the user to confirm. */
        data class MergePreview(val plan: MergePlan) : ImportState
        data class Success(val deckName: String, val undoable: Boolean = false) : ImportState
        data class Error(val message: String) : ImportState
    }

    data class MergePlan(
        val targetDeckName: String,
        val newCount: Int,
        val enrichCount: Int,
        val skipCount: Int,
    ) {
        val total get() = newCount + enrichCount + skipCount
    }

    private class PendingMerge(val deck: Deck, val plan: DeckMerge.Plan)
    private class ImportUndo(
        val insertedCardIds: List<Long>,
        val restoredNotes: List<Pair<Long, String?>>,
    )

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private var pendingMerge: PendingMerge? = null
    private var lastUndo: ImportUndo? = null

    private fun setMessage(msg: String) { _state.value = ImportState.Loading(msg) }
    fun reset() { _state.value = ImportState.Idle }

    // ---- entry point ----

    fun import(
        rawText: String,
        topicHint: String = "",
        fileUri: Uri? = null,
        mergeDeckId: Long? = null,
        density: String = "Balanced",
    ) {
        if (rawText.isBlank() && fileUri == null) return

        if (fileUri == null && ImportParsing.isYouTubeUrl(rawText.trim()) && topicHint.isBlank()) {
            _state.value = ImportState.Error(
                "A YouTube link alone isn't enough — AdaptiveFlow can't read the video's captions. " +
                    "Add a Focus/Topic hint (e.g. \"Swedish hobbies vocabulary\") describing what it covers."
            )
            return
        }

        _state.value = ImportState.Loading("Preparing…")
        scope.launch(Dispatchers.IO) {
            try {
                val input = rawText.trim()

                val fileText: String? = if (fileUri != null) {
                    val mime = appContext.contentResolver.getType(fileUri).orEmpty()
                    val looksBinary = mime.contains("pdf", true) || fileUri.toString().endsWith(".pdf", true)
                    if (looksBinary) null else runCatching {
                        appContext.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { it.readText() }?.take(500_000)
                    }.getOrNull()
                } else null

                // 1. A ready-made JSON deck — pasted, or a loaded export file.
                val jsonSource = (fileText ?: input).takeIf { it.isNotBlank() }
                if (jsonSource != null) {
                    val cleaned = jsonSource.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    runCatching { moshi.adapter(ParsedDeck::class.java).fromJson(cleaned) }
                        .getOrNull()?.takeIf { it.cards.isNotEmpty() }?.let { deck ->
                            setMessage("JSON deck detected — importing offline…")
                            _state.value = saveOrMergeCards(mergeDeckId, deck.deckName.ifBlank { "📋 Imported deck" }, deck.sourceLanguage, deck.targetLanguage, deck.cards)
                            return@launch
                        }
                }

                // 2. Honest offline: a plain "word: meaning" list.
                if (input.isNotEmpty() && fileUri == null && !ImportParsing.isYouTubeUrl(input)) {
                    ImportParsing.parseRawTextLocally(input, topicHint)?.let { local ->
                        setMessage("Importing list offline…")
                        _state.value = saveOrMergeCards(mergeDeckId, local.deckName, local.sourceLanguage, local.targetLanguage, local.cards)
                        return@launch
                    }
                }

                // 3. Everything else needs a real AI provider.
                val provider = activeProvider()
                if (provider == null) {
                    _state.value = ImportState.Error(noKeyMessage())
                    return@launch
                }

                var attachedText = rawText
                var pdfPageTexts: List<String>? = null
                var pdfBytes: ByteArray? = null

                if (fileUri != null) {
                    val tempFile = copyUriToCacheFile(fileUri)
                        ?: throw Exception("Could not read the selected file.")
                    try {
                        val mime = appContext.contentResolver.getType(fileUri).orEmpty()
                        val isPdf = mime.contains("pdf", true) || fileUri.toString().endsWith(".pdf", true)
                        if (isPdf) {
                            val pages = PdfTextExtractor.extractPages(tempFile) { p, total ->
                                setMessage("Extracting text from PDF page $p of $total…")
                            }
                            if (pages.sumOf { it.length } > 100) {
                                pdfPageTexts = pages
                            } else if (provider.supportsPdfBytes) {
                                pdfBytes = tempFile.readBytes().takeIf { it.isNotEmpty() }
                                    ?: throw Exception("The PDF is empty or unreadable.")
                            } else {
                                throw Exception(
                                    "This looks like a scanned PDF with no selectable text. " +
                                        "${providerDisplayName()} can't read those — switch to Gemini in Settings, or use a text-based PDF."
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
                    val pages = pdfPageTexts
                    val chunkSize = when {
                        pages.size <= 5 -> 1
                        pages.size <= 15 -> 3
                        pages.size <= 30 -> 5
                        pages.size <= 60 -> 8
                        else -> 12
                    }
                    val chunks = pages.chunked(chunkSize)
                    val cards = mutableListOf<ParsedCard>()
                    var deckName = ""
                    var srcLang = ""
                    var tgtLang = ""
                    chunks.forEachIndexed { i, chunk ->
                        val startPage = i * chunkSize + 1
                        val endPage = minOf((i + 1) * chunkSize, pages.size)
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
                    _state.value = saveOrMergeCards(mergeDeckId, deckName, srcLang, tgtLang, ImportParsing.filterOutUrlEchoCards(cards))
                    return@launch
                }

                setMessage("Analysing and building your deck…")
                val system = Prompts.importSystem(nativeLanguage(), targetLanguage(), densityInstruction)
                val user = Prompts.importUser(attachedText, topicHint, densityInstruction)
                val responseText = provider.generateStructured(system, user, pdfBytes = pdfBytes)
                val parsed = ImportParsing.robustParseJsonDeck(moshi, ImportParsing.cleanAndExtractJson(responseText))
                val realCards = ImportParsing.filterOutUrlEchoCards(parsed.cards)
                if (realCards.isEmpty()) {
                    _state.value = ImportState.Error(
                        if (ImportParsing.isYouTubeUrl(rawText.trim()))
                            "Couldn't build a deck from that video — try a more specific Focus/Topic hint."
                        else "Couldn't extract any flashcards. Check your input has real content."
                    )
                    return@launch
                }
                _state.value = saveOrMergeCards(
                    mergeDeckId,
                    parsed.deckName.ifBlank { "📄 " + topicHint.ifBlank { "Import" } },
                    parsed.sourceLanguage, parsed.targetLanguage, realCards,
                )
            } catch (e: AiException) {
                Log.e("ImportPipeline", "import AI error (${e.kind})", e)
                _state.value = ImportState.Error(friendlyError(e))
            } catch (e: Exception) {
                Log.e("ImportPipeline", "import failed", e)
                _state.value = ImportState.Error(e.message ?: "Import failed.")
            }
        }
    }

    // ---- merge (staged + undoable) ----

    fun confirmMerge() {
        val p = pendingMerge ?: return
        pendingMerge = null
        _state.value = ImportState.Loading("Merging into ${p.deck.name}…")
        scope.launch {
            val insertedIds = p.plan.toInsert.map {
                repository.insertFlashcard(
                    Flashcard(deckId = p.deck.id, front = it.front, back = it.back, notes = it.notes)
                )
            }
            val restored = p.plan.toEnrich.map { e ->
                repository.updateFlashcard(e.card.copy(notes = e.mergedNotes))
                e.card.id to e.card.notes
            }
            lastUndo = ImportUndo(insertedIds, restored)
            _state.value = ImportState.Success(p.deck.name, undoable = true)
        }
    }

    fun cancelMerge() {
        pendingMerge = null
        _state.value = ImportState.Idle
    }

    fun undoLastImport() {
        val u = lastUndo ?: return
        lastUndo = null
        scope.launch {
            u.insertedCardIds.forEach { id ->
                repository.getFlashcardById(id)?.let { repository.deleteFlashcard(it) }
            }
            u.restoredNotes.forEach { (id, notes) ->
                repository.getFlashcardById(id)?.let { repository.updateFlashcard(it.copy(notes = notes)) }
            }
            _state.value = ImportState.Idle
        }
    }

    private suspend fun saveOrMergeCards(
        targetDeckId: Long?,
        deckName: String,
        sourceLanguage: String?,
        targetLanguage: String?,
        parsedCards: List<ParsedCard>,
    ): ImportState {
        val existingDeck = targetDeckId?.let { repository.getDeckById(it) }
            ?: if (deckName == MASTER_POOL_NAME) {
                repository.allDecksFlowSnapshot().firstOrNull { it.name == MASTER_POOL_NAME }
            } else null
        if (existingDeck != null) {
            val existingCards = repository.getFlashcardsForDeck(existingDeck.id)
            val plan = DeckMerge.plan(existingCards, parsedCards.map { Triple(it.front, it.back, it.notes) })
            pendingMerge = PendingMerge(existingDeck, plan)
            return ImportState.MergePreview(
                MergePlan(existingDeck.name, plan.toInsert.size, plan.toEnrich.size, plan.skipCount)
            )
        }

        val newDeckId = repository.insertDeck(
            Deck(name = deckName, sourceLanguage = sourceLanguage, targetLanguage = targetLanguage)
        )
        repository.insertFlashcards(parsedCards.map { Flashcard(deckId = newDeckId, front = it.front, back = it.back, notes = it.notes) })
        lastUndo = null
        return ImportState.Success(deckName)
    }

    // ---- AI chunk parsing ----

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
            setMessage(
                if (attempts > 1) "Parsing chunk $chunkLabel (${pages.size} pgs)… attempt $attempts/$maxAttempts"
                else "Parsing chunk $chunkLabel (${pages.size} pgs)…"
            )
            try {
                val chunkText = pages.joinToString("\n\n")
                val system = Prompts.importSystem(nativeLanguage(), targetLanguage(), densityInstruction, chunkLabel)
                val user = Prompts.importUser("Document text chunk:\n$chunkText", topicHint, densityInstruction)
                val responseText = provider.generateStructured(system, user)
                return ImportParsing.robustParseJsonDeck(moshi, ImportParsing.cleanAndExtractJson(responseText))
            } catch (e: Exception) {
                lastError = e
                Log.e("ImportPipeline", "chunk $chunkLabel attempt $attempts/$maxAttempts failed", e)
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
                    setMessage("Retrying chunk $chunkLabel in ${backoff / 1000}s (attempt $attempts/$maxAttempts)…")
                    delay(backoff)
                }
            }
        }

        if (shouldSubdivide) {
            setMessage("Content too dense — splitting into smaller sections…")
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

    private fun densityInstruction(density: String, chunked: Boolean): String = when (density) {
        "Focused" -> if (chunked) "Extract 8–10 high-yield cards from this chunk."
        else "For short input generate 10–15 cards; for long input 15–20 premium cards."
        "Exhaustive" -> "Extract every unique term, phrase, definition and formula. Do not cap the count."
        else -> if (chunked) "Extract all unique vocabulary and key definitions — aim for 15–25 cards, don't truncate."
        else "For short input generate 15–25 cards; for long input extract everything found."
    }

    // ---- file helpers ----

    private fun copyUriToCacheFile(uri: Uri): File? = runCatching {
        if (uri.scheme == "file") {
            uri.path?.let { File(it) }?.takeIf { it.isFile }?.let { return it }
        }
        val ext = fileExtension(uri)
        val out = File(appContext.cacheDir, "import_${System.currentTimeMillis()}$ext")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out.takeIf { it.length() > 0 }
    }.onFailure { Log.e("ImportPipeline", "copyUriToCacheFile failed", it) }.getOrNull()

    private fun fileExtension(uri: Uri): String {
        val mime = appContext.contentResolver.getType(uri).orEmpty()
        return when {
            mime.contains("pdf", true) -> ".pdf"
            mime.contains("csv", true) -> ".csv"
            mime.contains("text", true) || mime.contains("plain", true) -> ".txt"
            uri.path?.substringAfterLast('.', "")?.isNotEmpty() == true -> ".${uri.path!!.substringAfterLast('.')}"
            else -> ""
        }
    }
}
