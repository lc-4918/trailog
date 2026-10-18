package fr.lc4918.trailog.watch

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/**
 * Pavage des tuiles de la montre Garmin (application Trailog Garmin, depot prive).
 *
 * La montre affiche des tuiles de [WATCH_TILE_PIXELS] px posees sur une grille a l'echelle d'un zoom, le
 * **niveau de detail** de l'envoi ([Detail]). Une tuile montre fait la moitie d'une tuile standard de 256 px du
 * meme zoom, soit un quart de sa surface. Le zoom peut etre fractionnaire : la grille n'a pas besoin de tomber
 * sur celle d'un fond, c'est le telephone qui decoupe et reechantillonne ses tuiles ([WatchTileImages]).
 *
 * Pourquoi 128 px et non 256 : le stockage de la montre refuse toute valeur de plus de 32 Ko, et une tuile
 * de 256 px en pese 64 a huit bits par pixel.
 */
object WatchTiles {
    const val WATCH_TILE_PIXELS = 128

    /**
     * Le niveau de detail : plus le zoom est haut, plus la carte est fine et plus il faut de tuiles pour une
     * meme trace (un quart de plus par demi-niveau, a peu pres le double).
     */
    enum class Detail(val zoom: Double) {
        STANDARD(15.5),
        FINE(16.0),
        VERY_FINE(16.5),
        ;

        /** Le zoom entier du fond dont on tire les tuiles : celui qui a au moins ce detail. */
        val sourceZoom: Int get() = ceil(zoom).toInt()

        companion object {
            /** Le niveau propose d'abord : le zoom 16, a 1,7 m/px, le plus fin dont le Scan 25 a le detail propre. */
            val DEFAULT = FINE

            /** Retrouve un niveau par son zoom (celui du manifeste), ou null. */
            fun ofZoom(zoom: Double): Detail? = entries.firstOrNull { abs(it.zoom - zoom) < 1e-6 }
        }
    }

    /** Une tuile montre, numerotee dans la grille de 128 px du zoom du niveau de detail. */
    data class WatchTile(val x: Int, val y: Int)

    private const val WORLD_METERS = 40_075_016.686
    private const val HALF_WORLD_METERS = WORLD_METERS / 2
    private const val EARTH_RADIUS_METERS = WORLD_METERS / (2 * PI)

    /** Cote au sol d'une tuile montre en metres Mercator, au zoom donne. */
    fun tileMercatorMeters(zoom: Double): Double = WORLD_METERS / 2.0.pow(zoom) / 2

    /** Metres par pixel de la montre au sol, a la latitude donnee : ce que le niveau de detail rend de la carte. */
    fun metersPerPixel(latitude: Double, zoom: Double): Double =
        tileMercatorMeters(zoom) * cos(Math.toRadians(latitude)) / WATCH_TILE_PIXELS

    /**
     * Les tuiles montre a moins de [radiusMeters] (au sol) du parcours [points] (longitude, latitude), au niveau
     * de detail [detail], rangees par ligne puis par colonne. Une tuile en fait partie si son carre touche la
     * bande de [radiusMeters] de chaque cote du trace, pas seulement si son centre y tombe.
     */
    fun tilesAlong(points: List<Pair<Double, Double>>, radiusMeters: Double, detail: Detail = Detail.DEFAULT): List<WatchTile> {
        if (points.isEmpty()) return emptyList()
        val size = tileMercatorMeters(detail.zoom)
        val found = HashSet<Long>()
        val segmentCount = max(1, points.size - 1)
        for (index in 0 until segmentCount) {
            val from = points[index]
            val to = points[min(index + 1, points.size - 1)]
            // Un rayon au sol vaut plus de metres Mercator quand on s'eloigne de l'equateur.
            val radius = radiusMeters.coerceAtLeast(0.0) / cos(Math.toRadians((from.second + to.second) / 2))
            val ax = mercatorX(from.first); val ay = mercatorY(from.second)
            val bx = mercatorX(to.first); val by = mercatorY(to.second)
            val columnFirst = floor((min(ax, bx) - radius + HALF_WORLD_METERS) / size).toInt()
            val columnLast = floor((max(ax, bx) + radius + HALF_WORLD_METERS) / size).toInt()
            // Les lignes comptent depuis le nord : y Mercator croit vers le nord, l'indice vers le sud.
            val rowFirst = floor((HALF_WORLD_METERS - (max(ay, by) + radius)) / size).toInt()
            val rowLast = floor((HALF_WORLD_METERS - (min(ay, by) - radius)) / size).toInt()
            for (row in rowFirst..rowLast) for (column in columnFirst..columnLast) {
                val left = column * size - HALF_WORLD_METERS
                val top = HALF_WORLD_METERS - row * size
                if (distanceToSquare(ax, ay, bx, by, left, top - size, left + size, top) <= radius) {
                    found.add((column.toLong() shl 32) or (row.toLong() and 0xFFFFFFFFL))
                }
            }
        }
        return found.map { WatchTile((it shr 32).toInt(), it.toInt()) }.sortedWith(compareBy({ it.y }, { it.x }))
    }

    private fun mercatorX(longitude: Double) = longitude / 360.0 * WORLD_METERS
    private fun mercatorY(latitude: Double) = ln(tan(PI / 4 + Math.toRadians(latitude.coerceIn(-85.0511, 85.0511)) / 2)) * EARTH_RADIUS_METERS

    /** Distance entre le segment (ax, ay)-(bx, by) et le carre [left, right] x [bottom, top] : 0 s'ils se touchent. */
    private fun distanceToSquare(ax: Double, ay: Double, bx: Double, by: Double, left: Double, bottom: Double, right: Double, top: Double): Double {
        if (inside(ax, ay, left, bottom, right, top) || inside(bx, by, left, bottom, right, top)) return 0.0
        val corners = doubleArrayOf(left, bottom, right, bottom, right, top, left, top)
        var best = Double.MAX_VALUE
        for (edge in 0 until 4) {
            val cx = corners[edge * 2]; val cy = corners[edge * 2 + 1]
            val dx = corners[(edge * 2 + 2) % 8]; val dy = corners[(edge * 2 + 3) % 8]
            if (segmentsCross(ax, ay, bx, by, cx, cy, dx, dy)) return 0.0
            best = min(best, min(min(pointToSegment(ax, ay, cx, cy, dx, dy), pointToSegment(bx, by, cx, cy, dx, dy)),
                min(pointToSegment(cx, cy, ax, ay, bx, by), pointToSegment(dx, dy, ax, ay, bx, by))))
        }
        return best
    }

    private fun inside(x: Double, y: Double, left: Double, bottom: Double, right: Double, top: Double) =
        x in left..right && y in bottom..top

    private fun pointToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax; val dy = by - ay
        val length2 = dx * dx + dy * dy
        val t = if (length2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / length2).coerceIn(0.0, 1.0)
        val ex = px - (ax + t * dx); val ey = py - (ay + t * dy)
        return Math.sqrt(ex * ex + ey * ey)
    }

    private fun segmentsCross(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double, dx: Double, dy: Double): Boolean {
        fun side(px: Double, py: Double, qx: Double, qy: Double, rx: Double, ry: Double) = (qx - px) * (ry - py) - (qy - py) * (rx - px)
        val d1 = side(cx, cy, dx, dy, ax, ay); val d2 = side(cx, cy, dx, dy, bx, by)
        val d3 = side(ax, ay, bx, by, cx, cy); val d4 = side(ax, ay, bx, by, dx, dy)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }
}
