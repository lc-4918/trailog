package fr.lc4918.trailog.poi

import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * Construction de la requete et lecture de la reponse de DATAtourisme.
 *
 * Meme raison d'etre que pour le geocodeur et les moteurs d'itineraire : ce sont les deux seuls endroits
 * ou une faute est MUETTE. Une condition mal ecrite ne leve pas, elle rend zero point d'interet - et une
 * carte sans marqueurs ressemble a une carte sans marqueurs.
 *
 * Chaque forme verrouillee ici a ete relevee sur une reponse reelle du service, pas deduite de sa
 * documentation : les deux ont diverge sur trois points (cf. les cas plus bas).
 */
class DatatourismeTest {

    private fun urlDecodee(u: String) = URLDecoder.decode(u, "UTF-8")

    // ---------- Requete ----------

    /**
     * Les conditions se combinent par " AND ". Une virgule ou un "&" entre deux conditions rend une
     * erreur 400 : la virgule est deja le separateur des valeurs d'un [in].
     */
    @Test fun `les conditions se combinent par AND`() {
        val u = urlDecodee(Datatourisme.catalogUrl(Datatourisme.DEFAULT_URL, 45.2, 5.6, 45.1, 5.8,
            setOf(PoiCategory.HOTELS), bikeOnly = true)!!)
        assertTrue(u, "type[in]=" in u)
        assertTrue(u, " AND hasTheme.key[eq]=Bike" in u)
    }

    /**
     * Le chemin d'un theme est hasTheme.key, et non hasTheme : ce dernier rend zero resultat SANS
     * protester, et c'est la faute la plus couteuse que ce fichier puisse laisser passer.
     */
    @Test fun `le theme se filtre par son chemin de cle`() {
        val u = urlDecodee(Datatourisme.catalogUrl(Datatourisme.DEFAULT_URL, 45.2, 5.6, 45.1, 5.8,
            setOf(PoiCategory.HOTELS), bikeOnly = true)!!)
        assertTrue(u, "hasTheme.key[eq]=" in u)
        assertTrue("hasTheme sans .key ne filtre rien", "hasTheme[eq]" !in u)
    }

    /** Sans le filtre, on ne demande pas le theme : c'est une condition de moins pour le service. */
    @Test fun `sans le filtre velo, la requete ne parle pas de theme`() {
        val u = urlDecodee(Datatourisme.catalogUrl(Datatourisme.DEFAULT_URL, 45.2, 5.6, 45.1, 5.8,
            setOf(PoiCategory.HOTELS))!!)
        assertTrue(u, "hasTheme.key" !in u)
    }

    /** L'emprise s'ecrit haut, gauche, bas, droite - lat max, lon min, lat min, lon max. */
    @Test fun `l'emprise part dans l'ordre attendu par le service`() {
        val u = urlDecodee(Datatourisme.catalogUrl(Datatourisme.DEFAULT_URL, 45.22, 5.68, 45.14, 5.78,
            setOf(PoiCategory.HOTELS))!!)
        assertTrue(u, "geo_bounding=45.22,5.68,45.14,5.78" in u)
    }

    /**
     * Une categorie designe PLUSIEURS classes du thesaurus, et c'est tout l'objet de la table : demander
     * "hotels" ne suffit pas, le service ne connait que Hotel, HotelRestaurant, HotelTrade.
     */
    @Test fun `une categorie se traduit en toutes ses classes`() {
        val u = urlDecodee(Datatourisme.catalogUrl(Datatourisme.DEFAULT_URL, 45.2, 5.6, 45.1, 5.8,
            setOf(PoiCategory.HOTELS))!!)
        PoiCategory.HOTELS.classes.forEach { assertTrue("$it absent de $u", it in u) }
    }

    /** Deux categories partagent parfois une classe : la demander deux fois alourdirait l'URL pour rien. */
    @Test fun `les classes communes a deux categories ne partent qu'une fois`() {
        val u = urlDecodee(Datatourisme.catalogUrl(Datatourisme.DEFAULT_URL, 45.2, 5.6, 45.1, 5.8,
            setOf(PoiCategory.HOTELS, PoiCategory.RESTAURANTS))!!)
        val classes = u.substringAfter("type[in]=").substringBefore(" AND ").substringBefore("&").split(",")
        assertEquals("une classe en double", classes.size, classes.distinct().size)
    }

