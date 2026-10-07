package fr.lc4918.trailog.geocode.offline

import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.geocode.GeocodePlace
import fr.lc4918.trailog.routing.offline.BrouterTile
import java.text.Normalizer

/**
 * Un lieu rendu par l'index hors ligne : son nom, son genre (cf. [PlaceKind]), son point, et la commune
 * qu'il designe - le contexte qui distingue deux "Rue de la Republique".
 */
data class PlaceHit(val name: String, val kind: Int, val lon: Double, val lat: Double, val locality: String?) {
    fun toPlace() = GeocodePlace(listOfNotNull(name, locality?.takeIf { it != name }), lon, lat)
}

/** Les genres de lieu de l'index, tels que `tools/geocode-index/build_index.py` les numerote. */
object PlaceKind {
    const val CITY = 1
    const val TOWN = 2
    const val VILLAGE = 3
    const val HAMLET = 4
    const val LOCALITY = 6
    const val STREET = 60

    /** Les genres qui sont des communes ou des quartiers : ils n'ont pas de commune a nommer. */
    private val SETTLEMENTS = setOf(CITY, TOWN, VILLAGE, HAMLET, 5, LOCALITY)

    fun isSettlement(kind: Int): Boolean = kind in SETTLEMENTS

    /**
     * L'importance d'un genre : plus petit, plus en vue. Reprend `TIER` du script - la meme echelle range
     * les identifiants de l'index, et departage ici les resultats de plusieurs carres.
     */
    fun tier(kind: Int): Int = when (kind) {
        CITY -> 0
        TOWN -> 1
        VILLAGE -> 2
        5, 7 -> 3                   // quartier, ile
        HAMLET -> 5
        17, 18 -> 6                 // source, grotte
        LOCALITY -> 7
        STREET -> 8
        else -> 4                   // relief, eau, refuges, gares...
    }
}

/**
 * Ce que l'index hors ligne comprend de la frappe, et de quoi classer ce qu'il rend. Rien ici ne touche
 * la base : ce sont les parties ou une faute est silencieuse, testees sans appareil.
 */
object PlaceQuery {

    /** Un lieu a moins de 150 m du point montre prend le pas sur le lieu-dit voisin : une rue n'est gardee
     *  qu'a cette condition, son point etant le milieu d'un troncon et non son axe. */
    const val STREET_REACH_M = 150.0

    /** Au-dela, un lieu ne dit plus "ou l'on est". */
    const val REVERSE_REACH_M = 5_000.0

    /** Rayon de recherche inverse, en cases : 6 cases, c'est 6,6 km en latitude et 4 a 5 km en longitude. */
    const val REVERSE_CELLS = 6

    private val WORD = Regex("[\\p{L}\\p{N}]+")

    /**
     * L'expression FTS5 d'une frappe : chaque mot entre guillemets - la syntaxe de la requete n'est pas celle
     * de l'utilisateur, un tiret ou un "OR" ne doivent rien signifier -, le dernier en prefixe, puisqu'il
     * n'est pas fini. Null quand la frappe ne porte aucun mot.
     */
    fun match(text: String): String? {
        val words = WORD.findAll(text).map { it.value }.toList()
        if (words.isEmpty()) return null
        return words.withIndex().joinToString(" ") { (i, w) ->
            "\"$w\"" + if (i == words.lastIndex) "*" else ""
        }
    }

    /** Minuscules, sans accents : la forme sous laquelle deux noms se comparent. */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    /** La case d'un point donne en 1e-5 degre (cf. `cell_of`). Division entiere par defaut : -0,5 est en -1. */
    fun cellOf(latE5: Long, lonE5: Long): Long =
        Math.floorDiv(latE5, 1000L) * 100_000L + Math.floorDiv(lonE5, 1000L) + 20_000L

    /**
     * Les rangees de cases a lire autour d'un point : une plage de cases CONSECUTIVES par rangee de latitude
     * (la longitude est le chiffre de poids faible de la case), que la requete lit d'un trait.
     */
    fun cellRanges(lat: Double, lon: Double, radius: Int = REVERSE_CELLS): List<LongRange> {
        val here = cellOf(Math.round(lat * 1e5), Math.round(lon * 1e5))
        return (-radius..radius).map { dy -> here + dy * 100_000L - radius..here + dy * 100_000L + radius }
    }

    /** Les carres a ouvrir pour chercher autour d'un point : le sien, et ceux que le rayon deborde. */
    fun tilesAround(lon: Double, lat: Double, marginDegrees: Double = 0.07): Set<BrouterTile> =
        listOf(-marginDegrees, marginDegrees).flatMap { dx ->
            listOf(-marginDegrees, marginDegrees).map { dy -> BrouterTile.of(lon + dx, lat + dy) }
        }.toSet() + BrouterTile.of(lon, lat)

    /**
     * L'ordre des propositions avant la prise en compte de la distance : le nom exactement tape d'abord,
     * puis l'importance du genre (une ville avant un hameau), puis le nom le plus court. Les doublons - le
     * meme nom, du meme genre, au meme endroit a un kilometre pres - ne comptent qu'une fois : une commune
     * est souvent a la fois un `place=town` et un `place=village` d'un carre voisin.
     */
    fun order(hits: List<PlaceHit>, typed: String): List<PlaceHit> {
        val wanted = fold(typed.trim())
        val seen = HashSet<Triple<String, Long, Long>>()
        return hits
            .sortedWith(
                compareBy<PlaceHit>({ if (fold(it.name) == wanted) 0 else 1 }, { PlaceKind.tier(it.kind) }, { it.name.length })
            )
            .filter { seen.add(Triple(fold(it.name), Math.round(it.lat * 100), Math.round(it.lon * 100))) }
    }

    /**
     * Le lieu qui dit "ou l'on est" : une rue a 150 m au plus, a defaut le lieu habite le plus proche
     * (commune, village, hameau, quartier) dans [REVERSE_REACH_M], a defaut le plus proche de n'importe quel
     * genre. Null quand rien n'est a portee.
     */
    fun pickReverse(hits: List<PlaceHit>, lon: Double, lat: Double): PlaceHit? {
        fun meters(h: PlaceHit) = TrackMath.haversine(lon, lat, h.lon, h.lat)
        val near = hits.map { it to meters(it) }.filter { it.second <= REVERSE_REACH_M }
        near.filter { it.first.kind == PlaceKind.STREET && it.second <= STREET_REACH_M }
            .minByOrNull { it.second }?.let { return it.first }
        near.filter { PlaceKind.isSettlement(it.first.kind) }.minByOrNull { it.second }?.let { return it.first }
        return near.filter { it.first.kind != PlaceKind.STREET }.minByOrNull { it.second }?.first
    }
}
