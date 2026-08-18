package fr.lc4918.trailog.map.offline

import java.net.HttpURLConnection
import java.net.URL

/**
 * Récupération HTTP, partagée par le téléchargement de tuiles, les miniatures, le géocodeur et le moteur
 * d'itinéraire.
 *
 * **Aucun `disconnect()` sur le chemin nominal** : il ferme la socket sous-jacente et prive les requêtes
 * suivantes du pool de connexions de `HttpURLConnection`. Chacune devait alors refaire résolution DNS,
 * poignée de main TCP puis TLS - de l'ordre d'une seconde sur un réseau mobile, à chaque frappe dans un
 * champ d'autocomplétion. On se contente de lire le corps jusqu'au bout, ce qui rend la connexion au pool.
 */
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

    /**
     * Envoi d'un corps en POST, même contrat de retour que [fetch].
     *
     * Un seul appelant à ce jour : le dépôt d'un profil de tracé chez BRouter, qui rend l'identifiant sous
     * lequel le rappeler (cf. [fr.lc4918.trailog.routing.Brouter]). Le profil pèse une vingtaine de
     * kilo-octets, bien au-delà de ce qu'une URL accepte - d'où le POST, et non un paramètre de plus.
     */
    fun post(
        url: String, body: ByteArray, contentType: String = "text/plain",
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS, readTimeoutMs: Int = READ_TIMEOUT_MS,
    ): Response {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Content-Type", contentType)
            requestMethod = "POST"
            doOutput = true
        }
        return try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code in 200..299) Response(code, conn.inputStream.use { it.readBytes() })
            else { conn.errorStream?.use { it.readBytes() }; Response(code, null) }
        } catch (e: Exception) {
            e.printStackTrace()
            conn.disconnect()
            Response(0, null)
        }
    }

    /** [headers] porte les en-têtes propres à un service - la clé d'API de DATAtourisme, seul appelant à
     *  ce jour, qui l'attend en `X-API-Key` et non dans l'URL. */
    fun fetch(
        url: String, connectTimeoutMs: Int = CONNECT_TIMEOUT_MS, readTimeoutMs: Int = READ_TIMEOUT_MS,
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            val code = conn.responseCode
            if (code in 200..299) {
                Response(code, conn.inputStream.use { it.readBytes() })
            } else {
                // Le flux d'erreur est VIDE PUIS FERME, jamais laisse en plan : une connexion dont le
                // corps n'a pas ete lu jusqu'au bout ne peut pas retourner au pool, et la suivante devra
                // rouvrir une liaison.
                conn.errorStream?.use { it.readBytes() }
                Response(code, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Seul cas ou l'on coupe pour de bon : la connexion est en mauvais etat, la garder
            // empoisonnerait le pool.
            conn.disconnect()
            Response(0, null)
        }
    }
}
