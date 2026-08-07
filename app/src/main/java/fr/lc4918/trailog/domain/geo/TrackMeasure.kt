package fr.lc4918.trailog.domain.geo

import fr.lc4918.trailog.domain.model.Sample
import kotlin.math.cos

/**
 * Mesure entre deux points du parcours d'une trace.
 *
 * Le doigt ne tombe jamais pile sur la ligne : chaque tap est rabattu sur son projete orthogonal, segment
 * par segment, et c'est le plus proche qui l'emporte. Le parametre de projection est borne a [0, 1], si
 * bien qu'un tap au-dela d'un bout de trace se pose sur ce bout - le depart ou l'arrivee - sans cas
 * particulier a ecrire.
 *
 * Le calcul se fait dans un plan local (les longitudes resserrees par le cosinus de la latitude) : sur les
 * quelques dizaines de metres que couvre un tap, l'ecart avec la sphere est sans consequence, et une
 * projection exacte y couterait une trigonometrie par segment de trace.
 *
 * La distance mesuree, elle, ne vient PAS de ce plan : elle se lit sur [Sample.x], le kilometrage cumule
 * depuis le debut de la trace (calcule en haversine sur la geometrie complete, avant toute decimation).
 * Mesurer, c'est donc soustraire deux kilometrages, et le resultat suit le parcours reel.
 */
object TrackMeasure {

    /** Un tap rabattu sur la trace : ou il tombe, a quel kilometrage, et a quelle distance du doigt. */
    data class Projection(
        val lon: Double,
        val lat: Double,
        /** Kilometrage du point retenu, depuis le debut de la trace (m). */
        val alongM: Double,
        /** Ecart entre le doigt et le point retenu (m) : departage les traces voisines. */
        val awayM: Double,
    )

    /** Projete (lon, lat) sur la ligne brisee [samples]. Null si la trace est vide. */
    fun project(samples: List<Sample>, lon: Double, lat: Double): Projection? {
        if (samples.isEmpty()) return null
        val kx = cos(Math.toRadians(lat))
        var best: Projection? = null
        for (i in 0 until samples.size - 1) {
            val a = samples[i]
            val b = samples[i + 1]
            val abx = (b.lon - a.lon) * kx
            val aby = b.lat - a.lat
            val apx = (lon - a.lon) * kx
            val apy = lat - a.lat
            val len2 = abx * abx + aby * aby
            val t = if (len2 <= 0.0) 0.0 else ((apx * abx + apy * aby) / len2).coerceIn(0.0, 1.0)
            val plon = a.lon + (b.lon - a.lon) * t
            val plat = a.lat + (b.lat - a.lat) * t
            val away = TrackMath.haversine(lon, lat, plon, plat)
            if (best == null || away < best.awayM) {
                best = Projection(plon, plat, a.x + (b.x - a.x) * t, away)
            }
        }
        // Trace d'un seul point : aucun segment a parcourir, ce point est la reponse.
        if (best == null) {
            val s = samples[0]
            best = Projection(s.lon, s.lat, s.x, TrackMath.haversine(lon, lat, s.lon, s.lat))
        }
        return best
    }

    /** Point de la trace situe au kilometrage [alongM] : sert a poser l'infobulle au milieu de la mesure. */
    fun pointAt(samples: List<Sample>, alongM: Double): Pair<Double, Double>? {
        if (samples.isEmpty()) return null
        val first = samples.first()
        if (alongM <= first.x) return first.lon to first.lat
        val last = samples.last()
        if (alongM >= last.x) return last.lon to last.lat
        for (i in 0 until samples.size - 1) {
            val a = samples[i]
            val b = samples[i + 1]
            if (alongM <= b.x) {
                val span = b.x - a.x
                val t = if (span <= 0.0) 0.0 else (alongM - a.x) / span
                return (a.lon + (b.lon - a.lon) * t) to (a.lat + (b.lat - a.lat) * t)
            }
        }
        return last.lon to last.lat
    }
}