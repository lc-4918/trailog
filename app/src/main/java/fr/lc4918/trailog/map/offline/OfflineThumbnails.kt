package fr.lc4918.trailog.map.offline

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import fr.lc4918.trailog.data.db.ProviderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.tan
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlin.math.pow

/**
 * Génère les deux miniatures d'une couche MBTiles (SPEC offline_map.md section 6) :
 *  1. "Localisation globale" : vue simple à l'échelle du pays sur un fond permissif fixe (Carto
 *     Positron), rectangle rouge opaque. On n'utilise PAS le fond source ici : s'il s'agit d'OSM (ou
 *     d'un service à politique stricte), il renvoie des tuiles "access blocked" ; Carto sert un fond
 *     clair sans clé, adapté à un usage léger de localisation.
 *  2. "Aperçu détail" : tuiles du fond source autour de la zone (~20 % de marge), rectangle rouge
 *     semi-transparent + échelle graphique.
 * Les fichiers sont nommés d'après le .mbtiles et rendus à une résolution suffisante pour la vue
 * agrandie ; l'éditeur les affiche s'ils existent (cache). Best-effort : les tuiles manquantes laissent
 * un fond gris, une erreur n'est pas propagée.
 */
object OfflineThumbnails {
    private const val TILE = 256
    private const val LOC_ZOOM = 6            // vue "pays" (quelques centaines de km)
    private const val LOC_RADIUS = 2          // grille (2*r+1)^2 de tuiles autour du centre
    private const val MAX_OUT = 1280          // largeur max de sortie (px) - nette en vue agrandie
    // Fond fixe et permissif (sans clé) pour la localisation globale - évite les blocages d'OSM & co.
    private const val LOCATOR_URL = "https://basemaps.cartocdn.com/light_all/%d/%d/%d.png"
    private val RED = Color.rgb(0xD3, 0x2F, 0x2F)

    private fun dir(ctx: Context): File = File(ctx.filesDir, "offline_thumbs").apply { mkdirs() }
    private fun stem(mbtilesFileName: String): String = mbtilesFileName.removeSuffix(".mbtiles")

    /** Fichiers (localisation, détail) associés à un .mbtiles ; existent seulement s'ils ont été générés. */
    fun files(ctx: Context, mbtilesFileName: String): Pair<File, File> {
        val d = dir(ctx); val s = stem(mbtilesFileName)
        return File(d, "${s}__loc.png") to File(d, "${s}__detail.png")
    }

    suspend fun generate(
        ctx: Context, provider: ProviderEntity, bbox: Bbox, minZoom: Int, maxZoom: Int, mbtilesFileName: String,
    ) = withContext(Dispatchers.IO) {
        val (locFile, detailFile) = files(ctx, mbtilesFileName)
        runCatching { renderLocation(bbox)?.let { save(it, locFile) } }
        runCatching { renderDetail(provider, bbox, minZoom, maxZoom)?.let { save(it, detailFile) } }
        Unit
    }

    // ---- Miniature 1 : localisation globale (fond permissif fixe, vue large) ----
    @SuppressLint("DefaultLocale")
    private fun renderLocation(bbox: Bbox): Bitmap? {
        val z = LOC_ZOOM
        val n = 1 shl z
        val world = (TILE * n).toDouble()
        val cx = floor(pxX((bbox.west + bbox.east) / 2, world) / TILE).toInt()
        val cy = floor(pxY((bbox.south + bbox.north) / 2, world) / TILE).toInt()
        val xMin = cx - LOC_RADIUS; val xMax = cx + LOC_RADIUS
        val yMin = (cy - LOC_RADIUS).coerceAtLeast(0); val yMax = (cy + LOC_RADIUS).coerceAtMost(n - 1)
        val stitched = stitch(z, xMin, xMax, yMin, yMax) { x, y ->
            TileHttp.get(String.format(LOCATOR_URL, z, ((x % n) + n) % n, y))
        } ?: return null
        val originX = xMin * TILE.toDouble(); val originY = yMin * TILE.toDouble()
        // Rectangle rouge opaque, avec une taille minimale visible (la zone est minuscule à ce zoom).
        val rect = bboxRect(bbox, world, originX, originY, minSizePx = 14f)
        val canvas = Canvas(stitched)
        canvas.drawRect(rect, Paint().apply { color = Color.argb(160, 0xD3, 0x2F, 0x2F); style = Paint.Style.FILL })
        canvas.drawRect(rect, Paint().apply { color = RED; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true })
        return capWidth(stitched, MAX_OUT)
    }

