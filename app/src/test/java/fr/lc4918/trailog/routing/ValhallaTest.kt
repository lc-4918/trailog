package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.model.HillPref
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.domain.model.SurfacePref
import fr.lc4918.trailog.domain.model.WayPref
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
        RoutingProfile.entries.filter { it != RoutingProfile.FOOT }
            .forEach { assertEquals("$it", "bicycle", Valhalla.costingOf(it)) }
        assertEquals("pedestrian", Valhalla.costingOf(RoutingProfile.FOOT))
    }

    /**
     * La monture suit la discipline ET le revetement demande, parce qu'elle EST le levier du revetement :
     * c'est elle qui fixe la vitesse pretee au cycliste sur du gravier, et cette vitesse ne se regle par
     * aucune option. Les voies vertes francaises etant gravillonnees, seule la monture VTT les emprunte.
     */
    @Test fun `la monture suit la discipline et le revetement demande`() {
        fun monture(p: RoutingProfile, s: SurfacePref) = Valhalla.bicycleTypeOf(p, s)
        // Un velo de route reste un velo de route : c'est ce qui le definit.
        SurfacePref.entries.forEach { assertEquals("$it", "Road", monture(RoutingProfile.ROAD_BIKE, it)) }
        // "Rester sur le revetu" veut dire rouler en velo de route, quelle que soit la machine.
        assertEquals("Road", monture(RoutingProfile.MOUNTAIN_BIKE, SurfacePref.PAVED))
        assertEquals("Road", monture(RoutingProfile.HYBRID_BIKE, SurfacePref.PAVED))
        // "Accepter les chemins" veut dire rouler en VTT - sans quoi la voie verte gravillonnee est fuie.
        assertEquals("Mountain", monture(RoutingProfile.GRAVEL, SurfacePref.ROUGH))
        assertEquals("Mountain", monture(RoutingProfile.HYBRID_BIKE, SurfacePref.ROUGH))
        // Au milieu, chacun garde la sienne.
        assertEquals("Cross", monture(RoutingProfile.GRAVEL, SurfacePref.BALANCED))
        assertEquals("Hybrid", monture(RoutingProfile.HYBRID_BIKE, SurfacePref.BALANCED))
        assertEquals("Mountain", monture(RoutingProfile.MOUNTAIN_BIKE, SurfacePref.BALANCED))
        assertNull("la marche n'a pas de monture", monture(RoutingProfile.FOOT, SurfacePref.ROUGH))
    }

    @Test fun `le velo porte son type de monture, la marche n'en a pas`() {
        assertTrue(""""bicycle_type":"Cross"""" in body(RoutingProfile.GRAVEL))
        assertTrue(""""costing":"pedestrian"""" in body(RoutingProfile.FOOT))
        assertTrue("la marche n'a pas de monture", "bicycle_type" !in body(RoutingProfile.FOOT))
    }

    // ---------- Preferences de trace ----------

    private fun body(profile: RoutingProfile, prefs: RoutingPrefs): String {
        val u = Valhalla.url(Valhalla.DEFAULT_URL, listOf(44.56 to 6.08, 45.18 to 5.72), profile, prefs)
        return URLDecoder.decode(u.substringAfter("json="), "UTF-8")
    }

    /**
     * LA regle de cette traduction : la position centrale n'emet rien.
     *
     * Mesure a l'appui - en velo de route sur Grenoble - Voiron, ne rien dire donne 59 % de voies douces,
     * envoyer use_roads:0.5, le defaut documente, tombe a 22 %. Emettre un defaut n'est donc pas neutre, et
     * un jour ou l'autre quelqu'un voudra "expliciter" ces valeurs : ce test est la pour l'en empecher.
     */
    @Test fun `les positions centrales n'emettent aucune option`() {
        RoutingProfile.entries.forEach { p ->
            val b = body(p, RoutingPrefs.Balanced)
            assertTrue("$p : ${b}", "use_roads" !in b && "use_hills" !in b &&
                "avoid_bad_surfaces" !in b && "walkway_factor" !in b && "use_tracks" !in b &&
                "max_hiking_difficulty" !in b)
        }
        // La monture, elle, n'est pas une preference : elle reste envoyee.
        assertTrue(""""bicycle_type":"Cross"""" in body(RoutingProfile.GRAVEL, RoutingPrefs.Balanced))
    }

    /** Les options se posent sous le modele de cout du moment, non sous un "bicycle" fige. */
    @Test fun `les options vont sous le modele de cout de la discipline`() {
        assertTrue(""""costing_options":{"bicycle":{""" in body(RoutingProfile.HYBRID_BIKE,
            RoutingPrefs(WayPref.SOFT, HillPref.BALANCED, SurfacePref.BALANCED)))
        assertTrue(""""costing_options":{"pedestrian":{""" in body(RoutingProfile.FOOT,
            RoutingPrefs(WayPref.SOFT, HillPref.BALANCED, SurfacePref.BALANCED)))
    }

    /** La meme question ne se dit pas pareil a un cycliste et a un marcheur, qui n'a pas de use_roads. */
    @Test fun `privilegier les voies douces se dit use_roads a velo, walkway_factor a pied`() {
        val velo = body(RoutingProfile.HYBRID_BIKE, RoutingPrefs(ways = WayPref.SOFT))
        assertTrue(velo, """"use_roads":0.1""" in velo)
        val pied = body(RoutingProfile.FOOT, RoutingPrefs(ways = WayPref.SOFT))
        assertTrue(pied, """"walkway_factor":0.5""" in pied)
        assertTrue("le marcheur n'a pas de use_roads", "use_roads" !in pied)
    }

    /**
     * Trois valeurs pour une seule demande, chacune mesuree :
     * - 0,1 au gravel et au VTC, parce que c'est la marche d'escalier : a 0,2 la voie verte de
     *   Revel - Soreze est ignoree, de 0,15 a 0 elle est prise ;
     * - 0 au VTT, la discipline qui privilegie les chemins (39 % de chemins contre 25 % a 0,1) ;
     * - 0,2 au velo de route, a qui descendre plus bas coute 3,2 km sur Grenoble - Voiron pour cinq
     *   points de voies douces.
     */
    @Test fun `privilegier les voies douces ne se paie pas au meme prix selon la monture`() {
        fun ur(p: RoutingProfile) = body(p, RoutingPrefs(ways = WayPref.SOFT))
        assertTrue(ur(RoutingProfile.MOUNTAIN_BIKE), """"use_roads":0.0""" in ur(RoutingProfile.MOUNTAIN_BIKE))
        assertTrue(ur(RoutingProfile.GRAVEL), """"use_roads":0.1""" in ur(RoutingProfile.GRAVEL))
        assertTrue(ur(RoutingProfile.HYBRID_BIKE), """"use_roads":0.1""" in ur(RoutingProfile.HYBRID_BIKE))
        assertTrue(ur(RoutingProfile.ROAD_BIKE), """"use_roads":0.2""" in ur(RoutingProfile.ROAD_BIKE))
    }

    /**
     * Sans cela le moteur s'arrete a T1 et s'interdit les sentiers de montagne, qui sont pourtant la
     * randonnee : Grenoble - Chamrousse y gagne 300 m et 8 min. Au-dela de 2, plus rien ne s'ouvre sur les
     * trajets mesures, et les cotations suivantes touchent a l'alpinisme.
     */
    @Test fun `accepter les chemins ouvre au marcheur les sentiers de montagne`() {
        val pied = body(RoutingProfile.FOOT, RoutingPrefs(surface = SurfacePref.ROUGH))
        assertTrue(pied, """"max_hiking_difficulty":2""" in pied)
        assertTrue("le cycliste n'a pas de cotation de randonnee",
            "max_hiking_difficulty" !in body(RoutingProfile.MOUNTAIN_BIKE, RoutingPrefs(surface = SurfacePref.ROUGH)))
        assertTrue("hors de la position haute, on ne dit rien",
            "max_hiking_difficulty" !in body(RoutingProfile.FOOT, RoutingPrefs(surface = SurfacePref.PAVED)))
    }

    /** Le revetement : avoid_bad_surfaces a velo, use_tracks a pied - et les deux vont en sens INVERSE. */
    @Test fun `accepter les chemins s'ecrit a l'envers selon la discipline`() {
        val velo = body(RoutingProfile.GRAVEL, RoutingPrefs(surface = SurfacePref.ROUGH))
        assertTrue(velo, """"avoid_bad_surfaces":0.0""" in velo)
        val pied = body(RoutingProfile.FOOT, RoutingPrefs(surface = SurfacePref.ROUGH))
        assertTrue(pied, """"use_tracks":1.0""" in pied)
        assertTrue(body(RoutingProfile.GRAVEL, RoutingPrefs(surface = SurfacePref.PAVED)),
            """"avoid_bad_surfaces":1.0""" in body(RoutingProfile.GRAVEL, RoutingPrefs(surface = SurfacePref.PAVED)))
        assertTrue(""""use_tracks":0.0""" in body(RoutingProfile.FOOT, RoutingPrefs(surface = SurfacePref.PAVED)))
    }

    @Test fun `les trois preferences tiennent dans la meme requete`() {
        val b = body(RoutingProfile.MOUNTAIN_BIKE, RoutingPrefs(WayPref.SOFT, HillPref.SEEK, SurfacePref.ROUGH))
        assertTrue(b, """"bicycle_type":"Mountain"""" in b)
        assertTrue(b, """"use_roads":0.0""" in b)
        assertTrue(b, """"use_hills":1.0""" in b)
        assertTrue(b, """"avoid_bad_surfaces":0.0""" in b)
    }

    /** Le JSON doit rester valide une fois les options posees : c'est la faute qui ne se verrait pas. */
    @Test fun `la requete reste un json valide avec toutes les options`() {
        val b = body(RoutingProfile.FOOT, RoutingPrefs(WayPref.ROADS, HillPref.AVOID, SurfacePref.PAVED))
        assertEquals("accolades appariees", b.count { it == '{' }, b.count { it == '}' })
        assertTrue(b, """{"walkway_factor":1.5,"use_hills":0.0,"use_tracks":0.0}""" in b)
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