    /**
     * Aucune categorie cochee : pas de requete du tout. Une requete sans type rapatrierait le catalogue
     * entier de la zone - des centaines de lieux dont pas un n'est demande.
     */
    @Test fun `sans categorie retenue, il n'y a rien a demander`() {
        assertNull(Datatourisme.catalogUrl(Datatourisme.DEFAULT_URL, 45.2, 5.6, 45.1, 5.8, emptySet()))
    }

    /** Instance derriere un proxy, ou URL de base finissant par une barre : le chemin doit rester sain. */
    @Test fun `l'url de base est nettoyee avant le chemin`() {
        val u = Datatourisme.catalogUrl("https://x/v1/", 45.2, 5.6, 45.1, 5.8, setOf(PoiCategory.HOTELS))!!
        assertTrue(u, u.startsWith("https://x/v1/catalog?"))
    }

    // ---------- Reponse ----------

    /** Forme relevee sur une reponse reelle : `address` et `geo` y sont des TABLEAUX, la ou le modele
     *  laisse attendre des objets. Lus en dur, ils levaient - et l'exception emportait le POI entier. */
    private val REPONSE = """
        {"objects":[
          {"uuid":"abc","label":{"@fr":"Hôtel Lesdiguières","@en":"Lesdiguieres Hotel"},
           "type":["Hotel","LodgingBusiness","Accommodation"],
           "isLocatedAt":[{"geo":{"latitude":45.191018,"longitude":5.719151},
                           "address":[{"addressLocality":"Grenoble","postalCode":"38000"}]}],
           "hasTheme":[{"key":"Bike","label":{"@fr":"Vélo"}}],
           "hasContact":[{"telephone":["+33 4 38 70 19 50"],"homepage":["http://www.lesdiguieres.com/"]}],
           "hasMainRepresentation":[{"hasRelatedResource":[{"locator":["https://cdt.example/photo.jpg"]}]}]}
        ],"meta":{"total":1}}
    """.trimIndent()

    @Test fun `un point d'interet se lit avec tout ce que l'infobulle demande`() {
        val p = Datatourisme.parse(REPONSE).single()
        assertEquals("abc", p.uuid)
        assertEquals("Hôtel Lesdiguières", p.label)
        assertEquals(45.191018, p.lat, 1e-9)
        assertEquals(5.719151, p.lon, 1e-9)
        assertEquals(PoiCategory.HOTELS, p.category)
        assertEquals("Grenoble", p.city)
        assertEquals("http://www.lesdiguieres.com/", p.webUrl)
        assertEquals("https://cdt.example/photo.jpg", p.imageUrl)
        assertTrue(p.bikeTheme)
    }

    /** Le service rend `geo` et `address` tantot en objet, tantot en tableau d'un objet. Les deux doivent
     *  se lire, faute de quoi la moitie des POIs disparait selon l'humeur de la source. */
    @Test fun `un objet ou un tableau d'un objet se lisent pareil`() {
        val enObjet = """{"objects":[{"uuid":"a","label":{"@fr":"X"},"type":["Hotel"],
            "isLocatedAt":{"geo":{"latitude":1.0,"longitude":2.0},"address":{"addressLocality":"Y"}}}]}"""
        val p = Datatourisme.parse(enObjet).single()
        assertEquals(1.0, p.lat, 1e-9)
        assertEquals("Y", p.city)
    }

    /**
     * Un lieu porte PLUSIEURS classes : un hotel-restaurant est Hotel ET Restaurant. Il est un hotel, quel
     * que soit le filtre, et sous le seul filtre "Restaurants" il ne s'affiche pas du tout.
     *
     * Le cas releve sur le centre d'Albi : la base n'y connait que six "restaurants", et les six sont des
     * hotels. Les servir sous le filtre restauration donnait une carte qui avait l'air juste.
     */
    @Test fun `un hotel-restaurant est un hotel, et rien d'autre`() {
        val json = """{"objects":[{"uuid":"a","label":{"@fr":"Hôtel du Commerce"},
            "type":["Restaurant","LodgingBusiness","Hotel"],
            "isLocatedAt":[{"geo":{"latitude":1.0,"longitude":2.0}}]}]}"""
        assertTrue(Datatourisme.parse(json, setOf(PoiCategory.RESTAURANTS)).isEmpty())
        assertEquals(PoiCategory.HOTELS,
            Datatourisme.parse(json, setOf(PoiCategory.HOTELS)).single().category)
    }

