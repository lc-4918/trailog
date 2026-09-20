package fr.lc4918.trailog.domain.geo

/**
 * Reconnaitre qu'on est a l'arret depuis un moment, pour espacer les demandes de position.
 *
 * En marche, le service demande une position toutes les deux secondes, sans distance minimale : c'est ce
 * qui donne une vitesse a jour, meme a pas lent. Mais a la pause de midi, une heure de positions toutes
 * les deux secondes ne dit rien de plus que la premiere, et coute la batterie. On compare donc chaque
 * position a une ANCRE : tant qu'on reste a quelques metres d'elle, le temps passe a l'arret s'accumule ;
 * au-dela de [REST_AFTER_MS], on est au repos. S'en eloigner franchement repart en marche.
 *
 * Pas sur la vitesse du capteur : a pied tres lent, elle peut tomber sous n'importe quel seuil sans qu'on
 * soit arrete. La distance a l'ancre, elle, finit toujours par grandir quand on avance.
 */
object RestDetector {

    /** Arret au-dela duquel on espace les demandes : une minute sur place. */
    const val REST_AFTER_MS = 60_000L

    /** Rayon de l'ancre : sous lui, on n'a pas bouge - au tremblement des positions pres. */
    const val RADIUS_M = 10.0

    data class State(
        val lat: Double? = null,
        val lon: Double? = null,
        val sinceMs: Long = 0L,
    )

    /**
     * Une position de plus : rend l'etat suivant, et vrai si l'on est au repos. [timeMs] est un temps
     * monotone. Une position imprecise elargit le rayon d'autant : son ecart a l'ancre peut n'etre que
     * de l'incertitude.
     */
    fun step(s: State, lat: Double, lon: Double, accuracyM: Float, timeMs: Long): Pair<State, Boolean> {
        val aLat = s.lat
        val aLon = s.lon
        if (aLat == null || aLon == null) return State(lat, lon, timeMs) to false
        val d = TrackMath.haversine(aLon, aLat, lon, lat)
        if (d > maxOf(RADIUS_M, accuracyM.toDouble())) return State(lat, lon, timeMs) to false
        return s to (timeMs - s.sinceMs >= REST_AFTER_MS)
    }
}
