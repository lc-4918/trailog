package fr.lc4918.trailog.domain.model

import fr.lc4918.trailog.geocode.GeocodePlace

/**
 * Les derniers lieux rencontrés dans l'application, proposés dans le planificateur quand un champ vide
 * prend le focus.
 *
 * **Quatre sources l'alimentent**, et non la seule saisie d'étape : une étape retenue dans le
 * planificateur, un lieu trouvé par la recherche, un point d'intérêt dont on a ouvert l'infobulle,
 * l'adresse d'un appui long sur la carte. Toutes disent la même chose - voilà un endroit qui intéresse
 * celui qui tient le téléphone - et un trajet se compose rarement dans la foulée : on regarde la carte, on
 * consulte, et c'est plus tard qu'on veut y aller. Réduire l'historique aux champs du planificateur, c'est
 * ne se souvenir que des trajets déjà faits, jamais de celui qu'on prépare.
 *
 * **Huit au plus.** Le plafond était de cinq quand seules les saisies d'étape le remplissaient ; les
 * quatre sources l'épuisent bien plus vite - parcourir la carte avec la couche des points d'intérêt
 * allumée chasse cinq entrées en autant de taps, et l'historique ne se souvenait plus du trajet qu'on
 * préparait la veille.
 *
 * Huit et non davantage : la liste s'affiche sous un champ de saisie, au-dessus du clavier, et au-delà
 * elle chasserait de l'écran les propositions du géocodeur - celles qu'on est en train de taper, qui
 * priment toujours sur ce qu'on a fait hier. La bande défile, ce qui rend les dernières atteignables sans
 * les mettre sur le chemin ; mais ce qui compte reste ce qu'on voit sans défiler.
 *
 * Ce sont des **lieux**, non du texte frappé : ce qui entre ici a des coordonnées, et c'est la seule chose
 * qu'on puisse reposer dans une étape sans redemander quoi que ce soit au géocodeur. Une saisie abandonnée
 * en cours de frappe n'a rien à faire ici, pas plus qu'un point de la carte dont on n'a pas su le nom.
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
        const val MAX = 8

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
