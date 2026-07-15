package fr.lc4918.trailog.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Remplacement compact d'OutlinedTextField pour toute l'application : le padding vertical par défaut de
 * Material3 (16dp haut/bas, cf. TextFieldImpl.TextFieldPadding) est trop généreux, réduit ici à 8dp.
 * [label] (si fourni) est une légende fixe au-dessus du champ, pas le label flottant M3 qui mord sur la
 * bordure (jugé inutile) - [placeholder] reste le simple indice affiché tant que le champ est vide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = false,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val mergedTextStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    // [modifier] doit rester sur le champ lui-même (pas sur un Column englobant) : des appelants s'en servent
    // pour `focusRequester` ou `menuAnchor`, qui doivent cibler l'élément focalisable/cliquable réel.
    val field = @Composable {
        BasicTextField(
            value = value, onValueChange = onValueChange, modifier = modifier,
            enabled = enabled, readOnly = readOnly, singleLine = singleLine,
            textStyle = mergedTextStyle, interactionSource = interactionSource,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        ) { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value, innerTextField = innerTextField, enabled = enabled, singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource, placeholder = placeholder,
                leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                suffix = suffix, supportingText = supportingText, isError = isError,
                contentPadding = OutlinedTextFieldDefaults.contentPadding(top = 8.dp, bottom = 8.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(enabled = enabled, isError = isError, interactionSource = interactionSource)
                },
            )
        }
    }
    if (label == null) {
        field()
    } else {
        Column {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                Column(Modifier.padding(bottom = 2.dp)) { label() }
            }
            field()
        }
    }
}
