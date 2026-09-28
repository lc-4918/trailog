package fr.lc4918.trailog.ui.offline

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.map.offline.OfflineDownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La progression d'un telechargement hors ligne : ses trois comptes en tuiles, et ses deux issues -
 * "Annuler", en rouge, puis "Reduire" a sa droite. Et, reduite, le bouton a anneau de la carte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class OfflineDownloadProgressUiTest {

    @get:Rule val compose = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val encours = OfflineDownloadState(name = "Vercors", total = 200, done = 100, failed = 2)

    @Test fun `les trois comptes s'affichent en tuiles`() {
        compose.setContent { OfflineDownloadCard(encours, onMinimize = {}, onCancel = {}, onClose = {}) }
        compose.onNodeWithText(ctx.getString(R.string.offline_stat_total).uppercase()).assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(R.string.offline_stat_received).uppercase()).assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(R.string.offline_stat_failed).uppercase()).assertIsDisplayed()
        compose.onNodeWithText("200").assertIsDisplayed()
        compose.onNodeWithText("51 %").assertIsDisplayed()
    }

    @Test fun `reduire est a droite d'annuler, et chacun fait ce qu'il dit`() {
        var reduit = 0
        var annule = 0
        compose.setContent {
            OfflineDownloadCard(encours, onMinimize = { reduit++ }, onCancel = { annule++ }, onClose = {})
        }
        val annuler = compose.onNode(hasText(ctx.getString(R.string.action_cancel)) and hasClickAction())
        val reduire = compose.onNode(hasText(ctx.getString(R.string.offline_action_minimize)) and hasClickAction())
        assertTrue(
            "Reduire a droite d'Annuler",
            reduire.fetchSemanticsNode().boundsInRoot.left > annuler.fetchSemanticsNode().boundsInRoot.left,
        )
        annuler.performClick()
        reduire.performClick()
        assertEquals(1, annule)
        assertEquals(1, reduit)
    }

    @Test fun `reduite, la progression est un bouton de carte qui la rouvre`() {
        var rouvert = false
        compose.setContent {
            OfflineMinimizedButton(encours.copy(minimized = true), fg = Color.Black, onClick = { rouvert = true })
        }
        compose.onNodeWithText("51").assertIsDisplayed()
        compose.onNodeWithContentDescription(ctx.getString(R.string.offline_action_reopen)).performClick()
        assertTrue(rouvert)
    }
}
