package fr.lc4918.trailog.routing.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * La liste des zones telechargees : celles qu'on a demandees, et celles que leurs carres rendent completes.
 */
class BrouterDownloadsTest {

    private val dir: File = Files.createTempDirectory("zones").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After fun nettoyer() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun poser(zone: BrouterZone) = zone.tiles.forEach { File(dir, it.fileName).writeBytes(ByteArray(8)) }

    private fun file() = BrouterDownloads(BrouterSegments(dir, "http://127.0.0.1:1/"), scope)

    /** Apres la France, les Pyrenees - deux carres francais - sont la sans rien telecharger. */
    @Test fun `une zone dont tous les carres sont la entre dans la liste`() {
        poser(BrouterZones.byId("fr")!!)
        File(dir, "zones.txt").writeText("fr")
        val zones = file().state.value.zones
        assertTrue("pyrenees" in zones)
        assertTrue("fr" in zones)
        assertFalse("il manque aux Alpes leurs carres de l'est", "alps" in zones)
    }

    /** Supprimee a la main, elle ne revient pas d'elle-meme, meme si ses carres restent pour la France. */
    @Test fun `une zone supprimee ne revient pas d'elle-meme`() {
        poser(BrouterZones.byId("fr")!!)
        File(dir, "zones.txt").writeText("fr\npyrenees")
        val f = file()
        f.deleteZone(BrouterZones.byId("pyrenees")!!)
        assertFalse("pyrenees" in f.state.value.zones)
        assertEquals("ses carres restent pour la France", 9, dir.listFiles { x -> x.name.endsWith(".rd5") }!!.size)
        assertFalse("au lancement suivant non plus", "pyrenees" in file().state.value.zones)
        assertTrue("fr" in file().state.value.zones)
    }

    /** Supprimer la France quand les Pyrenees sont la garde leurs deux carres. */
    @Test fun `supprimer une zone garde les carres d'une zone qui reste`() {
        poser(BrouterZones.byId("fr")!!)
        File(dir, "zones.txt").writeText("fr\npyrenees")
        val f = file()
        f.deleteZone(BrouterZones.byId("fr")!!)
        val restants = dir.listFiles { x -> x.name.endsWith(".rd5") }!!.map { it.name }.toSet()
        assertEquals(setOf("W5_N40.rd5", "E0_N40.rd5"), restants)
        assertEquals(setOf("pyrenees"), f.state.value.zones)
    }

    /** Changer de dossier deplace les carres et les registres : rien n'est a retelecharger. */
    @Test fun `changer de dossier deplace les donnees`(): Unit = kotlinx.coroutines.runBlocking {
        poser(BrouterZones.byId("pyrenees")!!)
        File(dir, "zones.txt").writeText("pyrenees")
        File(dir, "E5_N45.rd5.part").writeBytes(ByteArray(3))
        val cible = Files.createTempDirectory("ailleurs").toFile()
        var courant = dir
        val f = BrouterDownloads(BrouterSegments({ courant }, "http://127.0.0.1:1/"), scope)
        assertTrue(f.moveTo(cible) { courant = cible })
        assertEquals(setOf("W5_N40.rd5", "E0_N40.rd5", "E5_N45.rd5.part", "zones.txt"),
            cible.listFiles()!!.map { it.name }.toSet())
        assertTrue("l'ancien dossier est vide", dir.listFiles()!!.isEmpty())
        assertEquals(2, f.state.value.installed.size)
        assertTrue("pyrenees" in f.state.value.zones)
        cible.deleteRecursively()
    }
}
