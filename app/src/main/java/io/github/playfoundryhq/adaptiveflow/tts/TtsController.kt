package io.github.playfoundryhq.adaptiveflow.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.github.playfoundryhq.adaptiveflow.data.settings.SettingsStore
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.DiagnosticLogger as Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * All text-to-speech in one place, app-scoped so it survives ViewModel
 * recreation (no re-init on rotation).
 *
 * Improvements over the old inline version:
 *  - prefers Google's TTS engine (`com.google.android.tts`, neural voices) when
 *    installed, falling back to the system default;
 *  - routes to **AvaCore** (`com.github.opscalehub.avacore`) — a native offline
 *    engine with a real Persian NLP front-end (ezafe, number expansion) and
 *    solid Piper voices for other languages — for *any* language it reports
 *    supporting, asked live via `TextToSpeech.isLanguageAvailable`; AvaCore's
 *    own language list can grow (it started Persian-only, now also covers
 *    English/Swedish) without this controller needing to know about it;
 *  - picks the best [Voice] for the target language (highest quality, not a
 *    not-yet-installed voice, offline preferred);
 *  - surfaces a missing language pack ([missingLanguage]) so the UI can offer to
 *    install it, instead of failing silently;
 *  - keeps the whole phrase — the old code cut "a / b" down to just "a";
 *  - reports speaking state + per-utterance errors.
 */
class TtsController(context: Context, private val settings: SettingsStore) {

    private val appContext = context.applicationContext

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _rate = MutableStateFlow(settings.ttsRate)
    val rate: StateFlow<Float> = _rate.asStateFlow()

    private val _autoPlay = MutableStateFlow(settings.ttsAutoPlay)
    val autoPlay: StateFlow<Boolean> = _autoPlay.asStateFlow()

    /** Non-null = the last requested language has no installed voice data. */
    private val _missingLanguage = MutableStateFlow<Locale?>(null)
    val missingLanguage: StateFlow<Locale?> = _missingLanguage.asStateFlow()

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var engineStarted = false
    private var appliedLanguage: Locale? = null

    // AvaCore — bound as soon as it's detected installed (alongside the default
    // engine, in prepare()), so every speak() call can ask it live which
    // language it actually supports rather than this controller hardcoding a
    // list. The first utterance or two in a session may still go through the
    // default engine while AvaCore finishes binding in the background.
    @Volatile private var avaEngine: TextToSpeech? = null
    @Volatile private var avaEngineReady = false
    @Volatile private var avaEngineStarted = false
    private var avaAppliedLanguage: Locale? = null

    /**
     * Bind the TTS engine. Binding `GoogleTtsService` costs ~1–2s, so this is
     * deferred until a study session actually opens rather than run at startup.
     * Safe to call repeatedly.
     */
    @Synchronized
    fun prepare() {
        if (engineStarted) return
        engineStarted = true
        // Binding the engine service can stall the caller for ~2s — do it off
        // whatever thread asked (usually a Compose LaunchedEffect on main).
        Thread({ createEngine() }, "tts-init").start()
        if (isAvaCoreInstalled()) ensureAvaEngine()
    }

