package fr.lc4918.trailog.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Grammaire visuelle de l'ecran des reglages, reprise de la maquette
 * (captures/trailog-settings-refined.html).
 *
 * **Pourquoi un jeu de couleurs a part, et non le theme de l'application.** Le reste de l'app pose ses
 * ecrans sur la carte : le menu lateral, les infobulles et le gestionnaire flottent au-dessus d'elle et
 * empruntent les surfaces lavande de Material. Les reglages, eux, sont un formulaire plein ecran, ou
 * l'oeil doit distinguer d'un coup ce qui est un groupe, une rubrique et une ligne. La maquette y repond
 * par des cartes blanches sur un fond bleute, autour du bleu de l'application - une grammaire qui n'aurait
 * pas de sens sur la carte, et qui reste donc ici.
 *
 * En theme sombre, ces valeurs claires seraient illisibles : une seconde palette prend le relais
 * (cf. DarkSettingsPalette), batie sur les memes ecarts - fond, carte, filet - dans l'autre sens.
 */
class SettingsPalette(
    /** Fond de l'ecran, sur lequel les cartes se detachent. */
    val screen: Color,
    /** Fond d'une carte de rubrique. */
    val card: Color,
    /** Filet entre deux lignes d'une meme carte. */
    val divider: Color,
    /** Texte d'un libelle. */
    val label: Color,
    /** Sous-titre d'un libelle, et texte d'aide. */
    val subtle: Color,
    /** Titre de rubrique, en capitales espacees. */
    val section: Color,
    /** Accent : valeurs, remplissage des curseurs, boutons de fin de carte. */
    val accent: Color,
    /** Accent fonce : texte sur [accentContainer], poignee de curseur. */
    val accentStrong: Color,
    /** Aplat d'accent : puce retenue, bouton plein, onglet courant. */
    val accentContainer: Color,
    /** Contour d'une puce, d'un champ, d'un bouton en ligne. */
    val outline: Color,
    /** Fond d'un champ de saisie et d'un bouton de pas-a-pas. */
    val fieldBg: Color,
    /** Piste non remplie d'un curseur. */
    val track: Color,
)

private val LightSettingsPalette = SettingsPalette(
    screen = Color(0xFFF2F6FA), card = Color(0xFFFFFFFF), divider = Color(0xFFEBF1F6),
    label = Color(0xFF17222C), subtle = Color(0xFF63798C), section = Color(0xFF5C7A94),
    accent = Color(0xFF16588F), accentStrong = Color(0xFF0C3F6B), accentContainer = Color(0xFFD8E7F5),
    outline = Color(0xFFC3D1DD), fieldBg = Color(0xFFF4F8FB), track = Color(0xFFDCE7F0),
)

/**
 * La meme grammaire en sombre : les memes ecarts entre fond, carte et filet, dans l'autre sens.
 *
 * **Ecrite ici plutot que tiree des roles de Material.** Elle l'etait, et c'est ce qui posait du LAVANDE
 * sur cet ecran : l'application ne redefinit que `primary` et `secondary` de son jeu de couleurs, si bien
 * que `primaryContainer` et les surfaces restaient celles du jeu par defaut de Material 3 - violettes. La
 * pastille d'un onglet ouvert, celle d'une puce retenue et les fonds de carte tiraient donc au violet, sur
 * un ecran par ailleurs bleu.
 *
 * Les valeurs sont donc posees comme celles du clair, et les deux se lisent cote a cote. Meme bleu que
 * l'application, decline en sombre : c'est le seul moyen que "retenu" ait la meme couleur partout.
 */
private val DarkSettingsPalette = SettingsPalette(
    screen = Color(0xFF0E141B), card = Color(0xFF161F29), divider = Color(0xFF223140),
    label = Color(0xFFE7EEF5), subtle = Color(0xFF9FB3C6), section = Color(0xFF89A2B9),
    accent = Color(0xFF6FB6E8), accentStrong = Color(0xFFC7E3F8), accentContainer = Color(0xFF1D4A70),
    outline = Color(0xFF33475C), fieldBg = Color(0xFF1B2836), track = Color(0xFF2A3B4D),
)

private val LocalSettingsPalette = staticCompositionLocalOf { LightSettingsPalette }

/** La palette en cours. Voir [SettingsPalette] pour ce qui la distingue du theme. */
val settingsPalette: SettingsPalette
    @Composable get() = LocalSettingsPalette.current

