package fr.lc4918.trailog.geocode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qui repond d'abord, de Photon ou de l'index des lieux. */
class PlaceSearchTest {

    @Test fun `sans reseau et avec un index, l'index repond d'abord`() {
        assertTrue(PlaceSearch.offlineFirst(serviceReachable = false, indexAvailable = true))
    }

    /** Sans index, rien ne remplace Photon : on l'interroge, et son echec s'affiche comme avant. */
    @Test fun `sans index, Photon reste interroge`() {
        assertFalse(PlaceSearch.offlineFirst(serviceReachable = false, indexAvailable = false))
    }

    @Test fun `avec le reseau, Photon repond d'abord`() {
        assertFalse(PlaceSearch.offlineFirst(serviceReachable = true, indexAvailable = true))
        assertFalse(PlaceSearch.offlineFirst(serviceReachable = true, indexAvailable = false))
    }
}
