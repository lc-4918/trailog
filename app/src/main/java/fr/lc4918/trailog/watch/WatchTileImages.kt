package fr.lc4918.trailog.watch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import java.io.ByteArrayOutputStream

/**
 * Decoupe d'une tuile montre dans l'image d'une tuile standard.
 *
 * L'image est rendue en PNG, en couleurs pleines : c'est Garmin Connect Mobile, sur le telephone, qui la
 * reduit ensuite a la palette demandee par la montre et la trame. Le faire ici ferait double emploi.
 */
object WatchTileImages {

    /**
     * Le quart ([quarterColumn], [quarterRow]) de [sourceImage], ramene a [WatchTiles.WATCH_TILE_PIXELS] px.
     * Les tuiles de 512 px de certains fonds sont reduites au passage. Rend null si l'image est illisible.
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
        val output = ByteArrayOutputStream()
        target.compress(Bitmap.CompressFormat.PNG, 100, output)
        target.recycle()
        return output.toByteArray()
    }
}
