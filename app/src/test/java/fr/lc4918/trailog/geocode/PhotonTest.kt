package fr.lc4918.trailog.geocode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Construction de la requete et lecture de la reponse du geocodeur. Ce sont les deux seuls endroits ou une
 * faute serait muette : une URL mal formee ou un champ mal lu ne produit pas d'erreur, seulement une liste
 * de propositions vide ou des libelles incomprehensibles, indiscernables d'un service qui ne trouve rien.
 */
class PhotonTest {

    // ---------- URL ----------

    @Test fun `la requete porte le texte encode, la limite et la langue`() {
        val u = Photon.url(Photon.DEFAULT_URL, "rue de l'église", "fr", 10)
        assertTrue(u.startsWith(Photon.DEFAULT_URL + "?"))
        assertTrue("texte non encode : $u", "q=rue+de+l%27%C3%A9glise" in u)
        assertTrue("limit=10" in u)
        assertTrue("lang=fr" in u)
    }

    /**
     * Le centre connu part avec la requete : ce n'est pas lui qui CLASSE - c'est [Photon.rank] -, mais lui
     * qui fait entrer les lieux proches dans la liste des candidats. Un hameau a dix kilometres n'y
     * figurerait jamais par sa seule notoriete, et un classement ne rattrape pas ce qui n'a pas ete rendu.
     */
    @Test fun `la requete porte le centre quand on le connait`() {
        val u = Photon.url(Photon.DEFAULT_URL, "beziers", "fr", 4, 3.21 to 43.34)
        assertTrue("centre absent de $u", "lat=43.34" in u && "lon=3.21" in u)
    }

    /** Aucun centre connu - localisation eteinte, carte pas encore cadree : la requete est celle d'avant,
     *  au caractere pres. */
    @Test fun `sans centre, la requete ne porte aucune coordonnee`() {
        val u = Photon.url(Photon.DEFAULT_URL, "beziers", "fr", 4)
        assertTrue("coordonnees dans $u", "lat=" !in u && "lon=" !in u)
    }

    // ---------- Classement ----------

    /**
     * Un homonyme d'un autre continent passait devant le lieu cherche a vingt kilometres : son orthographe
     * collait, et sa notoriete le mettait en tete. La distance le fait tomber en bas de liste.
     */
    @Test fun `l'homonyme lointain passe derriere le lieu proche`() {
        val loin = lieu("Beziers, Bresil", -47.0, -15.0)
        val pres = lieu("Beziers, Herault", 3.21, 43.34)
        val r = Photon.rank(listOf(loin, pres), center = 3.87 to 43.61, limit = 10)
        assertEquals(listOf(pres, loin), r)
    }

    /**
     * Mais la notoriete ne se perd pas pour autant : la ville qu'on cherchait, a 150 km, reste devant le
     * hameau du meme nom a trois. C'est le dosage retenu - la distance compte sur une echelle
     * logarithmique, ou la region entiere se lit comme "par ici".
     */
    @Test fun `la ville a cent cinquante kilometres reste devant le hameau tout proche`() {
        val ville = lieu("Beziers, ville", 2.00, 43.60)
        val hameau = lieu("Beziers, hameau", 3.90, 43.63)
        val r = Photon.rank(listOf(ville, hameau), center = 3.87 to 43.61, limit = 10)
        assertEquals(listOf(ville, hameau), r)
    }

    /** Sans centre connu, il n'y a rien a quoi comparer : l'ordre du service ressort tel quel. */
    @Test fun `sans centre, le classement du service est conserve`() {
        val a = lieu("A", -47.0, -15.0)
        val b = lieu("B", 3.21, 43.34)
        assertEquals(listOf(a, b), Photon.rank(listOf(a, b), center = null, limit = 10))
    }

    /** La liste rendue tient dans ce que l'ecran affiche : les candidats supplementaires ne servaient
     *  qu'a reordonner. */
    @Test fun `la liste est coupee a la limite demandee`() {
        val lieux = (1..9).map { lieu("L$it", 3.0 + it / 100.0, 43.0) }
        assertEquals(3, Photon.rank(lieux, center = 3.0 to 43.0, limit = 3).size)
        assertEquals(3, Photon.rank(lieux, center = null, limit = 3).size)
    }

    private fun lieu(nom: String, lon: Double, lat: Double) = GeocodePlace(nom, lon, lat)

    /** Une instance auto-hebergee peut exposer une URL portant deja une chaine de requete (chemin derriere
     *  un reverse proxy, cle de service). Coller "?q=" derriere donnerait une URL invalide. */
    @Test fun `une url de base deja parametree recoit un et commercial`() {
        val u = Photon.url("https://geo.exemple.fr/api?token=abc", "gap", "fr", 4)
        assertTrue("https://geo.exemple.fr/api?token=abc&q=gap" in u)
    }

