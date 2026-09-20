package fr.lc4918.trailog.map.offline

import fr.lc4918.trailog.domain.geo.TrackMath

/**
 * Une portion de trace, entre deux kilometrages : ce qu'on emporte quand on ne veut la carte que d'une
 * partie du parcours - la semaine qui vient d'un GR de trois cents kilometres.
 *
 * Les points sont des paires (longitude, latitude), comme le parcours du couloir (cf. OfflineCorridor).
 */
object TrackSection {

    /** Kilometrage cumule a chaque point (m) : le premier a 0, le dernier a la longueur de la trace. */
    fun cumulative(points: List<Pair<Double, Double>>): DoubleArray {
        val out = DoubleArray(points.size)
        for (i in 1 until points.size) {
            val (aLon, aLat) = points[i - 1]
            val (bLon, bLat) = points[i]
            out[i] = out[i - 1] + TrackMath.haversine(aLon, aLat, bLon, bLat)
        }
        return out
    }

    /** Longueur de la trace (m). */
    fun length(points: List<Pair<Double, Double>>): Double = cumulative(points).lastOrNull() ?: 0.0

    /**
     * La portion entre [fromM] et [toM], ses deux bouts INTERPOLES : un kilometrage tombe rarement sur un
     * point, et arrondir au point voisin decalerait le debut de plusieurs centaines de metres sur une trace
     * aux points espaces. Rend la trace entiere quand la portion la couvre.
     */
    fun slice(points: List<Pair<Double, Double>>, fromM: Double, toM: Double): List<Pair<Double, Double>> {
        if (points.size < 2) return points
        val cum = cumulative(points)
        val total = cum.last()
        val a = fromM.coerceIn(0.0, total)
        val b = toM.coerceIn(a, total)
        if (a <= 0.0 && b >= total) return points
        val out = ArrayList<Pair<Double, Double>>()
        out += pointAt(points, cum, a)
        for (i in points.indices) if (cum[i] > a && cum[i] < b) out += points[i]
        out += pointAt(points, cum, b)
        return out
    }

    private fun pointAt(points: List<Pair<Double, Double>>, cum: DoubleArray, m: Double): Pair<Double, Double> {
        var i = cum.indexOfFirst { it >= m }
        if (i <= 0) return points[if (i < 0) points.lastIndex else 0]
        val seg = cum[i] - cum[i - 1]
        val t = if (seg > 0) (m - cum[i - 1]) / seg else 0.0
        val (aLon, aLat) = points[i - 1]
        val (bLon, bLat) = points[i]
        return (aLon + (bLon - aLon) * t) to (aLat + (bLat - aLat) * t)
    }

    /** L'emprise d'une portion : le rectangle qui la contient. */
    fun bboxOf(points: List<Pair<Double, Double>>): Bbox = Bbox.of(
        points.minOf { it.first }, points.minOf { it.second }, points.maxOf { it.first }, points.maxOf { it.second },
    )
}
