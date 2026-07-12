package fr.lc4918.trailog.ui.offline

import fr.lc4918.trailog.map.offline.Bbox

/** Paramètres validés à l'étape de configuration (SPEC offline_map.md §3), avant que le moteur
 *  de téléchargement (domaine B) ne les consomme. */
data class OfflineDownloadRequest(
    val bbox: Bbox,
    val minZoom: Int,
    val maxZoom: Int,
    val name: String,
    val continueOnError: Boolean,
)
