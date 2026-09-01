package fr.lc4918.trailog.ui.poi

import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.poi.Poi
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Le couloir des traces affichees : les points d'interet qui le bordent, et eux seuls.
 *
 * **Le probleme qu'il resout.** Le zoom minimum de chargement etait haut - une ville et ses abords - parce
 * qu'a l'echelle d'une region la vue porte des milliers de lieux dont le service ne rend que les premiers,
 * pris dans un ordre que rien ne fixe. On zoomait donc beaucoup pour voir quoi que ce soit, et le testeur
 * l'a dit ainsi : "il faudrait ne chercher les points d'interet qui sont a une distance raisonnable de la
 * trace, ca permettrait de zoomer moins".
 *
 * Un couloir repond aux deux : il retire de la vue ce qui ne borde aucune trace - un restaurant a quinze
 * kilometres de l'itineraire n'interesse personne qui le prepare - et ce qui reste tient a l'ecran meme
 * dezoome. C'est ce qui rend un zoom minimum plus bas supportable (cf. [PoiLoading.MIN_ZOOM]).
 *
 * **Il filtre l'AFFICHAGE, pas la requete.** Les services s'interrogent sur une emprise rectangulaire, et
 * aucun des deux ne sait suivre une polyligne. Le tri se fait donc ici, sur ce qui est arrive : cela ne
 * gagne aucune requete, mais c'est la lisibilite de la carte qui etait en cause, pas le quota.
 *
 * **Sans trace affichee, il ne filtre rien** : la couche des points d'interet ne depend pas de la
 * bibliotheque, et vider la carte parce qu'aucune trace n'est ouverte serait remplacer une gene par une
 * panne.
 */
object PoiCorridor {

    /** Metres par degre de latitude. Constant a la precision qui nous interesse ici - on compare une
     *  distance a un seuil de quelques kilometres, pas on ne mesure une etape. */
    private const val M_PER_DEG_LAT = 111_320.0

    /**
     * Ceux de [pois] qui passent a moins de [maxM] d'une des [tracks], chacune donnee par ses points
     * (lon, lat) dans l'ordre.
     *
     * Rend [pois] TEL QUEL quand il n'y a pas de couloir a appliquer : aucune trace affichee, ou une
     * distance nulle - le reglage eteint (cf. `SettingsEntity.poiTrackCorridorM`).
     *
     * La distance se mesure au SEGMENT et non au sommet le plus proche : une trace decimee peut poser deux
     * sommets a cinq cents metres l'un de l'autre en ligne droite, et un lieu pose au milieu de cette
     * droite serait alors rejete alors qu'il est sur le chemin.
     */
    fun filter(
        pois: List<Poi>,
        tracks: List<List<Pair<Double, Double>>>,
        maxM: Double,
    ): List<Poi> {
        if (maxM <= 0.0) return pois
        val utiles = tracks.filter { it.size >= 1 }
        if (utiles.isEmpty()) return pois
        // Une enveloppe par trace, elargie du seuil : un lieu hors de toutes les enveloppes est rejete sans
        // qu'aucun segment soit examine. C'est ce qui tient le cout supportable - quelques centaines de
        // lieux contre quelques milliers de sommets.
        val boites = utiles.map { boite(it, maxM) }
        return pois.filter { p ->
            utiles.indices.any { i -> boites[i].contient(p.lon, p.lat) && proche(p, utiles[i], maxM) }
        }
    }

    private fun proche(p: Poi, track: List<Pair<Double, Double>>, maxM: Double): Boolean {
        // Facteur de reduction des longitudes a la latitude du lieu : un degre de longitude vaut un degre de
        // latitude fois le cosinus de la latitude. Pris au LIEU et non a chaque sommet - la trace la plus
        // longue ne couvre pas assez de latitude pour que la difference compte devant un seuil en
        // kilometres, et le recalculer par sommet doublerait le cout de la boucle.
        val kx = cos(Math.toRadians(p.lat))
        val seuilDeg = maxM / M_PER_DEG_LAT
        val seuil2 = seuilDeg * seuilDeg
        var precedent: Pair<Double, Double>? = null
        for (s in track) {
            val a = precedent
            precedent = s
            // Le sommet lui-meme d'abord : il repond pour le premier point, et pour une trace d'un seul point.
            if (dist2(p.lon, p.lat, s.first, s.second, kx) <= seuil2) return true
            if (a == null) continue
            if (segment2(p.lon, p.lat, a.first, a.second, s.first, s.second, kx) <= seuil2) return true
        }
        return false
    }

    /** Distance au carre entre deux points, en degres de latitude, longitudes reduites par [kx]. */
    private fun dist2(lon: Double, lat: Double, bLon: Double, bLat: Double, kx: Double): Double {
        val dx = (lon - bLon) * kx
        val dy = lat - bLat
        return dx * dx + dy * dy
    }

