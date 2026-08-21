package fr.lc4918.trailog.poi

import fr.lc4918.trailog.data.db.PoiCacheEntity
import fr.lc4918.trailog.data.db.PoiDao
import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.map.offline.Bbox
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Les points d'intérêt d'une zone : les services quand ils répondent, le cache sinon.
 *
 * **Deux sources et non une** depuis qu'OpenStreetMap complète DATAtourisme - le monde hors de France, et
 * en France les services du terrain que la base touristique ignore. Le partage est décrit et justifié
 * dans [PoiSources], que ce dépôt ne fait qu'appliquer.
 *
 * L'ordre compte et il est délibéré : on **interroge le service d'abord**, et l'on ne se rabat sur le
 * cache que s'il ne rend rien. Le catalogue est vivant - un camping ferme, un loueur change d'adresse -
 * et rien ne sert de montrer d'abord ce qu'on savait hier quand on peut savoir aujourd'hui.
 *
 * Le cache, lui, n'est pas une optimisation : c'est ce qui permet à une couche allumée de montrer quelque
 * chose **sans réseau**, sur le bord d'un chemin. Une carte hors ligne est la promesse de l'application ;
 * une couche qui n'y rendrait rien serait une régression pour la seule fonction qu'on emporte justement
 * là où il n'y a pas de signal.
 */
class PoiRepository(private val dao: PoiDao) {

    /**
     * Charge la zone [box] pour les catégories demandées, **les deux sources en parallèle**.
     *
     * Rend un flux et non une liste, et c'est tout l'intérêt : chaque source publie **dès qu'elle répond**,
     * sans attendre l'autre. En France, DATAtourisme répond en une seconde quand OpenStreetMap en met
     * trente sur une ville dense - les faire attendre l'une l'autre, c'était trente secondes de carte nue
     * là où il y avait déjà tout à montrer.
     *
     * Chaque émission porte **tout ce qu'on sait à cet instant**, sources déjà arrivées comprises : l'écran
     * remplace sa liste au lieu de la compléter, et n'a donc rien à accumuler de son côté.
     *
     * La dernière émission dit aussi d'où vient ce qu'elle porte : l'écran doit pouvoir annoncer "ce sont
     * les derniers points connus" plutôt que de laisser croire à une réponse fraîche.
     */
    fun load(
        base: String, box: Bbox, libres: Set<PoiCategory>, velo: Set<PoiCategory>,
        osmBase: String = Overpass.DEFAULT_URL,
    ): Flow<PoiLoad> = poiStream(
        datatourisme = { datatourisme(base, box, libres, velo) },
        osm = osmSources(osmBase, box, libres),
        garder = { frais -> garder(frais) },
        cache = { duCache(box, libres, velo) },
    )

    /** Écrit au cache ce qu'une source vient de rendre, et fait le ménage des lignes périmées. */
    private suspend fun garder(frais: List<Poi>) {
        val now = System.currentTimeMillis()
        runCatching {
            dao.upsertAll(frais.map { it.toEntity(now) })
            dao.deleteOlderThan(now - TTL_MS)
        }
    }

    /**
     * Ce que le cache connaît de cette zone, filtré comme la demande.
     *
     * Lu seulement quand **aucune** source n'a rien rendu : soit la zone est vide, soit le réseau manque, et
     * s'il porte quelque chose ici c'est qu'on y est déjà venu - le montrer vaut mieux qu'une carte nue.
     */
    private suspend fun duCache(box: Bbox, libres: Set<PoiCategory>, velo: Set<PoiCategory>): List<Poi> {
        val gardes = runCatching { dao.inBounds(box.north, box.west, box.south, box.east) }
            .getOrDefault(emptyList())
        val retenues = libres + velo
        val groupesVelo = velo.map { it.group }
        return gardes.mapNotNull { it.toPoi() }
            .filter { it.category in retenues && (it.category.group !in groupesVelo || it.bikeTheme) }
    }

