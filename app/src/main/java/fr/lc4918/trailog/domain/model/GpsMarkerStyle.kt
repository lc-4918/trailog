package fr.lc4918.trailog.domain.model

/**
 * Symbole qui materialise la position GPS sur la carte (reglage Carte / Repere de position).
 *
 * [DOT] est la puce historique, celle du repere "ma position" de Google Maps : un rond plein a contour
 * blanc, qui dit ou l'on est sans rien dire de la direction regardee. Les deux fleches, elles, tournent
 * avec le telephone (cf. [oriented]) et disent en plus vers quoi il pointe ; la croix marque le point
 * exact, traits traversants, la ou une pastille le recouvre.
 *
 * [defaultColor] : la couleur du symbole tant qu'aucune n'a ete choisie. Le bleu de la puce est celui du
 * repere de Google Maps, devenu la couleur commune des boutons de carte allumes ; les autres symboles
 * partent en rouge, qui les detache d'un fond topographique ou l'on ne trouve ni l'un ni l'autre.
 */
enum class GpsMarkerStyle(val key: String, val defaultColor: String) {
    DOT("dot", "#4285F4"),
    ARROW_OUTLINE("arrow_outline", "#E03131"),
    ARROW_FILLED("arrow_filled", "#E03131"),
    CROSSHAIR("crosshair", "#E03131");

    /** Le symbole porte-t-il une direction ? Seules les fleches en ont une a orienter. */
    val oriented: Boolean get() = this == ARROW_OUTLINE || this == ARROW_FILLED

    companion object {
        fun of(key: String?): GpsMarkerStyle = entries.firstOrNull { it.key == key } ?: DOT
    }
}
