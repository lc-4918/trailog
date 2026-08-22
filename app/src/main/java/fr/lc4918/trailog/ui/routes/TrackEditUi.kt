package fr.lc4918.trailog.ui.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.R
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Link
import fr.lc4918.trailog.ui.edit.CutBubblePlacement
import fr.lc4918.trailog.ui.edit.EditTool
import fr.lc4918.trailog.ui.edit.TrackEditState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.repo.TrailogRepository
import fr.lc4918.trailog.domain.geo.TrackEdit
import fr.lc4918.trailog.domain.model.TrackPoint
import fr.lc4918.trailog.ui.components.MapActionBar
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.MapPromptBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La retouche d'une trace, cote ecran : la barre d'outils, la bulle qui marque le point de coupe, et la
 * bande de consigne du bas - chacune pour elle-meme, puis toutes ensemble dans [TrackEditLayer].
 *
 * Les calculs, eux, sont ailleurs et depuis toujours (`domain/geo/TrackEdit`, `ui/edit`) : ce fichier ne
 * porte que ce qui se voit.
 */

/*
 * Bulle du marqueur de coupe. Sa POINTE marque le point, le corps se tenant a cote - au-dessus, en
 * dessous, a gauche ou a droite selon ce que la trace laisse libre (cf. CutBubblePlacement) : c'est la
 * seule facon de designer un endroit sans le masquer, et cet endroit-la est precisement celui qu'on
 * regarde avant de confirmer.
 */
internal val CutBubbleWidth = 30.dp

internal val CutBubbleHeight = 28.dp

internal val CutTailHeight = 8.dp

internal val CutTailWidth = 11.dp

/** Air laisse autour de la bulle quand la carte se decale pour la degager : sans elle, la bulle
 *  affleurerait le bord de l'ecran, ce qui se lit comme un objet coupe. */
internal val CutBubbleMargin = 12.dp

/**
 * Marqueur de coupe : les ciseaux dans une bulle dont la pointe touche le point de coupe.
 *
 * Pose par son coin haut-gauche (cf. son appelant) : la pointe tombe alors exactement sur le point vise.
 */
@Composable
internal fun CutMarkerBubble(
    side: CutBubblePlacement.Side, bg: Color, fg: Color, modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // La pointe change de cote AVEC le cote retenu : bulle en haut, elle descend de son bord bas ; bulle a
    // droite, elle part de son bord gauche. Dans les quatre cas, son sommet tombe sur le point.
    val shape = remember(density, side) {
        val tail = with(density) { CutTailHeight.toPx() }
        val tailW = with(density) { CutTailWidth.toPx() }
        val radius = with(density) { 8.dp.toPx() }
        GenericShape { size, _ ->
            // Le corps recule du cote de la pointe, qui occupe la place ainsi liberee.
            val left = if (side == CutBubblePlacement.Side.RIGHT) tail else 0f
            val top = if (side == CutBubblePlacement.Side.BOTTOM) tail else 0f
            val right = size.width - if (side == CutBubblePlacement.Side.LEFT) tail else 0f
            val bottom = size.height - if (side == CutBubblePlacement.Side.TOP) tail else 0f
            addRoundRect(RoundRect(Rect(left, top, right, bottom), CornerRadius(radius, radius)))
            val cx = (left + right) / 2
            val cy = (top + bottom) / 2
            when (side) {
                CutBubblePlacement.Side.TOP -> {
                    moveTo(cx - tailW / 2, bottom); lineTo(cx, size.height); lineTo(cx + tailW / 2, bottom)
                }
                CutBubblePlacement.Side.BOTTOM -> {
                    moveTo(cx - tailW / 2, top); lineTo(cx, 0f); lineTo(cx + tailW / 2, top)
                }
                CutBubblePlacement.Side.LEFT -> {
                    moveTo(right, cy - tailW / 2); lineTo(size.width, cy); lineTo(right, cy + tailW / 2)
                }
                CutBubblePlacement.Side.RIGHT -> {
                    moveTo(left, cy - tailW / 2); lineTo(0f, cy); lineTo(left, cy + tailW / 2)
                }
            }
            close()
        }
    }
    val horizontal = side == CutBubblePlacement.Side.LEFT || side == CutBubblePlacement.Side.RIGHT
    Box(
        modifier
            .size(
                width = CutBubbleWidth + if (horizontal) CutTailHeight else 0.dp,
                height = CutBubbleHeight + if (horizontal) 0.dp else CutTailHeight,
            )
            .shadow(3.dp, shape)
            // Les memes couleurs que les boutons poses sur la carte : blanc sur noir en theme sombre,
            // noir sur blanc en clair. La bulle est un ornement de carte comme eux, elle ne peut pas
            // suivre une autre regle a deux centimetres de la barre d'outils.
            .background(bg, shape)
            .padding(
                start = if (side == CutBubblePlacement.Side.RIGHT) CutTailHeight else 0.dp,
                top = if (side == CutBubblePlacement.Side.BOTTOM) CutTailHeight else 0.dp,
                end = if (side == CutBubblePlacement.Side.LEFT) CutTailHeight else 0.dp,
                bottom = if (side == CutBubblePlacement.Side.TOP) CutTailHeight else 0.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.ContentCut, null, Modifier.size(16.dp), tint = fg)
    }
}

