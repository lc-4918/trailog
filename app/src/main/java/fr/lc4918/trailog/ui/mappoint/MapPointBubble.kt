package fr.lc4918.trailog.ui.mappoint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.ui.geocode.AddressText
import fr.lc4918.trailog.ui.geocode.CloseCorner
import fr.lc4918.trailog.ui.points.InfoBubbleWidth
import kotlinx.coroutines.launch

/**
 * Infobulle du point designe par un appui long : son adresse, puis les deux mesures de distance possibles.
 * Chaque mesure s'affiche sous le bouton qui l'a demandee - a cote de lui quand la place le permet - et y
 * reste.
 *
 * L'adresse arrive apres coup (une requete de geocodage inverse), la ou l'infobulle d'un lieu cherche par
 * son nom la connait des le premier instant : ici, c'est le point qui est connu et l'adresse qui se cherche.
 *
 * Les distances suivent la voirie : ce sont la longueur et la duree de l'itineraire calcule pour la
 * discipline reglee, non un vol d'oiseau. Le "i" en exposant le dit, la valeur seule ne pouvant pas
 * l'exprimer - et un nombre de kilometres pres d'une carte se lit spontanement comme une distance de trajet.
 *
 * [showPositionRow] : la mesure depuis la position n'a de sens qu'avec le capteur allume. Ligne retiree
 * plutot que bouton grise, un bouton qui ne repond pas laissant croire a une panne.
 */
@Composable
fun MapPointBubble(
    address: AddressState,
    profileLabel: String,
    showPositionRow: Boolean,
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
        modifier = modifier.width(InfoBubbleWidth),   // meme largeur que l'infobulle d'un marqueur
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp),
        // Couleur de contenu imposee : sous 100 % d'opacite, le fond n'est plus l'une des couleurs du theme
        // et contentColorFor n'y reconnait rien, laissant le texte heriter du LocalContentColor ambiant.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        // Croix plaquee dans l'angle haut-droit, PAR-DESSUS le contenu et sans lui prendre de place :
        // posee dans la rangee de l'adresse, elle se centrait sur sa hauteur et descendait avec elle des
        // que l'adresse passait sur deux lignes ; en lui reservant sa largeur, elle tronquait des
        // adresses qui tenaient. Ce qu'elle recouvre, l'appui long le redit en entier.
        Box {
            Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
                AddressLine(address, fontSp, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp))
                if (showPositionRow) {
                    MeasureRow(stringResource(R.string.geocode_distance_from_position), positionMeasure,
                        profileLabel, imperial, fontSp, onDistanceFromPosition)
                }
                MeasureRow(stringResource(R.string.geocode_distance_from_point), pointMeasure,
                    profileLabel, imperial, fontSp, onDistanceFromPoint)
            }
            CloseCorner(onClose, Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 4.dp))
        }
    }
}

/**
 * L'adresse du point, ou ce qui en tient lieu tant qu'elle n'est pas connue.
 *
 * Les deux echecs sont dits differemment et en italique : "aucune adresse ici" est une reponse, "adresse
 * indisponible" un service muet.
 */
@Composable
private fun AddressLine(address: AddressState, fontSp: Int, modifier: Modifier) {
    when (address) {
        AddressState.Loading -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        }
        is AddressState.Done -> AddressText(address.lines, fontSp, modifier)
        else -> Text(
            stringResource(
                if (address == AddressState.NotFound) R.string.mappoint_address_none
                else R.string.mappoint_address_failed
            ),
            fontSize = fontSp.sp, fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier,
        )
    }
}

/**
 * Une mesure : le bouton qui la declenche, puis son resultat - attente, echec, ou distance et duree.
 *
 * Disposition qui **passe a la ligne**, et non une simple rangee : le libelle du bouton est long dans
 * plusieurs langues ("Distance depuis la position" en francais), et bouton, valeur et "i" totalisent plus
 * que la largeur d'une infobulle. En rangee, la duree se coupait au milieu ("11,2 km - 54") et le "i"
 * disparaissait - le nombre restait lisible, mais faux de moitie, et rien ne disait plus qu'il suit la
 * voirie. La valeur se replie donc sous son bouton quand elle n'a plus la place a cote.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeasureRow(
    label: String,
    measure: MeasureState?,
    profileLabel: String,
    imperial: Boolean,
    fontSp: Int,
    onClick: () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            Text(label, fontSize = (fontSp - 2).sp, maxLines = 1)
        }
        when (measure) {
            null -> Unit
            MeasureState.Loading -> CircularProgressIndicator(
                Modifier.align(Alignment.CenterVertically).size(14.dp), strokeWidth = 2.dp)
            MeasureState.Failed -> Text(stringResource(R.string.geocode_no_route),
                fontSize = (fontSp - 2).sp, color = MaterialTheme.colorScheme.error, maxLines = 1,
                modifier = Modifier.align(Alignment.CenterVertically))
            is MeasureState.Done -> {
                Text(
                    stringResource(R.string.geocode_route_summary,
                        Format.shortDistance(measure.meters, imperial), Format.duration(measure.seconds)),
                    fontSize = fontSp.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                RouteInfoButton(profileLabel, Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

/**
 * Le "i" en exposant qui explique la nature du nombre affiche : itineraire recommande pour la discipline
 * reglee. L'infobulle se pose au-dessus du point d'appui, ce que fait le placeur M3 par defaut.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteInfoButton(profileLabel: String, modifier: Modifier = Modifier) {
    val state = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringResource(R.string.geocode_route_info, profileLabel)) } },
        state = state,
        modifier = modifier,
    ) {
        // En exposant : remonte d'un tiers de sa hauteur, comme un appel de note derriere la valeur.
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
