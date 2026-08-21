package fr.lc4918.trailog.ui.geocode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.ui.points.InfoBubbleWidth

/**
 * Infobulle du lieu trouvé : son adresse, ce qu'on peut en faire, et de quoi la fermer.
 *
 * Elle a porté des mesures de distance et le profil de l'itinéraire jusqu'au lieu ; c'est le
 * planificateur qui s'en charge (cf. `ui/planner`). Ce qui reste correspond à ce que le géocodage sait
 * vraiment dire : où se trouve l'adresse cherchée - et les **trois actions d'itinéraire**, qui mènent
 * justement au planificateur. Chercher un lieu pour aller quelque part est le geste le plus naturel qui
 * soit ; l'infobulle qui le trouvait ne savait pourtant rien en faire, et il fallait rouvrir le
 * planificateur pour retaper ce qu'on avait sous les yeux.
 *
 * L'adresse arrive en morceaux - intitulé, voie, commune (cf. [AddressText]) : trop longue pour la
 * largeur de la bulle, elle se coupe entre eux plutôt que d'être tronquée.
 */
@Composable
fun GeocodeBubble(
    lines: List<String>,
    onSetStart: () -> Unit,
    onSetEnd: () -> Unit,
    onAddStep: () -> Unit,
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
        // Croix plaquée dans l'angle haut-droit, PAR-DESSUS l'adresse et sans lui prendre de place :
        // au fil du texte, elle se centrait sur la hauteur de l'adresse et descendait avec elle dès son
        // passage sur deux lignes ; en lui réservant sa largeur, elle tronquait des adresses qui
        // tenaient. Ce qu'elle recouvre, l'appui long le redit en entier.
        Box {
            Column {
                AddressText(lines, fontSp,
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp))
                RouteActions(onSetStart, onSetEnd, onAddStep, fontSp,
                    Modifier.padding(start = 12.dp, end = 8.dp, bottom = 8.dp))
            }
            CloseCorner(onClose, Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 4.dp))
        }
    }
}
