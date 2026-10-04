package fr.lc4918.trailog.watch

import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.watch.WatchTiles.WatchTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
) {
    companion object {
        /** Sans requete de la montre pendant ce delai, l'envoi se referme : un serveur oublie ouvert ne sert a rien. */
        const val IDLE_TIMEOUT_MS = 15 * 60_000L
        /** Les tuiles standard gardees en memoire : chacune donne quatre tuiles montre, demandees a des moments differents. */
        private const val SOURCE_CACHE_ENTRIES = 64
    }

    data class State(val name: String, val servedCount: Int, val tileCount: Int) {
        val complete: Boolean get() = tileCount > 0 && servedCount >= tileCount
    }

    private val mutableState = MutableStateFlow<State?>(null)
    /** null quand aucun envoi n'est ouvert. */
    val state: StateFlow<State?> = mutableState.asStateFlow()

    private var server: WatchTileServer? = null
    private var source: WatchTileSource? = null
    private var idleTimer: Job? = null

    /**
     * Ouvre l'envoi de [tiles], decoupees dans [provider]. [mbtilesFile] est le fichier du fond quand il est
     * de type MBTiles. Remplace l'envoi precedent. Rend false si le serveur n'a pas pu ouvrir son port.
     */
    // Synchronise : le serveur rappelle depuis son propre fil, la minuterie depuis une coroutine.
    @Synchronized
    fun start(name: String, tiles: List<WatchTile>, provider: ProviderEntity, mbtilesFile: File?): Boolean {
        stop()
        val tileSource = WatchTileSource(provider, mbtilesFile)
        val sourceCache = object : LinkedHashMap<WatchTiles.SourceQuarter, ByteArray?>(SOURCE_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<WatchTiles.SourceQuarter, ByteArray?>) =
                size > SOURCE_CACHE_ENTRIES
        }
        val routes = WatchRoutes(
            exportName = name,
            tiles = tiles,
            imageOf = { tile ->
                val quarter = WatchTiles.sourceQuarterOf(tile)
                val sourceKey = quarter.copy(quarterColumn = 0, quarterRow = 0)
                val sourceImage = sourceCache.getOrPut(sourceKey) { tileSource.sourceImage(quarter.zoom, quarter.x, quarter.y) }
                sourceImage?.let { WatchTileImages.quarter(it, quarter.quarterColumn, quarter.quarterRow) }
            },
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
