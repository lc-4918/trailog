package fr.lc4918.trailog.ui.profile

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import fr.lc4918.trailog.domain.geo.ProfileScale
import android.annotation.SuppressLint
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.TrackStats
import kotlin.math.roundToInt

/** Contenu statique du profil (grille, labels, aire, ligne), reconstruit uniquement quand les
 *  données ou l'apparence changent (cf. les champs ci-dessous comparés dans le Canvas). Un simple
 *  déplacement du curseur ne les invalide pas : sans ce cache, tout (jusqu'à ~2000 points) était
 *  reconstruit et redessiné à chaque frame de scrub. */
private class ProfileDrawCache {
    var samplesRef: List<Sample>? = null
    var stats: TrackStats? = null
    var grid: Boolean? = null
    var slope: Boolean? = null
    var slopeClass: Int? = null
    var lineColor: Color? = null
    var axisFontSp: Int? = null
    var axisBold: Boolean? = null
    var axisColor: Color? = null
    var gridColor: Color? = null
    var textColor: Color? = null
    var vscale: String? = null
    var w: Float = -1f
    var h: Float = -1f

    var gridLines: List<Pair<Offset, Offset>> = emptyList()
    var yLabels: List<Triple<String, Float, Float>> = emptyList()
    var xLabels: List<Triple<String, Float, Float>> = emptyList()
    var axisPath: Path = Path()
    // aire : par plages de couleur identique (pente) au lieu d'un Path+drawPath par segment.
    var areaRuns: List<Pair<Path, Color>> = emptyList()
    var linePath: Path = Path()
}

