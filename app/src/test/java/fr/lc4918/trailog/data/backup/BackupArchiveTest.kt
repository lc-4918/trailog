package fr.lc4918.trailog.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Sauvegarde et restauration.
 *
 * Ce sont, avec les migrations, les seuls tests dont l'echec se paie en **donnees perdues** : une archive
 * incomplete ne se voit qu'au moment ou l'on en a besoin - c'est-a-dire quand l'original n'existe plus -
 * et une restauration qui se lance sur n'importe quel fichier detruit ce qu'elle devait sauver.
 */
class BackupArchiveTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, content: String) =
        File(dir, name).apply { parentFile?.mkdirs(); writeText(content) }

    /** Une installation : une base, des geometries, des images. */
    private class Install(root: File) {
        val db = File(root, "trailog.db")
        val layers = File(root, "layers").apply { mkdirs() }
        val images = File(root, "images").apply { mkdirs() }
        val staging = File(root, "staging")
        val dirs get() = listOf(BackupDir("layers", layers), BackupDir("images", images))
    }

    private fun install(name: String) = Install(tmp.newFolder(name))

    private fun archiveOf(source: Install): ByteArray {
        val out = ByteArrayOutputStream()
        BackupArchive.create(out, source.db, source.dirs, 1_600_000_000_000)
        return out.toByteArray()
    }

    // ---------- Aller-retour ----------

    @Test fun `la sauvegarde rend la base, les geometries et les images`() {
        val a = install("source")
        a.db.writeText("BASE")
        file(a.layers, "layer_1.geojson", "{}")
        file(a.layers, "layer_1.geojson.prof", "[]")
        file(a.images, "photo.jpg", "JPEG")

        val b = install("cible")
        val r = BackupArchive.restore(ByteArrayInputStream(archiveOf(a)), b.db, b.dirs, b.staging)

        assertEquals(BackupArchive.Result.OK, r)
        assertEquals("BASE", b.db.readText())
        assertEquals("{}", File(b.layers, "layer_1.geojson").readText())
        assertEquals("[]", File(b.layers, "layer_1.geojson.prof").readText())
        assertEquals("JPEG", File(b.images, "photo.jpg").readText())
    }

    /**
     * Une sauvegarde decrit un ETAT COMPLET, pas un ajout : ce qui n'y est pas doit disparaitre. Sans
     * cela, une couche supprimee avant la sauvegarde reviendrait a la restauration, et l'application ne
     * serait jamais exactement dans l'etat sauvegarde.
     */
    @Test fun `la restauration efface ce que l'archive ne porte pas`() {
        val a = install("source")
        a.db.writeText("BASE")
        file(a.layers, "gardee.geojson", "{}")

        val b = install("cible")
        file(b.layers, "ancienne.geojson", "{}")
        file(b.images, "vieille-photo.jpg", "JPEG")

        BackupArchive.restore(ByteArrayInputStream(archiveOf(a)), b.db, b.dirs, b.staging)

        assertTrue(File(b.layers, "gardee.geojson").exists())
        assertFalse("une couche absente de l'archive a survecu", File(b.layers, "ancienne.geojson").exists())
        assertFalse(File(b.images, "vieille-photo.jpg").exists())
    }

    /** Les journaux de la base d'AVANT decrivent des pages qui n'existent plus dans celle qu'on pose :
     *  les laisser corromprait la base restauree des sa premiere ouverture. */
    @Test fun `la restauration retire les journaux de l'ancienne base`() {
        val a = install("source"); a.db.writeText("NEUVE")
        val b = install("cible"); b.db.writeText("ANCIENNE")
        File(b.db.parentFile, "trailog.db-wal").writeText("journal")
        File(b.db.parentFile, "trailog.db-shm").writeText("memoire")

        BackupArchive.restore(ByteArrayInputStream(archiveOf(a)), b.db, b.dirs, b.staging)

        assertEquals("NEUVE", b.db.readText())
        assertFalse(File(b.db.parentFile, "trailog.db-wal").exists())
        assertFalse(File(b.db.parentFile, "trailog.db-shm").exists())
    }

    // ---------- Ce qui est refuse ----------

    /** Un zip quelconque - une archive de photos, un export d'une autre application - ne doit pas se
     *  deverser par-dessus les donnees. C'est l'entete qui distingue une sauvegarde d'un zip. */
    @Test fun `un zip sans entete n'est pas une sauvegarde, et ne touche a rien`() {
        val etranger = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("layers/piege.geojson")); zip.write("{}".toByteArray()); zip.closeEntry()
            }
        }.toByteArray()

        val b = install("cible")
        b.db.writeText("INTACTE")
        file(b.layers, "mienne.geojson", "{}")

        val r = BackupArchive.restore(ByteArrayInputStream(etranger), b.db, b.dirs, b.staging)

        assertEquals(BackupArchive.Result.NOT_A_BACKUP, r)
        assertEquals("INTACTE", b.db.readText())
        assertTrue("mes donnees ont ete touchees", File(b.layers, "mienne.geojson").exists())
        assertFalse(File(b.layers, "piege.geojson").exists())
    }

    /** Une archive d'une version future serait lue de travers : mieux vaut le dire que restaurer a moitie. */
    @Test fun `une sauvegarde d'une version plus recente est refusee`() {
        val future = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(BackupArchive.MANIFEST))
                zip.write("""{"format":${BackupArchive.FORMAT + 1}}""".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val b = install("cible")
        b.db.writeText("INTACTE")
        val r = BackupArchive.restore(ByteArrayInputStream(future), b.db, b.dirs, b.staging)

        assertEquals(BackupArchive.Result.UNSUPPORTED_FORMAT, r)
        assertEquals("INTACTE", b.db.readText())
    }

    /**
     * Une entree nommee `../autre.db` ferait ecrire l'archive HORS du dossier de travail, n'importe ou
     * dans l'espace de l'application. C'est une faiblesse connue des lecteurs de zip, et une archive vient
     * de l'exterieur.
     */
    @Test fun `une entree qui sort du dossier de travail est ignoree`() {
        val root = tmp.newFolder("travail")
        assertNull(BackupArchive.safeTarget(root, "../evasion.db"))
        assertNull(BackupArchive.safeTarget(root, "layers/../../evasion.db"))
        assertNull(BackupArchive.safeTarget(root, "/etc/passwd"))
        assertEquals(File(root, "layers/ok.geojson"), BackupArchive.safeTarget(root, "layers/ok.geojson"))
    }

    /** L'espace de travail est vide a la fin, y compris quand l'archive est refusee : c'est un espace de
     *  travail, pas un cache, et il porte une copie entiere des donnees. */
    @Test fun `l'espace de travail ne survit pas a la restauration`() {
        val a = install("source"); a.db.writeText("BASE")
        val b = install("cible")
        BackupArchive.restore(ByteArrayInputStream(archiveOf(a)), b.db, b.dirs, b.staging)
        assertFalse(b.staging.exists())

        BackupArchive.restore(ByteArrayInputStream(ByteArray(0)), b.db, b.dirs, b.staging)
        assertFalse(b.staging.exists())
    }

    // ---------- Nom du fichier ----------

    /** Date a l'envers : l'ordre alphabetique du gestionnaire de fichiers devient l'ordre chronologique. */
    @Test fun `le nom propose porte la date, la plus recente en dernier`() {
        val janvier = BackupFileName.of(1_704_100_000_000)   // 2024-01-01
        val mars = BackupFileName.of(1_710_000_000_000)      // 2024-03-09
        assertTrue(janvier, janvier.startsWith("trailog-2024-01-"))
        assertTrue(janvier.endsWith(".zip"))
        assertTrue("$janvier doit preceder $mars", janvier < mars)
    }
}
