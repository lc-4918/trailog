package fr.lc4918.trailog.poi

import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.map.offline.TileHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
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
 * France elle ne rend rien. En France même, elle ignore largement ce qui sert sur le terrain - mesuré
 * autour de Grenoble, à l'échelle d'un écran de carte : **zéro** point d'eau, zéro toilettes publiques,
 * zéro aire de pique-nique, zéro borne de recharge, quatre loueurs de vélos ; OSM y porte respectivement
 * 129, 46, 25, 165 et 50 objets. Sur le centre d'Albi, 6 restaurants contre 150 (cf. [PoiSources]).
 *
 * **On l'interroge par cellule de la grille** (cf. [PoiCells]), jamais sur l'emprise de l'écran : une
 * petite emprise fixe tient toujours sous les limites du service, se garde, et se rend à l'identique à
 * chaque passage. La requête ne porte donc **aucun plafond** d'objets. Le plafond d'avant ne protégeait de
 * rien : Overpass trie par type puis par identifiant et coupe à la fin, si bien qu'une réponse tronquée
 * n'était pas un échantillon mais le début de la liste - relevé sur Toulouse, 600 noeuds rendus et pas un
 * seul des restaurants dessinés en bâtiment.
 *
 * **Deux choix de forme** :
 * - les sélecteurs sont regroupés par clé en une expression régulière (`amenity~"^(bar|cafe|pub)$"`),
 *   ce qui tient la requête en une dizaine d'instructions au lieu d'une soixantaine ;
 * - `nwr` interroge d'un coup noeuds, chemins et relations, et `out tags center` rend le centre d'une
 *   surface : un camping ou un musée est souvent dessiné comme un contour.
 *
 * Un échec se distingue d'une zone vide ([Fetched.failed]) : une cellule en échec n'est pas retenue, et
 * sera redemandée.
 */
object Overpass {

    /**
     * L'instance par defaut : celle d'OpenStreetMap France, et c'est une correction.
     *
     * L'instance historique, `overpass-api.de`, etait la seule interrogee, et elle etait devenue
     * injoignable - depuis le telephone comme depuis un poste fixe, la liaison ne s'ouvrait meme pas. Les
     * instances de secours prenaient le relais au bout de la cascade, et mettaient chacune de douze
     * secondes a plus d'une minute pour une seule requete, quand elles repondaient. Le groupe "Manger"
     * n'arrivait jamais, et hors de France - ou OSM sert tout - la couche restait vide.
     *
     * Mesure le meme jour, meme requete (la restauration du centre de Logrono) :
     *
     * | Instance | Temps |
     * |---|---|
     * | overpass.openstreetmap.fr | 0,6 s |
     * | maps.mail.ru | 11,8 s |
     * | overpass.private.coffee | 48 a 56 s |
     * | overpass.kumi.systems | rien en 60 s |
     * | overpass-api.de | liaison refusee |
     *
     * L'instance francaise couvre le monde entier, et non la seule France : la meme requete sur Logrono,
     * Madrid ou Berlin y repond en une a sept secondes pour les cellules les plus denses.
     */
    const val DEFAULT_URL = "https://overpass.openstreetmap.fr/api/interpreter"

    /**
     * L'ancienne instance par defaut. Un reglage qui la nomme a ete ecrit par l'ecran quand elle etait le
     * defaut, et non par un choix : il suit le nouveau defaut, repli compris.
     */
    private const val LEGACY_DEFAULT_URL = "https://overpass-api.de/api/interpreter"

    /**
     * Les instances de SECOURS, essayees dans l'ordre quand celle d'origine n'a pas repondu.
     *
     * Elles ne sont essayees que si l'on interroge l'instance PAR DEFAUT. Qui vise sa propre instance a
     * une raison de le faire - un reseau ferme, une base a soi -, et l'envoyer en cachette chez des tiers
     * trahirait ce choix.
     */
    private val MIRRORS = listOf(
        LEGACY_DEFAULT_URL,
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )

    /** Délai annoncé au serveur, en secondes : c'est lui qui abandonne, plutôt que nous. Une cellule de
     *  centre-ville, la plus lourde, met sept secondes sur l'instance par défaut. */
    private const val QUERY_TIMEOUT_S = 40

    /** Délai de LECTURE : celui du serveur, plus de quoi rendre son propre abandon. */
    private const val READ_TIMEOUT_MS = 45_000

