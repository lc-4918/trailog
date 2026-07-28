package fr.lc4918.trailog.ui.points

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
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

    /**
     * Première ligne ajoutée (clé saisie par l'utilisateur) laissée sans nom mais remplie, dans l'ordre
     * d'affichage, ou null s'il n'y en a pas : sa clé étant vide, [build] l'écarterait en silence. On
     * refuse alors d'enregistrer et on renvoie l'utilisateur la nommer, une ligne après l'autre.
     */
    fun firstNamelessContentRow(): Long? = rows.firstOrNull { row ->
        row.fixedKey == null && keyOf(row).isBlank() && when (row.type) {
            PropType.TEXT -> texts[row.id].orEmpty().isNotBlank()
            PropType.IMAGE -> imageSources[row.id].orEmpty().isNotBlank()
            PropType.LINK -> linkTexts[row.id].orEmpty().isNotBlank() || linkUrls[row.id].orEmpty().isNotBlank()
        }
    }?.id

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
    // Ligne dont le nom vide bloque l'enregistrement : encadrée en erreur, centrée dans la vue, focalisée.
    // Le tick force le re-centrage même si c'est la même ligne qu'au clic précédent.
    var errorRow by remember(feature.id) { mutableStateOf<Long?>(null) }
    var errorTick by remember(feature.id) { mutableIntStateOf(0) }
    // De quoi centrer exactement le fautif : position/hauteur de la zone défilante et centre de chaque
    // ligne, tous mesurés en coordonnées fenêtre (onGloballyPositioned), plus le focus par ligne.
    val scrollState = rememberScrollState()
    var viewportTop by remember { mutableFloatStateOf(0f) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val rowCenters = remember(feature.id) { mutableStateMapOf<Long, Float>() }
    val nameFocusers = remember(feature.id) { mutableStateMapOf<Long, FocusRequester>() }

    // Centrage exact : on décale le scroll pour amener le centre de la ligne au centre de la zone visible.
    LaunchedEffect(errorTick) {
        if (errorTick == 0) return@LaunchedEffect
        val id = errorRow ?: return@LaunchedEffect
        val center = rowCenters[id]
        if (center != null && viewportHeight > 0) {
            val target = (scrollState.value + (center - (viewportTop + viewportHeight / 2f))).roundToInt()
            scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue))
        }
        runCatching { nameFocusers[id]?.requestFocus() }
    }

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
                Column(
                    Modifier.weight(1f)
                        .onGloballyPositioned { viewportTop = it.positionInWindow().y; viewportHeight = it.size.height }
                        .verticalScroll(scrollState),
                ) {
                    state.rows.forEach { row ->
                        key(row.id) {
                            // Un champ ajouté s'insère en bas, au-delà du bord visible : on amène toute sa
                            // ligne dans le cadre, pour que ses saisies soient là sans avoir à défiler.
                            val requester = remember { BringIntoViewRequester() }
                            val nameFocus = remember { FocusRequester() }
                            DisposableEffect(row.id) {
                                nameFocusers[row.id] = nameFocus
                                onDispose { nameFocusers.remove(row.id) }
                            }
                            // Nom vide : l'input passe en erreur tant qu'il n'est pas renseigné ; dès que
                            // l'utilisateur tape, l'erreur disparaît sans attendre un nouveau clic sur Enregistrer.
                            val showNameError = errorRow == row.id && state.keyNames[row.id].orEmpty().isBlank()
                            Column(
                                Modifier.bringIntoViewRequester(requester)
                                    .onGloballyPositioned { rowCenters[row.id] = it.positionInWindow().y + it.size.height / 2f },
                            ) {
                                FieldRow(row, state, nameFocus, showNameError,
                                    onDeleteField = { state.remove(row) },
                                    onPickImage = onPickImage, onEnlarge = { enlargedSource = it })
                            }
                            LaunchedEffect(justAdded) {
                                if (justAdded == row.id) {
                                    withFrameNanos { }      // la ligne vient d'être composée : la laisser se placer
                                    requester.bringIntoView()
                                    if (row.fixedKey == null) runCatching { nameFocus.requestFocus() }
                                    justAdded = null
                                }
                            }
                            // Champs ajoutés non encore enregistrés (clé libre) : séparation plus marquée entre eux.
                            if (row.fixedKey == null) {
                                HorizontalDivider(Modifier.padding(vertical = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant)
                            } else {
                                Spacer(Modifier.height(12.dp))
                            }
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
                    // Un champ rempli mais sans nom serait écarté en silence : on refuse d'enregistrer et on
                    // renvoie l'utilisateur au premier fautif (erreur + scroll + focus), un à un jusqu'au dernier.
                    Button(
                        onClick = {
                            val nameless = state.firstNamelessContentRow()
                            if (nameless != null) { errorRow = nameless; errorTick++ } else onSave(state.build(feature))
                        },
                        enabled = !state.hasInvalidUrl(),
                    ) { Text(stringResource(R.string.action_save)) }
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

/** Une propriété du formulaire : son libellé (ou son nom saisi, pour un champ ajouté) puis sa valeur.
 *  Un badge poubelle rouge, en haut à droite, supprime le champ du seul marqueur en cours d'édition. */
@Composable
private fun FieldRow(
    row: EditRow,
    state: EditorState,
    nameFocus: FocusRequester,
    showNameError: Boolean,
    onDeleteField: () -> Unit,
    onPickImage: ((String) -> Unit) -> Unit,
    onEnlarge: (String) -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            // En-tête : libellé (champ importé) ou saisie du nom (champ ajouté). Marge de droite réservée
            // au badge poubelle superposé, pour que le texte ne passe pas dessous.
            Row(Modifier.fillMaxWidth().padding(end = DeleteBadgeSize + 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (row.fixedKey != null) {
                    Text(fieldLabel(row.fixedKey), style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f))
                } else {
                    CompactOutlinedTextField(
                        value = state.keyNames[row.id].orEmpty(), onValueChange = { state.keyNames[row.id] = it },
                        placeholder = { Text(stringResource(R.string.field_name_hint)) },
                        isError = showNameError,
                        supportingText = if (showNameError) ({ Text(stringResource(R.string.field_name_required)) }) else null,
                        modifier = Modifier.weight(1f).focusRequester(nameFocus), singleLine = true,
                    )
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
                        // Le badge poubelle occupe le haut-droit du champ, là où l'image porte déjà pin et
                        // agrandir. On pousse l'image vers le bas d'un bouton minimum pour qu'ils ne se
                        // recouvrent pas, laissant cet écart entre la poubelle et le haut de pin/agrandir.
                        Spacer(Modifier.height(ImageDeleteGap))
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
        DeleteFieldBadge(onClick = onDeleteField, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/** Taille du badge poubelle posé en haut à droite d'un champ. */
private val DeleteBadgeSize = 28.dp
/** Champ photo : espace ajouté au-dessus de l'image (un bouton au minimum tactile) pour que le badge
 *  poubelle ne recouvre pas les boutons pin/agrandir portés par l'image. */
private val ImageDeleteGap = 48.dp

/** Badge poubelle rouge : supprime le champ du seul marqueur en cours d'édition (les autres marqueurs
 *  et le fichier importé ne sont pas touchés ; la suppression prend effet à l'enregistrement). */
@Composable
private fun DeleteFieldBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Neutralise le minimum tactile de 48dp, sans quoi le fond du badge s'étalerait bien au-delà de ses 28dp.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        FilledIconButton(
            onClick = onClick,
            modifier = modifier.size(DeleteBadgeSize),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.Filled.Delete, stringResource(R.string.action_remove_field), Modifier.size(OverlayIconSize))
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
