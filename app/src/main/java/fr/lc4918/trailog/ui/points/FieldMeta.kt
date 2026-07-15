package fr.lc4918.trailog.ui.points

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.lc4918.trailog.R

/** Clés GPX que l'infobulle et son éditeur traitent à part. "name" porte le titre ; les autres n'ont
 *  qu'un alias, leur valeur restant une propriété comme les autres. */
const val KEY_NAME = "name"
private const val KEY_ELE = "ele"
private const val KEY_DESC = "desc"
private const val KEY_CMT = "cmt"

/** Clés ni affichées ni éditables : elles décrivent le rendu du marqueur, pas son contenu. Elles sont
 *  malgré tout conservées à l'enregistrement, l'éditeur ne devant pas les faire disparaître du fichier. */
private val HiddenKeys = setOf("sym", "type")

fun isHiddenKey(key: String): Boolean = key in HiddenKeys

/** Libellé d'une propriété : alias traduit pour les clés GPX connues, sinon la clé brute. */
@Composable
fun fieldLabel(key: String): String = when (key) {
    KEY_NAME -> stringResource(R.string.field_alias_name)
    KEY_ELE -> stringResource(R.string.field_alias_ele)
    KEY_DESC -> stringResource(R.string.field_alias_desc)
    KEY_CMT -> stringResource(R.string.field_alias_cmt)
    else -> key
}

/** Valeur telle qu'affichée dans l'infobulle : l'altitude porte son unité. */
@Composable
fun fieldDisplayValue(key: String, value: String): String =
    if (key == KEY_ELE && value.isNotBlank()) stringResource(R.string.value_ele_meters, value) else value

/** Unité accolée au champ dans l'éditeur : la valeur saisie reste ainsi le seul nombre. */
@Composable
fun fieldUnit(key: String?): String? =
    if (key == KEY_ELE) stringResource(R.string.unit_meter) else null

/** Une URL de champ Lien doit être absolue en http(s) : elle part telle quelle vers le navigateur. */
fun isValidUrl(url: String): Boolean {
    val t = url.trim()
    return t.startsWith("http://") || t.startsWith("https://")
}
