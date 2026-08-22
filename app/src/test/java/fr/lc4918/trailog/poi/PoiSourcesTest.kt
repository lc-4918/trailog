package fr.lc4918.trailog.poi

import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiGroup
import fr.lc4918.trailog.map.offline.Bbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La regle de partage entre les deux sources de points d'interet.
 *
 * C'est elle qui decide du nombre de requetes envoyees, a qui, et de ce que la carte montre a un endroit
 * donne. Une faute ici ne leve pas davantage qu'ailleurs dans ce paquet : elle vide la couche, ou double
 * le nombre d'appels sans que personne ne le voie.
 */
class PoiSourcesTest {

    private val grenoble = Bbox(west = 5.55, south = 45.10, east = 5.85, north = 45.30)
    private val berlin = Bbox(west = 13.3, south = 52.4, east = 13.5, north = 52.6)
    private val reunion = Bbox(west = 55.4, south = -21.1, east = 55.6, north = -20.9)

    // ---------- Qui repond ----------

    @Test fun `la France metropolitaine est couverte par DATAtourisme`() {
        assertTrue(PoiSources.datatourismeCovers(grenoble))
    }

    /** Les departements d'outre-mer publient dans la meme base : les oublier les priverait de leur source. */
    @Test fun `l'outre-mer est couvert lui aussi`() {
        assertTrue(PoiSources.datatourismeCovers(reunion))
    }

    @Test fun `l'etranger ne l'est pas`() {
        assertFalse(PoiSources.datatourismeCovers(berlin))
    }

    /** Une carte a cheval sur la frontiere garde sa source francaise : la moitie du champ la concerne. */
    @Test fun `une emprise a cheval sur la frontiere reste couverte`() {
        val frontiere = Bbox(west = 7.5, south = 47.4, east = 8.2, north = 47.8)   // Bale et l'Alsace
        assertTrue(PoiSources.datatourismeCovers(frontiere))
    }

    // ---------- Ce qu'on demande a OpenStreetMap ----------

    /**
     * En France, OSM complete les services du terrain - l'eau, les toilettes, les bornes, les reparateurs -
     * ET la restauration, que la base touristique ne connait qu'a travers les hotels qui servent a manger
     * (6 restaurants contre 150 sur le centre d'Albi, et les 6 etaient des hotels).
     *
     * L'hebergement et les loisirs restent a DATAtourisme, qui les decrit mieux et les illustre de photos.
     */
    @Test fun `en France, OSM repond pour le pratique et la restauration`() {
        val toutes = PoiCategory.entries.toSet()
        val demandees = PoiSources.osmCategories(grenoble, toutes)
        assertTrue(demandees.isNotEmpty())
        assertTrue(demandees.all { it.group == PoiGroup.PRACTICAL || it.group == PoiGroup.FOOD })
        assertTrue(PoiCategory.WATER in demandees)
        assertTrue("un restaurant de quartier doit etre demande a OSM",
            PoiCategory.RESTAURANTS in demandees)
        assertTrue("un bar de quartier aussi", PoiCategory.BARS in demandees)
        assertFalse("l'hebergement reste a DATAtourisme", PoiCategory.HOTELS in demandees)
        assertFalse("les loisirs aussi", PoiCategory.CULTURAL_SITES in demandees)
    }

    /** Hors de France, DATAtourisme n'a rien a dire : OSM repond seul, pour tout ce qui est coche. */
    @Test fun `hors de France, OSM repond pour tout`() {
        val demandees = PoiSources.osmCategories(berlin, setOf(PoiCategory.HOTELS, PoiCategory.WATER))
        assertEquals(setOf(PoiCategory.HOTELS, PoiCategory.WATER), demandees)
    }

    /** Rien de coche, rien a demander - et donc aucune requete envoyee (cf. PoiRepository). */
    @Test fun `sans categorie cochee, on ne demande rien`() {
        assertTrue(PoiSources.osmCategories(berlin, emptySet()).isEmpty())
    }

    // ---------- Reunion des deux reponses ----------

    private fun lieu(uuid: String, lat: Double, lon: Double, cat: PoiCategory) =
        Poi(uuid = uuid, label = uuid, lat = lat, lon = lon, category = cat)

