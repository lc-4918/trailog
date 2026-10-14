package fr.lc4918.trailog.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Le choix d'une trace parmi celles qui se confondent sous le doigt : la plus basse d'abord, puis chacune a son tour. */
class PickAmongOverlappingTest {

    private val topFirst = listOf("blue", "green", "red")

    @Test fun `sans trace montree on prend la plus basse`() {
        assertEquals("red", pickAmongOverlapping(topFirst, null))
    }

    @Test fun `une trace montree absente des candidates donne aussi la plus basse`() {
        assertEquals("red", pickAmongOverlapping(topFirst, "other"))
    }

    @Test fun `chaque tap passe a la couche qui suit celle qui est montree`() {
        assertEquals("green", pickAmongOverlapping(topFirst, "blue"))
        assertEquals("red", pickAmongOverlapping(topFirst, "green"))
    }

    @Test fun `apres la plus basse on reboucle sur la plus haute`() {
        assertEquals("blue", pickAmongOverlapping(topFirst, "red"))
    }

    @Test fun `aucune candidate ne donne rien`() {
        assertNull(pickAmongOverlapping(emptyList(), "red"))
    }
}
