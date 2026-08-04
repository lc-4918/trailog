package fr.lc4918.trailog.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Migrations de la base. Ce sont les tests les plus critiques du lot : une migration fautive ne casse
 * pas le build, elle detruit les couches importees de l'utilisateur au premier lancement.
 *
 * On rejoue chaque migration sur un SQLite reel, a partir du schema tel qu'il etait, plutot que de faire
 * confiance a la relecture de son SQL.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationsTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun freshDb(name: String): SQLiteDatabase {
        val f = File(ctx.cacheDir, "$name-${System.nanoTime()}.db")
        f.delete()
        return SQLiteDatabase.openOrCreateDatabase(f, null)
    }

    /** Schema de settings tel qu'en v16, reduit aux colonnes que les migrations touchent. */
    private fun settingsV16(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE settings (
              id INTEGER PRIMARY KEY NOT NULL,
              defaultBasemapId TEXT NOT NULL DEFAULT 'osm',
              basemapControlWidthPct INTEGER NOT NULL DEFAULT 50,
              basemapControlOpacityPct INTEGER NOT NULL DEFAULT 20
            )
        """.trimIndent())
        db.execSQL("INSERT INTO settings (id) VALUES (0)")
    }

    private fun providersV18(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE providers (
              id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, groupName TEXT NOT NULL,
              type TEXT NOT NULL, urlTemplate TEXT NOT NULL, apiKey TEXT, subdomains TEXT,
              minZoom INTEGER NOT NULL, maxZoom INTEGER NOT NULL, tileSize INTEGER NOT NULL,
              attribution TEXT, transparent INTEGER NOT NULL, enabled INTEGER NOT NULL,
              builtin INTEGER NOT NULL, sortOrder INTEGER NOT NULL, folderId INTEGER
            )
        """.trimIndent())
        db.execSQL("INSERT INTO providers VALUES ('osm','OSM','Monde','XYZ','u',NULL,NULL,0,19,256,NULL,0,1,1,0,NULL)")
        db.execSQL("INSERT INTO providers VALUES ('hr','Croatie','Pays','WMS','u',NULL,NULL,0,18,256,NULL,0,1,1,7,NULL)")
    }

    private fun columns(db: SQLiteDatabase, table: String): List<String> =
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
        }

    private fun <T> scalar(db: SQLiteDatabase, sql: String, read: (android.database.Cursor) -> T): T =
        db.rawQuery(sql, null).use { it.moveToFirst(); read(it) }

    // ---------- 16 -> 17 : ajout de verticalExaggeration ----------

    @Test fun `16 vers 17 ajoute l'echelle verticale sans perdre les reglages`() {
        val db = freshDb("m1617"); settingsV16(db)
        db.execSQL("UPDATE settings SET defaultBasemapId = 'ign_fr'")
        db.execSQL(MigrationSql.ADD_VERTICAL_EXAGGERATION)
        assertTrue("verticalExaggeration" in columns(db, "settings"))
        assertEquals("ign_fr", scalar(db, "SELECT defaultBasemapId FROM settings") { it.getString(0) })
        assertEquals(0, scalar(db, "SELECT verticalExaggeration FROM settings") { it.getInt(0) })
        db.close()
    }

    // ---------- 17 -> 18 : ajout de bubblePosition ----------

    @Test fun `17 vers 18 ajoute la position d'infobulle avec auto par defaut`() {
        val db = freshDb("m1718"); settingsV16(db)
        db.execSQL(MigrationSql.ADD_BUBBLE_POSITION)
        assertEquals("auto", scalar(db, "SELECT bubblePosition FROM settings") { it.getString(0) })
        db.close()
    }

    // ---------- 18 -> 19 : legendAsset + insertion du fond AF3V ----------

    @Test fun `18 vers 19 ajoute la colonne de legende et insere le fond af3v`() {
        val db = freshDb("m1819"); providersV18(db)
        db.execSQL(MigrationSql.ADD_LEGEND_ASSET)
        db.execSQL(MigrationSql.INSERT_AF3V)
        assertTrue("legendAsset" in columns(db, "providers"))
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM providers WHERE id='af3v'") { it.getInt(0) })
        assertEquals("legends/af3v.png",
            scalar(db, "SELECT legendAsset FROM providers WHERE id='af3v'") { it.getString(0) })
        db.close()
    }

    /** La table n'est semee qu'a vide : sans cet INSERT, le fond n'existerait que sur une installation
     *  neuve. Son sortOrder doit se placer apres les existants, sans les heurter. */
    @Test fun `18 vers 19 place af3v apres les fonds existants`() {
        val db = freshDb("m1819b"); providersV18(db)
        db.execSQL(MigrationSql.ADD_LEGEND_ASSET)
        db.execSQL(MigrationSql.INSERT_AF3V)
        assertEquals(8, scalar(db, "SELECT sortOrder FROM providers WHERE id='af3v'") { it.getInt(0) })  // max(7) + 1
        val ordres = db.rawQuery("SELECT sortOrder FROM providers", null).use { c ->
            generateSequence { if (c.moveToNext()) c.getInt(0) else null }.toList()
        }
        assertEquals("aucun ordre en double", ordres.size, ordres.toSet().size)
        db.close()
    }

    @Test fun `18 vers 19 rejouee ne duplique pas af3v`() {
        val db = freshDb("m1819c"); providersV18(db)
        db.execSQL(MigrationSql.ADD_LEGEND_ASSET)
        db.execSQL(MigrationSql.INSERT_AF3V); db.execSQL(MigrationSql.INSERT_AF3V)
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM providers WHERE id='af3v'") { it.getInt(0) })
        db.close()
    }

    @Test fun `18 vers 19 preserve l'URL du fond af3v intacte`() {
        val db = freshDb("m1819d"); providersV18(db)
        db.execSQL(MigrationSql.ADD_LEGEND_ASSET)
        db.execSQL(MigrationSql.INSERT_AF3V)
        val url = scalar(db, "SELECT urlTemplate FROM providers WHERE id='af3v'") { it.getString(0) }
        assertTrue(url.contains("LAYERS=voie_cyclable,segment_cyclable,poi_travaux"))
        assertTrue("le gabarit bbox doit survivre au SQL", url.contains("{bbox-epsg-3857}"))
        db.close()
    }

    // ---------- 19 -> 20 : updateCheckMode ----------

    @Test fun `19 vers 20 ajoute le mode de verification avec auto par defaut`() {
        val db = freshDb("m1920"); settingsV16(db)
        db.execSQL(MigrationSql.ADD_UPDATE_CHECK_MODE)
        assertEquals("auto", scalar(db, "SELECT updateCheckMode FROM settings") { it.getString(0) })
        db.close()
    }

    // ---------- 20 -> 21 : inversion transparence -> opacite ----------

    /** La colonne portait une transparence malgre son nom. Sans conversion, un panneau regle a 20
     *  serait relu comme 20 % d'opacite et deviendrait quasi invisible. */
    @Test fun `20 vers 21 convertit la transparence en opacite sans changer le rendu`() {
        val db = freshDb("m2021"); settingsV16(db)
        listOf(0 to 100, 20 to 80, 50 to 50, 70 to 30).forEach { (avant, attendu) ->
            db.execSQL("UPDATE settings SET basemapControlOpacityPct = $avant")
            db.execSQL(MigrationSql.TRANSPARENCY_TO_OPACITY)
            assertEquals("transparence $avant %", attendu,
                scalar(db, "SELECT basemapControlOpacityPct FROM settings") { it.getInt(0) })
        }
        db.close()
    }

    /** Une transparence de 90 % donnerait 10 % d'opacite, sous le minimum du slider : on remonte a 30. */
    @Test fun `20 vers 21 borne au minimum du slider`() {
        val db = freshDb("m2021b"); settingsV16(db)
        db.execSQL("UPDATE settings SET basemapControlOpacityPct = 90")
        db.execSQL(MigrationSql.TRANSPARENCY_TO_OPACITY)
        assertEquals(30, scalar(db, "SELECT basemapControlOpacityPct FROM settings") { it.getInt(0) })
        db.close()
    }

    @Test fun `20 vers 21 ne sort jamais de la plage du slider`() {
        val db = freshDb("m2021c"); settingsV16(db)
        (0..100).forEach { v ->
            db.execSQL("UPDATE settings SET basemapControlOpacityPct = $v")
            db.execSQL(MigrationSql.TRANSPARENCY_TO_OPACITY)
            val n = scalar(db, "SELECT basemapControlOpacityPct FROM settings") { it.getInt(0) }
            assertTrue("transparence $v -> opacite $n hors de 30..100", n in 30..100)
        }
        db.close()
    }

    // ---------- 23 -> 24 : drapeau du jeu de demonstration ----------

    /** A faux, et non a vrai : une base deja en place n'a jamais vu le jeu de demonstration, elle doit
     *  le recevoir au premier lancement suivant la mise a jour, comme une installation neuve. Un defaut
     *  a 1 l'en priverait definitivement, sans que rien ne le signale. */
    @Test fun `23 vers 24 ajoute le drapeau de demo a faux`() {
        val db = freshDb("m2324"); settingsV16(db)
        db.execSQL(MigrationSql.ADD_DEMO_SEEDED)
        assertEquals(0, scalar(db, "SELECT demoSeeded FROM settings") { it.getInt(0) })
        db.close()
    }

    // ---------- La base reelle s'ouvre et porte le schema courant ----------

    @Test fun `la base courante s'ouvre et porte toutes les colonnes attendues`() {
        val db = AppDatabase.get(ctx)
        val s = db.openHelper.writableDatabase
        assertNotNull(s)
        val cols = s.query("PRAGMA table_info(settings)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
        }
        listOf("bubblePosition", "updateCheckMode", "basemapControlOpacityPct", "verticalExaggeration", "demoSeeded")
            .forEach { assertTrue("colonne $it absente", it in cols) }
        val pcols = s.query("PRAGMA table_info(providers)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
        }
        assertTrue("legendAsset" in pcols)
    }

}
