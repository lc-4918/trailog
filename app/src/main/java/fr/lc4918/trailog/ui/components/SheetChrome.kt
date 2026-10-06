package fr.lc4918.trailog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.ui.theme.Spacing
import fr.lc4918.trailog.R

/** Diametre du rond de la croix. */
private val SheetCloseSize = 32.dp

/** Glissement vers le bas au-dela duquel la poignee range le panneau. */
private val SheetHandleCollapseDistance = 24.dp

/**
 * La poignee d'un panneau pose au bas de la carte : un trait court et gris, centre au-dessus du titre,
 * comme en portent les feuilles des applications de cartographie.
 *
 * Elle range le panneau, au toucher comme en la tirant vers le bas : le trait promet un geste, il doit le
 * tenir des deux facons. Un glissement trop court, ou vers le haut, ne fait rien.
 */
@Composable
fun SheetHandle(onCollapse: () -> Unit, height: Dp, modifier: Modifier = Modifier) {
    val currentOnCollapse by rememberUpdatedState(onCollapse)
    Box(
        modifier.fillMaxWidth().height(height)
            .swipeDownToCollapse(onCollapse)
            // Sans ondulation : elle balaierait toute la largeur du panneau pour un trait de quelques dp.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.action_collapse),
                role = Role.Button,
                onClick = { currentOnCollapse() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant))
    }
}

/**
 * Un glissement vers le bas, de n'importe ou sur l'element, le range : le panneau entier se tire, pas
 * seulement sa poignee. Un glissement trop court, ou vers le haut, ne fait rien.
 *
 * Les enfants qui font defiler ou glisser (liste des etapes, profil) gardent leurs gestes : un
 * glissement qu'ils consomment ne parvient pas jusqu'ici. Les touchers passent, eux, a leurs boutons.
 */
fun Modifier.swipeDownToCollapse(onCollapse: () -> Unit): Modifier = composed {
    val currentOnCollapse by rememberUpdatedState(onCollapse)
    val collapseDistancePx = with(LocalDensity.current) { SheetHandleCollapseDistance.toPx() }
    var draggedPx by remember { mutableFloatStateOf(0f) }
    draggable(
        state = rememberDraggableState { delta -> draggedPx += delta },
        orientation = Orientation.Vertical,
        onDragStarted = { draggedPx = 0f },
        onDragStopped = { if (draggedPx > collapseDistancePx) currentOnCollapse() },
    )
}

/** La croix d'un panneau, sur un rond gris : elle se voit sans crier, et se touche sans viser. */
@Composable
fun SheetCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    SheetRoundButton(Icons.Filled.Close, stringResource(R.string.action_close), onClick, modifier)

/** Un bouton d'icone sur rond gris, celui de la croix : les actions du coin d'un panneau lui ressemblent. */
@Composable
fun SheetRoundButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(SheetCloseSize).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Le haut d'un panneau : la poignee, puis la ligne du titre, et la croix dans le coin.
 *
 * **La croix est aussi loin du haut du panneau que de son bord droit** ([edgeGap], la marge laterale du
 * panneau) : posee dans la ligne du titre, sous la poignee, elle tombait trop bas et flottait loin du coin.
 * Elle chevauche donc la hauteur de la poignee, dessinee par-dessus pour garder ses touchers ; la ligne
 * du titre a la hauteur de la croix, et le titre se lit a son niveau.
 *
 * @param edgeGap la marge laterale du panneau, que la croix reprend au-dessus d'elle.
 * @param extraAction une action posee a gauche de la croix, ou null (cf. [SheetRoundButton]).
 * @param header la ligne du titre, qui s'arrete avant les boutons.
 */
@Composable
fun SheetTop(
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    edgeGap: Dp,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
    closeModifier: Modifier = Modifier,
    extraAction: (@Composable () -> Unit)? = null,
    header: @Composable RowScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth()) {
        Column {
            SheetHandle(onCollapse = onCollapse, height = edgeGap, modifier = handleModifier)
            Row(
                Modifier.fillMaxWidth().height(SheetCloseSize).padding(end = (SheetCloseSize + Spacing.s) * (if (extraAction != null) 2 else 1)),
                verticalAlignment = Alignment.CenterVertically,
                content = header,
            )
        }
        Row(Modifier.align(Alignment.TopEnd).padding(top = edgeGap), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            extraAction?.invoke()
            SheetCloseButton(onClick = onClose, modifier = closeModifier)
        }
    }
}
