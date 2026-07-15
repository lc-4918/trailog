package fr.lc4918.trailog.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.lc4918.trailog.ui.routes.MainScreen
import fr.lc4918.trailog.ui.settings.SettingsScreen
import fr.lc4918.trailog.update.ReleaseInfo
import fr.lc4918.trailog.update.UpdateCheck
import fr.lc4918.trailog.update.UpdateFlow
import fr.lc4918.trailog.update.UpdateManager

/**
 * Les réglages s'affichent en superposition (Box), sans détruire MainScreen ni la carte.
 * Évite la réinitialisation de la MapView (zoom/flicker) et les transitions de NavHost.
 */
@Composable
fun AppRoot(autoCheckUpdates: Boolean) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        MainScreen(onSettings = { showSettings = true }, settingsOpen = showSettings)
        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(onBack = { showSettings = false })
        }
    }

    // Verification au demarrage, une seule fois par lancement : la cle du remember ne depend de rien, donc
    // ni un aller-retour dans les reglages ni une rotation ne la relancent. Silencieuse par nature : ni
    // erreur reseau ni "aucune mise a jour" ne sont montrees ici, contrairement au bouton des reglages.
    var pending by remember { mutableStateOf<ReleaseInfo?>(null) }
    var done by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoCheckUpdates) {
        if (!autoCheckUpdates || done || !UpdateManager.isSupported) return@LaunchedEffect
        done = true
        (UpdateManager.check() as? UpdateCheck.Available)?.let { pending = it.release }
    }
    UpdateFlow(release = pending, onDone = { pending = null })
}
