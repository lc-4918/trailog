package fr.lc4918.trailog.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Folder as FolderOutlined
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.BasemapFolderEntity
import fr.lc4918.trailog.data.db.CompositeEntity
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.map.compositeBasemapId
import fr.lc4918.trailog.map.flagAssetModel
import fr.lc4918.trailog.map.flagCodeFor
import fr.lc4918.trailog.ui.routes.DropPosition
import kotlinx.coroutines.launch

private enum class BHoverZone { BEFORE, INTO, AFTER }
private data class BDragInfo(val kind: String, val id: String, val offset: Float)
private data class BHoverTarget(val kind: String, val id: String, val zone: BHoverZone)
/** Bornes mesurées d'une ligne (position root Y, hauteur réelle) utilisées pour la détection de zone de dépose. */
private data class BRowBounds(val top: Float, val height: Float)

private class BDragCtx(
    val rowBounds: MutableMap<Pair<String, String>, BRowBounds>,
    val dragInfo: BDragInfo?,
    val hoverTarget: BHoverTarget?,
    val onStart: (String, String) -> Unit,
    val onDrag: (String, String, Float) -> Unit,
    val onEnd: (String, String) -> Unit,
)

private fun combinedBasemapChildren(
    parentId: Long?, folders: List<BasemapFolderEntity>, providers: List<ProviderEntity>, composites: List<CompositeEntity>,
): List<Any> {
    val f = folders.filter { it.parentId == parentId }
    // Le relief (DEM) reste toujours visible dans l'arbre, activé ou non : son "enabled" sert désormais de
    // bascule tap-pour-activer/désactiver (bug relief), pas de filtre de visibilité comme les autres fonds
    // sinon un tap qui l'éteint le ferait disparaître, sans plus aucun moyen de le rallumer.
    val p = providers.filter { (it.enabled || it.type == "DEM") && it.folderId == parentId }
    val c = composites.filter { it.enabled && it.folderId == parentId }
    fun order(e: Any) = when (e) { is BasemapFolderEntity -> e.sortOrder; is ProviderEntity -> e.sortOrder; is CompositeEntity -> e.sortOrder; else -> 0 }
    fun typeRank(e: Any) = when (e) { is BasemapFolderEntity -> 0; is ProviderEntity -> 1; else -> 2 }
    return (f + p + c).sortedWith(compareBy({ order(it) }, { typeRank(it) }))
}

/** Clé stable (kind, id) d'un noeud, pour préserver l'identité de composition (état "expanded", drag)
 *  quand la liste se réordonne (drag & drop, activation/désactivation d'un provider ailleurs, etc.). */
private fun basemapNodeKey(item: Any): Pair<String, String> = when (item) {
    is BasemapFolderEntity -> "folder" to item.id.toString()
    is ProviderEntity -> "provider" to item.id
    is CompositeEntity -> "composite" to item.id.toString()
    else -> "unknown" to item.toString()
}

private fun isDescendantBasemapFolder(candidateId: Long, ancestorId: Long, folders: List<BasemapFolderEntity>): Boolean {
    var cur = folders.firstOrNull { it.id == candidateId }?.parentId
    while (cur != null) {
        if (cur == ancestorId) return true
        cur = folders.firstOrNull { it.id == cur }?.parentId
    }
    return false
}

private fun strongHaptic(context: Context) {
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

/**
 * Détecteur de geste unique (tap OU appui long + glisser), remplace un couple `clickable` + drag séparé :
 * deux détecteurs de gestes indépendants sur la même ligne se neutralisent en pratique (le `clickable` du
 * parent consomme l'évènement "down" pour son ripple, ce qui annule la détection d'appui long de l'enfant).
 * Si le doigt bouge au-delà du seuil de toucher AVANT la fin du délai d'appui long, l'évènement n'est PAS
 * consommé : le parent (ici le `verticalScroll` du panneau) reprend la main normalement pour faire défiler.
 */
private suspend fun PointerInputScope.detectTapOrLongPressDrag(
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downTime = System.currentTimeMillis()
        var dragging = false
        var totalDy = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (dragging) onDragEnd()
                else if (System.currentTimeMillis() - downTime < viewConfiguration.longPressTimeoutMillis) onTap()
                change.consume()
                break
            }
            if (dragging) {
                totalDy += change.positionChange().y
                onDrag(totalDy)
                change.consume()
            } else {
                val elapsed = System.currentTimeMillis() - downTime
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                when {
                    elapsed >= viewConfiguration.longPressTimeoutMillis -> {
                        dragging = true
                        onDragStart()
                        change.consume()
                    }
                    dist > viewConfiguration.touchSlop -> break // laisse le parent (scroll) gérer ce mouvement
                    else -> change.consume()
                }
            }
        }
    }
}

