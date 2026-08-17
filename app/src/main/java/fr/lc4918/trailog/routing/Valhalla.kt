package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.model.HillPref
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.domain.model.SurfacePref
import fr.lc4918.trailog.domain.model.TrackPoint
import fr.lc4918.trailog.domain.model.WayPref
import fr.lc4918.trailog.map.offline.TileHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * Longueur et durée d'un itinéraire, en mètres et en secondes, et son tracé.
 *
 * [points] porte les altitudes quand le moteur en a rendu (cf. [RouteElevation]) ; sinon les mêmes points,
 * sans altitude. Un seul tracé, et non un couple géométrie/altitudes : tout ce qui le consomme - la ligne
 * sur la carte, sa teinte de pente, le profil - a besoin des deux ensemble.
 */
data class RouteResult(
    val meters: Double,
    val seconds: Double,
    val points: List<TrackPoint> = emptyList(),
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

    /**
     * Pas d'échantillonnage des altitudes, en mètres.
     *
     * 30 m parce que c'est la résolution des modèles de terrain que sert le moteur (SRTM et assimilés) :
     * descendre plus bas n'ajoute aucun relief, seulement des points interpolés et du poids de réponse.
     *
     * Demandé à chaque mesure, et non lors de l'ouverture du profil : le tracé est déjà teinté par la pente
     * quand il se pose sur la carte, et le profil s'ouvre alors sans attendre - c'est le parti que prend
     * déjà l'application pour les traces importées, dont les profils sont calculés à l'import.
     */
    const val ELEVATION_INTERVAL_M = 30

    private val json = Json { ignoreUnknownKeys = true }

    /** Modèle de coût et, pour le vélo, type de monture. Avec [costingOptionsOf], la seule chose ici à
     *  connaître le vocabulaire du moteur. */
    internal fun costingOf(profile: RoutingProfile): Pair<String, String?> = when (profile) {
        RoutingProfile.ROAD_BIKE -> "bicycle" to "Road"
        RoutingProfile.GRAVEL -> "bicycle" to "Cross"        // Valhalla nomme "Cross" le velo de cyclocross/gravel
        RoutingProfile.HYBRID_BIKE -> "bicycle" to "Hybrid"
        RoutingProfile.MOUNTAIN_BIKE -> "bicycle" to "Mountain"
        RoutingProfile.FOOT -> "pedestrian" to null
    }

    /**
     * Ce que les trois préférences de l'utilisateur (cf. [RoutingPrefs]) deviennent dans le vocabulaire du
     * moteur, pour la discipline [profile].
     *
     * Une même question ne se dit pas de la même façon selon qu'on roule ou qu'on marche : "privilégier les
     * voies vertes" se demande par `use_roads` au vélo - sa propension à rouler avec les voitures - et par
     * `walkway_factor` au marcheur, qui n'a pas de `use_roads`. De même le revêtement : le vélo a
     * `avoid_bad_surfaces`, le marcheur a `use_tracks`, les chemins de terre étant ce qui le concerne.
     *
     * **Une position centrale n'émet rien**, et c'est la règle qui gouverne toute cette fonction : sur
     * l'instance publique, renvoyer la valeur "par défaut" de la documentation ne redonne pas le
     * comportement par défaut. Mesuré : en vélo de route sur Grenoble - Voiron, ne rien dire donne 59 % de
     * voies douces, dire `use_roads: 0.5` - le prétendu défaut - tombe à 22 %. Le silence est donc la seule
     * façon de laisser le service décider.
     *
     * Les valeurs ne sont pas choisies au jugé, chacune sort d'une mesure :
     * - `use_roads` à 0,2 : le basculement vers la véloroute se fait entre 0,3 et 0,2, et descendre plus bas
     *   ne change plus rien. Le VTT va à 0,1, seul type de monture que les chemins ruraux accueillent.
     * - `walkway_factor` à 0,5 : de 23 à 85 % de voies douces sur un trajet urbain, pour 440 m de plus.
     * - `use_hills` à 1 : ne cherche pas la difficulté, cesse de payer le détour qui l'évite - d'où un
     *   trajet à la fois plus montagnard et plus court.
     */
    internal fun costingOptionsOf(profile: RoutingProfile, prefs: RoutingPrefs): Map<String, String> {
        val foot = profile == RoutingProfile.FOOT
        val o = linkedMapOf<String, String>()
        costingOf(profile).second?.let { o["bicycle_type"] = "\"$it\"" }
        when (prefs.ways) {
            WayPref.ROADS -> o[if (foot) "walkway_factor" else "use_roads"] = if (foot) "1.5" else "0.8"
            WayPref.BALANCED -> Unit
            WayPref.SOFT -> when {
                foot -> o["walkway_factor"] = "0.5"
                profile == RoutingProfile.MOUNTAIN_BIKE -> o["use_roads"] = "0.1"
                else -> o["use_roads"] = "0.2"
            }
        }
        when (prefs.hills) {
            HillPref.AVOID -> o["use_hills"] = "0.0"
            HillPref.BALANCED -> Unit
            HillPref.SEEK -> o["use_hills"] = "1.0"
        }
        when (prefs.surface) {
            SurfacePref.PAVED -> o[if (foot) "use_tracks" else "avoid_bad_surfaces"] = if (foot) "0.0" else "1.0"
            SurfacePref.BALANCED -> Unit
            SurfacePref.ROUGH -> o[if (foot) "use_tracks" else "avoid_bad_surfaces"] = if (foot) "1.0" else "0.0"
        }
        return o
    }

    /**
     * URL de requête passant par [points], en (lat, lon), dans l'ordre donné.
     *
     * Deux points suffisent ; au-delà, les intermédiaires deviennent des étapes que l'itinéraire doit
     * traverser, et le moteur rend un segment par intervalle. C'est le mode dont vit le planificateur.
     *
     * `directions_type=none` coupe la génération du guidage vocal virage par virage : on ne veut qu'un total,
     * et la narration représente l'essentiel du poids de la réponse.
     *
     * `elevation_interval` demande les altitudes du terrain le long de l'itinéraire ([elevationIntervalM],
     * 0 pour ne pas les demander). Un moteur trop ancien, ou dépourvu de données de terrain, ignore le
     * paramètre sans faillir : la réponse revient simplement sans altitudes.
     *
     * Les coordonnées sont interpolées par `toString()`, insensible à la locale, et non par `format()`, qui
     * écrirait une virgule décimale en français et produirait un JSON invalide.
     */
    fun url(
        base: String, points: List<Pair<Double, Double>>, profile: RoutingProfile,
        prefs: RoutingPrefs = RoutingPrefs.Balanced,
        elevationIntervalM: Int = ELEVATION_INTERVAL_M,
    ): String {
        val (costing, _) = costingOf(profile)
        val opts = costingOptionsOf(profile, prefs)
        val options = if (opts.isEmpty()) ""
        else ""","costing_options":{"$costing":{""" +
            opts.entries.joinToString(",") { (k, v) -> """"$k":$v""" } + "}}"
        val elevation = if (elevationIntervalM > 0) ""","elevation_interval":$elevationIntervalM""" else ""
        val locations = points.joinToString(",") { (lat, lon) -> """{"lat":$lat,"lon":$lon}""" }
        val body = """{"locations":[$locations],""" +
            """"costing":"$costing"$options,"units":"kilometers","directions_type":"none"$elevation}"""
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
     *
     * Les altitudes sont lues segment par segment, chacun ayant son propre pas d'échantillonnage, puis
     * reportées sur les points du tracé (cf. [RouteElevation]).
     */
    fun parse(body: String): RouteResult? = runCatching {
        val trip = json.decodeFromString<Response>(body).trip
        val km = trip?.summary?.length
        val sec = trip?.summary?.time
        if (km == null || sec == null) return@runCatching null
        val points = trip.legs.flatMap { leg ->
            val shape = leg.shape?.let { Polyline.decode(it) } ?: emptyList()
            RouteElevation.pointsOf(shape, elevationsOf(leg), leg.elevationInterval ?: 0.0)
        }
        RouteResult(km * 1000.0, sec, points)
    }.getOrNull()

    /**
     * Altitudes exploitables d'un segment, vide s'il n'y en a pas.
     *
     * Un trou dans le modèle de terrain (le moteur rend alors un `null`) fait rejeter **toute** la table du
     * segment plutôt que de combler : une altitude inventée en travers d'un profil se lit comme une côte,
     * et vaut moins qu'un itinéraire sans profil du tout.
     */
    private fun elevationsOf(leg: Leg): List<Double> {
        val e = leg.elevation ?: return emptyList()
        return if (e.any { it == null }) emptyList() else e.filterNotNull()
    }

    /**
     * Calcule l'itinéraire passant par [points], en (lat, lon). Null quand il n'y en a pas : deux étapes
     * consécutives ne sont reliées par aucune voie praticable dans cette discipline, le service ne répond
     * pas, ou le réseau manque. L'appelant n'a rien à en faire de différent - il ne peut afficher aucune
     * distance dans les trois cas.
     *
     * Null également en deçà de deux étapes, sans interroger le service : il n'y a pas d'itinéraire à
     * calculer, et le moteur répondrait une erreur.
     */
    suspend fun route(
        base: String, points: List<Pair<Double, Double>>, profile: RoutingProfile,
        prefs: RoutingPrefs = RoutingPrefs.Balanced,
    ): RouteResult? = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext null
        val resp = TileHttp.fetch(url(base, points, profile, prefs), TIMEOUT_MS, TIMEOUT_MS)
        resp.body?.let { parse(it.toString(Charsets.UTF_8)) }
    }

    @Serializable internal data class Response(val trip: Trip? = null)
    @Serializable internal data class Trip(
        val summary: Summary? = null,
        val legs: List<Leg> = emptyList(),
    )

    /** [elevation] tolère les nulls : le moteur en rend là où son modèle de terrain n'a pas de donnée. */
    @Serializable internal data class Leg(
        val shape: String? = null,
        val elevation: List<Double?>? = null,
        @SerialName("elevation_interval") val elevationInterval: Double? = null,
    )
    @Serializable internal data class Summary(val length: Double? = null, val time: Double? = null)
}