    /** Le catalogue est vivant : un enregistrement sans coordonnees, sans nom ou d'une classe qu'on
     *  n'affiche pas est ecarte, sans priver la carte des autres. */
    @Test fun `un enregistrement incomplet est ecarte, pas les autres`() {
        val json = """{"objects":[
            {"uuid":"sans-geo","label":{"@fr":"X"},"type":["Hotel"]},
            {"uuid":"sans-nom","type":["Hotel"],"isLocatedAt":[{"geo":{"latitude":1.0,"longitude":2.0}}]},
            {"uuid":"classe-inconnue","label":{"@fr":"Z"},"type":["Dolmen"],
             "isLocatedAt":[{"geo":{"latitude":1.0,"longitude":2.0}}]},
            {"uuid":"bon","label":{"@fr":"B"},"type":["Hotel"],
             "isLocatedAt":[{"geo":{"latitude":1.0,"longitude":2.0}}]}]}"""
        val pois = Datatourisme.parse(json)
        assertEquals(1, pois.size)
        assertEquals("bon", pois.single().uuid)
    }

    /** Sans nom francais, on prend celui qui vient : un marqueur nomme vaut mieux qu'un marqueur perdu. */
    @Test fun `un nom dans une autre langue vaut mieux que rien`() {
        val json = """{"objects":[{"uuid":"a","label":{"@en":"Castle Inn"},"type":["Hotel"],
            "isLocatedAt":[{"geo":{"latitude":1.0,"longitude":2.0}}]}]}"""
        assertEquals("Castle Inn", Datatourisme.parse(json).single().label)
    }

    /** Le site n'est pas garanti : l'infobulle ne rend le nom cliquable que s'il existe. */
    @Test fun `un lieu sans site web n'en invente pas`() {
        val json = """{"objects":[{"uuid":"a","label":{"@fr":"X"},"type":["Hotel"],
            "isLocatedAt":[{"geo":{"latitude":1.0,"longitude":2.0}}],"hasContact":[{"telephone":["01"]}]}]}"""
        val p = Datatourisme.parse(json).single()
        assertNull(p.webUrl)
        assertNull(p.imageUrl)
    }

    @Test fun `une reponse illisible ne leve pas`() {
        assertEquals(emptyList<Poi>(), Datatourisme.parse("<html>502</html>"))
        assertEquals(emptyList<Poi>(), Datatourisme.parse(""))
        assertEquals(emptyList<Poi>(), Datatourisme.parse("""{"objects":[]}"""))
    }

    // ---------- Categories ----------

    /**
     * Les categories de France Velo Tourisme, reparties en quatre groupes, plus celles que le terrain a
     * imposees : "Epicerie et supermarche" est la premiere qui ne vienne pas de FVT, et elle ne vient pas
     * non plus de DATAtourisme - c'est OpenStreetMap qui la porte (cf. PoiCategory.GROCERY).
     */
    @Test fun `les categories couvrent les quatre groupes`() {
        assertEquals(28, PoiCategory.entries.size)
        PoiGroup.entries.forEach { g ->
            assertTrue("$g sans categorie", PoiCategory.of(g).isNotEmpty())
        }
        assertEquals(9, PoiCategory.of(PoiGroup.LODGING).size)
        assertEquals(2, PoiCategory.of(PoiGroup.FOOD).size)
        assertEquals(7, PoiCategory.of(PoiGroup.LEISURE).size)
        assertEquals(10, PoiCategory.of(PoiGroup.PRACTICAL).size)
    }

    /**
     * Chaque categorie designe au moins une source : une categorie que NI DATAtourisme NI OpenStreetMap ne
     * remplit ne filtrerait rien et n'afficherait rien - une ligne de la bulle sans effet.
     *
     * L'invariant portait autrefois sur les seules classes DATAtourisme, et il etait trop etroit : une
     * categorie peut n'exister que dans OSM - "Epicerie et supermarche", que la base touristique ignore -
     * comme une autre peut n'exister que dans DATAtourisme, tels les "hebergements insolites" ou les
     * "villages de caractere", qui sont des jugements touristiques qu'OSM ne porte nulle part.
     */
    @Test fun `aucune categorie n'est sans source`() {
        PoiCategory.entries.forEach {
            assertTrue("${it.key} sans classe ni etiquette", it.classes.isNotEmpty() || it.osm.isNotEmpty())
        }
    }

    /** Les cles sont enregistrees en base : les changer casserait les reglages deja poses. */
    @Test fun `les cles sont celles de France Velo Tourisme`() {
        assertNotNull(PoiCategory.byKey("campings-et-aires-de-camping-car"))
        assertNotNull(PoiCategory.byKey("loueurs-reparateurs-velos"))
        assertNotNull(PoiCategory.byKey("aire_de_servies"))
        assertNull(PoiCategory.byKey("categorie-inventee"))
    }
}
