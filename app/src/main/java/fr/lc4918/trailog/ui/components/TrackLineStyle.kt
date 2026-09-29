package fr.lc4918.trailog.ui.components

import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.TrackPoint
import fr.lc4918.trailog.ui.profile.SlopeRamp
import kotlin.math.floor
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

    /**
     * La trace COMPLETE [points], chaque point portant la pente de son passage dans le [profile].
     *
     * Le profil ne garde que 2000 echantillons : dessiner le trait de pente sur eux coupait les virages en
     * ligne droite, et les chevrons, poses sur la vraie geometrie, s'en ecartaient. La pente se lit donc
     * au profil, mais le trait suit chaque point de la trace - ceux que [keep] retient, quand la trace est
     * simplifiee pour le rendu. Un point prend la pente du segment de profil ou il tombe : celle de
     * l'echantillon qui clot ce segment, comme dans [features].
     */
    fun alongTrack(points: List<TrackPoint>, profile: List<Sample>, keep: BooleanArray? = null): List<Sample> {
        if (points.isEmpty() || profile.isEmpty()) return emptyList()
        val out = ArrayList<Sample>()
        var x = 0.0
        var k = 0
        for (i in points.indices) {
            val p = points[i]
            if (i > 0) x += TrackMath.haversine(points[i - 1].lon, points[i - 1].lat, p.lon, p.lat)
            if (keep != null && !keep[i]) continue
            // Les distances croissent : l'echantillon courant ne fait qu'avancer.
            while (k < profile.lastIndex && profile[k].x < x) k++
            out.add(Sample(x, 0.0, profile[k].slope, null, p.lon, p.lat))
        }
        return out
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
 * En travers du trait, son encre noire deborde d'au plus [MAX_OVERFLOW] la largeur de la ligne : plus
 * large, elle masquerait le trait sous une file de fleches, alors qu'elle ne doit qu'en dire le sens. Il
 * gagne sa taille en LONGUEUR, le long du trait, et en epaisseur d'encre. Seul un fin lisere blanc, qui le
 * detache d'un trait sombre et se fond dans un trait clair, depasse cette encre.
 */
object TrackChevron {
    const val MAX_OVERFLOW = 1.10f
    private const val STROKE = 0.21f     // epaisseur de l'encre, en part de la hauteur de l'image
    private const val HALO = 1.5f        // largeur du halo, en epaisseurs d'encre

    /** Hauteur de l'image, en travers du trait : l'encre, plus le lisere du halo de part et d'autre. */
    fun heightPx(lineWidthDp: Float, density: Float): Int =
        floor(lineWidthDp * MAX_OVERFLOW * density / (1f - (HALO - 1f) * STROKE)).toInt().coerceIn(4, 160)

    /** Longueur de l'image, le long du trait. */
    fun widthPx(heightPx: Int): Int = (heightPx * 0.8f).roundToInt().coerceAtLeast(4)

    fun strokePx(heightPx: Int): Float = (heightPx * STROKE).coerceAtLeast(1.5f)

    /** Le halo blanc qui detache l'encre du trait, dessine dans le meme cadre que le chevron. */
    fun haloPx(heightPx: Int): Float = strokePx(heightPx) * HALO

    /** Etendue de l'encre noire en travers du trait : la hauteur, moins le lisere du halo de chaque cote. */
    fun inkPx(heightPx: Int): Float = heightPx - (haloPx(heightPx) - strokePx(heightPx))
}
