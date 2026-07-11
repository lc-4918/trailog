package fr.lc4918.trailog.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Langue de l'application, indépendante de la langue système (SPEC §1.3). Stockée en SharedPreferences
 * (et non en base Room) car elle doit être lisible de façon synchrone dans attachBaseContext, avant que
 * la base de données ne soit accessible. Français par défaut pour les nouvelles installations (aucune
 * option "suivre le système" proposée dans le sélecteur — cf. bug 1.1).
 */
object LocalePrefs {
    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "language"

    /** Conservé pour compatibilité avec une valeur stockée par une version antérieure de l'app (avant
     *  retrait de l'option "Système" du sélecteur) : `wrap()` la traite toujours comme un no-op. */
    private const val SYSTEM = "system"
    private const val DEFAULT = "fr"

    /** Langues proposées dans le sélecteur, dans l'ordre d'affichage. */
    val SELECTABLE = listOf("fr", "en", "es", "de", "it", "ca", "eu", "pt")

    /** Nom natif de la langue (indépendant de la langue actuelle de l'appli). */
    private val NATIVE_NAMES = mapOf(
        "fr" to "Français", "en" to "English", "es" to "Español", "de" to "Deutsch", "it" to "Italiano",
        "ca" to "Català", "eu" to "Euskara", "pt" to "Português",
    )
    fun nativeName(code: String): String = NATIVE_NAMES[code] ?: code

    /** Code de drapeau (asset SVG bundlé, cf. map/BasemapIcons.kt) associé à chaque langue. Distinct des
     *  drapeaux des fonds de plan nationaux : ici l'anglais utilise le Royaume-Uni, le catalan et le
     *  basque utilisent les drapeaux régionaux (Catalogne / Pays basque), pas un drapeau national. */
    private val FLAG_CODES = mapOf(
        "fr" to "fr", "en" to "gb", "es" to "es", "de" to "de", "it" to "it",
        "ca" to "es-ct", "eu" to "es-pv", "pt" to "pt",
    )
    fun flagCode(code: String): String? = FLAG_CODES[code]

    fun get(context: Context): String {
        val lang = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
        return when {
            lang == null -> DEFAULT
            lang == SYSTEM -> SYSTEM   // valeur historique : reste un no-op dans wrap()
            lang in SELECTABLE -> lang
            else -> DEFAULT
        }
    }

    fun set(context: Context, language: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LANGUAGE, language).apply()
    }

    /** Enveloppe le contexte avec la locale choisie (no-op pour l'ancienne valeur "system"). À appeler
     *  depuis attachBaseContext (Application ET Activity, pour couvrir aussi bien getString() côté
     *  ViewModel/Application que stringResource() côté Compose). */
    fun wrap(context: Context): Context {
        val lang = get(context)
        if (lang == SYSTEM) return context
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
