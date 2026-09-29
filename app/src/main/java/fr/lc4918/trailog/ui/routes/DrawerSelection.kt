package fr.lc4918.trailog.ui.routes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.ui.theme.Spacing
import fr.lc4918.trailog.ui.theme.TrailogIcons

/** Un element de l'arborescence : `"folder"` ou `"layer"`, et son identifiant. */
typealias TreeItem = Pair<String, Long>

/**
 * La selection multiple du menu lateral : un appui long sur un dossier ou une couche l'ouvre, les yeux
 * cedent la place a des cases a cocher, et l'en-tete propose de tout cocher, d'annuler ou de supprimer.
 */
class DrawerSelection {
    var active by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Set<TreeItem>>(emptySet())
        private set

    /** L'appui long : la selection s'ouvre, l'element touche coche. Deja ouverte, il (de)coche l'element. */
    fun start(item: TreeItem) {
        if (active) { toggle(item); return }
        active = true
        selected = setOf(item)
    }

    fun toggle(item: TreeItem) {
        selected = if (item in selected) selected - item else selected + item
    }

    fun isSelected(item: TreeItem) = item in selected

    /** La case de l'en-tete : tout coche la decoche entierement, sinon elle coche tout. */
    fun toggleAll(all: Set<TreeItem>) {
        selected = if (all.isNotEmpty() && selected.containsAll(all)) emptySet() else all
    }

    /** L'etat de la case de l'en-tete : pleine, vide, ou a moitie. */
    fun allState(all: Set<TreeItem>): ToggleableState = when {
        selected.isEmpty() || all.isEmpty() -> ToggleableState.Off
        selected.containsAll(all) -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    fun cancel() {
        active = false
        selected = emptySet()
    }
}

/** La selection du menu lateral, lue par ses lignes sans passer de parametre a chaque niveau de l'arbre. */
internal val LocalDrawerSelection = staticCompositionLocalOf { DrawerSelection() }

/** Tous les elements de l'arborescence : ce que coche la case de l'en-tete. */
internal fun allTreeItems(folders: List<FolderEntity>, layers: List<LayerEntity>): Set<TreeItem> =
    folders.map { "folder" to it.id }.toSet() + layers.map { "layer" to it.id }

/**
 * Ce qu'il faut supprimer pour supprimer la selection : les dossiers coches qui ne sont pas deja dans un
 * dossier coche - leur contenu part avec eux -, et les couches cochees hors de ceux-la. Sans ce tri, une
 * couche cochee dans un dossier coche serait supprimee deux fois, l'une des deux sur une ligne disparue.
 */
internal fun deletionPlan(
    selected: Set<TreeItem>, folders: List<FolderEntity>, layers: List<LayerEntity>,
): Pair<List<FolderEntity>, List<LayerEntity>> {
    val coches = folders.filter { ("folder" to it.id) in selected }
    val ids = coches.map { it.id }.toSet()
    val parents = folders.associate { it.id to it.parentId }
    // [soi] : le dossier dont on remonte les parents. Y retomber, c'est une boucle (base abimee), et non un
    // parent coche - sans quoi deux dossiers parents l'un de l'autre s'excluaient mutuellement.
    fun sousUnCoche(parentId: Long?, soi: Long? = null): Boolean {
        var p = parentId
        val vus = mutableSetOf<Long>()
        while (p != null && p != soi && vus.add(p)) {
            if (p in ids) return true
            p = parents[p]
        }
        return false
    }
    val dossiers = coches.filter { !sousUnCoche(it.parentId, it.id) }
    val couches = layers.filter { ("layer" to it.id) in selected && !sousUnCoche(it.folderId) }
    return dossiers to couches
}

/** Hauteur de la ligne des gestes de l'en-tete, celle des boutons "Importer" et "Dossier" (cf. HeaderButton). */
internal val HeaderActionsHeight = 40.dp

/**
 * L'en-tete en mode selection, a la place de "Importer" et "Dossier" : a gauche la case qui coche ou
 * decoche tout, a droite "Annuler" et "Supprimer" - en rouge, comme "Reinitialiser" du planificateur, le
 * seul geste qui efface.
 */
@Composable
internal fun SelectionHeader(
    state: ToggleableState,
    count: Int,
    onToggleAll: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rouge = MaterialTheme.colorScheme.error
    // La hauteur des boutons qu'elle remplace ("Importer", "Dossier") : l'en-tete ne bouge pas quand la
    // selection s'ouvre. La case, qui reservait sa cible de 48 dp, est ramenee a la ligne.
    Row(modifier.fillMaxWidth().height(HeaderActionsHeight).testTag("selection_header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            TriStateCheckbox(state = state, onClick = onToggleAll, modifier = Modifier.testTag("selection_all"))
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onCancel, modifier = Modifier.height(HeaderActionsHeight).testTag("selection_cancel"),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(Icons.Filled.Close, null, Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(onClick = onDelete, enabled = count > 0,
            modifier = Modifier.height(HeaderActionsHeight).testTag("selection_delete"),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = rouge),
            border = androidx.compose.foundation.BorderStroke(1.dp,
                if (count > 0) rouge else MaterialTheme.colorScheme.outlineVariant)) {
            Icon(TrailogIcons.Trash, null, Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** La case d'une ligne en mode selection, a la place de son oeil, dans la meme cible. */
@Composable
internal fun RowCheckbox(checked: Boolean, onToggle: () -> Unit, hitSize: Dp, tag: String) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Box(Modifier.size(hitSize), contentAlignment = Alignment.Center) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() }, modifier = Modifier.testTag(tag))
        }
    }
}

/**
 * L'infobulle ouverte dans le menu, une seule a la fois, et la regle qui la ferme.
 *
 * Tout appui dans le menu la ferme (cf. [closesTooltips]) ; l'appui qui l'a fermee n'en ouvre pas d'autre,
 * meme pose sur un nom - c'etait un appui pour fermer. D'ou [swallow], releve a CHAQUE appui : un appui qui
 * n'a rien ferme ne doit pas avaler le clic suivant.
 */
class TooltipHolder {
    var open by mutableStateOf<Any?>(null)
        private set
    private var swallow = false

