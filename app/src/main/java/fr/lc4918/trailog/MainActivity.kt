package fr.lc4918.trailog

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import fr.lc4918.trailog.data.LocalePrefs
import fr.lc4918.trailog.ui.nav.AppRoot
import fr.lc4918.trailog.ui.theme.CycleTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Doit être appelé avant super.onCreate() : affiche l'écran de démarrage système natif (thème
        // Theme.TrailogApp.Splash, manifeste) — aucun code/écran Compose ajouté dans la boucle, donc aucun
        // délai ni écran vide supplémentaire par rapport à l'écran de démarrage par défaut d'Android.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as TrailogApp).repository
        setContent {
            val settings by repo.settingsFlow.collectAsState(initial = null)
            // La couleur des icônes de la barre de statut est gérée dans MainScreen.
            CycleTheme(themePref = settings?.theme ?: "system") { AppRoot() }
        }
    }
}
