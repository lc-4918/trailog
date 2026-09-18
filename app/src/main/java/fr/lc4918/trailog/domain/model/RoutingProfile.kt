package fr.lc4918.trailog.domain.model

/**
 * Discipline retenue pour calculer un itinéraire (réglage Carte / Géocodage).
 *
 * Le vocabulaire reste celui de l'utilisateur, pas celui du moteur : la correspondance avec les modèles de
 * coût du service vit dans son client (cf. `routing/Valhalla.kt`). Changer un jour de moteur ne doit pas
 * obliger à réécrire le réglage, ni surtout à changer les clés déjà enregistrées en base.
 *
 * **L'ORDRE de déclaration est celui de la ligne des disciplines**, aux trois endroits qui la montrent : le
 * planificateur, la discipline par défaut et les préférences de tracé. Il va du bitume aux chemins les plus
 * rudes, la marche en bout de ligne - route, VTC, gravel, VTT, à pied. Le gravel se tenait avant le VTC, et
 * passe après lui : un gravel emprunte plus de chemins qu'un VTC, pas moins.
 *
 * Rien d'autre ne dépend de ce rang : les réglages enregistrent la [key], jamais la position.
 */
enum class RoutingProfile(val key: String) {
    ROAD_BIKE("road"),
    HYBRID_BIKE("hybrid"),
    GRAVEL("gravel"),
    MOUNTAIN_BIKE("mtb"),
    FOOT("foot");

    companion object {
        /** Repli sur le VTC : le vélo à tout faire, celui qui emprunte le plus de chemins sans en exclure. */
        fun of(key: String?): RoutingProfile = entries.firstOrNull { it.key == key } ?: HYBRID_BIKE
    }
}
