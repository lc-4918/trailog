package fr.lc4918.trailog.domain.geo

import fr.lc4918.trailog.domain.model.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reconnaitre qu'on suit une trace, et qu'on l'a quittee. Traverser une trace n'est pas la suivre, et
 * s'accrocher a la mauvaise annoncerait un restant faux sans que rien ne le dise.
 */
class AutoFollowTest {

    /** Une trace plein nord de [longueur] metres, a la longitude [lon]. */
    private fun trace(id: Long, lon: Double = 6.0, longueur: Double = 1000.0): AutoFollow.Candidate {
        val pts = (0..(longueur / 10).toInt()).map { i ->
            Sample(x = i * 10.0, z = 0.0, slope = 0.0, t = null, lon = lon, lat = 45.0 + i * 10.0 / 111_195.0)
        }
        return AutoFollow.Candidate(id, "trace $id", 0, 1, pts,
            pts.minOf { it.lon }, pts.minOf { it.lat }, pts.maxOf { it.lon }, pts.maxOf { it.lat })
    }

    private fun nord(m: Double) = 45.0 + m / 111_195.0

    /** Marche le long de la longitude [lon], aux distances [pas], et rend ce que la detection en a fait. */
    private fun marche(lon: Double, pas: List<Double>, traces: List<AutoFollow.Candidate>): AutoFollow.Match? {
        var etat = AutoFollow.Detection()
        for (m in pas) {
            val (suivant, trouve) = AutoFollow.detect(etat, nord(m), lon, traces)
            if (trouve != null) return trouve
            etat = suivant
        }
        return null
    }

    @Test fun `marcher sur une trace la fait reconnaitre`() {
        val m = marche(6.0, listOf(100.0, 150.0, 200.0, 250.0), listOf(trace(1)))
        assertNotNull(m)
        assertEquals(1L, m!!.candidate.id)
        assertEquals(1, m.direction)
    }

    @Test fun `la parcourir a l'envers se reconnait dans l'autre sens`() {
        val m = marche(6.0, listOf(800.0, 750.0, 700.0, 650.0), listOf(trace(1)))
        assertEquals(-1, m!!.direction)
    }

    /** Trois positions sur place, sur la trace : on n'a pas avance dessus, on ne la suit pas encore. */
    @Test fun `rester sur place ne suffit pas`() {
        assertNull(marche(6.0, listOf(100.0, 102.0, 101.0, 103.0, 100.0), listOf(trace(1))))
    }

    /** Loin de toute trace : rien. */
    @Test fun `loin de toute trace, rien n'est reconnu`() {
        assertNull(marche(6.01, listOf(100.0, 150.0, 200.0, 250.0), listOf(trace(1))))
    }

    /** Deux traces paralleles a 20 m l'une de l'autre : la plus proche l'emporte. */
    @Test fun `la plus proche des traces l'emporte`() {
        val decalage = 20.0 / (111_195.0 * Math.cos(Math.toRadians(45.0)))
        val m = marche(6.0, listOf(100.0, 150.0, 200.0, 250.0), listOf(trace(2, 6.0 + decalage), trace(1)))
        assertEquals(1L, m!!.candidate.id)
    }

    @Test fun `le sens ne change que sur un deplacement franc`() {
        assertEquals(1, AutoFollow.direction(1, 100.0, 102.0))
        assertEquals(1, AutoFollow.direction(1, 100.0, 97.0))
        assertEquals(-1, AutoFollow.direction(1, 100.0, 90.0))
        assertEquals(1, AutoFollow.direction(1, null, 50.0))
    }

    /** Cloche eteinte : une mesure aberrante ne fait pas lacher la trace, deux de suite si. */
    @Test fun `la trace se lache apres deux positions hors seuil`() {
        val (un, lache1) = AutoFollow.leave(0, 150.0, 100.0)
        assertFalse(lache1)
        val (_, lache2) = AutoFollow.leave(un, 160.0, 100.0)
        assertTrue(lache2)
        val (remis, _) = AutoFollow.leave(un, 20.0, 100.0)
        assertEquals(0, remis)
    }
}
