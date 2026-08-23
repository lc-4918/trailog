package fr.lc4918.trailog.ui.routes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ce qu'on dit quand un geste demande n'a rien produit.
 *
 * **Un `runCatching` qui avale n'est pas une gestion d'erreur.** Ecrire un GPX par le selecteur du
 * systeme, partager une trace, ouvrir le site d'un point d'interet : ces trois gestes sont VOULUS, et
 * leur echec ne laissait rien - pas de fichier, pas de message, un tap qui n'a servi a rien. La pire des
 * deux issues n'est pas de croire l'application cassee : c'est de croire le fichier ecrit.
 *
 * Le declenchement reel (un flux de sortie qui refuse, aucune application capable de recevoir un GPX)
 * n'est pas jouable ici - il faudrait un systeme de fichiers qui echoue a la demande. Ce qui se verifie
 * est le chemin qui reste : que le message pose arrive sous les yeux, et qu'il s'en aille quand on l'a lu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class MapFailureDialogTest {

    @get:Rule val compose = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `l'echec pose se lit`() {
        val texte = ctx.getString(R.string.error_file_not_written)
        compose.setContent { MapFailureDialog(message = texte, onDismiss = {}) }
        compose.onNodeWithText(texte).assertIsDisplayed()
    }

    @Test fun `le bouton referme la boite`() {
        var ferme = false
        compose.setContent {
            MapFailureDialog(ctx.getString(R.string.error_no_app_share), onDismiss = { ferme = true })
        }
        compose.onNodeWithText(ctx.getString(R.string.action_ok)).performClick()
        assertTrue(ferme)
    }

    /** Le porteur retient le MESSAGE, pas un booleen : les trois echecs passent par la meme boite, et
     *  c'est lui qui dit lequel on regarde. */
    @Test fun `le porteur garde le message pose puis le rend`() {
        val etat = MainDialogState()
        assertNull(etat.failure)
        etat.failed(R.string.error_no_app_link)
        assertEquals(R.string.error_no_app_link, etat.failure)
        etat.failure = null
        assertNull(etat.failure)
    }
}
