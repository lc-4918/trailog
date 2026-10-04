package fr.lc4918.trailog.watch

import fr.lc4918.trailog.watch.WatchTiles.WatchTile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchRoutesTest {

    private val tiles = (0 until 250).map { WatchTile(1000 + it, 2000) }
    private val image = byteArrayOf(1, 2, 3)

    private fun routes(onTileServed: (Int, Int) -> Unit = { _, _ -> }) =
        WatchRoutes("Tour \"du\" lac", tiles, { image }, onTileServed)

    private fun HttpReply.text() = body.toString(Charsets.UTF_8)

    @Test fun `manifest describes the export with an escaped name`() {
        val reply = routes().respond("/watch/manifest")
        assertEquals(200, reply.status)
        assertEquals("application/json", reply.contentType)
        assertEquals(
            """{"version":1,"name":"Tour \"du\" lac","zoom":15,"tilePixels":128,"tileCount":250}""",
            reply.text(),
        )
    }

    @Test fun `tile list is served by pages`() {
        val reply = routes().respond("/watch/tiles?from=1&count=2")
        assertEquals("""{"from":1,"tiles":[[1001,2000],[1002,2000]]}""", reply.text())
    }

    @Test fun `a page never exceeds the maximum size`() {
        val reply = routes().respond("/watch/tiles?from=0&count=1000")
        assertEquals(WatchRoutes.MAX_TILES_PER_PAGE, Regex("""\[\d+,\d+]""").findAll(reply.text()).count())
    }

    @Test fun `the last page is short and past the end is empty`() {
        assertEquals("""{"from":249,"tiles":[[1249,2000]]}""", routes().respond("/watch/tiles?from=249&count=100").text())
        assertEquals("""{"from":300,"tiles":[]}""", routes().respond("/watch/tiles?from=300&count=100").text())
    }

    @Test fun `a malformed page request is refused`() {
        assertEquals(404, routes().respond("/watch/tiles?from=-1&count=10").status)
        assertEquals(404, routes().respond("/watch/tiles?count=10").status)
    }

    @Test fun `a tile of the export is served as png`() {
        val reply = routes().respond("/watch/tile/15/1001/2000")
        assertEquals(200, reply.status)
        assertEquals("image/png", reply.contentType)
        assertArrayEquals(image, reply.body)
    }

    @Test fun `tiles outside the export or at another zoom are refused`() {
        assertEquals(404, routes().respond("/watch/tile/15/999/2000").status)
        assertEquals(404, routes().respond("/watch/tile/16/1001/2000").status)
    }

    @Test fun `a tile without image answers not found`() {
        val reply = WatchRoutes("x", tiles, { null }).respond("/watch/tile/15/1001/2000")
        assertEquals(404, reply.status)
    }

    @Test fun `progress counts each tile once even when requested again`() {
        var lastProgress = 0 to 0
        val routes = routes { served, total -> lastProgress = served to total }
        routes.respond("/watch/tile/15/1001/2000")
        routes.respond("/watch/tile/15/1001/2000")
        routes.respond("/watch/tile/15/1002/2000")
        assertEquals(2 to 250, lastProgress)
    }

    @Test fun `unknown paths answer not found`() {
        assertEquals(404, routes().respond("/").status)
        assertEquals(404, routes().respond("/watch/tile/15/a/b").status)
    }
}
