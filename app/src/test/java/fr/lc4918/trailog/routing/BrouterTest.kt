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

/**
 * Construction de la requete et lecture de la reponse du second moteur d'itineraire.
 *
 * Les memes fautes muettes que chez Valhalla - une requete mal formee ne leve rien, elle rend "aucun
 * itineraire" - et deux qui n'existent que chez celui-ci : les coordonnees y vont en lon,lat, l'ordre
 * INVERSE de celui de l'application, et le coeur du reglage n'est pas dans la requete mais dans le texte
 * du profil qu'elle accompagne.
 */
class BrouterTest {

    // ---------- Requete ----------

    /**
     * BRouter attend lon,lat quand toute l'application, et Valhalla, disent lat,lon. Inverser ne leve
     * rien : ca rend un itineraire ailleurs, ou pas d'itineraire du tout.
     */
    @Test fun `les coordonnees partent en lon,lat, l'inverse du reste de l'application`() {
        val u = Brouter.routeUrl("https://brouter.de/brouter", listOf(43.0765 to 1.9476, 43.0881 to 1.8743), "p")
        assertTrue(u, "lonlats=1.9476%2C43.0765%7C1.8743%2C43.0881" in u)
    }

    @Test fun `la requete demande le geojson et le meilleur trajet`() {
        val u = Brouter.routeUrl("https://brouter.de/brouter", listOf(1.0 to 2.0, 3.0 to 4.0), "custom_1")
        assertTrue(u, "profile=custom_1" in u)
        assertTrue(u, "format=geojson" in u)
        assertTrue(u, "alternativeidx=0" in u)
    }

    /** Les etapes intermediaires du planificateur doivent figurer dans l'ordre donne. */
    @Test fun `les etapes intermediaires figurent dans l'ordre donne`() {
        val u = Brouter.routeUrl("https://x/brouter", listOf(1.0 to 2.0, 3.0 to 4.0, 5.0 to 6.0), "p")
        assertTrue(u, "lonlats=2.0%2C1.0%7C4.0%2C3.0%7C6.0%2C5.0" in u)
    }

