package com.tunturo.app.ui.routes

/**
 * Les seuils du glissement qui ouvre et ferme le gestionnaire de fonds, au bord DROIT de l'ecran.
 *
 * Le meme geste que le menu lateral de la bibliotheque, en miroir : un glissement vers la gauche depuis
 * la bande du bord ouvre le panneau (meme seuil que la bande du bord gauche, cf. MainScreen), un
 * glissement du panneau vers la droite le ferme. Le panneau suit le doigt ; au lacher, il se ferme s'il a
 * ete pousse d'une bonne part de sa largeur, ou lance d'un geste vif, et revient en place sinon - ce que
 * fait le tiroir de Material pour le menu.
 */
internal object BasemapPanelSwipe {
    /** Course minimale, vers la gauche, d'un glissement parti du bord droit pour ouvrir le panneau. */
    const val OPEN_THRESHOLD_PX = 40f

    /** Part de la largeur du panneau au-dela de laquelle un glissement lache le ferme. */
    const val CLOSE_FRACTION = 0.3f

    /** Vitesse (px/s, vers la droite) qui ferme le panneau quelle que soit la course. */
    const val CLOSE_VELOCITY_PX_PER_S = 1000f

    /** Un glissement parti du bord droit, de course [totalDragPx] (negative vers la gauche), ouvre-t-il ? */
    fun edgeSwipeOpens(totalDragPx: Float): Boolean = -totalDragPx > OPEN_THRESHOLD_PX

    /** Le decalage du panneau pendant le glissement : jamais a gauche de sa place, ni au-dela de sa largeur. */
    fun dragOffset(currentPx: Float, deltaPx: Float, panelWidthPx: Float): Float =
        (currentPx + deltaPx).coerceIn(0f, maxOf(panelWidthPx, 0f))

    /** Au lacher : le panneau decale de [offsetPx], lance a [velocityPxPerS], se ferme-t-il ? */
    fun closesOnRelease(offsetPx: Float, panelWidthPx: Float, velocityPxPerS: Float): Boolean =
        velocityPxPerS > CLOSE_VELOCITY_PX_PER_S ||
            (panelWidthPx > 0f && offsetPx > panelWidthPx * CLOSE_FRACTION && velocityPxPerS > -CLOSE_VELOCITY_PX_PER_S)
}

/** La bande du bord droit qui ouvre le gestionnaire de fonds au glissement (repere des tests d'ecran). */
internal const val BasemapEdgeSwipeTag = "basemap-edge-swipe"

/** Le gestionnaire de fonds ouvert (repere des tests d'ecran). */
internal const val BasemapPanelTag = "basemap-panel"
