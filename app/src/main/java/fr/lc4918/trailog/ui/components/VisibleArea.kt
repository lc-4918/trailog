package fr.lc4918.trailog.ui.components

/**
 * La part de la carte qu'on VOIT : la carte entiere, moins ce que cachent en haut la barre de statut et les
 * boutons ([topCoverPx]), et en bas le panneau du moment - profil, bande du planificateur ([bottomCoverPx]).
 *
 * Sans elle, un point glisse sous le panneau du profil passait pour visible, puisqu'il etait dans la carte :
 * on deplacait le curseur sur le profil, et la carte ne suivait pas - le point designe etait sous le doigt.
 * Calcul pur, en pixels de l'ecran, verifiable sans carte.
 */
data class VisibleArea(
    val widthPx: Float, val heightPx: Float, val topCoverPx: Float, val bottomCoverPx: Float,
) {
    private val top get() = topCoverPx.coerceAtLeast(0f)
    private val bottom get() = (heightPx - bottomCoverPx).coerceAtLeast(top)

    /** Le point ([x], [y]) est-il dans la part visible, a [marginPx] de ses bords ? */
    fun contains(x: Float, y: Float, marginPx: Float): Boolean =
        x > marginPx && x < widthPx - marginPx && y > top + marginPx && y < bottom - marginPx

    /**
     * Ou poser le centre de la carte, en pixels de l'ecran ACTUEL, pour que le point ([x], [y]) vienne au
     * milieu de la part visible - et non au milieu de la carte, qui peut se trouver sous un grand panneau.
     */
    fun centerFor(x: Float, y: Float): Pair<Float, Float> {
        val milieuX = widthPx / 2
        val milieuY = (top + bottom) / 2
        return (widthPx / 2 + x - milieuX) to (heightPx / 2 + y - milieuY)
    }
}
