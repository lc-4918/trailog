package fr.lc4918.trailog.elevation

import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PropValue
import fr.lc4918.trailog.domain.model.TrackPoint
import fr.lc4918.trailog.map.offline.TileHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Ou chercher les altitudes. Un champ vide vaut le defaut de son client : l'application n'ecrit pas les
 *  URL par defaut en base, qui y resteraient perimees le jour ou le defaut du code change. */
data class ElevationServices(
    val ignUrl: String = "",
    val worldUrl: String = "",
    val worldKey: String = "",
) {
    val ign: String get() = ignUrl.ifBlank { IgnElevation.DEFAULT_URL }
    val world: String get() = worldUrl.ifBlank { OpenTopo.DEFAULT_URL }
    val key: String get() = worldKey.ifBlank { OpenTopo.DEFAULT_KEY }
}

/** Reponse d'un service, reduite a ce dont le calcul a besoin. [status] vaut 0 quand la requete n'a pas
 *  abouti du tout - pas de reseau, delai depasse -, ce qui ne se traite pas comme un refus du service. */
class ElevationResponse(val status: Int, val body: String?)

/** De quoi interroger un service. Un parametre plutot qu'un appel direct a [TileHttp] : c'est le seul
 *  point par lequel le remplissage touche au reseau, et le detourner permet de le verifier sans. */
typealias ElevationFetcher = suspend (String) -> ElevationResponse

/**
 * Altitudes manquantes d'une couche importee, completees d'apres des modeles de terrain.
 *
 * Un fichier sans Z - releve depuis un fond de carte, trace dessine a la main, export d'un outil qui n'en
 * garde pas - donne une trace sans profil altimetrique et des waypoints sans altitude. On va donc chercher
 * le Z la ou il existe : a l'IGN sur la France (cf. [IgnElevation]), a OpenTopography ailleurs (cf.
 * [OpenTopo]). Aucune frontiere n'est codee ici : on demande d'abord a l'IGN, qui dit lui-meme ou il ne
 * sait pas.
 *
 * **Une trace se complete entierement ou pas du tout.** Un profil dont il manque quelques points n'est pas
 * un profil incomplet : les points sans Z valent zero dans le calcul (cf. `TrackMath.compute`), le trace
 * plonge au niveau de la mer et le D+ devient absurde. C'est la meme regle que pour les altitudes rendues
 * par le moteur d'itineraire, ou un trou fait rejeter toute la table du segment. Les waypoints, eux, sont
 * independants les uns des autres : chacun se complete ou non pour son propre compte.
 *
 * Rien n'est signale a l'utilisateur en cas d'echec : la couche s'importe comme avant, sans Z. Le
 * remplissage est un supplement, il n'a pas a faire echouer un import qui reussissait sans lui.
 */
object ElevationFiller {

    /** Propriete d'un waypoint qui porte son altitude - celle-la meme que remplit un GPX qui en a une. */
    const val ELE_PROP = "ele"

    /**
     * Cote maximal d'une emprise telechargee d'un coup, en degres, et nombre d'emprises qu'on s'autorise.
     *
     * 0,05 degre fait environ 5,5 km, soit une grille de 60 x 60 cellules au pas de 90 m - une soixantaine
     * de kilo-octets. Vingt-quatre secteurs couvrent une trace de 130 km, pour 1,5 Mo au total.
     *
     * Au-dela, les secteurs passent a 0,15 degre : neuf fois la surface, donc neuf fois le poids par
     * secteur (environ 550 ko), pour couvrir jusqu'a 400 km. C'est le decoupage qui s'elargit, non le
     * modele qui se degrade - il n'y en a qu'un (cf. [OpenTopo.DEM]).
     *
     * Une trace qui deborde encore de ce second decoupage n'est PAS completee a moitie : on renonce avant
     * la premiere requete, plutot que d'epuiser le quota de la cle pour un profil qui serait refuse au bout
     * (cf. la regle du tout ou rien).
     */
    private const val FINE_SIDE_DEG = 0.05
    private const val COARSE_SIDE_DEG = 0.15
    private const val MAX_CHUNKS = 24

    /** Cote minimal demande au service de grille : il refuse en dessous de 250 m environ, on vise plus
     *  large pour ne pas se cogner a sa borne. */
    private const val MIN_BBOX_METERS = 400.0

    /**
     * Paquets qu'on s'autorise a envoyer a l'IGN, soit douze mille points.
     *
     * Le service ne rend que les points demandes : son cout suit donc le NOMBRE DE POINTS, la ou celui des
     * grilles mondiales suit la SURFACE couverte. Au-dela de ce plafond, une trace tres dense coute moins
     * cher en grilles qu'en paquets - et la passe mondiale s'en charge, au prix d'un modele a 90 m la ou
     * l'IGN est au metre. On y perd en finesse, non en profil.
     */
    private const val MAX_IGN_BATCHES = 60

