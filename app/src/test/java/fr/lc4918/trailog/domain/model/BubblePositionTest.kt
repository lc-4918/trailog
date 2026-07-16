package fr.lc4918.trailog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BubblePositionTest {
    /** La cle est persistee en base (settings.bubblePosition) : la changer casserait les reglages. */
    @Test fun `chaque position a une cle stable et unique`() {
        val keys = BubblePosition.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals("auto", BubblePosition.AUTO.key)
        assertEquals("bottom_right", BubblePosition.BOTTOM_RIGHT.key)
        assertEquals(10, BubblePosition.entries.size)     // AUTO + grille 3x3
    }

    @Test fun `of retrouve chaque position par sa cle`() {
        BubblePosition.entries.forEach { assertEquals(it, BubblePosition.of(it.key)) }
    }

    /** Valeur inconnue ou absente : on retombe sur AUTO plutot que de planter (base d'une version future,
     *  reglage jamais ecrit, ou valeur corrompue). */
    @Test fun `of retombe sur AUTO si la cle est inconnue ou nulle`() {
        assertEquals(BubblePosition.AUTO, BubblePosition.of(null))
        assertEquals(BubblePosition.AUTO, BubblePosition.of(""))
        assertEquals(BubblePosition.AUTO, BubblePosition.of("nord-nord-ouest"))
        assertEquals(BubblePosition.AUTO, BubblePosition.of("TOP_LEFT"))   // casse differente
    }
}
