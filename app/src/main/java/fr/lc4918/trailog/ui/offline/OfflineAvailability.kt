package fr.lc4918.trailog.ui.offline

import fr.lc4918.trailog.data.db.CompositeEntity
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

/**
 * Le nom du fond affiche, tel qu'il se lit dans les reglages.
 *
 * Il sert a DIRE POURQUOI un telechargement est refuse : "ce fond ne se telecharge pas" laisse chercher
 * lequel, alors que l'utilisateur en a souvent plusieurs et qu'il vient d'en choisir un.
 *
 * Rend une chaine vide quand le fond ne se retrouve ni parmi les fournisseurs ni parmi les composites :
 * l'appelant dit alors la phrase sans nom plutot qu'avec un identifiant technique.
 */
fun basemapLabel(
    defaultBasemapId: String, providers: List<ProviderEntity>, composites: List<CompositeEntity>,
): String {
    val compositeId = compositeIdFromBasemapId(defaultBasemapId)
    if (compositeId != null) return composites.firstOrNull { it.id == compositeId }?.name.orEmpty()
    return providers.firstOrNull { it.id == defaultBasemapId }?.name.orEmpty()
}
