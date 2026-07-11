package fr.lc4918.trailog.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.lc4918.trailog.ui.routes.MainScreen
import fr.lc4918.trailog.ui.settings.SettingsScreen

/**
 * Les réglages s'affichent en superposition (Box), sans détruire MainScreen ni la carte.
 * Évite la réinitialisation de la MapView (zoom/flicker) et les transitions de NavHost.
 */
@Composable
fun AppRoot() {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        MainScreen(onSettings = { showSettings = true }, settingsOpen = showSettings)
        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(onBack = { showSettings = false })
        }
    }
}
