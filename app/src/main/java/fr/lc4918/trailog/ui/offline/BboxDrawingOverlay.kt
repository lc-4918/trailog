package fr.lc4918.trailog.ui.offline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.R

private val ColorCancelPoint = Color(0xFFF9A825)  // jaune
private val ColorCancelAll = Color(0xFFD32F2F)    // rouge
private val ColorValidate = Color(0xFF2E7D32)     // vert
private val BarBackground = Color(0xFF1B1B1B).copy(alpha = 0.9f)   // gris très foncé, 10% transparent

/** Barre du bas pendant la saisie de la bounding box (SPEC offline_map.md section 2, ajustée) : compteur de
 *  points en haut, une seule ligne des 3 boutons en-dessous (barre plus basse). */
@Composable
fun BboxDrawingOverlay(
    pointCount: Int,
    onCancelPoint: () -> Unit,
    onCancelAll: () -> Unit,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(8.dp)
            .background(BarBackground, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text(
            stringResource(R.string.offline_bbox_points_count, pointCount),
            style = MaterialTheme.typography.bodySmall, color = Color.White,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))   // ~0.5 ligne
        // Rouge (retour) à gauche, orange (annuler le dernier point) au milieu, vert (valider) à droite.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            BboxActionButton(
                icon = Icons.Filled.Close, label = stringResource(R.string.offline_action_back),
                color = ColorCancelAll, enabled = true, onClick = onCancelAll,
            )
            BboxActionButton(
                icon = Icons.AutoMirrored.Filled.Undo, label = stringResource(R.string.offline_action_cancel_point),
                color = ColorCancelPoint, enabled = pointCount > 0, onClick = onCancelPoint,
            )
            BboxActionButton(
                icon = Icons.Filled.CheckCircle, label = stringResource(R.string.offline_action_validate),
                color = ColorValidate, enabled = pointCount >= 2, onClick = onValidate,
            )
        }
    }
}

/** Bouton d'action rond à icône seule (le libellé passe en description d'accessibilité) : contour épais
 *  et léger fond, tous deux de la couleur du bouton, pour le rendre bien visible sur la barre sombre.
 *  Reste visible mais estompé quand [enabled] est faux. */
@Composable
private fun BboxActionButton(
    icon: ImageVector, label: String, color: Color,
    enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val tint = if (enabled) color else color.copy(alpha = 0.4f)
    OutlinedIconButton(
        onClick = onClick, enabled = enabled, modifier = modifier.size(48.dp),
        shape = CircleShape,
        border = BorderStroke(3.dp, tint),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = tint.copy(alpha = 0.15f), contentColor = tint,
            disabledContainerColor = tint.copy(alpha = 0.15f), disabledContentColor = tint,
        ),
    ) {
        Icon(icon, label, Modifier.size(22.dp))
    }
}
