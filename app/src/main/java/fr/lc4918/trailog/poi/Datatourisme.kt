package fr.lc4918.trailog.poi

import fr.lc4918.trailog.data.seed.DATATOURISME_API_KEY
import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.map.offline.TileHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * Un point d'intérêt posé sur la carte : où il est, ce qu'il est, et de quoi remplir son infobulle.
 *
 * [webUrl] est nullable et le reste jusqu'en haut : le nom de l'infobulle n'est cliquable que si le lieu
 * publie un site, faute de quoi le tap ne ferait rien - un bouton muet est pire que pas de bouton.
 */
data class Poi(
    val uuid: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val category: PoiCategory,
    val city: String? = null,
    val imageUrl: String? = null,
    val webUrl: String? = null,
    val bikeTheme: Boolean = false,
)

/**
 * Client de l'API **DATAtourisme**, la base publique des points d'intérêt touristiques français
 * (Licence Ouverte Etalab 2.0, cf. l'écran À propos pour l'attribution).
 *
 * Même découpage que le géocodeur et les moteurs d'itinéraire - construction d'URL et lecture de la
 * réponse d'un côté, appel réseau de l'autre - pour la même raison : ce sont les deux seuls endroits où
 * une faute est silencieuse, et ils se testent alors sans réseau.
 *
 * **Ce que la découverte a établi**, et qui n'était garanti nulle part (chaque point a été vérifié par un
 * appel réel avant d'être écrit ici) :
 * - `filters` accepte des conditions combinées par ` AND `, et les valeurs d'un `[in]` se séparent par des
 *   virgules : `type[in]=Hotel,Camping AND hasTheme.key[eq]=Bike`. Un `&` ou une virgule entre conditions
 *   rend une erreur 400.
 * - le chemin d'un thème est `hasTheme.key`, et non `hasTheme` : ce dernier rend zéro résultat sans
 *   protester, ce qui est exactement la faute muette que ce fichier cherche à éviter.
 * - `fields` est nécessaire pour obtenir `hasTheme` et l'image : sans lui, la liste ne rend ni l'un ni
 *   l'autre, quand le détail d'un POI les porte. Les demander coûte moins qu'un second appel par marqueur.
 * - `geo_bounding` s'écrit `haut,gauche,bas,droite`, soit lat max, lon min, lat min, lon max.
 *
 * **Le label Accueil Vélo n'existe pas** dans cette API : le thésaurus `Label` est vide (0 valeur), et ni
 * `Theme` (758 valeurs) ni `Amenity` (312) n'en portent l'équivalent. Le rattachement à une véloroute
 * n'est pas exploitable non plus : les 212 thèmes `BikeRoute*` ne sont portés que par les tronçons
 * d'itinéraire eux-mêmes - zéro hébergement en France entière. Le seul signal vélo que portent les
 * services est le thème générique [BIKE_THEME] (790 hébergements, 7301 POIs au total), et c'est lui que
 * l'application propose sous le nom de "thème vélo".
 */
object Datatourisme {
    const val DEFAULT_URL = "https://api.datatourisme.fr/v1"

    /** Le seul thème vélo que portent les services eux-mêmes (cf. la note de découverte ci-dessus). */
    const val BIKE_THEME = "Bike"

    /**
     * Champs demandés. Sans eux la réponse n'aurait ni le thème ni l'image ; avec eux, elle n'a pas les
     * dizaines d'autres champs du modèle sémantique, dont l'infobulle n'a que faire.
     */
    private const val FIELDS = "uuid,label,type,isLocatedAt,hasTheme,hasMainRepresentation,hasContact"

    /**
     * Le maximum que l'API accorde.
     *
     * Il était de 100, choisi pour la lisibilité de la carte - et c'était une **perte silencieuse** : sur
     * un écran de carte autour de Souillac, le service connaît **149 lieux**, l'application n'en demandait
     * que 100, et les 49 autres étaient écartés dans un ordre que rien ne fixe. D'un déplacement de carte au
     * suivant, ce n'étaient pas les mêmes : un loueur de canoës s'affichait, puis disparaissait, sans que
     * rien ne l'explique. Relevé sur le terrain, et c'est le pire genre de faute - la carte avait l'air
     * juste.
     *
     * Au-delà de ce plafond, on ne se tait plus : la carte annonce qu'elle ne montre pas tout
     * (cf. `PoiState.partial`).
     */
    const val PAGE_SIZE = 250

