package fr.lc4918.trailog.geocode.offline

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import java.io.File

/**
 * L'index des lieux d'un carre de cinq degres : un fichier SQLite fait par `tools/geocode-index`, lu en
 * lecture seule.
 *
 * Trois tables comptent : `places` (le lieu, son genre, son point en 1e-5 degre, sa commune), `name_fts` et
 * `street_fts` (la recherche par mot et par prefixe, une table pour les lieux et une pour les rues - une
 * frappe de trois lettres tombe sur des centaines de milliers de rues, et ne doit pas noyer les villes),
 * et un index sur la case de chaque lieu pour le geocodage inverse.
 *
 * L'ordre des identifiants est celui de l'importance : une table FTS rend ses lignes dans l'ordre des
 * identifiants, si bien que s'arreter aux N premieres, c'est garder les villes avant les hameaux.
 *
 * Une connexion n'est pas partagee entre fils : on ouvre, on lit, on referme (cf. [OfflinePlaces]).
 */
class PlaceIndex private constructor(private val db: SQLiteConnection) : AutoCloseable {

    /** Les lieux dont un mot commence par la frappe [match] (cf. [PlaceQuery.match]), importants d'abord. */
    fun searchPlaces(match: String, limit: Int): List<PlaceHit> = query(
        "SELECT p.name, p.kind, p.lat, p.lon, l.name FROM name_fts f JOIN places p ON p.id = f.rowid " +
            "LEFT JOIN localities l ON l.id = p.locality WHERE name_fts MATCH ?1 ORDER BY f.rowid LIMIT ?2",
        match, limit.toLong(),
    )

    /** Les rues, mots d'abord. */
    fun searchStreets(match: String, limit: Int): List<PlaceHit> = query(
        "SELECT p.name, p.kind, p.lat, p.lon, l.name FROM street_fts f JOIN places p ON p.id = f.rowid " +
            "LEFT JOIN localities l ON l.id = p.locality WHERE street_fts MATCH ?1 ORDER BY f.rowid LIMIT ?2",
        match, limit.toLong(),
    )

    /** Tout ce qui se trouve dans les cases autour de ce point (cf. [PlaceQuery.cellRanges]). */
    fun around(lon: Double, lat: Double): List<PlaceHit> {
        val ranges = PlaceQuery.cellRanges(lat, lon)
        val where = ranges.joinToString(" OR ") { "p.cell BETWEEN ${it.first} AND ${it.last}" }
        return query(
            "SELECT p.name, p.kind, p.lat, p.lon, l.name FROM places p " +
                "LEFT JOIN localities l ON l.id = p.locality WHERE $where",
        )
    }

    private fun query(sql: String, vararg args: Any): List<PlaceHit> {
        val out = ArrayList<PlaceHit>()
        db.prepare(sql).use { stmt ->
            args.forEachIndexed { i, a ->
                when (a) {
                    is String -> stmt.bindText(i + 1, a)
                    is Long -> stmt.bindLong(i + 1, a)
                    else -> error("argument non pris en charge : $a")
                }
            }
            while (stmt.step()) {
                out += PlaceHit(
                    name = stmt.getText(0),
                    kind = stmt.getLong(1).toInt(),
                    lat = stmt.getLong(2) / 1e5,
                    lon = stmt.getLong(3) / 1e5,
                    locality = if (stmt.isNull(4)) null else stmt.getText(4),
                )
            }
        }
        return out
    }

    override fun close() = db.close()

    companion object {
        fun open(file: File): PlaceIndex =
            PlaceIndex(BundledSQLiteDriver().open(file.path, SQLITE_OPEN_READONLY))
    }
}
