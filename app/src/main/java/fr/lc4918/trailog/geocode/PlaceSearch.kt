package fr.lc4918.trailog.geocode

import android.content.Context
import fr.lc4918.trailog.geocode.offline.OfflinePlaces
import fr.lc4918.trailog.net.ServiceUrl

/**
 * La recherche de lieux telle que l'application la fait : Photon quand on peut l'atteindre, les index
 * telecharges avec les zones d'itineraire (cf. [OfflinePlaces]) quand on ne le peut pas - ou qu'il n'a pas
 * repondu.
 *
 * **Photon d'abord quand le reseau est la** : son classement et ses adresses postales valent mieux que ceux
 * de l'index, qui ne connait que des noms. **L'index d'abord sans reseau** : attendre l'echec d'une requete
 * qu'on sait perdue ferait attendre pour rien.
 *
 * Les deux rendent la meme chose que [Photon] - null quand rien n'a pu repondre, liste vide quand on a
 * repondu qu'il n'y avait rien -, si bien que les ecrans n'ont rien change a leurs messages.
 */
object PlaceSearch {

    /** L'index doit-il repondre avant Photon : le service est hors d'atteinte, et l'index a quelque chose. */
    internal fun offlineFirst(serviceReachable: Boolean, indexAvailable: Boolean): Boolean =
        !serviceReachable && indexAvailable

    private fun reachable(ctx: Context, base: String): Boolean =
        !ServiceUrl.needsInternet(base) || NetworkStatus.hasInternet(ctx)

    suspend fun search(
        ctx: Context, base: String, query: String, lang: String, limit: Int, center: Pair<Double, Double>? = null,
    ): List<GeocodePlace>? {
        val offline = OfflinePlaces.of(ctx)
        val available = offline.available()
        if (offlineFirst(reachable(ctx, base), available)) return offline.search(query, limit, center)
        return Photon.search(base, query, lang, limit, center)
            ?: if (available) offline.search(query, limit, center) else null
    }

    suspend fun reverse(ctx: Context, base: String, lon: Double, lat: Double, lang: String): List<GeocodePlace>? {
        val offline = OfflinePlaces.of(ctx)
        val available = offline.available()
        if (offlineFirst(reachable(ctx, base), available)) return offline.reverse(lon, lat)
        return Photon.reverse(base, lon, lat, lang)
            ?: if (available) offline.reverse(lon, lat) else null
    }
}
