package com.tunturo.app.watch.vector

import com.tunturo.app.data.db.ProviderEntity
import com.tunturo.app.map.offline.TileHttp
import org.json.JSONObject
import java.io.Closeable

/**
 * Les tuiles vectorielles OpenMapTiles d'OpenFreeMap (donnees OpenStreetMap, gratuites, sans cle), lues et decodees pour le dessin du
 * fond « Trailmap » ([WatchOsmRenderer]). Le service publie ses tuiles sous une adresse datee (le nom d'une version de la planete) :
 * [WatchOsmSource] la lit d'abord dans sa fiche TileJSON ([TILEJSON_URL]), une fois pour tout l'envoi.
 *
 * Comme le fond PLAN.IGN ([WatchVectorSource]), ce fond n'est pas un reglage de l'utilisateur : la fenetre d'envoi l'ajoute a sa liste
 * ([provider]).
 */
class WatchOsmSource(
    private val fetch: (String) -> ByteArray? = TileHttp::get,
) : Closeable {

    private var urlTemplate: String? = null

    /** La tuile (x, y) au zoom [zoom] (au plus [WatchOsmRenderer.MAX_SOURCE_ZOOM]), ou null si le service ne repond pas ou la renvoie illisible. */
    fun tile(zoom: Int, x: Int, y: Int): VectorTile? {
        val template = urlTemplate ?: resolveTemplate()?.also { urlTemplate = it } ?: return null
        val bytes = fetch(template.replace("{z}", zoom.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())) ?: return null
        return runCatching { VectorTile.decode(bytes) }.getOrNull()
    }

    private fun resolveTemplate(): String? {
        val bytes = fetch(TILEJSON_URL) ?: return null
        return runCatching { JSONObject(String(bytes)).getJSONArray("tiles").getString(0) }.getOrNull()
    }

    override fun close() {}

    companion object {
        const val PROVIDER_ID = "trailmap_osm"
        const val TYPE = "TRAILMAP_OSM"
        const val TILEJSON_URL = "https://tiles.openfreemap.org/planet"
        private const val MAX_ZOOM = 14

        /** Le fond tel que la fenetre d'envoi le liste ; [name] est son nom affiche. */
        fun provider(name: String) = ProviderEntity(
            id = PROVIDER_ID, name = name, groupName = "Monde", type = TYPE, urlTemplate = TILEJSON_URL,
            minZoom = 0, maxZoom = MAX_ZOOM, builtin = true,
        )

        fun isOsm(provider: ProviderEntity) = provider.id == PROVIDER_ID
    }
}
