package fr.lc4918.trailog.domain.geo

import fr.lc4918.trailog.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMathTest {

    @Test fun haversine_oneDegreeLat_isAbout111km() {
        val d = TrackMath.haversine(0.0, 0.0, 0.0, 1.0)
        assertEquals(111195.0, d, 200.0)   // ~111,2 km, tolérance 200 m
    }

    @Test fun compute_ascentDescent_andDuration() {
        // montée 0->100 m puis descente 100->50 m, 1 point / 60 s, vitesse > seuil d'arrêt
        val base = 45.0; val lon0 = 6.0
        val eles = listOf(0.0, 50.0, 100.0, 75.0, 50.0)
        val pts = eles.mapIndexed { i, e ->
            TrackPoint(lon = lon0 + i * 0.001, lat = base, ele = e, timeMs = i * 60_000L)
        }
        val c = TrackMath.compute(pts, ignoreStops = false, maxPoints = 0)
        assertTrue(c.hasZ); assertTrue(c.hasTime)
        assertEquals(100.0, c.stats.ascent, 1.0)      // +50 +50
        assertEquals(50.0, c.stats.descent, 1.0)      // -25 -25
        assertEquals(100.0, c.stats.max, 0.1)
        assertEquals(0.0, c.stats.min, 0.1)
        assertEquals(240.0, c.stats.duration ?: -1.0, 0.1)  // 4 intervalles * 60 s
    }

    @Test fun compute_movingTime_ignoresStops() {
        // 3 points : 2e point quasi immobile pendant 600 s -> ignoré en temps de mouvement
        val pts = listOf(
            TrackPoint(6.000, 45.0, 10.0, 0L),
            TrackPoint(6.010, 45.0, 12.0, 60_000L),       // ~785 m en 60 s : roule
            TrackPoint(6.0100001, 45.0, 12.0, 660_000L),  // ~0 m en 600 s : arrêt
        )
        val moving = TrackMath.compute(pts, ignoreStops = true, stopSpeed = 0.5, maxPoints = 0)
        val all = TrackMath.compute(pts, ignoreStops = false, maxPoints = 0)
        assertEquals(60.0, moving.stats.duration ?: -1.0, 1.0)   // seul le 1er intervalle compte
        assertEquals(660.0, all.stats.duration ?: -1.0, 1.0)
    }
}
