package fr.lc4918.trailog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import fr.lc4918.trailog.data.LocalePrefs
import fr.lc4918.trailog.data.imp.ImportInbox
import fr.lc4918.trailog.ui.nav.AppRoot
import fr.lc4918.trailog.ui.theme.TrailogTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Doit être appelé avant super.onCreate() : affiche l'écran de démarrage système natif (thème
        // Theme.TrailogApp.Splash, manifeste) - aucun code/écran Compose ajouté dans la boucle, donc aucun
        // délai ni écran vide supplémentaire par rapport à l'écran de démarrage par défaut d'Android.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Fichier ouvert depuis ailleurs (cf. les filtres du manifeste et [ImportInbox]) : il attend dans
        // la boite, l'ecran lui demandera son dossier des qu'il sera compose.
        ImportInbox.offer(ImportInbox.urisOf(intent))
        val repo = (application as TrailogApp).repository
        setContent {
            val settings by repo.settingsFlow.collectAsState(initial = null)
            // La couleur des icônes de la barre de statut est gérée dans MainScreen.
            TrailogTheme(themePref = settings?.theme ?: "system") {
                // Tant que les réglages chargent, pas de vérification : on ne connaît pas encore le mode.
                AppRoot(autoCheckUpdates = settings?.updateCheckMode == "auto")
            }
        }
    }

    /**
     * Un fichier ouvert alors que l'application tournait deja.
     *
     * L'activite est en `singleTask` : Android la reutilise au lieu d'en empiler une seconde, et l'intention
     * arrive ici plutot que dans [onCreate]. Sans ce relais, ouvrir un GPX depuis un gestionnaire de
     * fichiers avec Trailog deja ouvert ne faisait que ramener la carte a l'ecran, sans rien importer.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ImportInbox.offer(ImportInbox.urisOf(intent))
    }
}
