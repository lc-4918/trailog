package fr.lc4918.trailog.domain.model

/**
 * Placement de l'infobulle par rapport au marqueur tapé (réglage Carte / Infobulles).
 *
 * [AUTO] conserve le comportement historique : sous le marqueur, basculée au-dessus si elle ne tient pas,
 * puis bornée dans l'écran ; la carte ne bouge jamais.
 *
 * Les 9 autres valeurs forment une grille 3x3 centrée sur le point et imposent le placement demandé, quitte
 * à recentrer la carte pour que l'infobulle tienne entièrement à l'écran.
 */
enum class BubblePosition(val key: String) {
    AUTO("auto"),
    TOP_LEFT("top_left"),
    TOP("top"),
    TOP_RIGHT("top_right"),
    MIDDLE_LEFT("middle_left"),
    CENTER("center"),
    MIDDLE_RIGHT("middle_right"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM("bottom"),
    BOTTOM_RIGHT("bottom_right");

    companion object {
        fun of(key: String?): BubblePosition = entries.firstOrNull { it.key == key } ?: AUTO
    }
}
