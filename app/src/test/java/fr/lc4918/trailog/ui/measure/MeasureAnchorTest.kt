package fr.lc4918.trailog.ui.measure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasureAnchorTest {

    /** Ancre choisie parmi [count] points dont seuls [visible] sont dans l'emprise de la carte. */
    private fun pick(count: Int, vararg visible: Int) =
        MeasureAnchor.pick(count) { it in visible.toSet() }

    @Test fun visibleMiddle_isTheAnchor() {
        assertEquals(3, pick(7, 0, 3, 6))
    }

    @Test fun middleOutOfSight_fallsOnTheNearestVisiblePoint() {
        assertEquals(5, pick(7, 0, 5))       // 5 est a 2 crans du milieu, 0 a 3
        assertEquals(1, pick(7, 1, 6))       // 1 est a 2 crans, 6 a 3
    }

    @Test fun nothingVisible_hasNoAnchor() {
        assertNull(pick(7))
        assertNull(MeasureAnchor.pick(0) { true })
    }

    @Test fun visibleMiddle_costsASingleTest() {
        var calls = 0
        MeasureAnchor.pick(801) { calls++; true }
        assertEquals(1, calls)
    }

    @Test fun search_neverLooksOutsideTheParcours() {
        var lowest = Int.MAX_VALUE
        var highest = Int.MIN_VALUE
        MeasureAnchor.pick(6) { i ->
            lowest = minOf(lowest, i); highest = maxOf(highest, i)
            false
        }
        assertEquals(0, lowest)
        assertEquals(5, highest)
    }
}
