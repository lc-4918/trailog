package fr.lc4918.trailog.map.offline

import fr.lc4918.trailog.ui.offline.OfflineDownloadRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadStateTest {
    @Test fun `les phases couvrent le cycle complet d'un telechargement`() {
        assertTrue(OfflinePhase.entries.isNotEmpty())
        assertEquals(OfflinePhase.entries.size, OfflinePhase.entries.map { it.name }.toSet().size)
    }

    @Test fun `une demande porte son emprise et sa plage de zoom`() {
        val r = OfflineDownloadRequest(Bbox(2.0, 43.0, 2.5, 43.5), minZoom = 10, maxZoom = 14,
            name = "Ma rando", continueOnError = true)
        assertEquals(10, r.minZoom)
        assertEquals(14, r.maxZoom)
        assertTrue(r.continueOnError)
        assertEquals("Ma rando", r.name)
    }
}
