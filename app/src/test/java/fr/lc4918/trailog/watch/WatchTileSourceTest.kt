package fr.lc4918.trailog.watch

import android.database.sqlite.SQLiteDatabase
import fr.lc4918.trailog.data.db.ProviderEntity
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

    @Test fun `only opaque raster basemaps serving zoom 15 can be sent`() {
        assertTrue(WatchTileSource.supports(provider("XYZ")))
        assertTrue(WatchTileSource.supports(provider("MBTILES")))
        assertFalse(WatchTileSource.supports(provider("VECTOR")))
        assertFalse(WatchTileSource.supports(provider("DEM")))
        assertFalse(WatchTileSource.supports(provider("XYZ", transparent = true)))
        assertFalse(WatchTileSource.supports(provider("XYZ", maxZoom = 14)))
    }

    /** L'envoi telecharge un couloir entier : OpenStreetMap l'interdit, et renverrait des tuiles bloquees. */
    @Test fun `a basemap forbidding bulk download cannot be sent`() {
        assertFalse(WatchTileSource.supports(provider("XYZ", url = "https://tile.openstreetmap.org/{z}/{x}/{y}.png")))
        assertTrue(WatchTileSource.supports(provider("XYZ", url = "https://tile.opentopomap.org/{z}/{x}/{y}.png")))
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
}
