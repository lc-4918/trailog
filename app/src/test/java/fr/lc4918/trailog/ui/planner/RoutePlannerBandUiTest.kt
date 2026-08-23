package fr.lc4918.trailog.ui.planner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.model.PlannerHistory
import fr.lc4918.trailog.geocode.GeocodePlace
import org.junit.Assert.assertEquals
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

    private fun affiche(state: RoutePlannerState) = compose.setContent {
        MaterialTheme {
            Surface(Modifier.fillMaxSize()) {
                RoutePlannerBand(
                    state = state,
                    imperial = false,
                    settings = SettingsEntity(),
                    lastLabelInsetPx = 0f,
                    maxHeight = 600.dp,
                    onPickCurrentPosition = {},
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
}
