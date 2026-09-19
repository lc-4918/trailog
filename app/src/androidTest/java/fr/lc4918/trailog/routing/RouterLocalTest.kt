package fr.lc4918.trailog.routing

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.lc4918.trailog.domain.model.RouteEngine
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * INSTRUMENTATION - l'aiguillage du calcul : sur le telephone quand les donnees couvrent le trajet, en
 * ligne sinon. Demande les donnees de la France (zone telechargee depuis les reglages) ; sans elles, saute.
 */
@RunWith(AndroidJUnit4::class)
class RouterLocalTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val velo = RoutingProfile.HYBRID_BIKE

    private fun calcul(pts: List<Pair<Double, Double>>) = runBlocking {
        Router.route(ctx, RouteEngine.BROUTER, Brouter.DEFAULT_URL, pts, velo, RoutingPrefs.defaultFor(velo))
    }

    @Test fun unTrajetCouvertSeCalculeSurLeTelephone() {
        val pts = listOf(45.188 to 5.724, 45.564 to 5.918)
        assumeTrue("donnees absentes", BrouterLocal.covers(ctx, pts))
        val r = calcul(pts)
        Log.i("RouterLocal", "couvert : $r")
        assertTrue(r is RouteOutcome.Done && r.offline)
    }

    @Test fun unTrajetHorsDesDonneesPartEnLigne() {
        val pts = listOf(45.188 to 5.724, 52.52 to 13.40)
        assumeTrue("donnees absentes", BrouterLocal.covers(ctx, pts.take(1)))
        assertFalse(BrouterLocal.covers(ctx, pts))
        val r = calcul(listOf(48.86 to 2.35, 48.80 to 2.13))   // Paris - Versailles : couvert
        assertTrue(r is RouteOutcome.Done && r.offline)
        val loin = calcul(pts)
        Log.i("RouterLocal", "Berlin : ${loin::class.simpleName} offline=${(loin as? RouteOutcome.Done)?.offline}")
        assertTrue(loin !is RouteOutcome.Done || !loin.offline)
    }
}
