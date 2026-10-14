package fr.lc4918.trailog.map.offline

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Zone rectangulaire en coordonnées géographiques (WGS84), bornes toujours normalisées
 *  (west <= east, south <= north) par [of]. */
data class Bbox(val west: Double, val south: Double, val east: Double, val north: Double) {
    companion object {
        fun of(lon1: Double, lat1: Double, lon2: Double, lat2: Double) =
            Bbox(minOf(lon1, lon2), minOf(lat1, lat2), maxOf(lon1, lon2), maxOf(lat1, lat2))
    }
}

/**
 * Calculs de pavage de tuiles XYZ (Web Mercator) pour l'estimation du téléchargement hors-ligne
 * (SPEC offline_map.md section 3). Ne gère pas le franchissement de l'antiméridien (west > east après
 * projection) : cas non pertinent pour une app de randonnée centrée sur l'Europe.
 */
object TileMath {
    private const val AVG_TILE_BYTES = 75_000L   // ~75 Ko/tuile, moyenne raisonnable PNG/JPEG raster

    private fun lonToTileX(lon: Double, zoom: Int): Int {
        val n = 1 shl zoom
        return floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
    }

    /** Tuile (x, y) contenant un point, à un niveau de zoom donné. */
    fun tileAt(lon: Double, lat: Double, zoom: Int): Pair<Int, Int> =
        lonToTileX(lon, zoom) to latToTileY(lat, zoom)

    /** Nombre de tuiles couvrant [bbox] à un niveau de zoom donné. */
    fun tileCount(bbox: Bbox, zoom: Int): Long {
        val xMin = lonToTileX(bbox.west, zoom)
        val xMax = lonToTileX(bbox.east, zoom)
        val yMin = latToTileY(bbox.north, zoom)   // nord = y le plus petit (axe Y inversé)
        val yMax = latToTileY(bbox.south, zoom)
        val w = (xMax - xMin + 1).coerceAtLeast(0)
        val h = (yMax - yMin + 1).coerceAtLeast(0)
        return w.toLong() * h.toLong()
    }

    /** Toutes les tuiles (x, y, z) couvrant [bbox] pour un niveau de zoom donné. */
    fun tilesFor(bbox: Bbox, zoom: Int): List<Triple<Int, Int, Int>> {
        val xMin = lonToTileX(bbox.west, zoom)
        val xMax = lonToTileX(bbox.east, zoom)
        val yMin = latToTileY(bbox.north, zoom)
        val yMax = latToTileY(bbox.south, zoom)
        if (xMax < xMin || yMax < yMin) return emptyList()
        val out = ArrayList<Triple<Int, Int, Int>>((xMax - xMin + 1) * (yMax - yMin + 1))
        for (x in xMin..xMax) for (y in yMin..yMax) out.add(Triple(x, y, zoom))
        return out
    }

    /** Les tuiles de l'emprise au zoom donne, produites une a une : rien n'est tenu en memoire d'avance. */
    fun tileSequenceFor(bbox: Bbox, zoom: Int): Sequence<Triple<Int, Int, Int>> = sequence {
        val xMin = lonToTileX(bbox.west, zoom)
        val xMax = lonToTileX(bbox.east, zoom)
        val yMin = latToTileY(bbox.north, zoom)
        val yMax = latToTileY(bbox.south, zoom)
        for (x in xMin..xMax) for (y in yMin..yMax) yield(Triple(x, y, zoom))
    }

    /** Le couloir au zoom donne, produit tuile par tuile depuis sa grille de bits (cf. [tilesAlong]). */
    fun tileSequenceAlong(points: List<Pair<Double, Double>>, zoom: Int, radiusM: Double): Sequence<Triple<Int, Int, Int>> =
        sequence {
            val c = corridor(points, zoom, radiusM) ?: return@sequence
            for (i in 0 until c.height) {
                val row = c.row(i)
                var x = row.nextSetBit(0)
                while (x >= 0) { yield(Triple(c.x0 + x, c.y0 + i, zoom)); x = row.nextSetBit(x + 1) }
            }
        }

    /** Somme des tuiles sur toute la plage [minZoom, maxZoom]. */
    fun totalTileCount(bbox: Bbox, minZoom: Int, maxZoom: Int): Long =
        (minZoom..maxZoom).sumOf { tileCount(bbox, it) }

