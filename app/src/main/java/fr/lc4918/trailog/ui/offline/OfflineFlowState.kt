package fr.lc4918.trailog.ui.offline

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.map.offline.Bbox

/**
 * Ou en est la demande de carte hors-ligne, du bouton jusqu'a l'ecran de configuration.
 *
 * Le parcours a deux entrees et un seul aboutissement. On dit d'abord CE QU'ON TELECHARGE ([extentChoice])
 * : un rectangle cadre sur la carte, ou le couloir qui borde une trace. Le rectangle passe par
 * [drawingActive], ou l'on regle un cadre a ses poignees ; la trace passe par [pickTrack] et remplit
 * [corridor]. Les deux finissent sur [configBbox], qui ouvre l'ecran de configuration.
 *
 * Six drapeaux qui ne se comprennent qu'ensemble, et deux facons de tout refermer qui ne se distinguent
 * que par un detail - c'est ce qui les met ici plutot qu'en variables eparses dans l'ecran de carte.
 */
@Stable
class OfflineFlowState {
    /** Choix de ce qu'on telecharge, propose a l'appui du bouton. */
    var extentChoice by mutableStateOf(false)

    /** Choix de la trace a border, quand on a repondu "une trace". */
    var pickTrack by mutableStateOf(false)

    /** Trace a border et son parcours, gardes le temps de l'ecran de configuration. */
    var corridor by mutableStateOf<Pair<LayerEntity, List<Pair<Double, Double>>>?>(null)

    /** Reglage de l'emprise rectangulaire : un cadre pose sur la carte, qu'on redimensionne a ses poignees
     *  et sous lequel on deplace la carte (cf. BboxEditorOverlay). */
    var drawingActive by mutableStateOf(false)

    /** L'emprise retenue : sa presence ouvre l'ecran de configuration. */
    var configBbox by mutableStateOf<Bbox?>(null)


    /** Referme completement le flux (annulation ou fin de configuration). */
    fun closeFlow() {
        configBbox = null
        corridor = null
    }

    /** Quitte le reglage de l'emprise (bouton "Annuler" ou retour systeme). */
    fun cancelDrawing() {
        drawingActive = false
    }
}
