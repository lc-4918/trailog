package fr.lc4918.trailog.map.offline

import fr.lc4918.trailog.data.db.ProviderEntity

/**
 * Les fonds dont le service refuse qu'on telecharge les tuiles en masse.
 *
 * OpenStreetMap (tile.openstreetmap.org) l'interdit dans sa politique d'usage et renvoie des tuiles
 * "access blocked" a qui s'y risque. La regle vaut pour tout ce qui va chercher un couloir ou une zone
 * entiere de tuiles : le telechargement hors ligne comme l'envoi a la montre.
 */
object BulkDownloadPolicy {
    fun forbids(provider: ProviderEntity): Boolean =
        provider.urlTemplate.contains("tile.openstreetmap.org", ignoreCase = true)
}
