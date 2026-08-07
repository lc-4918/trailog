package fr.lc4918.trailog.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ecriture hexadecimale des couleurs de pente, celle que comprend la feuille de style de la carte.
 *
 * Verifiee a part parce que c'est le seul point ou la rampe sort du monde Compose : une couleur mal ecrite
 * n'est pas rejetee par MapLibre, elle rend simplement le trace invisible ou noir.
 */
class SlopeRampTest {

    @Test fun `la forme est un diese et six chiffres hexadecimaux majuscules`() {
        val hex = SlopeRamp.hexFor(4.0, 12.0)
        assertTrue(hex, Regex("^#[0-9A-F]{6}$").matches(hex))
    }

    /** La teinte de la carte doit designer la meme pente que l'aire du profil, sans quoi les deux se
     *  contrediraient a l'ecran. */
    @Test fun `la couleur est celle de la rampe du profil`() {
        val c = SlopeRamp.colorFor(7.0, 15.0)
        fun ch(v: Float) = Math.round(v * 255f)
        assertEquals("#%02X%02X%02X".format(ch(c.red), ch(c.green), ch(c.blue)), SlopeRamp.hexFor(7.0, 15.0))
    }

    /** Le plat prend le bas de la rampe (bleu), le maximum son haut (rouge). */
    @Test fun `le plat et la pente maximale sont aux deux bouts de la rampe`() {
        assertEquals("#2166AC", SlopeRamp.hexFor(0.0, 20.0))
        assertEquals("#D7191C", SlopeRamp.hexFor(20.0, 20.0))
    }

    /** Une descente se teinte comme la montee de meme raideur : la rampe classe l'effort, pas le sens. */
    @Test fun `le signe de la pente ne change pas la couleur`() {
        assertEquals(SlopeRamp.hexFor(6.0, 15.0), SlopeRamp.hexFor(-6.0, 15.0))
    }

    /** Les classes sont relatives au parcours : la meme pente ne se teinte pas pareil sur un parcours plat
     *  et sur un parcours de montagne, faute de quoi le premier serait uniformement bleu. */
    @Test fun `les classes suivent la pente maximale du parcours`() {
        assertNotEquals(SlopeRamp.hexFor(4.0, 5.0), SlopeRamp.hexFor(4.0, 20.0))
    }
}