/** Pose la palette de l'ecran : celle de la maquette en clair, sa jumelle sombre sinon. */
@Composable
fun ProvideSettingsPalette(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkSettingsPalette else LightSettingsPalette
    CompositionLocalProvider(LocalSettingsPalette provides palette, content = content)
}

/* ---------------- Mesures, toutes reprises de la maquette ---------------- */

private val CardRadius = 16.dp
private val RowPadH = 14.dp
private val RowPadV = 9.dp
private val RowMinHeight = 46.dp
private val RowGap = 12.dp
private val LabelSp = 12.5f
private val SubSp = 10.5f

/* ---------------- Titres ---------------- */

/** Titre de groupe : le premier niveau, en gras, sans capitales. */
@Composable
fun GroupTitle(text: String, first: Boolean = false) {
    Text(
        text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = settingsPalette.label,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = if (first) 14.dp else 26.dp, bottom = 2.dp),
    )
}

/** Titre de rubrique : petites capitales espacees, la ou un groupe est en gras - deux niveaux, deux
 *  traitements franchement differents, sans quoi la hierarchie ne se lit pas en defilant. */
@Composable
fun SectionTitle(text: String, tight: Boolean = false) {
    Text(
        text.uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em,
        color = settingsPalette.section,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = if (tight) 10.dp else 18.dp, bottom = 7.dp),
    )
}

/* ---------------- Carte et lignes ---------------- */

/** Carte d'une rubrique : les lignes s'y suivent, separees par un filet et non par du vide. */
@Composable
fun SettingsCard(content: @Composable ColumnScopeMarker.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(settingsPalette.card)) {
        ColumnScopeMarker.content()
    }
}

/** Marqueur de portee : rappelle a l'appelant qu'il empile des lignes de carte, et non n'importe quoi. */
object ColumnScopeMarker

/** Filet entre deux lignes. Pose par l'appelant, entre deux elements, plutot qu'en bordure de chacun :
 *  la premiere et la derniere ligne d'une carte n'en portent pas. */
@Composable
fun ColumnScopeMarker.RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(settingsPalette.divider))
}

/**
 * Ligne d'une carte : le libelle a gauche, ce qui le regle a droite.
 *
 * Une seule colonne de libelles, une seule colonne d'actions : c'est ce qui fait tenir ensemble des
 * rubriques qui portent tantot un interrupteur, tantot une valeur, tantot un bouton.
 */
@Composable
fun ColumnScopeMarker.SetRow(
    label: String, sub: String? = null, onClick: (() -> Unit)? = null, role: Role? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = RowMinHeight)
            .padding(horizontal = RowPadH, vertical = RowPadV),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = LabelSp.sp, lineHeight = (LabelSp * 1.35f).sp, color = settingsPalette.label)
            if (sub != null) {
                Text(sub, fontSize = SubSp.sp, lineHeight = (SubSp * 1.4f).sp, color = settingsPalette.subtle,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing()
    }
}

/** Valeur affichee au bout d'une ligne : c'est elle qui porte l'accent, pas le libelle. */
@Composable
fun ValueText(text: String) {
    Text(text, fontSize = LabelSp.sp, fontWeight = FontWeight.SemiBold, color = settingsPalette.accent)
}

/** Texte d'aide sous une ligne : dans la carte, pas dessous, et sans filet qui l'en separe. */
@Composable
fun ColumnScopeMarker.Hint(text: String) {
    Text(
        text, fontSize = SubSp.sp, lineHeight = (SubSp * 1.5f).sp, color = settingsPalette.subtle,
        modifier = Modifier.padding(start = RowPadH, end = RowPadH, bottom = 12.dp),
    )
}

/**
 * Ligne a curseur : libelle et valeur sur une ligne, piste dessous.
 *
 * La valeur sort du titre : "Largeur du panneau : 60 %" faisait danser le libelle a chaque cran, et
 * l'oeil suivait un texte qui change au lieu de suivre la poignee.
 */
@Composable
fun ColumnScopeMarker.SliderRow(
    label: String, value: String, fraction: Float,
    onFraction: (Float) -> Unit, steps: Int = 0,
) {
    Column(Modifier.fillMaxWidth().padding(start = RowPadH, end = RowPadH, top = 11.dp, bottom = 13.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, fontSize = LabelSp.sp, color = settingsPalette.label, modifier = Modifier.weight(1f))
            ValueText(value)
        }
        Spacer(Modifier.height(9.dp))
        SettingsSlider(fraction = fraction, onFraction = onFraction, steps = steps)
    }
}

