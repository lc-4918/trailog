package fr.lc4918.trailog.ui.routes

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.location.LocationHub
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.MapSurface
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L'ecran de carte entier, compose pour de vrai.
 *
 * **Ce qui n'etait pas teste, et pourquoi.** `MainScreen` ne calcule presque rien : il CABLE. Les reglages
 * aux ornements de la carte, les gestes de la carte aux infobulles, les modes de saisie exclusifs entre
 * eux. Ce cablage ne vit nulle part ailleurs - aucun test de domaine ne peut le voir - et rien n'en etait
 * verifie, parce qu'un seul appel rendait les 1800 lignes incomposables sur la JVM : `MapLibreView`
 * construit une `MapView`, dont les bibliotheques natives n'existent pas ici.
 *
 * La surface de carte est donc passee en parametre (cf. `MapSurface`). Tout le reste est de production :
 * l'application, la base, le depot, les reglages, le ViewModel, les effets et les 1800 lignes de
 * composition.
 *
 * **Le fil qu'on tient a la place de MapLibre est [MapController]**, par lequel la carte parle deja a
 * l'ecran. La surface le recoit en parametre - c'est ainsi que la vraie s'y branche -, donc l'espion le
 * capture sans que l'ecran ait a l'exposer. Declencher `onLongPressEmpty` ici est exactement ce que fait
 * MapLibre sur un appui long.
 *
 * **Ce qui n'est pas teste** se dit aussi clairement. Le rendu des tuiles et les gestes reels, d'abord.
 * Ensuite tout ce qui s'ANCRE a un point de carte - les infobulles d'un marqueur, d'un lieu trouve, d'un
 * point designe : leur position vient de `controller.screenOf`, c'est-a-dire de la projection de MapLibre,
 * qui n'existe pas sans carte. L'appui long est bien recu ici, mais sa bulle n'a aucun endroit ou se
 * poser. Cette frontiere-la est reelle et non contournable : la falsifier reviendrait a tester une
 * projection inventee.
 *
 * Le geocodeur est pointe sur un port ferme pour que rien ne parte sur le reseau : ce qu'on eprouve est
 * le cablage, pas le service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr", application = TestTrailogApp::class)
class MainScreenUiTest {

    /**
     * Une activite reelle, et non la seule surface de composition : le retour Android passe par son
     * `OnBackPressedDispatcher`, et c'est lui que les `BackHandler` de l'ecran interceptent (cf.
     * `MapBackHandlers`). Sans activite, ce geste-la ne serait pas jouable.
     */
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<TestTrailogApp>()

    /** Une surface qui ne dessine rien, et retient le controleur que l'ecran lui confie. */
    private class SurfaceEspion : MapSurface {
        var controller: MapController? = null
        @Composable override fun Render(
            modifier: Modifier, controller: MapController,
            styleJson: String?, styleUrl: String?, onReady: () -> Unit,
        ) {
            SideEffect { this.controller = controller }
        }
    }

    private val surface = SurfaceEspion()

    /** Le controleur que l'ecran a reellement cable ; c'est par lui que la carte lui parle. */
    private val carte: MapController get() = requireNotNull(surface.controller)

    /**
     * Pose les reglages AVANT la composition, apres avoir laisse le semis finir.
     *
     * Le geocodeur est pointe sur un port ferme : la resolution inverse d'un appui long echoue sur place,
     * aucune requete ne sort de la machine.
     */
    private fun reglages(bloc: (SettingsEntity) -> SettingsEntity = { it }) = runBlocking {
        app.repository.ensureSeed()
        val actuels = app.repository.settings.get() ?: SettingsEntity()
        app.repository.settings.upsert(bloc(actuels).copy(geocodingUrl = "http://127.0.0.1:1"))
    }

    /** Le concentrateur est un objet de processus : chaque test repart de l'etat eteint. */
    @Before fun suiviEteint() { LocationHub.stopRequestedByUser() }

    private fun ecran() {
        compose.setContent { MaterialTheme { MainScreen(onSettings = {}, map = surface) } }
        compose.waitForIdle()
    }

