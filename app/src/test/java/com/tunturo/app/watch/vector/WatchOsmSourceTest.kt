package com.tunturo.app.watch.vector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Sous Robolectric : la lecture de la fiche TileJSON passe par org.json, que le JDK de test ne fournit pas. */
@RunWith(RobolectricTestRunner::class)
class WatchOsmSourceTest {

    private val tileJson = """{"tilejson":"3.0.0","tiles":["https://tiles.example.org/planet/20261101_pt/{z}/{x}/{y}.pbf"],"maxzoom":14}"""
    private val realTile = javaClass.getResourceAsStream("/watch/openmaptiles/14_8257_5982.pbf")!!.readBytes()

    @Test fun `the tile address is read from the tilejson sheet`() {
        val asked = mutableListOf<String>()
        val source = WatchOsmSource { url -> asked.add(url); if (url == WatchOsmSource.TILEJSON_URL) tileJson.toByteArray() else realTile }
        assertNotNull(source.tile(14, 8257, 5982))
        assertEquals(listOf(WatchOsmSource.TILEJSON_URL, "https://tiles.example.org/planet/20261101_pt/14/8257/5982.pbf"), asked)
    }

    @Test fun `the tilejson sheet is read only once for a whole export`() {
        var sheetReads = 0
        val source = WatchOsmSource { url -> if (url == WatchOsmSource.TILEJSON_URL) { sheetReads++; tileJson.toByteArray() } else realTile }
        source.tile(14, 8257, 5982)
        source.tile(14, 8258, 5982)
        source.tile(14, 8257, 5983)
        assertEquals(1, sheetReads)
    }

    @Test fun `no tile when the service gives no sheet or an unreadable one`() {
        assertNull(WatchOsmSource { null }.tile(14, 1, 1))
        assertNull(WatchOsmSource { "pas du json".toByteArray() }.tile(14, 1, 1))
        assertNull(WatchOsmSource { """{"tiles":[]}""".toByteArray() }.tile(14, 1, 1))
    }

    @Test fun `no tile when the tile is missing or unreadable`() {
        assertNull(WatchOsmSource { url -> if (url == WatchOsmSource.TILEJSON_URL) tileJson.toByteArray() else null }.tile(14, 1, 1))
        assertNull(WatchOsmSource { url -> if (url == WatchOsmSource.TILEJSON_URL) tileJson.toByteArray() else byteArrayOf(0x0f, 0x7f, 0x00) }.tile(14, 1, 1))
    }

    @Test fun `the basemap is listed under its own identifier`() {
        val provider = WatchOsmSource.provider("Trailmap")
        assertTrue(WatchOsmSource.isOsm(provider))
        assertFalse(WatchVectorSource.isVector(provider))
        assertFalse(WatchOsmSource.isOsm(WatchVectorSource.provider("Scan 25 vectoriel")))
        assertEquals("Trailmap", provider.name)
    }
}
