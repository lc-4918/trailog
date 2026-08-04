package fr.lc4918.trailog.domain.model

import fr.lc4918.trailog.data.db.SettingsEntity
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

    /** Defaut d'une installation neuve : bas-gauche, et non le AUTO historique. La valeur transite par
     *  BubblePosition.of, qui retombe silencieusement sur AUTO : une cle mal ecrite dans SettingsEntity
     *  ne se verrait qu'a l'usage, sur une installation neuve. */
    @Test fun `le defaut des reglages est bas-gauche`() {
        assertEquals("bottom_left", SettingsEntity().bubblePosition)
        assertEquals(BubblePosition.BOTTOM_LEFT, BubblePosition.of(SettingsEntity().bubblePosition))
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
