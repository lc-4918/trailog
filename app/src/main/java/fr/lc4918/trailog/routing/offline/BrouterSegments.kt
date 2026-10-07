package fr.lc4918.trailog.routing.offline

import fr.lc4918.trailog.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.coroutineContext

/**
 * Les donnees de BRouter sur le telephone : ce qu'on a, ce que le serveur propose, et le transfert entre
 * les deux.
 *
 * Un fichier par carre de cinq degres (cf. [BrouterTile]), de quelques centaines de kilo-octets pour un
 * carre de mer a deux cent cinquante megaoctets pour les Alpes. Le serveur les regenere chaque semaine ; un
 * carre est A JOUR tant que sa date n'est pas anterieure a celle du serveur.
 *
 * **Un fichier incomplet ne porte jamais le nom d'un carre** : il s'ecrit sous `.part`, et ne prend son nom
 * qu'une fois entier. Le moteur, qui cherche `E5_N45.rd5`, ne lit donc jamais un fichier tronque - il le
 * lirait sans erreur, et rendrait des trajets faux la ou les donnees s'arretent. Le `.part` sert aussi a
 * reprendre un transfert interrompu la ou il s'etait arrete : le serveur accepte les demandes partielles.
 */
class BrouterSegments(
    private val dirOf: () -> File,
    private val baseUrl: String = DEFAULT_URL,
    /** Ou sont publies les index de lieux, un par carre (cf. `tools/geocode-index`). */
    private val geocodeUrl: String = DEFAULT_GEOCODE_URL,
) {

    constructor(dir: File, baseUrl: String = DEFAULT_URL, geocodeUrl: String = DEFAULT_GEOCODE_URL) :
        this({ dir }, baseUrl, geocodeUrl)

    /** Le dossier des carres, relu a chaque usage : on peut le changer dans les reglages (cf. BrouterStorage). */
    val dir: File get() = dirOf()

    /**
     * Un carre present sur le telephone. [modifiedMs] est la date du serveur, reportee sur le fichier.
     *
     * [bytes] est ce que le carre pese sur le telephone : ses donnees d'itineraire ET l'index de ses lieux
     * quand il est la ([hasGeocode]) - les deux voyagent ensemble, et c'est ce total que la progression
     * d'une zone compte (cf. [Remote.bytes]).
     */
    data class Installed(val tile: BrouterTile, val bytes: Long, val modifiedMs: Long, val hasGeocode: Boolean = false)

    /** L'index de lieux d'un carre tel que le serveur le propose : sa taille a transferer, sa taille une
     *  fois decompresse, et sa date. */
    data class GeocodeRemote(val packedBytes: Long, val rawBytes: Long, val modifiedMs: Long)

    /**
     * Un carre tel que le serveur le propose. [bytes] est le poids qu'il aura sur le telephone : les donnees
     * d'itineraire, plus l'index de ses lieux s'il en a un ([geocode]) - cf. [Installed.bytes].
     */
    data class Remote(val tile: BrouterTile, val bytes: Long, val modifiedMs: Long, val geocode: GeocodeRemote? = null)

    fun installed(): List<Installed> =
        dir.listFiles().orEmpty().mapNotNull { f ->
            val t = BrouterTile.parse(f.name).takeIf { f.isFile && f.name.endsWith(".rd5") } ?: return@mapNotNull null
            val geocode = File(dir, t.geocodeFileName).takeIf { it.isFile }
            Installed(t, f.length() + (geocode?.length() ?: 0L), f.lastModified(), geocode != null)
        }.sortedBy { it.tile.name }

    fun has(tile: BrouterTile): Boolean = File(dir, tile.fileName).isFile

    fun hasGeocode(tile: BrouterTile): Boolean = File(dir, tile.geocodeFileName).isFile

    fun delete(tile: BrouterTile) {
        File(dir, tile.fileName).delete()
        File(dir, tile.fileName + PART).delete()
        File(dir, tile.geocodeFileName).delete()
        File(dir, tile.geocodeFileName + PACKED + PART).delete()
        File(dir, tile.geocodeFileName + PART).delete()
    }

    /**
     * L'index du serveur : chaque carre, sa taille et sa date. Null si le serveur n'a pas repondu.
     *
     * Lu dans la page de liste du dossier, la seule chose que le serveur publie : une ligne par fichier, son
     * nom, sa date (en temps universel) et sa taille en octets.
     */
    suspend fun remoteIndex(): Map<BrouterTile, Remote>? = withContext(Dispatchers.IO) {
        val page = runInterruptible { get(baseUrl) } ?: return@withContext null
        // L'index des lieux est un plus : sans lui - serveur muet, rien de publie -, les carres restent ce
        // qu'ils etaient, et ne sont pas donnes pour perimes.
        val lieux = geocodeIndex()
        parseIndex(page).associate { r ->
            val g = lieux[r.tile]
            r.tile to if (g == null) r else r.copy(bytes = r.bytes + g.rawBytes, geocode = g)
        }
    }

    /** L'index des lieux publies : vide si le serveur n'a pas repondu - ou n'a encore rien. */
    suspend fun geocodeIndex(): Map<BrouterTile, GeocodeRemote> = withContext(Dispatchers.IO) {
        val page = runInterruptible { get(geocodeUrl.trimEnd('/') + "/index.txt") } ?: return@withContext emptyMap()
        parseGeocodeIndex(page)
    }

    /**
     * Telecharge un carre, en reprenant un transfert interrompu. Rend vrai s'il est entier.
     *
     * [onProgress] recoit les octets deja la et le total attendu, a chaque bloc lu.
     */
    suspend fun download(tile: BrouterTile, onProgress: (Long, Long) -> Unit = { _, _ -> }): Boolean =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val part = File(dir, tile.fileName + PART)
            val date = transfer(baseUrl.trimEnd('/') + "/" + tile.fileName, part, onProgress) ?: return@withContext false
            val fini = File(dir, tile.fileName)
            fini.delete()
            if (!part.renameTo(fini)) return@withContext false
            // La date du SERVEUR, et non celle du telechargement : c'est elle qu'on compare a l'index
            // pour savoir si le carre a change depuis.
            if (date > 0) fini.setLastModified(date)
            true
        }

    /**
     * Telecharge l'index des lieux d'un carre, compresse, puis le decompresse a cote. Rend vrai s'il est la
     * en entier - ou si le serveur n'en publie pas pour ce carre : il n'y a alors rien a attendre.
     *
     * **Meme garde que les donnees d'itineraire** : le fichier ne prend son nom qu'entier, et son debut est
     * garde pour une reprise. Un index tronque se lirait sans erreur et ne rendrait qu'une partie des lieux.
     *
     * [onProgress] recoit des octets DECOMPRESSES - ceux que [Remote.bytes] annonce -, le transfert etant
     * ramene a cette echelle : la barre d'une zone additionne des octets de meme nature.
     */
    suspend fun downloadGeocode(
        tile: BrouterTile, remote: GeocodeRemote?, onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Boolean = withContext(Dispatchers.IO) {
        if (remote == null) return@withContext true
        dir.mkdirs()
        val final = File(dir, tile.geocodeFileName)
        // Deja la, et pas plus ancien que le serveur : rien a refaire.
        if (final.isFile && final.lastModified() + MINUTE_MS >= remote.modifiedMs) return@withContext true
        val packed = File(dir, tile.geocodeFileName + PACKED + PART)
        val scale = remote.rawBytes.toDouble() / remote.packedBytes.coerceAtLeast(1)
        transfer(geocodeUrl.trimEnd('/') + "/" + tile.name + GEOCODE_REMOTE_SUFFIX, packed) { got, _ ->
            onProgress((got * scale).toLong(), remote.rawBytes)
        } ?: return@withContext false
        val unpacked = File(dir, tile.geocodeFileName + PART)
        try {
            runInterruptible {
                java.util.zip.GZIPInputStream(packed.inputStream().buffered(BLOCK)).use { ins ->
                    unpacked.outputStream().buffered(BLOCK).use { ins.copyTo(it, BLOCK) }
                }
            }
        } catch (e: java.io.IOException) {
            // Un fichier compresse abime ne se reprend pas : on le jette, la prochaine demande repart de zero.
            packed.delete()
            unpacked.delete()
            return@withContext false
        }
        if (unpacked.length() != remote.rawBytes) { unpacked.delete(); packed.delete(); return@withContext false }
        final.delete()
        if (!unpacked.renameTo(final)) return@withContext false
        final.setLastModified(remote.modifiedMs)
        packed.delete()
        true
    }

    /**
     * Le transfert lui-meme : [url] vers [part], en reprenant ce qui y est deja. Rend la date du serveur
     * (0 s'il n'en donne pas), ou null si le fichier n'est pas arrive en entier - [part] reste alors pour
     * une reprise.
     */
    private suspend fun transfer(url: String, part: File, onProgress: (Long, Long) -> Unit): Long? {
        val deja = if (part.isFile) part.length() else 0L
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            if (deja > 0) setRequestProperty("Range", "bytes=$deja-")
        }
        return try {
            val code = runInterruptible { conn.responseCode }
            // 206 : le serveur reprend la ou l'on s'etait arrete. 200 : il renvoie tout - le debut garde
            // ne vaut plus rien, et l'on repart de zero.
            val depart = when (code) {
                206 -> deja
                200 -> 0L
                else -> return null
            }
            val total = depart + conn.contentLengthLong.coerceAtLeast(0)
            val date = conn.lastModified
            RandomAccessFile(part, "rw").use { out ->
                out.setLength(depart)
                out.seek(depart)
                var recu = depart
                val tampon = ByteArray(BLOCK)
                conn.inputStream.use { ins ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = runInterruptible { ins.read(tampon) }
                        if (n < 0) break
                        out.write(tampon, 0, n)
                        recu += n
                        onProgress(recu, total)
                    }
                }
                if (total > 0 && recu != total) return null
            }
            date
        } catch (e: java.io.IOException) {
            // Le .part reste : la prochaine demande reprendra la ou celle-ci s'est arretee.
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun get(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes().decodeToString() } else null
        } catch (e: java.io.IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEFAULT_URL = "https://brouter.de/brouter/segments4/"

        /**
         * Ou sont publies les index de lieux : les fichiers que `tools/geocode-index/build_index.py` ecrit,
         * dans une release du depot.
         */
        const val DEFAULT_GEOCODE_URL = "https://github.com/lc-4918/trailog/releases/download/geocode-index/"

        /** L'extension d'un index de lieux sur le serveur : la base du carre, compressee. */
        private const val GEOCODE_REMOTE_SUFFIX = BrouterTile.GEOCODE_SUFFIX + ".gz"
        private const val PACKED = ".gz"
        private const val MINUTE_MS = 60_000L
        private const val PART = ".part"
        private const val TIMEOUT_MS = 30_000
        private const val BLOCK = 64 * 1024
        private val USER_AGENT =
            "Trailog/${BuildConfig.VERSION_NAME} (Android; +https://github.com/lc-4918/trailog)"

        /** Une ligne de l'index : `<a href="E5_N45.rd5">E5_N45.rd5</a>   19-Sep-2026 01:03   252331519`. */
        private val LIGNE = Regex(
            "href=\"([EW]\\d+_[NS]\\d+\\.rd5)\"[^\\n]*?(\\d{2}-[A-Za-z]{3}-\\d{4} \\d{2}:\\d{2})\\s+(\\d+)"
        )

        internal fun parseIndex(page: String): List<Remote> {
            val format = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return LIGNE.findAll(page).mapNotNull { m ->
                val tile = BrouterTile.parse(m.groupValues[1]) ?: return@mapNotNull null
                val date = runCatching { format.parse(m.groupValues[2])?.time }.getOrNull() ?: return@mapNotNull null
                Remote(tile, m.groupValues[3].toLong(), date)
            }.toList()
        }

        /**
         * L'index des lieux publie : une ligne par carre - `E5_N45 <octets compresses> <octets decompresses>
         * <date en ms>`. Une ligne illisible est ignoree.
         */
        internal fun parseGeocodeIndex(text: String): Map<BrouterTile, GeocodeRemote> =
            text.lineSequence().mapNotNull { line ->
                val p = line.trim().split(Regex("\\s+"))
                if (p.size != 4) return@mapNotNull null
                val tile = BrouterTile.parse(p[0]) ?: return@mapNotNull null
                val packed = p[1].toLongOrNull() ?: return@mapNotNull null
                val raw = p[2].toLongOrNull() ?: return@mapNotNull null
                val date = p[3].toLongOrNull() ?: return@mapNotNull null
                if (packed <= 0 || raw <= 0) return@mapNotNull null
                tile to GeocodeRemote(packed, raw, date)
            }.toMap()

        /**
         * Le carre du serveur est-il plus recent que celui du telephone.
         *
         * A la minute pres : l'index n'en dit pas plus, alors que la date du fichier, reprise de l'en-tete
         * du serveur, porte les secondes. Sans cette marge, chaque carre paraitrait perime d'une minute.
         */
        fun outdated(local: Installed, remote: Remote?): Boolean =
            rd5Outdated(local, remote) || (remote?.geocode != null && !local.hasGeocode)

        /** Les donnees d'itineraire seules : l'index des lieux, lui, se rattrape sans les retelecharger. */
        fun rd5Outdated(local: Installed, remote: Remote?): Boolean =
            remote != null && remote.modifiedMs > local.modifiedMs + MINUTE_MS
    }
}
