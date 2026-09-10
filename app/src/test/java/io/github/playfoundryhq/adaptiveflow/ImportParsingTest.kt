package io.github.playfoundryhq.adaptiveflow

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.playfoundryhq.adaptiveflow.data.ai.AiException
import io.github.playfoundryhq.adaptiveflow.domain.ImportParsing
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.ParsedCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportParsingTest {

    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test fun `cleanAndExtractJson strips a json fence`() {
        val raw = "```json\n{\"deckName\":\"x\",\"cards\":[]}\n```"
        assertEquals("{\"deckName\":\"x\",\"cards\":[]}", ImportParsing.cleanAndExtractJson(raw))
    }

    @Test fun `cleanAndExtractJson grabs the outermost object amid prose`() {
        val raw = "Sure! Here is your deck: {\"cards\":[{\"front\":\"a\",\"back\":\"b\"}]} — enjoy."
        assertEquals("{\"cards\":[{\"front\":\"a\",\"back\":\"b\"}]}", ImportParsing.cleanAndExtractJson(raw))
    }

    @Test fun `robustParseJsonDeck reads the full deck object`() {
        val deck = ImportParsing.robustParseJsonDeck(
            moshi,
            "{\"deckName\":\"Swedish\",\"sourceLanguage\":\"sv\",\"cards\":[{\"front\":\"Katt\",\"back\":\"Cat\"}]}",
        )
        assertEquals("Swedish", deck.deckName)
        assertEquals(1, deck.cards.size)
    }

    @Test fun `robustParseJsonDeck accepts a bare card array`() {
        val deck = ImportParsing.robustParseJsonDeck(moshi, "[{\"front\":\"Hund\",\"back\":\"Dog\"}]")
        assertEquals(1, deck.cards.size)
        assertEquals("Hund", deck.cards.single().front)
    }

    @Test fun `robustParseJsonDeck throws on junk`() {
        assertThrows(AiException::class.java) {
            ImportParsing.robustParseJsonDeck(moshi, "not json at all")
        }
    }

    @Test fun `isYouTubeUrl matches the common forms`() {
        assertTrue(ImportParsing.isYouTubeUrl("https://youtu.be/abc123"))
        assertTrue(ImportParsing.isYouTubeUrl("watch this: https://www.youtube.com/watch?v=x"))
        assertFalse(ImportParsing.isYouTubeUrl("just some vocabulary text"))
    }

    @Test fun `filterOutUrlEchoCards drops cards that are really URLs`() {
        val cards = listOf(
            ParsedCard("Katt", "Cat"),
            ParsedCard("https://youtu.be/x", "the video"),
            ParsedCard("Hund", "http://example.com"),
        )
        assertEquals(listOf("Katt"), ImportParsing.filterOutUrlEchoCards(cards).map { it.front })
    }

    @Test fun `parseRawTextLocally reads a colon list`() {
        val deck = ImportParsing.parseRawTextLocally("Katt: Cat\nHund: Dog\n# a comment\nFisk: Fish", "Swedish basics")
        assertEquals("Swedish basics", deck!!.deckName)
        assertEquals(listOf("Katt", "Hund", "Fisk"), deck.cards.map { it.front })
    }

    @Test fun `parseRawTextLocally falls back to hyphen then equals`() {
        assertEquals(2, ImportParsing.parseRawTextLocally("a - b\nc - d", "")!!.cards.size)
        assertEquals(1, ImportParsing.parseRawTextLocally("k = v", "")!!.cards.size)
    }

    @Test fun `parseRawTextLocally returns null when there is no separator`() {
        assertNull(ImportParsing.parseRawTextLocally("just\nplain\nlines", ""))
    }
}
