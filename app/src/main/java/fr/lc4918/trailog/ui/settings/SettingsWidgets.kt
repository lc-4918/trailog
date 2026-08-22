package fr.lc4918.trailog.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.model.GroupCheck
import kotlin.math.roundToInt

/**
 * Les briques repetees de l'ecran de reglages : une ligne d'interrupteur, une ligne de pas a pas, une case,
 * un titre de section, et les commandes compactes qu'elles emploient.
 *
 * Elles sont ici parce qu'elles servent aux QUATRE onglets. Les garder dans le fichier de l'ecran obligeait
 * a le lire en entier pour comprendre une ligne de reglage, et c'est ce qui l'avait porte a deux mille
 * lignes.
 */

/** Ligne a interrupteur : la ligne entiere bascule, pas seulement l'interrupteur. */
@Composable internal fun ColumnScopeMarker.SwitchLine(
    label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit,
) {
    SetRow(label, sub = sub, onClick = { onChange(!checked) }, role = Role.Switch) {
        SettingsSwitch(checked)
    }
}

/** Ligne a pas-a-pas, avec sa bascule de graisse quand le reglage en porte une. */
@Composable internal fun ColumnScopeMarker.StepperLine(
    label: String, value: Int, min: Int, max: Int,
    bold: Boolean? = null, onBold: ((Boolean) -> Unit)? = null, onChange: (Int) -> Unit,
) {
    StepperRow(
        label = label, value = value, min = min, max = max, bold = bold, onBold = onBold,
        boldLabel = stringResource(R.string.settings_bold_abbrev),
        decreaseLabel = stringResource(R.string.action_decrease),
        increaseLabel = stringResource(R.string.action_increase),
        onChange = onChange,
    )
}

/** Fraction 0..1 d'une valeur entiere dans sa plage, et son retour : les curseurs de l'ecran parlent tous
 *  en fractions, les reglages en unites. Une seule conversion, au meme endroit pour tous. */
internal fun fractionOf(value: Int, min: Int, max: Int): Float =
    ((value - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)

internal fun valueOf(fraction: Float, min: Int, max: Int): Int =
    (min + fraction * (max - min)).roundToInt().coerceIn(min, max)

/**
 * Ligne a cocher d'une categorie de point d'interet, avec son pictogramme.
 *
 * Trois etats et non deux : la ligne "tout selectionner" d'un groupe est a moitie cochee quand une partie
 * seulement de ses categories l'est, comme n'importe quel "select all".
 */
@Composable internal fun ColumnScopeMarker.CheckLine(
    label: String, etat: GroupCheck, onToggle: () -> Unit,
) {
    SetRow(label, onClick = onToggle, role = Role.Checkbox) {
        Icon(
            when (etat) {
                GroupCheck.ALL -> Icons.Filled.CheckBox
                GroupCheck.SOME -> Icons.Filled.IndeterminateCheckBox
                GroupCheck.NONE -> Icons.Filled.CheckBoxOutlineBlank
            },
            null,
            tint = if (etat == GroupCheck.NONE) settingsPalette.subtle else MaterialTheme.colorScheme.primary,
        )
    }
}

/** Puces d'un choix multiple ordonne (infos affichees) : l'ordre d'affichage suit celui des puces, non
 *  celui des taps - deux traces cote a cote doivent presenter leurs infos dans le meme ordre. */
@Composable internal fun ColumnScopeMarker.InfoChipRow(
    options: List<Pair<String, String>>, csv: String, scrollable: Boolean = false, onChange: (String) -> Unit,
) {
    val selected = csv.split(",").map { it.trim() }.filter { it.isNotBlank() }
    ChipRow(scrollable = scrollable) {
        options.forEach { (key, label) ->
            val on = key in selected
            SettingsChip(label, selected = on) {
                val next = if (on) selected - key else selected + key
                onChange(options.map { it.first }.filter { it in next }.joinToString(","))
            }
        }
    }
}

private val CompactIconButtonSize = 40.dp

internal val CompactIconSize = 22.dp

private val CompactChipHeight = 36.dp

@Composable private fun Section(t: String) {
    Spacer(Modifier.height(16.dp))
    Text(t, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

/**
 * Titre de groupe : un cran au-dessus de [Section], pour reunir les rubriques d'un meme sujet dans un
 * onglet devenu long.
 *
 * Se distingue par la taille, la couleur d'accent et un filet, et non par un simple gras : entre un
 * `titleSmall` de rubrique et un `titleMedium` de groupe, l'ecart seul ne suffirait pas a faire lire une
 * hierarchie sur une liste qui defile.
 */
@Composable private fun Group(t: String) {
    Spacer(Modifier.height(24.dp))
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(top = 2.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
}

/**
 * Ligne a interrupteur : l'interrupteur d'abord, son libelle ensuite.
 *
 * La ligne entiere est cliquable, pas seulement l'interrupteur : c'est le libelle qu'on vise du regard,
 * et une cible de 40 dp de large au bout d'une phrase de trente caracteres se rate.
 */
@Composable private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 4.dp),
    ) {
        Switch(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable private fun CompactIconButton(onClick: () -> Unit, contentDescription: String?, icon: ImageVector) {
    IconButton(onClick = onClick, modifier = Modifier.size(CompactIconButtonSize)) {
        Icon(icon, contentDescription, modifier = Modifier.size(CompactIconSize))
    }
}

/** Switch réellement compact : le track de [Switch] est en `requiredSize()` en interne (non
 *  surchargeable par un modifier extérieur), donc on le réduit par mise à l'échelle dans une Box
 *  dont la taille contrainte l'espace réellement réservé dans le Row parent. */
@Composable private fun CompactSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(Modifier.size(36.dp, 22.dp), contentAlignment = Alignment.Center) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.7f))
    }
}

