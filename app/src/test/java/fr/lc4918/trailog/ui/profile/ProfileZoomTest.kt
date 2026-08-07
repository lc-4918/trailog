package fr.lc4918.trailog.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Fenetre de zoom du profil. Seule piece du geste ou une faute reste muette : un mauvais ancrage ne leve
 * rien, il fait deriver le graphique sous les doigts, ce qui ne se voit qu'a l'usage.
 */
class ProfileZoomTest {
    private val total = 1000

    @Test fun `grossir depuis la vue complete resserre la fenetre`() {
        val w = ProfileZoom.window(null, total, scale = 2f, focusFraction = 0.5f)!!
        assertEquals(500, w.last - w.first + 1)
    }

    /** Le point vise ne doit pas bouger : c'est ce qui donne la sensation de tirer sur le graphique. */
    @Test fun `le point vise reste sous les doigts`() {
        val w = ProfileZoom.window(null, total, scale = 2f, focusFraction = 0.5f)!!
        val len = w.last - w.first + 1
        val viseApres = w.first + (0.5f * (len - 1)).roundToInt()
        val viseAvant = (0.5f * (total - 1)).roundToInt()
        assertTrue("vise $viseApres, attendu autour de $viseAvant",
            kotlin.math.abs(viseApres - viseAvant) <= 2)
    }

    @Test fun `viser le bord gauche garde la fenetre calee a gauche`() {
        val w = ProfileZoom.window(null, total, scale = 4f, focusFraction = 0f)!!
        assertEquals(0, w.first)
    }

    @Test fun `viser le bord droit garde la fenetre calee a droite`() {
        val w = ProfileZoom.window(null, total, scale = 4f, focusFraction = 1f)!!
        assertEquals(total - 1, w.last)
    }

    /** Les grossissements successifs se composent : c'est ce qui fait qu'un ecartement continu zoome
     *  franchement, chaque evenement n'apportant qu'un facteur minime. */
    @Test fun `les grossissements successifs se composent`() {
        var w: IntRange? = null
        repeat(50) { w = ProfileZoom.window(w, total, scale = 1.02f, focusFraction = 0.5f) }
        val len = w!!.last - w!!.first + 1
        assertTrue("attendu bien moins que $total, obtenu $len", len < total / 2)
    }

    /** La vue complete se represente par l'absence de fenetre : l'appelant n'a qu'un cas a tester. */
    @Test fun `elargir au-dela du parcours rend la vue complete`() {
        val zoomed = ProfileZoom.window(null, total, scale = 2f, focusFraction = 0.5f)
        assertNull(ProfileZoom.window(zoomed, total, scale = 0.1f, focusFraction = 0.5f))
    }

    /** En deca de ce plancher le graphique n'aurait plus de forme. */
    @Test fun `la fenetre ne descend pas sous le plancher`() {
        var w: IntRange? = null
        repeat(200) { w = ProfileZoom.window(w, total, scale = 1.5f, focusFraction = 0.5f) }
        assertEquals(MinZoomSamples, w!!.last - w!!.first + 1)
    }

    /** La fenetre ne doit jamais sortir du parcours, quel que soit le point vise. */
    @Test fun `la fenetre reste dans le parcours`() {
        listOf(0f, 0.13f, 0.5f, 0.87f, 1f).forEach { f ->
            var w: IntRange? = null
            repeat(30) { w = ProfileZoom.window(w, total, scale = 1.3f, focusFraction = f) }
            assertTrue("debut negatif pour f=$f", w!!.first >= 0)
            assertTrue("fin hors parcours pour f=$f", w!!.last <= total - 1)
        }
    }

    /** Un parcours trop court n'a rien a grossir : on le laisse tel quel plutot que de rendre une plage
     *  degeneree. */
    @Test fun `un parcours trop court n'est pas zoome`() {
        assertNull(ProfileZoom.window(null, MinZoomSamples, scale = 3f, focusFraction = 0.5f))
    }

    /** Un facteur aberrant (division par zero d'un detecteur) ne doit pas casser la fenetre courante. */
    @Test fun `un facteur invalide laisse la fenetre inchangee`() {
        val w = ProfileZoom.window(null, total, scale = 2f, focusFraction = 0.5f)
        assertEquals(w, ProfileZoom.window(w, total, scale = 0f, focusFraction = 0.5f))
        assertEquals(w, ProfileZoom.window(w, total, scale = Float.NaN, focusFraction = 0.5f))
    }
}
