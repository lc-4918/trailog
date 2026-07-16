package fr.lc4918.trailog.data.repo

import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.data.db.AppDatabase
import fr.lc4918.trailog.data.imp.EmptyLayerException
import kotlinx.coroutines.flow.first
import fr.lc4918.trailog.data.db.LayerEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Import bout en bout : parsing, ecriture des fichiers, stats et ligne en base. C'est le chemin que suit
 * chaque fichier depose par l'utilisateur.
 */
@RunWith(RobolectricTestRunner::class)
class TrailogRepositoryTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val repo = TrailogRepository(ctx)
    private val db = AppDatabase.get(ctx)

    private fun bytes(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("fichiers/$name")!!.readBytes()

    /** Derniere couche inseree. La base est partagee par les methodes de la classe : se fier a l'ordre
     *  de la liste rendrait les tests dependants les uns des autres. */
    private suspend fun derniereCouche(): LayerEntity = db.layers().all().first().maxByOrNull { it.id }!!

    // ---------- Import nominal ----------

    @Test fun `un gpx 3D importe cree une couche avec ses stats`() = runTest {
        val bounds = repo.importLayer(bytes("wikiloc_soreze-fontbruno-arfons-berniquaut.gpx"),
            "wikiloc.gpx", folderId = null)
        val l = derniereCouche()
        assertTrue("la trace doit etre marquee", l.hasLine)
        assertTrue("les waypoints doivent etre marques", l.hasPoints)
        assertTrue("altitude detectee", l.hasZ)
        assertTrue("temps detecte", l.hasTime)
        assertTrue("distance calculee", l.distance > 1000.0)
        assertTrue("denivele positif calcule", l.ascent > 0.0)
        // bounds : Montagne Noire
        assertEquals(4, bounds.size)
        assertTrue(bounds[0] in 1.5..2.5 && bounds[1] in 43.0..44.0)
        assertTrue(bounds[0] <= bounds[2] && bounds[1] <= bounds[3])
    }

    /** Cas 2D : la trace s'importe, mais hasZ reste faux. C'est lui qui declenche la banniere
     *  "Parcours sans altimetrie" a l'affichage du profil. */
    @Test fun `un gpx 2D importe sans altimetrie`() = runTest {
        repo.importLayer(bytes("test_locus.gpx"), "locus.gpx", folderId = null)
        val l = derniereCouche()
        assertTrue(l.hasLine)
        assertFalse("aucune altitude dans ce fichier", l.hasZ)
        assertEquals("altitude min sans Z", 0.0, l.minEle, 1e-9)
        assertEquals("altitude max sans Z", 0.0, l.maxEle, 1e-9)
    }

    @Test fun `l'import ecrit le fichier de geometrie et son rendu`() = runTest {
        repo.importLayer(bytes("test_locus.gpx"), "locus.gpx", folderId = null)
        val l = derniereCouche()
        val src = java.io.File(java.io.File(ctx.filesDir, "layers"), l.geometryFile)
        val map = java.io.File(java.io.File(ctx.filesDir, "layers"), l.geometryFile + ".map")
        assertTrue("fichier source absent", src.exists() && src.length() > 0)
        assertTrue("fichier de rendu absent", map.exists() && map.length() > 0)
    }

    @Test fun `un kmz s'importe comme son kml`() = runTest {
        repo.importLayer(bytes("test.kmz"), "test.kmz", folderId = null)
        val l = derniereCouche()
        assertTrue(l.hasLine)
    }

    // ---------- Fichiers refuses ----------

    /** Un fichier lisible mais sans geometrie est refuse AVANT toute ecriture : une couche vide
     *  n'apparaitrait nulle part sur la carte et polluerait l'arborescence. */
    @Test fun `un fichier vide leve EmptyLayerException`() = runTest {
        val avant = db.layers().all().first().size
        assertThrows(EmptyLayerException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.importLayer("""{"type":"FeatureCollection","features":[]}""".toByteArray(),
                    "vide.geojson", null)
            }
        }
        assertEquals("aucune couche ne doit avoir ete creee", avant, db.layers().all().first().size)
    }

    @Test fun `EmptyLayerException porte le nom du fichier fautif`() = runTest {
        val e = assertThrows(EmptyLayerException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.importLayer("""{"type":"FeatureCollection","features":[]}""".toByteArray(),
                    "ma_rando.geojson", null)
            }
        }
        assertEquals("ma_rando.geojson", e.fileName)
    }

    @Test fun `un fichier mal forme leve, sans rien ecrire`() = runTest {
        val avant = db.layers().all().first().size
        assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking { repo.importLayer("{pas du json".toByteArray(), "x.geojson", null) }
        }
        assertEquals(avant, db.layers().all().first().size)
    }

    // ---------- Amorcage ----------

    @Test fun `l'amorcage installe les fonds et les reglages`() = runTest {
        repo.ensureSeed()
        assertTrue("aucun fond seme", db.providers().count() > 0)
        assertTrue("fond par defaut absent", db.providers().byId("osm") != null)
        assertTrue("le fond af3v doit etre seme", db.providers().byId("af3v") != null)
        assertTrue("reglages absents", db.settings().get() != null)
    }

    /** Amorcage idempotent : il ne doit pas ressusciter les fonds que l'utilisateur a supprimes. */
    @Test fun `l'amorcage ne rejoue pas sur une base deja semee`() = runTest {
        repo.ensureSeed()
        val n = db.providers().count()
        db.providers().byId("osm")?.let { db.providers().delete(it) }
        repo.ensureSeed()
        assertEquals("le fond supprime ne doit pas revenir", n - 1, db.providers().count())
    }
}