    /** Photon rejette (400) une langue qu'il ne sert pas, au lieu de l'ignorer : une locale non prevue
     *  rendrait donc la recherche entierement muette. */
    @Test fun `une langue non servie retombe sur l'anglais`() {
        assertTrue("lang=en" in Photon.url(Photon.DEFAULT_URL, "gap", "eu", 4))
        assertTrue("lang=en" in Photon.url(Photon.DEFAULT_URL, "gap", "pt", 4))
        assertTrue("lang=de" in Photon.url(Photon.DEFAULT_URL, "gap", "de", 4))
    }

    /** La LECTURE de la reponse ne reordonne rien : le classement est le fait de [Photon.rank], et lui
     *  seul (cf. plus haut). */
    @Test fun `l'ordre du service est conserve a la lecture`() {
        val r = Photon.parse(fc(
            feature(""""name":"Béziers","postcode":"34500","city":"Béziers","country":"France""""),
            feature(""""name":"Béziers","county":"Aude","country":"France""""),
        ))
        assertEquals(listOf("34500 Béziers, France", "Béziers, Aude, France"), r.map { it.label })
    }

    // ---------- Reponse ----------

    private fun feature(props: String, coords: String = "[6.08,44.56]") =
        """{"type":"Feature","geometry":{"type":"Point","coordinates":$coords},"properties":{$props}}"""

    private fun fc(vararg features: String) =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    @Test fun `une adresse complete se lit en une ligne`() {
        val r = Photon.parse(fc(feature(
            """"housenumber":"12","street":"Rue Carnot","postcode":"38000","city":"Grenoble","country":"France"""")))
        assertEquals(1, r.size)
        assertEquals("12 Rue Carnot, 38000 Grenoble, France", r[0].label)
        assertEquals(6.08, r[0].lon, 1e-9)
        assertEquals(44.56, r[0].lat, 1e-9)
    }

    /** Photon renvoie name ET city identiques pour une commune : les concatener donnerait
     *  "Grenoble, 38000 Grenoble". */
    @Test fun `le nom qui repete la commune est omis`() {
        val r = Photon.parse(fc(feature(""""name":"Grenoble","postcode":"38000","city":"Grenoble","country":"France"""")))
        assertEquals("38000 Grenoble, France", r[0].label)
    }

    @Test fun `un lieu nomme garde son nom devant la commune`() {
        val r = Photon.parse(fc(feature(""""name":"Gare de Grenoble","city":"Grenoble","country":"France"""")))
        assertEquals("Gare de Grenoble, Grenoble, France", r[0].label)
    }

    /** Sommets et lieux-dits n'ont pas de commune : sans repli sur le departement, l'entree se reduirait
     *  au seul pays, et deux sommets voisins seraient impossibles a distinguer dans la liste. */
    @Test fun `sans commune on retombe sur le departement`() {
        val r = Photon.parse(fc(feature(""""name":"Pic de Bure","county":"Hautes-Alpes","country":"France"""")))
        assertEquals("Pic de Bure, Hautes-Alpes, France", r[0].label)
    }

    /** Une entree inexploitable ne doit pas emporter les autres avec elle. */
    @Test fun `une entree sans coordonnees est ignoree, les autres restent`() {
        val r = Photon.parse(fc(
            """{"type":"Feature","properties":{"name":"Sans geometrie"}}""",
            feature(""""name":"Gap","country":"France""""),
        ))
        assertEquals(1, r.size)
        assertEquals("Gap, France", r[0].label)
    }

    @Test fun `une entree sans propriete affiche ses coordonnees`() {
        val r = Photon.parse(fc(feature("")))
        assertEquals("44.56000, 6.08000", r[0].label)
    }

    /** Le service peut repondre autre chose que ce qu'on attend (page d'erreur d'un proxy, instance
     *  auto-hebergee mal configuree). Une exception ici viderait la recherche a la premiere frappe. */
    @Test fun `une reponse illisible donne une liste vide, sans lever`() {
        assertTrue(Photon.parse("<html>502 Bad Gateway</html>").isEmpty())
        assertTrue(Photon.parse("").isEmpty())
        assertTrue(Photon.parse("""{"features":"pas une liste"}""").isEmpty())
    }

    /**
     * L'adresse est aussi rendue en morceaux - intitule, voie, commune : ce sont les seules coupures que
     * l'infobulle s'autorise quand l'adresse ne tient pas sur une ligne. Coupee ailleurs - a la derniere
     * espace qui rentre - elle separerait un code postal de sa ville, ou un nom de voie en deux.
     */
    @Test fun `l'adresse se rend aussi en morceaux, intitule puis voie puis commune`() {
        val r = Photon.parse(fc(feature(
            """"name":"Vitesco Technologies","street":"Avenue du Général de Croutte","postcode":"31100","city":"Toulouse","country":"France"""")))
        assertEquals(
            listOf("Vitesco Technologies", "Avenue du Général de Croutte", "31100 Toulouse, France"),
            r[0].lines,
        )
        assertEquals("Vitesco Technologies, Avenue du Général de Croutte, 31100 Toulouse, France", r[0].label)
    }

    /** Le numero reste colle a sa voie : ils ne se separent jamais, et ne comptent que pour un morceau. */
    @Test fun `le numero fait partie de la voie`() {
        val r = Photon.parse(fc(feature(
            """"housenumber":"12","street":"Rue Carnot","postcode":"38000","city":"Grenoble","country":"France"""")))
        assertEquals(listOf("12 Rue Carnot", "38000 Grenoble, France"), r[0].lines)
    }

    /** Une entree qui n'est qu'une commune n'a rien a couper : un seul morceau, et le libelle d'un seul
     *  tenant ne s'en trouve pas ampute d'une virgule. */
    @Test fun `une commune seule ne fait qu'un morceau`() {
        val r = Photon.parse(fc(feature(""""name":"Grenoble","postcode":"38000","city":"Grenoble","country":"France"""")))
        assertEquals(listOf("38000 Grenoble, France"), r[0].lines)
        assertEquals("38000 Grenoble, France", r[0].label)
    }

    /** Photon ajoute des champs au fil de ses versions : les ignorer, plutot que d'echouer dessus. */
    @Test fun `les champs inconnus n'empechent pas la lecture`() {
        val r = Photon.parse(fc(feature(""""name":"Gap","osm_id":1234,"extent":[1,2,3,4],"country":"France"""")))
        assertEquals("Gap, France", r[0].label)
    }

    // ---------- URL du geocodage inverse ----------

    /**
     * L'inverse n'est pas servi par le meme chemin que la recherche : viser `/api` avec lon/lat rend une
     * erreur, non une adresse. C'est la terminaison de l'URL reglee qui change, l'instance restant la meme.
     */
    @Test fun `l'url inverse remplace api par reverse`() {
        val u = Photon.reverseUrl(Photon.DEFAULT_URL, 6.08, 44.56, "fr")
        assertTrue("chemin inattendu : $u", u.startsWith("https://photon.komoot.io/reverse?"))
        assertTrue("/api" !in u)
        assertTrue("lon=6.08" in u)
        assertTrue("lat=44.56" in u)
        assertTrue("lang=fr" in u)
    }

    /** Une instance exposee a la racine, ou derriere un chemin qui ne finit pas par `api`, recoit le
     *  segment sans rien perdre du sien. */
    @Test fun `une base sans api recoit reverse a la suite`() {
        assertTrue("https://geo.exemple.fr/photon/reverse?" in
            Photon.reverseUrl("https://geo.exemple.fr/photon/", 6.0, 44.0, "fr"))
    }

    /** Meme parametrage qu'une recherche (cle de service, chemin derriere un proxy) : la chaine de requete
     *  deja presente doit survivre, devant les parametres du point. */
    @Test fun `une url de base deja parametree conserve sa requete`() {
        val u = Photon.reverseUrl("https://geo.exemple.fr/api?token=abc", 6.0, 44.0, "fr")
        assertTrue(u, u.startsWith("https://geo.exemple.fr/reverse?token=abc&"))
        assertTrue("lon=6.0" in u)
    }

    /**
     * Les coordonnees s'ecrivent au point decimal, quelle que soit la locale de l'appareil : une virgule
     * francaise separerait a la fois les decimales et les deux valeurs, et le service lirait tout autre chose.
     */
    @Test fun `les coordonnees ne suivent pas la locale`() {
        val defaut = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE)
            val u = Photon.reverseUrl(Photon.DEFAULT_URL, 6.08, 44.56, "fr")
            assertTrue("virgule decimale dans $u", "6,08" !in u && "44,56" !in u)
        } finally {
            java.util.Locale.setDefault(defaut)
        }
    }

    /** Meme repli que la recherche : Photon rejette (400) une langue qu'il ne sert pas. */
    @Test fun `l'url inverse retombe sur l'anglais pour une langue non servie`() {
        assertTrue("lang=en" in Photon.reverseUrl(Photon.DEFAULT_URL, 6.0, 44.0, "eu"))
    }
}
