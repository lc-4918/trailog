package fr.lc4918.trailog.ui.offline

import fr.lc4918.trailog.data.db.CompositeEntity
import fr.lc4918.trailog.data.db.ProviderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quels fonds se telechargent, et sous quel nom on les refuse.
 *
 * Le nom compte autant que le refus : "ce fond ne se telecharge pas" laisse chercher lequel, alors qu'on
 * en a plusieurs et qu'on vient peut-etre d'en changer.
 */
class OfflineAvailabilityTest {

    private fun fond(id: String, nom: String, type: String = "XYZ", url: String = "https://example.org/{z}/{x}/{y}.png") =
        ProviderEntity(id = id, name = nom, groupName = "Monde", type = type, urlTemplate = url)

    private val fonds = listOf(
        fond("ign", "IGN Plan"),
        fond("osm", "OpenStreetMap", url = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
        fond("local", "Rando 66", type = "MBTILES", url = "file:///rando.mbtiles"),
        fond("relief", "Relief", type = "DEM"),
    )
    private val composites = listOf(CompositeEntity(id = 3, name = "IGN sur relief",
        backgroundProviderId = "relief", foregroundProviderId = "ign"))

    @Test fun `un fond en ligne se telecharge`() {
        assertTrue(offlineDownloadAvailable("ign", fonds))
    }

    /** Ni OpenStreetMap, dont les conditions d'usage l'interdisent, ni ce qui est deja sur le telephone. */
    @Test fun `les fonds qui ne se telechargent pas`() {
        assertFalse("OpenStreetMap", offlineDownloadAvailable("osm", fonds))
        assertFalse("un MBTiles deja la", offlineDownloadAvailable("local", fonds))
        assertFalse("le relief", offlineDownloadAvailable("relief", fonds))
        assertFalse("un fond inconnu", offlineDownloadAvailable("disparu", fonds))
    }

    @Test fun `le refus nomme le fond, fournisseur ou composite`() {
        assertEquals("OpenStreetMap", basemapLabel("osm", fonds, composites))
        assertEquals("IGN sur relief", basemapLabel("composite_3", fonds, composites))
    }

    /** Un fond introuvable ne donne pas son identifiant technique : l'appelant dit alors la phrase sans nom. */
    @Test fun `un fond introuvable n'a pas de nom`() {
        assertEquals("", basemapLabel("disparu", fonds, composites))
        assertEquals("", basemapLabel("composite_99", fonds, composites))
    }
}
