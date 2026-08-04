package fr.lc4918.trailog.map

import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.map.offline.Bbox

/**
 * Emprise servie par chaque fond du groupe "Pays". Renseignée à la main, comme les drapeaux
 * (cf. [NATIONAL_FLAG_CODES]) : aucun des services ne l'expose sous une forme exploitable à
 * l'exécution (les WMTS n'en déclarent pas, et le GetCapabilities d'un WMS coûterait une requête
 * réseau au démarrage pour une valeur qui ne bouge jamais).
 *
 * C'est la couverture RÉELLE du service, pas la frontière politique : le Portugal s'arrête au
 * continent (son service ne rend ni Madère ni les Açores), l'Espagne inclut les Canaries.
 * Volontairement un peu large : elle sert à recadrer la carte, pas à décider ce qui est affiché.
 *
 * Sert au recadrage automatique de [CoverageProbe] : un fond national activé alors que la carte
 * regarde ailleurs ne montrerait rien.
 */
object CoverageBounds {
    private val BOUNDS = mapOf(
        "ign_fr" to Bbox(-5.2, 41.3, 9.6, 51.1),      // France métropolitaine, Corse comprise
        "ign_es" to Bbox(-18.2, 27.6, 4.4, 43.8),     // Espagne, Canaries comprises
        "hu" to Bbox(16.1, 45.7, 22.9, 48.6),
        "sk" to Bbox(16.8, 47.7, 22.6, 49.7),
        "at" to Bbox(9.5, 46.3, 17.2, 49.1),
        "no" to Bbox(4.0, 57.8, 31.6, 71.3),
        "be" to Bbox(2.5, 49.4, 6.4, 51.6),
        "se" to Bbox(10.9, 55.2, 24.2, 69.1),
        "hr" to Bbox(13.4, 42.3, 19.5, 46.6),
        "ch" to Bbox(5.9, 45.8, 10.5, 47.9),
        "de" to Bbox(5.8, 47.2, 15.1, 55.1),
        "fi" to Bbox(19.0, 59.7, 31.6, 70.1),
        "si" to Bbox(13.3, 45.4, 16.7, 46.9),
        "cz" to Bbox(12.0, 48.5, 18.9, 51.1),
        "gb" to Bbox(-8.7, 49.8, 1.8, 61.0),          // Grande-Bretagne : OS ne couvre pas l'Irlande du Nord
        "pl" to Bbox(14.1, 49.0, 24.2, 54.9),
        // Bornes déclarées par le GetCapabilities du service, arrondies vers l'extérieur. Continent
        // seulement : Madère et les Açores y renvoient une image blanche.
        "pt" to Bbox(-9.7, 36.9, -6.0, 42.2),
    )

    /** Emprise du fond, ou null s'il n'en a pas de connue (tout fond hors groupe "Pays", ou ajouté
     *  par l'utilisateur : on ne recadre alors jamais, faute de savoir où l'envoyer). */
    fun of(providerId: String): Bbox? = BOUNDS[providerId]

    fun of(provider: ProviderEntity): Bbox? =
        if (provider.groupName == "Pays") BOUNDS[provider.id] else null

    /** Les deux emprises se chevauchent-elles ? Bornes incluses : deux zones qui se touchent par un
     *  bord partagent une bande de carte, aussi fine soit-elle. */
    fun intersects(a: Bbox, b: Bbox): Boolean =
        a.west <= b.east && b.west <= a.east && a.south <= b.north && b.south <= a.north

    /** Identifiants couverts, pour les tests d'intégrité du catalogue. */
    fun ids(): Set<String> = BOUNDS.keys
}
