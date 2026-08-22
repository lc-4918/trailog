package fr.lc4918.trailog.poi

import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.map.offline.TileHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * Client **Overpass**, l'interrogateur d'OpenStreetMap, la seconde source de points d'intérêt.
 *
 * **Pourquoi une seconde source.** DATAtourisme est la base publique du tourisme *français* : hors de
 * France elle ne rend rien, et la couche restait vide sans que rien ne l'explique. En France même, elle
 * ignore largement ce qui sert sur le terrain - mesuré autour de Grenoble, à l'échelle d'un écran de
 * carte : **zéro** point d'eau, zéro toilettes publiques, zéro aire de pique-nique, zéro borne de
 * recharge, quatre loueurs de vélos ; OSM y porte respectivement 129, 46, 25, 165 et 50 objets.
 *
 * Les **hébergements** sont comparables (49 hôtels contre 38), et c'est pour cela que DATAtourisme les
 * garde - avec ses photos, que l'infobulle montre. Cette mesure-là a longtemps servi à conclure que la
 * **restauration** l'était aussi : elle ne l'est pas, et personne ne l'avait mesurée. Sur le centre
 * d'Albi, 6 restaurants contre 150, et les 6 sont des hôtels (cf. [PoiSources]).
 *
 * **Le partage est donc géographique, et par groupe** (cf. [PoiRepository]) : hors de France, OSM répond
 * seul ; en France, il complète le groupe *pratique* - celui des services - et la *restauration*.
 *
 * **Ce que la requête a d'inhabituel.** Overpass parle son propre langage, pas une URL de paramètres. Deux
 * choix comptent :
 * - les sélecteurs sont **regroupés par clé en une expression régulière** (`amenity~"^(bar|cafe|pub)$"`),
 *   ce qui tient la requête en une dizaine d'instructions au lieu d'une soixantaine. Les deux formes ont
 *   été chronométrées côte à côte sans qu'aucune se détache : c'est la **densité de la zone** qui décide,
 *   pas la forme de la requête ;
 * - `nwr` interroge d'un coup noeuds, chemins et relations, et `out center` rend le centre d'une surface :
 *   un camping ou un musée est souvent dessiné comme un contour, et se demander seulement les noeuds
 *   revenait à ignorer les lieux les mieux cartographiés.
 *
 * **Ce que l'instance publique impose**, mesuré sur `overpass-api.de` :
 * - une requête de toutes les catégories sur une ville dense met **une trentaine de secondes**. D'où les
 *   délais généreux plus bas - trop courts, ils faisaient avorter la requête au moment précis où elle
 *   allait aboutir, et la couche restait vide hors de France ;
 * - **une requête sur deux** repart en 504 aux heures chargées, et ce refus arrive vite (8 à 13 secondes),
 *   sans rapport avec le poids de la requête. D'où la seconde tentative.
 *
 * Un échec ne se distingue toujours pas d'une zone vide : il rend une liste vide, et le dépôt se rabat sur
 * le cache, exactement comme pour l'autre source.
 */
object Overpass {

    const val DEFAULT_URL = "https://overpass-api.de/api/interpreter"

    /**
     * Délai annoncé au serveur, en secondes : c'est lui qui abandonne, plutôt que nous.
     *
     * Cinquante et non vingt-cinq, et c'est une correction : mesurée sur `overpass-api.de`, une requête de
     * toutes les catégories sur une ville dense met **une trentaine de secondes** à s'exécuter. Le délai
     * précédent la faisait donc avorter par le serveur lui-même - `"Query timed out after 26 seconds"` -,
     * et la couche restait vide hors de France, là où l'on demande toutes les catégories à la fois.
     */
    private const val QUERY_TIMEOUT_S = 50

    /** Délai de la liaison, plus large que celui de la requête : le serveur doit avoir le temps de rendre
     *  son propre abandon, qui vaut mieux qu'une coupure sans réponse. */
    private const val TIMEOUT_MS = 60_000

