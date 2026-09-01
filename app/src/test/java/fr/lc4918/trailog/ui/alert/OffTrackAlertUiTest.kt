package fr.lc4918.trailog.ui.alert

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.location.TrackWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L'alerte d'eloignement, cote ecran : la banniere et le choix de la trace a suivre.
 *
 * Ce que la mesure decide est teste ailleurs, sans Android (cf. `TrackWatchTest`). Ce qui se verifie ici
 * est l'autre moitie, celle qu'aucun test de domaine n'atteint : que la distance et le nom de la trace
 * arrivent bien sous les yeux, et que les gestes de la boite de dialogue menent ou ils annoncent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class OffTrackAlertUiTest {

    @get:Rule val compose = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun candidate(id: Long, nom: String, away: Double, index: Int = 0, count: Int = 1) =
        TrackCandidate(id, nom, index, count, away, listOf(Sample(0.0, 0.0, 0.0, null, 5.7, 45.2)))

    // ---------- La banniere ----------

    @Test fun `la banniere dit l'ecart et la trace`() {
        compose.setContent { OffTrackAlertBar("GR 9", awayM = 120.0, imperial = false, onClose = {}) }
        compose.onNodeWithText(ctx.getString(R.string.alert_off_track_banner, "120 m", "GR 9"))
            .assertIsDisplayed()
    }

    /** Les unites imperiales valent ici comme partout : une alerte en metres a qui a regle des miles
     *  serait la seule mesure de l'application a ne pas suivre le reglage. */
    @Test fun `la banniere suit les unites reglees`() {
        compose.setContent { OffTrackAlertBar("GR 9", awayM = 120.0, imperial = true, onClose = {}) }
        val attendu = ctx.getString(
            R.string.alert_off_track_banner, Format.shortDistance(120.0, imperial = true), "GR 9",
        )
        compose.onNodeWithText(attendu).assertIsDisplayed()
        compose.onNodeWithText(
            ctx.getString(R.string.alert_off_track_banner, "120 m", "GR 9"),
        ).assertDoesNotExist()
    }

    @Test fun `la croix de la banniere repond`() {
        var tue = false
        compose.setContent { OffTrackAlertBar("GR 9", 120.0, false, onClose = { tue = true }) }
        compose.onNode(androidx.compose.ui.test.hasContentDescription(ctx.getString(R.string.action_close)))
            .performClick()
        assertTrue(tue)
    }

    // ---------- Le choix d'une trace ----------

    @Test fun `les traces proposees portent leur ecart`() {
        compose.setContent {
            TrackChooserDialog(
                candidates = listOf(candidate(1, "GR 9", 30.0), candidate(2, "Boucle du lac", 240.0)),
                followed = null, imperial = false, soundEnabled = true, progress = null, onPick = {}, onStop = {}, onDismiss = {},
            )
        }
        compose.onNodeWithText("GR 9").assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(R.string.alert_track_away, "30 m")).assertIsDisplayed()
        compose.onNodeWithText("Boucle du lac").assertIsDisplayed()
    }

    /** Une couche qui porte plusieurs segments les numerote : sans cela, trois lignes porteraient le meme
     *  nom et le choix ne voudrait rien dire. */
    @Test fun `les segments d'une meme couche sont numerotes`() {
        compose.setContent {
            TrackChooserDialog(
                candidates = listOf(candidate(1, "Traversee", 30.0, index = 1, count = 3)),
                followed = null, imperial = false, soundEnabled = true, progress = null, onPick = {}, onStop = {}, onDismiss = {},
            )
        }
        compose.onNodeWithText(ctx.getString(R.string.alert_track_segment, "Traversee", 2, 3))
            .assertIsDisplayed()
    }

    @Test fun `un tap dans la liste vaut choix`() {
        var choisie: TrackCandidate? = null
        val c = candidate(1, "GR 9", 30.0)
        compose.setContent {
            TrackChooserDialog(listOf(c), null, false, true, progress = null, onPick = { choisie = it }, onStop = {}, onDismiss = {})
        }
        compose.onNodeWithText("GR 9").performClick()
        assertEquals(c, choisie)
    }

    /**
     * **Une trace suivie remplace la liste par un en-tete et un tableau de bord.**
     *
     * La liste restait affichee sous la ligne en surbrillance, c'est-a-dire que la popup consacrait tout
     * son espace a la seule chose dont on n'a plus besoin - choisir - quand la question devenue urgente
     * est "combien reste-t-il, et combien de montee".
     */
    @Test fun `une trace suivie remplace la liste par son en-tete`() {
        val suivie = TrackWatch.Followed(1, "GR 9", 0, 1, emptyList())
        compose.setContent {
            TrackChooserDialog(
                candidates = listOf(candidate(1, "GR 9", 30.0), candidate(2, "Boucle du lac", 240.0)),
                followed = suivie, imperial = false, soundEnabled = true, progress = progres(),
                onPick = {}, onStop = {}, onDismiss = {},
            )
        }
        compose.onNodeWithText("GR 9").assertIsDisplayed()
        compose.onNodeWithText("Boucle du lac").assertDoesNotExist()
        compose.onAllNodesWithText(ctx.getString(R.string.alert_track_away, "30 m")).assertCountEquals(0)
    }

    /** Le tableau de bord porte les neuf chiffres du suivi. */
    @Test fun `le tableau de bord affiche l'avancement`() {
        val suivie = TrackWatch.Followed(1, "GR 9", 0, 1, emptyList())
        compose.setContent {
            TrackChooserDialog(
                candidates = emptyList(), followed = suivie, imperial = false, soundEnabled = true,
                progress = progres(), onPick = {}, onStop = {}, onDismiss = {},
            )
        }
        compose.onNodeWithText("2,0 km").assertIsDisplayed()      // distance parcourue
        compose.onNodeWithText("3,0 km").assertIsDisplayed()      // distance restante
        compose.onNodeWithText("400 m").assertIsDisplayed()       // denivele positif restant
    }

    /** L'appui long sur un indicateur en donne le nom : neuf pictogrammes ne se devinent pas. */
    @Test fun `l'appui long nomme l'indicateur`() {
        val suivie = TrackWatch.Followed(1, "GR 9", 0, 1, emptyList())
        compose.setContent {
            TrackChooserDialog(
                candidates = emptyList(), followed = suivie, imperial = false, soundEnabled = true,
                progress = progres(), onPick = {}, onStop = {}, onDismiss = {},
            )
        }
        // Le libelle n'est pas la avant qu'on le demande : c'est ce qui distingue l'appui long d'une
        // legende permanente, que neuf indicateurs rendraient illisible.
        compose.onNodeWithText(ctx.getString(R.string.follow_done), substring = true).assertDoesNotExist()
        compose.onNodeWithText("2,0 km").performTouchInput { longClick() }
        compose.waitForIdle()
        // `assertExists` et non `assertIsDisplayed` : la ligne d'explication est sous la grille, et le
        // dialogue mesure dans un test peut la poser hors du cadre - ce n'est pas ce qu'on verifie ici.
        compose.onNodeWithText(ctx.getString(R.string.follow_done), substring = true).assertExists()
    }

    /** Sans suivi, la liste des traces est bien la : c'est le cas ou l'on vient choisir. */
    @Test fun `sans suivi, la liste des traces s'affiche`() {
        compose.setContent {
            TrackChooserDialog(
                candidates = listOf(candidate(1, "GR 9", 30.0), candidate(2, "Boucle du lac", 240.0)),
                followed = null, imperial = false, soundEnabled = true, progress = null,
                onPick = {}, onStop = {}, onDismiss = {},
            )
        }
        compose.onNodeWithText("GR 9").assertIsDisplayed()
        compose.onNodeWithText("Boucle du lac").assertIsDisplayed()
    }

    /** Un avancement de reference : 5 km de trace, 2 faits, 400 m de montee devant. */
    private fun progres() = fr.lc4918.trailog.ui.alert.FollowProgress(
        speedMps = 4f, doneM = 2_000.0, remainingM = 3_000.0,
        doneAscentM = 300.0, doneDescentM = 100.0,
        remainingAscentM = 400.0, remainingDescentM = 250.0,
        elapsedMs = 1_800_000L, etaMs = 2_700_000L,
    )

    /** Le bouton d'arret n'existe que si l'on suit deja quelque chose : sinon il n'aurait rien a arreter. */
    @Test fun `l'arret n'apparait que pendant un suivi`() {
        compose.setContent {
            TrackChooserDialog(emptyList(), null, false, true, progress = null, onPick = {}, onStop = {}, onDismiss = {})
        }
        compose.onNodeWithText(ctx.getString(R.string.alert_stop_following)).assertDoesNotExist()
    }

    @Test fun `l'arret repond pendant un suivi`() {
        var arrete = false
        val suivie = TrackWatch.Followed(1, "GR 9", 0, 1, emptyList())
        compose.setContent {
            TrackChooserDialog(emptyList(), suivie, false, true, progress = null, onPick = {}, onStop = { arrete = true }, onDismiss = {})
        }
        compose.onNodeWithText(ctx.getString(R.string.alert_stop_following)).performClick()
        assertTrue(arrete)
    }

    /** Aucune trace a proximite : on le dit, plutot que de laisser une boite vide. */
    @Test fun `une liste vide s'explique`() {
        compose.setContent {
            TrackChooserDialog(emptyList(), null, false, true, progress = null, onPick = {}, onStop = {}, onDismiss = {})
        }
        compose.onNodeWithText(ctx.getString(R.string.alert_pick_track_empty)).assertIsDisplayed()
    }

    // ---------- Le parcours du planificateur, suivi sans etre importe ----------

    /**
     * Le parcours qu'on vient de calculer se suit comme une trace de la bibliotheque.
     *
     * Il n'a pourtant aucune couche derriere lui : son identifiant est [PlannedRouteLayerId], et c'est ce
     * qui doit traverser intact le choix, la veille, et l'annonce de l'ecart. Importer d'abord serait un
     * detour, et laisserait derriere soi une couche dont on ne voulait pas.
     */
    @Test fun `le parcours en cours se suit comme une trace`() {
        val etat = OffTrackAlertState()
        val enCours = candidate(PlannedRouteLayerId, "Itineraire en cours", 40.0)
        compose.setContent {
            TrackChooserDialog(
                candidates = listOf(enCours, candidate(1, "GR 9", 300.0)),
                followed = null, imperial = false, soundEnabled = true,
                progress = null, onPick = { etat.follow(it, thresholdM = 100.0) }, onStop = {}, onDismiss = {},
            )
        }
        compose.onNodeWithText("Itineraire en cours").assertIsDisplayed()
        compose.onNodeWithText("Itineraire en cours").performClick()

        val suivie = TrackWatch.followed.value
        assertEquals(PlannedRouteLayerId, suivie?.layerId)
        assertEquals("Itineraire en cours", suivie?.layerName)
        assertEquals(enCours.samples, suivie?.samples)
        TrackWatch.stop()
    }

}