    /** Le meme lieu connu des deux bases n'est jamais pointe au meme metre : deux marqueurs superposes se
     *  recouvrent sans qu'on puisse ouvrir celui du dessous. */
    @Test fun `un lieu connu des deux sources ne parait qu'une fois`() {
        val dt = listOf(lieu("dt", 45.2000, 5.7000, PoiCategory.PICNIC))
        val osm = listOf(lieu("osm", 45.2001, 5.7001, PoiCategory.PICNIC))   // ~13 m
        val fusion = PoiSources.merge(dt, osm)
        assertEquals(listOf("dt"), fusion.map { it.uuid })
    }

    /** DATAtourisme l'emporte, et pour une raison precise : c'est lui qui porte la photo et le site. */
    @Test fun `c'est la description la plus riche qui reste`() {
        val dt = listOf(Poi("dt", "Aire du pont", 45.2, 5.7, PoiCategory.PICNIC, imageUrl = "http://p.jpg"))
        val osm = listOf(lieu("osm", 45.2, 5.7, PoiCategory.PICNIC))
        assertEquals("http://p.jpg", PoiSources.merge(dt, osm).single().imageUrl)
    }

    /** Deux lieux distincts se tiennent couramment au meme carrefour : les confondre en effacerait un. */
    @Test fun `deux categories differentes au meme endroit restent deux lieux`() {
        val dt = listOf(lieu("dt", 45.2, 5.7, PoiCategory.BARS))
        val osm = listOf(lieu("osm", 45.2, 5.7, PoiCategory.TOILETS))
        assertEquals(2, PoiSources.merge(dt, osm).size)
    }

    /** Au-dela du seuil, ce sont deux endroits, meme de meme categorie - deux fontaines d'un village. */
    @Test fun `deux lieux eloignes de meme categorie restent deux lieux`() {
        val dt = listOf(lieu("dt", 45.2000, 5.7000, PoiCategory.WATER))
        val osm = listOf(lieu("osm", 45.2020, 5.7000, PoiCategory.WATER))   // ~220 m
        assertEquals(2, PoiSources.merge(dt, osm).size)
    }

    /** Une source muette ne doit pas emporter l'autre. */
    @Test fun `une source vide laisse passer l'autre entiere`() {
        val osm = listOf(lieu("osm", 45.2, 5.7, PoiCategory.WATER))
        assertEquals(osm, PoiSources.merge(emptyList(), osm))
        assertEquals(osm, PoiSources.merge(osm, emptyList()))
    }

    // ---------- Le decoupage par groupe ----------

    /**
     * Hors de France, une requete par groupe et non une seule qui les porte tous : la lourde mettait une
     * trentaine de secondes sur une ville dense, et rien ne s'affichait avant la fin.
     */
    @Test fun `hors de France, un groupe donne une requete`() {
        val groupes = PoiSources.osmGroups(berlin, PoiCategory.entries.toSet())
        assertEquals(4, groupes.size)
        assertTrue("chaque requete ne porte qu'un groupe",
            groupes.all { g -> g.map { it.group }.distinct().size == 1 })
        assertEquals("et aucune categorie ne se perd en chemin",
            PoiCategory.entries.toSet(), groupes.flatten().toSet())
    }

    /** En France, deux requetes au plus : la restauration et le pratique, chacune s'affichant des qu'elle
     *  repond sans attendre l'autre. */
    @Test fun `en France, le decoupage fait deux requetes`() {
        val groupes = PoiSources.osmGroups(grenoble, PoiCategory.entries.toSet())
        assertEquals(2, groupes.size)
        assertEquals(setOf(PoiGroup.FOOD, PoiGroup.PRACTICAL),
            groupes.map { g -> g.first().group }.toSet())
        assertTrue("chaque requete ne porte qu'un groupe",
            groupes.all { g -> g.map { it.group }.distinct().size == 1 })
    }

    /** Un groupe dont rien n'est coche ne vaut pas une requete. */
    @Test fun `un groupe vide ne donne pas de requete`() {
        val groupes = PoiSources.osmGroups(berlin, setOf(PoiCategory.HOTELS, PoiCategory.WATER))
        assertEquals(2, groupes.size)
        assertEquals(setOf(PoiCategory.HOTELS), groupes.first { PoiCategory.HOTELS in it })
    }

    @Test fun `sans rien de coche, aucune requete`() {
        assertTrue(PoiSources.osmGroups(berlin, emptySet()).isEmpty())
    }
}
