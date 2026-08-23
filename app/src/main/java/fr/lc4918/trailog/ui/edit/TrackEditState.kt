package fr.lc4918.trailog.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.domain.geo.TrackEdit

/** Un segment designe du doigt : dans quelle couche, et lequel. */
data class SegmentRef(val layerId: Long, val layerName: String, val segment: Int)

/** Un endroit de coupe retenu : la couche, et le point vise sur sa geometrie. */
data class CutTarget(val layerId: Long, val layerName: String, val hit: TrackEdit.Hit)

/** L'outil en cours d'emploi. Chacun attend un tap sur une trace : c'est le meme geste pour les trois,
 *  seul change ce qu'il declenche. */
enum class EditTool { NONE, REVERSE, CUT, JOIN }

/**
 * Etat de la barre de retouche des traces.
 *
 * Les trois retouches ont quitte le menu d'une couche pour une barre posee sur la carte, parce qu'elles
 * ne portent pas sur une couche mais sur un **segment** - parfois deux, parfois dans deux couches
 * differentes -, et qu'un segment se designe du doigt sur la carte. Un menu d'arborescence n'a aucun moyen
 * de dire "celui-la, pas l'autre".
 *
 * La barre est un MODE : tant qu'elle est ouverte, les taps sur une trace lui reviennent au lieu d'ouvrir
 * un profil. C'est ce qui la rend previsible - et c'est pourquoi elle s'ouvre par un bouton, plutot que de
 * s'imposer des qu'une trace est selectionnee.
 */
class TrackEditState {

    /** Barre ouverte : la carte est en mode retouche. */
    var open by mutableStateOf(false)
        private set

    var tool by mutableStateOf(EditTool.NONE)
        private set

    /** Marqueur de coupe pose, en attente de confirmation. */
    var cut by mutableStateOf<CutTarget?>(null)
        private set

    /** Premier et second segment d'une jonction, dans l'ordre ou ils ont ete tapes. */
    var first by mutableStateOf<SegmentRef?>(null)
        private set
    var second by mutableStateOf<SegmentRef?>(null)
        private set

    /** Une operation est en cours (lecture de geometrie, appel au moteur d'itineraire). */
    var busy by mutableStateOf(false)

    /** Message a montrer a l'utilisateur : refus, ou repli sur la ligne droite. */
    var message by mutableStateOf<String?>(null)

    /**
     * Couche dont l'inversion attend confirmation, ou null.
     *
     * Seules les traces HORODATEES passent par la : inverser efface l'ordre des horaires, et donc la
     * sortie telle qu'elle s'est deroulee. Une trace sans temps n'a rien a perdre a etre relue a l'envers,
     * et se retourne sans qu'on demande rien.
     */
    var reverseConfirm by mutableStateOf<LayerEntity?>(null)

    /** La barre attend-elle un tap sur la carte ? C'est ce qui detourne les taps du profil. */
    val awaitingTap: Boolean get() = open && tool != EditTool.NONE

    fun toggleBar() {
        open = !open
        if (!open) reset()
    }

    fun close() { open = false; reset() }

    /** Choisit un outil, ou le referme s'il etait deja choisi - un second appui sur le meme bouton annule
     *  l'operation en cours, ce qui evite d'avoir a chercher comment en sortir. */
    fun choose(t: EditTool) {
        val same = tool == t
        reset()
        tool = if (same) EditTool.NONE else t
    }

    fun placeCut(target: CutTarget?) { cut = target }

    /**
     * Retient un segment tape. Le premier tap designe, le second complete ; un troisieme recommence.
     *
     * Le meme segment tape deux fois n'est pas une jonction : on le dit plutot que de le laisser passer,
     * une couche reecrite sur elle-meme etant plus difficile a defaire qu'a refuser.
     */
    fun pick(ref: SegmentRef, sameSegmentMessage: String) {
        when {
            first == null -> first = ref
            first?.layerId == ref.layerId && first?.segment == ref.segment -> message = sameSegmentMessage
            else -> second = ref
        }
    }

    fun reset() {
        tool = EditTool.NONE
        cut = null
        first = null
        second = null
        busy = false
    }
}
