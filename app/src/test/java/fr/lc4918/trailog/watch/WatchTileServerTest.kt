package fr.lc4918.trailog.watch

import fr.lc4918.trailog.watch.WatchTiles.WatchTile
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/** Le serveur sur une vraie socket de bouclage, interroge comme le ferait Garmin Connect Mobile. */
class WatchTileServerTest {

    private val image = byteArrayOf(9, 8, 7, 6)
    private lateinit var server: WatchTileServer

    @Before fun startServer() {
        val routes = WatchRoutes("Essai", listOf(WatchTile(1, 2)), { image })
        server = WatchTileServer(routes, port = 0).also { it.start() }
    }

    @After fun stopServer() = server.close()

    private fun get(path: String): HttpURLConnection =
        URL("http://127.0.0.1:${server.localPort}$path").openConnection() as HttpURLConnection

    @Test fun `manifest is served as json`() {
        val connection = get("/watch/manifest")
        assertEquals(200, connection.responseCode)
        assertEquals("application/json", connection.contentType)
        val body = connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        assertEquals("""{"version":1,"name":"Essai","zoom":15,"tilePixels":128,"tileCount":1}""", body)
    }

    @Test fun `tile image bytes arrive intact`() {
        val connection = get("/watch/tile/15/1/2")
        assertEquals(200, connection.responseCode)
        assertEquals("image/png", connection.contentType)
        assertArrayEquals(image, connection.inputStream.use { it.readBytes() })
    }

    @Test fun `several requests in a row are answered`() {
        repeat(3) { assertEquals(200, get("/watch/manifest").responseCode) }
        assertEquals(404, get("/watch/tile/15/5/5").responseCode)
        assertEquals(200, get("/watch/manifest").responseCode)
    }
}