    private fun libelle(res: Int) = app.getString(res)

    private fun affiche(res: Int) =
        compose.onAllNodesWithContentDescription(libelle(res)).fetchSemanticsNodes().isNotEmpty()

    private fun texte(res: Int) =
        compose.onAllNodesWithText(libelle(res)).fetchSemanticsNodes().isNotEmpty()

    /**
     * Reellement SOUS LES YEUX, et pas seulement present dans l'arbre.
     *
     * La distinction n'est pas byzantine ici : un tiroir ferme reste compose, pousse hors de l'ecran. Le
     * chercher par son existence le trouverait toujours, ouvert ou non.
     */
    private fun alEcran(res: Int) = runCatching {
        compose.onNodeWithContentDescription(libelle(res)).assertIsDisplayed(); true
    }.getOrDefault(false)

    /** Les reglages arrivent de la base par un Flow : l'ecran se recompose quand ils atterrissent. */
    private fun attend(condition: () -> Boolean) = compose.waitUntil(10_000, condition)

    // ---------- Les reglages commandent les ornements de la carte ----------

    /**
     * Chaque bouton de la carte est cable a SON reglage, et les deux tests qui suivent sont
     * COMPLEMENTAIRES : ce que le premier allume, le second l'eteint, un a un.
     *
     * Le defaut vise est le croisement de fils - la regle qui obeirait au reglage de la retouche, le
     * gestionnaire de fonds a celui de l'echelle. Un tout-allume suivi d'un tout-eteint ne le verrait pas :
     * deux boutons qui echangent leurs reglages apparaissent et disparaissent ensemble, donc toujours au
     * bon moment. Il faut que les voisins different pour que l'echange se voie. Ni l'un ni l'autre des
     * deux tests n'attrape seul le croisement ; ensemble, aucun n'y echappe.
     */
    @Test fun `chaque bouton suit son propre reglage`() {
        reglages {
            it.copy(
                sideMenuMode = "both", showGpsButton = true, geocodingEnabled = false,
                trackMeasureEnabled = true, trackEditEnabled = false, showBasemapControlButton = true,
            )
        }
        ecran()
        attend { affiche(R.string.content_desc_gps_position) }
        assertTrue("le burger", affiche(R.string.action_menu))
        assertTrue("la regle", affiche(R.string.measure_title))
        assertTrue("le gestionnaire de fonds", affiche(R.string.content_desc_basemap_control))
        assertFalse("la recherche de lieu", affiche(R.string.content_desc_geocode_search))
        assertFalse("le crayon de retouche", affiche(R.string.edit_toolbar))
    }

    /** Le complement exact du precedent : chaque reglage y vaut le contraire, chaque bouton aussi. */
    @Test fun `le complement des reglages donne le complement des boutons`() {
        reglages {
            it.copy(
                sideMenuMode = "both", showGpsButton = false, geocodingEnabled = true,
                trackMeasureEnabled = false, trackEditEnabled = true, showBasemapControlButton = false,
            )
        }
        ecran()
        attend { affiche(R.string.content_desc_geocode_search) }
        assertTrue("le burger reste : il ne depend d'aucun de ces reglages", affiche(R.string.action_menu))
        assertTrue("le crayon de retouche", affiche(R.string.edit_toolbar))
        assertFalse("le bouton GPS", affiche(R.string.content_desc_gps_position))
        assertFalse("la regle", affiche(R.string.measure_title))
        assertFalse("le gestionnaire de fonds", affiche(R.string.content_desc_basemap_control))
    }

    /** Le menu au seul glissement retire son bouton : sinon le burger doublerait un geste qui suffit. */
    @Test fun `le mode glissement retire le burger`() {
        reglages { it.copy(sideMenuMode = "swipe", showGpsButton = true) }
        ecran()
        attend { affiche(R.string.content_desc_gps_position) }
        assertFalse(affiche(R.string.action_menu))
    }

    // ---------- La carte parle a l'ecran ----------

