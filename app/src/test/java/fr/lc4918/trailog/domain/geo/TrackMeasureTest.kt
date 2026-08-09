package fr.lc4918.trailog.domain.geo

import fr.lc4918.trailog.domain.model.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMeasureTest {

    /** Trace est-ouest a latitude 45, un point tous les 0,001 deg (~78,7 m). */
    private fun track(n: Int = 5, lat: Double = 45.0): List<Sample> {
        var cum = 0.0
        return (0 until n).map { i ->
            if (i > 0) cum += TrackMath.haversine(6.0 + (i - 1) * 0.001, lat, 6.0 + i * 0.001, lat)
            Sample(x = cum, z = 0.0, slope = 0.0, t = null, lon = 6.0 + i * 0.001, lat = lat)
        }
    }

    @Test fun project_offTrack_fallsOnTheOrthogonalFoot() {
        val s = track()
        // Point pose au nord du 3e sommet : le pied de la perpendiculaire est ce sommet meme.
        val p = TrackMeasure.project(s, 6.002, 45.0005)!!
        assertEquals(6.002, p.lon, 1e-6)
        assertEquals(45.0, p.lat, 1e-6)
        assertEquals(s[2].x, p.alongM, 1.0)
        assertEquals(55.0, p.awayM, 5.0)          // 0,0005 deg de latitude ~ 55,6 m
    }

    @Test fun project_betweenTwoSamples_interpolatesKilometrage() {
        val s = track()
        val p = TrackMeasure.project(s, 6.0005, 45.0)!!
        assertEquals((s[0].x + s[1].x) / 2, p.alongM, 1.0)
        assertEquals(0.0, p.awayM, 1.0)
    }

    @Test fun project_beyondTheEnd_fallsOnTheEnd() {
        val s = track()
        // Loin derriere le depart, et loin devant l'arrivee : on retombe sur l'extremite la plus proche.
        val before = TrackMeasure.project(s, 5.990, 45.0)!!
        assertEquals(s.first().lon, before.lon, 1e-9)
        assertEquals(0.0, before.alongM, 1e-6)
        val after = TrackMeasure.project(s, 6.020, 45.0)!!
        assertEquals(s.last().lon, after.lon, 1e-9)
        assertEquals(s.last().x, after.alongM, 1e-6)
    }

    @Test fun project_picksTheNearestOfTwoTracks() {
        val north = track(lat = 45.0)
        val south = track(lat = 44.99)
        val lat = 44.9999   // tout proche de la trace nord
        val toNorth = TrackMeasure.project(north, 6.002, lat)!!
        val toSouth = TrackMeasure.project(south, 6.002, lat)!!
        assertTrue(toNorth.awayM < toSouth.awayM)
    }

    @Test fun project_emptyTrack_isNull() {
        assertNull(TrackMeasure.project(emptyList(), 6.0, 45.0))
    }

    @Test fun portion_hasAnOddCount_soItsCenterIsTheMiddleOfTheMeasure() {
        val s = track()
        val p = TrackMeasure.portion(s, 0.0, s.last().x)
        assertTrue(p.size % 2 == 1)
        assertEquals(s.first().lon, p.first().first, 1e-9)
        assertEquals(s.last().lon, p.last().first, 1e-9)
        assertEquals(6.002, p[p.size / 2].first, 1e-5)     // milieu des ~315 m parcourus
        assertEquals(45.0, p[p.size / 2].second, 1e-9)
    }

    @Test fun portion_ofASubRange_staysWithinIt() {
        val s = track()
        val p = TrackMeasure.portion(s, s[1].x, s[3].x)
        assertEquals(6.001, p.first().first, 1e-6)
        assertEquals(6.003, p.last().first, 1e-6)
        assertEquals(6.002, p[p.size / 2].first, 1e-6)
    }

    @Test fun portion_pointsAreEvenlySpacedAlongTheTrack() {
        val s = track()
        val p = TrackMeasure.portion(s, 0.0, s.last().x)
        val step = TrackMath.haversine(p[0].first, p[0].second, p[1].first, p[1].second)
        for (i in 1 until p.size - 1) {
            val d = TrackMath.haversine(p[i].first, p[i].second, p[i + 1].first, p[i + 1].second)
            assertEquals(step, d, 0.5)
        }
        assertTrue(step <= 25.0)
    }

    @Test fun portion_takenBackwards_isTheSameAsForwards() {
        val s = track()
        assertEquals(TrackMeasure.portion(s, s[1].x, s[3].x), TrackMeasure.portion(s, s[3].x, s[1].x))
    }

    @Test fun portion_beyondTheTrack_isClampedToItsEnds() {
        val s = track()
        val p = TrackMeasure.portion(s, -100.0, s.last().x + 100.0)
        assertEquals(s.first().lon, p.first().first, 1e-9)
        assertEquals(s.last().lon, p.last().first, 1e-9)
    }

    @Test fun portion_ofALongMeasure_staysBounded() {
        // 5000 points de ~78,7 m : le pas de 25 m donnerait plus de 15 000 points, la liste s'etire.
        val p = TrackMeasure.portion(track(n = 5000), 0.0, 393_000.0)
        assertEquals(801, p.size)
    }

    @Test fun portion_ofAnEmptyTrack_isEmpty() {
        assertTrue(TrackMeasure.portion(emptyList(), 0.0, 100.0).isEmpty())
    }
}
