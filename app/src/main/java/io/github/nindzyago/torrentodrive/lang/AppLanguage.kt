package io.github.nindzyago.torrentodrive.lang

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * The UI language: the one the user picked on the home screen, or — by default — the first of
 * the system's preferred languages that the app has, else English.
 */
object AppLanguage {

    private const val PREFS = "language"
    private const val KEY = "tag"

    /** The language the user picked, or null for automatic. */
    fun chosen(context: Context): LanguageWords? =
        prefs(context).getString(KEY, null)?.let { tag -> Languages.all.firstOrNull { it.tag == tag } }

    /** Remembers the choice (null = automatic); the activity has to be recreated to apply it. */
    fun choose(context: Context, language: LanguageWords?) {
        prefs(context).edit().apply { if (language == null) remove(KEY) else putString(KEY, language.tag) }.apply()
    }

    /** What automatic means on this device. */
    fun auto(): LanguageWords {
        val system = Resources.getSystem().configuration.locales
        return (0 until system.size()).firstNotNullOfOrNull { i ->
            Languages.all.firstOrNull { it.tag == system[i].language }
        } ?: English
    }

    fun current(context: Context): LanguageWords = chosen(context) ?: auto()

    /** A context whose resources (and the default locale, used for speech recognition) are in [current]. */
    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(current(base).tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
