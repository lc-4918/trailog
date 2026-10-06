package fr.lc4918.trailog.ui.watch

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.watch.WatchExportSession
import fr.lc4918.trailog.watch.WatchLink
import fr.lc4918.trailog.watch.WatchTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    private fun show(
        providers: List<ProviderEntity>, session: WatchExportSession.State? = null, sendSucceeds: Boolean = true,
        link: WatchLink = WatchLink.Connected("fenix 6X Pro"),
    ) {
        compose.setContent {
            WatchSendDialog(
                trackName = "Tour de Chamrousse", trackPoints = track, providers = providers,
                initialProviderId = "osm", session = session, link = link, dark = false,
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
        compose.onNodeWithTag("watch_provider_select").performClick()
        compose.onNodeWithTag("watch_provider_ign").performClick()
        compose.onNodeWithText("Scan 25").assertExists()
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

    /** Le select ne montre que le fond retenu tant qu'il est ferme. */
    @Test fun `the basemap select shows only the chosen basemap when closed`() {
        show(listOf(scan25, osm))
        compose.onNodeWithText("OpenStreetMap").assertExists()
        compose.onNodeWithText("Scan 25").assertDoesNotExist()
    }

    /** Le couloir se regle par crans, et l'envoi part avec les tuiles du cran retenu. */
    @Test fun `the corridor slider changes the sent tiles`() {
        show(listOf(osm))
        compose.onNodeWithTag("watch_corridor_slider").performTouchInput { swipeRight() }
        compose.onNodeWithText("2 km de chaque côté").assertExists()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("watch_send_estimate").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("watch_send_confirm").performClick()
        assertEquals(WatchTiles.tilesAlong(track, 2000.0), sentTiles)
    }

    /**
     * Regler le couloir ne doit pas faire sauter la fenetre : la ligne de l'estimation a une hauteur
     * imposee, celle que le rond du calcul occupe aussi.
     */
    @Test fun `the estimate line keeps its height while computing`() {
        var estimate by mutableStateOf<String?>(null)
        // Hors boite de dialogue : sous Robolectric, le contenu d'une boite n'est pas mesure.
        compose.setContent { EstimateLine(estimate) }
        val computingHeight = compose.onNodeWithTag("watch_send_estimate_line").fetchSemanticsNode().size.height
        estimate = "1234 tuiles, 15,4 Mo"
        compose.waitForIdle()
        compose.onNodeWithTag("watch_send_estimate").assertExists()
        val estimateHeight = compose.onNodeWithTag("watch_send_estimate_line").fetchSemanticsNode().size.height
        assertTrue(computingHeight > 0)
        assertEquals(computingHeight, estimateHeight)
    }

    @Test fun `the watch link is shown in the setup`() {
        show(listOf(osm), link = WatchLink.Connected("fenix 6X Pro"))
        compose.onNodeWithText("fenix 6X Pro : connectée").assertExists()
    }

    @Test fun `the watch link is shown during the export`() {
        show(listOf(osm), session = WatchExportSession.State("Tour", 0, 480), link = WatchLink.Disconnected("fenix 6X Pro"))
        compose.onNodeWithText("fenix 6X Pro : non connectée").assertExists()
    }

    @Test fun `without garmin connect the link says so`() {
        show(listOf(osm), link = WatchLink.GarminConnectMissing)
        compose.onNodeWithText("Garmin Connect n'est pas installé").assertExists()
    }

    @Test fun `bluetooth off is said`() {
        show(listOf(osm), link = WatchLink.BluetoothOff)
        compose.onNodeWithText("Le Bluetooth du téléphone est éteint").assertExists()
    }

    /** Sans l'autorisation, le lien "Autoriser" renvoie aux parametres du telephone. */
    @Test fun `the authorize link opens the phone settings`() {
        var opened = 0
        compose.setContent {
            WatchSendDialog(
                trackName = "Tour", trackPoints = track, providers = listOf(osm), initialProviderId = "osm",
                session = null, link = WatchLink.PermissionNeeded, dark = false,
                onSend = { _, _ -> true }, onStop = {}, onDismiss = {}, onOpenPermissionSettings = { opened++ },
            )
        }
        compose.onNodeWithText("Autoriser").performClick()
        assertEquals(1, opened)
    }

    /** Le lien n'apparait que quand l'autorisation manque. */
    @Test fun `the authorize link only shows when the permission is missing`() {
        show(listOf(osm), link = WatchLink.Connected("fenix 6X Pro"))
        compose.onNodeWithTag("watch_link_authorize").assertDoesNotExist()
    }

    @Test fun `radius is written in meters then kilometers`() {
        assertEquals("250 m", formatRadius(250.0))
        assertEquals("2 km", formatRadius(2000.0))
    }
}
