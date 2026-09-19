package fr.lc4918.trailog.poi

import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiGroup
import fr.lc4918.trailog.map.offline.Bbox

/**
 * Qui répond, et pour quelles catégories : la règle de partage entre les deux sources de points d'intérêt.
 *
 * Elle vit à part et sans Android parce que c'est **elle** qui décide du nombre de requêtes envoyées, à
 * qui, et de ce que la carte montre à un endroit donné - trois choses qu'on ne veut pas voir changer sans
 * qu'un test le dise.
 *
 * **La règle, et ce qui la fonde** (mesures relevées sur un écran de carte autour de Grenoble) :
 *
 * | Catégories | DATAtourisme | OpenStreetMap |
 * |---|---|---|
 * | hôtels | 49 | 38 |
 * | campings | 4 | - |
 * | points d'eau, toilettes, pique-nique | 0 | 200 |
 * | bornes de recharge | 0 | 165 |
 * | loueurs et réparateurs de vélos | 4 | 50 |
 *
 * D'où :
 * - **hors de France**, DATAtourisme n'a rien à dire du tout : OSM répond seul, pour tout ;
 * - **en France**, DATAtourisme garde l'hébergement et les loisirs, qu'il décrit mieux et illustre de
 *   photos, et OSM complète le groupe *pratique* - l'eau, les toilettes, les bornes, les réparateurs.
 *   C'est exactement ce qu'on cherche à dix-huit heures dans une vallée, et ce que la base touristique
 *   ignore.
 *
 * **La restauration est passée à OSM elle aussi**, et c'est une correction. Relevé sur le centre d'Albi,
 * même emprise pour les deux sources :
 *
 * | | DATAtourisme | OpenStreetMap |
 * |---|---|---|
 * | restaurants | **6** | **150** |
 * | bars, cafés, pubs | 1 | 23 |
 *
 * Et les six de DATAtourisme sont **tous des hôtels** : ils portent `Restaurant` en plus de `Hotel`, et
 * sont les seuls "restaurants" que la base connaisse. Un restaurant de quartier n'est pas un objet
 * touristique - il n'entre dans cette base que s'il est adossé à un hébergement.
 *
 * La couverture varie d'ailleurs énormément d'une région à l'autre, ce qui interdit de s'y fier : pour la
 * même question, DATAtourisme rend 10 lieux de restauration à Albi, 46 à Grenoble, 29 à Nantes, 185 à
 * Strasbourg et 729 à Marseille. Ce ne sont pas des villes de tailles si différentes ; ce sont des comités
 * régionaux qui ne publient pas les mêmes choses.
 *
 * L'hébergement et les loisirs, eux, restent à DATAtourisme, et le contenu le justifie autant que le
 * nombre : sur le centre d'Albi il rend les quatorze hôtels de la ville, nommés, et cinquante-deux lieux
 * de loisirs dont les circuits de découverte, les bouclettes de randonnée urbaine, le petit train
 * touristique et les marchés de producteurs - autant d'objets qui n'existent tout simplement pas dans
 * OpenStreetMap.
 *
 * **Le complément se coupe.** La requête Overpass est longue, et qui n'en veut pas doit pouvoir s'en
 * passer : le réglage "Compléter avec OpenStreetMap" rend alors la France à DATAtourisme seul. Il ne
 * touche que le complément - hors de France, OpenStreetMap répond quoi qu'il arrive, faute de quoi la
 * couche serait vide sans explication.
 */
object PoiSources {

