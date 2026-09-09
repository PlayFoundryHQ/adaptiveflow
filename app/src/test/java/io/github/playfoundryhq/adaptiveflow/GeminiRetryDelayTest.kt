package io.github.playfoundryhq.adaptiveflow

import io.github.playfoundryhq.adaptiveflow.data.ai.GeminiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiRetryDelayTest {

    @Test
    fun `parses integer retryDelay seconds from a 429 error body`() {
        val body = """
            {"error":{"code":429,"status":"RESOURCE_EXHAUSTED",
             "details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"37s"}]}}
        """.trimIndent()
        assertEquals(37_000L, GeminiProvider.extractRetryDelayMs(body))
    }

    @Test
    fun `parses fractional retryDelay seconds`() {
        assertEquals(1_500L, GeminiProvider.extractRetryDelayMs("""prefix {"retryDelay": "1.5s"} suffix"""))
    }

    @Test
    fun `returns null when no retryDelay present`() {
        assertNull(GeminiProvider.extractRetryDelayMs("""{"error":{"code":500,"message":"internal"}}"""))
        assertNull(GeminiProvider.extractRetryDelayMs(""))
    }
}
