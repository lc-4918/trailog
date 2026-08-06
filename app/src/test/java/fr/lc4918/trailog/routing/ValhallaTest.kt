package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.model.RoutingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * Construction de la requete et lecture de la reponse du moteur d'itineraire. Comme pour le geocodeur, ce
 * sont les deux seuls endroits ou une faute reste muette : une requete mal formee ou un total mal lu ne
 * leve rien, la mesure affiche seulement "Aucun itineraire", indiscernable de deux points non relies.
 */
class ValhallaTest {

    /** Corps JSON reellement envoye, une fois l'URL decodee. */
    private fun body(profile: RoutingProfile): String {
        val u = Valhalla.url(Valhalla.DEFAULT_URL, 44.56, 6.08, 45.18, 5.72, profile)
        return URLDecoder.decode(u.substringAfter("json="), "UTF-8")
    }

    // ---------- Requete ----------

    @Test fun `la requete porte les deux points, dans l'ordre`() {
        val b = body(RoutingProfile.HYBRID_BIKE)
        assertTrue(b, """"locations":[{"lat":44.56,"lon":6.08},{"lat":45.18,"lon":5.72}]""" in b)
    }

    /**
     * Les coordonnees passent par toString(), insensible a la locale. Avec format(), une locale francaise
     * ecrirait "44,56" et le JSON deviendrait invalide - faute invisible sur un poste anglophone.
     */
    @Test fun `les coordonnees gardent le point decimal en locale francaise`() {
        val defaut = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE)
            assertTrue("virgule decimale dans le JSON", "44,56" !in body(RoutingProfile.FOOT))
            assertTrue(""""lat":44.56""" in body(RoutingProfile.FOOT))
        } finally {
            java.util.Locale.setDefault(defaut)
        }
    }

    /** Les cinq disciplines sortent d'une seule instance : c'est la raison du choix de Valhalla. */
    @Test fun `chaque discipline a son modele de cout`() {
        assertEquals("bicycle" to "Road", Valhalla.costingOf(RoutingProfile.ROAD_BIKE))
        assertEquals("bicycle" to "Cross", Valhalla.costingOf(RoutingProfile.GRAVEL))
        assertEquals("bicycle" to "Hybrid", Valhalla.costingOf(RoutingProfile.HYBRID_BIKE))
        assertEquals("bicycle" to "Mountain", Valhalla.costingOf(RoutingProfile.MOUNTAIN_BIKE))
        assertEquals("pedestrian" to null, Valhalla.costingOf(RoutingProfile.FOOT))
    }

    @Test fun `le velo porte son type de monture, la marche n'en a pas`() {
        assertTrue(""""bicycle_type":"Cross"""" in body(RoutingProfile.GRAVEL))
        assertTrue(""""costing":"pedestrian"""" in body(RoutingProfile.FOOT))
        assertTrue("la marche n'a pas de monture", "bicycle_type" !in body(RoutingProfile.FOOT))
    }

    /** Le guidage virage par virage represente l'essentiel du poids de la reponse, et on ne veut qu'un total. */
    @Test fun `la requete ne demande pas le guidage`() {
        assertTrue(""""directions_type":"none"""" in body(RoutingProfile.ROAD_BIKE))
    }

    /** Une instance auto-hebergee peut exposer une URL portant deja une chaine de requete. */
    @Test fun `une url de base deja parametree recoit un et commercial`() {
        val u = Valhalla.url("https://route.exemple.fr/route?token=abc", 1.0, 2.0, 3.0, 4.0, RoutingProfile.FOOT)
        assertTrue(u, "https://route.exemple.fr/route?token=abc&json=" in u)
    }

    // ---------- Reponse ----------

    @Test fun `le total se lit en metres et en secondes`() {
        val r = Valhalla.parse("""{"trip":{"summary":{"time":1830.5,"length":12.34},"status":0}}""")
        assertEquals(12340.0, r!!.meters, 1e-6)   // la reponse est demandee en kilometres
        assertEquals(1830.5, r.seconds, 1e-6)
    }

    /** Deux points non relies dans cette discipline : Valhalla repond une erreur, pas un trajet nul. */
    @Test fun `une reponse d'erreur ne donne aucun itineraire`() {
        assertNull(Valhalla.parse("""{"error_code":442,"error":"No path could be found for input"}"""))
    }

    /** Un total incomplet ne doit pas passer pour une distance de zero. */
    @Test fun `un total sans longueur ou sans duree ne donne aucun itineraire`() {
        assertNull(Valhalla.parse("""{"trip":{"summary":{"time":120.0}}}"""))
        assertNull(Valhalla.parse("""{"trip":{"summary":{"length":3.5}}}"""))
        assertNull(Valhalla.parse("""{"trip":{}}"""))
    }

    /** Page d'erreur d'un proxy, instance mal configuree : une exception ici casserait la mesure. */
    @Test fun `une reponse illisible ne leve pas`() {
        assertNull(Valhalla.parse("<html>502 Bad Gateway</html>"))
        assertNull(Valhalla.parse(""))
    }

    /** Le trace vient des segments, decode en (lon, lat) : c'est lui qu'on pose en noir sur la carte. */
    @Test fun `le trace des segments est decode`() {
        val r = Valhalla.parse(
            """{"trip":{"summary":{"time":60.0,"length":1.0},"legs":[{"shape":"_p~iF~ps|U_ulLnnqC"}]}}""")
        assertEquals(2, r!!.shape.size)
        assertEquals(Polyline.decode("_p~iF~ps|U_ulLnnqC"), r.shape)
    }

    /** Un itineraire a plusieurs segments : leurs traces se suivent en un seul, pas un par segment. */
    @Test fun `les traces de plusieurs segments se suivent`() {
        val r = Valhalla.parse(
            """{"trip":{"summary":{"time":60.0,"length":1.0},"legs":[{"shape":"_p~iF~ps|U"},{"shape":"_p~iF~ps|U"}]}}""")
        assertEquals(2, r!!.shape.size)
    }

    /** La mesure ne depend pas de la geometrie : une reponse sans trace reste exploitable. */
    @Test fun `une reponse sans trace garde sa mesure`() {
        val r = Valhalla.parse("""{"trip":{"summary":{"time":60.0,"length":1.0}}}""")
        assertEquals(1000.0, r!!.meters, 1e-6)
        assertTrue(r.shape.isEmpty())
    }

    /** Valhalla enrichit ses reponses au fil de ses versions : les champs en trop sont ignores. */
    @Test fun `les champs inconnus n'empechent pas la lecture`() {
        val r = Valhalla.parse(
            """{"id":"x","trip":{"legs":[{"shape":"abc"}],"summary":{"time":60.0,"length":1.0,"cost":9.9},"units":"kilometers"}}""")
        assertEquals(1000.0, r!!.meters, 1e-6)
    }
}
