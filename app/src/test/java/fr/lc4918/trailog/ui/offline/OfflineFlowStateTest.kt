package fr.lc4918.trailog.ui.offline

import fr.lc4918.trailog.location.TrackWatch
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'ouverture du cadrage d'une zone a telecharger : le cadre se regle sur une carte degagee, sans le
 * profil ni le tableau de bord qui en recouvraient le bas.
 */
class OfflineFlowStateTest {

    @After fun raz() = TrackWatch.setDashboard(false)

    @Test fun `ouvrir le cadrage referme le profil et le tableau de bord`() {
        TrackWatch.setDashboard(true)
        var profilFerme = false
        val flow = OfflineFlowState()
        flow.startDrawing { profilFerme = true }
        assertTrue(flow.drawingActive)
        assertTrue(profilFerme)
        assertFalse(TrackWatch.dashboard.value)
    }
}
