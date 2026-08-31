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
     * **Descendu de 11 à 9**, soit seize fois la surface. 11 correspondait à une ville et ses abords, et
     * c'était trop près pour le geste qui compte : on prépare une étape en regardant le trajet entier, pas
     * un quartier. Le testeur l'a dit ainsi - "ça permettrait de zoomer moins, là ce n'est pas très
     * pratique".
     *
     * Ce qui rend la descente tenable est le **couloir des traces** (cf. [PoiCorridor]) : à 9, la vue porte
     * une région, mais on n'en affiche que ce qui borde le trajet affiché. Sans trace ouverte, la réponse
     * sera souvent tronquée à cette échelle - et la carte le dit déjà (cf. le bandeau "affichage
     * incomplet"), ce qui vaut mieux que de refuser de montrer quoi que ce soit.
     */
    const val MIN_ZOOM = 9.0

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
     * Ce qu'il faut resserrer la vue pour qu'une emprise TRONQUEE se redemande : la moitié de sa surface.
     *
     * Resserrer est le seul geste qui ait une chance de rendre la réponse complète - moins de lieux tiennent
     * dans une vue plus étroite. Un simple déplacement dans la zone déjà chargée n'en a aucune, et c'est
     * pour cela qu'il ne redemande plus rien.
     *
     * La moitié, et non un dixième : au pixel près, tout geste de zoom relancerait, et l'on retomberait sur
     * ce qu'on corrige.
     */
    const val ZOOM_RETRY_RATIO = 0.5

    /**
     * Au bout de combien de temps une emprise tronquée vaut la peine d'être redemandée, sans qu'on ait
     * resserré : cinq minutes.
     *
     * Assez long pour qu'une carte qu'on consulte ne relance rien, assez court pour qu'une pause déjeuner
     * reparte sur des données fraîches - et pour que le découpage retombe peut-être mieux.
     */
    const val PARTIAL_TTL_MS = 5 * 60_000L

    /**
     * L'emprise à demander pour un écran donné : la même, élargie de [MARGIN] de part et d'autre.
     *
     * Charger un peu plus large que l'écran rend gratuits les petits déplacements : tant que la vue reste
     * dans ce qu'on a chargé, il n'y a rien à redemander (cf. `PoiState.needsLoad`).
     *
     * **Descendue de 0,25 à 0,05**, et c'est une correction. À 0,25, l'emprise demandée faisait une fois et
     * demie l'écran dans chaque dimension, soit **2,25 fois sa surface** - donc deux fois plus de lieux à
     * faire tenir sous le plafond d'une requête, et deux fois plus de travail demandé au service, pour une
     * marge dont on ne profitait qu'en se déplaçant de peu. À 0,05 la marge reste utile aux petits
     * déplacements et ne coûte plus que 10 % de surface.
     */
    const val MARGIN = 0.05

    fun grow(box: Bbox, margin: Double = MARGIN): Bbox {
        val dLon = (box.east - box.west) * margin
        val dLat = (box.north - box.south) * margin
        return Bbox.of(
            (box.west - dLon).coerceAtLeast(-180.0), (box.south - dLat).coerceAtLeast(-85.0),
            (box.east + dLon).coerceAtMost(180.0), (box.north + dLat).coerceAtMost(85.0),
        )
    }
}
