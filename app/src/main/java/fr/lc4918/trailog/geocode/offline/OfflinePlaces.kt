package fr.lc4918.trailog.geocode.offline

import android.content.Context
import fr.lc4918.trailog.geocode.GeocodePlace
import fr.lc4918.trailog.geocode.Photon
import fr.lc4918.trailog.routing.offline.BrouterStorage
import fr.lc4918.trailog.routing.offline.BrouterTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * La recherche de lieux SANS reseau : les index des carres telecharges avec les zones d'itineraire (cf.
 * `BrouterDownloads`), interroges l'un apres l'autre.
 *
 * **Un fichier par carre, et rien en memoire** : chaque appel ouvre les carres utiles, lit, referme. Ouvrir
 * une base SQLite coute quelques millisecondes, et il n'y a ainsi ni connexion a partager entre les fils ni
 * fichier tenu ouvert sous un carre qu'on supprime ou qu'on deplace.
 */
class OfflinePlaces(private val dirOf: () -> File) {

    /** Les index presents, par carre. */
    fun installed(): Map<BrouterTile, File> =
        dirOf().listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(BrouterTile.GEOCODE_SUFFIX) }
            .mapNotNull { f -> BrouterTile.parse(f.name.removeSuffix(BrouterTile.GEOCODE_SUFFIX))?.let { it to f } }
            .toMap()

    /** Y a-t-il de quoi chercher sans reseau. */
    fun available(): Boolean = installed().isNotEmpty()

    /**
     * Les lieux dont un mot commence par [query], classes (cf. [PlaceQuery.order]) puis ramenes a [center]
     * (en lon, lat) comme ceux de Photon (cf. [Photon.rank]) - les deux listes se lisent donc de la meme
     * facon. Liste vide quand rien ne correspond, ou qu'aucun index n'est la : la distinction qu'on fait
     * pour Photon (service muet ou rien trouve) n'a pas lieu d'etre, il n'y a pas de service.
     */
    suspend fun search(query: String, limit: Int, center: Pair<Double, Double>? = null): List<GeocodePlace> =
        withContext(Dispatchers.IO) {
            val match = PlaceQuery.match(query) ?: return@withContext emptyList()
            val hits = installed().values.flatMap { file ->
                runCatching {
                    PlaceIndex.open(file).use { it.searchPlaces(match, PER_TILE_PLACES) + it.searchStreets(match, PER_TILE_STREETS) }
                }.getOrDefault(emptyList())
            }
            Photon.rank(PlaceQuery.order(hits, query).map { it.toPlace() }, center, limit)
        }

    /**
     * Ce qu'il y a au point (lon, lat) : le lieu qui le dit le mieux (cf. [PlaceQuery.pickReverse]), seul
     * dans la liste, comme la reponse de Photon. Vide quand rien n'est a portee.
     */
    suspend fun reverse(lon: Double, lat: Double): List<GeocodePlace> = withContext(Dispatchers.IO) {
        val files = installed()
        val hits = PlaceQuery.tilesAround(lon, lat).mapNotNull { files[it] }.flatMap { file ->
            runCatching { PlaceIndex.open(file).use { it.around(lon, lat) } }.getOrDefault(emptyList())
        }
        listOfNotNull(PlaceQuery.pickReverse(hits, lon, lat)?.toPlace())
    }

    companion object {
        /** Combien de lieux, et de rues, on garde de chaque carre avant de les classer ensemble. */
        private const val PER_TILE_PLACES = 60
        private const val PER_TILE_STREETS = 40

        fun of(ctx: Context) = OfflinePlaces { BrouterStorage.dir(ctx) }
    }
}
