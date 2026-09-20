package fr.lc4918.trailog.domain.geo

import kotlinx.serialization.Serializable

/**
 * Les compteurs de la sortie : distance parcourue, denivele positif et negatif, temps en mouvement.
 *
 * Ils ne repartent pas a zero tout seuls - on les remet a zero soi-meme, depuis le tableau de bord - et
 * continuent d'une session de localisation a l'autre : une sortie ne s'arrete pas parce qu'on a coupe le
 * GPS le temps d'un cafe.
 *
 * Serialisable : ils survivent a la mort du processus, comme la trace suivie (cf. `TripStore`).
 *
 * [lastLat]/[lastLon] est le dernier point COMPTE - l'ancre -, et non la derniere position recue : a
 * l'arret, les positions tremblent de quelques metres autour de soi, et les additionner ferait marcher le
 * compteur sur place. [anchorTimeMs] est l'instant ou l'ancre a ete posee : depuis, on n'a pas bouge. [refAltM] est l'altitude a partir de laquelle on attend un ecart franc (cf.
 * [TripStats.ALT_STEP_M]).
 */
@Serializable
data class Trip(
    val distanceM: Double = 0.0,
    val ascentM: Double = 0.0,
    val descentM: Double = 0.0,
    val movingMs: Long = 0L,
    val lastLat: Double? = null,
    val lastLon: Double? = null,
    val lastTimeMs: Long? = null,
    val refAltM: Double? = null,
    val anchorTimeMs: Long? = null,
)

/**
 * Le calcul des compteurs, position apres position. Sans Android : une faute y serait silencieuse - un
 * denivele gonfle ne leve rien, il fait seulement croire a une journee plus dure qu'elle ne l'etait.
 */
object TripStats {

    /** Une position moins precise que cela ne compte pas : trente metres d'incertitude, c'est un ecart
     *  qu'on additionnerait comme une distance parcourue. */
    const val MAX_ACCURACY_M = 30f

    /** Le pas lent : 1,5 km/h. Il borne le temps compte en mouvement pour un deplacement (cf. [add]). */
    const val MIN_MOVING_MPS = 1.5 / 3.6

    /**
     * Deplacement minimal avant de compter : cinq metres, ou la precision de la position si elle est
     * moins bonne. En deca, on ne distingue pas un pas du tremblement du GPS - telephone pose sur une
     * table, les positions bougent de quelques metres sans que rien ne bouge.
     */
    const val MIN_STEP_M = 5.0

    /**
     * Un changement d'altitude ne compte qu'au-dela de cinq metres : l'altitude d'un GPS de telephone
     * tremble de quelques metres d'une mesure a l'autre, et sommer ce bruit ferait des centaines de metres
     * de denivele sur une route plate.
     */
    const val ALT_STEP_M = 5.0

    /**
     * Au-dela d'une minute sans position, on ne relie pas les deux : le GPS a ete coupe, ou le signal perdu,
     * et la ligne droite entre les deux points - peut-etre a des kilometres - n'est pas un chemin parcouru.
     * Le point suivant devient la nouvelle ancre.
     */
    const val MAX_GAP_MS = 60_000L

    /**
     * Une position de plus. [timeMs] est un temps monotone (depuis le demarrage de l'appareil).
     *
     * **Seule la distance a l'ancre decide**, et non la vitesse du capteur : a l'interieur, il annonce
     * parfois quelques km/h sans que le telephone ait bouge. On compte un deplacement quand on s'est
     * eloigne de l'ancre de plus de [MIN_STEP_M] (ou de la precision), et l'ancre vient alors ici. A pas
     * lent, plusieurs positions passent sous le seuil, puis la distance entiere se compte d'un coup : rien
     * n'est perdu.
     *
     * Le denivele ne se lit qu'avec un deplacement compte : on ne monte pas sans avancer, et a l'arret
     * l'altitude du GPS tremble bien plus que la position.
     *
     * Le temps en mouvement est celui ecoule depuis l'ancre, borne par ce qu'il faut au pas lent pour
     * franchir ce deplacement : cinq minutes debout puis cinq metres ne font pas cinq minutes de marche.
     */
    fun add(t: Trip, lat: Double, lon: Double, accuracyM: Float, altitudeM: Double?, timeMs: Long): Trip {
        if (accuracyM > MAX_ACCURACY_M) return t
        val lastLat = t.lastLat
        val lastLon = t.lastLon
        val lastTime = t.lastTimeMs
        // Premiere position, ou reprise apres un trou : elle devient l'ancre, sans rien compter.
        if (lastLat == null || lastLon == null || lastTime == null || timeMs - lastTime > MAX_GAP_MS || timeMs < lastTime) {
            return t.copy(lastLat = lat, lastLon = lon, lastTimeMs = timeMs, anchorTimeMs = timeMs,
                refAltM = altitudeM ?: t.refAltM)
        }
        val d = TrackMath.haversine(lastLon, lastLat, lon, lat)
        var next = t.copy(lastTimeMs = timeMs)
        // Sur place, rien de plus : ni distance, ni temps, ni denivele. L'altitude d'un telephone pose sur
        // une table varie de plus de cinq metres d'une minute a l'autre, et le seuil seul ne suffisait pas.
        if (d <= maxOf(MIN_STEP_M, accuracyM.toDouble())) return next
        val depuis = timeMs - (t.anchorTimeMs ?: lastTime)
        val auPasLent = (d / MIN_MOVING_MPS * 1000.0).toLong()
        next = next.copy(
            distanceM = next.distanceM + d, movingMs = next.movingMs + minOf(depuis, auPasLent),
            lastLat = lat, lastLon = lon, anchorTimeMs = timeMs,
        )
        if (altitudeM != null) {
            val ref = next.refAltM
            next = when {
                ref == null -> next.copy(refAltM = altitudeM)
                altitudeM - ref >= ALT_STEP_M -> next.copy(ascentM = next.ascentM + (altitudeM - ref), refAltM = altitudeM)
                ref - altitudeM >= ALT_STEP_M -> next.copy(descentM = next.descentM + (ref - altitudeM), refAltM = altitudeM)
                else -> next
            }
        }
        return next
    }
}