    /**
     * Hors mode de saisie, la selection habituelle tient les taps.
     *
     * Quatre rappels, et c'est leur REPARTITION qui compte : un tap brut qui resterait pose apres la
     * sortie d'un mode volerait ses taps a la selection, sans que rien ne l'annonce a l'ecran.
     */
    @Test fun `hors mode, la selection habituelle tient les taps`() {
        reglages { it.copy(trackMeasureEnabled = true) }
        ecran()
        attend { surface.controller != null }
        assertNull("aucun tap brut detourne", carte.onRawTap)
        assertNotNull("le tap sur un marqueur", carte.onPickPoint)
        assertNotNull("le tap sur une trace", carte.onPickLine)
        assertNotNull("le tap sur le vide", carte.onTapEmpty)
    }

    /**
     * La mesure sur trace detourne TOUS les taps, y compris ceux qui tomberaient sur un marqueur.
     *
     * C'est la regle des modes exclusifs, et elle ne s'ecrit qu'ici. Un tap qui ouvrirait une infobulle
     * pendant qu'une bande demande un point de depart repondrait a cote de la question posee.
     */
    @Test fun `la mesure detourne tous les taps et rend la regle a sa bande`() {
        reglages { it.copy(trackMeasureEnabled = true) }
        ecran()
        attend { affiche(R.string.measure_title) }

        compose.onNodeWithContentDescription(libelle(R.string.measure_title)).performClick()
        attend { texte(R.string.measure_pick_start) }

        assertNotNull("les taps reviennent au mode", carte.onRawTap)
        assertFalse("la regle s'efface, sa bande porte la consigne", affiche(R.string.measure_title))

        // La croix de la bande rend les taps a la selection : le mode se referme entierement.
        compose.onNodeWithContentDescription(libelle(R.string.action_close)).performClick()
        attend { affiche(R.string.measure_title) }
        assertNull("plus aucun tap detourne", carte.onRawTap)
        assertNotNull("la selection a repris la main", carte.onTapEmpty)
    }

    /** L'appui long est bien cable : le controleur porte le rappel que l'ecran y a pose. Ce que la bulle
     *  DEVIENT ensuite ne se verifie pas ici, faute de projection (voir la note de classe). */
    @Test fun `l'appui long sur le vide revient a l'ecran`() {
        reglages()
        ecran()
        attend { surface.controller != null }
        assertNotNull(carte.onLongPressEmpty)
    }

    // ---------- L'arret subi du suivi de position ----------

    /**
     * Le suivi s'arrete tout seul : la carte le DIT.
     *
     * **Ce test vient du terrain.** Un testeur a fait vingt kilometres dans le mauvais sens parce que son
     * repere avait disparu sans un mot. L'application effacait le repere et se taisait ; la carte
     * continuait de s'afficher exactement comme avant.
     *
     * Aucun test de domaine ne peut voir cela : la regle est bien dans `LocationHub` et s'y teste (cf.
     * `LocationHubTest`), mais que l'ecran la porte SOUS LES YEUX ne se verifie qu'ici.
     */
    @Test fun `un arret subi du suivi s'affiche sur la carte`() {
        reglages { it.copy(showGpsButton = true) }
        ecran()
        attend { affiche(R.string.content_desc_gps_position) }
        assertFalse("rien tant que rien ne s'est arrete", texte(R.string.location_stopped_system))

        // Ce que fait le service quand il meurt sans que personne ne l'ait demande.
        compose.runOnUiThread {
            LocationHub.wantTracking()
            LocationHub.setTracking(false, LocationHub.StopReason.SYSTEM)
        }
        attend { texte(R.string.location_stopped_system) }
    }

    /** La localisation coupee dans le telephone se dit AUTREMENT : elle annonce sa propre reprise, la
     *  precedente non. */
    @Test fun `la localisation coupee se dit avec ses mots`() {
        reglages()
        ecran()
        attend { surface.controller != null }
        compose.runOnUiThread {
            LocationHub.wantTracking()
            LocationHub.setTracking(false, LocationHub.StopReason.SENSOR_OFF)
        }
        attend { texte(R.string.location_stopped_sensor) }
        assertFalse(texte(R.string.location_stopped_system))
    }

