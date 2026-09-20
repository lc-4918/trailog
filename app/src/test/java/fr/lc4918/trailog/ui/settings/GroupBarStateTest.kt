package fr.lc4918.trailog.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le titre de groupe qui reste sous les onglets : lequel afficher, et quand se taire.
 *
 * Le defaut vise est celui qu'on ne voit pas en developpant sur un grand ecran : le titre du groupe
 * PRECEDENT reste affiche alors qu'on lit deja le suivant.
 */
class GroupBarStateTest {

    private fun etat(viewport: Float, vararg titres: Pair<String, Float>) = GroupBarState().apply {
        viewportTop = viewport
        barHeight = 26f
        titres.forEach { (t, y) -> report(t, y) }
    }

    /** Tant que le premier titre est visible, la barre se tait : il est deja sous les yeux. */
    @Test fun `aucun titre passe, aucune barre`() {
        val s = etat(100f, "Boutons et gestes" to 140f, "GPS" to 600f)
        assertNull(s.current)
    }

    /** La barre etant posee par-dessus, elle prend le relais des que le titre a fini de passer derriere. */
    @Test fun `le relais se fait quand le titre passe derriere la barre`() {
        val s = etat(100f, "Boutons et gestes" to 140f)
        assertNull("le titre se lit encore sous la barre", s.current)
        s.report("Boutons et gestes", 120f)
        assertEquals("Boutons et gestes", s.current)
    }

    /** Le dernier titre passe au-dessus du bord : c'est celui dont on lit les lignes. */
    @Test fun `le dernier titre passe est celui qu'on lit`() {
        val s = etat(100f, "Boutons et gestes" to -40f, "GPS" to 60f, "Points d'interet" to 500f)
        assertEquals("GPS", s.current)
    }

    /** Un groupe qui disparait - le mode expert en cache plusieurs - ne doit plus compter. */
    @Test fun `un groupe oublie ne s'affiche plus`() {
        val s = etat(100f, "Boutons et gestes" to -40f, "GPS" to 60f)
        s.forget("GPS")
        assertEquals("Boutons et gestes", s.current)
    }

    /** Remonter tout en haut rend la barre inutile : le premier titre est revenu. */
    @Test fun `remonter fait disparaitre la barre`() {
        val s = etat(100f, "Boutons et gestes" to -40f)
        s.report("Boutons et gestes", 140f)
        assertNull(s.current)
    }
}
