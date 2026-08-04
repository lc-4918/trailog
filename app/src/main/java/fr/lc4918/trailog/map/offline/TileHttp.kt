package fr.lc4918.trailog.map.offline

import java.net.HttpURLConnection
import java.net.URL

/** Récupération HTTP d'une tuile (partagée par le moteur de téléchargement et les miniatures). */
object TileHttp {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val USER_AGENT = "Trailog/1.0 (Android)"

    /** Statut HTTP et corps de la réponse. [status] vaut 0 quand la requête n'a pas abouti du tout
     *  (pas de réseau, délai dépassé) : un appelant qui doit distinguer "le serveur répond qu'il n'y
     *  a rien ici" d'une panne réseau ne peut pas se contenter d'un corps null (cf. CoverageProbe). */
    class Response(val status: Int, val body: ByteArray?)

    /** Renvoie les octets de la tuile, ou null en cas d'échec (statut non 2xx, exception réseau). */
    fun get(url: String): ByteArray? = fetch(url).body

    fun fetch(url: String, connectTimeoutMs: Int = CONNECT_TIMEOUT_MS, readTimeoutMs: Int = READ_TIMEOUT_MS): Response {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            val code = conn.responseCode
            Response(code, if (code in 200..299) conn.inputStream.use { it.readBytes() } else null)
        } catch (e: Exception) {
            e.printStackTrace()
            Response(0, null)
        } finally {
            conn.disconnect()
        }
    }
}
