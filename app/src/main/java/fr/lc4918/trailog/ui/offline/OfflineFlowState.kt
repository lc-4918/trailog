package fr.lc4918.trailog.ui.offline

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.map.offline.Bbox

/**
 * Ou en est la demande de carte hors-ligne, du bouton jusqu'a l'ecran de configuration.
 *
 * Le parcours a deux entrees et un seul aboutissement. On dit d'abord CE QU'ON TELECHARGE ([extentChoice])
 * : un rectangle trace sur la carte, ou le couloir qui borde une trace. Le rectangle passe par [pickTrack]
 * a faux et un trace au doigt ([drawingActive], [bboxPoints]) ; la trace passe par [pickTrack] et remplit
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

    /** Saisie du rectangle au doigt : deux coins a poser sur la carte. */
    var drawingActive by mutableStateOf(false)

    /** Les coins poses, en (lon, lat). */
    var bboxPoints by mutableStateOf<List<Pair<Double, Double>>>(emptyList())

    /** L'emprise retenue : sa presence ouvre l'ecran de configuration. */
    var configBbox by mutableStateOf<Bbox?>(null)

    /** Hauteur mesuree de la barre de trace, pour decaler l'echelle graphique au-dessus (SPEC). */
    var barHeightPx by mutableIntStateOf(0)

    /**
     * Referme completement le flux (annulation ou fin de configuration).
     *
     * Sans reinitialiser les points, le rectangle trace resterait affiche indefiniment sur la carte une
     * fois l'ecran de configuration quitte.
     */
    fun closeFlow() {
        configBbox = null
        bboxPoints = emptyList()
        corridor = null
    }

    /**
     * Quitte le trace du rectangle (bouton "Annuler" ou retour systeme).
     *
     * Contrairement a "Reinitialiser", referme aussi entierement le mode de saisie, et pas seulement les
     * points deja poses.
     */
    fun cancelDrawing() {
        drawingActive = false
        bboxPoints = emptyList()
    }
}