    /** Un arret DEMANDE ne s'annonce pas : une banniere apres chaque tap sur le bouton apprendrait a
     *  l'ignorer, et c'est la seule chose qu'elle ne doit pas devenir. */
    @Test fun `un arret demande n'affiche rien`() {
        reglages()
        ecran()
        attend { surface.controller != null }
        compose.runOnUiThread {
            LocationHub.wantTracking()
            LocationHub.stopRequestedByUser()
            LocationHub.setTracking(false, LocationHub.StopReason.USER)
        }
        compose.waitForIdle()
        assertFalse(texte(R.string.location_stopped_system))
        assertFalse(texte(R.string.location_stopped_sensor))
    }

    /** La croix retire l'annonce : elle a ete lue. */
    @Test fun `la croix de la banniere la retire`() {
        reglages()
        ecran()
        attend { surface.controller != null }
        compose.runOnUiThread {
            LocationHub.wantTracking()
            LocationHub.setTracking(false, LocationHub.StopReason.SYSTEM)
        }
        attend { texte(R.string.location_stopped_system) }
        compose.onNodeWithContentDescription(libelle(R.string.action_close)).performClick()
        attend { !texte(R.string.location_stopped_system) }
    }

    // ---------- Le calcul d'itineraire : un seul bouton, et deux appuis pour le perdre ----------

    /** Le geste du retour Android, tel que le systeme le remet a l'activite. */
    private fun retour() = compose.runOnUiThread {
        compose.activity.onBackPressedDispatcher.onBackPressed()
    }

    /**
     * Le calcul d'itineraire, pointe sur un moteur local.
     *
     * Un moteur PUBLIC ferait passer le bouton par sa garde de connexion (cf. ServiceUrl.needsInternet),
     * et le tap ouvrirait la boite "pas de connexion" au lieu de la bande. Une adresse de boucle locale
     * est traitee comme une instance auto-hebergee : le bouton ouvre, et la requete echoue sur place -
     * ce qu'on eprouve ici est le cablage, pas le moteur.
     */
    private fun calculLocal(reglage: SettingsEntity) =
        reglage.copy(routePlannerEnabled = true, routeEngine = "brouter",
            routingUrlBrouter = "http://127.0.0.1:1")

    private fun ouvreLeCalcul() {
        compose.onNodeWithContentDescription(libelle(R.string.planner_title)).performClick()
        attend { texte(R.string.planner_start) }
    }

    /**
     * **Un seul bouton d'itineraire a l'ecran**, jamais deux.
     *
     * La bande reduite posait auparavant son propre bouton au coin bas-gauche, pendant que le bouton
     * habituel s'effacait au coin bas-droit : un itineraire en cours en affichait donc un a chaque bout de
     * l'ecran, sans que rien ne dise lequel faisait quoi. C'est le meme bouton qui ouvre et qui rouvre, et
     * il ne bouge pas.
     */
    @Test fun `le meme bouton ouvre le calcul et rouvre celui qui est reduit`() {
        reglages { calculLocal(it) }
        ecran()
        attend { affiche(R.string.planner_title) }
        ouvreLeCalcul()
        assertFalse("le bouton s'efface sous sa propre bande", affiche(R.string.planner_title))

        // EN PLEIN MILIEU du bouton, et c'est le point du test : la bande de 24 dp du bord gauche qui
        // ouvre le menu au glissement recouvrait exactement ce centre-la, et prenait le tap.
        compose.onNodeWithContentDescription(libelle(R.string.planner_collapse)).performClick()
        attend { affiche(R.string.planner_title) }
        assertFalse("la bande s'est retiree de l'ecran", texte(R.string.planner_start))

        // Et il redeploie le trajet en cours, au lieu d'en commencer un autre.
        compose.onNodeWithContentDescription(libelle(R.string.planner_title)).performClick()
        attend { texte(R.string.planner_start) }
    }

