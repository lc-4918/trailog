package fr.lc4918.trailog.watch

import fr.lc4918.trailog.map.offline.TileMath
import fr.lc4918.trailog.watch.WatchTiles.WatchTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTilesTest {

    /** Valeurs croisees avec l'application montre (TileMathTest.mc) : meme lieu, memes numeros de tuile. */
    @Test fun `watch tile numbering matches the watch app at Chamrousse`() {
        val tiles = WatchTiles.tilesAlong(listOf(5.877 to 45.121), radiusMeters = 0.0)
        assertEquals(listOf(WatchTile(33837, 23543)), tiles)
    }

    @Test fun `a watch tile is one quarter of the standard tile at the same zoom`() {
        assertEquals(WatchTiles.SourceQuarter(15, 16918, 11771, 1, 1), WatchTiles.sourceQuarterOf(WatchTile(33837, 23543)))
        assertEquals(WatchTiles.SourceQuarter(15, 16918, 11771, 0, 0), WatchTiles.sourceQuarterOf(WatchTile(33836, 23542)))
    }

    @Test fun `the four quarters of a standard tile come back to it`() {
        val quarters = listOf(WatchTile(10, 20), WatchTile(11, 20), WatchTile(10, 21), WatchTile(11, 21))
            .map { WatchTiles.sourceQuarterOf(it) }
        assertTrue(quarters.all { it.x == 5 && it.y == 10 })
        assertEquals(4, quarters.map { it.quarterColumn to it.quarterRow }.toSet().size)
    }

    @Test fun `the corridor is the standard corridor of the next zoom`() {
        val track = listOf(5.85 to 45.10, 5.90 to 45.13, 5.95 to 45.12)
        val expected = TileMath.tilesAlong(track, 16, 500.0).map { (x, y, _) -> WatchTile(x, y) }
        assertEquals(expected, WatchTiles.tilesAlong(track, 500.0))
    }
}
