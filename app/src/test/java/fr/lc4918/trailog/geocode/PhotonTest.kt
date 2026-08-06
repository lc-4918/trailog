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
     * La requete ne doit **jamais** porter de biais geographique. Photon accepte `lat`/`lon` et reordonne
     * alors les resultats par proximite : chercher "Beziers" depuis l'Herault ferait remonter un hameau
     * voisin du meme nom devant la ville. Sans eux, le classement reste celui de l'importance OSM.
     */
    @Test fun `la requete ne porte aucun biais de proximite`() {
        val u = Photon.url(Photon.DEFAULT_URL, "beziers", "fr", 4)
        assertTrue("biais de proximite dans $u", "lat=" !in u && "lon=" !in u)
    }

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

    /** Photon rend ses resultats deja classes : les reordonner ici defairait ce classement. */
    @Test fun `l'ordre du service est conserve`() {
        val r = Photon.parse(fc(
            feature(""""name":"Béziers","postcode":"34500","city":"Béziers","country":"France""""),
            feature(""""name":"Béziers","county":"Aude","country":"France""""),
        ))
        assertEquals(listOf("34500 Béziers, France", "Béziers, Aude, France"), r.map { it.label })
    }

    // ---------- Portee du service (avertissement d'absence de connexion) ----------

    @Test fun `l'instance publique exige internet`() {
        assertTrue(Photon.needsInternet(Photon.DEFAULT_URL))
        assertTrue(Photon.needsInternet("https://geo.exemple.fr/api"))
        assertTrue(Photon.needsInternet("https://api-adresse.data.gouv.fr/search/"))
    }

    /** Une instance auto-hebergee reste joignable en wifi sans sortie Internet : refuser la recherche dans
     *  ce cas serait faux, et priverait justement l'utilisateur qui a fait l'effort de l'heberger. */
    @Test fun `une instance du reseau local n'exige pas internet`() {
        listOf(
            "http://192.168.1.10:2322/api",
            "http://10.0.0.5/api",
            "http://172.16.0.1/api",
            "http://172.31.255.254/api",
            "http://127.0.0.1:2322/api",
            "http://localhost:2322/api",
            "http://photon.local/api",
            "http://nas:2322/api",          // nom de machine seul, resolu sur le reseau local
        ).forEach { assertTrue("compte a tort comme externe : $it", !Photon.needsInternet(it)) }
    }

    /** 172.16.0.0/12 s'arrete a 172.31 : au-dela, l'adresse est publique et routable. */
    @Test fun `les adresses hors plages privees exigent internet`() {
        assertTrue(Photon.needsInternet("http://172.32.0.1/api"))
        assertTrue(Photon.needsInternet("http://172.15.0.1/api"))
        assertTrue(Photon.needsInternet("http://193.168.1.10/api"))
    }

    /** Prevenir a tort vaut mieux que laisser une recherche echouer sans explication. */
    @Test fun `une url illisible est comptee comme externe`() {
        assertTrue(Photon.needsInternet("pas une url"))
        assertTrue(Photon.needsInternet(""))
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

    /** Photon ajoute des champs au fil de ses versions : les ignorer, plutot que d'echouer dessus. */
    @Test fun `les champs inconnus n'empechent pas la lecture`() {
        val r = Photon.parse(fc(feature(""""name":"Gap","osm_id":1234,"extent":[1,2,3,4],"country":"France"""")))
        assertEquals("Gap, France", r[0].label)
    }
}
