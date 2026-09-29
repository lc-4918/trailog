package fr.lc4918.trailog.ui.routes

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextStyle
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Le nom d'une ligne du menu (infobulle, appui long) et l'en-tete de la selection multiple. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class DrawerSelectionUiTest {

    @get:Rule val compose = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val long = "EV1 Nantes - Hendaye par la cote atlantique, version longue"

    private fun bulles() = compose.onAllNodesWithTag("name_tooltip").fetchSemanticsNodes().size

    /** Comme le menu : un porteur des infobulles, et l'observateur des appuis qui les ferme. */
    private fun menu(content: @Composable () -> Unit) = compose.setContent {
        val porteur = remember { TooltipHolder() }
        MaterialTheme {
            CompositionLocalProvider(LocalTooltipHolder provides porteur) {
                Column(Modifier.closesTooltips(porteur)) { content() }
            }
        }
    }

    @Test fun `un appui sur le nom montre le nom entier, un second le masque`() {
        menu { RowLabel(long, TextStyle.Default, Color.Black, null, onLongPress = {}) }
        assertEquals(0, bulles())
        compose.onNodeWithTag("row_label").performClick()
        compose.waitForIdle()
        assertEquals(1, bulles())
        compose.onNodeWithTag("name_tooltip").assertExists()
        // Le second appui tombe hors de la bulle : il la ferme.
        compose.onNodeWithTag("row_label").performClick()
        compose.waitForIdle()
        assertEquals(0, bulles())
    }

    /** Un appui sur le nom d'un AUTRE element ferme la bulle ouverte, sans en ouvrir une seconde. */
    @Test fun `un appui sur un autre nom ferme la bulle sans en ouvrir d'autre`() {
        menu {
            RowLabel("Premier", TextStyle.Default, Color.Black, null, onLongPress = {})
            RowLabel("Second", TextStyle.Default, Color.Black, null, onLongPress = {})
        }
        compose.onAllNodesWithTag("row_label")[0].performClick()
        compose.waitForIdle()
        assertEquals(1, bulles())
        compose.onAllNodesWithTag("row_label")[1].performClick()
        compose.waitForIdle()
        assertEquals(0, bulles())
    }

    @Test fun `l'appui long ouvre la selection et ne montre pas de bulle`() {
        var longs = 0
        menu { RowLabel(long, TextStyle.Default, Color.Black, null, onLongPress = { longs++ }) }
        compose.onNodeWithTag("row_label").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(1, longs)
        assertEquals(0, bulles())
    }

    // ---------- En-tete ----------

    @Test fun `l'en-tete de la selection propose tout cocher, annuler et supprimer`() {
        var tout = 0
        var annule = 0
        compose.setContent {
            MaterialTheme {
                SelectionHeader(ToggleableState.Indeterminate, count = 2, onToggleAll = { tout++ },
                    onCancel = { annule++ }, onDelete = {})
            }
        }
        compose.onNodeWithText(ctx.getString(R.string.action_cancel)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.action_delete)).assertExists()
        compose.onNodeWithTag("selection_all").performClick()
        compose.onNodeWithTag("selection_cancel").performClick()
        assertEquals(1, tout)
        assertEquals(1, annule)
        compose.onNodeWithTag("selection_delete").assertIsEnabled()
    }

    /** Rien de coche, rien a supprimer : le bouton est grise. */
    @Test fun `supprimer est grise tant que rien n'est coche`() {
        compose.setContent {
            MaterialTheme { SelectionHeader(ToggleableState.Off, count = 0, onToggleAll = {}, onCancel = {}, onDelete = {}) }
        }
        compose.onNodeWithTag("selection_delete").assertIsNotEnabled()
    }

    /** Un appui qui n'a rien ferme ne doit pas avaler le clic suivant sur un nom. */
    @Test fun `apres une bulle fermee ailleurs, un nom s'ouvre au premier appui`() {
        menu {
            RowLabel("Premier", TextStyle.Default, Color.Black, null, onLongPress = {})
            RowLabel("Second", TextStyle.Default, Color.Black, null, onLongPress = {})
        }
        compose.onAllNodesWithTag("row_label")[0].performClick()
        compose.onAllNodesWithTag("row_label")[1].performClick()   // ferme
        compose.waitForIdle()
        assertEquals(0, bulles())
        compose.onAllNodesWithTag("row_label")[1].performClick()   // ouvre, du premier coup
        compose.waitForIdle()
        assertEquals(1, bulles())
    }

    /** L'en-tete ne change pas de hauteur quand la selection s'ouvre : 40 dp, comme Importer / Dossier. */
    @Test fun `l'en-tete de selection a la hauteur de la ligne qu'il remplace`() {
        compose.setContent {
            MaterialTheme { SelectionHeader(ToggleableState.Off, count = 0, onToggleAll = {}, onCancel = {}, onDelete = {}) }
        }
        val h = compose.onNodeWithTag("selection_header").fetchSemanticsNode().size.height
        val attendu = with(compose.density) { HeaderActionsHeight.roundToPx() }
        assertEquals(attendu, h)
    }
}
