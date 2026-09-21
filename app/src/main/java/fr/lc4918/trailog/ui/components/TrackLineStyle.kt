package fr.lc4918.trailog.ui.components

import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.ui.profile.SlopeRamp
import kotlin.math.roundToInt

/**
 * Les troncons d'une ligne coloriee par pente, en GeoJSON : ce que la carte dessine pour un itineraire
 * calcule et pour une couche "coloriee selon la pente".
 *
 * Un troncon par plage de meme classe, et non un par segment : deux mille segments feraient deux mille
 * objets a dessiner pour une douzaine de couleurs. Les plages **partagent leur point de jonction**, sans
 * quoi la ligne s'ouvrirait d'un trou a chaque changement de couleur.
 *
 * La couleur voyage dans une propriete de chaque troncon ("color") et non dans le style : le style est
 * fixe, les couleurs changent avec la trace.
 */
object SlopeLines {

    /**
     * Les troncons d'une ligne, separes par des virgules. [classTenths] nul : un seul troncon, de la
     * couleur [plainColor].
     */
    fun features(pts: List<Sample>, classTenths: Int?, plainColor: String): String {
        if (pts.size < 2) return ""
        fun feature(from: Int, to: Int, color: String): String {
            val coords = (from..to).joinToString(",") { "[${pts[it].lon},${pts[it].lat}]" }
            return """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},""" +
                """"properties":{"color":"$color"}}"""
        }
        if (classTenths == null) return feature(0, pts.lastIndex, plainColor)
        val out = StringBuilder()
        var i = 0
        while (i < pts.lastIndex) {
            // La pente d'un echantillon est celle du segment qui y ARRIVE : le segment i -> i+1 prend donc
            // celle de i+1.
            val cls = SlopeRamp.classOf(pts[i + 1].slope, classTenths)
            var j = i + 1
            while (j < pts.lastIndex && SlopeRamp.classOf(pts[j + 1].slope, classTenths) == cls) j++
            if (out.isNotEmpty()) out.append(',')
            out.append(feature(i, j, SlopeRamp.hex(SlopeRamp.at(cls))))
            i = j
        }
        return out.toString()
    }

    /** Une collection pour plusieurs lignes - les segments d'une couche. */
    fun collection(lines: List<List<Sample>>, classTenths: Int): String {
        val features = lines.filter { it.size >= 2 }.joinToString(",") { features(it, classTenths, "#000000") }
        return """{"type":"FeatureCollection","features":[$features]}"""
    }
}

/**
 * Dimensions du chevron du sens de parcours, en pixels.
 *
 * Sa hauteur deborde A PEINE la largeur du trait - un peu plus d'un tiers, et un dp - : un chevron plus
 * grand que la ligne la masquerait sous une file de fleches, alors qu'il ne doit qu'en dire le sens.
 */
object TrackChevron {
    fun heightPx(lineWidthDp: Float, density: Float): Int =
        ((lineWidthDp * 1.35f + 1f) * density).roundToInt().coerceIn(6, 160)

    fun widthPx(heightPx: Int): Int = (heightPx * 0.6f).roundToInt().coerceAtLeast(4)

    fun strokePx(heightPx: Int): Float = (heightPx * 0.16f).coerceAtLeast(1f)
}
