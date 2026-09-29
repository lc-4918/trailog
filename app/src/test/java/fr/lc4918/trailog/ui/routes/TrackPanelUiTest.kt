package fr.lc4918.trailog.ui.routes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.LayerWays
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.WaySegment
import fr.lc4918.trailog.ui.planner.PlannerViewer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le panneau du profil d'une trace touchee sur la carte, avec ses VIEWER des surfaces et des types de voies.
 *
 * Le panneau ne connait pas le ViewModel : il recoit ce qu'il montre et rend les gestes. L'etat est tenu ici
 * comme le ViewModel le tiendrait (cf. TrackPanelView).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class TrackPanelUiTest {

    @get:Rule val compose = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val profil = List(11) { i ->
        Sample(x = i * 100.0, z = 200.0 + i * 10, slope = 0.1, t = null, lon = 2.0 + i * 0.001, lat = 43.0)
    }.let { ComputedTrack(it, TrackMath.statsOf(it), hasZ = true, hasTime = false) }

    private val voies = LayerWays(listOf(listOf(
        WaySegment(750.0, "highway=track surface=gravel"),
        WaySegment(250.0, "highway=tertiary surface=asphalt"),
    )))

    private var vue by mutableStateOf(TrackPanelView.Initial)
    private var chargement by mutableStateOf<WaysLoad?>(null)
    private val demandes = mutableListOf<PlannerViewer>()
    private var relances = 0

    private fun affiche() = compose.setContent {
        MaterialTheme {
            Box(Modifier.fillMaxSize()) {
                TrackProfileLayer(
                    activeLayerId = 1, computed = profil, lastComputed = profil, loading = false, zoom = null,
                    cursor = 450.0, title = "Soreze - Arfons", lineColor = Color.Blue,
                    settings = SettingsEntity(), imperial = false, onHeightChange = {}, onExpandZoom = {},
                    slopeColored = false, onScrub = {}, onZoom = { _, _ -> }, onDoubleTapZoom = {},
                    panel = vue, ways = chargement, trackIndex = 0,
                    onViewer = { v ->
                        demandes += v
                        vue = vue.showing(v, (chargement as? WaysLoad.Done)?.ways?.let { lineWays(it, 0) }.orEmpty())
                    },
                    onSelect = { vue = vue.selecting(it) },
                    onRetryWays = { relances++ },
                )
            }
        }
    }

    private fun existe(texte: String) =
        compose.onAllNodesWithText(texte, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun `le choix du viewer est a droite du titre et propose les trois`() {
        affiche()
        compose.onNodeWithText("Soreze - Arfons").assertExists()
        val titre = compose.onNodeWithText("Soreze - Arfons").fetchSemanticsNode().boundsInRoot
        val choix = compose.onNodeWithTag("track_viewer_select").fetchSemanticsNode().boundsInRoot
        assertEquals(true, choix.left >= titre.right)
        compose.onNodeWithTag("track_viewer_select").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.planner_details_surfaces)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.planner_details_ways)).assertExists()
    }

    /** Hors du profil : ni graphique, ni infos du point ; les voies a la place. Elles reviennent au retour. */
    @Test fun `les surfaces remplacent le profil et retirent les infos du point`() {
        chargement = WaysLoad.Done(voies)
        affiche()
        val pente = ctx.getString(R.string.chip_slope)
        assertEquals(true, existe(pente))
        compose.onNodeWithTag("track_viewer_select").performClick()
        compose.onNodeWithText(ctx.getString(R.string.planner_details_surfaces)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(PlannerViewer.SURFACES), demandes)
        assertEquals(false, existe(pente))
        compose.onNodeWithTag("planner_viewer_bar").assertExists()
        compose.onNodeWithText(ctx.getString(R.string.surface_gravel)).assertExists()
        // Retour sur le profil : les infos du point reviennent.
        compose.onNodeWithTag("track_viewer_select").performClick()
        compose.onNodeWithText(ctx.getString(R.string.planner_viewer_elevation)).performClick()
        compose.waitForIdle()
        assertEquals(true, existe(pente))
    }

    @Test fun `pendant la recherche des voies, le panneau le dit`() {
        vue = TrackPanelView(PlannerViewer.WAYS)
        chargement = WaysLoad.Analyzing
        affiche()
        compose.onNodeWithText(ctx.getString(R.string.stats_ways_analyzing)).assertExists()
    }

    @Test fun `un service injoignable se redemande depuis le panneau`() {
        vue = TrackPanelView(PlannerViewer.SURFACES)
        chargement = WaysLoad.Unreachable
        affiche()
        compose.onNodeWithText(ctx.getString(R.string.stats_ways_unreachable)).assertExists()
        compose.onNodeWithTag("track_panel_retry").performClick()
        assertEquals(1, relances)
    }
}
