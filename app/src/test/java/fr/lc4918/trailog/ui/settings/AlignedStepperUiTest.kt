package fr.lc4918.trailog.ui.settings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les lignes "libelle - taille - interrupteur" du tableau de bord : leurs pas-a-pas sont alignes.
 *
 * **Ce que ce test protege.** Le pas-a-pas se glisse dans l'espace laisse entre le libelle et
 * l'interrupteur, et cet espace n'est pas le meme d'une ligne a l'autre. Laisser chaque ligne s'arranger
 * donnerait une colonne de commandes en escalier, que l'oeil ne suit plus - c'est la ligne au plus long
 * libelle qui decide pour toutes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class AlignedStepperUiTest {

    @get:Rule val compose = createComposeRule()

    private val libelles = listOf("Vitesse instantanee", "Denivele negatif restant", "Duree en mouvement")

    private fun lignes(onValue: (Int) -> Unit = {}, onToggle: (Boolean) -> Unit = {}) = compose.setContent {
        ProvideSettingsPalette(dark = false) {
            SettingsCard {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val metrics = alignedStepperMetrics(libelles, maxWidth)
                    Column {
                        libelles.forEach { l ->
                            SwitchStepperRow(
                                label = l, value = 16, min = 10, max = 40, checked = true, metrics = metrics,
                                decreaseLabel = "Diminuer", increaseLabel = "Augmenter",
                                onValue = onValue, onToggle = onToggle,
                            )
                        }
                    }
                }
            }
        }
    }

    /*
     * L'arbre NON FUSIONNE, partout dans cette classe : la ligne entiere est cliquable, donc Compose
     * fusionne la semantique de ses enfants dans un seul noeud. Sur l'arbre fusionne, chercher "+" rend
     * la LIGNE, large comme la carte - trois lignes alignees sur zero, un test qui passe toujours sans
     * rien verifier.
     */
    @Test fun `les pas-a-pas de toutes les lignes partagent la meme abscisse`() {
        lignes()
        val x = compose.onAllNodesWithText("+", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.positionInRoot.x }
        assertEquals("un pas-a-pas par ligne", 3, x.size)
        assertEquals("et tous a la meme abscisse", 1, x.distinct().size)
    }

    /** Il se glisse entre les deux, et non par-dessus : la ligne la plus serree fait la place pour toutes. */
    @Test fun `le pas-a-pas tient entre le libelle et l'interrupteur`() {
        lignes()
        val moins = compose.onAllNodesWithText("−", useUnmergedTree = true)
            .fetchSemanticsNodes().first()
        val libelleLePlusLong = compose.onAllNodesWithText(libelles[1], useUnmergedTree = true)
            .fetchSemanticsNodes().first()
        val finDuLibelle = libelleLePlusLong.positionInRoot.x + libelleLePlusLong.size.width
        assertTrue("le pas-a-pas commence apres le plus long libelle", moins.positionInRoot.x >= finDuLibelle)
    }

    /**
     * Un lecteur d'ecran doit pouvoir dire ce que font ces deux boutons.
     *
     * Le libelle etait passe depuis toujours par tous les appelants, et `StepButton` ne l'appliquait
     * nulle part : ne restait que le glyphe, "moins" et "plus", sans dire de quoi.
     */
    @Test fun `les boutons du pas-a-pas portent leur libelle`() {
        lignes()
        compose.onAllNodesWithContentDescription("Augmenter", useUnmergedTree = true)
            .assertCountEquals(libelles.size)
        compose.onAllNodesWithContentDescription("Diminuer", useUnmergedTree = true)
            .assertCountEquals(libelles.size)
    }

    /** Les boutons gardent leur propre tap : regler une taille ne doit pas masquer le champ. */
    @Test fun `le pas-a-pas ne bascule pas l'interrupteur de sa ligne`() {
        var taille = 16
        var bascule = false
        lignes(onValue = { taille = it }, onToggle = { bascule = true })
        compose.onAllNodesWithText("+", useUnmergedTree = true)[0].performClick()
        assertEquals(17, taille)
        assertEquals("l'interrupteur n'a pas bouge", false, bascule)
    }
}
