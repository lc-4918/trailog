package fr.lc4918.trailog.ui.points

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PropType
import fr.lc4918.trailog.domain.model.PropValue
import fr.lc4918.trailog.domain.model.SchemaItem
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField
import fr.lc4918.trailog.ui.components.FullscreenImageDialog
import fr.lc4918.trailog.ui.components.imageModel

/**
 * Une ligne du formulaire. Les valeurs sont indexées par [id] et non par clé de propriété : la clé d'un
 * champ ajouté ici est saisie par l'utilisateur, et la ré-indexer à chaque frappe ferait perdre le focus.
 * [fixedKey] non nul : propriété déjà présente, sa clé n'est pas modifiable et son libellé passe par
 * [fieldLabel]. Nul : champ ajouté, l'utilisateur en saisit le nom.
 */
private class EditRow(val id: Long, val type: PropType, val fixedKey: String?)

/** État éditable du formulaire, amorcé une fois par marqueur. */
private class EditorState(feature: PointFeature, schema: List<SchemaItem>) {
    val rows = mutableStateListOf<EditRow>()
    val keyNames = mutableStateMapOf<Long, String>()
    val texts = mutableStateMapOf<Long, String>()
    val linkTexts = mutableStateMapOf<Long, String>()
    val linkUrls = mutableStateMapOf<Long, String>()
    val imageSources = mutableStateMapOf<Long, String>()
    var pinnedRow by mutableStateOf<Long?>(null)
    private var nextId = 0L

    init {
        // Le titre d'abord : c'est la propriété "name", créée à la volée si le marqueur n'en avait pas.
        texts[add(PropType.TEXT, KEY_NAME)] = (feature.props[KEY_NAME] as? PropValue.Text)?.value.orEmpty()
        // Puis l'ordre du schéma de la couche, puis les propriétés propres au marqueur.
        val ordered = buildList {
            schema.forEach { if (feature.props.containsKey(it.key)) add(it.key) }
            feature.props.keys.forEach { if (it !in this) add(it) }
        }.filter { it != KEY_NAME && !isHiddenKey(it) }
        ordered.forEach { key ->
            when (val v = feature.props[key]) {
                is PropValue.Text -> texts[add(PropType.TEXT, key)] = v.value
                is PropValue.Link -> add(PropType.LINK, key).let { linkTexts[it] = v.text; linkUrls[it] = v.url }
                is PropValue.Image -> add(PropType.IMAGE, key).let {
                    imageSources[it] = v.path
                    if (feature.pinnedImageKey == key) pinnedRow = it
                }
                null -> Unit
            }
        }
    }

    fun add(type: PropType, fixedKey: String? = null): Long =
        nextId++.also { rows.add(EditRow(it, type, fixedKey)) }

    fun remove(row: EditRow) {
        rows.remove(row)
        if (pinnedRow == row.id) pinnedRow = null
    }

    /** Clé effective d'une ligne : la sienne, ou celle saisie pour un champ ajouté. */
    fun keyOf(row: EditRow): String = row.fixedKey ?: keyNames[row.id].orEmpty().trim()

    /** Une URL saisie mais mal formée bloque l'enregistrement ; vide, elle laisse juste un lien sans cible. */
    fun hasInvalidUrl(): Boolean = rows.any {
        it.type == PropType.LINK && linkUrls[it.id].orEmpty().let { u -> u.isNotBlank() && !isValidUrl(u) }
    }

    fun build(feature: PointFeature): PointFeature {
        val props = LinkedHashMap<String, PropValue>()
        rows.forEach { row ->
            val key = keyOf(row)
            if (key.isBlank() || isHiddenKey(key)) return@forEach
            when (row.type) {
                // Un titre vidé retire la propriété : l'infobulle retombe alors sur "Marqueur".
                PropType.TEXT -> texts[row.id].orEmpty().let {
                    if (key != KEY_NAME || it.isNotBlank()) props[key] = PropValue.Text(it)
                }
                PropType.LINK -> props[key] = PropValue.Link(linkTexts[row.id].orEmpty(), linkUrls[row.id].orEmpty())
                PropType.IMAGE -> props[key] = PropValue.Image(imageSources[row.id].orEmpty())
            }
        }
        // Les propriétés masquées ne sont pas éditées, mais elles doivent survivre à l'enregistrement.
        feature.props.forEach { (k, v) -> if (isHiddenKey(k)) props[k] = v }
        val pinned = rows.firstOrNull { it.id == pinnedRow }?.let { keyOf(it) }
            ?.takeIf { (props[it] as? PropValue.Image)?.path?.isNotBlank() == true }
        return feature.copy(props = props, pinnedImageKey = pinned)
    }
}

