package fr.lc4918.trailog.ui.routes

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.MinMapButtonSizeDp
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.model.GpsMarkerStyle
import fr.lc4918.trailog.map.legendAssetModel
import fr.lc4918.trailog.ui.components.MapController
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Les commandes posees SUR la carte : leurs couleurs, leur fond, leur taille, et l'echelle graphique.
 *
 * Un fichier a part parce que ces valeurs se lisent ensemble - un bouton de carte doit s'accorder aux
 * autres - et qu'elles etaient dispersees sur trois cents lignes en bas de `MainScreen`, entre deux
 * composables sans rapport. La legende d'un fond y a rejoint son bouton, pour la meme raison.
 */

/** Un bouton d'une barre du bas : le choix principal se distingue par son fond. */
@Composable
internal fun MapBarAction(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
        color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (primary) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Opacite du fond des boutons de controle. Deux crans au-dessus de l'echelle graphique (0,7) : elle ne
 *  porte qu'un trait et deux chiffres, la ou un bouton doit rester franchement lisible sur une orthophoto
 *  ou un fond satellite. */
internal const val ControlButtonBgAlpha = 0.9f

/**
 * Fond des ornements poses sur la carte - echelle graphique et boutons de controle - en theme sombre.
 *
 * Clair, ils s'ecrivent en noir sur blanc ; sombre, les deux s'echangent, faute de quoi un pave blanc
 * troue un ecran par ailleurs sombre et eblouit de nuit, quand on s'en sert le plus.
 *
 * Gris tres fonce et non noir : un aplat franchement noir sur un fond de carte sombre ne se lit plus
 * comme un objet POSE dessus mais comme un trou dedans, et ses angles arrondis disparaissent.
 */
internal val MapChromeDarkBg = Color(0xFF202124)

/**
 * Le dessin d'un bouton de carte ALLUME.
 *
 * Le bleu de la puce de position, et non l'accent du theme : celui-ci est un vert sombre, qui allume un
 * trait de 2 dp sans qu'on le remarque au-dessus d'une carte - or c'est le seul signe qui distingue un
 * bouton actif d'un bouton au repos. Le bleu du repere GPS, lui, ne se confond avec aucun fond
 * topographique, et l'ecran gagne au passage une couleur d'etat unique : ce qui est en marche est bleu.
 */
internal val MapChromeActive = Color(GpsMarkerStyle.DOT.defaultColor.toColorInt())

/**
 * Cloche allumee ET en alerte : le rouge de la banniere du bas, pas le bleu des commandes en marche.
 *
 * C'est la seule entorse a la couleur d'etat unique, et elle se justifie : suivre une trace est un etat
 * comme un autre (bleu), s'en etre ecarte est un evenement, et les deux doivent se distinguer sur le meme
 * bouton. La cloche est d'ailleurs souvent le seul signe visible quand la banniere vient d'etre tue.
 */
internal val OffTrackAlertColor = Color(0xFFB3261E)

/**
 * Le repere de position quand il ne dit plus la verite : gris, et non la couleur reglee.
 *
 * Le capteur muet depuis une demi-minute laisse un repere fige, visuellement identique a un repere juste.
 * Le gris est la seule teinte de l'application qui dise un doute - tout le reste affirme quelque chose.
 *
 * Une chaine et non une Color : c'est [MapController.setUserMarker] qui la recoit, et MapLibre travaille
 * en hexadecimal.
 */
internal const val GpsStaleColor = "#8A8A8A"

/** Le fond d'un ornement de carte selon le theme. */
internal fun mapChromeBg(dark: Boolean): Color = if (dark) MapChromeDarkBg else Color.White

/** Ce qui se dessine dessus : l'inverse exact du fond, sans demi-teinte - ces objets sont petits et se
 *  lisent par-dessus n'importe quel fond de carte. */
internal fun mapChromeFg(dark: Boolean): Color = if (dark) Color.White else Color.Black

/**
 * Fond d'un bouton pose sur la carte : un carre a peine adouci, resserre autour de l'icone.
 *
 * Dessine SOUS le contenu plutot que pose sur toute la surface : un IconButton mesure 48 dp pour la zone
 * tactile, l'icone n'en occupe que 24. Un fond plein donnerait une pastille deux fois plus large que ce
 * qu'elle habille, et se superposerait a l'ondulation que l'IconButton peint lui-meme.
 */
internal fun Modifier.mapButtonBackground(color: Color, square: Dp): Modifier = drawBehind {
    // Le carre est CENTRE dans le bouton, et borne a sa taille : la zone tactile ne bouge pas, seul le
    // fond grandit ou se resserre en son milieu (cf. SettingsEntity.mapButtonSizeDp).
    val side = square.toPx().coerceAtMost(minOf(size.width, size.height))
    val r = ControlButtonRadius.toPx().coerceAtMost(side / 2)
    drawRoundRect(
        color = color,
        topLeft = Offset((size.width - side) / 2, (size.height - side) / 2),
        size = Size(side, side),
        cornerRadius = CornerRadius(r, r),
    )
}

/** Angles du fond : ceux du bouton d'itineraire de Google Maps, borne a la moitie du cote pour qu'un
 *  petit bouton s'arrondisse sans jamais depasser le cercle. */
internal val ControlButtonRadius = 16.dp

/**
 * Bouton "i" de la legende des pentes, au bout de la ligne de titre du profil.
 *
 * La legende n'a plus de reglage : elle se demande la, sur le profil qu'elle explique, et se referme du
 * meme geste. Un reglage aurait demande d'aller le chercher dans un autre ecran pour lire une echelle de
 * couleurs qu'on ne consulte qu'une fois.
 *
 * Fond blanc a 60 % : le bouton flotte au-dessus du titre, qu'un nom long fait passer dessous.
 */
@Composable
internal fun SlopeLegendButton(shown: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        // Plus de fond blanc : il servait a garder le "i" lisible quand il chevauchait le titre, ce qui
        // n'arrive plus - le titre lui reserve sa gouttiere (cf. SlopeLegendGutter).
        modifier.size(SlopeLegendButtonSize).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (shown) Icons.Filled.Info else Icons.Outlined.Info,
            stringResource(R.string.settings_profile_slope_legend),
            Modifier.size(17.dp), tint = Color(0xFF3F4A55),
        )
    }
}

