package fr.lc4918.trailog.ui.points

import fr.lc4918.trailog.domain.model.BubblePosition

/**
 * Ce qui ne change pas d'une infobulle a l'autre : les bords qu'elles respectent, et l'epingle a degager.
 *
 * L'ecran principal en porte quatre, toutes posees dans la meme geometrie - la barre de statut a laisser
 * libre, l'air qu'elles gardent au bord, l'ecart qui les separe du point, la hauteur de l'epingle reglee.
 * Ces quatre nombres se recalculaient a l'identique quatre fois, et les passer un par un a chaque appel
 * noyait la seule chose qui distingue vraiment les bulles : le coin ou elles s'ouvrent.
 */
data class BubbleGeometry(
    val topInset: Int,
    val margin: Int,
    val gap: Int,
    val markerHeight: Int,
) {
    /** A la position reglee pour les infobulles, autour du point (cf. [computeBubblePlacement]). */
    fun at(pos: BubblePosition, anchorX: Int, anchorY: Int, bubbleW: Int, bubbleH: Int, viewW: Int, viewH: Int) =
        computeBubblePlacement(pos, anchorX, anchorY, bubbleW, bubbleH, viewW, viewH, topInset, margin, gap, markerHeight)

    /** Dans celui des quatre coins du point qui deplace le moins la carte (cf. [computeGeocodePlacement]). */
    fun atNearestCorner(anchorX: Int, anchorY: Int, bubbleW: Int, bubbleH: Int, viewW: Int, viewH: Int) =
        computeGeocodePlacement(anchorX, anchorY, bubbleW, bubbleH, viewW, viewH, topInset, margin, gap, markerHeight)
}

/** Placement calculé de l'infobulle : coin haut-gauche dans la vue, et décalage de carte à appliquer.
 *  [panX]/[panY] = de combien le marqueur doit se déplacer à l'écran pour que le placement demandé tienne
 *  entièrement ; 0 en mode AUTO (la carte ne bouge jamais) et quand le placement demandé tient déjà. */
data class BubblePlacement(val x: Int, val y: Int, val panX: Int, val panY: Int)

/**
 * Où poser l'infobulle d'un marqueur affiché en (markerX, markerY), en pixels de la vue.
 *
 * AUTO : sous le marqueur, basculée au-dessus si elle déborde en bas, puis bornée dans l'écran ; la carte
 * ne bouge pas.
 *
 * Grille 3x3 : placement fixe autour du point. S'il déborde, la bulle est ramenée dans l'écran ET on
 * renvoie le décalage de carte correspondant : une fois la carte décalée, le marqueur s'est déplacé de
 * (panX, panY) et la bulle se retrouve exactement au placement demandé, sans avoir bougé à l'écran.
 *
 * [gap] : écart entre le point et la bulle. [markerHeight] : hauteur de l'épingle, ancrée par le bas sur
 * le point (elle occupe donc l'espace au-dessus), à dégager pour la rangée haute.
 */
fun computeBubblePlacement(
    pos: BubblePosition,
    markerX: Int,
    markerY: Int,
    bubbleW: Int,
    bubbleH: Int,
    viewW: Int,
    viewH: Int,
    topInset: Int,
    margin: Int,
    gap: Int,
    markerHeight: Int,
): BubblePlacement {
    val minX = margin
    val maxX = (viewW - bubbleW - margin).coerceAtLeast(margin)
    val minY = topInset + margin
    val maxY = (viewH - bubbleH - margin).coerceAtLeast(minY)

    if (pos == BubblePosition.AUTO) {
        var y = markerY + gap
        if (y + bubbleH > viewH - margin) y = markerY - bubbleH - gap
        return BubblePlacement((markerX - bubbleW / 2).coerceIn(minX, maxX), y.coerceIn(minY, maxY), 0, 0)
    }

    val x = when (pos) {
        BubblePosition.TOP_LEFT, BubblePosition.MIDDLE_LEFT, BubblePosition.BOTTOM_LEFT -> markerX - gap - bubbleW
        BubblePosition.TOP_RIGHT, BubblePosition.MIDDLE_RIGHT, BubblePosition.BOTTOM_RIGHT -> markerX + gap
        else -> markerX - bubbleW / 2
    }
    val y = when (pos) {
        BubblePosition.TOP_LEFT, BubblePosition.TOP, BubblePosition.TOP_RIGHT -> markerY - markerHeight - gap - bubbleH
        BubblePosition.BOTTOM_LEFT, BubblePosition.BOTTOM, BubblePosition.BOTTOM_RIGHT -> markerY + gap
        else -> markerY - bubbleH / 2
    }
    val panX = when {
        x < minX -> minX - x
        x + bubbleW > viewW - margin -> (viewW - margin) - (x + bubbleW)
        else -> 0
    }
    val panY = when {
        y < minY -> minY - y
        y + bubbleH > viewH - margin -> (viewH - margin) - (y + bubbleH)
        else -> 0
    }
    return BubblePlacement((x + panX).coerceIn(minX, maxX), (y + panY).coerceIn(minY, maxY), panX, panY)
}