/**
 * Ligne a curseur double : meme dessin que [SliderRow], pour un reglage qui a un debut et une fin.
 *
 * La plage se lit dans la valeur, a droite du libelle - "8 - 14" -, et non sous chaque poignee : deux
 * etiquettes qui suivent les poignees se chevauchent des que la plage se resserre.
 */
@Composable
fun ColumnScopeMarker.RangeSliderRow(
    label: String, value: String,
    range: ClosedFloatingPointRange<Float>, bounds: ClosedFloatingPointRange<Float>,
    onRange: (ClosedFloatingPointRange<Float>) -> Unit, steps: Int = 0,
) {
    Column(Modifier.fillMaxWidth().padding(start = RowPadH, end = RowPadH, top = 11.dp, bottom = 13.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, fontSize = LabelSp.sp, color = settingsPalette.label, modifier = Modifier.weight(1f))
            ValueText(value)
        }
        Spacer(Modifier.height(9.dp))
        SettingsRangeSlider(range = range, bounds = bounds, onRange = onRange, steps = steps)
    }
}

/** Pas-a-pas d'une taille : moins, valeur, plus - et la bascule de graisse quand le reglage en a une. */
@Composable
fun ColumnScopeMarker.StepperRow(
    label: String, value: Int, min: Int, max: Int,
    bold: Boolean? = null, onBold: ((Boolean) -> Unit)? = null,
    boldLabel: String = "G", decreaseLabel: String, increaseLabel: String,
    onChange: (Int) -> Unit,
) {
    SetRow(label) {
        if (bold != null && onBold != null) {
            SquareButton(
                onClick = { onBold(!bold) }, selected = bold, contentDescription = null,
            ) {
                Text(boldLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
        }
        StepButton("−", decreaseLabel) { if (value > min) onChange(value - 1) }
        Text("$value", fontSize = LabelSp.sp, fontWeight = FontWeight.SemiBold, color = settingsPalette.label,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 26.dp))
        StepButton("+", increaseLabel) { if (value < max) onChange(value + 1) }
    }
}

/* ---------------- Briques ---------------- */

/** Interrupteur de la maquette : 38 x 22, redessine plutot que pris a Material, dont le Switch fait
 *  52 x 32 et se retaille mal (sa piste est en taille imposee). */
@Composable
fun SettingsSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)? = null) {
    val p = settingsPalette
    Box(
        Modifier.size(38.dp, 22.dp).clip(RoundedCornerShape(12.dp))
            .background(if (checked) p.accent else p.track)
            .then(if (onCheckedChange != null) Modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) } else Modifier),
    ) {
        Box(
            Modifier.padding(start = if (checked) 20.dp else 4.dp)
                .align(Alignment.CenterStart).size(14.dp)
                .clip(RoundedCornerShape(50)).background(Color.White),
        )
    }
}

/** Curseur de la maquette : piste de 5, poignee en barre verticale. Redessine pour la meme raison que
 *  l'interrupteur - la poignee ronde de Material est deux fois plus large que cette grille. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsSlider(fraction: Float, onFraction: (Float) -> Unit, steps: Int = 0) {
    val p = settingsPalette
    androidx.compose.material3.Slider(
        value = fraction, onValueChange = onFraction, valueRange = 0f..1f, steps = steps,
        thumb = {
            Box(Modifier.size(5.dp, 18.dp).clip(RoundedCornerShape(3.dp)).background(p.accentStrong))
        },
        track = { state ->
            val f = state.value.coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(5.dp)) {
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.track))
                Box(Modifier.fillMaxWidth(f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.accent))
            }
        },
    )
}

/** Curseur double, au dessin de [SettingsSlider] : deux poignees en barre, la piste remplie entre elles. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsRangeSlider(
    range: ClosedFloatingPointRange<Float>, bounds: ClosedFloatingPointRange<Float>,
    onRange: (ClosedFloatingPointRange<Float>) -> Unit, steps: Int = 0,
) {
    val p = settingsPalette
    val thumb = @Composable { _: androidx.compose.material3.RangeSliderState ->
        Box(Modifier.size(5.dp, 18.dp).clip(RoundedCornerShape(3.dp)).background(p.accentStrong))
    }
    androidx.compose.material3.RangeSlider(
        value = range, onValueChange = onRange, valueRange = bounds, steps = steps,
        startThumb = thumb, endThumb = thumb,
        track = { state ->
            val span = (bounds.endInclusive - bounds.start).takeIf { it > 0f } ?: 1f
            val from = ((state.activeRangeStart - bounds.start) / span).coerceIn(0f, 1f)
            val to = ((state.activeRangeEnd - bounds.start) / span).coerceIn(from, 1f)
            Box(Modifier.fillMaxWidth().height(5.dp)) {
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.track))
                // Une rangee a poids plutot qu'un decalage en dp : la largeur de la piste n'est connue
                // qu'a la mesure, et le remplissage doit suivre les deux poignees, pas seulement la fin.
                Row(Modifier.fillMaxWidth().height(5.dp)) {
                    if (from > 0f) Spacer(Modifier.weight(from))
                    Box(Modifier.weight((to - from).coerceAtLeast(0.001f)).height(5.dp)
                        .clip(RoundedCornerShape(3.dp)).background(p.accent))
                    if (to < 1f) Spacer(Modifier.weight(1f - to))
                }
            }
        },
    )
}

/** Petit carre d'action au bout d'une ligne : moins, plus, ou la bascule de graisse. */
@Composable
private fun StepButton(glyph: String, label: String, onClick: () -> Unit) {
    val p = settingsPalette
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(p.fieldBg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.accent)
    }
}

