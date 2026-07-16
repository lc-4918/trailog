package fr.lc4918.trailog.map

import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.data.seed.Providers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BasemapIconsTest {
    private fun p(id: String, group: String) =
        ProviderEntity(id = id, name = id, groupName = group, type = "XYZ", urlTemplate = "https://x")

    @Test fun `un fond national rend son code pays`() {
        assertEquals("fr", flagCodeFor(p("ign_fr", "Pays")))
        assertEquals("hr", flagCodeFor(p("hr", "Pays")))
    }

    /** Hors du groupe "Pays", pas de drapeau : le gestionnaire affiche un globe generique. */
    @Test fun `aucun drapeau hors du groupe Pays`() {
        assertNull(flagCodeFor(p("osm", "Monde")))
        assertNull(flagCodeFor(p("ign_fr", "Monde")))     // meme id, mauvais groupe
        assertNull(flagCodeFor(p("inconnu", "Pays")))
    }

    /** La table des drapeaux est tenue a la main : un fond "Pays" ajoute sans son entree passerait
     *  silencieusement au globe generique. */
    @Test fun `chaque fond du groupe Pays a son drapeau`() {
        Providers.defaults().filter { it.groupName == "Pays" }.forEach {
            assertTrue("${it.id} : aucun drapeau declare", flagCodeFor(it) != null)
        }
    }

    @Test fun `les assets pointes existent bien`() {
        assertEquals("file:///android_asset/flags/fr.svg", flagAssetModel("fr"))
        assertEquals("file:///android_asset/legends/af3v.png", legendAssetModel("legends/af3v.png"))
    }
}
