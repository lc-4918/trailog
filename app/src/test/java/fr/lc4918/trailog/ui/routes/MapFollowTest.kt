package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.ui.planner.RoutePlannerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suivi de la position par la carte.
 *
 * Deux fautes possibles, aussi genantes l'une que l'autre : une carte qui revient sous les doigts pendant
 * qu'on la deplace, et une carte qui ne revient jamais. Aucune des deux ne leve quoi que ce soit.
 */
class MapFollowTest {

    /** Le cas nominal : reglage actif, capteur en marche, rien d'ouvert par-dessus la carte. */
    private fun suit(
        enabled: Boolean = true,
        gpsActive: Boolean = true,
        resumed: Boolean = true,
        plannerExpanded: Boolean = false,
        layerOpen: Boolean = false,
        bubbleOpen: Boolean = false,
    ) = MapFollow.follows(enabled, gpsActive, resumed, plannerExpanded, layerOpen, bubbleOpen)

    @Test fun `le suivi demande le reglage et le capteur`() {
        assertTrue(suit())
        assertFalse("reglage eteint", suit(enabled = false))
        assertFalse("capteur arrete", suit(gpsActive = false))
    }

    /**
     * **La carte doit etre DEVANT l'utilisateur**, et ce cas vient du terrain lui aussi.
     *
     * Le suivi vit dans la composition, et celle-ci ne s'arrete ni quand l'ecran s'eteint, ni quand
     * l'application passe derriere une autre, ni au retour au bureau d'accueil. La `MapView`, elle,
     * recoit `onPause()` : le suivi continuait donc a lui demander une animation de camera par position
     * recue - une par seconde, indefiniment - sur une carte qui ne dessine plus.
     */
    @Test fun `l'ecran endormi ou l'application derriere suspendent le suivi`() {
        assertFalse(suit(resumed = false))
    }

    /** ... et le retour au premier plan le rend, sans que rien d'autre n'ait a etre refait. */
    @Test fun `le retour au premier plan rend le suivi`() {
        assertTrue(suit(resumed = true))
    }

    /** Les ecrans qui se servent de la carte ailleurs qu'a l'endroit ou l'on se tient. */
    @Test fun `le planificateur et le profil d'une trace suspendent le suivi`() {
        assertFalse("bande du planificateur deployee", suit(plannerExpanded = true))
        assertFalse("profil d'une trace ouvert", suit(layerOpen = true))
    }

    /**
     * **La bande REDUITE ne suspend plus rien**, et ce test vient du terrain.
     *
     * Le suivi lisait `planner.open`, qui reste vrai une fois la bande repliee - c'est meme tout l'objet
     * du repli, garder le trajet en rendant la carte. Or c'est exactement l'etat dans lequel on roule en
     * suivant un parcours calcule : suivi automatique allume, et la carte qui ne se recentrait jamais.
     *
     * Les deux notions passent donc par [RoutePlannerState.expanded], et le test les eprouve ENSEMBLE :
     * la regle seule ne peut pas attraper une faute qui est dans ce qu'on lui donne a lire.
     */
    @Test fun `un planificateur reduit laisse la carte suivre la position`() {
        val reduit = RoutePlannerState().apply { openPlanner(); collapse(true) }
        assertTrue("un trajet existe toujours", reduit.open)
        assertFalse("mais la bande n'occupe plus l'ecran", reduit.expanded)
        assertTrue(suit(plannerExpanded = reduit.expanded))
    }

    /** Deployee, elle le suspend toujours : composer un trajet avec une carte qui revient sur soi toutes
     *  les cinq secondes est impossible. */
    @Test fun `un planificateur deploye suspend toujours le suivi`() {
        val deploye = RoutePlannerState().apply { openPlanner() }
        assertTrue(deploye.expanded)
        assertFalse(suit(plannerExpanded = deploye.expanded))
    }

    /**
     * Une infobulle decrit un point precis de la carte : la recentrer sur la position emporterait ce point
     * hors de l'ecran, et l'infobulle avec, pendant qu'on la lit.
     */
    @Test fun `une infobulle ouverte suspend le suivi`() {
        assertFalse("infobulle ouverte", suit(bubbleOpen = true))
        assertTrue("infobulle refermee", suit(bubbleOpen = false))
    }