/** Profil : axes/ticks/grille + aire (pente ou couleur de la trace) + ligne + curseur. La légende est externe. */
@Composable
fun ElevationProfile(
    samples: List<Sample>,
    stats: TrackStats,
    modifier: Modifier = Modifier,
    grid: Boolean = true,
    slope: Boolean = true,
    // Largeur des classes de pente, en dixiemes de point (cf. SlopeRamp).
    slopeClassTenths: Int = SlopeRamp.DefaultClassTenths,
    lineColor: Color = Color(0xFF1F6FB2),
    axisFontSp: Int = 9,
    axisBold: Boolean = false,
    axisColor: Color = Color(0xFF888888),
    gridColor: Color = Color(0x22000000),
    textColor: Color = Color(0xFF555555),
    // Abscisse du point courant (m depuis le debut de la trace), et non un indice d'echantillon : le
    // curseur se pose n'importe ou sur le parcours, y compris entre deux sommets (cf. TrackMath.sampleAt).
    cursorX: Double? = null,
    onScrub: (Double) -> Unit = {},
    // Marge (px) dont on rentre le dernier label de l'axe X, pour dégager la courbure de l'angle bas-droit
    // de l'écran. 0 si l'écran n'a pas d'angle arrondi (détecté par l'appelant via l'API RoundedCorner).
    lastLabelInsetPx: Float = 0f,
    // Échelle verticale, telle qu'elle est réglée : plafond d'exagération, mètres par centimètre, ou
    // exagération fixe (cf. ProfileScale). C'est elle qui décide de ce que l'axe couvre, de la hauteur
    // que le dessin occupe, et des graduations.
    verticalScale: String = "",
    // Zoom par pincement et double-tap, facultatif : seul le profil du planificateur l'utilise, celui
    // d'une trace gardant sa selection de bornes. Null = aucun geste de zoom, comportement inchange.
    // [onZoom] recoit le facteur de grossissement et la fraction horizontale visee (0 a gauche, 1 a droite).
    onZoom: ((Float, Float) -> Unit)? = null,
    onDoubleTap: ((Float) -> Unit)? = null,
) {
    if (samples.size < 2) return
    val areaColor = lineColor.copy(alpha = 0.30f)   // aire = couleur de la trace si pentes inactives
    val minX = samples.first().x; val maxX = samples.last().x
    val minZ = stats.min; val maxZ = stats.max
    val spanX = (maxX - minX).coerceAtLeast(1.0)
    val spanZ = (maxZ - minZ).coerceAtLeast(1.0)
    val vertical = remember(verticalScale) { ProfileScale.parse(verticalScale) }
    val padLpx = padL(axisFontSp)

    /** L'abscisse visee par un doigt pose a [px] : la position exacte sous le doigt, sans se rabattre sur
     *  l'echantillon le plus proche. */
    fun xAt(px: Float, w: Float): Double {
        val rel = ((px - padLpx) / (w - padLpx - padR)).coerceIn(0f, 1f)
        return minX + rel * spanX
    }

    val cache = remember { ProfileDrawCache() }
    val labelPaint = remember { Paint().apply { isAntiAlias = true } }

    /** Fraction horizontale d'une abscisse ecran, dans la zone de trace (hors marges d'axes). */
    fun fractionAt(px: Float, w: Float): Float =
        ((px - padLpx) / (w - padLpx - padR)).coerceIn(0f, 1f)

    /*
     * Rappels et convertisseurs lus a travers rememberUpdatedState, et detecteurs cles sur Unit.
     *
     * Les cles d'un pointerInput le RELANCENT quand elles changent, ce qui annule le geste en cours. Or
     * [onZoom] est une lambda recreee a chaque recomposition, et [samples] une sous-liste dont l'identite
     * change a chaque cran de zoom : les prendre pour cles tuait le pincement des son premier evenement,
     * qu'il fallait alors recommencer depuis le seuil de deplacement. Ecarter les bras d'un bout a l'autre
     * de l'ecran ne grossissait que de deux pour cent.
     */
    val scrub by rememberUpdatedState(onScrub)
    val zoomCb by rememberUpdatedState(onZoom)
    val doubleTapCb by rememberUpdatedState(onDoubleTap)
    val toX by rememberUpdatedState<(Float, Float) -> Double> { px, w -> xAt(px, w) }
    val toFraction by rememberUpdatedState<(Float, Float) -> Float> { px, w -> fractionAt(px, w) }
    val zoomable = onZoom != null

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { off -> doubleTapCb?.invoke(toFraction(off.x, size.width.toFloat())) },
                    onTap = { scrub(toX(it.x, size.width.toFloat())) },
                )
            }
            .then(
                if (!zoomable) {
                    // Profil d'une trace : le seul geste est le deplacement du curseur.
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures { ch, _ -> scrub(toX(ch.position.x, size.width.toFloat())) }
                    }
                } else {
                    // Profil du planificateur : le detecteur de transformation de Compose couvre les deux
                    // gestes d'un coup. A deux doigts le facteur s'ecarte de 1 et l'on zoome ; a un doigt
                    // il vaut exactement 1 et le centroide est le doigt lui-meme, qu'on suit du curseur.
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures(panZoomLock = false) { centroid, _, zoom, _ ->
                            val w = size.width.toFloat()
                            if (zoom != 1f) zoomCb?.invoke(zoom, toFraction(centroid.x, w))
                            else scrub(toX(centroid.x, w))
                        }
                    }
                }
            )
    ) {
        val padBpx = axisFontSp.sp.toPx() + 12f
        val w = size.width; val h = size.height
        val plotW = w - padLpx - padR; val plotHMax = h - padT - padBpx
        // Échelle horizontale (px/m). Échelle verticale (px/m) : Auto (<=0) remplit la hauteur (plotH/spanZ) ;
        // sinon échelle absolue = cmPx / (m par cm), bornée par le remplissage pour ne pas déborder du cadre.
        // Dans les deux cas le profil est ancré sur la ligne de base (minZ en bas) ; à échelle fixe fine il
        // n'occupe alors qu'une partie de la hauteur (relief honnête, pas étiré).
        val cmPx = (160f / 2.54f).dp.toPx()      // 1 cm physique en px (dp de base = 1/160 pouce)
        val xScale = plotW / spanX
        // Ce que l'axe couvre, la hauteur que le dessin prend, et les graduations : tout vient de la même
        // règle, éprouvée hors d'Android (cf. ProfileScale). Sous un plafond d'exagération, la hauteur
        // rendue est plus petite que celle disponible - c'est le panneau qui se réduit, pas l'axe qui
        // s'envole (l'appelant l'a déjà mesurée, cf. profileChartHeightPx).
        val win = ProfileScale.window(vertical, minZ, maxZ, spanX, plotW, plotHMax, cmPx)
        val plotH = win.heightPx
        val baseY = padT + plotH
        val yScale = plotH / win.spanZ
        fun sx(x: Double) = padLpx + ((x - minX) * xScale).toFloat()
        fun sy(z: Double) = baseY - ((z - win.minZ) * yScale).toFloat()

        val stale = cache.samplesRef !== samples || cache.stats != stats || cache.grid != grid ||
            cache.slope != slope || cache.slopeClass != slopeClassTenths || cache.lineColor != lineColor || cache.axisFontSp != axisFontSp ||
            cache.axisBold != axisBold || cache.axisColor != axisColor || cache.gridColor != gridColor ||
            cache.textColor != textColor || cache.vscale != verticalScale || cache.w != w || cache.h != h
        if (stale) {
            labelPaint.textSize = axisFontSp.sp.toPx(); labelPaint.isFakeBoldText = axisBold; labelPaint.color = textColor.toArgb()

            val gridLines = ArrayList<Pair<Offset, Offset>>()
            val yLabels = ArrayList<Triple<String, Float, Float>>()
            val xLabels = ArrayList<Triple<String, Float, Float>>()
            // Les graduations viennent de l'échelle : des altitudes RONDES, et non les bornes de la
            // trace - "1 149 m" en haut d'un axe n'apprend rien de plus que "1 200 m", et coûte une
            // lecture. Celles qui ne tiendraient pas dans la hauteur sont sautées une sur deux.
            val hauteurLabel = axisFontSp.sp.toPx() * 2.2f
            val saut = if (win.ticks.size > 1) {
                val ecart = plotH / (win.ticks.size - 1)
                if (ecart >= hauteurLabel) 1 else (hauteurLabel / ecart).toInt().coerceAtLeast(1)
            } else 1
            win.ticks.forEachIndexed { i, z ->
                val y = sy(z)
                if (grid) gridLines.add(Offset(padLpx, y) to Offset(padLpx + plotW, y))
                if (i % saut == 0 || i == win.ticks.lastIndex) {
                    yLabels.add(Triple("${z.roundToInt()}", padLpx - 5f, y + axisFontSp.sp.toPx() / 3f))
                }
            }
            val xTicks = 4
            for (i in 0..xTicks) {
                val xVal = minX + spanX * i / xTicks; val x = sx(xVal)
                if (grid && i in 1 until xTicks) gridLines.add(Offset(x, padT) to Offset(x, baseY))
                xLabels.add(Triple(fmtKm((xVal - minX) / 1000.0), x, baseY + axisFontSp.sp.toPx() + 6f))
            }

            cache.gridLines = gridLines
            cache.yLabels = yLabels
            cache.xLabels = xLabels
            cache.axisPath = Path().apply {
                moveTo(padLpx, padT); lineTo(padLpx, baseY)
                moveTo(padLpx, baseY); lineTo(padLpx + plotW, baseY)
            }
            cache.areaRuns = if (slope) {
                buildAreaRuns(samples, slopeClassTenths, ::sx, ::sy, baseY)
            } else {
                listOf(
                    Path().apply {
                        moveTo(sx(minX), baseY); samples.forEach { lineTo(sx(it.x), sy(it.z)) }; lineTo(sx(maxX), baseY); close()
                    } to areaColor
                )
            }
            cache.linePath = Path().apply {
                moveTo(sx(samples.first().x), sy(samples.first().z)); samples.forEach { lineTo(sx(it.x), sy(it.z)) }
            }

            cache.samplesRef = samples; cache.stats = stats; cache.grid = grid; cache.slope = slope
            cache.slopeClass = slopeClassTenths
            cache.lineColor = lineColor; cache.axisFontSp = axisFontSp; cache.axisBold = axisBold
            cache.axisColor = axisColor; cache.gridColor = gridColor; cache.textColor = textColor
            cache.vscale = verticalScale; cache.w = w; cache.h = h
        }

        cache.gridLines.forEach { (a, b) -> drawLine(gridColor, a, b, strokeWidth = 1f) }
        labelPaint.textAlign = Paint.Align.RIGHT
        cache.yLabels.forEach { (t, x, y) -> drawContext.canvas.nativeCanvas.drawText(t, x, y, labelPaint) }
        // Tous centrés sous leur tick, sauf le dernier : centré, il déborderait à droite et serait rogné par
        // l'angle arrondi de l'écran -> on l'aligne à droite ET on le rentre de [lastLabelInsetPx] vers
        // l'intérieur (0 si l'écran est plat, cf. détection RoundedCorner côté appelant).
        cache.xLabels.forEachIndexed { i, (t, x, y) ->
            if (i == cache.xLabels.lastIndex) {
                labelPaint.textAlign = Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText(t, x - lastLabelInsetPx, y, labelPaint)
            } else {
                labelPaint.textAlign = Paint.Align.CENTER
                drawContext.canvas.nativeCanvas.drawText(t, x, y, labelPaint)
            }
        }
        drawPath(cache.axisPath, axisColor, style = Stroke(width = 2f))

        cache.areaRuns.forEach { (path, col) -> drawPath(path, col) }
        drawPath(cache.linePath, lineColor, style = Stroke(width = 2.5f))

        // Le curseur ne se dessine que s'il tombe dans la fenetre affichee : zoome sur une portion, un
        // point courant reste ailleurs sur la trace, et le rabattre sur le bord le montrerait la ou il
        // n'est pas.
        cursorX?.takeIf { it in minX..maxX }?.let { cxVal ->
            TrackMath.sampleAt(samples, cxVal)?.let { s ->
                val cx = sx(s.x); val cy = sy(s.z)
                drawLine(Color(0x99000000), Offset(cx, padT), Offset(cx, baseY), strokeWidth = 2f)
                drawCircle(Color.White, radius = 7f, center = Offset(cx, cy))
                drawCircle(lineColor, radius = 7f, center = Offset(cx, cy), style = Stroke(width = 3.5f))
            }
        }

    }
}

