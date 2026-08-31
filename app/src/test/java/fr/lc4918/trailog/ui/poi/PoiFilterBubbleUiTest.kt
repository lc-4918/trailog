package fr.lc4918.trailog.ui.poi

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.domain.model.PoiGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La bulle des categories de points d'interet, jouee pour de vrai.
 *
 * **Ce qu'elle remplace**, et ce qui justifie de l'eprouver : les vingt-sept categories vivaient dans
 * Reglages > Trajets, ou une case a cocher qui ne coche rien se voit tout de suite. Ici elles commandent
 * la carte depuis la carte, et une ligne qui ne bascule pas se lit comme un service muet - on croit que
 * les campings n'existent pas dans la region.
 *
 * Sur la JVM et non sur un appareil (cf. TESTS.md) : Robolectric joue la composition entiere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class PoiFilterBubbleUiTest {

    @get:Rule val compose = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    /**
     * Compose la bulle sur [depart] et rend ce que les taps en ont fait.
     *
     * Un etat Compose et non une simple variable : la bulle est menee par son appelant - elle n'a pas de
     * filtre a elle - et une variable ordinaire ne la recomposerait pas. Le deuxieme tap retomberait alors
     * sur l'affichage du premier, ce qui est exactement la faute qu'on veut pouvoir attraper.
     */
    private fun bulle(depart: PoiFilters = PoiFilters()): () -> PoiFilters {
        val etat = mutableStateOf(depart)
        compose.setContent {
            PoiFilterBubble(etat.value, onFilters = { etat.value = it })
        }
        return { etat.value }
    }

    /** La meme bulle, avec l'oeil : rend l'etat de la mise de cote, que les taps font basculer. */
    private fun bulleAvecOeil(depart: PoiFilters = PoiFilters()): () -> Boolean {
        val filtre = mutableStateOf(depart)
        val masque = mutableStateOf(false)
        compose.setContent {
            PoiFilterBubble(
                filtre.value,
                onFilters = { filtre.value = it },
                masked = masque.value,
                onToggleMask = { masque.value = !masque.value },
            )
        }
        return { masque.value }
    }

    private fun nom(c: PoiCategory) = ctx.getString(poiCategoryLabelRes(c))

    @Test fun `les libelles de groupe restent courts`() {
        compose.setContent {
            PoiGroup.entries.forEach { g ->
                assertTrue("${g.key} trop long : ${poiGroupLabel(g)}", poiGroupLabel(g).length <= 10)
            }
        }
    }

    @Test fun `l'onglet ouvert montre les categories de son groupe`() {
        bulle()
        // Hebergements en tete, l'onglet par defaut.
        compose.onNodeWithText(nom(PoiCategory.CAMPINGS)).assertIsDisplayed()
        compose.onNodeWithText(nom(PoiCategory.HOTELS)).assertIsDisplayed()
    }

    /** Le geste central : une ligne bascule sa categorie, et rien d'autre. */
    @Test fun `un tap sur une ligne bascule sa categorie`() {
        val etat = bulle()
        compose.onNodeWithText(nom(PoiCategory.CAMPINGS)).performClick()
        compose.waitForIdle()
        assertFalse("le camping est masque", etat().isShown(PoiCategory.CAMPINGS))
        assertTrue("les hotels ne bougent pas", etat().isShown(PoiCategory.HOTELS))
        compose.onNodeWithText(nom(PoiCategory.CAMPINGS)).performClick()
        compose.waitForIdle()
        assertTrue("et il revient", etat().isShown(PoiCategory.CAMPINGS))
    }

    /**
     * Les onglets changent de groupe : c'est la seule facon d'atteindre les vingt-sept.
     *
     * **Ce cas a trouve une faute reelle.** L'onglet portait `clip` avant `clickable` - l'ordre qu'on
     * ecrit d'instinct pour que l'ondulation epouse les angles - et le doigt ne l'atteignait pas : la
     * bulle s'ouvrait sur les hebergements et n'en sortait jamais. Un tap sans effet ne leve rien ; on
     * aurait conclu que la region n'a pas de restaurants.
     */
    @Test fun `changer d'onglet montre un autre groupe`() {
        bulle()
        compose.onNodeWithText(ctx.getString(R.string.poi_group_food)).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText(nom(PoiCategory.RESTAURANTS)).assertCountEquals(1)
        compose.onAllNodesWithText(nom(PoiCategory.HOTELS)).assertCountEquals(0)
    }

    /** "Tout selectionner" vide le groupe ouvert, et lui seul. */
    @Test fun `tout selectionner vide le groupe ouvert`() {
        val etat = bulle()
        compose.onNodeWithText(ctx.getString(R.string.settings_poi_select_all)).performClick()
        compose.waitForIdle()
        assertEquals(
            "aucun hebergement",
            0, PoiCategory.of(PoiGroup.LODGING).count { etat().isShown(it) },
        )
        assertTrue("la restauration est intacte", etat().isShown(PoiCategory.RESTAURANTS))
    }

    /**
     * "Tout masquer" eteint la couche : c'est la SEULE facon de le faire depuis que le filtre a remplace
     * l'interrupteur du bouton, et le geste doit donc etre atteignable d'un tap.
     */
    @Test fun `tout masquer eteint la couche`() {
        val etat = bulle()
       compose.onNodeWithContentDescription(ctx.getString(R.string.poi_filter_hide_all)).performClick()
        compose.waitForIdle()
        assertTrue(etat().nothingShown)
    }

    /**
     * ... et il disparait quand il n'y a plus rien a masquer : un geste sans effet ne doit pas s'offrir,
     * c'est la regle des commandes de la carte.
     */
    @Test fun `tout masquer ne s'affiche pas sur une carte deja vide`() {
        bulle(PoiFilters().hideAll())
       compose.onAllNodesWithContentDescription(ctx.getString(R.string.poi_filter_hide_all)).assertCountEquals(0)
    }

    /**
     * L'oeil range la couche SANS toucher au filtre - c'est ce qui le distingue de la poubelle d'a cote, et
     * la raison pour laquelle il existe : "on ne peut plus masquer les points d'interet", disait le
     * testeur, qui n'avait que le geste destructeur.
     */
    @Test fun `l'oeil range la couche sans vider le filtre`() {
        val filtre = mutableStateOf(PoiFilters())
        val masque = mutableStateOf(false)
        compose.setContent {
            PoiFilterBubble(
                filtre.value,
                onFilters = { filtre.value = it },
                masked = masque.value,
                onToggleMask = { masque.value = !masque.value },
            )
        }
        compose.onNodeWithContentDescription(ctx.getString(R.string.poi_filter_hide_layer)).performClick()
        compose.waitForIdle()
        assertTrue("la couche est rangee", masque.value)
        assertFalse("et le filtre est intact", filtre.value.nothingShown)
    }

    /** Rangee, l'oeil propose l'inverse : c'est l'etat courant que le dessin montre, non le geste. */
    @Test fun `l'oeil range propose de remontrer la couche`() {
        val masque = bulleAvecOeil()
        compose.onNodeWithContentDescription(ctx.getString(R.string.poi_filter_hide_layer)).performClick()
        compose.waitForIdle()
        assertTrue(masque())
        compose.onNodeWithContentDescription(ctx.getString(R.string.poi_filter_show_layer)).performClick()
        compose.waitForIdle()
        assertFalse(masque())
    }

    /** Rien a ranger sur une carte deja vide : l'oeil s'efface comme la poubelle. */
    @Test fun `l'oeil ne s'affiche pas sur une carte deja vide`() {
        bulle(PoiFilters().hideAll())
        compose.onAllNodesWithContentDescription(ctx.getString(R.string.poi_filter_hide_layer))
            .assertCountEquals(0)
    }
}
