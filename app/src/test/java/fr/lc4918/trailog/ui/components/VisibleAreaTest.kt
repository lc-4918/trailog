package fr.lc4918.trailog.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La part visible de la carte, sous les boutons du haut et au-dessus du panneau du bas (cf. VisibleArea).
 *
 * Un ecran de 1000 x 2000 px, 200 px caches en haut, un panneau de profil de 800 px en bas : on voit de
 * y = 200 a y = 1200.
 */
class VisibleAreaTest {

    private val vue = VisibleArea(widthPx = 1000f, heightPx = 2000f, topCoverPx = 200f, bottomCoverPx = 800f)

    @Test fun `un point au milieu de la part visible est visible`() {
        assertTrue(vue.contains(500f, 700f, marginPx = 96f))
    }

    /** Le cas qui ne se voyait pas : dans la carte, mais sous le panneau du profil. */
    @Test fun `un point sous le panneau n'est pas visible`() {
        assertFalse(vue.contains(500f, 1500f, marginPx = 96f))
        // Et pas davantage colle a son bord.
        assertFalse(vue.contains(500f, 1150f, marginPx = 96f))
    }

    @Test fun `un point sous les boutons du haut n'est pas visible`() {
        assertFalse(vue.contains(500f, 150f, marginPx = 96f))
    }

    /** Sans panneau ni boutons, la part visible est la carte entiere, marge comprise. */
    @Test fun `sans rien de cache, toute la carte est visible`() {
        val libre = VisibleArea(1000f, 2000f, 0f, 0f)
        assertTrue(libre.contains(500f, 1800f, marginPx = 96f))
        assertFalse(libre.contains(500f, 1950f, marginPx = 96f))
    }

    /**
     * Recentrer amene le point au milieu de la part visible (y = 700), et non au milieu de la carte
     * (y = 1000), qui tombe ici sous le panneau.
     */
    @Test fun `le point recentre vient au milieu de la part visible`() {
        val (cx, cy) = vue.centerFor(300f, 1500f)
        // Deplacer le centre de la carte de (cx - 500, cy - 1000) amene le point en (500, 700).
        assertEquals(500f, 300f - (cx - 500f), 1e-3f)
        assertEquals(700f, 1500f - (cy - 1000f), 1e-3f)
    }

    /** Un panneau plus haut que la carte ne rend pas une part visible negative. */
    @Test fun `un panneau demesure laisse une part visible vide, pas negative`() {
        val ecrasee = VisibleArea(1000f, 2000f, 200f, 3000f)
        assertFalse(ecrasee.contains(500f, 200f, 0f))
    }
}