    /**
     * Le contour de la France metropolitaine continentale, en (lon, lat), a une dizaine de kilometres
     * pres. Du cote de la mer il passe au large : il n'y a rien a y trouver, et la cote se suit mal.
     *
     * **Un contour et non plus un rectangle, et c'est une correction.** Le rectangle d'avant (41,2 a 51,3 N,
     * -5,5 a 9,8 E) englobait le nord de l'Espagne - Logrono, Pampelune, Barcelone -, la Belgique, le
     * Luxembourg, la Suisse romande et le Piemont. L'hebergement et les loisirs y etaient confies a
     * DATAtourisme seul, qui n'en connait rien : ces deux groupes restaient vides a Logrono sans que rien
     * ne l'explique.
     */
    private val METROPOLE = listOf(
        2.55 to 51.09, 2.60 to 50.82, 3.10 to 50.78, 3.28 to 50.52, 3.67 to 50.35, 4.02 to 50.35,
        4.15 to 49.98, 4.45 to 49.94, 4.68 to 49.99, 4.80 to 50.17, 4.90 to 50.15, 4.87 to 49.80,
        5.47 to 49.50, 5.82 to 49.54, 6.37 to 49.46, 6.72 to 49.16, 7.05 to 49.11, 7.44 to 49.17,
        7.94 to 49.06, 8.23 to 48.97, 7.80 to 48.50, 7.58 to 48.12, 7.59 to 47.58, 7.40 to 47.43,
        6.94 to 47.30, 6.45 to 46.97, 6.11 to 46.59, 6.08 to 46.25, 5.97 to 46.14, 6.30 to 46.25,
        6.25 to 46.44, 6.79 to 46.44, 6.80 to 46.13, 7.04 to 45.93, 6.80 to 45.72, 7.15 to 45.40,
        6.64 to 45.12, 7.07 to 44.85, 6.86 to 44.53, 6.97 to 44.24, 7.67 to 44.15, 7.53 to 43.79,
        7.80 to 43.40, 6.60 to 42.80, 4.50 to 43.10, 3.40 to 42.40, 3.17 to 42.43, 2.67 to 42.34,
        1.97 to 42.37, 1.73 to 42.50, 1.44 to 42.60, 0.95 to 42.80, 0.72 to 42.86, 0.66 to 42.70,
        0.40 to 42.69, 0.00 to 42.69, -0.74 to 42.91, -1.44 to 43.05, -1.47 to 43.27, -1.79 to 43.37,
        -2.20 to 43.60, -1.70 to 46.00, -3.00 to 47.10, -5.40 to 48.00, -5.40 to 48.70, -3.60 to 48.95,
        -2.30 to 48.72, -1.66 to 48.75, -1.72 to 49.30, -2.05 to 49.75, -1.90 to 49.90, 0.00 to 49.75,
        1.40 to 50.40, 1.50 to 51.10,
    )

    /** La Corse, et les departements d'outre-mer : des iles, qu'un rectangle decrit sans rien deborder. */
    private val ILES = listOf(
        Bbox(west = 8.4, south = 41.33, east = 9.7, north = 43.1),      // Corse
        Bbox(west = -61.9, south = 15.7, east = -60.7, north = 16.6),   // Guadeloupe
        Bbox(west = -61.3, south = 14.3, east = -60.7, north = 14.9),   // Martinique
        Bbox(west = -54.7, south = 2.0, east = -51.5, north = 5.9),     // Guyane
        Bbox(west = 55.1, south = -21.5, east = 55.9, north = -20.8),   // La Réunion
        Bbox(west = 45.0, south = -13.1, east = 45.4, north = -12.6),   // Mayotte
    )

    /** Ce point est-il dans ce que DATAtourisme couvre. */
    fun datatourismeCovers(lon: Double, lat: Double): Boolean =
        ILES.any { lon >= it.west && lon <= it.east && lat >= it.south && lat <= it.north } ||
            dansPolygone(lon, lat, METROPOLE)

