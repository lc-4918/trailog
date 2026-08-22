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
 *
 * [defaultSizeDp] : sa taille de depart, pour la meme raison que la couleur - un symbole a la taille ou il
 * se lit. Les deux FLECHES arrivent a 30 dp la ou la puce et la croix se contentent de 20 : une fleche dit
 * une direction, et une direction ne se lit pas sur un dessin de la taille d'un point. Changer de symbole
 * rend au nouveau sa taille comme sa couleur (cf. l'ecran de reglages).
 */
enum class GpsMarkerStyle(val key: String, val defaultColor: String, val defaultSizeDp: Int) {
    DOT("dot", "#4285F4", 20),
    ARROW_OUTLINE("arrow_outline", "#E03131", 30),
    ARROW_FILLED("arrow_filled", "#E03131", 30),
    CROSSHAIR("crosshair", "#E03131", 20);

    /** Le symbole porte-t-il une direction ? Seules les fleches en ont une a orienter. */
    val oriented: Boolean get() = this == ARROW_OUTLINE || this == ARROW_FILLED

    companion object {
        /**
         * Le repli est la FLECHE PLEINE, c'est-a-dire le defaut du reglage.
         *
         * Les deux doivent coincider. `of` est appele avec `settings?.gpsMarkerStyle`, donc avec null tant
         * que les reglages ne sont pas revenus de la base - et cette fenetre se rouvre a chaque recreation
         * du ViewModel. Un repli qui differe du defaut y ferait clignoter le repere d'un symbole a l'autre.
         */
        fun of(key: String?): GpsMarkerStyle = entries.firstOrNull { it.key == key } ?: ARROW_FILLED
    }
}
