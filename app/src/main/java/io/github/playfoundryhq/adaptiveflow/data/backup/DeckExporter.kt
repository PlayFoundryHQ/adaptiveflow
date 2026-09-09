package io.github.playfoundryhq.adaptiveflow.data.backup

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.playfoundryhq.adaptiveflow.data.model.Deck
import io.github.playfoundryhq.adaptiveflow.data.model.Flashcard

/**
 * Exports a deck to a portable file. Building a deck costs real API tokens, so
 * users can save one and re-import it later for free.
 *
 * The JSON shape is a superset of the importer's `ParsedDeck` — `deckName` /
 * `sourceLanguage` / `targetLanguage` / `cards[{front,back,notes}]` — so an
 * exported file drops straight back into the "📝 Paste" tab with no AI call.
 * The extra `srs` block (schedule state) is ignored on import but lets a
 * future full-restore keep progress.
 */
object DeckExporter {

    @JsonClass(generateAdapter = true)
    data class ExportCard(
        val front: String,
        val back: String,
        val notes: String? = null,
        val srs: Srs? = null,
    )

    @JsonClass(generateAdapter = true)
    data class Srs(
        val easeFactor: Float,
        val interval: Int,
        val repetitions: Int,
        val nextReview: Long,
    )

    @JsonClass(generateAdapter = true)
    data class ExportDeck(
        val deckName: String,
        val sourceLanguage: String? = null,
        val targetLanguage: String? = null,
        val exportedAt: Long = System.currentTimeMillis(),
        val schema: Int = 1,
        val cards: List<ExportCard>,
    )

    private val adapter by lazy {
        Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            .adapter(ExportDeck::class.java).indent("  ")
    }

    fun toJson(deck: Deck, cards: List<Flashcard>): String {
        val export = ExportDeck(
            deckName = deck.name,
            sourceLanguage = deck.sourceLanguage,
            targetLanguage = deck.targetLanguage,
            cards = cards.map {
                ExportCard(
                    front = it.front,
                    back = it.back,
                    notes = it.notes,
                    srs = Srs(it.easeFactor, it.interval, it.repetitions, it.nextReview),
                )
            },
        )
        return adapter.toJson(export)
    }

    /** Anki-friendly: `front,back,notes` with RFC-4180 quoting. */
    fun toCsv(cards: List<Flashcard>): String = buildString {
        append("front,back,notes\n")
        cards.forEach { append(csv(it.front)).append(',').append(csv(it.back)).append(',').append(csv(it.notes.orEmpty())).append('\n') }
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** A filesystem-safe base name derived from the deck name. */
    fun safeFileName(deckName: String): String {
        val cleaned = deckName
            .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")
            .take(48)
            .ifBlank { "deck" }
        return "adaptiveflow-$cleaned"
    }
}
