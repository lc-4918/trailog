package fr.lc4918.trailog.routing.offline

import fr.lc4918.trailog.map.offline.Bbox
import kotlin.math.cos
import kotlin.math.floor

/**
 * Un carre de donnees BRouter : cinq degres de cote, un fichier `.rd5`, nomme par son coin sud-ouest -
 * `E5_N45` couvre de 5 a 10 degres est et de 45 a 50 degres nord.
 *
 * C'est la seule unite que le serveur public distribue (https://brouter.de/brouter/segments4/) : on ne
 * telecharge pas une region, on telecharge les carres qui la couvrent.
 */
data class BrouterTile(val lon0: Int, val lat0: Int) {
    val name: String get() {
        val ew = if (lon0 < 0) "W${-lon0}" else "E$lon0"
        val ns = if (lat0 < 0) "S${-lat0}" else "N$lat0"
        return "${ew}_$ns"
    }

    val fileName: String get() = "$name.rd5"

    val bbox: Bbox get() = Bbox(
        west = lon0.toDouble(), south = lat0.toDouble(),
        east = (lon0 + SIZE).toDouble(), north = (lat0 + SIZE).toDouble(),
    )

    companion object {
        const val SIZE = 5

        /** Le carre qui contient ce point. Le plancher, et non la troncature : a l'ouest de Greenwich,
         *  -2,4 degres est dans W5, pas dans E0. */
        fun of(lon: Double, lat: Double): BrouterTile =
            BrouterTile(floor(lon / SIZE).toInt() * SIZE, floor(lat / SIZE).toInt() * SIZE)

        private val NOM = Regex("^([EW])(\\d+)_([NS])(\\d+)(\\.rd5)?$")

        /** Relit un nom de carre, avec ou sans son extension. Null s'il n'en est pas un. */
        fun parse(name: String): BrouterTile? {
            val m = NOM.matchEntire(name) ?: return null
            val lon = m.groupValues[2].toInt() * if (m.groupValues[1] == "W") -1 else 1
            val lat = m.groupValues[4].toInt() * if (m.groupValues[3] == "S") -1 else 1
            if (lon % SIZE != 0 || lat % SIZE != 0) return null
            return BrouterTile(lon, lat)
        }
    }
}

object BrouterTiles {

    /** Les carres qu'une emprise touche, meme en partie. */
    fun covering(box: Bbox): List<BrouterTile> {
        val sw = BrouterTile.of(box.west, box.south)
        val ne = BrouterTile.of(box.east, box.north)
        return buildList {
            var lat = sw.lat0
            while (lat <= ne.lat0) {
                var lon = sw.lon0
                while (lon <= ne.lon0) { add(BrouterTile(lon, lat)); lon += BrouterTile.SIZE }
                lat += BrouterTile.SIZE
            }
        }
    }

    /**
     * Les carres qu'un parcours traverse, elargi de [radiusM] de chaque cote - (lat, lon), comme les
     * parcours du telechargement hors ligne.
     *
     * Point par point, et non sur l'emprise du parcours : un trajet en diagonale sur trois carres n'a que
     * faire du quatrieme, qui fermerait son rectangle - deux cents megaoctets pour rien.
     */
    fun along(points: List<Pair<Double, Double>>, radiusM: Double): Set<BrouterTile> {
        val dLat = radiusM / M_PAR_DEGRE
        return points.flatMapTo(LinkedHashSet()) { (lat, lon) ->
            val dLon = dLat / cos(Math.toRadians(lat)).coerceAtLeast(1e-6)
            covering(Bbox(west = lon - dLon, south = lat - dLat, east = lon + dLon, north = lat + dLat))
        }
    }

    private const val M_PAR_DEGRE = 111_320.0
}
