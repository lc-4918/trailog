package fr.lc4918.trailog.routing.offline

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread

/**
 * Le telechargement de l'index des lieux d'un carre : transfert compresse, reprise, decompression, et ce
 * qui ne doit jamais arriver - un fichier tronque sous le nom d'un index.
 */
class BrouterGeocodeDownloadTest {

    private lateinit var server: ServerSocket
    private lateinit var dir: File
    private val raw = ByteArray(200_000) { (it * 7 % 253).toByte() }
    private val packed: ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(raw) } }.toByteArray()
    private val ranges = java.util.Collections.synchronizedList(mutableListOf<String?>())
    private val requested = java.util.Collections.synchronizedList(mutableListOf<String>())
    @Volatile private var status = 200

    @Before fun start() {
        dir = Files.createTempDirectory("gc").toFile()
        server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use { sock ->
                    val reader = sock.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    var range: String? = null
                    var path = ""
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("GET ")) path = line.split(' ')[1]
                        if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
                    }
                    requested += path
                    ranges += range
                    val out = sock.getOutputStream()
                    if (status != 200) {
                        out.write("HTTP/1.1 $status Nope\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        return@use
                    }
                    val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
                    val code = if (range != null) "206 Partial Content" else "200 OK"
                    out.write(("HTTP/1.1 $code\r\nContent-Length: ${packed.size - from}\r\nConnection: close\r\n\r\n")
                        .toByteArray(Charsets.ISO_8859_1))
                    out.write(packed, from, packed.size - from)
                    out.flush()
                }
            }
        }
    }

    @After fun stop() {
        server.close()
        dir.deleteRecursively()
    }

    private val tile = BrouterTile(5, 45)
    private fun remote(rawBytes: Long = raw.size.toLong()) =
        BrouterSegments.GeocodeRemote(packed.size.toLong(), rawBytes, 1_791_359_138_000L)

    private fun segments() = BrouterSegments(dir, "http://127.0.0.1:1/", "http://127.0.0.1:${server.localPort}/gc/")

    @Test fun `l'index est telecharge puis decompresse sous son nom`() = runBlocking {
        assertTrue(segments().downloadGeocode(tile, remote()))
        assertArrayEquals(raw, File(dir, "E5_N45.gc.sqlite").readBytes())
        assertEquals("/gc/E5_N45.gc.sqlite.gz", requested.single())
        assertEquals("la date du serveur est reportee", 1_791_359_138_000L, File(dir, "E5_N45.gc.sqlite").lastModified())
        assertEquals("rien d'autre ne reste", listOf("E5_N45.gc.sqlite"), dir.listFiles()!!.map { it.name })
    }

    /** La progression compte des octets decompresses, ceux que le poids annonce, et finit sur le total. */
    @Test fun `la progression est a l'echelle des octets decompresses`() = runBlocking {
        val seen = mutableListOf<Pair<Long, Long>>()
        segments().downloadGeocode(tile, remote()) { got, total -> seen += got to total }
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it.second == raw.size.toLong() })
        assertTrue(seen.zipWithNext().all { (a, b) -> a.first <= b.first })
        assertEquals(raw.size.toLong(), seen.last().first)
    }

    @Test fun `un transfert interrompu reprend ou il s'etait arrete`() = runBlocking {
        File(dir, "E5_N45.gc.sqlite.gz.part").writeBytes(packed.copyOf(100))
        assertTrue(segments().downloadGeocode(tile, remote()))
        assertEquals("bytes=100-", ranges.single())
        assertArrayEquals(raw, File(dir, "E5_N45.gc.sqlite").readBytes())
    }

    /** Un index qui ne fait pas la taille annoncee n'est jamais installe : il se lirait sans erreur. */
    @Test fun `un index de la mauvaise taille n'est pas installe`() = runBlocking {
        assertFalse(segments().downloadGeocode(tile, remote(rawBytes = raw.size + 1L)))
        assertFalse(File(dir, "E5_N45.gc.sqlite").exists())
        assertTrue("rien de tronque ne reste sous un nom d'index", dir.listFiles()!!.none { it.name.endsWith(".gc.sqlite") })
    }

    @Test fun `un serveur qui refuse laisse le carre sans index`() = runBlocking {
        status = 404
        assertFalse(segments().downloadGeocode(tile, remote()))
        assertFalse(File(dir, "E5_N45.gc.sqlite").exists())
    }

    /** Rien de publie pour ce carre : il n'y a rien a attendre, et rien n'est demande. */
    @Test fun `sans index publie il n'y a rien a faire`() = runBlocking {
        assertTrue(segments().downloadGeocode(tile, null))
        assertTrue(requested.isEmpty())
    }

    /** Un index deja la, aussi recent que celui du serveur, n'est pas retelecharge. */
    @Test fun `un index a jour n'est pas retelecharge`() = runBlocking {
        File(dir, "E5_N45.gc.sqlite").apply { writeBytes(ByteArray(5)); setLastModified(remote().modifiedMs) }
        assertTrue(segments().downloadGeocode(tile, remote()))
        assertTrue(requested.isEmpty())
        assertEquals(5L, File(dir, "E5_N45.gc.sqlite").length())
    }

    @Test fun `un index plus ancien que celui du serveur est remplace`() = runBlocking {
        File(dir, "E5_N45.gc.sqlite").apply { writeBytes(ByteArray(5)); setLastModified(remote().modifiedMs - 86_400_000L) }
        assertTrue(segments().downloadGeocode(tile, remote()))
        assertArrayEquals(raw, File(dir, "E5_N45.gc.sqlite").readBytes())
    }

    /** L'index des lieux est un plus : un serveur muet ne fait pas echouer la lecture de celui des carres. */
    @Test fun `un serveur muet donne un index de lieux vide`() = runBlocking {
        val s = BrouterSegments(dir, "http://127.0.0.1:1/", "http://127.0.0.1:1/")
        assertTrue(s.geocodeIndex().isEmpty())
    }
}
