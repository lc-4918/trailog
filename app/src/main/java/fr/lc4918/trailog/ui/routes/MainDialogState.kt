package fr.lc4918.trailog.ui.routes

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Les deux fenetres que l'ecran de carte ouvre pour son propre compte.
 *
 * **Pourquoi elles sont ici et pas ailleurs.** Presque toutes les boites de l'ecran appartiennent a un
 * sujet qui a deja son porteur : la confirmation d'inversion est une affaire de retouche
 * (`TrackEditState`), le refus au-dela de vingt-cinq etapes une affaire de planificateur
 * (`RoutePlannerState`), le rapport d'import une affaire d'import (`ImportFlow`). Chacune y est passee.
 *
 * Restent ces deux-ci, qui n'ont de proprietaire nulle part :
 *
 * - [noConnection] est une reponse a une question posee de TROIS endroits qui ne se connaissent pas - le
 *   bouton du geocodeur, les deux mesures de l'infobulle d'un point, le bouton du planificateur. C'est
 *   l'ecran qui les heberge tous les trois, c'est donc a lui de porter leur reponse commune.
 * - [editingFeature] ouvre l'editeur de proprietes d'un marqueur, qui n'est pas une boite de cet ecran
 *   mais un composant a part entiere. L'ecran n'en garde que l'interrupteur, et le rendez-vous que
 *   l'import d'image lui a donne.
 */
@Stable
class MainDialogState {

    /**
     * Le service ne repondra pas : on le dit AVANT de partir.
     *
     * Un itineraire ou un geocodage sont des requetes. Sans reseau, la mesure ne rend qu'un echec que
     * rien n'explique, et un champ de recherche dont aucune frappe ne rendra jamais rien laisse croire a
     * un service muet. La question se pose donc au moment du geste, pas au retour de la requete.
     */
    var noConnection by mutableStateOf(false)

    /**
     * L'editeur de proprietes du marqueur selectionne est ouvert.
     *
     * Il REMPLACE l'infobulle, qui s'efface le temps de l'edition : les deux montrent les memes champs,
     * l'un en lecture et l'autre en saisie, et les superposer donnerait a lire deux fois la meme chose.
     */
    var editingFeature by mutableStateOf(false)

    /**
     * Ce qu'il faut faire de l'image qu'on est alle chercher, ou null.
     *
     * Un champ IMAGE de l'editeur ouvre le selecteur du systeme, qui rend la main bien plus tard et sans
     * dire quel champ l'avait ouvert. Le rendez-vous est donc pris ici au moment du tap, et tenu au
     * retour.
     */
    var pendingImageCallback by mutableStateOf<((String) -> Unit)?>(null)

    /** Un champ IMAGE demande une image : on retient a qui la rendre, puis on ouvre le selecteur. */
    fun awaitImage(onImported: (String) -> Unit) { pendingImageCallback = onImported }
}
