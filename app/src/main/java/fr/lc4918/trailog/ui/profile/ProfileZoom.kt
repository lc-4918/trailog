package fr.lc4918.trailog.ui.profile

import kotlin.math.roundToInt

/** Plancher de la fenetre de zoom, en nombre de points : en deca, le graphique n'a plus de forme. */
const val MinZoomSamples = 12

/**
 * Calcul de la fenetre visible d'un profil altimetrique, sous le geste de zoom.
 *
 * Partage par le profil d'une trace importee et par celui d'un itineraire planifie : c'est le meme
 * graphique, sous les memes doigts, et les deux doivent grossir exactement pareil. La fonction est pure -
 * elle ne connait ni Compose ni Android - et c'est la seule piece du zoom ou une faute serait muette : un
 * mauvais ancrage ne leve rien, il fait seulement deriver le graphique sous les doigts.
 */
object ProfileZoom {

    /**
     * Nouvelle fenetre apres un grossissement de [scale] centre sur [focusFraction] (0 au bord gauche de
     * la fenetre actuelle, 1 au bord droit).
     *
     * [current] est la fenetre courante, ou null pour la vue complete ; [total] le nombre de points du
     * parcours. Rend null quand la fenetre couvre a nouveau tout : la vue complete se represente par
     * l'absence de fenetre, et non par une plage egale au tout, pour que l'appelant n'ait qu'un cas a
     * tester.
     *
     * [scale] superieur a 1 grossit (les doigts s'ecartent), inferieur a 1 elargit.
     */
    fun window(current: IntRange?, total: Int, scale: Float, focusFraction: Float): IntRange? {
        if (total <= MinZoomSamples || scale <= 0f || !scale.isFinite()) return current
        val cur = current ?: 0..(total - 1)
        val len = cur.last - cur.first + 1
        val newLen = (len / scale).roundToInt().coerceIn(MinZoomSamples, total)
        if (newLen >= total) return null
        // Le point sous les doigts ne bouge pas : on garde son abscisse, et la fenetre se resserre autour.
        val f = focusFraction.coerceIn(0f, 1f)
        // Les deux abscisses se comptent en INDICES, donc sur (longueur - 1) : viser le bord droit doit
        // ramener le dernier point du parcours sur le dernier point de la fenetre, pas un cran avant.
        val focusIdx = cur.first + (f * (len - 1)).roundToInt()
        val start = (focusIdx - (f * (newLen - 1)).roundToInt()).coerceIn(0, total - newLen)
        return start..(start + newLen - 1)
    }
}
