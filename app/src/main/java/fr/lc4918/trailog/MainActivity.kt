package fr.lc4918.trailog

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import fr.lc4918.trailog.data.LocalePrefs
import fr.lc4918.trailog.data.ThemePrefs
import fr.lc4918.trailog.data.imp.ImportInbox
import fr.lc4918.trailog.location.AlertAnswer
import fr.lc4918.trailog.location.TrackWatch
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
        answerOffTrackAlert(intent)
        val repo = (application as TrailogApp).repository
        // Lu de façon synchrone, avant la première composition : le Flow des réglages (Room) ne livre sa
        // première valeur qu'un instant plus tard, et sans ce repli la première image affichait le thème
        // système avant de basculer sur celui choisi (cf. ThemePrefs).
        val cachedTheme = ThemePrefs.get(this)
        setContent {
            val settings by repo.settingsFlow.collectAsState(initial = null)
            // La couleur des icônes de la barre de statut est gérée dans MainScreen.
            TrailogTheme(themePref = settings?.theme ?: cachedTheme) {
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
        answerOffTrackAlert(intent)
    }

    /**
     * L'alerte d'eloignement amene la carte a l'ecran - et de deux facons, qui ne veulent pas dire la
     * meme chose (cf. AlertAnswer).
     *
     * **L'ecran s'allume et l'application se pose par-dessus le verrouillage**, dans les deux cas :
     * l'alerte arrive telephone en poche, ecran eteint, et une alerte qu'il faut deverrouiller pour lire
     * arrive apres le mauvais embranchement. C'est l'intention plein ecran de la notification qui nous
     * amene ici (cf. LocationService.postOffTrackAlert) ; ces deux drapeaux sont ce qui, de notre cote,
     * permet a Android de le faire. Un verrouillage securise reste securise : il montre son ecran, pas la
     * carte.
     *
     * **L'alerte ne se tait qu'au TAP.** La sonnerie boucle jusqu'a ce qu'on reponde, et taper la
     * notification EST la reponse - on regarde la carte, on sait qu'on est loin (cf. TrackWatch.silence).
     * Le reveil automatique, lui, ne repond rien : c'est l'alerte qui allume l'ecran, et se taire la
     * reviendrait a se repondre a soi-meme - l'ecran s'allumait sur une carte sans banniere et sans
     * sonnerie, la cloche rouge pour seul indice. La sonnerie continue donc, sous les yeux de celui
     * qu'elle appelle, jusqu'a la banniere, la notification, "Taire", ou le retour sur la trace.
     */
    private fun answerOffTrackAlert(intent: Intent?) {
        val action = intent?.action
        if (!AlertAnswer.wakesScreen(action)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        if (AlertAnswer.silences(action)) TrackWatch.silence()
    }
}