    /** Points et traces completes. Les listes rendues sont celles d'origine quand il n'y avait rien a
     *  completer, ou que rien n'a pu l'etre. */
    data class Filled(val points: List<PointFeature>, val lines: List<List<TrackPoint>>)

    /** Le vrai acces reseau, sur le dispatcher des entrees-sorties. */
    val httpFetcher: ElevationFetcher = { url ->
        withContext(Dispatchers.IO) {
            val r = TileHttp.fetch(url)
            ElevationResponse(r.status, r.body?.toString(Charsets.UTF_8))
        }
    }

    /**
     * Y a-t-il quelque chose a completer ? Pose avant [fill] par l'import, qui annonce le calcul a
     * l'utilisateur : un fichier deja pourvu de ses altitudes ne doit pas faire clignoter un libelle pour
     * un travail qui n'aura pas lieu.
     */
    fun hasHoles(points: List<PointFeature>, lines: List<List<TrackPoint>>): Boolean =
        points.any { needsElevation(it) } || lines.any { line -> line.any { it.ele == null } }

    suspend fun fill(
        points: List<PointFeature>,
        lines: List<List<TrackPoint>>,
        services: ElevationServices,
        fetch: ElevationFetcher = httpFetcher,
    ): Filled {
        // Les trous, dans un seul tableau : les waypoints d'abord, puis les points de trace dans l'ordre
        // des traces. Cet ordre compte pour le decoupage en emprises, qui suppose que deux points voisins
        // dans la liste le sont aussi sur le terrain.
        val wptHoles = ArrayList<Int>()
        val lineHoles = ArrayList<IntArray>()
        val coords = ArrayList<LonLat>()
        points.forEachIndexed { i, p ->
            if (needsElevation(p)) { wptHoles.add(i); coords.add(LonLat(p.lon, p.lat)) }
        }
        lines.forEachIndexed { li, line ->
            line.forEachIndexed { pi, p ->
                if (p.ele == null) { lineHoles.add(intArrayOf(li, pi)); coords.add(LonLat(p.lon, p.lat)) }
            }
        }
        if (coords.isEmpty()) return Filled(points, lines)

        val z = arrayOfNulls<Double>(coords.size)
        // La passe mondiale ne s'ouvre que si le reseau a repondu a la premiere : sans lui, elle ne ferait
        // qu'aligner les memes echecs.
        if (!francePass(coords, z, services, fetch)) worldPass(coords, z, services, fetch)
        if (z.all { it == null }) return Filled(points, lines)

        return Filled(
            applyToPoints(points, wptHoles, z),
            applyToLines(lines, lineHoles, z, wptHoles.size),
        )
    }

    /** Un waypoint sans altitude : la propriete absente, ou vide. Un waypoint qui en porte deja une n'est
     *  pas retouche, y compris quand elle vient d'un GPS moins exact que le modele de terrain - c'est la
     *  mesure du fichier importe, elle appartient a l'utilisateur. */
    private fun needsElevation(p: PointFeature): Boolean {
        val v = p.props[ELE_PROP] ?: return true
        return v is PropValue.Text && v.value.isBlank()
    }

    /**
     * Passe francaise : l'IGN, par paquets de deux cents points. Rend vrai si le RESEAU a manque, auquel
     * cas il n'y a rien a esperer de la passe suivante non plus.
     *
     * Le service ne connaissant pas ses propres frontieres autrement qu'en repondant "pas de donnee", un
     * fichier entierement etranger lui couterait autant de requetes inutiles qu'a une trace francaise. D'ou
     * l'abandon des le premier paquet quand il revient entierement vide : une trace commencant hors de
     * France passe directement au modele mondial.
     *
     * Elle est sautee sans une seule requete au-dela de [MAX_IGN_BATCHES] paquets : la passe mondiale y est
     * moins chere, son cout ne suivant pas le nombre de points.
     */
    private suspend fun francePass(
        coords: List<LonLat>, z: Array<Double?>, services: ElevationServices, fetch: ElevationFetcher,
    ): Boolean {
        if (coords.size > MAX_IGN_BATCHES * IgnElevation.MAX_POINTS) return false
        var i = 0
        while (i < coords.size) {
            val end = min(i + IgnElevation.MAX_POINTS, coords.size)
            val batch = coords.subList(i, end)
            val r = fetch(IgnElevation.url(services.ign, batch))
            if (r.status == 0) return true
            val found = r.body?.let { IgnElevation.parse(it, batch.size) } ?: return false
            for (k in found.indices) z[i + k] = found[k]
            if (i == 0 && found.all { it == null }) return false
            i = end
        }
        return false
    }

