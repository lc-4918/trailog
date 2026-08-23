package fr.lc4918.trailog.ui.alert

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.location.TrackWatch

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

/**
 * L'itineraire du planificateur, qui n'est encore dans aucune couche.
 *
 * Un parcours calcule se suit comme n'importe quelle trace - c'est meme le cas le plus courant : on
 * compose son trajet, on part, et on veut etre prevenu si on le quitte. L'importer d'abord dans la
 * bibliotheque serait un detour, et laisserait derriere soi une couche dont on ne voulait pas.
 *
 * Un identifiant NEGATIF, donc hors de portee des identifiants de couches (Room les attribue a partir de
 * 1) : la trace suivie se designe partout par [TrackCandidate.layerId], et il fallait une valeur qui ne
 * puisse jamais tomber sur une couche reelle. C'est aussi ce qui permet de reconnaitre le parcours
 * temporaire la ou l'on verifie que la couche suivie est toujours a l'ecran (cf. OffTrackAlertEffects).
 */
const val PlannedRouteLayerId = -1L

/**
 * Le CHOIX d'une trace a suivre : la liste ouverte, et les candidates qu'on y propose.
 *
 * **Ce qui n'est plus ici.** La trace suivie, l'ecart mesure et l'alerte qui en decoule vivaient dans cet
 * etat d'ecran ; ils sont passes dans [TrackWatch], hors de la composition, parce que l'ecran eteint
 * arretait la mesure - et une alerte qui ne se declenche que sous les yeux de celui qu'elle doit prevenir
 * n'alerte personne. Ne reste donc ici que ce qui EST une affaire d'ecran : une question posee, une liste
 * qu'on ouvre et qu'on referme.
 */
@Stable
class OffTrackAlertState {

    /** Choix d'une trace ouvert. */
    var chooserOpen by mutableStateOf(false)
        private set

    /** Traces proposees, les plus proches d'abord. Null tant que la recherche n'a pas rendu sa reponse. */
    var candidates by mutableStateOf<List<TrackCandidate>?>(null)

    /**
     * La cloche a ete touchee capteur eteint : on propose d'aller l'allumer.
     *
     * Tout ici est suspendu au capteur - sans position, il n'y a rien a projeter sur la trace. Ouvrir la
     * liste quand meme donnerait un choix de traces classees par une distance qu'on ne sait pas mesurer.
     */
    var needsGpsDialog by mutableStateOf(false)
        private set

    /**
     * La liste est demandee, et elle attend que le capteur reponde.
     *
     * Elle ne s'ouvre pas au retour de la boite de dialogue mais a l'allumage : l'utilisateur passe par
     * les reglages du systeme entre-temps, et le temps qu'il en revienne, la question qu'il a posee doit
     * tenir toute seule.
     */
    var chooserPending by mutableStateOf(false)
        private set

    /** Tap sur la cloche : la liste repart vide, les distances d'il y a une heure ne valent plus rien. */
    fun openChooser() {
        chooserOpen = true
        candidates = null
    }

    /** Le geste de la cloche : la liste si le capteur tourne, la proposition de l'allumer sinon. */
    fun onBellTap(gpsActive: Boolean) {
        if (gpsActive) openChooser() else needsGpsDialog = true
    }

    /** "Allumer" : la boite se referme, et la liste attend la premiere position. */
    fun awaitGps() {
        needsGpsDialog = false
        chooserPending = true
    }

    fun dismissNeedsGps() { needsGpsDialog = false }

    /** Le capteur repond : la liste mise en attente s'ouvre enfin. */
    fun openPendingChooser(gpsActive: Boolean) {
        if (chooserPending && gpsActive) {
            chooserPending = false
            openChooser()
        }
    }

    /** Plus rien a suivre - reglage eteint, ou capteur coupe : tout ce qui etait en cours se referme. */
    fun reset() {
        closeChooser()
        chooserPending = false
        needsGpsDialog = false
    }

    fun closeChooser() {
        chooserOpen = false
        candidates = null
    }

    /**
     * Trace retenue : la veille en prend charge, et la liste se referme.
     *
     * L'ecart est connu d'emblee - c'est celui qui a servi a classer les candidates - et evite d'afficher
     * une cloche sans distance le temps de la premiere mesure.
     */
    fun follow(c: TrackCandidate, thresholdM: Double) {
        TrackWatch.follow(
            TrackWatch.Followed(c.layerId, c.layerName, c.trackIndex, c.trackCount, c.samples),
            c.awayM, thresholdM,
        )
        closeChooser()
    }
}
