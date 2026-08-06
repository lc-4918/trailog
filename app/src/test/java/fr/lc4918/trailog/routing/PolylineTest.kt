package fr.lc4918.trailog.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodage des polylignes encodees, par lesquelles Valhalla rend la geometrie de ses itineraires.
 *
 * Une faute ici est muette : le trace s'affiche quand meme, simplement ailleurs. Le piege principal est la
 * precision, Valhalla encodant au millionieme la ou l'algorithme d'origine travaille au cent-millieme.
 */
class PolylineTest {

    /** Exemple canonique de la documentation Google, en precision 5 : verifie l'algorithme lui-meme. */
    @Test fun `l'exemple de reference se decode au bon endroit`() {
        val pts = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 1e5)
        assertEquals(3, pts.size)
        assertEquals(-120.2, pts[0].first, 1e-5)
        assertEquals(38.5, pts[0].second, 1e-5)
        assertEquals(-120.95, pts[1].first, 1e-5)
        assertEquals(40.7, pts[1].second, 1e-5)
        assertEquals(-126.453, pts[2].first, 1e-5)
        assertEquals(43.252, pts[2].second, 1e-5)
    }

    /**
     * La precision par defaut doit etre celle de Valhalla. Decoder ses reponses au cent-millieme placerait
     * le trace dix fois trop loin de l'equateur - sans erreur, sans message.
     */
    @Test fun `la precision par defaut est celle de Valhalla`() {
        assertEquals(1e6, Polyline.VALHALLA_PRECISION, 0.0)
        val enc = "_p~iF~ps|U"
        val parDefaut = Polyline.decode(enc)
        val enPrecision5 = Polyline.decode(enc, precision = 1e5)
        assertEquals(enPrecision5.first().second / 10.0, parDefaut.first().second, 1e-9)
    }

    /** Les points sortent en (lon, lat), l'ordre du GeoJSON, alors qu'ils sont encodes lat puis lon. */
    @Test fun `les points sortent en lon puis lat`() {
        val p = Polyline.decode("_p~iF~ps|U", precision = 1e5).first()
        assertTrue("longitude attendue en premier", p.first < -100.0)
        assertTrue("latitude attendue en second", p.second in 0.0..90.0)
    }

    @Test fun `une chaine vide ne donne aucun point`() {
        assertTrue(Polyline.decode("").isEmpty())
    }

    /** Une reponse tronquee garde les points deja lus : la mesure vient du total, pas de la geometrie,
     *  et une portion de trace vaut mieux que la perte de tout l'affichage. */
    @Test fun `une chaine tronquee rend les points deja lus`() {
        val complet = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 1e5)
        val tronque = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq", precision = 1e5)
        assertEquals(2, tronque.size)
        assertEquals(complet[0], tronque[0])
        assertEquals(complet[1], tronque[1])
    }
}
