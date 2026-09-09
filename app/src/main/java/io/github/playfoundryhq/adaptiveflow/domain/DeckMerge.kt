package io.github.playfoundryhq.adaptiveflow.domain

import io.github.playfoundryhq.adaptiveflow.data.model.Flashcard

/**
 * Pure logic for merging freshly-parsed cards into an existing deck: match by a
 * normalised front, add genuinely new cards, and fold new alternative
 * translations / notes into cards that already exist. No I/O — the ViewModel
 * turns the resulting [Plan] into DB writes (and into a preview + undo).
 */
object DeckMerge {

    /** A card to be added, carrying its parsed fields. */
    data class NewCard(val front: String, val back: String, val notes: String?)

    /** An existing card and the notes it should have after enrichment. */
    data class Enrichment(val card: Flashcard, val mergedNotes: String)

    data class Plan(
        val toInsert: List<NewCard>,
        val toEnrich: List<Enrichment>,
        val skipCount: Int,
    ) {
        val hasChanges get() = toInsert.isNotEmpty() || toEnrich.isNotEmpty()
    }

    fun normalize(s: String): String = s.lowercase().trim()
        .replace(Regex("[\\p{Punct}]"), "")
        .replace(Regex("\\s+"), " ")

    /** Notes for [existing] after folding in [incomingBack] / [incomingNotes], or null if nothing new. */
    fun enrichedNotes(existing: Flashcard, incomingBack: String, incomingNotes: String?): String? {
        val currentNotes = existing.notes ?: ""
        // Dedup against both the definition and anything already in the notes,
        // so re-importing the same deck doesn't keep stacking "Alt:" lines.
        val known = normalize("${existing.back} $currentNotes")
        val addition = buildString {
            if (incomingBack.isNotBlank() && !known.contains(normalize(incomingBack))) {
                append("\n• Alt: ").append(incomingBack)
            }
            val n = incomingNotes ?: ""
            if (n.isNotBlank() && !currentNotes.contains(n)) append("\n• ").append(n)
        }.trim()
        if (addition.isEmpty()) return null
        return if (currentNotes.isBlank()) addition else "$currentNotes\n$addition"
    }

    fun plan(
        existing: List<Flashcard>,
        incoming: List<Triple<String, String, String?>>, // front, back, notes
    ): Plan {
        val byFront = existing.associateBy { normalize(it.front) }
        val toInsert = mutableListOf<NewCard>()
        val toEnrich = mutableListOf<Enrichment>()
        for ((front, back, notes) in incoming) {
            val match = byFront[normalize(front)]
            if (match == null) {
                toInsert += NewCard(front, back, notes)
            } else {
                enrichedNotes(match, back, notes)?.let { toEnrich += Enrichment(match, it) }
            }
        }
        return Plan(toInsert, toEnrich, incoming.size - toInsert.size - toEnrich.size)
    }
}
