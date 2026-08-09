package fr.lc4918.trailog.domain.geo

import fr.lc4918.trailog.domain.model.Sample
import kotlin.math.ceil
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

    /** Pas d'echantillonnage du parcours mesure (m), et nombre de points au-dela duquel on l'etire. */
    private const val PortionStepM = 25.0
    private const val PortionMaxSteps = 800

    /**
     * Parcours mesure echantillonne a pas constant, du kilometrage [fromM] a [toM] : les ancres possibles
     * de l'infobulle, du milieu vers les bords.
     *
     * Le nombre de points est toujours IMPAIR, si bien que l'element central est exactement le milieu du
     * parcours - la ou l'infobulle pointe tant qu'il est a l'ecran. Les autres sont ses replis : le milieu
     * sorti de l'emprise, l'ancre glisse dans cette liste jusqu'au premier point encore visible, et comme
     * le pas est constant, s'ecarter d'un index c'est s'ecarter d'autant de parcours (cf. MeasureAnchor).
     *
     * Le pas vise 25 m - assez fin pour qu'un bout de trace visible au zoom le plus serre contienne
     * toujours une ancre - mais s'etire au-dela de 800 intervalles : sur une mesure de plusieurs dizaines
     * de kilometres, projeter la liste entiere couterait plus que la finesse ne rapporte.
     */
    fun portion(samples: List<Sample>, fromM: Double, toM: Double): List<Pair<Double, Double>> {
        if (samples.isEmpty()) return emptyList()
        if (samples.size == 1) return listOf(samples[0].lon to samples[0].lat)
        val from = minOf(fromM, toM)
        val to = maxOf(fromM, toM)
        var steps = ceil((to - from) / PortionStepM).toInt().coerceIn(2, PortionMaxSteps)
        if (steps % 2 != 0) steps++      // nombre d'intervalles pair -> nombre de points impair
        val step = (to - from) / steps
        val out = ArrayList<Pair<Double, Double>>(steps + 1)
        // Un seul balayage des samples : les kilometrages demandes croissent, l'index ne recule jamais.
        var i = 0
        for (k in 0..steps) {
            val alongM = from + k * step
            while (i < samples.size - 2 && samples[i + 1].x < alongM) i++
            val a = samples[i]
            val b = samples[i + 1]
            val span = b.x - a.x
            val t = if (span <= 0.0) 0.0 else ((alongM - a.x) / span).coerceIn(0.0, 1.0)
            out += (a.lon + (b.lon - a.lon) * t) to (a.lat + (b.lat - a.lat) * t)
        }
        return out
    }
}