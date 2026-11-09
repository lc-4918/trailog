package fr.lc4918.trailog.watch

/**
 * Pixels d'une tuile montre : reduits a la palette de la montre, puis codes par plages de pixels identiques.
 *
 * C'est le telephone qui reduit les couleurs et non Garmin Connect Mobile (makeImageRequest) : le chemin
 * image ne joint pas le serveur local dans le simulateur, et la montre recoit ainsi des octets qu'elle
 * stocke tels quels, sans conversion cote montre.
 *
 * La palette est la grille des 64 couleurs que sait afficher un ecran Garmin a memoire dans le pixel (les composantes
 * 00, 55, AA et FF) : une couleur hors de cette grille est de toute facon arrondie par l'ecran, au risque de confondre
 * plusieurs entrees de la palette. Elle offre plusieurs nuances de chaque teinte, donc des bords de traits adoucis (textes,
 * routes, courbes) au lieu de marches de deux couleurs. Quinze couleurs au plus tiendraient sur quatre bits par pixel mais
 * ne laissent aucune nuance. Un octet par pixel ferait 16 Ko par tuile, trop pour une reponse que la montre accepte (code
 * -402, reponse trop grande) : la tuile est donc codee par plages (cf. [encode]), qu'une carte d'aplats et de traits fins
 * ramene a quelques Ko, et que la montre dessine telles quelles. Une carte vectorielle chargee (hachures, courbes de niveau,
 * noms de rues) est faite de beaucoup de plages de un a trois pixels : elles tiennent sur un octet.
 *
 * Chaque pixel prend la couleur de palette la plus proche en espace Lab (distance perceptuelle). Pas de tramage : un fond
 * de carte est fait d'aplats et de traits fins, et un tramage y ferait des points parasites.
 */
object WatchTilePixels {

    private val LEVELS = intArrayOf(0xFF, 0xAA, 0x55, 0x00)

    /** Les 64 couleurs de la grille, du blanc (indice 0) au noir (indice 63). */
    val PALETTE: IntArray = IntArray(LEVELS.size * LEVELS.size * LEVELS.size) { index ->
        (LEVELS[index / 16] shl 16) or (LEVELS[index / 4 % 4] shl 8) or LEVELS[index % 4]
    }

    /** Pixels d'une tuile. */
    const val TILE_PIXELS = WatchTiles.WATCH_TILE_PIXELS * WatchTiles.WATCH_TILE_PIXELS

    /**
     * Taille maximale d'une tuile codee : la montre refuse les reponses de plus de 16 Ko environ (code -402) ; mesure dans
     * le simulateur, 11264 octets (15 Ko en base64) passent et 12288 (16,4 Ko) non.
     */
    const val MAX_ENCODED_BYTES = 11000

    private const val MAX_RUN = 255

    /** Regularisation spatiale de [quantize] : passes, et cout (en distance Lab au carre) d'un voisin de couleur differente. */
    private const val SMOOTHING_PASSES = 3
    private const val SMOOTHING_WEIGHT = 60.0

    /** Stabilisation des aplats de [steadied] : rayon de la fenetre (5 x 5 pixels) et ecart Lab sous lequel un pixel prend la moyenne. */
    private const val FLAT_RADIUS = 2
    private const val FLAT_TOLERANCE = 10.0

    /** Les plages de cette longueur au plus tiennent sur un octet (cf. [encode]). */
    private const val MAX_SHORT_RUN = 3
    private const val LONG_RUN_MARKER = 3
    private const val INDEX_MASK = 0x3F

    private val linearByLevel = DoubleArray(256) {
        val level = it / 255.0
        if (level > 0.04045) Math.pow((level + 0.055) / 1.055, 2.4) else level / 12.92
    }

    private val paletteLab = Array(PALETTE.size) { lab(PALETTE[it]) }

    /** La tuile [rgbPixels] (0xRRGGBB, ligne par ligne, [size] x [size]) reduite a la palette puis codee par [encode]. */
    fun pack(rgbPixels: IntArray, size: Int = WatchTiles.WATCH_TILE_PIXELS): ByteArray {
        require(rgbPixels.size == size * size) { "pixels attendus : ${size * size}, recus : ${rgbPixels.size}" }
        return encode(quantize(rgbPixels, size), size)
    }

