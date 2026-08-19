package fr.lc4918.trailog.ui.routes

/**
 * Quand la carte suit la position du porteur, et quand elle le laisse tranquille.
 *
 * En sortie, on avance : sans ce suivi, il faut faire glisser la carte tous les cent mètres pour garder
 * devant soi la trace qu'on suit. La carte se recentre donc à chaque nouvelle position - mais elle rend la
 * main dès qu'on la touche, et ne la reprend qu'après [QuietDelayMs] sans geste : regarder ce qu'il y a
 * plus loin sur l'itinéraire est un besoin aussi réel que le suivi, et une carte qui revient sous les
 * doigts est inutilisable.
 *
 * Logique séparée de l'écran parce qu'elle est la seule chose ici qui se vérifie : le reste est un appel de
 * caméra dans un effet.
 */
object MapFollow {

    /**
     * Silence à observer après un geste avant de reprendre le suivi.
     *
     * Cinq secondes : de quoi lire la carte à l'endroit qu'on vient d'atteindre, sans que le retour à la
     * position se fasse attendre au point qu'on aille chercher le bouton de recentrage.
     */
    const val QuietDelayMs = 5_000L

    /**
     * Le suivi a-t-il lieu d'être ?
     *
     * Trois choses le suspendent, et pour la même raison : elles sont posées SUR la carte et s'en servent
     * ailleurs qu'à l'endroit où l'on se tient.
     * - le planificateur cadre le parcours qu'il vient de calculer, et composer un trajet avec une carte
     *   qui revient toutes les cinq secondes sur soi est impossible ;
     * - le profil d'une trace ouverte déplace la carte au curseur qu'on promène dessus, geste qui perdrait
     *   son résultat de la même façon ;
     * - une infobulle ouverte est accrochée à un point précis - un waypoint, un point d'intérêt, un lieu
     *   trouvé, l'endroit d'un appui long : le recentrage emporterait hors de l'écran le point qu'elle
     *   décrit, et l'infobulle avec, au milieu de la lecture.
     *
     * Aucune des trois n'éteint le réglage : elles le mettent en pause, et le suivi reprend en les fermant.
     * Le silence de [QuietDelayMs] repart alors de la fermeture, l'écran relevant l'heure à ce moment-là :
     * refermer une infobulle ne doit pas faire sauter la carte dans la seconde.
     */
    fun follows(
        enabled: Boolean,
        gpsActive: Boolean,
        plannerOpen: Boolean,
        layerOpen: Boolean,
        bubbleOpen: Boolean,
    ): Boolean = enabled && gpsActive && !plannerOpen && !layerOpen && !bubbleOpen

    /**
     * Attente restante avant de pouvoir recentrer, en millisecondes ; 0 quand le dernier geste est assez
     * vieux, ou qu'il n'y en a jamais eu ([lastGestureAt] à 0).
     *
     * Une attente RESTANTE et non un délai fixe : les positions arrivent au rythme du capteur, environ une
     * par seconde. Repartir de zéro à chacune ferait reculer l'échéance indéfiniment, et la carte ne
     * reviendrait jamais.
     */
    fun waitMs(now: Long, lastGestureAt: Long, delayMs: Long = QuietDelayMs): Long {
        if (lastGestureAt <= 0L) return 0L
        val since = now - lastGestureAt
        // Une horloge qui recule (now avant le geste) ne doit pas suspendre le suivi pour l'éternité :
        // hors de la fenêtre, on recentre.
        if (since < 0L) return 0L
        return (delayMs - since).coerceAtLeast(0L)
    }
}
