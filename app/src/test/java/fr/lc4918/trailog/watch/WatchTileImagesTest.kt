package fr.lc4918.trailog.watch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
                x < half && y < half -> Color.RED
                x >= half && y < half -> Color.GREEN
                x < half -> Color.BLUE
                else -> Color.BLACK
            }
            bitmap.setPixel(x, y, color)
        }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }

    private fun decode(png: ByteArray): Bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)

    @Test fun `each quarter of a 256 px tile becomes a 128 px watch tile`() {
        val source = quarteredTile(256)
        val expectedColors = mapOf((0 to 0) to Color.RED, (1 to 0) to Color.GREEN, (0 to 1) to Color.BLUE, (1 to 1) to Color.BLACK)
        for ((quarter, expectedColor) in expectedColors) {
            val tile = decode(WatchTileImages.quarter(source, quarter.first, quarter.second)!!)
            assertEquals(128, tile.width)
            assertEquals(128, tile.height)
            assertEquals(expectedColor, tile.getPixel(64, 64))
        }
    }

    @Test fun `a 512 px tile is reduced to 128 px`() {
        val tile = decode(WatchTileImages.quarter(quarteredTile(512), 1, 0)!!)
        assertEquals(128, tile.width)
        assertEquals(Color.GREEN, tile.getPixel(64, 64))
    }

    @Test fun `transparent areas become white`() {
        val transparent = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        transparent.compress(Bitmap.CompressFormat.PNG, 100, output)
        val tile = decode(WatchTileImages.quarter(output.toByteArray(), 0, 0)!!)
        assertEquals(Color.WHITE, tile.getPixel(10, 10))
    }

    @Test fun `unreadable bytes give no tile`() {
        assertNull(WatchTileImages.quarter(byteArrayOf(1, 2, 3), 0, 0))
    }
}
