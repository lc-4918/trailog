package fr.lc4918.trailog.ui.profile

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.TrackStats
import kotlin.math.roundToInt

/** Contenu statique du profil (grille, labels, aire, ligne), reconstruit uniquement quand les
 *  données ou l'apparence changent (cf. les champs ci-dessous comparés dans le Canvas). Un simple
 *  déplacement du curseur ne les invalide pas : sans ce cache, tout (jusqu'à ~2000 points) était
 *  reconstruit et redessiné à chaque frame de scrub. */
private class ProfileDrawCache {
    var samplesRef: List<Sample>? = null
    var stats: TrackStats? = null
    var grid: Boolean? = null
    var slope: Boolean? = null
    var lineColor: Color? = null
    var axisFontSp: Int? = null
    var axisBold: Boolean? = null
    var axisColor: Color? = null
    var gridColor: Color? = null
    var textColor: Color? = null
    var vscale: Int? = null
    var w: Float = -1f
    var h: Float = -1f

    var gridLines: List<Pair<Offset, Offset>> = emptyList()
    var yLabels: List<Triple<String, Float, Float>> = emptyList()
    var xLabels: List<Triple<String, Float, Float>> = emptyList()
    var axisPath: Path = Path()
    // aire : par plages de couleur identique (pente) au lieu d'un Path+drawPath par segment.
    var areaRuns: List<Pair<Path, Color>> = emptyList()
    var linePath: Path = Path()
}

