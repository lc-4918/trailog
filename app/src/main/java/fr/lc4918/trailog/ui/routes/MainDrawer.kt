package fr.lc4918.trailog.ui.routes

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.toColorInt
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.ui.components.ColorPickerDialog
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField
import fr.lc4918.trailog.ui.components.tintedFieldColors
import fr.lc4918.trailog.routing.GpxWriter
import fr.lc4918.trailog.ui.settings.ProvideSettingsPalette
import fr.lc4918.trailog.ui.settings.settingsPalette
import fr.lc4918.trailog.ui.settings.ColumnScopeMarker
import fr.lc4918.trailog.ui.settings.SettingsCard
import fr.lc4918.trailog.ui.settings.SetRow
import fr.lc4918.trailog.ui.settings.RowDivider
import fr.lc4918.trailog.ui.settings.ValueText
import fr.lc4918.trailog.ui.theme.Spacing
import fr.lc4918.trailog.ui.theme.TrailogIcons
import fr.lc4918.trailog.ui.theme.isDarkTheme
import fr.lc4918.trailog.domain.model.LayerWays
import fr.lc4918.trailog.routing.TraceMatch
import fr.lc4918.trailog.ui.planner.DetailsRubrics
import fr.lc4918.trailog.ui.planner.ElevationRubric
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.ui.planner.PlannerViewer
import kotlinx.coroutines.launch

/**
 * Le menu lateral : l'arborescence des dossiers et des couches, ses lignes, son glisser-deposer.
 *
 * Sorti de `MainScreen`, ou il tenait sept cents lignes au milieu de la carte, du profil et des
 * infobulles. Rien n'y a change : ce sont les memes composables, appeles au meme endroit. Ce qui change
 * est qu'on peut desormais lire une ligne de l'arbre sans traverser tout le reste.
 */

/** Zone de dépose visée dans la ligne survolée. */
internal enum class HoverZone { BEFORE, INTO, AFTER }

/** État d'un drag en cours : type/id de l'item déplacé et l'écart cumulé (non borné) depuis le début. */
internal data class DragInfo(val kind: String, val id: Long, val offset: Float)

/** Ligne actuellement survolée et zone visée dedans. */
internal data class HoverTarget(val kind: String, val id: Long, val zone: HoverZone)

/** Contexte partagé transmis à tout l'arbre pour le drag & drop (positions des lignes, item en cours de drag, cible de dépose). */
internal class DragCtx(
    val rowBounds: MutableMap<Pair<String, Long>, Float>,
    val dragInfo: DragInfo?,
    val hoverTarget: HoverTarget?,
    val onStart: (String, Long) -> Unit,
    val onDrag: (String, Long, Float) -> Unit,
    val onEnd: (String, Long) -> Unit,
)

/** Vrai si `candidateId` est un descendant (direct ou indirect) de `ancestorId`, pour éviter les cycles au drop. */
internal fun isDescendantFolder(candidateId: Long, ancestorId: Long, folders: List<FolderEntity>): Boolean {
    var cur = folders.firstOrNull { it.id == candidateId }?.parentId
    while (cur != null) {
        if (cur == ancestorId) return true
        cur = folders.firstOrNull { it.id == cur }?.parentId
    }
    return false
}

internal fun parentIdOf(
    kind: String, id: Long, folders: List<FolderEntity>, layers: List<LayerEntity>,
): Long? = when (kind) {
    "folder" -> folders.firstOrNull { it.id == id }?.parentId
    else -> layers.firstOrNull { it.id == id }?.folderId
}

/** Fusionne dossiers + couches d'un même parent en une seule liste triée par ordre unifié. */
internal fun combinedChildren(parentId: Long?, folders: List<FolderEntity>, layers: List<LayerEntity>): List<Any> {
    val f = folders.filter { it.parentId == parentId }
    val l = layers.filter { it.folderId == parentId }
    fun order(e: Any): Int = when (e) { is FolderEntity -> e.sortOrder; is LayerEntity -> e.sortOrder; else -> 0 }
    fun typeRank(e: Any): Int = if (e is FolderEntity) 0 else 1
    fun idOf(e: Any): Long = when (e) { is FolderEntity -> e.id; is LayerEntity -> e.id; else -> 0L }
    return (f + l).sortedWith(compareBy({ order(it) }, { typeRank(it) }, { idOf(it) }))
}

/** Vibration plus marquée que le retour haptique système par défaut, pour le démarrage d'un drag. */
internal fun strongHaptic(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createOneShot(50, 200))
    } else {
        @Suppress("DEPRECATION") vibrator?.vibrate(50)
    }
}

internal fun applyMove(vm: MainViewModel, kind: String, id: Long, target: Long?) {
    when (kind) { "folder" -> vm.moveFolder(id, target); "layer" -> vm.moveLayer(id, target) }
}

