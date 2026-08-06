package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.map.offline.TileHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/** Longueur et durée d'un itinéraire, en mètres et en secondes, et son tracé en (lon, lat). */
data class RouteResult(
    val meters: Double,
    val seconds: Double,
    val shape: List<Pair<Double, Double>> = emptyList(),
)

/**
 * Client du moteur d'itinéraire **Valhalla**.
 *
 * Retenu pour une raison décisive ici : ses cinq disciplines sortent d'une **seule** instance, via le
 * modèle de coût `bicycle` et son `bicycle_type` (plus `pedestrian`). OSRM impose un serveur par profil,
 * soit cinq à héberger ; GraphHopper n'offre pas d'instance publique sans clé. L'instance publique de
 * FOSSGIS sert de défaut, et l'URL est un réglage : même montage que le géocodeur, pour la même raison.
 *
 * Valhalla ne rend pas l'itinéraire le plus court mais le moins coûteux, le coût mêlant temps, revêtement
 * et dénivelé selon la discipline. C'est donc un itinéraire *recommandé*, ce que dit l'infobulle du "i".
 */
object Valhalla {
    const val DEFAULT_URL = "https://valhalla1.openstreetmap.de/route"

    private const val TIMEOUT_MS = 15_000

    private val json = Json { ignoreUnknownKeys = true }

    /** Modèle de coût et, pour le vélo, type de monture. Seule fonction à connaître le vocabulaire du moteur. */
    internal fun costingOf(profile: RoutingProfile): Pair<String, String?> = when (profile) {
        RoutingProfile.ROAD_BIKE -> "bicycle" to "Road"
        RoutingProfile.GRAVEL -> "bicycle" to "Cross"        // Valhalla nomme "Cross" le velo de cyclocross/gravel
        RoutingProfile.HYBRID_BIKE -> "bicycle" to "Hybrid"
        RoutingProfile.MOUNTAIN_BIKE -> "bicycle" to "Mountain"
        RoutingProfile.FOOT -> "pedestrian" to null
    }

    /**
     * URL de requête, de ([fromLat], [fromLon]) vers ([toLat], [toLon]).
     *
     * `directions_type=none` coupe la génération du guidage vocal virage par virage : on ne veut qu'un total,
     * et la narration représente l'essentiel du poids de la réponse.
     *
     * Les coordonnées sont interpolées par `toString()`, insensible à la locale, et non par `format()`, qui
     * écrirait une virgule décimale en français et produirait un JSON invalide.
     */
    fun url(
        base: String, fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, profile: RoutingProfile,
    ): String {
        val (costing, bicycleType) = costingOf(profile)
        val options = if (bicycleType == null) ""
        else ""","costing_options":{"bicycle":{"bicycle_type":"$bicycleType"}}"""
        val body = """{"locations":[{"lat":$fromLat,"lon":$fromLon},{"lat":$toLat,"lon":$toLon}],""" +
            """"costing":"$costing"$options,"units":"kilometers","directions_type":"none"}"""
        val sep = if ('?' in base) '&' else '?'
        return base.trimEnd('&', '?') + sep + "json=" + URLEncoder.encode(body, "UTF-8")
    }

    /**
     * Lit le total et le tracé. Null si la réponse ne porte pas de total : réponse d'erreur, ou corps
     * inattendu.
     *
     * Le tracé vient des segments, chacun encodé en polyligne (cf. [Polyline]) ; il est facultatif, et une
     * réponse qui n'en porterait pas donne quand même sa mesure. `directions_type=none` ne supprime que le
     * guidage rédigé, pas la géométrie.
     */
    fun parse(body: String): RouteResult? = runCatching {
        val trip = json.decodeFromString<Response>(body).trip
        val km = trip?.summary?.length
        val sec = trip?.summary?.time
        if (km == null || sec == null) return@runCatching null
        val shape = trip.legs.flatMap { leg -> leg.shape?.let { Polyline.decode(it) } ?: emptyList() }
        RouteResult(km * 1000.0, sec, shape)
    }.getOrNull()

    /**
     * Calcule l'itinéraire. Null quand il n'y en a pas : les deux points ne sont reliés par aucune voie
     * praticable dans cette discipline, le service ne répond pas, ou le réseau manque. L'appelant n'a rien
     * à en faire de différent - il ne peut afficher aucune distance dans les trois cas.
     */
    suspend fun route(
        base: String, fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, profile: RoutingProfile,
    ): RouteResult? = withContext(Dispatchers.IO) {
        val resp = TileHttp.fetch(url(base, fromLat, fromLon, toLat, toLon, profile), TIMEOUT_MS, TIMEOUT_MS)
        resp.body?.let { parse(it.toString(Charsets.UTF_8)) }
    }

    @Serializable internal data class Response(val trip: Trip? = null)
    @Serializable internal data class Trip(
        val summary: Summary? = null,
        val legs: List<Leg> = emptyList(),
    )
    @Serializable internal data class Leg(val shape: String? = null)
    @Serializable internal data class Summary(val length: Double? = null, val time: Double? = null)
}
