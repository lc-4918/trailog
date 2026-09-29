package fr.lc4918.trailog.domain.model

import kotlinx.serialization.Serializable

/**
 * Un morceau d'itineraire et les attributs OSM de la voie qu'il emprunte, tels que BRouter les rend :
 * `highway=track surface=gravel tracktype=grade2`.
 *
 * Le texte brut, et non deja trie en revetement et type de voie : c'est lui qu'on garde avec le parcours
 * (cf. PlannerStore), et un classement retouche s'appliquera ainsi aux parcours deja enregistres.
 *
 * Pas de coordonnees : les morceaux se suivent dans l'ordre du trajet, et leurs longueurs cumulees disent
 * ou chacun commence et finit (cf. RouteDetails.ranges).
 */
@Serializable
data class WaySegment(val meters: Double, val tags: String)

/**
 * Le revetement d'une voie, par familles qu'on distingue a l'oeil : les valeurs de `surface` sont une
 * trentaine, dont beaucoup ne different que pour un cartographe.
 *
 * [paved] : revetu (vrai), non revetu (faux), ou inconnu (nul) - la voie ne dit pas son revetement.
 */
enum class SurfaceKind(val paved: Boolean?) {
    ASPHALT(true),
    PAVED(true),
    CONCRETE(true),
    SETT(true),
    WOOD_METAL(true),
    COMPACTED(false),
    FINE_GRAVEL(false),
    GRAVEL(false),
    UNPAVED(false),
    GROUND(false),
    GRASS(false),
    SAND(false),
    ROCK(false),
    UNKNOWN(null),
}

/** Le type d'une voie, d'apres `highway` - et `route=ferry` pour un bac, qui n'a pas de `highway`. */
enum class WayKind {
    MAIN_ROAD,
    ROAD,
    MINOR_ROAD,
    STREET,
    PEDESTRIAN,
    SERVICE,
    CYCLEWAY,
    TRACK,
    PATH,
    FOOTWAY,
    STEPS,
    FERRY,
    OTHER,
}

/**
 * Les voies d'une couche de la bibliotheque, retrouvees par recalage (cf. TraceMatch) : un jeu de morceaux
 * par LIGNE de la couche, dans l'ordre des lignes, pour que la mise en evidence d'une categorie tombe sur
 * la bonne. Garde a cote de la geometrie, et jete avec elle quand elle change.
 */
@Serializable
data class LayerWays(
    val lines: List<List<WaySegment>>,
    /** Longueur de trace posee sur aucune voie : hors sentier, ou voie absente d'OSM. */
    val offNetworkMeters: Double = 0.0,
    /** Faux quand Overpass n'a pas repondu : les attributs sont ceux de Valhalla, plus grossiers. */
    val osmTags: Boolean = true,
) {
    /** Tous les morceaux, lignes mises bout a bout : ce que comptent les parts. */
    val all: List<WaySegment> get() = lines.flatten()
}
