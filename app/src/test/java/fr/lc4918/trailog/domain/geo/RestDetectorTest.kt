package fr.lc4918.trailog.domain.geo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'arret prolonge, qui fait espacer les demandes de position. Trop tot, la vitesse a pied lent se
 * figerait ; trop tard, ou jamais, la pause de midi viderait la batterie.
 */
class RestDetectorTest {

    private fun nord(m: Double) = 45.0 + m / 111_195.0

    /** Rend l'etat de repos apres chaque position ([metres], [ms]). */
    private fun suite(vararg pas: Pair<Double, Long>, precision: Float = 5f): List<Boolean> {
        var s = RestDetector.State()
        return pas.map { (m, t) ->
            val (n, repos) = RestDetector.step(s, nord(m), 6.0, precision, t)
            s = n
            repos
        }
    }

    @Test fun `une minute sur place, c'est le repos`() {
        val r = suite(0.0 to 0L, 3.0 to 30_000L, 1.0 to 59_000L, 2.0 to 61_000L)
        assertFalse(r[2])
        assertTrue(r[3])
    }

    /** A pied tres lent, on quitte l'ancre avant la minute : jamais au repos. */
    @Test fun `marcher lentement n'est pas un arret`() {
        val r = suite(*(0..40).map { i -> i * 0.6 to i * 2_000L }.toTypedArray())
        assertFalse(r.any { it })
    }

    /** Repartir : s'eloigner de l'ancre quitte le repos tout de suite. */
    @Test fun `repartir quitte le repos`() {
        val r = suite(0.0 to 0L, 1.0 to 70_000L, 25.0 to 80_000L)
        assertTrue(r[1])
        assertFalse(r[2])
    }

    /** Une position imprecise qui semble s'ecarter n'est que de l'incertitude : l'arret continue. */
    @Test fun `une position imprecise ne rompt pas l'arret`() {
        val r = suite(0.0 to 0L, 20.0 to 30_000L, 1.0 to 61_000L, precision = 30f)
        assertTrue(r[2])
    }
}