/** Regroupe les segments consécutifs de même couleur (classe de pente) en un seul Path : évite
 *  jusqu'à ~2000 Path/drawPath (un par segment) pour n'en garder qu'un par plage de pente stable. */
private fun buildAreaRuns(
    samples: List<Sample>,
    slopeClassTenths: Int,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    baseY: Float,
): List<Pair<Path, Color>> {
    val runs = ArrayList<Pair<Path, Color>>()
    var i = 1
    while (i < samples.size) {
        val cls = SlopeRamp.classOf(samples[i].slope, slopeClassTenths)
        var j = i
        while (j + 1 < samples.size && SlopeRamp.classOf(samples[j + 1].slope, slopeClassTenths) == cls) j++
        val col = SlopeRamp.at(cls)
        val path = Path().apply {
            moveTo(sx(samples[i - 1].x), baseY)
            lineTo(sx(samples[i - 1].x), sy(samples[i - 1].z))
            for (k in i..j) lineTo(sx(samples[k].x), sy(samples[k].z))
            lineTo(sx(samples[j].x), baseY)
            close()
        }
        runs.add(path to col)
        i = j + 1
    }
    return runs
}

private const val padR = 8f
private const val padT = 6f
private fun padL(axisFontSp: Int) = axisFontSp * 3.6f + 14f

