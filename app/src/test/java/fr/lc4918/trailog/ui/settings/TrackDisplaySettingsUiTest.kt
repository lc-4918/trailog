package fr.lc4918.trailog.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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

    // Par morceau : un libelle porteur d'un "i" se termine par l'espace insecable qui l'y lie (cf. TextWithInfo).
    private fun present(texte: String) = compose.onAllNodesWithText(texte, substring = true).fetchSemanticsNodes().isNotEmpty()

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
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(texte, substring = true))
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

    /**
     * L'affichage du profil n'a plus ni coloration par pente - elle suit l'option de la couche - ni
     * classes des pentes ; la ligne du restant a disparu aussi.
     */
    @Test fun `le profil ne regle plus la pente`() {
        ouvrir(expert = false)
        defileVers(app.getString(R.string.settings_profile_grid))
        assertFalse(present("Colorer l'aire par pente"))
        assertFalse(present("Classes des pentes"))
        assertFalse(present("Restant sur la trace"))
    }

    /** Une seule ligne pour la pente de l'itineraire, qui dit dans son "i" qu'elle vaut trace et profil. */
    @Test fun `l'onglet Trajets regle la coloration de l'itineraire d'une seule ligne`() {
        ouvrir(expert = false)
        compose.onNodeWithText(app.getString(R.string.settings_tab_routes)).performClick()
        compose.waitForIdle()
        defileVers(app.getString(R.string.settings_route_slope_line))
        assertTrue(present(app.getString(R.string.settings_section_route_slope).uppercase()))
        assertTrue(present("Colorer suivant la pente"))
        assertFalse("plus de ligne a part pour le profil", present("Trace de l'itinéraire calculé"))
        val info = app.getString(R.string.settings_route_slope_info)
        assertFalse(present(info))
        // Le "i" de la ligne porte l'explication pour description (cf. InfoTip).
        compose.onNode(hasTestTag("info_tip") and hasContentDescription(info)).performClick()
        compose.waitForIdle()
        assertTrue(present(info))
    }

    /** L'onglet Trajets finit par l'altimetrie manquante, puis les points d'interet. */
    @Test fun `les points d'interet ferment l'onglet Trajets, apres l'altimetrie manquante`() {
        ouvrir(expert = true)
        compose.onNodeWithText(app.getString(R.string.settings_tab_routes)).performClick()
        compose.waitForIdle()
        // La position, et non les bornes : celles-ci s'arretent au bord de la liste, et deux rubriques
        // hors de l'ecran y auraient la meme.
        fun haut(res: Int) = compose.onNodeWithText(app.getString(res).uppercase(), useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        val services = haut(R.string.settings_section_services)
        val altimetrie = haut(R.string.settings_section_elevation_fill)
        val poi = haut(R.string.settings_section_poi)
        assertTrue("altimetrie apres les services", altimetrie > services)
        assertTrue("points d'interet en dernier", poi > altimetrie)
    }
}
