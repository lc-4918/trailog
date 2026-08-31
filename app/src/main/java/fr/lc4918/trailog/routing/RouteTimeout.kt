package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.geo.TrackMath

/**
 * Combien de temps laisser au moteur d'itineraire, selon la longueur qu'on lui demande.
 *
 * **Le defaut que cela corrige.** Les deux clients attendaient un delai FIXE - 30 s chez BRouter, 15 s chez
 * Valhalla - dimensionne pour la sortie du dimanche. Un itineraire de cinq cents kilometres traverse des
 * dizaines de milliers de segments, et le service met sensiblement plus longtemps a le rendre : la requete
 * expirait, l'ecran annoncait "Aucun itineraire", et le trajet etait pourtant parfaitement calculable. Le
 * signalement disait exactement cela - "sur un grand itineraire genre 500 km, j'ai souvent aucun itineraire
 * trouve la premiere fois".
 *
 * **Le vol d'oiseau des etapes**, et non la longueur du trajet : celle-ci n'est connue qu'une fois le calcul
 * fait, ce qui est un peu tard pour decider combien de temps l'attendre. La somme des segments droits entre
 * etapes minore le trajet reel - un itineraire cyclable est toujours plus long que la ligne droite - donc
 * l'estimation est prudente par construction : elle accorde un peu moins de temps qu'il n'en faudrait, jamais
 * plus. Le pas par tranche est assez large pour absorber l'ecart.
 *
 * **Un plafond**, parce qu'un delai est aussi une promesse faite a celui qui regarde le rond tourner : au-dela
 * de deux minutes, l'attente ne se distingue plus d'un blocage, et mieux vaut dire qu'on n'a pas trouve.
 */
object RouteTimeout {

    /** Ce qu'on accorde a un trajet de proximite : de quoi payer l'ouverture de la liaison - DNS, TCP, TLS -
     *  et le calcul lui-meme. C'est l'ancien delai de BRouter, qui suffisait a tout sauf aux longs trajets. */
    const val BASE_MS = 30_000

    /**
     * Ce qu'on ajoute par centaine de kilometres demandee, au prorata et non par paliers : un trajet ne
     * devient pas long d'un coup a un kilometre pres, et un palier ferait varier le delai du simple au
     * double de part et d'autre d'une frontiere que rien ne justifie.
     */
    const val PER_100KM_MS = 12_000

    /** Au-dela, on renonce : une attente qu'on ne peut plus distinguer d'un blocage. */
    const val MAX_MS = 120_000

    /**
     * Le delai a accorder pour un trajet passant par [points], en (lat, lon).
     *
     * Moins de deux etapes : le delai de base. Il n'y a rien a calculer, et l'appelant s'arrete avant la
     * requete - mais rendre une valeur utilisable plutot que zero evite un cas particulier chez lui.
     */
    fun msFor(points: List<Pair<Double, Double>>): Int {
        val km = crowKm(points)
        val ms = BASE_MS + (km / 100.0 * PER_100KM_MS).toInt()
        return ms.coerceIn(BASE_MS, MAX_MS)
    }

    /** La somme des segments droits entre etapes successives, en kilometres. */
    internal fun crowKm(points: List<Pair<Double, Double>>): Double {
        if (points.size < 2) return 0.0
        var m = 0.0
        for (i in 1 until points.size) {
            val (aLat, aLon) = points[i - 1]
            val (bLat, bLon) = points[i]
            m += TrackMath.haversine(aLon, aLat, bLon, bLat)
        }
        return m / 1000.0
    }
}