/** Panneau latéral droit du Basemap Control : fonds activés, organisation en dossiers, drag & drop
 *  (appui long sur le nom, pas de poignée). Tap sur un fond -> le sélectionne et ferme le panneau ;
 *  tap sur le relief -> bascule son activation sans fermer le panneau ni changer le fond courant. */
@Composable
fun BasemapControlPanel(
    folders: List<BasemapFolderEntity>,
    providers: List<ProviderEntity>,
    composites: List<CompositeEntity>,
    currentBasemapId: String,
    widthFraction: Float,
    backgroundAlpha: Float,
    onSelect: (String) -> Unit,
    onCreateFolder: (String, Long?) -> Unit,
    onReorderDrop: suspend (String, String, String, String, DropPosition) -> Unit,
    onToggleRelief: (String) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val rowBounds = remember { mutableStateMapOf<Pair<String, String>, BRowBounds>() }
    var dragInfo by remember { mutableStateOf<BDragInfo?>(null) }
    val hoverTarget: BHoverTarget? = dragInfo?.let { info ->
        val start = rowBounds[info.kind to info.id] ?: return@let null
        val centerY = start.top + start.height / 2f + info.offset
        val hit = rowBounds.entries.firstOrNull { (k, b) ->
            centerY in b.top..(b.top + b.height) && !(k.first == info.kind && k.second == info.id)
        } ?: return@let null
        val (key, bounds) = hit
        val rel = (centerY - bounds.top) / bounds.height
        val zone = when {
            rel < 0.25f -> BHoverZone.BEFORE
            rel > 0.75f -> BHoverZone.AFTER
            key.first == "folder" -> BHoverZone.INTO
            rel < 0.5f -> BHoverZone.BEFORE
            else -> BHoverZone.AFTER
        }
        if (info.kind == "folder") {
            val prospectiveId = if (zone == BHoverZone.INTO) key.second.toLongOrNull() else when (key.first) {
                "folder" -> folders.firstOrNull { it.id.toString() == key.second }?.parentId
                "provider" -> providers.firstOrNull { it.id == key.second }?.folderId
                else -> composites.firstOrNull { it.id.toString() == key.second }?.folderId
            }
            val selfId = info.id.toLongOrNull()
            if (prospectiveId != null && selfId != null &&
                (prospectiveId == selfId || isDescendantBasemapFolder(prospectiveId, selfId, folders))) {
                return@let null
            }
        }
        BHoverTarget(key.first, key.second, zone)
    }
    val context = LocalContext.current
    val dctx = BDragCtx(
        rowBounds = rowBounds, dragInfo = dragInfo, hoverTarget = hoverTarget,
        onStart = { kind, id -> strongHaptic(context); dragInfo = BDragInfo(kind, id, 0f) },
        onDrag = { kind, id, total -> if (dragInfo?.kind == kind && dragInfo?.id == id) dragInfo = dragInfo!!.copy(offset = total) },
        onEnd = { kind, id ->
            val info = dragInfo; val target = hoverTarget
            if (info != null && info.kind == kind && info.id == id && target != null) {
                val position = when (target.zone) { BHoverZone.BEFORE -> DropPosition.BEFORE; BHoverZone.INTO -> DropPosition.INTO; BHoverZone.AFTER -> DropPosition.AFTER }
                scope.launch { onReorderDrop(kind, id, target.kind, target.id, position); dragInfo = null }
            } else dragInfo = null
        },
    )

    var newFolderDialog by remember { mutableStateOf(false) }

    // Surface (et non Box+background) : fournit LocalContentColor adapté au thème pour le texte/les icônes
    // du panneau. Un Box+background nu laisse LocalContentColor à sa valeur par défaut (noir), d'où les
    // textes/icônes illisibles en thème sombre.
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(widthFraction).fillMaxHeight(),
    ) {
        // Défilement désactivé pendant un drag actif : sans ça, le scroll du panneau peut reprendre la main
        // sur le geste en cours de route et casser le suivi du glisser (indicateurs de zone qui "sautent").
        // CompositionLocalProvider : désactive le plancher tactile de 48dp que Material3 impose par défaut
        // aux IconButton (cf. Groupe N) nécessaire à la fois pour le header 1 ligne compact (section 7.1) et pour
        // que le chevron plie/déplie des dossiers fasse bien 32dp comme le Spacer d'alignement des basemaps
        // racine, sans quoi les deux ne s'alignent pas (section 7.2).
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState(), enabled = dragInfo == null)) {
            Box(Modifier.fillMaxWidth().padding(4.dp)) {
                IconButton(onClick = { newFolderDialog = true }, modifier = Modifier.align(Alignment.CenterStart).size(32.dp)) {
                    Icon(Icons.Filled.CreateNewFolder, stringResource(R.string.label_new_folder), Modifier.size(18.dp))
                }
                // Décalé au maximum vers l'angle haut-droit du gestionnaire (SPEC section 7.1).
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(18.dp))
                }
            }
            HorizontalDivider()
            combinedBasemapChildren(null, folders, providers, composites).forEach { item ->
                key(basemapNodeKey(item)) {
                    BasemapNode(item, folders, providers, composites, 0, dctx, currentBasemapId, onSelect, onToggleRelief)
                }
            }
        }
        }
    }

    if (newFolderDialog) {
        var name by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text(stringResource(R.string.label_new_folder)) },
            text = { CompactOutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.focusRequester(focus)) },
            confirmButton = {
                TextButton(onClick = {
                    onCreateFolder(name.ifBlank { null } ?: return@TextButton, null); newFolderDialog = false
                }) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun BasemapNode(
    item: Any, allFolders: List<BasemapFolderEntity>, allProviders: List<ProviderEntity>, allComposites: List<CompositeEntity>,
    depth: Int, dctx: BDragCtx, currentBasemapId: String, onSelect: (String) -> Unit, onToggleRelief: (String) -> Unit,
) {
    when (item) {
        is BasemapFolderEntity -> {
            var expanded by remember { mutableStateOf(true) }
            val key = "folder" to item.id.toString()
            val isDragging = dctx.dragInfo?.kind == "folder" && dctx.dragInfo.id == item.id.toString()
            val offset = if (isDragging) dctx.dragInfo.offset else 0f
            val hoverZone = dctx.hoverTarget?.takeIf { it.kind == "folder" && it.id == item.id.toString() }?.zone
            // pointerInput(item.id) ne relance jamais son bloc tant que l'id ne change pas : sans
            // rememberUpdatedState, la coroutine de geste resterait figée sur le dctx de la toute première
            // composition (hoverTarget alors toujours null) et le drop ne ferait jamais rien (bug 1.1).
            val currentDctx by rememberUpdatedState(dctx)

            if (hoverZone == BHoverZone.BEFORE) DropLine()
            Row(Modifier.fillMaxWidth()
                .onGloballyPositioned { dctx.rowBounds[key] = BRowBounds(it.positionInRoot().y, it.size.height.toFloat()) }
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer { translationY = offset; alpha = if (isDragging) 0.85f else 1f }
                .background(if (hoverZone == BHoverZone.INTO) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                .padding(start = (12 + depth * 16).dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Chevron aligné à gauche de sa zone tactile (au lieu du centrage par défaut d'IconButton) :
                // sans ça, son icône (24dp) centrée dans les 32dp du bouton retombe 4dp plus à droite que
                // l'icône d'une couche à plat, cassant l'alignement caret/icône demandé (bug 1.2).
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        Icon(if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight, null)
                    }
                }
                // Couleur adaptée au thème (clair/sombre), pas figée : contour/remplissage noir en thème
                // clair, blanc en thème sombre, dans les deux cas (ouvert ou fermé) (bug 4.1).
                // Icons.Filled.FolderOpen dessine un dossier "entrouvert" dont seul l'onglet arrière est
                // rempli (le reste en contour) : on garde la MÊME silhouette que la version fermée
                // (Icons.Filled.Folder, pleine) pour un remplissage total quand le dossier est ouvert.
                Icon(if (expanded) Icons.Filled.Folder else Icons.Outlined.FolderOutlined, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text(item.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                    .pointerInput(item.id) {
                        detectTapOrLongPressDrag(
                            onTap = {},
                            onDragStart = { currentDctx.onStart("folder", item.id.toString()) },
                            onDrag = { total -> currentDctx.onDrag("folder", item.id.toString(), total) },
                            onDragEnd = { currentDctx.onEnd("folder", item.id.toString()) },
                        )
                    })
            }
            if (hoverZone == BHoverZone.AFTER) DropLine()
            if (expanded) {
                combinedBasemapChildren(item.id, allFolders, allProviders, allComposites).forEach { child ->
                    key(basemapNodeKey(child)) {
                        BasemapNode(child, allFolders, allProviders, allComposites, depth + 1, dctx, currentBasemapId, onSelect, onToggleRelief)
                    }
                }
            }
        }
        // Le relief n'est jamais "sélectionné" comme fond visuel (tuiles DEM brutes) : le tap bascule son
        // activation (surbrillance = actif), au lieu de remplacer le fond courant comme les autres fonds.
        is ProviderEntity -> if (item.type == "DEM") BasemapLeaf(
            kind = "provider", id = item.id, name = item.name, depth = depth,
            selected = item.enabled, dctx = dctx, onSelect = { onToggleRelief(item.id) },
        ) { BasemapIcon(item) } else BasemapLeaf(
            kind = "provider", id = item.id, name = item.name, depth = depth,
            selected = item.id == currentBasemapId, dctx = dctx, onSelect = { onSelect(item.id) },
        ) { BasemapIcon(item) }
        is CompositeEntity -> BasemapLeaf(
            kind = "composite", id = item.id.toString(), name = item.name, depth = depth,
            selected = compositeBasemapId(item.id) == currentBasemapId, dctx = dctx,
            onSelect = { onSelect(compositeBasemapId(item.id)) },
        ) { Icon(Icons.Filled.Layers, null, Modifier.size(20.dp)) }
    }
}

@Composable
private fun BasemapLeaf(
    kind: String, id: String, name: String, depth: Int, selected: Boolean, dctx: BDragCtx,
    onSelect: () -> Unit, icon: @Composable () -> Unit,
) {
    val key = kind to id
    val isDragging = dctx.dragInfo?.kind == kind && dctx.dragInfo.id == id
    val offset = if (isDragging) dctx.dragInfo.offset else 0f
    val hoverZone = dctx.hoverTarget?.takeIf { it.kind == kind && it.id == id }?.zone
    // Cf. commentaire équivalent dans BasemapNode (bug 1.1) : sans ça, le drop ne déclenche jamais rien.
    val currentDctx by rememberUpdatedState(dctx)

    if (hoverZone == BHoverZone.BEFORE) DropLine()
    Row(Modifier.fillMaxWidth()
        .onGloballyPositioned { dctx.rowBounds[key] = BRowBounds(it.positionInRoot().y, it.size.height.toFloat()) }
        .zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer { translationY = offset; alpha = if (isDragging) 0.85f else 1f }
        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
        // Indentation basée sur la profondeur du DOSSIER PARENT (depth - 1), pas la profondeur propre de la
        // couche : une couche imbriquée doit s'aligner sur l'icône de son dossier contenant, pas creuser un
        // niveau d'indentation supplémentaire pour elle-même. À la racine (depth 0), aucune indentation.
        .padding(start = (12 + (depth - 1).coerceAtLeast(0) * 16).dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Réservée seulement à partir d'un niveau imbriqué, pour aligner l'icône sur celle du dossier
        // parent : à la racine, une couche est à plat, sans indentation (bug 1.2).
        if (depth > 0) Spacer(Modifier.width(32.dp))
        icon()
        Spacer(Modifier.width(8.dp))
        Text(name, modifier = Modifier.weight(1f)
            .pointerInput(id) {
                detectTapOrLongPressDrag(
                    onTap = onSelect,
                    onDragStart = { currentDctx.onStart(kind, id) },
                    onDrag = { total -> currentDctx.onDrag(kind, id, total) },
                    onDragEnd = { currentDctx.onEnd(kind, id) },
                )
            })
    }
    if (hoverZone == BHoverZone.AFTER) DropLine()
}

/** Drapeau du pays (asset SVG bundlé) pour les fonds nationaux détectés, sinon globe générique. */
@Composable
private fun BasemapIcon(p: ProviderEntity) {
    val code = flagCodeFor(p)
    if (code != null) {
        AsyncImage(model = flagAssetModel(code), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(2.dp)))
    } else {
        Icon(Icons.Filled.Public, null, Modifier.size(20.dp))
    }
}

@Composable
private fun DropLine() {
    Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary))
}
