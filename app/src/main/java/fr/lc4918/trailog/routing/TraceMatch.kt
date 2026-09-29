package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.LayerWays
import fr.lc4918.trailog.domain.model.WaySegment
import fr.lc4918.trailog.map.offline.TileHttp
import fr.lc4918.trailog.poi.Overpass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Les voies qu'une trace ENREGISTREE a empruntees, pour en tirer les memes "Details" qu'un itineraire
 * calcule : surfaces et types de voies (cf. [fr.lc4918.trailog.domain.geo.RouteDetails]).
 *
 * Un itineraire BRouter porte ces attributs parce que le moteur sait sur quelles voies il passe ; une trace
 * importee n'est qu'une suite de points. Il faut donc d'abord la RECALER sur le reseau (map-matching), puis
 * demander les attributs des voies trouvees :
 * - **Valhalla** `/trace_attributes` recale, et rend dans l'ordre chaque arete suivie, sa longueur et
 *   l'identifiant OSM de sa voie. BRouter ne sait que calculer des itineraires : lui donner des etapes
 *   rapprochees le ferait couper par une autre rue, ou contourner ce qu'interdit son profil ;
 * - **Overpass** rend les attributs OSM de ces voies (`way(id:...)`). Valhalla rend aussi les siens, mais
 *   dans son propre vocabulaire, plus grossier - huit revetements, sans herbe, sable ni rocher. Ils ne
 *   servent qu'a defaut (cf. [fallbackTagsOf]).
 *
 * Le resultat est une liste de [WaySegment] : la forme que rend BRouter, et que tout l'affichage sait deja
 * lire.
 *
 * Mesure sur des traces reelles (instance publique de FOSSGIS, septembre 2026) : Soreze - Arfons, 49,46 km
 * de GPX, 49,45 km recales ; VTT noir de Rignac, 56,18 km pour 56,23 ; EV1 Nantes - Hendaye, 833 km en six
 * morceaux et une dizaine de secondes. Overpass rend les 109 voies de Soreze - Arfons en une demi-seconde.
 */
object TraceMatch {

    /**
     * Longueur maximale d'un morceau envoye au service, en metres.
     *
     * L'instance publique refuse au-dela de 200 km ("Path distance exceeds the max distance limit"), mesures
     * sur le trace RECALE, qui peut depasser celui du GPX : 150 km laissent la marge.
     */
    internal const val CHUNK_METERS = 150_000.0

    /** Nombre maximal de points d'un morceau. 12 800 points passent en 0,6 s ; la limite par defaut du
     *  service est de 16 000. */
    internal const val CHUNK_POINTS = 10_000

    /**
     * En deca de cette part de trace hors reseau, le recalage a pied est garde sans essayer le velo.
     *
     * **Pourquoi deux modeles de cout.** Le recalage ne suit que les voies ouvertes au modele demande, et
     * aucun ne convient a tout : a pied, EV1 laisse 44 km hors reseau sur 833 (le velo : 6) ; a velo, le
     * GR 3 Jura en laisse 9 sur 116 (a pied : 0,4). Chaque morceau est donc recale a pied, puis a velo
     * s'il reste trop de trace hors reseau, et garde le meilleur des deux.
     */
    internal const val GOOD_ENOUGH = 0.01

    private val COSTINGS = listOf("pedestrian", "bicycle")

    /** Les attributs OSM gardes : ceux que le classement lit (cf. RouteDetails.surfaceOf et wayOf). */
    internal val KEPT_TAGS = listOf("highway", "route", "surface", "tracktype", "bicycle")

    private val json = Json { ignoreUnknownKeys = true }

    /** Ce que rend le recalage d'une trace. */
    data class WayMatch(
        val segments: List<WaySegment>,
        /** La longueur de trace que le service n'a pu poser sur aucune voie : hors sentier, ou voie absente
         *  d'OSM. Elle figure dans [segments], sans attributs - donc "inconnue". */
        val offNetworkMeters: Double,
        /** Faux quand Overpass n'a pas repondu : les attributs sont alors ceux de Valhalla, plus grossiers. */
        val osmTags: Boolean,
    )

    sealed interface Outcome {
        data class Done(val match: WayMatch) : Outcome
        /** Le service a REPONDU, sans rien pouvoir recaler. */
        data object NoMatch : Outcome
        /** Rien n'a repondu : reseau absent, delai depasse. */
        data object Unreachable : Outcome
    }

    // ---------- Requete ----------

