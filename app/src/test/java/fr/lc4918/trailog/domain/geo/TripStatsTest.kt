package fr.lc4918.trailog.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les compteurs de la sortie : ce qui compte, ce qui ne compte pas, et pourquoi. Un compteur trop genereux
 * ne leve rien - il fait seulement croire a une journee plus dure qu'elle ne l'etait.
 */
class TripStatsTest {

    /** Un point a [m] metres au nord de 45 N, 6 E. */
    private fun nord(m: Double) = 45.0 + m / 111_195.0

    private fun marche(vararg pas: Triple<Double, Double?, Long>, precision: Float = 5f): Trip =
        pas.fold(Trip()) { t, (m, alt, ms) -> TripStats.add(t, nord(m), 6.0, precision, alt, ms) }

    @Test fun `la distance s'additionne en mouvement`() {
        val t = marche(Triple(0.0, null, 0L), Triple(20.0, null, 10_000L), Triple(40.0, null, 20_000L))
        assertEquals(40.0, t.distanceM, 0.5)
        assertEquals(20_000L, t.movingMs)
    }

    /** A l'arret, les positions tremblent autour de soi : ni distance, ni temps. */
    @Test fun `a l'arret, rien ne compte`() {
        val t = marche(Triple(0.0, null, 0L), Triple(2.0, null, 10_000L), Triple(1.0, null, 20_000L),
            Triple(2.5, null, 30_000L))
        assertEquals(0.0, t.distanceM, 0.01)
        assertEquals(0L, t.movingMs)
    }

    @Test fun `une position imprecise est ignoree`() {
        val t = marche(Triple(0.0, null, 0L), Triple(500.0, null, 10_000L), precision = 50f)
        assertEquals(0.0, t.distanceM, 0.01)
    }

    /** Une minute sans position : on ne relie pas les deux points par une ligne droite. */
    @Test fun `un trou dans les positions ne compte pas de distance`() {
        val t = marche(Triple(0.0, null, 0L), Triple(20.0, null, 10_000L), Triple(3000.0, null, 200_000L))
        assertEquals(20.0, t.distanceM, 0.5)
    }

    /** Le bruit d'altitude sous cinq metres ne fait pas de denivele ; un ecart franc, si. */
    @Test fun `le denivele ne compte qu'au-dela du seuil`() {
        val t = marche(
            Triple(0.0, 100.0, 0L), Triple(20.0, 103.0, 10_000L), Triple(40.0, 101.0, 20_000L),
            Triple(60.0, 104.0, 30_000L), Triple(80.0, 112.0, 40_000L), Triple(100.0, 104.0, 50_000L),
        )
        assertEquals(12.0, t.ascentM, 0.01)
        assertEquals(8.0, t.descentM, 0.01)
    }

    /**
     * Telephone pose sur une table : les positions tremblent de trois ou quatre metres, toutes les deux
     * secondes, pendant une minute. Rien ne doit compter - c'est le cas vu sur le terrain, ou la distance
     * montait toute seule.
     */
    @Test fun `le tremblement sur une table ne compte rien`() {
        val pas = (0..30).map { i -> Triple(if (i % 2 == 0) 0.0 else 3.5, null as Double?, i * 2_000L) }
        val t = marche(*pas.toTypedArray())
        assertEquals(0.0, t.distanceM, 0.0)
        assertEquals(0L, t.movingMs)
    }

    /** A pas lent, les positions passent une a une sous le seuil ; la distance se compte quand meme. */
    @Test fun `a pas lent, la distance se compte par morceaux`() {
        val pas = (0..30).map { i -> Triple(i * 1.0, null as Double?, i * 2_000L) }
        val t = marche(*pas.toTypedArray())
        assertEquals(30.0, t.distanceM, 6.0)
    }

    /** Cinq minutes debout, puis dix metres : le temps compte est celui des dix metres, pas la pause. */
    @Test fun `une pause ne compte pas comme temps en mouvement`() {
        val pas = (0..150).map { i -> Triple(0.5 * (i % 2), null as Double?, i * 2_000L) } +
            Triple(10.0, null as Double?, 302_000L)
        val t = marche(*pas.toTypedArray())
        assertEquals(10.0, t.distanceM, 0.5)
        assertTrue("au plus le temps du pas lent", t.movingMs <= (10.0 / TripStats.MIN_MOVING_MPS * 1000).toLong())
    }

    /** Une position moins precise elargit le seuil d'autant : son ecart peut n'etre que de l'incertitude. */
    @Test fun `une position imprecise elargit le seuil`() {
        val t = marche(Triple(0.0, null, 0L), Triple(12.0, null, 2_000L), precision = 15f)
        assertEquals(0.0, t.distanceM, 0.0)
    }

    /**
     * Telephone pose sur une table : l'altitude du GPS varie de plus de cinq metres, la position non. Ni
     * D+ ni D- - c'est le cas vu sur le terrain, apres une remise a zero.
     */
    @Test fun `l'altitude qui tremble sur place ne fait pas de denivele`() {
        val pas = (0..30).map { i -> Triple(if (i % 2 == 0) 0.0 else 2.0, if (i % 3 == 0) 100.0 else 112.0, i * 2_000L) }
        val t = marche(*pas.toTypedArray())
        assertEquals(0.0, t.ascentM, 0.0)
        assertEquals(0.0, t.descentM, 0.0)
    }
}
