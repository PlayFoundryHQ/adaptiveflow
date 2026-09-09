package io.github.playfoundryhq.adaptiveflow.data.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.IOException

/**
 * DeepSeek via its OpenAI-compatible Chat Completions API.
 *
 * Auth: `Authorization: Bearer <key>`. Structured output uses
 * `response_format = {"type":"json_object"}`. DeepSeek has no vision / file
 * intake, so scanned-PDF-as-bytes is rejected with a clear message.
 *
 * TODO: verify model ids (`deepseek-chat`, `deepseek-reasoner`) and the
 * `response_format` contract against current DeepSeek docs before each release.
 */
class DeepSeekProvider(
    private val apiKey: String,
    okHttpClient: OkHttpClient,
    moshi: Moshi,
    private val model: String = DEFAULT_MODEL,
    baseUrl: String = BASE_URL,
) : AiProvider {

    override val id = AiProviderId.DEEPSEEK
    override val supportsPdfBytes = false

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
        if (pdfBytes != null && pdfBytes.isNotEmpty()) {
            throw AiException(
                "DeepSeek can't read scanned/image PDFs. Extract the text first, or switch the provider to Gemini in Settings.",
                AiException.Kind.BAD_REQUEST,
            )
        }
        val req = ChatRequest(
            model = model,
            messages = listOf(
                Message("system", systemPrompt),
                Message("user", userPrompt),
            ),
            temperature = temperature,
            maxTokens = maxOutputTokens,
            responseFormat = ResponseFormat("json_object"),
        )
        return call(req)
    }

    override suspend fun chat(
        systemPrompt: String,
        history: List<AiTurn>,
        userMessage: String,
        temperature: Float,
    ): String {
        val messages = buildList {
            add(Message("system", systemPrompt))
            history.forEach {
                add(Message(if (it.role == AiTurn.Role.USER) "user" else "assistant", it.text))
            }
            add(Message("user", userMessage))
        }
        return call(ChatRequest(model = model, messages = messages, temperature = temperature))
    }

    private suspend fun call(req: ChatRequest): String {
        val res = try {
            service.chatCompletions("Bearer $apiKey", req)
        } catch (e: HttpException) {
            throw mapHttpError(e)
        } catch (e: IOException) {
            throw AiException("Network error contacting DeepSeek: ${e.message}", AiException.Kind.NETWORK, cause = e)
        } catch (e: Exception) {
            throw AiException("Unexpected DeepSeek error: ${e.message}", AiException.Kind.UNKNOWN, cause = e)
        }
        return res.choices?.firstOrNull()?.message?.content
            ?: throw AiException("DeepSeek returned an empty response.", AiException.Kind.EMPTY)
    }

    private fun mapHttpError(e: HttpException): AiException {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        val msg = runCatching { errorAdapter.fromJson(body)?.error?.message }.getOrNull()
        return when (e.code()) {
            400 -> AiException(msg ?: "DeepSeek rejected the request (400).", AiException.Kind.BAD_REQUEST)
            401, 403 -> AiException(msg ?: "DeepSeek API key is missing or invalid.", AiException.Kind.AUTH)
            402 -> AiException(msg ?: "DeepSeek account has insufficient balance.", AiException.Kind.AUTH)
            413 -> AiException("Text chunk too large for DeepSeek.", AiException.Kind.PAYLOAD_TOO_LARGE)
            429 -> AiException(msg ?: "DeepSeek rate limit hit. Try again shortly.", AiException.Kind.RATE_LIMIT)
            in 500..599 -> AiException(msg ?: "DeepSeek is temporarily unavailable.", AiException.Kind.TRANSIENT)
            else -> AiException("DeepSeek error ${e.code()}: ${msg ?: body.take(200)}", AiException.Kind.UNKNOWN)
        }
    }

    private interface Service {
        @POST("chat/completions")
        suspend fun chatCompletions(
            @Header("Authorization") auth: String,
            @Body request: ChatRequest,
        ): ChatResponse
    }

    @JsonClass(generateAdapter = true)
    data class Message(@Json(name = "role") val role: String, @Json(name = "content") val content: String)

    @JsonClass(generateAdapter = true)
    data class ResponseFormat(@Json(name = "type") val type: String)

    @JsonClass(generateAdapter = true)
    data class ChatRequest(
        @Json(name = "model") val model: String,
        @Json(name = "messages") val messages: List<Message>,
        @Json(name = "temperature") val temperature: Float? = null,
        @Json(name = "max_tokens") val maxTokens: Int? = null,
        @Json(name = "response_format") val responseFormat: ResponseFormat? = null,
        @Json(name = "stream") val stream: Boolean = false,
    )

    @JsonClass(generateAdapter = true)
    data class Choice(@Json(name = "message") val message: Message?)

    @JsonClass(generateAdapter = true)
    data class ChatResponse(@Json(name = "choices") val choices: List<Choice>? = null)

    @JsonClass(generateAdapter = true)
    data class ApiErrorEnvelope(@Json(name = "error") val error: ApiError? = null)

    @JsonClass(generateAdapter = true)
    data class ApiError(@Json(name = "message") val message: String? = null, @Json(name = "type") val type: String? = null)

    companion object {
        const val BASE_URL = "https://api.deepseek.com/"
        const val DEFAULT_MODEL = "deepseek-chat"
        private val errorAdapter = Moshi.Builder().build().adapter(ApiErrorEnvelope::class.java)
    }
}
