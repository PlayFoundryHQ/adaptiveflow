package io.github.playfoundryhq.adaptiveflow.data.ai

import android.util.Base64
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException

/**
 * Google Gemini via the Generative Language REST API.
 *
 * Auth is sent as the `x-goog-api-key` **header** (never `?key=` in the URL, so
 * keys can't leak into logged URLs). The model id is a constant — verify it
 * against the current Gemini API docs when updating; there is no "preferred vs
 * fallback" magic model.
 */
class GeminiProvider(
    private val apiKey: String,
    okHttpClient: OkHttpClient,
    moshi: Moshi,
    private val model: String = DEFAULT_MODEL,
    baseUrl: String = BASE_URL,
) : AiProvider {

    override val id = AiProviderId.GEMINI
    override val supportsPdfBytes = true

    private val service: Service = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(Service::class.java)

    override suspend fun generateStructured(
        systemPrompt: String,
        userPrompt: String,
        pdfBytes: ByteArray?,
        maxOutputTokens: Int,
        temperature: Float,
    ): String {
        val parts = buildList {
            add(Part(text = userPrompt))
            if (pdfBytes != null && pdfBytes.isNotEmpty()) {
                add(Part(inlineData = Blob("application/pdf", Base64.encodeToString(pdfBytes, Base64.NO_WRAP))))
            }
        }
        val req = GenerateContentRequest(
            contents = listOf(Content(role = "user", parts = parts)),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig(
                temperature = temperature,
                responseMimeType = "application/json",
                maxOutputTokens = maxOutputTokens,
            ),
        )
        return call(req)
    }

    override suspend fun chat(
        systemPrompt: String,
        history: List<AiTurn>,
        userMessage: String,
        temperature: Float,
    ): String {
        val contents = history.map {
            Content(
                role = if (it.role == AiTurn.Role.USER) "user" else "model",
                parts = listOf(Part(text = it.text)),
            )
        } + Content(role = "user", parts = listOf(Part(text = userMessage)))

        val req = GenerateContentRequest(
            contents = contents,
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig(temperature = temperature),
        )
        return call(req)
    }

    private suspend fun call(req: GenerateContentRequest): String {
        val res = try {
            service.generateContent(model, mapOf("x-goog-api-key" to apiKey), req)
        } catch (e: HttpException) {
            throw mapHttpError(e)
        } catch (e: IOException) {
            throw AiException("Network error contacting Gemini: ${e.message}", AiException.Kind.NETWORK, cause = e)
        } catch (e: Exception) {
            throw AiException("Unexpected Gemini error: ${e.message}", AiException.Kind.UNKNOWN, cause = e)
        }
        return res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw AiException("Gemini returned an empty response.", AiException.Kind.EMPTY)
    }

    private fun mapHttpError(e: HttpException): AiException {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        val parsed = runCatching { errorAdapter.fromJson(body)?.error }.getOrNull()
        val status = parsed?.status.orEmpty()
        val retryMs = extractRetryDelayMs(body)
        return when (e.code()) {
            400 -> if (body.contains("exceeds the maximum", true) || body.contains("too large", true))
                AiException("PDF/text chunk too large for the model.", AiException.Kind.PAYLOAD_TOO_LARGE, retryMs)
            else AiException(parsed?.message ?: "Gemini rejected the request (400).", AiException.Kind.BAD_REQUEST)
            401, 403 -> AiException(
                parsed?.message ?: "Gemini API key is missing, invalid, or lacks access.",
                AiException.Kind.AUTH,
            )
            413 -> AiException("PDF/text chunk too large for the model.", AiException.Kind.PAYLOAD_TOO_LARGE, retryMs)
            429 -> AiException(
                parsed?.message ?: "Gemini quota exceeded. Try again shortly.",
                AiException.Kind.RATE_LIMIT, retryMs,
            )
            in 500..599 -> AiException(
                parsed?.message ?: "Gemini is temporarily unavailable ($status).",
                AiException.Kind.TRANSIENT, retryMs,
            )
            else -> AiException("Gemini error ${e.code()}: ${parsed?.message ?: body.take(200)}", AiException.Kind.UNKNOWN)
        }
    }

    // ---- wire types ----

    private interface Service {
        @POST("v1beta/models/{model}:generateContent")
        suspend fun generateContent(
            @Path("model") model: String,
            @HeaderMap headers: Map<String, String>,
            @Body request: GenerateContentRequest,
        ): GenerateContentResponse
    }

    @JsonClass(generateAdapter = true)
    data class Blob(@Json(name = "mimeType") val mimeType: String, @Json(name = "data") val data: String)

    @JsonClass(generateAdapter = true)
    data class Part(
        @Json(name = "text") val text: String? = null,
        @Json(name = "inlineData") val inlineData: Blob? = null,
    )

    @JsonClass(generateAdapter = true)
    data class Content(
        @Json(name = "role") val role: String? = null,
        @Json(name = "parts") val parts: List<Part>,
    )

    @JsonClass(generateAdapter = true)
    data class GenerationConfig(
        @Json(name = "temperature") val temperature: Float? = null,
        @Json(name = "responseMimeType") val responseMimeType: String? = null,
        @Json(name = "maxOutputTokens") val maxOutputTokens: Int? = null,
    )

    @JsonClass(generateAdapter = true)
    data class GenerateContentRequest(
        @Json(name = "contents") val contents: List<Content>,
        @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
        @Json(name = "systemInstruction") val systemInstruction: Content? = null,
    )

    @JsonClass(generateAdapter = true)
    data class Candidate(@Json(name = "content") val content: Content)

    @JsonClass(generateAdapter = true)
    data class GenerateContentResponse(@Json(name = "candidates") val candidates: List<Candidate>? = null)

    @JsonClass(generateAdapter = true)
    data class ApiErrorEnvelope(@Json(name = "error") val error: ApiError? = null)

    @JsonClass(generateAdapter = true)
    data class ApiError(
        @Json(name = "code") val code: Int? = null,
        @Json(name = "message") val message: String? = null,
        @Json(name = "status") val status: String? = null,
    )

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/"

        /** TODO: verify against current Gemini API docs before each release. */
        const val DEFAULT_MODEL = "gemini-2.5-flash"

        private val errorAdapter = Moshi.Builder().build().adapter(ApiErrorEnvelope::class.java)

        private val retryDelayRegex = """"retryDelay"\s*:\s*"(\d+(?:\.\d+)?)s"""".toRegex()

        internal fun extractRetryDelayMs(body: String): Long? =
            retryDelayRegex.find(body)?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 1000).toLong() }
    }
}
