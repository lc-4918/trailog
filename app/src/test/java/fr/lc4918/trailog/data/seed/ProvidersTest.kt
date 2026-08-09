package fr.lc4918.trailog.data.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Integrite du catalogue de fonds livre avec l'app : une faute ici ne se voit qu'a l'execution,
 *  sur une installation neuve, sous forme de carte grise. */
class ProvidersTest {
    private val all = Providers.defaults()

    @Test fun `les identifiants sont uniques`() {
        val ids = all.map { it.id }
        assertEquals("ids dupliques : " + ids.groupBy { it }.filterValues { it.size > 1 }.keys,
            ids.size, ids.toSet().size)
    }

    @Test fun `les ordres de tri sont uniques et contigus`() {
        val orders = all.map { it.sortOrder }.sorted()
        assertEquals(orders.size, orders.toSet().size)
        assertEquals((0 until all.size).toList(), orders)
    }

    @Test fun `les types sont ceux que StyleBuilder sait traiter`() {
        val connus = setOf("XYZ", "WMS", "WMTS", "VECTOR", "MBTILES", "PMTILES", "DEM")
        all.forEach { assertTrue("${it.id} : type ${it.type}", it.type in connus) }
    }

    @Test fun `les groupes sont ceux que le gestionnaire affiche`() {
        val connus = setOf("Monde", "Pays", "Overlays", "Local", "Relief")
        all.forEach { assertTrue("${it.id} : groupe ${it.groupName}", it.groupName in connus) }
    }

    /** Android bloque le trafic en clair : un fond en http resterait gris sans message. */
    @Test fun `toutes les URL sont en https`() {
        all.forEach { assertTrue("${it.id} : ${it.urlTemplate}", it.urlTemplate.startsWith("https://")) }
    }

    /** Un gabarit doit etre developpable par TileUrl, sinon la tuile est demandee telle quelle. */
    @Test fun `chaque fond tuile porte un gabarit exploitable`() {
        all.filter { it.type in setOf("XYZ", "WMS", "WMTS") }.forEach { p ->
            val xyz = p.urlTemplate.contains("{z}") && p.urlTemplate.contains("{x}") && p.urlTemplate.contains("{y}")
            val bbox = p.urlTemplate.contains("{bbox-epsg-3857}")
            assertTrue("${p.id} : ni {z}/{x}/{y} ni {bbox-epsg-3857}", xyz || bbox)
        }
    }

    @Test fun `un fond a jeton s declare ses sous-domaines`() {
        all.filter { it.urlTemplate.contains("{s}") }.forEach {
            assertTrue("${it.id} : {s} sans subdomains", !it.subdomains.isNullOrBlank())
        }
    }

    @Test fun `un fond a jeton KEY declare un champ de cle`() {
        all.filter { it.urlTemplate.contains("{KEY}") }.forEach {
            assertTrue("${it.id} : {KEY} sans apiKey", it.apiKey != null)
        }
    }

    @Test fun `les zooms sont coherents`() {
        all.forEach {
            assertTrue("${it.id} : minZoom ${it.minZoom}", it.minZoom in 0..22)
            assertTrue("${it.id} : maxZoom ${it.maxZoom}", it.maxZoom in 0..22)
            assertTrue("${it.id} : min > max", it.minZoom <= it.maxZoom)
        }
    }

    @Test fun `le fond par defaut existe bien dans le catalogue`() {
        // SettingsEntity.defaultBasemapId vaut "osm" : sans ce fond, l'app demarrerait sans carte.
        assertTrue(all.any { it.id == "osm" })
    }

    /** Un seul fond DEM : tout le code le cherche par son type, pas par son id (cf. buildStyle). Il est
     *  liste d'entree, contrairement aux autres fonds decoches : son "enabled" ne dit que sa presence dans
     *  le gestionnaire, et l'ombrage lui-meme s'allume d'un tap (settings.hillshadeOn, faux par defaut). */
    @Test fun `un seul fond de relief, liste des l'entree`() {
        val dem = all.filter { it.type == "DEM" }
        assertEquals(1, dem.size)
        assertTrue("le relief doit figurer dans le gestionnaire", dem.first().enabled)
    }

    @Test fun `les surcouches sont declarees transparentes`() {
        all.filter { it.groupName == "Overlays" }.forEach {
            assertTrue("${it.id} : surcouche non transparente", it.transparent)
        }
    }

    /** Le fond AF3V inaugure le mecanisme de legende (cf. migration 18-19). */
    @Test fun `le fond af3v porte sa legende et ses trois couches`() {
        val af3v = all.first { it.id == "af3v" }
        assertEquals("legends/af3v.png", af3v.legendAsset)
        assertTrue(af3v.urlTemplate.contains("LAYERS=voie_cyclable,segment_cyclable,poi_travaux"))
        assertTrue(af3v.transparent)
        assertEquals(20, af3v.maxZoom)
    }

    @Test fun `seul af3v declare une legende pour l'instant`() {
        assertEquals(listOf("af3v"), all.filter { it.legendAsset != null }.map { it.id })
    }

    /** Le Basemap Control ne liste que les fonds actifs : la liste ci-dessous est ce que voit un nouvel
     *  utilisateur. Un "enabled = false" oublie sur un fond ajoute la rallongerait sans qu'on le remarque. */
    @Test fun `seuls huit fonds sont actifs d entree`() {
        assertEquals(
            listOf("osm", "mapbox_outdoors", "google_street", "google_sat", "google_relief",
                "dem_terrarium", "ign_fr", "ign_es"),
            all.filter { it.enabled }.map { it.id },
        )
    }
}
