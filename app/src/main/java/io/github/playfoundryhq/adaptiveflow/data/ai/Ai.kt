package io.github.playfoundryhq.adaptiveflow.data.ai

/**
 * Provider-agnostic AI layer. The rest of the app talks to [AiProvider] and never
 * to a concrete vendor SDK or wire type. Two providers are supported today:
 * Google Gemini and DeepSeek. Both are "bring your own key" — no key is bundled
 * in the APK.
 */

enum class AiProviderId(val storageKey: String, val displayName: String) {
    GEMINI("gemini", "Google Gemini"),
    DEEPSEEK("deepseek", "DeepSeek");

    companion object {
        fun fromStorageKey(key: String?): AiProviderId =
            entries.firstOrNull { it.storageKey == key } ?: GEMINI
    }
}

/** One turn of a tutor conversation. */
data class AiTurn(val role: Role, val text: String) {
    enum class Role { USER, MODEL }
}

/**
 * Every failure from a provider is normalised to this so callers can react to the
 * *kind* without string-matching vendor error prose.
 */
class AiException(
    message: String,
    val kind: Kind,
    /** Provider-recommended wait before retry, if it gave one. */
    val retryAfterMs: Long? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Kind {
        /** Missing / invalid API key, or the key lacks access. Not retryable. */
        AUTH,
        /** 429 / quota exhausted. Retryable after [retryAfterMs]. */
        RATE_LIMIT,
        /** 5xx / timeout / "high demand". Retryable with backoff. */
        TRANSIENT,
        /** Request body exceeded the model's input limit. Caller should subdivide. */
        PAYLOAD_TOO_LARGE,
        /** 400 that is not a size issue — malformed request. Not retryable. */
        BAD_REQUEST,
        /** No network / DNS / connection. Retryable. */
        NETWORK,
        /** The call succeeded but the model returned nothing usable. */
        EMPTY,
        UNKNOWN,
    }

    val isRetryable: Boolean
        get() = kind == Kind.RATE_LIMIT || kind == Kind.TRANSIENT || kind == Kind.NETWORK
}

interface AiProvider {
    val id: AiProviderId

    /** True if this provider can accept raw PDF bytes (Gemini can; DeepSeek cannot). */
    val supportsPdfBytes: Boolean

    /**
     * One structured-JSON generation. Returns the model's raw text output (the
     * caller is responsible for extracting/validating JSON from it).
     *
     * @param pdfBytes optional raw PDF to attach inline (only if [supportsPdfBytes]).
     */
    @Throws(AiException::class)
    suspend fun generateStructured(
        systemPrompt: String,
        userPrompt: String,
        pdfBytes: ByteArray? = null,
        maxOutputTokens: Int = 8192,
        temperature: Float = 0.3f,
    ): String

    /** A multi-turn tutor exchange. Returns the model's reply text. */
    @Throws(AiException::class)
    suspend fun chat(
        systemPrompt: String,
        history: List<AiTurn>,
        userMessage: String,
        temperature: Float = 0.7f,
    ): String
}
