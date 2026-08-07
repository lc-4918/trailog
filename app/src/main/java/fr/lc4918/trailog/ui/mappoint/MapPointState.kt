package fr.lc4918.trailog.ui.mappoint

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.domain.model.ComputedTrack

/** Ou en est la recherche de l'adresse du point designe. */
sealed interface AddressState {
    data object Loading : AddressState

    /** Le service a repondu qu'il n'y a rien la : plein champ, foret, lac. Distinct de [Failed], qui
     *  appellerait un "reessayez" que ce cas-ci ne merite pas. */
    data object NotFound : AddressState
    data object Failed : AddressState
    data class Done(val label: String) : AddressState
}

/** Ou en est une mesure de distance. Null = jamais demandee : le bouton est alors seul, sans rien a droite. */
sealed interface MeasureState {
    /** Requete en cours, ou position GPS pas encore recue. */
    data object Loading : MeasureState

    /** Aucun itineraire : points non relies dans cette discipline, service muet, ou reseau absent. */
    data object Failed : MeasureState

    /** [track] : l'itineraire mesure, pret a dessiner. Null si le moteur n'a rendu aucune geometrie
     *  exploitable - la mesure reste alors chiffree, mais rien ne se pose sur la carte. */
    data class Done(
        val meters: Double,
        val seconds: Double,
        val track: ComputedTrack? = null,
    ) : MeasureState
}

/**
 * Etat du point designe par un appui long sur la carte : son adresse, et les deux distances qui s'y mesurent.
 *
 * Le point n'est ni une trace, ni un marqueur, ni un lieu cherche par son nom : c'est un endroit quelconque,
 * dont on veut savoir ou il est et a quelle distance il se trouve. Il ne survit pas a la croix de son
 * infobulle - rien ne l'enregistre, et rien ne le retrouverait.
 *
 * Regroupe ici plutot qu'en variables eparses dans l'ecran de carte : les transitions comptent plus que les
 * valeurs (choisir un point de reference masque l'infobulle puis la rend, un nouvel appui long doit tout
 * defaire), et elles sont fautives a ecrire deux fois.
 */
@Stable
class MapPointState {

    /** Le point designe, en (lon, lat). Null = aucune infobulle a l'ecran. */
    var point by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    var address by mutableStateOf<AddressState>(AddressState.Loading)
        private set

    /** En attente d'un tap sur la carte pour poser le point de reference (barre de consigne affichee). */
    var pickingPoint by mutableStateOf(false)
        private set

    /** Point de reference pose a la main, en (lon, lat). */
    var refPoint by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    var positionMeasure by mutableStateOf<MeasureState?>(null)
        private set
    var pointMeasure by mutableStateOf<MeasureState?>(null)
        private set

    /**
     * Origine figee du calcul depuis la position, en (lat, lon).
     *
     * Figee, et non relue a chaque position recue : un itineraire est une requete reseau, la ou le vol
     * d'oiseau ne coutait rien. Suivre le capteur (une position toutes les 2 s) en lancerait une par point,
     * ce qu'aucun service public ne tolererait. La mesure vaut donc pour l'endroit d'ou elle a ete demandee.
     */
    var positionOrigin by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    /**
     * Numero d'ordre des mesures, incremente chaque fois que l'une d'elles change.
     *
     * Sert de cle de relance a l'effet qui pose les itineraires sur la carte. Les prendre eux-memes pour
     * cle serait correct mais couteux : un itineraire porte plusieurs milliers de points, et Compose
     * compare ses cles a chaque recomposition.
     */
    var measureRevision by mutableStateOf(0)
        private set

    /** Epingles noires a poser sur la carte : le point designe, puis son point de reference. */
    val markers: List<Pair<Double, Double>>
        get() = listOfNotNull(point, refPoint)

    /** Traces des itineraires mesures, a poser sur la carte. Vide tant qu'aucune mesure n'a abouti. */
    val routeTracks: List<ComputedTrack>
        get() = listOfNotNull(positionMeasure, pointMeasure)
            .filterIsInstance<MeasureState.Done>()
            .mapNotNull { it.track }
            .filter { it.samples.size >= 2 }

    /** L'infobulle s'efface le temps de choisir un point sur la carte, qu'elle recouvrirait. */
    val bubbleVisible: Boolean get() = point != null && !pickingPoint

    /**
     * Appui long sur la carte : un nouveau point remplace entierement le precedent.
     *
     * Tout retombe, jusqu'a l'adresse : les distances mesurees valaient pour un autre endroit, et les
     * garder afficherait, a cote des boutons, des chiffres sans rapport avec le point qu'on vient de
     * designer.
     */
    fun open(lon: Double, lat: Double) {
        point = lon to lat
        address = AddressState.Loading
        pickingPoint = false
        clearMeasures()
    }

    fun publishAddress(a: AddressState) { address = a }

    /** Demande la mesure depuis la position : l'origine sera figee des la premiere position connue. */
    fun requestDistanceFromPosition() {
        positionMeasure = MeasureState.Loading
        positionOrigin = null
    }

    /** Fige l'origine, une seule fois par demande (cf. [positionOrigin]). */
    fun fixPositionOrigin(lat: Double, lon: Double) {
        if (positionMeasure == MeasureState.Loading && positionOrigin == null) positionOrigin = lat to lon
    }

    fun publishPositionMeasure(m: MeasureState) {
        positionMeasure = m
        measureRevision++
    }

    fun startPickingPoint() { pickingPoint = true }

    fun cancelPickingPoint() { pickingPoint = false }

    /** Tap sur la carte pendant le choix : le point de reference est pose, et la mesure part de la. */
    fun chooseRefPoint(lon: Double, lat: Double) {
        refPoint = lon to lat
        pointMeasure = MeasureState.Loading
        pickingPoint = false
    }

    fun publishPointMeasure(m: MeasureState) {
        pointMeasure = m
        measureRevision++
    }

    /** Croix de l'infobulle, ou abandon complet : ne laisse ni epingle, ni trace, ni mesure. */
    fun clear() {
        point = null
        address = AddressState.Loading
        pickingPoint = false
        clearMeasures()
    }

    private fun clearMeasures() {
        refPoint = null
        positionMeasure = null
        pointMeasure = null
        positionOrigin = null
        measureRevision++
    }
}
