package com.tunturo.app.watch

import android.database.sqlite.SQLiteDatabase
import com.tunturo.app.data.db.ProviderEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class WatchTileSourceTest {

    private fun provider(type: String, url: String = "x", minZoom: Int = 0, maxZoom: Int = 19, transparent: Boolean = false) =
        ProviderEntity(id = "p", name = "p", groupName = "g", type = type, urlTemplate = url,
            minZoom = minZoom, maxZoom = maxZoom, transparent = transparent)

    @Test fun `only opaque raster basemaps serving zoom 16 can be sent`() {
        assertTrue(WatchTileSource.supports(provider("XYZ")))
        assertTrue(WatchTileSource.supports(provider("MBTILES")))
        assertFalse(WatchTileSource.supports(provider("VECTOR")))
        assertFalse(WatchTileSource.supports(provider("DEM")))
        assertFalse(WatchTileSource.supports(provider("XYZ", transparent = true)))
        assertFalse(WatchTileSource.supports(provider("XYZ", maxZoom = 15)))
    }

    /** L'envoi telecharge un couloir entier : OpenStreetMap l'interdit, et renverrait des tuiles bloquees. */
    @Test fun `a basemap forbidding bulk download cannot be sent`() {
        assertFalse(WatchTileSource.supports(provider("XYZ", url = "https://tile.openstreetmap.org/{z}/{x}/{y}.png")))
        assertTrue(WatchTileSource.supports(provider("XYZ", url = "https://tile.opentopomap.org/{z}/{x}/{y}.png")))
    }

    /** Google ne s'envoie pas a la montre, quelle que soit sa variante (rue, satellite, relief) ; l'URL du fond decide, pas son identifiant. */
    @Test fun `a google basemap cannot be sent to the watch`() {
        listOf("m", "s", "p").forEach { layer ->
            assertFalse(WatchTileSource.supports(provider("XYZ", url = "https://mt{s}.google.com/vt/lyrs=$layer&x={x}&y={y}&z={z}")))
        }
        assertTrue(WatchTileSource.supports(provider("XYZ", url = "https://example.com/{z}/{x}/{y}.png")))
    }

    /** L'URL (et la cle) reglees dans le provider decident seules de ce qui est demande : rien en dur dans l'envoi. */
    @Test fun `source tiles are fetched from the url set in the provider`() {
        val requested = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = java.net.ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))
        val serving = Thread {
            while (!server.isClosed) {
                runCatching {
                    server.accept().use { socket ->
                        val line = socket.getInputStream().bufferedReader().readLine().orEmpty()
                        requested.add(line.split(" ")[1])
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\n".toByteArray())
                            write(byteArrayOf(1, 2, 3)); flush()
                        }
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        try {
            val base = "http://127.0.0.1:${server.localPort}"
            val withKey = provider("WMTS", url = "$base/wmts?KEY={KEY}&z={z}&col={x}&row={y}").copy(apiKey = "user_key")
            WatchTileSource(withKey, null).use { assertArrayEquals(byteArrayOf(1, 2, 3), it.sourceImage(16, 33031, 23930)) }
            val moved = provider("WMTS", url = "$base/other/{z}/{x}/{y}.jpg")
            WatchTileSource(moved, null).use { it.sourceImage(16, 5, 6) }
            assertEquals(listOf("/wmts?KEY=user_key&z=16&col=33031&row=23930", "/other/16/5/6.jpg"), requested)
        } finally {
            server.close()
            serving.join(2_000)
        }
    }

    @Test fun `mbtiles file is resolved like the map style does`() {
        val directory = File("/data/maps")
        assertEquals(File("/sd/zone.mbtiles"), WatchTileSource.mbtilesFileOf(provider("MBTILES", "mbtiles:///sd/zone.mbtiles"), directory))
        assertEquals(File(directory, "zone.mbtiles"), WatchTileSource.mbtilesFileOf(provider("MBTILES", "zone.mbtiles"), directory))
        assertNull(WatchTileSource.mbtilesFileOf(provider("XYZ"), directory))
    }

    /** MBTiles range les lignes depuis le bas (TMS) : la tuile XYZ y = 11771 au zoom 15 est la ligne 2^15 - 1 - y. */
    @Test fun `mbtiles rows are read in TMS order`() {
        val file = File.createTempFile("watch", ".mbtiles").apply { delete() }
        val image = byteArrayOf(4, 5, 6)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
            database.execSQL("INSERT INTO tiles VALUES (15, 16918, ?, ?)", arrayOf((1 shl 15) - 1 - 11771, image))
        }
        WatchTileSource(provider("MBTILES"), file).use { source ->
            assertArrayEquals(image, source.sourceImage(15, 16918, 11771))
            assertNull(source.sourceImage(15, 16918, 11772))
        }
        file.delete()
    }

    @Test fun `zooms outside the basemap range give no image`() {
        WatchTileSource(provider("MBTILES", maxZoom = 14), File("/missing")).use { source ->
            assertNull(source.sourceImage(15, 0, 0))
        }
    }

    @Test fun `the Scan 25 offers the levels up to zoom 16 only, its zoom 17 being another product`() {
        val scan25 = provider("WMTS", "https://example/{z}/{x}/{y}").copy(id = "ign_fr", maxZoom = 17)
        assertEquals(listOf(WatchTiles.Detail.FINE), WatchTileSource.availableDetails(scan25))
        val other = provider("XYZ", "https://example/{z}/{x}/{y}.png")
        assertEquals(listOf(WatchTiles.Detail.FINE, WatchTiles.Detail.VERY_FINE), WatchTileSource.availableDetails(other))
    }

    @Test fun `the osm basemap is sendable and offers the three levels of the vector basemaps`() {
        val osm = com.tunturo.app.watch.vector.WatchOsmSource.provider("Trailmap")
        assertTrue(WatchTileSource.supports(osm))
        assertEquals(WatchTiles.Detail.entries.toList(), WatchTileSource.availableDetails(osm))
    }

    @Test fun `the vector basemap offers all three levels and the raster ones never the ultra fine level`() {
        val vector = com.tunturo.app.watch.vector.WatchVectorSource.provider("Scan 25 vectoriel")
        assertTrue(WatchTileSource.supports(vector))
        assertEquals(WatchTiles.Detail.entries.toList(), WatchTileSource.availableDetails(vector))
        assertTrue(WatchTiles.Detail.ULTRA_FINE in WatchTileSource.availableDetails(vector))
        val raster = provider("XYZ", "https://example/{z}/{x}/{y}.png")
        assertFalse(WatchTiles.Detail.ULTRA_FINE in WatchTileSource.availableDetails(raster))
        assertEquals(listOf(WatchTiles.Detail.FINE, WatchTiles.Detail.VERY_FINE), WatchTileSource.availableDetails(raster))
    }
}
