package fr.lc4918.trailog.domain.model

/**
 * Discipline retenue pour calculer un itinéraire (réglage Carte / Géocodage).
 *
 * Le vocabulaire reste celui de l'utilisateur, pas celui du moteur : la correspondance avec les modèles de
 * coût du service vit dans son client (cf. `routing/Valhalla.kt`). Changer un jour de moteur ne doit pas
 * obliger à réécrire le réglage, ni surtout à changer les clés déjà enregistrées en base.
 */
enum class RoutingProfile(val key: String) {
    ROAD_BIKE("road"),
    GRAVEL("gravel"),
    HYBRID_BIKE("hybrid"),
    MOUNTAIN_BIKE("mtb"),
    FOOT("foot");

    companion object {
        /** Repli sur le VTC : le vélo à tout faire, celui qui emprunte le plus de chemins sans en exclure. */
        fun of(key: String?): RoutingProfile = entries.firstOrNull { it.key == key } ?: HYBRID_BIKE
    }
}
