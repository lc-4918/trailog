package fr.lc4918.trailog.map.offline

import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.ui.offline.OfflineDownloadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Moteur de téléchargement des tuiles (SPEC offline_map.md §4). Télécharge en parallèle borné, écrit
 * dans un [MbtilesWriter] via un unique thread consommateur (contrainte de thread de SQLite), et
 * remonte l'avancement par [onProgress]. Annulable via l'annulation de coroutine.
 */
class OfflineTileDownloader(private val provider: ProviderEntity) {

    /** Résultat brut du moteur, avant décision d'enregistrement du provider par le dépôt. */
    sealed interface Outcome {
        data class Success(val format: String, val done: Int, val failed: Int) : Outcome
        data class Failed(val failed: Int) : Outcome
    }

    /**
     * @param onProgress rappelé (done, failed) à chaque tuile traitée ; peut être appelé depuis
     *   plusieurs threads, l'appelant doit être tolérant à la concurrence.
     */
    suspend fun download(
        req: OfflineDownloadRequest,
        writer: MbtilesWriter,
        onProgress: (done: Int, failed: Int) -> Unit,
    ): Outcome {
        val tiles = (req.minZoom..req.maxZoom).flatMap { TileMath.tilesFor(req.bbox, it) }

        val done = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val stopped = AtomicBoolean(false)
        val format = AtomicReference<String?>(null)   // format raster détecté sur la 1re tuile reçue

        // Dispatcher mono-thread dédié à l'écriture : le natif SQLite confine une transaction à son
        // thread ; open/beginBatch/put/commit/metadata/close doivent donc rester sur ce seul thread.
        val writeDispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "mbtiles-writer") }
            .asCoroutineDispatcher()

        try {
            coroutineScope {
                val tileChannel = Channel<Triple<Int, Int, Int>>(Channel.UNLIMITED).apply {
                    tiles.forEach { trySend(it) }; close()
                }
                // File des tuiles téléchargées à écrire ; capacité bornée pour ne pas tout garder en RAM.
                val writeChannel = Channel<Tile>(capacity = 256)

                // Unique consommateur : possède tout le cycle de vie du fichier MBTiles sur son thread.
                val writerJob = launch(writeDispatcher) {
                    writer.open()
                    writer.beginBatch()
                    try {
                        var sinceFlush = 0
                        for (t in writeChannel) {
                            if (format.get() == null) format.set(detectFormat(t.data))
                            writer.putTile(t.z, t.x, t.y, t.data)
                            if (++sinceFlush >= FLUSH_EVERY) { writer.commitBatch(); sinceFlush = 0 }
                        }
                        // Canal fermé normalement : les workers ont terminé, `failed` est définitif.
                        if (!req.continueOnError && failed.get() > 0) {
                            writer.abort()   // arrêt sur erreur : le fichier sera supprimé par le dépôt
                        } else {
                            writer.writeMetadata(req.name, format.get() ?: "png", req.bbox, req.minZoom, req.maxZoom, provider.attribution)
                            writer.close()   // valide le lot en cours puis ferme
                        }
                    } catch (t: Throwable) {
                        writer.abort()       // annulation/erreur : rollback + close sur le thread writer
                        throw t
                    }
                }

                val workers = List(PARALLELISM) {
                    launch(Dispatchers.IO) {
                        for ((x, y, z) in tileChannel) {
                            if (stopped.get()) break
                            ensureActive()
                            val bytes = runCatching { fetch(buildUrl(x, y, z)) }.getOrNull()
                            if (bytes != null && bytes.isNotEmpty()) {
                                writeChannel.send(Tile(x, y, z, bytes))
                                onProgress(done.incrementAndGet(), failed.get())
                            } else {
                                val f = failed.incrementAndGet()
                                onProgress(done.get(), f)
                                if (!req.continueOnError) { stopped.set(true); break }
                            }
                        }
                    }
                }
                workers.forEach { it.join() }
                writeChannel.close()
                writerJob.join()
            }
        } finally {
            writeDispatcher.close()
        }

        return if (!req.continueOnError && failed.get() > 0) Outcome.Failed(failed.get())
        else Outcome.Success(format.get() ?: "png", done.get(), failed.get())
    }

    private class Tile(val x: Int, val y: Int, val z: Int, val data: ByteArray)

    /** Développe les gabarits d'URL supportés par MapLibre : {z}/{x}/{y}, {s}, {KEY}, {bbox-epsg-3857}. */
    private fun buildUrl(x: Int, y: Int, z: Int): String {
        var u = provider.urlTemplate.replace("{KEY}", provider.apiKey ?: "")
        u = u.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
        if (u.contains("{s}")) {
            val subs = provider.subdomains?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            if (!subs.isNullOrEmpty()) u = u.replace("{s}", subs[Math.floorMod(x + y, subs.size)])
        }
        if (u.contains("{bbox-epsg-3857}")) u = u.replace("{bbox-epsg-3857}", mercatorBbox(x, y, z))
        return u
    }

    private fun fetch(url: String): ByteArray? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes() } else null
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val PARALLELISM = 6
        private const val FLUSH_EVERY = 128
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val USER_AGENT = "Trailog/1.0 (offline map download)"
        private const val ORIGIN_SHIFT = 20037508.342789244   // demi-circonférence Web Mercator (m)

        /** Emprise d'une tuile XYZ en EPSG:3857 (mètres), ordre WMS 1.3.0 : minx,miny,maxx,maxy. */
        private fun mercatorBbox(x: Int, y: Int, z: Int): String {
            val size = 2.0 * ORIGIN_SHIFT / (1 shl z)
            val minX = x * size - ORIGIN_SHIFT
            val maxX = minX + size
            val maxY = ORIGIN_SHIFT - y * size
            val minY = maxY - size
            return "$minX,$minY,$maxX,$maxY"
        }

        /** Format raster d'après la signature magique des octets (PNG sinon JPEG par défaut). */
        private fun detectFormat(data: ByteArray): String =
            if (data.size >= 4 && data[0] == 0x89.toByte() && data[1] == 0x50.toByte() &&
                data[2] == 0x4E.toByte() && data[3] == 0x47.toByte()) "png" else "jpg"
    }
}
