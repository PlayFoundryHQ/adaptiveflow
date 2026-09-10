package io.github.playfoundryhq.adaptiveflow.data.settings

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Per-app UI language, the manual way (min SDK 24, no AppCompat).
 *
 * The chosen BCP-47 tag is stored in the same plain prefs file [SettingsStore]
 * uses, and applied in [Activity.attachBaseContext] by wrapping the base context
 * with an overridden [Configuration] locale + layout direction. Changing it calls
 * [Activity.recreate].
 *
 * `null` / empty means "follow the system" (which is English for us — the only
 * default `values/` catalogue).
 */
object LocaleManager {

    private const val PREFS = "adaptiveflow_prefs"
    private const val KEY = "app_locale"

    fun storedTag(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.takeIf { it.isNotBlank() }

    /** Wrap a base context so the whole activity tree resolves resources in the
     *  stored locale. Call from [Activity.attachBaseContext]. */
    fun wrap(base: Context): Context {
        val tag = storedTag(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Persist [tag] (null to follow system) and, if it actually changed, recreate
     * [activity] so the new catalogue + layout direction take effect immediately.
     * Returns true when a recreate was triggered.
     */
    fun apply(activity: Activity, tag: String?): Boolean {
        val normalized = tag?.trim()?.takeIf { it.isNotBlank() }
        val current = storedTag(activity)
        if (normalized == current) return false

        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply { if (normalized == null) remove(KEY) else putString(KEY, normalized) }
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) activity.recreate()
        return true
    }
}
