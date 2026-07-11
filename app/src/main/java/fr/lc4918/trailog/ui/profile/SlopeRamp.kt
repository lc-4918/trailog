package fr.lc4918.trailog.ui.profile

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/** Rampe de pente bleu->rouge (cyan/vert, jaune pur au milieu), portée depuis la lib JS. */
object SlopeRamp {
    private val stops = listOf(
        0.0 to Color(0xFF2166AC), 0.16 to Color(0xFF27A35A), 0.42 to Color(0xFFFFE000),
        0.68 to Color(0xFFF4791F), 1.0 to Color(0xFFD7191C),
    )

    fun at(t: Float): Color {
        val x = t.coerceIn(0f, 1f)
        for (i in 1 until stops.size) {
            val (t0, c0) = stops[i - 1]; val (t1, c1) = stops[i]
            if (x <= t1) {
                val f = if (t1 == t0) 0f else ((x - t0) / (t1 - t0)).toFloat()
                return Color(
                    red = c0.red + (c1.red - c0.red) * f,
                    green = c0.green + (c1.green - c0.green) * f,
                    blue = c0.blue + (c1.blue - c0.blue) * f,
                )
            }
        }
        return stops.last().second
    }

    /** Couleur d'une pente selon les classes présentes (classSize %, maxClasses). */
    fun colorFor(slope: Double, maxAbsSlope: Double, classSize: Double = 2.5, maxClasses: Int = 8): Color {
        val maxIdx = min(floor(maxAbsSlope / classSize).toInt(), maxClasses - 1).coerceAtLeast(1)
        val idx = min(maxIdx, floor(abs(slope) / classSize).toInt())
        return at(idx.toFloat() / maxIdx)
    }
}
