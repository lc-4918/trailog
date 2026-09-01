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

    /** Distance isolée, à l'unité adaptée : sous le kilomètre (le mile en impérial) on donne des mètres
     *  (des pieds) entiers, au-delà des kilomètres (des miles) à une décimale. Contrairement à [distance],
     *  qui sert à comparer des totaux de traces et garde donc une unité fixe, celle-ci s'affiche seule
     *  et n'a personne avec qui s'aligner : "180 m" y est plus lisible que "0,18 km". */
    fun shortDistance(meters: Double, imperial: Boolean = false): String {
        if (!meters.isFinite() || meters < 0) return ""
        return if (imperial) {
            if (meters < 1609.344) "${(meters * 3.28084).roundToInt()} ft"
            else "${"%.1f".format(meters / 1609.344)} mi"
        } else {
            if (meters < 1000.0) "${meters.roundToInt()} m" else "${"%.1f".format(meters / 1000.0)} km"
        }
    }

    fun elevation(m: Double, imperial: Boolean = false): String =
        if (imperial) "${(m * 3.28084).roundToInt()} ft" else "${m.roundToInt()} m"

    /**
     * Vitesse, du metre par seconde du capteur au kilometre-heure qu'on lit sur un compteur.
     *
     * Une decimale sous 10, aucune au-dela : a 4 km/h le dixieme distingue la marche de la flanerie, a
     * 27 km/h il ne fait que battre la mesure sans rien apprendre.
     */
    fun speed(mps: Double, imperial: Boolean = false): String {
        if (!mps.isFinite() || mps < 0) return ""
        val v = if (imperial) mps * 2.236936 else mps * 3.6
        val unite = if (imperial) "mph" else "km/h"
        return if (v < 10.0) "${"%.1f".format(v)} $unite" else "${v.roundToInt()} $unite"
    }
}
