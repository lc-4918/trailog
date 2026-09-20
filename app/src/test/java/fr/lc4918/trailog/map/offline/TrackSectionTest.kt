package fr.lc4918.trailog.map.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * La portion de trace a emporter. Une faute ici ne se voit pas a l'ecran : elle se constate la-bas, sur
 * le kilometre ou la carte s'arrete trop tot.
 */
class TrackSectionTest {

    /** Une trace plein nord de 10 km, un point tous les kilometres. */
    private val trace = (0..10).map { i -> 6.0 to 45.0 + i * 1000.0 / 111_195.0 }

    @Test fun `la longueur est celle de la trace`() {
        assertEquals(10_000.0, TrackSection.length(trace), 5.0)
    }

    @Test fun `toute la trace se rend telle quelle`() {
        assertSame(trace, TrackSection.slice(trace, 0.0, 10_000.0))
    }

    /** Les bouts tombent entre deux points : ils sont interpoles, pas arrondis au point voisin. */
    @Test fun `une portion commence et finit a son kilometrage`() {
        val s = TrackSection.slice(trace, 2_500.0, 6_500.0)
        assertEquals(4_000.0, TrackSection.length(s), 5.0)
        assertEquals(45.0 + 2_500.0 / 111_195.0, s.first().second, 1e-7)
        assertEquals(45.0 + 6_500.0 / 111_195.0, s.last().second, 1e-7)
        assertEquals("les points entre les deux sont gardes", 6, s.size)
    }

    @Test fun `une portion au-dela de la trace est ramenee a ses bornes`() {
        val s = TrackSection.slice(trace, 8_000.0, 50_000.0)
        assertEquals(2_000.0, TrackSection.length(s), 5.0)
    }

    @Test fun `l'emprise est celle de la portion`() {
        val b = TrackSection.bboxOf(TrackSection.slice(trace, 2_000.0, 3_000.0))
        assertEquals(45.0 + 2_000.0 / 111_195.0, b.south, 1e-7)
        assertEquals(45.0 + 3_000.0 / 111_195.0, b.north, 1e-7)
    }
}
