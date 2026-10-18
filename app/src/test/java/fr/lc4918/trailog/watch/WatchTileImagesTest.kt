package fr.lc4918.trailog.watch

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** En rendu natif : celui par defaut de Robolectric ne dessine rien et rendrait des pixels vides. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatchTileImagesTest {

    /** Une tuile source dont chaque quart a sa couleur : rouge, vert / bleu, noir. */
    private fun quarteredTile(size: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val half = size / 2
        for (y in 0 until size) for (x in 0 until size) {
            val color = when {
                x < half && y < half -> Color.rgb(0xFF, 0x00, 0x00)
                x >= half && y < half -> Color.rgb(0x55, 0xAA, 0x55)
                x < half -> Color.rgb(0x00, 0x55, 0xAA)
                else -> Color.BLACK
            }
            bitmap.setPixel(x, y, color)
        }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }

    /** La couleur de palette du pixel (x, y) d'une tuile rangee. */
    private fun colorAt(packed: ByteArray, x: Int, y: Int): Int =
        WatchTilePixels.PALETTE[WatchTilePixels.unpack(packed)[y * 128 + x]]

    @Test fun `each quarter of a 256 px tile becomes a 128 px watch tile`() {
        val source = quarteredTile(256)
        val expectedColors = mapOf((0 to 0) to Color.rgb(0xFF, 0x00, 0x00), (1 to 0) to Color.rgb(0x55, 0xAA, 0x55), (0 to 1) to Color.rgb(0x00, 0x55, 0xAA), (1 to 1) to Color.BLACK)
        for ((quarter, expectedColor) in expectedColors) {
            val tile = WatchTileImages.quarter(source, quarter.first, quarter.second)!!
            assertEquals(WatchTilePixels.PACKED_BYTES, tile.size)
            assertEquals(expectedColor, colorAt(tile, 64, 64) or 0xFF000000.toInt())
        }
    }

    @Test fun `a 512 px tile is reduced to 128 px`() {
        val tile = WatchTileImages.quarter(quarteredTile(512), 1, 0)!!
        assertEquals(WatchTilePixels.PACKED_BYTES, tile.size)
        assertEquals(Color.rgb(0x55, 0xAA, 0x55), colorAt(tile, 64, 64) or 0xFF000000.toInt())
    }

    @Test fun `transparent areas become white`() {
        val transparent = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        transparent.compress(Bitmap.CompressFormat.PNG, 100, output)
        val tile = WatchTileImages.quarter(output.toByteArray(), 0, 0)!!
        assertEquals(0xFFFFFF, colorAt(tile, 10, 10))
    }

    @Test fun `unreadable bytes give no tile`() {
        assertNull(WatchTileImages.quarter(byteArrayOf(1, 2, 3), 0, 0))
    }
}
