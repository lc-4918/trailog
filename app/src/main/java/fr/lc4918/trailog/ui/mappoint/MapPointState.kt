package fr.lc4918.trailog.ui.mappoint

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ou en est la recherche de l'adresse du point designe. */
sealed interface AddressState {
    data object Loading : AddressState

    /** Le service a repondu qu'il n'y a rien la : plein champ, foret, lac. Distinct de [Failed], qui
     *  appellerait un "reessayez" que ce cas-ci ne merite pas. */
    data object NotFound : AddressState
    data object Failed : AddressState

    /**
     * L'adresse trouvee, en morceaux : l'intitule, la voie, la commune (cf. Photon.labelParts). Le
     * decoupage vient du geocodeur et sert a l'infobulle : trop longue pour une ligne, l'adresse se coupe
     * la et pas ailleurs.
     */
    data class Done(val lines: List<String>) : AddressState {
        /** Une adresse d'un seul tenant, dont on ne connait pas le decoupage. */
        constructor(label: String) : this(listOf(label))

        /** L'adresse d'un seul tenant, quand elle tient sur une ligne. */
        val label: String get() = lines.joinToString(", ")
    }
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
 *
 * **Les mesures, elles, ont demenage** dans [PointMeasures] : elles ne sont plus le privilege d'un appui
 * long, un point d'interet posant exactement la meme question. Ce qui reste ici est ce qu'un appui long a
 * de propre - un endroit quelconque, et l'adresse qu'on lui cherche.
 */
@Stable
class MapPointState {

    /** Le point designe, en (lon, lat). Null = aucune infobulle a l'ecran. */
    var point by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    var address by mutableStateOf<AddressState>(AddressState.Loading)
        private set

    /** Les deux distances mesurees depuis ce point. Sa cible suit [point] (cf. [open], [clear]). */
    val measures = PointMeasures()

    /** En attente d'un tap sur la carte pour poser le point de reference (barre de consigne affichee). */
    val pickingPoint: Boolean get() = measures.pickingPoint

    /** Epingles noires a poser sur la carte : le point designe, puis son point de reference. */
    val markers: List<Pair<Double, Double>>
        get() = listOfNotNull(point) + measures.markers

    /** L'infobulle s'efface le temps de choisir un point sur la carte, qu'elle recouvrirait. */
    val bubbleVisible: Boolean get() = point != null && !measures.busy

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
        measures.retarget(lon to lat)
    }

    fun publishAddress(a: AddressState) { address = a }

    /** Croix de l'infobulle, ou abandon complet : ne laisse ni epingle, ni trace, ni mesure. */
    fun clear() {
        point = null
        address = AddressState.Loading
        measures.retarget(null)
    }
}
