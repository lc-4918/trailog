package fr.lc4918.trailog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Les deux palettes de l'application, posees par [TrailogTheme] et par lui seul : aucun ecran ne prend
 *  l'autre que celle en cours. */
private val TrailogLight = lightColorScheme(primary = Color(0xFF1F6FB2), secondary = Color(0xFF2D867C))
private val TrailogDark = darkColorScheme(primary = Color(0xFF6FB6E8), secondary = Color(0xFF7FC8BD))

/** themePref : "system" | "light" | "dark". */
@Composable
fun TrailogTheme(themePref: String = "system", content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDarkTheme(themePref)) TrailogDark else TrailogLight, content = content)
}

/** Le theme demande se resout-il en sombre ? "system" suit le reglage de l'appareil. */
@Composable
fun isDarkTheme(themePref: String?): Boolean = when (themePref) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}
