package fr.lc4918.trailog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import fr.lc4918.trailog.R

/**
 * Couleurs proposees pour tout ce qui se dessine sur la carte : traces, dossiers, repere de position.
 *
 * Une seule palette, commune : deux listes divergentes finiraient par offrir un rouge ici et un autre la,
 * pour des objets qui se regardent sur le meme fond.
 */
val MapPalette = listOf(
    "#1F6FB2", "#1098AD", "#2F9E44", "#7CB342", "#F4B400", "#F08C00", "#E8590C", "#E03131",
    "#C2185B", "#9C36B5", "#6741D9", "#3949AB", "#00897B", "#6D4C41", "#546E7A", "#212121",
)

/** Choix d'une couleur dans la palette, la couleur courante portant sa coche. */
@Composable
fun ColorPickerDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_color_title)) },
        text = {
            Column {
                MapPalette.chunked(4).forEach { row ->
                    Row {
                        row.forEach { hex ->
                            Box(
                                Modifier.padding(6.dp).size(40.dp).clip(CircleShape)
                                    .background(Color(hex.toColorInt()))
                                    .clickable { onPick(hex) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (hex.equals(current, true)) Icon(Icons.Filled.Check, null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