    private fun createEngine() {
        if (tts != null) return
        val engine = pickEngine(appContext)
        val onInit = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setSpeechRate(_rate.value)
                tts?.setOnUtteranceProgressListener(progressListener)
                _isReady.value = true
            } else {
                Log.e("TtsController", "TTS init failed (status=$status)")
            }
        }
        tts = if (engine != null) TextToSpeech(appContext, onInit, engine) else TextToSpeech(appContext, onInit)
    }

    private fun ensureAvaEngine() {
        if (avaEngineStarted) return
        avaEngineStarted = true
        Thread({
            val onInit = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    avaEngine?.setSpeechRate(_rate.value)
                    avaEngine?.setOnUtteranceProgressListener(progressListener)
                    avaEngineReady = true
                } else {
                    Log.e("TtsController", "AvaCore init failed (status=$status)")
                }
            }
            avaEngine = TextToSpeech(appContext, onInit, AVACORE_PACKAGE)
        }, "tts-avacore-init").start()
    }

    private fun isAvaCoreInstalled(): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(AVACORE_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** True when AvaCore is bound and itself reports it can speak [locale] —
     *  asked live, so its supported-language list can grow without a change
     *  here. False (not yet an error) while it's still binding. */
    private fun avaEngineSupports(locale: Locale): Boolean {
        val engine = avaEngine ?: return false
        if (!avaEngineReady) return false
        return runCatching { engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE }
            .getOrDefault(false)
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) { _isSpeaking.value = true }
        override fun onDone(utteranceId: String?) { _isSpeaking.value = false }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) { _isSpeaking.value = false }
        override fun onError(utteranceId: String?, errorCode: Int) {
            _isSpeaking.value = false
            Log.e("TtsController", "utterance error code=$errorCode")
        }
    }

    fun speak(text: String, languageCode: String? = null, deckSourceLanguage: String? = null) {
        if (!engineStarted) prepare()
        if (!_isReady.value) return
        val locale = when {
            !languageCode.isNullOrBlank() -> Locale.forLanguageTag(languageCode)
            else -> localeFromLanguageName(deckSourceLanguage ?: "en")
        }

        val useAva = avaEngineSupports(locale)
        val engine = (if (useAva) avaEngine else tts) ?: return

        runCatching {
            if (useAva) {
                if (locale != avaAppliedLanguage) avaAppliedLanguage = applyLanguage(engine, locale)
            } else {
                if (locale != appliedLanguage) appliedLanguage = applyLanguage(engine, locale)
            }
            val cleaned = cleanForSpeech(text, locale)
            if (cleaned.isNotBlank()) {
                engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "af-utterance")
            }
        }.onFailure { Log.e("TtsController", "speak failed", it) }
    }

    fun setRate(value: Float) {
        _rate.value = value
        settings.ttsRate = value
        tts?.setSpeechRate(value)
        avaEngine?.setSpeechRate(value)
    }

    fun setAutoPlay(enabled: Boolean) {
        _autoPlay.value = enabled
        settings.ttsAutoPlay = enabled
    }

    fun stop() {
        tts?.stop()
        avaEngine?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        avaEngine?.stop()
        avaEngine?.shutdown()
        avaEngine = null
    }

    // ---- internals ----

    private fun applyLanguage(engine: TextToSpeech, locale: Locale): Locale {
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            _missingLanguage.value = locale
            Log.w("TtsController", "no voice data for ${locale.toLanguageTag()} (result=$result)")
        } else {
            _missingLanguage.value = null
        }
        bestVoiceFor(engine, locale)?.let { engine.voice = it }
        engine.setSpeechRate(_rate.value)
        return locale
    }

    private fun bestVoiceFor(engine: TextToSpeech, locale: Locale): Voice? = runCatching {
        engine.voices
            ?.filter {
                it.locale.language == locale.language &&
                    it.quality >= Voice.QUALITY_NORMAL &&
                    it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }
            ?.sortedWith(
                compareByDescending<Voice> { it.locale.country.equals(locale.country, ignoreCase = true) }
                    .thenBy { it.isNetworkConnectionRequired } // offline first
                    .thenByDescending { it.quality }
            )
            ?.firstOrNull()
    }.getOrNull()

    private fun cleanForSpeech(text: String, locale: Locale): String {
        var s = text
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\[[^]]*\\]"), "")
            .replace(Regex("\\{[^}]*\\}"), "")
            .replace("/", ", ")   // "a / b" -> spoken as "a, b", not truncated
        // For a non-Latin target, a trailing "word: gloss" is usually the Latin
        // gloss — drop it so the engine doesn't spell out English letters.
        val nonLatin = locale.language in NON_LATIN
        if (nonLatin && s.contains(":")) s = s.substringBefore(":")
        if (nonLatin) s = s.replace(Regex("[a-zA-Z]"), "")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun locale(language: String, region: String? = null): Locale =
        Locale.Builder().setLanguage(language).apply { region?.let { setRegion(it) } }.build()

    private fun localeFromLanguageName(name: String): Locale {
        val t = name.lowercase(Locale.ROOT).trim()
        if (t.length == 2) return locale(t)
        return when (t) {
            "persian", "farsi" -> locale("fa")
            "spanish" -> locale("es", "ES")
            "french" -> Locale.FRENCH
            "german" -> Locale.GERMAN
            "swedish" -> locale("sv", "SE")
            "italian" -> Locale.ITALIAN
            "japanese" -> Locale.JAPANESE
            "korean" -> Locale.KOREAN
            "chinese" -> Locale.CHINESE
            "english" -> Locale.ENGLISH
            "arabic" -> locale("ar")
            "portuguese" -> locale("pt", "PT")
            "russian" -> locale("ru")
            "hindi" -> locale("hi")
            "turkish" -> locale("tr")
            "dutch" -> locale("nl", "NL")
            else -> Locale.getAvailableLocales().firstOrNull {
                it.displayLanguage.lowercase(Locale.ROOT) == t || it.language.lowercase(Locale.ROOT) == t
            } ?: Locale.getDefault()
        }
    }

    companion object {
        /** AvaCore's package — an offline TTS engine with a real Persian NLP
         *  front-end plus Piper voices for other languages, not bundled with
         *  AdaptiveFlow. Discovered at runtime; if the project ever migrates
         *  orgs this is the one line to update. */
        const val AVACORE_PACKAGE = "com.github.opscalehub.avacore"

        private val NON_LATIN = setOf("fa", "ar", "ja", "zh", "ko", "hi", "he", "el", "ru", "uk", "bg", "th")

        /** Google's engine if installed, else null (system default). */
        private fun pickEngine(context: Context): String? {
            val google = "com.google.android.tts"
            val installed = runCatching {
                context.packageManager.getPackageInfo(google, 0); true
            }.getOrDefault(false)
            return google.takeIf { installed }
        }
    }
}
