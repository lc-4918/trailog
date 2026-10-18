package fr.lc4918.trailog.watch

import android.graphics.Bitmap
import android.graphics.Color
import fr.lc4918.trailog.watch.WatchTiles.WatchTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** En rendu natif : celui par defaut de Robolectric ne dessine rien et rendrait des pixels vides. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatchTileImagesTest {

    private val red = Color.rgb(0xFF, 0x00, 0x00)
    private val green = Color.rgb(0x55, 0xAA, 0x55)
    private val blue = Color.rgb(0x00, 0x55, 0xAA)

    /** Une tuile source dont chaque quart a sa couleur : rouge, vert / bleu, noir (couleurs exactes de la palette). */
    private fun quarteredTile(size: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val half = size / 2
        for (y in 0 until size) for (x in 0 until size) {
            val color = when {
                x < half && y < half -> red
                x >= half && y < half -> green
                x < half -> blue
                else -> Color.BLACK
            }
            bitmap.setPixel(x, y, color)
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** La couleur de palette du pixel (x, y) d'une tuile rangee. */
    private fun colorAt(packed: ByteArray, x: Int, y: Int): Int =
        WatchTilePixels.PALETTE[WatchTilePixels.unpack(packed)[y * 128 + x]] or 0xFF000000.toInt()

    // --- geometrie : quelles tuiles du fond, et ou ---

    @Test fun `at the same zoom a watch tile is a quarter of one source tile`() {
        val pieces = WatchTileImages.sourcePieces(WatchTile(33837, 23543), 15.0, 15)
        assertEquals(1, pieces.size)
        // La tuile montre est le quart bas droit : la tuile source deborde de moitie a gauche et en haut.
        assertEquals(WatchTileImages.SourcePiece(16918, 11771, -128f, -128f, 128f, 128f), pieces[0])
        val topLeft = WatchTileImages.sourcePieces(WatchTile(33836, 23542), 15.0, 15)
        assertEquals(WatchTileImages.SourcePiece(16918, 11771, 0f, 0f, 256f, 256f), topLeft.single())
    }

    @Test fun `the same holds at zoom 16 with a zoom 16 source`() {
        val pieces = WatchTileImages.sourcePieces(WatchTile(67674, 47086), 16.0, 16)
        assertEquals(WatchTileImages.SourcePiece(33837, 23543, 0f, 0f, 256f, 256f), pieces.single())
    }

    @Test fun `a fractional zoom may need up to four source tiles that cover the whole watch tile`() {
        for (x in 40000..40007) for (y in 30000..30007) {
            val pieces = WatchTileImages.sourcePieces(WatchTile(x, y), 15.5, 16)
            assertTrue(pieces.size in 1..4)
            assertTrue(pieces.minOf { it.left } <= 0.001f && pieces.maxOf { it.right } >= 127.999f)
            assertTrue(pieces.minOf { it.top } <= 0.001f && pieces.maxOf { it.bottom } >= 127.999f)
        }
    }

    @Test fun `neighbouring source tiles of a piece set touch without gaps`() {
        val pieces = WatchTileImages.sourcePieces(WatchTile(40003, 30003), 15.5, 16).sortedWith(compareBy({ it.y }, { it.x }))
        val row = pieces.filter { it.y == pieces.first().y }
        for (index in 1 until row.size) assertEquals(row[index - 1].right, row[index].left, 0.001f)
    }

    @Test fun `a source with less detail than the grid is enlarged`() {
        // Un fond limite au zoom 15 sert une tuile montre du zoom 16 : 64 px du fond font 128 px de la montre.
        val piece = WatchTileImages.sourcePieces(WatchTile(67674, 47086), 16.0, 15).single()
        assertEquals(-256f, piece.left, 0.001f)
        assertEquals(256f, piece.right, 0.001f)
    }

    @Test fun `source tiles outside the world are ignored`() {
        assertTrue(WatchTileImages.sourcePieces(WatchTile(-1, 0), 15.0, 15).isEmpty())
    }

    // --- rendu : les pixels reduits a la palette ---

    @Test fun `each quarter of a 256 px tile becomes a 128 px watch tile`() {
        val source = quarteredTile(256)
        val expectedColors = mapOf(
            WatchTile(33836, 23542) to red, WatchTile(33837, 23542) to green,
            WatchTile(33836, 23543) to blue, WatchTile(33837, 23543) to Color.BLACK,
        )
        for ((tile, expectedColor) in expectedColors) {
            val packed = WatchTileImages.render(tile, 15.0, 15) { x, y -> if (x == 16918 && y == 11771) source else null }!!
            assertEquals(WatchTilePixels.TILE_PIXELS, WatchTilePixels.unpack(packed).size)
            assertEquals(expectedColor, colorAt(packed, 64, 64))
        }
    }

    @Test fun `a 512 px source tile gives the same result`() {
        val source = quarteredTile(512)
        val packed = WatchTileImages.render(WatchTile(33837, 23542), 15.0, 15) { x, y -> if (x == 16918 && y == 11771) source else null }!!
        assertEquals(green, colorAt(packed, 64, 64))
    }

    @Test fun `tiles of two neighbouring sources are assembled into one watch tile`() {
        // Zoom 15,5 sur un fond du zoom 16 : la tuile montre chevauche plusieurs tuiles. On donne a gauche du
        // rouge, a droite du bleu, et on regarde les deux cotes de la tuile montre.
        val tile = WatchTile(40003, 30003)
        val pieces = WatchTileImages.sourcePieces(tile, 15.5, 16)
        assertTrue(pieces.map { it.x }.distinct().size >= 2)
        val firstColumn = pieces.minOf { it.x }
        val redSource = solidTile(red)
        val blueSource = solidTile(blue)
        val packed = WatchTileImages.render(tile, 15.5, 16) { x, _ -> if (x == firstColumn) redSource else blueSource }!!
        assertEquals(red, colorAt(packed, 2, 64))
        assertEquals(blue, colorAt(packed, 125, 64))
    }

    private fun solidTile(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test fun `transparent areas become white`() {
        val transparent = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().also { transparent.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val packed = WatchTileImages.render(WatchTile(33836, 23542), 15.0, 15) { _, _ -> png }!!
        assertEquals(0xFFFFFFFF.toInt(), colorAt(packed, 10, 10))
    }

    @Test fun `unreadable bytes or no source give no tile`() {
        assertNull(WatchTileImages.render(WatchTile(33836, 23542), 15.0, 15) { _, _ -> byteArrayOf(1, 2, 3) })
        assertNull(WatchTileImages.render(WatchTile(33836, 23542), 15.0, 15) { _, _ -> null })
    }
}
