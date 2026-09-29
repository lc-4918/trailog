package fr.lc4918.trailog.ui.routes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La fenetre de renommage d'un dossier ou d'une couche (cf. RenameDialog). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class RenameDialogTest {

    @get:Rule val compose = createComposeRule()

    private fun affiche(nom: String) = compose.setContent {
        MaterialTheme { RenameDialog(value = nom, onValue = {}, onConfirm = {}, onDismiss = {}) }
    }

    /** On vient pour taper : le champ a le focus des l'ouverture, sans toucher l'ecran. */
    @Test fun `le champ a le focus des l'ouverture`() {
        affiche("Demo")
        compose.waitForIdle()
        compose.onNodeWithTag("rename_field").assertIsFocused()
    }

    /**
     * Un nom court ne donne pas un champ etroit : le meme champ, avec un nom d'une lettre puis un nom qui
     * deborde, garde la meme largeur - celle de la fenetre. Sur un ecran large : sur un ecran etroit, un
     * champ qui s'adapte au contenu bute de toute facon sur le bord, et le test ne verrait rien.
     */
    @Config(qualifiers = "fr-w600dp-h900dp")
    @Test fun `le champ a la meme largeur pour un nom court ou long`() {
        var nom by mutableStateOf("A")
        compose.setContent {
            MaterialTheme { RenameDialog(value = nom, onValue = {}, onConfirm = {}, onDismiss = {}) }
        }
        val court = compose.onNodeWithTag("rename_field").fetchSemanticsNode().boundsInRoot.width
        nom = "EV1 Nantes - Hendaye par la cote atlantique, version longue du parcours"
        compose.waitForIdle()
        val long = compose.onNodeWithTag("rename_field").fetchSemanticsNode().boundsInRoot.width
        assertEquals(long, court, 0.5f)
    }
}
