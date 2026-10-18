package fr.lc4918.trailog.watch

import fr.lc4918.trailog.watch.WatchTrack.WatchTrackPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Les virages de la trace, calcules sur sa seule geometrie : une trace importee n'a pas d'instructions.
 *
 * Un virage est un changement de direction net, mesure sur [LOOK_METERS] de part et d'autre du sommet : un
 * lacet serre en est un, une courbe qui tourne doucement sur deux cents metres n'en est pas un. Les
 * sommets voisins d'un meme virage (une trace simplifiee en garde souvent deux ou trois) sont fondus en un
 * seul, celui qui tourne le plus.
 */
object WatchTurns {

    /** Changement de direction en dessous duquel on continue tout droit. */
    const val MIN_ANGLE_DEGREES = 35
    /** Distance de part et d'autre du sommet sur laquelle la direction est mesuree. */
    const val LOOK_METERS = 30
    /** Deux sommets a moins de cela l'un de l'autre font le meme virage. */
    const val MERGE_METERS = 40
    /** Avant ce recul depuis le depart ou l'arrivee, une direction n'est pas assez etablie pour parler de virage. */
    private const val MIN_LEG_METERS = 10

    private const val METERS_PER_MICRO_DEGREE_LATITUDE = 0.11054
    private const val METERS_PER_MICRO_DEGREE_LONGITUDE_AT_EQUATOR = 0.11132

    /**
     * Un virage : ou il est sur la trace ([distanceMeters] depuis le depart, comme [WatchTrackPoint]),
     * de combien on tourne ([angleDegrees], positif a droite, negatif a gauche, de -180 a 180) et vers
     * quel cap on repart ([headingDegrees], 0 au nord, vers l'est).
     */
    data class WatchTurn(
        val distanceMeters: Int,
        val latitudeMicro: Int,
        val longitudeMicro: Int,
        val angleDegrees: Int,
        val headingDegrees: Int,
    )

    fun detect(track: List<WatchTrackPoint>): List<WatchTurn> {
        val candidates = ArrayList<WatchTurn>()
        for (index in 1 until track.size - 1) {
            val vertex = track[index]
            val before = pointAtLeastBefore(track, index) ?: continue
            val after = pointAtLeastAfter(track, index) ?: continue
            val incoming = bearingDegrees(before, vertex)
            val outgoing = bearingDegrees(vertex, after)
            val angle = normalize(outgoing - incoming)
            if (abs(angle) < MIN_ANGLE_DEGREES) continue
            candidates.add(
                WatchTurn(
                    vertex.distanceMeters, vertex.latitudeMicro, vertex.longitudeMicro,
                    angle.roundToInt(), ((outgoing + 360.0) % 360.0).roundToInt() % 360,
                ),
            )
        }
        return merge(candidates)
    }

    /** Le point le plus proche situe a [LOOK_METERS] ou plus avant le sommet ; le depart s'il est a plus de [MIN_LEG_METERS]. */
    private fun pointAtLeastBefore(track: List<WatchTrackPoint>, index: Int): WatchTrackPoint? {
        val target = track[index].distanceMeters - LOOK_METERS
        var candidate = index - 1
        while (candidate > 0 && track[candidate].distanceMeters > target) candidate--
        return track[candidate].takeIf { track[index].distanceMeters - it.distanceMeters >= MIN_LEG_METERS }
    }

    private fun pointAtLeastAfter(track: List<WatchTrackPoint>, index: Int): WatchTrackPoint? {
        val target = track[index].distanceMeters + LOOK_METERS
        var candidate = index + 1
        while (candidate < track.size - 1 && track[candidate].distanceMeters < target) candidate++
        return track[candidate].takeIf { it.distanceMeters - track[index].distanceMeters >= MIN_LEG_METERS }
    }

    private fun merge(candidates: List<WatchTurn>): List<WatchTurn> {
        val merged = ArrayList<WatchTurn>()
        var group = ArrayList<WatchTurn>()
        for (candidate in candidates) {
            if (group.isNotEmpty() && candidate.distanceMeters - group.last().distanceMeters > MERGE_METERS) {
                merged.add(group.maxBy { abs(it.angleDegrees) })
                group = ArrayList()
            }
            group.add(candidate)
        }
        if (group.isNotEmpty()) merged.add(group.maxBy { abs(it.angleDegrees) })
        return merged
    }

    /** Cap de [from] vers [to], en degres depuis le nord vers l'est : projection locale, suffisante a cette echelle. */
    private fun bearingDegrees(from: WatchTrackPoint, to: WatchTrackPoint): Double {
        val northMeters = (to.latitudeMicro - from.latitudeMicro) * METERS_PER_MICRO_DEGREE_LATITUDE
        val latitudeRadians = Math.toRadians(from.latitudeMicro / 1_000_000.0)
        val eastMeters = (to.longitudeMicro - from.longitudeMicro) * METERS_PER_MICRO_DEGREE_LONGITUDE_AT_EQUATOR * cos(latitudeRadians)
        return Math.toDegrees(atan2(eastMeters, northMeters))
    }

    /** Ramene un ecart d'angle dans ]-180, 180]. */
    private fun normalize(degrees: Double): Double {
        var result = degrees % 360.0
        if (result > 180.0) result -= 360.0
        if (result <= -180.0) result += 360.0
        return result
    }
}