    /**
     * Les tuiles qu'un telechargement va demander : le couloir s'il y en a un, sinon tout le rectangle.
     *
     * Le compte et la liste vivent ici cote a cote, pour que la progression ne puisse plus diverger de ce
     * que le moteur telecharge : l'emprise d'un couloir est celle de la trace seule, sans sa largeur, et
     * compter ses tuiles annoncait un total bien inferieur aux tuiles reellement recues.
     */
    fun tileSequenceFor(req: OfflineDownloadRequest): Sequence<Triple<Int, Int, Int>> {
        val corridor = req.corridor
        return (req.minZoom..req.maxZoom).asSequence().flatMap { zoom ->
            if (corridor != null) tileSequenceAlong(corridor.points, zoom, corridor.radiusM)
            else tileSequenceFor(req.bbox, zoom)
        }
    }

    /** Nombre de tuiles de [tileSequenceFor], sans les enumerer une a une. */
    fun totalTileCount(req: OfflineDownloadRequest): Long {
        val corridor = req.corridor
        return if (corridor != null) totalTileCountAlong(corridor.points, req.minZoom, req.maxZoom, corridor.radiusM)
        else totalTileCount(req.bbox, req.minZoom, req.maxZoom)
    }

    /** Cote d'une tuile au sol, en metres, a la latitude et au zoom donnes. */
    private fun tileMeters(lat: Double, zoom: Int): Double =
        40_075_016.686 * cos(Math.toRadians(lat.coerceIn(-85.0511, 85.0511))) / (1L shl zoom)

    /**
     * Tuiles a moins de [radiusM] du parcours [points], au zoom donne : le **couloir** qui le borde.
     *
     * Une randonnee de soixante kilometres en diagonale tient dans un rectangle dont on ne verra jamais
     * les trois quarts. Le couloir ne prend que ce qui borde le trace, pour la meme sortie et une fraction
     * du telechargement.
     *
     * Le resultat est un ENSEMBLE : une trace qui revient sur elle-meme, un lacet, une boucle passent
     * plusieurs fois sur les memes tuiles, et chacune ne doit etre comptee - donc telechargee - qu'une fois.
     */
    fun tilesAlong(
        points: List<Pair<Double, Double>>, zoom: Int, radiusM: Double,
    ): List<Triple<Int, Int, Int>> {
        val c = corridor(points, zoom, radiusM) ?: return emptyList()
        val out = ArrayList<Triple<Int, Int, Int>>(c.count().toInt())
        for (i in 0 until c.height) {
            val row = c.row(i)
            var x = row.nextSetBit(0)
            while (x >= 0) { out.add(Triple(c.x0 + x, c.y0 + i, zoom)); x = row.nextSetBit(x + 1) }
        }
        return out
    }

    /** Tuiles du couloir sur toute la plage de zoom. Chaque niveau a son propre ensemble : une meme tuile
     *  ne peut pas exister a deux zooms differents. Compte sans rien construire tuile par tuile.
     *
     *  [checkCancelled] est appele a chaque rangee : il leve pour interrompre le calcul. Sans lui, un calcul
     *  remplace par le suivant (un cran de curseur de plus) continuait jusqu'au bout, et les calculs
     *  s'empilaient en parallele jusqu'a saturer la memoire. */
    fun totalTileCountAlong(
        points: List<Pair<Double, Double>>, minZoom: Int, maxZoom: Int, radiusM: Double,
        checkCancelled: () -> Unit = {},
    ): Long = (minZoom..maxZoom).sumOf { corridor(points, it, radiusM, checkCancelled)?.count(checkCancelled) ?: 0L }