    // ---- Miniature 2 : aperçu détail (fond source + échelle) ----
    private fun renderDetail(provider: ProviderEntity, bbox: Bbox, minZoom: Int, maxZoom: Int): Bitmap? {
        val z = pickDetailZoom(bbox, minZoom, maxZoom)
        val n = 1 shl z
        val world = (TILE * n).toDouble()
        val left = pxX(bbox.west, world); val right = pxX(bbox.east, world)
        val top = pxY(bbox.north, world); val bottom = pxY(bbox.south, world)
        val marginX = (0.2 * (right - left)).coerceAtLeast(40.0)
        val marginY = (0.2 * (bottom - top)).coerceAtLeast(40.0)
        val cropL = left - marginX; val cropR = right + marginX
        val cropT = top - marginY; val cropB = bottom + marginY

        val xMin = floor(cropL / TILE).toInt(); val xMax = floor(cropR / TILE).toInt()
        val yMin = floor(cropT / TILE).toInt().coerceAtLeast(0)
        val yMax = floor(cropB / TILE).toInt().coerceAtMost(n - 1)
        val stitched = stitch(z, xMin, xMax, yMin, yMax) { x, y ->
            TileHttp.get(TileUrl.build(provider, ((x % n) + n) % n, y, z))
        } ?: return null
        val originX = xMin * TILE.toDouble(); val originY = yMin * TILE.toDouble()

        // Découpe la fenêtre (zone + marge) dans la mosaïque, à résolution native (bornée à MAX_OUT).
        val srcRect = Rect(
            (cropL - originX).toInt().coerceIn(0, stitched.width),
            (cropT - originY).toInt().coerceIn(0, stitched.height),
            (cropR - originX).toInt().coerceIn(0, stitched.width),
            (cropB - originY).toInt().coerceIn(0, stitched.height),
        )
        if (srcRect.width() <= 0 || srcRect.height() <= 0) return null
        val outW = srcRect.width().coerceAtMost(MAX_OUT)
        val outH = (outW.toDouble() * srcRect.height() / srcRect.width()).toInt().coerceAtLeast(1)
        val out = createBitmap(outW, outH)
        val canvas = Canvas(out)
        canvas.drawBitmap(stitched, srcRect, Rect(0, 0, outW, outH), Paint(Paint.FILTER_BITMAP_FLAG))

        // Rectangle rouge semi-transparent sur la zone téléchargée, en coordonnées de sortie.
        val sx = outW.toDouble() / srcRect.width(); val sy = outH.toDouble() / srcRect.height()
        val red = RectF(
            ((left - originX - srcRect.left) * sx).toFloat(),
            ((top - originY - srcRect.top) * sy).toFloat(),
            ((right - originX - srcRect.left) * sx).toFloat(),
            ((bottom - originY - srcRect.top) * sy).toFloat(),
        )
        canvas.drawRect(red, Paint().apply { color = Color.argb(128, 0xD3, 0x2F, 0x2F); style = Paint.Style.FILL })
        canvas.drawRect(red, Paint().apply { color = RED; style = Paint.Style.STROKE; strokeWidth = 2f * sx.toFloat() })

        drawScaleBar(canvas, outW, outH, metersPerPixelOut(bbox, z, srcRect.width(), outW))
        return out
    }

    /** Plus haut zoom téléchargé où la zone tient dans ~3 tuiles (mosaïque de miniature légère). */
    private fun pickDetailZoom(bbox: Bbox, minZoom: Int, maxZoom: Int): Int {
        for (z in maxZoom downTo minZoom) {
            val world = (TILE * (1 shl z)).toDouble()
            val spanX = floor(pxX(bbox.east, world) / TILE).toInt() - floor(pxX(bbox.west, world) / TILE).toInt() + 1
            val spanY = floor(pxY(bbox.south, world) / TILE).toInt() - floor(pxY(bbox.north, world) / TILE).toInt() + 1
            if (maxOf(spanX, spanY) <= 3) return z
        }
        return minZoom
    }

