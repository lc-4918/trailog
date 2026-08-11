package fr.lc4918.trailog.ui.edit

import kotlin.math.max
import kotlin.math.min

/**
 * De quel cote poser la bulle du marqueur de coupe, autour du point vise.
 *
 * Deux exigences, dans cet ordre :
 *
 * 1. **Ne pas couvrir la trace.** La bulle designe un endroit qu'on est en train de regarder pour decider
 *    si l'on coupe la : la poser sur le trace, c'est masquer ce que l'on vient examiner.
 * 2. **Tenir entierement a l'ecran.** Le cote retenu peut sortir par un bord ; la carte se decale alors
 *    d'autant, plus une marge - c'est ce que fait deja l'infobulle d'un marqueur.
 *
 * Le choix se fait par **recouvrement reel** : on pose les quatre rectangles candidats et l'on compte les
 * troncons de trace qui les traversent. Une premiere version jugeait sur la seule ORIENTATION de la trace
 * au point vise, ce qui ne vaut que pour une trace droite : dans un lacet, un virage, une boucle qui
 * revient sur elle-meme, le cote "oppose a la direction" tombe en plein sur une autre portion du trace.
 *
 * Sans dependance Android ni Compose : c'est de la geometrie d'ecran, et elle se verifie sans emulateur.
 */
object CutBubblePlacement {

    /** Le cote ou se pose la bulle, vu du point de coupe. L'ordre est celui des preferences a cout egal :
     *  au-dessus d'abord, la place habituelle d'une bulle d'annotation. */
    enum class Side { TOP, RIGHT, BOTTOM, LEFT }

    /** Ou dessiner la bulle, et de combien decaler la carte pour qu'elle tienne. */
    data class Placement(
        /** Coin haut-gauche de la bulle, pointe comprise, AVANT decalage de carte. */
        val x: Int,
        val y: Int,
        val side: Side,
        /** Decalage a appliquer a la carte (px) : le point, et donc la bulle, se deplacent d'autant. */
        val panX: Int,
        val panY: Int,
    ) {
        /** Encombrement total, pointe comprise : c'est lui qui doit tenir a l'ecran. */
        fun width(bubbleW: Int, tail: Int) = if (side == Side.LEFT || side == Side.RIGHT) bubbleW + tail else bubbleW
        fun height(bubbleH: Int, tail: Int) = if (side == Side.TOP || side == Side.BOTTOM) bubbleH + tail else bubbleH
    }

    /** Ce qui separe la bulle du trace quand on juge du recouvrement : sans cette marge, elle peut affleurer
     *  la ligne, ce qui se lit comme un contact. */
    private const val CLEARANCE = 4

    /**
     * Choisit le cote et calcule la position.
     *
     * [track] porte les portions de trace voisines, en coordonnees ECRAN : une liste de polylignes, telles
     * que les rend [fr.lc4918.trailog.domain.geo.TrackEdit.nearbyRuns] une fois projetees. Vide, aucun
     * cote n'est meilleur qu'un autre et l'ordre de preference tranche.
     *
     * [bubbleW] et [bubbleH] sont ceux du CORPS de la bulle ; [tail] s'y ajoute du cote de la pointe.
     */
    fun choose(
        pointX: Int, pointY: Int,
        track: List<List<Pair<Float, Float>>>,
        bubbleW: Int, bubbleH: Int, tail: Int,
        viewW: Int, viewH: Int,
        topInset: Int, margin: Int,
    ): Placement {
        val side = Side.entries.minWith(
            compareBy({ crossings(it, pointX, pointY, bubbleW, bubbleH, tail, track) }, { it.ordinal })
        )
        val totalW = if (side == Side.LEFT || side == Side.RIGHT) bubbleW + tail else bubbleW
        val totalH = if (side == Side.TOP || side == Side.BOTTOM) bubbleH + tail else bubbleH
        val (x, y) = corner(side, pointX, pointY, totalW, totalH)
        val panX = when {
            x < margin -> margin - x
            x + totalW > viewW - margin -> (viewW - margin) - (x + totalW)
            else -> 0
        }
        val floorY = topInset + margin
        val panY = when {
            y < floorY -> floorY - y
            y + totalH > viewH - margin -> (viewH - margin) - (y + totalH)
            else -> 0
        }
        return Placement(x, y, side, panX, panY)
    }

