package fr.lc4918.trailog.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Le rond d'attente : ce qu'il mesure, et ce qu'il ne recouvre pas.
 *
 * Il parait pendant les quelques dixiemes de seconde - parfois deux - ou l'application prepare un ecran
 * lourd sans que rien ne bouge : l'attente etait muette, et l'on retapait sur le bouton.
 *
 * **Ce que ces tests ne couvrent PAS** : l'encre. Ils mesurent la place occupee, et c'est
 * precisement ce qui ne suffisait pas - l'indicateur de Material 3 en 1.4 prenait bien ses 40 % et
 * n'en dessinait qu'une pastille dans un coin. La capture d'image, qui seule le dirait, n'aboutit
 * pas sous Robolectric (le dessin de fenetre ne se termine jamais). Le rond est donc trace au
 * compas plutot que pris a Material, et son dessin se verifie a l'oeil.
 */
@RunWith(RobolectricTestRunner::class)
class BusySpinnerTest {

    @get:Rule val compose = createComposeRule()

    /** Quarante pour cent de la largeur de ce qui l'accueille : assez grand pour ne pas etre cherche. */
    @Test fun `le rond fait 40 pour cent de la largeur`() {
        var attendu = 0
        compose.setContent {
            attendu = with(LocalDensity.current) { (200.dp * 0.4f).roundToPx() }
            Box(Modifier.size(200.dp)) { BusySpinner() }
        }
        val n = compose.onNodeWithTag("busy_spinner").fetchSemanticsNode()
        assertEquals(attendu, n.size.width)
        assertEquals(attendu, n.size.height)
    }

    /** Il se centre sur ce qui l'accueille, et non sur un coin. */
    @Test fun `le rond se pose au milieu`() {
        compose.setContent { Box(Modifier.size(200.dp)) { BusySpinner() } }
        compose.onNodeWithTag("busy_spinner").assertIsDisplayed()
        val n = compose.onNodeWithTag("busy_spinner").fetchSemanticsNode()
        val conteneur = with(compose.density) { 200.dp.roundToPx() }
        val marge = (conteneur - n.size.width) / 2
        assertEquals(marge.toFloat(), n.positionInRoot.x, 1f)
        assertEquals(marge.toFloat(), n.positionInRoot.y, 1f)
    }
}
