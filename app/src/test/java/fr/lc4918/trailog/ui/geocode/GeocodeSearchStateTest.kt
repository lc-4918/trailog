package fr.lc4918.trailog.ui.geocode

import fr.lc4918.trailog.geocode.GeocodePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transitions de la recherche de lieu. Ce sont elles, et non les valeurs, qui portent le risque : une barre
 * qui ne se referme pas ou un lieu qui survit a sa fermeture s'affichent sans rien casser.
 *
 * Les mesures de distance que portait cet etat sont passees au planificateur : ce qui reste ici tient a la
 * seule recherche d'adresse.
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

    /** Fermer la seule barre de recherche laisse en place le lieu deja trouve : on peut rouvrir la
     *  recherche sans perdre l'infobulle affichee. */
    @Test fun `fermer la barre de recherche conserve le lieu affiche`() {
        val s = GeocodeSearchState().apply { select(gap) }
        s.openSearch(); s.query = "gre"
        s.closeSearch()
        assertEquals(gap, s.place)
        assertFalse(s.searchOpen)
        assertEquals("", s.query)
    }

    /** Le nouveau lieu remplace l'ancien, sans que la barre reste ouverte derriere lui. */
    @Test fun `choisir un autre lieu remplace le precedent`() {
        val s = GeocodeSearchState().apply { select(gap) }
        s.openSearch()
        s.select(grenoble)
        assertEquals(grenoble, s.place)
        assertFalse(s.searchOpen)
    }

    /** Une recherche abandonnee ne doit pas laisser ses propositions derriere elle : rouvrir la barre
     *  afficherait les resultats d'une frappe precedente. */
    @Test fun `fermer la barre vide la frappe et ses propositions`() {
        val s = searching().apply { searching = true }
        s.closeSearch()
        assertEquals("", s.query)
        assertTrue(s.results.isEmpty())
        assertFalse(s.searching)
    }

    @Test fun `tout fermer ne laisse ni lieu ni recherche`() {
        val s = searching().apply { select(gap); openSearch() }
        s.clear()
        assertNull(s.place)
        assertFalse(s.searchOpen)
        assertEquals("", s.query)
        assertTrue(s.results.isEmpty())
    }
}
