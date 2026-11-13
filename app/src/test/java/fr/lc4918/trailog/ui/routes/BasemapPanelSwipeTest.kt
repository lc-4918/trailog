package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.data.db.BasemapControlMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasemapPanelSwipeTest {
    @Test fun `un glissement vers la gauche au-dela du seuil ouvre le panneau`() {
        assertTrue(BasemapPanelSwipe.edgeSwipeOpens(-41f))
        assertFalse("trop court", BasemapPanelSwipe.edgeSwipeOpens(-40f))
        assertFalse("vers la droite", BasemapPanelSwipe.edgeSwipeOpens(200f))
    }

    @Test fun `le panneau suit le doigt sans passer a gauche de sa place ni au-dela de sa largeur`() {
        assertEquals(30f, BasemapPanelSwipe.dragOffset(10f, 20f, 500f), 0f)
        assertEquals(0f, BasemapPanelSwipe.dragOffset(10f, -50f, 500f), 0f)
        assertEquals(500f, BasemapPanelSwipe.dragOffset(490f, 50f, 500f), 0f)
    }

    @Test fun `lache au-dela du tiers de sa largeur, le panneau se ferme`() {
        assertTrue(BasemapPanelSwipe.closesOnRelease(160f, 500f, 0f))
        assertFalse("revient en place", BasemapPanelSwipe.closesOnRelease(140f, 500f, 0f))
    }

    @Test fun `un geste vif vers la droite ferme, un geste vif vers la gauche retient`() {
        assertTrue(BasemapPanelSwipe.closesOnRelease(20f, 500f, 1500f))
        assertFalse(BasemapPanelSwipe.closesOnRelease(300f, 500f, -1500f))
    }

    @Test fun `un panneau pas encore mesure ne se ferme que sur un geste vif`() {
        assertFalse(BasemapPanelSwipe.closesOnRelease(100f, 0f, 0f))
        assertTrue(BasemapPanelSwipe.closesOnRelease(0f, 0f, 1500f))
    }

    @Test fun `chaque mode donne son bouton et son glissement`() {
        assertTrue(BasemapControlMode.buttonVisible(BasemapControlMode.BUTTON))
        assertFalse(BasemapControlMode.swipeEnabled(BasemapControlMode.BUTTON))
        assertFalse(BasemapControlMode.buttonVisible(BasemapControlMode.SWIPE))
        assertTrue(BasemapControlMode.swipeEnabled(BasemapControlMode.SWIPE))
        assertTrue(BasemapControlMode.buttonVisible(BasemapControlMode.BOTH))
        assertTrue(BasemapControlMode.swipeEnabled(BasemapControlMode.BOTH))
    }
}
