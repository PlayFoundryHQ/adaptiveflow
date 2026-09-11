package io.github.playfoundryhq.adaptiveflow

import io.github.playfoundryhq.adaptiveflow.tts.TtsController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TtsEngineRoutingTest {

    @Test fun `routes Persian to AvaCore only when it's installed`() {
        assertTrue(TtsController.shouldUseAvaCore(Locale.forLanguageTag("fa"), avaCoreInstalled = true))
        assertFalse(TtsController.shouldUseAvaCore(Locale.forLanguageTag("fa"), avaCoreInstalled = false))
    }

    @Test fun `never routes a non-Persian locale to AvaCore`() {
        assertFalse(TtsController.shouldUseAvaCore(Locale.ENGLISH, avaCoreInstalled = true))
        assertFalse(TtsController.shouldUseAvaCore(Locale.forLanguageTag("sv"), avaCoreInstalled = true))
        assertFalse(TtsController.shouldUseAvaCore(Locale.forLanguageTag("ar"), avaCoreInstalled = true))
    }

    @Test fun `is case-insensitive on the language subtag`() {
        assertTrue(TtsController.shouldUseAvaCore(Locale.forLanguageTag("fa-IR"), avaCoreInstalled = true))
    }
}
