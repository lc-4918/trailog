package fr.lc4918.trailog.ui.components

import fr.lc4918.trailog.ui.planner.StepMark
import kotlin.math.abs

/**
 * La pastille qu'on vient de deposer, tenue LA OU ON L'A DEPOSEE jusqu'a ce que le planificateur ait publie
 * son nouveau point.
 *
 * Le redessin du lever de doigt repartait des marqueurs du planificateur, qui n'avaient pas encore bouge : la
 * pastille retournait a sa place d'origine le temps que l'etape soit relocalisee et la composition rejouee,
 * puis sautait au bon endroit.
 */
class DroppedStepHold {
    private var stepId: Long? = null
    private var at: Pair<Double, Double>? = null

    val active: Boolean get() = stepId != null

    fun hold(id: Long, lonLat: Pair<Double, Double>) { stepId = id; at = lonLat }

    fun release() { stepId = null; at = null }

    /** Ou dessiner l'etape [id] : au point du depot si elle est tenue, sinon a [planned]. */
    fun position(id: Long, planned: Pair<Double, Double>): Pair<Double, Double> =
        if (id == stepId) at ?: planned else planned

    /**
     * Le planificateur a publie ses marqueurs : si l'etape tenue y est au point du depot, il a rattrape la
     * pastille et on la lache. Des marqueurs qui la montrent AILLEURS - un autre changement, la position
     * actuelle qui bouge - ne la lachent pas : ils ne disent rien du depot. Rend vrai si elle est lachee.
     */
    fun confirm(marks: List<StepMark>): Boolean {
        val held = at ?: return false
        if (marks.none { it.stepId == stepId && close(it.lon, held.first) && close(it.lat, held.second) }) return false
        release()
        return true
    }

    private fun close(a: Double, b: Double) = abs(a - b) < 1e-7
}