/**
 * Les quatre coins ou peut se poser une infobulle de géocodage, dans l'ordre des préférences à égalité.
 *
 * En bas d'abord : la rangée haute doit dégager la hauteur de l'épingle, et éloigne donc la bulle du
 * point qu'elle décrit. À droite d'abord : c'est le sens de lecture, la bulle prolonge le point.
 */
private val GeocodeCorners = listOf(
    BubblePosition.BOTTOM_RIGHT, BubblePosition.BOTTOM_LEFT,
    BubblePosition.TOP_RIGHT, BubblePosition.TOP_LEFT,
)

/**
 * Placement d'une infobulle de géocodage : le coin qui **déplace le moins la carte**.
 *
 * Ces infobulles-là ne suivent pas le réglage de position (cf. [computeBubblePlacement]), et c'est
 * voulu : celui-ci vaut pour un marqueur qu'on a soi-même posé sur une trace, où l'on sait ce qui
 * entoure le point et où l'on tient à voir la bulle toujours au même endroit. Un point désigné du doigt
 * ou un lieu trouvé par sa recherche tombent, eux, n'importe où - souvent près d'un bord - et une
 * position imposée y demanderait un recentrage à chaque fois. Le geste compte alors plus que l'habitude :
 * la carte doit rester où l'utilisateur l'a mise.
 *
 * Les quatre coins sont donc essayés, et l'on garde celui dont le décalage de carte est le plus faible -
 * nul dès qu'un coin tient à l'écran, ce qui est le cas ordinaire. À égalité, l'ordre de [GeocodeCorners]
 * tranche, sans quoi la bulle changerait de coin d'un point à l'autre sans raison visible.
 *
 * Ce qui doit tenir à l'écran, c'est **l'épingle ET la bulle** : l'épingle est un carré de [markerHeight]
 * de côté posé par sa pointe sur le point, donc entièrement au-dessus de lui (cf. `setBlackPins`). Un
 * appui long contre le bord haut de la carte laissait sans cela une épingle coupée en deux, et l'on ne
 * voyait plus ce que la bulle décrivait.
 *
 * Point hors de la carte enfin - un lieu trouvé loin de la vue courante : le rapprocher du bord le plus
 * proche le poserait dans un coin, à moitié sous les commandes. L'ensemble épingle + bulle est alors
 * **centré**, ce qui est aussi la seule réponse quand il est trop grand pour l'écran.
 */
fun computeGeocodePlacement(
    markerX: Int,
    markerY: Int,
    bubbleW: Int,
    bubbleH: Int,
    viewW: Int,
    viewH: Int,
    topInset: Int,
    margin: Int,
    gap: Int,
    markerHeight: Int,
): BubblePlacement {
    val minX = margin
    val maxX = viewW - margin
    val minY = topInset + margin
    val maxY = viewH - margin
    // Boite de l'epingle : carree, posee par sa pointe sur le point, donc entierement au-dessus de lui.
    val pinLeft = markerX - markerHeight / 2
    val pinRight = markerX + markerHeight / 2
    val pinTop = markerY - markerHeight
    // Le point est-il sur la carte ? Testé sur la vue entière et non sur les marges : un appui long tout
    // contre un bord y tombe, et mérite le petit décalage qui dégage son épingle, pas un recentrage.
    val visible = markerX in 0..viewW && markerY in 0..viewH
    return GeocodeCorners.map { pos ->
        val x = when (pos) {
            BubblePosition.BOTTOM_RIGHT, BubblePosition.TOP_RIGHT -> markerX + gap
            else -> markerX - gap - bubbleW
        }
        val y = when (pos) {
            BubblePosition.BOTTOM_RIGHT, BubblePosition.BOTTOM_LEFT -> markerY + gap
            else -> markerY - markerHeight - gap - bubbleH
        }
        val panX = shift(minOf(x, pinLeft), maxOf(x + bubbleW, pinRight), minX, maxX, visible)
        val panY = shift(minOf(y, pinTop), maxOf(y + bubbleH, markerY), minY, maxY, visible)
        BubblePlacement(x + panX, y + panY, panX, panY)
    }.minBy { kotlin.math.abs(it.panX) + kotlin.math.abs(it.panY) }
}

/**
 * De combien décaler la carte pour que l'intervalle [a, b] tienne dans [min, max].
 *
 * Le plus petit décalage qui suffit quand le point est [visible] : la carte ne bouge que de ce qu'il
 * faut, et pas du tout si l'ensemble tient déjà. Sinon - point hors de la carte, ou ensemble trop grand
 * pour elle - on centre : il n'y a pas de "plus petit décalage" qui vaille, seulement un endroit où tout
 * se voit au mieux.
 */
private fun shift(a: Int, b: Int, min: Int, max: Int, visible: Boolean): Int = when {
    !visible || b - a > max - min -> (min + max) / 2 - (a + b) / 2
    a < min -> min - a
    b > max -> max - b
    else -> 0
}
