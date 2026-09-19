package fr.lc4918.trailog.poi

import fr.lc4918.trailog.data.db.PoiCacheEntity
import fr.lc4918.trailog.data.db.PoiCellEntity
import fr.lc4918.trailog.data.db.PoiDao
import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiGroup
import fr.lc4918.trailog.map.offline.Bbox
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Les points d'intérêt, cellule par cellule (cf. [PoiCells]) : le cache d'abord, le service ensuite.
 *
 * **Le cache fait foi tant qu'il est frais**, et c'est l'inverse de la règle d'avant. On interrogeait le
 * service à chaque geste, pour une emprise d'écran qu'on ne retrouvait jamais deux fois ; le cache ne
 * servait que sans réseau. Une cellule, elle, se retrouve : chargée une fois, elle se relit en quelques
 * millisecondes pendant une semaine, et le service n'est plus sollicité que pour ce qu'on n'a jamais vu.
 *
 * Le cache reste aussi ce qui fait vivre la couche **sans réseau**, sur le bord d'un chemin : une cellule
 * périmée se montre quand même, en attendant qu'une requête la remplace (cf. [PoiLookup.stale]).
 */
class PoiRepository(private val dao: PoiDao) {

    /**
     * Ce que le cache sait de ces unités.
     *
     * Une seule lecture pour toutes les cellules : un écran en touche une dizaine, et les demander une à une
     * multiplierait les allers-retours à la base.
     */
    suspend fun lookup(units: List<PoiUnit>, now: Long = System.currentTimeMillis()): PoiLookup {
        if (units.isEmpty()) return PoiLookup(emptyMap(), emptyMap())
        val cles = units.map { it.cell.key }.distinct()
        val marques = runCatching { dao.cellMarks(cles) }.getOrDefault(emptyList())
            .associateBy { Triple(it.cell, it.groupKey, it.source) }
        val parUnite = runCatching { dao.inCells(cles) }.getOrDefault(emptyList())
            .mapNotNull { e -> e.toPoi()?.let { p -> Triple(e.cell, p.category.group.key, PoiSource.ofUuid(p.uuid).key) to p } }
            .groupBy({ it.first }, { it.second })
        val frais = HashMap<PoiUnit, List<Poi>>()
        val perimes = HashMap<PoiUnit, List<Poi>>()
        for (u in units) {
            val cle = Triple(u.cell.key, u.group.key, u.source.key)
            val lieux = parUnite[cle].orEmpty()
            val m = marques[cle]
            if (m != null && (m.pinned || m.fetchedAt >= now - TTL_MS)) frais[u] = lieux else perimes[u] = lieux
        }
        return PoiLookup(frais, perimes)
    }

    /**
     * Demande une cellule au service, et garde la réponse.
     *
     * Un échec n'écrit **rien** : une cellule vide se lirait comme une zone sans le moindre lieu, et le
     * mensonge survivrait une semaine dans le cache.
     */
    suspend fun fetch(job: PoiJob, osmBase: String, pinned: Boolean = false): PoiFetch {
        val categories = PoiSources.categories(job.source, job.groups)
        val box = job.cell.bbox
        val (lieux, echec) = when (job.source) {
            PoiSource.OSM -> Overpass.fetch(osmBase, box, categories).let { it.pois to it.failed }
            PoiSource.DATATOURISME -> datatourisme(box, categories).let { it.pois to it.echec }
        }
        if (echec) return PoiFetch(emptyMap(), failed = true)
        // Un batiment a cheval sur deux cellules est rendu par les deux requetes : il n'appartient qu'a
        // celle ou tombe son centre, et c'est elle seule qui le garde.
        val siens = lieux.filter { PoiCells.of(it.lon, it.lat) == job.cell }.distinctBy { it.uuid }
        val parGroupe = job.groups.associateWith { g -> siens.filter { it.category.group == g } }
        garder(job, parGroupe, pinned)
        return PoiFetch(parGroupe, failed = false)
    }

    private suspend fun garder(job: PoiJob, parGroupe: Map<PoiGroup, List<Poi>>, pinned: Boolean) {
        val now = System.currentTimeMillis()
        runCatching {
            parGroupe.forEach { (g, lieux) ->
                dao.deleteInCell(
                    job.cell.key, PoiCategory.of(g).map { it.key }, osm = job.source == PoiSource.OSM,
                )
                if (lieux.isNotEmpty()) dao.upsertAll(lieux.map { it.toEntity(now, job.cell, pinned) })
            }
            // La marque en DERNIER : une ecriture interrompue avant elle laisse la cellule a redemander,
            // jamais une cellule marquee chargee et a moitie vide.
            dao.markCells(parGroupe.keys.map { g ->
                PoiCellEntity(job.cell.key, g.key, job.source.key, now, pinned)
            })
            dao.deleteOlderThan(now - TTL_MS)
            dao.deleteCellsOlderThan(now - TTL_MS)
        }
    }

