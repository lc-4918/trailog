package com.tunturo.app.ui.settings

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
import com.tunturo.app.R
import com.tunturo.app.data.db.SettingsEntity
import com.tunturo.app.ui.routes.TestTunturoApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La vibration de l'alerte d'eloignement, dans les reglages : une ligne a part de la sonnerie, qui
 * s'allume et s'enregistre d'elle-meme, que le son soit allume ou non.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr", application = TestTunturoApp::class)
class OffTrackVibrationSettingsUiTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<TestTunturoApp>()

    private fun reglages() = runBlocking { app.repository.settings.get() ?: SettingsEntity() }

    @Test fun `la vibration s'allume sans le son, et s'enregistre`() {
        runBlocking {
            app.repository.ensureSeed()
            app.repository.settings.upsert(reglages().copy(offTrackAlertEnabled = true, offTrackAlertSound = false))
        }
        assertFalse(reglages().offTrackAlertVibrate)
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(app.getString(R.string.settings_tab_map)).fetchSemanticsNodes().isNotEmpty()
        }
        val libelle = app.getString(R.string.settings_sw_off_track_vibrate)
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(libelle, substring = true))
        compose.onNodeWithText(libelle, substring = true).performClick()
        compose.waitUntil(5_000) { reglages().offTrackAlertVibrate }
        assertTrue(reglages().offTrackAlertVibrate)
        assertFalse("le son reste eteint", reglages().offTrackAlertSound)
    }
}
