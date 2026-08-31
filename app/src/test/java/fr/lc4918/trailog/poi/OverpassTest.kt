package fr.lc4918.trailog.poi

import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.map.offline.Bbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Construction de la requete et lecture de la reponse d'Overpass.
 *
 * Meme raison d'etre que pour DATAtourisme : une faute y est MUETTE. Une emprise ecrite dans le mauvais
 * ordre, une etiquette mal orthographiee, un chemin de coordonnees ignore - rien ne leve, la reponse est
 * simplement vide, et une carte sans marqueurs ressemble a une carte sans marqueurs.
 *
 * Les formes verrouillees ici viennent de reponses reelles du service, relevees pendant la mise au point.
 */
class OverpassTest {

    private val grenoble = Bbox(west = 5.55, south = 45.10, east = 5.85, north = 45.30)

    // ---------- Requete ----------

    /** L'emprise s'ecrit (sud,ouest,nord,est) : l'ordre d'Overpass, l'INVERSE de celui de DATAtourisme. */
    @Test fun `l'emprise part dans l'ordre attendu par le service`() {
        val q = Overpass.query(grenoble, setOf(PoiCategory.TOILETS))!!
        assertTrue(q, "(45.1,5.55,45.3,5.85)" in q)
    }

    /**
     * Les etiquettes d'une meme cle se regroupent en une expression reguliere.
     *
     * Une instruction par etiquette donnait, a 27 categories, une requete d'une soixantaine de lignes que
     * l'instance publique met plusieurs secondes a seulement analyser.
     */
    @Test fun `les etiquettes d'une meme cle tiennent en une seule instruction`() {
        val q = Overpass.query(grenoble, setOf(PoiCategory.BARS))!!
        assertTrue(q, """nwr["amenity"~"^(bar|cafe|pub)$"]""" in q)
        assertEquals("une seule instruction pour les trois", 1, q.split("nwr[").size - 1)
    }

    /** Cles distinctes, instructions distinctes : rien ne les regroupe. */
    @Test fun `deux cles donnent deux instructions`() {
        val q = Overpass.query(grenoble, setOf(PoiCategory.WATER))!!
        assertTrue(q, """nwr["amenity"~"^(drinking_water|water_point)$"]""" in q)
        assertTrue(q, """nwr["man_made"~"^(water_tap)$"]""" in q)
    }

    /**
     * Un selecteur a deux paires les exige toutes les deux, dans une instruction a part : `tourism=information`
     * seul designe aussi bien un panneau au bord d'un chemin qu'un office de tourisme.
     */
    @Test fun `un selecteur conjoint garde ses deux conditions`() {
        val q = Overpass.query(grenoble, setOf(PoiCategory.TOURIST_OFFICES))!!
        assertTrue(q, """nwr["tourism"="information"]["information"="office"]""" in q)
    }

    /**
     * `nwr` et le centre des surfaces : un camping ou un musee est souvent dessine en contour, pas pose en
     * noeud.
     *
     * `out TAGS center` et non `out center` : le second est un `out body`, qui joint a chaque chemin la
     * liste de ses noeuds - une donnee dont on ne fait rien, le centre suffisant a poser un marqueur.
     */
    @Test fun `la requete demande aussi les surfaces et leur centre`() {
        val q = Overpass.query(grenoble, setOf(PoiCategory.CAMPINGS))!!
        assertTrue(q, q.startsWith("[out:json][timeout:"))
        assertTrue(q, "nwr[" in q)
        assertTrue(q, q.endsWith("out tags center ${Overpass.LIMIT};"))
    }

    /** La geometrie complete des chemins n'est jamais demandee : elle pese sans rien apprendre. */
    @Test fun `la requete ne demande pas la geometrie des chemins`() {
        val q = Overpass.query(grenoble, setOf(PoiCategory.CAMPINGS))!!
        assertTrue(q, "geom" !in q)
    }

    // ---------- le decoupage d'une tuile trop dense ----------

    /**
     * Les quatre quadrants pavent l'emprise sans trou ni recouvrement.
     *
     * Un trou serait une bande de carte sans point d'interet, qu'aucun geste ne peuplerait ; un
     * recouvrement rendrait deux fois les memes lieux - sans dommage, mais pour rien.
     */
    @Test fun `les quadrants pavent l'emprise`() {
        val q = Overpass.quadrants(grenoble)
        assertEquals(4, q.size)
        assertEquals(grenoble.west, q.minOf { it.west }, 1e-9)
        assertEquals(grenoble.east, q.maxOf { it.east }, 1e-9)
        assertEquals(grenoble.south, q.minOf { it.south }, 1e-9)
        assertEquals(grenoble.north, q.maxOf { it.north }, 1e-9)
        val milieuLon = (grenoble.west + grenoble.east) / 2
        val milieuLat = (grenoble.south + grenoble.north) / 2
        assertTrue("les quatre se touchent au centre", q.all {
            (it.west == grenoble.west || it.west == milieuLon) &&
                (it.south == grenoble.south || it.south == milieuLat)
        })
        assertEquals("chacun fait le quart de la surface",
            aire(grenoble) / 4, aire(q.first()), 1e-9)
    }

