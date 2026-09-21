package fr.lc4918.trailog.ui.alert

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.domain.geo.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le tableau de bord, cote ecran : les champs qui s'affichent, la cloche qui n'apparait que sur une trace,
 * et la remise a zero qui demande confirmation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class DashboardUiTest {

    @get:Rule val compose = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val trip = Trip(distanceM = 12_345.0, ascentM = 640.0, descentM = 210.0, movingMs = 108 * 60_000L)

    private val progress = FollowProgress(null, 0.0, 3_200.0, 0.0, 0.0, 180.0, 90.0, 0L, null)

    private fun tableau(
        progress: FollowProgress? = null,
        trackName: String? = null,
        armed: Boolean = false,
        hidden: Set<DashboardField> = emptySet(),
        onBell: () -> Unit = {},
        onReset: () -> Unit = {},
    ) = compose.setContent {
        Dashboard(
            trip = trip, speedMps = 2.5f, progress = progress, trackName = trackName, armed = armed,
            alerting = false, hidden = hidden, imperial = false, bg = Color.White, fg = Color.Black,
            onBell = onBell, onReset = onReset,
        )
    }

    private fun texte(res: Int) = ctx.getString(res)

    @Test fun `hors trace, les compteurs de la sortie s'affichent`() {
        tableau()
        compose.onNodeWithText(Format.speedFixed(2.5)).assertIsDisplayed()
        compose.onNodeWithText("1:48").assertIsDisplayed()
        compose.onNodeWithText("640 m").assertIsDisplayed()
        compose.onNodeWithText("210 m").assertIsDisplayed()
        compose.onNodeWithText(texte(R.string.dash_remaining)).assertDoesNotExist()
    }

    /** Hors trace, pas de cloche : il n'y a rien dont s'ecarter. */
    @Test fun `hors trace, ni nom ni cloche`() {
        tableau()
        compose.onNodeWithTag("dashboard_bell").assertDoesNotExist()
        compose.onNodeWithTag("dashboard_track").assertDoesNotExist()
    }

    @Test fun `sur une trace, le restant et la cloche s'ajoutent`() {
        tableau(progress = progress, trackName = "GR 9")
        compose.onNodeWithText("GR 9").assertIsDisplayed()
        compose.onNodeWithText(Format.shortDistance(3_200.0)).assertIsDisplayed()
        compose.onNodeWithText("180 m").assertIsDisplayed()
        compose.onNodeWithText("90 m").assertIsDisplayed()
        // 12,3 km en 1 h 48 de mouvement : les 3,2 km restants demandent un peu moins de 28 minutes.
        compose.onNodeWithText("0:27").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_bell").assertIsDisplayed()
    }

    @Test fun `la cloche repond`() {
        var touchee = false
        tableau(progress = progress, trackName = "GR 9", onBell = { touchee = true })
        compose.onNodeWithTag("dashboard_bell").performClick()
        assertTrue(touchee)
    }

    @Test fun `un champ masque ne s'affiche pas`() {
        tableau(hidden = setOf(DashboardField.SPEED))
        compose.onNodeWithText(texte(R.string.dash_speed)).assertDoesNotExist()
        compose.onNodeWithText(texte(R.string.dash_distance)).assertIsDisplayed()
    }

    /** La remise a zero demande confirmation : une sortie effacee d'un doigt qui glisse ne se retrouve pas. */
    @Test fun `la remise a zero demande confirmation`() {
        var remis = 0
        tableau(onReset = { remis++ })
        compose.onNodeWithTag("dashboard_reset").performClick()
        assertEquals("rien avant la confirmation", 0, remis)
        compose.onNodeWithText(texte(R.string.action_cancel)).performClick()
        assertEquals(0, remis)
        compose.onNodeWithTag("dashboard_reset").performClick()
        compose.onNodeWithText(texte(R.string.dash_reset)).performClick()
        assertEquals(1, remis)
    }

    // ---------- Les champs masques, en base ----------

    @Test fun `la colonne des champs masques se lit et s'ecrit`() {
        assertEquals(emptySet<DashboardField>(), DashboardField.hidden(""))
        val un = DashboardField.withHidden("", DashboardField.DURATION, hide = true)
        assertEquals("duration", un)
        val deux = DashboardField.withHidden(un, DashboardField.SPEED, hide = true)
        assertEquals("dans l'ordre des champs", "speed,duration", deux)
        assertEquals("duration", DashboardField.withHidden(deux, DashboardField.SPEED, hide = false))
        assertEquals("une cle inconnue s'ignore", setOf(DashboardField.SPEED), DashboardField.hidden("speed,vieux"))
    }

    /** Deux rangees : la sortie, puis ce qu'il reste de la trace suivie. */
    @Test fun `les champs se repartissent en deux rangees`() {
        assertEquals(
            listOf(DashboardField.SPEED, DashboardField.DISTANCE, DashboardField.DURATION,
                DashboardField.ASCENT, DashboardField.DESCENT),
            DashboardField.shown(emptySet(), onTrack = false),
        )
        assertEquals(
            listOf(DashboardField.REMAINING, DashboardField.REMAINING_TIME,
                DashboardField.REMAINING_ASCENT, DashboardField.REMAINING_DESCENT),
            DashboardField.shown(emptySet(), onTrack = true),
        )
    }

    // ---------- La vitesse et le temps restant ----------

    /** Les positions cessent d'arriver : la derniere vitesse de marche ne reste pas affichee. */
    @Test fun `une position vieillie ramene la vitesse a zero`() {
        assertEquals(2f, DashboardMath.speed(2f, null, 1_000L, null)!!, 0f)
        assertEquals(0f, DashboardMath.speed(2f, null, DashboardMath.STALE_FIX_MS + 1, null)!!, 0f)
    }

    /** A pied, une vitesse lente mais sure se lit telle quelle : elle n'est pas ramenee a zero. */
    @Test fun `une vitesse lente reste affichee`() {
        assertEquals(0.3f, DashboardMath.speed(0.3f, 0.1f, 1_000L, 2_000L)!!, 0f)
    }

    /** Une vitesse plus petite que sa propre incertitude n'est que du bruit. */
    @Test fun `une vitesse sous son incertitude vaut zero`() {
        assertEquals(0f, DashboardMath.speed(0.8f, 1.0f, 1_000L, 2_000L)!!, 0f)
    }

    /** Telephone pose sur une table : le capteur annonce des km/h, mais on n'a pas bouge depuis 20 s. */
    @Test fun `sans deplacement depuis un moment, la vitesse vaut zero`() {
        assertEquals(0f, DashboardMath.speed(1.2f, null, 1_000L, DashboardMath.STILL_MS + 1)!!, 0f)
    }

    @Test fun `sans position, pas de vitesse`() {
        assertEquals(null, DashboardMath.speed(null, null, null, null))
    }

    // ---------- le corps des compteurs ----------

    /**
     * Chaque champ porte SA taille : celle qu'on regle pour la vitesse ne touche pas aux autres.
     *
     * C'est la demande qui a remplace le corps unique - ce qu'on veut lire d'un coup d'oeil sur un
     * guidon n'est pas la meme chose d'un champ a l'autre, et un corps unique obligeait a choisir
     * pour tous.
     */
    @Test fun `la taille d'un champ ne touche pas a celle des autres`() {
        val csv = DashboardField.withFontSize("", DashboardField.SPEED, 32)
        assertEquals("speed:32", csv)
        val tailles = DashboardField.fontSizes(csv)
        assertEquals(32, tailles[DashboardField.SPEED])
        assertEquals(null, tailles[DashboardField.DISTANCE])
    }

    /** Remise a la taille par defaut : l'entree disparait plutot que de porter la valeur d'origine. */
    @Test fun `revenir a la taille par defaut retire l'entree`() {
        val csv = DashboardField.withFontSize("speed:32,ascent:12", DashboardField.SPEED, DashboardFontDefaultSp)
        assertEquals("ascent:12", csv)
    }

    /** Une base abimee ne doit pas rendre un compteur illisible ni demesure. */
    @Test fun `une taille aberrante se ramene dans ses bornes`() {
        val tailles = DashboardField.fontSizes("speed:999,distance:1,duration:abc,inconnu:20,ascent")
        assertEquals(DashboardFontMaxSp, tailles[DashboardField.SPEED])
        assertEquals(DashboardFontMinSp, tailles[DashboardField.DISTANCE])
        assertEquals(null, tailles[DashboardField.DURATION])
        assertEquals(null, tailles[DashboardField.ASCENT])
    }

    /**
     * Le corps se regle, et le panneau suit.
     *
     * **Ce que ce test protege.** Les compteurs se resserrent d'eux-memes pour tenir dans leur colonne
     * (cf. FitText). Si la rangee ne savait pas passer a la ligne, un corps regle plus grand serait
     * aussitot repris par ce resserrement : le reglage existerait en base, dans l'ecran des reglages, et
     * ne se verrait nulle part. La hauteur du panneau est la preuve observable qu'il agit.
     */
    @Test fun `un corps plus grand fait grandir le panneau, sans perdre de champ`() {
        var corps by mutableIntStateOf(DashboardFontDefaultSp)
        compose.setContent {
            Dashboard(
                trip = trip, speedMps = 2.5f, progress = null, trackName = null, armed = false,
                alerting = false, hidden = emptySet(),
                fontSizes = DashboardField.entries.associateWith { corps },
                imperial = false, bg = Color.White, fg = Color.Black, onBell = {}, onReset = {},
            )
        }
        val petit = compose.onNodeWithTag("dashboard").fetchSemanticsNode().size.height
        corps = 40
        compose.waitForIdle()
        val grand = compose.onNodeWithTag("dashboard").fetchSemanticsNode().size.height
        assertTrue("le panneau grandit avec le corps ($petit -> $grand)", grand > petit)
        // Passer a la ligne ne perd aucun champ : ils descendent, ils ne disparaissent pas.
        compose.onNodeWithText("640 m").assertExists()
        compose.onNodeWithText("210 m").assertExists()
        compose.onNodeWithText("1:48").assertExists()
    }

    /** Le restant, a la vitesse moyenne en mouvement : 6 km en 1 h, il en reste 3, soit 30 min. */
    @Test fun `le temps restant suit la moyenne en mouvement`() {
        val t = Trip(distanceM = 6_000.0, movingMs = 3_600_000L)
        assertEquals(1_800_000L, DashboardMath.etaMs(t, 3_000.0))
    }

    /** Moins d'une minute de mouvement : la moyenne ne dit encore rien. */
    @Test fun `pas de temps restant sur une moyenne trop courte`() {
        assertEquals(null, DashboardMath.etaMs(Trip(distanceM = 50.0, movingMs = 30_000L), 3_000.0))
    }
}
