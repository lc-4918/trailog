package fr.lc4918.trailog.routing

/**
 * Ce qu'un calcul d'itineraire rend, et **pourquoi il ne rend rien** quand il ne rend rien.
 *
 * **Deux echecs que tout separe, et qui se disaient pareil.** Un moteur qui REPOND qu'il ne sait pas relier
 * ces etapes-la, et un moteur qu'on n'a pas pu joindre du tout - reseau absent, delai depasse, service
 * eteint -, rendaient l'un comme l'autre un `RouteResult` nul. L'ecran n'avait donc qu'un seul mot pour les
 * deux, "Aucun itineraire", qui envoie chercher la faute du cote du trajet : on change de discipline, on
 * deplace une etape, on recommence - alors que la requete n'est jamais partie et qu'il suffirait de
 * reessayer une fois le reseau revenu.
 *
 * La distinction est celle que [fr.lc4918.trailog.geocode.Photon] fait deja de son cote (null quand le
 * service n'a pas repondu, liste vide quand il a repondu qu'il ne trouvait rien) : deux causes qui
 * appellent deux gestes opposes ne peuvent pas partager un message.
 */
sealed interface RouteOutcome {
    data class Done(val result: RouteResult) : RouteOutcome

    /** Le service a REPONDU : ces etapes ne se relient pas dans cette discipline, ou il refuse le profil. */
    data object NoRoute : RouteOutcome

    /** Rien n'a repondu : reseau absent, liaison coupee, delai depasse. Seul cas qui merite qu'on redemande. */
    data object Unreachable : RouteOutcome

    /** Le parcours quand il y en a un, pour les appelants qu'une panne et un refus n'interessent pas
     *  separement - une mesure de distance affiche la meme chose dans les deux cas. */
    val routeOrNull: RouteResult? get() = (this as? Done)?.result
}
