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
         * Chaque valeur sort d'une mesure, non d'une intuition (voir IMPLEMENTATION_TEST.md pour le
         * détail des trajets) :
         * - toutes les disciplines partent sur les voies douces : c'est le manque qui a motivé le réglage,
         *   et là où le réseau cyclable n'existe pas, la demande ne coûte rien ;
         * - le VTT cherche le dénivelé, seule discipline dans ce cas : sur Grenoble - Chamrousse, c'est
         *   ce qui fait passer les voies douces de 33 à 52 % en RACCOURCISSANT le trajet ;
         * - le gravel, le VTT et la marche acceptent les chemins, c'est leur raison d'être ;
         * - le vélo de route, lui, ne demande RIEN sur le revêtement, contre l'intuition. Exiger le revêtu
         *   ne le protège pas, il le fait fuir : sur Grenoble - Chamrousse, pour éviter 3 % de chemin, il
         *   rallonge de 3,5 km et perd un tiers de ses voies douces. La position "rester sur le revêtu"
         *   reste offerte à qui la veut, elle n'est simplement pas un défaut défendable.
         */
        fun defaultFor(profile: RoutingProfile): RoutingPrefs = when (profile) {
            RoutingProfile.ROAD_BIKE -> RoutingPrefs(WayPref.SOFT, HillPref.BALANCED, SurfacePref.BALANCED)
            RoutingProfile.GRAVEL -> RoutingPrefs(WayPref.SOFT, HillPref.BALANCED, SurfacePref.ROUGH)
            RoutingProfile.HYBRID_BIKE -> RoutingPrefs(WayPref.SOFT, HillPref.BALANCED, SurfacePref.BALANCED)
            RoutingProfile.MOUNTAIN_BIKE -> RoutingPrefs(WayPref.SOFT, HillPref.SEEK, SurfacePref.ROUGH)
            RoutingProfile.FOOT -> RoutingPrefs(WayPref.SOFT, HillPref.BALANCED, SurfacePref.ROUGH)
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
