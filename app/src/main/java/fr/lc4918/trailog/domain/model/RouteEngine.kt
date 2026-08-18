package fr.lc4918.trailog.domain.model

/**
 * Moteur qui calcule les itinéraires (réglage Trajets).
 *
 * Deux moteurs, et le réglage existe pour les comparer sur le terrain plutôt que sur le papier. Ils ne
 * répondent pas à la même question :
 *
 * - **Valhalla** rend un itinéraire à partir d'un modèle de coût figé dans son code, dont il n'expose
 *   qu'une poignée de curseurs. C'est ce qui plafonne le résultat : rien n'y privilégie un sentier au
 *   marcheur, et une voie verte gravillonnée y est fuie quelle que soit l'option (cf. `Valhalla`).
 * - **BRouter** lit un **profil**, un texte que l'on écrit et que l'on envoie avec la requête, qui décrit
 *   le coût tag par tag. Tout ce que Valhalla interdit de dire s'y écrit - au prix d'un profil à tenir
 *   par discipline (cf. `BrouterProfile`).
 *
 * Le vocabulaire de l'application ne change pas d'un moteur à l'autre : les cinq disciplines et les trois
 * préférences restent celles de l'utilisateur, et chaque client les traduit dans sa propre langue. C'est
 * ce qui rend la comparaison honnête - on demande la même chose aux deux.
 *
 * **BRouter est le défaut**, y compris sur une installation en place. Non par préférence mais par mesure :
 * à pied, Moulin-Neuf - Mirepoix passe de 15 à 87 % de voies douces, la voie verte étant enfin empruntée,
 * et Revel - Sorèze de 24 à 57 % de chemins. Valhalla reste offert d'un tap - il calcule plus vite sur les
 * longues distances, son graphe étant hiérarchique.
 */
enum class RouteEngine(val key: String) {
    VALHALLA("valhalla"),
    BROUTER("brouter");

    companion object {
        /** Repli sur BRouter : c'est le moteur par défaut, celui vers lequel une base sans réglage lisible
         *  doit calculer - et non le premier de la liste, qui ne veut rien dire. */
        fun of(key: String?): RouteEngine = entries.firstOrNull { it.key == key } ?: BROUTER
    }
}
