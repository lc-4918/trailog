package fr.lc4918.trailog.ui.location

import android.content.Context
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.location.LocationHub
import fr.lc4918.trailog.ui.components.MapController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Le repere qui ment : quand la carte le passe au gris.
 *
 * Un repere fige est visuellement identique a un repere juste - c'est le meme accident que la disparition,
 * sous une forme plus sournoise : on regarde un point qui affirme ou l'on est, et il a raison depuis dix
 * minutes. Passe [LocationControls.STALE_AFTER_MS] sans mesure, il change donc de couleur.
 *
 * **Ce que ce fichier ne teste plus.** Une banniere annoncait la meme chose en bas de la carte - "Position
 * figee depuis x" - et la moitie de ces cas portait sur sa croix. Elle a ete retiree : un trou de reception
 * dure ce qu'il dure - une gorge, un couvert, un tunnel - et une alerte qu'on ne peut ni corriger ni eviter
 * occupait le bas de la carte pour rien, jusqu'a se lire comme un decor. Le fait reste dit la ou il se
 * produit, sur le repere lui-meme.
 *
 * ---
 *
 * **Deux precautions, chacune payee d'une suite de tests bloquee pendant treize minutes.**
 *
 * 1. **Le temps se simule sur la MESURE, jamais sur l'horloge.** Une premiere version avancait l'horloge
 *    du bac a sable avec `ShadowSystemClock.advanceBy`. Elle est partagee avec toutes les classes qui
 *    suivent dans la meme JVM. La peremption se mesurant par `maintenant - receivedAtMs`, il suffit de
 *    vieillir la mesure : c'est le meme calcul, et ca ne deborde sur personne.
 *
 * 2. **Toute ecriture d'etat passe par [modifie].** Ce test pilote des `mutableStateOf` - les siens, et
 *    ceux que porte [LocationControls] - hors de toute composition. Ecrites nues, ces modifications
 *    restent en attente dans le snapshot global, lui aussi partage : tout test d'interface qui suit y lit
 *    "il reste du travail a faire", et son attente d'inactivite tourne jusqu'a expirer au bout de soixante
 *    secondes. Treize tests y sont passes. Un snapshot applique les publie et ne laisse rien derriere.
 *
 * Les deux fautes ont le meme visage : cette classe ne compose rien, mais elle touche a des etats que
 * Compose partage a l'echelle du processus. Ce qu'on y ecrit doit etre referme derriere soi.
 */
@RunWith(RobolectricTestRunner::class)
class PositionStaleTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val trackingState = mutableStateOf(true)
    private val fixState = mutableStateOf<LocationHub.Fix?>(null)
    private val controls = LocationControls(
        ctx = ctx,
        controller = MapController(),
        locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager,
        trackingState = trackingState,
        fixState = fixState,
    )

    /** Toute mutation d'etat Compose passe par ici : le snapshot publie ce qu'on y ecrit (cf. l'en-tete). */
    private fun <R> modifie(bloc: () -> R): R = Snapshot.withMutableSnapshot(bloc)

    /** Le capteur repond : une mesure de plus, recue a l'instant. */
    private fun mesureRecue() = modifie {
        val fix = LocationHub.Fix(
            lat = 45.18, lon = 5.72, accuracyM = 5f, bearingDeg = null, speedMps = null, altitudeM = null,
            timeMs = 1_700_000_000_000L, receivedAtMs = SystemClock.elapsedRealtime(),
        )
        fixState.value = fix
        controls.onFix(fix)
        controls.refreshStale()
    }

    /**
     * Le temps passe sans qu'aucune mesure n'arrive.
     *
     * La mesure en place recule dans le passe, ce qui revient exactement a laisser l'horloge avancer -
     * l'age se calcule par difference. [LocationControls.onFix] n'est deliberement PAS rappele : aucune
     * mesure nouvelle n'arrive, c'est tout l'objet de la peremption.
     */
    private fun laisserVieillir() = modifie {
        val fix = fixState.value ?: error("il faut une mesure avant de la laisser vieillir")
        fixState.value = fix.copy(receivedAtMs = fix.receivedAtMs - (LocationControls.STALE_AFTER_MS + 1_000L))
        controls.refreshStale()
    }

    @Test fun `une mesure fraiche ne perime rien`() {
        mesureRecue()
        assertFalse(controls.positionStale)
    }

    @Test fun `sans mesure, le repere finit par mentir`() {
        mesureRecue()
        laisserVieillir()
        assertTrue("passe le delai, le repere affirme une position qu'il ne sait plus", controls.positionStale)
    }

    /** Le capteur revient : le repere redit la verite, et reprend sa couleur. */
    @Test fun `une mesure nouvelle rend le repere honnete`() {
        mesureRecue()
        laisserVieillir()
        mesureRecue()
        assertFalse(controls.positionStale)
    }

    /** Suivi arrete : il n'y a plus de repere du tout, donc plus rien a perimer. Sans cette remise a zero,
     *  le gris survivrait a la disparition de ce qu'il colorait. */
    @Test fun `un suivi arrete n'a plus de repere a perimer`() {
        mesureRecue()
        laisserVieillir()
        modifie {
            trackingState.value = false
            controls.onTrackingStopped()
            fixState.value = null
            controls.refreshStale()
        }
        assertFalse(controls.positionStale)
    }
}
