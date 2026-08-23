package fr.lc4918.trailog.ui.routes

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import fr.lc4918.trailog.data.db.SettingsEntity

/**
 * La couleur des icones de la barre de statut : noires, ou claires.
 *
 * Le cas particulier est la barre TRANSPARENTE, sous laquelle la carte passe : elle n'a alors plus de fond
 * a elle, et ce sont les tuiles qui se voient dessous. Des icones claires y disparaitraient sur un fond de
 * carte clair - ce que sont la plupart des fonds -, d'ou le noir impose, quel que soit le theme.
 *
 * @param overlayOpen les reglages ou le menu lateral recouvrent la carte : la barre retrouve alors le fond
 *   du theme, donc ses icones ordinaires.
 */
@Composable
internal fun StatusBarAppearanceEffect(settings: SettingsEntity, overlayOpen: Boolean) {
    val dark = when (settings.theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val transparentBar = settings.statusBarTransparent
    val view = LocalView.current
    LaunchedEffect(dark, transparentBar, overlayOpen) {
        val light = when {
            overlayOpen -> !dark
            transparentBar -> true                 // toujours noir au-dessus de la carte transparente
            else -> !dark
        }
        WindowCompat.getInsetsController((view.context as Activity).window, view)
            .isAppearanceLightStatusBars = light
    }
}