    /**
     * Vrai pendant l'appui qui vient de fermer une infobulle, jusqu'au suivant : les noms n'ont alors pas de
     * fond d'appui (cf. [pressFeedback]). Un etat et non un simple drapeau, pour que les noms se recomposent :
     * dans une liste qui defile, le fond d'appui part avec un leger retard, APRES cette recomposition.
     */
    var quiet by mutableStateOf(false)
        private set

    /** Un appui commence, n'importe ou dans le menu. */
    fun onPress() {
        swallow = open != null
        quiet = swallow
        open = null
    }

    /** Les noms portent un fond d'appui seulement quand l'appui peut OUVRIR une infobulle. */
    val pressFeedback: Boolean get() = open == null && !quiet

    /** Un appui simple sur le nom [key] : il l'ouvre, sauf si cet appui vient de fermer une infobulle. */
    fun onLabelClick(key: Any) {
        if (swallow) { swallow = false; return }
        open = key
    }

    fun close() { open = null }
}

internal val LocalTooltipHolder = staticCompositionLocalOf { TooltipHolder() }

/**
 * Observe chaque appui qui commence dans cette zone, sur la passe INITIALE et sans le consommer : la ligne
 * touchee le recoit ensuite normalement, et l'infobulle ouverte se ferme.
 */
internal fun Modifier.closesTooltips(holder: TooltipHolder): Modifier = pointerInput(holder) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.type == PointerEventType.Press) holder.onPress()
        }
    }
}

/**
 * Le nom d'un dossier ou d'une couche, et ses deux gestes :
 * - l'appui simple montre le nom entier dans une infobulle au-dessus - il est souvent coupe ;
 * - l'appui long vibre et ouvre la selection multiple ([onLongPress]).
 *
 * L'infobulle se referme au second appui, ou a un appui n'importe ou ailleurs - y compris sur le nom d'un
 * autre element, qui ne l'ouvre pas pour autant : c'est un appui pour fermer (cf. [TooltipHolder]).
 *
 * Le fond gris de l'appui ne parait qu'a l'OUVERTURE : un appui qui ferme ne fait rien d'autre que fermer,
 * et un nom qui s'assombrit laissait croire qu'il allait s'ouvrir a son tour.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RowLabel(
    name: String,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
    fontWeight: FontWeight?,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bulles = LocalTooltipHolder.current
    // Une identite propre a CE nom : deux elements peuvent porter le meme.
    val cle = remember { Any() }
    Box(modifier) {
        Text(name, style = style, color = color, fontWeight = fontWeight, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = if (bulles.pressFeedback) ripple() else null,
                    onClick = { bulles.onLabelClick(cle) },
                    onLongClick = { bulles.close(); onLongPress() },
                )
                .testTag("row_label"))
        if (bulles.open === cle) NameTooltip(name, onDismiss = { bulles.close() })
    }
}

/** L'ecart entre l'infobulle et le nom qu'elle deplie. */
private val TooltipGap = 4.dp

/** L'infobulle du nom entier, posee au-dessus du nom, dans la largeur de l'ecran. */
@Composable
private fun NameTooltip(name: String, onDismiss: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val ecart = with(density) { TooltipGap.roundToPx() }
    val marge = with(density) { Spacing.s.roundToPx() }
    val position = remember(ecart, marge) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize,
            ): IntOffset {
                val x = anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width - marge).coerceAtLeast(marge)
                // Au-dessus du nom ; dessous seulement s'il n'y a pas la place (tout en haut de la liste).
                val dessus = anchorBounds.top - popupContentSize.height - ecart
                val y = if (dessus >= 0) dessus else anchorBounds.bottom + ecart
                return IntOffset(x, y)
            }
        }
    }
    val largeur = LocalConfiguration.current.screenWidthDp.dp - Spacing.s * 2
    // Sans focus ni fermeture a l'appui exterieur : c'est le menu qui ferme la bulle (cf. TooltipHolder).
    // La fenetre surgissante ne garantit pas que l'appui qui la ferme s'arrete la ; le nom d'a cote
    // l'aurait recu, et ouvert sa propre bulle.
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = false)) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 3.dp,
            modifier = Modifier.widthIn(max = largeur).testTag("name_tooltip"),
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s))
        }
    }
}

/** Vibre au passage en selection, comme la prise d'une ligne a glisser (cf. strongHaptic). */
@Composable
internal fun rememberSelectionHaptic(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { { strongHaptic(context) } }
}
