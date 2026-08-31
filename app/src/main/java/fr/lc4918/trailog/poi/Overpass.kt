package fr.lc4918.trailog.poi

import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.map.offline.TileHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.delay
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
     * Le plafond d'une TUILE, au-delà duquel on la découpe plutôt que de rendre un échantillon.
     *
     * **Ce plafond ne protégeait de rien, il mutilait.** Relevé sur le centre de Toulouse, groupe
     * restauration : 1781 objets correspondent, on en demandait 250. Et le tri d'Overpass est par type puis
     * par identifiant - les 250 rendus étaient donc **tous des noeuds**, aucun des 114 chemins n'était
     * jamais atteint. Un restaurant cartographié comme bâtiment était structurellement invisible, et parmi
     * les noeuds seuls les plus anciens passaient.
     *
     * Le relever ne suffit pas : à 600, la même requête rend 600 noeuds et toujours **zéro chemin**. Il
     * faut passer SOUS le plafond pour que le tri cesse de couper, et c'est le découpage qui y mène
     * (cf. [around]). Mesuré sur les quatre quadrants de la même emprise : 173, 461, 167 objets - complets,
     * chemins compris - et un seul encore tronqué, celui du centre-ville.
     *
     * 600 plutôt que 250 pour que le découpage s'arrête vite : trois quadrants sur quatre y tiennent.
     */
    const val LIMIT = 600

    /**
     * Profondeur maximale du découpage : la tuile de départ, puis deux subdivisions.
     *
     * Au-delà, on rend ce qu'on a et l'on annonce un affichage incomplet : mieux vaut le dire que d'ouvrir
     * une descente sans fond sur un centre-ville, où chaque niveau quadruple le nombre de requêtes.
     */
    const val MAX_DEPTH = 2

    /**
     * Nombre maximal de requêtes par groupe et par chargement.
     *
     * Le découpage est adaptatif - une tuile n'est divisée que si elle déborde - mais un centre-ville dense
     * pourrait en demander vingt et une. Ce plafond-là borne la dépense envers un service public qui
     * n'accorde que deux créneaux par adresse ; ce qu'il coupe est annoncé comme incomplet.
     */
    const val MAX_TILES = 12

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
        // `out tags center` et non `out center` : le second est un `out body`, qui joint a chaque chemin la
        // LISTE DE SES NOEUDS - une donnee dont on ne fait rien, le centre suffisant a poser un marqueur.
        // Mesure a nombre d'elements egal sur une requete qui rend des chemins : 7 501 contre 6 279 octets.
        return "[out:json][timeout:$QUERY_TIMEOUT_S];(${corps.joinToString("")});out tags center $LIMIT;"
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
     * Ce qu'une tuile a rendu, et **pourquoi elle s'est arrêtée** quand elle s'arrête.
     *
     * [tronque] se lit sur le nombre d'éléments **bruts** de la réponse, et non sur les POI retenus après
     * filtrage : un objet écarté faute de catégorie connue compte tout autant dans ce que le plafond a
     * coupé. Le compter après filtrage faisait passer pour complète une réponse qui ne l'était pas, et
     * l'emprise était alors retenue avec ses lieux manquants.
     */
    internal data class Tuile(val pois: List<Poi>, val tronque: Boolean, val echec: Boolean)

    /**
     * Charge les points d'intérêt d'une emprise, **en la découpant tant qu'elle déborde**.
     *
     * **Pourquoi découper plutôt que relever le plafond.** Overpass rend ses résultats triés par type puis
     * par identifiant, et coupe à la fin : une réponse tronquée n'est donc pas un échantillon, c'est le
     * début de la liste. Relevé sur le centre de Toulouse, groupe restauration - 1781 objets présents,
     * plafond à 250 : les 250 rendus sont **tous des noeuds**, et pas un des 114 chemins. Porter le plafond
     * à 600 rend 600 noeuds et toujours aucun chemin. Seule une tuile qui tient **sous** le plafond rend
     * ce qu'elle contient vraiment.
     *
     * Le découpage est **adaptatif** : on ne divise que ce qui déborde. Sur la même emprise, trois
     * quadrants sur quatre tiennent d'un coup (173, 461 et 167 objets, chemins compris) et seul celui du
     * centre-ville demande un niveau de plus. Une zone rurale reste donc à une requête, comme avant.
     *
     * Borné par [MAX_DEPTH] et [MAX_TILES] : ce qui n'a pas pu être découpé assez finement est rendu quand
     * même, et signalé tronqué - la carte le dit alors, plutôt que de laisser croire qu'elle montre tout.
     */
    internal suspend fun tiles(
        base: String, box: Bbox, categories: Set<PoiCategory>,
    ): Tuile = withContext(Dispatchers.IO) {
        var restant = MAX_TILES
        val trouves = LinkedHashMap<String, Poi>()
        var tronqueFinal = false
        var echecFinal = false
        // Largeur d'abord : on épuise un niveau avant de descendre, de sorte qu'un budget serré rende une
        // couverture homogène plutôt qu'un seul coin très détaillé.
        var niveau = listOf(box)
        var profondeur = 0
        while (niveau.isNotEmpty() && restant > 0) {
            val aDecouper = mutableListOf<Bbox>()
            for (tuile in niveau) {
                if (restant <= 0) { tronqueFinal = true; break }
                coroutineContext.ensureActive()
                restant--
                val r = fetchTile(base, tuile, categories)
                r.pois.forEach { p -> trouves.merge(p.uuid, p) { a, b -> mieuxClasse(a, b) } }
                if (r.echec) echecFinal = true
                if (!r.tronque) continue
                // Trop dense pour cette tuile : on la coupe en quatre, sauf si l'on est au bout.
                if (profondeur < MAX_DEPTH) aDecouper += quadrants(tuile) else tronqueFinal = true
            }
            niveau = aDecouper
            profondeur++
        }
        if (niveau.isNotEmpty()) tronqueFinal = true
        Tuile(trouves.values.toList(), tronqueFinal, echecFinal)
    }

    /** Les quatre quadrants d'une emprise, dans l'ordre sud-ouest, sud-est, nord-ouest, nord-est. */
    internal fun quadrants(box: Bbox): List<Bbox> {
        val midLon = (box.west + box.east) / 2
        val midLat = (box.south + box.north) / 2
        return listOf(
            Bbox(west = box.west, south = box.south, east = midLon, north = midLat),
            Bbox(west = midLon, south = box.south, east = box.east, north = midLat),
            Bbox(west = box.west, south = midLat, east = midLon, north = box.north),
            Bbox(west = midLon, south = midLat, east = box.east, north = box.north),
        )
    }

    /**
     * Une requête, et une seule tuile.
     *
     * En POST et non en GET : la requête dépasse couramment le millier de caractères, et une URL de cette
     * longueur se fait tronquer par les intermédiaires.
     *
     * **Deux tentatives** : l'instance publique refuse une requête sur deux aux heures chargées, et son
     * refus arrive bien avant qu'elle n'ait travaillé.
     */
    private suspend fun fetchTile(base: String, box: Bbox, categories: Set<PoiCategory>): Tuile {
        // Rien a demander n'est pas un echec : c'est une reponse vide, et une reponse vide est une reponse.
        val ql = query(box, categories) ?: return Tuile(emptyList(), tronque = false, echec = false)
        val corps = ("data=" + URLEncoder.encode(ql, "UTF-8")).toByteArray(Charsets.UTF_8)
        val cible = base.ifBlank { DEFAULT_URL }
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
            coroutineContext.ensureActive()
            val resp = runInterruptible {
                TileHttp.post(
                    cible, corps,
                    contentType = "application/x-www-form-urlencoded; charset=utf-8",
                    connectTimeoutMs = TIMEOUT_MS, readTimeoutMs = TIMEOUT_MS,
                )
            }
            val corpsRecu = resp.body ?: return@repeat
            val brut = count(corpsRecu.toString(Charsets.UTF_8))
            return Tuile(
                parse(corpsRecu.toString(Charsets.UTF_8), categories),
                tronque = brut >= LIMIT,
                echec = false,
            )
        }
        // L'instance n'a jamais repondu - un 504, une coupure, un delai depasse. La zone peut etre reellement
        // vide, mais on n'en sait rien, et l'appelant ne doit pas retenir cette emprise comme chargee : le
        // groupe manquant ne serait plus jamais redemande (cf. PoiLoad.complete).
        return Tuile(emptyList(), tronque = false, echec = true)
    }

    /** Le nombre d'elements BRUTS de la reponse, avant tout filtrage : c'est lui que le plafond a coupe. */
    private fun count(body: String): Int = runCatching {
        json.parseToJsonElement(body).jsonObject["elements"]?.jsonArray?.size ?: 0
    }.getOrDefault(0)

}
