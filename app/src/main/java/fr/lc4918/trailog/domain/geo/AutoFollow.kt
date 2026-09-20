package fr.lc4918.trailog.domain.geo

import fr.lc4918.trailog.domain.model.Sample

/**
 * Le suivi AUTOMATIQUE d'une trace : reconnaitre qu'on la parcourt, et qu'on l'a quittee.
 *
 * Il remplace le choix a la main, dans une liste de traces proches : l'application sait ou l'on est, et
 * sait quelles traces passent la. Etre SUR une trace suffit a la suivre, sans avoir a marcher pour le
 * prouver (cf. [ENTER_FIXES]) : ce qu'il faut ecarter n'est pas l'arret, c'est la position aberrante.
 *
 * Sans Android : ce sont des regles de seuil, et une faute y serait silencieuse - un suivi qui s'accroche
 * a la mauvaise trace annoncerait un restant faux sans que rien ne le dise.
 */
object AutoFollow {

    /** A moins de cela d'une trace, on est dessus - au bruit d'un GPS de telephone pres. */
    const val ON_TRACK_M = 30.0

    /**
     * Positions consecutives sur la meme trace avant de la tenir pour suivie.
     *
     * C'est la SEULE condition, et elle se remplit sans bouger : trois positions a moins de [ON_TRACK_M],
     * six secondes a la cadence de marche, et la trace est accrochee. Etre sur une trace suffit a la
     * suivre - c'est ce qu'on attend en arrivant au depart, telephone en main, avant d'avoir fait un pas.
     *
     * Il fallait aussi, avant, avoir avance de cent metres LE LONG de la trace. La regle ecartait bien les
     * croisements - traverser une trace ne progresse pas dessus -, mais elle ecartait du meme coup l'arret :
     * pose au depart, ou en pause, on n'accrochait rien, et il fallait marcher cent metres pour que le
     * tableau de bord se decide. Trois positions restent ce qui separe une trace d'une mesure aberrante.
     *
     * Ce qu'on paie : un croisement accroche brievement la trace traversee. Cela ne sonne pas - une trace
     * reconnue part cloche ETEINTE (cf. TrackWatch.follow) - et le suivi la lache de lui-meme des qu'on
     * s'en eloigne (cf. [leave]).
     */
    const val ENTER_FIXES = 3

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

    /**
     * Ce qu'on accumule en attendant de reconnaitre une trace.
     *
     * [startAlongM] ne commande plus l'accrochage ; il sert encore a le faire dans le bon SENS : compare
     * au kilometrage de la position qui accroche, il dit de quel cote on va (cf. [detect]).
     */
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
     * La plus proche des traces a moins de [ON_TRACK_M] l'emporte ; changer de trace plus proche remet le
     * compte a zero, et c'est voulu - les positions comptees l'etaient sur une autre.
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
        if (next.fixes >= ENTER_FIXES) {
            // Le sens, s'il se lit : sur place, le kilometrage tremble sans rien dire, et la trace part
            // alors dans son propre sens (+1) - la premiere avancee franche le corrigera (cf. [direction]).
            val sens = direction(1, next.startAlongM, bestProj.alongM)
            return Detection() to Match(best, bestProj.alongM, bestProj.awayM, sens)
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
