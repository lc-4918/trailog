package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.TrackPoint

/**
 * Report des altitudes rendues par Valhalla sur les points de son tracé.
 *
 * Le moteur ne rend **pas** une altitude par point de la polyligne : il échantillonne le modèle de terrain
 * à pas constant le long du segment (`elevation_interval`, en mètres). Les deux listes n'ont donc ni la
 * même longueur ni les mêmes abscisses - un itinéraire de 12,7 km rend 762 points de tracé, espacés de 17 m
 * en moyenne mais très inégalement, et 425 altitudes régulièrement espacées de 30 m.
 *
 * Le sens du rapprochement n'est pas indifférent : on ramène les altitudes **sur** le tracé, et non
 * l'inverse. C'est le tracé qui porte les coordonnées, dont on a besoin pour teinter la ligne sur la carte
 * et pour promener le curseur du profil ; une altitude échantillonnée n'a, elle, aucune position propre.
 *
 * Sans dépendance Android, comme le reste du calcul de tracé : vérifiable sans émulateur.
 */
object RouteElevation {

    /**
     * Points de l'itinéraire, altitude comprise.
     *
     * Rend les points sans altitude ([TrackPoint.ele] nul) quand le service n'en a pas fourni : instance
     * auto-hébergée sans données de terrain, ou version antérieure au paramètre `elevation_interval`. Le
     * tracé reste alors affichable, seul le profil est refusé plus haut.
     */
    fun pointsOf(
        shape: List<Pair<Double, Double>>,
        elevation: List<Double>,
        intervalM: Double,
    ): List<TrackPoint> {
        if (shape.isEmpty()) return emptyList()
        if (elevation.isEmpty() || intervalM <= 0.0) {
            return shape.map { (lon, lat) -> TrackPoint(lon, lat) }
        }
        val out = ArrayList<TrackPoint>(shape.size)
        var cum = 0.0
        for (i in shape.indices) {
            val (lon, lat) = shape[i]
            if (i > 0) cum += TrackMath.haversine(shape[i - 1].first, shape[i - 1].second, lon, lat)
            out.add(TrackPoint(lon, lat, eleAt(elevation, cum / intervalM)))
        }
        return out
    }

    /**
     * Altitude à l'abscisse [pos], exprimée en pas d'échantillonnage (2,5 = entre la 3e et la 4e altitude).
     *
     * Bornée aux deux extrémités plutôt que de lever : la longueur cumulée de la polyligne, somme de cordes
     * droites entre points, ne retombe jamais exactement sur celle qu'a mesurée le moteur le long des voies.
     * Le dernier point sort donc de la table de quelques mètres, et prolonger la dernière altitude y vaut
     * mieux qu'un profil tronqué.
     */
    private fun eleAt(e: List<Double>, pos: Double): Double {
        if (pos <= 0.0) return e.first()
        val lo = pos.toInt()
        if (lo >= e.lastIndex) return e.last()
        return e[lo] + (e[lo + 1] - e[lo]) * (pos - lo)
    }
}
