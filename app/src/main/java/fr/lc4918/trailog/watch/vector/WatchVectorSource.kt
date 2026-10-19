package fr.lc4918.trailog.watch.vector

import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.map.offline.TileHttp
import java.io.Closeable

/**
 * Les tuiles vectorielles PLAN.IGN de la Geoplateforme (publiques, sans cle), lues et decodees pour le dessin du fond
 * "Scan 25 vectoriel" ([WatchVectorRenderer]). Meme chemin reseau que les fonds raster ([TileHttp]).
 *
 * Ce fond n'est pas un reglage de l'utilisateur : il n'est pas dans la base des fonds, la fenetre d'envoi l'ajoute a sa liste
 * ([provider]). Son adresse est donc une constante.
 */
class WatchVectorSource(
    private val urlTemplate: String = URL_TEMPLATE,
    private val fetch: (String) -> ByteArray? = TileHttp::get,
) : Closeable {

    /** La tuile (x, y) au zoom [zoom], ou null si le service ne repond pas ou renvoie une tuile illisible. */
    fun tile(zoom: Int, x: Int, y: Int): VectorTile? {
        val bytes = fetch(urlTemplate.replace("{z}", zoom.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())) ?: return null
        return runCatching { VectorTile.decode(bytes) }.getOrNull()
    }

    override fun close() {}

    companion object {
        const val PROVIDER_ID = "plan_ign_vector"
        const val TYPE = "PLAN_IGN_VECTOR"
        const val URL_TEMPLATE = "https://data.geopf.fr/tms/1.0.0/PLAN.IGN/{z}/{x}/{y}.pbf"
        private const val MAX_ZOOM = 18

        /** Le fond tel que la fenetre d'envoi le liste ; [name] est son nom affiche (traduit). */
        fun provider(name: String) = ProviderEntity(
            id = PROVIDER_ID, name = name, groupName = "Pays", type = TYPE, urlTemplate = URL_TEMPLATE,
            minZoom = 0, maxZoom = MAX_ZOOM, builtin = true,
        )

        fun isVector(provider: ProviderEntity) = provider.id == PROVIDER_ID
    }
}
