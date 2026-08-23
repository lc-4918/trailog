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
 * La banniere "position figee" se referme.
 *
 * **Ce test existe a cause d'un appui sans effet.** La croix de la banniere etait branchee sur une lambda
 * vide : la banniere occupait le bas de la carte pour toute la duree du trou de reception - sous un
 * couvert, dans une gorge - et rien ne pouvait l'en deloger. Une alerte qu'on ne peut pas refermer finit
 * par se lire comme un decor, ce qui est exactement ce qu'une alerte ne doit pas devenir.
 *
 * Ce qui se decide ici tient en deux points : refermer dit "j'ai lu" et non "c'est faux" - le repere garde
 * sa couleur de peremption -, et le silence ne vaut que pour CETTE peremption.
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
 * Compose partage a l'echelle du processus. Ce qu'on y ecrit doit etre refermé derriere soi.
 */
@RunWith(RobolectricTestRunner::class)
class StaleNoticeTest {

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

    @Test fun `la croix referme la banniere`() {
        mesureRecue()
        laisserVieillir()
        assertTrue("le repere ment : la banniere est due", controls.staleNoticeVisible)

        modifie { controls.dismissStaleNotice() }
        assertFalse("la croix la referme", controls.staleNoticeVisible)
    }

    @Test fun `refermer ne rend pas la position bonne`() {
        mesureRecue()
        laisserVieillir()
        modifie { controls.dismissStaleNotice() }
        assertTrue(
            "le repere garde sa couleur de peremption : on a lu l'annonce, le fait reste",
            controls.positionStale,
        )
    }

    @Test fun `une peremption suivante s'annonce a nouveau`() {
        mesureRecue()
        laisserVieillir()
        modifie { controls.dismissStaleNotice() }

        // Le capteur repond : ce trou-ci est fini, et le suivant est un autre ennui.
        mesureRecue()
        modifie { controls.refreshStale() }
        assertFalse("une mesure fraiche n'a rien a annoncer", controls.staleNoticeVisible)

        laisserVieillir()
        assertTrue("se taire pour celle-la reviendrait a se taire pour toujours", controls.staleNoticeVisible)
    }

    @Test fun `un suivi relance n'herite pas du silence de l'ancien`() {
        mesureRecue()
        laisserVieillir()
        modifie { controls.dismissStaleNotice() }

        modifie {
            trackingState.value = false
            controls.onTrackingStopped()
            fixState.value = null
            controls.refreshStale()
        }

        modifie { trackingState.value = true }
        mesureRecue()
        laisserVieillir()
        assertTrue("le suivi d'avant a emporte son silence avec lui", controls.staleNoticeVisible)
    }
}