/** Profil : axes/ticks/grille + aire (pente ou couleur de la trace) + ligne + curseur. La légende est externe. */
@Composable
fun ElevationProfile(
    samples: List<Sample>,
    stats: TrackStats,
    modifier: Modifier = Modifier,
    grid: Boolean = true,
    slope: Boolean = true,
    lineColor: Color = Color(0xFF1F6FB2),
    axisFontSp: Int = 9,
    axisBold: Boolean = false,
    axisColor: Color = Color(0xFF888888),
    gridColor: Color = Color(0x22000000),
    textColor: Color = Color(0xFF555555),
    cursorIndex: Int? = null,
    onScrub: (Int) -> Unit = {},
    // Repères de sélection du zoom A/B (index dans [samples]) : barre verticale + label, tant que le
    // second point n'est pas posé (puis brièvement le temps que la vue zoomée les remplace - cf. MainScreen).
    markA: Int? = null,
    markB: Int? = null,
    // Marge (px) dont on rentre le dernier label de l'axe X, pour dégager la courbure de l'angle bas-droit
    // de l'écran. 0 si l'écran n'a pas d'angle arrondi (détecté par l'appelant via l'API RoundedCorner).
    lastLabelInsetPx: Float = 0f,
    // Échelle verticale du profil : mètres d'altitude par centimètre physique de l'écran. 0 = Auto (le profil
    // remplit la hauteur, échelle ajustée au dénivelé). Une valeur fixe (ex. 100 = 1 cm pour 100 m) donne un
    // rendu absolu et honnête : le même dénivelé occupe toujours la même hauteur, une pente de 2% ne ressemble
    // plus à un mur. Le profil est alors ancré sur la ligne de base (borné au remplissage pour ne pas déborder).
    verticalScaleMPerCm: Int = 0,
) {
    if (samples.size < 2) return
    val areaColor = lineColor.copy(alpha = 0.30f)   // aire = couleur de la trace si pentes inactives
    val minX = samples.first().x; val maxX = samples.last().x
    val minZ = stats.min; val maxZ = stats.max
    val spanX = (maxX - minX).coerceAtLeast(1.0)
    val spanZ = (maxZ - minZ).coerceAtLeast(1.0)
    val padLpx = padL(axisFontSp)

    fun idxAt(px: Float, w: Float): Int {
        val rel = ((px - padLpx) / (w - padLpx - padR)).coerceIn(0f, 1f)
        val target = minX + rel * spanX
        var lo = 0; var hi = samples.size - 1
        while (lo < hi) { val m = (lo + hi) / 2; if (samples[m].x < target) lo = m + 1 else hi = m }
        return lo
    }

    val cache = remember { ProfileDrawCache() }
    val labelPaint = remember { Paint().apply { isAntiAlias = true } }
    val markPaint = remember { Paint().apply { isAntiAlias = true; isFakeBoldText = true; textAlign = Paint.Align.CENTER } }

    Canvas(
        modifier = modifier
            .pointerInput(samples) { detectTapGestures { onScrub(idxAt(it.x, size.width.toFloat())) } }
            .pointerInput(samples) {
                detectHorizontalDragGestures { ch, _ -> onScrub(idxAt(ch.position.x, size.width.toFloat())) }
            }
    ) {
        val padBpx = axisFontSp.sp.toPx() + 12f
        val w = size.width; val h = size.height
        val plotW = w - padLpx - padR; val plotH = h - padT - padBpx
        val baseY = padT + plotH
        // Échelle horizontale (px/m). Échelle verticale (px/m) : Auto (<=0) remplit la hauteur (plotH/spanZ) ;
        // sinon échelle absolue = cmPx / (m par cm), bornée par le remplissage pour ne pas déborder du cadre.
        // Dans les deux cas le profil est ancré sur la ligne de base (minZ en bas) ; à échelle fixe fine il
        // n'occupe alors qu'une partie de la hauteur (relief honnête, pas étiré).
        val cmPx = (160f / 2.54f).dp.toPx()      // 1 cm physique en px (dp de base = 1/160 pouce)
        val xScale = plotW / spanX
        val yScale = if (verticalScaleMPerCm <= 0) (plotH / spanZ).toDouble()
            else minOf((plotH / spanZ).toDouble(), (cmPx / verticalScaleMPerCm).toDouble())
        val drawnH = (spanZ * yScale).toFloat()
        fun sx(x: Double) = padLpx + ((x - minX) * xScale).toFloat()
        fun sy(z: Double) = baseY - ((z - minZ) * yScale).toFloat()

        val stale = cache.samplesRef !== samples || cache.stats != stats || cache.grid != grid ||
            cache.slope != slope || cache.lineColor != lineColor || cache.axisFontSp != axisFontSp ||
            cache.axisBold != axisBold || cache.axisColor != axisColor || cache.gridColor != gridColor ||
            cache.textColor != textColor || cache.vscale != verticalScaleMPerCm || cache.w != w || cache.h != h
        if (stale) {
            labelPaint.textSize = axisFontSp.sp.toPx(); labelPaint.isFakeBoldText = axisBold; labelPaint.color = textColor.toArgb()

            val gridLines = ArrayList<Pair<Offset, Offset>>()
            val yLabels = ArrayList<Triple<String, Float, Float>>()
            val xLabels = ArrayList<Triple<String, Float, Float>>()
            // À échelle fixe le profil peut n'occuper qu'une faible hauteur : on réduit le nombre de
            // graduations Y pour que les labels ne se chevauchent pas.
            val yTicks = if (verticalScaleMPerCm <= 0) 3
                else (drawnH / (axisFontSp.sp.toPx() * 2.2f)).toInt().coerceIn(1, 3)
            for (i in 0..yTicks) {
                val z = minZ + spanZ * i / yTicks; val y = sy(z)
                if (grid) gridLines.add(Offset(padLpx, y) to Offset(padLpx + plotW, y))
                yLabels.add(Triple("${z.roundToInt()}", padLpx - 5f, y + axisFontSp.sp.toPx() / 3f))
            }
            val xTicks = 4
            for (i in 0..xTicks) {
                val xVal = minX + spanX * i / xTicks; val x = sx(xVal)
                if (grid && i in 1 until xTicks) gridLines.add(Offset(x, padT) to Offset(x, baseY))
                xLabels.add(Triple(fmtKm((xVal - minX) / 1000.0), x, baseY + axisFontSp.sp.toPx() + 6f))
            }

            cache.gridLines = gridLines
            cache.yLabels = yLabels
            cache.xLabels = xLabels
            cache.axisPath = Path().apply {
                moveTo(padLpx, padT); lineTo(padLpx, baseY)
                moveTo(padLpx, baseY); lineTo(padLpx + plotW, baseY)
            }
            cache.areaRuns = if (slope) {
                buildAreaRuns(samples, stats.maxAbsSlope, ::sx, ::sy, baseY)
            } else {
                listOf(
                    Path().apply {
                        moveTo(sx(minX), baseY); samples.forEach { lineTo(sx(it.x), sy(it.z)) }; lineTo(sx(maxX), baseY); close()
                    } to areaColor
                )
            }
            cache.linePath = Path().apply {
                moveTo(sx(samples.first().x), sy(samples.first().z)); samples.forEach { lineTo(sx(it.x), sy(it.z)) }
            }

            cache.samplesRef = samples; cache.stats = stats; cache.grid = grid; cache.slope = slope
            cache.lineColor = lineColor; cache.axisFontSp = axisFontSp; cache.axisBold = axisBold
            cache.axisColor = axisColor; cache.gridColor = gridColor; cache.textColor = textColor
            cache.vscale = verticalScaleMPerCm; cache.w = w; cache.h = h
        }

        cache.gridLines.forEach { (a, b) -> drawLine(gridColor, a, b, strokeWidth = 1f) }
        labelPaint.textAlign = Paint.Align.RIGHT
        cache.yLabels.forEach { (t, x, y) -> drawContext.canvas.nativeCanvas.drawText(t, x, y, labelPaint) }
        // Tous centrés sous leur tick, sauf le dernier : centré, il déborderait à droite et serait rogné par
        // l'angle arrondi de l'écran -> on l'aligne à droite ET on le rentre de [lastLabelInsetPx] vers
        // l'intérieur (0 si l'écran est plat, cf. détection RoundedCorner côté appelant).
        cache.xLabels.forEachIndexed { i, (t, x, y) ->
            if (i == cache.xLabels.lastIndex) {
                labelPaint.textAlign = Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText(t, x - lastLabelInsetPx, y, labelPaint)
            } else {
                labelPaint.textAlign = Paint.Align.CENTER
                drawContext.canvas.nativeCanvas.drawText(t, x, y, labelPaint)
            }
        }
        drawPath(cache.axisPath, axisColor, style = Stroke(width = 2f))

        cache.areaRuns.forEach { (path, col) -> drawPath(path, col) }
        drawPath(cache.linePath, lineColor, style = Stroke(width = 2.5f))

        cursorIndex?.let { idx ->
            if (idx in samples.indices) {
                val s = samples[idx]; val cx = sx(s.x); val cy = sy(s.z)
                drawLine(Color(0x99000000), Offset(cx, padT), Offset(cx, baseY), strokeWidth = 2f)
                drawCircle(Color.White, radius = 7f, center = Offset(cx, cy))
                drawCircle(lineColor, radius = 7f, center = Offset(cx, cy), style = Stroke(width = 3.5f))
            }
        }

        // Repères A/B du zoom : barre verticale pleine largeur + label juste sous le bord haut du graphique
        // (la légende de pente, elle, est un composable externe au-dessus de ce Canvas).
        if (markA != null || markB != null) {
            markPaint.textSize = (axisFontSp + 2).sp.toPx()
            markPaint.color = MARK_COLOR.toArgb()
            fun drawMark(idx: Int, label: String) {
                if (idx !in samples.indices) return
                val x = sx(samples[idx].x)
                val labelBaseline = padT + markPaint.textSize
                drawContext.canvas.nativeCanvas.drawText(label, x, labelBaseline, markPaint)
                // La barre démarre sous la lettre (baseline + descente) pour ne pas la traverser.
                drawLine(MARK_COLOR, Offset(x, labelBaseline + markPaint.descent() + 2f), Offset(x, baseY), strokeWidth = 2f)
            }
            markA?.let { drawMark(it, "A") }
            markB?.let { drawMark(it, "B") }
        }
    }
}

