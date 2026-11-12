package fr.lc4918.trailog.ui.planner

import org.junit.Assert.assertEquals
import org.junit.Test

/** Les libelles de la liste des etapes reprennent ceux de la carte : A, les rangs, B. */
class StepBadgeLabelsTest {

    private fun mark(stepId: Long, kind: StepMarkKind, number: Int = 0) =
        StepMark(stepId, lon = 6.0, lat = 45.0, kind = kind, number = number)

    @Test fun `depart en A, arrivee en B, etapes intermediaires numerotees`() {
        val marks = listOf(
            mark(1, StepMarkKind.Start),
            mark(2, StepMarkKind.Via, number = 1),
            mark(3, StepMarkKind.Via, number = 2),
            mark(4, StepMarkKind.End),
        )
        assertEquals(mapOf(1L to "A", 2L to "1", 3L to "2", 4L to "B"), stepBadgeLabels(marks))
    }

    @Test fun `un trajet sans etape intermediaire n'a que A et B`() {
        val marks = listOf(mark(7, StepMarkKind.Start), mark(8, StepMarkKind.End))
        assertEquals(mapOf(7L to "A", 8L to "B"), stepBadgeLabels(marks))
    }

    @Test fun `aucune etape posee, aucun libelle`() {
        assertEquals(emptyMap<Long, String>(), stepBadgeLabels(emptyList()))
    }
}
