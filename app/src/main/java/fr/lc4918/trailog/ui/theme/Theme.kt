package fr.lc4918.trailog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Color(0xFF1F6FB2), secondary = Color(0xFF2D867C))
private val Dark = darkColorScheme(primary = Color(0xFF6FB6E8), secondary = Color(0xFF7FC8BD))

/** themePref : "system" | "light" | "dark". */
@Composable
fun CycleTheme(themePref: String = "system", content: @Composable () -> Unit) {
    val dark = when (themePref) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
