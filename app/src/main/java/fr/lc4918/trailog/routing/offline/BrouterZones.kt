package fr.lc4918.trailog.routing.offline

import fr.lc4918.trailog.map.offline.Bbox

/**
 * Une zone de donnees d'itineraire hors ligne : un pays ou une grande region, telle qu'on la choisit.
 *
 * **Ce qu'on telecharge, ce sont ses carres** (cf. [BrouterTile]) : le serveur ne distribue rien d'autre.
 * Une zone en nomme la liste, a la main, parce que les carres de cinq degres debordent des frontieres - la
 * France en touche douze par son rectangle, et n'en demande que neuf. Deux zones se partagent souvent des
 * carres : les Alpes n'en ont aucun qui ne soit aussi francais, italien, suisse ou autrichien.
 *
 * @param flag le code du drapeau (cf. `assets/flags`), ou null pour une region sans drapeau - un massif,
 *   que l'ecran marque d'une montagne.
 * @param area l'emprise de la zone elle-meme, telle qu'on la montre sur la carte. Plus etroite que ses
 *   carres, qui ne sont qu'un moyen.
 */
data class BrouterZone(
    val id: String,
    val flag: String?,
    val area: Bbox,
    val tiles: Set<BrouterTile>,
)

object BrouterZones {

    private fun t(vararg noms: String): Set<BrouterTile> =
        noms.mapTo(LinkedHashSet()) { requireNotNull(BrouterTile.parse(it)) { it } }

    private fun b(w: Double, s: Double, e: Double, n: Double) = Bbox(west = w, south = s, east = e, north = n)

    /**
     * Le catalogue, pays d'abord, massifs ensuite.
     *
     * Les carres de chaque zone ont ete choisis sur la carte du serveur : ceux que la zone touche, sans ceux
     * qu'elle n'effleure que d'une bande de mer ou de quelques kilometres. La Belgique perd ainsi le carre
     * E0_N45 - cent vingt-sept megaoctets de France pour la pointe de Chimay.
     */
    val all: List<BrouterZone> = listOf(
        BrouterZone("fr", "fr", b(-5.2, 41.3, 9.6, 51.1),
            t("W10_N45", "W5_N40", "W5_N45", "W5_N50", "E0_N40", "E0_N45", "E0_N50", "E5_N40", "E5_N45")),
        BrouterZone("es", "es", b(-9.4, 35.9, 4.4, 43.8),
            t("W10_N35", "W10_N40", "W5_N35", "W5_N40", "E0_N35", "E0_N40")),
        BrouterZone("pt", "pt", b(-9.6, 36.9, -6.1, 42.2), t("W10_N35", "W10_N40")),
        BrouterZone("it", "it", b(6.6, 36.6, 18.6, 47.1),
            t("E5_N35", "E5_N40", "E5_N45", "E10_N35", "E10_N40", "E10_N45", "E15_N35", "E15_N40")),
        BrouterZone("ch", "ch", b(5.9, 45.8, 10.5, 47.8), t("E5_N45", "E10_N45")),
        BrouterZone("at", "at", b(9.5, 46.4, 17.2, 49.0), t("E5_N45", "E10_N45", "E15_N45")),
        BrouterZone("de", "de", b(5.9, 47.3, 15.0, 55.1), t("E5_N45", "E5_N50", "E10_N45", "E10_N50")),
        BrouterZone("be", "be", b(2.5, 49.5, 6.4, 51.5), t("E0_N50", "E5_N50", "E5_N45")),
        BrouterZone("gb", "gb", b(-8.2, 49.9, 1.8, 58.7), t("W10_N50", "W5_N50", "E0_N50", "W10_N55", "W5_N55")),
        BrouterZone("alps", null, b(5.0, 43.7, 16.0, 48.3), t("E5_N40", "E5_N45", "E10_N45", "E15_N45")),
        BrouterZone("pyrenees", null, b(-2.0, 42.0, 3.3, 43.4), t("W5_N40", "E0_N40")),
    )

    fun byId(id: String): BrouterZone? = all.firstOrNull { it.id == id }

    /**
     * Les carres qu'on peut retirer en supprimant [zone] : les siens, sauf ceux qu'une autre zone
     * telechargee utilise encore. Supprimer les Alpes ne doit pas amputer la France.
     */
    fun releasable(zone: BrouterZone, kept: Collection<BrouterZone>): Set<BrouterTile> {
        val encore = kept.filter { it.id != zone.id }.flatMapTo(HashSet()) { it.tiles }
        return zone.tiles.filterTo(LinkedHashSet()) { it !in encore }
    }
}
