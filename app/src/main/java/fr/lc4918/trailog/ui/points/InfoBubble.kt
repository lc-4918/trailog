package fr.lc4918.trailog.ui.points

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    var enlarged by remember { mutableStateOf<String?>(null) }
    val pinnedImage = feature.pinnedImageKey?.let { feature.props[it] as? PropValue.Image }
    val orderedKeys = buildList {
        schema.forEach { if (feature.props.containsKey(it.key)) add(it.key) }
        feature.props.keys.forEach { if (it !in this) add(it) }   // props propres au marqueur
    }.filter { pinnedImage == null || it != feature.pinnedImageKey }

    Card(
        modifier = modifier.width(280.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            if (pinnedImage != null && pinnedImage.path.isNotBlank()) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))) {
                    AsyncImage(model = imageModel(pinnedImage.path), contentDescription = null,
                        contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                    ExpandButton(onClick = { enlarged = pinnedImage.path }, modifier = Modifier.align(Alignment.TopEnd))
                }
            }
            Column(Modifier.padding(12.dp).heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.bubble_title_marker), fontSize = titleFontSp.sp,
                        fontWeight = if (titleBold) FontWeight.Bold else null, modifier = Modifier.weight(1f))
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.action_edit)) }
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
                }
                if (orderedKeys.isEmpty()) Text(stringResource(R.string.bubble_no_properties), style = MaterialTheme.typography.bodySmall)
                orderedKeys.forEach { key ->
                    val v = feature.props[key] ?: return@forEach
                    Spacer(Modifier.height(8.dp))
                    Text(key, fontSize = (fontSp - 3).sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    when (v) {
                        is PropValue.Text -> Text(v.value, fontSize = fontSp.sp, fontWeight = if (bold) FontWeight.Bold else null)
                        is PropValue.Link -> LinkChip(v.text.ifBlank { v.url }, v.url, fontSp)
                        is PropValue.Image -> ImageProp(v.path) { enlarged = v.path }
                    }
                }
            }
        }
    }

    enlarged?.let { src -> FullscreenImageDialog(src) { enlarged = null } }
}

@Composable
private fun ImageProp(path: String, onEnlarge: () -> Unit) {
    if (path.isBlank()) {
        Text(stringResource(R.string.bubble_image_not_found), style = MaterialTheme.typography.bodySmall)
        return
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
        AsyncImage(model = imageModel(path), contentDescription = null,
            contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
        ExpandButton(onClick = onEnlarge, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/** Bouton "Agrandir" superposé, fond blanc 75 % transparent (coin haut-droite d'une image). */
@Composable
private fun ExpandButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.padding(6.dp).size(32.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.75f)),
    ) { Icon(Icons.Filled.Fullscreen, stringResource(R.string.action_expand_image), tint = Color.Black, modifier = Modifier.size(18.dp)) }
}

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