    /**
     * URL du recalage : le chemin frere `/trace_attributes` de celui du calcul (`/route`), comme BRouter a
     * son `/profile`. Une chaine de requete portee par l'URL de base est conservee derriere le chemin.
     */
    internal fun traceUrl(base: String): String {
        val i = base.indexOf('?')
        val chemin = (if (i < 0) base else base.substring(0, i)).trimEnd('/')
        val requete = if (i < 0) "" else base.substring(i)
        val racine = if (chemin.endsWith("/route")) chemin.removeSuffix("/route") else chemin
        return "$racine/trace_attributes$requete"
    }

    /**
     * Le corps de la requete. `map_snap` : les points collent aux voies, et un trou dans la trace (point
     * sans voie a proximite) interrompt le chemin au lieu d'inventer un detour.
     *
     * Coordonnees par `toString()`, insensible a la locale (cf. Valhalla.url).
     */
    internal fun body(points: List<Pair<Double, Double>>, costing: String): String {
        val shape = points.joinToString(",") { (lat, lon) -> """{"lat":$lat,"lon":$lon}""" }
        val attributs = listOf(
            "edge.way_id", "edge.length", "edge.surface", "edge.road_class", "edge.use",
            "matched.type", "matched.edge_index",
        ).joinToString(",") { "\"$it\"" }
        return """{"shape":[$shape],"costing":"$costing","shape_match":"map_snap",""" +
            """"filters":{"attributes":[$attributs],"action":"include"}}"""
    }

    /**
     * Les morceaux a envoyer, en plages d'indices de [points] (bornes comprises). Deux morceaux voisins
     * partagent leur point de jonction : sans lui, le bout de trace qui les separe ne serait recale par
     * aucun des deux.
     */
    internal fun chunks(
        points: List<Pair<Double, Double>>,
        maxMeters: Double = CHUNK_METERS, maxPoints: Int = CHUNK_POINTS,
    ): List<IntRange> {
        if (points.size < 2) return emptyList()
        val out = mutableListOf<IntRange>()
        var debut = 0
        var cumul = 0.0
        for (i in 1 until points.size) {
            cumul += distance(points[i - 1], points[i])
            if (cumul >= maxMeters || i - debut + 1 >= maxPoints) {
                out += debut..i
                debut = i
                cumul = 0.0
            }
        }
        if (debut < points.lastIndex) out += debut..points.lastIndex
        return out
    }

    // ---------- Reponse ----------

    @Serializable internal data class Response(
        val edges: List<Edge> = emptyList(),
        @SerialName("matched_points") val matchedPoints: List<Matched> = emptyList(),
    )

    /** [length] en kilometres, comme le reste des reponses du service. */
    @Serializable internal data class Edge(
        @SerialName("way_id") val wayId: Long? = null,
        val length: Double = 0.0,
        val surface: String? = null,
        @SerialName("road_class") val roadClass: String? = null,
        val use: String? = null,
    )

    @Serializable internal data class Matched(
        val type: String? = null,
        @SerialName("edge_index") val edgeIndex: Long? = null,
    )

    /** La reponse lue, ou null si elle ne porte aucune arete : erreur, ou corps inattendu. */
    internal fun parse(body: String): Response? = runCatching {
        json.decodeFromString<Response>(body).takeIf { it.edges.isNotEmpty() }
    }.getOrNull()

    /**
     * Les bouts de trace que le service n'a pose sur aucune voie, en metres, par arete apres laquelle ils
     * se trouvent (-1 : avant la premiere).
     *
     * Un point "unmatched" n'appartient a aucune arete. Le bout manquant va du dernier point recale qui le
     * precede au premier qui le suit : c'est la que le chemin rendu s'interrompt. Il est range apres
     * l'arete du point qui precede, puisque les aretes se suivent dans l'ordre de la trace.
     */
    internal fun gapsOf(points: List<Pair<Double, Double>>, r: Response): Map<Int, Double> {
        val mp = r.matchedPoints
        if (mp.size != points.size) return emptyMap()
        fun areteDe(i: Int): Int? = mp[i].edgeIndex?.takeIf { mp[i].type != "unmatched" && it < r.edges.size }?.toInt()
        val out = linkedMapOf<Int, Double>()
        var i = 0
        while (i < mp.size) {
            if (areteDe(i) != null) { i++; continue }
            val debut = i
            while (i < mp.size && areteDe(i) == null) i++
            // Du dernier point recale avant le trou au premier apres, bornes a la trace.
            val a = (debut - 1).coerceAtLeast(0)
            val b = i.coerceAtMost(mp.lastIndex)
            val m = (a until b).sumOf { distance(points[it], points[it + 1]) }
            val ancre = if (debut == 0) -1 else areteDe(debut - 1) ?: -1
            if (m > 0.0) out[ancre] = (out[ancre] ?: 0.0) + m
        }
        return out
    }

