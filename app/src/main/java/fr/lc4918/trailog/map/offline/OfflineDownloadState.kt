package fr.lc4918.trailog.map.offline

/** Étape où en est le téléchargement (SPEC offline_map.md section 4, "États de la popup de progression"). */
enum class OfflinePhase { RUNNING, SUCCESS, ERROR }

/**
 * Ce que fait le telechargement pendant qu'il est en cours. Les tuiles ne sont que la premiere etape : une fois
 * la derniere recue, le fichier se ferme, les miniatures se generent et les lieux se recuperent - quelques
 * secondes pendant lesquelles la barre est pleine et rien ne bouge. La popup nomme alors l'etape.
 */
enum class OfflineStage { TILES, WRITING, THUMBNAILS, POIS }

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
    /** L'etape en cours : au-dela des tuiles, la popup la nomme a la place de la barre de progression. */
    val stage: OfflineStage = OfflineStage.TILES,
    /** Les lieux font-ils partie du telechargement : sans eux, leur etape n'est pas annoncee. */
    val withPois: Boolean = false,
    /**
     * Points d'interet emportes avec la zone : leur nombre, 0 si le service n'a rien rendu la, null si on
     * n'en a pas demande ou si le service n'a pas repondu.
     *
     * Trois etats et non deux : "aucun lieu ici" et "le service est muet" ne s'annoncent pas du meme mot,
     * et la zone est deja telechargee dans les deux cas - le message de succes doit pouvoir le nuancer
     * sans se transformer en erreur.
     */
    val pinnedPois: Int? = null,
) {
    /** Les etapes qui suivent les tuiles, dans l'ordre ou elles s'enchainent. */
    val stagesAfterTiles: List<OfflineStage>
        get() = listOfNotNull(OfflineStage.WRITING, OfflineStage.THUMBNAILS, OfflineStage.POIS.takeIf { withPois })

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