    private const val TIMEOUT_MS = 15_000

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * URL du catalogue pour la zone visible, restreinte aux catégories [categories].
     *
     * [bikeOnly] ajoute le thème vélo à la requête plutôt que de trier la réponse : le tri côté client
     * rapatrierait des centaines de lieux pour n'en garder que quelques-uns.
     *
     * Rend null quand aucune catégorie n'est retenue - il n'y a alors rien à demander, et une requête sans
     * `type` rapatrierait le catalogue entier de la zone.
     */
    fun catalogUrl(
        base: String, north: Double, west: Double, south: Double, east: Double,
        categories: Set<PoiCategory>, bikeOnly: Boolean = false, pageSize: Int = PAGE_SIZE,
    ): String? {
        val classes = categories.flatMap { it.classes }.distinct().sorted()
        if (classes.isEmpty()) return null
        val conditions = buildList {
            add("type[in]=" + classes.joinToString(","))
            if (bikeOnly) add("hasTheme.key[eq]=$BIKE_THEME")
        }
        val q = listOf(
            "geo_bounding" to "$north,$west,$south,$east",
            "filters" to conditions.joinToString(" AND "),
            "fields" to FIELDS,
            "lang" to "fr",
            "page_size" to pageSize.toString(),
        ).joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        val sep = if ('?' in base) '&' else '?'
        return base.trimEnd('&', '?', '/') + "/catalog" + sep + q
    }

    /**
     * Lit les points d'intérêt d'une réponse, en ne gardant que ceux qu'on sait placer et nommer.
     *
     * [retenues] tranche l'ambiguïté d'un lieu qui porte plusieurs classes : un hôtel-restaurant est
     * `Hotel` ET `Restaurant`, et doit s'afficher sous la catégorie que l'utilisateur a demandée.
     *
     * Un POI sans coordonnées, sans nom ou sans catégorie connue est écarté silencieusement : le service
     * rend un catalogue vivant, et un enregistrement incomplet ne doit pas priver la carte des autres.
     */
    fun parse(body: String, retenues: Set<PoiCategory> = PoiCategory.entries.toSet()): List<Poi> =
        runCatching {
            val racine = json.parseToJsonElement(body).jsonObject
            racine["objects"]?.jsonArray.orEmpty().mapNotNull { poiOf(it, retenues) }
        }.getOrDefault(emptyList())

    private fun poiOf(e: JsonElement, retenues: Set<PoiCategory>): Poi? = runCatching {
        val o = e.jsonObject
        val geo = o["isLocatedAt"]?.premier()?.get("geo")?.premier()
        val lat = geo?.get("latitude")?.nombre() ?: return null
        val lon = geo["longitude"]?.nombre() ?: return null
        val classes = o["type"]?.jsonArray.orEmpty().mapNotNull { it.texte() }
        // La categorie est celle du lieu, pas celle qu'on a demandee : un hotel-restaurant est un hotel,
        // et il ne s'affiche pas si les hotels sont masques (cf. PoiCategory.of).
        val categorie = PoiCategory.visibleDans(PoiCategory.of(classes), retenues) ?: return null
        val nom = o["label"]?.jsonObject?.get("@fr")?.texte()
            ?: o["label"]?.jsonObject?.values?.firstOrNull()?.texte() ?: return null
        val themes = o["hasTheme"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["key"]?.texte() }
        Poi(
            uuid = o["uuid"]?.texte() ?: return null,
            label = nom,
            lat = lat,
            lon = lon,
            category = categorie,
            // `address` arrive tantot en objet, tantot en TABLEAU d'un objet, d'ou le meme lecteur
            // tolerant que partout ailleurs : lu en dur, il levait, et l'exception emportait le POI
            // entier - la carte restait vide sans que rien ne le dise.
            city = o["isLocatedAt"]?.premier()?.get("address")?.premier()
                ?.get("addressLocality")?.texte(),
            // Chemins relevés sur des réponses réelles, et non déduits du modèle sémantique : l'image
            // est trois niveaux plus bas que son champ, et sans le préfixe "ebucore:" que le modèle
            // laisse attendre. Environ un POI sur trois en porte une, d'où le visuel de repli.
            imageUrl = o["hasMainRepresentation"]?.premier()
                ?.get("hasRelatedResource")?.premier()
                ?.get("locator")?.texteOuPremier(),
            webUrl = o["hasContact"]?.premier()?.get("homepage")?.texteOuPremier(),
            bikeTheme = BIKE_THEME in themes,
        )
    }.getOrNull()

