package fr.lc4918.trailog.ui.points

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PropValue
import fr.lc4918.trailog.domain.model.SchemaItem
import fr.lc4918.trailog.ui.components.FullscreenImageDialog
import fr.lc4918.trailog.ui.components.imageModel

/** Infobulle en lecture : image de garde (si épinglée) au-dessus du titre, puis propriétés
 *  dans l'ordre du schéma (couche) puis propriétés propres au marqueur. */
@Composable
fun InfoBubble(
    feature: PointFeature,
    schema: List<SchemaItem>,
    modifier: Modifier = Modifier,
    fontSp: Int = 14,
    bold: Boolean = false,
    titleFontSp: Int = 14,
    titleBold: Boolean = true,
    // Hauteur max de l'infobulle : elle défile en interne au-delà, pour toujours tenir entièrement à l'écran.
    maxHeightDp: Dp = 400.dp,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    var enlarged by remember { mutableStateOf<String?>(null) }
    val pinnedImage = feature.pinnedImageKey?.let { feature.props[it] as? PropValue.Image }
    // Titre = propriété "name" si présente (sinon "Marqueur"). "name" n'est jamais affichée comme champ.
    val title = (feature.props[KEY_NAME] as? PropValue.Text)?.value?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.bubble_title_marker)
    val orderedKeys = buildList {
        schema.forEach { if (feature.props.containsKey(it.key)) add(it.key) }
        feature.props.keys.forEach { if (it !in this) add(it) }   // props propres au marqueur
    }.filter { it != KEY_NAME && !isHiddenKey(it) && (pinnedImage == null || it != feature.pinnedImageKey) }

    Card(
        modifier = modifier.width(280.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(BubbleRadius),
    ) {
        Column(Modifier.heightIn(max = maxHeightDp)) {
            // ---- en-tête fixe (toujours visible) : boutons + titre, superposés à l'image de garde si présente ----
            if (pinnedImage != null && pinnedImage.path.isNotBlank()) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = BubbleRadius, topEnd = BubbleRadius))) {
                    AsyncImage(model = imageModel(pinnedImage.path), contentDescription = null,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp))
                    // crayon niché dans l'angle haut-gauche : son arrondi frôle celui de l'infobulle
                    OverlayIconButton(Icons.Filled.Edit, R.string.action_edit, onEdit,
                        modifier = Modifier.align(Alignment.TopStart)
                            .padding(top = arcInset(BubbleRadius), start = arcInset(BubbleRadius)))
                    OverlayIconButton(Icons.Filled.Close, R.string.action_close, onClose, filled = false,
                        modifier = Modifier.align(Alignment.TopEnd).padding(OverlayInset))
                    // titre en bas à gauche, fond blanc opaque à 80 % sous le seul texte
                    Text(title, fontSize = titleFontSp.sp, fontWeight = if (titleBold) FontWeight.Bold else null,
                        color = Color.Black, maxLines = 2,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                    // agrandir : bords droit et bas contigus à ceux de l'image de garde, d'où le côté
                    // droit sans arrondi (les angles bas de l'image de garde sont droits eux aussi)
                    ExpandButton(onClick = { enlarged = pinnedImage.path },
                        modifier = Modifier.align(Alignment.BottomEnd), shape = OverlayShapeStart)
                }
            } else {
                // sans image : boutons en haut (crayon à gauche, X à droite), titre en dessous. Faute de
                // fond, le bouton se réduit à son icône : elle est donc taillée comme celle des boutons
                // superposés à l'image de garde, pour que les deux en-têtes s'équivalent. Même taille de
                // bouton, donc même neutralisation du minimum tactile : sans elle les 48dp imposés
                // tiendraient l'icône à 19dp du coin, hors de portée du retrait voulu.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    Row(Modifier.fillMaxWidth().padding(start = HeaderInset, end = HeaderInset, top = HeaderInset),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(OverlaySize)) {
                            Icon(Icons.Filled.Edit, stringResource(R.string.action_edit), Modifier.size(OverlayIconSize))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onClose, modifier = Modifier.size(OverlaySize)) {
                            Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(OverlayIconSize))
                        }
                    }
                }
                Text(title, fontSize = titleFontSp.sp, fontWeight = if (titleBold) FontWeight.Bold else null,
                    maxLines = 2, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = TitleTopInset))
            }
            // ---- propriétés : seule zone défilante ----
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 12.dp),
            ) {
                if (orderedKeys.isEmpty()) Text(stringResource(R.string.bubble_no_properties), style = MaterialTheme.typography.bodySmall)
                orderedKeys.forEachIndexed { i, key ->
                    val v = feature.props[key] ?: return@forEachIndexed
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Text(fieldLabel(key), fontSize = (fontSp - 3).sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    when (v) {
                        is PropValue.Text -> Text(fieldDisplayValue(key, v.value), fontSize = fontSp.sp, fontWeight = if (bold) FontWeight.Bold else null)
                        is PropValue.Link -> LinkChip(v.text.ifBlank { v.url }, v.url, fontSp)
                        is PropValue.Image -> ImageProp(v.path) { enlarged = v.path }
                    }
                }
            }
        }
    }

    enlarged?.let { src -> FullscreenImageDialog(src) { enlarged = null } }
}

