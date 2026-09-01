package fr.lc4918.trailog.ui.alert

import fr.lc4918.trailog.domain.model.Sample

/**
 * Ou l'on en est de la trace qu'on suit : ce qui est fait, ce qui reste, et en combien de temps.
 *
 * **Pourquoi ces neuf chiffres et pas d'autres.** Le suivi de trace ne disait qu'une chose - l'ecart a la
 * trace - qui repond a "suis-je sur le bon chemin" et a rien d'autre. Or la question qu'on se pose en
 * roulant est ailleurs : combien reste-t-il, et surtout combien de MONTEE reste-t-il. La distance seule ne
 * dit pas si les trois derniers kilometres sont une descente ou le mur du col.
 *
 * Le fait et le restant se lisent en vis-a-vis : c'est leur rapport qui situe, non leur valeur absolue -
 * savoir qu'on a fait 400 m de D+ ne vaut que si l'on sait qu'il en reste 900.
 */
data class FollowProgress(
    /** Vitesse instantanee (m/s), ou null quand la mesure ne la porte pas. */
    val speedMps: Float?,
    val doneM: Double,
    val remainingM: Double,
    val doneAscentM: Double,
    val doneDescentM: Double,
    val remainingAscentM: Double,
    val remainingDescentM: Double,
    /** Temps ecoule depuis le debut du suivi (ms). */
    val elapsedMs: Long,
    /**
     * Temps restant estime (ms), ou null quand on n'a pas de quoi l'estimer - suivi trop jeune, ou pas
     * encore avance.
     */
    val etaMs: Long?,
)

/**
 * Le calcul de l'avancement, isole de l'ecran qui l'affiche.
 *
 * Sans Android et sans Compose : ce sont neuf valeurs qu'on lit d'un coup d'oeil en roulant, et une faute
 * y serait silencieuse - un denivele restant faux ne leve rien, il fait seulement renoncer a un col qu'on
 * aurait passe, ou l'inverse.
 */
object FollowProgressMath {

    /**
     * Duree minimale de suivi avant d'oser une estimation, en millisecondes.
     *
     * Une minute : en deca, la vitesse moyenne est dominee par le demarrage - on est encore a l'arret au
     * feu, ou l'on vient de partir - et l'estimation annoncerait des heures ou des minutes au hasard.
     */
    const val MIN_ELAPSED_FOR_ETA_MS = 60_000L

    /**
     * L'avancement sur [samples], la position etant a [alongM] du depart de la trace.
     *
     * [startedAtMs] et [nowMs] sont des temps depuis le demarrage de l'appareil, et non l'heure murale :
     * une remise a l'heure du reseau ne doit ni allonger ni raccourcir la sortie.
     *
     * L'estimation part de la vitesse MOYENNE depuis le debut du suivi, et non de la vitesse instantanee :
     * celle-ci tombe a zero a chaque arret et annoncerait alors l'infini, quand la moyenne porte deja les
     * arrets, les montees et les descentes qu'on a faits. C'est une extrapolation, pas une prevision : elle
     * suppose que la suite ressemble a ce qui precede, ce que le denivele restant vient nuancer a cote.
     */
    fun of(
        samples: List<Sample>,
        alongM: Double,
        speedMps: Float?,
        startedAtMs: Long,
        nowMs: Long,
    ): FollowProgress {
        val elapsed = (nowMs - startedAtMs).coerceAtLeast(0L)
        if (samples.size < 2) {
            return FollowProgress(speedMps, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, elapsed, null)
        }
        val debut = samples.first().x
        val fin = samples.last().x
        val ici = alongM.coerceIn(debut, fin)
        val fait = ici - debut
        val reste = fin - ici
        val (dPlusFait, dMoinsFait) = denivele(samples, debut, ici)
        val (dPlusReste, dMoinsReste) = denivele(samples, ici, fin)
        return FollowProgress(
            speedMps = speedMps,
            doneM = fait,
            remainingM = reste,
            doneAscentM = dPlusFait,
            doneDescentM = dMoinsFait,
            remainingAscentM = dPlusReste,
            remainingDescentM = dMoinsReste,
            elapsedMs = elapsed,
            etaMs = eta(fait, reste, elapsed),
        )
    }

    /**
     * Denivele positif et negatif entre deux abscisses de la trace.
     *
     * Les deux bouts sont INTERPOLES entre leurs voisins : la position tombe presque jamais sur un
     * echantillon, et compter le segment en cours en entier ferait sauter le denivele d'un coup a chaque
     * fois qu'on franchit un sommet de la ligne brisee.
     */
    internal fun denivele(samples: List<Sample>, fromM: Double, toM: Double): Pair<Double, Double> {
        if (toM <= fromM) return 0.0 to 0.0
        var plus = 0.0
        var moins = 0.0
        var z = altitudeAt(samples, fromM)
        for (s in samples) {
            if (s.x <= fromM) continue
            if (s.x >= toM) break
            val dz = s.z - z
            if (dz > 0) plus += dz else moins -= dz
            z = s.z
        }
        // Le dernier morceau, jusqu'a l'abscisse demandee : sans lui, la portion s'arreterait au dernier
        // sommet franchi et perdrait la montee en cours.
        val dz = altitudeAt(samples, toM) - z
        if (dz > 0) plus += dz else moins -= dz
        return plus to moins
    }

    /** L'altitude a une abscisse quelconque, interpolee entre les deux echantillons qui l'encadrent. */
    internal fun altitudeAt(samples: List<Sample>, alongM: Double): Double {
        if (samples.isEmpty()) return 0.0
        if (alongM <= samples.first().x) return samples.first().z
        if (alongM >= samples.last().x) return samples.last().z
        val i = samples.indexOfFirst { it.x >= alongM }.coerceAtLeast(1)
        val a = samples[i - 1]
        val b = samples[i]
        val span = b.x - a.x
        return if (span > 0) a.z + (b.z - a.z) * ((alongM - a.x) / span) else a.z
    }

    /** Le temps restant, a la vitesse moyenne tenue jusqu'ici. Null tant qu'on n'a pas de quoi la calculer. */
    internal fun eta(doneM: Double, remainingM: Double, elapsedMs: Long): Long? {
        if (remainingM <= 0.0) return 0L
        if (elapsedMs < MIN_ELAPSED_FOR_ETA_MS || doneM <= 0.0) return null
        val vitesse = doneM / (elapsedMs / 1000.0)   // m/s
        if (vitesse <= 0.0) return null
        return (remainingM / vitesse * 1000.0).toLong()
    }
}
