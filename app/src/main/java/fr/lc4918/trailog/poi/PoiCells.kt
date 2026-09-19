package fr.lc4918.trailog.poi

import fr.lc4918.trailog.domain.model.PoiGroup
import fr.lc4918.trailog.map.offline.Bbox
import kotlin.math.cos
import kotlin.math.floor

/**
 * Une cellule de la grille des points d'interet : un carre de [PoiCells.SIZE_DEG] degres de cote, fixe
 * sur la Terre et non sur l'ecran.
 *
 * **C'est l'unite de tout** - de la requete, du cache et de l'affichage. Une emprise d'ecran change a
 * chaque geste, et rien de ce qu'on demandait pour l'une ne se retrouvait pour la suivante : un pas de
 * cote relancait tout, une reponse tronquee rendait un sous-ensemble que rien ne fixait, et les lieux
 * paraissaient et disparaissaient d'un geste a l'autre. Une cellule, elle, est demandee UNE fois, gardee,
 * et rendue a l'identique chaque fois qu'on y repasse. C'est le principe de la collecte de Trailmap, qui
 * ne rencontre aucun de ces defauts : de petites emprises fixes, jamais tronquees.
 */
data class PoiCell(val ix: Int, val iy: Int) {
    /** Cle stable, ecrite en base (cf. `PoiCacheEntity.cell`). */
    val key: String get() = "${ix}_$iy"

    val bbox: Bbox get() = Bbox(
        west = ix / PoiCells.PER_DEG, south = iy / PoiCells.PER_DEG,
        east = (ix + 1) / PoiCells.PER_DEG, north = (iy + 1) / PoiCells.PER_DEG,
    )

    val centerLon: Double get() = (ix + 0.5) / PoiCells.PER_DEG
    val centerLat: Double get() = (iy + 0.5) / PoiCells.PER_DEG
}

/**
 * Ce qu'une requete demande : une cellule, une source, et les groupes qu'elle y sert.
 *
 * Les groupes et non les categories cochees : la reponse porte TOUTES les categories du groupe, si bien
 * que cocher ou decocher une categorie ne coute plus aucune requete - le filtre s'applique a ce qu'on a.
 * Un groupe est l'unite de ce qui se garde (cf. [PoiUnit]) ; plusieurs groupes d'une meme source partent
 * pourtant dans une seule requete, qui en couvre plusieurs d'un coup.
 */
data class PoiJob(val cell: PoiCell, val source: PoiSource, val groups: Set<PoiGroup>)

/** L'unite de ce qui se garde : une cellule, un groupe, une source. */
data class PoiUnit(val cell: PoiCell, val group: PoiGroup, val source: PoiSource) {
    val key: String get() = "${cell.key}/${group.key}/${source.key}"
}

object PoiCells {
    /**
     * Cellules par degre : 10, soit des carres de 0,1 degre - onze kilometres de haut, huit de large a
     * la latitude de Toulouse.
     *
     * Mesure sur `overpass.openstreetmap.fr`, une emprise de cet ordre au centre de Toulouse, de Berlin
     * et de Madrid : de 1,7 a 2,8 s pour la restauration, de 4 a 7 s pour le groupe pratique, le plus
     * lourd. C'est le pire cas - une cellule de campagne repond en moins d'une seconde. Plus grande, une
     * cellule de centre-ville deborderait le delai ; plus petite, un ecran de campagne demanderait des
     * dizaines de requetes pour quelques fontaines.
     */
    const val PER_DEG = 10.0
    const val SIZE_DEG = 1.0 / PER_DEG

    /** La cellule qui contient ce point. Le plancher et non la troncature : a l'ouest de Greenwich, les
     *  longitudes sont negatives, et tronquer rangerait deux cellules sous le meme indice. */
    fun of(lon: Double, lat: Double): PoiCell =
        PoiCell(floor(lon * PER_DEG).toInt(), floor(lat * PER_DEG).toInt())

    /** Les cellules que l'emprise touche, meme en partie. */
    fun covering(box: Bbox): List<PoiCell> {
        val sw = of(box.west, box.south)
        val ne = of(box.east, box.north)
        return buildList {
            for (iy in sw.iy..ne.iy) for (ix in sw.ix..ne.ix) add(PoiCell(ix, iy))
        }
    }

    /**
     * Les cellules triees de la plus proche a la plus eloignee de ([lon], [lat]) - le centre de l'ecran.
     *
     * C'est l'ordre des requetes : ce qu'on regarde se remplit d'abord, les bords ensuite. La distance
     * tient compte du resserrement des meridiens, sans quoi les cellules d'est en ouest passeraient
     * derriere celles du nord et du sud.
     */
    fun byDistance(cells: List<PoiCell>, lon: Double, lat: Double): List<PoiCell> {
        val kx = cos(Math.toRadians(lat))
        return cells.sortedBy { c ->
            val dx = (c.centerLon - lon) * kx
            val dy = c.centerLat - lat
            dx * dx + dy * dy
        }
    }

    /**
     * Les unites a charger pour ces cellules et ces groupes, dans l'ordre des cellules : chaque groupe
     * aupres de la source qui le sert sur cette cellule.
     *
     * La source se choisit PAR CELLULE (cf. [PoiSources.sources]) : une cellule a cheval sur la frontiere
     * en interroge deux, le reste de l'Espagne n'en interroge qu'une.
     */
    fun units(cells: List<PoiCell>, groups: Set<PoiGroup>, complement: Boolean): List<PoiUnit> =
        cells.flatMap { cell ->
            groups.sortedBy { it.ordinal }.flatMap { g ->
                PoiSources.sources(cell.bbox, g, complement).map { PoiUnit(cell, g, it) }
            }
        }

    /**
     * Les requetes qui couvrent ces unites : une par cellule et par source, qui porte tous les groupes que
     * cette source y sert. L'ordre est celui de la premiere unite de chaque requete - celui des cellules,
     * de la plus proche a la plus lointaine.
     */
    fun jobs(units: List<PoiUnit>): List<PoiJob> =
        units.groupBy { it.cell to it.source }
            .map { (cle, us) -> PoiJob(cle.first, cle.second, us.mapTo(LinkedHashSet()) { it.group }) }
}
