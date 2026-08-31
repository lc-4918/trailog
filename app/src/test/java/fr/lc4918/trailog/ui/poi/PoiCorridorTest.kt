package fr.lc4918.trailog.ui.poi

import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.poi.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le couloir des traces : quels points d'interet bordent le trajet affiche.
 *
 * La faute que ces cas attrapent ne leve rien - elle retire de la carte des lieux qui sont sur le chemin,
 * ou en laisse qui en sont a vingt kilometres. Dans les deux cas la couche a l'air de fonctionner, et c'est
 * ce qui rend la verification necessaire ici plutot qu'a l'oeil.
 */
class PoiCorridorTest {

    /** Un degre de latitude vaut 111 320 m : de quoi poser un lieu a une distance connue d'une trace. */
    private val DEG = 111_320.0

    private fun lieu(nom: String, lon: Double, lat: Double) =
        Poi(nom, nom, lat, lon, PoiCategory.WATER)

    /** Une trace est-ouest a la latitude 45, de la longitude 5,0 a 5,2. */
    private val trace = listOf(5.0 to 45.0, 5.1 to 45.0, 5.2 to 45.0)

    /** Un lieu a [m] metres au nord de la trace. */
    private fun auNord(nom: String, m: Double, lon: Double = 5.1) = lieu(nom, lon, 45.0 + m / DEG)

    @Test fun `un lieu au bord de la trace est retenu`() {
        val pres = auNord("pres", 300.0)
        assertEquals(listOf(pres), PoiCorridor.filter(listOf(pres), listOf(trace), 1_000.0))
    }

    @Test fun `un lieu au-dela du seuil est ecarte`() {
        val loin = auNord("loin", 4_000.0)
        assertTrue(PoiCorridor.filter(listOf(loin), listOf(trace), 1_000.0).isEmpty())
    }

    /**
     * LA raison de mesurer au SEGMENT et non au sommet le plus proche.
     *
     * Une trace decimee pose ses sommets a plusieurs centaines de metres l'un de l'autre. Un lieu tombe au
     * milieu de deux sommets est sur le chemin ; le comparer aux seuls sommets l'en eloignerait de la
     * moitie de leur ecart, et il disparaitrait de la carte alors qu'on passe devant.
     */
    @Test fun `un lieu entre deux sommets eloignes est sur le chemin`() {
        // Deux sommets a 5,0 et 5,2 : environ 15 km l'un de l'autre a cette latitude.
        val ecartes = listOf(5.0 to 45.0, 5.2 to 45.0)
        val milieu = auNord("milieu", 200.0, lon = 5.1)
        assertEquals(listOf(milieu), PoiCorridor.filter(listOf(milieu), listOf(ecartes), 1_000.0))
    }

    /** Au-dela des BOUTS de la trace, la distance se mesure au sommet : la trace s'arrete, le couloir aussi. */
    @Test fun `un lieu au-dela du bout de la trace suit le dernier sommet`() {
        val apres = lieu("apres", 5.3, 45.0)     // environ 7,9 km a l'est du dernier sommet
        assertTrue(PoiCorridor.filter(listOf(apres), listOf(trace), 1_000.0).isEmpty())
        assertEquals(listOf(apres), PoiCorridor.filter(listOf(apres), listOf(trace), 10_000.0))
    }

    /**
     * **Sans trace affichee, rien n'est filtre.**
     *
     * La couche des points d'interet ne depend pas de la bibliotheque : vider la carte parce qu'aucune
     * trace n'est ouverte remplacerait une gene par une panne.
     */
    @Test fun `sans trace, tout passe`() {
        val loin = auNord("loin", 50_000.0)
        assertEquals(listOf(loin), PoiCorridor.filter(listOf(loin), emptyList(), 1_000.0))
    }

    /** Reglage eteint (distance nulle) : le couloir ne s'applique pas, meme avec une trace affichee. */
    @Test fun `a distance nulle, le couloir ne filtre rien`() {
        val loin = auNord("loin", 50_000.0)
        assertEquals(listOf(loin), PoiCorridor.filter(listOf(loin), listOf(trace), 0.0))
    }

    /** Plusieurs traces : border l'une d'elles suffit. */
    @Test fun `border une seule des traces suffit`() {
        val autre = listOf(6.0 to 44.0, 6.1 to 44.0)
        val pres = lieu("pres", 6.05, 44.0 + 200.0 / DEG)
        assertEquals(listOf(pres), PoiCorridor.filter(listOf(pres), listOf(trace, autre), 1_000.0))
    }

    /** Une trace d'un seul point borde quand meme : le sommet repond seul, faute de segment. */
    @Test fun `une trace d'un seul point borde son voisinage`() {
        val pres = auNord("pres", 300.0, lon = 5.0)
        assertEquals(listOf(pres), PoiCorridor.filter(listOf(pres), listOf(listOf(5.0 to 45.0)), 1_000.0))
    }

    /** L'ordre des lieux retenus est celui d'entree : la carte ne doit pas voir ses marqueurs permuter
     *  d'un chargement au suivant pour la seule raison qu'on les a tries. */
    @Test fun `l'ordre des lieux est conserve`() {
        val a = auNord("a", 100.0, lon = 5.02)
        val b = auNord("b", 100.0, lon = 5.05)
        val c = auNord("c", 100.0, lon = 5.08)
        assertEquals(listOf(a, b, c), PoiCorridor.filter(listOf(a, b, c), listOf(trace), 1_000.0))
    }
}
