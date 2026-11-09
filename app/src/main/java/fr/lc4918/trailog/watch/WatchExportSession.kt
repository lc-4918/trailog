package fr.lc4918.trailog.watch

import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.map.offline.TileHttp
import fr.lc4918.trailog.watch.WatchTiles.WatchTile
import fr.lc4918.trailog.watch.vector.VectorTile
import fr.lc4918.trailog.watch.vector.WatchVectorRenderer
import fr.lc4918.trailog.watch.vector.WatchOsmRenderer
import fr.lc4918.trailog.watch.vector.WatchOsmSource
import fr.lc4918.trailog.watch.vector.WatchVectorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File

/**
 * L'envoi en cours vers la montre : le serveur local ouvert, et ce que la montre en a deja recu.
 *
 * Ici et non dans un ecran, comme la file de BRouter : la montre ne synchronise qu'une fois l'utilisateur
 * passe sur elle, Trailog en arriere-plan, et l'envoi doit survivre a l'ecran qui l'a lance. Le service de
 * premier plan ([WatchExportService]) ne fait que tenir le processus en vie.
 *
 * Un seul envoi a la fois : le port est fixe, connu de l'application montre.
 */
class WatchExportSession(
    private val scope: CoroutineScope,
    private val onStarted: () -> Unit,
    private val port: Int = WatchTileServer.DEFAULT_PORT,
    private val vectorFetch: (String) -> ByteArray? = TileHttp::get,
) {
    companion object {
        /** Sans requete de la montre pendant ce delai, l'envoi se referme : un serveur oublie ouvert ne sert a rien. */
        const val IDLE_TIMEOUT_MS = 15 * 60_000L
        /** Les tuiles du fond gardees en memoire : chacune sert a plusieurs tuiles montre, demandees a des moments differents. */
        private const val SOURCE_CACHE_ENTRIES = 64
        /** Tuiles vectorielles decodees gardees en memoire : un objet par entite, bien plus gros que l'image compressee d'une tuile raster. */
        private const val VECTOR_CACHE_ENTRIES = 24
    }

    data class State(val name: String, val servedCount: Int, val tileCount: Int) {
        val complete: Boolean get() = tileCount > 0 && servedCount >= tileCount
    }

    private val mutableState = MutableStateFlow<State?>(null)
    /** null quand aucun envoi n'est ouvert. */
    val state: StateFlow<State?> = mutableState.asStateFlow()

    private var server: WatchTileServer? = null
    private var source: Closeable? = null
    private var idleTimer: Job? = null

    /**
     * Ouvre l'envoi de [tiles], decoupees dans [provider]. [mbtilesFile] est le fichier du fond quand il est
     * de type MBTiles. Remplace l'envoi precedent. Rend false si le serveur n'a pas pu ouvrir son port.
     */
    // Synchronise : le serveur rappelle depuis son propre fil, la minuterie depuis une coroutine.
    @Synchronized
    fun start(
        name: String,
        tiles: List<WatchTile>,
        provider: ProviderEntity,
        mbtilesFile: File?,
        track: List<WatchTrack.WatchTrackPoint> = emptyList(),
        detail: WatchTiles.Detail = WatchTiles.Detail.DEFAULT,
    ): Boolean {
        stop()
        val sourceZoom = detail.sourceZoom
        // Les tuiles du fond gardees en memoire : une tuile montre en recouvre jusqu'a quatre, et les voisines
        // en partagent.
        val tileSource: Closeable
        val pixelsOf: (WatchTile) -> ByteArray?
        if (WatchVectorSource.isVector(provider)) {
            val vectorSource = WatchVectorSource(fetch = vectorFetch)
            val vectorCache = object : LinkedHashMap<Pair<Int, Int>, VectorTile?>(VECTOR_CACHE_ENTRIES, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, VectorTile?>) = size > VECTOR_CACHE_ENTRIES
            }
            tileSource = vectorSource
            pixelsOf = { tile ->
                WatchVectorRenderer.render(tile, detail.zoom, sourceZoom) { x, y ->
                    vectorCache.getOrPut(x to y) { vectorSource.tile(sourceZoom, x, y) }
                }?.let { WatchTilePixels.encode(it, WatchTiles.WATCH_TILE_PIXELS) }
            }
        } else if (WatchOsmSource.isOsm(provider)) {
            // Les tuiles OpenMapTiles s'arretent au zoom 14 : les niveaux plus fins en agrandissent la geometrie.
            val osmZoom = minOf(sourceZoom, WatchOsmRenderer.MAX_SOURCE_ZOOM)
            val osmSource = WatchOsmSource(fetch = vectorFetch)
            val osmCache = object : LinkedHashMap<Pair<Int, Int>, VectorTile?>(VECTOR_CACHE_ENTRIES, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, VectorTile?>) = size > VECTOR_CACHE_ENTRIES
            }
            tileSource = osmSource
            pixelsOf = { tile ->
                WatchOsmRenderer.render(tile, detail.zoom, osmZoom) { x, y ->
                    osmCache.getOrPut(x to y) { osmSource.tile(osmZoom, x, y) }
                }?.let { WatchTilePixels.encode(it, WatchTiles.WATCH_TILE_PIXELS) }
            }
        } else {
            val rasterSource = WatchTileSource(provider, mbtilesFile)
            val sourceCache = object : LinkedHashMap<Triple<Int, Int, Int>, ByteArray?>(SOURCE_CACHE_ENTRIES, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<Int, Int, Int>, ByteArray?>) =
                    size > SOURCE_CACHE_ENTRIES
            }
            tileSource = rasterSource
            pixelsOf = { tile ->
                WatchTileImages.render(tile, detail.zoom, sourceZoom) { x, y ->
                    sourceCache.getOrPut(Triple(sourceZoom, x, y)) { rasterSource.sourceImage(sourceZoom, x, y) }
                }
            }
        }
        val routes = WatchRoutes(
            exportName = name,
            tiles = tiles,
            track = track,
            turns = WatchTurns.detect(track),
            zoom = detail.zoom,
            pixelsOf = pixelsOf,
            onTileServed = { servedCount, tileCount ->
                mutableState.value = State(name, servedCount, tileCount)
                restartIdleTimer()
            },
        )
        val tileServer = WatchTileServer(routes, port)
        val opened = runCatching { tileServer.start() }.isSuccess
        if (!opened) {
            tileSource.close()
            return false
        }
        server = tileServer
        source = tileSource
        mutableState.value = State(name, 0, tiles.size)
        restartIdleTimer()
        onStarted()
        return true
    }

    @Synchronized
    fun stop() {
        idleTimer?.cancel()
        idleTimer = null
        server?.close()
        server = null
        source?.close()
        source = null
        mutableState.value = null
    }

    @Synchronized
    private fun restartIdleTimer() {
        idleTimer?.cancel()
        idleTimer = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            stop()
        }
    }
}