    // ---- Mosaïque & géométrie ----
    private inline fun stitch(zoom: Int, xMin: Int, xMax: Int, yMin: Int, yMax: Int, fetch: (Int, Int) -> ByteArray?): Bitmap? {
        val cols = xMax - xMin + 1; val rows = yMax - yMin + 1
        if (cols <= 0 || rows <= 0) return null
        val bmp = createBitmap(cols * TILE, rows * TILE)
        val canvas = Canvas(bmp).apply { drawColor(Color.rgb(0xE8, 0xE8, 0xE8)) }
        val n = 1 shl zoom
        for (tx in xMin..xMax) for (ty in yMin..yMax) {
            if (ty !in 0..<n) continue
            val bytes = fetch(tx, ty) ?: continue
            val t = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            canvas.drawBitmap(t, ((tx - xMin) * TILE).toFloat(), ((ty - yMin) * TILE).toFloat(), null)
            t.recycle()
        }
        return bmp
    }

    private fun bboxRect(bbox: Bbox, world: Double, originX: Double, originY: Double, minSizePx: Float): RectF {
        var l = (pxX(bbox.west, world) - originX).toFloat(); var r = (pxX(bbox.east, world) - originX).toFloat()
        var t = (pxY(bbox.north, world) - originY).toFloat(); var b = (pxY(bbox.south, world) - originY).toFloat()
        if (r - l < minSizePx) { val c = (l + r) / 2; l = c - minSizePx / 2; r = c + minSizePx / 2 }
        if (b - t < minSizePx) { val c = (t + b) / 2; t = c - minSizePx / 2; b = c + minSizePx / 2 }
        return RectF(l, t, r, b)
    }

    /** Réduit à [maxWidth] si nécessaire, sinon renvoie l'image native (pas d'agrandissement). */
    private fun capWidth(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src
        val h = (maxWidth.toDouble() * src.height / src.width).toInt().coerceAtLeast(1)
        return src.scale(maxWidth, h)
    }

    /** Mètres/pixel sur l'image de sortie (après mise à l'échelle de la mosaïque). */
    private fun metersPerPixelOut(bbox: Bbox, zoom: Int, cropWidthPx: Int, outWidthPx: Int): Double {
        val lat = (bbox.south + bbox.north) / 2
        val mppAtZoom = 156543.03392 * cos(Math.toRadians(lat)) / (1 shl zoom)
        return mppAtZoom * cropWidthPx / outWidthPx
    }

    private fun drawScaleBar(canvas: Canvas, w: Int, h: Int, mpp: Double) {
        if (mpp <= 0) return
        val target = w * 0.30 * mpp                 // ~30 % de la largeur, en mètres
        val nice = niceDistance(target)
        val barPx = (nice / mpp).toFloat()
        val label = if (nice >= 1000) "${(nice / 1000).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }} km"
        else "${nice.toInt()} m"
        // Dimensions proportionnelles à la largeur de sortie (nettes quelle que soit la résolution).
        val margin = w * 0.02f
        val tick = w * 0.018f
        val textSize = (w * 0.028f).coerceIn(16f, 44f)
        val x1 = w - margin - barPx; val x2 = w - margin; val y = h - margin
        val bg = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = w * 0.007f; isAntiAlias = true }
        val fg = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = w * 0.003f; isAntiAlias = true }
        for (p in listOf(bg, fg)) {
            canvas.drawLine(x1, y, x2, y, p)
            canvas.drawLine(x1, y, x1, y - tick, p)
            canvas.drawLine(x2, y, x2, y - tick, p)
        }
        val textFg = Paint().apply { color = Color.BLACK; this.textSize = textSize; isAntiAlias = true; textAlign = Paint.Align.RIGHT }
        val textBg = Paint(textFg).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = textSize * 0.15f }
        canvas.drawText(label, x2, y - tick - textSize * 0.3f, textBg)
        canvas.drawText(label, x2, y - tick - textSize * 0.3f, textFg)
    }

    /** Arrondi "joli" (1, 2, 5 x 10^k) le plus proche par le bas de [m]. */
    private fun niceDistance(m: Double): Double {
        val pow = 10.0.pow(floor(log10(m)))
        val f = m / pow
        val n = when { f < 1.5 -> 1.0; f < 3.5 -> 2.0; f < 7.5 -> 5.0; else -> 10.0 }
        return n * pow
    }

    private fun save(bmp: Bitmap, file: File) {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---- Projection Web Mercator (pixels globaux au zoom donné) ----
    private fun pxX(lon: Double, world: Double): Double = (lon + 180.0) / 360.0 * world
    private fun pxY(lat: Double, world: Double): Double {
        val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * world
    }
}
