package fr.lc4918.trailog.poi

import fr.lc4918.trailog.data.db.PoiCacheEntity
import fr.lc4918.trailog.data.db.PoiDao
import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.map.offline.Bbox

/**
 * Les points d'intérêt d'une zone : le service quand il répond, le cache sinon.
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
     * Charge la zone [box] pour les catégories demandées, en deux requêtes au plus - celles qui se
     * contentent du catalogue, et celles limitées au thème vélo.
     *
     * Rend le résultat **et** d'où il vient : l'écran doit pouvoir dire "ce sont les derniers points
     * connus" plutôt que de laisser croire à une réponse fraîche.
     */
    suspend fun load(
        base: String, box: Bbox, libres: Set<PoiCategory>, velo: Set<PoiCategory>,
    ): PoiLoad {
        val frais = buildList {
            if (libres.isNotEmpty()) {
                addAll(Datatourisme.catalog(base, box.north, box.west, box.south, box.east, libres))
            }
            if (velo.isNotEmpty()) {
                addAll(Datatourisme.catalog(base, box.north, box.west, box.south, box.east, velo,
                    bikeOnly = true))
            }
        }
        if (frais.isNotEmpty()) {
            val now = System.currentTimeMillis()
            runCatching {
                dao.upsertAll(frais.map { it.toEntity(now) })
                dao.deleteOlderThan(now - TTL_MS)
            }
            return PoiLoad(frais, fromCache = false)
        }
        // Rien du service : soit la zone est vide, soit le reseau manque. Le cache tranche - s'il porte
        // quelque chose ici, c'est qu'on y est deja venu, et le montrer vaut mieux qu'une carte nue.
        val gardes = runCatching { dao.inBounds(box.north, box.west, box.south, box.east) }
            .getOrDefault(emptyList())
        val retenues = libres + velo
        val duCache = gardes.mapNotNull { it.toPoi() }
            .filter { it.category in retenues && (it.category.group !in velo.map { c -> c.group } || it.bikeTheme) }
        return PoiLoad(duCache, fromCache = duCache.isNotEmpty())
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
    ): Int? {
        if (libres.isEmpty() && velo.isEmpty()) return 0
        val frais = buildList {
            if (libres.isNotEmpty()) {
                addAll(Datatourisme.catalog(base, box.north, box.west, box.south, box.east, libres))
            }
            if (velo.isNotEmpty()) {
                addAll(Datatourisme.catalog(base, box.north, box.west, box.south, box.east, velo,
                    bikeOnly = true))
            }
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

    companion object {
        /**
         * Durée de vie du cache : une semaine.
         *
         * Les lieux touristiques changent peu - un camping n'ouvre pas et ne ferme pas dans le mois - et
         * une donnée d'une semaine vaut infiniment mieux qu'une carte vide sans réseau. Au-delà, elle est
         * jetée : un cache qu'on ne périme jamais finit par décrire un pays qui n'existe plus.
         */
        const val TTL_MS = 7L * 24 * 3600 * 1000
    }
}

/** Ce qu'un chargement rend, et d'où il vient. */
data class PoiLoad(val pois: List<Poi>, val fromCache: Boolean)

private fun Poi.toEntity(now: Long, pinned: Boolean = false) = PoiCacheEntity(
    uuid = uuid, label = label, lat = lat, lon = lon, categoryKey = category.key,
    city = city, imageUrl = imageUrl, webUrl = webUrl, bikeTheme = bikeTheme, fetchedAt = now,
    pinned = pinned,
)

private fun PoiCacheEntity.toPoi(): Poi? {
    val cat = PoiCategory.byKey(categoryKey) ?: return null
    return Poi(uuid, label, lat, lon, cat, city, imageUrl, webUrl, bikeTheme)
}
