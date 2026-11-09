package fr.lc4918.trailog.watch.vector

import fr.lc4918.trailog.watch.WatchTilePixels
import fr.lc4918.trailog.watch.WatchTiles
import fr.lc4918.trailog.watch.WatchTiles.WatchTile
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Le fond Trailmap sur une vraie tuile OpenMapTiles (OpenFreeMap) du zoom 14 : le centre de Toulouse et la Garonne au Pont Neuf. Les
 * niveaux 16,0 a 17,0 en agrandissent la geometrie.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatchOsmRendererTest {

    private val source = VectorTile.decode(javaClass.getResourceAsStream("/watch/openmaptiles/14_8257_5982.pbf")!!.readBytes())

    private fun index(rgb: Int) = WatchTilePixels.PALETTE.indexOf(rgb)

    private fun render(tile: WatchTile, zoom: Double, withText: Boolean = false): IntArray =
        WatchOsmRenderer.render(tile, zoom, WatchOsmRenderer.MAX_SOURCE_ZOOM, withText) { x, y -> if (x == 8257 && y == 5982) source else null }!!

    /** La tuile montre du niveau 17,0 (grille de 128 px) sur la Garonne, au Pont Neuf. */
    private val river = WatchTile(132119, 95726)

    /** La rive gauche, a cote : des batiments, des rues et leurs noms, sans eau. */
    private val city = WatchTile(132118, 95726)

    @Test fun `a tile without any readable source tile has no drawing`() {
        assertNull(WatchOsmRenderer.render(WatchTile(0, 0), 17.0, WatchOsmRenderer.MAX_SOURCE_ZOOM, true) { _, _ -> null })
    }

    @Test fun `only colours of the 64 colour grid are used`() {
        val pixels = render(river, 17.0, withText = true)
        assertTrue(pixels.all { it in WatchTilePixels.PALETTE.indices })
    }

    @Test fun `the river is blue`() {
        val blue = render(river, 17.0).count { it == index(0x55AAFF) }
        assertTrue("eau : $blue pixels", blue > 2000)
    }

    @Test fun `the city is grey buildings and white streets`() {
        val pixels = render(city, 17.0)
        assertTrue("batiments : ${pixels.count { it == index(0xAAAAAA) }} pixels", pixels.count { it == index(0xAAAAAA) } > 500)
        assertTrue(pixels.count { it == index(0xFFFFFF) } > 500)
    }

    @Test fun `a finer level draws the same place larger, with the same colours`() {
        val coarse = render(WatchTile(66059, 47863), 16.0)
        val fine = render(river, 17.0)
        assertTrue(coarse.toSet().size >= 4)
        assertTrue(fine.toSet().size >= 4)
    }

    @Test fun `street names are written in black with a white halo`() {
        val withText = render(city, 17.0, withText = true)
        val without = render(city, 17.0)
        assertTrue("des pixels de texte", withText.indices.count { withText[it] != without[it] } > 100)
        assertTrue(withText.indices.any { withText[it] != without[it] && withText[it] == index(0x000000) })
    }
}
