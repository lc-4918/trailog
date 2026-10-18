package fr.lc4918.trailog.watch

import fr.lc4918.trailog.data.repo.LayerGeoJson
import fr.lc4918.trailog.domain.model.TrackPoint
import kotlin.math.roundToInt

/**
 * La trace telle que la montre la dessine : simplifiee, en micro-degres entiers.
 *
 * La montre n'a ni la place ni le temps de calcul d'une trace complete, et son ecran trace au mieux un
 * point par pixel (3 a 4 m au zoom [WatchTiles.WATCH_ZOOM]). Des entiers plutot que des flottants : un
 * micro-degre (0,11 m) tient dans un Number de la montre et la reponse JSON reste courte.
 */
object WatchTrack {

    /** Ecart tolere a la simplification : environ deux pixels de la montre. */
    const val SIMPLIFY_TOLERANCE_METERS = 8.0
    private const val MICRO_DEGREES = 1_000_000.0

    /** Un point de la trace, en micro-degres de latitude et de longitude. */
    data class WatchTrackPoint(val latitudeMicro: Int, val longitudeMicro: Int)

    /** La trace [points] (longitude, latitude), simplifiee. */
    fun prepare(points: List<Pair<Double, Double>>): List<WatchTrackPoint> =
        LayerGeoJson.simplifyLine(points.map { (longitude, latitude) -> TrackPoint(longitude, latitude) }, SIMPLIFY_TOLERANCE_METERS)
            .map { WatchTrackPoint((it.lat * MICRO_DEGREES).roundToInt(), (it.lon * MICRO_DEGREES).roundToInt()) }
}
