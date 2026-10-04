package fr.lc4918.trailog.watch

import android.database.sqlite.SQLiteDatabase
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.map.offline.TileHttp
import fr.lc4918.trailog.map.offline.TileUrl
import java.io.Closeable
import java.io.File

/**
 * Images des tuiles standard d'un fond, pour les decouper en tuiles montre.
 *
 * Un fond MBTiles est lu dans son fichier, hors ligne ; les autres sont demandes au service, par le meme
 * chemin que le telechargement hors ligne ([TileUrl], [TileHttp]).
 *
 * N'est pas sur pour plusieurs fils : la base SQLite est ouverte une fois et lue depuis le seul fil du
 * serveur ([WatchTileServer]).
 */
class WatchTileSource(private val provider: ProviderEntity, private val mbtilesFile: File?) : Closeable {

    companion object {
        /** Les fonds raster. Vectoriel, relief et PMTiles demanderaient un rendu que le telephone ne fait pas.
         *  Une surcouche transparente, seule, n'aurait rien sous elle sur la montre. */
        private val SUPPORTED_TYPES = setOf("XYZ", "WMS", "WMTS", "MBTILES")

        /** Un fond envoyable a la montre : raster, opaque, et servi au zoom de la montre. */
        fun supports(provider: ProviderEntity): Boolean =
            provider.type in SUPPORTED_TYPES && !provider.transparent &&
                WatchTiles.WATCH_ZOOM in provider.minZoom..provider.maxZoom

        /**
         * Le fichier d'un fond MBTiles : chemin absolu derriere `mbtiles://`, ou nom de fichier dans
         * [mbtilesDir] (cf. StyleBuilder, qui le resout de la meme facon). null pour les autres types.
         */
        fun mbtilesFileOf(provider: ProviderEntity, mbtilesDir: File): File? {
            if (provider.type != "MBTILES") return null
            return if (provider.urlTemplate.startsWith("mbtiles://")) File(provider.urlTemplate.removePrefix("mbtiles://"))
            else File(mbtilesDir, provider.urlTemplate)
        }
    }

    private var database: SQLiteDatabase? = null

    /** Rend null si la tuile manque (hors couverture, hors plage de zoom) ou si le service ne repond pas. */
    fun sourceImage(zoom: Int, x: Int, y: Int): ByteArray? {
        if (zoom < provider.minZoom || zoom > provider.maxZoom) return null
        return if (provider.type == "MBTILES") readMbtiles(zoom, x, y)
        else TileHttp.get(TileUrl.build(provider, x, y, zoom))
    }

    private fun readMbtiles(zoom: Int, x: Int, y: Int): ByteArray? {
        val file = mbtilesFile ?: return null
        val openDatabase = database ?: SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            .also { database = it }
        // MBTiles numerote les lignes depuis le bas (TMS), le pavage XYZ depuis le haut.
        val tmsRow = (1 shl zoom) - 1 - y
        openDatabase.rawQuery(
            "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
            arrayOf(zoom.toString(), x.toString(), tmsRow.toString()),
        ).use { cursor -> return if (cursor.moveToFirst()) cursor.getBlob(0) else null }
    }

    override fun close() {
        database?.close()
        database = null
    }
}
