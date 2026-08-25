package fr.lc4918.trailog.domain.model

/**
 * Ce que l'utilisateur veut voir des points d'intérêt : quelles catégories.
 *
 * **Ce sont les catégories MASQUÉES qui sont retenues**, et non les affichées. Une catégorie ajoutée par
 * une version ultérieure apparaît alors d'elle-même, là où une liste d'affichées l'aurait laissée
 * invisible jusqu'à ce que l'utilisateur aille la chercher.
 *
 * **Le filtre EST l'interrupteur de la couche.** Il n'y a plus de "montrer les points d'intérêt" à côté :
 * la carte montre exactement ce qui est retenu ici, et [nothingShown] - tout masqué - éteint la couche.
 * Deux commandes pour un seul comportement finissent toujours par se contredire, et celle-ci se lit sur la
 * bulle elle-même : une catégorie en surbrillance est une catégorie qu'on voit sur la carte.
 *
 * Le filtre "uniquement les lieux vélo", par groupe, a été retiré : il demandait de comprendre ce que
 * DATAtourisme appelle le thème vélo pour deviner pourquoi la moitié des campings disparaissait, et se
 * réglait dans un écran que personne n'ouvrait en roulant.
 */
data class PoiFilters(
    val hidden: Set<PoiCategory> = emptySet(),
) {
    /** Les catégories réellement demandées au service. */
    val shown: Set<PoiCategory> get() = PoiCategory.entries.toSet() - hidden

    fun isShown(c: PoiCategory) = c !in hidden

    /** Plus rien n'est retenu : la couche n'a rien à montrer, et s'éteint d'elle-même. */
    val nothingShown: Boolean get() = hidden.size == PoiCategory.entries.size

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

    /** Tout masquer, tous groupes confondus : éteindre la couche en un geste plutôt qu'en vingt-sept. */
    fun hideAll(): PoiFilters = copy(hidden = PoiCategory.entries.toSet())

    fun hiddenCsv() = hidden.joinToString(",") { it.key }

    companion object {
        /**
         * Relit le réglage enregistré. Une clé inconnue est ignorée plutôt que fatale : un réglage écrit
         * par une version plus récente, rouvert par une plus ancienne, ne doit pas vider les autres.
         */
        fun of(hiddenCsv: String?): PoiFilters = PoiFilters(
            hidden = hiddenCsv.orEmpty().split(',').mapNotNull { PoiCategory.byKey(it.trim()) }.toSet(),
        )

        /** Tout masqué, la forme enregistrée : ce que porte une installation neuve (cf. `SettingsEntity`). */
        fun allHiddenCsv(): String = PoiCategory.entries.joinToString(",") { it.key }
    }
}

/** État d'une case de groupe : tout, rien, ou une partie (cf. `PoiFilters.groupState`). */
enum class GroupCheck { ALL, SOME, NONE }
