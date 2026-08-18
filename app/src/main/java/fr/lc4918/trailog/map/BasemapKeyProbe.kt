package fr.lc4918.trailog.map

import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.map.offline.TileHttp
import fr.lc4918.trailog.map.offline.TileMath
import fr.lc4918.trailog.map.offline.TileUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Le service du fond par défaut accepte-t-il encore notre clé ?
 *
 * La question ne se posait pas tant que le fond de démarrage était OSM, qui n'en demande aucune. Depuis
 * qu'il s'agit de Mapbox Outdoors, tout le premier écran de l'application dépend d'une clé tierce à quota :
 * si elle tombe, la carte s'ouvre **grise**, sans que rien ne dise pourquoi ni ne propose d'en sortir.
 *
 * Même règle que [CoverageProbe], et c'est la seule qui compte ici : **on ne conclut que sur un refus
 * explicite du serveur**. Un délai dépassé, une absence de réseau, un 500 laissent [Verdict.UNKNOWN] et le
 * fond choisi en place - hors ligne, les tuiles déjà en cache s'affichent encore, et basculer sur OSM
 * n'apporterait qu'une carte tout aussi vide.
 *
 * Ce que le repli n'est pas : un changement de réglage. Il ne vaut que pour la session en cours (cf.
 * `MainViewModel`), parce qu'un quota se remet à zéro et qu'une clé se remplace - le lendemain,
 * l'application doit revenir d'elle-même sur le fond demandé.
 */
object BasemapKeyProbe {

    enum class Verdict {
        /** Le service a servi la tuile : la clé passe. */
        OK,

        /** Le service refuse explicitement : clé absente, invalide, expirée, ou quota épuisé. */
        REFUSED,

        /** On ne sait pas - et dans le doute on ne touche à rien. */
        UNKNOWN,
    }

    /**
     * Statuts qui disent une clé refusée, et eux seuls.
     *
     * 401 et 403 : clé absente, invalide ou revoquée. 402 : compte impayé, que Mapbox emploie. 429 : quota
     * epuisé. Un 404 n'en fait pas partie - c'est une tuile absente, pas une clé morte, et l'on ne
     * changerait pas de fond entier pour un trou de couverture.
     */
    private val REFUSALS = setOf(401, 402, 403, 429)

    /** Sonde brève : la carte s'ouvre pendant ce temps-là, et le fond demandé reste affiché tant qu'on
     *  n'a pas de raison d'en changer. */
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 6_000

    /** Tuile interrogée : le monde entier au zoom 0, servie par tous les fonds mondiaux et la plus petite
     *  qu'un service produise. On ne sonde pas une couverture, seulement une porte. */
    private const val PROBE_ZOOM = 0

    /**
     * Le fond [provider] a-t-il besoin d'une clé pour répondre ?
     *
     * Sans `{KEY}` dans son gabarit, il n'y a rien à refuser : sonder OSM ou un MBTiles local ne dirait
     * jamais rien d'utile, et coûterait une requête à chaque démarrage.
     */
    fun needsKey(provider: ProviderEntity): Boolean =
        provider.type != "MBTILES" && "{KEY}" in provider.urlTemplate

    suspend fun probe(provider: ProviderEntity): Verdict {
        if (!needsKey(provider)) return Verdict.OK
        val z = PROBE_ZOOM.coerceIn(provider.minZoom, provider.maxZoom)
        val (x, y) = TileMath.tileAt(0.0, 0.0, z)
        val url = TileUrl.build(provider, x, y, z)
        val res = withContext(Dispatchers.IO) {
            runCatching { TileHttp.fetch(url, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS) }.getOrNull()
        } ?: return Verdict.UNKNOWN
        return verdictOf(res.status, res.body?.size ?: 0)
    }

    /**
     * Le verdict d'une réponse. [bytes] compte : un 200 au corps vide n'est pas une tuile, et certains
     * proxys rendent 200 avec une page d'erreur - mais une page d'erreur pèse plus qu'une tuile vide, on
     * ne peut donc pas la distinguer ici. On s'en tient au statut, seul signal sûr.
     */
    internal fun verdictOf(status: Int, bytes: Int): Verdict = when {
        status in REFUSALS -> Verdict.REFUSED
        status in 200..299 && bytes > 0 -> Verdict.OK
        else -> Verdict.UNKNOWN
    }
}
