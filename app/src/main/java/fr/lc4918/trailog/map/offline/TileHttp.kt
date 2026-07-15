package fr.lc4918.trailog.map.offline

import java.net.HttpURLConnection
import java.net.URL

/** Récupération HTTP d'une tuile (partagée par le moteur de téléchargement et les miniatures). */
object TileHttp {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val USER_AGENT = "Trailog/1.0 (Android)"

    /** Renvoie les octets de la tuile, ou null en cas d'échec (statut non 2xx, exception réseau). */
    fun get(url: String): ByteArray? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes() } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            conn.disconnect()
        }
    }
}
