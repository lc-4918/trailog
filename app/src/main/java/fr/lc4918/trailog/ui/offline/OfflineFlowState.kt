package fr.lc4918.trailog.ui.offline

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.location.TrackWatch
import fr.lc4918.trailog.map.offline.Bbox

/**
 * Ou en est la demande de carte hors-ligne, de son entree jusqu'a l'ecran de configuration.
 *
 * Deux entrees, un seul aboutissement. Une ZONE se demande depuis les reglages (onglet Tuiles) : la carte
 * ouvre son cadrage ([drawingActive]), un cadre qu'on regle a ses poignees. Une TRACE se demande depuis son
 * menu dans le menu lateral, et remplit [corridor]. Les deux finissent sur [configBbox], qui ouvre l'ecran
 * de configuration.
 */
@Stable
class OfflineFlowState {
    /** Trace a border et son parcours, gardes le temps de l'ecran de configuration. */
    var corridor by mutableStateOf<Pair<LayerEntity, List<Pair<Double, Double>>>?>(null)

    /** Reglage de l'emprise rectangulaire : un cadre pose sur la carte, qu'on redimensionne a ses poignees
     *  et sous lequel on deplace la carte (cf. BboxEditorOverlay). */
    var drawingActive by mutableStateOf(false)

    /** L'emprise retenue : sa presence ouvre l'ecran de configuration. */
    var configBbox by mutableStateOf<Bbox?>(null)

    /**
     * Le flux est demande, et ce qu'il doit ouvrir n'est pas encore la.
     *
     * Une trace a border se relit du disque avant que l'ecran de configuration ne puisse s'ouvrir, et
     * une trace de vingt mille points n'arrive pas dans la seconde. La carte restait alors telle quelle,
     * sans rien pour dire qu'on avait ete entendu - et l'on retapait. Le rond d'attente le dit
     * (cf. BusySpinner).
     */
    var preparing by mutableStateOf(false)


    /** Referme completement le flux (annulation ou fin de configuration). */
    fun closeFlow() {
        configBbox = null
        corridor = null
        preparing = false
    }

    /**
     * Ouvre le reglage d'une zone. Le profil altimetrique et le tableau de bord se referment : ils
     * occupaient le bas de l'ecran, la ou le cadre doit se regler. [closeProfile] ferme le profil, qui
     * vit dans le ViewModel de la carte.
     */
    fun startDrawing(closeProfile: () -> Unit) {
        closeFlow()
        closeProfile()
        TrackWatch.setDashboard(false)
        drawingActive = true
    }

    /** Quitte le reglage de l'emprise (bouton "Annuler" ou retour systeme). */
    fun cancelDrawing() {
        drawingActive = false
    }
}
