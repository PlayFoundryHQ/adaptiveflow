package io.github.playfoundryhq.adaptiveflow

import io.github.playfoundryhq.adaptiveflow.domain.Languages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagesTest {

    @Test fun `find matches english name case-insensitively`() {
        assertEquals("fa", Languages.find("persian")?.code)
        assertEquals("fa", Languages.find("  Persian ")?.code)
        assertEquals("sv", Languages.find("Swedish")?.code)
    }

    @Test fun `find matches the code`() {
        assertEquals("English", Languages.find("en")?.english)
    }

    @Test fun `find returns null for blank or unknown`() {
        assertNull(Languages.find(""))
        assertNull(Languages.find(null))
        assertNull(Languages.find("Klingon"))
    }

    @Test fun `isRtl is true only for rtl scripts`() {
        assertTrue(Languages.isRtl("Persian"))
        assertTrue(Languages.isRtl("Arabic"))
        assertFalse(Languages.isRtl("English"))
        assertFalse(Languages.isRtl("Klingon"))
    }

    @Test fun `label shows endonym for non-english, raw value when unknown`() {
        assertEquals("Persian · فارسی", Languages.label("Persian"))
        assertEquals("English", Languages.label("English"))
        assertEquals("Klingon", Languages.label("Klingon"))
    }

    @Test fun `list has stable unique codes`() {
        val codes = Languages.all.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test fun `uiLocaleTag returns a tag only for translated languages`() {
        assertEquals("fa", Languages.uiLocaleTag("Persian"))
        assertEquals("en", Languages.uiLocaleTag("English"))
        assertNull(Languages.uiLocaleTag("German"))   // no values-de yet
        assertNull(Languages.uiLocaleTag("Klingon"))
    }
}
