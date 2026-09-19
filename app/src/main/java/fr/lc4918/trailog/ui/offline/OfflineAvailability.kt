package fr.lc4918.trailog.ui.offline

import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.map.compositeIdFromBasemapId

/**
 * Le fond par defaut peut-il etre telecharge pour le hors-ligne.
 *
 * Seulement un fond EN LIGNE standard : ni composite, ni MBTiles - deja sur le telephone -, ni relief. Ni
 * OpenStreetMap (tile.openstreetmap.org), dont la politique d'usage interdit le telechargement en masse et
 * renvoie des tuiles "access blocked".
 *
 * Partagee par les deux entrees du telechargement : la zone, depuis les reglages, et la trace, depuis son
 * menu dans le menu lateral.
 */
fun offlineDownloadAvailable(defaultBasemapId: String, providers: List<ProviderEntity>): Boolean =
    compositeIdFromBasemapId(defaultBasemapId) == null &&
        providers.firstOrNull { it.id == defaultBasemapId }?.let {
            it.type != "MBTILES" && it.type != "DEM" &&
                !it.urlTemplate.contains("tile.openstreetmap.org", ignoreCase = true)
        } == true
