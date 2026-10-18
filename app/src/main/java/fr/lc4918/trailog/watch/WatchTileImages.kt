package fr.lc4918.trailog.watch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect

/**
 * Decoupe d'une tuile montre dans l'image d'une tuile standard.
 *
 * Le quart est reduit a la palette de la montre et trame ([WatchTilePixels]) : la montre recoit des pixels
 * prets a stocker.
 */
object WatchTileImages {

    /**
     * Le quart ([quarterColumn], [quarterRow]) de [sourceImage], ramene a [WatchTiles.WATCH_TILE_PIXELS] px.
     * Les tuiles de 512 px de certains fonds sont reduites au passage. Rend les pixels ranges par
     * [WatchTilePixels.pack], ou null si l'image est illisible.
     */
    fun quarter(sourceImage: ByteArray, quarterColumn: Int, quarterRow: Int): ByteArray? {
        val source = BitmapFactory.decodeByteArray(sourceImage, 0, sourceImage.size) ?: return null
        val quarterWidth = source.width / 2
        val quarterHeight = source.height / 2
        val sourceRect = Rect(
            quarterColumn * quarterWidth, quarterRow * quarterHeight,
            (quarterColumn + 1) * quarterWidth, (quarterRow + 1) * quarterHeight,
        )
        val size = WatchTiles.WATCH_TILE_PIXELS
        val target = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        // Fond blanc : un fond transparent (surcouche, bord de couverture) deviendrait noir sur la montre.
        target.eraseColor(Color.WHITE)
        Canvas(target).drawBitmap(source, sourceRect, Rect(0, 0, size, size), null)
        source.recycle()
        val pixels = IntArray(size * size)
        target.getPixels(pixels, 0, size, 0, 0, size, size)
        target.recycle()
        return WatchTilePixels.pack(pixels, size)
    }
}
