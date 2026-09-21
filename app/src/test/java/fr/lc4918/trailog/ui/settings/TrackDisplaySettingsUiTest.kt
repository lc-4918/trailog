package fr.lc4918.trailog.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.ui.routes.TestTrailogApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L'affichage des traces et la coloration par pente dans les reglages : le groupe "Trace" de l'onglet
 * Carte, la rubrique de l'itineraire dans l'onglet Trajets, et le profil qui a perdu sa ligne du restant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr", application = TestTrailogApp::class)
class TrackDisplaySettingsUiTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<TestTrailogApp>()

    private fun present(texte: String) = compose.onAllNodesWithText(texte).fetchSemanticsNodes().isNotEmpty()

    private fun ouvrir(expert: Boolean) {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = expert))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(app.getString(R.string.settings_tab_map)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun defileVers(texte: String) {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(texte))
        compose.waitForIdle()
    }

    @Test fun `le groupe Trace regle la largeur du trait, sans chevrons hors mode expert`() {
        ouvrir(expert = false)
        defileVers(app.getString(R.string.settings_track_line_width))
        assertTrue(present(app.getString(R.string.settings_group_track)))
        assertFalse("reglage expert", present(app.getString(R.string.settings_track_direction)))
    }

    @Test fun `le sens de parcours se montre en mode expert`() {
        ouvrir(expert = true)
        defileVers(app.getString(R.string.settings_track_direction))
        assertTrue(present(app.getString(R.string.settings_track_direction)))
    }

    /** Le profil garde la coloration et gagne les classes ; la ligne du restant a disparu. */
    @Test fun `le profil propose les classes des pentes`() {
        ouvrir(expert = false)
        defileVers(app.getString(R.string.settings_slope_classes))
        assertTrue(present(app.getString(R.string.settings_profile_color_by_slope)))
        assertFalse(present("Restant sur la trace"))
    }

    @Test fun `l'onglet Trajets regle la coloration de l'itineraire`() {
        ouvrir(expert = false)
        compose.onNodeWithText(app.getString(R.string.settings_tab_routes)).performClick()
        compose.waitForIdle()
        defileVers(app.getString(R.string.settings_route_slope_profile))
        assertTrue(present(app.getString(R.string.settings_section_route_slope).uppercase()))
        assertTrue(present(app.getString(R.string.settings_route_slope_line)))
    }
}