    /**
     * **Emporte** les points d'intérêt d'une zone, pour les avoir sans réseau.
     *
     * Le cache ordinaire ne retient que ce qu'on a survolé **connecté** : sur le terrain, la couche est
     * donc vide précisément là où l'on n'est jamais allé avec du signal - l'endroit où elle servirait le
     * plus. Une zone téléchargée pour partir doit emporter ses lieux comme elle emporte ses tuiles.
     *
     * Les lignes écrites ici sont marquées (`pinned`) : le ménage hebdomadaire les épargne, une zone
     * emportée pour quinze jours ne devant pas se vider au milieu du séjour.
     *
     * Rend le nombre de lieux emportés, **ou null si le service n'a pas répondu** : la distinction compte,
     * une zone sans le moindre café et un service muet ne s'annoncent pas du même mot. Un lieu déjà connu
     * du cache est réécrit marqué : le demander exprès vaut mieux que l'avoir croisé.
     */
    suspend fun pinArea(
        base: String, box: Bbox, libres: Set<PoiCategory>, velo: Set<PoiCategory>,
        osmBase: String = Overpass.DEFAULT_URL,
    ): Int? {
        if (libres.isEmpty() && velo.isEmpty()) return 0
        // En parallele, comme le chargement de la carte : on emporte une zone entiere, et les deux services
        // n'ont aucune raison de s'attendre.
        val frais = coroutineScope {
            val d = async { datatourisme(base, box, libres, velo) }
            val o = osmSources(osmBase, box, libres).map { source -> async { source() } }
            PoiSources.merge(d.await().pois, o.flatMap { it.await().pois })
        }
        // Aucun lieu ET rien en base sur cette zone : le service n'a probablement pas repondu. On ne peut
        // pas trancher a coup sur - une zone peut etre reellement vide - mais annoncer "0 lieu emporte"
        // apres une panne reseau ferait croire a un desert.
        if (frais.isEmpty()) return null
        val now = System.currentTimeMillis()
        return runCatching {
            dao.upsertAll(frais.map { it.toEntity(now, pinned = true) })
            frais.size
        }.getOrNull()
    }

    /**
     * DATAtourisme, en deux requetes au plus : celles qui se contentent du catalogue, et celles limitees au
     * theme velo. Rien du tout hors de la zone qu'il couvre - une requete qui rendrait zero lieu a coup sur
     * n'a pas a etre envoyee (cf. [PoiSources]).
     */
    private suspend fun datatourisme(
        base: String, box: Bbox, libres: Set<PoiCategory>, velo: Set<PoiCategory>,
    ): PoiBatch {
        if (!PoiSources.datatourismeCovers(box)) return PoiBatch(emptyList(), tronque = false)
        var tronque = false
        val lieux = buildList {
            if (libres.isNotEmpty()) {
                val r = Datatourisme.catalog(base, box.north, box.west, box.south, box.east, libres)
                tronque = tronque || r.size >= Datatourisme.PAGE_SIZE
                addAll(r)
            }
            if (velo.isNotEmpty()) {
                val r = Datatourisme.catalog(base, box.north, box.west, box.south, box.east, velo,
                    bikeOnly = true)
                tronque = tronque || r.size >= Datatourisme.PAGE_SIZE
                addAll(r)
            }
        }
        return PoiBatch(lieux, tronque)
    }

    /**
     * OpenStreetMap, **une requete par groupe** : tout hors de France, les seuls services du terrain en
     * France (cf. [PoiSources.osmGroups]).
     *
     * Deux au plus a la fois, et c'est une contrainte du service et non un choix de confort : l'instance
     * publique n'accorde que deux creneaux par adresse, et les requetes au-dela repartent en 504 plutot
     * que d'attendre leur tour. Le jeton fait donc ici la file d'attente que le serveur ne fait pas.
     */
    private fun osmSources(
        base: String, box: Bbox, libres: Set<PoiCategory>,
    ): List<suspend () -> PoiBatch> {
        val creneaux = Semaphore(OSM_CRENEAUX)
        return PoiSources.osmGroups(box, libres).map { groupe ->
            {
                val lieux = creneaux.withPermit { Overpass.around(base, box, groupe) }
                PoiBatch(lieux, tronque = lieux.size >= Overpass.LIMIT)
            }
        }
    }

    companion object {
        /**
         * Durée de vie du cache : une semaine.
         *
         * Les lieux touristiques changent peu - un camping n'ouvre pas et ne ferme pas dans le mois - et
         * une donnée d'une semaine vaut infiniment mieux qu'une carte vide sans réseau. Au-delà, elle est
         * jetée : un cache qu'on ne périme jamais finit par décrire un pays qui n'existe plus.
         */
        const val TTL_MS = 7L * 24 * 3600 * 1000

        /**
         * Requetes OpenStreetMap simultanees.
         *
         * Deux, parce que c'est ce que l'instance publique accorde par adresse. Au-dela, elle ne fait pas
         * patienter : elle refuse d'un 504, et le groupe concerne serait perdu pour cet ecran de carte.
         */
        const val OSM_CRENEAUX = 2
    }
}

