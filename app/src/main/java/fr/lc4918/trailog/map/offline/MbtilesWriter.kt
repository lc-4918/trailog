package fr.lc4918.trailog.map.offline

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Écrit un conteneur MBTiles (SQLite) au format raster (SPEC offline_map.md section 5). Toutes les méthodes
 * doivent être appelées depuis un seul et même thread (le natif SQLite lie une transaction à son
 * thread) : le moteur de téléchargement les confine sur un dispatcher mono-thread dédié.
 *
 * Rappel MBTiles : la table `tiles` indexe la ligne en TMS (origine en bas), alors que le pavage XYZ
 * (et MapLibre) utilise une origine en haut ; d'où l'inversion `tile_row = 2^z - 1 - y`.
 */
class MbtilesWriter(private val file: File) {
    private lateinit var db: SQLiteDatabase
    private var inTx = false

    fun open() {
        db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL("CREATE TABLE IF NOT EXISTS metadata (name TEXT, value TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS tile_index ON tiles (zoom_level, tile_column, tile_row)")
    }

    /** Ouvre une transaction pour un lot d'insertions (bien plus rapide qu'un commit par tuile). */
    fun beginBatch() {
        if (!inTx) { db.beginTransaction(); inTx = true }
    }

    /** Valide le lot courant et en ouvre un nouveau (flush périodique pendant le téléchargement). */
    fun commitBatch() {
        if (inTx) { db.setTransactionSuccessful(); db.endTransaction(); inTx = false }
        beginBatch()
    }

    fun putTile(z: Int, x: Int, y: Int, data: ByteArray) {
        val tmsRow = (1 shl z) - 1 - y
        val cv = ContentValues(4).apply {
            put("zoom_level", z); put("tile_column", x); put("tile_row", tmsRow); put("tile_data", data)
        }
        db.insertWithOnConflict("tiles", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Métadonnées standard MBTiles 1.3 (bounds en "west,south,east,north", WGS84). */
    fun writeMetadata(name: String, format: String, bbox: Bbox, minZoom: Int, maxZoom: Int, attribution: String?) {
        val center = "${(bbox.west + bbox.east) / 2},${(bbox.south + bbox.north) / 2},$minZoom"
        val meta = linkedMapOf(
            "name" to name,
            "format" to format,
            "type" to "baselayer",
            "version" to "1.1",
            "minzoom" to minZoom.toString(),
            "maxzoom" to maxZoom.toString(),
            "bounds" to "${bbox.west},${bbox.south},${bbox.east},${bbox.north}",
            "center" to center,
        )
        if (!attribution.isNullOrBlank()) meta["attribution"] = attribution
        meta.forEach { (k, v) ->
            db.insert("metadata", null, ContentValues(2).apply { put("name", k); put("value", v) })
        }
    }

    /** Valide la transaction en cours (le cas échéant) puis ferme la base. */
    fun close() {
        if (inTx) { db.setTransactionSuccessful(); db.endTransaction(); inTx = false }
        if (::db.isInitialized) db.close()
    }

    /** Ferme sans valider la transaction en cours : le lot non commité est perdu (annulation/erreur). */
    fun abort() {
        if (inTx) { db.endTransaction(); inTx = false }
        if (::db.isInitialized) db.close()
    }
}