    @Test fun `sans geste, la carte suit sans attendre`() {
        assertEquals(0L, MapFollow.waitMs(now = 10_000L, lastGestureAt = 0L))
    }

    @Test fun `un geste recent fait attendre ce qu'il reste du silence`() {
        assertEquals(3_000L, MapFollow.waitMs(now = 12_000L, lastGestureAt = 10_000L, delayMs = 5_000L))
        assertEquals(5_000L, MapFollow.waitMs(now = 10_000L, lastGestureAt = 10_000L, delayMs = 5_000L))
    }

    /**
     * L'attente est ce qu'il RESTE, non le delai entier : les positions arrivent environ une par seconde,
     * et repartir de zero a chacune ferait reculer l'echeance indefiniment.
     */
    @Test fun `l'attente decroit a mesure que les positions arrivent`() {
        val geste = 10_000L
        val attentes = (1..6).map { MapFollow.waitMs(now = geste + it * 1_000L, geste, delayMs = 5_000L) }
        assertEquals(listOf(4_000L, 3_000L, 2_000L, 1_000L, 0L, 0L), attentes)
    }

    @Test fun `un geste ancien n'attend plus`() {
        assertEquals(0L, MapFollow.waitMs(now = 100_000L, lastGestureAt = 10_000L, delayMs = 5_000L))
    }

    /**
     * Fermeture d'une infobulle : l'ecran la releve comme un geste, et le silence repart entier. Sans quoi
     * une infobulle lue longuement laissait un dernier geste vieux de plusieurs minutes, et la carte
     * sautait sur la position a l'instant meme ou l'on refermait la bulle.
     */
    @Test fun `la fermeture d'une infobulle fait repartir le silence entier`() {
        val fermeture = 100_000L
        assertEquals(5_000L, MapFollow.waitMs(now = fermeture, lastGestureAt = fermeture, delayMs = 5_000L))
        assertEquals(0L, MapFollow.waitMs(now = fermeture + 5_000L, fermeture, delayMs = 5_000L))
    }

    /** Horloge qui recule : le suivi ne doit pas rester suspendu pour l'eternite. */
    @Test fun `un geste dans le futur ne bloque pas le suivi`() {
        assertEquals(0L, MapFollow.waitMs(now = 5_000L, lastGestureAt = 10_000L, delayMs = 5_000L))
    }

    // ---------- Le bouton de suivi, sur la carte ----------

    /**
     * Le bouton fait toujours ce qui MANQUE : il suit, il ramene, ou il rend la carte.
     *
     * Le testeur devait ouvrir le menu, les reglages et l'onglet Carte pour couper un centrage qui le
     * genait - pour un geste qu'on fait chaque fois qu'on veut lire la carte ailleurs qu'a l'endroit ou
     * l'on se tient.
     */
    @Test fun `le suivi eteint, l'appui l'allume`() {
        assertEquals(MapFollow.FollowTap.ARM, MapFollow.tapAction(following = false, positionCentered = false))
        assertEquals(MapFollow.FollowTap.ARM, MapFollow.tapAction(following = false, positionCentered = true))
    }

    /**
     * Suivi allume, position pas encore revenue : l'appui ABREGE le silence d'apres-geste, et ne touche
     * pas au reglage.
     *
     * C'est le geste que l'ancien bouton de recentrage savait faire, et le seul qu'une simple bascule
     * aurait emporte : sans lui, revenir tout de suite demandait d'eteindre puis de rallumer - deux
     * appuis qui traversent l'etat "eteint" pour ne rien changer au bout du compte.
     */
    @Test fun `le suivi allume et la position hors centre, l'appui recentre`() {
        assertEquals(MapFollow.FollowTap.RECENTER, MapFollow.tapAction(following = true, positionCentered = false))
    }

    /** Suivi allume et position deja centree : il n'y a plus rien a demander, l'appui rend la carte. */
    @Test fun `le suivi allume et la position centree, l'appui eteint`() {
        assertEquals(MapFollow.FollowTap.DISARM, MapFollow.tapAction(following = true, positionCentered = true))
    }
}
