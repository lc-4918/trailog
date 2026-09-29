package fr.lc4918.trailog.ui.routes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les boutons du coin bas-droit sous le profil d'une trace (cf. coveredBy) : entierement ou en partie
 * dessous, ils s'effacent ; au-dessus, ils restent.
 */
class CoveredButtonTest {

    // Le panneau du profil (ou les infos du point) commence a y = 1400.

    @Test fun `un bouton entierement sous le panneau s'efface`() {
        assertTrue(coveredBy(buttonBottomPx = 1700f, coverTopPx = 1400))
    }

    /** A moitie cache, il se touche et se lit mal : il s'efface aussi. */
    @Test fun `un bouton en partie sous le panneau s'efface`() {
        assertTrue(coveredBy(buttonBottomPx = 1420f, coverTopPx = 1400))
    }

    @Test fun `un bouton au-dessus du panneau reste`() {
        assertFalse(coveredBy(buttonBottomPx = 1380f, coverTopPx = 1400))
        assertFalse(coveredBy(buttonBottomPx = 1400f, coverTopPx = 1400))
    }

    /** Sans profil ouvert, rien ne recouvre le coin. */
    @Test fun `sans profil, aucun bouton ne s'efface`() {
        assertFalse(coveredBy(buttonBottomPx = 2200f, coverTopPx = null))
    }
}
