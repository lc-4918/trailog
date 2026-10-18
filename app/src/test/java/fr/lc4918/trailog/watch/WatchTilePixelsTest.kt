package fr.lc4918.trailog.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTilePixelsTest {

    private val size = 8

    private fun solid(rgb: Int) = IntArray(size * size) { rgb }

    @Test fun `the palette fits four bits per pixel`() {
        assertTrue(WatchTilePixels.PALETTE.size <= 15)
    }

    @Test fun `a solid palette color keeps its index everywhere`() {
        val indices = WatchTilePixels.quantize(solid(0xFF0000), size)
        assertTrue(indices.all { WatchTilePixels.PALETTE[it] == 0xFF0000 })
    }

    @Test fun `a color between two palette colors is dithered with both`() {
        // Gris moyen entre blanc et noir : le tramage doit employer les deux, pas un seul.
        val usedColors = WatchTilePixels.quantize(solid(0x808080), size).map { WatchTilePixels.PALETTE[it] }.toSet()
        assertTrue(usedColors.size >= 2)
    }

    @Test fun `the average of a dithered area stays close to the wanted color`() {
        val indices = WatchTilePixels.quantize(IntArray(32 * 32) { 0x808080 }, 32)
        val averageRed = indices.map { (WatchTilePixels.PALETTE[it] shr 16) and 0xFF }.average()
        assertEquals(0x80.toDouble(), averageRed, 12.0)
    }

    @Test fun `pack puts the first pixel of a pair in the high bits`() {
        val packed = WatchTilePixels.pack(IntArray(4) { if (it % 2 == 0) 0xFF0000 else 0x000000 }, 2)
        val redIndex = WatchTilePixels.PALETTE.indexOf(0xFF0000)
        val blackIndex = WatchTilePixels.PALETTE.indexOf(0x000000)
        assertEquals(2, packed.size)
        assertEquals((redIndex shl 4) or blackIndex, packed[0].toInt() and 0xFF)
    }

    @Test fun `unpack reverses pack`() {
        val pixels = IntArray(size * size) { WatchTilePixels.PALETTE[it % WatchTilePixels.PALETTE.size] }
        val unpacked = WatchTilePixels.unpack(WatchTilePixels.pack(pixels, size))
        assertEquals(pixels.toList(), unpacked.map { WatchTilePixels.PALETTE[it] })
    }

    @Test fun `a full watch tile takes the documented number of bytes`() {
        val packed = WatchTilePixels.pack(IntArray(128 * 128) { 0xFFFFFF })
        assertEquals(WatchTilePixels.PACKED_BYTES, packed.size)
    }
}
