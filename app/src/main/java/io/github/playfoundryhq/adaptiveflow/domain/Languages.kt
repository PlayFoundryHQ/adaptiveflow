package io.github.playfoundryhq.adaptiveflow.domain

/**
 * The canonical list of languages the learner profile offers. Stored (in
 * [io.github.playfoundryhq.adaptiveflow.data.settings.SettingsStore]) and passed
 * to the AI prompts by [english] name, so the existing free-text values
 * ("English", "Swedish", …) keep working with no migration.
 *
 * [rtl] flags the right-to-left scripts — unused today (the UI is English only)
 * but it's what a future app-UI-localisation track keys off, so it's recorded
 * here now rather than rediscovered later.
 */
data class Language(
    val code: String,       // BCP-47-ish, stable
    val english: String,    // display + what we store / send to the model
    val endonym: String,    // the language's own name, shown alongside
    val rtl: Boolean = false,
)

object Languages {

    /** Curated, not exhaustive: the common study languages plus every script we
     *  might localise the UI into. Order = rough global-usage. */
    val all: List<Language> = listOf(
        Language("en", "English", "English"),
        Language("zh", "Chinese", "中文"),
        Language("hi", "Hindi", "हिन्दी"),
        Language("es", "Spanish", "Español"),
        Language("fr", "French", "Français"),
        Language("ar", "Arabic", "العربية", rtl = true),
        Language("bn", "Bengali", "বাংলা"),
        Language("ru", "Russian", "Русский"),
        Language("pt", "Portuguese", "Português"),
        Language("ur", "Urdu", "اردو", rtl = true),
        Language("id", "Indonesian", "Bahasa Indonesia"),
        Language("de", "German", "Deutsch"),
        Language("ja", "Japanese", "日本語"),
        Language("fa", "Persian", "فارسی", rtl = true),
        Language("tr", "Turkish", "Türkçe"),
        Language("ko", "Korean", "한국어"),
        Language("it", "Italian", "Italiano"),
        Language("vi", "Vietnamese", "Tiếng Việt"),
        Language("pl", "Polish", "Polski"),
        Language("uk", "Ukrainian", "Українська"),
        Language("nl", "Dutch", "Nederlands"),
        Language("sv", "Swedish", "Svenska"),
        Language("he", "Hebrew", "עברית", rtl = true),
        Language("el", "Greek", "Ελληνικά"),
        Language("th", "Thai", "ไทย"),
        Language("ps", "Pashto", "پښتو", rtl = true),
    )

    /** Tolerant lookup by stored value: matches the English name or the code,
     *  case-insensitively. Returns null for a free-text "Other…" target. */
    fun find(value: String?): Language? {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return null
        return all.firstOrNull { it.english.equals(v, ignoreCase = true) || it.code.equals(v, ignoreCase = true) }
    }

    fun isRtl(value: String?): Boolean = find(value)?.rtl == true

    /** "English" -> "English", "fa" -> "Persian · فارسی", unknown -> the raw value. */
    fun label(value: String?): String {
        val lang = find(value) ?: return value?.trim().orEmpty()
        return if (lang.english == lang.endonym) lang.english else "${lang.english} · ${lang.endonym}"
    }
}
