package fr.lc4918.trailog.ui.offline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.ui.components.MapBarBackground

private val ColorCancelPoint = Color(0xFFF9A825)  // jaune
private val ColorCancelAll = Color(0xFFD32F2F)    // rouge
private val ColorValidate = Color(0xFF2E7D32)     // vert

/**
 * Barre du bas pendant la saisie de la bounding box (SPEC offline_map.md section 2, ajustée) : l'étape en
 * cours, le compteur de points, puis la ligne des 3 boutons.
 *
 * La barre va d'un bord à l'autre et jusqu'au bas de l'écran : c'est le plan de travail de l'étape, pas
 * une infobulle posée sur la carte. Seul son contenu se tient au-dessus de la barre de navigation.
 *
 * [dark] la fait passer du blanc du thème clair au fond sombre des barres de carte : sur un thème clair,
 * une barre noire pleine largeur trancherait avec le reste de l'écran, alors qu'elle s'y fond en sombre.
 */
@Composable
fun BboxDrawingOverlay(
    pointCount: Int,
    dark: Boolean,
    onCancelPoint: () -> Unit,
    onCancelAll: () -> Unit,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fg = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    Column(
        modifier.fillMaxWidth()
            .background(if (dark) MapBarBackground else Color.White)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // L'étape se nomme au-dessus de son compteur : sans elle, "0/2 points" ne dit pas ce qu'on compte.
        Text(
            stringResource(R.string.offline_bbox_step_label),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = fg,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.offline_bbox_points_count, pointCount),
            style = MaterialTheme.typography.bodySmall, color = fg,
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
 *  et léger fond, tous deux de la couleur du bouton, pour le rendre bien visible sur la barre.
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
