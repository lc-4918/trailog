package fr.lc4918.trailog.geocode.offline

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * L'index des lieux sur une VRAIE base SQLite avec FTS5 - celle de l'application, jouee sur la JVM : la
 * recherche par prefixe, l'ordre par importance et le geocodage inverse ne se verifient pas autrement.
 *
 * Le schema est celui que `tools/geocode-index/build_index.py` ecrit.
 */
class PlaceIndexTest {

    private lateinit var dir: File

    @Before fun setUp() { dir = Files.createTempDirectory("places").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    private data class Row(val name: String, val kind: Int, val lon: Double, val lat: Double, val locality: String? = null)

    /** Ecrit un carre comme le script : identifiants par importance, une table FTS pour les lieux, une pour les rues. */
    private fun writeTile(file: File, rows: List<Row>) {
        val db: SQLiteConnection = BundledSQLiteDriver().open(file.path)
        try {
            db.execSQL("CREATE TABLE localities(id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
            db.execSQL(
                "CREATE TABLE places(id INTEGER PRIMARY KEY, name TEXT NOT NULL, kind INTEGER NOT NULL, " +
                    "lat INTEGER NOT NULL, lon INTEGER NOT NULL, locality INTEGER, cell INTEGER NOT NULL)")
            for (table in listOf("name_fts", "street_fts")) {
                db.execSQL("CREATE VIRTUAL TABLE $table USING fts5(name, content='', contentless_delete=0, detail=none, " +
                    "tokenize='unicode61 remove_diacritics 2', prefix='2 3')")
            }
            val localityIds = rows.mapNotNull { it.locality }.distinct().withIndex().associate { it.value to it.index + 1 }
            localityIds.forEach { (name, id) -> db.execSQL("INSERT INTO localities VALUES ($id, '${name.replace("'", "''")}')") }
            val ordered = rows.sortedWith(compareBy({ PlaceKind.tier(it.kind) }, { it.name.length }))
            ordered.forEachIndexed { i, r ->
                val id = i + 1
                val latE5 = Math.round(r.lat * 1e5)
                val lonE5 = Math.round(r.lon * 1e5)
                val safe = r.name.replace("'", "''")
                db.execSQL("INSERT INTO places VALUES ($id, '$safe', ${r.kind}, $latE5, $lonE5, " +
                    "${r.locality?.let { localityIds[it] } ?: "NULL"}, ${PlaceQuery.cellOf(latE5, lonE5)})")
                val fts = if (r.kind == PlaceKind.STREET) "street_fts" else "name_fts"
                db.execSQL("INSERT INTO $fts(rowid, name) VALUES ($id, '$safe')")
            }
            db.execSQL("CREATE INDEX places_cell ON places(cell)")
        } finally {
            db.close()
        }
    }

    private val rows = listOf(
        Row("Revel", PlaceKind.TOWN, 2.0, 43.46),
        Row("Revel-Tourdan", PlaceKind.VILLAGE, 5.1, 45.3),
        Row("Les Revels", PlaceKind.HAMLET, 2.1, 43.5),
        Row("Rue de la République", PlaceKind.STREET, 2.001, 43.461, "Revel"),
        Row("Rue de la République", PlaceKind.STREET, 5.7, 45.19, "Grenoble"),
        Row("Évian-les-Bains", PlaceKind.TOWN, 6.58, 46.40),
        Row("Col du Galibier", 13, 6.41, 45.06, "Valloire"),
        Row("Rue d'Évian", PlaceKind.STREET, 2.003, 43.462, "Revel"),
    )

    private fun open(): PlaceIndex {
        val file = File(dir, "E0_N40.gc.sqlite")
        writeTile(file, rows)
        return PlaceIndex.open(file)
    }

    @Test fun `FTS5 est dans le SQLite embarque`() {
        open().use { assertTrue(it.searchPlaces("\"revel\"*", 10).isNotEmpty()) }
    }

    @Test fun `la recherche par prefixe rend les villes avant les hameaux`() {
        open().use { index ->
            val found = index.searchPlaces(PlaceQuery.match("reve")!!, 10)
            assertEquals(listOf("Revel", "Les Revels", "Revel-Tourdan").sorted(), found.map { it.name }.sorted())
            assertEquals("l'importance d'abord", "Revel", found.first().name)
            assertEquals(PlaceKind.TOWN, found.first().kind)
        }
    }

    @Test fun `un mot du milieu trouve le lieu, dans n'importe quel ordre`() {
        open().use { index ->
            assertEquals(listOf("Col du Galibier"), index.searchPlaces(PlaceQuery.match("galibier")!!, 10).map { it.name })
            assertEquals(listOf("Col du Galibier"), index.searchPlaces(PlaceQuery.match("galibier col")!!, 10).map { it.name })
        }
    }

    @Test fun `les accents et la casse ne comptent pas`() {
        open().use { index ->
            assertEquals(listOf("Évian-les-Bains"), index.searchPlaces(PlaceQuery.match("EVIAN")!!, 10).map { it.name })
            assertEquals(2, index.searchStreets(PlaceQuery.match("republique")!!, 10).size)
        }
    }

    /** Les rues ont leur table : "rue" ne noie pas les lieux, et les lieux ne polluent pas les rues. */
    @Test fun `les rues et les lieux se cherchent separement`() {
        open().use { index ->
            assertTrue(index.searchPlaces(PlaceQuery.match("rue")!!, 10).isEmpty())
            assertEquals(3, index.searchStreets(PlaceQuery.match("rue")!!, 10).size)
        }
    }

    @Test fun `une rue porte sa commune, un lieu non`() {
        open().use { index ->
            val streets = index.searchStreets(PlaceQuery.match("rue de la republique")!!, 10)
            assertEquals(setOf("Revel", "Grenoble"), streets.map { it.locality }.toSet())
            assertEquals(null, index.searchPlaces(PlaceQuery.match("revel")!!, 1).first().locality)
        }
    }

    @Test fun `les coordonnees reviennent en degres`() {
        open().use { index ->
            val revel = index.searchPlaces(PlaceQuery.match("revel")!!, 1).first()
            assertEquals(2.0, revel.lon, 1e-9)
            assertEquals(43.46, revel.lat, 1e-9)
        }
    }

    @Test fun `la limite borne la reponse`() {
        open().use { assertEquals(1, it.searchPlaces(PlaceQuery.match("revel")!!, 1).size) }
    }

    @Test fun `ce qui est autour d'un point se lit par ses cases`() {
        open().use { index ->
            val around = index.around(2.001, 43.461)
            assertEquals(setOf("Revel", "Rue de la République", "Rue d'Évian"), around.map { it.name }.toSet())
            assertTrue("Grenoble est loin", around.none { it.locality == "Grenoble" })
            assertTrue(index.around(0.0, 40.0).isEmpty())
        }
    }

    // ---------- Plusieurs carres ----------

    @Test fun `la recherche parcourt tous les carres installes`() = runBlocking {
        writeTile(File(dir, "E0_N40.gc.sqlite"), rows)
        writeTile(File(dir, "W5_N40.gc.sqlite"), listOf(Row("Revel-sur-Mer", PlaceKind.VILLAGE, -3.0, 42.0)))
        File(dir, "E5_N45.rd5").writeBytes(ByteArray(3))
        val places = OfflinePlaces { dir }
        assertTrue(places.available())
        assertEquals(2, places.installed().size)
        val found = places.search("revel", 10)
        assertEquals("Revel", found.first().lines.first())
        assertTrue(found.any { it.lines.first() == "Revel-sur-Mer" })
    }

    @Test fun `sans index, la recherche ne rend rien`() = runBlocking {
        val places = OfflinePlaces { dir }
        assertFalse(places.available())
        assertTrue(places.search("revel", 10).isEmpty())
        assertTrue(places.reverse(2.0, 43.46).isEmpty())
    }

    /** Un fichier abime ne fait pas echouer la recherche : les autres carres repondent. */
    @Test fun `un index illisible est ignore`() = runBlocking {
        File(dir, "W5_N40.gc.sqlite").writeText("ceci n'est pas une base")
        writeTile(File(dir, "E0_N40.gc.sqlite"), rows)
        assertEquals("Revel", OfflinePlaces { dir }.search("revel", 5).first().lines.first())
    }

    @Test fun `l'adresse d'un point vient du carre qui le contient`() = runBlocking {
        writeTile(File(dir, "E0_N40.gc.sqlite"), rows)
        val here = OfflinePlaces { dir }.reverse(2.0011, 43.4611)
        assertEquals(1, here.size)
        assertEquals(listOf("Rue de la République", "Revel"), here.single().lines)
    }

    @Test fun `un point sans rien alentour n'a pas d'adresse`() = runBlocking {
        writeTile(File(dir, "E0_N40.gc.sqlite"), rows)
        assertTrue(OfflinePlaces { dir }.reverse(3.5, 41.0).isEmpty())
    }

    /** Un nom de fichier qui n'est pas un carre n'est pas un index. */
    @Test fun `seuls les fichiers de carre sont des index`() {
        File(dir, "notes.gc.sqlite").writeBytes(ByteArray(1))
        File(dir, "E2_N40.gc.sqlite").writeBytes(ByteArray(1))
        File(dir, "E0_N40.gc.sqlite.part").writeBytes(ByteArray(1))
        assertTrue(OfflinePlaces { dir }.installed().isEmpty())
    }
}
