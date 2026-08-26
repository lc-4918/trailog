package fr.lc4918.trailog.data

import android.content.Context
import androidx.core.content.edit

/**
 * Thème choisi ("system" | "light" | "dark"), mis en cache en SharedPreferences en plus de la colonne Room
 * qui reste la source de vérité (cf. SettingsEntity.theme). Même raison que LocalePrefs : la première image
 * composée par [MainActivity] doit déjà porter le bon thème, et la lecture des réglages par Room - même
 * locale - est asynchrone. Sans ce cache, un thème différent du système se voyait un instant, le temps que
 * le Flow des réglages livre sa première valeur, avant de basculer sur le bon.
 */
object ThemePrefs {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME = "theme"
    private const val DEFAULT = "system"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_THEME, DEFAULT) ?: DEFAULT

    fun set(context: Context, theme: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_THEME, theme) }
    }
}
