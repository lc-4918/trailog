package fr.lc4918.trailog.ui.location

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
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.R

/**
 * L'orange de l'avertissement, distinct du rouge de l'alerte d'eloignement.
 *
 * Les deux bannieres ne disent pas la meme chose. Le rouge dit "tu quittes le chemin", un fait mesure. Cet
 * orange-ci dit "je ne sais plus ou tu es", ce qui est un aveu de l'application sur elle-meme. Leur donner
 * la meme couleur ferait lire la seconde comme la premiere.
 */
private val LocationNoticeBackground = Color(0xFFB35C00).copy(alpha = 0.94f)

/**
 * Le suivi s'est arrete tout seul, ou le repere ne bouge plus : la carte le DIT.
 *
 * **Ce qui manquait.** Quand le suivi s'arretait - localisation coupee par l'economie d'energie, service
 * tue par le systeme -, l'application effacait le repere et se taisait. La carte continuait de s'afficher
 * exactement comme avant. Un testeur a fait vingt kilometres dans le mauvais sens : il avait vu que son
 * repere avait disparu, s'etait dit que ce n'etait pas grave, et rien ne lui a appris que l'application
 * ne savait plus ou il etait.
 *
 * Le gabarit est celui de l'alerte d'eloignement, et volontairement : ce sont les deux seules choses que
 * la carte annonce d'elle-meme, et elles disent la meme categorie d'ennui - "ce que tu regardes ne
 * correspond plus a ce qui se passe". Elles n'apparaissent jamais ensemble, un suivi arrete n'ayant plus
 * d'ecart a mesurer.
 *
 * La banniere n'est pas le canal principal : elle attend qu'on rallume l'ecran, ce qui arrive apres les
 * vingt kilometres. C'est la notification du service qui previent sur le moment (cf. LocationService).
 * Celle-ci est ce qu'on trouve en revenant a la carte.
 */
@Composable
fun LocationNoticeBar(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier.fillMaxWidth().padding(8.dp)
            .background(LocationNoticeBackground, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(end = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.LocationOff, null, Modifier.size(20.dp), tint = Color.White)
            Text(
                text,
                style = MaterialTheme.typography.bodySmall, color = Color.White,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(16.dp), tint = Color.White)
        }
    }
}
