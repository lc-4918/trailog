package fr.lc4918.trailog.map.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pavage des tuiles : c'est lui qui annonce a l'utilisateur combien de tuiles et de Mo il va telecharger. */
class TileMathTest {
    /** Bbox construite dans n'importe quel sens : [of] normalise, sinon tileCount renverrait 0. */
    @Test fun `of normalise les bornes quel que soit l'ordre des points`() {
        val a = Bbox.of(3.0, 44.0, 1.0, 42.0)
        assertEquals(Bbox(1.0, 42.0, 3.0, 44.0), a)
        assertEquals(a, Bbox.of(1.0, 42.0, 3.0, 44.0))
    }

    @Test fun `le monde entier tient en une tuile au zoom 0`() {
        assertEquals(1L, TileMath.tileCount(Bbox(-180.0, -85.0, 180.0, 85.0), 0))
    }

    /** A chaque zoom, la grille double dans les deux axes : le monde passe de 1 a 4 puis 16 tuiles. */
    @Test fun `le nombre de tuiles quadruple a chaque zoom`() {
        val monde = Bbox(-180.0, -85.0, 180.0, 85.0)
        assertEquals(4L, TileMath.tileCount(monde, 1))
        assertEquals(16L, TileMath.tileCount(monde, 2))
    }

    @Test fun `un point isole donne une seule tuile`() {
        assertEquals(1L, TileMath.tileCount(Bbox(2.11, 43.24, 2.11, 43.24), 14))
    }

    @Test fun `tilesFor rend exactement le nombre annonce par tileCount`() {
        val b = Bbox(2.0, 43.0, 2.5, 43.5)
        for (z in 8..13) {
            assertEquals("zoom $z", TileMath.tileCount(b, z), TileMath.tilesFor(b, z).size.toLong())
        }
    }

    @Test fun `tilesFor ne rend aucun doublon et reste au bon zoom`() {
        val t = TileMath.tilesFor(Bbox(2.0, 43.0, 2.5, 43.5), 12)
        assertEquals(t.size, t.toSet().size)
        assertTrue(t.all { it.third == 12 })
    }

    /** Les latitudes au-dela de la limite Mercator sont ramenees dans la plage, sans exploser en NaN. */
    @Test fun `latitudes polaires bornees a la limite Mercator`() {
        val n = TileMath.tileCount(Bbox(-10.0, -89.0, 10.0, 89.0), 5)
        assertTrue("doit rester fini et positif", n in 1..(32L * 32L))
    }

    @Test fun `total sur une plage de zooms egale la somme des niveaux`() {
        val b = Bbox(2.0, 43.0, 2.5, 43.5)
        val attendu = (10..13).sumOf { TileMath.tileCount(b, it) }
        assertEquals(attendu, TileMath.totalTileCount(b, 10, 13))
    }

    @Test fun `plage de zoom inversee ne compte aucune tuile`() {
        assertEquals(0L, TileMath.totalTileCount(Bbox(2.0, 43.0, 2.5, 43.5), 13, 10))
    }

    @Test fun `estimation de taille proportionnelle au nombre de tuiles`() {
        assertEquals(0L, TileMath.estimateSizeBytes(0))
        assertEquals(TileMath.estimateSizeBytes(1) * 10, TileMath.estimateSizeBytes(10))
    }

    @Test fun `formatSize choisit l'unite lisible`() {
        assertEquals("1 Ko", TileMath.formatSize(0))              // borne basse : jamais "0 Ko"
        assertEquals("500 Ko", TileMath.formatSize(500_000))
        assertTrue(TileMath.formatSize(12_300_000).endsWith(" Mo"))
        assertTrue(TileMath.formatSize(2_500_000_000).endsWith(" Go"))
    }
}
