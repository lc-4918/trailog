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
        s.measures.requestDistanceFromPosition()
        s.measures.publishPositionMeasure(MeasureState.Done(1000.0, 300.0))
        s.measures.chooseRefPoint(5.72, 45.18)
        s.measures.publishPointMeasure(MeasureState.Done(2000.0, 600.0))

        s.open(3.0, 48.0)

        assertEquals(3.0 to 48.0, s.point)
        assertEquals(AddressState.Loading, s.address)
        assertNull(s.measures.positionMeasure)
        assertNull(s.measures.pointMeasure)
        assertNull(s.measures.refPoint)
        assertNull(s.measures.positionOrigin)
        assertTrue(s.measures.routeTracks.isEmpty())
    }

    /** L'infobulle recouvrirait la carte ou l'on doit viser : elle s'efface le temps du choix, sans que le
     *  point, lui, ne bouge. */
    @Test fun `choisir un point de reference masque l'infobulle puis la rend`() {
        val s = opened()
        s.measures.startPickingPoint()
        assertFalse(s.bubbleVisible)
        assertEquals(6.08 to 44.56, s.point)

        s.measures.chooseRefPoint(5.72, 45.18)
        assertTrue(s.bubbleVisible)
        assertFalse(s.pickingPoint)
        assertEquals(MeasureState.Loading, s.measures.pointMeasure)
        assertEquals(listOf(6.08 to 44.56, 5.72 to 45.18), s.markers)
    }

    /** Abandonner le choix rend l'infobulle sans lancer de mesure : rien n'a ete pose. */
    @Test fun `abandonner le choix ne mesure rien`() {
        val s = opened()
        s.measures.startPickingPoint()
        s.measures.cancelPickingPoint()
        assertTrue(s.bubbleVisible)
        assertNull(s.measures.refPoint)
        assertNull(s.measures.pointMeasure)
    }

    /**
     * L'origine de la mesure depuis la position est figee a la premiere position recue : le capteur en
     * livre une toutes les 2 s, et suivre le capteur lancerait autant de requetes d'itineraire.
     */
    @Test fun `l'origine depuis la position ne se fige qu'une fois`() {
        val s = opened()
        s.measures.requestDistanceFromPosition()
        s.measures.fixPositionOrigin(44.0, 6.0)
        s.measures.fixPositionOrigin(44.1, 6.1)
        assertEquals(44.0 to 6.0, s.measures.positionOrigin)
    }

    /** Hors demande, une position recue ne doit rien declencher : sans cela le capteur allume mesurerait
     *  tout seul vers chaque point designe. */
    @Test fun `une position recue sans demande ne fige aucune origine`() {
        val s = opened()
        s.measures.fixPositionOrigin(44.0, 6.0)
        assertNull(s.measures.positionOrigin)
    }

    /** Une mesure aboutie qui n'a pas rendu de geometrie reste chiffree, mais ne pose rien sur la carte :
     *  un trace d'un seul point n'est pas une ligne. */
    @Test fun `seules les mesures tracables se posent sur la carte`() {
        val s = opened()
        s.measures.publishPositionMeasure(MeasureState.Done(1000.0, 300.0, null))
        s.measures.publishPointMeasure(MeasureState.Failed)
        assertTrue(s.measures.routeTracks.isEmpty())
    }

    /** La croix de l'infobulle ne laisse ni epingle, ni trace, ni mesure : rien ne les expliquerait plus. */
    @Test fun `fermer ne laisse rien derriere`() {
        val s = opened()
        s.measures.requestDistanceFromPosition()
        s.measures.chooseRefPoint(5.72, 45.18)

        s.clear()

        assertNull(s.point)
        assertNull(s.measures.refPoint)
        assertNull(s.measures.positionMeasure)
        assertNull(s.measures.pointMeasure)
        assertFalse(s.pickingPoint)
        assertFalse(s.bubbleVisible)
        assertTrue(s.markers.isEmpty())
    }

    /** Cle de relance des traces posees sur la carte : elle doit avancer a chaque mesure publiee, sans quoi
     *  l'itineraire affiche resterait celui d'avant. */
    @Test fun `le numero d'ordre des mesures avance a chaque publication`() {
        val s = opened()
        val start = s.measures.revision
        s.measures.publishPositionMeasure(MeasureState.Loading)
        s.measures.publishPointMeasure(MeasureState.Failed)
        assertTrue(s.measures.revision > start + 1)
    }
}