    /**
     * Les indices de palette codes par plages, dans l'ordre de lecture (ligne par ligne, sans tenir compte des bords de
     * ligne). Une plage de 1 a 3 pixels tient sur un octet : la longueur moins un dans les deux bits de poids fort, l'indice de
     * palette (de 0 a 63) dans les six autres. Une plage plus longue prend deux octets : 3 dans les deux bits de poids fort et
     * l'indice, puis la longueur de 4 a 255. Une tuile qui depasserait
     * [MAX_ENCODED_BYTES] (un fond photographique, tres bruite) est simplifiee jusqu'a y tenir : d'abord les pixels
     * isoles repris par leurs voisins, puis des blocs de 2, 4 ou 8 pixels de cote.
     */
    internal fun encode(indices: IntArray, size: Int): ByteArray {
        var current = indices
        var encoded = runs(current)
        var passes = 0
        while (encoded.size > MAX_ENCODED_BYTES && passes < 3) {
            current = removeIsolatedPixels(current, size)
            encoded = runs(current)
            passes++
        }
        // Toujours trop : des blocs de plus en plus grands, jusqu'a tenir (des blocs de 8 pixels tiennent toujours).
        var block = 2
        while (encoded.size > MAX_ENCODED_BYTES) {
            encoded = runs(coarsen(current, size, block))
            block *= 2
        }
        return encoded
    }

    /** L'inverse de [encode] : un indice de palette par pixel. */
    fun unpack(encoded: ByteArray): IntArray {
        val indices = ArrayList<Int>(TILE_PIXELS)
        var position = 0
        while (position < encoded.size) {
            val first = encoded[position].toInt() and 0xFF
            val index = first and INDEX_MASK
            val code = first shr 6
            val length = if (code == LONG_RUN_MARKER) {
                if (position + 1 >= encoded.size) break
                (encoded[position + 1].toInt() and 0xFF).also { position++ }
            } else code + 1
            position++
            repeat(length) { indices.add(index) }
        }
        return indices.toIntArray()
    }

    private fun runs(indices: IntArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var start = 0
        while (start < indices.size) {
            var end = start + 1
            while (end < indices.size && indices[end] == indices[start] && end - start < MAX_RUN) end++
            val length = end - start
            if (length <= MAX_SHORT_RUN) {
                out.write(((length - 1) shl 6) or indices[start])
            } else {
                out.write((LONG_RUN_MARKER shl 6) or indices[start])
                out.write(length)
            }
            start = end
        }
        return out.toByteArray()
    }

    /** Un pixel dont la couleur n'est celle d'aucun de ses huit voisins prend la plus frequente autour de lui. */
    private fun removeIsolatedPixels(indices: IntArray, size: Int): IntArray {
        val cleaned = indices.copyOf()
        val neighbours = IntArray(8)
        for (row in 1 until size - 1) for (column in 1 until size - 1) {
            val own = indices[row * size + column]
            var count = 0
            var alone = true
            for (rowOffset in -1..1) for (columnOffset in -1..1) {
                if (rowOffset == 0 && columnOffset == 0) continue
                val neighbour = indices[(row + rowOffset) * size + column + columnOffset]
                if (neighbour == own) alone = false
                neighbours[count++] = neighbour
            }
            if (!alone) continue
            cleaned[row * size + column] = neighbours.groupBy { it }.maxByOrNull { it.value.size }!!.key
        }
        return cleaned
    }

    /** Chaque bloc de [block] x [block] pixels prend la couleur la plus frequente du bloc (la premiere a egalite). */
    private fun coarsen(indices: IntArray, size: Int, block: Int): IntArray {
        val coarse = indices.copyOf()
        for (row in 0 until size step block) for (column in 0 until size step block) {
            val counts = HashMap<Int, Int>()
            for (rowOffset in 0 until block) for (columnOffset in 0 until block) {
                val index = indices[minOf(row + rowOffset, size - 1) * size + minOf(column + columnOffset, size - 1)]
                counts[index] = (counts[index] ?: 0) + 1
            }
            val chosen = counts.maxByOrNull { it.value }!!.key
            for (rowOffset in 0 until block) for (columnOffset in 0 until block) {
                val targetRow = row + rowOffset
                val targetColumn = column + columnOffset
                if (targetRow < size && targetColumn < size) coarse[targetRow * size + targetColumn] = chosen
            }
        }
        return coarse
    }

