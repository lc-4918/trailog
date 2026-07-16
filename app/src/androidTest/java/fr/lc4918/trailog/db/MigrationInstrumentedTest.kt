package fr.lc4918.trailog.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.lc4918.trailog.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * INSTRUMENTATION - la base sur un vrai SQLite Android, la ou Robolectric n'emule que le moteur.
 * Verifie que le schema courant s'ouvre et que l'amorcage installe ce qu'il faut pour demarrer.
 */
@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun laBaseSOuvreEtPorteLeSchemaCourant() {
        val db = AppDatabase.get(ctx)
        val s = db.openHelper.writableDatabase
        assertNotNull(s)
        val cols = s.query("PRAGMA table_info(settings)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
        }
        listOf("bubblePosition", "updateCheckMode", "basemapControlOpacityPct").forEach {
            assertTrue("colonne $it absente", it in cols)
        }
    }

    @Test fun leFondParDefautEtLeFondAf3vSontPresents() = runBlocking {
        val db = AppDatabase.get(ctx)
        assertTrue("aucun fond", db.providers().count() > 0)
        assertNotNull("fond par defaut absent", db.providers().byId("osm"))
        assertNotNull("fond af3v absent", db.providers().byId("af3v"))
    }
}
