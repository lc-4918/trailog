package fr.lc4918.trailog.location

import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.domain.geo.AutoFollow
import fr.lc4918.trailog.domain.geo.OffTrack
import fr.lc4918.trailog.domain.model.Sample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Les traces que le suivi automatique peut reconnaitre : celles de la bibliotheque affichees sur la carte,
 * et l'itineraire que le planificateur vient de calculer.
 *
 * Les couches, le service les lit lui-meme dans la base (cf. `LocationService`) : ecran eteint, il n'y a
 * plus d'ecran pour les lui donner. L'itineraire du planificateur, lui, n'y entre jamais - c'est l'ecran
 * qui le depose ici, a chaque calcul.
 *
 * **Seules les couches a portee sont ouvertes** ([layersNear]) : reconnaitre une trace demande de projeter
 * la position sur son profil, des milliers de points, et le faire sur les cinquante couches de quelqu'un
 * qui en a cinquante, a chaque position, serait ruineux. L'emprise, deja en base, les ecarte sans rien lire.
 */
object FollowCatalog {

    /** Identifiant de l'itineraire du planificateur : negatif, hors de portee des couches. */
    const val ROUTE_ID = -1L

    /**
     * Couches ouvertes au plus pour une position : une poignee de traces se recouvrent au meme endroit, et
     * au-dela on paierait des lectures pour des traces qu'on ne suit pas.
     */
    const val MAX_LAYERS = 12

    private val _route = MutableStateFlow<AutoFollow.Candidate?>(null)
    val route: StateFlow<AutoFollow.Candidate?> = _route.asStateFlow()

    fun setRoute(samples: List<Sample>?, label: String) {
        _route.value = samples?.takeIf { it.size >= 2 }?.let { candidate(ROUTE_ID, label, 0, 1, it) }
    }

    /**
     * Les couches dont une trace peut passer sous la position : visibles, porteuses d'une ligne, et dont
     * l'emprise est a portee de detection. La distance a l'emprise minore celle a la trace - le rectangle
     * la contient - si bien qu'on n'ecarte ici que des couches qui ne peuvent pas etre reconnues.
     */
    fun layersNear(layers: List<LayerEntity>, lat: Double, lon: Double, limit: Int = MAX_LAYERS): List<LayerEntity> =
        layers.asSequence()
            .filter { it.visible && it.hasLine }
            .map { it to OffTrack.bboxDistanceM(lat, lon, it.west, it.south, it.east, it.north) }
            .filter { it.second <= AutoFollow.ON_TRACK_M }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
            .toList()

    /** Une candidate, son emprise calculee une fois pour toutes : c'est elle qui ecarte d'un coup les traces
     *  lointaines, a chaque position. */
    fun candidate(id: Long, name: String, index: Int, count: Int, samples: List<Sample>) = AutoFollow.Candidate(
        id, name, index, count, samples,
        samples.minOf { it.lon }, samples.minOf { it.lat }, samples.maxOf { it.lon }, samples.maxOf { it.lat },
    )
}
