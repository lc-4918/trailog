package fr.lc4918.trailog.watch

import fr.lc4918.trailog.map.offline.TileMath

/**
 * Pavage des tuiles de la montre Garmin (application Trailog Garmin, depot prive).
 *
 * La montre affiche des tuiles de [WATCH_TILE_PIXELS] px a l'echelle du zoom [WATCH_ZOOM] : a ce zoom, une
 * tuile standard de 256 px en couvre quatre. Une tuile montre a donc exactement l'emprise d'une tuile
 * standard du zoom suivant, ce qui permet de reprendre tel quel le couloir de [TileMath.tilesAlong].
 *
 * Pourquoi 128 px et non 256 : le stockage de la montre refuse toute valeur de plus de 32 Ko, et une tuile
 * de 256 px en pese 64 a huit bits par pixel.
 */
object WatchTiles {
    const val WATCH_ZOOM = 15
    const val WATCH_TILE_PIXELS = 128

    /** Une tuile montre, numerotee dans la grille de 128 px du zoom [WATCH_ZOOM]. */
    data class WatchTile(val x: Int, val y: Int)

    /** La tuile standard (256 px, zoom [WATCH_ZOOM]) dont la tuile montre est un quart, et lequel. */
    data class SourceQuarter(val zoom: Int, val x: Int, val y: Int, val quarterColumn: Int, val quarterRow: Int)

    /** Les tuiles montre a moins de [radiusMeters] du parcours, points en (longitude, latitude). */
    fun tilesAlong(points: List<Pair<Double, Double>>, radiusMeters: Double): List<WatchTile> =
        TileMath.tilesAlong(points, WATCH_ZOOM + 1, radiusMeters).map { (x, y, _) -> WatchTile(x, y) }

    fun sourceQuarterOf(tile: WatchTile): SourceQuarter =
        SourceQuarter(WATCH_ZOOM, tile.x shr 1, tile.y shr 1, tile.x and 1, tile.y and 1)
}
