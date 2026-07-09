package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Blob(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: Blob? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "responseMimeType") val responseMimeType: String? = null,
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }

    suspend fun generateContentWithFallback(
        apiKey: String,
        request: GenerateContentRequest,
        preferredModel: String = "gemini-3.5-flash",
        fallbackModel: String = "gemini-2.5-flash"
    ): GenerateContentResponse {
        return try {
            service.generateContent(preferredModel, apiKey, request)
        } catch (ex: Exception) {
            val isQuotaOrTransient = isTransientOrQuotaException(ex)
            if (isQuotaOrTransient) {
                try {
                    android.util.Log.w("RetrofitClient", "Preferred model $preferredModel failed ($ex). Falling back to $fallbackModel...")
                    service.generateContent(fallbackModel, apiKey, request)
                } catch (fallbackEx: Exception) {
                    throw fallbackEx
                }
            } else {
                throw ex
            }
        }
    }

    private fun isTransientOrQuotaException(throwable: Throwable): Boolean {
        if (throwable is retrofit2.HttpException) {
            val code = throwable.code()
            if (code == 429 || code == 503 || code == 504 || code == 502 || code == 500) {
                return true
            }
        }
        val msg = throwable.message.orEmpty()
        return msg.contains("429") || msg.contains("503") || msg.contains("quota", ignoreCase = true) || msg.contains("demand", ignoreCase = true) || msg.contains("timeout", ignoreCase = true)
    }
}
