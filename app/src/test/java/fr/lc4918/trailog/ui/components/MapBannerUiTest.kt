package fr.lc4918.trailog.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.ui.routes.SlopeLegendInfo
import fr.lc4918.trailog.ui.theme.TrailogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les bandeaux poses sur la carte : ils se ferment en les touchant, sans croix, et prennent leurs couleurs
 * au theme - assombries en sombre. Et le "i" de la legende des pentes, qui montre la palette.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class MapBannerUiTest {

    @get:Rule val compose = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `une consigne se ferme en la touchant, et n'a pas de croix`() {
        var fermee = false
        compose.setContent { TrailogTheme("light") { MapPromptBar("Choisir un point", onClose = { fermee = true }) } }
        assertEquals(0, compose.onAllNodesWithContentDescription(ctx.getString(R.string.action_close))
            .fetchSemanticsNodes().size)
        compose.onNodeWithText("Choisir un point").performClick()
        assertTrue(fermee)
    }

    @Test fun `en sombre, l'alerte et l'avertissement prennent leurs conteneurs, en clair leurs couleurs vives`() {
        var clair: Pair<Color, Color>? = null
        var sombre: Pair<Color, Color>? = null
        var roles: List<Color> = emptyList()
        compose.setContent {
            TrailogTheme("light") { clair = MapBannerTone.ALERT.colors() }
            TrailogTheme("dark") {
                sombre = MapBannerTone.ALERT.colors()
                val c = MaterialTheme.colorScheme
                roles = listOf(c.errorContainer, c.onErrorContainer)
            }
        }
        compose.waitForIdle()
        assertEquals(roles, sombre!!.toList())
        assertTrue("le clair reste vif", clair!!.first != sombre!!.first)
    }

    @Test fun `le i de la legende des pentes montre la palette`() {
        compose.setContent { TrailogTheme("light") { SlopeLegendInfo(SettingsEntity()) } }
        val titre = ctx.getString(R.string.settings_profile_slope_legend)
        compose.onNodeWithTag("slope_legend_info").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(titre).assertIsDisplayed()
    }
}
