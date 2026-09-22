package fr.lc4918.trailog.ui.components

/**
 * Les calques de tete de la carte : ceux que rien ne doit recouvrir.
 *
 * Le repere de position GPS (son cercle de precision et son symbole) et le curseur du profil disent OU
 * l'on est. Un trait de trace pose par-dessus les efface, et la carte ment alors sur la position.
 *
 * MapLibre empile les calques dans leur ordre d'ajout : une couche ajoutee apres le repere passe devant
 * lui. Tout ce qui s'ajoute a la carte se glisse donc SOUS le plus bas des calques de tete presents.
 */
object MapHeadOverlays {
    const val USER_ACCURACY = "user-location-accuracy"
    const val USER_DOT = "user-location-dot"
    const val CURSOR_DOT = "cursor-dot"

    val ids = setOf(USER_ACCURACY, USER_DOT, CURSOR_DOT)

    /**
     * Le plus bas des calques de tete presents dans [layerIds], ou null s'il n'y en a aucun - la nouvelle
     * couche va alors au sommet, elle n'a rien a passer dessous.
     *
     * [layerIds] est l'ordre du style, du bas vers le haut : le premier trouve est donc le plus bas.
     */
    fun lowest(layerIds: List<String>): String? = layerIds.firstOrNull { it in ids }
}