    /** Le modèle sémantique rend tantôt un objet, tantôt un tableau d'un seul objet : les deux se lisent
     *  pareil ici, faute de quoi la moitié des champs serait perdue selon l'humeur de la source. */
    private fun JsonElement.premier(): JsonObject? = runCatching {
        if (this is kotlinx.serialization.json.JsonArray) this.firstOrNull()?.jsonObject else jsonObject
    }.getOrNull()

    private fun JsonElement.texte(): String? = runCatching { jsonPrimitive.content }.getOrNull()
    private fun JsonElement.nombre(): Double? = runCatching { jsonPrimitive.content.toDouble() }.getOrNull()
    private fun JsonElement.texteOuPremier(): String? =
        texte() ?: runCatching { jsonArray.firstOrNull()?.texte() }.getOrNull()

    /**
     * Le nombre total de lieux que le service connaît pour cette requête, indépendamment de la page rendue.
     *
     * **Il est dans la réponse, et personne ne le lisait.** La troncature se devinait sur le nombre de POI
     * RETENUS après filtrage (`taille >= PAGE_SIZE`), ce qui est une approximation par le bas : une réponse
     * de 250 objets dont 40 sont écartés faute de catégorie connue en laisse 210, et passait donc pour
     * complète. L'emprise était alors retenue comme chargée, et les lieux manquants ne revenaient jamais.
     * `meta.total` tranche exactement.
     */
    internal fun total(body: String): Int? = runCatching {
        json.parseToJsonElement(body).jsonObject["meta"]?.jsonObject?.get("total")?.nombre()?.toInt()
    }.getOrNull()

    /**
     * Ce qu'un chargement a rendu, et ce qu'il en manque.
     *
     * [echec] distingue le service muet de la zone déserte. Les deux rendaient une liste vide, et un 504
     * passait donc pour "il n'y a rien ici" : l'emprise était retenue comme chargée, et il fallait s'en
     * aller et revenir pour que les lieux réapparaissent.
     */
    data class Catalog(
        val pois: List<Poi>,
        val tronque: Boolean = false,
        val echec: Boolean = false,
    )

    /**
     * Charge les points d'intérêt de la zone visible.
     *
     * Trois issues distinctes, là où il n'y en avait qu'une : ce qu'on a trouvé, ce qui manque au-delà du
     * plafond (cf. [total]), et le service qui n'a pas répondu (cf. [Catalog.echec]).
     */
    suspend fun catalog(
        base: String, north: Double, west: Double, south: Double, east: Double,
        categories: Set<PoiCategory>, bikeOnly: Boolean = false,
    ): Catalog = withContext(Dispatchers.IO) {
        val url = catalogUrl(base, north, west, south, east, categories, bikeOnly)
            ?: return@withContext Catalog(emptyList())
        val resp = TileHttp.fetch(url, TIMEOUT_MS, TIMEOUT_MS, mapOf("X-API-Key" to DATATOURISME_API_KEY))
        val corps = resp.body?.toString(Charsets.UTF_8)
            // Statut nul : la requete n'a pas abouti du tout (cf. TileHttp.Response). Un refus du service -
            // 4xx, 5xx - rend un corps nul aussi, et compte pareil : on ne sait pas ce qu'il y a ici.
            ?: return@withContext Catalog(emptyList(), echec = true)
        val lieux = parse(corps, categories)
        val connus = total(corps)
        Catalog(
            lieux,
            // Le total exact quand le service le donne ; le repli d'avant sinon, qui vaut mieux que rien.
            tronque = if (connus != null) connus > PAGE_SIZE else lieux.size >= PAGE_SIZE,
        )
    }
}
