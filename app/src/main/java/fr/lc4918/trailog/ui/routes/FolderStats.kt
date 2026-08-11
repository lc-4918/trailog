package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity

/**
 * Ce que porte un dossier, tout compris.
 *
 * [movingTime] est null des qu'une seule trace du dossier n'a pas d'horodatage : additionner les durees
 * connues donnerait un total plus petit que le temps reellement passe, sans que rien ne le dise. Les
 * distances et deniveles, eux, existent pour toutes les traces et s'additionnent sans reserve.
 */
data class FolderStats(
    val layers: Int,
    val tracks: Int,
    val markers: Int,
    val distance: Double,
    val ascent: Double,
    val descent: Double,
    val movingTime: Double?,
)

/**
 * Totaux d'un dossier, **sous-dossiers compris**.
 *
 * Recursif comme la visibilite, la couleur et la suppression : un dossier repond de tout ce qu'il
 * contient, et s'arreter a ses couches directes donnerait un total qui ne correspond a rien de ce que
 * l'ecran montre.
 *
 * Les chiffres viennent de la ligne en base de chaque couche, calculee a l'import : aucune geometrie n'est
 * relue ici, et l'ecran s'ouvre donc instantanement, meme sur un dossier de deux cents traces.
 */
fun folderStats(folderId: Long, folders: List<FolderEntity>, layers: List<LayerEntity>): FolderStats {
    val under = layersUnder(folderId, folders, layers)
    val withLine = under.filter { it.hasLine }
    return FolderStats(
        layers = under.size,
        tracks = withLine.size,
        markers = under.count { it.hasPoints },
        distance = withLine.sumOf { it.distance },
        ascent = withLine.sumOf { it.ascent },
        descent = withLine.sumOf { it.descent },
        movingTime = if (withLine.isNotEmpty() && withLine.all { it.movingTime != null })
            withLine.sumOf { it.movingTime ?: 0.0 } else null,
    )
}
