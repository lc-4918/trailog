package fr.lc4918.trailog.watch

import fr.lc4918.trailog.map.offline.TileMath
import fr.lc4918.trailog.watch.WatchTiles.Detail
import fr.lc4918.trailog.watch.WatchTiles.WatchTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTilesTest {

    /** Valeurs croisees avec l'application montre (TileMathTest.mc) : meme lieu, memes numeros de tuile. */
    @Test fun `watch tile numbering matches the watch app at Chamrousse`() {
        val tiles = WatchTiles.tilesAlong(listOf(5.877 to 45.121), radiusMeters = 0.0, detail = Detail.STANDARD)
        assertEquals(listOf(WatchTile(47853, 33295)), tiles)
    }

    @Test fun `a finer level numbers the same place on a finer grid`() {
        val point = listOf(5.877 to 45.121)
        val fine = WatchTiles.tilesAlong(point, 0.0, Detail.FINE).single()
        val veryFine = WatchTiles.tilesAlong(point, 0.0, Detail.VERY_FINE).single()
        assertEquals(WatchTile(67675, 47087), fine)
        // Un demi-zoom de plus : les numeros sont multiplies par racine de 2.
        assertEquals(WatchTile(95707, 66591), veryFine)
    }

    @Test fun `the corridor is inside the offline corridor of the next zoom and tighter`() {
        // A zoom entier la grille de la montre est celle des tuiles standard du zoom suivant. Le couloir du
        // telechargement hors ligne dilate la bande par un nombre entier de tuiles et couvre plus large : celui de
        // la montre garde exactement les tuiles dont le carre touche la bande, donc il est contenu dans l'autre.
        val track = listOf(5.85 to 45.10, 5.90 to 45.13, 5.95 to 45.12)
        val offline = TileMath.tilesAlong(track, 17, 500.0).map { (x, y, _) -> WatchTile(x, y) }.toSet()
        val watch = WatchTiles.tilesAlong(track, 500.0, Detail.FINE)
        assertTrue(offline.containsAll(watch))
        assertTrue(watch.size < offline.size)
    }

    @Test fun `every point of the track lies in a tile of the corridor`() {
        val track = listOf(5.85 to 45.10, 5.90 to 45.13, 5.95 to 45.12)
        for (detail in Detail.entries) {
            val tiles = WatchTiles.tilesAlong(track, 0.0, detail).toSet()
            for ((longitude, latitude) in track) {
                val tile = WatchTiles.tilesAlong(listOf(longitude to latitude), 0.0, detail).single()
                assertTrue(tile in tiles)
            }
        }
    }

    @Test fun `a point at the radius from the track is covered`() {
        // Un point a 400 m au nord de la trace tombe dans une tuile du couloir de 500 m, pas dans celui de 100 m.
        val track = listOf(5.90 to 45.10, 5.92 to 45.10)
        val farPoint = listOf(5.91 to 45.10 + 400.0 / 111_320.0)
        val tile = WatchTiles.tilesAlong(farPoint, 0.0, Detail.FINE).single()
        assertTrue(tile in WatchTiles.tilesAlong(track, 500.0, Detail.FINE))
    }

    @Test fun `a finer level needs more tiles for the same track`() {
        val track = listOf(5.85 to 45.10, 5.90 to 45.13, 5.95 to 45.12)
        val standard = WatchTiles.tilesAlong(track, 500.0, Detail.STANDARD).size
        val fine = WatchTiles.tilesAlong(track, 500.0, Detail.FINE).size
        val veryFine = WatchTiles.tilesAlong(track, 500.0, Detail.VERY_FINE).size
        assertTrue(standard < fine && fine < veryFine)
        // Un zoom entier de plus donne de l'ordre de trois a quatre fois plus de tuiles ; un demi-niveau, environ
        // deux fois : moins que le carre du facteur d'echelle pour une trace courte, qui a moins de pertes de bord.
        assertTrue(veryFine.toDouble() / standard in 2.8..4.4)
        assertTrue(fine.toDouble() / standard in 1.5..2.5)
    }

    @Test fun `a wider corridor needs more tiles`() {
        val track = listOf(5.85 to 45.10, 5.95 to 45.12)
        assertTrue(WatchTiles.tilesAlong(track, 250.0, Detail.FINE).size < WatchTiles.tilesAlong(track, 2000.0, Detail.FINE).size)
    }

    @Test fun `tiles come by row then by column without duplicates`() {
        val tiles = WatchTiles.tilesAlong(listOf(5.85 to 45.10, 5.95 to 45.12), 500.0, Detail.FINE)
        assertEquals(tiles.size, tiles.toSet().size)
        assertEquals(tiles.sortedWith(compareBy({ it.y }, { it.x })), tiles)
    }

    @Test fun `no points give no tiles`() {
        assertTrue(WatchTiles.tilesAlong(emptyList(), 500.0, Detail.FINE).isEmpty())
    }

    @Test fun `ground resolution improves with the level`() {
        val standard = WatchTiles.metersPerPixel(45.121, Detail.STANDARD.zoom)
        val fine = WatchTiles.metersPerPixel(45.121, Detail.FINE.zoom)
        val veryFine = WatchTiles.metersPerPixel(45.121, Detail.VERY_FINE.zoom)
        assertEquals(2.38, standard, 0.02)
        assertEquals(1.68, fine, 0.02)
        assertEquals(1.19, veryFine, 0.02)
    }

    @Test fun `a level is found again from its zoom`() {
        assertEquals(Detail.STANDARD, Detail.ofZoom(15.5))
        assertEquals(Detail.FINE, Detail.ofZoom(16.0))
        assertEquals(null, Detail.ofZoom(15.0))
        assertEquals(null, Detail.ofZoom(14.0))
    }

    @Test fun `each level asks for the source zoom that has at least its detail`() {
        assertEquals(16, Detail.STANDARD.sourceZoom)
        assertEquals(16, Detail.FINE.sourceZoom)
        assertEquals(17, Detail.VERY_FINE.sourceZoom)
    }
}
