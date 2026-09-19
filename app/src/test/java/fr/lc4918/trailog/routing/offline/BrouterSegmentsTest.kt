package fr.lc4918.trailog.routing.offline

import fr.lc4918.trailog.map.offline.Bbox
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.concurrent.thread

/**
 * Les donnees de BRouter : la grille de ses carres, l'index du serveur, et le transfert.
 *
 * Une faute ici ne leve rien : un carre mal nomme n'est jamais trouve par le moteur, un fichier tronque
 * rend des trajets faux la ou il s'arrete, et un index mal lu fait croire a une mise a jour permanente.
 */
class BrouterSegmentsTest {

    // ---------- La grille ----------

    @Test fun `un carre se nomme par son coin sud-ouest, comme sur le serveur`() {
        assertEquals("E5_N45", BrouterTile.of(5.72, 45.19).name)
        assertEquals("W5_N40", BrouterTile.of(-2.45, 42.46).name)
        assertEquals("E0_N45", BrouterTile.of(0.0, 45.0).name)
        assertEquals("W5_S20", BrouterTile.of(-0.1, -20.0).name)
        assertEquals("W5_S25", BrouterTile.of(-0.1, -20.5).name)
        assertEquals("E5_N45.rd5", BrouterTile.of(5.72, 45.19).fileName)
    }

    @Test fun `un nom se relit, avec ou sans extension`() {
        assertEquals(BrouterTile(-10, 40), BrouterTile.parse("W10_N40.rd5"))
        assertEquals(BrouterTile(5, -25), BrouterTile.parse("E5_S25"))
        assertNull(BrouterTile.parse("E3_N45.rd5"))
        assertNull(BrouterTile.parse("lookups.dat"))
        assertNull(BrouterTile.parse("E5_N45.rd5.part"))
    }

    @Test fun `le rectangle de la France touche neuf carres`() {
        val france = Bbox(west = -4.8, south = 42.3, east = 8.2, north = 51.1)
        assertEquals(9, BrouterTiles.covering(france).size)
        assertTrue(BrouterTile(5, 45) in BrouterTiles.covering(france))
    }

    /** Un parcours en diagonale ne prend pas le carre qui fermerait son rectangle. */
    @Test fun `un parcours ne prend que les carres qu'il traverse`() {
        val autour = BrouterTiles.along(listOf(44.5 to 4.5, 44.6 to 4.6, 45.5 to 5.5, 46.0 to 6.0), 1000.0)
        assertTrue(BrouterTile(0, 40) in autour)
        assertTrue(BrouterTile(5, 45) in autour)
        assertFalse(BrouterTile(0, 45) in autour)
        // Son rectangle, lui, en prendrait quatre.
        assertEquals(4, BrouterTiles.covering(Bbox(west = 4.5, south = 44.5, east = 6.0, north = 46.0)).size)
    }

    /** Pres d'un bord, le couloir deborde sur le voisin : le trajet peut y passer. */
    @Test fun `le couloir deborde sur le carre voisin pres d'un bord`() {
        val t = BrouterTiles.along(listOf(44.99 to 5.5), 5_000.0)
        assertEquals(setOf(BrouterTile(5, 40), BrouterTile(5, 45)), t)
    }

    // ---------- L'index du serveur ----------

    private val page = """
        <a href="E0_N45.rd5">E0_N45.rd5</a>                                         19-Sep-2026 01:03           127211409
        <a href="E5_N45.rd5">E5_N45.rd5</a>                                         19-Sep-2026 01:03           252331519
        <a href="W10_N45.rd5">W10_N45.rd5</a>                                        12-Sep-2026 23:59              144948
        <a href="lookups.dat">lookups.dat</a>                                        01-Jan-2026 00:00               31604
    """.trimIndent()

    @Test fun `l'index donne chaque carre, sa taille et sa date`() {
        val index = BrouterSegments.parseIndex(page).associateBy { it.tile }
        assertEquals(3, index.size)
        val alpes = index.getValue(BrouterTile(5, 45))
        assertEquals(252331519L, alpes.bytes)
        // 19 septembre 2026 a 01:03 en temps universel
        assertEquals(1789779780000L, alpes.modifiedMs)
        assertEquals(144948L, index.getValue(BrouterTile(-10, 45)).bytes)
    }

