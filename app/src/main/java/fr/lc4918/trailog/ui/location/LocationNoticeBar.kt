package fr.lc4918.trailog.ui.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.ui.theme.Spacing
import fr.lc4918.trailog.ui.components.MapBannerTone
import fr.lc4918.trailog.ui.components.MapBanner

/**
 * L'orange de l'avertissement, distinct du rouge de l'alerte d'eloignement.
 *
 * Les deux bannieres ne disent pas la meme chose. Le rouge dit "tu quittes le chemin", un fait mesure. Cet
 * orange-ci dit "je ne sais plus ou tu es", ce qui est un aveu de l'application sur elle-meme. Leur donner
 * la meme couleur ferait lire la seconde comme la premiere.
 */
@Composable
fun LocationNoticeBar(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    // Le bandeau entier le referme ; le bouton d'action garde son propre toucher (cf. MapBanner).
    MapBanner(MapBannerTone.WARNING, modifier, onClose = onDismiss) { fg ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.LocationOff, null, Modifier.size(20.dp), tint = fg)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = fg, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
