package fr.lc4918.trailog.ui.points

import fr.lc4918.trailog.domain.model.BubblePosition

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
