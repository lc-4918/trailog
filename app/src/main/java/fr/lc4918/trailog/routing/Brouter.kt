package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.domain.model.TrackPoint
import fr.lc4918.trailog.map.offline.TileHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Client du moteur d'itinéraire **BRouter**, le second moteur du réglage (cf. `RouteEngine`).
 *
 * Ce qu'il change par rapport à Valhalla tient en une phrase : le coût n'est pas dans le serveur, il est
 * dans le **profil** qu'on lui envoie. D'où une conversation en deux temps, là où Valhalla n'en demandait
 * qu'un : on dépose le texte du profil, le service rend un identifiant, et l'itinéraire se demande sous
 * cet identifiant. Le dépôt n'est fait qu'une fois par texte (cf. [ids]) - le même profil ressert à toutes
 * les étapes d'une même sortie.
 *
 * Deux cadeaux de la réponse, qui coûtaient un aller-retour ou un calcul chez Valhalla : la géométrie
 * porte l'**altitude** de chaque point, et le total porte le **dénivelé** - non lu ici, l'application
 * recalculant le sien pour que le profil d'un itinéraire et celui d'une trace se lisent pareil.
 */
object Brouter {
    const val DEFAULT_URL = "https://brouter.de/brouter"

    /**
     * Delai du DEPOT du profil, qui ne depend pas de la longueur du trajet : c'est un envoi de vingt
     * kilo-octets, que le service range sans rien calculer. Le calcul, lui, prend le sien de la longueur
     * demandee (cf. [RouteTimeout]).
     */
    private const val DEPOSIT_TIMEOUT_MS = 30_000

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Identifiants rendus par le service, par texte de profil déposé.
     *
     * En mémoire et non en base : un identifiant n'a de sens que pour l'instance qui l'a rendu, et rien ne
     * dit qu'il lui survive. Le perdre au redémarrage coûte un dépôt, soit une requête ; le garder en base
     * ferait espérer une permanence que le service ne promet nulle part.
     */
    private val ids = ConcurrentHashMap<String, String>()

    /**
     * URL de dépôt d'un profil : le chemin frère `/profile`, comme le géocodeur a son `/reverse`.
     *
     * Une chaîne de requête portée par l'URL de base - instance derrière un proxy à jeton - est conservée
     * et repoussée derrière le chemin, faute de quoi le `/profile` atterrirait au milieu des paramètres.
     */
    internal fun profileUrl(base: String): String {
        val i = base.indexOf('?')
        val chemin = (if (i < 0) base else base.substring(0, i)).trimEnd('/')
        val requete = if (i < 0) "" else base.substring(i)
        return "$chemin/profile$requete"
    }

    /**
     * URL de calcul passant par [points], en (lat, lon), dans l'ordre donné.
     *
     * BRouter attend des couples **lon,lat** séparés par des barres verticales - l'ordre inverse de celui
     * de l'application et de Valhalla. C'est le genre d'inversion qui ne lève rien : elle rend un
     * itinéraire, ailleurs, ou pas d'itinéraire du tout. D'où son test.
     *
     * `alternativeidx=0` demande le meilleur trajet et non l'une de ses variantes ; `format=geojson` la
     * seule forme qui porte à la fois le total et la géométrie (le GPX ne rendrait pas les totaux).
     *
     * Les coordonnées passent par `toString()`, insensible à la locale, et non par `format()`, qui
     * écrirait une virgule décimale en français - ici elle séparerait en plus les deux valeurs.
     */
    internal fun routeUrl(base: String, points: List<Pair<Double, Double>>, profileId: String): String {
        val lonlats = points.joinToString("|") { (lat, lon) -> "$lon,$lat" }
        val q = "lonlats=" + URLEncoder.encode(lonlats, "UTF-8") +
            "&profile=" + URLEncoder.encode(profileId, "UTF-8") +
            "&alternativeidx=0&format=geojson"
        val sep = if ('?' in base) '&' else '?'
        return base.trimEnd('&', '?') + sep + q
    }

    /**
     * Lit le total et le tracé. Null si la réponse ne porte pas de total.
     *
     * Les deux totaux arrivent en **chaînes** et non en nombres (`"track-length": "7079"`), et en mètres et
     * secondes - pas de conversion, contrairement aux kilomètres de Valhalla.
     *
     * Un point sans troisième valeur garde une altitude nulle plutôt qu'un zéro : zéro est une altitude
     * valide, et le niveau de la mer en travers d'un profil de montagne se lit comme un gouffre.
     */
    fun parse(body: String): RouteResult? = runCatching {
        val f = json.decodeFromString<Response>(body).features.firstOrNull() ?: return@runCatching null
        val metres = f.properties?.trackLength?.toDoubleOrNull()
        val secondes = f.properties?.totalTime?.toDoubleOrNull()
        if (metres == null || secondes == null) return@runCatching null
        val points = f.geometry?.coordinates.orEmpty().mapNotNull { c ->
            if (c.size < 2) null else TrackPoint(lon = c[0], lat = c[1], ele = c.getOrNull(2))
        }
        RouteResult(metres, secondes, points)
    }.getOrNull()