/**
 * Formulaire d'édition de l'infobulle (popup ~80 % de l'écran).
 * Titre, valeurs texte, lien et image (source locale ou URL, épinglage en image de garde), ajout de
 * champs et suppression du marqueur.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PropertyEditor(
    feature: PointFeature,
    schema: List<SchemaItem>,
    onSave: (PointFeature) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onPickImage: ((String) -> Unit) -> Unit,
) {
    val state = remember(feature.id) { EditorState(feature, schema) }
    var enlargedSource by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var justAdded by remember(feature.id) { mutableStateOf<Long?>(null) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.8f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                // Croix en haut à droite comme sur l'infobulle : fait double emploi avec "Annuler",
                // mais l'en-tête reste celui attendu d'un popup.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dialog_edit_bubble_title), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(OverlayIconSize))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    state.rows.forEach { row ->
                        key(row.id) {
                            // Un champ ajouté s'insère en bas, au-delà du bord visible : on amène toute sa
                            // ligne dans le cadre, pour que ses saisies soient là sans avoir à défiler.
                            val requester = remember { BringIntoViewRequester() }
                            Column(Modifier.bringIntoViewRequester(requester)) {
                                FieldRow(row, state, onPickImage, onEnlarge = { enlargedSource = it })
                            }
                            LaunchedEffect(justAdded) {
                                if (justAdded == row.id) {
                                    withFrameNanos { }      // la ligne vient d'être composée : la laisser se placer
                                    requester.bringIntoView()
                                    justAdded = null
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    AddFieldButton { type -> justAdded = state.add(type) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = { confirmDelete = true },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete_point)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(state.build(feature)) }, enabled = !state.hasInvalidUrl()) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }

    enlargedSource?.let { src -> FullscreenImageDialog(src) { enlargedSource = null } }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.dialog_delete_point_title)) },
            text = { Text(stringResource(R.string.dialog_delete_point_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Une propriété du formulaire : son libellé (ou son nom saisi, pour un champ ajouté) puis sa valeur. */
@Composable
private fun FieldRow(
    row: EditRow,
    state: EditorState,
    onPickImage: ((String) -> Unit) -> Unit,
    onEnlarge: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (row.fixedKey != null) {
            Text(fieldLabel(row.fixedKey), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        } else {
            CompactOutlinedTextField(
                value = state.keyNames[row.id].orEmpty(), onValueChange = { state.keyNames[row.id] = it },
                placeholder = { Text(stringResource(R.string.field_name_hint)) },
                modifier = Modifier.weight(1f), singleLine = true,
            )
            // Seuls les champs ajoutés ici se retirent : ceux du fichier importé restent, quitte à être vidés.
            IconButton(onClick = { state.remove(row) }) {
                Icon(Icons.Filled.Delete, stringResource(R.string.action_remove_field), Modifier.size(OverlayIconSize))
            }
        }
    }
    when (row.type) {
        PropType.TEXT -> {
            val unit = fieldUnit(row.fixedKey)
            CompactOutlinedTextField(
                value = state.texts[row.id].orEmpty(), onValueChange = { state.texts[row.id] = it },
                suffix = unit?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(), singleLine = row.fixedKey == KEY_NAME,
            )
        }
        PropType.LINK -> {
            val url = state.linkUrls[row.id].orEmpty()
            val bad = url.isNotBlank() && !isValidUrl(url)
            CompactOutlinedTextField(state.linkTexts[row.id].orEmpty(), { state.linkTexts[row.id] = it },
                label = { Text(stringResource(R.string.field_display_text)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            CompactOutlinedTextField(url, { state.linkUrls[row.id] = it },
                label = { Text(stringResource(R.string.settings_field_url)) },
                isError = bad,
                supportingText = if (bad) ({ Text(stringResource(R.string.error_invalid_url)) }) else null,
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        PropType.IMAGE -> {
            val source = state.imageSources[row.id].orEmpty()
            val isPinned = state.pinnedRow == row.id
            if (source.isNotBlank()) {
                // Mêmes boutons superposés que l'infobulle : épingle puis agrandir, accolés
                // (côté mitoyen droit), l'agrandir niché dans l'angle haut-droit de l'image.
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(ImageRadius))) {
                    AsyncImage(model = imageModel(source), contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp))
                    Row(Modifier.align(Alignment.TopEnd)
                        .padding(top = arcInset(ImageRadius), end = arcInset(ImageRadius))) {
                        OverlayIconButton(
                            icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            descRes = if (isPinned) R.string.action_unpin_image else R.string.action_pin_image,
                            onClick = { state.pinnedRow = if (isPinned) null else row.id },
                            shape = OverlayShapeStart,
                        )
                        OverlayIconButton(
                            icon = Icons.Filled.Fullscreen,
                            descRes = R.string.action_expand_image,
                            onClick = { onEnlarge(source) },
                            shape = OverlayShapeEnd,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            OutlinedButton(onClick = { onPickImage { path -> state.imageSources[row.id] = path } }) {
                Text(stringResource(R.string.action_browse))
            }
            CompactOutlinedTextField(source, { state.imageSources[row.id] = it },
                label = { Text(stringResource(R.string.field_image_source)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
    }
}

/** "+ Ajouter un champ" et son menu de type. */
@Composable
private fun AddFieldButton(onAdd: (PropType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Filled.Add, null, Modifier.size(OverlayIconSize))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.action_add_field))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                PropType.TEXT to R.string.field_type_text,
                PropType.LINK to R.string.field_type_link,
                PropType.IMAGE to R.string.field_type_image,
            ).forEach { (type, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = { open = false; onAdd(type) },
                )
            }
        }
    }
}
