package fr.lc4918.trailog.ui.routes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VanishedLayerTest {
    @Test fun `la couche montree supprimee de la bibliotheque a disparu`() {
        assertTrue(VanishedLayer.vanished(2L, setOf(1L, 2L), setOf(1L)))
    }

    @Test fun `la couche montree encore listee reste`() {
        assertFalse(VanishedLayer.vanished(2L, setOf(1L, 2L), setOf(1L, 2L, 3L)))
    }

    @Test fun `une autre couche supprimee ne ferme rien`() {
        assertFalse(VanishedLayer.vanished(2L, setOf(1L, 2L), setOf(2L)))
    }

    @Test fun `une couche pas encore listee n'a pas disparu`() {
        assertFalse(VanishedLayer.vanished(2L, emptySet(), emptySet()))
    }

    @Test fun `sans panneau ouvert rien ne disparait`() {
        assertFalse(VanishedLayer.vanished(null, setOf(1L), emptySet()))
    }
}
