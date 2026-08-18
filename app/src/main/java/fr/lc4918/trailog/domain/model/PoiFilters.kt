package fr.lc4918.trailog.domain.model

/**
 * Ce que l'utilisateur veut voir des points d'intérêt : quelles catégories, et quels groupes limités aux
 * lieux qui portent le thème vélo.
 *
 * **Ce sont les catégories MASQUÉES qui sont retenues**, et non les affichées. Un réglage vide veut alors
 * dire "tout montrer", ce qui est le comportement attendu d'une couche qu'on vient d'allumer ; et une
 * catégorie ajoutée par une version ultérieure apparaît d'elle-même, là où une liste d'affichées l'aurait
 * laissée invisible jusqu'à ce que l'utilisateur aille la chercher.
 *
 * Le filtre vélo est **par groupe** : on veut des hébergements qui accueillent les cyclistes sans exiger
 * la même chose des points d'eau. Il porte le thème `Bike` de DATAtourisme, seul signal vélo que les
 * lieux eux-mêmes portent - le label Accueil Vélo n'y existe pas, et le rattachement à une véloroute n'est
 * porté que par les tronçons d'itinéraire (cf. `poi/Datatourisme`).
 */
data class PoiFilters(
    val hidden: Set<PoiCategory> = emptySet(),
    val bikeGroups: Set<PoiGroup> = emptySet(),
) {
    /** Les catégories réellement demandées au service. */
    val shown: Set<PoiCategory> get() = PoiCategory.entries.toSet() - hidden

    fun isShown(c: PoiCategory) = c !in hidden

    /** Une case à cocher de groupe a trois états : tout coché, rien, ou entre les deux. */
    fun groupState(g: PoiGroup): GroupCheck {
        val cats = PoiCategory.of(g)
        val n = cats.count { isShown(it) }
        return when (n) {
            0 -> GroupCheck.NONE
            cats.size -> GroupCheck.ALL
            else -> GroupCheck.SOME
        }
    }

    fun toggle(c: PoiCategory) =
        copy(hidden = if (c in hidden) hidden - c else hidden + c)

    /** "Tout sélectionner" d'un groupe : coche tout, ou décoche tout si tout était déjà coché. */
    fun toggleGroup(g: PoiGroup): PoiFilters {
        val cats = PoiCategory.of(g).toSet()
        return copy(hidden = if (groupState(g) == GroupCheck.ALL) hidden + cats else hidden - cats)
    }

    fun toggleBike(g: PoiGroup) =
        copy(bikeGroups = if (g in bikeGroups) bikeGroups - g else bikeGroups + g)

    fun isBikeOnly(g: PoiGroup) = g in bikeGroups

    /**
     * Les catégories à demander sans le filtre vélo, et celles à demander avec.
     *
     * Deux requêtes au plus, et non une par groupe : le service accepte une liste de classes, et le filtre
     * vélo est la seule chose qui sépare vraiment deux demandes. Un groupe dont aucune catégorie n'est
     * cochée ne pèse dans aucune des deux.
     */
    fun queries(): Pair<Set<PoiCategory>, Set<PoiCategory>> {
        val visibles = shown
        return visibles.filter { it.group !in bikeGroups }.toSet() to
            visibles.filter { it.group in bikeGroups }.toSet()
    }

    fun hiddenCsv() = hidden.joinToString(",") { it.key }
    fun bikeCsv() = bikeGroups.joinToString(",") { it.key }

    companion object {
        /**
         * Relit les deux réglages enregistrés. Une clé inconnue est ignorée plutôt que fatale : un réglage
         * écrit par une version plus récente, rouvert par une plus ancienne, ne doit pas vider les autres.
         */
        fun of(hiddenCsv: String?, bikeCsv: String?): PoiFilters = PoiFilters(
            hidden = hiddenCsv.orEmpty().split(',').mapNotNull { PoiCategory.byKey(it.trim()) }.toSet(),
            bikeGroups = bikeCsv.orEmpty().split(',')
                .mapNotNull { k -> PoiGroup.entries.firstOrNull { it.key == k.trim() } }.toSet(),
        )
    }
}

/** État d'une case de groupe : tout, rien, ou une partie (cf. `PoiFilters.groupState`). */
enum class GroupCheck { ALL, SOME, NONE }
