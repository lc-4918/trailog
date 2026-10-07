package fr.lc4918.trailog.ui.components

import fr.lc4918.trailog.ui.planner.StepMark
import fr.lc4918.trailog.ui.planner.StepMarkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Une pastille deposee ne retourne pas a sa place d'origine le temps que le planificateur la rattrape. */
class DroppedStepHoldTest {

    private fun mark(id: Long, lon: Double, lat: Double) = StepMark(id, lon, lat, StepMarkKind.Via, 1)

    @Test fun `une etape tenue se dessine au point du depot, les autres a leur place`() {
        val hold = DroppedStepHold().apply { hold(7, 5.0 to 45.0) }
        assertEquals(5.0 to 45.0, hold.position(7, 1.0 to 43.0))
        assertEquals(1.0 to 43.0, hold.position(8, 1.0 to 43.0))
    }

    @Test fun `sans depot, tout se dessine ou le planificateur le dit`() {
        assertEquals(1.0 to 43.0, DroppedStepHold().position(7, 1.0 to 43.0))
    }

    /** Les marqueurs d'AVANT le rattrapage montrent encore l'ancien point : la pastille reste tenue. */
    @Test fun `des marqueurs qui montrent l'etape ailleurs ne la lachent pas`() {
        val hold = DroppedStepHold().apply { hold(7, 5.0 to 45.0) }
        assertFalse(hold.confirm(listOf(mark(7, 1.0, 43.0))))
        assertTrue(hold.active)
        assertEquals(5.0 to 45.0, hold.position(7, 1.0 to 43.0))
    }

    @Test fun `le planificateur qui rattrape le depot lache la pastille`() {
        val hold = DroppedStepHold().apply { hold(7, 5.0 to 45.0) }
        assertTrue(hold.confirm(listOf(mark(6, 1.0, 43.0), mark(7, 5.0, 45.0))))
        assertFalse(hold.active)
        assertEquals(5.0 to 45.0, hold.position(7, 5.0 to 45.0))
    }

    /** Le meme point sur une AUTRE etape ne dit rien de celle qu'on a deposee. */
    @Test fun `le point d'une autre etape ne compte pas`() {
        val hold = DroppedStepHold().apply { hold(7, 5.0 to 45.0) }
        assertFalse(hold.confirm(listOf(mark(8, 5.0, 45.0))))
        assertTrue(hold.active)
    }

    @Test fun `sans depot, rien n'est a confirmer`() {
        assertFalse(DroppedStepHold().confirm(listOf(mark(7, 5.0, 45.0))))
    }

    @Test fun `lacher rend la pastille au planificateur`() {
        val hold = DroppedStepHold().apply { hold(7, 5.0 to 45.0) }
        hold.release()
        assertEquals(1.0 to 43.0, hold.position(7, 1.0 to 43.0))
    }
}
