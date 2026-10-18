package fr.lc4918.trailog.watch

/**
 * Pixels d'une tuile montre : reduits a la palette de la montre, trames, puis ranges a quatre bits par pixel.
 *
 * C'est le telephone qui reduit les couleurs et non Garmin Connect Mobile (makeImageRequest) : le chemin
 * image ne joint pas le serveur local dans le simulateur, et la montre recoit ainsi des octets qu'elle
 * stocke tels quels, sans conversion cote montre.
 */
object WatchTilePixels {

    /** Au plus 15 couleurs : la montre range alors la tuile sur 4 bits par pixel (cf. TilePalettes dans Trailog Garmin). */
    val PALETTE: IntArray = intArrayOf(
        0xFFFFFF, 0x000000, 0xAAAAAA,
        0xAAFFAA, 0x55AA55, 0xFFFFAA, 0xAA5500,
        0xFF00FF, 0xAA00AA, 0xFFAA00, 0xFF0000,
        0xFFFF55, 0x55AAFF, 0x0055AA, 0xAAFFFF,
    )

    /** Octets d'une tuile rangee : deux pixels par octet. */
    const val PACKED_BYTES = WatchTiles.WATCH_TILE_PIXELS * WatchTiles.WATCH_TILE_PIXELS / 2

    /**
     * Les indices de palette de [rgbPixels] (0xRRGGBB, ligne par ligne, [size] x [size]), par tramage de
     * Floyd-Steinberg, deux par octet : le premier pixel d'une paire dans les quatre bits de poids fort.
     */
    fun pack(rgbPixels: IntArray, size: Int = WatchTiles.WATCH_TILE_PIXELS): ByteArray {
        require(rgbPixels.size == size * size) { "pixels attendus : ${size * size}, recus : ${rgbPixels.size}" }
        val indices = quantize(rgbPixels, size)
        val packed = ByteArray((indices.size + 1) / 2)
        for (index in indices.indices) {
            val shift = if (index % 2 == 0) 4 else 0
            packed[index / 2] = (packed[index / 2].toInt() or (indices[index] shl shift)).toByte()
        }
        return packed
    }

    /** L'inverse de [pack] : un indice de palette par pixel. */
    fun unpack(packed: ByteArray): IntArray = IntArray(packed.size * 2) { index ->
        val octet = packed[index / 2].toInt() and 0xFF
        if (index % 2 == 0) octet shr 4 else octet and 0x0F
    }

    internal fun quantize(rgbPixels: IntArray, size: Int): IntArray {
        // Une ligne d'erreur en cours et la suivante, par canal : l'erreur d'un pixel part a droite et en dessous.
        var currentError = Array(3) { FloatArray(size + 2) }
        var nextError = Array(3) { FloatArray(size + 2) }
        val indices = IntArray(size * size)
        for (row in 0 until size) {
            for (column in 0 until size) {
                val rgb = rgbPixels[row * size + column]
                val wanted = FloatArray(3) { channel ->
                    ((rgb shr (16 - 8 * channel)) and 0xFF) + currentError[channel][column + 1]
                }
                val best = nearestPaletteIndex(wanted)
                indices[row * size + column] = best
                for (channel in 0 until 3) {
                    val error = wanted[channel] - ((PALETTE[best] shr (16 - 8 * channel)) and 0xFF)
                    currentError[channel][column + 2] += error * 7f / 16f
                    nextError[channel][column] += error * 3f / 16f
                    nextError[channel][column + 1] += error * 5f / 16f
                    nextError[channel][column + 2] += error * 1f / 16f
                }
            }
            val finished = currentError
            currentError = nextError
            nextError = finished
            for (channel in 0 until 3) nextError[channel].fill(0f)
        }
        return indices
    }

    private fun nearestPaletteIndex(wanted: FloatArray): Int {
        var bestIndex = 0
        var bestDistance = Float.MAX_VALUE
        for (index in PALETTE.indices) {
            var distance = 0f
            for (channel in 0 until 3) {
                val difference = wanted[channel] - ((PALETTE[index] shr (16 - 8 * channel)) and 0xFF)
                distance += difference * difference
            }
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }
}
