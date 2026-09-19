package fr.lc4918.trailog.ui.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ou tombe une etape qu'on glisse : au-dela de chaque voisine dont elle a franchi la moitie, et nulle part
 * ailleurs. Une regle relachee ici deposerait l'etape un rang trop tot ou trop tard - et changerait
 * l'itineraire sans que le geste l'ait voulu.
 */
class StepDragTest {

    private val ids = listOf(10L, 11L, 12L, 13L)

    private fun glisser(depuis: Int, dy: Float): StepDrag = StepDrag().apply {
        ids.forEach { heights[it] = 100 }
        start(depuis)
        move(dy, ids)
    }

    @Test fun `en deca de la moitie d'une voisine, la ligne reste a sa place`() {
        assertEquals(1, glisser(1, 49f).to)
        assertEquals(1, glisser(1, -49f).to)
    }

    @Test fun `passe la moitie, elle prend la place de la voisine`() {
        assertEquals(2, glisser(1, 50f).to)
        assertEquals(0, glisser(1, -50f).to)
    }

    @Test fun `un seul geste peut franchir plusieurs lignes`() {
        assertEquals(3, glisser(0, 260f).to)
        assertEquals(0, glisser(3, -260f).to)
    }

    @Test fun `la ligne ne sort pas de la liste`() {
        assertEquals(3, glisser(1, 5_000f).to)
        assertEquals(0, glisser(2, -5_000f).to)
    }

    /** Les lignes franchies glissent d'un cran vers la place laissee ; les autres ne bougent pas. */
    @Test fun `les voisines franchies se decalent d'une ligne`() {
        val d = glisser(0, 160f)
        assertEquals(2, d.to)
        assertEquals(160f, d.shift(0, ids))
        assertEquals(-100f, d.shift(1, ids))
        assertEquals(-100f, d.shift(2, ids))
        assertEquals(0f, d.shift(3, ids))
    }

    @Test fun `le lacher rend le deplacement, et remet tout a zero`() {
        val d = glisser(0, 160f)
        assertEquals(0 to 2, d.end())
        assertNull(d.from)
        assertEquals(0f, d.shift(1, ids))
    }

    @Test fun `revenir a sa place ne deplace rien`() {
        val d = glisser(2, 30f)
        assertNull(d.end())
    }

    /** Le deplacement franchit plusieurs rangs d'un coup, et l'itineraire suit l'ordre des points poses. */
    @Test fun `deposer une etape reordonne le trajet`() {
        val etat = RoutePlannerState()
        etat.addStep()
        val a = fr.lc4918.trailog.geocode.GeocodePlace("A", 5.0, 45.0)
        val b = fr.lc4918.trailog.geocode.GeocodePlace("B", 5.1, 45.1)
        val c = fr.lc4918.trailog.geocode.GeocodePlace("C", 5.2, 45.2)
        etat.choose(etat.steps[0], StepTarget.Place(a))
        etat.choose(etat.steps[1], StepTarget.Place(b))
        etat.choose(etat.steps[2], StepTarget.Place(c))
        val avant = etat.revision
        etat.moveStepTo(0, 2)
        assertEquals(listOf(StepTarget.Place(b), StepTarget.Place(c), StepTarget.Place(a)), etat.targets)
        assertEquals(avant + 1, etat.revision)
    }
}
