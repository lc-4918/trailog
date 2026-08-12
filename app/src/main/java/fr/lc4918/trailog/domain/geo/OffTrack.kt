package fr.lc4918.trailog.domain.geo

/**
 * Ce qu'il faut pour dire qu'on s'est ecarte de la trace qu'on suit.
 *
 * Deux calculs, et rien d'autre : approcher la distance d'une couche sans la lire, et decider si l'ecart
 * mesure vaut une alerte. Le rabattement exact sur la ligne, lui, est deja celui de la mesure sur trace
 * (cf. [TrackMeasure.project]) - s'ecarter d'une trace et mesurer dessus posent la meme question.
 */
object OffTrack {

    /**
     * Distance au rectangle englobant d'une couche (m) : le point rabattu sur le rectangle, puis la
     * haversine jusqu'a lui. Zero des que la position tombe dedans.
     *
     * Sert a CHOISIR quelles couches lire avant de les projeter : une couche dont l'emprise est a
     * cinquante kilometres n'a aucune trace a proposer, et la lire couterait un fichier de profils pour
     * rien. C'est une minoration - la trace reelle est forcement plus loin que son rectangle -, donc un
     * pre-tri sur, jamais un resultat affiche.
     *
     * Les bornes sont prises dans l'ordre, sans supposer que west < east : une couche vide porte quatre
     * zeros, et un rectangle a l'envers ferait echouer le rabattement.
     */
    fun bboxDistanceM(
        lat: Double, lon: Double, west: Double, south: Double, east: Double, north: Double,
    ): Double {
        val nearLat = lat.coerceIn(minOf(south, north), maxOf(south, north))
        val nearLon = lon.coerceIn(minOf(west, east), maxOf(west, east))
        return TrackMath.haversine(lon, lat, nearLon, nearLat)
    }

    /**
     * Fraction du seuil sous laquelle l'alerte s'eteint : on la declenche a l'ecart regle, on ne la lache
     * qu'a 80 % de celui-ci.
     *
     * Sans cette marge, une position qui oscille autour du seuil - c'est le lot d'un GPS de telephone sous
     * couvert - rallumerait la banniere et son son toutes les deux secondes.
     */
    const val ReturnRatio = 0.8

    /**
     * L'alerte, une position de plus : [current] est son etat precedent, [awayM] l'ecart mesure a la trace
     * suivie, [thresholdM] l'ecart regle.
     *
     * Entre le seuil et sa marge de retour, rien ne change - c'est la zone morte qui empeche le clignotement.
     */
    fun alerting(current: Boolean, awayM: Double, thresholdM: Double): Boolean = when {
        awayM >= thresholdM -> true
        awayM <= thresholdM * ReturnRatio -> false
        else -> current
    }
}
