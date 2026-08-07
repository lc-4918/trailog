package fr.lc4918.trailog.ui.geocode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.geocode.GeocodePlace

/**
 * État de la recherche de lieu : sa barre de saisie, et le lieu retenu.
 *
 * Rien de plus. Les mesures de distance et le profil d'itinéraire qu'a portés cette infobulle vivent
 * désormais dans le planificateur (cf. `ui/planner`), qui en fait sa raison d'être plutôt qu'un
 * supplément : un lieu trouvé se decrit par son adresse, un trajet se calcule ailleurs.
 *
 * Regroupé ici plutôt qu'en variables éparses dans l'écran de carte : les transitions comptent plus que
 * les valeurs, et elles sont fautives à écrire deux fois.
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

    fun openSearch() { searchOpen = true }

    /** Referme la seule barre de saisie : le lieu déjà choisi reste affiché. */
    fun closeSearch() {
        searchOpen = false
        query = ""
        results = emptyList()
        searching = false
    }

    fun select(p: GeocodePlace) {
        place = p
        closeSearch()
    }

    /** Ferme tout : la recherche et le lieu. */
    fun clear() {
        closeSearch()
        place = null
    }
}
