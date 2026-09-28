package fr.lc4918.trailog.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le motif de la vibration d'alerte : sans attente, deux secousses, puis un silence plus long qu'elles. */
class AlertVibrationTest {

    @Test fun `deux secousses, puis un silence`() {
        val p = AlertVibration.PATTERN
        assertEquals("part tout de suite", 0L, p[0])
        assertEquals("attente, secousse, pause, secousse, silence", 5, p.size)
        assertTrue("le silence dure plus qu'une secousse", p[4] > p[1] && p[4] > p[3])
        assertTrue("la pause entre les deux est plus courte que le silence", p[2] < p[4])
    }
}
