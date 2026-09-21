package fr.lc4918.trailog.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La trame des pentes, calquee sur celle d'OruxMaps, et son ecriture hexadecimale - celle que comprend la
 * feuille de style de la carte : une couleur mal ecrite n'y est pas rejetee, elle rend le trace invisible.
 */
class SlopeRampTest {

    @Test fun `la forme est un diese et six chiffres hexadecimaux majuscules`() {
        val hex = SlopeRamp.hexFor(4.0)
        assertTrue(hex, Regex("^#[0-9A-F]{6}$").matches(hex))
    }

    /** La teinte de la carte doit designer la meme pente que l'aire du profil. */
    @Test fun `la couleur ecrite est celle de la rampe`() {
        assertEquals(SlopeRamp.hex(SlopeRamp.colorFor(7.0)), SlopeRamp.hexFor(7.0))
    }

    /** Les reperes de la legende d'OruxMaps, releves a chaque graduation. */
    @Test fun `les graduations ont les couleurs d'OruxMaps`() {
        assertEquals("#EA3524", SlopeRamp.hexFor(25.0))
        assertEquals("#F2A33A", SlopeRamp.hexFor(10.0))
        assertEquals("#FFFF54", SlopeRamp.hexFor(1.0))
        assertEquals("#75FB4C", SlopeRamp.hexFor(0.0))
        assertEquals("#75FBFE", SlopeRamp.hexFor(-1.0))
        assertEquals("#4499F6", SlopeRamp.hexFor(-10.0))
        assertEquals("#0005F5", SlopeRamp.hexFor(-25.0))
    }

    /** Entre deux graduations, ce que montre la legende au milieu de l'intervalle, a une unite pres. */
    @Test fun `entre deux graduations la couleur s'interpole`() {
        val c = SlopeRamp.at(7.5)
        assertEquals(244.0, c.red * 255.0, 1.01)
        assertEquals(188.0, c.green * 255.0, 1.01)
        assertEquals(65.0, c.blue * 255.0, 1.01)
    }

    /** Le plat, d'un cote comme de l'autre du zero, est vert. */
    @Test fun `moins de 1 pourcent est du plat`() {
        assertEquals("#75FB4C", SlopeRamp.hexFor(0.99))
        assertEquals("#75FB4C", SlopeRamp.hexFor(-0.99))
    }

    /** Montee et descente ne se confondent plus : c'est tout l'objet de la trame. */
    @Test fun `le signe de la pente change la couleur`() {
        assertNotEquals(SlopeRamp.hexFor(6.0), SlopeRamp.hexFor(-6.0))
    }

    @Test fun `au-dela de 25 pourcent la couleur ne change plus`() {
        assertEquals(SlopeRamp.hexFor(25.0), SlopeRamp.hexFor(40.0))
        assertEquals(SlopeRamp.hexFor(-25.0), SlopeRamp.hexFor(-60.0))
    }

    /** Une pente prend la couleur du bas de sa classe ; la premiere classe commence au seuil du plat. */
    @Test fun `une pente se range au bas de sa classe`() {
        assertEquals(3.5, SlopeRamp.classOf(3.7, 5), 1e-9)
        assertEquals(5.0, SlopeRamp.classOf(7.4, 25), 1e-9)
        assertEquals(1.0, SlopeRamp.classOf(4.9, 50), 1e-9)
        assertEquals(-10.0, SlopeRamp.classOf(-12.0, 50), 1e-9)
        assertEquals(0.0, SlopeRamp.classOf(0.5, 50), 1e-9)
        assertEquals(25.0, SlopeRamp.classOf(31.0, 10), 1e-9)
    }

    /** Une borne exacte appartient a la classe qu'elle ouvre, malgre l'arrondi des flottants. */
    @Test fun `une borne exacte ouvre sa classe`() {
        assertEquals(7.0, SlopeRamp.classOf(7.0, 5), 1e-9)
        assertEquals(15.0, SlopeRamp.classOf(15.0, 25), 1e-9)
    }

    /** La legende : les classes de la plus forte montee a la plus forte descente, le plat au milieu. */
    @Test fun `les bandes de la legende vont de 25 a -25`() {
        val b = SlopeRamp.bands(50)
        assertEquals(listOf(25.0, 20.0, 15.0, 10.0, 5.0, 1.0, 0.0, -1.0, -5.0, -10.0, -15.0, -20.0, -25.0), b.map { it.first })
        // A 0,5 %, la trame d'OruxMaps : 49 classes par sens, et le plat.
        assertEquals(49 * 2 + 1, SlopeRamp.bands(5).size)
    }
}