    /** Les quatre quadrants sont distincts : un decoupage qui rendrait deux fois la meme tuile tournerait
     *  en rond sans jamais resserrer. */
    @Test fun `les quatre quadrants sont distincts`() {
        assertEquals(4, Overpass.quadrants(grenoble).toSet().size)
    }

    private fun aire(b: Bbox) = (b.east - b.west) * (b.north - b.south)

    /** Aucune categorie retenue ne porte d'etiquette OSM : il n'y a rien a demander, et on ne demande rien. */
    @Test fun `sans etiquette connue, pas de requete`() {
        assertNull(Overpass.query(grenoble, setOf(PoiCategory.HERITAGE_VILLAGES)))
        assertNull(Overpass.query(grenoble, emptySet()))
    }

    // ---------- Reponse ----------

    private val reponse = """
        {"elements":[
          {"type":"node","id":304882878,"lat":45.1874483,"lon":5.7238677,
           "tags":{"amenity":"toilets","fee":"no"}},
          {"type":"way","id":123456,"center":{"lat":45.19,"lon":5.72},
           "tags":{"tourism":"camp_site","name":"Camping du Drac","website":"https://exemple.fr",
                   "addr:city":"Grenoble"}},
          {"type":"node","id":42,"lat":45.2,"lon":5.7,"tags":{"amenity":"post_box"}}
        ]}
    """.trimIndent()

    /** Un noeud porte ses coordonnees ; une surface rend le centre demande par `out center`. */
    @Test fun `les surfaces sont placees par leur centre`() {
        val lieux = Overpass.parse(reponse)
        val camping = lieux.first { it.category == PoiCategory.CAMPINGS }
        assertEquals(45.19, camping.lat, 1e-9)
        assertEquals(5.72, camping.lon, 1e-9)
    }

    /**
     * Un lieu SANS NOM est garde - c'est la difference avec l'autre source. Une fontaine ou des toilettes
     * n'en portent presque jamais dans OSM, et ce sont justement les lieux qu'on cherche.
     */
    @Test fun `un lieu sans nom est garde, son libelle vide`() {
        val toilettes = Overpass.parse(reponse).first { it.category == PoiCategory.TOILETS }
        assertEquals("", toilettes.label)
    }

    /** Une etiquette qu'aucune categorie ne reclame n'a rien a faire sur la carte. */
    @Test fun `une etiquette inconnue est ecartee`() {
        assertTrue(Overpass.parse(reponse).none { "42" in it.uuid })
    }

    /** L'identifiant porte sa source et le type de l'objet : deux sources partagent la table du cache, et
     *  un numero de noeud pourrait par ailleurs etre celui d'un chemin. */
    @Test fun `l'identifiant distingue la source et le type`() {
        val lieux = Overpass.parse(reponse)
        assertTrue(lieux.map { it.uuid }.toString(), "osm:node/304882878" in lieux.map { it.uuid })
        assertTrue(lieux.map { it.uuid }.toString(), "osm:way/123456" in lieux.map { it.uuid })
    }

    /** Ville et site sont repris quand ils existent ; le theme velo n'existe pas ici et reste faux. */
    @Test fun `les champs de l'infobulle sont repris tels quels`() {
        val camping = Overpass.parse(reponse).first { it.category == PoiCategory.CAMPINGS }
        assertEquals("Camping du Drac", camping.label)
        assertEquals("Grenoble", camping.city)
        assertEquals("https://exemple.fr", camping.webUrl)
        assertTrue(!camping.bikeTheme)
    }

    /** Les categories cochees bornent la lecture, comme pour l'autre source. */
    @Test fun `la lecture se borne aux categories demandees`() {
        val lieux = Overpass.parse(reponse, setOf(PoiCategory.TOILETS))
        assertEquals(1, lieux.size)
        assertEquals(PoiCategory.TOILETS, lieux.first().category)
    }

    /** Une reponse illisible - l'instance rend une page HTML quand elle est saturee - ne leve pas. */
    @Test fun `une reponse illisible rend une liste vide`() {
        assertNotNull(Overpass.parse("<html>Overpass is busy</html>"))
        assertTrue(Overpass.parse("<html>Overpass is busy</html>").isEmpty())
    }
}
