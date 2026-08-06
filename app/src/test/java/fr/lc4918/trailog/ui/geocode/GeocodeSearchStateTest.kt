package fr.lc4918.trailog.ui.geocode

import fr.lc4918.trailog.geocode.GeocodePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transitions de la recherche de lieu. Ce sont elles, et non les valeurs, qui portent le risque : une bulle
 * qui reste affichee pendant le choix d'un point le recouvre, une mesure oubliee au changement de lieu
 * affiche une distance calculee vers un autre endroit - deux fautes qui s'affichent sans rien casser.
 */
class GeocodeSearchStateTest {
    private val gap = GeocodePlace("Gap, France", 6.08, 44.56)
    private val grenoble = GeocodePlace("Grenoble, France", 5.72, 45.18)

    private fun searching() = GeocodeSearchState().apply {
        openSearch(); query = "gap"; results = listOf(gap)
    }

    @Test fun `choisir un lieu referme la barre de recherche`() {
        val s = searching()
        s.select(gap)
        assertFalse(s.searchOpen)
        assertEquals("", s.query)
        assertTrue(s.results.isEmpty())
        assertEquals(gap, s.place)
    }

    @Test fun `l'infobulle s'affiche des qu'un lieu est choisi`() {
        val s = GeocodeSearchState()
        assertFalse(s.bubbleVisible)
        s.select(gap)
        assertTrue(s.bubbleVisible)
    }

    /** L'infobulle s'efface le temps de choisir un point, puis revient avec sa mesure. */
    @Test fun `l'infobulle s'efface pendant le choix d'un point et revient apres`() {
        val s = GeocodeSearchState().apply { select(gap) }
        s.startPickingPoint()
        assertTrue(s.pickingPoint)
        assertFalse(s.bubbleVisible)
        s.setRefPoint(5.0, 44.0)
        assertFalse(s.pickingPoint)
        assertTrue(s.bubbleVisible)
        assertEquals(5.0 to 44.0, s.refPoint)
    }

    /** Sortir du choix par le retour systeme ne pose aucun point et rend l'infobulle telle qu'elle etait. */
    @Test fun `annuler le choix d'un point ne pose rien`() {
        val s = GeocodeSearchState().apply { select(gap); startPickingPoint() }
        s.cancelPickingPoint()
        assertFalse(s.pickingPoint)
        assertNull(s.refPoint)
        assertTrue(s.bubbleVisible)
    }

    /** Les deux mesures portent sur le lieu affiche : en changer sans les effacer laisserait a l'ecran des
     *  distances calculees vers le lieu precedent. */
    @Test fun `changer de lieu efface les mesures du precedent`() {
        val s = GeocodeSearchState().apply {
            select(gap); requestDistanceFromPosition(); setRefPoint(5.0, 44.0)
        }
        s.select(grenoble)
        assertEquals(grenoble, s.place)
        assertFalse(s.distanceFromPositionRequested)
        assertNull(s.refPoint)
    }

    /** Fermer la seule barre de recherche laisse en place le lieu deja trouve : on peut rouvrir la
     *  recherche sans perdre la mesure en cours. */
    @Test fun `fermer la barre de recherche conserve le lieu affiche`() {
        val s = GeocodeSearchState().apply { select(gap); requestDistanceFromPosition() }
        s.openSearch(); s.query = "gre"
        s.closeSearch()
        assertEquals(gap, s.place)
        assertTrue(s.distanceFromPositionRequested)
        assertTrue(s.bubbleVisible)
    }

    @Test fun `tout fermer ne laisse ni lieu ni mesure ni mode de saisie`() {
        val s = GeocodeSearchState().apply {
            select(gap); requestDistanceFromPosition(); setRefPoint(5.0, 44.0); startPickingPoint()
        }
        s.clear()
        assertNull(s.place)
        assertNull(s.refPoint)
        assertFalse(s.pickingPoint)
        assertFalse(s.distanceFromPositionRequested)
        assertFalse(s.searchOpen)
        assertFalse(s.bubbleVisible)
    }
}
