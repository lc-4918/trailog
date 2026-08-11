package fr.lc4918.trailog.elevation

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/** Un endroit dont on cherche l'altitude. Un couple nomme plutot qu'un `Pair` : lon/lat s'inversent d'un
 *  service a l'autre, et c'est la faute la plus facile a commettre ici. */
data class LonLat(val lon: Double, val lat: Double)

/** Emprise rectangulaire en degres, telle que l'attendent les services de terrain. */
data class Bbox(val west: Double, val south: Double, val east: Double, val north: Double) {

    val widthDeg: Double get() = east - west
    val heightDeg: Double get() = north - south

    /** Le plus grand des deux cotes, en degres : c'est lui qui dit si l'emprise tient dans ce qu'on
     *  s'autorise a telecharger d'un coup. */
    val sideDeg: Double get() = max(widthDeg, heightDeg)

    /**
     * L'emprise elargie a [minMeters] de cote au minimum, et d'au moins [marginCells] cellules.
     *
     * Les deux elargissements repondent a deux besoins distincts. Le premier tient au service, qui refuse
     * les emprises de moins de 250 m de cote environ ; on vise plus large pour ne pas se cogner a sa borne.
     * Le second sert a l'interpolation : un point pose sur le bord de l'emprise n'a pas de voisin au-dela,
     * et [AsciiGrid.sample] ne rendrait rien.
     *
     * Un degre de longitude se resserre vers les poles, d'ou le cosinus : sans lui, l'emprise d'un point en
     * Laponie serait deux fois trop etroite pour le service.
     */
    fun padded(minMeters: Double, cellDeg: Double, marginCells: Int = 2): Bbox {
        val latMid = (south + north) / 2
        val degPerMeterLat = 1.0 / 111_320.0
        val degPerMeterLon = degPerMeterLat / max(0.05, cos(Math.toRadians(latMid)))
        val minLat = minMeters * degPerMeterLat
        val minLon = minMeters * degPerMeterLon
        val margin = cellDeg * marginCells
        val padLon = max(margin, (minLon - widthDeg) / 2 + margin)
        val padLat = max(margin, (minLat - heightDeg) / 2 + margin)
        return Bbox(
            (west - padLon).coerceAtLeast(-180.0), (south - padLat).coerceAtLeast(-90.0),
            (east + padLon).coerceAtMost(180.0), (north + padLat).coerceAtMost(90.0),
        )
    }

    companion object {
        /** L'emprise qui contient tous ces points. Null pour une liste vide : il n'y a pas d'emprise vide. */
        fun of(points: List<LonLat>): Bbox? {
            if (points.isEmpty()) return null
            var w = points[0].lon; var e = w; var s = points[0].lat; var n = s
            for (p in points) {
                if (p.lon < w) w = p.lon; if (p.lon > e) e = p.lon
                if (p.lat < s) s = p.lat; if (p.lat > n) n = p.lat
            }
            return Bbox(w, s, e, n)
        }

        /**
         * Le cote de l'emprise de [points] etendue a [next], sans la construire.
         *
         * Sert au decoupage en emprises telechargeables (cf. [ElevationFiller.chunks]) : la question posee
         * a chaque point est "est-ce que celui-la tient encore dans l'emprise en cours ?", et y repondre en
         * reconstruisant une emprise a chaque point couterait un balayage complet par point.
         */
        fun sideWith(w: Double, s: Double, e: Double, n: Double, next: LonLat): Double =
            max(abs(max(e, next.lon) - kotlin.math.min(w, next.lon)),
                abs(max(n, next.lat) - kotlin.math.min(s, next.lat)))
    }
}