/** Carre a contour, plein quand il est retenu (bascule de graisse). */
@Composable
fun SquareButton(
    onClick: () -> Unit, selected: Boolean, contentDescription: String?, content: @Composable () -> Unit,
) {
    val p = settingsPalette
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
            .background(if (selected) p.accent else Color.Transparent)
            .border(1.dp, if (selected) p.accent else p.outline, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (selected) Color.White else p.subtle,
        ) { content() }
    }
}

/** Icone d'action au bout d'une ligne (crayon, corbeille, chevron). [rotation] sert au caret d'un select,
 *  qui pivote quand son menu s'ouvre. */
@Composable
fun RowIcon(icon: ImageVector, contentDescription: String?, rotation: Float = 0f, onClick: (() -> Unit)? = null) {
    val p = settingsPalette
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(16.dp).rotate(rotation), tint = p.subtle)
    }
}

/**
 * Fin de ligne d'un select : la valeur en cours, le caret, et le menu des choix.
 *
 * Le menu est ancre ici, sur la valeur, et non sur la ligne entiere : il se deroule sous ce qu'il va
 * remplacer, la ou un menu ancre a gauche s'ouvrait a l'oppose de ce qu'on venait de lire. Le caret
 * bascule a 180 degres pendant l'ouverture - une fleche qui montre le bas quand le menu est ferme, le
 * haut quand il est ouvert, et l'animation dit lequel des deux vient d'arriver.
 */
@Composable
fun PickValue(
    value: String, open: Boolean, onDismiss: () -> Unit,
    menuItems: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val angle by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "caret")
    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ValueText(value)
            RowIcon(Icons.Filled.KeyboardArrowDown, null, rotation = angle)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open, onDismissRequest = onDismiss, content = menuItems,
        )
    }
}

/** Puce d'un choix : contour quand elle est libre, aplat d'accent quand elle est retenue. */
@Composable
fun SettingsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = settingsPalette
    Box(
        Modifier.clip(RoundedCornerShape(9.dp))
            .background(if (selected) p.accentContainer else Color.Transparent)
            .border(1.dp, if (selected) p.accentContainer else p.outline, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        // Le texte d'une puce libre suit le gris de la palette, et non un bleu-gris ecrit en dur : celui-ci
        // venait de la maquette claire, et disparaissait presque sur une carte sombre.
        Text(label, fontSize = 11.sp, color = if (selected) p.accentStrong else p.subtle,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Rangee de puces d'une carte, defilante quand elles ne tiennent pas. */
@Composable
fun ColumnScopeMarker.ChipRow(scrollable: Boolean = false, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = RowPadH, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** Bouton plein d'une carte : l'action principale d'une rubrique ("Importer un fichier"), ou l'une de
 *  deux actions jumelles d'un en-tete ("Importer" / "Creer un composite", cf. le header de la rubrique
 *  Fonds de plan personnalises) - d'ou le modifier ouvert, plein par defaut mais reductible a une moitie
 *  de rangee via [Modifier.weight]. */
@Composable
fun ColumnScopeMarker.CardButton(
    label: String, icon: Painter? = null, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit,
) {
    val p = settingsPalette
    Row(
        modifier.padding(horizontal = RowPadH, vertical = 6.dp)
            .height(42.dp).clip(RoundedCornerShape(12.dp)).background(p.accentContainer)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = p.accentStrong)
        Text(label, fontSize = LabelSp.sp, fontWeight = FontWeight.SemiBold, color = p.accentStrong)
    }
}

/** Action de fin de carte, calee a droite au-dessus d'un filet : "Reinitialiser", "Enregistrer". */
@Composable
fun ColumnScopeMarker.CardAction(label: String, onClick: () -> Unit) {
    val p = settingsPalette
    RowDivider()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = RowPadH, vertical = 10.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.accent)
    }
}

