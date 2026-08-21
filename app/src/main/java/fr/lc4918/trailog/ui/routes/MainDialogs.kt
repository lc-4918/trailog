package fr.lc4918.trailog.ui.routes

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import fr.lc4918.trailog.R
import fr.lc4918.trailog.ui.settings.ProvideSettingsPalette
import fr.lc4918.trailog.ui.settings.settingsPalette
import fr.lc4918.trailog.ui.settings.SettingsCard
import fr.lc4918.trailog.ui.settings.SetRow
import fr.lc4918.trailog.ui.settings.RowDivider
import fr.lc4918.trailog.ui.settings.Hint
import fr.lc4918.trailog.ui.settings.RowIcon
import androidx.compose.material.icons.filled.KeyboardArrowRight

/**
 * Les boites de dialogue de l'ecran principal qui ne dependent que de leurs parametres.
 *
 * Celles qui lisent l'etat de l'ecran - le choix du dossier d'import, celui d'une trace a suivre - sont
 * restees la ou cet etat vit. Ici, seules celles qu'on peut appeler de n'importe ou.
 */

/**
 * Choix de ce que l'on telecharge : un rectangle, ou le couloir qui borde une trace.
 *
 * Le rectangle etait le seul mode, et il reste le bon pour un secteur qu'on ne connait pas encore. Pour
 * une sortie deja tracee, il fait telecharger tout ce qui l'entoure - trois quarts de tuiles qu'on ne
 * verra jamais sur une diagonale de soixante kilometres.
 */
@Composable
internal fun OfflineExtentDialog(
    dark: Boolean, onDismiss: () -> Unit, onArea: () -> Unit, onTrack: () -> Unit,
) {
    ProvideSettingsPalette(dark = dark) {
        val p = settingsPalette
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = p.screen,
            title = { Text(stringResource(R.string.offline_extent_title), color = p.label) },
            text = {
                SettingsCard {
                    SetRow(stringResource(R.string.offline_extent_area), onClick = onArea) {
                        RowIcon(Icons.Filled.KeyboardArrowRight, null)
                    }
                    Hint(stringResource(R.string.offline_extent_area_hint))
                    RowDivider()
                    SetRow(stringResource(R.string.offline_extent_track), onClick = onTrack) {
                        RowIcon(Icons.Filled.KeyboardArrowRight, null)
                    }
                    Hint(stringResource(R.string.offline_extent_track_hint))
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = p.accent) }
            },
        )
    }
}

/**
 * Choix du format d'export : ce que chacun garde, et ce qu'il perd.
 *
 * Un sous-menu de plus dans la ligne de la couche aurait suffi a lancer l'export, mais pas a CHOISIR : les
 * deux formats ne portent pas la meme chose, et l'ecart ne se devine ni du nom du format ni du selecteur de
 * fichier du systeme, qui ne montre qu'un nom et un dossier. D'ou une etape a part, ou chaque format dit ce
 * qu'il vaut.
 *
 * Meme grammaire que les reglages - carte, ligne, texte d'aide dessous : c'est le meme genre d'objet, un
 * choix explique.
 */
@Composable
internal fun ExportFormatDialog(dark: Boolean, onDismiss: () -> Unit, onPick: (geoJson: Boolean) -> Unit) {
    ProvideSettingsPalette(dark = dark) {
        val p = settingsPalette
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = p.screen,
            title = { Text(stringResource(R.string.action_export_layer), color = p.label) },
            text = {
                SettingsCard {
                    SetRow(stringResource(R.string.export_format_gpx), onClick = { onPick(false) }) {
                        RowIcon(Icons.Filled.KeyboardArrowRight, null)
                    }
                    Hint(stringResource(R.string.export_format_gpx_hint))
                    RowDivider()
                    SetRow(stringResource(R.string.export_format_geojson), onClick = { onPick(true) }) {
                        RowIcon(Icons.Filled.KeyboardArrowRight, null)
                    }
                    Hint(stringResource(R.string.export_format_geojson_hint))
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = p.accent) }
            },
        )
    }
}
