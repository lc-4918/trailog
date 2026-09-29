package fr.lc4918.trailog.ui.planner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.PlannerHistory
import fr.lc4918.trailog.domain.model.TrackPoint
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.geocode.GeocodePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La bande du planificateur, jouee pour de vrai.
 *
 * **Ce test existe a cause d'un plantage** : toucher le champ de depart d'un itineraire deja calcule
 * fermait l'application, net (cf. le commit "revenir sur une etape deja remplie ne ferme plus
 * l'application"). Une etape remplie n'affiche pas son champ mais un cadre, qui demandait le focus a un
 * champ non compose - le `FocusRequester` n'avait aucun noeud a saisir et levait.
 *
 * Aucun test de domaine ne pouvait voir cela : la faute n'etait ni dans l'etat, ni dans le calcul, mais
 * dans l'ordre de composition. C'est exactement ce qu'un test d'interface attrape, et rien d'autre.
 *
 * Le geocodeur n'est jamais interroge ici : rien n'est tape, et une etape qui porte deja un lieu ne
 * demande rien (cf. la garde `step.target != null` de la recherche).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class RoutePlannerBandUiTest {

    @get:Rule val compose = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val grenoble = GeocodePlace(listOf("Grenoble", "Isere, France"), 5.72, 45.18)

    private fun affiche(state: RoutePlannerState, settings: SettingsEntity = SettingsEntity()) = compose.setContent {
        MaterialTheme {
            Surface(Modifier.fillMaxSize()) {
                RoutePlannerBand(
                    state = state,
                    imperial = false,
                    settings = settings,
                    lastLabelInsetPx = 0f,
                    maxHeight = 600.dp,
                    onPickCurrentPosition = {},
                    onPickOnMap = { state.startPickingOnMap(it) },
                    sensorEnabled = false,
                    geocoding = GeocodingParams("http://localhost", "fr", 5),
                    history = PlannerHistory(),
                    onPlaceChosen = {},
                    onPlaceForgotten = {},
                    onImport = {},
                    onDownload = {},
                )
            }
        }
    }

    private fun planificateurOuvert() = RoutePlannerState().apply { openPlanner() }

    @Test fun `la bande s'ouvre sur un depart et une arrivee`() {
        affiche(planificateurOuvert())
        compose.onNodeWithText(ctx.getString(R.string.planner_start)).assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(R.string.planner_end)).assertIsDisplayed()
    }

    @Test fun `une etape retenue montre son lieu`() {
        val state = planificateurOuvert().apply { setStart(grenoble) }
        affiche(state)
        compose.onNodeWithText(grenoble.label).assertIsDisplayed()
    }

    /**
     * LE test de non-regression : revenir sur une etape deja remplie.
     *
     * Le cadre cede la place au champ de saisie, qui s'ouvre VIDE - le lieu retenu n'y est pas recopie,
     * sans quoi la frappe suivante s'ecrirait a cote de lui et c'est le tout qui partirait au geocodeur.
     */
    @Test fun `toucher une etape remplie ouvre son champ, sans emporter l'application`() {
        val state = planificateurOuvert().apply { setStart(grenoble) }
        affiche(state)
        compose.onNodeWithText(grenoble.label).performClick()
        compose.waitForIdle()
        // Le champ a pris la place du cadre : son intitule est la, le libelle du lieu n'y est plus.
        compose.onNodeWithText(ctx.getString(R.string.planner_start)).assertIsDisplayed()
        // Et le lieu reste pose tant qu'on n'a rien tape : l'itineraire ne doit pas s'effacer d'un tap.
        assertEquals(StepTarget.Place(grenoble), state.steps.first().target)
    }

    /** La croix d'une etape la vide, lieu compris. */
    @Test fun `la croix efface l'etape`() {
        val state = planificateurOuvert().apply { setStart(grenoble) }
        affiche(state)
        compose.onNode(
            androidx.compose.ui.test.hasContentDescription(ctx.getString(R.string.planner_clear_step)),
        ).performClick()
        compose.waitForIdle()
        assertEquals(null, state.steps.first().target)
    }

    /** Repliee, la bande n'est plus qu'un bouton : la carte doit rester entierement visible dessous. */
    @Test fun `repliee, la bande ne montre plus ses etapes`() {
        val state = planificateurOuvert().apply { collapse(true) }
        affiche(state)
        compose.onNodeWithText(ctx.getString(R.string.planner_start)).assertDoesNotExist()
    }

    /**
     * "Choisir un point sur la carte" est offert au focus d'un champ vierge, SANS condition de capteur -
     * a la difference de la position actuelle : montrer un endroit ne demande rien a personne.
     *
     * Le test compose sans capteur (`sensorEnabled = false`), ce qui est exactement le cas ou la liste
     * etait auparavant vide.
     */
    @Test fun `un champ vierge propose de montrer un point sur la carte`() {
        val state = planificateurOuvert()
        affiche(state)
        compose.onNodeWithText(ctx.getString(R.string.planner_start)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.planner_pick_on_map)).assertIsDisplayed()
    }

    /** Le choisir range la bande et passe la main a la carte (cf. RoutePlannerState.startPickingOnMap). */
    @Test fun `choisir un point sur la carte range la bande`() {
        val state = planificateurOuvert()
        affiche(state)
        compose.onNodeWithText(ctx.getString(R.string.planner_start)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.planner_pick_on_map)).performClick()
        compose.waitForIdle()
        assertTrue(state.pickingOnMap)
        assertTrue(state.collapsed)
    }

    /**
     * La discipline se choisit dans un menu : ferme, le selecteur ne montre que celle en cours ; ouvert, il
     * les offre toutes, et en retenir une la fait sienne - le libelle COURT du velo de route compris.
     */
    @Test fun `le selecteur de discipline ouvre son menu et en change`() {
        val state = planificateurOuvert()
        affiche(state)
        compose.onNodeWithText(ctx.getString(R.string.profile_hybrid_bike)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.profile_road_short)).performClick()
        compose.waitForIdle()
        assertEquals(RoutingProfile.ROAD_BIKE, state.profile)
        // Le menu referme, le selecteur porte la nouvelle discipline, et elle seule.
        compose.onNodeWithText(ctx.getString(R.string.profile_road_short)).assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(R.string.profile_hybrid_bike)).assertDoesNotExist()
    }

    /**
     * Enregistrer et exporter sont la, mais inertes, tant que rien n'est calcule : la ligne ne bouge pas a
     * l'arrivee du trajet. Reinitialiser, lui, sert toujours.
     */
    @Test fun `enregistrer et exporter attendent un trajet calcule`() {
        affiche(planificateurOuvert())
        compose.onNodeWithText(ctx.getString(R.string.planner_action_save)).assertIsNotEnabled()
        compose.onNodeWithText(ctx.getString(R.string.planner_action_export)).assertIsNotEnabled()
        compose.onNodeWithText(ctx.getString(R.string.planner_reset)).assertIsEnabled()
    }

    /** Reinitialiser demande confirmation avant d'effacer : la boite s'ouvre, rien n'est encore perdu. */
    @Test fun `reinitialiser demande confirmation`() {
        val state = planificateurOuvert()
        state.choose(state.steps.first(), StepTarget.Place(grenoble))
        affiche(state)
        compose.onNodeWithText(ctx.getString(R.string.planner_reset)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.planner_reset_confirm_title)).assertIsDisplayed()
        compose.onNodeWithText(grenoble.label).assertExists()
    }

    /** Un itineraire calcule, qui monte : de quoi colorier par pente et montrer un profil. */
    private fun calcule(): RoutePlannerState = planificateurOuvert().apply {
        val pts = List(50) { TrackPoint(5.72 + it * 0.001, 45.18, 200.0 + it * 5, null) }
        publish(RouteState.Done(4000.0, 1200.0, TrackMath.compute(pts)))
    }

    private fun existe(tag: String) =
        compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /**
     * Profil replie, le "i" de la legende des pentes se tient au bout de "Ajouter une etape", et la
     * legende se deplie SOUS cette ligne ; profil deplie, il passe sur la ligne du profil.
     */
    @Test fun `le i de la legende suit le profil, de la ligne d'ajout a celle du profil`() {
        val state = calcule()
        affiche(state)
        assertTrue(existe("planner_slope_legend_info"))
        assertTrue("pas de i sur la ligne du profil replie", !existe("slope_legend_info"))
        assertTrue(!existe("planner_slope_legend"))
        compose.onNodeWithTag("planner_slope_legend_info").performClick()
        compose.waitForIdle()
        val ajout = compose.onNodeWithText(ctx.getString(R.string.planner_add_step)).fetchSemanticsNode().positionInRoot.y
        val legende = compose.onNodeWithTag("planner_slope_legend").fetchSemanticsNode().positionInRoot.y
        assertTrue("la legende sous la ligne d'ajout", legende > ajout)

        compose.onNodeWithText(ctx.getString(R.string.planner_show_profile)).performClick()
        compose.waitForIdle()
        assertTrue(existe("slope_legend_info"))
        assertTrue("plus de i sur la ligne d'ajout", !existe("planner_slope_legend_info"))
    }

    @Test fun `sans coloration par pente, pas de legende`() {
        affiche(calcule(), SettingsEntity(routeSlopeLine = false))
        assertTrue(!existe("planner_slope_legend_info"))
        compose.onNodeWithText(ctx.getString(R.string.planner_show_profile)).performClick()
        compose.waitForIdle()
        assertTrue(!existe("slope_legend_info"))
    }

    /** Un parcours calcule par BRouter, avec les attributs de ses voies. */
    private fun calculeAvecVoies(): RoutePlannerState = planificateurOuvert().apply {
        val pts = List(50) { TrackPoint(5.72 + it * 0.001, 45.18, 200.0 + it * 5, null) }
        publish(RouteState.Done(4000.0, 1200.0, TrackMath.compute(pts), segments = listOf(
            fr.lc4918.trailog.domain.model.WaySegment(3000.0, "highway=tertiary surface=asphalt"),
            fr.lc4918.trailog.domain.model.WaySegment(1000.0, "highway=track surface=gravel"),
        )))
    }

    /**
     * La zone "Details", repliee sous le profil : l'ouvrir montre les deux rubriques, avec la part de
     * chaque categorie ; toucher une rubrique ouvre son VIEWER, la plus longue categorie en evidence.
     *
     * L'ecran de Robolectric est petit : la bande y est pleine avant meme les details, exactement le cas
     * ou ils recevaient une hauteur nulle - et ou `performScrollTo` bouclait sans fin sur une fenetre vide.
     */
    @Test fun `les details s'ouvrent, et une rubrique ouvre son viewer`() {
        val state = calculeAvecVoies()
        affiche(state)
        assertTrue(!existe("planner_details_surfaces"))
        compose.onNodeWithText(ctx.getString(R.string.planner_show_details)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.planner_details_surfaces)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.planner_details_ways)).assertExists()
        compose.onNodeWithTag("planner_details_surfaces")
            .assert(androidx.compose.ui.test.hasText(ctx.getString(R.string.surface_asphalt)))
            .assert(androidx.compose.ui.test.hasText("75 %"))
        // La bande pleine ne doit pas laisser aux details une hauteur nulle : ils y etaient, mais invisibles.
        val rubrique = compose.onNodeWithTag("planner_details_ways").performScrollTo().fetchSemanticsNode()
        assertTrue("rubrique visible, et non ecrasee", rubrique.boundsInRoot.height > 0f)
        compose.onNodeWithTag("planner_details_ways").performClick()
        compose.waitForIdle()
        assertEquals(PlannerViewer.WAYS, state.viewer)
        assertEquals(fr.lc4918.trailog.domain.model.WayKind.ROAD, state.highlight)
    }

    /** Un parcours sans attributs de voies (Valhalla) le dit, au lieu d'afficher un trajet "inconnu". */
    @Test fun `sans attributs de voies, les details le disent`() {
        affiche(calcule())
        compose.onNodeWithText(ctx.getString(R.string.planner_show_details)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.planner_details_unavailable)).assertExists()
        assertTrue(!existe("planner_details_surfaces"))
    }

    /** Le panneau VIEWER : sa fleche rend la bande, et son menu passe d'un VIEWER a l'autre. */
    @Test fun `le panneau viewer change d'affichage et rend la bande`() {
        val state = calculeAvecVoies().apply {
            openViewer(PlannerViewer.SURFACES, fr.lc4918.trailog.domain.model.SurfaceKind.ASPHALT)
        }
        compose.setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) {
                PlannerViewerPanel(state, imperial = false, settings = SettingsEntity(), lastLabelInsetPx = 0f)
            } }
        }
        compose.onNodeWithTag("planner_viewer_bar").assertExists()
        compose.onNodeWithText(ctx.getString(R.string.planner_details_surfaces)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.planner_viewer_elevation)).performClick()
        compose.waitForIdle()
        assertEquals(PlannerViewer.PROFILE, state.viewer)
        assertTrue(existe("planner_viewer_profile"))
        compose.onNodeWithTag("planner_viewer_back").performClick()
        compose.waitForIdle()
        assertEquals(null, state.viewer)
    }
}