@Composable
internal fun FolderNode(
    folder: FolderEntity, allFolders: List<FolderEntity>, allLayers: List<LayerEntity>,
    depth: Int, vm: MainViewModel, dctx: DragCtx,
    onRename: (String, Long, String) -> Unit, onMove: (String, Long) -> Unit, onNewFolder: (Long?) -> Unit, onZoom: (String, Long) -> Unit,
    importingIds: ImportSpinners, layerActions: LayerActions,
    onStats: (FolderEntity) -> Unit, onDeleteFolder: (FolderEntity) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    var showColor by remember { mutableStateOf(false) }
    // Les couches sur lesquelles portent les actions du dossier : sous-dossiers compris, comme la
    // suppression et l'oeil. Calculees une fois, l'oeil et le choix de couleur en repondent tous deux.
    val contents = layersUnder(folder.id, allFolders, allLayers)
    val context = LocalContext.current
    val isDragging = dctx.dragInfo?.kind == "folder" && dctx.dragInfo.id == folder.id
    val offset = if (isDragging) dctx.dragInfo.offset else 0f
    val hoverZone = dctx.hoverTarget?.takeIf { it.kind == "folder" && it.id == folder.id }?.zone

    if (hoverZone == HoverZone.BEFORE) DropIndicatorLine()
    DrawerRow(
        depth = depth, dragging = isDragging, offset = offset, hovered = hoverZone == HoverZone.INTO,
        onPositioned = { dctx.rowBounds["folder" to folder.id] = it },
    ) {
        val allVisible = contents.isEmpty() || contents.all { it.visible }
        val selection = LocalDrawerSelection.current
        val vibre = rememberSelectionHaptic()
        DrawerIcon(
            if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
            if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
            size = DrawerChevronSize, onClick = { expanded = !expanded },
        )
        // En selection, la case prend la place de l'oeil : meme colonne, meme cible.
        if (selection.active) {
            RowCheckbox(selection.isSelected("folder" to folder.id), { selection.toggle("folder" to folder.id) },
                DrawerHitSize, "select_folder_${folder.id}")
        } else DrawerIcon(
            if (allVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            if (allVisible) stringResource(R.string.action_hide_folder) else stringResource(R.string.action_show_folder),
            onClick = { vm.setFolderVisible(folder.id, !allVisible) },
        )
        // Couleur adaptée au thème (clair/sombre), pas figée : contour (fermé) ou remplissage (ouvert)
        // noir en thème clair, blanc en thème sombre (bug 4.1). Même silhouette pleine (Filled.Folder) dans
        // les deux états : Filled.FolderOpen ne remplit que l'onglet arrière, pas tout le dossier.
        Icon(if (expanded) Icons.Filled.Folder else Icons.Outlined.Folder, null,
            Modifier.size(DrawerIconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        // Nom d'un dossier : demi-gras. Il ne porte pas de couleur, contrairement a une couche, et n'a que
        // sa graisse pour se distinguer de ce qu'il contient. Plus de capitales : elles criaient, et
        // allongeaient les noms au point de les couper plus tot que ceux des couches.
        RowLabel(folder.name, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurface,
            FontWeight.SemiBold, onLongPress = { vibre(); selection.start("folder" to folder.id) },
            modifier = Modifier.weight(1f))
        // Nombre de couches sous le dossier, sous-dossiers compris : c'est ce que ses actions touchent
        // (l'oeil, la couleur commune), et ce qu'un dossier replie cache.
        if (contents.isNotEmpty()) {
            Text("${contents.size}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                    .padding(horizontal = 7.dp, vertical = 2.dp))
        }
        // Poignee et menu colles : ce sont les deux prises de la ligne, pas deux elements a distinguer.
        // L'ecart de la ligne les separerait autant que le nom du compteur, qui n'ont rien a voir entre eux.
        // En selection, ils s'effacent : on coche, on ne range ni ne retouche.
        if (!selection.active) RowEndActions {
            DragHandle(
                onStart = { strongHaptic(context); dctx.onStart("folder", folder.id) },
                onDrag = { dctx.onDrag("folder", folder.id, it) },
                onEnd = { dctx.onEnd("folder", folder.id) })
            RowMenu(onRename = { onRename("folder", folder.id, folder.name) }, onMove = { onMove("folder", folder.id) },
                onNewSub = { onNewFolder(folder.id) }, onDelete = { onDeleteFolder(folder) },
                onZoom = { onZoom("folder", folder.id) },
                onColor = if (contents.isEmpty()) null else ({ showColor = true }),
                onStats = { onStats(folder) })
        }
    }
    if (hoverZone == HoverZone.AFTER) DropIndicatorLine()
    // Coche posee sur la couleur commune aux couches du dossier, s'il y en a une : autrement elles sont de
    // plusieurs couleurs, et en designer une comme "celle du dossier" serait faux.
    if (showColor) {
        ColorPickerDialog(
            current = contents.map { it.color }.distinct().singleOrNull() ?: "",
            onPick = { vm.setFolderColor(folder.id, it); showColor = false },
            onDismiss = { showColor = false },
        )
    }
    if (expanded) {
        // Spinner d'import entre la ligne du dossier et sa première couche (SPEC).
        importingIds.state(folder.id)?.let { ImportSpinnerRow(depth + 1, it) }
        combinedChildren(folder.id, allFolders, allLayers).forEach { item ->
            when (item) {
                is FolderEntity -> key("folder", item.id) {
                    FolderNode(item, allFolders, allLayers, depth + 1, vm, dctx, onRename, onMove, onNewFolder, onZoom,
                        importingIds, layerActions, onStats, onDeleteFolder)
                }
                is LayerEntity -> key("layer", item.id) { LayerRow(item, depth + 1, vm, dctx, onRename, onZoom, layerActions) }
            }
        }
    }
}

/** Ligne « import en cours » : petit spinner, indenté comme les couches du dossier. */
@Composable
internal fun ImportSpinnerRow(depth: Int, elevation: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(DrawerRowHeight)
            .padding(start = DrawerRowPadH + DrawerIndent * depth + DrawerHitSize + DrawerRowGap + 7.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        // Le libelle dit CE QU'ON ATTEND, et non ce qu'on a demande : l'altimetrie interroge deux services
        // distants et peut durer, la ou le reste de l'import ne tient qu'a la machine. Sans cette
        // distinction, un import qui semble bloque n'est qu'un import qui attend le reseau.
        Text(
            stringResource(if (elevation) R.string.elevation_in_progress else R.string.import_in_progress),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Dossiers dont un import est en cours, et ceux dont l'import en est au calcul de l'altimetrie.
 *
 * Un porteur plutot que deux ensembles traverses cote a cote : l'arborescence les passe de dossier en
 * dossier sur toute sa profondeur, et le second aurait suivi le premier a chaque appel.
 */
class ImportSpinners(private val importing: Set<Long?>, private val elevating: Set<Long?>) {
    /** Null quand ce dossier n'attend rien ; sinon, vrai si ce qu'il attend est l'altimetrie. */
    fun state(folderId: Long?): Boolean? =
        if (folderId !in importing) null else folderId in elevating
}

/** Icône globe si la couche a des points ET des lignes, ligne (trace) si lignes seules, sinon point. */
@Composable
internal fun LayerRow(
    layer: LayerEntity, depth: Int, vm: MainViewModel, dctx: DragCtx,
    onRename: (String, Long, String) -> Unit, onZoom: (String, Long) -> Unit,
    actions: LayerActions,
) {
    LayerLine(
        kind = "layer", id = layer.id,
        depth = depth, color = layer.color, name = layer.name, visible = layer.visible,
        icon = when {
            layer.hasLine && layer.hasPoints -> R.drawable.ic_layer_globe
            layer.hasLine -> R.drawable.ic_layer_route
            else -> R.drawable.ic_layer_place
        },
        onToggle = { vm.setLayerVisible(layer, it) }, onColor = { vm.setLayerColor(layer, it) }, dctx = dctx,
        onRename = { onRename("layer", layer.id, layer.name) },
        onDelete = { vm.deleteLayer(layer) }, onZoom = { onZoom("layer", layer.id) },
        layerActions = actions, layer = layer,
    )
}

/** Ligne couche : œil + symbole couleur + nom + poignée + menu. */
@Composable
internal fun LayerLine(
    kind: String, id: Long,
    depth: Int, color: String, name: String, visible: Boolean,
    @DrawableRes icon: Int,
    onToggle: (Boolean) -> Unit, onColor: (String) -> Unit, dctx: DragCtx,
    onRename: () -> Unit, onDelete: () -> Unit, onZoom: () -> Unit,
    // Une couche et ce qu'on peut en faire ; null pour un dossier, dont le menu n'a ni sortie ni retouche.
    layerActions: LayerActions? = null, layer: LayerEntity? = null,
) {
    var showColor by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isDragging = dctx.dragInfo?.kind == kind && dctx.dragInfo.id == id
    val offset = if (isDragging) dctx.dragInfo.offset else 0f
    val hoverZone = dctx.hoverTarget?.takeIf { it.kind == kind && it.id == id }?.zone

    if (hoverZone == HoverZone.BEFORE) DropIndicatorLine()
    DrawerRow(
        depth = depth, dragging = isDragging, offset = offset, hovered = false,
        onPositioned = { dctx.rowBounds[kind to id] = it },
    ) {
        // Place du chevron d'un dossier, laissee vide : sans elle, l'oeil d'une couche remonterait sous
        // celui de son dossier et l'arbre perdrait sa colonne.
        Spacer(Modifier.width(DrawerChevronSize))
        val selection = LocalDrawerSelection.current
        val vibre = rememberSelectionHaptic()
        if (selection.active) {
            RowCheckbox(selection.isSelected(kind to id), { selection.toggle(kind to id) }, DrawerHitSize,
                "select_${kind}_$id")
        } else DrawerIcon(
            if (visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            if (visible) stringResource(R.string.action_hide) else stringResource(R.string.action_show),
            onClick = { onToggle(!visible) },
        )
        // Le symbole palit avec le nom quand la couche est masquee : une ligne eteinte doit se lire
        // eteinte d'un bout a l'autre, pas seulement a son oeil barre.
        DrawerIcon(
            painter = painterResource(icon), contentDescription = stringResource(R.string.action_color),
            tint = Color(color.toColorInt()).copy(alpha = if (visible) 1f else 0.4f),
            onClick = { showColor = true },
        )
        RowLabel(name, MaterialTheme.typography.bodyMedium,
            if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            null, onLongPress = { vibre(); selection.start(kind to id) },
            modifier = Modifier.weight(1f))
        if (!selection.active) RowEndActions {
            DragHandle(
                onStart = { strongHaptic(context); dctx.onStart(kind, id) },
                onDrag = { dctx.onDrag(kind, id, it) },
                onEnd = { dctx.onEnd(kind, id) })
            RowMenu(
                // Sans "Deplacer" : une couche se range en la glissant par sa poignee, et l'entree du menu
                // faisait la meme chose en deux ecrans de plus.
                onRename = onRename, onMove = null, onNewSub = null, onDelete = onDelete, onZoom = onZoom,
                layer = layer, layerActions = layerActions,
            )
        }
    }
    if (hoverZone == HoverZone.AFTER) DropIndicatorLine()
    if (showColor) ColorPickerDialog(color, onPick = { onColor(it); showColor = false }, onDismiss = { showColor = false })
}

/**
 * Champ de recherche du menu lateral : la loupe DANS le champ, la croix a l'autre bout.
 *
 * Les deux icones sont a l'interieur du contour, et non posees de part et d'autre : dehors, la loupe
 * mangeait la marge gauche et le champ n'etait plus centre entre les deux bords du tiroir. Dedans, elles
 * appartiennent au champ - ce qu'elles decrivent - et les marges redeviennent egales.
 *
 * La croix n'apparait qu'une fois la saisie commencee : c'est le seul moment ou elle a quelque chose a
 * faire.
 */
@Composable
internal fun SearchField(query: String, focus: FocusRequester, onQuery: (String) -> Unit) {
    CompactOutlinedTextField(
        value = query, onValueChange = onQuery, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.s)
            .height(44.dp).focusRequester(focus),
        textStyle = MaterialTheme.typography.bodyMedium,
        // Meme champ que ceux du calcul d'itineraire : fond teinte, contour discret au repos.
        colors = tintedFieldColors(),
        placeholder = {
            Text(stringResource(R.string.search_placeholder), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingIcon = {
            Icon(Icons.Filled.Search, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(DrawerHitSize).clip(CircleShape).clickable { onQuery("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_clear_search),
                        Modifier.size(DrawerIconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

/**
 * Ce que porte un dossier, en chiffres.
 *
 * Reprend la grammaire des reglages - carte blanche, une ligne par valeur, l'accent sur la valeur et non
 * sur son libelle : c'est le meme genre d'objet, une liste de couples nom/valeur qu'on parcourt de l'oeil.
 *
 * La duree ne s'affiche QUE si toutes les traces du dossier sont horodatees (cf. [FolderStats]) : un total
 * partiel serait plus petit que le temps reellement passe, sans que rien ne le dise.
 */
@Composable
internal fun FolderStatsDialog(
    folder: FolderEntity,
    folders: List<FolderEntity>,
    layers: List<LayerEntity>,
    imperial: Boolean,
    dark: Boolean,
    onDismiss: () -> Unit,
) {
    val stats = remember(folder, folders, layers) { folderStats(folder.id, folders, layers) }
    ProvideSettingsPalette(dark = dark) {
        val p = settingsPalette
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = p.screen,
            title = { Text(folder.name, color = p.label, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            text = {
                SettingsCard {
                    SetRow(stringResource(R.string.stats_layers)) { ValueText("${stats.layers}") }
                    RowDivider()
                    SetRow(stringResource(R.string.stats_tracks)) { ValueText("${stats.tracks}") }
                    if (stats.markers > 0) {
                        RowDivider()
                        SetRow(stringResource(R.string.stats_marker_layers)) { ValueText("${stats.markers}") }
                    }
                    // Les mesures n'ont de sens que s'il y a des traces : un dossier de marqueurs
                    // afficherait sinon trois zeros, qui se lisent comme une mesure ratee.
                    if (stats.tracks > 0) {
                        RowDivider()
                        SetRow(stringResource(R.string.chip_distance)) {
                            ValueText(Format.distance(stats.distance, imperial))
                        }
                        RowDivider()
                        SetRow(stringResource(R.string.info_name_ascent)) {
                            ValueText(Format.elevation(stats.ascent, imperial))
                        }
                        RowDivider()
                        SetRow(stringResource(R.string.info_name_descent)) {
                            ValueText(Format.elevation(stats.descent, imperial))
                        }
                        stats.movingTime?.let {
                            RowDivider()
                            SetRow(stringResource(R.string.info_name_duration)) { ValueText(Format.duration(it)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = p.accent) }
            },
        )
    }
}

/**
 * Les chiffres d'une trace : sa longueur, ses deniveles, sa duree si le fichier porte des heures, et le
 * jour ou on l'a parcourue.
 *
 * Meme grammaire que celle d'un dossier (cf. [FolderStatsDialog]). La longueur et les deniveles viennent de
 * la ligne en base ; la DATE, elle, n'y est pas - la couche ne retient que l'instant de son import, qui ne
 * dit rien de la sortie. Elle se lit sur le premier point horodate de la trace, a l'ouverture de la
 * fenetre : un fichier a relire, le temps d'un tour de roue.
 *
 * Une trace sans horodatage - dessinee, ou calculee par le planificateur - le dit, plutot que de montrer
 * la date d'import comme si c'etait celle du parcours.
 *
 * Sous les chiffres, les rubriques "Surfaces" et "Types de voies", rendues comme les "Details" d'un
 * itineraire calcule (cf. DetailsRubrics). Une trace importee ne dit pas sur quelles voies elle passe : il
 * faut la recaler sur le reseau (cf. TraceMatch), ce qui se fait a la premiere ouverture et se garde avec
 * la couche. Un appui sur une rubrique ferme la fenetre et ouvre son VIEWER sur la carte ([onOpenViewer]).
 */
@Composable
internal fun LayerStatsDialog(
    layer: LayerEntity,
    imperial: Boolean,
    dark: Boolean,
    loadStart: suspend (LayerEntity) -> Long?,
    loadWays: suspend (LayerEntity) -> LayerWays?,
    analyzeWays: suspend (LayerEntity) -> TraceMatch.LayerOutcome,
    /** Le profil de chaque ligne de la couche (cf. MainViewModel.layerProfiles). */
    loadProfiles: suspend (LayerEntity) -> List<ComputedTrack>,
    settings: SettingsEntity,
    /** Un VIEWER a ouvrir : les voies si on les a, le VIEWER, et la categorie ou le point du profil a designer. */
    onOpenViewer: (LayerWays?, PlannerViewer, Any?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Trois etats : en lecture, lue (une date), lue sans rien trouver.
    var lu by remember(layer.id) { mutableStateOf(false) }
    var debut by remember(layer.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(layer.id) {
        debut = if (layer.hasTime) runCatching { loadStart(layer) }.getOrNull() else null
        lu = true
    }
    // Les voies : lues du disque, sinon retrouvees. [essai] relance le recalage apres un echec.
    var voies by remember(layer.id) { mutableStateOf<WaysLoad>(WaysLoad.Loading) }
    var essai by remember(layer.id) { mutableIntStateOf(0) }
    LaunchedEffect(layer.id, essai) {
        if (essai == 0) {
            val gardees = runCatching { loadWays(layer) }.getOrNull()
            if (gardees != null) { voies = WaysLoad.Done(gardees); return@LaunchedEffect }
        }
        voies = WaysLoad.Analyzing
        voies = when (val issue = runCatching { analyzeWays(layer) }.getOrNull()) {
            is TraceMatch.LayerOutcome.Done -> WaysLoad.Done(issue.ways)
            TraceMatch.LayerOutcome.NoMatch -> WaysLoad.NoMatch
            TraceMatch.LayerOutcome.Unreachable, null -> WaysLoad.Unreachable
        }
    }
    // Le profil de la ligne la plus longue, pour la rubrique "Elevation" : sans altitudes, pas de rubrique.
    var profil by remember(layer.id) { mutableStateOf<ComputedTrack?>(null) }
    LaunchedEffect(layer.id) {
        profil = runCatching { profileTrackOf(loadProfiles(layer)) }.getOrNull()
            ?.takeIf { it.hasZ && it.samples.size >= 2 }
    }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    ProvideSettingsPalette(dark = dark) {
        val p = settingsPalette
        /*
         * Une fenetre dessinee ici et non un AlertDialog : celui-ci reserve sous son contenu une bande de
         * boutons haute de pres de 80 dp, pour le seul "Fermer". Les voies ont besoin de cette hauteur.
         */
        @OptIn(ExperimentalMaterial3Api::class)
        BasicAlertDialog(onDismissRequest = onDismiss) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = p.screen) {
                Column(Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xl, bottom = Spacing.xs)) {
                    Text(layer.name, style = MaterialTheme.typography.headlineSmall, color = p.label, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = Spacing.l))
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("layer_stats")) {
                        SettingsCard {
                            SetRow(stringResource(R.string.chip_distance)) {
                                ValueText(Format.distance(layer.distance, imperial))
                            }
                            RowDivider()
                            // Les deux deniveles sur une ligne, sous leurs noms courts : ils se lisent ensemble.
                            StatPair(
                                stringResource(R.string.chip_ascent), Format.elevation(layer.ascent, imperial),
                                stringResource(R.string.chip_descent), Format.elevation(layer.descent, imperial),
                            )
                            RowDivider()
                            val d = debut
                            val date: @Composable () -> Unit = {
                                when {
                                    !lu -> androidx.compose.material3.CircularProgressIndicator(
                                        Modifier.size(14.dp), strokeWidth = 2.dp)
                                    // Le mois abrege : la date partage sa ligne avec la duree.
                                    d != null -> ValueText(
                                        java.time.Instant.ofEpochMilli(d).atZone(java.time.ZoneId.systemDefault())
                                            .format(java.time.format.DateTimeFormatter
                                                .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)))
                                    else -> ValueText(stringResource(R.string.stats_date_unknown))
                                }
                            }
                            // La duree, seulement si le fichier porte des heures : sans elles, il n'y a rien a
                            // mesurer, et une case vide se lirait comme une sortie de zero minute. Elle
                            // partage alors sa ligne avec la date.
                            val duree = layer.movingTime?.takeIf { layer.hasTime }
                            if (duree != null) {
                                StatPair(stringResource(R.string.chip_duration), { ValueText(Format.duration(duree)) },
                                    stringResource(R.string.stats_date), date)
                            } else {
                                SetRow(stringResource(R.string.stats_date)) { date() }
                            }
                        }
                        LayerWaysSection(voies, imperial, onRetry = { essai++ }, onOpenViewer = onOpenViewer,
                            elevation = profil?.let { t ->
                                {
                                    ElevationRubric(
                                        track = t, settings = settings, slope = layer.slopeColored,
                                        lineColor = runCatching { Color(layer.color.toColorInt()) }
                                            .getOrDefault(MaterialTheme.colorScheme.primary),
                                        onOpen = { x ->
                                            onOpenViewer((voies as? WaysLoad.Done)?.ways, PlannerViewer.PROFILE, x)
                                        },
                                    )
                                }
                            })
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.action_close), color = p.accent)
                    }
                }
            }
        }
    }
}

/**
 * Deux mesures sur une ligne de carte, separees d'un filet vertical : chacune son libelle court a gauche,
 * sa valeur a droite. La valeur ne se coupe jamais ; c'est le libelle qui cede, s'il le faut.
 */
@Composable
private fun ColumnScopeMarker.StatPair(
    leftLabel: String, left: @Composable () -> Unit, rightLabel: String, right: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        StatCell(leftLabel, Modifier.weight(1f), left)
        Box(Modifier.fillMaxHeight().width(1.dp).background(settingsPalette.divider))
        StatCell(rightLabel, Modifier.weight(1f), right)
    }
}

@Composable
private fun ColumnScopeMarker.StatPair(leftLabel: String, left: String, rightLabel: String, right: String) =
    StatPair(leftLabel, { ValueText(left) }, rightLabel, { ValueText(right) })

@Composable
private fun StatCell(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Row(
        modifier.defaultMinSize(minHeight = 52.dp).padding(horizontal = Spacing.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = settingsPalette.label, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        value()
    }
}

/** Ou en sont les voies d'une couche dans la fenetre des statistiques. */
sealed interface WaysLoad {
    data object Loading : WaysLoad
    data object Analyzing : WaysLoad
    data class Done(val ways: LayerWays) : WaysLoad
    data object NoMatch : WaysLoad
    data object Unreachable : WaysLoad
}

/**
 * Les surfaces et les types de voies d'une couche, sous ses chiffres : les rubriques d'un itineraire, sur
 * une carte a part. Pendant le recalage, un indicateur et ce qu'il fait ; en cas d'echec, pourquoi - et de
 * quoi reessayer quand c'est le reseau.
 */
@Composable
private fun LayerWaysSection(
    load: WaysLoad,
    imperial: Boolean,
    onRetry: () -> Unit,
    onOpenViewer: (LayerWays?, PlannerViewer, Any?) -> Unit,
    /** La rubrique "Elevation", au-dessus des voies ; null quand la trace n'a pas d'altitudes. */
    elevation: (@Composable () -> Unit)? = null,
) {
    val p = settingsPalette
    Spacer(Modifier.height(Spacing.m))
    SettingsCard {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m).testTag("layer_ways")) {
            if (elevation != null) CompositionLocalProvider(LocalContentColor provides p.label) {
                elevation()
                Spacer(Modifier.height(Spacing.s))
            }
            when (load) {
                WaysLoad.Loading -> Unit
                WaysLoad.Analyzing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.stats_ways_analyzing), color = p.subtle,
                        style = MaterialTheme.typography.bodyMedium)
                }
                WaysLoad.NoMatch -> Text(stringResource(R.string.stats_ways_no_match), color = p.subtle,
                    style = MaterialTheme.typography.bodyMedium)
                WaysLoad.Unreachable -> Column {
                    Text(stringResource(R.string.stats_ways_unreachable), color = p.subtle,
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("layer_ways_retry")) {
                        Text(stringResource(R.string.planner_retry), color = p.accent)
                    }
                }
                is WaysLoad.Done -> CompositionLocalProvider(LocalContentColor provides p.label) {
                    val w = load.ways
                    DetailsRubrics(w.all, imperial, onOpen = { v, initial -> onOpenViewer(w, v, initial) })
                    // Ce que le recalage n'a pas pu dire, dit plutot que fondu dans les parts.
                    if (w.offNetworkMeters >= 50.0) {
                        Text(stringResource(R.string.stats_ways_off_network, Format.shortDistance(w.offNetworkMeters, imperial)),
                            style = MaterialTheme.typography.bodySmall, color = p.subtle,
                            modifier = Modifier.padding(top = Spacing.s))
                    }
                    if (!w.osmTags) {
                        Text(stringResource(R.string.stats_ways_coarse), style = MaterialTheme.typography.bodySmall,
                            color = p.subtle, modifier = Modifier.padding(top = Spacing.xs))
                    }
                }
            }
        }
    }
}

@Composable
internal fun DropIndicatorLine() {
    Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary))
}

/*
 * Mesures du menu lateral, d'apres la maquette "Trailog - theme et maquettes" (rangee "Menu lateral").
 * Elles sont ici et non a l'appel : une ligne d'arbre est faite de cinq elements que trois composables se
 * partagent, et les voir cote a cote est le seul moyen de garder la grille droite.
 *
 * Une ligne fait 44 dp, les icones ont 32 dp de cible : l'arbre se visait mal du bout du doigt a 40 dp et
 * 30 de cible. Les tailles de texte, elles, sont celles du theme (bodyMedium), plus aucune a part.
 */
/** Hauteur d'une ligne de l'arbre - et, pendant un glisser-deposer, le pas qui dit quelle ligne est survolee. */
internal val DrawerRowHeight = 44.dp

/** Retrait de chaque niveau d'imbrication. */
internal val DrawerIndent = 20.dp

/** Marge de bord d'une ligne, a gauche comme a droite. */
internal val DrawerRowPadH = 8.dp

/** Ecart entre deux elements d'une ligne (chevron, oeil, symbole, nom...). */
internal val DrawerRowGap = 2.dp

/** Chevron, oeil, symbole : deux tailles voisines - l'oeil et le symbole portent la ligne, le chevron
 *  n'est qu'un accessoire de pliage. */
internal val DrawerChevronSize = 18.dp

internal val DrawerIconSize = 20.dp

/** Cible tactile posee autour de ces petites icones : ce qu'on peut prendre sans grossir le dessin. */
internal val DrawerHitSize = 32.dp

/**
 * Un bouton de l'en-tete du menu : icone et libelle dans une pastille de 40 dp.
 *
 * [primary] : l'aplat bleu clair du geste qu'on vient chercher ici (importer). Sinon un simple contour -
 * le bouton existe, mais ne tire pas l'oeil.
 */
@Composable
internal fun HeaderButton(
    icon: ImageVector, label: String, primary: Boolean, contentDescription: String? = null, onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.height(HeaderActionsHeight).clip(CircleShape)
            .then(
                if (primary) Modifier.background(scheme.primaryContainer)
                else Modifier.border(1.dp, scheme.outlineVariant, CircleShape)
            )
            .clickable(onClick = onClick)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .padding(start = if (primary) 12.dp else 10.dp, end = if (primary) 16.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (primary) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelLarge,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            color = if (primary) scheme.onPrimaryContainer else scheme.onSurface, maxLines = 1)
    }
}

/**
 * Un bouton rond de l'en-tete, icone seule. Allume ([active]), il prend l'aplat bleu clair : c'est ainsi
 * que la recherche dit qu'elle filtre ce qui est dessous.
 */
@Composable
internal fun HeaderIconButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (active) scheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
    }
}

/**
 * Une ligne de l'arbre du menu lateral : le retrait de son niveau, et ce que le drag lui fait.
 *
 * Le retrait (20 dp par niveau) est plus court que l'indentation d'une liste ordinaire : cet arbre descend
 * a trois ou quatre niveaux sur un ecran de telephone, et il ne doit pas manger la moitie de la largeur au
 * dernier.
 */
@Composable
internal fun DrawerRow(
    depth: Int, dragging: Boolean, offset: Float, hovered: Boolean,
    onPositioned: (Float) -> Unit, content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { onPositioned(it.positionInRoot().y) }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = offset; alpha = if (dragging) 0.85f else 1f }
            .background(if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .height(DrawerRowHeight)
            .padding(start = DrawerRowPadH + DrawerIndent * depth, end = DrawerRowPadH / 2),
        horizontalArrangement = Arrangement.spacedBy(DrawerRowGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Icone d'une ligne d'arbre : petite au dessin, large au doigt.
 *
 * Le dessin fait 15 dp comme dans la maquette, la zone tapee 26 : sans cet ecart, il faudrait choisir
 * entre une ligne haute de 48 dp et des cibles qu'on rate. La cible reste sous le minimum Material,
 * assume - une ligne d'arbre en aligne cinq, et l'arbre en empile une vingtaine.
 */
@Composable
internal fun DrawerIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?, size: Dp = DrawerIconSize,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit,
) {
    Box(Modifier.size(DrawerHitSize).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(imageVector, contentDescription, Modifier.size(size), tint = tint)
    }
}

/** Meme icone, dessinee a partir d'un trace du projet (symboles de couche, cf. ic_layer_route). */
@Composable
internal fun DrawerIcon(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String?, tint: Color, onClick: () -> Unit,
) {
    Box(Modifier.size(DrawerHitSize).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painter, contentDescription, Modifier.size(DrawerIconSize), tint = tint)
    }
}

/** Les deux prises de fin de ligne, poignee et menu, serrees l'une contre l'autre. */
@Composable
internal fun RowEndActions(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Poignée : appui long -> drag de réordonnancement (avec animation/haptique gérées par la ligne). */
@Composable
internal fun DragHandle(onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit) {
    // pointerInput(Unit) ne relance jamais son bloc : sans rememberUpdatedState, la coroutine de geste
    // resterait figée sur les callbacks de la toute première composition (hoverTarget alors toujours null).
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnEnd by rememberUpdatedState(onEnd)
    // La cible fait la hauteur de la ligne, le dessin 18 dp : un appui long se pose a peu pres, pas au pixel.
    Box(
        Modifier.size(width = 28.dp, height = 40.dp).pointerInput(Unit) {
            var total = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { total = 0f; currentOnStart() },
                onDrag = { change, amount -> change.consume(); total += amount.y; currentOnDrag(total) },
                onDragEnd = { currentOnEnd() }, onDragCancel = { currentOnEnd() },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.DragIndicator, stringResource(R.string.action_drag_to_move), Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
    }
}

/** 3 points : menu contextuel (appui simple). */
@Composable
internal fun RowMenu(
    onRename: () -> Unit, onMove: (() -> Unit)?, onNewSub: (() -> Unit)?, onDelete: () -> Unit, onZoom: () -> Unit,
    // Propre au dossier, et seulement s'il porte des couches : une couche a deja sa pastille de couleur
    // dans sa ligne, et un dossier vide n'a rien a colorer - l'entree disparait plutot que de ne rien faire.
    onColor: (() -> Unit)? = null,
    layer: LayerEntity? = null, layerActions: LayerActions? = null,
    // Le total de ce que le dossier contient, sous-dossiers compris. Pour une couche, ses propres chiffres
    // passent par [layerActions].
    onStats: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(DrawerHitSize).clickable { open = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more), Modifier.size(DrawerIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.medium, containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            // Un dossier cadre TOUTES ses couches, pas une : le libelle le dit au pluriel.
            MenuEntry(Icons.Outlined.CenterFocusWeak,
                stringResource(if (onNewSub != null) R.string.action_zoom_to_layers else R.string.action_zoom_to_layer)) {
                open = false; onZoom()
            }
            if (onStats != null) {
                MenuEntry(Icons.Outlined.BarChart, stringResource(R.string.action_folder_stats)) { open = false; onStats() }
            }
            // Une couche de marqueurs n'a ni longueur ni denivele : l'entree n'y apparait pas, plutot que
            // d'ouvrir une fenetre de zeros.
            if (layer != null && layerActions != null && layer.hasLine) {
                MenuEntry(Icons.Outlined.BarChart, stringResource(R.string.action_folder_stats)) {
                    open = false; layerActions.onStats(layer)
                }
                // La carte le long de la trace, pour le hors-ligne : ici, sur la trace elle-meme, plutot que
                // dans un parcours qui demandait d'abord ce qu'on telecharge, puis quelle trace.
                layerActions.onDownloadMap?.let { telecharger ->
                    MenuEntry(Icons.Outlined.FileDownload, stringResource(R.string.action_download_map)) {
                        open = false; telecharger(layer)
                    }
                }
                // Colorier le trait selon la pente, puis lui rendre sa couleur : une entree qui dit ce
                // qu'elle fera, selon l'etat. Sans altitude, pas de pente, et pas d'entree.
                if (layer.hasZ) {
                    MenuEntry(
                        Icons.Outlined.Landscape,
                        stringResource(if (layer.slopeColored) R.string.action_restore_color else R.string.action_color_by_slope),
                        modifier = Modifier.testTag("menu_slope_colored"),
                    ) { open = false; layerActions.onSlopeColored(layer, !layer.slopeColored) }
                }
            }
            if (onColor != null) {
                MenuEntry(Icons.Outlined.Palette, stringResource(R.string.action_color_layers)) { open = false; onColor() }
            }
            MenuEntry(Icons.Outlined.Edit, stringResource(R.string.action_rename)) { open = false; onRename() }
            if (onMove != null) {
                MenuEntry(Icons.Outlined.DriveFileMove, stringResource(R.string.action_move)) { open = false; onMove() }
            }
            if (onNewSub != null) {
                MenuEntry(Icons.Outlined.CreateNewFolder, stringResource(R.string.action_new_subfolder)) {
                    open = false; onNewSub()
                }
            }
            // Les SORTIES d'une couche seulement. Les retouches, elles, ont quitte ce menu pour la barre
            // d'outils de la carte : elles agissent sur un segment, parfois sur deux, et designer un
            // segment se fait du doigt sur la carte - pas dans le menu d'une ligne d'arborescence.
            if (layer != null && layerActions != null) {
                MenuEntry(Icons.Outlined.FileUpload, stringResource(R.string.action_export_layer)) {
                    open = false; layerActions.onExport(layer)
                }
                MenuEntry(Icons.Outlined.Share, stringResource(R.string.action_share)) {
                    open = false; layerActions.onShare(layer)
                }
            }
            // Le seul geste qui detruit : a part, sous un filet, et en rouge.
            HorizontalDivider(Modifier.padding(horizontal = 6.dp, vertical = Spacing.xs),
                color = MaterialTheme.colorScheme.outlineVariant)
            MenuEntry(TrailogIcons.Trash, stringResource(R.string.action_delete),
                color = MaterialTheme.colorScheme.error) { open = false; onDelete() }
        }
    }
}

/** Une entree de menu : son icone, son libelle, et la meme couleur pour les deux. */
@Composable
internal fun MenuEntry(
    icon: ImageVector, text: String, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit,
) {
    val iconColor = if (color == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurfaceVariant else color
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = iconColor),
        modifier = modifier,
    )
}

/**
 * Ce qu'on peut faire d'une couche depuis son menu, au-dela de ce qu'un dossier sait faire aussi.
 *
 * Un porteur plutot que cinq lambdas passees de main en main : l'arborescence les traverse sur toute sa
 * profondeur, et chacune aurait suivi les autres a chaque appel. Toutes visent la couche entiere, d'ou le
 * parametre commun - la ligne qui les declenche sait laquelle, pas ce qu'il faut en faire.
 */
class LayerActions(
    val onExport: (LayerEntity) -> Unit,
    val onShare: (LayerEntity) -> Unit,
    val onStats: (LayerEntity) -> Unit = {},
    /** Telecharger la carte le long de la trace ; null si le fond affiche ne s'y prete pas. */
    val onDownloadMap: ((LayerEntity) -> Unit)? = null,
    /** Colorier le trait selon la pente (vrai), ou lui rendre la couleur de la couche (faux). */
    val onSlopeColored: (LayerEntity, Boolean) -> Unit = { _, _ -> },
)

/** Les couches que porte un dossier, sous-dossiers compris : ce sur quoi portent ses actions (oeil,
 *  couleur, cadrage). Cote base, cf. MainViewModel.descendantFolderIds. */
internal fun layersUnder(folderId: Long, folders: List<FolderEntity>, layers: List<LayerEntity>): List<LayerEntity> {
    val ids = HashSet<Long>(); val stack = ArrayDeque<Long>(); stack.add(folderId)
    while (stack.isNotEmpty()) { val f = stack.removeLast(); if (ids.add(f)) folders.filter { it.parentId == f }.forEach { stack.add(it.id) } }
    return layers.filter { it.folderId in ids }
}

internal fun folderBbox(folderId: Long, folders: List<FolderEntity>, layers: List<LayerEntity>): DoubleArray? {
    val ls = layersUnder(folderId, folders, layers)
    val w = ls.map { it.west }.filter { it != 0.0 }.minOrNull() ?: return null
    val s = ls.map { it.south }.filter { it != 0.0 }.minOrNull() ?: return null
    val e = ls.map { it.east }.filter { it != 0.0 }.maxOrNull() ?: return null
    val n = ls.map { it.north }.filter { it != 0.0 }.maxOrNull() ?: return null
    return doubleArrayOf(w, s, e, n)
}

@Composable
internal fun DrawerContent(
    folders: List<FolderEntity>, layers: List<LayerEntity>, settings: SettingsEntity,
    vm: MainViewModel,
    // Le tiroir reste COMPOSE une fois referme : sans ce drapeau, son contenu n'a aucun moyen de savoir
    // qu'il a disparu de l'ecran, et garde l'etat dans lequel on l'a laisse.
    open: Boolean,
    onSettings: () -> Unit, onClose: () -> Unit, onImport: () -> Unit,
    /** "Telecharger la carte" le long d'une trace, depuis son menu ; null quand le fond affiche ne se
     *  telecharge pas (cf. offlineDownloadAvailable) - l'entree n'apparait pas. */
    onDownloadMap: ((LayerEntity) -> Unit)?,
    onZoom: (String, Long) -> Unit,
    // Un geste du tiroir n'a rien produit : l'ecran le dit. Un rappel etroit plutot que le porteur des
    // boites, dont le tiroir n'a aucune raison de lire le reste (cf. MainDialogState.failure).
    onFailure: (Int) -> Unit,
    /** Une rubrique touchee dans les statistiques d'une couche - elevation ou voies : son VIEWER, sur la carte. */
    onWaysViewer: (LayerEntity, LayerWays?, PlannerViewer, Any?) -> Unit = { _, _, _, _ -> },
) {
    var renameTarget by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val rowPx = with(LocalDensity.current) { DrawerRowHeight.toPx() }
    val scope = rememberCoroutineScope()

    // Positions (Y, coord. racine) de chaque ligne affichée, pour détecter au vol la ligne survolée pendant un drag.
    val rowBounds = remember { mutableStateMapOf<Pair<String, Long>, Float>() }
    var dragInfo by remember { mutableStateOf<DragInfo?>(null) }
    val hoverTarget: HoverTarget? = dragInfo?.let { info ->
        val startTop = rowBounds[info.kind to info.id] ?: return@let null
        val centerY = startTop + rowPx / 2f + info.offset
        val hit = rowBounds.entries.firstOrNull { (k, top) ->
            centerY in top..(top + rowPx) && !(k.first == info.kind && k.second == info.id)
        } ?: return@let null
        val (key, top) = hit
        val rel = (centerY - top) / rowPx
        val zone = when {
            rel < 0.25f -> HoverZone.BEFORE
            rel > 0.75f -> HoverZone.AFTER
            key.first == "folder" -> HoverZone.INTO
            rel < 0.5f -> HoverZone.BEFORE
            else -> HoverZone.AFTER
        }
        // Empêche de déposer un dossier dans lui-même ou dans l'un de ses propres descendants (créerait un cycle).
        if (info.kind == "folder") {
            val prospectiveParent = if (zone == HoverZone.INTO) key.second else parentIdOf(key.first, key.second, folders, layers)
            if (prospectiveParent == info.id || (prospectiveParent != null && isDescendantFolder(prospectiveParent, info.id, folders))) {
                return@let null
            }
        }
        HoverTarget(key.first, key.second, zone)
    }
    val dctx = DragCtx(
        rowBounds = rowBounds,
        dragInfo = dragInfo,
        hoverTarget = hoverTarget,
        onStart = { kind, id -> dragInfo = DragInfo(kind, id, 0f) },
        onDrag = { kind, id, total -> if (dragInfo?.kind == kind && dragInfo?.id == id) dragInfo = dragInfo!!.copy(offset = total) },
        onEnd = { kind, id ->
            val info = dragInfo
            val target = hoverTarget
            if (info != null && info.kind == kind && info.id == id && target != null) {
                val position = when (target.zone) {
                    HoverZone.BEFORE -> DropPosition.BEFORE
                    HoverZone.INTO -> DropPosition.INTO
                    HoverZone.AFTER -> DropPosition.AFTER
                }
                // Garde la ligne "en drag" (donc à sa position de dépose) jusqu'à ce que l'écriture soit faite,
                // pour éviter qu'elle ne revienne un instant à sa place d'origine avant de sauter à la nouvelle.
                scope.launch {
                    vm.reorderDrop(kind, id, target.kind, target.id, position)
                    dragInfo = null
                }
            } else {
                dragInfo = null
            }
        },
    )

    val openRename: (String, Long, String) -> Unit = { k, id, n -> renameTarget = k to id; renameValue = n }
    val openMove: (String, Long) -> Unit = { k, id -> moveTarget = k to id }

    var newFolderDialog by remember { mutableStateOf(false) }
    var newFolderParent by remember { mutableStateOf<Long?>(null) }
    var newFolderName by remember { mutableStateOf("") }
    val defaultFolderName = stringResource(R.string.label_new_folder)
    val openNewFolder: (Long?) -> Unit = { parentId -> newFolderParent = parentId; newFolderName = ""; newFolderDialog = true }

    // Dossiers avec un import en cours (null = racine) : spinner ; et confirmation de suppression de dossier.
    val importing by vm.importing.collectAsState()
    val elevating by vm.elevating.collectAsState()
    val importingIds = ImportSpinners(importing.keys, elevating.keys)
    var deleteFolderTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var statsTarget by remember { mutableStateOf<FolderEntity?>(null) }

    // ---------- sorties d'une couche : enregistrer un GPX, ou l'envoyer ailleurs ----------
    // Le selecteur de fichier est UNIQUE et vit ici, non dans chaque ligne de l'arborescence : une couche
    // par ligne en ouvrirait autant, pour un geste qui ne concerne jamais qu'une couche a la fois. La
    // couche visee attend donc son tour dans un etat, que le retour du selecteur relit.
    val drawerCtx = LocalContext.current
    var gpxPending by remember { mutableStateOf<ByteArray?>(null) }
    val gpxExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val bytes = gpxPending
        gpxPending = null
        // Renoncer au selecteur n'est pas un echec ; ne rien ecrire en est un, et il se dit.
        if (uri == null) return@rememberLauncherForActivityResult
        val ecrit = bytes != null && runCatching {
            drawerCtx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        }.getOrDefault(false)
        if (!ecrit) onFailure(R.string.error_file_not_written)
    }
    // L'export se fait en DEUX temps : on choisit d'abord le format, on nomme le fichier ensuite. Les deux
    // formats ne portent pas la meme chose (cf. ExportFormatDialog), et ce choix ne se devine pas depuis le
    // selecteur de fichier du systeme, qui ne montre qu'un nom et un dossier.
    var exportTarget by remember { mutableStateOf<LayerEntity?>(null) }
    val onExportLayer: (LayerEntity) -> Unit = { layer -> exportTarget = layer }
    val shareLabel = stringResource(R.string.action_share)
    val onShareLayer: (LayerEntity) -> Unit = { layer ->
        vm.shareLayerGpx(layer) { uri ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, layer.name)
                // Le droit de lecture est accorde a l'application QUI RECOIT, et pour cette URI seulement :
                // sans ce drapeau, elle obtient une adresse qu'elle n'a pas le droit d'ouvrir.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Aucune application capable de recevoir un GPX : le selecteur ne s'ouvre pas, et sans ce
            // message le partage serait un bouton qui ne fait rien.
            runCatching { drawerCtx.startActivity(Intent.createChooser(send, shareLabel)) }
                .onFailure { onFailure(R.string.error_no_app_share) }
        }
    }
    var layerStatsTarget by remember { mutableStateOf<LayerEntity?>(null) }
    val layerActions = LayerActions(onExport = onExportLayer, onShare = onShareLayer,
        onStats = { layerStatsTarget = it }, onDownloadMap = onDownloadMap,
        onSlopeColored = { l, on -> vm.setLayerSlopeColored(l, on) })
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Tiroir referme : la recherche se referme avec lui. On rouvre le menu pour consulter l'arborescence,
    // pas pour retrouver un filtre pose la fois d'avant - et le bouton allume, seul temoin de ce filtre,
    // n'est plus la pour le dire.
    LaunchedEffect(open) {
        if (!open && searchOpen) {
            searchOpen = false
            searchQuery = ""
            focusManager.clearFocus()
        }
    }

    // La selection multiple : ouverte par un appui long sur une ligne, annulee par le retour, et refermee
    // avec le tiroir - on ne retrouve pas en rouvrant le menu des cases cochees la fois d'avant.
    val selection = remember { DrawerSelection() }
    var confirmDeleteSelection by remember { mutableStateOf(false) }
    // L'infobulle du nom entier : une a la fois, fermee par tout appui dans le menu (cf. TooltipHolder).
    val bulles = remember { TooltipHolder() }
    androidx.activity.compose.BackHandler(enabled = selection.active) { selection.cancel() }
    LaunchedEffect(open) { if (!open) { selection.cancel(); bulles.close() } }
    CompositionLocalProvider(LocalDrawerSelection provides selection, LocalTooltipHolder provides bulles) {
    Column(Modifier.fillMaxSize().statusBarsPadding().closesTooltips(bulles)) {
        // En-tete, sur deux lignes : les reglages, le titre et la croix ; puis les gestes de la bibliotheque.
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = Spacing.s, end = Spacing.s, top = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            // Une roue crantee, pas l'avatar : ici le bouton mene aux reglages, et c'est l'action qui doit
            // se lire. L'avatar reste en tete de l'ecran des reglages, ou il designe bien quelqu'un.
            HeaderIconButton(Icons.Outlined.Settings, stringResource(R.string.settings_title), onClick = onSettings)
            // Titre d'ecran : le meme style que celui des autres ecrans de l'application.
            // Fallback traduit si le titre personnalise est vide, au lieu de ne rien afficher.
            val title = settings.customTitle.ifBlank { stringResource(R.string.drawer_default_title) }
                ?: stringResource(R.string.drawer_default_title)
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            HeaderIconButton(Icons.Filled.Close, stringResource(R.string.action_close_menu), onClick = onClose)
        }
        // Les gestes de la bibliotheque, sans bande grise : c'est l'aplat du bouton "Importer" qui les
        // designe, et le filet dessous qui les separe de l'arborescence.
        //
        // "Importer" est le geste qu'on vient chercher ici : il porte l'aplat. "Dossier" a le sien en
        // contour, et son libelle plutot que l'icone seule - une icone de dossier "plus" ne se lisait
        // pas. La recherche est a l'oppose : elle ne cree ni n'importe rien, elle change la facon de LIRE
        // ce qui est en dessous, et le vide entre elle et les autres dit cette difference.
        // En selection, la ligne des gestes cede la place a ceux de la selection (cf. SelectionHeader).
        if (selection.active) {
            val tous = allTreeItems(folders, layers)
            SelectionHeader(
                state = selection.allState(tous), count = selection.selected.size,
                onToggleAll = { selection.toggleAll(tous) },
                onCancel = { selection.cancel() },
                onDelete = { confirmDeleteSelection = true },
                modifier = Modifier.padding(start = Spacing.xs, end = Spacing.s, top = Spacing.s, bottom = Spacing.m),
            )
        } else
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.s, top = Spacing.s, bottom = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderButton(Icons.Outlined.FileUpload, stringResource(R.string.action_import), primary = true,
                onClick = onImport)
            HeaderButton(Icons.Outlined.CreateNewFolder, stringResource(R.string.label_folder_short), primary = false,
                contentDescription = stringResource(R.string.label_new_folder)) { openNewFolder(null) }
            Spacer(Modifier.weight(1f))
            HeaderIconButton(Icons.Filled.Search, stringResource(R.string.search_placeholder), active = searchOpen) {
                searchOpen = !searchOpen
                // Refermer la barre efface la recherche : la garder filtrerait l'arborescence sans que rien
                // a l'ecran ne dise pourquoi elle est incomplete.
                if (!searchOpen) { searchQuery = ""; focusManager.clearFocus() }
            }
        }

        // Recherche : elle remplace l'arbre par la liste a plat de ce qu'elle trouve, plutot que de
        // deplier les dossiers autour des resultats. Une couche trouvee est une couche qu'on veut voir
        // MAINTENANT - son rangement, on le connait deja, c'est meme pour ne pas avoir a le parcourir
        // qu'on a tape son nom.
        if (searchOpen) {
            // Le focus est pris A L'OUVERTURE, et la seule : demande a chaque recomposition, le champ
            // reprendrait le clavier des qu'on le lache.
            LaunchedEffect(Unit) { searchFocus.requestFocus() }
            SearchField(searchQuery, searchFocus) { searchQuery = it }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        /*
         * Un toucher dans l'arborescence rend le focus au tiroir, donc referme le clavier.
         *
         * Sur la passe INITIALE et sans consommer l'evenement : la ligne touchee le recoit ensuite
         * normalement. Un simple clickable englobant, lui, aurait vole les taps des lignes.
         */
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            if (e.type == PointerEventType.Press) focusManager.clearFocus()
                        }
                    }
                }
                .verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
        ) {
            val query = searchQuery.trim()
            if (query.isNotEmpty()) {
                val found = layers.filter { TreeSearch.matches(it.name, query) }
                if (found.isEmpty()) {
                    Text(
                        stringResource(R.string.search_no_result), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                found.forEach { item ->
                    key("found", item.id) {
                        LayerRow(item, 0, vm, dctx, openRename, onZoom, layerActions)
                    }
                }
                return@Column
            }
            importingIds.state(null)?.let { ImportSpinnerRow(0, it) }
            combinedChildren(null, folders, layers).forEach { item ->
                when (item) {
                    is FolderEntity -> key("folder", item.id) {
                        FolderNode(item, folders, layers, 0, vm, dctx, openRename, openMove, openNewFolder, onZoom, importingIds,
                            layerActions, onStats = { statsTarget = it }) { deleteFolderTarget = it }
                    }
                    is LayerEntity -> key("layer", item.id) { LayerRow(item, 0, vm, dctx, openRename, onZoom, layerActions) }
                }
            }
        }
    }
    }

    if (confirmDeleteSelection) {
        val n = selection.selected.size
        AlertDialog(
            onDismissRequest = { confirmDeleteSelection = false },
            title = { Text(stringResource(R.string.selection_delete_title)) },
            text = { Text(pluralStringResource(R.plurals.selection_delete_text, n, n)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteSelection = false
                    val (dossiers, couches) = deletionPlan(selection.selected, folders, layers)
                    dossiers.forEach { vm.deleteFolder(it, deleteContents = true) }
                    couches.forEach { vm.deleteLayer(it) }
                    selection.cancel()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelection = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    exportTarget?.let { layer ->
        ExportFormatDialog(
            dark = isDarkTheme(settings.theme),
            onDismiss = { exportTarget = null },
            onPick = { geoJson ->
                exportTarget = null
                val extension = if (geoJson) "geojson" else "gpx"
                val ready: (ByteArray) -> Unit = { bytes ->
                    gpxPending = bytes
                    gpxExporter.launch(GpxWriter.fileName(layer.name, extension))
                }
                if (geoJson) vm.layerGeoJson(layer, ready) else vm.layerGpx(layer, ready)
            },
        )
    }

    layerStatsTarget?.let { l ->
        LayerStatsDialog(
            layer = l, imperial = settings.units == "imperial", dark = isDarkTheme(settings.theme),
            loadStart = { vm.trackStartTime(it) },
            loadWays = { vm.layerWays(it) },
            analyzeWays = { vm.analyzeLayerWays(it) },
            loadProfiles = { vm.layerProfiles(it) },
            settings = settings,
            onOpenViewer = { w, v, initial -> layerStatsTarget = null; onWaysViewer(l, w, v, initial) },
            onDismiss = { layerStatsTarget = null },
        )
    }

    statsTarget?.let { f ->
        FolderStatsDialog(
            folder = f, folders = folders, layers = layers,
            imperial = settings.units == "imperial", dark = isDarkTheme(settings.theme),
            onDismiss = { statsTarget = null },
        )
    }

    deleteFolderTarget?.let { f ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            title = { Text(stringResource(R.string.dialog_delete_folder_title)) },
            text = { Text(stringResource(R.string.dialog_delete_folder_text)) },
            // "Oui" (confirmButton, à droite) supprime aussi le contenu ; "Non" (à gauche) le remonte au parent.
            confirmButton = { TextButton(onClick = { vm.deleteFolder(f, deleteContents = true); deleteFolderTarget = null }) { Text(stringResource(R.string.action_yes)) } },
            dismissButton = { TextButton(onClick = { vm.deleteFolder(f, deleteContents = false); deleteFolderTarget = null }) { Text(stringResource(R.string.action_no)) } },
        )
    }

    if (newFolderDialog) {
        val focus = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text(stringResource(R.string.label_new_folder)) },
            text = {
                CompactOutlinedTextField(newFolderName, { newFolderName = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
                // Le focus se demande DANS la boite, une fois son champ compose : demande depuis l'ecran,
                // il partait avant que la fenetre de la boite n'existe, et le FocusRequester sans noeud
                // levait - l'application se fermait. Sous garde malgre tout : un focus refuse ne coute
                // que le clavier, qu'un toucher sur le champ fait venir.
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = newFolderName.ifBlank { defaultFolderName }
                    newFolderDialog = false
                    vm.createFolder(n, newFolderParent)
                }) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    renameTarget?.let { (kind, id) ->
        RenameDialog(
            value = renameValue, onValue = { renameValue = it },
            onConfirm = {
                when (kind) { "folder" -> vm.renameFolder(id, renameValue); "layer" -> vm.renameLayer(id, renameValue) }
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
    moveTarget?.let { (kind, id) ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text(stringResource(R.string.dialog_move_to_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { applyMove(vm, kind, id, null); moveTarget = null }) { Text(stringResource(R.string.label_root)) }
                    folders.forEach { f -> TextButton(onClick = { applyMove(vm, kind, id, f.id); moveTarget = null }) { Text(f.name) } }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { moveTarget = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

/**
 * Renommer un dossier ou une couche : le champ a le focus des l'ouverture - on vient pour taper, et le
 * clavier sort d'emblee -, et il prend toute la largeur de la fenetre, quelle que soit la longueur du nom.
 * Il s'adaptait a son contenu : un nom court donnait un champ etroit, ou l'on visait mal.
 */
@Composable
internal fun RenameDialog(
    value: String,
    onValue: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            CompactOutlinedTextField(value, onValue, singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("rename_field"))
            // DANS la fenetre, et non a cote : elle compose son contenu dans sa propre fenetre, apres
            // l'appelant ; demande de l'exterieur, le focus visait un champ pas encore pose, et levait.
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
