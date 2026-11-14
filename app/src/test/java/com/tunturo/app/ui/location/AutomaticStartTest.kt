package com.tunturo.app.ui.location

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le demarrage automatique du suivi, sans bouton de localisation : il suit la localisation du telephone,
 * et ne demande la permission qu'une fois.
 */
class AutomaticStartTest {

    @Test fun `localisation du telephone eteinte, rien ne demarre`() {
        assertEquals(AutomaticStart.NONE, automaticStart(sensorEnabled = false, trackingActive = false,
            permissionGranted = true, permissionAlreadyAsked = false))
    }

    @Test fun `suivi deja en cours, rien ne redemarre`() {
        assertEquals(AutomaticStart.NONE, automaticStart(sensorEnabled = true, trackingActive = true,
            permissionGranted = true, permissionAlreadyAsked = false))
    }

    @Test fun `localisation allumee et permission accordee, le suivi demarre`() {
        assertEquals(AutomaticStart.START, automaticStart(sensorEnabled = true, trackingActive = false,
            permissionGranted = true, permissionAlreadyAsked = false))
    }

    @Test fun `permission manquante, elle est demandee une premiere fois`() {
        assertEquals(AutomaticStart.ASK_PERMISSION, automaticStart(sensorEnabled = true, trackingActive = false,
            permissionGranted = false, permissionAlreadyAsked = false))
    }

    @Test fun `permission deja demandee et refusee, elle n'est pas redemandee`() {
        assertEquals(AutomaticStart.NONE, automaticStart(sensorEnabled = true, trackingActive = false,
            permissionGranted = false, permissionAlreadyAsked = true))
    }
}
