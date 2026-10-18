package fr.lc4918.trailog.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class WatchTilePixelsTest {

    private val size = 8

    private fun solid(rgb: Int, side: Int = size) = IntArray(side * side) { rgb }

    private fun colorOf(index: Int) = WatchTilePixels.PALETTE[index]

    @Test fun `the palette is the grid of the 64 colors a Garmin screen shows`() {
        assertEquals(64, WatchTilePixels.PALETTE.size)
        assertEquals(64, WatchTilePixels.PALETTE.toSet().size)
        assertTrue(WatchTilePixels.PALETTE.all { color -> listOf(16, 8, 0).all { ((color shr it) and 0xFF) in setOf(0x00, 0x55, 0xAA, 0xFF) } })
        assertEquals(0xFFFFFF, WatchTilePixels.PALETTE.first())
        assertEquals(0x000000, WatchTilePixels.PALETTE.last())
    }

    @Test fun `every palette color keeps its own index`() {
        for ((index, color) in WatchTilePixels.PALETTE.withIndex()) {
            assertEquals(index, WatchTilePixels.nearestPaletteIndex(color))
        }
    }

    @Test fun `a solid palette color keeps its index everywhere`() {
        val indices = WatchTilePixels.quantize(solid(0xFF0000), size)
        assertTrue(indices.all { colorOf(it) == 0xFF0000 })
    }

    @Test fun `without dithering a color between two palette colors gives one flat color`() {
        // Un aplat intermediaire n'est pas trame : il prend une seule couleur, la plus proche.
        val used = WatchTilePixels.quantize(solid(0x808080, 32), 32).toSet()
        assertEquals(1, used.size)
    }

    @Test fun `a mid grey stays neutral and does not fall on a hue`() {
        for (level in 0..255 step 5) {
            val rgb = (level shl 16) or (level shl 8) or level
            val color = colorOf(WatchTilePixels.nearestPaletteIndex(rgb))
            val isNeutral = (color shr 16 and 0xFF) == (color shr 8 and 0xFF) && (color shr 8 and 0xFF) == (color and 0xFF)
            assertTrue("gris $level -> %06X".format(color), isNeutral)
        }
    }

    @Test fun `noise on white stays white`() {
        // Le bruit du JPEG : des ecarts de quelques niveaux autour du blanc ne doivent donner aucun point.
        val random = Random(7)
        val noisy = IntArray(64 * 64) {
            val noise = random.nextInt(13) - 6
            val channel = (250 + noise).coerceIn(0, 255)
            (channel shl 16) or (channel shl 8) or (253 + noise.coerceAtMost(2)).coerceIn(0, 255)
        }
        assertTrue(WatchTilePixels.quantize(noisy, 64).all { colorOf(it) == 0xFFFFFF })
    }

    @Test fun `the softened edge of black text on white gets intermediate greys, not a coloured fringe`() {
        // Un trait noir de 2 px avec des bords adoucis sur du blanc : les nuances de gris adoucissent le bord.
        val side = 16
        val pixels = IntArray(side * side) { index ->
            when (index % side) {
                6 -> 0xB0B0B0
                7, 8 -> 0x000000
                9 -> 0x555555
                else -> 0xFFFFFF
            }
        }
        val indices = WatchTilePixels.quantize(pixels, side)
        assertEquals(0xAAAAAA, colorOf(indices[6]))
        assertEquals(0x555555, colorOf(indices[9]))
        assertTrue(indices.all { color -> colorOf(color).let { it shr 16 and 0xFF == it shr 8 and 0xFF && it shr 8 and 0xFF == it and 0xFF } })
    }

    @Test fun `an isolated pixel is kept, fine details are not erased`() {
        val pixels = IntArray(25) { 0xFFFFFF }
        pixels[12] = 0x000000
        assertEquals(0x000000, colorOf(WatchTilePixels.quantize(pixels, 5)[12]))
    }

    @Test fun `the border of the tile is treated like the rest`() {
        val pixels = IntArray(25) { 0xFFFFFF }
        pixels[0] = 0x000000
        assertEquals(0x000000, colorOf(WatchTilePixels.quantize(pixels, 5)[0]))
    }

    @Test fun `a thin line two pixels wide survives`() {
        val side = 10
        val pixels = IntArray(side * side) { if (it % side in 4..5) 0xFF0000 else 0xFFFFFF }
        val indices = WatchTilePixels.quantize(pixels, side)
        assertEquals(0xFF0000, colorOf(indices[5 * side + 4]))
        assertEquals(0xFF0000, colorOf(indices[5 * side + 5]))
    }

    @Test fun `pack codes the tile by runs of the same index`() {
        // Deux pixels rouges puis deux noirs : deux plages, (longueur, indice).
        val packed = WatchTilePixels.pack(intArrayOf(0xFF0000, 0xFF0000, 0x000000, 0x000000), 2)
        assertEquals(
            listOf(2, WatchTilePixels.PALETTE.indexOf(0xFF0000), 2, WatchTilePixels.PALETTE.indexOf(0x000000)),
            packed.map { it.toInt() and 0xFF },
        )
    }

    @Test fun `a run longer than 255 pixels is cut`() {
        val packed = WatchTilePixels.pack(IntArray(20 * 20) { 0xFFFFFF }, 20)
        assertEquals(listOf(255, 0, 145, 0), packed.map { it.toInt() and 0xFF })
    }

    @Test fun `unpack reverses pack`() {
        val pixels = IntArray(size * size) { WatchTilePixels.PALETTE[it % WatchTilePixels.PALETTE.size] }
        val unpacked = WatchTilePixels.unpack(WatchTilePixels.pack(pixels, size))
        assertEquals(WatchTilePixels.quantize(pixels, size).toList(), unpacked.toList())
    }

    @Test fun `a flat watch tile is a few dozen runs`() {
        val packed = WatchTilePixels.pack(IntArray(128 * 128) { 0xFFFFFF })
        assertEquals(130, packed.size)
        assertEquals(WatchTilePixels.TILE_PIXELS, WatchTilePixels.unpack(packed).size)
    }

    @Test fun `a very noisy tile is simplified to stay within the size the watch accepts`() {
        val random = Random(3)
        val noise = IntArray(128 * 128) { WatchTilePixels.PALETTE[random.nextInt(WatchTilePixels.PALETTE.size)] }
        val packed = WatchTilePixels.pack(noise)
        assertTrue("taille ${packed.size}", packed.size <= WatchTilePixels.MAX_ENCODED_BYTES)
        assertEquals(WatchTilePixels.TILE_PIXELS, WatchTilePixels.unpack(packed).size)
    }

    @Test fun `a map-like tile keeps all its pixels when it fits`() {
        // Des bandes verticales de 6 pixels : une plage par bande et par ligne, rien a simplifier.
        val side = 128
        val pixels = IntArray(side * side) { WatchTilePixels.PALETTE[it % side / 6 % 4] }
        val packed = WatchTilePixels.pack(pixels)
        assertEquals(WatchTilePixels.quantize(pixels, side).toList(), WatchTilePixels.unpack(packed).toList())
    }
}