/**
 * Ce qu'un chargement rend, d'où il vient, et s'il est **complet**.
 *
 * [partial] dit qu'une source a buté sur son plafond : ce qu'on montre n'est alors qu'une partie de ce
 * qu'elle connaît, et le reste est écarté dans un ordre que rien ne fixe. Le taire donnait une carte qui
 * avait l'air juste et dont les marqueurs disparaissaient d'un déplacement au suivant.
 */
data class PoiLoad(val pois: List<Poi>, val fromCache: Boolean, val partial: Boolean = false)

/**
 * Ce qu'une source rend : ses lieux, et si elle s'est arrêtée à son plafond.
 *
 * [tronque] est une **approximation par le bas** : il se lit sur le nombre de lieux retenus, après que les
 * enregistrements illisibles ou hors catégorie ont été écartés. Il peut donc manquer une troncature, jamais
 * en inventer une - on préfère se taire à tort qu'alarmer à tort.
 */
internal data class PoiBatch(val pois: List<Poi>, val tronque: Boolean)

private fun Poi.toEntity(now: Long, pinned: Boolean = false) = PoiCacheEntity(
    uuid = uuid, label = label, lat = lat, lon = lon, categoryKey = category.key,
    city = city, imageUrl = imageUrl, webUrl = webUrl, bikeTheme = bikeTheme, fetchedAt = now,
    pinned = pinned,
)

private fun PoiCacheEntity.toPoi(): Poi? {
    val cat = PoiCategory.byKey(categoryKey) ?: return null
    return Poi(uuid, label, lat, lon, cat, city, imageUrl, webUrl, bikeTheme)
}

/**
 * Le flux des deux sources, sans savoir ce qu'elles sont.
 *
 * Isolé du dépôt et de ses services pour une seule raison : c'est ici que se joue l'ordre d'affichage -
 * qui publie, quand, et ce que porte chaque émission - et cela se teste avec deux fonctions bidon, là où
 * le dépôt entier demanderait deux services en ligne et une base (cf. `PoiStreamTest`).
 *
 * Les règles tiennent en trois lignes :
 * - une source qui rend quelque chose publie **aussitôt**, avec ce que l'autre a déjà rendu ;
 * - une source muette ne publie rien - elle n'a rien à ajouter, et une émission de plus ferait clignoter
 *   la carte pour rien ;
 * - les deux muettes, on se rabat sur le cache, et l'émission finale le dit. Elle a lieu **même vide** :
 *   c'est elle qui apprend à l'écran que cette emprise est chargée, faute de quoi il la redemanderait à
 *   chaque geste.
 */
internal fun poiStream(
    datatourisme: suspend () -> PoiBatch,
    osm: List<suspend () -> PoiBatch>,
    garder: suspend (List<Poi>) -> Unit,
    cache: suspend () -> List<Poi>,
): Flow<PoiLoad> = channelFlow {
    val verrou = Mutex()
    var deDatatourisme = emptyList<Poi>()
    // Une seule source qui bute sur son plafond suffit a rendre l'affichage partiel, et cela ne se defait
    // pas : la reponse d'a cote ne rendra pas les lieux que celle-ci a laisses.
    var partiel = false
    // Par identifiant, et non en liste : le decoupage par groupe fait qu'un meme objet peut repondre a deux
    // requetes - un hotel-restaurant est rendu par les hebergements ET par la restauration - et deux
    // marqueurs se poseraient alors l'un sur l'autre (cf. mieuxClasse).
    val dOsm = LinkedHashMap<String, Poi>()
    var publie = false

    suspend fun arrivee(reponse: PoiBatch, venuDeDatatourisme: Boolean) {
        val lieux = reponse.pois
        if (reponse.tronque) partiel = true
        if (lieux.isEmpty()) return
        // Sous verrou : les sources arrivent sur des fils distincts, et l'emission doit porter un etat
        // coherent - jamais la moitie d'une reunion en cours.
        verrou.withLock {
            if (venuDeDatatourisme) deDatatourisme = lieux
            else lieux.forEach { p -> dOsm.merge(p.uuid, p) { a, b -> mieuxClasse(a, b) } }
            garder(lieux)
            publie = true
            send(PoiLoad(
                PoiSources.merge(deDatatourisme, dOsm.values.toList()),
                fromCache = false, partial = partiel,
            ))
        }
    }

    val travaux = buildList {
        add(launch { arrivee(datatourisme(), venuDeDatatourisme = true) })
        osm.forEach { source -> add(launch { arrivee(source(), venuDeDatatourisme = false) }) }
    }
    travaux.forEach { it.join() }
    if (!publie) {
        val gardes = cache()
        send(PoiLoad(gardes, fromCache = gardes.isNotEmpty(), partial = partiel))
    }
}