    /** A la minute pres : la date du fichier porte les secondes, l'index non. */
    @Test fun `un carre du jour n'est pas perime`() {
        val remote = BrouterSegments.parseIndex(page).first { it.tile == BrouterTile(5, 45) }
        val local = BrouterSegments.Installed(remote.tile, remote.bytes, remote.modifiedMs + 1_000L)
        assertFalse(BrouterSegments.outdated(local, remote))
        val vieux = local.copy(modifiedMs = remote.modifiedMs - 7L * 24 * 3600 * 1000)
        assertTrue(BrouterSegments.outdated(vieux, remote))
        assertFalse("sans index, on ne sait pas", BrouterSegments.outdated(vieux, null))
    }

    // ---------- Le transfert ----------

    private lateinit var serveur: ServerSocket
    private lateinit var dir: File
    private val contenu = ByteArray(300_000) { (it * 31 % 251).toByte() }
    @Volatile private var accepteLesReprises = true
    private val plages = java.util.Collections.synchronizedList(mutableListOf<String?>())

    /**
     * Un serveur HTTP minimal sur une socket : celui du JDK n'est pas sur le chemin des tests Android. Il
     * sert [contenu] a toute demande, entier ou a partir de l'octet demande par un en-tete Range.
     */
    @Before fun demarrer() {
        dir = Files.createTempDirectory("rd5").toFile()
        serveur = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!serveur.isClosed) {
                val c = runCatching { serveur.accept() }.getOrNull() ?: break
                c.use { sock ->
                    val lecteur = sock.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    var range: String? = null
                    while (true) {
                        val l = lecteur.readLine() ?: break
                        if (l.isEmpty()) break
                        if (l.startsWith("Range:", ignoreCase = true)) range = l.substringAfter(':').trim()
                    }
                    plages += range
                    val debut = range?.takeIf { accepteLesReprises }?.removePrefix("bytes=")?.removeSuffix("-")?.toInt()
                    val depart = debut ?: 0
                    val statut = if (debut != null) "206 Partial Content" else "200 OK"
                    val out = sock.getOutputStream()
                    out.write(("HTTP/1.1 $statut\r\nContent-Length: ${contenu.size - depart}\r\n" +
                        "Last-Modified: Sat, 19 Sep 2026 01:03:01 GMT\r\nConnection: close\r\n\r\n")
                        .toByteArray(Charsets.ISO_8859_1))
                    out.write(contenu, depart, contenu.size - depart)
                    out.flush()
                }
            }
        }
    }

    @After fun arreter() {
        serveur.close()
        dir.deleteRecursively()
    }

    private fun segments() = BrouterSegments(dir, "http://127.0.0.1:${serveur.localPort}/segments4/")

    @Test fun `un carre telecharge porte son nom, et la date du serveur`() = runBlocking {
        val s = segments()
        val t = BrouterTile(5, 45)
        assertTrue(s.download(t))
        val f = File(dir, "E5_N45.rd5")
        assertArrayEquals(contenu, f.readBytes())
        assertFalse(File(dir, "E5_N45.rd5.part").exists())
        assertEquals(1789779781000L, f.lastModified())
        assertEquals(listOf(t), s.installed().map { it.tile })
    }

    /** Un transfert interrompu reprend la ou il s'etait arrete, et le fichier final est entier. */
    @Test fun `un transfert interrompu reprend la ou il s'etait arrete`() = runBlocking {
        File(dir, "E5_N45.rd5.part").writeBytes(contenu.copyOf(120_000))
        assertTrue(segments().download(BrouterTile(5, 45)))
        assertEquals("bytes=120000-", plages.single())
        assertArrayEquals(contenu, File(dir, "E5_N45.rd5").readBytes())
    }

    /** Un serveur qui ignore la reprise renvoie tout : on repart de zero, sans coller deux debuts. */
    @Test fun `sans reprise possible, on repart de zero`() = runBlocking {
        accepteLesReprises = false
        File(dir, "E5_N45.rd5.part").writeBytes(ByteArray(50_000) { 7 })
        assertTrue(segments().download(BrouterTile(5, 45)))
        assertArrayEquals(contenu, File(dir, "E5_N45.rd5").readBytes())
    }

    /** Un fichier en cours ne se lit jamais comme un carre : le moteur le prendrait pour entier. */
    @Test fun `un fichier partiel n'est pas un carre installe`() {
        File(dir, "E5_N45.rd5.part").writeBytes(ByteArray(10))
        assertTrue(segments().installed().isEmpty())
        assertFalse(segments().has(BrouterTile(5, 45)))
    }

    @Test fun `supprimer retire le carre et son debut`() = runBlocking {
        val s = segments()
        s.download(BrouterTile(5, 45))
        File(dir, "E5_N45.rd5.part").writeBytes(ByteArray(10))
        s.delete(BrouterTile(5, 45))
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }
}
