package fr.lc4918.trailog.map

import fr.lc4918.trailog.data.db.ProviderEntity
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Construction du style MapLibre. Sous Robolectric : StyleBuilder s'appuie sur org.json.
 *  Une faute ici donne une carte grise, sans message d'erreur. */
@RunWith(RobolectricTestRunner::class)
class StyleBuilderTest {
    private val dir = File("/tmp")
    private fun p(id: String, type: String, url: String, key: String? = null, opaque: Boolean = true) =
        ProviderEntity(id = id, name = id, groupName = "g", type = type, urlTemplate = url,
            apiKey = key, transparent = !opaque)

    private fun style(json: String?) = JSONObject(json!!)
    private fun layerIds(root: JSONObject) =
        (0 until root.getJSONArray("layers").length()).map { root.getJSONArray("layers").getJSONObject(it).getString("id") }

    @Test fun `un fond raster seul donne une source et une couche`() = runTest {
        val r = StyleBuilder.build(p("osm", "XYZ", "https://t/{z}/{x}/{y}.png"), emptyList(), null, dir)
        assertNull("un raster n'a pas d'URL de style", r.styleUrl)
        val root = style(r.styleJson)
        assertEquals(8, root.getInt("version"))
        assertEquals(listOf("no-tile-background", "base"), layerIds(root))
        assertTrue(root.getJSONObject("sources").has("base"))
    }

    /** Sans cette couche, les zones sans tuile restent au canevas nu de MapLibre, noir, sur lequel les
     *  boutons de la carte (noirs eux aussi) disparaissent. Elle doit passer sous toutes les autres. */
    @Test fun `une couche de fond claire passe sous toutes les autres`() = runTest {
        val r = StyleBuilder.build(p("osm", "XYZ", "https://t/{z}/{x}/{y}.png"),
            listOf(p("a", "XYZ", "https://a/{z}/{x}/{y}.png", opaque = false)), null, dir)
        val first = style(r.styleJson).getJSONArray("layers").getJSONObject(0)
        assertEquals("background", first.getString("type"))
        assertEquals("#E8E8E8", first.getJSONObject("paint").getString("background-color"))
    }

    /** Un style vectoriel seul se delegue a MapLibre par URL : rien a construire. */
    @Test fun `un fond vectoriel seul rend son URL de style`() = runTest {
        val r = StyleBuilder.build(p("ofm", "VECTOR", "https://tiles/styles/liberty"), emptyList(), null, dir)
        assertNull(r.styleJson)
        assertEquals("https://tiles/styles/liberty", r.styleUrl)
    }

    @Test fun `la cle API est resolue dans l'URL de style`() = runTest {
        val r = StyleBuilder.build(p("mt", "VECTOR", "https://api/style.json?key={KEY}", key = "abc"), emptyList(), null, dir)
        assertEquals("https://api/style.json?key=abc", r.styleUrl)
    }

    /** Des qu'il y a une surcouche, le vectoriel ne peut plus etre delegue : il faut fusionner un style. */
    @Test fun `un vectoriel avec surcouche construit un style au lieu de le deleguer`() = runTest {
        val r = StyleBuilder.build(p("ofm", "VECTOR", "https://tiles/styles/liberty"),
            listOf(p("way", "XYZ", "https://o/{z}/{x}/{y}.png", opaque = false)), null, dir)
        assertNull(r.styleUrl)
        assertNotNull(r.styleJson)
    }

    @Test fun `les surcouches se superposent au fond dans l'ordre`() = runTest {
        val r = StyleBuilder.build(
            p("osm", "XYZ", "https://t/{z}/{x}/{y}.png"),
            listOf(p("a", "XYZ", "https://a/{z}/{x}/{y}.png", opaque = false),
                   p("b", "XYZ", "https://b/{z}/{x}/{y}.png", opaque = false)),
            null, dir)
        assertEquals(listOf("no-tile-background", "base", "ov_0", "ov_1"), layerIds(style(r.styleJson)))
    }

    @Test fun `l'opacite d'une surcouche se retrouve dans le style`() = runTest {
        val r = StyleBuilder.build(p("osm", "XYZ", "https://t/{z}/{x}/{y}.png"),
            listOf(p("a", "XYZ", "https://a/{z}/{x}/{y}.png", opaque = false)), null, dir,
            overlayOpacities = mapOf("a" to 0.5f))
        val ov = style(r.styleJson).getJSONArray("layers").getJSONObject(2)
        assertEquals(0.5, ov.getJSONObject("paint").getDouble("raster-opacity"), 1e-6)
    }

    /** Une opacite pleine n'ecrit rien : le defaut de MapLibre vaut deja 1. */
    @Test fun `une surcouche opaque n'ecrit pas d'opacite`() = runTest {
        val r = StyleBuilder.build(p("osm", "XYZ", "https://t/{z}/{x}/{y}.png"),
            listOf(p("a", "XYZ", "https://a/{z}/{x}/{y}.png", opaque = false)), null, dir,
            overlayOpacities = mapOf("a" to 1f))
        val ov = style(r.styleJson).getJSONArray("layers").getJSONObject(2)
        assertTrue(!ov.has("paint") || !ov.getJSONObject("paint").has("raster-opacity"))
    }

    @Test fun `le relief ajoute sa source et sa couche hillshade`() = runTest {
        val r = StyleBuilder.build(p("osm", "XYZ", "https://t/{z}/{x}/{y}.png"), emptyList(),
            p("dem", "DEM", "https://d/{z}/{x}/{y}.png"), dir)
        val root = style(r.styleJson)
        assertTrue("hillshade" in layerIds(root))
        assertTrue(root.getJSONObject("sources").has("dem"))
        assertEquals("raster-dem", root.getJSONObject("sources").getJSONObject("dem").getString("type"))
    }

    /** Le relief se pose au-dessus du fond ET des surcouches. */
    @Test fun `le relief passe en dernier`() = runTest {
        val r = StyleBuilder.build(p("osm", "XYZ", "https://t/{z}/{x}/{y}.png"),
            listOf(p("a", "XYZ", "https://a/{z}/{x}/{y}.png", opaque = false)),
            p("dem", "DEM", "https://d/{z}/{x}/{y}.png"), dir)
        assertEquals("hillshade", layerIds(style(r.styleJson)).last())
    }

    @Test fun `un fond WMS garde son gabarit bbox dans la source`() = runTest {
        val r = StyleBuilder.build(p("es", "WMS", "https://w?BBOX={bbox-epsg-3857}"), emptyList(), null, dir)
        val src = style(r.styleJson).getJSONObject("sources").getJSONObject("base")
        assertTrue(src.getJSONArray("tiles").getString(0).contains("{bbox-epsg-3857}"))
    }

    @Test fun `le style produit est du JSON valide`() = runTest {
        val r = StyleBuilder.build(p("osm", "XYZ", "https://t/{z}/{x}/{y}.png"),
            listOf(p("a", "XYZ", "https://a/{z}/{x}/{y}.png", opaque = false)),
            p("dem", "DEM", "https://d/{z}/{x}/{y}.png"), dir)
        JSONObject(r.styleJson!!)   // leve si invalide
    }
}
