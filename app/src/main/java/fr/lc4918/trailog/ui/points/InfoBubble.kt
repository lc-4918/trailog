package fr.lc4918.trailog.ui.points

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import fr.lc4918.trailog.ui.theme.Spacing
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import fr.lc4918.trailog.R
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PropValue
import fr.lc4918.trailog.domain.model.SchemaItem
import fr.lc4918.trailog.ui.components.FullscreenImageDialog
import fr.lc4918.trailog.ui.components.imageModel

/**
 * Infobulle en lecture : image de garde (si épinglée) au-dessus du titre, puis propriétés dans l'ordre du
 * schéma (couche) puis propriétés propres au marqueur.
 *
 * **Le premier lien du point est son titre.** Souligné et cliquable, comme le nom d'un point d'intérêt qui
 * publie un site : c'est là qu'on le cherche, et il n'occupe plus une ligne de la bulle. Les liens
 * suivants gardent leur pastille, sous leur libellé.
 *
 * Les libellés des champs sont en petites capitales grises : c'est la valeur qu'on lit, le libellé ne fait
 * que la nommer. Le bleu reste à ce qui se touche.
 */
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
    // Opacité du fond de l'infobulle (0f..1f) : seul le fond devient translucide, le contenu reste opaque.
    backgroundAlpha: Float = 1f,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    var enlarged by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val pinnedImage = feature.pinnedImageKey?.let { feature.props[it] as? PropValue.Image }
    // Titre = propriété "name" si présente (sinon "Marqueur"). "name" n'est jamais affichée comme champ.
    val title = (feature.props[KEY_NAME] as? PropValue.Text)?.value?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.bubble_title_marker)
    val shownKeys = buildList {
        schema.forEach { if (feature.props.containsKey(it.key)) add(it.key) }
        feature.props.keys.forEach { if (it !in this) add(it) }   // props propres au marqueur
    }.filter { it != KEY_NAME && !isHiddenKey(it) && (pinnedImage == null || it != feature.pinnedImageKey) }
    val (titleLink, orderedKeys) = titleLinkOf(feature.props, shownKeys)
    val openTitle: (() -> Unit)? = titleLink?.let { l -> { openLink(ctx, l.url) } }

    Card(
        modifier = modifier.width(InfoBubbleWidth),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = MaterialTheme.shapes.large,
        // Couleur de contenu imposée. Sous 100 % d'opacité, le fond n'est plus l'une des couleurs du thème :
        // contentColorFor n'y reconnaît rien et rend Color.Unspecified, laissant le texte hériter du
        // LocalContentColor ambiant. À 100 %, copy(alpha = 1f) rend une couleur identique à surface, qui
        // retrouve donc son onSurface - d'où un défaut invisible tant qu'on ne baisse pas l'opacité.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.heightIn(max = maxHeightDp)) {
            // ---- en-tête fixe (toujours visible) : boutons + titre, superposés à l'image de garde si présente ----
            if (pinnedImage != null && pinnedImage.path.isNotBlank()) {
                val painter = rememberAsyncImagePainter(imageModel(pinnedImage.path))
                val fillsWidth = rememberPinnedImageFillsWidth(pinnedImage.path, painter)
                Box(Modifier.fillMaxWidth().clip(topCorners(MaterialTheme.shapes.large))) {
                    Image(painter = painter, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = BubbleImageMaxHeight.dp))
                    if (fillsWidth) {
                        // Crayon et croix posés sur la photo, dans ses deux angles hauts.
                        OverlayIconButton(Icons.Outlined.Edit, R.string.action_edit, onEdit,
                            modifier = Modifier.align(Alignment.TopStart).padding(OverlayInset))
                        OverlayIconButton(Icons.Filled.Close, R.string.action_close, onClose,
                            modifier = Modifier.align(Alignment.TopEnd).padding(OverlayInset))
                    } else {
                        // Image trop étroite : les boutons tombent sur les bandes vides, hors de la photo.
                        // Ils prennent alors le style d'une infobulle sans image de garde (icône seule).
                        PlainHeaderButtons(onEdit, onClose)
                    }
                    // Au bas de la photo, sur une même ligne : le titre à gauche, sur son fond blanc à 85 %
                    // (un texte sans fond disparaît sur un cliché clair), et l'agrandissement contre le bord
                    // droit.
                    //
                    // Le titre prend TOUTE la largeur que le bouton lui laisse. Il la partageait auparavant a
                    // parts egales avec un espace vide : un nom long se coupait a mi-largeur, et un nom court
                    // laissait sa moitie inemployee, ce qui repoussait le bouton vers le milieu de la photo.
                    Row(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .padding(start = Spacing.s, end = OverlayInset, bottom = OverlayInset),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f).testTag("bubble_cover_title_area")) {
                            Text(title, fontSize = titleFontSp.sp, fontWeight = if (titleBold) FontWeight.Bold else null,
                                color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textDecoration = if (openTitle != null) TextDecoration.Underline else null,
                                modifier = Modifier.testTag("bubble_cover_title")
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(Color.White.copy(alpha = 0.85f))
                                    .then(if (openTitle != null) Modifier.clickable(onClick = openTitle) else Modifier)
                                    .padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.width(Spacing.s))
                        OverlayIconButton(Icons.Filled.Fullscreen, R.string.action_expand_image,
                            onClick = { enlarged = pinnedImage.path })
                    }
                }
            } else {
                // sans image : boutons en haut (crayon à gauche, X à droite), titre en dessous.
                PlainHeaderButtons(onEdit, onClose)
                Text(title, fontSize = titleFontSp.sp, fontWeight = if (titleBold) FontWeight.Bold else null,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (openTitle != null) TextDecoration.Underline else null,
                    modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = 2.dp)
                        .then(if (openTitle != null) Modifier.clickable(onClick = openTitle) else Modifier))
            }
            // ---- propriétés : seule zone défilante ----
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.l).padding(top = Spacing.m, bottom = Spacing.l),
            ) {
                if (orderedKeys.isEmpty()) Text(stringResource(R.string.bubble_no_properties), style = MaterialTheme.typography.bodySmall)
                orderedKeys.forEachIndexed { i, key ->
                    val v = feature.props[key] ?: return@forEachIndexed
                    if (i > 0) Spacer(Modifier.height(Spacing.m))
                    // Petites capitales grises, a la taille reglee moins trois points : le libelle nomme la
                    // valeur, il ne se lit pas a sa place.
                    Text(fieldLabel(key).uppercase(), fontSize = (fontSp - 3).sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.06.em, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
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

/**
 * Le lien qui devient titre - le premier lien du point, dans l'ordre d'affichage - et les champs qui
 * restent a montrer sans lui. Sans lien, le titre n'en porte pas et tous les champs restent.
 */
internal fun titleLinkOf(props: Map<String, PropValue>, keys: List<String>): Pair<PropValue.Link?, List<String>> {
    val key = keys.firstOrNull { (props[it] as? PropValue.Link)?.url?.isNotBlank() == true }
        ?: return null to keys
    return props[key] as PropValue.Link to keys.filter { it != key }
}

/** Ouvre un lien dans le navigateur. Avale l'echec : sans navigateur, il n'y a rien a ouvrir. */
private fun openLink(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}

/** Les deux coins hauts d'une forme arrondie, les deux bas droits : une image posee en tete d'une bulle. */
private fun topCorners(shape: CornerBasedShape) = shape.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0))

/** Infobulle en attente de ses propriétés : même cadre que [InfoBubble] mais réduite au seul spinner
 *  (pas la pleine largeur, pas de boutons tant que le contenu n'est pas là). Affichée dès le tap à la
 *  position définie ; elle prend sa taille normale quand les données arrivent. */
@Composable
fun InfoBubbleLoading(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

/**
 * L'image de garde (ContentScale.Fit) remplit-elle la largeur ? Oui tant que son ratio est au moins
 * aussi large que le cadre (largeur / hauteur max) ; plus étroite (portrait), elle laisse des bandes
 * vides sur les côtés. Pour une image locale on lit ses dimensions tout de suite (en-tête seul, sans
 * allouer le bitmap) afin d'éviter tout clignotement de style au retour d'édition ; pour une URL on
 * retombe sur la taille que Coil finit par connaître (résolue de façon asynchrone).
 */
@Composable
private fun rememberPinnedImageFillsWidth(path: String, painter: AsyncImagePainter): Boolean {
    val localAspect = remember(path) {
        if (path.startsWith("http://") || path.startsWith("https://")) null
        else runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else null
        }.getOrNull()
    }
    val aspect = localAspect ?: painter.intrinsicSize.let {
        if (it.isSpecified && it.height > 0f) it.width / it.height else null
    }
    return aspect == null || aspect >= InfoBubbleWidth.value / BubbleImageMaxHeight
}

/** En-tete sans image de garde : crayon a gauche, croix a droite, en cibles rondes de 32 dp. */
@Composable
private fun PlainHeaderButtons(onEdit: () -> Unit, onClose: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(Modifier.fillMaxWidth().padding(start = OverlayInset, end = OverlayInset, top = OverlayInset),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onEdit, modifier = Modifier.size(OverlaySize)) {
                Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit), Modifier.size(OverlayIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(OverlaySize)) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(OverlayIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ImageProp(path: String, onEnlarge: () -> Unit) {
    if (path.isBlank()) {
        Text(stringResource(R.string.bubble_image_not_found), style = MaterialTheme.typography.bodySmall)
        return
    }
    Box(Modifier.padding(top = 2.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium)) {
        AsyncImage(model = imageModel(path), contentDescription = null,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp))
        OverlayIconButton(Icons.Filled.Fullscreen, R.string.action_expand_image, onEnlarge,
            modifier = Modifier.align(Alignment.TopEnd).padding(OverlayInset))
    }
}

/** Cible d'un bouton pose sur une photo, et son dessin. */
internal val OverlaySize = 32.dp
internal val OverlayIconSize = 20.dp
/** Retrait du bord de l'image ou de la bulle. */
internal val OverlayInset = 6.dp
/** Largeur de l'infobulle. Exposée hors du composable : l'infobulle de géocodage s'y aligne plutôt que de
 *  redéclarer la sienne, les deux devant faire le même bloc au-dessus de la carte. */
internal val InfoBubbleWidth = 280.dp
/** Hauteur max de l'image de garde (en dp) ; son ratio avec la largeur de la bulle dit si l'image remplit
 *  la largeur ou laisse des bandes vides sur les côtés. */
private const val BubbleImageMaxHeight = 220f

/**
 * Un bouton pose sur une photo : un rond sombre translucide, l'icone en blanc.
 *
 * Il remplace le petit carre blanc a icone noire cerclee d'un halo : le rond sombre se lit aussi bien sur
 * un cliche clair que sur un cliche sombre, sans halo a dessiner, et il a la forme des autres boutons de
 * la bulle.
 */
@Composable
internal fun OverlayIconButton(
    icon: ImageVector,
    descRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(OverlaySize).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
        ) {
            Icon(icon, stringResource(descRes), tint = Color.White, modifier = Modifier.size(OverlayIconSize))
        }
    }
}

/** Un lien qui n'est pas le titre : une pastille teintee, son icone et son texte. */
@Composable
private fun LinkChip(label: String, url: String, fontSp: Int) {
    val ctx = LocalContext.current
    Surface(
        // Avale, et c'est le seul de son espece : la pastille est enfouie a quatre composables prives de
        // la bulle, et lui donner une voix demanderait de faire descendre un rappel jusqu'ici a travers
        // tout l'editeur de proprietes. Le seul echec possible est l'absence de navigateur, que la meme
        // action dit deja depuis l'infobulle d'un point d'interet (cf. MapBubbles).
        onClick = { openLink(ctx, url) },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Link, null, modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = fontSp.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
