package fr.lc4918.trailog.map.offline

/** Étape où en est le téléchargement (SPEC offline_map.md section 4, "États de la popup de progression"). */
enum class OfflinePhase { RUNNING, SUCCESS, ERROR }

/**
 * État observable de la popup de progression (domaine B). [minimized] est un état purement UI (popup
 * réduite en bouton orange sur la carte) porté ici pour survivre aux recompositions.
 */
data class OfflineDownloadState(
    val name: String,
    val total: Int,
    val done: Int = 0,
    val failed: Int = 0,
    val phase: OfflinePhase = OfflinePhase.RUNNING,
    val minimized: Boolean = false,
) {
    /** Tuiles traitées (réussies + échouées) sur le total, en pourcentage borné [0, 100]. */
    val percent: Int get() = if (total <= 0) 0 else ((done + failed) * 100 / total).coerceIn(0, 100)
}

/** Issue du moteur de téléchargement, consommée par le dépôt pour décider d'enregistrer (ou non) le
 *  provider MBTiles résultant. */
sealed interface OfflineDownloadResult {
    /** Le fichier MBTiles est complet et un provider a été enregistré. */
    data class Success(val providerId: String) : OfflineDownloadResult
    /** Arrêté sur erreur (continueOnError désactivé) : [failed] tuiles manquantes, fichier supprimé. */
    data class Failed(val failed: Int) : OfflineDownloadResult
}
