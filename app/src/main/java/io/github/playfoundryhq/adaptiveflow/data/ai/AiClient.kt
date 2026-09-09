package io.github.playfoundryhq.adaptiveflow.data.ai

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP + JSON stack and the provider factory. No state — safe to
 * construct anywhere; will become a DI-provided singleton in the architecture
 * pass.
 */
class AiClient {

    private val moshi: Moshi = Moshi.Builder().build()

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS) // hard cap so a hung request can't wedge an import forever
        .build()

    fun provider(id: AiProviderId, apiKey: String): AiProvider = when (id) {
        AiProviderId.GEMINI -> GeminiProvider(apiKey, okHttp, moshi)
        AiProviderId.DEEPSEEK -> DeepSeekProvider(apiKey, okHttp, moshi)
    }
}