    /**
     * Attente avant la seconde tentative.
     *
     * L'instance publique refuse **une requête sur deux** aux heures chargées, et son refus arrive vite
     * (8 à 13 secondes, un 504 de passerelle, sans rapport avec le poids de la requête). Réessayer coûte
     * donc peu et change tout : deux échecs d'affilée sont bien plus rares qu'un seul.
     */
    private const val RETRY_DELAY_MS = 1_500L

    /**
     * Le même plafond que l'autre source, et relevé pour la même raison : sous ce plafond, les lieux
     * excédentaires étaient écartés dans un ordre que rien ne fixe, et disparaissaient d'un déplacement de
     * carte au suivant sans que rien ne l'explique (cf. [Datatourisme.PAGE_SIZE]).
     *
     * Il ne coûte pas le travail du serveur, seulement la taille de la réponse : Overpass ramasse de toute
     * façon tout ce qui correspond avant d'en rendre le nombre demandé.
     */
    const val LIMIT = 250

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * La requête Overpass QL pour une emprise et un jeu de catégories, ou null si aucune catégorie retenue
     * ne porte d'étiquette OSM - il n'y a alors rien à demander.
     *
     * L'emprise s'écrit `(sud,ouest,nord,est)`, l'ordre d'Overpass, et non celui de DATAtourisme : les deux
     * sources ne s'accordent pas là-dessus, et c'est le genre d'inversion qui rend une carte vide sans
     * lever d'erreur.
     */
    fun query(box: Bbox, categories: Set<PoiCategory>): String? {
        val selecteurs = categories.flatMap { it.osm }.distinct()
        if (selecteurs.isEmpty()) return null
        val emprise = "(${box.south},${box.west},${box.north},${box.east})"
        val simples = selecteurs.filter { ',' !in it }
            .groupBy({ it.substringBefore('=') }, { it.substringAfter('=') })
        val composes = selecteurs.filter { ',' in it }
        val corps = buildList {
            simples.toSortedMap().forEach { (cle, valeurs) ->
                val alternatives = valeurs.distinct().sorted().joinToString("|")
                add("nwr[\"$cle\"~\"^($alternatives)$\"]$emprise;")
            }
            composes.sorted().forEach { selecteur ->
                val paires = selecteur.split(',').joinToString("") { p ->
                    "[\"${p.substringBefore('=')}\"=\"${p.substringAfter('=')}\"]"
                }
                add("nwr$paires$emprise;")
            }
        }
        return "[out:json][timeout:$QUERY_TIMEOUT_S];(${corps.joinToString("")});out center $LIMIT;"
    }

    /**
     * Lit les points d'intérêt d'une réponse Overpass.
     *
     * Un objet sans catégorie connue est écarté ; un objet **sans nom**, lui, est gardé - c'est la
     * différence avec l'autre source. Une fontaine, des toilettes ou une aire de pique-nique n'ont
     * presque jamais de nom dans OSM, et ce sont justement les lieux qu'on cherche : l'infobulle affiche
     * alors le nom de la catégorie (cf. `PoiBubble`).
     *
     * L'identifiant porte le préfixe `osm:` et le type de l'objet : deux sources se partagent la table du
     * cache, et un numéro de noeud pourrait par ailleurs être celui d'un chemin.
     */
    fun parse(body: String, retenues: Set<PoiCategory> = PoiCategory.entries.toSet()): List<Poi> =
        runCatching {
            json.parseToJsonElement(body).jsonObject["elements"]?.jsonArray.orEmpty()
                .mapNotNull { poiOf(it.jsonObject, retenues) }
        }.getOrDefault(emptyList())

