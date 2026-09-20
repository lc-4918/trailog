package fr.lc4918.trailog.domain.geo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tri des positions quand plusieurs fournisseurs parlent en meme temps.
 *
 * Le suivi ne s'abonne plus au seul GPS : l'economie d'energie l'eteint avec l'ecran, et le reseau est
 * alors tout ce qui reste. Les deux repondent donc ensemble tant que l'ecran est allume, a quelques
 * centaines de metres l'un de l'autre, et c'est ici qu'on decide lequel devient le repere.
 */
class FixPickerTest {

    private companion object {
        const val GPS = "gps"
        const val RESEAU = "network"
    }

    @Test fun `une position plus fine passe toujours`() {
        assertTrue(FixPicker.better(GPS, 40f, 1_000L, RESEAU, 8f, 3_000L))
    }

    /** Le cas de la sortie : le GPS tient le repere, le reseau parle par-dessus a 400 m pres. */
    @Test fun `une position bien plus grossiere ne chasse pas une position fine et fraiche`() {
        assertFalse(FixPicker.better(GPS, 8f, 1_000L, RESEAU, 400f, 3_000L))
    }

    /** Sous les arbres, le GPS se degrade sans cesser d'etre le bon repere : la fraicheur l'emporte. */
    @Test fun `le meme fournisseur a droit de se degrader un peu`() {
        assertTrue(FixPicker.better(GPS, 8f, 1_000L, GPS, 50f, 3_000L))
        assertFalse("mais pas de s'effondrer", FixPicker.better(GPS, 8f, 1_000L, GPS, 200f, 3_000L))
    }

    /**
     * Releve dans un journal de sortie : le GPS tenait huit metres, et des positions reseau a
     * trente-quatre metres passaient - le curseur bougeait d'une trentaine de metres sans raison. Le
     * reseau n'est pas un GPS qui faiblit ; d'un fournisseur a l'autre, il faut faire MIEUX.
     */
    @Test fun `un autre fournisseur n'a pas droit a cette tolerance`() {
        assertFalse(FixPicker.better(GPS, 8f, 1_000L, RESEAU, 34f, 3_000L))
        assertTrue("faire mieux suffit toujours", FixPicker.better(GPS, 34f, 1_000L, RESEAU, 8f, 3_000L))
    }

    /** Les fournisseurs ne livrent pas dans l'ordre : une mesure plus vieille ne remonte pas le temps. */
    @Test fun `une position plus vieille que celle qu'on tient ne passe pas`() {
        assertFalse(FixPicker.better(RESEAU, 400f, 10_000L, GPS, 8f, 2_000L))
    }

    /** Ecran eteint, GPS coupe : la seule position qui arrive est grossiere, et vaut mieux qu'un repere fige. */
    @Test fun `passe le delai, n'importe quelle position vaut mieux qu'un repere fige`() {
        assertTrue(FixPicker.better(GPS, 8f, 1_000L, RESEAU, 900f, 1_000L + FixPicker.STALE_MS))
    }

    /** Un `Location` sans precision en rend zero : ce n'est pas une position parfaite, c'est une inconnue. */
    @Test fun `une position sans precision annoncee est la pire de toutes`() {
        assertFalse(FixPicker.better(GPS, 8f, 1_000L, GPS, 0f, 3_000L))
        assertTrue(FixPicker.better(GPS, 0f, 1_000L, GPS, 900f, 3_000L))
    }
}