private val MARK_COLOR = Color(0xFFE8590C)

/** Regroupe les segments consécutifs de même couleur (classe de pente) en un seul Path : évite
 *  jusqu'à ~2000 Path/drawPath (un par segment) pour n'en garder qu'un par plage de pente stable. */
private fun buildAreaRuns(
    samples: List<Sample>,
    maxAbsSlope: Double,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    baseY: Float,
): List<Pair<Path, Color>> {
    val runs = ArrayList<Pair<Path, Color>>()
    var i = 1
    while (i < samples.size) {
        val col = SlopeRamp.colorFor(samples[i].slope, maxAbsSlope)
        var j = i
        while (j + 1 < samples.size && SlopeRamp.colorFor(samples[j + 1].slope, maxAbsSlope) == col) j++
        val path = Path().apply {
            moveTo(sx(samples[i - 1].x), baseY)
            lineTo(sx(samples[i - 1].x), sy(samples[i - 1].z))
            for (k in i..j) lineTo(sx(samples[k].x), sy(samples[k].z))
            lineTo(sx(samples[j].x), baseY)
            close()
        }
        runs.add(path to col)
        i = j + 1
    }
    return runs
}

private const val padR = 8f
private const val padT = 6f
private fun padL(axisFontSp: Int) = axisFontSp * 3.6f + 14f

private fun fmtKm(km: Double): String = if (km < 10) String.format("%.1f", km) else "${km.roundToInt()}"
