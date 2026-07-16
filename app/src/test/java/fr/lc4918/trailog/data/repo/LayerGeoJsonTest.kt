package fr.lc4918.trailog.data.repo

import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PropType
import fr.lc4918.trailog.domain.model.PropValue
import fr.lc4918.trailog.domain.model.SchemaItem
import fr.lc4918.trailog.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GeoJSON stocke sur disque : c'est le format de persistance des couches. Une regression ici rend
 *  illisibles des traces deja importees. */
class LayerGeoJsonTest {
    private fun pt(lon: Double, lat: Double, ele: Double? = null, t: Long? = null) =
        TrackPoint(lon = lon, lat = lat, ele = ele, timeMs = t)

    private val ligne = listOf(pt(2.0, 43.0, 100.0, 1000L), pt(2.1, 43.1, 150.0, 2000L))
    private val point = PointFeature("f0", 2.0, 43.0, linkedMapOf(
        "name" to PropValue.Text("Sommet"),
        "url" to PropValue.Link("Site", "https://x"),
        "photo" to PropValue.Image("/a/b.jpg"),
    ))

    @Test fun `une trace fait l'aller-retour sans perte`() {
        val r = LayerGeoJson.parse(LayerGeoJson.write(emptyList(), listOf(ligne)))
        assertEquals(1, r.lines.size)
        assertEquals(2, r.lines[0].size)
        assertEquals(2.0, r.lines[0][0].lon, 1e-9)
        assertEquals(43.0, r.lines[0][0].lat, 1e-9)
        assertEquals(100.0, r.lines[0][0].ele!!, 1e-9)
        assertEquals(1000L, r.lines[0][0].timeMs)
    }

    @Test fun `un point et ses trois types de propriete font l'aller-retour`() {
        val r = LayerGeoJson.parse(LayerGeoJson.write(listOf(point), emptyList()))
        assertEquals(1, r.points.size)
        val p = r.points[0]
        assertEquals(2.0, p.lon, 1e-9)
        assertEquals(PropValue.Text("Sommet"), p.props["name"])
        assertEquals(PropValue.Link("Site", "https://x"), p.props["url"])
        assertEquals(PropValue.Image("/a/b.jpg"), p.props["photo"])
    }

    @Test fun `l'ordre des proprietes est preserve`() {
        val r = LayerGeoJson.parse(LayerGeoJson.write(listOf(point), emptyList()))
        assertEquals(listOf("name", "url", "photo"), r.points[0].props.keys.toList())
    }

    @Test fun `l'image de garde epinglee survit a l'aller-retour`() {
        val avec = point.copy(pinnedImageKey = "photo")
        val r = LayerGeoJson.parse(LayerGeoJson.write(listOf(avec), emptyList()))
        assertEquals("photo", r.points[0].pinnedImageKey)
    }

    @Test fun `une trace 2D sans temps s'ecrit sans altitude ni horodatage`() {
        val plate = listOf(pt(2.0, 43.0), pt(2.1, 43.1))
        val r = LayerGeoJson.parse(LayerGeoJson.write(emptyList(), listOf(plate)))
        assertNull(r.lines[0][0].ele)
        assertNull(r.lines[0][0].timeMs)
    }

    @Test fun `points et traces cohabitent dans un meme fichier`() {
        val r = LayerGeoJson.parse(LayerGeoJson.write(listOf(point), listOf(ligne)))
        assertEquals(1, r.points.size)
        assertEquals(1, r.lines.size)
    }

    @Test fun `une couche vide s'ecrit et se relit`() {
        val r = LayerGeoJson.parse(LayerGeoJson.write(emptyList(), emptyList()))
        assertTrue(r.points.isEmpty() && r.lines.isEmpty())
    }

    @Test fun `le schema fait l'aller-retour`() {
        val s = listOf(SchemaItem("name", PropType.TEXT), SchemaItem("photo", PropType.IMAGE),
            SchemaItem("url", PropType.LINK))
        assertEquals(s, LayerGeoJson.parseSchema(LayerGeoJson.writeSchema(s)).toList())
    }

    @Test fun `un schema absent ou illisible rend une liste vide plutot que de planter`() {
        assertTrue(LayerGeoJson.parseSchema(null).isEmpty())
        assertTrue(LayerGeoJson.parseSchema("").isEmpty())
        assertTrue(LayerGeoJson.parseSchema("pas du json").isEmpty())
        assertTrue(LayerGeoJson.parseSchema("{}").isEmpty())
    }

    /** Le .map est le fichier que lit MapLibre : la simplification y allege la geometrie sans toucher
     *  au fichier source, qui reste la reference pour le profil et les stats. */
    @Test fun `la simplification allege le rendu sans deformer la trace`() {
        // ligne quasi droite : les points intermediaires n'apportent rien au trace
        val dense = (0..200).map { pt(2.0 + it * 0.0001, 43.0) }
        val simplifie = LayerGeoJson.parse(LayerGeoJson.writeForMap(emptyList(), listOf(dense), simplify = true))
        val brut = LayerGeoJson.parse(LayerGeoJson.writeForMap(emptyList(), listOf(dense), simplify = false))
        assertEquals(201, brut.lines[0].size)
        assertTrue("la simplification doit retirer des points", simplifie.lines[0].size < brut.lines[0].size)
        // extremites conservees : sinon la trace serait raccourcie a l'ecran
        assertEquals(dense.first().lon, simplifie.lines[0].first().lon, 1e-9)
        assertEquals(dense.last().lon, simplifie.lines[0].last().lon, 1e-9)
    }

    @Test fun `le rendu conserve les points comme le fichier source`() {
        val r = LayerGeoJson.parse(LayerGeoJson.writeForMap(listOf(point), emptyList()))
        assertEquals(1, r.points.size)
    }

    @Test fun `un geojson etranger sans nos conventions se lit quand meme`() {
        val brut = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[2.0,43.0]},"properties":{"name":"X"}},
              {"type":"Feature","geometry":{"type":"LineString","coordinates":[[2.0,43.0],[2.1,43.1]]},"properties":{}}
            ]}
        """.trimIndent()
        val r = LayerGeoJson.parse(brut)
        assertEquals(1, r.points.size)
        assertEquals(1, r.lines.size)
        assertEquals(PropValue.Text("X"), r.points[0].props["name"])
    }

    @Test fun `un MultiLineString donne plusieurs traces`() {
        val r = LayerGeoJson.parse("""
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"MultiLineString",
               "coordinates":[[[2.0,43.0],[2.1,43.1]],[[3.0,44.0],[3.1,44.1]]]},"properties":{}}]}
        """.trimIndent())
        assertEquals(2, r.lines.size)
    }
}
