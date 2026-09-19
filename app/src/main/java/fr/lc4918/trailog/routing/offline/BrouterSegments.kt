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
class BrouterSegments(private val dirOf: () -> File, private val baseUrl: String = DEFAULT_URL) {

    constructor(dir: File, baseUrl: String = DEFAULT_URL) : this({ dir }, baseUrl)

    /** Le dossier des carres, relu a chaque usage : on peut le changer dans les reglages (cf. BrouterStorage). */
    val dir: File get() = dirOf()

    /** Un carre present sur le telephone. [modifiedMs] est la date du serveur, reportee sur le fichier. */
    data class Installed(val tile: BrouterTile, val bytes: Long, val modifiedMs: Long)

    /** Un carre tel que le serveur le propose. */
    data class Remote(val tile: BrouterTile, val bytes: Long, val modifiedMs: Long)

    fun installed(): List<Installed> =
        dir.listFiles().orEmpty().mapNotNull { f ->
            val t = BrouterTile.parse(f.name).takeIf { f.isFile && f.name.endsWith(".rd5") } ?: return@mapNotNull null
            Installed(t, f.length(), f.lastModified())
        }.sortedBy { it.tile.name }

    fun has(tile: BrouterTile): Boolean = File(dir, tile.fileName).isFile

    fun delete(tile: BrouterTile) {
        File(dir, tile.fileName).delete()
        File(dir, tile.fileName + PART).delete()
    }

    /**
     * L'index du serveur : chaque carre, sa taille et sa date. Null si le serveur n'a pas repondu.
     *
     * Lu dans la page de liste du dossier, la seule chose que le serveur publie : une ligne par fichier, son
     * nom, sa date (en temps universel) et sa taille en octets.
     */
    suspend fun remoteIndex(): Map<BrouterTile, Remote>? = withContext(Dispatchers.IO) {
        val page = runInterruptible { get(baseUrl) } ?: return@withContext null
        parseIndex(page).associateBy { it.tile }
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
            val deja = if (part.isFile) part.length() else 0L
            val conn = (URL(baseUrl.trimEnd('/') + "/" + tile.fileName).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                if (deja > 0) setRequestProperty("Range", "bytes=$deja-")
            }
            try {
                val code = runInterruptible { conn.responseCode }
                // 206 : le serveur reprend la ou l'on s'etait arrete. 200 : il renvoie tout - le debut garde
                // ne vaut plus rien, et l'on repart de zero.
                val depart = when (code) {
                    206 -> deja
                    200 -> 0L
                    else -> return@withContext false
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
                    if (total > 0 && recu != total) return@withContext false
                }
                val fini = File(dir, tile.fileName)
                fini.delete()
                if (!part.renameTo(fini)) return@withContext false
                // La date du SERVEUR, et non celle du telechargement : c'est elle qu'on compare a l'index
                // pour savoir si le carre a change depuis.
                if (date > 0) fini.setLastModified(date)
                true
            } catch (e: java.io.IOException) {
                // Le .part reste : la prochaine demande reprendra la ou celle-ci s'est arretee.
                false
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
         * Le carre du serveur est-il plus recent que celui du telephone.
         *
         * A la minute pres : l'index n'en dit pas plus, alors que la date du fichier, reprise de l'en-tete
         * du serveur, porte les secondes. Sans cette marge, chaque carre paraitrait perime d'une minute.
         */
        fun outdated(local: Installed, remote: Remote?): Boolean =
            remote != null && remote.modifiedMs > local.modifiedMs + 60_000L
    }
}
