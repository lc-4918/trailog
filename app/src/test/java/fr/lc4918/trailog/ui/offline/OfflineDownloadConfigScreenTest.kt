package fr.lc4918.trailog.ui.offline

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.map.offline.Bbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L'ecran de configuration du telechargement : le long d'une trace, son titre, sa ligne d'explication, et
 * la portion a emporter - toute la trace par defaut.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class OfflineDownloadConfigScreenTest {

    @get:Rule val compose = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    /** Une trace plein nord de 10 km. */
    private val trace = (0..10).map { i -> 6.0 to 45.0 + i * 1000.0 / 111_195.0 }

    private fun ecran(corridor: List<Pair<Double, Double>>?, pois: Boolean = false) = compose.setContent {
        OfflineDownloadConfigScreen(
            bbox = Bbox.of(6.0, 45.0, 6.01, 45.1), providerMinZoom = 8, providerMaxZoom = 16, dark = false,
            styleJson = null, styleUrl = null, onDismiss = {}, onDownload = {}, poiAvailable = pois,
            corridorPoints = corridor, corridorName = "GR 9", overview = { _, _, _, _ -> },
        )
    }

    @Test fun `le long d'une trace, le titre et la portion entiere par defaut`() {
        ecran(trace)
        compose.onNodeWithText(ctx.getString(R.string.offline_config_title_track)).assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(R.string.offline_config_track_hint)).assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(R.string.offline_config_section_value, 0, 10)).assertIsDisplayed()
    }

    @Test fun `une zone garde son titre, sans portion`() {
        ecran(null)
        compose.onNodeWithText(ctx.getString(R.string.offline_config_title)).assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(R.string.offline_config_section_label)).assertDoesNotExist()
    }

    /** L'explication de la largeur est derriere le "i" : absente de l'ecran, elle parait au toucher. */
    @Test fun `l'explication de la largeur parait au toucher du i`() {
        ecran(trace)
        compose.onNodeWithTag("info_tip_text").assertDoesNotExist()
        compose.onAllNodesWithTag("info_tip")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ctx.getString(R.string.offline_config_width_hint)).assertIsDisplayed()
    }

    /** Dans l'ordre ou l'on decide : zoom, portion, largeur, puis la carte, puis le nom. */
    @Test fun `les reglages se suivent dans l'ordre`() {
        compose.setContent {
            OfflineDownloadConfigScreen(
                bbox = Bbox.of(6.0, 45.0, 6.01, 45.1), providerMinZoom = 8, providerMaxZoom = 16, dark = false,
                styleJson = null, styleUrl = null, onDismiss = {}, onDownload = {},
                corridorPoints = trace, corridorName = "GR 9",
                overview = { _, _, _, _ ->
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.testTag("apercu"),
                    )
                },
            )
        }
        fun y(res: Int) = compose.onNodeWithText(ctx.getString(res)).fetchSemanticsNode().positionInRoot.y
        val zoom = y(R.string.offline_config_zoom_label)
        val portion = y(R.string.offline_config_section_label)
        val largeur = y(R.string.offline_config_width_label)
        val carte = compose.onNodeWithTag("apercu").fetchSemanticsNode().positionInRoot.y
        val nom = y(R.string.offline_config_name_label)
        assertTrue(zoom < portion && portion < largeur && largeur < carte && carte < nom)
    }

    /** La croix reste au coin haut : sous un titre de deux lignes, elle ne descend pas au milieu du bloc. */
    @Test fun `la croix de fermeture est en haut`() {
        ecran(trace)
        val croix = compose.onNode(hasContentDescription(ctx.getString(R.string.action_close)))
            .fetchSemanticsNode().boundsInRoot
        val titre = compose.onNodeWithText(ctx.getString(R.string.offline_config_title_track))
            .fetchSemanticsNode().boundsInRoot
        val sousTitre = compose.onNodeWithText(ctx.getString(R.string.offline_config_track_hint))
            .fetchSemanticsNode().boundsInRoot
        assertTrue("la croix commence au-dessus du titre", croix.top <= titre.top)
        assertTrue("et finit avant le bas du sous-titre", croix.bottom < sousTitre.bottom)
    }

    /** Les points d'interet ont aussi leur "i" : l'explication n'est plus ecrite sous la ligne. */
    @Test fun `l'explication des points d'interet parait au toucher du i`() {
        ecran(trace, pois = true)
        val texte = ctx.getString(R.string.offline_config_pois_desc)
        compose.onNodeWithText(texte).assertDoesNotExist()
        compose.onAllNodesWithTag("info_tip")[1].performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(texte).assertIsDisplayed()
    }

    /**
     * L'apercu dessine la trace ENTIERE et se cadre sur elle ; seule la zone tampon suit la portion. Sans
     * cela, la trace raccourcissait avec le curseur, et l'on ne voyait plus quelle partie on emportait.
     */
    @Test fun `l'apercu garde la trace entiere`() {
        val emprise = Bbox.of(6.0, 45.0, 6.01, 45.1)
        var cadre: Bbox? = null
        var dessinee: List<Pair<Double, Double>>? = null
        var tampon: List<Pair<Double, Double>>? = null
        compose.setContent {
            OfflineDownloadConfigScreen(
                bbox = emprise, providerMinZoom = 8, providerMaxZoom = 16, dark = false,
                styleJson = null, styleUrl = null, onDismiss = {}, onDownload = {},
                corridorPoints = trace, corridorName = "GR 9",
                overview = { b, t, c, _ -> cadre = b; dessinee = t; tampon = c },
            )
        }
        compose.waitForIdle()
        assertEquals(emprise, cadre)
        assertSame(trace, dessinee)
        assertEquals(trace, tampon)
    }

    /** La ligne grise sous le titre ne se montre que tout en haut : on descend, elle se retire. */
    @Test fun `la ligne sous le titre se retire au defilement`() {
        ecran(trace, pois = true)
        val ligne = ctx.getString(R.string.offline_config_track_hint)
        compose.onNodeWithText(ligne).assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(R.string.offline_config_pois_label)).performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithText(ligne).assertDoesNotExist()
    }
}
