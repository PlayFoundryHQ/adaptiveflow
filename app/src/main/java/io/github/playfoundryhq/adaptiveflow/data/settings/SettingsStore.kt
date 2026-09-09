package io.github.playfoundryhq.adaptiveflow.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.playfoundryhq.adaptiveflow.data.ai.AiProviderId

/**
 * All user settings in one place.
 *
 * - **API keys** live in [EncryptedSharedPreferences] (`adaptiveflow_secure`).
 *   No key is bundled in the APK — the user supplies their own Gemini and/or
 *   DeepSeek key.
 * - Everything else (provider choice, learning goal, TTS prefs, lightweight
 *   gamification counters) lives in plain prefs (`adaptiveflow_prefs`).
 *   Gamification will migrate to Room in the architecture pass.
 */
class SettingsStore(context: Context) {

    private val plain: SharedPreferences =
        context.getSharedPreferences("adaptiveflow_prefs", Context.MODE_PRIVATE)

    private val secure: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "adaptiveflow_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // Can fail after a backup restore with a rotated key, or on a broken
        // keystore. Fall back to plain prefs rather than crash — the key is the
        // user's own and re-enterable.
        Log.w("SettingsStore", "EncryptedSharedPreferences unavailable, falling back to plain", it)
        context.getSharedPreferences("adaptiveflow_secure_fallback", Context.MODE_PRIVATE)
    }

    // ---- AI provider + keys ----

    var providerId: AiProviderId
        get() = AiProviderId.fromStorageKey(plain.getString(KEY_PROVIDER, null))
        set(value) = plain.edit().putString(KEY_PROVIDER, value.storageKey).apply()

    fun apiKeyFor(id: AiProviderId): String =
        secure.getString(keyName(id), "")?.trim().orEmpty()

    fun setApiKeyFor(id: AiProviderId, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) secure.edit().remove(keyName(id)).apply()
        else secure.edit().putString(keyName(id), trimmed).apply()
    }

    /** The key for the *currently selected* provider, or "" if none set. */
    fun activeApiKey(): String = apiKeyFor(providerId)

    fun hasUsableKey(): Boolean = activeApiKey().isNotEmpty()

    private fun keyName(id: AiProviderId) = "api_key_${id.storageKey}"

    // ---- Learning goal ----

    var nativeLanguage: String
        get() = plain.getString(KEY_NATIVE_LANG, "English") ?: "English"
        set(value) = plain.edit().putString(KEY_NATIVE_LANG, value.trim()).apply()

    var targetLanguage: String
        get() = plain.getString(KEY_TARGET_LANG, "Swedish") ?: "Swedish"
        set(value) = plain.edit().putString(KEY_TARGET_LANG, value.trim()).apply()

    // ---- TTS ----

    var ttsRate: Float
        get() = plain.getFloat(KEY_TTS_RATE, 1.0f)
        set(value) = plain.edit().putFloat(KEY_TTS_RATE, value).apply()

    var ttsAutoPlay: Boolean
        get() = plain.getBoolean(KEY_TTS_AUTOPLAY, true)
        set(value) = plain.edit().putBoolean(KEY_TTS_AUTOPLAY, value).apply()

    // ---- Gamification (interim — moves to Room) ----

    var hasSeeded: Boolean
        get() = plain.getBoolean(KEY_HAS_SEEDED, false)
        set(value) = plain.edit().putBoolean(KEY_HAS_SEEDED, value).apply()

    var xp: Int
        get() = plain.getInt(KEY_XP, 0)
        set(value) = plain.edit().putInt(KEY_XP, value).apply()

    var streak: Int
        get() = plain.getInt(KEY_STREAK, 0)
        set(value) = plain.edit().putInt(KEY_STREAK, value).apply()

    var lastStudyDate: String
        get() = plain.getString(KEY_LAST_STUDY_DATE, "") ?: ""
        set(value) = plain.edit().putString(KEY_LAST_STUDY_DATE, value).apply()

    private companion object {
        const val KEY_PROVIDER = "ai_provider"
        const val KEY_NATIVE_LANG = "native_language"
        const val KEY_TARGET_LANG = "target_language"
        const val KEY_TTS_RATE = "tts_rate"
        const val KEY_TTS_AUTOPLAY = "tts_autoplay"
        const val KEY_HAS_SEEDED = "has_seeded_default_decks"
        const val KEY_XP = "gamified_xp"
        const val KEY_STREAK = "study_streak"
        const val KEY_LAST_STUDY_DATE = "last_study_date"
    }
}
