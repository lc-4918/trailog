package fr.lc4918.trailog.domain.model

/**
 * Ce qu'on demande au calcul d'itinéraire, en mots d'utilisateur : quelles voies, quel relief, quel
 * revêtement.
 *
 * Trois questions, et non les huit options du moteur : ce sont les seules dont la mesure a montré qu'elles
 * déplacent réellement le tracé (cf. `Valhalla.costingOptionsOf` pour la traduction, et les chiffres qui
 * ont servi à choisir les défauts). Le vocabulaire reste celui du cycliste et du marcheur - "voies vertes",
 * "éviter les côtes" - comme pour [RoutingProfile] : le jour où le moteur change, seule sa traduction bouge.
 *
 * Chaque question a une position CENTRALE qui n'envoie rien du tout. Ce n'est pas un détail : l'instance
 * publique du service n'applique pas les valeurs par défaut de la documentation, et lui renvoyer un
 * prétendu défaut change le trajet. Ne rien dire est donc la seule façon de la laisser faire.
 */
data class RoutingPrefs(
    val ways: WayPref = WayPref.BALANCED,
    val hills: HillPref = HillPref.BALANCED,
    val surface: SurfacePref = SurfacePref.BALANCED,
) {
    /** Forme enregistrée : trois clés séparées par des virgules, dans l'ordre des trois questions. */
    fun asCsv(): String = "${ways.key},${hills.key},${surface.key}"

    companion object {
        /** Ne rien demander : le service décide seul, comme avant que ce réglage existe. */
        val Balanced = RoutingPrefs()

        /**
         * Ce que porte une discipline tant que l'utilisateur n'y a pas touché.
         *
         * Une phrase par discipline, et le réglage qui la dit (les trajets qui ont servi à les fixer sont
         * cités dans `Valhalla.bicycleTypeOf` et `Valhalla.costingOptionsOf`, au plus près des valeurs) :
         * - **vélo de route** : uniquement sur la route. C'est la seule discipline à exiger le revêtu, et
         *   c'est ce que veut dire "de route" - le moteur lui interdit alors ce qui est plus grossier que
         *   la grave compactée. Ce n'est pas gratuit : sur Grenoble - Chamrousse, éviter 3 % de non revêtu
         *   rallonge de 3,6 km. C'est le prix demandé, non un effet de bord ;
         * - **gravel** : accepte les chemins et le dénivelé, et privilégie les chemins ;
         * - **VTC** : accepte les chemins et privilégie les voies vertes, mais ne cherche pas le dénivelé -
         *   c'est ce qui le sépare du gravel et du VTT ;
         * - **VTT** : accepte et privilégie les chemins, plus fort que les autres (cf. `use_roads` à 0 dans
         *   `Valhalla.costingOptionsOf`), et accepte le dénivelé ;
         * - **à pied** : accepte les chemins, y compris les sentiers de montagne, et accepte le dénivelé -
         *   ce dernier raccourcit la marche au lieu de l'allonger, le détour qui évite la côte coûtant plus
         *   cher que la côte.
         *
         * Toutes partent sur les voies douces : c'est le manque qui a motivé le réglage, et là où le réseau
         * cyclable n'existe pas, la demande ne coûte rien.
         *
         * Accepter les chemins n'est pas une nuance : c'est ce qui donne au vélo la monture qui sait rouler
         * sur les voies vertes françaises, gravillonnées pour la plupart (cf. `Valhalla.bicycleTypeOf`).
         */
        fun defaultFor(profile: RoutingProfile): RoutingPrefs = when (profile) {
            RoutingProfile.ROAD_BIKE -> RoutingPrefs(WayPref.SOFT, HillPref.BALANCED, SurfacePref.PAVED)
            RoutingProfile.GRAVEL -> RoutingPrefs(WayPref.SOFT, HillPref.SEEK, SurfacePref.ROUGH)
            RoutingProfile.HYBRID_BIKE -> RoutingPrefs(WayPref.SOFT, HillPref.BALANCED, SurfacePref.ROUGH)
            RoutingProfile.MOUNTAIN_BIKE -> RoutingPrefs(WayPref.SOFT, HillPref.SEEK, SurfacePref.ROUGH)
            RoutingProfile.FOOT -> RoutingPrefs(WayPref.SOFT, HillPref.SEEK, SurfacePref.ROUGH)
        }

        /**
         * Relit la forme enregistrée, en retombant sur le défaut de [profile] pour ce qui manque.
         *
         * Champ par champ, et non tout ou rien : une clé inconnue - réglage écrit par une version plus
         * récente, puis rouvert par une plus ancienne - ne doit pas emporter les deux autres.
         */
        fun of(csv: String?, profile: RoutingProfile): RoutingPrefs {
            val d = defaultFor(profile)
            val parts = csv?.split(',').orEmpty()
            fun part(i: Int) = parts.getOrNull(i)?.trim()
            return RoutingPrefs(
                WayPref.entries.firstOrNull { it.key == part(0) } ?: d.ways,
                HillPref.entries.firstOrNull { it.key == part(1) } ?: d.hills,
                SurfacePref.entries.firstOrNull { it.key == part(2) } ?: d.surface,
            )
        }
    }
}

/** Sur quoi rouler ou marcher : la voirie, ou ce qui en est séparé - voies vertes, pistes, chemins. */
enum class WayPref(val key: String) {
    ROADS("roads"),
    BALANCED("balanced"),
    SOFT("soft"),
}

/** Ce qu'on fait des côtes. La position haute ne cherche pas la difficulté pour elle-même : elle cesse de
 *  payer le détour qui l'évite, ce qui ramène sur les chemins qui montent. */
enum class HillPref(val key: String) {
    AVOID("avoid"),
    BALANCED("balanced"),
    SEEK("seek"),
}

/** Ce qu'on accepte sous les roues ou sous les pieds. */
enum class SurfacePref(val key: String) {
    PAVED("paved"),
    BALANCED("balanced"),
    ROUGH("rough"),
}
