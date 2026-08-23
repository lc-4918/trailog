package fr.lc4918.trailog.ui.routes

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Ce que les bandes de l'ecran RECOUVRENT, mesure a l'affichage.
 *
 * **Pourquoi mesurer plutot que calculer.** Aucune de ces hauteurs n'est connue d'avance : la colonne de
 * boutons du haut grandit avec le nombre de fonctions activees, la bande du planificateur avec le nombre
 * d'etapes et la presence de son profil, la barre de consigne avec la longueur de sa phrase dans la langue
 * du telephone. Elles se relevent donc a la pose, par `onGloballyPositioned`, et se lisent ici.
 *
 * **Pourquoi un porteur.** Ces cinq nombres sont ecrits a un endroit et lus a trois autres, tres eloignes
 * dans l'ecran : l'echelle graphique se decale au-dessus de la barre de consigne du moment, le cadrage
 * d'un parcours degage la colonne du haut ET la bande du bas, la barre de retouche ne descend jamais sous
 * les boutons. Chacun de ces accords se defait en silence - rien ne plante, une chose en recouvre une
 * autre - et c'est exactement le genre de reglage qui doit tenir en un seul endroit.
 */
@Stable
class MapInsetsState {

    /**
     * La colonne de boutons du coin haut-gauche, marge de barre de statut comprise.
     *
     * C'est elle et non la barre de statut qui dit ce qu'il faut degager en haut : un parcours cadre au
     * plus juste passait sous les boutons, qui recouvrent la carte bien plus bas qu'elle.
     */
    var topControlsPx by mutableIntStateOf(0)

    /** Le panneau de profil, superpose a la carte, qui garde toujours sa taille pleine. */
    var profilePanelPx by mutableIntStateOf(0)

    /** La bande du planificateur, dont le cadrage du parcours doit degager ce qu'elle recouvre. */
    var plannerBandPx by mutableIntStateOf(0)

    // ---------- Les trois barres de consigne ----------
    // Elles ne sont jamais affichees ensemble : chacune est un mode de saisie exclusif, et celui qui
    // occupe l'ecran a ferme les autres. D'ou [promptBarPx], qui n'a pas a les departager.

    /** Le trace de l'emprise hors-ligne. */
    var offlineBarPx by mutableIntStateOf(0)

    /** Le choix des deux points d'une mesure sur trace. */
    var measureBarPx by mutableIntStateOf(0)

    /** Le choix du point de reference d'une distance. */
    var pointBarPx by mutableIntStateOf(0)

    /**
     * La barre de consigne affichee, ou 0 s'il n'y en a aucune.
     *
     * Se lit sur la hauteur relevee et non sur le mode en cours : une barre qui se retire rend sa hauteur
     * a zero de toute facon, et lire l'etat des trois modes ici obligerait ce porteur a connaitre trois
     * autres porteurs pour repondre a une question de geometrie.
     */
    val promptBarPx: Int get() = maxOf(offlineBarPx, measureBarPx, pointBarPx)

    /** Ce qu'il faut degager en haut d'un cadrage : les boutons, ou la barre de statut si elle depasse. */
    fun topCoverPx(statusBarPx: Int): Int = maxOf(statusBarPx, topControlsPx)
}
