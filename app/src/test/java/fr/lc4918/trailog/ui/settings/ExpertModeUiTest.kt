package fr.lc4918.trailog.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.ui.routes.TestTrailogApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le mode expert des reglages : ses options restent cachees, sept appuis sur l'avatar les montrent - une
 * alerte le dit -, et sept autres les cachent de nouveau. Le reglage est enregistre en base.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr", application = TestTrailogApp::class)
class ExpertModeUiTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<TestTrailogApp>()

    private fun present(res: Int) =
        compose.onAllNodesWithText(app.getString(res)).fetchSemanticsNodes().isNotEmpty()

    private fun septAppuis() = repeat(ExpertTaps.TAPS) { compose.onNodeWithTag("settings_avatar").performClick() }

    @Test fun `sept appuis sur l'avatar montrent puis cachent les options expertes`() {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = false))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        val expert = R.string.settings_sw_gps_last_fix
        compose.waitUntil(5_000) { present(R.string.settings_sw_gps_recenter) }
        assertTrue("cachee hors mode expert", !present(expert))

        septAppuis()
        compose.waitUntil(5_000) { present(expert) }
        assertTrue("l'alerte le dit", present(R.string.settings_expert_on))
        assertTrue("enregistre en base", runBlocking { app.repository.settings.get()!!.expertMode })

        septAppuis()
        compose.waitUntil(5_000) { !present(expert) }
        assertTrue(present(R.string.settings_expert_off))
    }

    /**
     * Le titre du groupe courant se pose sous les onglets des que son titre est passe en haut, et se
     * retire quand on remonte. Sans lui, on regle des curseurs sans savoir a quoi ils se rapportent.
     */
    @Test fun `le groupe courant reste affiche en defilant`() {
        runBlocking { app.repository.ensureSeed() }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        compose.waitUntil(5_000) { present(R.string.settings_section_position) }
        compose.onAllNodesWithTag("settings_group_bar").assertCountEquals(0)

        // On descend jusqu'a un reglage du groupe GPS : son titre est alors passe au-dessus du bord.
        compose.onNodeWithText(app.getString(R.string.settings_section_gps_marker).uppercase()).performScrollTo()
        compose.waitForIdle()
        compose.onAllNodesWithTag("settings_group_bar").assertCountEquals(1)
    }
}