    /**
     * Passe mondiale : OpenTopography, sur les seuls points que l'IGN n'a pas servis. Rend vrai si le
     * reseau a manque en cours de route.
     *
     * Un point isole passe par l'API du point, un groupe par une emprise de modele : la premiere coute une
     * requete par point, la seconde une requete par secteur. La bascule se fait donc sur la taille du
     * groupe, et non sur la nature de ce qu'on complete - un waypoint perdu au milieu d'une trace
     * etrangere profite de l'emprise que la trace telecharge deja.
     */
    private suspend fun worldPass(
        coords: List<LonLat>, z: Array<Double?>, services: ElevationServices, fetch: ElevationFetcher,
    ): Boolean {
        val rest = z.indices.filter { z[it] == null }
        if (rest.isEmpty()) return false
        val pts = rest.map { coords[it] }
        // Un seul modele, deux tailles de secteur : c'est le DECOUPAGE qui s'elargit pour une longue trace,
        // pas le modele qui se degrade. Il n'y a donc rien a choisir ici en dehors du nombre de requetes.
        var plan = chunks(pts, FINE_SIDE_DEG)
        if (plan.size > MAX_CHUNKS) plan = chunks(pts, COARSE_SIDE_DEG)
        if (plan.size > MAX_CHUNKS) return false
        val dem = OpenTopo.DEM
        for (c in plan) {
            val part = pts.subList(c.first, c.last + 1)
            val found: List<Double?> = if (part.size == 1) {
                val r = fetch(OpenTopo.pointUrl(services.world, services.key, part[0], dem))
                if (r.status == 0) return true
                listOf(r.body?.let { OpenTopo.parsePoint(it) })
            } else {
                val box = Bbox.of(part)!!.padded(MIN_BBOX_METERS, dem.cellDeg)
                val r = fetch(OpenTopo.gridUrl(services.world, services.key, box, dem))
                if (r.status == 0) return true
                val grid = r.body?.let { AsciiGrid.parse(it) }
                part.map { p -> grid?.sample(p.lon, p.lat) }
            }
            for (k in found.indices) z[rest[c.first + k]] = found[k]
        }
        return false
    }

    /**
     * Decoupage d'une suite de points en groupes dont l'emprise reste sous [maxSideDeg] de cote.
     *
     * Glouton, dans l'ordre donne : les points d'une trace se suivent sur le terrain, un groupe se ferme
     * donc quand la trace est sortie du secteur, et non parce qu'un point lointain aurait ete range la par
     * hasard. Des waypoints eparpilles donnent, eux, autant de groupes que de secteurs habites.
     */
    fun chunks(points: List<LonLat>, maxSideDeg: Double): List<IntRange> {
        val out = ArrayList<IntRange>()
        if (points.isEmpty()) return out
        var start = 0
        var w = points[0].lon; var e = w; var s = points[0].lat; var n = s
        for (i in 1 until points.size) {
            val p = points[i]
            if (Bbox.sideWith(w, s, e, n, p) > maxSideDeg) {
                out.add(start until i)
                start = i
                w = p.lon; e = p.lon; s = p.lat; n = p.lat
            } else {
                w = min(w, p.lon); e = max(e, p.lon); s = min(s, p.lat); n = max(n, p.lat)
            }
        }
        out.add(start until points.size)
        return out
    }

    /** Les waypoints, chacun complete pour son compte. */
    private fun applyToPoints(
        points: List<PointFeature>, holes: List<Int>, z: Array<Double?>,
    ): List<PointFeature> {
        if (holes.isEmpty()) return points
        val out = points.toMutableList()
        holes.forEachIndexed { k, idx ->
            val ele = z[k] ?: return@forEachIndexed
            val props = LinkedHashMap(out[idx].props)
            props[ELE_PROP] = PropValue.Text(String.format(Locale.US, "%.1f", ele))
            out[idx] = out[idx].copy(props = props)
        }
        return out
    }

    /** Les traces, chacune completee entierement ou laissee telle quelle (cf. la regle du tout ou rien). */
    private fun applyToLines(
        lines: List<List<TrackPoint>>, holes: List<IntArray>, z: Array<Double?>, offset: Int,
    ): List<List<TrackPoint>> {
        if (holes.isEmpty()) return lines
        // Altitudes trouvees, rangees par trace puis par point, et la liste des traces incompletes.
        val byLine = HashMap<Int, HashMap<Int, Double>>()
        val broken = HashSet<Int>()
        holes.forEachIndexed { k, hole ->
            val li = hole[0]
            val ele = z[offset + k]
            if (ele == null) broken.add(li) else byLine.getOrPut(li) { HashMap() }[hole[1]] = ele
        }
        return lines.mapIndexed { li, line ->
            val found = byLine[li]
            if (found == null || li in broken) line
            else line.mapIndexed { pi, p -> if (p.ele != null) p else p.copy(ele = found[pi]) }
        }
    }
}
