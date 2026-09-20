package fr.lc4918.trailog.ui.alert

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.geo.Format
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L'alerte d'eloignement, cote ecran : la banniere.
 *
 * Ce que la mesure decide est teste ailleurs, sans Android (cf. `TrackWatchTest`). Ce qui se verifie ici
 * est l'autre moitie, celle qu'aucun test de domaine n'atteint : que la distance et le nom de la trace
 * arrivent bien sous les yeux, et que la banniere entiere repond - pas seulement sa croix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class OffTrackAlertUiTest {

    @get:Rule val compose = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    // ---------- La banniere ----------

    @Test fun `la banniere dit l'ecart et la trace`() {
        compose.setContent { OffTrackAlertBar("GR 9", awayM = 120.0, imperial = false, onClose = {}) }
        compose.onNodeWithText(ctx.getString(R.string.alert_off_track_banner, "120 m", "GR 9"))
            .assertIsDisplayed()
    }

    /** Les unites imperiales valent ici comme partout : une alerte en metres a qui a regle des miles
     *  serait la seule mesure de l'application a ne pas suivre le reglage. */
    @Test fun `la banniere suit les unites reglees`() {
        compose.setContent { OffTrackAlertBar("GR 9", awayM = 120.0, imperial = true, onClose = {}) }
        val attendu = ctx.getString(
            R.string.alert_off_track_banner, Format.shortDistance(120.0, imperial = true), "GR 9",
        )
        compose.onNodeWithText(attendu).assertIsDisplayed()
        compose.onNodeWithText(
            ctx.getString(R.string.alert_off_track_banner, "120 m", "GR 9"),
        ).assertDoesNotExist()
    }

    /**
     * **Toute la banniere repond, pas seulement sa croix.** La sonnerie boucle jusqu'a ce qu'on reponde
     * (cf. AlertSound), et viser une croix de 20 dp en marchant pour faire taire un telephone qui sonne est
     * le geste qu'il ne faut pas demander la : le tap tombe a cote, et le telephone sonne toujours.
     */
    @Test fun `un tap n'importe ou sur la banniere repond`() {
        var tue = false
        compose.setContent { OffTrackAlertBar("GR 9", 120.0, false, onClose = { tue = true }) }
        compose.onNodeWithText(ctx.getString(R.string.alert_off_track_banner, "120 m", "GR 9")).performClick()
        assertTrue(tue)
    }

    @Test fun `la croix de la banniere repond`() {
        var tue = false
        compose.setContent { OffTrackAlertBar("GR 9", 120.0, false, onClose = { tue = true }) }
        compose.onNode(androidx.compose.ui.test.hasContentDescription(ctx.getString(R.string.action_close)))
            .performClick()
        assertTrue(tue)
    }
}