    /**
     * Les attributs d'une arete dans le vocabulaire d'OSM, d'apres ceux de Valhalla : le repli quand
     * Overpass ne rend pas ceux de la voie. Traduits pour passer par le meme classement que le reste.
     *
     * `surface=path` et `impassable` ne disent rien du revetement : ils ne sont pas repris, et le classement
     * l'estime alors d'apres le type de voie.
     */
    internal fun fallbackTagsOf(e: Edge): String {
        val voie = when (e.use) {
            "ferry", "rail-ferry" -> return "route=ferry"
            "track" -> "track"
            "cycleway" -> "cycleway"
            "mountain_bike", "path" -> "path"
            "footway", "sidewalk" -> "footway"
            "steps" -> "steps"
            "pedestrian" -> "pedestrian"
            "bridleway" -> "bridleway"
            "living_street" -> "living_street"
            "driveway", "alley", "parking_aisle", "drive-through", "service_road" -> "service"
            else -> when (e.roadClass) {
                "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential" -> e.roadClass
                "service_other" -> "service"
                else -> null
            }
        }
        val revetement = when (e.surface) {
            "paved_smooth" -> "asphalt"
            "paved" -> "paved"
            "paved_rough" -> "sett"
            "compacted" -> "compacted"
            "gravel" -> "gravel"
            "dirt" -> "ground"
            else -> null
        }
        return listOfNotNull(voie?.let { "highway=$it" }, revetement?.let { "surface=$it" }).joinToString(" ")
    }

    /**
     * Les morceaux du trajet, dans l'ordre : une arete par voie suivie, les bouts hors reseau ([gaps], cf.
     * [gapsOf]) a leur place et sans attributs. Deux morceaux voisins aux memes attributs n'en font qu'un :
     * une voie est coupee en autant d'aretes que de carrefours, et c'est ce qui est garde avec la trace.
     */
    internal fun segmentsOf(r: Response, gaps: Map<Int, Double>, tagsOf: (Edge) -> String): List<WaySegment> {
        val out = mutableListOf<WaySegment>()
        fun add(meters: Double, tags: String) {
            if (meters <= 0.0) return
            val prec = out.lastOrNull()
            if (prec != null && prec.tags == tags) out[out.lastIndex] = WaySegment(prec.meters + meters, tags)
            else out += WaySegment(meters, tags)
        }
        gaps[-1]?.let { add(it, "") }
        r.edges.forEachIndexed { i, e ->
            add(e.length * 1000.0, tagsOf(e))
            gaps[i]?.let { add(it, "") }
        }
        return out
    }

    // ---------- Appel ----------

    /**
     * Recale la trace [points], en (lat, lon), et rend les voies suivies.
     *
     * Les morceaux (cf. [chunks]) sont recales un a un, puis les voies de tous demandees d'un coup a
     * Overpass. Un morceau que le service n'a pas pu recaler compte tout entier hors reseau ; un seul qui
     * n'a pas abouti faute de reseau fait echouer le tout, plutot que de rendre des parts fausses.
     */
    suspend fun match(
        valhallaBase: String, overpassBase: String, points: List<Pair<Double, Double>>,
    ): Outcome = withContext(Dispatchers.IO) {
        val morceaux = chunks(points)
        if (morceaux.isEmpty()) return@withContext Outcome.NoMatch
        val url = traceUrl(valhallaBase)
        val recales = mutableListOf<Pair<Response, Map<Int, Double>>>()
        var horsReseau = 0.0
        for (plage in morceaux) {
            val pts = points.subList(plage.first, plage.last + 1)
            val longueur = (1 until pts.size).sumOf { distance(pts[it - 1], pts[it]) }
            var meilleur: Pair<Response, Map<Int, Double>>? = null
            var joint = false
            for (costing in COSTINGS) {
                val resp = post(url, body(pts, costing))
                if (resp.status != 0) joint = true
                val r = resp.body?.let { parse(it.toString(Charsets.UTF_8)) } ?: continue
                val g = gapsOf(pts, r)
                if (meilleur == null || g.values.sum() < meilleur.second.values.sum()) meilleur = r to g
                if (g.values.sum() <= longueur * GOOD_ENOUGH) break
            }
            if (!joint) return@withContext Outcome.Unreachable
            if (meilleur == null) {
                // Rien de recale : le morceau entier est hors reseau.
                recales += Response() to mapOf(-1 to longueur)
                horsReseau += longueur
            } else {
                recales += meilleur
                horsReseau += meilleur.second.values.sum()
            }
        }
        if (recales.all { it.first.edges.isEmpty() }) return@withContext Outcome.NoMatch
        val ids = recales.flatMap { (r, _) -> r.edges.mapNotNull { it.wayId } }.distinct()
        val osm = Overpass.wayTags(overpassBase, ids, KEPT_TAGS)
        val segments = recales.flatMap { (r, g) ->
            segmentsOf(r, g) { e -> e.wayId?.let { osm?.get(it) } ?: fallbackTagsOf(e) }
        }.fold(mutableListOf<WaySegment>()) { acc, s ->
            // Les morceaux se recollent : une meme voie a cheval sur deux n'en fait qu'un.
            val prec = acc.lastOrNull()
            if (prec != null && prec.tags == s.tags) acc[acc.lastIndex] = WaySegment(prec.meters + s.meters, s.tags)
            else acc += s
            acc
        }
        Outcome.Done(WayMatch(segments, horsReseau, osmTags = osm != null))
    }