    /**
     * DATAtourisme sur une emprise, **découpée tant qu'elle déborde** : le service ne rend que
     * [Datatourisme.PAGE_SIZE] lieux par requête, et en connaît parfois plus sur une cellule de
     * centre-ville. Deux niveaux au plus - une cellule de 0,1 degré coupée en seize.
     */
    private suspend fun datatourisme(box: Bbox, categories: Set<PoiCategory>, depth: Int = 0): Datatourisme.Catalog {
        val r = Datatourisme.catalog(
            Datatourisme.DEFAULT_URL, box.north, box.west, box.south, box.east, categories,
        )
        if (r.echec || !r.tronque || depth >= DT_MAX_DEPTH) return r
        val parts = quadrants(box).map { datatourisme(it, categories, depth + 1) }
        return Datatourisme.Catalog(
            parts.flatMap { it.pois }.distinctBy { it.uuid },
            tronque = parts.any { it.tronque },
            echec = parts.any { it.echec },
        )
    }

    /**
     * **Emporte** les points d'intérêt d'une zone, pour les avoir sans réseau.
     *
     * Le cache ordinaire ne retient que ce qu'on a survolé **connecté** : sur le terrain, la couche est
     * donc vide précisément là où l'on n'est jamais allé avec du signal. Une zone téléchargée pour partir
     * emporte ses lieux comme elle emporte ses tuiles, cellule par cellule, marquées pour échapper au
     * ménage.
     *
     * Rend le nombre de lieux emportés, **ou null si aucune cellule n'a répondu** : une zone sans le
     * moindre café et un service muet ne s'annoncent pas du même mot.
     */
    suspend fun pinArea(
        box: Bbox, groups: Set<PoiGroup>, osmBase: String, complement: Boolean = true,
    ): Int? {
        if (groups.isEmpty()) return 0
        val cellules = PoiCells.covering(box).take(PIN_MAX_CELLS)
        val jobs = PoiCells.jobs(PoiCells.units(cellules, groups, complement))
        val creneaux = PoiSource.entries.associateWith { Semaphore(PARALLEL_PER_SOURCE) }
        val resultats = coroutineScope {
            jobs.map { job ->
                async { creneaux.getValue(job.source).withPermit { fetch(job, osmBase, pinned = true) } }
            }.awaitAll()
        }
        if (resultats.all { it.failed }) return null
        return resultats.sumOf { r -> r.pois.values.sumOf { it.size } }
    }

    companion object {
        /**
         * Durée de vie du cache : une semaine.
         *
         * Les lieux changent peu - un camping n'ouvre pas et ne ferme pas dans le mois - et une donnée
         * d'une semaine vaut infiniment mieux qu'une carte vide sans réseau. Au-delà, la cellule se
         * redemande ; sans réseau, elle se montre quand même.
         */
        const val TTL_MS = 7L * 24 * 3600 * 1000

        /**
         * Requêtes simultanées par source.
         *
         * Deux : c'est ce que les instances publiques d'Overpass accordent par adresse, et au-delà les
         * requêtes repartent en refus plutôt que d'attendre leur tour. Les deux sources ont chacune les
         * leurs - ce sont deux services distincts, qui n'ont aucune raison de s'attendre.
         */
        const val PARALLEL_PER_SOURCE = 2

        /** Profondeur du découpage d'une cellule que DATAtourisme ne rend pas en une page. */
        private const val DT_MAX_DEPTH = 2

        /** Plafond des cellules emportées avec une zone hors ligne : une région entière, une centaine de
         *  kilomètres de côté. Au-delà, le téléchargement durerait des heures pour des lieux qu'on ne
         *  visitera pas. */
        const val PIN_MAX_CELLS = 400
    }
}

/**
 * Ce que le cache sait d'un jeu d'unités.
 *
 * [fresh] : chargées il y a moins d'une semaine, ou emportées - rien à redemander. [stale] : les autres,
 * avec ce que le cache en garde encore (parfois rien) - de quoi montrer quelque chose en attendant le
 * service, ou sans réseau.
 */
data class PoiLookup(val fresh: Map<PoiUnit, List<Poi>>, val stale: Map<PoiUnit, List<Poi>>)

/** Ce qu'une requête a rendu, groupe par groupe, ou le constat qu'elle a échoué. */
data class PoiFetch(val pois: Map<PoiGroup, List<Poi>>, val failed: Boolean)

/** Les quatre quadrants d'une emprise, dans l'ordre sud-ouest, sud-est, nord-ouest, nord-est. */
internal fun quadrants(box: Bbox): List<Bbox> {
    val midLon = (box.west + box.east) / 2
    val midLat = (box.south + box.north) / 2
    return listOf(
        Bbox(west = box.west, south = box.south, east = midLon, north = midLat),
        Bbox(west = midLon, south = box.south, east = box.east, north = midLat),
        Bbox(west = box.west, south = midLat, east = midLon, north = box.north),
        Bbox(west = midLon, south = midLat, east = box.east, north = box.north),
    )
}

private fun Poi.toEntity(now: Long, cell: PoiCell, pinned: Boolean) = PoiCacheEntity(
    uuid = uuid, label = label, lat = lat, lon = lon, categoryKey = category.key,
    city = city, imageUrl = imageUrl, webUrl = webUrl, bikeTheme = bikeTheme, fetchedAt = now,
    pinned = pinned, cell = cell.key,
)

private fun PoiCacheEntity.toPoi(): Poi? {
    val cat = PoiCategory.byKey(categoryKey) ?: return null
    return Poi(uuid, label, lat, lon, cat, city, imageUrl, webUrl, bikeTheme)
}
