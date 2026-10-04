package fr.lc4918.trailog.watch

import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import kotlin.concurrent.thread

/**
 * Serveur HTTP local qu'interroge la montre, par l'intermediaire de Garmin Connect Mobile.
 *
 * Il n'ecoute que sur l'interface de bouclage : Garmin Connect Mobile tourne sur le meme telephone, et rien
 * d'autre n'a a joindre ce serveur. Les requetes sont traitees une a une, sur un seul fil : la montre n'en
 * envoie qu'une a la fois, et la base MBTiles ([WatchTileSource]) ne se partage pas entre fils.
 *
 * Volontairement minimal - GET seulement, une requete par connexion - plutot qu'une bibliotheque de
 * serveur de plus dans l'application.
 */
class WatchTileServer(private val routes: WatchRoutes, private val port: Int = DEFAULT_PORT) : Closeable {

    companion object {
        /** Connu des deux cotes : l'application montre appelle `http://127.0.0.1:DEFAULT_PORT`. */
        const val DEFAULT_PORT = 22080
        private const val READ_TIMEOUT_MS = 10_000
    }

    private var serverSocket: ServerSocket? = null

    /** Le port reellement ecoute ; utile quand [port] vaut 0 (choisi par le systeme, dans les tests). */
    val localPort: Int get() = serverSocket?.localPort ?: -1

    fun start() {
        val socket = ServerSocket(port, 8, InetAddress.getLoopbackAddress())
        serverSocket = socket
        thread(name = "watch-tile-server", isDaemon = true) {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { handle(it) }
                } catch (exception: SocketException) {
                    // Fermeture du serveur : accept() est interrompu, la boucle s'arrete.
                } catch (exception: Exception) {
                    exception.printStackTrace()
                }
            }
        }
    }

    private fun handle(connection: Socket) {
        connection.soTimeout = READ_TIMEOUT_MS
        val reader = BufferedReader(InputStreamReader(connection.getInputStream(), Charsets.ISO_8859_1))
        val requestLine = reader.readLine() ?: return
        // Les en-tetes ne servent a rien ici, mais doivent etre lus jusqu'a la ligne vide.
        while (!reader.readLine().isNullOrEmpty()) Unit
        val parts = requestLine.split(' ')
        val reply = if (parts.size >= 2 && parts[0] == "GET") routes.respond(parts[1]) else HttpReply.notFound()
        val output = connection.getOutputStream()
        val header = "HTTP/1.1 ${reply.status} ${if (reply.status == 200) "OK" else "Not Found"}\r\n" +
            "Content-Type: ${reply.contentType}\r\n" +
            "Content-Length: ${reply.body.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(reply.body)
        output.flush()
    }

    override fun close() {
        serverSocket?.close()
        serverSocket = null
    }
}