    /** Ce que rend le recalage de toutes les lignes d'une couche. */
    sealed interface LayerOutcome {
        data class Done(val ways: LayerWays) : LayerOutcome
        data object NoMatch : LayerOutcome
        data object Unreachable : LayerOutcome
    }

    /**
     * Recale chaque ligne d'une couche a part, en (lat, lon) : les voies se rangent par ligne (cf.
     * [LayerWays]), pour que la mise en evidence d'une categorie tombe sur la bonne.
     */
    suspend fun matchLines(
        valhallaBase: String, overpassBase: String, lines: List<List<Pair<Double, Double>>>,
    ): LayerOutcome {
        val issues = mutableListOf<Outcome>()
        for (l in lines) {
            val o = match(valhallaBase, overpassBase, l)
            // Inutile d'insister : sans reseau, les lignes suivantes echoueraient de meme.
            if (o == Outcome.Unreachable) return LayerOutcome.Unreachable
            issues += o
        }
        return combine(issues, lines.map { l -> (1 until l.size).sumOf { distance(l[it - 1], l[it]) } })
    }

    /**
     * Les recalages des lignes, [lengths] leurs longueurs en metres, reunis en un seul resultat.
     *
     * Une ligne que le service n'a pu recaler compte tout entiere hors reseau - un seul morceau sans
     * attributs, "inconnu" -, plutot que de disparaitre des parts : sa longueur fait partie de la trace.
     * Aucune ligne recalee : rien a montrer. Les attributs ne sont dits d'OSM que si toutes les lignes
     * recalees le sont.
     */
    internal fun combine(issues: List<Outcome>, lengths: List<Double>): LayerOutcome {
        if (issues.any { it == Outcome.Unreachable }) return LayerOutcome.Unreachable
        val faits = issues.filterIsInstance<Outcome.Done>()
        if (faits.isEmpty()) return LayerOutcome.NoMatch
        val lignes = issues.mapIndexed { i, o ->
            (o as? Outcome.Done)?.match?.segments
                ?: listOfNotNull(lengths.getOrNull(i)?.takeIf { it > 0.0 }?.let { WaySegment(it, "") })
        }
        val horsReseau = issues.mapIndexed { i, o ->
            (o as? Outcome.Done)?.match?.offNetworkMeters ?: lengths.getOrElse(i) { 0.0 }
        }.sum()
        return LayerOutcome.Done(LayerWays(lignes, horsReseau, osmTags = faits.all { it.match.osmTags }))
    }

    /** Une seconde tentative quand la premiere n'a pas abouti du tout, comme pour le calcul (cf. Valhalla.route). */
    private suspend fun post(url: String, body: String): TileHttp.Response {
        val octets = body.toByteArray(Charsets.UTF_8)
        repeat(2) {
            val r = runInterruptible { TileHttp.post(url, octets, "application/json", 15_000, 90_000) }
            if (r.status != 0) return r
        }
        return TileHttp.Response(0, null)
    }

    private fun distance(a: Pair<Double, Double>, b: Pair<Double, Double>): Double =
        TrackMath.haversine(a.second, a.first, b.second, b.first)
}
