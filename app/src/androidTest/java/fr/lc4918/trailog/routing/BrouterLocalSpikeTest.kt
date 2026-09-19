package fr.lc4918.trailog.routing

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ESSAI - BRouter sur l'appareil : temps, memoire, et ecart avec le meme calcul fait en ligne.
 *
 * Demande le carre E5_N45 depose a la main dans [BrouterLocal.segmentDir] ; sans lui, l'essai est saute.
 * Les resultats partent dans le journal, sous l'etiquette "BrouterSpike".
 */
@RunWith(AndroidJUnit4::class)
class BrouterLocalSpikeTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private class Trajet(val nom: String, val profil: RoutingProfile, val points: List<Pair<Double, Double>>)

    private val trajets = listOf(
        Trajet("Grenoble-Chambery VTC", RoutingProfile.HYBRID_BIKE, listOf(45.188 to 5.724, 45.564 to 5.918)),
        Trajet("Grenoble-Annecy route", RoutingProfile.ROAD_BIKE, listOf(45.188 to 5.724, 45.899 to 6.129)),
        Trajet("Chamonix-Argentiere pied", RoutingProfile.FOOT, listOf(45.924 to 6.869, 45.980 to 6.927)),
        Trajet("Grenoble-Geneve VTC", RoutingProfile.HYBRID_BIKE, listOf(45.188 to 5.724, 46.204 to 6.143)),
        Trajet("Vercors VTT 3 etapes", RoutingProfile.MOUNTAIN_BIKE,
            listOf(45.070 to 5.550, 45.175 to 5.543, 45.125 to 5.590)),
    )

    private fun texte(p: RoutingProfile) =
        ctx.assets.open(BrouterProfile.assetOf(p)).use { it.readBytes().decodeToString() }

    @Test fun essai() = runBlocking {
        assumeTrue("carre E5_N45 absent", BrouterLocal.covers(ctx, trajets.first().points))
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        Log.i(TAG, "memoryClass=${am.memoryClass} large=${am.largeMemoryClass} maxHeap=${Runtime.getRuntime().maxMemory() / 1_048_576} Mo")
        var reussis = 0
        for (t in trajets) {
            val prefs = RoutingPrefs.defaultFor(t.profil)
            val rt = Runtime.getRuntime()
            System.gc()
            val avant = rt.totalMemory() - rt.freeMemory()
            val t0 = SystemClock.elapsedRealtime()
            val local = BrouterLocal.route(ctx, t.points, t.profil, prefs, texte(t.profil), am.memoryClass)
            val ms = SystemClock.elapsedRealtime() - t0
            val apres = rt.totalMemory() - rt.freeMemory()
            val l = (local.outcome as? RouteOutcome.Done)?.result
            val t1 = SystemClock.elapsedRealtime()
            val enLigne = Brouter.route(Brouter.DEFAULT_URL, t.points, t.profil, prefs, texte(t.profil))
            val msLigne = SystemClock.elapsedRealtime() - t1
            val o = (enLigne as? RouteOutcome.Done)?.result
            Log.i(TAG, "${t.nom}: local ${ms} ms, tas +${(apres - avant) / 1_048_576} Mo, " +
                "${l?.meters?.toInt()} m ${l?.seconds?.toInt()} s ${l?.points?.size} pts err=${local.error} | " +
                "en ligne ${msLigne} ms ${o?.meters?.toInt()} m ${o?.points?.size} pts")
            if (l != null) reussis++
        }
        // Hors des donnees presentes, le moteur doit le DIRE, et non rendre un trajet vide.
        val dehors = BrouterLocal.route(ctx, listOf(45.10 to 5.55, 44.90 to 5.50), RoutingProfile.MOUNTAIN_BIKE,
            RoutingPrefs.defaultFor(RoutingProfile.MOUNTAIN_BIKE), texte(RoutingProfile.MOUNTAIN_BIKE), am.memoryClass)
        Log.i(TAG, "hors donnees: ${dehors.outcome} err=${dehors.error}")
        assertTrue(dehors.outcome is RouteOutcome.NoRoute)
        assertTrue("aucun trajet calcule", reussis > 0)
    }

    private companion object { const val TAG = "BrouterSpike" }
}