    /**
     * Estimation du total du couloir pour l'ecran de configuration : exacte tant que le calcul reste
     * raisonnable, extrapolee au-dela.
     *
     * Le cout d'un niveau quadruple a chaque zoom - la grille grandit en largeur, en hauteur, et l'elargissement
     * se compte en plus de tuiles - et sur une longue trace aux derniers niveaux, il se chiffre en dizaines de
     * secondes sur un telephone. Quand un niveau depasserait [budget], lui et les suivants valent quatre fois le
     * niveau d'avant : le nombre de tuiles d'une forme quadruple a chaque zoom, et l'ecart tombe sous le
     * pour cent des que le couloir est large devant une tuile. Le telechargement, lui, garde le compte exact
     * ([totalTileCountAlong]).
     */
    fun estimateTileCountAlong(
        points: List<Pair<Double, Double>>, minZoom: Int, maxZoom: Int, radiusM: Double,
        budget: Double = CORRIDOR_COST_BUDGET, checkCancelled: () -> Unit = {},
    ): Long {
        var total = 0L
        var previousCount = -1L
        for (zoom in minZoom..maxZoom) {
            val count = if (previousCount >= 0 && corridorCost(points, zoom, radiusM) > budget) {
                previousCount * 4
            } else {
                corridor(points, zoom, radiusM, checkCancelled)?.count(checkCancelled) ?: 0L
            }
            total += count
            previousCount = count
        }
        return total
    }

    /** Ordre de grandeur du travail d'un niveau : rangees x elargissement vertical x mots d'une rangee. */
    private fun corridorCost(points: List<Pair<Double, Double>>, zoom: Int, radiusM: Double): Double {
        if (points.isEmpty()) return 0.0
        val n = 1 shl zoom
        val width = points.maxOf { tileXf(it.first, n) } - points.minOf { tileXf(it.first, n) }
        val height = points.maxOf { tileYf(it.second, n) } - points.minOf { tileYf(it.second, n) }
        val latMax = points.maxOf { kotlin.math.abs(it.second) }
        val range = radiusM.coerceAtLeast(0.0) / tileMeters(latMax, zoom).coerceAtLeast(1.0)
        return (height + 2 * range) * (2 * range + 1) * ((width + 2 * range) / 64 + 1)
    }

    /** Cout au-dela duquel un niveau est extrapole : environ trois secondes de calcul sur un telephone. */
    private const val CORRIDOR_COST_BUDGET = 3e9

    /** Le couloir a un zoom : une grille de bits, une rangee par ligne de tuiles, a partir de ([x0], [y0]). */
    private class Couloir(
        val x0: Int, val y0: Int,
        private val large: Array<java.util.BitSet>, private val portee: IntArray,
    ) {
        val height: Int get() = large.size

        /** La rangee [j] du couloir : l'union des rangees elargies dont la portee atteint [j]. Calculee a la
         *  demande, une a la fois, au lieu de garder toute la grille en memoire. */
        fun row(j: Int): java.util.BitSet {
            val union = java.util.BitSet()
            for (i in (j - maxRange).coerceAtLeast(0)..(j + maxRange).coerceAtMost(large.size - 1)) {
                if (kotlin.math.abs(i - j) <= portee[i]) union.or(large[i])
            }
            return union
        }

        private val maxRange: Int = portee.maxOrNull() ?: 0

        fun count(checkCancelled: () -> Unit = {}): Long {
            var total = 0L
            for (j in 0 until large.size) { checkCancelled(); total += row(j).cardinality() }
            return total
        }
    }

    private fun tileXf(lon: Double, n: Int): Double = (lon + 180.0) / 360.0 * n

    private fun tileYf(lat: Double, n: Int): Double {
        val r = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return (1.0 - kotlin.math.ln(kotlin.math.tan(r) + 1 / cos(r)) / Math.PI) / 2.0 * n
    }