    /**
     * Dépose [text] et rend son identifiant, null si le service n'en veut pas.
     *
     * [force] rejette l'identifiant gardé en mémoire et en redemande un. C'est la réponse au seul mode de
     * panne propre à ce moteur : un identifiant que le service a oublié - redémarrage, ménage - pour
     * lequel il répond une erreur nue, indiscernable d'un trajet impossible.
     */
    private fun profileId(base: String, text: String, force: Boolean): String? {
        if (!force) ids[text]?.let { return it }
        val resp = TileHttp.post(
            profileUrl(base), text.toByteArray(), "text/plain", DEPOSIT_TIMEOUT_MS, DEPOSIT_TIMEOUT_MS)
        val id = resp.body?.let {
            runCatching { json.decodeFromString<Deposit>(it.toString(Charsets.UTF_8)).profileId }.getOrNull()
        } ?: return null
        ids[text] = id
        return id
    }

    /**
     * Calcule l'itinéraire passant par [points], en (lat, lon), avec le profil [profileText] réglé pour
     * [profile] et [prefs] (cf. [BrouterProfile]). Null quand il n'y en a pas, pour les mêmes raisons que
     * chez Valhalla - étapes non reliées, service muet, réseau absent - et pour une de plus, propre à ce
     * moteur : le service a refusé le profil.
     *
     * **Une seconde tentative, et une seule, quand la première N'A PAS ABOUTI** - délai dépassé, liaison
     * coupée, pas de réseau (cf. [Attempt.transportFailed]). Elle redépose le profil au passage, ce qui
     * couvre du même geste le second mode de panne : un identifiant que le service a oublié, pour lequel
     * il répond une erreur nue.
     *
     * **Ce qui NE se réessaie pas : un service qui a répondu.** BRouter dit "pas d'itinéraire" par une
     * réponse en bonne et due forme, et redemander la même chose rendrait la même réponse - deux fois
     * l'attente pour le même non.
     *
     * La règle était l'inverse, et c'est le défaut que ceci corrige : la seconde tentative n'était offerte
     * qu'à un profil DÉJÀ déposé, c'est-à-dire jamais au premier calcul. Un long trajet expirait donc sans
     * recours, et rebasculer de discipline en discipline finissait par revenir sur un profil déjà en
     * mémoire, qui lui avait droit au second essai. C'est très exactement ce que décrivait le signalement :
     * "je bascule entre gravel et VTC et j'en trouve un".
     */
    suspend fun route(
        base: String, points: List<Pair<Double, Double>>, profile: RoutingProfile,
        prefs: RoutingPrefs, profileText: String,
    ): RouteResult? = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext null
        val texte = BrouterProfile.tune(profileText, profile, prefs)
        // Le profil etait-il DEJA depose avant cet appel : un identifiant neuf ne peut pas etre perime.
        val depose = ids.containsKey(texte)
        val delai = RouteTimeout.msFor(points)
        val id = profileId(base, texte, force = false) ?: return@withContext null
        val premier = fetch(base, points, id, delai)
        premier.result?.let { return@withContext it }
        // Le service a REPONDU, sous un identifiant qu'il vient de rendre : sa reponse est un vrai refus.
        if (!premier.transportFailed && !depose) return@withContext null
        val neuf = profileId(base, texte, force = true) ?: return@withContext null
        fetch(base, points, neuf, delai).result
    }

    /**
     * Ce qu'une tentative rend, et **pourquoi elle a echoue** quand elle echoue.
     *
     * [transportFailed] : la requete n'a pas abouti du tout - delai depasse, liaison coupee, pas de reseau
     * (cf. `TileHttp.Response.status`, nul dans ce cas). A distinguer d'un service qui repond qu'il n'y a
     * pas d'itineraire : les deux donnent un [result] nul, et un seul des deux merite qu'on redemande.
     */
    private class Attempt(val result: RouteResult?, val transportFailed: Boolean)

    private fun fetch(
        base: String, points: List<Pair<Double, Double>>, id: String, timeoutMs: Int,
    ): Attempt {
        val resp = TileHttp.fetch(routeUrl(base, points, id), timeoutMs, timeoutMs)
        return Attempt(resp.body?.let { parse(it.toString(Charsets.UTF_8)) }, resp.status == 0)
    }

    @Serializable internal data class Deposit(@SerialName("profileid") val profileId: String? = null)
    @Serializable internal data class Response(val features: List<Feature> = emptyList())
    @Serializable internal data class Feature(
        val properties: Props? = null,
        val geometry: Geometry? = null,
    )
    @Serializable internal data class Props(
        @SerialName("track-length") val trackLength: String? = null,
        @SerialName("total-time") val totalTime: String? = null,
    )
    @Serializable internal data class Geometry(val coordinates: List<List<Double>> = emptyList())
}
