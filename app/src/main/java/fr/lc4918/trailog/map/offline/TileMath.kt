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
            c.rows.forEachIndexed { i, row ->
                var x = row.nextSetBit(0)
                while (x >= 0) { yield(Triple(c.x0 + x, c.y0 + i, zoom)); x = row.nextSetBit(x + 1) }
            }
        }

    /** Somme des tuiles sur toute la plage [minZoom, maxZoom]. */
    fun totalTileCount(bbox: Bbox, minZoom: Int, maxZoom: Int): Long =
        (minZoom..maxZoom).sumOf { tileCount(bbox, it) }

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
        c.rows.forEachIndexed { i, row ->
            var x = row.nextSetBit(0)
            while (x >= 0) { out.add(Triple(c.x0 + x, c.y0 + i, zoom)); x = row.nextSetBit(x + 1) }
        }
        return out
    }

    /** Tuiles du couloir sur toute la plage de zoom. Chaque niveau a son propre ensemble : une meme tuile
     *  ne peut pas exister a deux zooms differents. Compte sans rien construire tuile par tuile. */
    fun totalTileCountAlong(
        points: List<Pair<Double, Double>>, minZoom: Int, maxZoom: Int, radiusM: Double,
    ): Long = (minZoom..maxZoom).sumOf { corridor(points, it, radiusM)?.count() ?: 0L }

    /** Le couloir a un zoom : une grille de bits, une rangee par ligne de tuiles, a partir de ([x0], [y0]). */
    private class Couloir(val x0: Int, val y0: Int, val rows: Array<java.util.BitSet>) {
        fun count(): Long = rows.sumOf { it.cardinality().toLong() }
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
    private fun corridor(points: List<Pair<Double, Double>>, zoom: Int, radiusM: Double): Couloir? {
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
        // Puis d'une rangee aux voisines : le carre de portee autour de chaque tuile de la ligne.
        val rows = Array(hauteur) { java.util.BitSet() }
        for (i in 0 until hauteur) {
            if (large[i].isEmpty) continue
            val r = portee[i]
            for (j in (i - r).coerceAtLeast(0)..(i + r).coerceAtMost(hauteur - 1)) rows[j].or(large[i])
        }
        return Couloir(x0, y0, rows)
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
