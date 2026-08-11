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
     * Le parcours est parcouru par pas d'une DEMI-TUILE : deux sommets distants de plusieurs kilometres -
     * une ligne droite tracee a la regle - sauteraient sinon toutes les tuiles entre eux. Chaque position
     * marque sa tuile et celles qui l'entourent dans le rayon demande.
     *
     * Le resultat est un ENSEMBLE : une trace qui revient sur elle-meme, un lacet, une boucle passent
     * plusieurs fois sur les memes tuiles, et chacune ne doit etre comptee - donc telechargee - qu'une fois.
     */
    fun tilesAlong(
        points: List<Pair<Double, Double>>, zoom: Int, radiusM: Double,
    ): List<Triple<Int, Int, Int>> {
        if (points.isEmpty()) return emptyList()
        val n = 1 shl zoom
        val seen = LinkedHashSet<Long>()
        val out = ArrayList<Triple<Int, Int, Int>>()

        fun mark(lon: Double, lat: Double) {
            val side = tileMeters(lat, zoom).coerceAtLeast(1.0)
            val reach = kotlin.math.ceil(radiusM / side).toInt().coerceAtLeast(0)
            val (cx, cy) = tileAt(lon, lat, zoom)
            for (x in (cx - reach)..(cx + reach)) {
                for (y in (cy - reach)..(cy + reach)) {
                    if (x < 0 || y < 0 || x >= n || y >= n) continue
                    if (seen.add(x.toLong() * n + y)) out.add(Triple(x, y, zoom))
                }
            }
        }

        mark(points[0].first, points[0].second)
        for (i in 0 until points.size - 1) {
            val (lon1, lat1) = points[i]
            val (lon2, lat2) = points[i + 1]
            val step = tileMeters((lat1 + lat2) / 2, zoom).coerceAtLeast(1.0) / 2
            val length = haversine(lon1, lat1, lon2, lat2)
            val steps = kotlin.math.ceil(length / step).toInt().coerceAtLeast(1)
            for (k in 1..steps) {
                val t = k.toDouble() / steps
                mark(lon1 + (lon2 - lon1) * t, lat1 + (lat2 - lat1) * t)
            }
        }
        return out
    }

    /** Tuiles du couloir sur toute la plage de zoom. Chaque niveau a son propre ensemble : une meme tuile
     *  ne peut pas exister a deux zooms differents. */
    fun totalTileCountAlong(
        points: List<Pair<Double, Double>>, minZoom: Int, maxZoom: Int, radiusM: Double,
    ): Long = (minZoom..maxZoom).sumOf { tilesAlong(points, it, radiusM).size.toLong() }

    /** Distance entre deux points, en metres. Recopiee ici plutot qu'empruntee a `domain/geo` : ce module
     *  ne depend de rien, et c'est ce qui lui permet d'etre teste sans le reste. */
    private fun haversine(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
        val r = 6_371_000.0
        val la1 = Math.toRadians(lat1); val la2 = Math.toRadians(lat2)
        val dLa = la2 - la1
        val dLo = Math.toRadians(lon2 - lon1)
        val h = kotlin.math.sin(dLa / 2).let { it * it } +
            cos(la1) * cos(la2) * kotlin.math.sin(dLo / 2).let { it * it }
        return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
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
