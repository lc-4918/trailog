package fr.lc4918.trailog.ui.points

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PropValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * L'infobulle d'un point de trace, et l'editeur qui s'ouvre de son crayon.
 *
 * Le premier lien du point en devient le TITRE, souligne et cliquable ; les liens suivants gardent leur
 * pastille. Les libelles des champs sont en capitales. Dans l'editeur, l'image epinglee porte sa pastille
 * "Image de garde".
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class InfoBubbleUiTest {

    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun point(vararg props: Pair<String, PropValue>, pinned: String? = null) =
        PointFeature("p1", 5.9, 45.2, LinkedHashMap(props.toMap()), pinnedImageKey = pinned)

    private val refuge = point(
        KEY_NAME to PropValue.Text("Refuge de la Pra"),
        "ele" to PropValue.Text("2109"),
        "site" to PropValue.Link("refuge-lapra.fr", "https://refuge-lapra.fr"),
        "resa" to PropValue.Link("Reserver une nuit", "https://refuge-lapra.fr/reserver"),
    )

    private fun ouvre(f: PointFeature) = compose.setContent {
        InfoBubble(feature = f, schema = emptyList(), onEdit = {}, onClose = {})
    }

    @Test fun `le premier lien devient le titre, les suivants restent des pastilles`() {
        ouvre(refuge)
        compose.onNodeWithText("Refuge de la Pra").assertIsDisplayed()
        // Le premier lien n'a plus de ligne a lui : il est le titre.
        assertEquals(0, compose.onAllNodesWithText("refuge-lapra.fr").fetchSemanticsNodes().size)
        compose.onNodeWithText("Reserver une nuit").assertIsDisplayed()
    }

    @Test fun `toucher le titre ouvre le premier lien`() {
        ouvre(refuge)
        compose.onNodeWithText("Refuge de la Pra").performClick()
        val lance = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, lance.action)
        assertEquals("https://refuge-lapra.fr", lance.dataString)
    }

    @Test fun `sans lien, le titre ne mene nulle part`() {
        ouvre(point(KEY_NAME to PropValue.Text("Col du Coq")))
        compose.onNodeWithText("Col du Coq").performClick()
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun `les libelles des champs sont en capitales`() {
        ouvre(refuge)
        compose.onNodeWithText(app.getString(R.string.field_alias_ele).uppercase()).assertIsDisplayed()
    }

    @Test fun `le lien du titre est le premier dans l'ordre d'affichage, et quitte les champs`() {
        val props = refuge.props
        val (lien, restants) = titleLinkOf(props, listOf("ele", "site", "resa"))
        assertEquals("https://refuge-lapra.fr", lien?.url)
        assertEquals(listOf("ele", "resa"), restants)
        // Un lien sans adresse ne fait pas un titre.
        val vide = LinkedHashMap<String, PropValue>(mapOf("x" to PropValue.Link("rien", "")))
        assertEquals(null to listOf("x"), titleLinkOf(vide, listOf("x")))
    }

    @Test fun `dans l'editeur, l'image epinglee porte sa pastille`() {
        val f = point(
            KEY_NAME to PropValue.Text("Refuge"),
            "photo" to PropValue.Image("/nulle/part.jpg"),
            pinned = "photo",
        )
        compose.setContent {
            PropertyEditor(f, emptyList(), onSave = {}, onCancel = {}, onDelete = {}, onPickImage = {})
        }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText(app.getString(R.string.label_pinned_image))
            .fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun `dans l'editeur, une image non epinglee n'a pas de pastille`() {
        val f = point(KEY_NAME to PropValue.Text("Refuge"), "photo" to PropValue.Image("/nulle/part.jpg"))
        compose.setContent {
            PropertyEditor(f, emptyList(), onSave = {}, onCancel = {}, onDelete = {}, onPickImage = {})
        }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText(app.getString(R.string.label_pinned_image))
            .fetchSemanticsNodes().size)
    }

    /**
     * Sur une image de garde, le titre prend toute la largeur que le bouton d'agrandissement lui laisse, et
     * ce bouton se pose contre le bord droit. Le titre partageait la largeur a parts egales avec un vide :
     * "Plage des Sables d'Olonnes" se coupait a mi-bulle, et un titre court repoussait le bouton au milieu.
     */
    @Test fun `sur une image de garde, le titre prend la largeur et l'agrandissement le bord`() {
        val f = point(
            KEY_NAME to PropValue.Text("Plage des Sables d'Olonnes, grande plage de la baie"),
            "photo" to PropValue.Image("/nulle/part.jpg"),
            pinned = "photo",
        )
        ouvre(f)
        compose.waitForIdle()
        val largeur = InfoBubbleWidth.value
        // La PLACE du titre, et non son texte : sous Robolectric, un texte se mesure presque a vide.
        val titre = compose.onNodeWithTag("bubble_cover_title_area", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val agrandir = compose.onNodeWithContentDescription(app.getString(R.string.action_expand_image))
            .fetchSemanticsNode().boundsInRoot
        val densite = app.resources.displayMetrics.density
        assertTrue("la place du titre depasse la moitie de la bulle", titre.width / densite > largeur * 0.6f)
        assertTrue("l'agrandissement touche presque le bord droit",
            agrandir.right / densite >= largeur - OverlayInset.value - 1f)
    }
}
