package fr.lc4918.trailog.ui.geocode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.geocode.GeocodePlace

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

    /** La distance depuis la position a été demandée : elle se recalcule ensuite à chaque position reçue,
     *  plutôt que d'être figée à l'instant du tap - l'utilisateur qui marche voit la valeur suivre. */
    var distanceFromPositionRequested by mutableStateOf(false)
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
        distanceFromPositionRequested = false
        refPoint = null
        pickingPoint = false
    }

    fun requestDistanceFromPosition() { distanceFromPositionRequested = true }

    fun startPickingPoint() { pickingPoint = true }

    fun cancelPickingPoint() { pickingPoint = false }

    fun setRefPoint(lon: Double, lat: Double) {
        refPoint = lon to lat
        pickingPoint = false
    }

    /** Ferme tout : la recherche, le lieu, ses mesures. */
    fun clear() {
        closeSearch()
        place = null
        pickingPoint = false
        refPoint = null
        distanceFromPositionRequested = false
    }
}
