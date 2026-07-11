package fr.lc4918.trailog.domain.geo

import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.TrackPoint
import fr.lc4918.trailog.domain.model.TrackStats
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Calculs de tracé portés depuis la librairie JS ol-elevation-profile :
 * distance (haversine), lissage en mètres, pente par classe, D+/D-, et temps
 * en mouvement (les segments sous [stopSpeed] sont ignorés si [ignoreStops]).
 */
object TrackMath {

    private const val R = 6_371_000.0 // rayon Terre (m)

    fun haversine(aLon: Double, aLat: Double, bLon: Double, bLat: Double): Double {
        val la1 = Math.toRadians(aLat); val la2 = Math.toRadians(bLat)
        val dLa = la2 - la1
        val dLo = Math.toRadians(bLon - aLon)
        val h = sin(dLa / 2).let { it * it } +
            cos(la1) * cos(la2) * sin(dLo / 2).let { it * it }
        return 2 * R * atan2(sqrt(h), sqrt(1 - h))
    }

    fun hasZ(points: List<TrackPoint>): Boolean = points.any { it.ele != null }
    fun hasTime(points: List<TrackPoint>): Boolean = points.count { it.timeMs != null } >= 2

    /**
     * @param smoothingM fenêtre de lissage de l'altitude en mètres (0 = aucun)
     * @param maxPoints décimation pour le rendu (0 = aucune)
     * @param ignoreStops temps en mouvement (ignore les arrêts)
     * @param stopSpeed seuil d'arrêt en m/s
     */
    fun compute(
        points: List<TrackPoint>,
        smoothingM: Double = 0.0,
        maxPoints: Int = 2000,
        ignoreStops: Boolean = true,
        stopSpeed: Double = 0.5,
    ): ComputedTrack {
        val n = points.size
        val withTime = hasTime(points)
        if (n == 0) {
            return ComputedTrack(emptyList(), TrackStats(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, null, 0), false, false)
        }

        val x = DoubleArray(n)        // distance cumulée
        val z = DoubleArray(n)        // altitude
        val t = arrayOfNulls<Double>(n) // temps écoulé (s)

        var cum = 0.0
        var tAcc = 0.0
        var prevMs: Long? = null
        for (i in 0 until n) {
            val p = points[i]
            if (i > 0) cum += haversine(points[i - 1].lon, points[i - 1].lat, p.lon, p.lat)
            x[i] = cum
            z[i] = p.ele ?: 0.0
            val ms = p.timeMs
            if (withTime && ms != null) {
                if (prevMs != null) {
                    val dt = (ms - prevMs!!) / 1000.0
                    val dseg = if (i > 0) haversine(points[i - 1].lon, points[i - 1].lat, p.lon, p.lat) else 0.0
                    if (dt > 0 && (!ignoreStops || (dseg / dt) >= stopSpeed)) tAcc += dt
                }
                t[i] = tAcc
                prevMs = ms
            }
        }

        if (smoothingM > 0) smooth(x, z, smoothingM)

        // décimation
        val idx: IntArray = if (maxPoints > 0 && n > maxPoints) {
            val step = n.toDouble() / maxPoints
            IntArray(maxPoints + 1) { k -> if (k == maxPoints) n - 1 else floor(k * step).toInt() }
        } else IntArray(n) { it }

        val samples = ArrayList<Sample>(idx.size)
        for (k in idx.indices) {
            val i = idx[k]
            val slope = if (k > 0) {
                val prev = idx[k - 1]
                val ddx = x[i] - x[prev]
                if (ddx > 0) ((z[i] - z[prev]) / ddx) * 100.0 else 0.0
            } else 0.0
            samples.add(Sample(x[i], z[i], slope, t[i], points[i].lon, points[i].lat))
        }
        if (samples.size > 1) samples[0] = samples[0].copy(slope = samples[1].slope)

        val stats = statsOf(samples)
        return ComputedTrack(samples, stats, hasZ(points), withTime)
    }

    private fun smooth(x: DoubleArray, z: DoubleArray, meters: Double) {
        val n = z.size
        if (meters <= 0 || n < 3) return
        val half = meters / 2
        val out = DoubleArray(n)
        var lo = 0; var hi = 0; var sum = 0.0
        for (i in 0 until n) {
            val xi = x[i]
            while (lo < n && x[lo] < xi - half) { sum -= z[lo]; lo++ }
            while (hi < n && x[hi] <= xi + half) { sum += z[hi]; hi++ }
            out[i] = if (hi > lo) sum / (hi - lo) else z[i]
        }
        System.arraycopy(out, 0, z, 0, n)
    }

    private fun statsOf(s: List<Sample>): TrackStats {
        var ascent = 0.0; var descent = 0.0
        var zmin = Double.POSITIVE_INFINITY; var zmax = Double.NEGATIVE_INFINITY
        var maxAbs = 0.0
        for (k in s.indices) {
            val z = s[k].z
            if (z < zmin) zmin = z
            if (z > zmax) zmax = z
            if (k > 0) { val dz = z - s[k - 1].z; if (dz > 0) ascent += dz else descent -= dz }
            val sl = abs(s[k].slope); if (sl > maxAbs) maxAbs = sl
        }
        val distance = if (s.isNotEmpty()) s.last().x - s.first().x else 0.0
        val ta = s.firstOrNull()?.t; val tb = s.lastOrNull()?.t
        val duration = if (ta != null && tb != null) tb - ta else null
        return TrackStats(
            distance = distance,
            ascent = ascent, descent = descent,
            min = if (zmin.isFinite()) zmin else 0.0,
            max = if (zmax.isFinite()) zmax else 0.0,
            maxAbsSlope = maxAbs,
            duration = duration,
            points = s.size,
        )
    }
}
