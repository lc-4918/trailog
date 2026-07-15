package fr.lc4918.trailog.domain.geo

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Formatage adaptatif de la durée : 7 sec · 26 min · 1 h 48 min · 2 j 3 h. */
object Format {
    fun duration(sec: Double?): String {
        if (sec == null || !sec.isFinite()) return ""
        val s = sec.roundToLong().coerceAtLeast(0)
        if (s < 60) return "$s sec"
        if (s < 3600) return "${(s / 60.0).roundToInt()} min"
        if (s < 86400) {
            var h = (s / 3600).toInt(); var m = ((s % 3600) / 60.0).roundToInt()
            if (m == 60) { h++; m = 0 }
            return if (m != 0) "$h h $m min" else "$h h"
        }
        var d = (s / 86400).toInt(); var h = ((s % 86400) / 3600.0).roundToInt()
        if (h == 24) { d++; h = 0 }
        return if (h != 0) "$d j $h h" else "$d j"
    }

    fun distance(meters: Double, imperial: Boolean = false): String =
        if (imperial) "${"%.2f".format(meters / 1609.344)} mi"
        else "${"%.2f".format(meters / 1000.0)} km"

    fun elevation(m: Double, imperial: Boolean = false): String =
        if (imperial) "${(m * 3.28084).roundToInt()} ft" else "${m.roundToInt()} m"
}