/** Infobulle en attente de ses propriétés : même cadre que [InfoBubble] mais réduite au seul spinner
 *  (pas la pleine largeur, pas de boutons tant que le contenu n'est pas là). Affichée dès le tap à la
 *  position définie ; elle prend sa taille normale quand les données arrivent. */
@Composable
fun InfoBubbleLoading(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(BubbleRadius),
    ) {
        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun ImageProp(path: String, onEnlarge: () -> Unit) {
    if (path.isBlank()) {
        Text(stringResource(R.string.bubble_image_not_found), style = MaterialTheme.typography.bodySmall)
        return
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(ImageRadius))) {
        AsyncImage(model = imageModel(path), contentDescription = null,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp))
        // agrandir niché dans l'angle haut-droit : son arrondi frôle celui de l'image
        ExpandButton(onClick = onEnlarge, modifier = Modifier.align(Alignment.TopEnd)
            .padding(top = arcInset(ImageRadius), end = arcInset(ImageRadius)))
    }
}

/** Rayon des boutons superposés, aligné sur les miniatures des fonds composites (ThumbOverlayButton). */
private val OverlayRadius = 4.dp
private val OverlaySize = 32.dp
internal val OverlayIconSize = 18.dp
/** Retrait du bord de l'image. */
internal val OverlayInset = 4.dp
/** Rayons des conteneurs qui portent les boutons : l'infobulle et les images. [arcInset] en dépend. */
private val BubbleRadius = 16.dp
internal val ImageRadius = 12.dp

/** Halo du bouton sans fond : épaisseur du contour blanc tracé derrière l'icône, et son opacité
 *  (30 % de transparence). */
private val HaloWidth = 1.dp
private const val HaloAlpha = 0.7f
/** Huit directions à distance égale du centre (les diagonales ramenées à 1 par V2/2), de sorte que le
 *  contour garde partout la même épaisseur, y compris au bout des branches. */
private val HaloOffsets = run {
    val d = 0.70710678f
    listOf(
        -d to -d, 0f to -1f, d to -d,
        -1f to 0f, 1f to 0f,
        -d to d, 0f to 1f, d to d,
    )
}
/** Le milieu de l'arc d'un angle de rayon R est sur la diagonale, à R x (1 - 1/V2) du coin. */
private const val ArcMidRatio = 0.29289323f
/** Cheveu laissé entre les deux arcs pour qu'ils se frôlent sans se toucher tout à fait. */
private val ArcHair = 0.5.dp

/** Retrait posant le milieu de l'arc du bouton juste avant celui de l'angle qui le porte : les deux
 *  milieux étant sur la même diagonale, il reste (R - r) x (1 - 1/V2) entre eux, plus un cheveu. */
internal fun arcInset(containerRadius: Dp): Dp = (containerRadius - OverlayRadius) * ArcMidRatio + ArcHair

/** En-tête sans image de garde : l'icône n'ayant pas de fond, elle n'a pas d'arrondi propre à caler sur
 *  celui de l'infobulle comme le fait [arcInset]. On rapproche donc son angle du coin à mi-chemin du
 *  milieu de l'arc ; il en était à 19dp, un bouton au minimum tactile de 48dp retraité de 4dp. */