/** Bouton a contour pose dans une ligne ("Parcourir", "Verifier"). */
@Composable
fun InlineButton(label: String, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = settingsPalette
    Row(
        Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, p.outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = p.accent)
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = p.accent)
    }
}

/**
 * Champ de saisie de la maquette : un cadre de 42 dp, avec sa legende au-dessus.
 *
 * [icon] accompagne la legende quand le champ en tire un sens que son texte ne porte pas seul - les deux
 * couches d'un composite, ou "premier plan" et "arriere-plan" se distinguent d'un coup d'oeil par leur
 * pictogramme. L'icone prend la couleur de la legende : elle en fait partie, elle ne s'en detache pas.
 */
@Composable
fun ColumnScopeMarker.FieldRow(caption: String?, icon: ImageVector? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = RowPadH, vertical = 11.dp)) {
        if (caption != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = settingsPalette.section)
                Text(caption, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = settingsPalette.section)
            }
        }
        content()
    }
}

/** Contour et fond d'un champ, a poser autour d'un champ de saisie ou d'un texte fige. */
@Composable
fun fieldBoxModifier(): Modifier {
    val p = settingsPalette
    return Modifier.fillMaxWidth().height(42.dp)
        .clip(RoundedCornerShape(11.dp)).background(p.fieldBg)
        .border(1.dp, p.outline, RoundedCornerShape(11.dp))
}

/**
 * Champ de saisie de l'ecran : le cadre de [fieldBoxModifier], et le texte au corps des valeurs figees.
 *
 * Redessine sur un BasicTextField plutot que pris a Material, dont l'OutlinedTextField impose 56 dp de
 * haut et une legende flottante que la legende de [FieldRow], posee au-dessus, rend inutile.
 *
 * [placeholder] est le texte gris affiche a vide - le plus souvent la valeur qui s'appliquera faute de
 * saisie, et non une redite du libelle.
 */
@Composable
fun SettingsTextField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val p = settingsPalette
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = p.label),
        cursorBrush = SolidColor(p.accent),
        modifier = fieldBoxModifier(),
        decorationBox = { field ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, fontSize = 12.sp, color = p.subtle, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                field()
            }
        },
    )
}

/** Bordure d'une carte de discipline, retenue ou non. */
@Composable
fun disciplineBorder(selected: Boolean): BorderStroke =
    BorderStroke(1.dp, if (selected) settingsPalette.accentContainer else settingsPalette.outline)

/**
 * Ligne a choix : le libelle, la valeur retenue, et le menu qui s'ouvre d'un tap sur la ligne entiere.
 *
 * Un menu et non une file de puces : theme, langue, position d'infobulle ont trois a dix valeurs, dont on
 * ne lit que celle en cours - les etaler couterait une ligne par choix, pour un reglage qu'on touche une
 * fois.
 */
@Composable
fun <T> ColumnScopeMarker.PickRow(
    label: String, current: T, options: List<T>, optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SetRow(label, onClick = { open = true }) {
        PickValue(optionLabel(current), open, { open = false }) {
            options.forEach { o ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(optionLabel(o)) },
                    onClick = { open = false; onSelect(o) },
                )
            }
        }
    }
}

/** Puces d'un choix exclusif (mode d'ouverture du menu, unites...). */
@Composable
fun ColumnScopeMarker.SegChips(options: List<Pair<String, String>>, current: String, onSelect: (String) -> Unit) {
    ChipRow {
        options.forEach { (key, label) ->
            SettingsChip(label, selected = key == current) { onSelect(key) }
        }
    }
}

/** Taille des icones posees dans une ligne de reglage. */
val SettingsRowIconSize: Dp = 16.dp