    /**
     * Coin haut-gauche de la bulle pour un cote donne.
     *
     * La pointe touche le point ; sur l'autre axe, la bulle est centree dessus - c'est ce qui la fait lire
     * comme l'etiquette de CE point.
     */
    private fun corner(side: Side, pointX: Int, pointY: Int, totalW: Int, totalH: Int): Pair<Int, Int> {
        val x = when (side) {
            Side.LEFT -> pointX - totalW
            Side.RIGHT -> pointX
            else -> pointX - totalW / 2
        }
        val y = when (side) {
            Side.TOP -> pointY - totalH
            Side.BOTTOM -> pointY
            else -> pointY - totalH / 2
        }
        return x to y
    }

    /**
     * Combien de troncons de trace ce cote recouvrirait-il ? Zero = cote libre.
     *
     * Le test porte sur le seul **corps** de la bulle, pointe exclue. La trace passe par definition PAR le
     * point de coupe : la pointe la touche donc toujours, quel que soit le cote - c'est son role. Compter
     * la pointe donnerait quatre cotes occupes et un choix sans objet.
     */
    private fun crossings(
        side: Side, pointX: Int, pointY: Int,
        bubbleW: Int, bubbleH: Int, tail: Int,
        track: List<List<Pair<Float, Float>>>,
    ): Int {
        if (track.isEmpty()) return 0
        // Coin du CORPS : recule d'une longueur de pointe par rapport au point.
        val bx = when (side) {
            Side.LEFT -> pointX - tail - bubbleW
            Side.RIGHT -> pointX + tail
            else -> pointX - bubbleW / 2
        }
        val by = when (side) {
            Side.TOP -> pointY - tail - bubbleH
            Side.BOTTOM -> pointY + tail
            else -> pointY - bubbleH / 2
        }
        val l = (bx - CLEARANCE).toFloat()
        val t = (by - CLEARANCE).toFloat()
        val r = (bx + bubbleW + CLEARANCE).toFloat()
        val b = (by + bubbleH + CLEARANCE).toFloat()
        var count = 0
        track.forEach { run ->
            for (i in 0 until run.size - 1) {
                if (crosses(run[i], run[i + 1], l, t, r, b)) count++
            }
        }
        return count
    }

    /** Le troncon touche-t-il le rectangle ? Une extremite dedans suffit ; sinon, il faut qu'il coupe un
     *  des quatre cotes - cas d'une trace qui traverse la bulle de part en part. */
    private fun crosses(
        p: Pair<Float, Float>, q: Pair<Float, Float>, l: Float, t: Float, r: Float, b: Float,
    ): Boolean {
        if (inside(p, l, t, r, b) || inside(q, l, t, r, b)) return true
        // Rejet rapide : un troncon entierement d'un cote du rectangle ne peut pas le couper.
        if (max(p.first, q.first) < l || min(p.first, q.first) > r) return false
        if (max(p.second, q.second) < t || min(p.second, q.second) > b) return false
        return segmentsCross(p, q, l to t, r to t) || segmentsCross(p, q, r to t, r to b) ||
            segmentsCross(p, q, r to b, l to b) || segmentsCross(p, q, l to b, l to t)
    }

    private fun inside(p: Pair<Float, Float>, l: Float, t: Float, r: Float, b: Float): Boolean =
        p.first in l..r && p.second in t..b

    /** Deux segments se croisent-ils ? Par le signe des produits vectoriels, sans division ni cas
     *  particulier de pente verticale. */
    private fun segmentsCross(
        a1: Pair<Float, Float>, a2: Pair<Float, Float>,
        b1: Pair<Float, Float>, b2: Pair<Float, Float>,
    ): Boolean {
        val d1 = cross(b1, b2, a1)
        val d2 = cross(b1, b2, a2)
        val d3 = cross(a1, a2, b1)
        val d4 = cross(a1, a2, b2)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    private fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>): Float =
        (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
}