@SuppressLint("DefaultLocale")
private fun fmtKm(km: Double): String = if (km < 10) String.format("%.1f", km) else "${km.roundToInt()}"

/**
 * La hauteur (px) dont le graphe a besoin, marges comprises, pour une trace donnee.
 *
 * L'appelant l'utilise pour DIMENSIONNER le panneau : sous un plafond d'exageration, le dessin n'a pas
 * besoin de toute la hauteur - a echelle imposee, ce qu'on ajoute au-dessus n'est que du vide - et le
 * panneau se reduit d'autant, rendant la carte qu'il recouvrait (cf. ProfileScale.window).
 */
internal fun profileChartHeightPx(
    verticalScale: String, zMin: Double, zMax: Double, distanceM: Double,
    widthPx: Float, maxHeightPx: Float, pxPerCm: Float, axisFontSp: Int, axisFontPx: Float,
): Float {
    val padB = axisFontPx + 12f
    val plotW = (widthPx - padL(axisFontSp) - padR).coerceAtLeast(1f)
    val plotH = (maxHeightPx - padT - padB).coerceAtLeast(1f)
    val win = ProfileScale.window(ProfileScale.parse(verticalScale), zMin, zMax, distanceM, plotW, plotH, pxPerCm)
    return padT + win.heightPx + padB
}

/**
 * La hauteur que le graphe demande, en dp, bornee par [maxHeight].
 *
 * Le panneau s'y ajuste : sous un plafond d'exageration, un profil long et doux n'a pas besoin de toute
 * la hauteur, et ce qu'on lui rendrait ne serait que du vide au-dessus du dessin (cf. ProfileScale).
 */
@Composable
internal fun profileChartHeight(
    verticalScale: String, zMin: Double, zMax: Double, distanceM: Double,
    widthPx: Int, maxHeight: Dp, axisFontSp: Int,
): Dp {
    val density = LocalDensity.current
    return with(density) {
        profileChartHeightPx(
            verticalScale, zMin, zMax, distanceM, widthPx.toFloat(), maxHeight.toPx(),
            (160f / 2.54f).dp.toPx(), axisFontSp, axisFontSp.sp.toPx(),
        ).toDp().coerceIn(0.dp, maxHeight)
    }
}
