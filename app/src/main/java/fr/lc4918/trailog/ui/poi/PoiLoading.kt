package fr.lc4918.trailog.ui.poi

import fr.lc4918.trailog.domain.model.PoiGroup
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.poi.PoiCell
import fr.lc4918.trailog.poi.PoiCells
import fr.lc4918.trailog.poi.PoiUnit

/**
 * Les règles de chargement des points d'intérêt : quand demander, et quoi demander.
 *
 * Hors de l'écran et sans Android, parce que ce sont elles qui décident du nombre de requêtes envoyées aux
 * services, et qu'une règle qui ne se teste pas finit par ne plus se vérifier.
 */
object PoiLoading {

    /**
     * Zoom en deçà duquel on ne charge rien.
     *
     * **Descendu de 11 à 9** : on prépare une étape en regardant le trajet entier, pas un quartier. Ce qui
     * rend ce zoom tenable est le **couloir des traces** (cf. [PoiCorridor]) : à 9, la vue porte une
     * région, mais on ne demande que les cellules que le trajet affiché traverse. Sans trace ouverte, on
     * s'arrête à [MAX_CELLS] cellules autour du centre, et la carte le dit.
     */
    const val MIN_ZOOM = 9.0

    /**
     * Délai après le dernier geste, en millisecondes.
     *
     * Un déplacement de carte émet des dizaines d'événements ; sans attente, chacun partirait en requête.
     * 500 ms est le temps qu'il faut pour distinguer "j'ai fini de déplacer" de "je continue".
     */
    const val DEBOUNCE_MS = 500L

    /**
     * Cellules demandées au plus pour une vue (cf. [PoiCells]), les plus proches du centre d'abord.
     *
     * Quarante cellules de 0,1 degré, c'est un écran au zoom 11, ou un trajet de deux cents kilomètres au
     * zoom 9 avec le couloir des traces. Au-delà, la vue est trop large pour qu'on y cherche un restaurant,
     * et les requêtes s'accumuleraient pour des lieux qu'on ne distinguerait pas.
     */
    const val MAX_CELLS = 40

    /**
     * L'emprise à demander pour un écran donné : la même, élargie de [MARGIN] de part et d'autre, pour
     * que les marqueurs juste hors de l'écran soient déjà là au premier déplacement.
     */
    const val MARGIN = 0.05

    fun grow(box: Bbox, margin: Double = MARGIN): Bbox {
        val dLon = (box.east - box.west) * margin
        val dLat = (box.north - box.south) * margin
        return Bbox.of(
            (box.west - dLon).coerceAtLeast(-180.0), (box.south - dLat).coerceAtLeast(-85.0),
            (box.east + dLon).coerceAtMost(180.0), (box.north + dLat).coerceAtMost(85.0),
        )
    }

    /**
     * Ce qu'une vue demande : ses unités, dans l'ordre où les charger, et ce qui a été écarté.
     *
     * [capped] : la vue portait plus de [MAX_CELLS] cellules, et seules les plus proches du centre sont
     * demandées. [away] : le couloir des traces a tout écarté - aucune trace affichée ne passe par ici.
     */
    data class Plan(val units: List<PoiUnit>, val capped: Boolean, val away: Boolean)

    /**
     * Les unités à charger pour la vue [view] : les cellules qu'elle touche, que le couloir des traces
     * laisse passer, triées du centre vers les bords, et bornées à [MAX_CELLS].
     *
     * Le couloir gouverne la REQUÊTE et non le seul affichage : à plusieurs centaines de kilomètres de
     * toute trace, on interrogeait les deux services pour tout jeter. Il s'applique ici cellule par cellule,
     * et un long trajet vu de haut ne demande donc que les cellules qu'il traverse.
     */
    fun plan(
        view: Bbox, groups: Set<PoiGroup>, complement: Boolean,
        tracks: List<List<Pair<Double, Double>>>, corridorM: Int,
        maxCells: Int = MAX_CELLS,
    ): Plan {
        val box = grow(view)
        val touchees = PoiCells.covering(box)
        val bordees = touchees.filter { PoiCorridor.crosses(it.bbox, tracks, corridorM.toDouble()) }
        if (bordees.isEmpty()) return Plan(emptyList(), capped = false, away = touchees.isNotEmpty())
        val triees: List<PoiCell> = PoiCells.byDistance(
            bordees, (view.west + view.east) / 2, (view.south + view.north) / 2,
        )
        val retenues = triees.take(maxCells)
        return Plan(
            PoiCells.units(retenues, groups, complement),
            capped = triees.size > maxCells,
            away = false,
        )
    }
}