    /**
     * Délai d'ÉTABLISSEMENT de la liaison, court : une instance injoignable ne se distingue autrement
     * d'une instance lente qu'au bout du délai de lecture. Une liaison qui ne s'ouvre pas en six secondes
     * ne s'ouvrira pas.
     */
    private const val CONNECT_TIMEOUT_MS = 6_000

    /**
     * La dernière instance qui a RÉPONDU, essayée en tête la fois suivante : quand l'instance par défaut
     * flanche, chaque cellule repayait sinon la cascade entière pour finir au même endroit.
     */
    @Volatile private var derniereQuiRepond: String? = null

    private val json = Json { ignoreUnknownKeys = true }

    /** La marque d'un abandon du serveur dans une reponse 200 (cf. [fetch]). */
    private val ABANDON = Regex("\"remark\"\\s*:\\s*\"runtime error")

    /** La cible est-elle l'instance par defaut - et donc le repli permis (cf. [MIRRORS]). */
    internal fun isDefault(base: String): Boolean =
        base.isBlank() || base.trim() == DEFAULT_URL || base.trim() == LEGACY_DEFAULT_URL

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
        // `out tags center` et non `out center` : le second joint a chaque chemin la LISTE DE SES NOEUDS,
        // une donnee dont on ne fait rien. Et aucun plafond : la cellule est assez petite pour tenir.
        return "[out:json][timeout:$QUERY_TIMEOUT_S];(${corps.joinToString("")});out tags center;"
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
        // La categorie est intrinseque, le filtre ne fait qu'ecarter (cf. PoiCategory.ofOsm).
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

    /** Ce qu'une requete a rendu, ou le constat qu'aucune instance n'a repondu. */
    data class Fetched(val pois: List<Poi>, val failed: Boolean)

    /**
     * Les instances a essayer, dans l'ordre : celle reglee seule si l'utilisateur en a choisi une ; sinon
     * la derniere qui a repondu, l'instance par defaut, puis les secours.
     */
    internal fun cascade(base: String, derniere: String? = derniereQuiRepond): List<String> {
        if (!isDefault(base)) return listOf(base.trim())
        val ordre = listOf(DEFAULT_URL) + MIRRORS
        return if (derniere != null && derniere in ordre) listOf(derniere) + (ordre - derniere) else ordre
    }

    /**
     * Charge les points d'intérêt d'une emprise - une cellule de la grille.
     *
     * En POST et non en GET : la requête dépasse couramment le millier de caractères, et une URL de cette
     * longueur se fait tronquer par les intermédiaires.
     *
     * Une tentative par instance, sans pause : un refus d'une instance ne dit rien de la suivante, et
     * attendre avant de s'adresser ailleurs ne ferait que retarder la carte.
     */
    suspend fun fetch(base: String, box: Bbox, categories: Set<PoiCategory>): Fetched =
        withContext(Dispatchers.IO) {
            // Rien a demander n'est pas un echec : c'est une reponse vide, et une reponse vide est une reponse.
            val ql = query(box, categories) ?: return@withContext Fetched(emptyList(), failed = false)
            val corps = ("data=" + URLEncoder.encode(ql, "UTF-8")).toByteArray(Charsets.UTF_8)
            for (cible in cascade(base)) {
                /*
                 * `runInterruptible` et non un appel direct : `HttpURLConnection` bloque dans un thread
                 * d'E/S ne s'apercoit pas d'une annulation. Ici, l'annulation interrompt le thread, la
                 * lecture leve, et la requete s'arrete pour de bon.
                 */
                coroutineContext.ensureActive()
                val resp = runInterruptible {
                    TileHttp.post(
                        cible, corps,
                        contentType = "application/x-www-form-urlencoded; charset=utf-8",
                        connectTimeoutMs = CONNECT_TIMEOUT_MS,
                        readTimeoutMs = READ_TIMEOUT_MS,
                    )
                }
                val texte = resp.body?.toString(Charsets.UTF_8) ?: continue
                // Un 200 peut porter un abandon du serveur - "runtime error: Query timed out" - dans un
                // corps sans liste d'elements : ce n'est pas une zone vide, c'est un echec.
                if (!texte.contains("\"elements\"") || ABANDON.containsMatchIn(texte)) continue
                derniereQuiRepond = cible
                return@withContext Fetched(parse(texte, categories), failed = false)
            }
            // Aucune instance n'a repondu. La zone peut etre reellement vide, mais on n'en sait rien, et
            // la cellule ne doit pas etre retenue comme chargee.
            Fetched(emptyList(), failed = true)
        }
}
