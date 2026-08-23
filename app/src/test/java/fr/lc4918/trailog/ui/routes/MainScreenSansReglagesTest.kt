package fr.lc4918.trailog.ui.routes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.AppDatabase
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.MapSurface
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La carte pendant que les reglages n'ont pas encore repondu.
 *
 * **Ce test vient du terrain.** `MainViewModel.settings` etait un `StateFlow<SettingsEntity?>` dont la
 * valeur initiale etait null : le premier etat de tout ViewModel, et il dure le temps d'ouvrir la base.
 * Apres une mort du processus - Android reprend sa memoire pendant une longue sortie, ecran eteint -
 * l'activite et le ViewModel sont recrees, et cette fenetre se rouvrait en pleine route.
 *
 * Trois lectures y traitaient l'inconnu comme un "non" alors que le defaut du reglage est "oui". La carte
 * ne portait plus alors QUE le burger : ni bouton GPS, ni itineraire, ni gestionnaire de fonds. Les deux
 * premiers sont exactement ceux qu'un testeur a decrits comme disparus, apres vingt kilometres dans le
 * mauvais sens.
 *
 * **Le null a depuis disparu du chemin de LECTURE** : le flux rend les defauts de `SettingsEntity` tant
 * que la ligne n'est pas lue, et le type ne peut plus dire "je ne sais pas". Ce test garde ce que ce
 * changement PROMET, et qui ne se lit nulle part ailleurs : que ces defauts-la soient bien ceux que la
 * carte montre. Il vaut donc aujourd'hui pour la raison inverse d'hier - non plus attraper trois oublis,
 * mais verrouiller le mecanisme qui les rend impossibles.
 *
 * **Une classe a part, et la base videe.** Le singleton de base est partage par les methodes d'une meme
 * classe (cf. TESTS.md) - et, l'experience le montre, par les classes successives d'une meme execution :
 * sans ce menage, cette classe heritait des reglages ecrits par `MainScreenUiTest`, dont un menu lateral
 * regle sur le glissement, et l'etat qu'on veut tenir se defaisait. Ici, aucune ligne de reglages
 * n'existe : la base repond "rien", durablement, comme au premier lancement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr", application = TestTrailogApp::class)
class MainScreenSansReglagesTest {

    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestTrailogApp>()

    private object Surface : MapSurface {
        @Composable override fun Render(
            modifier: Modifier, controller: MapController,
            styleJson: String?, styleUrl: String?, onReady: () -> Unit,
        ) { }
    }

    /**
     * Aucune ligne de reglages, et aucun semis.
     *
     * L'appel direct a la base est reserve au depot dans le code de production (cf. la regle de couches
     * d'ARCHITECTURE, qui ne porte que sur `app/src/main/java`). Un test qui doit poser un etat de base
     * precis n'a pas d'autre porte.
     */
    @Before fun baseVide() {
        // Hors du fil principal : Room refuse d'y toucher, et il a raison - c'est une ecriture.
        val t = Thread { AppDatabase.get(app).clearAllTables() }
        t.start(); t.join()
    }

    private fun ecran() {
        compose.setContent { MaterialTheme { MainScreen(onSettings = {}, map = Surface) } }
        compose.waitForIdle()
    }

    private fun affiche(res: Int) =
        compose.onAllNodesWithContentDescription(app.getString(res)).fetchSemanticsNodes().isNotEmpty()

    @Test fun `la carte garde les commandes affichees par defaut`() {
        ecran()
        assertTrue("le burger", affiche(R.string.action_menu))
        assertTrue("le bouton GPS", affiche(R.string.content_desc_gps_position))
        assertTrue("l'itineraire", affiche(R.string.planner_title))
        assertTrue("le gestionnaire de fonds", affiche(R.string.content_desc_basemap_control))
    }

    /**
     * L'autre moitie, et elle compte autant.
     *
     * Tout afficher par precaution serait la faute inverse : une carte qui propose, le temps d'une
     * ouverture de base, des fonctions que personne n'a demandees - et qui les retire ensuite.
     */
    @Test fun `ce qui est eteint par defaut reste absent`() {
        ecran()
        assertFalse("la recherche de lieu", affiche(R.string.content_desc_geocode_search))
        assertFalse("la regle", affiche(R.string.measure_title))
        assertFalse("le crayon", affiche(R.string.edit_toolbar))
        assertFalse("les points d'interet", affiche(R.string.poi_layer_title))
        assertFalse("la cloche d'alerte", affiche(R.string.content_desc_off_track_alert))
    }
}
