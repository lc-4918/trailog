package fr.lc4918.trailog.ui.watch

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.watch.WatchExportSession
import fr.lc4918.trailog.watch.WatchTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class WatchSendDialogTest {

    @get:Rule val compose = createComposeRule()

    private val scan25 = ProviderEntity(id = "ign", name = "Scan 25", groupName = "Pays", type = "WMTS", urlTemplate = "x")
    private val osm = ProviderEntity(id = "osm", name = "OpenStreetMap", groupName = "Monde", type = "XYZ", urlTemplate = "x")
    private val track = listOf(5.85 to 45.10, 5.90 to 45.13)

    private var sentProvider: ProviderEntity? = null
    private var sentTiles: List<WatchTiles.WatchTile>? = null
    private var stopCount = 0

    private fun show(providers: List<ProviderEntity>, session: WatchExportSession.State? = null, sendSucceeds: Boolean = true) {
        compose.setContent {
            WatchSendDialog(
                trackName = "Tour de Chamrousse", trackPoints = track, providers = providers,
                initialProviderId = "osm", session = session,
                onSend = { provider, tiles -> sentProvider = provider; sentTiles = tiles; sendSucceeds },
                onStop = { stopCount++ }, onDismiss = {},
            )
        }
        compose.waitForIdle()
    }

    @Test fun `sending uses the displayed basemap and the corridor tiles`() {
        show(listOf(scan25, osm))
        val expectedTiles = WatchTiles.tilesAlong(track, 500.0)
        compose.onNodeWithText("${expectedTiles.size} tuiles", substring = true).assertExists()
        compose.onNodeWithTag("watch_send_confirm").performClick()
        assertEquals("osm", sentProvider?.id)
        assertEquals(expectedTiles, sentTiles)
    }

    @Test fun `another basemap can be chosen`() {
        show(listOf(scan25, osm))
        compose.onNodeWithTag("watch_provider_ign").performClick()
        compose.onNodeWithTag("watch_send_confirm").performClick()
        assertEquals("ign", sentProvider?.id)
    }

    @Test fun `without a sendable basemap nothing can be sent`() {
        show(emptyList())
        compose.onNodeWithText("Aucun fond ne peut être envoyé", substring = true).assertExists()
        compose.onNodeWithTag("watch_send_confirm").assertIsNotEnabled()
        assertNull(sentProvider)
    }

    @Test fun `a failed start is reported`() {
        show(listOf(osm), sendSucceeds = false)
        compose.onNodeWithTag("watch_send_confirm").assertIsEnabled().performClick()
        compose.onNodeWithText("L'envoi n'a pas pu s'ouvrir", substring = true).assertExists()
    }

    @Test fun `an open export shows its progress and can be stopped`() {
        show(listOf(osm), session = WatchExportSession.State("Tour de Chamrousse", 120, 480))
        compose.onNodeWithText("120 / 480 tuiles reçues par la montre").assertExists()
        compose.onNodeWithTag("watch_send_stop").performClick()
        assertEquals(1, stopCount)
    }

    @Test fun `an export not yet reached by the watch says it is waiting`() {
        show(listOf(osm), session = WatchExportSession.State("Tour", 0, 480))
        compose.onNodeWithText("En attente de la montre").assertExists()
    }

    @Test fun `radius is written in meters then kilometers`() {
        assertEquals("250 m", formatRadius(250.0))
        assertEquals("2 km", formatRadius(2000.0))
    }
}
