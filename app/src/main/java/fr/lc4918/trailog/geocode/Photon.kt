package fr.lc4918.trailog.geocode

import fr.lc4918.trailog.map.offline.TileHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/** Un lieu proposé par l'autocomplétion : ce qu'on affiche, et où c'est. */
data class GeocodePlace(val label: String, val lon: Double, val lat: Double)

/**
 * Client du géocodeur **Photon** (komoot), le seul géocodeur OSM à la fois conçu pour l'autocomplétion au
 * clavier et auto-hébergeable. L'instance publique sert de défaut ; l'URL est un réglage (Carte /
 * Géocodage) pour viser sa propre instance sans attendre une nouvelle version de l'application.
 *
 * Nominatim est écarté malgré sa notoriété : sa politique d'usage interdit explicitement les requêtes
 * envoyées à chaque frappe. Google Places l'est aussi : clé facturée, et des CGU qui réservent l'affichage
 * des résultats à une carte Google, là où l'application rend ses cartes avec MapLibre.
 *
 * Le découpage (construction d'URL et lecture de la réponse d'un côté, appel réseau de l'autre) tient à ce
 * que les deux premiers soient les seuls endroits où une faute est silencieuse : ils sont testés sans réseau.
 */
object Photon {
    const val DEFAULT_URL = "https://photon.komoot.io/api"

    /** Langues que Photon sait rendre. Toute autre retombe sur l'anglais : le paramètre est rejeté par le
     *  service (400) et non ignoré, une locale exotique rendrait donc la recherche muette. */
    private val SUPPORTED_LANGS = setOf("de", "en", "fr", "it")

    /** Délais courts : la frappe suivante rend la requête caduque, mieux vaut abandonner tôt. */
    private const val TIMEOUT_MS = 8_000

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * URL de requête.
     *
     * Volontairement sans les paramètres `lat`/`lon` que Photon accepte : ils réordonnent les résultats par
     * proximité, ce qui fait remonter un hameau voisin devant la ville que l'on cherchait. Sans eux, le
     * classement reste celui de l'importance OSM, qui met la ville en tête.
     */
    fun url(base: String, query: String, lang: String, limit: Int): String {
        val sep = if ('?' in base) '&' else '?'
        val l = if (lang in SUPPORTED_LANGS) lang else "en"
        val sb = StringBuilder(base.trimEnd('&', '?'))
        sb.append(sep).append("q=").append(URLEncoder.encode(query, "UTF-8"))
        sb.append("&limit=").append(limit).append("&lang=").append(l)
        return sb.toString()
    }

    /**
     * L'URL sort-elle du réseau local ? Vrai pour l'instance publique et pour tout hôte routable, faux pour
     * une instance auto-hébergée à la maison.
     *
     * Sert à ne prévenir de l'absence de connexion que lorsqu'elle empêche vraiment la recherche : un
     * téléphone en wifi sans accès Internet atteint encore le Photon du NAS, refuser la recherche y serait
     * faux. Une URL illisible est comptée comme externe : c'est le cas courant, et prévenir à tort vaut
     * mieux que laisser une recherche échouer sans explication.
     */
    fun needsInternet(base: String): Boolean {
        val host = runCatching { java.net.URI(base.trim()).host }.getOrNull()?.lowercase()
            ?: return true
        if (host == "localhost" || host == "::1" || host.endsWith(".local")) return false
        if ('.' !in host && ':' !in host) return false      // nom de machine seul, resolu sur le reseau local
        val o = host.split('.').mapNotNull { it.toIntOrNull() }
        if (o.size != 4) return true                        // nom de domaine : routable
        return when {
            o[0] == 127 -> false                            // boucle locale
            o[0] == 10 -> false                             // 10.0.0.0/8
            o[0] == 192 && o[1] == 168 -> false             // 192.168.0.0/16
            o[0] == 172 && o[1] in 16..31 -> false          // 172.16.0.0/12
            o[0] == 169 && o[1] == 254 -> false             // lien-local
            else -> true
        }
    }

    /** Lit la réponse GeoJSON. Une entrée sans coordonnées exploitables est ignorée plutôt que de faire
     *  échouer toute la liste : Photon renvoie occasionnellement des géométries non ponctuelles. */
    fun parse(body: String): List<GeocodePlace> = runCatching {
        json.decodeFromString<FeatureCollection>(body).features.mapNotNull { f ->
            val c = f.geometry?.coordinates ?: return@mapNotNull null
            if (c.size < 2) return@mapNotNull null
            val lon = c[0]; val lat = c[1]
            // Repli sur les coordonnees quand l'entree n'a aucun libelle. Point decimal impose : la virgule
            // d'une locale francaise separerait a la fois les decimales et les deux valeurs ("44,56, 6,08").
            GeocodePlace(
                label(f.properties).ifBlank { "%.5f, %.5f".format(java.util.Locale.US, lat, lon) },
                lon, lat,
            )
        }
    }.getOrDefault(emptyList())

    /**
     * Adresse d'une entrée, sur une ligne : intitulé, voie, code postal et commune, pays.
     *
     * Le nom est omis quand il répète la commune (une recherche de ville renvoie name = "Grenoble" ET
     * city = "Grenoble", ce qui donnerait "Grenoble, 38000 Grenoble"). À défaut de commune, on retombe sur
     * le département : les lieux-dits et sommets n'en portent pas, et l'entrée se réduirait au seul pays.
     */
    internal fun label(p: Props?): String {
        if (p == null) return ""
        val street = listOfNotNull(p.housenumber, p.street).joinToString(" ").ifBlank { null }
        val town = p.city ?: p.county ?: p.state
        val townLine = listOfNotNull(p.postcode, town).joinToString(" ").ifBlank { null }
        return buildList {
            p.name?.takeIf { it != town }?.let { add(it) }
            street?.let { add(it) }
            townLine?.let { add(it) }
            p.country?.let { add(it) }
        }.joinToString(", ")
    }

    /**
     * Interroge le service. Renvoie une liste vide aussi bien quand il n'y a rien à trouver que lorsque le
     * réseau manque : l'appelant n'a rien à en faire de différent, et l'application est utilisable hors
     * ligne, où l'absence de résultat est le fonctionnement normal.
     *
     * Réutilise le client HTTP des tuiles (mêmes en-têtes, mêmes garde-fous) plutôt que d'en ouvrir un second.
     */
    suspend fun search(
        base: String, query: String, lang: String, limit: Int,
    ): List<GeocodePlace> = withContext(Dispatchers.IO) {
        val resp = TileHttp.fetch(url(base, query, lang, limit), TIMEOUT_MS, TIMEOUT_MS)
        val body = resp.body ?: return@withContext emptyList()
        parse(body.toString(Charsets.UTF_8))
    }

    @Serializable internal data class FeatureCollection(val features: List<Feature> = emptyList())
    @Serializable internal data class Feature(val geometry: Geometry? = null, val properties: Props? = null)
    @Serializable internal data class Geometry(val coordinates: List<Double>? = null)

    @Serializable internal data class Props(
        val name: String? = null,
        val housenumber: String? = null,
        val street: String? = null,
        val postcode: String? = null,
        val city: String? = null,
        val county: String? = null,
        val state: String? = null,
        val country: String? = null,
    )
}
