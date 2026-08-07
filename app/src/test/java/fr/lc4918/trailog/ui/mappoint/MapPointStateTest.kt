package fr.lc4918.trailog.ui.mappoint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transitions du point designe par un appui long. Ce sont elles, et non les valeurs, qui portent le risque :
 * une mesure oubliee au changement de point affiche une distance calculee vers un autre endroit, et une
 * epingle qui survit a son infobulle ne se retire plus par aucun geste.
 */
class MapPointStateTest {

    private fun opened() = MapPointState().apply { open(6.08, 44.56) }

    @Test fun `un appui long pose le point et cherche son adresse`() {
        val s = opened()
        assertEquals(6.08 to 44.56, s.point)
        assertEquals(AddressState.Loading, s.address)
        assertTrue(s.bubbleVisible)
        assertEquals(listOf(6.08 to 44.56), s.markers)
    }

    /** Les distances valaient pour l'endroit precedent : les garder les afficherait, telles quelles, a cote
     *  des boutons d'un autre point. L'adresse repart de meme en recherche. */
    @Test fun `un nouveau point efface les mesures du precedent`() {
        val s = opened()
        s.publishAddress(AddressState.Done("Gap, France"))
        s.requestDistanceFromPosition()
        s.publishPositionMeasure(MeasureState.Done(1000.0, 300.0))
        s.chooseRefPoint(5.72, 45.18)
        s.publishPointMeasure(MeasureState.Done(2000.0, 600.0))

        s.open(3.0, 48.0)

        assertEquals(3.0 to 48.0, s.point)
        assertEquals(AddressState.Loading, s.address)
        assertNull(s.positionMeasure)
        assertNull(s.pointMeasure)
        assertNull(s.refPoint)
        assertNull(s.positionOrigin)
        assertTrue(s.routeTracks.isEmpty())
    }

    /** L'infobulle recouvrirait la carte ou l'on doit viser : elle s'efface le temps du choix, sans que le
     *  point, lui, ne bouge. */
    @Test fun `choisir un point de reference masque l'infobulle puis la rend`() {
        val s = opened()
        s.startPickingPoint()
        assertFalse(s.bubbleVisible)
        assertEquals(6.08 to 44.56, s.point)

        s.chooseRefPoint(5.72, 45.18)
        assertTrue(s.bubbleVisible)
        assertFalse(s.pickingPoint)
        assertEquals(MeasureState.Loading, s.pointMeasure)
        assertEquals(listOf(6.08 to 44.56, 5.72 to 45.18), s.markers)
    }

    /** Abandonner le choix rend l'infobulle sans lancer de mesure : rien n'a ete pose. */
    @Test fun `abandonner le choix ne mesure rien`() {
        val s = opened()
        s.startPickingPoint()
        s.cancelPickingPoint()
        assertTrue(s.bubbleVisible)
        assertNull(s.refPoint)
        assertNull(s.pointMeasure)
    }

    /**
     * L'origine de la mesure depuis la position est figee a la premiere position recue : le capteur en
     * livre une toutes les 2 s, et suivre le capteur lancerait autant de requetes d'itineraire.
     */
    @Test fun `l'origine depuis la position ne se fige qu'une fois`() {
        val s = opened()
        s.requestDistanceFromPosition()
        s.fixPositionOrigin(44.0, 6.0)
        s.fixPositionOrigin(44.1, 6.1)
        assertEquals(44.0 to 6.0, s.positionOrigin)
    }

    /** Hors demande, une position recue ne doit rien declencher : sans cela le capteur allume mesurerait
     *  tout seul vers chaque point designe. */
    @Test fun `une position recue sans demande ne fige aucune origine`() {
        val s = opened()
        s.fixPositionOrigin(44.0, 6.0)
        assertNull(s.positionOrigin)
    }

    /** Une mesure aboutie qui n'a pas rendu de geometrie reste chiffree, mais ne pose rien sur la carte :
     *  un trace d'un seul point n'est pas une ligne. */
    @Test fun `seules les mesures tracables se posent sur la carte`() {
        val s = opened()
        s.publishPositionMeasure(MeasureState.Done(1000.0, 300.0, null))
        s.publishPointMeasure(MeasureState.Failed)
        assertTrue(s.routeTracks.isEmpty())
    }

    /** La croix de l'infobulle ne laisse ni epingle, ni trace, ni mesure : rien ne les expliquerait plus. */
    @Test fun `fermer ne laisse rien derriere`() {
        val s = opened()
        s.requestDistanceFromPosition()
        s.chooseRefPoint(5.72, 45.18)

        s.clear()

        assertNull(s.point)
        assertNull(s.refPoint)
        assertNull(s.positionMeasure)
        assertNull(s.pointMeasure)
        assertFalse(s.pickingPoint)
        assertFalse(s.bubbleVisible)
        assertTrue(s.markers.isEmpty())
    }

    /** Cle de relance des traces posees sur la carte : elle doit avancer a chaque mesure publiee, sans quoi
     *  l'itineraire affiche resterait celui d'avant. */
    @Test fun `le numero d'ordre des mesures avance a chaque publication`() {
        val s = opened()
        val start = s.measureRevision
        s.publishPositionMeasure(MeasureState.Loading)
        s.publishPointMeasure(MeasureState.Failed)
        assertTrue(s.measureRevision > start + 1)
    }
}
