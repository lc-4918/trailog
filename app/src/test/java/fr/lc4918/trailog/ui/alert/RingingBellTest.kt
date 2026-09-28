package fr.lc4918.trailog.ui.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le balancement de la cloche en alerte : une volee qui s'amortit, puis le silence. */
class RingingBellTest {

    @Test fun `la volee part et revient a la verticale`() {
        assertEquals(0f, bellSwing(0f), 0.001f)
        assertEquals(16f, bellSwing(100f), 0.001f)
        assertEquals(0f, bellSwing(700f), 0.001f)
    }

    @Test fun `elle bat de part et d'autre, de moins en moins fort`() {
        val pics = listOf(100f, 220f, 340f, 460f, 580f).map { bellSwing(it) }
        pics.zipWithNext().forEach { (a, b) ->
            assertTrue("change de cote : $a puis $b", a * b < 0)
            assertTrue("s'amortit : $a puis $b", kotlin.math.abs(b) < kotlin.math.abs(a))
        }
    }

    @Test fun `puis elle se tait jusqu'a la fin du cycle`() {
        for (ms in listOf(750f, 1000f, 1400f, BellCycleMs.toFloat())) assertEquals(0f, bellSwing(ms), 0.001f)
    }

    @Test fun `un instant hors du cycle reste borne`() {
        assertEquals(0f, bellSwing(-50f), 0.001f)
        assertEquals(0f, bellSwing(5000f), 0.001f)
    }
}
