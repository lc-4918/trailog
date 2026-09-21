package fr.lc4918.trailog.ui.components

import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.ui.profile.SlopeRamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les troncons colories d'une ligne, et la taille du chevron du sens de parcours. */
class TrackLineStyleTest {

    private fun s(i: Int, slope: Double) = Sample(i * 100.0, 0.0, slope, null, 6.0 + i * 0.001, 45.0)

    private fun colors(json: String) = Regex("\"color\":\"(#[0-9A-F]{6})\"").findAll(json).map { it.groupValues[1] }.toList()

    @Test fun `sans pente, un seul troncon de la couleur demandee`() {
        val json = SlopeLines.features(listOf(s(0, 0.0), s(1, 8.0), s(2, -8.0)), null, "#000000")
        assertEquals(listOf("#000000"), colors(json))
    }

    /** Une plage par classe, et chaque plage reprend le dernier point de la precedente. */
    @Test fun `une plage par classe, jointes bout a bout`() {
        val pts = listOf(s(0, 0.0), s(1, 6.0), s(2, 6.2), s(3, -12.0))
        val json = SlopeLines.features(pts, 50, "#000000")
        assertEquals(listOf(SlopeRamp.hexFor(6.0, 50), SlopeRamp.hexFor(-12.0, 50)), colors(json))
        // Le point 2 termine la premiere plage et ouvre la seconde.
        assertEquals(2, Regex("\\[6.002,45.0]").findAll(json).count())
    }

    @Test fun `une collection vide reste une collection valide`() {
        assertEquals("""{"type":"FeatureCollection","features":[]}""", SlopeLines.collection(emptyList(), 5))
    }

    /** Le chevron deborde a peine le trait : moins d'une fois et demie sa largeur, plus un dp. */
    @Test fun `le chevron ne deborde guere le trait`() {
        for (w in listOf(2f, 4f, 8f, 12f)) {
            val h = TrackChevron.heightPx(w, 3f)
            assertTrue("largeur $w", h > w * 3f && h <= (w * 1.5f + 1f) * 3f + 1)
        }
    }
}
