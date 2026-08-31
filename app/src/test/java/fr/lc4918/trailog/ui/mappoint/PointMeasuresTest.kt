package fr.lc4918.trailog.ui.mappoint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les deux distances qu'on demande depuis un endroit de la carte, maintenant qu'elles ne sont plus le
 * privilege d'un appui long.
 *
 * Ce que ces cas verrouillent tient en une phrase : **chaque bulle a les siennes**. Le point d'appui long
 * et le point d'interet peuvent etre a l'ecran en meme temps, et un porteur partage afficherait sous l'un
 * la distance mesuree pour l'autre - un chiffre juste, au mauvais endroit, que rien ne signalerait.
 */
class PointMeasuresTest {

    private fun mesures() = PointMeasures().apply { retarget(6.08 to 44.56) }

    @Test fun `la cible se pose et les mesures partent vierges`() {
        val m = mesures()
        assertEquals(6.08 to 44.56, m.target)
        assertNull(m.positionMeasure)
        assertNull(m.pointMeasure)
        assertTrue(m.markers.isEmpty())
    }

    /** Les distances valaient pour l'endroit precedent : les garder les afficherait, telles quelles, sous
     *  le nom d'un autre lieu. */
    @Test fun `changer de cible efface les mesures du precedent`() {
        val m = mesures()
        m.requestDistanceFromPosition()
        m.publishPositionMeasure(MeasureState.Done(1000.0, 300.0))
        m.chooseRefPoint(5.72, 45.18)
        m.publishPointMeasure(MeasureState.Done(2000.0, 600.0))

        m.retarget(3.0 to 48.0)

        assertEquals(3.0 to 48.0, m.target)
        assertNull(m.positionMeasure)
        assertNull(m.pointMeasure)
        assertNull(m.refPoint)
        assertNull(m.positionOrigin)
        assertTrue(m.routeTracks.isEmpty())
    }

    /**
     * **Reposer la MEME cible ne touche a rien**, et ce n'est pas un detail : l'appelant la repose a chaque
     * recomposition, et remettre a zero effacerait la mesure a l'instant meme ou on la demande.
     */
    @Test fun `reposer la meme cible ne perd pas la mesure en cours`() {
        val m = mesures()
        m.requestDistanceFromPosition()
        m.retarget(6.08 to 44.56)
        assertEquals(MeasureState.Loading, m.positionMeasure)
    }

    /** La cible retiree - la bulle refermee - ne laisse ni chiffre ni trace sur la carte. */
    @Test fun `retirer la cible efface tout`() {
        val m = mesures()
        m.publishPositionMeasure(MeasureState.Done(1000.0, 300.0))
        m.retarget(null)
        assertNull(m.target)
        assertNull(m.positionMeasure)
    }

    /** L'epingle du point de REFERENCE seule : le point mesure appartient a ce qui l'a designe - une
     *  epingle d'appui long, un marqueur de point d'interet - et se pose ailleurs. */
    @Test fun `seule l'epingle du point de reference est posee`() {
        val m = mesures()
        assertTrue(m.markers.isEmpty())
        m.chooseRefPoint(5.72, 45.18)
        assertEquals(listOf(5.72 to 45.18), m.markers)
    }

    /** Le choix d'un point occupe l'ecran : ce qui l'a demande doit s'effacer, la carte etant a viser. */
    @Test fun `le choix d'un point occupe l'ecran`() {
        val m = mesures()
        assertFalse(m.busy)
        m.startPickingPoint()
        assertTrue(m.busy)
        m.chooseRefPoint(5.72, 45.18)
        assertFalse(m.busy)
        assertEquals(MeasureState.Loading, m.pointMeasure)
    }

    /**
     * LE cas qui justifie deux porteurs : les deux bulles a l'ecran ensemble, chacune ses chiffres.
     *
     * Un porteur partage affichait sous le point d'interet la distance mesuree depuis le point d'appui
     * long - juste, mais pour un autre endroit, et rien ne l'aurait dit.
     */
    @Test fun `deux porteurs ne se melangent pas`() {
        val appuiLong = mesures()
        val pointInteret = PointMeasures().apply { retarget(3.0 to 48.0) }

        appuiLong.publishPositionMeasure(MeasureState.Done(1000.0, 300.0))

        assertEquals(MeasureState.Done(1000.0, 300.0), appuiLong.positionMeasure)
        assertNull("le point d'interet n'a rien demande", pointInteret.positionMeasure)
        assertEquals(6.08 to 44.56, appuiLong.target)
        assertEquals(3.0 to 48.0, pointInteret.target)
    }

    /** L'origine du calcul depuis la position est figee UNE FOIS : le repere en livre une toutes les deux
     *  secondes, et les suivre lancerait autant de requetes d'itineraire. */
    @Test fun `l'origine de la mesure depuis la position ne se fige qu'une fois`() {
        val m = mesures()
        m.requestDistanceFromPosition()
        m.fixPositionOrigin(44.0, 6.0)
        m.fixPositionOrigin(45.0, 7.0)
        assertEquals(44.0 to 6.0, m.positionOrigin)
    }

    /** Et pas du tout tant que rien n'a ete demande : une position recue ne doit pas lancer de calcul. */
    @Test fun `sans demande, aucune origine ne se fige`() {
        val m = mesures()
        m.fixPositionOrigin(44.0, 6.0)
        assertNull(m.positionOrigin)
    }

    /** Cle de relance des traces posees sur la carte : elle doit avancer a chaque mesure publiee, sans quoi
     *  l'itineraire affiche resterait celui d'avant. */
    @Test fun `le numero d'ordre avance a chaque publication`() {
        val m = mesures()
        val debut = m.revision
        m.publishPositionMeasure(MeasureState.Loading)
        m.publishPointMeasure(MeasureState.Failed)
        assertTrue(m.revision > debut + 1)
    }
}
