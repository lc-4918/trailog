package fr.lc4918.trailog.ui.alert

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
                followed = null, imperial = false, onPick = {}, onStop = {}, onDismiss = {},
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
                followed = null, imperial = false, onPick = {}, onStop = {}, onDismiss = {},
            )
        }
        compose.onNodeWithText(ctx.getString(R.string.alert_track_segment, "Traversee", 2, 3))
            .assertIsDisplayed()
    }

    @Test fun `un tap dans la liste vaut choix`() {
        var choisie: TrackCandidate? = null
        val c = candidate(1, "GR 9", 30.0)
        compose.setContent {
            TrackChooserDialog(listOf(c), null, false, onPick = { choisie = it }, onStop = {}, onDismiss = {})
        }
        compose.onNodeWithText("GR 9").performClick()
        assertEquals(c, choisie)
    }

    /**
     * La trace qu'on suit DEJA se distingue des autres.
     *
     * La question posee en ouvrant cette liste est "laquelle est-ce que je suis en ce moment ?", et une
     * simple graisse de caractere n'y repondait pas : sur huit lignes qui portent le meme genre de nom,
     * celle en gras se cherche. Le mot est la marque qui compte ici - c'est la seule que lit une synthese
     * vocale, et la seule qu'un test peut voir : ni l'aplat ni la cloche ne portent de texte.
     */
    @Test fun `la trace suivie se distingue dans la liste`() {
        val suivie = TrackWatch.Followed(1, "GR 9", 0, 1, emptyList())
        compose.setContent {
            TrackChooserDialog(
                candidates = listOf(candidate(1, "GR 9", 30.0), candidate(2, "Boucle du lac", 240.0)),
                followed = suivie, imperial = false, onPick = {}, onStop = {}, onDismiss = {},
            )
        }
        compose.onNodeWithText(ctx.getString(R.string.alert_track_following)).assertIsDisplayed()
    }

    /** Une seule ligne la porte : la marque ne vaut rien si toutes l'ont. */
    @Test fun `la marque ne va qu'a la trace suivie`() {
        val suivie = TrackWatch.Followed(1, "GR 9", 0, 1, emptyList())
        compose.setContent {
            TrackChooserDialog(
                candidates = listOf(candidate(1, "GR 9", 30.0), candidate(2, "Boucle du lac", 240.0)),
                followed = suivie, imperial = false, onPick = {}, onStop = {}, onDismiss = {},
            )
        }
        assertEquals(1, compose.onAllNodesWithText(ctx.getString(R.string.alert_track_following))
            .fetchSemanticsNodes().size)
    }

    /** Rien de suivi : personne ne porte la marque. */
    @Test fun `sans suivi, aucune trace n'est marquee`() {
        compose.setContent {
            TrackChooserDialog(
                candidates = listOf(candidate(1, "GR 9", 30.0)),
                followed = null, imperial = false, onPick = {}, onStop = {}, onDismiss = {},
            )
        }
        compose.onNodeWithText(ctx.getString(R.string.alert_track_following)).assertDoesNotExist()
    }

    /** Le bouton d'arret n'existe que si l'on suit deja quelque chose : sinon il n'aurait rien a arreter. */
    @Test fun `l'arret n'apparait que pendant un suivi`() {
        compose.setContent {
            TrackChooserDialog(emptyList(), null, false, onPick = {}, onStop = {}, onDismiss = {})
        }
        compose.onNodeWithText(ctx.getString(R.string.alert_stop_following)).assertDoesNotExist()
    }

    @Test fun `l'arret repond pendant un suivi`() {
        var arrete = false
        val suivie = TrackWatch.Followed(1, "GR 9", 0, 1, emptyList())
        compose.setContent {
            TrackChooserDialog(emptyList(), suivie, false, onPick = {}, onStop = { arrete = true }, onDismiss = {})
        }
        compose.onNodeWithText(ctx.getString(R.string.alert_stop_following)).performClick()
        assertTrue(arrete)
    }

    /** Aucune trace a proximite : on le dit, plutot que de laisser une boite vide. */
    @Test fun `une liste vide s'explique`() {
        compose.setContent {
            TrackChooserDialog(emptyList(), null, false, onPick = {}, onStop = {}, onDismiss = {})
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
                followed = null, imperial = false,
                onPick = { etat.follow(it, thresholdM = 100.0) }, onStop = {}, onDismiss = {},
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