/**
 * Barre de retouche des traces : inverser, couper, joindre, et defaire.
 *
 * Verticale et du meme cote que le bouton qui l'ouvre - la colonne de gauche -, sans quoi le regard
 * traverserait l'ecran entre le geste et son resultat. Les trois premiers boutons sont des OUTILS : ils
 * s'allument, et le prochain tap sur une trace leur revient. Le quatrieme est une action immediate, d'ou
 * sa separation.
 */
@Composable
internal fun BoxWithConstraintsScope.TrackEditToolbar(
    state: TrackEditState,
    canUndo: Boolean,
    chromeBg: Color,
    chromeFg: Color,
    /** Bas de la colonne de commandes du coin haut-gauche : la barre ne remonte jamais dessus. */
    topControlsHeightPx: Int,
    onUndo: () -> Unit,
) {
    // A mi-hauteur de l'ecran, sauf si les commandes du haut descendent plus bas : c'est alors sous elles
    // qu'elle se pose, dans la meme colonne et au meme ecart que ses voisines.
    var toolbarHeightPx by remember { mutableIntStateOf(0) }
    val gapPx = with(LocalDensity.current) { MapControlSpacing.roundToPx() }
    val topPx = maxOf((constraints.maxHeight - toolbarHeightPx) / 2, topControlsHeightPx + gapPx)
    Column(
        Modifier.align(Alignment.TopStart)
            .offset { IntOffset(0, topPx) }
            .padding(start = 8.dp)
            .onGloballyPositioned { toolbarHeightPx = it.size.height }
            .background(chromeBg.copy(alpha = ControlButtonBgAlpha), RoundedCornerShape(ControlButtonRadius))
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EditToolButton(Icons.Filled.Autorenew, stringResource(R.string.action_reverse_track),
            active = state.tool == EditTool.REVERSE, chromeFg = chromeFg) { state.choose(EditTool.REVERSE) }
        EditToolButton(Icons.Filled.ContentCut, stringResource(R.string.action_split_track),
            active = state.tool == EditTool.CUT, chromeFg = chromeFg) { state.choose(EditTool.CUT) }
        EditToolButton(Icons.Filled.Link, stringResource(R.string.action_join_track),
            active = state.tool == EditTool.JOIN, chromeFg = chromeFg) { state.choose(EditTool.JOIN) }
        // Un filet entre les outils et l'annulation : celle-ci n'attend aucun tap sur la carte, elle
        // s'applique en se touchant. Les confondre ferait chercher un point a designer.
        Box(Modifier.padding(vertical = 2.dp).size(width = 18.dp, height = 1.dp)
            .background(chromeFg.copy(alpha = 0.25f)))
        EditToolButton(
            Icons.AutoMirrored.Filled.Undo, stringResource(R.string.action_undo),
            active = false, chromeFg = chromeFg, enabled = canUndo, onClick = onUndo,
        )
    }
}

/** Un bouton de la barre : allume quand son outil attend un tap, eteint sinon. */
@Composable
internal fun EditToolButton(
    icon: ImageVector, label: String, active: Boolean, chromeFg: Color,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            icon, label, Modifier.size(20.dp),
            tint = when {
                !enabled -> chromeFg.copy(alpha = 0.3f)
                active -> MapChromeActive
                else -> chromeFg
            },
        )
    }
}