    /**
     * Le retour Android replie d'abord, et DEMANDE ensuite.
     *
     * Il quittait le calcul d'un seul appui, emportant un trajet compose etape par etape - et c'est le
     * meme geste que celui qui quitte l'application, donc celui qu'on fait sans y penser.
     */
    @Test fun `le retour replie le calcul, puis demande avant de le perdre`() {
        reglages { calculLocal(it) }
        ecran()
        attend { affiche(R.string.planner_title) }
        ouvreLeCalcul()

        retour()
        attend { affiche(R.string.planner_title) }
        assertFalse("la bande s'est repliee", texte(R.string.planner_start))
        assertFalse("et rien n'est demande au premier appui", texte(R.string.planner_cancel_title))

        retour()
        attend { texte(R.string.planner_cancel_title) }
        assertTrue("deux lignes : ce qu'on perd, puis la question",
            texte(R.string.planner_cancel_question))

        // "Non" : la boite se referme sur un trajet intact, que le bouton redeploie.
        compose.onNodeWithText(libelle(R.string.action_no)).performClick()
        attend { !texte(R.string.planner_cancel_title) }
        compose.onNodeWithContentDescription(libelle(R.string.planner_title)).performClick()
        attend { texte(R.string.planner_start) }
    }

    /** La croix de l'en-tete, elle, ferme sans rien demander : c'est un geste vise, pose sur le bouton qui
     *  dit "fermer". */
    @Test fun `la croix du calcul ferme sans rien demander`() {
        reglages { calculLocal(it) }
        ecran()
        attend { affiche(R.string.planner_title) }
        ouvreLeCalcul()

        compose.onNodeWithContentDescription(libelle(R.string.action_close)).performClick()
        attend { !texte(R.string.planner_start) }
        assertFalse("aucune question posee", texte(R.string.planner_cancel_title))
        assertTrue("le bouton est revenu", affiche(R.string.planner_title))
    }

    /**
     * Le travail en cours vit dans le VIEWMODEL DE L'ACTIVITE, et non dans la composition.
     *
     * C'est ce qui le fait survivre a une rotation : `MainActivity` n'annonce aucun `configChanges`, un
     * quart de tour la recree, et un `remember` repartait de zero - l'itineraire compose etape par etape
     * s'en allait sans un mot, la ou le retour Android, lui, demande avant de le perdre.
     *
     * On ne tourne pas l'ecran ici : `setContent` pose le contenu sur l'activite du test, et le recreer
     * l'emporterait avec. Ce qui se verifie est la propriete qui compte et qui, elle, est a portee - que
     * l'etat que l'ecran vient de modifier soit bien celui du magasin de l'activite, seul endroit qui
     * traverse une recreation.
     */
    @Test fun `le trajet en cours vit dans le magasin de l'activite`() {
        reglages { calculLocal(it) }
        ecran()
        attend { affiche(R.string.planner_title) }
        ouvreLeCalcul()

        val garde = ViewModelProvider(compose.activity)[MapScreenStates::class.java]
        assertTrue("le planificateur ouvert par l'ecran est celui du magasin", garde.planner.open)
        assertFalse("et il est bien deploye", garde.planner.collapsed)
    }

    // ---------- Le menu lateral ----------

    /**
     * Le burger ouvre le menu, et le menu se compose.
     *
     * Le tiroir porte l'arborescence des couches, ses recherches et ses statistiques - plusieurs centaines
     * de lignes qui ne sont montees que depuis ici. Qu'il s'ouvre sans rien lever est deja ce qu'aucun
     * test ne disait.
     */
    @Test fun `le burger ouvre le menu lateral`() {
        reglages { it.copy(sideMenuMode = "both") }
        ecran()
        attend { affiche(R.string.action_menu) }
        assertFalse("ferme au depart", alEcran(R.string.search_placeholder))

        compose.onNodeWithContentDescription(libelle(R.string.action_menu)).performClick()
        attend { alEcran(R.string.search_placeholder) }
    }
}
