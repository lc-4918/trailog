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
}
