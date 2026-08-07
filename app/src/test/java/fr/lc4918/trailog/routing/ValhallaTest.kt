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
        val u = Valhalla.url(Valhalla.DEFAULT_URL, listOf(44.56 to 6.08, 45.18 to 5.72), profile)
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

    /** Le planificateur autorise jusqu'a 25 lieux : les etapes intermediaires doivent figurer dans la
     *  requete, dans l'ordre, sinon le moteur relierait simplement le depart a l'arrivee. */
    @Test fun `les etapes intermediaires figurent dans l'ordre donne`() {
        val u = Valhalla.url(Valhalla.DEFAULT_URL,
            listOf(44.0 to 5.0, 44.5 to 5.5, 45.0 to 6.0), RoutingProfile.GRAVEL)
        val b = URLDecoder.decode(u.substringAfter("json="), "UTF-8")
        assertTrue(b, """"locations":[{"lat":44.0,"lon":5.0},{"lat":44.5,"lon":5.5},{"lat":45.0,"lon":6.0}]""" in b)
    }

    /** Une instance auto-hebergee peut exposer une URL portant deja une chaine de requete. */
    @Test fun `une url de base deja parametree recoit un et commercial`() {
        val u = Valhalla.url("https://route.exemple.fr/route?token=abc", listOf(1.0 to 2.0, 3.0 to 4.0), RoutingProfile.FOOT)
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

    /** Le trace vient des segments, decode en (lon, lat) : c'est lui qu'on pose sur la carte. */
    @Test fun `le trace des segments est decode`() {
        val r = Valhalla.parse(
            """{"trip":{"summary":{"time":60.0,"length":1.0},"legs":[{"shape":"_p~iF~ps|U_ulLnnqC"}]}}""")
        assertEquals(2, r!!.points.size)
        assertEquals(Polyline.decode("_p~iF~ps|U_ulLnnqC"), r.points.map { it.lon to it.lat })
    }

    /** Un itineraire a plusieurs segments : leurs traces se suivent en un seul, pas un par segment. */
    @Test fun `les traces de plusieurs segments se suivent`() {
        val r = Valhalla.parse(
            """{"trip":{"summary":{"time":60.0,"length":1.0},"legs":[{"shape":"_p~iF~ps|U"},{"shape":"_p~iF~ps|U"}]}}""")
        assertEquals(2, r!!.points.size)
    }

    /** La mesure ne depend pas de la geometrie : une reponse sans trace reste exploitable. */
    @Test fun `une reponse sans trace garde sa mesure`() {
        val r = Valhalla.parse("""{"trip":{"summary":{"time":60.0,"length":1.0}}}""")
        assertEquals(1000.0, r!!.meters, 1e-6)
        assertTrue(r.points.isEmpty())
    }

    // ---------- Altitudes ----------

    /** Sans ce parametre le moteur ne rend aucune altitude : ni profil, ni teinte de pente. */
    @Test fun `la requete demande les altitudes`() {
        assertTrue(""""elevation_interval":30""" in body(RoutingProfile.ROAD_BIKE))
    }

    @Test fun `un pas nul ne demande pas d'altitudes`() {
        val u = Valhalla.url(Valhalla.DEFAULT_URL, listOf(1.0 to 2.0, 3.0 to 4.0), RoutingProfile.FOOT, elevationIntervalM = 0)
        assertTrue("elevation_interval" !in URLDecoder.decode(u, "UTF-8"))
    }

    /** Les altitudes sont echantillonnees a pas constant, pas une par point : elles sont reportees sur le
     *  trace. Ici deux points aux extremites d'une table de trois altitudes. */
    @Test fun `les altitudes sont reportees sur les points du trace`() {
        val r = Valhalla.parse(
            """{"trip":{"summary":{"time":60.0,"length":1.0},"legs":[""" +
                """{"shape":"_p~iF~ps|U_ulLnnqC","elevation":[100.0,150.0,200.0],"elevation_interval":30.0}]}}""")
        assertEquals(2, r!!.points.size)
        assertEquals(100.0, r.points.first().ele!!, 1e-6)
        // Le second point est a des dizaines de km : bien au-dela de la table, donc borne a sa derniere valeur.
        assertEquals(200.0, r.points.last().ele!!, 1e-6)
    }

    /** Instance sans donnees de terrain, ou trop ancienne pour connaitre le parametre : le trace reste la. */
    @Test fun `une reponse sans altitudes garde son trace`() {
        val r = Valhalla.parse(
            """{"trip":{"summary":{"time":60.0,"length":1.0},"legs":[{"shape":"_p~iF~ps|U_ulLnnqC"}]}}""")
        assertEquals(2, r!!.points.size)
        assertNull(r.points.first().ele)
    }

    /**
     * Un trou dans le modele de terrain fait rejeter toute la table du segment. Combler donnerait une cote
     * inventee au milieu du profil, ce qui se lit comme du relief - une absence de profil est plus honnete.
     */
    @Test fun `un trou dans les altitudes fait renoncer a tout le segment`() {
        val r = Valhalla.parse(
            """{"trip":{"summary":{"time":60.0,"length":1.0},"legs":[""" +
                """{"shape":"_p~iF~ps|U_ulLnnqC","elevation":[100.0,null,200.0],"elevation_interval":30.0}]}}""")
        assertEquals(2, r!!.points.size)
        assertNull(r.points.first().ele)
    }

    /** Valhalla enrichit ses reponses au fil de ses versions : les champs en trop sont ignores. */
    @Test fun `les champs inconnus n'empechent pas la lecture`() {
        val r = Valhalla.parse(
            """{"id":"x","trip":{"legs":[{"shape":"abc"}],"summary":{"time":60.0,"length":1.0,"cost":9.9},"units":"kilometers"}}""")
        assertEquals(1000.0, r!!.meters, 1e-6)
    }
}
