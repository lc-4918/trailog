package fr.lc4918.trailog.poi

import fr.lc4918.trailog.domain.model.PoiGroup
import fr.lc4918.trailog.map.offline.Bbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La grille des points d'interet : l'unite de la requete, du cache et de l'affichage.
 *
 * Une faute ici ne leve rien : une cellule mal calculee rend une bande de carte sans lieux, ou range un
 * lieu dans une cellule qu'on ne relira jamais.
 */
class PoiCellsTest {

    /** Le plancher et non la troncature : a l'ouest de Greenwich, tronquer rangerait -0,05 et 0,05 dans
     *  la meme cellule. */
    @Test fun `une longitude negative tombe dans sa propre cellule`() {
        assertEquals(PoiCell(-1, 424), PoiCells.of(-0.05, 42.45))
        assertEquals(PoiCell(0, 424), PoiCells.of(0.05, 42.45))
        assertEquals(PoiCell(-25, 424), PoiCells.of(-2.45, 42.46))
    }

    /** Un point est dans l'emprise de sa cellule. */
    @Test fun `une cellule contient ses points`() {
        listOf(-2.45 to 42.46, 1.444 to 43.604, 13.4 to 52.52, 55.45 to -20.88).forEach { (lon, lat) ->
            val b = PoiCells.of(lon, lat).bbox
            assertTrue("$lon $lat", lon >= b.west && lon < b.east && lat >= b.south && lat < b.north)
        }
    }

    /** Les cellules d'une emprise la pavent entiere. */
    @Test fun `les cellules couvrent l'emprise`() {
        val box = Bbox(west = 1.33, south = 43.55, east = 1.52, north = 43.66)
        val cs = PoiCells.covering(box)
        assertEquals(6, cs.size)   // 3 en longitude, 2 en latitude
        assertTrue(cs.minOf { it.bbox.west } <= box.west && cs.maxOf { it.bbox.east } >= box.east)
        assertTrue(cs.minOf { it.bbox.south } <= box.south && cs.maxOf { it.bbox.north } >= box.north)
    }

    /** La cle s'ecrit en base comme la migration la calcule : "ix_iy". */
    @Test fun `la cle est celle que la base connait`() {
        assertEquals("-25_424", PoiCells.of(-2.45, 42.46).key)
    }

    @Test fun `les cellules se trient du centre vers les bords`() {
        val cs = PoiCells.covering(Bbox(west = 1.0, south = 43.0, east = 1.5, north = 43.5))
        val tri = PoiCells.byDistance(cs, 1.25, 43.25)
        assertEquals(PoiCells.of(1.25, 43.25), tri.first())
    }

    /** Une requete par cellule et par source, qui porte tous les groupes que cette source y sert. */
    @Test fun `les unites d'une cellule se regroupent par source`() {
        val grenoble = PoiCells.of(5.72, 45.19)
        val units = PoiCells.units(listOf(grenoble), PoiGroup.entries.toSet(), complement = true)
        assertEquals(4, units.size)
        val jobs = PoiCells.jobs(units)
        assertEquals(2, jobs.size)
        assertEquals(setOf(PoiGroup.FOOD, PoiGroup.PRACTICAL), jobs.single { it.source == PoiSource.OSM }.groups)
        assertEquals(setOf(PoiGroup.LODGING, PoiGroup.LEISURE),
            jobs.single { it.source == PoiSource.DATATOURISME }.groups)
    }

    /** Hors de France, une seule requete pour tout. */
    @Test fun `a Logrono, une requete par cellule`() {
        val logrono = PoiCells.of(-2.45, 42.46)
        val jobs = PoiCells.jobs(PoiCells.units(listOf(logrono), PoiGroup.entries.toSet(), complement = true))
        assertEquals(1, jobs.size)
        assertEquals(PoiSource.OSM, jobs.single().source)
        assertEquals(PoiGroup.entries.toSet(), jobs.single().groups)
    }
}
