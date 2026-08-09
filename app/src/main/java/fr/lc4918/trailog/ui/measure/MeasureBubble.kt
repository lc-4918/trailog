package fr.lc4918.trailog.ui.measure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R

private val TailWidth = 16.dp
private val TailHeight = 8.dp

/** Rayon des coins de la bulle : la pointe ne doit pas s'y ancrer, elle y decollerait du bord droit. */
private val BubbleCorner = 16.dp

/**
 * Infobulle de la mesure : la longueur parcourue entre les deux points, et de quoi tout refermer.
 *
 * Sa pointe touche le parcours mesure ([tipX], [tipY], en px ecran), au plus pres de son milieu (cf.
 * [MeasureAnchor]) : c'est ce qui la relie a ce qu'elle chiffre, deux marqueurs noirs pouvant etre
 * distants de plusieurs ecrans de zoom.
 *
 * Le placement se fait en une seule passe de layout, avec les DEUX pointes composees et une seule posee :
 * la bulle va au-dessus du point quand la place le permet, sinon en dessous, et l'on ne sait de quel cote
 * qu'une fois sa hauteur mesuree. Les mesurer toutes deux coute deux triangles ; publier le cote dans un
 * etat aurait coute une premiere image avec la pointe du mauvais cote.
 */
@Composable
fun MeasureBubble(
    text: String,
    tipX: Int,
    tipY: Int,
    topInset: Int,
    margin: Int,
    onClose: () -> Unit,
    fontSp: Int = 14,
    backgroundAlpha: Float = 1f,
) {
    val background = MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha)
    val density = LocalDensity.current
    val cornerPx = with(density) { BubbleCorner.roundToPx() }
    Layout(
        content = {
            Box {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(BubbleCorner),
                    // Couleur de contenu imposee, comme l'infobulle d'un lieu : sous 100 % d'opacite le fond
                    // n'est plus une couleur du theme, et le texte heriterait du LocalContentColor ambiant.
                    colors = CardDefaults.cardColors(
                        containerColor = background, contentColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    // Marge de droite degagee pour la croix, posee par-dessus dans le coin.
                    Text(text, fontSize = fontSp.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 14.dp, end = 34.dp, top = 10.dp, bottom = 10.dp))
                }
                // Croix hors du padding du contenu : plaquee dans le coin haut-droit de la bulle.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    IconButton(onClick = onClose,
                        modifier = Modifier.align(Alignment.TopEnd).size(26.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(15.dp))
                    }
                }
            }
            Tail(background, pointingDown = true)
            Tail(background, pointingDown = false)
        },
    ) { measurables, cs ->
        val bubble = measurables[0].measure(cs.copy(minWidth = 0, minHeight = 0))
        val tailDown = measurables[1].measure(Constraints())
        val tailUp = measurables[2].measure(Constraints())
        val tailH = tailDown.height
        val above = tipY - tailH - bubble.height >= topInset + margin
        val y = if (above) tipY - tailH - bubble.height else tipY + tailH
        val maxX = (cs.maxWidth - margin - bubble.width).coerceAtLeast(margin)
        val x = (tipX - bubble.width / 2).coerceIn(margin.coerceAtMost(maxX), maxX)
        // La pointe suit le point vise tant qu'elle reste sous la partie droite de la bulle : rabattue au
        // bord, elle sortirait de l'arrondi et flotterait a cote du fond qu'elle prolonge.
        val tailMin = x + cornerPx
        val tailMax = (x + bubble.width - cornerPx - tailDown.width).coerceAtLeast(tailMin)
        val tailX = (tipX - tailDown.width / 2).coerceIn(tailMin, tailMax)
        layout(cs.maxWidth, cs.maxHeight) {
            bubble.place(x, y)
            if (above) tailDown.place(tailX, y + bubble.height) else tailUp.place(tailX, y - tailUp.height)
        }
    }
}

/** Pointe triangulaire de la bulle, du meme fond qu'elle : vers le bas sous une bulle posee au-dessus du
 *  point vise, vers le haut au-dessus d'une bulle posee en dessous. */
@Composable
private fun Tail(color: Color, pointingDown: Boolean) {
    Canvas(Modifier.size(TailWidth, TailHeight)) {
        val path = Path().apply {
            if (pointingDown) {
                moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height)
            } else {
                moveTo(0f, size.height); lineTo(size.width, size.height); lineTo(size.width / 2f, 0f)
            }
            close()
        }
        drawPath(path, color)
    }
}
