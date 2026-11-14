package fr.lc4918.trailog.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoppedAlertPlacementTest {
    @Test fun `au premier plan, l'annonce passe dans la notification permanente`() {
        assertTrue(StoppedAlertPlacement.mergedIntoOngoing(inForeground = true, serviceStopping = false))
    }

    @Test fun `en mode ecran seul, sans notification permanente, l'alerte reste a part`() {
        assertFalse(StoppedAlertPlacement.mergedIntoOngoing(inForeground = false, serviceStopping = false))
    }

    @Test fun `quand le service s'arrete, l'alerte reste a part pour lui survivre`() {
        assertFalse(StoppedAlertPlacement.mergedIntoOngoing(inForeground = true, serviceStopping = true))
    }
}