    /** Les coordonnees passent par toString(), insensible a la locale : une virgule decimale francaise
     *  separerait ici a la fois les decimales ET les deux valeurs du couple. */
    @Test fun `les coordonnees gardent le point decimal en locale francaise`() {
        val defaut = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE)
            val u = Brouter.routeUrl("https://x/brouter", listOf(43.56 to 6.08, 45.18 to 5.72), "p")
            assertTrue(u, "6.08%2C43.56" in u)
        } finally {
            java.util.Locale.setDefault(defaut)
        }
    }

    /** Le depot du profil est un chemin frere, comme le /reverse du geocodeur. */
    @Test fun `le profil se depose sur le chemin frere`() {
        assertEquals("https://brouter.de/brouter/profile", Brouter.profileUrl("https://brouter.de/brouter"))
        assertEquals("https://x/brouter/profile", Brouter.profileUrl("https://x/brouter/"))
    }

    /** Instance derriere un proxy a jeton : la chaine de requete doit rester DERRIERE le chemin, sans quoi
     *  le /profile atterrirait au milieu des parametres. */
    @Test fun `une url de base deja parametree garde sa chaine de requete`() {
        assertEquals("https://x/brouter/profile?token=abc", Brouter.profileUrl("https://x/brouter?token=abc"))
        val u = Brouter.routeUrl("https://x/brouter?token=abc", listOf(1.0 to 2.0, 3.0 to 4.0), "p")
        assertTrue(u, "https://x/brouter?token=abc&lonlats=" in u)
    }

    // ---------- Reponse ----------

    private val REPONSE = """
        {"type":"FeatureCollection","features":[{"type":"Feature",
         "properties":{"creator":"BRouter-1.7.10","track-length":"7079","filtered ascend":"2",
           "total-time":"1138","times":[0,2.5],"messages":[["Longitude","Latitude"],["1947355","43076669"]]},
         "geometry":{"type":"LineString","coordinates":[[1.947554,43.076693,327.0],[1.947355,43.076669,326.0]]}}]}
    """.trimIndent()

    /** Les totaux arrivent en CHAINES et en metres/secondes, la ou Valhalla rend des nombres et des km. */
    @Test fun `le total se lit en metres et en secondes`() {
        val r = Brouter.parse(REPONSE)!!
        assertEquals(7079.0, r.meters, 1e-6)
        assertEquals(1138.0, r.seconds, 1e-6)
    }

    /** L'altitude vient AVEC la geometrie : c'est ce qui evite le second appel que Valhalla demandait. */
    @Test fun `le trace porte l'altitude de chaque point`() {
        val r = Brouter.parse(REPONSE)!!
        assertEquals(2, r.points.size)
        assertEquals(1.947554, r.points[0].lon, 1e-9)
        assertEquals(43.076693, r.points[0].lat, 1e-9)
        assertEquals(327.0, r.points[0].ele!!, 1e-9)
    }

    /** Un point sans troisieme valeur garde une altitude NULLE : zero est une altitude valide, et le
     *  niveau de la mer en travers d'un profil de montagne se lit comme un gouffre. */
    @Test fun `un point sans altitude n'en invente pas`() {
        val r = Brouter.parse(
            """{"features":[{"properties":{"track-length":"10","total-time":"5"},
               "geometry":{"coordinates":[[1.0,2.0],[3.0,4.0]]}}]}""")!!
        assertNull(r.points[0].ele)
    }

    @Test fun `une reponse sans total ne donne aucun itineraire`() {
        assertNull(Brouter.parse("""{"features":[{"properties":{"track-length":"10"}}]}"""))
        assertNull(Brouter.parse("""{"features":[]}"""))
    }

    /** Page d'erreur d'un proxy, ou l'erreur nue que rend BRouter pour un profil perime. */
    @Test fun `une reponse illisible ne leve pas`() {
        assertNull(Brouter.parse("<html>502 Bad Gateway</html>"))
        assertNull(Brouter.parse(""))
    }

    @Test fun `l'identifiant du profil depose se lit`() {
        val d = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<Brouter.Deposit>("""{"profileid":"custom_178"}""")
        assertEquals("custom_178", d.profileId)
    }

    // ---------- Profils ----------

    /** Une discipline, un profil : se tromper de fichier donnerait un itineraire plausible mais faux. */
    @Test fun `chaque discipline a son profil`() {
        assertEquals("brouter/fastbike.brf", BrouterProfile.assetOf(RoutingProfile.ROAD_BIKE))
        assertEquals("brouter/gravel.brf", BrouterProfile.assetOf(RoutingProfile.GRAVEL))
        assertEquals("brouter/trekking.brf", BrouterProfile.assetOf(RoutingProfile.HYBRID_BIKE))
        assertEquals("brouter/mtb.brf", BrouterProfile.assetOf(RoutingProfile.MOUNTAIN_BIKE))
        assertEquals("brouter/hiking-mountain.brf", BrouterProfile.assetOf(RoutingProfile.FOOT))
    }

    /** Le signe egal est facultatif : trekking.brf ecrit "assign x = 1", mtb.brf ecrit "assign x 1".
     *  Les deux dialectes cohabitent dans les assets, la meme expression doit lire les deux. */
    @Test fun `la reecriture lit les deux dialectes`() {
        assertEquals("assign   x = 2  # commentaire",
            BrouterProfile.assign("assign   x = 1  # commentaire", "x", "2"))
        assertEquals("assign   x  2 # commentaire",
            BrouterProfile.assign("assign   x  1 # commentaire", "x", "2"))
    }

    /**
     * La PREMIERE declaration seulement : un profil declare sa variable puis la derive plus bas
     * (assign downhillcost = if consider_elevation then downhillcost else 0). Ecraser la derivee
     * emporterait la logique du profil, celle-la meme qu'on veut garder.
     */
    @Test fun `seule la premiere declaration est reecrite`() {
        val t = "assign a = 1\nassign b = 2\nassign a = if b then a else 0\n"
        assertEquals("assign a = 9\nassign b = 2\nassign a = if b then a else 0\n",
            BrouterProfile.assign(t, "a", "9"))
    }

    /** Un profil amont qui renomme un curseur ne doit pas priver d'itineraire : on perd le reglage, pas
     *  le trajet. C'est la table ci-dessous qui rattrape la derive, pas une exception a l'usage. */
    @Test fun `une variable absente laisse le profil intact`() {
        assertEquals("assign a = 1\n", BrouterProfile.assign("assign a = 1\n", "zzz", "9"))
    }

    /** Comme chez Valhalla, la position centrale n'ecrit rien - mais pour la raison inverse : ici elle
     *  laisse au profil la valeur que ses auteurs ont retenue, ce qui est le plus sur des defauts. */
    @Test fun `les positions centrales ne reecrivent rien`() {
        RoutingProfile.entries.forEach { p ->
            assertTrue("$p", BrouterProfile.tuningOf(p, RoutingPrefs.Balanced).isEmpty())
        }
    }

    /**
     * Le sens de consider_elevation est contre-intuitif et vaut d'etre verrouille : le mettre a VRAI fait
     * EVITER le denivele. "Accepter le denivele" s'ecrit donc en l'eteignant, et l'ecrire a l'envers
     * rendrait des trajets plus longs sans que rien ne le dise.
     */
    @Test fun `accepter le denivele eteint consider_elevation`() {
        val seek = BrouterProfile.tuningOf(RoutingProfile.HYBRID_BIKE, RoutingPrefs(hills = HillPref.SEEK))
        assertEquals("false", seek["consider_elevation"])
        val avoid = BrouterProfile.tuningOf(RoutingProfile.HYBRID_BIKE, RoutingPrefs(hills = HillPref.AVOID))
        assertEquals("true", avoid["consider_elevation"])
    }

    /**
     * Chaque profil parle son propre dialecte : la MEME demande porte cinq noms. C'est ce test qui dit
     * pourquoi la table de traduction n'est pas une redondance.
     */
    @Test fun `privilegier les voies douces porte un nom par profil, ou aucun`() {
        fun voies(p: RoutingProfile) = BrouterProfile.tuningOf(p, RoutingPrefs(ways = WayPref.SOFT))
        // Le velo de route est le seul a n'avoir RIEN a dire ici : son unique levier echange la
        // circulation contre le revetement, et l'actionner l'envoie sur les chemins (mesure : 60 % de
        // chemins sur Grenoble - Voiron). Son profil tient deja la route sans qu'on le lui demande.
        assertTrue(voies(RoutingProfile.ROAD_BIKE).isEmpty())
        assertTrue(voies(RoutingProfile.GRAVEL).containsKey("prefer_cycle_routes"))
        assertTrue(voies(RoutingProfile.HYBRID_BIKE).containsKey("stick_to_cycleroutes"))
        assertTrue(voies(RoutingProfile.MOUNTAIN_BIKE).containsKey("cycleroutes_pref"))
        // Le marcheur non plus n'a rien a dire : au-dela du 0,20 de son profil, hiking_routes_preference
        // ne bouge rien puis se retourne (Revel - Soreze : 57 % de chemins tombent a 22 %, le balisage y
        // suivant la route). Les deux profils qui n'ont pas de levier sont ceux qui n'en ont pas besoin.
        assertTrue(voies(RoutingProfile.FOOT).isEmpty())
        // En baisser reste utile, et c'est ce que veut dire "par les routes" : le levier existe, il ne
        // sert qu'a l'autre bout.
        assertTrue(BrouterProfile.tuningOf(RoutingProfile.FOOT, RoutingPrefs(ways = WayPref.ROADS))
            .containsKey("hiking_routes_preference"))
    }

    /** Le pendant du max_hiking_difficulty demande a Valhalla : la cotation au-dela de laquelle un sentier
     *  est interdit au marcheur. */
    @Test fun `accepter les chemins ouvre au marcheur les sentiers de montagne`() {
        val rough = BrouterProfile.tuningOf(RoutingProfile.FOOT, RoutingPrefs(surface = SurfacePref.ROUGH))
        assertEquals("3", rough["SAC_scale_limit"])
        val paved = BrouterProfile.tuningOf(RoutingProfile.FOOT, RoutingPrefs(surface = SurfacePref.PAVED))
        assertEquals("1", paved["SAC_scale_limit"])
        // Et surtout PAS path_preference : il penalise tout ce qui n'est pas un sentier, voie verte
        // comprise - Moulin-Neuf - Mirepoix y perdait la sienne, 87 % de voies douces tombant a 24 %.
        assertTrue("path_preference ne doit pas revenir", SurfacePref.entries.none { s ->
            "path_preference" in BrouterProfile.tuningOf(RoutingProfile.FOOT, RoutingPrefs(surface = s))
        })
    }

    /** Le velo de route n'a pas de levier de revetement : fastbike.brf n'en expose aucun, et n'en a pas
     *  besoin - il ne quitte pas le revetu, mesure sur les quatre trajets de reference. */
    @Test fun `le velo de route n'a rien a dire du revetement`() {
        SurfacePref.entries.forEach { s ->
            assertTrue("$s", BrouterProfile.tuningOf(RoutingProfile.ROAD_BIKE, RoutingPrefs(surface = s))
                .keys.none { it in setOf("unpavedPenalty", "prefer_unpaved_paths", "path_preference") })
        }
    }

    /** Le texte envoye est le profil officiel, ses valeurs reglees - pas un profil reecrit. */
    @Test fun `regler un profil ne touche qu'aux lignes concernees`() {
        val brut = "# entete\n---context:global\nassign stick_to_cycleroutes = false\nassign autre = 1\n"
        val t = BrouterProfile.tune(brut, RoutingProfile.HYBRID_BIKE, RoutingPrefs(ways = WayPref.SOFT))
        assertTrue(t, "assign stick_to_cycleroutes = true" in t)
        assertTrue("le reste du profil doit rester intact", "assign autre = 1" in t && "# entete" in t)
    }
}
