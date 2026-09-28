package fr.lc4918.trailog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Le theme de l'application, pose par lui seul : couleurs (cf. Color.kt), texte (Type.kt) et arrondis
 * (Shape.kt). Un ecran lit MaterialTheme.colorScheme, .typography et .shapes, et n'a pas de taille de
 * texte ni d'arrondi a lui.
 *
 * themePref : "system" | "light" | "dark".
 */
@Composable
fun TrailogTheme(themePref: String = "light", content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDarkTheme(themePref)) TrailogDark else TrailogLight,
        typography = TrailogTypography,
        shapes = TrailogShapes,
        content = content,
    )
}

/** Le theme demande se resout-il en sombre ? "system" suit le reglage de l'appareil. */
@Composable
fun isDarkTheme(themePref: String?): Boolean = when (themePref) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}
