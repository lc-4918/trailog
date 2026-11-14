package com.tunturo.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.tunturo.app.R
import com.tunturo.app.ui.routes.TestTunturoApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La notification d'une zone a mettre a jour ouvre les reglages sur l'onglet des itineraires, ou sont les zones. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr", application = TestTunturoApp::class)
class SettingsRequestedTabUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<TestTunturoApp>()

    private fun zonesSectionShown() = compose.onAllNodesWithText(
        app.getString(R.string.settings_section_offline_routing), substring = true, ignoreCase = true,
    ).fetchSemanticsNodes().isNotEmpty()

    @Test fun `l'onglet demande s'ouvre, une seule fois`() {
        runBlocking { app.repository.ensureSeed() }
        var shown = 0
        compose.setContent {
            MaterialTheme {
                SettingsScreen(onBack = {}, requestedTab = SETTINGS_TAB_ROUTES, onRequestedTabShown = { shown++ })
            }
        }
        compose.waitUntil(5_000) { zonesSectionShown() }
        assertEquals(1, shown)
    }
}
