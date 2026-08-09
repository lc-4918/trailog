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

    // ---------- 24 -> 25 : tolerance de tap propre aux traces ----------

    private fun migrate2425(db: SQLiteDatabase, tapAvant: Int) {
        db.execSQL("ALTER TABLE settings ADD COLUMN tapToleranceDp INTEGER NOT NULL DEFAULT 16")
        db.execSQL("UPDATE settings SET tapToleranceDp = $tapAvant")
        db.execSQL(MigrationSql.ADD_LINE_TAP_TOLERANCE)
        db.execSQL(MigrationSql.LINE_TAP_TOLERANCE_FROM_TAP_TOLERANCE)
        db.execSQL(MigrationSql.TIGHTEN_TAP_TOLERANCE)
    }

    /** Base restee au defaut : les traces gardent 16, les marqueurs se resserrent a 10. Recopier apres le
     *  resserrement donnerait 10 aux deux, ce que cet ordre de verification verrouille. */
    @Test fun `24 vers 25 resserre les marqueurs et laisse les traces a 16`() {
        val db = freshDb("m2425"); settingsV16(db)
        migrate2425(db, 16)
        assertEquals(16, scalar(db, "SELECT lineTapToleranceDp FROM settings") { it.getInt(0) })
        assertEquals(10, scalar(db, "SELECT tapToleranceDp FROM settings") { it.getInt(0) })
        db.close()
    }

    /** Tolerance choisie expres : recopiee telle quelle sur les traces, et laissee intacte sur les
     *  marqueurs. Le nouveau defaut ne doit pas ecraser un reglage de l'utilisateur. */
    @Test fun `24 vers 25 preserve une tolerance reglee a la main`() {
        val db = freshDb("m2425b"); settingsV16(db)
        migrate2425(db, 28)
        assertEquals(28, scalar(db, "SELECT lineTapToleranceDp FROM settings") { it.getInt(0) })
        assertEquals(28, scalar(db, "SELECT tapToleranceDp FROM settings") { it.getInt(0) })
        db.close()
    }

    // ---------- 25 -> 26 : geocodage ----------

    /** A faux, et non a vrai : le geocodage est la seule fonction qui interroge un service tiers en cours
     *  d'usage. L'allumer d'office sur une base deja en place l'imposerait a qui ne l'a pas demande. */
    @Test fun `25 vers 26 ajoute le geocodage desactive et sans url`() {
        val db = freshDb("m2526"); settingsV16(db)
        db.execSQL(MigrationSql.ADD_GEOCODING_ENABLED)
        db.execSQL(MigrationSql.ADD_GEOCODING_URL)
        assertEquals(0, scalar(db, "SELECT geocodingEnabled FROM settings") { it.getInt(0) })
        assertEquals("", scalar(db, "SELECT geocodingUrl FROM settings") { it.getString(0) })
        db.close()
    }

    // ---------- 26 -> 27 : itineraires ----------

    @Test fun `26 vers 27 ajoute l'itineraire sans url et en VTC`() {
        val db = freshDb("m2627"); settingsV16(db)
        db.execSQL(MigrationSql.ADD_ROUTING_URL)
        db.execSQL(MigrationSql.ADD_ROUTING_PROFILE)
        assertEquals("", scalar(db, "SELECT routingUrl FROM settings") { it.getString(0) })
        assertEquals("hybrid", scalar(db, "SELECT routingProfile FROM settings") { it.getString(0) })
        db.close()
    }

    // ---------- 27 -> 28 : planificateur d'itineraire ----------

    /** Le planificateur arrive desactive sur une base deja en place, comme le geocodage avant lui : il
     *  interroge des services tiers, et ne doit pas s'imposer a qui ne l'a pas demande. */
    @Test fun `27 vers 28 ajoute le planificateur desactive et sa bande sur le theme systeme`() {
        val db = freshDb("m2728"); settingsV16(db)
        db.execSQL(MigrationSql.ADD_ROUTE_PLANNER_ENABLED)
        db.execSQL(MigrationSql.ADD_PLANNER_BAND_THEME)
        assertEquals(0, scalar(db, "SELECT routePlannerEnabled FROM settings") { it.getInt(0) })
        assertEquals("system", scalar(db, "SELECT plannerBandTheme FROM settings") { it.getString(0) })
        db.close()
    }

    // ---------- 28 -> 29 : fond des boutons de controle ----------

    /** Desactive sur une base deja en place : les boutons nus sont l'aspect connu de l'application, et une
     *  mise a jour n'a pas a changer l'allure de la carte sans qu'on l'ait demande. */
    @Test fun `28 vers 29 ajoute le fond des boutons desactive`() {
        val db = freshDb("m2829"); settingsV16(db)
        db.execSQL(MigrationSql.ADD_CONTROL_BUTTONS_BACKGROUND)
        assertEquals(0, scalar(db, "SELECT controlButtonsBackground FROM settings") { it.getInt(0) })
        db.close()
    }

    // ---------- 29 -> 30 : mesure sur trace ----------

    /** Desactivee sur une base deja en place : un bouton de plus sur la carte ne s'invite pas de lui-meme. */
    @Test fun `29 vers 30 ajoute la mesure sur trace desactivee`() {
        val db = freshDb("m2930"); settingsV16(db)
        db.execSQL(MigrationSql.ADD_TRACK_MEASURE_ENABLED)
        assertEquals(0, scalar(db, "SELECT trackMeasureEnabled FROM settings") { it.getInt(0) })
        db.close()
    }

    // ---------- 30 -> 31 : opacite du relief, et boutons de carte affiches ----------

    /** Le relief recoit sa force d'ombrage, la meme pour tous les fonds : c'est un rendu, pas un choix
     *  deja fait quelque part. 40 % et non les 50 % figes jusque-la, qui ecrasaient le fond. */
    @Test fun `30 vers 31 ajoute l'opacite des fonds a 40 pourcent`() {
        val db = freshDb("m3031a"); providersV18(db)
        db.execSQL(MigrationSql.ADD_PROVIDER_OPACITY)
        assertTrue("opacityPct" in columns(db, "providers"))
        assertEquals(DefaultDemOpacityPct, scalar(db, "SELECT opacityPct FROM providers WHERE id = 'osm'") { it.getInt(0) })
        db.close()
    }

    /**
     * Trois reglages d'affichage passent a "actif", sans condition : un booleen ne dit pas s'il vaut faux
     * par choix ou par defaut, et aucune version en circulation n'a d'utilisateur dont le choix serait a
     * menager. Le test verrouille que les TROIS y passent - en oublier un ne casserait rien de visible.
     */
    @Test fun `30 vers 31 affiche les boutons de carte et leur fond`() {
        val db = freshDb("m3031b"); settingsV16(db)
        db.execSQL(MigrationSql.ADD_ROUTE_PLANNER_ENABLED)
        db.execSQL(MigrationSql.ADD_CONTROL_BUTTONS_BACKGROUND)
        db.execSQL("ALTER TABLE settings ADD COLUMN showGpsButton INTEGER NOT NULL DEFAULT 0")
        db.execSQL(MigrationSql.SHOW_MAP_CONTROLS_BY_DEFAULT)
        assertEquals(1, scalar(db, "SELECT showGpsButton FROM settings") { it.getInt(0) })
        assertEquals(1, scalar(db, "SELECT routePlannerEnabled FROM settings") { it.getInt(0) })
        assertEquals(1, scalar(db, "SELECT controlButtonsBackground FROM settings") { it.getInt(0) })
        db.close()
    }

    // ---------- 31 -> 32 : taille des boutons de carte ----------

    @Test fun `31 vers 32 ajoute la taille des boutons a sa valeur par defaut`() {
        val db = freshDb("m3132"); settingsV16(db)
        db.execSQL(MigrationSql.ADD_MAP_BUTTON_SIZE)
        assertEquals(DefaultMapButtonSizeDp, scalar(db, "SELECT mapButtonSizeDp FROM settings") { it.getInt(0) })
        db.close()
    }

    // ---------- 32 -> 33 : taille par defaut des boutons portee a 42 ----------

    /** N'ajuste que les bases restees a l'ancienne valeur : une taille choisie expres ne doit pas etre
     *  ecrasee par un changement de defaut. */
    @Test fun `32 vers 33 ne remonte que les tailles restees a l'ancien defaut`() {
        val db = freshDb("m3233"); settingsV16(db)
        db.execSQL(MigrationSql.ADD_MAP_BUTTON_SIZE)
        db.execSQL("UPDATE settings SET mapButtonSizeDp = $MinMapButtonSizeDp")
        db.execSQL(MigrationSql.BUMP_MAP_BUTTON_SIZE)
        assertEquals(DefaultMapButtonSizeDp, scalar(db, "SELECT mapButtonSizeDp FROM settings") { it.getInt(0) })
        db.execSQL("UPDATE settings SET mapButtonSizeDp = $MaxMapButtonSizeDp")
        db.execSQL(MigrationSql.BUMP_MAP_BUTTON_SIZE)
        assertEquals(MaxMapButtonSizeDp, scalar(db, "SELECT mapButtonSizeDp FROM settings") { it.getInt(0) })
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
        listOf("bubblePosition", "updateCheckMode", "basemapControlOpacityPct", "verticalExaggeration", "demoSeeded",
            "lineTapToleranceDp", "geocodingEnabled", "geocodingUrl", "routingUrl", "routingProfile",
            "routePlannerEnabled", "plannerBandTheme", "controlButtonsBackground", "trackMeasureEnabled",
            "mapButtonSizeDp")
            .forEach { assertTrue("colonne $it absente", it in cols) }
        val pcols = s.query("PRAGMA table_info(providers)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
        }
        assertTrue("legendAsset" in pcols)
        assertTrue("opacityPct" in pcols)
    }

}