    /** Test du rayon : le nombre de bords que croise une demi-droite partie du point vers l'est. */
    private fun dansPolygone(lon: Double, lat: Double, poly: List<Pair<Double, Double>>): Boolean {
        var dedans = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val (xi, yi) = poly[i]
            val (xj, yj) = poly[j]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) dedans = !dedans
            j = i
        }
        return dedans
    }

    /**
     * Ce que DATAtourisme couvre de l'emprise : toute, rien, ou une partie.
     *
     * Lu sur neuf points - les coins, les milieux des bords et le centre -, ce qui suffit a l'echelle
     * d'une cellule de la grille (cf. [PoiCells]) devant un contour precis a dix kilometres.
     */
    fun coverage(box: Bbox): Coverage {
        val lons = listOf(box.west, (box.west + box.east) / 2, box.east)
        val lats = listOf(box.south, (box.south + box.north) / 2, box.north)
        val dedans = lons.sumOf { lon -> lats.count { lat -> datatourismeCovers(lon, lat) } }
        return when (dedans) {
            0 -> Coverage.NONE
            9 -> Coverage.FULL
            else -> Coverage.PARTIAL
        }
    }

    enum class Coverage { NONE, PARTIAL, FULL }

    /**
     * Les groupes qu'OpenStreetMap sert **même là où DATAtourisme répond** (cf. la note de tête).
     *
     * Le pratique parce que la base touristique l'ignore ; la restauration parce qu'elle n'y connaît que
     * les hôtels qui servent à manger.
     */
    private val COMPLETES_PAR_OSM = setOf(PoiGroup.PRACTICAL, PoiGroup.FOOD)

    /**
     * Les sources qui servent le groupe [group] sur l'emprise [box] - une cellule de la grille.
     *
     * La règle de partage s'y lit entière :
     * - hors de ce que DATAtourisme couvre, OpenStreetMap, pour tout ;
     * - en France, OpenStreetMap pour la restauration et le pratique, DATAtourisme pour le reste ;
     * - le complément coupé (le réglage "Compléter avec OpenStreetMap"), DATAtourisme pour tout en
     *   France. Six restaurants valent mieux que zéro, même si ce sont six hôtels. Hors de France, le
     *   réglage est ignoré : OpenStreetMap y est la seule source, et l'écouter viderait la couche sans que
     *   rien sur la carte ne l'explique ;
     * - **à cheval sur la frontière, les deux règles à la fois** : la part française de la cellule a
     *   besoin de DATAtourisme, l'autre d'OpenStreetMap. Les doublons se fondent à l'affichage
     *   (cf. [merge]).
     */
    fun sources(box: Bbox, group: PoiGroup, complement: Boolean = true): Set<PoiSource> {
        val enFrance = if (!complement) PoiSource.DATATOURISME
            else if (group in COMPLETES_PAR_OSM) PoiSource.OSM else PoiSource.DATATOURISME
        return when (coverage(box)) {
            Coverage.NONE -> setOf(PoiSource.OSM)
            Coverage.FULL -> setOf(enFrance)
            Coverage.PARTIAL -> setOf(enFrance, PoiSource.OSM)
        }
    }

    /**
     * Les catégories à demander à [source] pour ces groupes : TOUTES celles du groupe que la source sait
     * décrire, cochées ou non.
     *
     * Toutes, et non les seules cochées : ce que la réponse porte se garde par cellule et par groupe, et
     * le filtre s'applique ensuite à ce qu'on a. Cocher une catégorie de plus ne coûte donc plus aucune
     * requête, et la décocher puis la recocher ne redemande rien.
     */
    fun categories(source: PoiSource, groups: Set<PoiGroup>): Set<PoiCategory> =
        PoiCategory.entries.filterTo(mutableSetOf()) { c ->
            c.group in groups && when (source) {
                PoiSource.DATATOURISME -> c.classes.isNotEmpty()
                PoiSource.OSM -> c.osm.isNotEmpty()
            }
        }

    /**
     * Distance en deçà de laquelle deux lieux de même catégorie sont tenus pour le même endroit.
     *
     * Cinquante mètres : la fontaine que les deux bases connaissent n'est jamais pointée au même mètre, et
     * deux marqueurs superposés à cette échelle se recouvrent sans qu'on puisse ouvrir celui du dessous.
     */
    const val DOUBLON_M = 50.0

    /**
     * Réunit les deux réponses en écartant les doublons.
     *
     * DATAtourisme l'emporte, et pour une raison précise : c'est lui qui porte la photo, la ville et le
     * site du lieu, quand OSM n'a souvent qu'un nom. À description égale, on garde la plus riche.
     *
     * Comparaison par catégorie **et** par distance : deux lieux distincts se tiennent couramment au même
     * carrefour - un café et une boulangerie -, et les confondre effacerait l'un des deux.
     */
    fun merge(datatourisme: List<Poi>, osm: List<Poi>): List<Poi> {
        if (datatourisme.isEmpty() || osm.isEmpty()) return datatourisme + osm
        // Par categorie : seules deux fiches de meme categorie peuvent designer le meme lieu, et la regle
        // de partage fait que les deux sources ne servent presque jamais les memes. Sans ce classement, le
        // cout croissait avec le produit des deux listes - des millions de distances sur une ville dense.
        val connus = datatourisme.groupBy { it.category }
        val gardes = osm.filter { candidat ->
            connus[candidat.category].orEmpty().none { connu ->
                TrackMath.haversine(connu.lon, connu.lat, candidat.lon, candidat.lat) < DOUBLON_M
            }
        }
        return datatourisme + gardes
    }
}

/**
 * Les deux sources de points d'intérêt. [key] s'écrit en base (cf. `PoiCellEntity`), et le préfixe `osm:`
 * des identifiants distingue leurs fiches une fois réunies (cf. `Overpass.parse`).
 */
enum class PoiSource(val key: String) {
    DATATOURISME("dt"),
    OSM("osm");

    companion object {
        fun ofUuid(uuid: String): PoiSource = if (uuid.startsWith("osm:")) OSM else DATATOURISME
    }
}
