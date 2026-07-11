package fr.lc4918.trailog.ui.profile

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.TrackStats
import kotlin.math.roundToInt

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
        fun sx(x: Double) = padLpx + ((x - minX) / spanX * plotW).toFloat()
        fun sy(z: Double) = padT + ((maxZ - z) / spanZ * plotH).toFloat()

        val labelPaint = Paint().apply {
            isAntiAlias = true; color = textColor.toArgb(); textSize = axisFontSp.sp.toPx(); isFakeBoldText = axisBold
        }

        val yTicks = 3
        for (i in 0..yTicks) {
            val z = minZ + spanZ * i / yTicks; val y = sy(z)
            if (grid) drawLine(gridColor, Offset(padLpx, y), Offset(padLpx + plotW, y), strokeWidth = 1f)
            labelPaint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText("${z.roundToInt()}", padLpx - 5f, y + axisFontSp.sp.toPx() / 3f, labelPaint)
        }
        val xTicks = 4
        for (i in 0..xTicks) {
            val xVal = minX + spanX * i / xTicks; val x = sx(xVal)
            if (grid && i in 1 until xTicks) drawLine(gridColor, Offset(x, padT), Offset(x, baseY), strokeWidth = 1f)
            labelPaint.textAlign = Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(fmtKm((xVal - minX) / 1000.0), x, baseY + axisFontSp.sp.toPx() + 6f, labelPaint)
        }
        drawLine(axisColor, Offset(padLpx, padT), Offset(padLpx, baseY), strokeWidth = 2f)
        drawLine(axisColor, Offset(padLpx, baseY), Offset(padLpx + plotW, baseY), strokeWidth = 2f)

        if (slope) {
            for (i in 1 until samples.size) {
                val a = samples[i - 1]; val b = samples[i]
                val col = SlopeRamp.colorFor(b.slope, stats.maxAbsSlope)
                drawPath(Path().apply {
                    moveTo(sx(a.x), baseY); lineTo(sx(a.x), sy(a.z)); lineTo(sx(b.x), sy(b.z)); lineTo(sx(b.x), baseY); close()
                }, col)
            }
        } else {
            drawPath(Path().apply {
                moveTo(sx(minX), baseY); samples.forEach { lineTo(sx(it.x), sy(it.z)) }; lineTo(sx(maxX), baseY); close()
            }, areaColor)
        }

        drawPath(Path().apply {
            moveTo(sx(samples.first().x), sy(samples.first().z)); samples.forEach { lineTo(sx(it.x), sy(it.z)) }
        }, lineColor, style = Stroke(width = 2.5f))

        cursorIndex?.let { idx ->
            if (idx in samples.indices) {
                val s = samples[idx]; val cx = sx(s.x); val cy = sy(s.z)
                drawLine(Color(0x99000000), Offset(cx, padT), Offset(cx, baseY), strokeWidth = 2f)
                drawCircle(Color.White, radius = 7f, center = Offset(cx, cy))
                drawCircle(lineColor, radius = 7f, center = Offset(cx, cy), style = Stroke(width = 3.5f))
            }
        }
    }
}

private const val padR = 8f
private const val padT = 6f
private fun padL(axisFontSp: Int) = axisFontSp * 3.6f + 14f

private fun fmtKm(km: Double): String = if (km < 10) String.format("%.1f", km) else "${km.roundToInt()}"
