package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.data.db.CompositeEntity
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.map.compositeBasemapId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Quels fonds sont reellement sous les yeux, dans leur ordre d'empilement.
 *
 * Ce que le reglage nomme n'est pas toujours ce qu'on voit : il peut designer un COMPOSITE, qui empile
 * deux fonds. Et le relief n'est pas un fond, seulement un ombrage pose sur ce qui l'est.
 *
 * Ce qui se decide ici est la legende que le bouton "info" propose. Une legende qui ne decrit pas ce qu'on
 * regarde est pire qu'une absence de legende : elle a l'air juste.
 */
class DisplayedBasemapsTest {

    private fun fond(id: String, type: String = "XYZ") =
        ProviderEntity(id = id, name = id, groupName = "Monde", type = type, urlTemplate = "http://x/{z}/{x}/{y}")

    private val ign = fond("ign")
    private val osm = fond("osm")
    private val relief = fond("relief", type = "DEM")

    private fun reglages(basemapId: String) = SettingsEntity(defaultBasemapId = basemapId)

    // ---------- Le cas ordinaire ----------

    @Test fun `le fond nomme par les reglages est celui qu'on affiche`() {
        assertEquals(listOf(ign), displayedProviders(reglages("ign"), listOf(ign, osm), emptyList()))
    }

    /** Un reglage qui designe un fond disparu - desinstalle, ou d'une version anterieure - ne doit pas
     *  laisser la carte sans legende : on retombe sur le premier fond disponible. */
    @Test fun `un fond introuvable retombe sur le premier disponible`() {
        assertEquals(listOf(ign), displayedProviders(reglages("disparu"), listOf(ign, osm), emptyList()))
    }

    @Test fun `sans aucun fond, on n'annonce rien`() {
        assertEquals(emptyList<ProviderEntity>(),
            displayedProviders(reglages("ign"), emptyList(), emptyList()))
    }

    // ---------- Le relief n'est pas un fond ----------

    /** Le relief ne se suffit pas a lui-meme : l'annoncer comme fond affiche donnerait une legende qui ne
     *  decrit pas ce qu'on regarde. */
    @Test fun `le relief nomme par les reglages n'est pas annonce`() {
        assertEquals(listOf(ign), displayedProviders(reglages("relief"), listOf(relief, ign), emptyList()))
    }

    @Test fun `un catalogue de relief seul n'annonce rien`() {
        assertEquals(emptyList<ProviderEntity>(),
            displayedProviders(reglages("relief"), listOf(relief), emptyList()))
    }

    // ---------- Les composites ----------

    private fun composite(id: Long, bg: String, fg: String, enabled: Boolean = true) =
        CompositeEntity(id = id, name = "c$id", backgroundProviderId = bg,
            foregroundProviderId = fg, enabled = enabled)

    @Test fun `un composite allume annonce ses deux fonds, du dessous vers le dessus`() {
        val c = composite(3, bg = "ign", fg = "osm")
        assertEquals(listOf(ign, osm),
            displayedProviders(reglages(compositeBasemapId(3)), listOf(ign, osm), listOf(c)))
    }

    /** Eteint, il ne designe plus rien : on retombe sur le comportement ordinaire. */
    @Test fun `un composite eteint ne compte pas`() {
        val c = composite(3, bg = "ign", fg = "osm", enabled = false)
        assertEquals(listOf(ign),
            displayedProviders(reglages(compositeBasemapId(3)), listOf(ign, osm), listOf(c)))
    }

    /** Un composite dont le fond a disparu ne peut pas s'afficher : ce n'est plus un empilement. */
    @Test fun `un composite au fond disparu retombe sur le premier disponible`() {
        val c = composite(3, bg = "disparu", fg = "osm")
        assertEquals(listOf(ign),
            displayedProviders(reglages(compositeBasemapId(3)), listOf(ign, osm), listOf(c)))
    }

    /** Un composite dont le calque du dessus a disparu reste valable : le fond, lui, s'affiche. */
    @Test fun `un composite au calque disparu garde son fond`() {
        val c = composite(3, bg = "ign", fg = "disparu")
        assertEquals(listOf(ign),
            displayedProviders(reglages(compositeBasemapId(3)), listOf(ign, osm), listOf(c)))
    }

    /** Le relief est ecarte des deux cotes du composite, et pas seulement du reglage. */
    @Test fun `le relief est ecarte du composite aussi`() {
        assertEquals(listOf(ign),
            displayedProviders(reglages(compositeBasemapId(3)), listOf(ign, relief),
                listOf(composite(3, bg = "ign", fg = "relief"))))
        assertEquals(listOf(ign),
            displayedProviders(reglages(compositeBasemapId(3)), listOf(relief, ign),
                listOf(composite(3, bg = "relief", fg = "ign"))))
    }

    // ---------- Le composite actif ----------

    @Test fun `un identifiant ordinaire ne designe aucun composite`() {
        assertEquals(null, activeComposite(reglages("ign"), listOf(composite(3, "ign", "osm"))))
    }

    @Test fun `un composite absent de la liste ne se trouve pas`() {
        assertEquals(null, activeComposite(reglages(compositeBasemapId(9)), listOf(composite(3, "ign", "osm"))))
    }
}
