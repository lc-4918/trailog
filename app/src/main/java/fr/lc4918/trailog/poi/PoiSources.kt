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
 * - **en France**, DATAtourisme garde le tourisme, qu'il décrit mieux et illustre de photos, et OSM ne
 *   complète que le groupe *pratique* - l'eau, les toilettes, les bornes, les réparateurs. C'est
 *   exactement ce qu'on cherche à dix-huit heures dans une vallée, et ce que la base touristique ignore.
 *
 * **Un groupe limité au thème vélo reste à DATAtourisme**, où qu'on soit : OSM ne porte pas l'équivalent
 * de ce thème, et rendre des hébergements quelconques sous un filtre "vélo" serait promettre ce qu'on ne
 * sait pas. Une catégorie vide dit la vérité ; un marqueur qui ment ne se rattrape pas.
 */
object PoiSources {

    /**
     * Les emprises que DATAtourisme couvre : la France métropolitaine et les départements d'outre-mer,
     * qui publient dans la même base.
     *
     * Des rectangles larges, et volontairement : la question posée est "cette source a-t-elle une chance
     * de répondre ici", pas "sommes-nous en France". Un rectangle trop juste priverait de sa source une
     * carte cadrée sur une frontière, alors qu'un rectangle trop large ne coûte qu'une requête qui rendra
     * zéro lieu - et OSM aura de toute façon repondu à côté.
     */
    private val COUVERTURE = listOf(
        Bbox(west = -5.5, south = 41.2, east = 9.8, north = 51.3),      // métropole et Corse
        Bbox(west = -61.9, south = 15.7, east = -60.7, north = 16.6),   // Guadeloupe
        Bbox(west = -61.3, south = 14.3, east = -60.7, north = 14.9),   // Martinique
        Bbox(west = -54.7, south = 2.0, east = -51.5, north = 5.9),     // Guyane
        Bbox(west = 55.1, south = -21.5, east = 55.9, north = -20.8),   // La Réunion
        Bbox(west = 45.0, south = -13.1, east = 45.4, north = -12.6),   // Mayotte
    )

    /** Deux emprises se touchent-elles. Sans franchissement de l'antiméridien, comme partout ailleurs. */
    private fun croise(a: Bbox, b: Bbox): Boolean =
        a.west <= b.east && a.east >= b.west && a.south <= b.north && a.north >= b.south

    /** L'emprise visible est-elle, même en partie, dans ce que DATAtourisme couvre. */
    fun datatourismeCovers(box: Bbox): Boolean = COUVERTURE.any { croise(it, box) }

    /**
     * Les catégories à demander à OpenStreetMap pour cette emprise.
     *
     * [libres] est le jeu des catégories cochées **hors** thème vélo : celles limitées au vélo ne passent
     * jamais par ici (cf. la note de tête).
     */
    fun osmCategories(box: Bbox, libres: Set<PoiCategory>): Set<PoiCategory> =
        if (datatourismeCovers(box)) libres.filterTo(mutableSetOf()) { it.group == PoiGroup.PRACTICAL }
        else libres

    /**
     * Les catégories à demander à OpenStreetMap, **découpées par groupe** : une requête par groupe, plutôt
     * qu'une seule qui les porte toutes.
     *
     * **Pourquoi découper**, mesuré sur un écran de carte à Berlin, la requête unique servant d'étalon :
     *
     * | Requête | Temps |
     * |---|---|
     * | toutes catégories (avant) | 30 s, et rien à l'écran avant la fin |
     * | hébergements | 3,1 s |
     * | restauration | 2,9 s |
     * | loisirs | 15,9 s |
     * | pratique | 23,3 s |
     *
     * Ce ne sont pas les 30 secondes qui gênaient, c'est de n'avoir **rien** pendant 30 secondes. Découpé,
     * l'écran se peuple au bout de trois : les hôtels et les restaurants d'abord, les musées ensuite, les
     * services en dernier - **chaque groupe s'affiche dès qu'il répond** (cf. `poiStream`).
     *
     * Les groupes vides ne comptent pas : rien de coché, rien à demander.
     *
     * En France, ce découpage ne change rien - seul le groupe pratique y est demandé à OSM, et il ne fait
     * qu'une requête.
     */
    fun osmGroups(box: Bbox, libres: Set<PoiCategory>): List<Set<PoiCategory>> =
        osmCategories(box, libres)
            .groupBy { it.group }
            .toSortedMap(compareBy { it.ordinal })
            .values.map { it.toSet() }

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
        val gardes = osm.filter { candidat ->
            datatourisme.none { connu ->
                connu.category == candidat.category &&
                    TrackMath.haversine(connu.lon, connu.lat, candidat.lon, candidat.lat) < DOUBLON_M
            }
        }
        return datatourisme + gardes
    }
}

/**
 * Le même objet rendu par deux requêtes de groupes différents : lequel garder.
 *
 * Le cas naît du découpage (cf. [PoiSources.osmGroups]) : un hôtel-restaurant d'OpenStreetMap répond à la
 * requête des hébergements ET à celle de la restauration, sous deux catégories différentes, avec le même
 * identifiant. Deux marqueurs se poseraient alors l'un sur l'autre, et un tap ne saurait plus lequel ouvrir.
 *
 * On tranche par l'ordre de résolution des catégories, celui-là même qui tranche quand un seul appel rend un
 * lieu à plusieurs classes : le résultat ne dépend donc pas de l'ordre d'arrivée des réponses, qui, lui, ne
 * se reproduit jamais deux fois pareil.
 */
internal fun mieuxClasse(a: Poi, b: Poi): Poi =
    if (PoiCategory.resolutionRank(a.category) <= PoiCategory.resolutionRank(b.category)) a else b
