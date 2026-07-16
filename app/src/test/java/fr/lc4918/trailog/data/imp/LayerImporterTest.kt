package fr.lc4918.trailog.data.imp

import fr.lc4918.trailog.domain.model.PropValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Parsing des fichiers importes. Sous Robolectric : LayerImporter s'appuie sur android.util.Xml.
 * Les fichiers de "resources/fichiers" sont de VRAIS exports (Wikiloc, OruxMaps, Locus, Google Earth),
 * pas des extraits fabriques : c'est la seule facon d'attraper les particularites de chaque producteur.
 */
@RunWith(RobolectricTestRunner::class)
class LayerImporterTest {
    private fun bytes(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("fichiers/$name")!!.readBytes()

    private fun parse(name: String) = LayerImporter.parse(bytes(name), name)

    // ---------- GPX ----------

    @Test fun `gpx wikiloc 3D avec temps`() {
        val l = parse("wikiloc_soreze-fontbruno-arfons-berniquaut.gpx")
        assertEquals(1, l.lines.size)
        assertEquals(1498, l.lines[0].size)
        assertTrue("nom repris du gpx", l.name.contains("Soreze"))
        assertTrue("altitude presente", l.lines[0].all { it.ele != null })
        assertTrue("horodatage present", l.lines[0].all { it.timeMs != null })
        assertEquals("waypoints", 25, l.points.size)
    }

    @Test fun `gpx wikiloc, coordonnees plausibles`() {
        val p = parse("wikiloc_soreze-fontbruno-arfons-berniquaut.gpx").lines[0]
        assertTrue(p.all { it.lat in 43.0..44.0 && it.lon in 1.5..2.5 })   // Montagne Noire
        assertTrue(p.all { it.ele!! in 100.0..1500.0 })
    }

    /** Le temps doit croitre : un parsing qui melangerait les fuseaux ou l'ordre casserait les stats. */
    @Test fun `gpx wikiloc, horodatage croissant`() {
        val p = parse("wikiloc_soreze-fontbruno-arfons-berniquaut.gpx").lines[0]
        assertTrue(p.zipWithNext().all { (a, b) -> b.timeMs!! >= a.timeMs!! })
    }

    @Test fun `gpx oruxmaps 3D avec temps et waypoints`() {
        val l = parse("orux_test.gpx")
        assertEquals(1, l.lines.size)
        assertEquals(2742, l.lines[0].size)
        assertEquals(6, l.points.size)
        assertTrue(l.lines[0].all { it.ele != null && it.timeMs != null })
    }

    /** Cas 2D de la spec : Locus n'exporte aucune balise <ele>. C'est ce fichier qui doit declencher
     *  la banniere "Parcours sans altimetrie". */
    @Test fun `gpx locus 2D sans altitude`() {
        val l = parse("test_locus.gpx")
        assertEquals(1, l.lines.size)
        assertEquals(1372, l.lines[0].size)
        assertTrue("aucune altitude attendue", l.lines[0].all { it.ele == null })
        assertEquals(1, l.points.size)
    }

    // ---------- KML / KMZ ----------

    @Test fun `kml lu et non vide`() {
        val l = parse("test.kml")
        assertTrue("au moins une trace", l.lines.isNotEmpty())
        assertTrue("geometrie non vide", l.lines.any { it.size > 1 })
    }

    /** Le KMZ est un zip : l'importeur doit en extraire le doc.kml et le parser comme un KML. */
    @Test fun `kmz produit la meme couche que son kml`() {
        val kml = parse("test.kml")
        val kmz = parse("test.kmz")
        assertEquals(kml.lines.size, kmz.lines.size)
        assertEquals(kml.lines.sumOf { it.size }, kmz.lines.sumOf { it.size })
        assertEquals(kml.points.size, kmz.points.size)
    }

    // ---------- GeoJSON ----------

    @Test fun `geojson FeatureCollection`() {
        val l = LayerImporter.parse("""
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[2.0,43.0]},"properties":{"name":"Col"}},
              {"type":"Feature","geometry":{"type":"LineString","coordinates":[[2.0,43.0],[2.1,43.1]]},"properties":{}}]}
        """.trimIndent().toByteArray(), "a.geojson")
        assertEquals(1, l.points.size)
        assertEquals(1, l.lines.size)
        assertEquals(PropValue.Text("Col"), l.points[0].props["name"])
    }

    /** Fichier etranger : une Feature seule, hors collection, doit passer (contrairement au lecteur du
     *  stockage LayerGeoJson, qui ne relit que ce que l'app a ecrit). */
    @Test fun `geojson Feature isolee hors collection`() {
        val l = LayerImporter.parse(
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[2.0,43.0]},"properties":{}}""".toByteArray(),
            "a.geojson")
        assertEquals(1, l.points.size)
    }

    // ---------- Fichiers refuses ----------

    @Test fun `gpx tronque leve une exception`() {
        val tronque = bytes("test_locus.gpx").copyOfRange(0, 5000)      // coupe en plein XML
        assertThrows(Exception::class.java) { LayerImporter.parse(tronque, "tronque.gpx") }
    }

    @Test fun `gpx au xml invalide leve une exception`() {
        val mauvais = """<?xml version="1.0"?><gpx><trk><trkseg><trkpt lat="43"></gpx>""".toByteArray()
        assertThrows(Exception::class.java) { LayerImporter.parse(mauvais, "mauvais.gpx") }
    }

    @Test fun `geojson mal forme leve une exception`() {
        assertThrows(Exception::class.java) { LayerImporter.parse("{ceci n'est pas du json".toByteArray(), "a.geojson") }
    }

    @Test fun `kml au xml invalide leve une exception`() {
        assertThrows(Exception::class.java) { LayerImporter.parse("ceci n'est pas du xml".toByteArray(), "a.kml") }
    }

    /**
     * LACUNE CONNUE, verifiee ici pour qu'elle ne derive pas en silence.
     * Un KML tronque mais syntaxiquement correct jusqu'a la coupure ne leve pas : le parseur XML atteint
     * la fin du flux sans se plaindre des balises non fermees. L'utilisateur lit donc "le fichier est
     * vide" la ou la spec demande "invalide". Un GPX tronque, lui, leve (cf. test ci-dessus), la coupure
     * tombant en general en plein attribut. Corriger demanderait de verifier que la racine s'est refermee.
     */
    @Test fun `kml tronque rend une couche vide au lieu de lever`() {
        val l = LayerImporter.parse("""<?xml version="1.0"?><kml><Document><Placemark>""".toByteArray(), "a.kml")
        assertTrue(l.lines.isEmpty() && l.points.isEmpty())
    }

    @Test fun `kmz sans kml a l'interieur leve une exception`() {
        val zipVide = java.io.ByteArrayOutputStream().also { bos ->
            java.util.zip.ZipOutputStream(bos).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("lisezmoi.txt")); z.write("rien".toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        assertThrows(Exception::class.java) { LayerImporter.parse(zipVide, "vide.kmz") }
    }

    @Test fun `kmz qui n'est pas un zip leve une exception`() {
        assertThrows(Exception::class.java) { LayerImporter.parse("pas un zip".toByteArray(), "faux.kmz") }
    }

    // ---------- Fichiers vides (lisibles, mais sans geometrie) ----------

    @Test fun `gpx valide mais sans trace ni point rend une couche vide`() {
        val vide = """<?xml version="1.0"?><gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"></gpx>"""
        val l = LayerImporter.parse(vide.toByteArray(), "vide.gpx")
        assertTrue(l.lines.isEmpty() && l.points.isEmpty())
    }

    @Test fun `geojson valide mais sans feature rend une couche vide`() {
        val l = LayerImporter.parse("""{"type":"FeatureCollection","features":[]}""".toByteArray(), "vide.geojson")
        assertTrue(l.lines.isEmpty() && l.points.isEmpty())
    }

    // ---------- Nom de couche ----------

    @Test fun `a defaut de nom dans le fichier, le nom du fichier sert de repli`() {
        val l = LayerImporter.parse("""{"type":"FeatureCollection","features":[
            {"type":"Feature","geometry":{"type":"Point","coordinates":[2.0,43.0]},"properties":{}}]}""".toByteArray(),
            "ma_rando.geojson")
        assertNotNull(l.name)
        assertFalse(l.name.isBlank())
    }
}