    private fun poiOf(o: JsonObject, retenues: Set<PoiCategory>): Poi? = runCatching {
        val etiquettes = o["tags"]?.jsonObject?.mapValues { (_, v) -> v.texte().orEmpty() } ?: return null
        // Meme regle que pour l'autre source : la categorie est intrinseque, le filtre ne fait
        // qu'ecarter (cf. PoiCategory.ofOsm).
        val categorie = PoiCategory.visibleDans(PoiCategory.ofOsm(etiquettes), retenues) ?: return null
        // Un noeud porte ses coordonnees ; une surface ou une relation rend le centre demande par
        // "out center". Sans ce repli, tout ce qui est dessine en contour serait perdu.
        val centre = o["center"]?.jsonObject
        val lat = (o["lat"] ?: centre?.get("lat"))?.nombre() ?: return null
        val lon = (o["lon"] ?: centre?.get("lon"))?.nombre() ?: return null
        val type = o["type"]?.texte() ?: "node"
        val id = o["id"]?.jsonPrimitive?.content ?: return null
        Poi(
            uuid = "osm:$type/$id",
            label = etiquettes["name"].orEmpty(),
            lat = lat,
            lon = lon,
            category = categorie,
            city = etiquettes["addr:city"],
            // `image` est parfois un lien vers une page et non vers un cliche : on ne garde que ce qui
            // ressemble a une adresse, et l'infobulle se passe d'illustration le reste du temps.
            imageUrl = etiquettes["image"]?.takeIf { it.startsWith("http") },
            webUrl = (etiquettes["website"] ?: etiquettes["contact:website"])?.takeIf { it.startsWith("http") },
            bikeTheme = false,
        )
    }.getOrNull()

    private fun kotlinx.serialization.json.JsonElement.texte(): String? =
        runCatching { jsonPrimitive.content }.getOrNull()

    private fun kotlinx.serialization.json.JsonElement.nombre(): Double? =
        runCatching { jsonPrimitive.content.toDouble() }.getOrNull()

    /**
     * Charge les points d'intérêt d'une emprise. Liste vide quand il n'y a rien à montrer ici, et **null
     * quand l'instance n'a pas répondu** - les deux ne se valent pas, et les confondre faisait passer un
     * refus pour une zone déserte (cf. [PoiRepository.load]).
     *
     * En POST et non en GET : la requête dépasse couramment le millier de caractères, et une URL de cette
     * longueur se fait tronquer par les intermédiaires.
     *
     * **Deux tentatives**, pour la raison dite plus haut : l'instance publique refuse une requête sur deux
     * aux heures chargées, et son refus arrive bien avant qu'elle n'ait travaillé.
     */
    suspend fun around(
        base: String, box: Bbox, categories: Set<PoiCategory>,
    ): List<Poi>? = withContext(Dispatchers.IO) {
        // Rien a demander n'est pas un echec : c'est une reponse vide, et une reponse vide est une reponse.
        val ql = query(box, categories) ?: return@withContext emptyList()
        val corps = ("data=" + URLEncoder.encode(ql, "UTF-8")).toByteArray(Charsets.UTF_8)
        val cible = base.ifBlank { DEFAULT_URL }
        var repondu = false
        repeat(2) { essai ->
            if (essai > 0) delay(RETRY_DELAY_MS)
            /*
             * `runInterruptible` et non un appel direct : un geste de carte de plus annule ce chargement,
             * mais `HttpURLConnection` bloque dans un thread d'E/S ne s'en apercoit pas. Les requetes
             * abandonnees continuaient donc de courir et de consommer les creneaux du service, au point de
             * faire refuser l'appelant - releve a Albi, quatre requetes en cours pour un seul geste utile.
             *
             * Ici, l'annulation interrompt le thread, la lecture leve, et la requete s'arrete pour de bon.
             * `ensureActive` evite en plus d'en lancer une seconde apres coup.
             */
            ensureActive()
            val resp = runInterruptible {
                TileHttp.post(
                    cible, corps,
                    contentType = "application/x-www-form-urlencoded; charset=utf-8",
                    connectTimeoutMs = TIMEOUT_MS, readTimeoutMs = TIMEOUT_MS,
                )
            }
            val corpsRecu = resp.body ?: return@repeat
            repondu = true
            val lieux = parse(corpsRecu.toString(Charsets.UTF_8), categories)
            if (lieux.isNotEmpty()) return@withContext lieux
        }
        // Null quand l'instance n'a jamais repondu - un 504, une coupure, un delai depasse. La zone peut
        // etre reellement vide, mais on n'en sait rien, et l'appelant ne doit pas retenir cette emprise
        // comme chargee : le groupe manquant ne serait plus jamais redemande (cf. PoiLoad.complete).
        if (repondu) emptyList() else null
    }
}