    /** La latitude du milieu de la ligne de tuiles [y]. */
    private fun latOfRow(y: Int, n: Int): Double {
        val m = Math.PI * (1 - 2 * (y + 0.5) / n)
        return Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(m)))
    }

    /**
     * Le couloir, calcule en deux temps sur une grille de bits : la LIGNE du parcours d'abord, tuile par
     * tuile, puis son elargissement a [radiusM] de chaque cote, rangee par rangee.
     *
     * **C'est une correction de lenteur, et elle etait bloquante.** Le calcul d'avant posait autour de chaque
     * point du parcours un carre de tuiles, et rangeait chacune dans un ensemble : pour l'EV1 de Nantes a
     * Hendaye au zoom 17 avec cinq kilometres de chaque cote, des dizaines de millions d'operations et des
     * millions d'objets - refaits a chaque cran des curseurs de l'ecran de configuration, que l'application
     * cessait de rafraichir jusqu'a ce qu'Android propose de la fermer. Le meme couloir se trace ici en
     * quelques milliers de pas le long de la ligne, puis en operations sur des mots de 64 bits.
     *
     * Le parcours est parcouru par pas d'une DEMI-TUILE : deux sommets distants de plusieurs kilometres - une
     * ligne droite tracee a la regle - sauteraient sinon les tuiles entre eux. L'elargissement se prend a la
     * latitude de chaque rangee, ou une tuile n'a pas la meme taille au sol.
     */
    private fun corridor(
        points: List<Pair<Double, Double>>, zoom: Int, radiusM: Double, checkCancelled: () -> Unit = {},
    ): Couloir? {
        if (points.isEmpty()) return null
        val n = 1 shl zoom
        val xs = points.map { tileXf(it.first, n) }
        val ys = points.map { tileYf(it.second, n) }
        // L'elargissement le plus grand, pour dimensionner la grille : a la latitude la plus eloignee de
        // l'equateur, ou les tuiles sont les plus petites au sol.
        val latMax = points.maxOf { kotlin.math.abs(it.second) }
        val marge = kotlin.math.ceil(radiusM.coerceAtLeast(0.0) / tileMeters(latMax, zoom).coerceAtLeast(1.0)).toInt() + 1
        val x0 = (floor(xs.min()).toInt() - marge).coerceAtLeast(0)
        val x1 = (floor(xs.max()).toInt() + marge).coerceAtMost(n - 1)
        val y0 = (floor(ys.min()).toInt() - marge).coerceAtLeast(0)
        val y1 = (floor(ys.max()).toInt() + marge).coerceAtMost(n - 1)
        val largeur = x1 - x0 + 1
        val hauteur = y1 - y0 + 1
        val ligne = Array(hauteur) { java.util.BitSet() }
        fun poser(x: Double, y: Double) {
            val tx = floor(x).toInt().coerceIn(0, n - 1) - x0
            val ty = floor(y).toInt().coerceIn(0, n - 1) - y0
            if (tx in 0 until largeur && ty in 0 until hauteur) ligne[ty].set(tx)
        }
        poser(xs[0], ys[0])
        for (i in 0 until points.size - 1) {
            if (i % 256 == 0) checkCancelled()
            val dx = xs[i + 1] - xs[i]
            val dy = ys[i + 1] - ys[i]
            val pas = kotlin.math.ceil(kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dy)) * 2).toInt().coerceAtLeast(1)
            for (k in 1..pas) {
                val t = k.toDouble() / pas
                poser(xs[i] + dx * t, ys[i] + dy * t)
            }
        }
        // L'elargissement de chaque rangee, en tuiles, a sa propre latitude.
        val portee = IntArray(hauteur) { i ->
            kotlin.math.ceil(radiusM.coerceAtLeast(0.0) / tileMeters(latOfRow(y0 + i, n), zoom).coerceAtLeast(1.0)).toInt()
        }
        // Le long des rangees d'abord : chaque tuile de la ligne s'etend de sa portee a gauche et a droite.
        val large = Array(hauteur) { i ->
            checkCancelled()
            val r = portee[i]
            val src = ligne[i]
            val dst = java.util.BitSet()
            var x = src.nextSetBit(0)
            while (x >= 0) {
                dst.set((x - r).coerceAtLeast(0), (x + r + 1).coerceAtMost(largeur))
                x = src.nextSetBit(x + 1)
            }
            dst
        }
        // Puis d'une rangee aux voisines : le carre de portee autour de chaque tuile de la ligne, ce que
        // [Couloir.row] fait rangee par rangee, a la demande.
        return Couloir(x0, y0, large, portee)
    }

    fun estimateSizeBytes(tileCount: Long): Long = tileCount * AVG_TILE_BYTES

    /** Formate un nombre d'octets en Ko/Mo/Go lisible (ex. "12,3 Mo"). */
    fun formatSize(bytes: Long): String {
        val mb = bytes / 1_000_000.0
        return when {
            mb < 1.0 -> "${(bytes / 1000).coerceAtLeast(1)} Ko"
            mb < 1000.0 -> "%.1f Mo".format(mb)
            else -> "%.1f Go".format(mb / 1000.0)
        }
    }
}
