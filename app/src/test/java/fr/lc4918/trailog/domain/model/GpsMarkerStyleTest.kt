package fr.lc4918.trailog.domain.model

import fr.lc4918.trailog.data.db.DefaultGpsMarkerSizeDp
import fr.lc4918.trailog.data.db.MaxGpsMarkerSizeDp
import fr.lc4918.trailog.data.db.MinGpsMarkerSizeDp
import fr.lc4918.trailog.data.db.SettingsEntity
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

    /**
     * Cle inconnue - reglage ecrit par une version plus recente, ou reglages pas encore lus : on retombe
     * sur un repere plutot que de priver la carte de tout symbole.
     *
     * Le repli doit valoir le DEFAUT du reglage, et c'est ce que le test suivant verifie. `of` recoit
     * `settings?.gpsMarkerStyle`, donc null tant que la base n'a pas repondu, et cette fenetre se rouvre a
     * chaque recreation du ViewModel : un repli qui differe du defaut y ferait clignoter le repere d'un
     * symbole a l'autre.
     */
    @Test fun `une cle inconnue retombe sur la fleche pleine`() {
        assertEquals(GpsMarkerStyle.ARROW_FILLED, GpsMarkerStyle.of("symbole_de_demain"))
        assertEquals(GpsMarkerStyle.ARROW_FILLED, GpsMarkerStyle.of(null))
        assertEquals(GpsMarkerStyle.DOT, GpsMarkerStyle.of("dot"))
    }

    /**
     * Le repli, le defaut du reglage et la taille par defaut disent tous trois la meme chose.
     *
     * Trois endroits qu'il faut changer ENSEMBLE, et rien ne le rappelle a la compilation : la constante
     * de taille ne peut pas citer l'enum (un `const` exige une valeur calculable a la compilation), et le
     * defaut de l'entite est une chaine. C'est ce test qui les tient accordes.
     */
    @Test fun `le defaut du reglage, le repli et la taille sont accordes`() {
        val defaut = GpsMarkerStyle.of(SettingsEntity().gpsMarkerStyle)
        assertEquals("le defaut de l'entite", GpsMarkerStyle.ARROW_FILLED, defaut)
        assertEquals("le repli de of()", defaut, GpsMarkerStyle.of(null))
        assertEquals("la taille par defaut", defaut.defaultSizeDp, DefaultGpsMarkerSizeDp)
    }
}