    /** Distance au carre d'un point au SEGMENT [a,b], meme repere que [dist2]. */
    private fun segment2(
        lon: Double, lat: Double,
        aLon: Double, aLat: Double, bLon: Double, bLat: Double,
        kx: Double,
    ): Double {
        val ax = aLon * kx; val ay = aLat
        val bx = bLon * kx; val by = bLat
        val px = lon * kx; val py = lat
        val vx = bx - ax; val vy = by - ay
        val len2 = vx * vx + vy * vy
        // Segment degenere - deux sommets confondus : le sommet a deja repondu.
        if (len2 == 0.0) return Double.MAX_VALUE
        // Abscisse du projete sur le segment, bornee a ses deux bouts : au-dela, c'est un sommet qui est le
        // plus proche, et il a deja ete teste.
        val t = (((px - ax) * vx + (py - ay) * vy) / len2).coerceIn(0.0, 1.0)
        val dx = px - (ax + t * vx)
        val dy = py - (ay + t * vy)
        return dx * dx + dy * dy
    }

    /**
     * Une des [tracks] passe-t-elle a moins de [maxM] de [box] ?
     *
     * **La question a poser AVANT d'interroger les services.** Le couloir ne filtrait que l'affichage :
     * a plusieurs centaines de kilometres de toute trace, on demandait quand meme les points d'interet aux
     * deux services, on les recevait tous, et on les jetait tous. La carte restait vide - ce qui est
     * l'effet voulu - mais on avait paye le chargement, et le rond du bouton tournait pour rien.
     *
     * Vrai quand il n'y a pas de couloir a appliquer : une distance nulle - le reglage eteint - ou aucune
     * trace affichee ne filtrent rien, et tout ce que la vue porte a donc lieu d'etre demande.
     *
     * Le test porte sur les SEGMENTS et non sur les seuls sommets : une trace decimee peut traverser la
     * vue de part en part sans y poser un seul sommet, et l'on conclurait qu'elle en est loin.
     */
    fun crosses(box: Bbox, tracks: List<List<Pair<Double, Double>>>, maxM: Double): Boolean {
        if (maxM <= 0.0) return true
        val utiles = tracks.filter { it.isNotEmpty() }
        if (utiles.isEmpty()) return true
        // L'emprise elargie du seuil : une trace qui la touche a bien un point a moins de maxM de la vue.
        val dLat = maxM / M_PER_DEG_LAT
        val cosMin = cos(Math.toRadians(max(abs(box.south), abs(box.north)))).coerceAtLeast(1e-6)
        val dLon = dLat / cosMin
        val large = Boite(box.west - dLon, box.south - dLat, box.east + dLon, box.north + dLat)
        return utiles.any { track ->
            // Rejet par l'enveloppe d'abord : deux comparaisons contre quelques milliers de segments.
            val b = boite(track, 0.0)
            if (b.west > large.east || b.east < large.west ||
                b.south > large.north || b.north < large.south
            ) return@any false
            if (track.any { (lon, lat) -> large.contient(lon, lat) }) return@any true
            (1 until track.size).any { i -> coupe(large, track[i - 1], track[i]) }
        }
    }

    /** Le segment [a,b] traverse-t-il la boite, aucun de ses bouts n'y etant. Decoupage de Liang-Barsky,
     *  ramene a ce qu'on en demande : oui ou non, sans le morceau retenu. */
    private fun coupe(b: Boite, a: Pair<Double, Double>, z: Pair<Double, Double>): Boolean {
        var t0 = 0.0
        var t1 = 1.0
        val dx = z.first - a.first
        val dy = z.second - a.second
        val bornes = listOf(
            -dx to (a.first - b.west), dx to (b.east - a.first),
            -dy to (a.second - b.south), dy to (b.north - a.second),
        )
        for ((p, q) in bornes) {
            if (p == 0.0) {
                // Segment parallele a ce bord : hors de la bande, il ne peut pas la traverser.
                if (q < 0) return false
                continue
            }
            val r = q / p
            if (p < 0) { if (r > t1) return false; if (r > t0) t0 = r }
            else { if (r < t0) return false; if (r < t1) t1 = r }
        }
        return true
    }

    private class Boite(
        val west: Double, val south: Double, val east: Double, val north: Double,
    ) {
        fun contient(lon: Double, lat: Double) =
            lon >= west && lon <= east && lat >= south && lat <= north
    }

    /** L'enveloppe d'une trace, elargie de [maxM] de tous cotes. */
    private fun boite(track: List<Pair<Double, Double>>, maxM: Double): Boite {
        var w = Double.MAX_VALUE; var e = -Double.MAX_VALUE
        var s = Double.MAX_VALUE; var n = -Double.MAX_VALUE
        for ((lon, lat) in track) {
            w = min(w, lon); e = max(e, lon)
            s = min(s, lat); n = max(n, lat)
        }
        val dLat = maxM / M_PER_DEG_LAT
        // La marge en longitude se prend a la latitude la PLUS eloignee de l'equateur, ou un degre est le
        // plus court : c'est celle qui donne la marge la plus large, et une enveloppe trop large ne fait que
        // laisser passer des lieux que le test au segment rejettera.
        val cosMin = cos(Math.toRadians(max(abs(s), abs(n)))).coerceAtLeast(1e-6)
        val dLon = dLat / cosMin
        return Boite(w - dLon, s - dLat, e + dLon, n + dLat)
    }
}
