package fr.lc4918.trailog.ui.routes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.LayerEntity
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.model.LayerWays
import fr.lc4918.trailog.domain.model.SurfaceKind
import fr.lc4918.trailog.domain.model.WayKind
import fr.lc4918.trailog.domain.model.WaySegment
import fr.lc4918.trailog.routing.TraceMatch
import fr.lc4918.trailog.ui.planner.PlannerViewer
import fr.lc4918.trailog.ui.planner.WaysViewerPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les voies d'une trace de la bibliotheque, a l'ecran : la fenetre des statistiques et le panneau VIEWER.
 *
 * Aucun service n'est interroge : le recalage est remplace par ce qu'il rendrait (cf. TraceMatchTest pour
 * le recalage lui-meme).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class LayerWaysUiTest {

    @get:Rule val compose = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val couche = LayerEntity(id = 3, name = "Soreze - Arfons", folderId = null, geometryFile = "x.geojson",
        distance = 1000.0, hasLine = true, ascent = 3145.0, descent = 3150.0, movingTime = 97_200.0, hasTime = true)

    private val voies = LayerWays(listOf(listOf(
        WaySegment(750.0, "highway=track surface=gravel"),
        WaySegment(250.0, "highway=tertiary surface=asphalt"),
    )))

    private val profil = List(11) { i ->
        Sample(x = i * 100.0, z = 200.0 + i * 10, slope = 0.1, t = null, lon = 2.0 + i * 0.001, lat = 43.0)
    }.let { ComputedTrack(it, TrackMath.statsOf(it), hasZ = true, hasTime = false) }

    private fun fenetre(
        gardees: LayerWays?,
        analyse: suspend (LayerEntity) -> TraceMatch.LayerOutcome = { TraceMatch.LayerOutcome.Done(voies) },
        ouvre: (LayerWays?, PlannerViewer, Any?) -> Unit = { _, _, _ -> },
        profils: List<ComputedTrack> = listOf(profil),
    ) = compose.setContent {
        MaterialTheme {
            LayerStatsDialog(
                layer = couche, imperial = false, dark = false,
                loadStart = { 1_501_142_400_000L },
                loadWays = { gardees },
                analyzeWays = analyse,
                loadProfiles = { profils },
                settings = SettingsEntity(),
                onOpenViewer = ouvre,
                onDismiss = {},
            )
        }
    }

    @Test fun `les voies gardees s'affichent sans nouveau recalage`() {
        var analyses = 0
        fenetre(voies, analyse = { analyses++; TraceMatch.LayerOutcome.Done(voies) })
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.planner_details_surfaces)).performScrollTo().assertExists()
        compose.onNodeWithText(ctx.getString(R.string.planner_details_ways)).performScrollTo().assertExists()
        compose.onNodeWithText(ctx.getString(R.string.surface_gravel)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.way_track)).assertExists()
        assertEquals(0, analyses)
    }

    /** Les chiffres d'avant restent en tete : les voies viennent dessous. */
    @Test fun `les chiffres de la trace restent en tete`() {
        fenetre(voies)
        compose.waitForIdle()
        // Positions non rognees : la rubrique peut se trouver sous le bord de la fenetre, qui defile.
        val distance = compose.onNodeWithText(ctx.getString(R.string.chip_distance)).fetchSemanticsNode()
        val surfaces = compose.onNodeWithTag("planner_details_surfaces").fetchSemanticsNode()
        assertTrue(distance.positionInRoot.y < surfaces.positionInRoot.y)
    }

    @Test fun `sans voies gardees, la trace est recalee`() {
        var analyses = 0
        fenetre(null, analyse = { analyses++; TraceMatch.LayerOutcome.Done(voies) })
        compose.waitForIdle()
        assertEquals(1, analyses)
        compose.onNodeWithTag("planner_details_surfaces").assertExists()
    }

    @Test fun `un service injoignable le dit, et se redemande`() {
        var analyses = 0
        fenetre(null, analyse = {
            analyses++
            if (analyses == 1) TraceMatch.LayerOutcome.Unreachable else TraceMatch.LayerOutcome.Done(voies)
        })
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.stats_ways_unreachable)).assertExists()
        compose.onNodeWithTag("layer_ways_retry").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(2, analyses)
        compose.onNodeWithTag("planner_details_surfaces").assertExists()
    }

    @Test fun `une trace hors de tout reseau le dit`() {
        fenetre(null, analyse = { TraceMatch.LayerOutcome.NoMatch })
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.stats_ways_no_match)).assertExists()
    }

    /** Surfaces et types de voies ouvrent chacun leur VIEWER, la plus longue categorie en evidence. */
    @Test fun `une rubrique ouvre son viewer`() {
        val ouverts = mutableListOf<Pair<PlannerViewer, Any?>>()
        fenetre(voies, ouvre = { _, v, k -> ouverts += v to k })
        compose.waitForIdle()
        compose.onNodeWithTag("planner_details_surfaces").performScrollTo().performClick()
        compose.onNodeWithTag("planner_details_ways").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(PlannerViewer.SURFACES to SurfaceKind.GRAVEL, PlannerViewer.WAYS to WayKind.TRACK), ouverts)
    }

    // ---------- Panneau VIEWER ----------

    @Test fun `le panneau change de viewer par son menu et se referme par sa fleche`() {
        val s = LayerWaysViewerState().apply { open(couche, voies, listOf(profil), PlannerViewer.SURFACES, SurfaceKind.GRAVEL) }
        compose.setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) {
                val w = s.ways
                val l = s.layer
                if (w != null && l != null) WaysViewerPanel(
                    viewer = s.viewer, title = l.name, segments = w.all, highlight = s.highlight, imperial = false,
                    offered = s.offered,
                    onSwitch = { v, k -> s.switch(v, k) }, onSelect = { s.select(it) }, onBack = { s.close() },
                )
            } }
        }
        compose.onNodeWithText("Soreze - Arfons").assertExists()
        compose.onNodeWithTag("planner_viewer_bar").assertExists()
        compose.onNodeWithTag("layer_viewer_title").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.planner_details_ways)).performClick()
        compose.waitForIdle()
        assertEquals(PlannerViewer.WAYS, s.viewer)
        assertEquals(WayKind.TRACK, s.highlight)
        compose.onNodeWithTag("layer_viewer_back").performClick()
        compose.waitForIdle()
        assertEquals(false, s.isOpen)
    }

    // ---------- Mise en page ----------

    private fun haut(texte: String) = compose.onNodeWithText(texte).fetchSemanticsNode().boundsInRoot.top

    /** Les deniveles se lisent ensemble, la duree et la date aussi : une ligne pour deux. */
    @Test fun `deniveles, puis duree et date, partagent leur ligne`() {
        fenetre(voies)
        compose.waitForIdle()
        assertEquals(haut(ctx.getString(R.string.chip_ascent)), haut(ctx.getString(R.string.chip_descent)), 0.5f)
        assertEquals(haut(ctx.getString(R.string.chip_duration)), haut(ctx.getString(R.string.stats_date)), 0.5f)
        assertTrue(haut(ctx.getString(R.string.chip_ascent)) < haut(ctx.getString(R.string.chip_duration)))
    }

    /**
     * Revetu, non revetu ET inconnu : la troisieme pastille ne tenait plus sur la ligne, son texte se
     * coupait a chaque lettre et creusait un grand vide sous la synthese. Elle passe a la ligne, entiere.
     *
     * Sur l'ecran du telephone ou c'est arrive : 360 dp de large, texte agrandi. Et en rendu NATIF : celui
     * par defaut de Robolectric ne mesure pas le texte (quelques pixels pour un mot), et tout "tenait".
     */
    @Config(qualifiers = "fr-w360dp-h760dp", fontScale = 1.3f)
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun `la synthese des revetements tient ses trois pastilles sur une ligne chacune`() {
        fenetre(LayerWays(listOf(listOf(
            WaySegment(700.0, "highway=tertiary surface=asphalt"),
            WaySegment(250.0, "highway=track surface=gravel"),
            WaySegment(50.0, ""),
        ))))
        compose.waitForIdle()
        fun bornes(texte: String) =
            compose.onAllNodesWithText(texte, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        // Le premier "Inconnu" est celui de la synthese ; le second, celui de la liste des revetements.
        val inconnu = bornes(ctx.getString(R.string.planner_details_unknown))
        val revetu = bornes(ctx.getString(R.string.planner_details_paved))
        assertTrue("pastille ecrasee : $inconnu", inconnu.width > revetu.width / 2)
        assertEquals(revetu.height, inconnu.height, 0.5f)
        // Les pourcentages sur une ligne, comme leur libelle.
        assertEquals(revetu.height, bornes("25 %").height, 0.5f)
    }

    // ---------- Elevation ----------

    /** Le profil vient au-dessus des surfaces, titre comme elles. */
    @Test fun `la rubrique elevation precede les surfaces`() {
        fenetre(voies)
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.planner_viewer_elevation)).assertExists()
        val elevation = compose.onNodeWithTag("details_elevation").fetchSemanticsNode()
        val surfaces = compose.onNodeWithTag("planner_details_surfaces").fetchSemanticsNode()
        assertTrue(elevation.positionInRoot.y + elevation.size.height <= surfaces.positionInRoot.y)
    }

    /** Un appui sur le profil ouvre son VIEWER, le point touche designe ; les voies suivent si on les a. */
    @Test fun `un appui sur le profil ouvre son viewer au point touche`() {
        val ouverts = mutableListOf<Triple<LayerWays?, PlannerViewer, Any?>>()
        fenetre(voies, ouvre = { w, v, k -> ouverts += Triple(w, v, k) })
        compose.waitForIdle()
        compose.onNodeWithTag("details_elevation_profile", useUnmergedTree = true).performScrollTo()
            .performTouchInput { click(center) }
        // L'appui simple n'est rendu qu'une fois ecarte le double appui : on laisse passer son delai.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val (w, v, k) = ouverts.single()
        assertEquals(voies, w)
        assertEquals(PlannerViewer.PROFILE, v)
        assertTrue("un point du profil, en metres : $k", k is Double && k in 0.0..1000.0)
    }

    @Test fun `sans altitudes, pas de rubrique elevation`() {
        fenetre(voies, profils = listOf(profil.copy(hasZ = false)))
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("details_elevation").fetchSemanticsNodes().size)
    }

    @Test fun `le viewer du profil montre le profil, et son menu les trois viewer`() {
        val s = LayerWaysViewerState().apply { open(couche, voies, listOf(profil), PlannerViewer.PROFILE, null) }
        compose.setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) {
                val l = s.layer
                if (l != null) WaysViewerPanel(
                    viewer = s.viewer, title = l.name, segments = s.ways?.all.orEmpty(), highlight = s.highlight,
                    imperial = false, offered = s.offered,
                    onSwitch = { v, k -> s.switch(v, k) }, onSelect = { s.select(it) }, onBack = { s.close() },
                    profile = {
                        fr.lc4918.trailog.ui.planner.ProfileViewerContent(
                            all = profil.samples, fullStats = profil.stats, zoom = null, cursor = s.cursor,
                            settings = SettingsEntity(), slope = false, lineColor = androidx.compose.ui.graphics.Color.Blue,
                            infos = { _, _ -> emptyList() }, onScrub = { s.tapProfile(it) }, onZoom = { _, _ -> },
                        )
                    },
                )
            } }
        }
        compose.onNodeWithText(ctx.getString(R.string.planner_viewer_elevation)).assertExists()
        compose.onNodeWithTag("layer_viewer_title").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.planner_details_surfaces)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.planner_details_ways)).performClick()
        compose.waitForIdle()
        assertEquals(PlannerViewer.WAYS, s.viewer)
    }
}
