package fr.lc4918.trailog.domain.model

import fr.lc4918.trailog.geocode.GeocodePlace

/**
 * Les derniers lieux retenus dans le planificateur, proposés quand un champ vide prend le focus.
 *
 * Cinq au plus : la liste s'affiche sous un champ de saisie, au-dessus du clavier, et au-delà elle
 * chasserait de l'écran les propositions du géocodeur - celles qu'on est en train de taper, qui priment
 * toujours sur ce qu'on a fait hier. La bande défile, mais ce qui compte est ce qu'on voit sans défiler.
 *
 * Ce sont les lieux **retenus**, non le texte frappé : c'est le lieu qui a des coordonnées, donc le seul
 * qu'on puisse reposer dans une étape sans redemander quoi que ce soit au géocodeur. Une saisie
 * abandonnée en cours de frappe n'a rien à faire ici.
 */
data class PlannerHistory(val places: List<GeocodePlace> = emptyList()) {

    /**
     * Ajoute [place] en tête et rend l'historique borné à [MAX].
     *
     * Le même lieu choisi deux fois ne s'y met pas deux fois : il **remonte**. Sans quoi quelques
     * allers-retours entre chez soi et le col voisin rempliraient la liste de deux entrées répétées, et
     * l'historique ne se souviendrait plus de rien d'autre.
     */
    operator fun plus(place: GeocodePlace): PlannerHistory =
        PlannerHistory((listOf(place) + places.filter { it.label != place.label }).take(MAX))

    /**
     * Forme enregistrée : une ligne par lieu, `libellé` puis longitude et latitude séparés par des
     * tabulations.
     *
     * Ni JSON ni virgules : un libellé d'adresse en contient toujours ("Mirepoix, 09500 Ariège, France"),
     * jamais de tabulation ni de saut de ligne.
     */
    fun asText(): String = places.joinToString("\n") { "${it.label}\t${it.lon}\t${it.lat}" }

    companion object {
        const val MAX = 5

        /**
         * Relit la forme enregistrée. Une ligne illisible est ignorée plutôt que fatale : c'est un
         * confort, et il ne doit pas priver le planificateur de s'ouvrir.
         */
        fun of(text: String?): PlannerHistory = PlannerHistory(
            text.orEmpty().lineSequence().mapNotNull { ligne ->
                val p = ligne.split('\t')
                if (p.size != 3) return@mapNotNull null
                val lon = p[1].toDoubleOrNull() ?: return@mapNotNull null
                val lat = p[2].toDoubleOrNull() ?: return@mapNotNull null
                if (p[0].isBlank()) null else GeocodePlace(p[0], lon, lat)
            }.take(MAX).toList()
        )
    }
}