    /**
     * Chaque pixel prend sa couleur de palette la plus proche, puis une regularisation spatiale retire le grain : un pixel dont la
     * couleur est presque aussi proche de celle de ses voisins que de sa couleur de palette la plus proche prend celle de ses voisins
     * (cf. [SMOOTHING_WEIGHT]). Un trait fin ou un contour net, dont la couleur est loin de celle du fond, garde la sienne.
     */
    internal fun quantize(rgbPixels: IntArray, size: Int): IntArray {
        val labs = steadied(Array(rgbPixels.size) { lab(rgbPixels[it]) }, size)
        val labels = IntArray(rgbPixels.size) { nearestLabIndex(labs[it]) }
        if (size < 3) return labels
        val own = IntArray(rgbPixels.size) { labels[it] }
        repeat(SMOOTHING_PASSES) {
            for (row in 0 until size) for (column in 0 until size) {
                val position = row * size + column
                var bestLabel = labels[position]
                var bestCost = Double.MAX_VALUE
                val neighbourLabels = intArrayOf(
                    if (column > 0) labels[position - 1] else -1, if (column < size - 1) labels[position + 1] else -1,
                    if (row > 0) labels[position - size] else -1, if (row < size - 1) labels[position + size] else -1,
                )
                // Candidats : la couleur de palette la plus proche et les couleurs des quatre voisins.
                val candidates = intArrayOf(own[position], neighbourLabels[0], neighbourLabels[1], neighbourLabels[2], neighbourLabels[3])
                for (candidate in candidates) {
                    if (candidate < 0) continue
                    val reference = paletteLab[candidate]
                    val lightnessGap = labs[position][0] - reference[0]
                    val greenRedGap = labs[position][1] - reference[1]
                    val blueYellowGap = labs[position][2] - reference[2]
                    var cost = lightnessGap * lightnessGap + greenRedGap * greenRedGap + blueYellowGap * blueYellowGap
                    for (neighbour in neighbourLabels) if (neighbour >= 0 && neighbour != candidate) cost += SMOOTHING_WEIGHT
                    if (cost < bestCost) { bestCost = cost; bestLabel = candidate }
                }
                labels[position] = bestLabel
            }
        }
        return labels
    }

    /**
     * Les couleurs Lab de [labs] (tuile de [size] pixels de cote) ou chaque pixel proche de la moyenne de ses voisins (a moins de
     * [FLAT_TOLERANCE] de distance Lab) prend cette moyenne : un fond d'ombrage doux, dont la couleur flotte autour d'un seuil de la
     * palette, devient un aplat au lieu d'un semis de deux couleurs. Un trait, un contour, un bord net s'ecartent plus que la
     * tolerance de la moyenne et gardent leur couleur.
     */
    private fun steadied(labs: Array<DoubleArray>, size: Int): Array<DoubleArray> {
        if (size < 2 * FLAT_RADIUS + 1) return labs
        val steadied = Array(labs.size) { labs[it] }
        for (row in 0 until size) for (column in 0 until size) {
            val mean = DoubleArray(3)
            var count = 0
            for (rowOffset in -FLAT_RADIUS..FLAT_RADIUS) for (columnOffset in -FLAT_RADIUS..FLAT_RADIUS) {
                val neighbour = labs[(row + rowOffset).coerceIn(0, size - 1) * size + (column + columnOffset).coerceIn(0, size - 1)]
                for (channel in 0 until 3) mean[channel] += neighbour[channel]
                count++
            }
            for (channel in 0 until 3) mean[channel] /= count
            val own = labs[row * size + column]
            val gapL = own[0] - mean[0]
            val gapA = own[1] - mean[1]
            val gapB = own[2] - mean[2]
            if (gapL * gapL + gapA * gapA + gapB * gapB < FLAT_TOLERANCE * FLAT_TOLERANCE) steadied[row * size + column] = mean
        }
        return steadied
    }

    /** L'indice de la couleur de palette la plus proche de [rgb] en espace Lab. */
    internal fun nearestPaletteIndex(rgb: Int): Int = nearestLabIndex(lab(rgb))

    private fun nearestLabIndex(pixel: DoubleArray): Int {
        var bestIndex = 0
        var bestDistance = Double.MAX_VALUE
        for (index in PALETTE.indices) {
            val reference = paletteLab[index]
            val lightnessGap = pixel[0] - reference[0]
            val greenRedGap = pixel[1] - reference[1]
            val blueYellowGap = pixel[2] - reference[2]
            val distance = lightnessGap * lightnessGap + greenRedGap * greenRedGap + blueYellowGap * blueYellowGap
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    /** CIE L*a*b* (illuminant D65) d'une couleur 0xRRGGBB : [L, a, b]. */
    private fun lab(rgb: Int): DoubleArray {
        val red = linearByLevel[(rgb shr 16) and 0xFF]
        val green = linearByLevel[(rgb shr 8) and 0xFF]
        val blue = linearByLevel[rgb and 0xFF]
        val x = (0.4124 * red + 0.3576 * green + 0.1805 * blue) / 0.95047
        val y = 0.2126 * red + 0.7152 * green + 0.0722 * blue
        val z = (0.0193 * red + 0.1192 * green + 0.9505 * blue) / 1.08883
        val fx = labCurve(x)
        val fy = labCurve(y)
        val fz = labCurve(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun labCurve(value: Double) = if (value > 0.008856) Math.cbrt(value) else 7.787 * value + 16.0 / 116
}
