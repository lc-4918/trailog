package fr.lc4918.trailog.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapHeadOverlaysTest {

    @Test
    fun `aucun calque de tete - rien a passer dessous`() {
        assertNull(MapHeadOverlays.lowest(listOf("base", "trace-ln", "trace-pt")))
    }

    @Test
    fun `le repere GPS pose, une trace enregistree ensuite passe dessous`() {
        // L'ordre du style va du bas vers le haut : le GPS est allume avant que la trace n'arrive.
        val style = listOf("base", MapHeadOverlays.USER_ACCURACY, MapHeadOverlays.USER_DOT)
        assertEquals(MapHeadOverlays.USER_ACCURACY, MapHeadOverlays.lowest(style))
    }

    @Test
    fun `le plus bas des calques de tete, et non le premier de la liste des identifiants`() {
        val style = listOf("base", MapHeadOverlays.CURSOR_DOT, MapHeadOverlays.USER_DOT)
        assertEquals(MapHeadOverlays.CURSOR_DOT, MapHeadOverlays.lowest(style))
    }

    @Test
    fun `le curseur du profil seul compte aussi`() {
        assertEquals(
            MapHeadOverlays.CURSOR_DOT,
            MapHeadOverlays.lowest(listOf("base", "trace-ln", MapHeadOverlays.CURSOR_DOT)),
        )
    }
}