/**
 * Ce que la retouche pose au RAS DU BAS de l'ecran : le marqueur du point de coupe, et la bande qui porte
 * la consigne de l'outil en cours ou la confirmation qu'il attend.
 *
 * Les deux ensemble parce qu'ils se repondent - poser le marqueur change la consigne en confirmation, et
 * confirmer les efface tous les deux -, mais separes de la barre d'outils, qui se pose PLUS TOT dans la
 * boite : la bande du planificateur passe par-dessus la barre, et sous ces deux-ci.
 *
 * Prend le ViewModel plutot qu'une poignee de rappels : couper et joindre lui demandent une trace, un
 * segment, un mode et une suite a donner, et les envelopper un par un n'aurait deplace le noeud que d'un
 * cran (meme raison que `DrawerContent`).
 */
@Composable
internal fun BoxWithConstraintsScope.TrackEditPrompts(
    edit: TrackEditState,
    layers: List<LayerEntity>,
    cutGeometry: List<List<TrackPoint>>,
    controller: MapController,
    chromeBg: Color,
    chromeFg: Color,
    /** Deplacement de carte en cours : le marqueur suit le terrain, image par image. */
    moveTick: Int,
    /** Fin d'un deplacement : le COTE de la bulle ne se revoit qu'a ce moment-la. */
    idleTick: Int,
    /** Ce qui recouvre deja le bas de l'ecran (le panneau de profil) : la bande se pose au-dessus. */
    bottomCoverPx: Int,
    vm: MainViewModel,
) {
    val density = LocalDensity.current
    val cannotSplitMessage = stringResource(R.string.split_impossible)
    val routedFellBack = stringResource(R.string.join_fell_back_straight)

    // Le marqueur suit la carte au deplacement et au zoom (moveTick), comme les infobulles : il designe un
    // point du terrain, pas un point de l'ecran.
    edit.cut?.let { target ->
        val p = target.hit.point
        // La bulle SUIT la carte a chaque image : c'est le point du terrain qu'elle designe.
        val off = remember(moveTick, target) { controller.screenOf(p.lon, p.lat) }
        /*
         * Le COTE, lui, ne se recalcule pas a chaque image : recalcule en continu, il change en cours de
         * geste et la bulle tremble. Il est donc fige, et revu seulement :
         *
         * - a la pose du marqueur ;
         * - a la FIN d'un deplacement ou d'un dezoom (idleTick), une seule fois.
         *
         * Jamais a un zoom avant : ce qui etait degage a l'echelle du dessus l'est encore en s'approchant,
         * la trace ne faisant que s'ecarter d'elle-meme.
         */
        val cutPlacedTick = remember(target) { idleTick }
        var cutSide by remember(target) { mutableStateOf<CutBubblePlacement.Side?>(null) }
        var cutZoom by remember(target) { mutableStateOf(Double.MAX_VALUE) }
        val bubbleWpx = with(density) { CutBubbleWidth.roundToPx() }
        val bubbleHpx = with(density) { CutBubbleHeight.roundToPx() }
        val tailPx = with(density) { CutTailHeight.roundToPx() }
        val marginPx = with(density) { CutBubbleMargin.roundToPx() }
        val topInsetPx = WindowInsets.statusBars.getTop(density)

        LaunchedEffect(target, idleTick) {
            val zoom = controller.cameraState()?.third ?: return@LaunchedEffect
            if (cutSide != null && zoom > cutZoom) return@LaunchedEffect
            val here = controller.screenOf(p.lon, p.lat) ?: return@LaunchedEffect
            // Ce que la bulle pourrait recouvrir : les portions de trace assez proches pour tomber sous
            // elle, converties en metres a l'echelle du moment.
            val reachPx = kotlin.math.hypot(
                (bubbleWpx + tailPx).toDouble(), (bubbleHpx + tailPx).toDouble(),
            ) + marginPx
            val radiusM = reachPx * controller.metersPerPixel(p.lat)
            val runs = withContext(Dispatchers.Default) {
                TrackEdit.nearbyRuns(cutGeometry, p.lon, p.lat, radiusM)
            }
            val screenRuns = runs.map { run ->
                run.mapNotNull { q -> controller.screenOf(q.lon, q.lat)?.let { it.x to it.y } }
            }.filter { it.size >= 2 }
            val pl = CutBubblePlacement.choose(
                pointX = here.x.toInt(), pointY = here.y.toInt(), track = screenRuns,
                bubbleW = bubbleWpx, bubbleH = bubbleHpx, tail = tailPx,
                viewW = constraints.maxWidth, viewH = constraints.maxHeight,
                topInset = topInsetPx, margin = marginPx,
            )
            cutSide = pl.side
            cutZoom = zoom
            // Le decalage de carte n'a lieu qu'a la POSE du marqueur : le rejouer a chaque fin de
            // deplacement ramenerait la carte de force des qu'on la pousse pour regarder ailleurs.
            if (idleTick == cutPlacedTick && (pl.panX != 0 || pl.panY != 0)) {
                controller.panByScreen(pl.panX.toFloat(), pl.panY.toFloat())
            }
        }
        val side = cutSide
        if (off != null && side != null) {
            val totalW = if (side == CutBubblePlacement.Side.LEFT || side == CutBubblePlacement.Side.RIGHT)
                bubbleWpx + tailPx else bubbleWpx
            val totalH = if (side == CutBubblePlacement.Side.TOP || side == CutBubblePlacement.Side.BOTTOM)
                bubbleHpx + tailPx else bubbleHpx
            val bx = when (side) {
                CutBubblePlacement.Side.LEFT -> off.x.toInt() - totalW
                CutBubblePlacement.Side.RIGHT -> off.x.toInt()
                else -> off.x.toInt() - totalW / 2
            }
            val by = when (side) {
                CutBubblePlacement.Side.TOP -> off.y.toInt() - totalH
                CutBubblePlacement.Side.BOTTOM -> off.y.toInt()
                else -> off.y.toInt() - totalH / 2
            }
            CutMarkerBubble(
                side = side, bg = chromeBg, fg = chromeFg,
                modifier = Modifier.offset { IntOffset(bx, by) },
            )
        }
    }

    if (edit.open && edit.tool != EditTool.NONE) {
        val barModifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            .padding(bottom = with(density) { bottomCoverPx.toDp() })
        val cutTarget = edit.cut
        val a = edit.first
        val b = edit.second
        when {
            // Coupe : le marqueur est pose, on demande confirmation. Un nouveau tap ailleurs le deplace
            // tant qu'on n'a pas confirme.
            cutTarget != null -> MapActionBar(
                text = stringResource(R.string.edit_cut_confirm, cutTarget.layerName),
                onClose = { edit.choose(EditTool.NONE) }, modifier = barModifier,
            ) {
                MapBarAction(stringResource(R.string.action_cancel)) { edit.choose(EditTool.NONE) }
                MapBarAction(stringResource(R.string.action_split_track), primary = true) {
                    val layer = layers.firstOrNull { it.id == cutTarget.layerId }
                    edit.choose(EditTool.NONE)
                    if (layer != null) vm.splitLayerAt(layer, cutTarget.hit) { ok ->
                        if (!ok) edit.message = cannotSplitMessage
                    }
                }
            }
            // Jonction : les deux segments sont designes, reste a dire comment les relier.
            a != null && b != null -> MapActionBar(
                text = stringResource(R.string.edit_join_how, a.layerName, b.layerName),
                onClose = { edit.choose(EditTool.NONE) }, modifier = barModifier,
            ) {
                val apply: (TrailogRepository.JoinMode) -> Unit = { mode ->
                    val la = layers.firstOrNull { it.id == a.layerId }
                    val lb = layers.firstOrNull { it.id == b.layerId }
                    edit.choose(EditTool.NONE)
                    if (la != null && lb != null) {
                        vm.joinSegments(la, a.segment, lb, b.segment, mode) { applied ->
                            if (applied != mode) edit.message = routedFellBack
                        }
                    }
                }
                MapBarAction(stringResource(R.string.join_straight)) { apply(TrailogRepository.JoinMode.STRAIGHT) }
                MapBarAction(stringResource(R.string.join_routed), primary = true) { apply(TrailogRepository.JoinMode.ROUTED) }
                MapBarAction(stringResource(R.string.join_none)) { apply(TrailogRepository.JoinMode.NONE) }
            }
            // Sinon : la consigne de l'outil en cours.
            else -> MapPromptBar(
                text = when (edit.tool) {
                    EditTool.REVERSE -> stringResource(R.string.edit_pick_reverse)
                    EditTool.CUT -> stringResource(R.string.edit_pick_cut)
                    EditTool.JOIN -> if (a == null) stringResource(R.string.edit_pick_first)
                        else stringResource(R.string.edit_pick_second, a.layerName)
                    EditTool.NONE -> ""
                },
                onClose = { edit.choose(EditTool.NONE) },
                modifier = barModifier,
            )
        }
    }
}
