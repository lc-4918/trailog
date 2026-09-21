package fr.lc4918.trailog.ui.profile

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Les graduations de la legende : tous les cinq points, et le seuil du plat. */
internal val SlopeLegendMarks = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 0.0, -5.0, -10.0, -15.0, -20.0, -25.0)

/** Libelle d'une graduation : "+/-1%" pour le plat, comme la legende d'OruxMaps. */
internal fun slopeMarkLabel(v: Double): String =
    if (v == 0.0) "+/-${SlopeRamp.FlatPct.roundToInt()}%" else "${v.roundToInt()}%"

/**
 * Legende des pentes : la trame entiere, bande par bande, et ses graduations.
 *
 * Horizontale sous le profil - la descente a gauche, la montee a droite, comme on lit un graphique -, ou
 * verticale, la montee en haut, telle que la montre OruxMaps (cf. le "i" des reglages). Toutes les bandes
 * ont la meme epaisseur, plat compris : c'est la trame qu'on montre, pas une echelle des pentes.
 */
@Composable
fun SlopeLegend(
    classTenths: Int, fontSp: Int, modifier: Modifier = Modifier, bold: Boolean = false,
    vertical: Boolean = false,
    /** Longueur de la trame en vertical ; en horizontal, elle prend la largeur donnee. */
    length: Dp = 320.dp,
) {
    val bands = remember(classTenths) { SlopeRamp.bands(classTenths) }
    val textColor = LocalContentColor.current.takeIf { it != Color.Unspecified } ?: Color.Black
    val paint = remember { Paint().apply { isAntiAlias = true } }
    val barDp = (fontSp + 3).dp
    val dims = if (vertical) Modifier.size(width = barDp + (fontSp * 4).dp, height = length)
    else Modifier.fillMaxWidth().height(barDp + (fontSp + 4).dp)
    Canvas(modifier.then(dims).testTag("slope_legend")) {
        paint.textSize = fontSp.sp.toPx(); paint.isFakeBoldText = bold; paint.color = textColor.toArgb()
        val bar = barDp.toPx()
        val gap = 4.dp.toPx()
        // Montee en haut en vertical ; descente a gauche en horizontal.
        val ordered = if (vertical) bands else bands.reversed()
        if (vertical) {
            val step = size.height / ordered.size
            ordered.forEachIndexed { i, (_, c) -> drawRect(c, Offset(0f, i * step), Size(bar, step + 0.5f)) }
            paint.textAlign = Paint.Align.LEFT
            SlopeLegendMarks.forEach { v ->
                val i = ordered.indexOfFirst { abs(it.first - v) < 1e-9 }.takeIf { it >= 0 } ?: return@forEach
                val y = (i + 0.5f) * step + paint.textSize / 3f
                drawContext.canvas.nativeCanvas.drawText(slopeMarkLabel(v), bar + gap, y, paint)
            }
        } else {
            val step = size.width / ordered.size
            ordered.forEachIndexed { i, (_, c) -> drawRect(c, Offset(i * step, 0f), Size(step + 0.5f, bar)) }
            val y = bar + gap + paint.textSize * 0.8f
            SlopeLegendMarks.forEach { v ->
                val i = ordered.indexOfFirst { abs(it.first - v) < 1e-9 }.takeIf { it >= 0 } ?: return@forEach
                val x = (i + 0.5f) * step
                // Les deux bouts s'alignent sur le bord, faute de quoi ils sortiraient du cadre.
                paint.textAlign = when (i) {
                    0 -> Paint.Align.LEFT
                    ordered.lastIndex -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }
                val xx = when (i) { 0 -> 0f; ordered.lastIndex -> size.width; else -> x }
                drawContext.canvas.nativeCanvas.drawText(slopeMarkLabel(v), xx, y, paint)
            }
        }
    }
}