/** Cote du "i" de la legende des pentes. */
internal val SlopeLegendButtonSize = 22.dp

/** Largeur que le titre du profil laisse au "i" : le bouton, et l'air qui l'ecarte du texte. C'est cette
 *  gouttiere qui empeche un titre long de passer dessous. */
internal val SlopeLegendGutter = SlopeLegendButtonSize + 4.dp

/**
 * Cote du pictogramme du bouton des points d'interet, et de l'attente qui le remplace pendant un
 * chargement.
 *
 * 24 dp : la taille par defaut d'une icone Material, celle que le bouton affiche deja. Le rond d'attente
 * doit la reprendre au pixel pres - un rond plus petit ferait sauter le bouton a chaque requete.
 */
internal val PoiButtonIconSize = 24.dp


/*
 * Dessin du bouton GPS : son epingle, et le mot "GPS" dessous.
 *
 * Il porte DEUX elements la ou ses voisins n'en ont qu'un, et se dessinait donc plus petit qu'eux - une
 * epingle de 16 dp contre les 24 dp d'une icone ordinaire. Les deux ont grandi ensemble, en gardant leur
 * rapport : le mot doit rester lisible sans concurrencer l'epingle, qui porte le sens.
 *
 * La borne haute n'est pas le confort mais le CARRE DE FOND, dont le cote se regle a partir de 36 dp
 * (cf. MinMapButtonSizeDp) : a 20 dp d'epingle et 8 sp de mot, l'ensemble tient dans le plus petit carre
 * avec de l'air de chaque cote. Au-dela, le dessin toucherait le bord de son fond au premier cran du
 * curseur.
 */
internal val GpsIconSize = 20.dp

internal const val GpsLabelSp = 8f

/** Ecart entre deux boutons poses sur la carte. Le triple de ce qu'il etait : les fonds arrondis se
 *  touchaient presque, et la colonne se lisait comme un seul bloc. */
internal val MapControlSpacing = 24.dp

/**
 * Bleu du disque de recentrage quand la position n'est plus au centre.
 *
 * Exactement celui du point de position pose sur la carte (cf. MapController.setUserLocation) : le bouton
 * reprend la teinte de ce qu'il vise, et les deux se repondent d'un bout a l'autre de l'ecran. Fixe et non
 * pris au theme, comme le point qu'il rappelle.
 */
internal val RecenterDotColor = Color(0xFF4285F4)

