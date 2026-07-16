package fr.lc4918.trailog.map.offline

import fr.lc4918.trailog.data.db.ProviderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Developpement des gabarits d'URL : partage par le telechargement hors-ligne et les miniatures. */
class TileUrlTest {
    private fun provider(url: String, key: String? = null, subs: String? = null) =
        ProviderEntity(id = "p", name = "p", groupName = "g", type = "XYZ", urlTemplate = url,
            apiKey = key, subdomains = subs)

    @Test fun `gabarit xyz standard`() {
        val u = TileUrl.build(provider("https://t/{z}/{x}/{y}.png"), x = 3, y = 5, z = 7)
        assertEquals("https://t/7/3/5.png", u)
    }

    @Test fun `la cle API remplace le jeton KEY`() {
        val u = TileUrl.build(provider("https://t/{z}/{x}/{y}?k={KEY}", key = "secret"), 1, 2, 3)
        assertEquals("https://t/3/1/2?k=secret", u)
    }

    /** Provider sans cle renseignee : le jeton disparait plutot que de laisser "{KEY}" dans l'URL. */
    @Test fun `jeton KEY vide si la cle est absente`() {
        val u = TileUrl.build(provider("https://t/{z}/{x}/{y}?k={KEY}", key = null), 1, 2, 3)
        assertEquals("https://t/3/1/2?k=", u)
    }

    @Test fun `le sous-domaine est choisi de facon deterministe`() {
        val p = provider("https://mt{s}.x/{z}/{x}/{y}", subs = "0,1,2,3")
        assertEquals(TileUrl.build(p, 5, 3, 10), TileUrl.build(p, 5, 3, 10))   // stable
        val s = TileUrl.build(p, 5, 3, 10).substringAfter("mt").substringBefore(".")
        assertTrue(s in listOf("0", "1", "2", "3"))
    }

    /** Sans liste de sous-domaines, le jeton reste tel quel plutot que de produire une URL invalide
     *  silencieuse : la faute de configuration se voit. */
    @Test fun `jeton s laisse tel quel si aucun sous-domaine n'est configure`() {
        assertTrue(TileUrl.build(provider("https://mt{s}.x/{z}/{x}/{y}", subs = null), 1, 1, 1).contains("{s}"))
        assertTrue(TileUrl.build(provider("https://mt{s}.x/{z}/{x}/{y}", subs = ""), 1, 1, 1).contains("{s}"))
    }

    /** WMS : le gabarit bbox se developpe en metres Web Mercator, dans l'ordre minx,miny,maxx,maxy
     *  qu'impose WMS 1.3.0 avec CRS=EPSG:3857. */
    @Test fun `gabarit bbox-epsg-3857 developpe en metres mercator`() {
        val u = TileUrl.build(provider("https://w?BBOX={bbox-epsg-3857}"), x = 0, y = 0, z = 0)
        val bbox = u.substringAfter("BBOX=").split(",").map { it.toDouble() }
        assertEquals(4, bbox.size)
        val demiTour = 20037508.342789244
        assertEquals(-demiTour, bbox[0], 1.0)     // minx
        assertEquals(-demiTour, bbox[1], 1.0)     // miny
        assertEquals(demiTour, bbox[2], 1.0)      // maxx
        assertEquals(demiTour, bbox[3], 1.0)      // maxy
        assertTrue("minx < maxx", bbox[0] < bbox[2])
        assertTrue("miny < maxy", bbox[1] < bbox[3])
    }

    /** La tuile 0/0 du zoom 1 est le quart nord-ouest : x negatif, y positif. */
    @Test fun `la tuile nord-ouest du zoom 1 couvre le quadrant attendu`() {
        val u = TileUrl.build(provider("https://w?BBOX={bbox-epsg-3857}"), x = 0, y = 0, z = 1)
        val b = u.substringAfter("BBOX=").split(",").map { it.toDouble() }
        assertTrue(b[0] < 0 && b[2] <= 1.0)       // de l'ouest jusqu'au meridien de Greenwich
        assertTrue(b[3] > 0 && b[1] >= -1.0)      // du nord jusqu'a l'equateur
    }

    @Test fun `un gabarit sans jeton bbox n'est pas touche`() {
        val u = TileUrl.build(provider("https://t/{z}/{x}/{y}.png"), 1, 2, 3)
        assertFalse(u.contains("BBOX"))
    }
}
