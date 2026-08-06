package fr.lc4918.trailog.ui.geocode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.geocode.GeocodePlace

/** Où en est une mesure de distance. Null = jamais demandée : le bouton est alors seul, sans rien à droite. */
sealed interface MeasureState {
    /** Requête en cours, ou position GPS pas encore reçue. */
    data object Loading : MeasureState
    /** Aucun itinéraire : points non reliés dans cette discipline, service muet, ou réseau absent. */
    data object Failed : MeasureState
    data class Done(val meters: Double, val seconds: Double) : MeasureState
}

/**
 * État de la recherche de lieu, de sa barre de saisie jusqu'aux mesures de distance.
 *
 * Regroupé ici plutôt qu'en variables éparses dans l'écran de carte : les transitions comptent plus que
 * les valeurs (choisir un point de référence masque l'infobulle puis la rend, fermer la recherche doit
 * tout défaire), et elles sont fautives à écrire deux fois.
 */
@Stable
class GeocodeSearchState {
    /** Barre de saisie ouverte (le bouton loupe la bascule). */
    var searchOpen by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    var results by mutableStateOf<List<GeocodePlace>>(emptyList())
    var searching by mutableStateOf(false)

    /** Lieu choisi : marqueur noir sur la carte, et sujet de l'infobulle. */
    var place by mutableStateOf<GeocodePlace?>(null)
        private set

    /** En attente d'un tap sur la carte pour poser le point de référence (barre noire affichée). */
    var pickingPoint by mutableStateOf(false)
        private set

    /** Point de référence posé à la main, en (lon, lat). */
    var refPoint by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    var positionMeasure by mutableStateOf<MeasureState?>(null)
        private set
    var pointMeasure by mutableStateOf<MeasureState?>(null)
        private set

    /**
     * Origine figée du calcul depuis la position, en (lat, lon).
     *
     * Figée, et non relue à chaque position reçue : un itinéraire est une requête réseau, là où le vol
     * d'oiseau ne coûtait rien. Suivre le capteur (une position toutes les 2 s) en lancerait une par point,
     * ce qu'aucun service public ne tolérerait. La mesure vaut donc pour l'endroit d'où elle a été demandée.
     */
    var positionOrigin by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    /** L'infobulle s'efface le temps de choisir un point sur la carte, qu'elle recouvrirait. */
    val bubbleVisible: Boolean get() = place != null && !pickingPoint

    fun openSearch() { searchOpen = true }

    /** Referme la seule barre de saisie : le lieu déjà choisi et ses mesures restent affichés. */
    fun closeSearch() {
        searchOpen = false
        query = ""
        results = emptyList()
        searching = false
    }

    fun select(p: GeocodePlace) {
        place = p
        closeSearch()
        // Un nouveau lieu rend caduques les mesures du précédent : les garder afficherait, à côté des
        // boutons, des distances calculées vers un autre endroit.
        clearMeasures()
        pickingPoint = false
    }

    /** Demande la mesure depuis la position : l'origine sera figée dès la première position connue. */
    fun requestDistanceFromPosition() {
        positionMeasure = MeasureState.Loading
        positionOrigin = null
    }

    /** Fige l'origine, une seule fois par demande (cf. [positionOrigin]). */
    fun fixPositionOrigin(lat: Double, lon: Double) {
        if (positionMeasure == MeasureState.Loading && positionOrigin == null) positionOrigin = lat to lon
    }

    fun publishPositionMeasure(m: MeasureState) { positionMeasure = m }

    fun startPickingPoint() { pickingPoint = true }

    fun cancelPickingPoint() { pickingPoint = false }

    fun setRefPoint(lon: Double, lat: Double) {
        refPoint = lon to lat
        pointMeasure = MeasureState.Loading
        pickingPoint = false
    }

    fun publishPointMeasure(m: MeasureState) { pointMeasure = m }

    /** Ferme tout : la recherche, le lieu, ses mesures. */
    fun clear() {
        closeSearch()
        place = null
        pickingPoint = false
        clearMeasures()
    }

    private fun clearMeasures() {
        refPoint = null
        positionMeasure = null
        pointMeasure = null
        positionOrigin = null
    }
}
