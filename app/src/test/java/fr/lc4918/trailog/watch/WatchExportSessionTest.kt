package fr.lc4918.trailog.watch

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.watch.WatchTiles.WatchTile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

/** Un envoi complet : un fond MBTiles, le serveur, et la montre simulee par des requetes HTTP. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatchExportSessionTest {

    private val scope = TestScope()
    private val port = ServerSocket(0).use { it.localPort }
    private var startedCount = 0
    private val session = WatchExportSession(scope, onStarted = { startedCount++ }, port = port)
    private val mbtilesFile = File.createTempFile("export", ".mbtiles").apply { delete() }

    // La tuile standard (15, 16918, 11771) : rouge en haut a gauche, vert en haut a droite, bleu en bas.
    private val tiles = listOf(WatchTile(33836, 23542), WatchTile(33837, 23542), WatchTile(33836, 23543))

    private val provider = ProviderEntity(id = "zone", name = "Zone", groupName = "Local", type = "MBTILES", urlTemplate = "zone.mbtiles")

    private fun writeMbtiles() {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        for (y in 0 until 256) for (x in 0 until 256) {
            bitmap.setPixel(x, y, if (y >= 128) Color.BLUE else if (x < 128) Color.RED else Color.GREEN)
        }
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        SQLiteDatabase.openOrCreateDatabase(mbtilesFile, null).use { database ->
            database.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
            database.execSQL("INSERT INTO tiles VALUES (15, 16918, ?, ?)", arrayOf((1 shl 15) - 1 - 11771, png))
        }
    }

    private fun get(path: String): HttpURLConnection =
        URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection

    @After fun cleanUp() {
        session.stop()
        mbtilesFile.delete()
    }

    @Test fun `the watch receives each quarter of the basemap tile and progress follows`() {
        writeMbtiles()
        assertTrue(session.start("Tour", tiles, provider, mbtilesFile))
        assertEquals(1, startedCount)
        assertEquals(WatchExportSession.State("Tour", 0, 3), session.state.value)

        val expectedColors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        tiles.forEachIndexed { index, tile ->
            val connection = get("/watch/tile/15/${tile.x}/${tile.y}")
            assertEquals(200, connection.responseCode)
            val bytes = connection.inputStream.use { it.readBytes() }
            val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertEquals(expectedColors[index], image.getPixel(64, 64))
        }
        val finalState = session.state.value!!
        assertEquals(3, finalState.servedCount)
        assertTrue(finalState.complete)
    }

    @Test fun `stopping closes the server`() {
        writeMbtiles()
        session.start("Tour", tiles, provider, mbtilesFile)
        session.stop()
        assertNull(session.state.value)
        assertFalse(runCatching { get("/watch/manifest").responseCode }.isSuccess)
    }

    @Test fun `the export closes itself after a long silence from the watch`() {
        writeMbtiles()
        session.start("Tour", tiles, provider, mbtilesFile)
        scope.advanceTimeBy(WatchExportSession.IDLE_TIMEOUT_MS - 1_000)
        scope.runCurrent()
        assertEquals(3, session.state.value?.tileCount)
        scope.advanceTimeBy(2_000)
        scope.runCurrent()
        assertNull(session.state.value)
    }

    @Test fun `a second export replaces the first on the same port`() {
        writeMbtiles()
        session.start("Premier", tiles, provider, mbtilesFile)
        assertTrue(session.start("Second", tiles.take(1), provider, mbtilesFile))
        assertEquals("Second", session.state.value?.name)
        assertEquals(200, get("/watch/manifest").responseCode)
    }
}
