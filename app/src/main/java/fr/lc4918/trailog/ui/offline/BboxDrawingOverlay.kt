package fr.lc4918.trailog.ui.offline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.MapBarBackground
import kotlin.math.roundToInt

/** Le rouge du cadre, celui de l'emprise dans l'apercu du telechargement. */
private val CadreRouge = Color(0xFFD32F2F)

/** Cible tactile d'une poignee : assez large pour le pouce, bien plus que le dessin. */
private val PoigneeCible = 44.dp
private val PoigneeCoin = 18.dp
private val PoigneeCote = 14.dp

/** Le plus petit cadre qu'on puisse regler : en deca, les poignees se chevaucheraient. */
private val CadreMin = 72.dp

/** Les huit poignees : quatre sommets, quatre milieux de cotes. Chacune dit quels bords elle deplace. */
private enum class Poignee(val gauche: Boolean, val haut: Boolean, val droite: Boolean, val bas: Boolean) {
    HG(true, true, false, false), H(false, true, false, false), HD(false, true, true, false),
    D(false, false, true, false), BD(false, false, true, true), B(false, false, false, true),
    BG(true, false, false, true), G(true, false, false, false),
}

/**
 * Le reglage de l'emprise a telecharger : un cadre pose d'emblee au milieu de la carte, qu'on redimensionne
 * par ses sommets ou ses cotes, et sous lequel on deplace et zoome la carte.
 *
 * **Il remplace les deux points poses au doigt.** Poser deux coins ne laissait voir l'emprise qu'une fois
 * finie, et un coin mal place se reprenait en annulant ; on ne pouvait pas non plus bouger la carte entre
 * les deux sans risquer de poser un point. Ici l'emprise est visible des le depart, et chaque geste l'ajuste
 * : les poignees pour la taille, la carte elle-meme pour le cadrage.
 *
 * Le cadre vit dans l'ECRAN, et non sur la carte : il reste ou on l'a mis pendant qu'on deplace la carte
 * dessous, comme un viseur. L'emprise geographique ne se lit qu'au moment de passer a la suite, sous ses
 * quatre coins.
 *
 * Seules les poignees prennent les gestes : partout ailleurs, dedans comme dehors, le doigt atteint la
 * carte.
 */
@Composable
fun BoxScope.BboxEditorOverlay(
    controller: MapController,
    dark: Boolean,
    /** Ce que les boutons du haut recouvrent : le cadre de depart se pose en dessous. */
    topInsetPx: Int,
    onCancel: () -> Unit,
    onNext: (Bbox) -> Unit,
    onBarHeight: (Int) -> Unit = {},
) {
    val density = LocalDensity.current
    val minPx = with(density) { CadreMin.toPx() }
    var ecran by remember { mutableStateOf(Size.Zero) }
    var barre by remember { mutableIntStateOf(0) }
    var cadre by remember { mutableStateOf<Rect?>(null) }
    val basLibre = ecran.height - barre

    // Le cadre de depart : au milieu de ce que la carte laisse voir, entre les boutons du haut et la barre
    // du bas - assez petit pour qu'on voie ce qui l'entoure, assez grand pour se saisir.
    if (cadre == null && ecran.width > 0 && barre > 0) {
        val haut = topInsetPx.toFloat()
        val l = ecran.width * 0.6f
        val h = ((basLibre - haut) * 0.4f).coerceAtLeast(minPx)
        val cx = ecran.width / 2
        val cy = (haut + basLibre) / 2
        cadre = Rect(cx - l / 2, cy - h / 2, cx + l / 2, cy + h / 2)
    }

    Box(Modifier.fillMaxSize().onSizeChanged { ecran = Size(it.width.toFloat(), it.height.toFloat()) }) {
        val c = cadre
        if (c != null) {
            // Le cadre : un voile blanc leger et un bord rouge, comme l'emprise de l'apercu. Aucun geste :
            // le doigt passe au travers, jusqu'a la carte.
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.White.copy(alpha = 0.12f), topLeft = c.topLeft, size = c.size)
                drawRect(CadreRouge, topLeft = c.topLeft, size = c.size, style = Stroke(width = 3.dp.toPx()))
            }
            Poignee.entries.forEach { p ->
                val x = when { p.gauche -> c.left; p.droite -> c.right; else -> c.center.x }
                val y = when { p.haut -> c.top; p.bas -> c.bottom; else -> c.center.y }
                val cible = with(density) { PoigneeCible.toPx() }
                val coin = (p.gauche || p.droite) && (p.haut || p.bas)
                Box(
                    Modifier
                        .offset { IntOffset((x - cible / 2).roundToInt(), (y - cible / 2).roundToInt()) }
                        .size(PoigneeCible)
                        .pointerInput(p) {
                            detectDragGestures { change, delta ->
                                change.consume()
                                val r = cadre ?: return@detectDragGestures
                                // Relus a chaque pas, et non captures : la barre du bas a pu changer de
                                // hauteur depuis que la poignee est nee.
                                cadre = redimensionner(r, p, delta, minPx, ecran.width, ecran.height - barre)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Les sommets en rond, les cotes en barrette le long du bord : la forme dit ce qu'on
                    // deplace - deux bords, ou un seul.
                    val dessin = if (coin) Modifier.size(PoigneeCoin).background(Color.White, CircleShape)
                        .border(3.dp, CadreRouge, CircleShape)
                    else if (p.haut || p.bas) Modifier.size(28.dp, PoigneeCote / 2 + 4.dp)
                        .background(Color.White, RoundedCornerShape(4.dp)).border(2.dp, CadreRouge, RoundedCornerShape(4.dp))
                    else Modifier.size(PoigneeCote / 2 + 4.dp, 28.dp)
                        .background(Color.White, RoundedCornerShape(4.dp)).border(2.dp, CadreRouge, RoundedCornerShape(4.dp))
                    Box(dessin)
                }
            }
        }
    }

    // La barre du bas : ce qu'on fait, et les deux issues.
    val fg = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(if (dark) MapBarBackground else Color.White)
            .onSizeChanged { barre = it.height; onBarHeight(it.height) }
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            stringResource(R.string.offline_bbox_adjust), fontSize = 16.sp, fontWeight = FontWeight.Bold,
            color = fg, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Button(
                enabled = cadre != null,
                onClick = {
                    val r = cadre ?: return@Button
                    val a = controller.lonLatAt(r.left, r.top) ?: return@Button
                    val b = controller.lonLatAt(r.right, r.bottom) ?: return@Button
                    onNext(Bbox.of(minOf(a.first, b.first), minOf(a.second, b.second),
                        maxOf(a.first, b.first), maxOf(a.second, b.second)))
                },
            ) { Text(stringResource(R.string.action_next)) }
        }
    }
}

/**
 * Deplace les bords que [p] tient, du glissement [d], sans passer sous la taille minimale ni sortir de ce
 * que la carte laisse voir - la barre du bas comprise.
 */
private fun redimensionner(r: Rect, p: Poignee, d: Offset, min: Float, largeur: Float, basLibre: Float): Rect {
    var l = r.left; var t = r.top; var rr = r.right; var b = r.bottom
    if (p.gauche) l = (l + d.x).coerceIn(0f, rr - min)
    if (p.droite) rr = (rr + d.x).coerceIn(l + min, largeur)
    if (p.haut) t = (t + d.y).coerceIn(0f, b - min)
    if (p.bas) b = (b + d.y).coerceIn(t + min, basLibre)
    return Rect(l, t, rr, b)
}
