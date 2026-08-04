package fr.lc4918.trailog.map

import fr.lc4918.trailog.map.CoverageProbe.Coverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verdict de la sonde de couverture. Le risque a couvrir est le faux positif : recadrer la carte
 *  d'un utilisateur qui regardait ce qu'il voulait est bien plus penible que ne pas recadrer. */
class CoverageProbeTest {

    private fun uniform(color: Int) = IntArray(256) { color }

    // --- classify : ce que dit le serveur ---

    @Test fun `404 et 204 valent absence`() {
        assertEquals(Coverage.EMPTY, CoverageProbe.classify(404, null))
        assertEquals(Coverage.EMPTY, CoverageProbe.classify(204, null))
    }

    /** Statut 0 = requete non aboutie (pas de reseau, delai depasse). Sans reseau, on ne recadre pas :
     *  la carte est peut-etre servie par le cache hors-ligne. */
    @Test fun `une panne reseau ne conclut rien`() {
        assertEquals(Coverage.UNKNOWN, CoverageProbe.classify(0, null))
    }

    /** 403 = quota ou plan insuffisant, 500 = service en panne : ni l'un ni l'autre ne dit que la zone
     *  n'est pas couverte. L'OS britannique repond 403 sur ses tuiles Premium, en plein Royaume-Uni. */
    @Test fun `un refus ou une panne serveur ne concluent rien`() {
        assertEquals(Coverage.UNKNOWN, CoverageProbe.classify(403, null))
        assertEquals(Coverage.UNKNOWN, CoverageProbe.classify(500, null))
    }

    /** Un 200 dont le corps n'est pas une image (page d'erreur HTML servie en 200) : sans avis. */
    @Test fun `un corps illisible ne conclut rien`() {
        assertEquals(Coverage.UNKNOWN, CoverageProbe.classify(200, null))
        assertEquals(Coverage.UNKNOWN, CoverageProbe.classify(200, ByteArray(0)))
    }

    // --- isBlank : ce que montre l'image ---

    @Test fun `une image toute blanche est vide`() {
        assertTrue(CoverageProbe.isBlank(uniform(0xFFFFFFFF.toInt())))
    }

    /** Les services ne rendent pas toujours un blanc exact : JPEG et fonds de page derivent un peu. */
    @Test fun `un blanc casse uniforme est vide`() {
        assertTrue(CoverageProbe.isBlank(uniform(0xFFF4F4F2.toInt())))
    }

    @Test fun `une image entierement transparente est vide`() {
        assertTrue(CoverageProbe.isBlank(uniform(0x00000000)))
        assertTrue(CoverageProbe.isBlank(uniform(0x00FFFFFF)))
    }

    /** Le coeur de la regle : uniforme ne suffit pas, il faut du blanc. Une tuile en pleine mer ou dans
     *  un grand lac est uniformement bleue, et recadrer la serait un contresens. */
    @Test fun `une tuile uniformement bleue est une donnee`() {
        assertFalse(CoverageProbe.isBlank(uniform(0xFF9ECCE8.toInt())))
    }

    @Test fun `une tuile uniformement verte ou noire est une donnee`() {
        assertFalse(CoverageProbe.isBlank(uniform(0xFFC8E6A0.toInt())))
        assertFalse(CoverageProbe.isBlank(uniform(0xFF000000.toInt())))
    }

    /** Un gris clair uniforme reste une donnee : c'est une teinte de relief, pas un vide. */
    @Test fun `un gris clair uniforme est une donnee`() {
        assertFalse(CoverageProbe.isBlank(uniform(0xFFDCDCDC.toInt())))
    }

    @Test fun `un seul pixel different suffit a valoir donnee`() {
        val px = uniform(0xFFFFFFFF.toInt())
        px[px.size / 2] = 0xFF8B4513.toInt()   // une courbe de niveau sur une tuile presque vide
        assertFalse(CoverageProbe.isBlank(px))
    }

    /** Une teinte uniforme et claire mais franchement coloree n'est pas du blanc. */
    @Test fun `un blanc trop colore est une donnee`() {
        assertFalse(CoverageProbe.isBlank(uniform(0xFFFFF0D0.toInt())))
    }

    @Test fun `une image sans pixel ne conclut pas a l absence`() {
        assertFalse(CoverageProbe.isBlank(IntArray(0)))
    }
}
