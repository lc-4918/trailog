package fr.lc4918.trailog.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le "i" des lignes de reglage : l'explication n'est plus ecrite sous la ligne, elle parait dans une bulle
 * au toucher du "i" - et ce toucher ne bascule pas l'interrupteur de la ligne.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class InfoTipTest {

    @get:Rule val compose = createComposeRule()

    private val explication = "Hebergements, restaurants, services le long du parcours"

    @Test fun `l'explication d'un interrupteur parait au toucher du i, sans le basculer`() {
        var allume = false
        compose.setContent {
            ProvideSettingsPalette(dark = false) {
                SettingsCard { SwitchLine("Points d'interet", allume, info = explication) { allume = it } }
            }
        }
        compose.onNodeWithText(explication).assertDoesNotExist()
        compose.onAllNodesWithTag("info_tip")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText(explication).assertIsDisplayed()
        assertFalse("le i ne bascule pas l'interrupteur", allume)
    }

    @Test fun `un choix porte aussi son i`() {
        compose.setContent {
            ProvideSettingsPalette(dark = false) {
                SettingsCard {
                    PickRow("Symbole", "Puce", listOf("Puce", "Fleche"), optionLabel = { it }, info = explication) {}
                }
            }
        }
        compose.onNodeWithText(explication).assertDoesNotExist()
        compose.onAllNodesWithTag("info_tip")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText(explication).assertIsDisplayed()
    }

    /** Une rubrique entiere peut porter son "i", a cote de son titre. */
    @Test fun `un titre de rubrique porte son i`() {
        compose.setContent {
            ProvideSettingsPalette(dark = false) { SectionTitle("Services", info = explication) }
        }
        compose.onNodeWithText(explication).assertDoesNotExist()
        compose.onAllNodesWithTag("info_tip")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText(explication).assertIsDisplayed()
    }

    /** Le "i" ne remplace pas la ligne du dessous quand elle dit un etat : le nombre de lieux reste. */
    @Test fun `le compteur sous la ligne reste affiche a cote du i`() {
        compose.setContent {
            ProvideSettingsPalette(dark = false) {
                SettingsCard { SetRow("Effacer l'historique", sub = "8 lieux", info = explication) }
            }
        }
        compose.onNodeWithText("8 lieux").assertIsDisplayed()
        compose.onNodeWithText(explication).assertDoesNotExist()
    }

    /** La disquette ne parait qu'apres une modification du champ, et enregistre ce qu'on a tape. */
    @Test fun `la disquette du champ parait apres modification`() {
        var enregistre: String? = null
        compose.setContent {
            ProvideSettingsPalette(dark = false) {
                var texte by remember { mutableStateOf("Trailog") }
                SettingsCard {
                    FieldRow("Titre") {
                        SettingsTextField(
                            texte, "Trailog",
                            trailing = if (texte == "Trailog") null else ({
                                RowIcon(Icons.Filled.Save, "Enregistrer") { enregistre = texte }
                            }),
                        ) { texte = it }
                    }
                }
            }
        }
        compose.onAllNodesWithContentDescription("Enregistrer").assertCountEquals(0)
        compose.onNode(hasSetTextAction()).performTextReplacement("Mes traces")
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Enregistrer")).performClick()
        assertEquals("Mes traces", enregistre)
    }

    /**
     * Les infos affichees se cochent une par une, et l'ORDRE enregistre reste celui des options : c'est
     * lui qui decide de l'ordre a l'ecran, pas l'ordre des gestes.
     */
    @Test fun `les infos s'allument une par une, dans l'ordre des options`() {
        var csv = "asc"
        compose.setContent {
            ProvideSettingsPalette(dark = false) {
                SettingsCard {
                    InfoSwitchRows(
                        listOf("dist" to "Distance", "asc" to "D+", "desc" to "D-"), csv,
                    ) { csv = it }
                }
            }
        }
        compose.onNodeWithText("Distance").performClick()
        assertEquals("dist,asc", csv)
    }

    /**
     * Le "i" fait partie du TEXTE du libelle, pose apres son dernier mot, et non a cote du bloc de texte :
     * ainsi, quand le libelle passe sur deux lignes, il le suit sur la derniere - "Transparence barre de
     * statut (i)" - au lieu de rester au bout de la premiere.
     *
     * Verifie par la position du "i", DANS les bornes du texte : pose a cote, comme avant, il en sortait.
     * Le passage a la ligne lui-meme ne se mesure pas ici - Robolectric mesure un texte presque a vide -, et
     * le rendu natif qui le permettrait coutait quarante secondes a ce seul test.
     */
    @Test fun `le i fait partie du texte du libelle`() {
        compose.setContent {
            ProvideSettingsPalette(dark = false) {
                SettingsCard { SetRow("Transparence barre de statut", info = explication) }
            }
        }
        val texte = compose.onNodeWithText("Transparence barre de statut", substring = true)
            .fetchSemanticsNode().boundsInRoot
        val i = compose.onAllNodesWithTag("info_tip")[0].fetchSemanticsNode().boundsInRoot
        assertTrue("le i est dans le texte : $i dans $texte",
            i.left >= texte.left - 1f && i.right <= texte.right + 1f && i.top >= texte.top - 1f && i.bottom <= texte.bottom + 1f)
    }
}
