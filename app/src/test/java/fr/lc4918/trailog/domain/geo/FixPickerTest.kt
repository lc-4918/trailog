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

    @Test fun `une position plus fine passe toujours`() {
        assertTrue(FixPicker.better(heldAccuracyM = 40f, heldAtMs = 1_000L, newAccuracyM = 8f, newAtMs = 3_000L))
    }

    /** Le cas de la sortie : le GPS tient le repere, le reseau parle par-dessus a 400 m pres. */
    @Test fun `une position bien plus grossiere ne chasse pas une position fine et fraiche`() {
        assertFalse(FixPicker.better(heldAccuracyM = 8f, heldAtMs = 1_000L, newAccuracyM = 400f, newAtMs = 3_000L))
    }

    /** Sous les arbres, le GPS se degrade sans cesser d'etre le bon repere : la fraicheur l'emporte. */
    @Test fun `une legere degradation ne coute pas la fraicheur`() {
        assertTrue(FixPicker.better(heldAccuracyM = 8f, heldAtMs = 1_000L, newAccuracyM = 50f, newAtMs = 3_000L))
    }

    /** Les fournisseurs ne livrent pas dans l'ordre : une mesure plus vieille ne remonte pas le temps. */
    @Test fun `une position plus vieille que celle qu'on tient ne passe pas`() {
        assertFalse(FixPicker.better(heldAccuracyM = 400f, heldAtMs = 10_000L, newAccuracyM = 8f, newAtMs = 2_000L))
    }

    /** Ecran eteint, GPS coupe : la seule position qui arrive est grossiere, et vaut mieux qu'un repere fige. */
    @Test fun `passe le delai, n'importe quelle position vaut mieux qu'un repere fige`() {
        assertTrue(
            FixPicker.better(
                heldAccuracyM = 8f, heldAtMs = 1_000L,
                newAccuracyM = 900f, newAtMs = 1_000L + FixPicker.STALE_MS,
            ),
        )
    }

    /** Un `Location` sans precision en rend zero : ce n'est pas une position parfaite, c'est une inconnue. */
    @Test fun `une position sans precision annoncee est la pire de toutes`() {
        assertFalse(FixPicker.better(heldAccuracyM = 8f, heldAtMs = 1_000L, newAccuracyM = 0f, newAtMs = 3_000L))
        assertTrue(FixPicker.better(heldAccuracyM = 0f, heldAtMs = 1_000L, newAccuracyM = 900f, newAtMs = 3_000L))
    }
}
