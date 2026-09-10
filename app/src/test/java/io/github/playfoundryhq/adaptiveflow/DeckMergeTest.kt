package io.github.playfoundryhq.adaptiveflow

import io.github.playfoundryhq.adaptiveflow.data.model.Flashcard
import io.github.playfoundryhq.adaptiveflow.domain.DeckMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckMergeTest {

    private fun card(front: String, back: String, notes: String? = null) =
        Flashcard(id = front.hashCode().toLong(), deckId = 1L, front = front, back = back, notes = notes)

    @Test
    fun `matching ignores case and punctuation`() {
        assertEquals(DeckMerge.normalize("Hej!"), DeckMerge.normalize("  hej "))
    }

    @Test
    fun `unseen fronts are inserted, seen fronts are not`() {
        val existing = listOf(card("Katt", "Cat"))
        val plan = DeckMerge.plan(
            existing,
            listOf(
                Triple("Katt", "Cat", null),      // already present, nothing new -> skip
                Triple("Hund", "Dog", null),      // new
            ),
        )
        assertEquals(listOf("Hund"), plan.toInsert.map { it.front })
        assertEquals(0, plan.toEnrich.size)
        assertEquals(1, plan.skipCount)
    }

    @Test
    fun `a new alternative translation enriches the existing card`() {
        val existing = listOf(card("Bank", "Financial institution"))
        val plan = DeckMerge.plan(existing, listOf(Triple("bank", "River edge", "Geography sense")))
        assertEquals(0, plan.toInsert.size)
        assertEquals(1, plan.toEnrich.size)
        val merged = plan.toEnrich.single().mergedNotes
        assertTrue(merged.contains("Alt: River edge"))
        assertTrue(merged.contains("Geography sense"))
    }

    @Test
    fun `re-importing the exact same deck is a no-op`() {
        val existing = listOf(card("Katt", "Cat", "• A pet"), card("Hund", "Dog"))
        val plan = DeckMerge.plan(
            existing,
            listOf(Triple("Katt", "Cat", "A pet"), Triple("Hund", "Dog", null)),
        )
        assertTrue(plan.toInsert.isEmpty())
        assertTrue(plan.toEnrich.isEmpty())
        assertEquals(2, plan.skipCount)
        assertTrue(!plan.hasChanges)
    }

    @Test
    fun `enrichedNotes returns null when nothing is new`() {
        val existing = card("Katt", "Cat", "• Alt: feline\n• common household animal")
        assertNull(DeckMerge.enrichedNotes(existing, "feline", "common household animal"))
    }

    @Test
    fun `counts always add up to the incoming size`() {
        val existing = listOf(card("A", "a"), card("B", "b"))
        val incoming = listOf(
            Triple("A", "a", null),        // skip
            Triple("B", "b2", null),       // enrich
            Triple("C", "c", null),        // insert
        )
        val plan = DeckMerge.plan(existing, incoming)
        assertEquals(incoming.size, plan.toInsert.size + plan.toEnrich.size + plan.skipCount)
    }
}
