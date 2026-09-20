package fr.lc4918.trailog.ui.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.location.TrackWatch

/**
 * Fond de la banniere d'alerte : le rouge des messages d'erreur, a peine translucide.
 *
 * Elle ne reprend pas le gris des consignes de saisie (cf. MapPromptBar), et c'est voulu : celles-la
 * accompagnent un geste qu'on vient de demander, celle-ci interrompt une marche pour dire ce qu'on n'a
 * pas vu venir. Deux barres de meme couleur au meme endroit se confondraient dans le coin de l'oeil.
 */
private val AlertBarBackground = Color(0xFFB3261E).copy(alpha = 0.94f)

/**
 * Banniere du haut : on s'est ecarte de la trace suivie, de tant, et voila laquelle.
 *
 * **Toute la banniere repond**, et non sa seule croix : la sonnerie boucle jusqu'a ce qu'on reponde, et
 * viser une cible de 20 dp d'un pouce gante, en marchant, pour faire taire un telephone qui sonne, est
 * exactement le geste qu'il ne faut pas demander la. La croix reste, comme signe de ce que le tap fera.
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
    Box(
        modifier.fillMaxWidth().padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AlertBarBackground)
            .clickable(onClick = onClose)
            .padding(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(end = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(20.dp), tint = Color.White)
            Text(
                stringResource(R.string.alert_off_track_banner, Format.shortDistance(awayM, imperial), trackName),
                style = MaterialTheme.typography.bodySmall, color = Color.White,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
        }
        // La croix n'a plus de clic a elle : c'est la banniere entiere qui repond, et deux surfaces
        // cliquables imbriquees pour le meme effet ne feraient que rendre le tap incertain.
        Box(
            Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(16.dp), tint = Color.White)
        }
    }
}

