package fr.lc4918.trailog.ui.planner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.TrackPoint
import fr.lc4918.trailog.domain.model.WaySegment
import fr.lc4918.trailog.geocode.GeocodePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les VIEWER du calcul d'itineraire sont le panneau du profil d'une trace : sur toute la largeur, le
 * parcours en titre, le choix du VIEWER, et le point courant pose AU-DESSUS du panneau.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class PlannerViewerPanelUiTest {
    @get:Rule val compose = createComposeRule()

    private fun computed(): RoutePlannerState = RoutePlannerState().apply {
        openPlanner()
        setStart(GeocodePlace("Grenoble, Isere", 5.72, 45.18))
        setEnd(GeocodePlace("Vizille, Isere", 5.77, 45.08))
        val points = List(50) { TrackPoint(5.72 + it * 0.001, 45.18, 200.0 + it * 5, null) }
        publish(RouteState.Done(4000.0, 1200.0, TrackMath.compute(points), segments = listOf(
            WaySegment(3000.0, "highway=tertiary surface=asphalt"),
            WaySegment(1000.0, "highway=track surface=gravel"),
        )))
        openViewer(PlannerViewer.PROFILE, null)
    }

    private fun show(state: RoutePlannerState) {
        compose.setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) {
                PlannerViewerPanel(state, imperial = false, settings = SettingsEntity(), lastLabelInsetPx = 0f)
            } }
        }
        compose.waitForIdle()
    }

    @Test fun `le panneau prend toute la largeur et porte le parcours en titre`() {
        show(computed())
        val panel = compose.onNodeWithTag("planner_viewer").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals("toute la largeur, comme le profil d'une trace", root.width, panel.width, 1f)
        assertTrue(compose.onAllNodesWithText("Grenoble - Vizille").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("track_viewer_select").fetchSemanticsNode()
    }

    @Test fun `le point courant se pose au-dessus du panneau`() {
        val state = computed()
        show(state)
        state.tapProfile(1000.0)
        compose.waitForIdle()
        // Comme sur le profil d'une trace : le bloc deborde sur la marge haute du panneau, jamais sur son titre.
        val titleTop = compose.onAllNodesWithText("Grenoble - Vizille").fetchSemanticsNodes().single().boundsInRoot.top
        val duration = compose.onAllNodesWithText("~", substring = true).fetchSemanticsNodes()
        assertTrue("l'horaire du point", duration.isNotEmpty())
        assertTrue("au-dessus du titre du panneau", duration.all { it.boundsInRoot.bottom <= titleTop })
    }
}
