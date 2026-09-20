package fr.lc4918.trailog.domain.geo

import fr.lc4918.trailog.domain.model.Sample
import kotlin.math.abs

/**
 * Le suivi AUTOMATIQUE d'une trace : reconnaitre qu'on la parcourt, et qu'on l'a quittee.
 *
 * Il remplace le choix a la main, dans une liste de traces proches : l'application sait ou l'on est, et
 * sait quelles traces passent la. Encore faut-il ne pas se tromper - traverser une trace n'est pas la
 * suivre, et une route empruntee cent metres par un GR ne fait pas de lui la trace du jour.
 *
 * Sans Android : ce sont des regles de seuil, et une faute y serait silencieuse - un suivi qui s'accroche
 * a la mauvaise trace annoncerait un restant faux sans que rien ne le dise.
 */
object AutoFollow {

    /** A moins de cela d'une trace, on est dessus - au bruit d'un GPS de telephone pres. */
    const val ON_TRACK_M = 30.0

    /** Positions consecutives sur la meme trace avant de la tenir pour suivie : un croisement n'en donne
     *  qu'une ou deux. */
    const val ENTER_FIXES = 3

    /** Chemin parcouru LE LONG de la trace avant de la tenir pour suivie : la traverser n'avance pas sur elle. */
    const val ENTER_ALONG_M = 100.0

    /** Positions consecutives au-dela du seuil avant de lacher la trace, cloche eteinte : une seule mesure
     *  aberrante ne doit pas faire perdre le suivi. */
    const val LEAVE_FIXES = 2

    /** Un deplacement le long de la trace plus petit que cela ne dit rien du sens : c'est du bruit. */
    const val DIRECTION_MIN_M = 5.0

    /** Une trace candidate : ce qu'il faut pour la reconnaitre, la nommer, et la suivre. */
    data class Candidate(
        val id: Long,
        val name: String,
        val trackIndex: Int,
        val trackCount: Int,
        val samples: List<Sample>,
        val west: Double, val south: Double, val east: Double, val north: Double,
    ) {
        /** Identite d'une trace precise : une couche peut en porter plusieurs. */
        val key: String get() = "$id/$trackIndex"
    }

    /** Ce qu'on accumule en attendant de reconnaitre une trace. */
    data class Detection(
        val key: String? = null,
        val fixes: Int = 0,
        val startAlongM: Double = 0.0,
    )

    /** La trace reconnue : laquelle, ou l'on est dessus, et dans quel sens (+1 : du debut vers la fin). */
    data class Match(val candidate: Candidate, val alongM: Double, val awayM: Double, val direction: Int)

    /**
     * Une position de plus, hors de tout suivi : rend l'etat de detection suivant, et la trace reconnue
     * quand elle l'est.
     *
     * La plus proche des traces a moins de [ON_TRACK_M] l'emporte ; changer de trace plus proche remet la
     * detection a zero, et c'est voulu - on n'a pas encore fait cent metres sur celle-ci.
     */
    fun detect(state: Detection, lat: Double, lon: Double, candidates: List<Candidate>): Pair<Detection, Match?> {
        var best: Candidate? = null
        var bestProj: TrackMeasure.Projection? = null
        for (c in candidates) {
            // L'emprise d'abord : deux comparaisons, contre des milliers de segments.
            if (OffTrack.bboxDistanceM(lat, lon, c.west, c.south, c.east, c.north) > ON_TRACK_M) continue
            val p = TrackMeasure.project(c.samples, lon, lat) ?: continue
            if (p.awayM > ON_TRACK_M) continue
            if (bestProj == null || p.awayM < bestProj.awayM) { best = c; bestProj = p }
        }
        if (best == null || bestProj == null) return Detection() to null
        val next = if (state.key == best.key) state.copy(fixes = state.fixes + 1)
            else Detection(best.key, 1, bestProj.alongM)
        val avance = bestProj.alongM - next.startAlongM
        if (next.fixes >= ENTER_FIXES && abs(avance) >= ENTER_ALONG_M) {
            return Detection() to Match(best, bestProj.alongM, bestProj.awayM, if (avance >= 0) 1 else -1)
        }
        return next to null
    }

    /**
     * Le sens de parcours, tenu a jour : il ne change que sur un deplacement franc le long de la trace, et
     * garde sa valeur sinon - a l'arret, le kilometrage tremble dans les deux sens.
     */
    fun direction(current: Int, previousAlongM: Double?, alongM: Double): Int {
        val prev = previousAlongM ?: return current
        val d = alongM - prev
        return when {
            d >= DIRECTION_MIN_M -> 1
            d <= -DIRECTION_MIN_M -> -1
            else -> current
        }
    }

    /**
     * Cloche eteinte : faut-il lacher la trace. Rend le compte de positions hors seuil, et vrai quand il
     * atteint [LEAVE_FIXES]. Revenir sous le seuil remet le compte a zero.
     */
    fun leave(outFixes: Int, awayM: Double, thresholdM: Double): Pair<Int, Boolean> {
        if (awayM <= thresholdM) return 0 to false
        val n = outFixes + 1
        return n to (n >= LEAVE_FIXES)
    }
}
