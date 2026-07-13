package fr.lc4918.trailog.ui.offline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BboxActionButton(
                icon = Icons.AutoMirrored.Filled.Undo, label = stringResource(R.string.offline_action_cancel_point),
                color = ColorCancelPoint, enabled = pointCount > 0, onClick = onCancelPoint, modifier = Modifier.weight(1f),
            )
            BboxActionButton(
                icon = Icons.Filled.Close, label = stringResource(R.string.offline_action_back),
                color = ColorCancelAll, enabled = true, onClick = onCancelAll, modifier = Modifier.weight(1f),
            )
            BboxActionButton(
                icon = Icons.Filled.CheckCircle, label = stringResource(R.string.offline_action_validate),
                color = ColorValidate, enabled = pointCount >= 2, onClick = onValidate, modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Bouton outlined icône puis texte (icône à gauche, texte à droite), contour de la même couleur que
 *  l'icône/le texte. Reste visible mais estompé (jamais masqué) quand [enabled] est faux. */
@Composable
private fun BboxActionButton(
    icon: ImageVector, label: String, color: Color,
    enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val tint = if (enabled) color else color.copy(alpha = 0.4f)
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
        border = BorderStroke(1.dp, tint),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // Row explicite + fillMaxWidth : le Row interne par défaut du Button ne centre le couple
        // icône/texte que s'il reste de la marge après son contenu ; avec le libellé le plus long
        // ("Annuler point"), ce content pouvait finir collé à gauche plutôt que centré.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            // maxLines = 2 (pas 1) : "Annuler point" ne tient pas sur une ligne dans le tiers de
            // largeur alloué à 3 boutons - en une seule ligne, la fin du texte ("point") était
            // tronquée sans le montrer (retour visuel juste "Annuler").
            Text(label, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 13.sp)
        }
    }
}
