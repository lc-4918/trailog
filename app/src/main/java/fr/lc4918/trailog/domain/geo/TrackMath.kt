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
 * en mouvement (les segments sous stopSpeed sont ignorés si ignoreStops).
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

    /**
     * Duree de marche estimee, en secondes, d'apres la **fonction de Tobler**.
     *
     * Sert aux traces qui n'ont pas d'horodatage - un parcours dessine, un itineraire recu, un export qui
     * n'a pas garde les temps : la question qu'on se pose devant est "combien de temps ca me prend", et
     * une distance seule n'y repond pas en montagne.
     *
     * Tobler donne la vitesse de marche en fonction de la PENTE : `v = 6 x exp(-3,5 x |p + 0,05|)` km/h,
     * ou p est la pente en tangente. Le decalage de 0,05 place le maximum en legere descente (environ -3 %),
     * ou l'on marche effectivement le plus vite, et non a plat - c'est ce que cette fonction apporte sur
     * une simple moyenne, et c'est ce qui la rend utile en montagne : cinq kilometres de plat et cinq
     * kilometres de raide ne se marchent pas dans le meme temps.
     *
     * Ce n'est qu'une estimation, jamais un temps mesure : l'affichage la marque d'un "~" (cf.
     * `TrackInfoColumns`). Elle ne compte ni pause, ni repas, ni photo, et suppose une marche reguliere -
     * elle est optimiste pour qui flane, pessimiste pour qui court.
     *
     * Zero pour une trace sans altitude exploitable : la formule y verrait un immense plat et rendrait un
     * temps de coureur sur un parcours de montagne. Mieux vaut ne rien annoncer.
     */
    fun toblerSeconds(samples: List<Sample>): Double {
        if (samples.size < 2) return 0.0
        var seconds = 0.0
        for (i in 1 until samples.size) {
            val dx = samples[i].x - samples[i - 1].x
            if (dx <= 0.0) continue
            val slope = (samples[i].z - samples[i - 1].z) / dx
            // 6 km/h = 1,667 m/s, la vitesse de reference de Tobler a plat.
            val speed = 1.66667 * kotlin.math.exp(-3.5 * abs(slope + 0.05))
            if (speed > 0.0) seconds += dx / speed
        }
        return seconds
    }

    /**
     * L'echantillon a l'abscisse [x], **interpole entre les deux points qui l'encadrent**.
     *
     * C'est ce qui permet au point courant de se poser n'importe ou sur le parcours, et non seulement sur
     * un sommet de la trace. Un profil affiche jusqu'a deux mille echantillons pour une trace qui en porte
     * dix fois plus : sans interpolation, le curseur saute d'un echantillon a l'autre, et la coupe qui s'y
     * fiait ne pouvait tomber qu'entre deux points deja presents.
     *
     * Tout est interpole lineairement - position, altitude, temps - sauf la pente, prise a l'echantillon
     * suivant : c'est LA pente du troncon qu'on est en train de parcourir, la moyenner avec celle du
     * troncon precedent l'adoucirait precisement la ou elle change.
     *
     * Null pour une trace vide. Borne aux deux extremites : demander une abscisse au-dela du bout rend le
     * bout, sans cas particulier a ecrire chez l'appelant.
     */
    fun sampleAt(samples: List<Sample>, x: Double): Sample? {
        if (samples.isEmpty()) return null
        if (x <= samples.first().x) return samples.first()
        if (x >= samples.last().x) return samples.last()
        // Recherche dichotomique du premier echantillon a droite : une trace en porte jusqu'a deux mille,
        // et le curseur se deplace au doigt, donc plusieurs fois par seconde.
        var lo = 0; var hi = samples.size - 1
        while (lo < hi) { val m = (lo + hi) / 2; if (samples[m].x < x) lo = m + 1 else hi = m }
        val b = samples[lo]
        val a = samples[(lo - 1).coerceAtLeast(0)]
        val span = b.x - a.x
        if (span <= 0.0) return b
        val t = ((x - a.x) / span).coerceIn(0.0, 1.0)
        return Sample(
            x = x,
            z = a.z + (b.z - a.z) * t,
            slope = b.slope,
            t = if (a.t != null && b.t != null) a.t + (b.t - a.t) * t else b.t,
            lon = a.lon + (b.lon - a.lon) * t,
            lat = a.lat + (b.lat - a.lat) * t,
        )
    }

    /** Ce qui reste a parcourir depuis une abscisse : la distance, et le denivele positif. */
    data class Remaining(val distance: Double, val ascent: Double)

    /**
     * Ce qui reste de la trace a partir du kilometrage [alongM] : la distance, et le D+.
     *
     * Le D+ restant est ce qu'on vient chercher en cours de sortie ; la distance seule ne dit pas si les
     * trois derniers kilometres sont une descente ou le mur du col. Le point de depart tombe rarement sur
     * un echantillon : l'altitude y est interpolee entre ses deux voisins, faute de quoi la montee du
     * segment en cours serait comptee en entier alors qu'on en a deja fait la moitie.
     */
    fun remaining(samples: List<Sample>, alongM: Double): Remaining {
        if (samples.isEmpty()) return Remaining(0.0, 0.0)
        val end = samples.last().x
        if (alongM >= end) return Remaining(0.0, 0.0)
        val from = alongM.coerceAtLeast(samples.first().x)
        // Premier echantillon devant nous, et altitude a l'endroit exact ou l'on se trouve.
        var i = samples.indexOfFirst { it.x >= from }
        if (i <= 0) i = 1
        val prev = samples[i - 1]
        val next = samples[i]
        val span = next.x - prev.x
        var z = if (span > 0) prev.z + (next.z - prev.z) * ((from - prev.x) / span) else prev.z
        var ascent = 0.0
        for (k in i until samples.size) {
            val dz = samples[k].z - z
            if (dz > 0) ascent += dz
            z = samples[k].z
        }
        return Remaining(end - from, ascent)
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

    /** Stats (distance/D+/D-/min/max/duree) d'une liste de samples : reutilisable telle quelle sur une
     *  sous-plage (x/t deja cumules depuis le debut de la trace, la difference premier/dernier donne la
     *  valeur relative a la sous-plage) - cf. zoom sur profil dans MainViewModel. */
    fun statsOf(s: List<Sample>): TrackStats {
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
