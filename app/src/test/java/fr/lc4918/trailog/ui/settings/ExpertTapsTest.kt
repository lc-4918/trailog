package fr.lc4918.trailog.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le geste qui ouvre le mode expert : sept appuis rapproches, ni plus ni moins. */
class ExpertTapsTest {

    private fun appuis(t: ExpertTaps, vararg instants: Long) = instants.map { t.tap(it) }

    @Test fun `sept appuis rapproches basculent le mode`() {
        val r = appuis(ExpertTaps(), 0, 300, 600, 900, 1200, 1500, 1800)
        assertEquals(listOf(false, false, false, false, false, false, true), r)
    }

    /** Une pause trop longue repart de un : le geste ne se fait pas en plusieurs fois. */
    @Test fun `une pause fait repartir le compte`() {
        val t = ExpertTaps()
        appuis(t, 0, 300, 600, 900, 1200, 1500)
        assertFalse("pause de trois secondes", t.tap(4500))
        val r = appuis(t, 4800, 5100, 5400, 5700, 6000)
        assertFalse(r.last())
        assertTrue("le septieme depuis la pause", t.tap(6300))
    }

    /** Apres une bascule, il en faut sept autres pour revenir en arriere. */
    @Test fun `apres une bascule, le compte repart de zero`() {
        val t = ExpertTaps()
        appuis(t, 0, 100, 200, 300, 400, 500, 600)
        assertFalse(t.tap(700))
    }
}
