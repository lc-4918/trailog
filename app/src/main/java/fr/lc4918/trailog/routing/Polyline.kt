package fr.lc4918.trailog.routing

/**
 * Decodage des polylignes encodees de Google, format dans lequel Valhalla rend la geometrie de ses
 * itineraires.
 *
 * **Precision 6 et non 5** : Valhalla encode au millionieme de degre, la ou l'algorithme d'origine et la
 * plupart des implementations travaillent au cent-millieme. Decoder au mauvais facteur ne leve rien : le
 * trace sort simplement dix fois trop loin de l'equateur, quelque part dans l'ocean.
 */
object Polyline {

    /** Facteur de Valhalla : coordonnees encodees en millioniemes de degre. */
    const val VALHALLA_PRECISION = 1e6

    /**
     * Rend les points en (lon, lat), l'ordre du GeoJSON.
     *
     * Une chaine tronquee ou fautive rend les points deja lus plutot que de lever : la mesure de distance
     * reste juste (elle vient du total, pas de la geometrie), et une portion de trace vaut mieux que la
     * perte de tout l'affichage.
     */
    fun decode(encoded: String, precision: Double = VALHALLA_PRECISION): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lon = 0
        runCatching {
            while (index < encoded.length) {
                // Chaque valeur est un ecart au point precedent, code en zigzag sur des groupes de 5 bits.
                var shift = 0
                var result = 0
                var b: Int
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

                shift = 0
                result = 0
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                lon += if (result and 1 != 0) (result shr 1).inv() else result shr 1

                out.add(lon / precision to lat / precision)
            }
        }
        return out
    }
}
