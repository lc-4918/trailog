package fr.lc4918.trailog.ui.points

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PropValue
import fr.lc4918.trailog.domain.model.SchemaItem
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField
import fr.lc4918.trailog.ui.components.FullscreenImageDialog
import fr.lc4918.trailog.ui.components.imageModel

/**
 * Formulaire d'édition de l'infobulle (popup ~80 % de l'écran).
 * Édition des valeurs texte, lien et image (source locale ou URL, épinglage en image de garde).
 */
@Composable
fun PropertyEditor(
    feature: PointFeature,
    schema: List<SchemaItem>,
    onSave: (PointFeature) -> Unit,
    onCancel: () -> Unit,
    onPickImage: ((String) -> Unit) -> Unit,
) {
    // copies éditables
    val texts = remember(feature.id) {
        mutableStateMapOf<String, String>().apply {
            feature.props.forEach { (k, v) -> if (v is PropValue.Text) put(k, v.value) }
        }
    }
    val linkTexts = remember(feature.id) {
        mutableStateMapOf<String, String>().apply {
            feature.props.forEach { (k, v) -> if (v is PropValue.Link) put(k, v.text) }
        }
    }
    val linkUrls = remember(feature.id) {
        mutableStateMapOf<String, String>().apply {
            feature.props.forEach { (k, v) -> if (v is PropValue.Link) put(k, v.url) }
        }
    }
    val imageSources = remember(feature.id) {
        mutableStateMapOf<String, String>().apply {
            feature.props.forEach { (k, v) -> if (v is PropValue.Image) put(k, v.path) }
        }
    }
    var pinnedKey by remember(feature.id) { mutableStateOf(feature.pinnedImageKey) }
    var enlargedSource by remember { mutableStateOf<String?>(null) }

    val orderedKeys = buildList {
        schema.forEach { if (feature.props.containsKey(it.key)) add(it.key) }
        feature.props.keys.forEach { if (it !in this) add(it) }
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.8f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(stringResource(R.string.dialog_edit_bubble_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    orderedKeys.forEach { key ->
                        val v = feature.props[key] ?: return@forEach
                        Text(key, style = MaterialTheme.typography.labelMedium)
                        when (v) {
                            is PropValue.Text -> CompactOutlinedTextField(
                                value = texts[key] ?: "", onValueChange = { texts[key] = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = false)
                            is PropValue.Link -> {
                                CompactOutlinedTextField(linkTexts[key] ?: "", { linkTexts[key] = it },
                                    label = { Text(stringResource(R.string.field_display_text)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                CompactOutlinedTextField(linkUrls[key] ?: "", { linkUrls[key] = it },
                                    label = { Text(stringResource(R.string.settings_field_url)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            }
                            is PropValue.Image -> {
                                val source = imageSources[key] ?: ""
                                val isPinned = pinnedKey == key
                                if (source.isNotBlank()) {
                                    Box(Modifier.fillMaxWidth()) {
                                        AsyncImage(model = imageModel(source), contentDescription = null,
                                            contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                                        Row(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                                            FilledIconButton(
                                                onClick = { pinnedKey = if (isPinned) null else key },
                                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.75f)),
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                    if (isPinned) stringResource(R.string.action_unpin_image) else stringResource(R.string.action_pin_image),
                                                    tint = Color.Black, modifier = Modifier.size(18.dp))
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            FilledIconButton(
                                                onClick = { enlargedSource = source },
                                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.75f)),
                                                modifier = Modifier.size(32.dp),
                                            ) { Icon(Icons.Filled.Fullscreen, stringResource(R.string.action_expand_image), tint = Color.Black, modifier = Modifier.size(18.dp)) }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                                OutlinedButton(onClick = { onPickImage { path -> imageSources[key] = path } }) {
                                    Text(stringResource(R.string.action_browse))
                                }
                                CompactOutlinedTextField(source, { imageSources[key] = it },
                                    label = { Text(stringResource(R.string.field_image_source)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val newProps = LinkedHashMap(feature.props)
                        texts.forEach { (k, v) -> newProps[k] = PropValue.Text(v) }
                        linkTexts.keys.forEach { k ->
                            newProps[k] = PropValue.Link(linkTexts[k] ?: "", linkUrls[k] ?: "")
                        }
                        imageSources.forEach { (k, v) -> newProps[k] = PropValue.Image(v) }
                        val finalPin = pinnedKey?.takeIf { (newProps[it] as? PropValue.Image)?.path?.isNotBlank() == true }
                        onSave(feature.copy(props = newProps, pinnedImageKey = finalPin))
                    }) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }

    enlargedSource?.let { src -> FullscreenImageDialog(src) { enlargedSource = null } }
}
