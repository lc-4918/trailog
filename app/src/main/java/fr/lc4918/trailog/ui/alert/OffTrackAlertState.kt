package fr.lc4918.trailog.ui.alert

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.domain.geo.OffTrack
import fr.lc4918.trailog.domain.model.Sample

/**
 * Une trace proposee au suivi : d'ou elle vient, et a quelle distance de la position elle passe.
 *
 * [trackIndex] designe le segment dans sa couche - une couche importee peut en porter plusieurs, sans
 * continuite entre eux -, et [trackCount] combien elle en a : au-dela d'un seul, la liste les numerote,
 * sans quoi trois lignes porteraient le meme nom.
 *
 * Les echantillons voyagent AVEC le candidat : ils viennent d'etre lus pour calculer [awayM], et les
 * relire au moment du choix ferait attendre une seconde fois ce qu'on tient deja.
 */
data class TrackCandidate(
    val layerId: Long,
    val layerName: String,
    val trackIndex: Int,
    val trackCount: Int,
    val awayM: Double,
    val samples: List<Sample>,
)

/** La trace qu'on suit des yeux : son identite, et le parcours sur lequel la position se rabat. */
data class FollowedTrack(
    val layerId: Long,
    val layerName: String,
    val trackIndex: Int,
    val trackCount: Int,
    val samples: List<Sample>,
)

/**
 * Etat de l'alerte d'eloignement : la trace suivie, l'ecart du moment, et le choix d'une trace.
 *
 * Trois etats bien distincts, et non un seul drapeau : on peut suivre une trace sans etre en alerte (le
 * cas ordinaire), etre en alerte sans que la banniere s'affiche (la croix l'a tue), et chercher une trace
 * a suivre pendant qu'on en suit deja une.
 */
@Stable
class OffTrackAlertState {

    /** Trace suivie, ou null : le bouton est alors une cloche eteinte, et rien ne se calcule. */
    var followed by mutableStateOf<FollowedTrack?>(null)
        private set

    /** Ecart a la trace suivie a la derniere position recue (m). */
    var awayM by mutableStateOf<Double?>(null)
        private set

    /** Au-dela du seuil regle (avec sa marge de retour, cf. [OffTrack.alerting]). */
    var alerting by mutableStateOf(false)
        private set

    /**
     * Banniere tue d'un tap sur sa croix. Ce n'est pas un arret du suivi : on sait qu'on est loin, on ne
     * veut plus le lire. Le silence est leve des qu'on revient sur la trace, et l'alerte suivante se dira.
     */
    var silenced by mutableStateOf(false)
        private set

    /** Choix d'une trace ouvert. */
    var chooserOpen by mutableStateOf(false)
        private set

    /** Traces proposees, les plus proches d'abord. Null tant que la recherche n'a pas rendu sa reponse. */
    var candidates by mutableStateOf<List<TrackCandidate>?>(null)

    /** La banniere s'affiche : en alerte, et pas tue. */
    val banner: Boolean get() = alerting && !silenced

    /** Tap sur la cloche : la liste repart vide, les distances d'il y a une heure ne valent plus rien. */
    fun openChooser() {
        chooserOpen = true
        candidates = null
    }

    fun closeChooser() {
        chooserOpen = false
        candidates = null
    }

    /**
     * Trace retenue : on repart d'une alerte vierge, l'ecart de la trace precedente n'ayant rien a dire de
     * celle-ci. L'ecart est en revanche connu d'emblee - c'est celui qui a servi a classer les candidates.
     */
    fun follow(c: TrackCandidate, thresholdM: Double) {
        followed = FollowedTrack(c.layerId, c.layerName, c.trackIndex, c.trackCount, c.samples)
        awayM = c.awayM
        alerting = c.awayM >= thresholdM
        silenced = false
        closeChooser()
    }

    /** Fin du suivi : plus de trace, plus d'ecart, plus d'alerte. */
    fun stop() {
        followed = null
        awayM = null
        alerting = false
        silenced = false
    }

    /**
     * Une position de plus : l'ecart mesure, et l'alerte qui en decoule.
     *
     * Revenir sous le seuil leve le silence : la croix ne tait que l'ecart du moment, pas la fonction.
     */
    fun update(away: Double, thresholdM: Double) {
        awayM = away
        val next = OffTrack.alerting(alerting, away, thresholdM)
        if (!next) silenced = false
        alerting = next
    }

    /** Croix de la banniere. */
    fun silence() { silenced = true }
}