/**
 * Diametre du disque repeint au centre de l'icone "centrer sur ma position".
 *
 * Le trace Material `my_location` porte ce disque a un rayon de 4 sur une grille de 24, soit 8 dp pour
 * une icone de 24 : un poil plus large pour couvrir son bord lisse, et toujours en deca de l'anneau qui
 * l'entoure (rayon 5, soit 10 dp).
 */
internal val MyLocationDotSize = 9.dp

@SuppressLint("DefaultLocale")
@Composable
internal fun ScaleBar(
    controller: MapController, tick: Int, maxWidthPx: Float,
    bg: Color, fg: Color, modifier: Modifier = Modifier,
) {
    if (maxWidthPx <= 0f) return
    val cam = remember(tick) { controller.cameraState() }
    val mpp = remember(tick) { cam?.let { controller.metersPerPixel(it.first) } ?: 0.0 }
    if (mpp <= 0.0) return
    val nice = niceDistance(maxWidthPx * mpp)
    val barPx = (nice / mpp).toFloat()
    val density = LocalDensity.current
    val barDp = with(density) { barPx.toDp() }
    val fontSizeSp = 11f
    val tickHeightDp = with(density) { (fontSizeSp * 0.5f * 1.5f).sp.toDp() }
    val label = if (nice >= 1000) {
        val km = nice / 1000.0; (if (km % 1.0 == 0.0) "${km.toInt()}" else String.format("%.1f", km)) + " km"
    } else "${nice.toInt()} m"
    val strokeColor = fg.copy(alpha = 0.7f)
    val bgAlpha = 0.7f
    Column(
        // Padding du haut supprimé (le fond ne doit pas déborder au-dessus du texte) ; celui du bas
        // reste pour ne pas coller le trait horizontal au bord de la carte.
        modifier.background(bg.copy(alpha = bgAlpha)).padding(start = 2.dp, end = 2.dp, top = 0.dp, bottom = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // lineHeight réduit sous fontSize : resserre vraiment la boîte du texte (hauteur réservée), au lieu
        // de juste déplacer son rendu. Décalé vers le bas de la moitié de la hauteur des ticks pour que le
        // texte descende plus bas que leur extrémité haute (sans quoi il reste entièrement au-dessus).
        Text(
            label, fontSize = fontSizeSp.sp, lineHeight = (fontSizeSp * 0.8f).sp, color = fg,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            modifier = Modifier.offset(y = tickHeightDp / 2),
        )
        Box(Modifier.width(barDp).height(tickHeightDp)) {
            // trait horizontal en bas, traits verticaux qui ne remontent qu'au-dessus (jamais sous la ligne)
            Box(Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter).background(strokeColor))
            Box(Modifier.width(2.dp).height(tickHeightDp).align(Alignment.BottomStart).background(strokeColor))
            Box(Modifier.width(2.dp).height(tickHeightDp).align(Alignment.BottomEnd).background(strokeColor))
        }
    }
}

internal fun niceDistance(max: Double): Double {
    if (max <= 0) return 1.0
    val pow = 10.0.pow(floor(log10(max)))
    return when { max / pow >= 5 -> 5 * pow; max / pow >= 2 -> 2 * pow; else -> pow }
}

/** Au-delà, la légende ne gagne plus en lisibilité : l'image ne ferait que s'étirer (500 px de large). */
internal val LegendMaxWidth = 260.dp

/**
 * Légende du fond de plan affiché, dépliée depuis le bouton "info" auquel elle s'adosse : [anchor] est le
 * coin haut-gauche de ce bouton, en px fenêtre. L'image occupe donc au plus la place entre le bord gauche
 * de l'écran et le bouton, ce qui la garde entière quels que soient les autres boutons de la barre.
 * Un tap n'importe où ailleurs la referme, via un voile transparent posé sur la carte le temps qu'elle
 * s'affiche. Plusieurs légendes s'empilent : un composite peut afficher deux fonds qui en ont chacun une.
 */
@Composable
internal fun BasemapLegend(legends: List<String>, visible: Boolean, anchor: IntOffset, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        if (visible) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })
        }
        AnimatedVisibility(
            visible = visible,
            // Depliement vers la gauche depuis le bouton : le mouvement dit d'ou vient l'image.
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
            modifier = Modifier.offset { IntOffset(0, anchor.y) }
                .width(with(density) { anchor.x.toDp() }),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Column(
                    Modifier.padding(start = 8.dp).widthIn(max = LegendMaxWidth)
                        .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(6.dp),
                ) {
                    legends.forEach { asset ->
                        AsyncImage(model = legendAssetModel(asset), contentDescription = null,
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
