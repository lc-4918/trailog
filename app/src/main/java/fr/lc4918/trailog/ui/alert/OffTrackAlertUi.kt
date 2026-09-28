package fr.lc4918.trailog.ui.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.ui.theme.Spacing
import fr.lc4918.trailog.ui.components.MapBannerTone
import fr.lc4918.trailog.ui.components.MapBanner
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.location.TrackWatch

/**
 * Banniere du haut : on s'est ecarte de la trace suivie, de tant, et voila laquelle.
 *
 * **Toute la banniere repond**, et elle n'a pas de croix : la sonnerie boucle jusqu'a ce qu'on reponde, et
 * viser une cible de 20 dp d'un pouce gante, en marchant, pour faire taire un telephone qui sonne, est
 * exactement le geste qu'il ne faut pas demander la. La croix y est restee un temps, comme signe de ce que
 * le tap ferait ; elle disait surtout le contraire de ce qui est vrai - qu'il fallait la viser - et prenait
 * au texte la largeur qu'elle reservait.
 *
 * Repondre ne rend pas la marche a la trace - cela tait CET ecart-la (cf. [TrackWatch.silenced]).
 * L'alerte, elle, se desarme depuis la cloche du tableau de bord (cf. [Dashboard]).
 */
@Composable
fun OffTrackAlertBar(
    trackName: String,
    awayM: Double,
    imperial: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Le rouge des erreurs, et non le fond des consignes (cf. MapBannerTone) : celles-la accompagnent un
    // geste qu'on vient de demander, celle-ci interrompt une marche pour dire ce qu'on n'a pas vu venir.
    MapBanner(MapBannerTone.ALERT, modifier, onClose = onClose) { fg ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // La banniere n'est la que pendant l'alerte : sa cloche sonne, comme celle du tableau de bord.
            Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(20.dp).ringing(true), tint = fg)
            Text(
                stringResource(R.string.alert_off_track_banner, Format.shortDistance(awayM, imperial), trackName),
                style = MaterialTheme.typography.bodyMedium, color = fg,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
        }
    }
}