private val HeaderIconCorner = BubbleRadius * ArcMidRatio + (19.dp - BubbleRadius * ArcMidRatio) / 2
/** Retrait porté par la ligne : l'IconButton centrant son icône, sa demi-marge est déjà acquise. */
private val HeaderInset = HeaderIconCorner - (OverlaySize - OverlayIconSize) / 2

/** Écart voulu entre le bas de l'icône de l'en-tête et le haut du titre, sans image de garde. */
private val TitleGap = 30.dp
/** Retrait haut du titre : la ligne n'ayant pas de retrait bas, l'IconButton laisse déjà sa demi-marge
 *  sous son icône ; le titre ne porte donc que le reste de l'écart. */
private val TitleTopInset = TitleGap - (OverlaySize - OverlayIconSize) / 2
/** Bouton superposé isolé : arrondi de bouton sur les quatre coins. Les boutons se posent au centre
 *  horizontal, à l'écart des angles arrondis de l'infobulle, pour que ce rayon reste toujours intact. */
internal val OverlayShape = RoundedCornerShape(OverlayRadius)
/** Deux boutons accolés (épingle + agrandir) : le côté mitoyen reste droit, seul l'extérieur est arrondi.
 *  Ils se touchent sans se chevaucher (Row sans écart, chacun exactement [OverlaySize] de large). */
internal val OverlayShapeStart = RoundedCornerShape(topStart = OverlayRadius, bottomStart = OverlayRadius)
internal val OverlayShapeEnd = RoundedCornerShape(topEnd = OverlayRadius, bottomEnd = OverlayRadius)

/** Petit bouton d'action superposé à une image (crayon/agrandir/épingle) : fond blanc à 30 %, coins à 4dp,
 *  icône noire. [filled] à false ne laisse que l'icône, cernée d'un halo blanc pour la détacher de la
 *  photo faute de fond. */
@Composable
internal fun OverlayIconButton(
    icon: ImageVector,
    descRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = OverlayShape,
    filled: Boolean = true,
) {
    // Sans cette neutralisation, le minimumInteractiveComponentSize() interne d'IconButton impose 48dp :
    // le fond, dessiné à cette taille, déborde largement l'icône de 18dp au lieu de la cerner, et son
    // angle vient mordre l'arrondi de l'infobulle. Même neutralisation qu'ailleurs dans l'app (réglages,
    // légende, panneau de fonds), d'où le rendu compact des miniatures de fond composite.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(OverlaySize)
                .then(if (filled) Modifier.background(Color.White.copy(alpha = 0.3f), shape) else Modifier),
        ) {
            val desc = stringResource(descRes)
            if (!filled) {
                // Halo blanc : l'icône blanche retracée tout autour d'elle-même, pour la détacher de la
                // photo faute de fond. Ni blur() (API 31, ignoré en dessous alors que minSdk = 24) ni
                // agrandissement : ce dernier déplace chaque point proportionnellement à sa distance au
                // centre, d'où un halo nul au centre et débordant au bout des branches.
                // L'alpha est porté par le calque qui les réunit, pas par chaque copie : les copies se
                // chevauchent, et leurs alphas s'additionneraient en un halo opaque près du tracé.
                Box(Modifier.alpha(HaloAlpha), contentAlignment = Alignment.Center) {
                    HaloOffsets.forEach { (dx, dy) ->
                        Icon(icon, null, tint = Color.White,
                            modifier = Modifier.size(OverlayIconSize).offset(HaloWidth * dx, HaloWidth * dy))
                    }
                }
            }
            Icon(icon, desc, tint = Color.Black, modifier = Modifier.size(OverlayIconSize))
        }
    }
}

/** Bouton "Agrandir" superposé (coin d'une image). */
@Composable
private fun ExpandButton(onClick: () -> Unit, modifier: Modifier = Modifier, shape: Shape = OverlayShape) =
    OverlayIconButton(Icons.Filled.Fullscreen, R.string.action_expand_image, onClick, modifier, shape)

/** Lien en mise en forme moderne (pastille cliquable, pas un <a> souligné). */
@Composable
private fun LinkChip(label: String, url: String, fontSp: Int) {
    val ctx = LocalContext.current
    Surface(
        onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Link, null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = fontSp.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
