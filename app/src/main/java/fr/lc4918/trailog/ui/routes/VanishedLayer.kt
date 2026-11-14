package fr.lc4918.trailog.ui.routes

/**
 * La couche que montre un panneau a-t-elle quitte la bibliotheque ?
 *
 * Supprimer une trace depuis la bibliotheque laissait son profil a l'ecran : un titre au-dessus d'un
 * graphique vide, d'une trace qui n'existe plus. La suppression passe par plusieurs chemins (la trace, son
 * dossier, une fusion qui reecrit les couches), on observe donc la liste des couches plutot que chacun
 * d'eux.
 *
 * Seule une couche vue dans la liste precedente compte comme disparue : la liste est vide le temps que la
 * base la livre, et un panneau ouvert sur une couche pas encore listee ne doit pas se refermer pour autant.
 */
object VanishedLayer {
    fun vanished(shownLayerId: Long?, previousIds: Set<Long>, currentIds: Set<Long>): Boolean =
        shownLayerId != null && shownLayerId in previousIds && shownLayerId !in currentIds
}
