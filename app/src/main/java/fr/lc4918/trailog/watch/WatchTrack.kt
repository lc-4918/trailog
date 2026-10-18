package fr.lc4918.trailog.watch

import fr.lc4918.trailog.data.repo.LayerGeoJson
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.TrackPoint
import kotlin.math.roundToInt

/**
 * La trace telle que la montre la dessine : simplifiee, en micro-degres entiers.
 *
 * La montre n'a ni la place ni le temps de calcul d'une trace complete, et son ecran trace au mieux un
 * point par pixel (3 a 4 m au zoom [WatchTiles.WATCH_ZOOM]). Des entiers plutot que des flottants : un
 * micro-degre (0,11 m) tient dans un Number de la montre et la reponse JSON reste courte.
 *
 * Chaque point porte sa distance depuis le depart, le long de la trace simplifiee : la montre en tire la
 * distance parcourue et restante sans rien cumuler elle-meme.
 */
object WatchTrack {

    /** Ecart tolere a la simplification : environ deux pixels de la montre. */
    const val SIMPLIFY_TOLERANCE_METERS = 8.0
    private const val MICRO_DEGREES = 1_000_000.0

    /** Un point de la trace : micro-degres de latitude et de longitude, et metres depuis le depart. */
    data class WatchTrackPoint(val latitudeMicro: Int, val longitudeMicro: Int, val distanceMeters: Int = 0)

    /** La trace [points] (longitude, latitude), simplifiee. */
    fun prepare(points: List<Pair<Double, Double>>): List<WatchTrackPoint> {
        val simplified = LayerGeoJson.simplifyLine(
            points.map { (longitude, latitude) -> TrackPoint(longitude, latitude) }, SIMPLIFY_TOLERANCE_METERS)
        var distanceMeters = 0.0
        return simplified.mapIndexed { index, point ->
            if (index > 0) {
                val previous = simplified[index - 1]
                distanceMeters += TrackMath.haversine(previous.lon, previous.lat, point.lon, point.lat)
            }
            WatchTrackPoint((point.lat * MICRO_DEGREES).roundToInt(), (point.lon * MICRO_DEGREES).roundToInt(), distanceMeters.roundToInt())
        }
    }
}
