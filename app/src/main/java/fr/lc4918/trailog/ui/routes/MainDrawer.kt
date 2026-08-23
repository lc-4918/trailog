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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Place
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.toColorInt
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.ui.components.Avatar
import fr.lc4918.trailog.ui.components.ColorPickerDialog
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField
import fr.lc4918.trailog.routing.GpxWriter
import fr.lc4918.trailog.ui.settings.ProvideSettingsPalette
import fr.lc4918.trailog.ui.settings.settingsPalette
import fr.lc4918.trailog.ui.settings.SettingsCard
import fr.lc4918.trailog.ui.settings.SetRow
import fr.lc4918.trailog.ui.settings.RowDivider
import fr.lc4918.trailog.ui.settings.ValueText
import fr.lc4918.trailog.ui.theme.isDarkTheme
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
        DrawerIcon(
            if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
            if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
            size = DrawerChevronSize, onClick = { expanded = !expanded },
        )
        DrawerIcon(
            if (allVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            if (allVisible) stringResource(R.string.action_hide_folder) else stringResource(R.string.action_show_folder),
            onClick = { vm.setFolderVisible(folder.id, !allVisible) },
        )
        // Couleur adaptée au thème (clair/sombre), pas figée : contour (fermé) ou remplissage (ouvert)
        // noir en thème clair, blanc en thème sombre (bug 4.1). Même silhouette pleine (Filled.Folder) dans
        // les deux états : Filled.FolderOpen ne remplit que l'onglet arrière, pas tout le dossier.
        Icon(if (expanded) Icons.Filled.Folder else Icons.Outlined.Folder, null,
            Modifier.size(DrawerIconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        // Nom d'un dossier : gras et en capitales. Il ne porte pas de couleur, contrairement a une couche,
        // et n'a que sa graisse pour se distinguer de ce qu'il contient.
        Text(folder.name.uppercase(), fontSize = DrawerNameSp.sp, lineHeight = (DrawerNameSp * 1.25f).sp,
            fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        // Nombre de couches sous le dossier, sous-dossiers compris : c'est ce que ses actions touchent
        // (l'oeil, la couleur commune), et ce qu'un dossier replie cache.
        if (contents.isNotEmpty()) {
            Text("${contents.size}", fontSize = DrawerCountSp.sp, lineHeight = (DrawerCountSp * 1.3f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Poignee et menu colles : ce sont les deux prises de la ligne, pas deux elements a distinguer.
        // L'ecart de la ligne les separerait autant que le nom du compteur, qui n'ont rien a voir entre eux.
        RowEndActions {
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
                is LayerEntity -> key("layer", item.id) { LayerRow(item, depth + 1, vm, dctx, onRename, onMove, onZoom, layerActions) }
            }
        }
    }
}

/** Ligne « import en cours » : petit spinner, indenté comme les couches du dossier. */
@Composable
internal fun ImportSpinnerRow(depth: Int, elevation: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = (4 + depth * 20).dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
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
    onRename: (String, Long, String) -> Unit, onMove: (String, Long) -> Unit, onZoom: (String, Long) -> Unit,
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
        onRename = { onRename("layer", layer.id, layer.name) }, onMove = { onMove("layer", layer.id) },
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
    onRename: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit, onZoom: () -> Unit,
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
        DrawerIcon(
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
        Text(name, fontSize = DrawerNameSp.sp, lineHeight = (DrawerNameSp * 1.25f).sp, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.weight(1f))
        RowEndActions {
            DragHandle(
                onStart = { strongHaptic(context); dctx.onStart(kind, id) },
                onDrag = { dctx.onDrag(kind, id, it) },
                onEnd = { dctx.onEnd(kind, id) })
            RowMenu(
                onRename = onRename, onMove = onMove, onNewSub = null, onDelete = onDelete, onZoom = onZoom,
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).focusRequester(focus),
        placeholder = { Text(stringResource(R.string.search_placeholder), fontSize = DrawerNameSp.sp) },
        leadingIcon = {
            Icon(Icons.Filled.Search, null, Modifier.size(DrawerIconSize),
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

@Composable
internal fun DropIndicatorLine() {
    Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary))
}

/*
 * Mesures du menu lateral, reprises telles quelles de la maquette (captures/trailog-drawer-styles.html).
 * Elles sont ici et non a l'appel : une ligne d'arbre est faite de cinq elements que trois composables se
 * partagent, et les voir cote a cote est le seul moyen de garder la grille droite.
 */
/*
 * Toutes ces mesures ont ete relevees d'un cran (~15 %) par rapport a la maquette : a l'echelle du dessin,
 * l'arbre se lisait juste, mais du bout du doigt il se visait mal. Elles montent ENSEMBLE - le rapport
 * entre le chevron, l'oeil, le nom et la poignee fait la ligne, pas leurs valeurs prises une a une.
 */
/** Retrait de chaque niveau d'imbrication. */
internal val DrawerIndent = 17.dp

/** Marges d'une ligne : le retrait de depart, puis ce qui la separe de la suivante. */
internal val DrawerRowPadH = 7.dp

internal val DrawerRowPadV = 6.dp

/** Ecart entre deux elements d'une ligne (chevron, oeil, symbole, nom...). */
internal val DrawerRowGap = 7.dp

/** Chevron, oeil, symbole : trois tailles voisines, pas une seule - l'oeil et le symbole portent la ligne,
 *  le chevron n'est qu'un accessoire de pliage. */
internal val DrawerChevronSize = 16.dp

internal val DrawerIconSize = 17.dp

/** Cible tactile posee autour de ces petites icones : ce qu'on peut prendre sans grossir le dessin. */
internal val DrawerHitSize = 30.dp

/** Nom d'une couche, et compteur d'un dossier. */
internal val DrawerNameSp = 13f

internal val DrawerCountSp = 10.5f

/** Action de l'en-tete du menu : cible de 38 dp, dessin de 18 - un cran au-dessus de la maquette, comme
 *  le reste du tiroir. */
@Composable
internal fun HeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
    showLabel: Boolean = false, active: Boolean = false, onClick: () -> Unit,
) {
    // Allume, seul le DESSIN change de couleur. Un aplat derriere lui - meme leger - lui donnait un poids
    // que ses voisins n'ont pas, et le bouton paraissait plus gros alors que sa cible fait les memes
    // 44 dp. C'est la regle des boutons de la carte, ou l'etat se lit a la couleur du trait.
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    if (!showLabel) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size(22.dp), tint = tint)
        }
        return
    }
    // Icone et libelle dans une meme pastille cliquable : le texte n'est pas une legende posee a cote du
    // bouton, il en fait partie, et un appui dessus vaut un appui sur l'icone.
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick)
            .padding(start = 11.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tint, maxLines = 1)
    }
}

/**
 * Une ligne de l'arbre du menu lateral : le retrait de son niveau, et ce que le drag lui fait.
 *
 * Le retrait est celui de la maquette (15 dp par niveau) et non l'indentation d'une liste ordinaire : cet
 * arbre descend a trois ou quatre niveaux sur un ecran de telephone, et 15 dp est ce qui reste lisible
 * sans manger la moitie de la largeur au dernier.
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
            .padding(
                start = DrawerRowPadH + DrawerIndent * depth, end = DrawerRowPadH,
                top = DrawerRowPadV, bottom = DrawerRowPadV,
            ),
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
    Icon(
        Icons.Filled.DragIndicator, stringResource(R.string.action_drag_to_move),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.size(DrawerIconSize).pointerInput(Unit) {
            var total = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { total = 0f; currentOnStart() },
                onDrag = { change, amount -> change.consume(); total += amount.y; currentOnDrag(total) },
                onDragEnd = { currentOnEnd() }, onDragCancel = { currentOnEnd() },
            )
        },
    )
}

/** 3 points : menu contextuel (appui simple). */
@Composable
internal fun RowMenu(
    onRename: () -> Unit, onMove: () -> Unit, onNewSub: (() -> Unit)?, onDelete: () -> Unit, onZoom: () -> Unit,
    // Propre au dossier, et seulement s'il porte des couches : une couche a deja sa pastille de couleur
    // dans sa ligne, et un dossier vide n'a rien a colorer - l'entree disparait plutot que de ne rien faire.
    onColor: (() -> Unit)? = null,
    layer: LayerEntity? = null, layerActions: LayerActions? = null,
    // Propre au dossier : le total de ce qu'il contient, sous-dossiers compris.
    onStats: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(DrawerHitSize).clickable { open = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more), Modifier.size(DrawerIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // Un dossier cadre TOUTES ses couches, pas une : le libelle le dit au pluriel.
            DropdownMenuItem(
                text = {
                    Text(stringResource(
                        if (onNewSub != null) R.string.action_zoom_to_layers else R.string.action_zoom_to_layer
                    ))
                },
                onClick = { open = false; onZoom() },
            )
            if (onStats != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_folder_stats)) },
                    onClick = { open = false; onStats() })
            }
            if (onColor != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_color_layers)) },
                    onClick = { open = false; onColor() })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { open = false; onRename() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_move)) }, onClick = { open = false; onMove() })
            if (onNewSub != null) DropdownMenuItem(text = { Text(stringResource(R.string.action_new_subfolder)) }, onClick = { open = false; onNewSub() })
            // Les SORTIES d'une couche seulement. Les retouches, elles, ont quitte ce menu pour la barre
            // d'outils de la carte : elles agissent sur un segment, parfois sur deux, et designer un
            // segment se fait du doigt sur la carte - pas dans le menu d'une ligne d'arborescence.
            if (layer != null && layerActions != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_export_layer)) },
                    onClick = { open = false; layerActions.onExport(layer) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_share)) },
                    onClick = { open = false; layerActions.onShare(layer) })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { open = false; onDelete() })
        }
    }
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
    showOfflineButton: Boolean, onDownloadOffline: () -> Unit,
    onZoom: (String, Long) -> Unit,
    // Un geste du tiroir n'a rien produit : l'ecran le dit. Un rappel etroit plutot que le porteur des
    // boites, dont le tiroir n'a aucune raison de lire le reste (cf. MainDialogState.failure).
    onFailure: (Int) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val rowPx = with(LocalDensity.current) { 52.dp.toPx() }
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
    val layerActions = LayerActions(onExport = onExportLayer, onShare = onShareLayer)
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

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header 2 lignes à hauteur totale inchangée (SPEC section 6.1) : l'ancien Row faisait 48dp de
        // contenu (IconButton) + 32dp de padding vertical = 80dp. On désactive le plancher tactile
        // de 48dp de Material3 (cf. Groupe N) pour tenir 2 lignes de 32dp dans le même budget.
        Box(Modifier.fillMaxWidth()) {
            // Plus de marge sous la bande grise : l'arborescence commence juste dessous, la bande faisant
            // desormais la separation a elle seule.
            Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(34.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clickable(onClick = onSettings)) {
                            Avatar(settings.avatarSource, size = 30.dp, contentDescription = stringResource(R.string.settings_title))
                        }
                        Spacer(Modifier.width(8.dp))
                        // Meme taille et meme graisse que le titre "Reglages" (17 sp, semi-gras) : ce sont
                        // les deux titres d'ecran de l'application, et rien ne justifierait qu'ils se lisent
                        // a deux tailles. La maquette du tiroir en donnait 15 ; celle des reglages, plus
                        // recente, en donne 17, et c'est elle qui fait foi pour les deux.
                        // Fallback traduit si le titre personnalise est vide, au lieu de ne rien afficher.
                        val title = settings.customTitle.ifBlank { stringResource(R.string.drawer_default_title) }
                            ?: stringResource(R.string.drawer_default_title)
                        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(11.dp))
                    // Les actions du header, sur la seule bande grise de l'en-tete : c'est elle qui les
                    // rassemble, et le titre au-dessus s'en trouve rendu au fond du tiroir.
                    //
                    // "Importer" et "Telecharger" portent leur libelle, et tout le bouton - icone comme
                    // texte - declenche l'action : ce sont les deux gestes qu'on vient chercher ici, et
                    // une icone seule ne dit pas ce qu'elle importe ni ce qu'elle telecharge. "Nouveau
                    // dossier" garde l'icone seule, universelle, et laisse la place aux deux autres.
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeaderAction(Icons.Outlined.CreateNewFolder, stringResource(R.string.label_new_folder)) { openNewFolder(null) }
                        HeaderAction(Icons.Outlined.FileUpload, stringResource(R.string.action_import),
                            showLabel = true, onClick = onImport)
                        if (showOfflineButton) {
                            HeaderAction(Icons.Outlined.FileDownload, stringResource(R.string.offline_action_download),
                                showLabel = true, onClick = onDownloadOffline)
                        }
                        // La recherche est a l'oppose des trois autres : elle ne cree ni n'importe rien,
                        // elle change la facon de LIRE ce qui est en dessous. Le vide entre elle et les
                        // autres dit cette difference mieux qu'un filet.
                        Spacer(Modifier.weight(1f))
                        HeaderAction(
                            Icons.Filled.Search, stringResource(R.string.search_placeholder),
                            active = searchOpen,
                        ) {
                            searchOpen = !searchOpen
                            // Refermer la barre efface la recherche : la garder filtrerait l'arborescence
                            // sans que rien a l'ecran ne dise pourquoi elle est incomplete.
                            if (!searchOpen) { searchQuery = ""; focusManager.clearFocus() }
                        }
                    }
                }
                // Décalé au maximum vers l'angle haut-droit (SPEC section 6.1), superposé aux 2 lignes ci-dessus
                // sans agrandir la hauteur du Box (32dp < hauteur totale du Column).
                // Meme marge de bord que les lignes de l'arborescence, et meme cible de 30 dp : la croix
                // tombe donc exactement sur la colonne des menus "trois points" des couches. A 14 dp, elle
                // s'en decalait de sept, ce qui se voyait comme un defaut d'alignement sans qu'on sache
                // lequel des deux etait de travers.
                Box(
                    Modifier.align(Alignment.TopEnd).padding(end = DrawerRowPadH).size(DrawerHitSize)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close_menu), Modifier.size(17.dp))
                }
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
                        stringResource(R.string.search_no_result), fontSize = DrawerNameSp.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                found.forEach { item ->
                    key("found", item.id) {
                        LayerRow(item, 0, vm, dctx, openRename, openMove, onZoom, layerActions)
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
                    is LayerEntity -> key("layer", item.id) { LayerRow(item, 0, vm, dctx, openRename, openMove, onZoom, layerActions) }
                }
            }
        }
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
        LaunchedEffect(Unit) { focus.requestFocus() }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text(stringResource(R.string.label_new_folder)) },
            text = {
                CompactOutlinedTextField(newFolderName, { newFolderName = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
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
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.action_rename)) },
            text = { CompactOutlinedTextField(renameValue, { renameValue = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    when (kind) { "folder" -> vm.renameFolder(id, renameValue); "layer" -> vm.renameLayer(id, renameValue) }
                    renameTarget = null
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
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
