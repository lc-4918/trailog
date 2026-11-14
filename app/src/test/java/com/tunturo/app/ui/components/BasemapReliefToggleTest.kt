package com.tunturo.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import com.tunturo.app.R
import com.tunturo.app.data.db.ProviderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Le relief sort de la liste des fonds et passe dans l'en-tete du gestionnaire, en icone a deux etats. */
@RunWith(RobolectricTestRunner::class)
class BasemapReliefToggleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val osm = ProviderEntity("osm", "OpenStreetMap", "Monde", "XYZ", "https://tile/{z}/{x}/{y}.png")
    private val relief = ProviderEntity("dem", "Relief (DEM)", "Relief", "DEM", "https://dem/{z}/{x}/{y}.png")

    private fun reliefLabel() = compose.activity.getString(R.string.content_desc_relief_toggle)

    private fun panel(providers: List<ProviderEntity>, reliefOn: () -> Boolean, onToggle: () -> Unit) {
        compose.setContent {
            MaterialTheme {
                BasemapControlPanel(
                    folders = emptyList(), providers = providers, composites = emptyList(),
                    currentBasemapId = "osm", widthFraction = 1f, backgroundAlpha = 1f,
                    onSelect = {}, onCreateFolder = { _, _ -> }, onReorderDrop = { _, _, _, _, _ -> },
                    reliefOn = reliefOn(), onToggleRelief = onToggle, onClose = {},
                )
            }
        }
    }

    @Test fun `le relief n'est plus une ligne de la liste des fonds`() {
        assertEquals(listOf<Any>(osm), combinedBasemapChildren(null, emptyList(), listOf(osm, relief), emptyList()))
    }

    @Test fun `le bouton du relief ne parait que si un fond de relief est coche`() {
        assertTrue(reliefAvailable(listOf(osm, relief)))
        assertFalse(reliefAvailable(listOf(osm, relief.copy(enabled = false))))
        assertFalse(reliefAvailable(listOf(osm)))
    }

    @Test fun `l'icone de l'en-tete bascule le relief et dit son etat`() {
        var on by mutableStateOf(false)
        panel(listOf(osm, relief), { on }) { on = !on }
        assertTrue("pas de ligne de relief", compose.onAllNodesWithText("Relief (DEM)").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription(reliefLabel()).assertIsOff().performClick()
        compose.waitForIdle()
        assertTrue(on)
        compose.onNodeWithContentDescription(reliefLabel()).assertIsOn()
    }

    @Test fun `sans fond de relief coche, l'en-tete n'a pas d'icone de relief`() {
        panel(listOf(osm, relief.copy(enabled = false)), { false }) {}
        assertTrue(compose.onAllNodesWithContentDescription(reliefLabel()).fetchSemanticsNodes().isEmpty())
    }
}
