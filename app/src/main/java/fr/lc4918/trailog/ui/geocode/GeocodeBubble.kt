package fr.lc4918.trailog.ui.geocode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.ui.points.InfoBubbleWidth

/**
 * Infobulle du lieu trouvé : son adresse, et de quoi la fermer.
 *
 * Elle a porté des mesures de distance et le profil de l'itinéraire jusqu'au lieu ; c'est le
 * planificateur qui s'en charge (cf. `ui/planner`). Ce qui reste correspond à ce que le géocodage sait
 * vraiment dire : où se trouve l'adresse cherchée.
 */
@Composable
fun GeocodeBubble(
    address: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    fontSp: Int = 14,
    backgroundAlpha: Float = 1f,
) {
    Card(
        modifier = modifier.width(InfoBubbleWidth),   // même largeur que l'infobulle d'un marqueur
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp),
        // Couleur de contenu imposée : sous 100 % d'opacité, le fond n'est plus l'une des couleurs du thème
        // et contentColorFor n'y reconnaît rien, laissant le texte hériter du LocalContentColor ambiant.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // L'adresse tient sur une seule ligne, tronquée au besoin : la bulle garde ainsi une
                // hauteur constante, quelle que soit la longueur du libellé rendu par le géocodeur.
                Text(address, fontSize = fontSp.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(top = 8.dp, bottom = 8.dp, end = 4.dp))
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
