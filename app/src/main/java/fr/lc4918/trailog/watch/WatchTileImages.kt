package fr.lc4918.trailog.watch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.floor
import kotlin.math.pow

/**
 * Une tuile montre, tiree des tuiles du fond qui la recouvrent.
 *
 * La grille de la montre (zoom fractionnaire, cf. [WatchTiles.Detail]) ne tombe pas sur celle du fond : une
 * tuile montre peut recouvrir jusqu'a quatre tuiles du fond. On les assemble dans une image de la taille de la
 * tuile montre, puis on la reduit a la palette de la montre et on la trame ([WatchTilePixels]) : la montre
 * recoit des pixels prets a stocker.
 */
object WatchTileImages {

    /** Cote, en pixels, d'une tuile du fond pour la geometrie : 256, quelle que soit sa definition reelle. */
    private const val SOURCE_TILE_UNITS = 256.0

    /**
     * Une tuile du fond ([x], [y] au zoom du fond) et la place qu'elle prend dans la tuile montre, en pixels
     * de la tuile montre (de 0 a [WatchTiles.WATCH_TILE_PIXELS]) ; elle peut deborder de ce carre.
     */
    data class SourcePiece(val x: Int, val y: Int, val left: Float, val top: Float, val right: Float, val bottom: Float)

    /**
     * Les tuiles du fond au zoom [sourceZoom] qui recouvrent [tile], posee sur la grille du zoom [zoom], et ou
     * chacune tombe. Un fond limite a un zoom plus bas que [zoom] donne une tuile montre plus petite que sa tuile :
     * elle est agrandie.
     */
    fun sourcePieces(tile: WatchTiles.WatchTile, zoom: Double, sourceZoom: Int): List<SourcePiece> {
        // Cote de la tuile montre, en pixels du fond (tuiles de 256) : 128 au meme zoom, plus grand si le fond est
        // plus fin que la grille, plus petit s'il l'est moins.
        val side = WatchTiles.WATCH_TILE_PIXELS * 2.0.pow(sourceZoom - zoom)
        val originX = tile.x * side
        val originY = tile.y * side
        val scale = WatchTiles.WATCH_TILE_PIXELS / side
        val lastIndex = (1L shl sourceZoom) - 1
        val columns = floor(originX / SOURCE_TILE_UNITS).toLong()..floor((originX + side - 1e-6) / SOURCE_TILE_UNITS).toLong()
        val rows = floor(originY / SOURCE_TILE_UNITS).toLong()..floor((originY + side - 1e-6) / SOURCE_TILE_UNITS).toLong()
        val pieces = ArrayList<SourcePiece>()
        for (row in rows) for (column in columns) {
            if (column < 0 || row < 0 || column > lastIndex || row > lastIndex) continue
            pieces.add(
                SourcePiece(
                    column.toInt(), row.toInt(),
                    ((column * SOURCE_TILE_UNITS - originX) * scale).toFloat(), ((row * SOURCE_TILE_UNITS - originY) * scale).toFloat(),
                    (((column + 1) * SOURCE_TILE_UNITS - originX) * scale).toFloat(), (((row + 1) * SOURCE_TILE_UNITS - originY) * scale).toFloat(),
                ),
            )
        }
        return pieces
    }

    /**
     * Les pixels de [tile] rangés par [WatchTilePixels.pack], assembles depuis les tuiles du fond que donne
     * [sourceImage] (x, y, au zoom [sourceZoom]), ou null si aucune n'est lisible. Un fond blanc sous le tout :
     * un fond transparent (surcouche, bord de couverture) deviendrait noir sur la montre.
     */
    fun render(
        tile: WatchTiles.WatchTile, zoom: Double, sourceZoom: Int, sourceImage: (x: Int, y: Int) -> ByteArray?,
    ): ByteArray? {
        val size = WatchTiles.WATCH_TILE_PIXELS
        val target = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        target.eraseColor(Color.WHITE)
        val canvas = Canvas(target)
        // Reduction douce : une tuile montre a 15,5 reduit les tuiles du zoom 16 d'environ 30 %.
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        var drawn = 0
        for (piece in sourcePieces(tile, zoom, sourceZoom)) {
            val bytes = sourceImage(piece.x, piece.y) ?: continue
            val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            canvas.drawBitmap(source, null, RectF(piece.left, piece.top, piece.right, piece.bottom), paint)
            source.recycle()
            drawn++
        }
        if (drawn == 0) {
            target.recycle()
            return null
        }
        val pixels = IntArray(size * size)
        target.getPixels(pixels, 0, size, 0, 0, size, size)
        target.recycle()
        return WatchTilePixels.pack(pixels, size)
    }
}
