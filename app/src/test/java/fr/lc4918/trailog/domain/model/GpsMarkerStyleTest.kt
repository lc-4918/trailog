package fr.lc4918.trailog.domain.model

import fr.lc4918.trailog.data.db.MaxGpsMarkerSizeDp
import fr.lc4918.trailog.data.db.MinGpsMarkerSizeDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le symbole de position et ce qu'il emporte avec lui.
 *
 * Ce que ces cas gardent : un symbole arrive avec SA couleur et SA taille, et non avec celles du
 * precedent. La faute serait muette - une fleche bleue et minuscule chez qui vient de quitter la puce,
 * qui croirait avoir mal regle quelque chose.
 */
class GpsMarkerStyleTest {

    /**
     * Une fleche dit une direction, et une direction ne se lit pas sur un dessin de la taille d'un point :
     * les deux fleches partent donc a 30 dp, la ou la puce et la croix se contentent de 20.
     */
    @Test fun `les fleches sont plus grandes que la puce et la croix`() {
        assertEquals(30, GpsMarkerStyle.ARROW_OUTLINE.defaultSizeDp)
        assertEquals(30, GpsMarkerStyle.ARROW_FILLED.defaultSizeDp)
        assertEquals(20, GpsMarkerStyle.DOT.defaultSizeDp)
        assertEquals(20, GpsMarkerStyle.CROSSHAIR.defaultSizeDp)
    }

    /** Une taille par defaut hors des bornes du reglage donnerait un curseur bloque a son extremite. */
    @Test fun `chaque taille par defaut tient dans les bornes du reglage`() {
        GpsMarkerStyle.entries.forEach {
            assertTrue("${it.key} : ${it.defaultSizeDp}", it.defaultSizeDp in MinGpsMarkerSizeDp..MaxGpsMarkerSizeDp)
        }
    }

    /** Chaque symbole porte une couleur lisible, et les fleches et la croix se detachent d'un fond
     *  topographique la ou le bleu de la puce s'y fond. */
    @Test fun `chaque symbole porte sa couleur`() {
        GpsMarkerStyle.entries.forEach {
            assertTrue(it.key, Regex("^#[0-9A-Fa-f]{6}$").matches(it.defaultColor))
        }
        assertEquals("#4285F4", GpsMarkerStyle.DOT.defaultColor)
    }

    /** Seules les fleches ont une direction a orienter : faire tourner une puce ne se verrait pas, et
     *  faire tourner la croix la ferait vibrer a chaque frisson de la boussole. */
    @Test fun `seules les fleches sont orientees`() {
        assertTrue(GpsMarkerStyle.ARROW_OUTLINE.oriented)
        assertTrue(GpsMarkerStyle.ARROW_FILLED.oriented)
        assertFalse(GpsMarkerStyle.DOT.oriented)
        assertFalse(GpsMarkerStyle.CROSSHAIR.oriented)
    }

    /** Cle inconnue - reglage ecrit par une version plus recente : on retombe sur la puce plutot que de
     *  priver la carte de tout repere. */
    @Test fun `une cle inconnue retombe sur la puce`() {
        assertEquals(GpsMarkerStyle.DOT, GpsMarkerStyle.of("symbole_de_demain"))
        assertEquals(GpsMarkerStyle.DOT, GpsMarkerStyle.of(null))
        assertEquals(GpsMarkerStyle.ARROW_FILLED, GpsMarkerStyle.of("arrow_filled"))
    }
}
