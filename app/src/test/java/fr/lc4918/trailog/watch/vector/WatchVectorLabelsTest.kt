package fr.lc4918.trailog.watch.vector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import fr.lc4918.trailog.watch.vector.WatchVectorLabels.Kind
import fr.lc4918.trailog.watch.vector.WatchVectorLabels.Label

class WatchVectorLabelsTest {

    /** Une police a chasse fixe : 0,6 fois la taille par caractere. */
    private val measure = { text: String, size: Float -> text.length * size * 0.6f }
    private val measureStyled = { text: String, size: Float, _: Boolean, _: Boolean -> measure(text, size) }

    private fun street(text: String, vararg points: Pair<Float, Float>, size: Float = 7.5f) =
        Label(Kind.STREET, text, listOf(points.toList()), size, bold = true, italic = false, color = 0)

    private fun label(kind: Kind, text: String, x: Float, y: Float) =
        Label(kind, text, listOf(listOf(x to y)), 9f, bold = false, italic = true, color = 0)

    @Test fun `a name that fits is written as it is`() {
        val fitted = WatchVectorLabels.fit("RUE DE METZ", 7.5f, 100f, measure)!!
        assertEquals("RUE DE METZ", fitted.first)
        assertEquals(7.5f, fitted.second, 0.001f)
    }

    @Test fun `a name slightly too long is written smaller`() {
        // 11 caracteres a 7,5 px : 49,5 ; a 85 % : 42,1. Sur 50 px (marge de 10 %), seule la taille reduite passe.
        val fitted = WatchVectorLabels.fit("RUE DE METZ", 7.5f, 50f, measure)!!
        assertEquals("RUE DE METZ", fitted.first)
        assertEquals(7.5f * 0.85f, fitted.second, 0.001f)
    }

    @Test fun `a long name is cut at a whole word with a final dot`() {
        val fitted = WatchVectorLabels.fit("ROUTE DU FEU DE LA CALANQUE DE MORGIOU", 7.5f, 100f, measure)!!
        assertTrue(fitted.first.endsWith("."))
        assertTrue(fitted.first.length < "ROUTE DU FEU DE LA CALANQUE DE MORGIOU".length)
        assertTrue(measure(fitted.first, fitted.second) * 1.1f <= 100f)
        assertTrue("ROUTE DU FEU DE LA CALANQUE DE MORGIOU".startsWith(fitted.first.removeSuffix(".")))
    }

    @Test fun `a cut name never ends on a linking word`() {
        for (available in 30..200 step 5) {
            val fitted = WatchVectorLabels.fit("ROUTE DE LA GRANDE FORET DU ROI", 7.5f, available.toFloat(), measure) ?: continue
            val lastWord = fitted.first.removeSuffix(".").split(" ").last().lowercase()
            assertTrue("$available px : ${fitted.first}", lastWord !in setOf("de", "du", "des", "la", "le", "les", "et", "au"))
        }
    }

    @Test fun `a name too short to keep six characters is dropped`() {
        assertNull(WatchVectorLabels.fit("AV DE LA REPUBLIQUE", 7.5f, 14f, measure))
        assertNull(WatchVectorLabels.fit("ROUTE", 7.5f, 5f, measure))
    }

    @Test fun `the straightest part of a bent street is chosen`() {
        // Une rue qui tourne en equerre : 60 px vers l'est puis 60 px vers le sud. Un texte de 30 px se pose sur l'un des deux bras.
        val dense = WatchVectorLabels.densify(listOf(0f to 0f, 60f to 0f, 60f to 60f))
        val start = WatchVectorLabels.straightestWindow(dense, 30)
        val a = dense[start]
        val b = dense[start + 30]
        assertTrue("fenetre droite : $a -> $b", (a.second == b.second) || (a.first == b.first))
    }

    @Test fun `text is upright whatever the direction of the street`() {
        assertEquals(0f, WatchVectorLabels.readableAngle(1f, 0f), 0.01f)
        assertEquals(0f, WatchVectorLabels.readableAngle(-1f, 0f), 0.01f)
        assertEquals(45f, WatchVectorLabels.readableAngle(1f, 1f), 0.01f)
        assertEquals(45f, WatchVectorLabels.readableAngle(-1f, -1f), 0.01f)
        assertEquals(-45f, WatchVectorLabels.readableAngle(-1f, 1f), 0.01f)
        assertEquals(90f, WatchVectorLabels.readableAngle(0f, 1f), 0.01f)
    }

    @Test fun `a street name is centred on its street`() {
        val placed = WatchVectorLabels.place(listOf(street("RUE DE METZ", 10f to 40f, 110f to 40f)), 128f, measureStyled).single()
        assertEquals("RUE DE METZ", placed.text)
        assertEquals(60f, placed.x, 6f)
        assertEquals(40f, placed.y, 0.5f)
        assertEquals(0f, placed.angleDegrees, 0.01f)
    }

    @Test fun `the same street seen from two source tiles is written once`() {
        val first = street("RUE DE METZ", 10f to 40f, 110f to 40f)
        val second = street("RUE DE METZ", 12f to 41f, 112f to 41f)
        assertEquals(1, WatchVectorLabels.place(listOf(first, second), 128f, measureStyled).size)
    }

    @Test fun `a text overlapping a placed one is dropped`() {
        val labels = listOf(street("RUE DE METZ", 10f to 40f, 110f to 40f), street("RUE DE LA POMME", 10f to 44f, 110f to 44f))
        val placed = WatchVectorLabels.place(labels, 128f, measureStyled)
        assertEquals(listOf("RUE DE METZ"), placed.map { it.text })
    }

    @Test fun `locality and building names are centred on their point`() {
        val placed = WatchVectorLabels.place(listOf(label(Kind.LOCALITY, "Alsace", 64f, 30f), label(Kind.BUILDING, "Musee", 20f, 100f)), 128f, measureStyled)
        assertEquals(2, placed.size)
        assertEquals(64f, placed[0].x, 0.001f)
        assertEquals(0f, placed[0].angleDegrees, 0.001f)
        assertNotNull(placed.firstOrNull { it.text == "Musee" })
    }

    @Test fun `a text far outside the tile is not kept`() {
        val placed = WatchVectorLabels.place(listOf(label(Kind.LOCALITY, "Loin", 900f, 30f)), 128f, measureStyled)
        assertTrue(placed.isEmpty())
    }
}
