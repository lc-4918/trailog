package fr.lc4918.trailog.ui.poi

import fr.lc4918.trailog.map.offline.Bbox

/**
 * Les règles de chargement des points d'intérêt : quand demander, et quoi demander.
 *
 * Hors de l'écran et sans Android, parce que ce sont elles qui décident du nombre de requêtes envoyées au
 * service - la seule chose qui puisse faire dépasser son quota - et qu'une règle qui ne se teste pas
 * finit par ne plus se vérifier.
 */
object PoiLoading {

    /**
     * Zoom en deçà duquel on ne charge rien.
     *
     * À l'échelle d'une région, l'écran porterait des milliers de lieux dont l'API n'en rendrait que les
     * cent premiers - un échantillon arbitraire, pris là où le service a commencé à lire. Mieux vaut ne
     * rien montrer que montrer au hasard, et la carte le dit (cf. le message de l'écran).
     *
     * 11 correspond à une ville et ses abords, l'échelle à laquelle on prépare une étape.
     */
    const val MIN_ZOOM = 11.0

    /**
     * Délai après le dernier geste, en millisecondes.
     *
     * Un déplacement de carte émet des dizaines d'événements ; sans attente, chacun partirait en requête.
     * 500 ms est le temps qu'il faut pour distinguer "j'ai fini de déplacer" de "je continue", sans que
     * l'attente se remarque une fois le doigt levé.
     */
    const val DEBOUNCE_MS = 500L

    /**
     * Delai avant de redemander une zone dont le chargement a **echoue**.
     *
     * Une minute, et ce n'est pas une precaution : c'est ce qui separe un service qui hoquette d'un service
     * qui nous bannit. Une emprise en echec n'est pas retenue comme chargee - sans quoi le manque se
     * figerait -, mais sans ce frein chaque geste de carte la redemandait aussitot. Releve a Albi : huit
     * gestes, vingt-cinq requetes Overpass, vingt-cinq refus de connexion. L'instance publique n'accorde
     * que deux creneaux par adresse, et l'application se faisait refuser d'autant plus fort qu'elle
     * insistait.
     *
     * Ne s'applique qu'a l'echec. Une reponse simplement **tronquee** se redemande, elle, a chaque geste :
     * le service a repondu, et resserrer la vue est precisement ce qui rendra la reponse complete.
     */
    const val RETRY_AFTER_FAIL_MS = 60_000L

    /**
     * L'emprise à demander pour un écran donné : la même, élargie de [MARGIN] de part et d'autre.
     *
     * Charger plus large que l'écran est ce qui rend gratuits les petits déplacements : tant que la vue
     * reste dans ce qu'on a chargé, il n'y a rien à redemander (cf. `PoiState.needsLoad`). Le prix est une
     * poignée de lieux hors champ dans la réponse, sans commune mesure avec une requête de plus.
     */
    const val MARGIN = 0.25

    fun grow(box: Bbox, margin: Double = MARGIN): Bbox {
        val dLon = (box.east - box.west) * margin
        val dLat = (box.north - box.south) * margin
        return Bbox.of(
            (box.west - dLon).coerceAtLeast(-180.0), (box.south - dLat).coerceAtLeast(-85.0),
            (box.east + dLon).coerceAtMost(180.0), (box.north + dLat).coerceAtMost(85.0),
        )
    }
}