/**
 * Curseur de reglage, celui de Material sans retouche.
 *
 * Il a longtemps ete redessine plus fin (piste de 4 dp, pastille de 14 dp) pour tenir plus de rubriques
 * a l'ecran. La pastille s'attrapait mal et la piste se lisait mal ; l'ecran des reglages se parcourt en
 * defilant, la place gagnee ne valait pas ce qu'elle coutait.
 */
@Composable private fun CompactSlider(
    value: Float, onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Slider(
        value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps,
        onValueChangeFinished = onValueChangeFinished,
    )
}

@Composable private fun FontStepper(
    label: String, value: Int, min: Int = 7, max: Int = 28,
    bold: Boolean? = null, onBold: ((Boolean) -> Unit)? = null, onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (bold != null && onBold != null) {
            FilterChip(selected = bold, onClick = { onBold(!bold) },
                label = { Text(stringResource(R.string.settings_bold_abbrev), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(CompactChipHeight).padding(end = 4.dp))
        }
        CompactIconButton(onClick = { if (value > min) onChange(value - 1) }, stringResource(R.string.action_decrease), Icons.Filled.Remove)
        Text("$value", Modifier.widthIn(min = 26.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        CompactIconButton(onClick = { if (value < max) onChange(value + 1) }, stringResource(R.string.action_increase), Icons.Filled.Add)
    }
}

@Composable private fun InfoChips(options: List<Pair<String, String>>, csv: String, onChange: (String) -> Unit) {
    val sel = csv.split(",").map { it.trim() }.filter { it.isNotBlank() }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { (k, label) ->
            val on = k in sel
            FilterChip(selected = on, onClick = {
                val newSel = options.map { it.first }.filter { if (it == k) !on else it in sel }
                onChange(newSel.joinToString(","))
            }, label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(CompactChipHeight).padding(end = 4.dp))
        }
    }
}

@Composable private fun SegRow(options: List<Pair<String, String>>, value: String, onSelect: (String) -> Unit) {
    Row { options.forEach { (k, label) ->
        FilterChip(selected = value == k, onClick = { onSelect(k) },
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.height(CompactChipHeight).padding(end = 4.dp))
    } }
}
