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
import fr.lc4918.trailog.domain.geo.TrackEdit

/**
 * La retouche d'une trace, cote ecran : la bulle qui marque le point de coupe, et la barre d'outils.
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
internal fun TrackEditToolbar(
    state: TrackEditState,
    canUndo: Boolean,
    chromeBg: Color,
    chromeFg: Color,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.background(chromeBg.copy(alpha = ControlButtonBgAlpha), RoundedCornerShape(ControlButtonRadius))
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
