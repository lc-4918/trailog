package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.geo.TrackMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Report des altitudes echantillonnees a pas constant sur les points du trace.
 *
 * C'est le seul endroit ou les deux listes du moteur se rencontrent, et une faute y serait muette : un
 * decalage d'un pas ne leve rien, il deplace seulement le relief le long du parcours, ce qui ne se voit
 * qu'en comparant le profil au terrain.
 */
class RouteElevationTest {

    /** Deux points espaces d'environ [meters] sur un meridien : la latitude bouge, la longitude non. */
    private fun northOf(lat: Double, meters: Double) = lat + meters / 111_320.0

    // ---------- Absence d'altitudes ----------

    @Test fun `sans table d'altitudes les points n'en portent pas`() {
        val pts = RouteElevation.pointsOf(listOf(5.0 to 45.0, 5.001 to 45.0), emptyList(), 30.0)
        assertEquals(2, pts.size)
        assertNull(pts[0].ele)
        assertNull(pts[1].ele)
    }

    /** Un pas nul ou negatif ne permet aucune interpolation : on renonce plutot que de diviser par zero. */
    @Test fun `un pas nul fait renoncer aux altitudes`() {
        val pts = RouteElevation.pointsOf(listOf(5.0 to 45.0, 5.001 to 45.0), listOf(100.0, 200.0), 0.0)
        assertNull(pts[0].ele)
    }

    @Test fun `un trace vide ne rend aucun point`() {
        assertTrue(RouteElevation.pointsOf(emptyList(), listOf(100.0), 30.0).isEmpty())
    }

    // ---------- Report ----------

    /** Les coordonnees ne sont jamais touchees : ce sont elles qui portent la ligne sur la carte. */
    @Test fun `les coordonnees traversent intactes`() {
        val shape = listOf(5.0 to 45.0, 5.5 to 45.5, 6.0 to 46.0)
        val pts = RouteElevation.pointsOf(shape, listOf(100.0, 110.0), 1000.0)
        assertEquals(shape, pts.map { it.lon to it.lat })
    }

    @Test fun `le premier point prend la premiere altitude`() {
        val pts = RouteElevation.pointsOf(listOf(5.0 to 45.0, 5.0 to northOf(45.0, 30.0)), listOf(100.0, 130.0), 30.0)
        assertEquals(100.0, pts[0].ele!!, 1e-9)
    }

    /** Un point tombant pile sur un echantillon en reprend la valeur, sans interpolation. */
    @Test fun `un point sur un echantillon en reprend la valeur`() {
        val pts = RouteElevation.pointsOf(
            listOf(5.0 to 45.0, 5.0 to northOf(45.0, 30.0)), listOf(100.0, 130.0, 160.0), 30.0)
        assertEquals(130.0, pts[1].ele!!, 0.05)
    }

    /** Le cas courant : les points du trace ne tombent pas sur les echantillons, il faut interpoler. */
    @Test fun `un point entre deux echantillons recoit une altitude interpolee`() {
        val pts = RouteElevation.pointsOf(
            listOf(5.0 to 45.0, 5.0 to northOf(45.0, 15.0)), listOf(100.0, 200.0), 30.0)
        assertEquals("moitie du pas -> moitie de l'ecart", 150.0, pts[1].ele!!, 0.5)
    }

    /**
     * La longueur cumulee de la polyligne, somme de cordes droites, ne retombe pas exactement sur celle que
     * le moteur a mesuree le long des voies : le dernier point sort de la table de quelques metres. Sans
     * bornage il tomberait hors de l'index, et le profil s'arreterait avant sa fin.
     */
    @Test fun `un point au-dela de la table prend la derniere altitude`() {
        val pts = RouteElevation.pointsOf(
            listOf(5.0 to 45.0, 5.0 to northOf(45.0, 500.0)), listOf(100.0, 130.0), 30.0)
        assertEquals(130.0, pts.last().ele!!, 1e-9)
    }

    // ---------- Bout en bout ----------

    /**
     * Ce que voit reellement l'ecran : une montee reguliere doit ressortir en pente positive constante,
     * puisque c'est elle qui teinte la ligne sur la carte et dessine l'aire du profil.
     */
    @Test fun `une montee reguliere donne une pente positive constante`() {
        // 11 points tous les 100 m, altitudes montant de 5 m tous les 50 m -> 10 % de pente.
        val shape = (0..10).map { 5.0 to northOf(45.0, it * 100.0) }
        val elevation = (0..24).map { 100.0 + it * 5.0 }
        val pts = RouteElevation.pointsOf(shape, elevation, 50.0)
        val track = TrackMath.compute(pts, smoothingM = 0.0, maxPoints = 0)
        assertTrue("altitudes presentes", track.hasZ)
        track.samples.forEach { assertEquals(10.0, it.slope, 0.2) }
        assertEquals("1000 m a 10 %", 100.0, track.stats.ascent, 1.0)
        assertEquals(0.0, track.stats.descent, 1e-6)
    }
}
