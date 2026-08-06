package fr.lc4918.trailog.ui.geocode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.ui.points.InfoBubbleWidth
import kotlinx.coroutines.launch

/**
 * Infobulle du lieu trouvé : son adresse, puis les deux mesures de distance possibles, une par ligne.
 * La mesure s'affiche à droite du bouton qui l'a demandée, et y reste.
 *
 * Les distances suivent la voirie : ce sont la longueur et la durée de l'itinéraire calculé pour la
 * discipline réglée, non un vol d'oiseau. Le "i" en exposant le dit, la valeur seule ne pouvant pas
 * l'exprimer - et un nombre de kilomètres près d'une carte se lit spontanément comme une distance de trajet.
 */
@Composable
fun GeocodeBubble(
    address: String,
    profileLabel: String,
    positionMeasure: MeasureState?,
    pointMeasure: MeasureState?,
    imperial: Boolean,
    onDistanceFromPosition: () -> Unit,
    onDistanceFromPoint: () -> Unit,
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
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
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
            MeasureRow(stringResource(R.string.geocode_distance_from_position), positionMeasure,
                profileLabel, imperial, fontSp, onDistanceFromPosition)
            MeasureRow(stringResource(R.string.geocode_distance_from_point), pointMeasure,
                profileLabel, imperial, fontSp, onDistanceFromPoint)
        }
    }
}

/** Une mesure : le bouton qui la déclenche, puis son résultat - attente, échec, ou distance et durée. */
@Composable
private fun MeasureRow(
    label: String,
    measure: MeasureState?,
    profileLabel: String,
    imperial: Boolean,
    fontSp: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            Text(label, fontSize = (fontSp - 2).sp, maxLines = 1)
        }
        when (measure) {
            null -> Unit
            MeasureState.Loading -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            MeasureState.Failed -> Text(stringResource(R.string.geocode_no_route),
                fontSize = (fontSp - 2).sp, color = MaterialTheme.colorScheme.error, maxLines = 1)
            is MeasureState.Done -> {
                Text(
                    stringResource(R.string.geocode_route_summary,
                        Format.shortDistance(measure.meters, imperial), Format.duration(measure.seconds)),
                    fontSize = fontSp.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1,
                )
                RouteInfoButton(profileLabel)
            }
        }
    }
}

/**
 * Le "i" en exposant qui explique la nature du nombre affiché : itinéraire recommandé pour la discipline
 * réglée. L'infobulle se pose au-dessus du point d'appui, ce que fait le placeur M3 par défaut.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteInfoButton(profileLabel: String) {
    val state = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringResource(R.string.geocode_route_info, profileLabel)) } },
        state = state,
    ) {
        // En exposant : remonté d'un tiers de sa hauteur, comme un appel de note derrière la valeur.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            IconButton(
                onClick = { scope.launch { state.show() } },
                modifier = Modifier.size(16.dp).offset(y = (-5).dp),
            ) {
                Icon(Icons.Outlined.Info, stringResource(R.string.content_desc_route_info),
                    modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
