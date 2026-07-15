package fr.lc4918.trailog.ui.profile

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.min

/** Légende des pentes : carrés de couleur (taille = police) + bornes de classe. */
@Composable
fun SlopeLegend(maxAbsSlope: Double, fontSp: Int, modifier: Modifier = Modifier, bold: Boolean = false, classSize: Double = 2.5, maxClasses: Int = 6) {
    val maxIdx = min(floor(maxAbsSlope / classSize).toInt(), maxClasses - 1).coerceAtLeast(1)
    val sq = (fontSp + 3).dp
    Row(modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..maxIdx) {
            val col = SlopeRamp.at(i.toFloat() / maxIdx)
            Box(Modifier.size(sq).clip(RoundedCornerShape(2.dp)).background(col))
            Spacer(Modifier.width(3.dp))
            val lo = i * classSize
            val label = if (i == maxIdx) ">=${fmt(lo)}%" else "${fmt(lo)}-${fmt(lo + classSize)}%"
            Text(label, fontSize = fontSp.sp, fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null)
            Spacer(Modifier.width(10.dp))
        }
    }
}

@SuppressLint("DefaultLocale")
private fun fmt(v: Double): String = if (v % 1.0 == 0.0) "${v.toInt()}" else String.format("%.1f", v)
