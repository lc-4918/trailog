package fr.lc4918.trailog.ui.location

import android.content.Context
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.location.LocationHub
import fr.lc4918.trailog.ui.components.MapController
import java.time.Duration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

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

    /** Le capteur repond : une mesure de plus, recue a l'instant. */
    private fun mesureRecue() {
        val fix = LocationHub.Fix(
            lat = 45.18, lon = 5.72, accuracyM = 5f, bearingDeg = null, speedMps = null, altitudeM = null,
            timeMs = 1_700_000_000_000L, receivedAtMs = SystemClock.elapsedRealtime(),
        )
        fixState.value = fix
        controls.onFix(fix)
    }

    /** Le temps passe sans qu'aucune mesure n'arrive : c'est l'horloge qui perime le repere, pas lui. */
    private fun laisserVieillir() {
        ShadowSystemClock.advanceBy(Duration.ofMillis(LocationControls.STALE_AFTER_MS + 1_000L))
        controls.refreshStale()
    }

    @Test fun `la croix referme la banniere`() {
        mesureRecue()
        laisserVieillir()
        assertTrue("le repere ment : la banniere est due", controls.staleNoticeVisible)

        controls.dismissStaleNotice()
        assertFalse("la croix la referme", controls.staleNoticeVisible)
    }

    @Test fun `refermer ne rend pas la position bonne`() {
        mesureRecue()
        laisserVieillir()
        controls.dismissStaleNotice()
        assertTrue(
            "le repere garde sa couleur de peremption : on a lu l'annonce, le fait reste",
            controls.positionStale,
        )
    }

    @Test fun `une peremption suivante s'annonce a nouveau`() {
        mesureRecue()
        laisserVieillir()
        controls.dismissStaleNotice()

        // Le capteur repond : ce trou-ci est fini, et le suivant est un autre ennui.
        mesureRecue()
        controls.refreshStale()
        assertFalse("une mesure fraiche n'a rien a annoncer", controls.staleNoticeVisible)

        laisserVieillir()
        assertTrue("se taire pour celle-la reviendrait a se taire pour toujours", controls.staleNoticeVisible)
    }

    @Test fun `un suivi relance n'herite pas du silence de l'ancien`() {
        mesureRecue()
        laisserVieillir()
        controls.dismissStaleNotice()

        trackingState.value = false
        controls.onTrackingStopped()
        fixState.value = null
        controls.refreshStale()

        trackingState.value = true
        mesureRecue()
        laisserVieillir()
        assertTrue("le suivi d'avant a emporte son silence avec lui", controls.staleNoticeVisible)
    }
}
