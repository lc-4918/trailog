package fr.lc4918.trailog.data.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Integrite des composites livres avec l'app. Un composite ne porte que des identifiants de
 *  fournisseurs : une faute de frappe n'echoue nulle part, elle donne une couche muette a l'ecran. */
class CompositesTest {
    private val all = Composites.defaults()
    private val providers = Providers.defaults().associateBy { it.id }

    @Test fun `les deux couches referencent des fonds du catalogue`() {
        all.forEach {
            assertTrue("${it.name} : arriere-plan ${it.backgroundProviderId} inconnu",
                it.backgroundProviderId in providers)
            assertTrue("${it.name} : premier plan ${it.foregroundProviderId} inconnu",
                it.foregroundProviderId in providers)
        }
    }

    /** Un premier plan opaque masquerait entierement son arriere-plan : le composite n'aurait plus d'objet. */
    @Test fun `le premier plan est une surcouche transparente`() {
        all.forEach {
            val fg = providers.getValue(it.foregroundProviderId)
            assertTrue("${it.name} : ${fg.id} n'est pas transparent", fg.transparent)
        }
    }

    @Test fun `les opacites sont dans la plage attendue`() {
        all.forEach { assertTrue("${it.name} : ${it.foregroundOpacity}", it.foregroundOpacity in 0f..1f) }
    }

    @Test fun `les ordres de tri sont uniques et contigus`() {
        val orders = all.map { it.sortOrder }.sorted()
        assertEquals((0 until all.size).toList(), orders)
    }

    /** Composite semé par défaut : l'AF3V par-dessus Mapbox Outdoors, a pleine opacite. */
    @Test fun `le composite af3v est seme tel quel`() {
        val c = all.single()
        assertEquals("mapbox_outdoors", c.backgroundProviderId)
        assertEquals("af3v", c.foregroundProviderId)
        assertEquals(1f, c.foregroundOpacity, 0f)
        assertTrue(c.enabled)
    }
}
