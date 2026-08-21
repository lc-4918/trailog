package fr.lc4918.trailog.ui.poi

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.poi.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L'infobulle d'un point d'interet, jouee pour de vrai.
 *
 * **Le premier test d'interface de l'application**, et il vise l'endroit ou les plantages sont arrives :
 * un composable qu'on ouvre sur une donnee venue d'un service, dont un champ manque toujours quelque part.
 * Les tests de domaine ne voient rien de cela - ils verifient ce que le service rend, pas ce que l'ecran
 * en fait.
 *
 * **Sur la JVM et non sur un appareil** (cf. TESTS.md) : Robolectric joue la composition entiere, et la
 * CI n'a pas d'emulateur. La contrepartie est connue - rien de ce qui touche MapLibre ne peut etre
 * compose ici, ses bibliotheques natives ne se chargeant pas sur la JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class PoiBubbleUiTest {

    @get:Rule val compose = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val fontaine = Poi(
        uuid = "osm:node/1", label = "", lat = 45.2, lon = 5.7, category = PoiCategory.WATER,
    )
    private val camping = Poi(
        uuid = "dt:1", label = "Camping du Drac", lat = 45.2, lon = 5.7, category = PoiCategory.CAMPINGS,
        city = "Grenoble", webUrl = "https://exemple.fr",
    )

    private fun ouvre(
        poi: Poi,
        onOpenWeb: (String) -> Unit = {},
        onSetStart: () -> Unit = {},
        onSetEnd: () -> Unit = {},
        onAddStep: () -> Unit = {},
        onClose: () -> Unit = {},
    ) = compose.setContent {
        PoiBubble(poi, onOpenWeb, onSetStart, onSetEnd, onAddStep, onClose)
    }

    @Test fun `le nom du lieu et sa categorie s'affichent`() {
        ouvre(camping)
        compose.onNodeWithText("Camping du Drac").assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(R.string.poi_cat_campings)).assertIsDisplayed()
    }

    /**
     * Un lieu SANS NOM prend celui de sa categorie.
     *
     * C'est le cas courant depuis qu'OpenStreetMap complete la couche : une fontaine ou des toilettes n'y
     * portent presque jamais de nom, et l'infobulle s'ouvrait sur un titre vide.
     */
    @Test fun `un lieu sans nom porte le nom de sa categorie`() {
        ouvre(fontaine)
        // Deux fois : en titre, faute de nom propre, et dans le badge de categorie qui y figure toujours.
        compose.onAllNodesWithText(ctx.getString(R.string.poi_cat_water)).assertCountEquals(2)
    }

    /** Les trois actions d'itineraire sont la, et chacune appelle la sienne. */
    @Test fun `les trois actions d'itineraire repondent`() {
        val appels = mutableListOf<String>()
        ouvre(camping,
            onSetStart = { appels += "depart" },
            onSetEnd = { appels += "arrivee" },
            onAddStep = { appels += "etape" })
        compose.onNodeWithText(ctx.getString(R.string.poi_set_start)).performClick()
        compose.onNodeWithText(ctx.getString(R.string.poi_set_end)).performClick()
        compose.onNodeWithText(ctx.getString(R.string.poi_add_step)).performClick()
        assertEquals(listOf("depart", "arrivee", "etape"), appels)
    }

    /** Le nom mene au site du lieu quand il en publie un. */
    @Test fun `le nom ouvre le site du lieu`() {
        var ouvert: String? = null
        ouvre(camping, onOpenWeb = { ouvert = it })
        compose.onNodeWithText("Camping du Drac").performClick()
        assertEquals("https://exemple.fr", ouvert)
    }

    /** Sans site, le nom ne mene nulle part : un tap dessus ne doit rien declencher, ni planter. */
    @Test fun `sans site, le nom ne declenche rien`() {
        var ouvert: String? = null
        ouvre(camping.copy(webUrl = null), onOpenWeb = { ouvert = it })
        compose.onNodeWithText("Camping du Drac").performClick()
        assertTrue(ouvert == null)
    }

    @Test fun `la croix referme l'infobulle`() {
        var ferme = false
        ouvre(camping, onClose = { ferme = true })
        compose.onNodeWithContentDescriptionRes(R.string.action_close).performClick()
        assertTrue(ferme)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionRes(res: Int) =
        onNode(androidx.compose.ui.test.hasContentDescription(ctx.getString(res)))
}
