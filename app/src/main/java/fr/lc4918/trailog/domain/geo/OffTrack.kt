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

    /**
     * L'alerte s'annonce : la cloche est armee, l'ecart dure, et personne n'a encore repondu.
     *
     * Ce que l'annonce vaut se dit partout ou l'alerte se montre - la banniere de la carte, la notification
     * de l'ecran de verrouillage - et vaut sans le son : couper le son ne supprime pas l'alerte, il la rend
     * muette.
     */
    fun announcing(armed: Boolean, alerting: Boolean, silenced: Boolean): Boolean =
        armed && alerting && !silenced

    /**
     * La sonnerie doit sonner : l'alerte s'annonce ([announcing]) ET le son est demande dans les reglages.
     *
     * **Quatre conditions et non une**, parce que la sonnerie ne s'arrete plus d'elle-meme depuis qu'elle
     * boucle : elle sonnait une fois a l'entree en alerte, et un seul evenement suffisait a la declencher.
     * Ce qui la coupe est desormais aussi important que ce qui la lance - un tap sur la banniere ou sur la
     * notification ([silenced]), un retour sur la trace ([alerting] retombe), la cloche desarmee, le
     * reglage eteint en pleine alerte.
     *
     * Ici plutot que dans le service : c'est une regle, pas un branchement audio, et elle se verifie.
     */
    fun ringing(soundOn: Boolean, armed: Boolean, alerting: Boolean, silenced: Boolean): Boolean =
        soundOn && announcing(armed, alerting, silenced)
}
