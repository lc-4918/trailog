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
