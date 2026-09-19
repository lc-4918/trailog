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

    /** Une cellule de la grille autour d'un point. */
    private fun cellule(lon: Double, lat: Double): Bbox = PoiCells.of(lon, lat).bbox

    private val grenoble = cellule(5.72, 45.19)
    private val berlin = cellule(13.40, 52.52)
    private val logrono = cellule(-2.45, 42.46)
    private val reunion = cellule(55.45, -20.88)

    // ---------- Ce que DATAtourisme couvre ----------

    @Test fun `la France metropolitaine est couverte par DATAtourisme`() {
        listOf(2.35 to 48.86, 5.72 to 45.19, -4.49 to 48.39, 7.75 to 48.58, 7.26 to 43.70, 2.89 to 42.70,
            3.06 to 50.63, -1.56 to 43.48, 9.15 to 42.15).forEach { (lon, lat) ->
            assertTrue("$lon, $lat", PoiSources.datatourismeCovers(lon, lat))
        }
    }

    /** Les departements d'outre-mer publient dans la meme base : les oublier les priverait de leur source. */
    @Test fun `l'outre-mer est couvert lui aussi`() {
        assertEquals(PoiSources.Coverage.FULL, PoiSources.coverage(reunion))
    }

    /**
     * **Le rectangle d'avant couvrait le nord de l'Espagne, la Belgique et la Suisse romande**, et
     * l'hebergement et les loisirs y etaient confies a DATAtourisme seul, qui n'en connait rien : ces deux
     * groupes restaient vides a Logrono.
     */
    @Test fun `les pays voisins ne le sont pas, meme pres de la frontiere`() {
        listOf(-2.45 to 42.46, -1.64 to 42.81, 2.17 to 41.39, 4.35 to 50.85, 6.13 to 49.61, 6.14 to 46.20,
            6.63 to 46.52, 7.69 to 45.07, 1.52 to 42.51, 7.59 to 47.56, 7.00 to 49.23, 4.31 to 50.05)
            .forEach { (lon, lat) -> assertFalse("$lon, $lat", PoiSources.datatourismeCovers(lon, lat)) }
        assertEquals(PoiSources.Coverage.NONE, PoiSources.coverage(logrono))
        assertEquals(PoiSources.Coverage.NONE, PoiSources.coverage(berlin))
    }

    // ---------- Qui sert quoi ----------

    /**
     * En France, OSM sert les services du terrain ET la restauration, que la base touristique ne connait
     * qu'a travers les hotels qui servent a manger (6 restaurants contre 150 sur le centre d'Albi). Les
     * hebergements et les loisirs restent a DATAtourisme, qui les illustre de photos.
     */
    @Test fun `en France, chaque groupe a sa source`() {
        assertEquals(setOf(PoiSource.OSM), PoiSources.sources(grenoble, PoiGroup.FOOD))
        assertEquals(setOf(PoiSource.OSM), PoiSources.sources(grenoble, PoiGroup.PRACTICAL))
        assertEquals(setOf(PoiSource.DATATOURISME), PoiSources.sources(grenoble, PoiGroup.LODGING))
        assertEquals(setOf(PoiSource.DATATOURISME), PoiSources.sources(grenoble, PoiGroup.LEISURE))
    }

    /** Hors de France, OSM sert tout - a Logrono comme a Berlin. */
    @Test fun `hors de France, OSM sert tout`() {
        PoiGroup.entries.forEach { g ->
            assertEquals(setOf(PoiSource.OSM), PoiSources.sources(logrono, g))
            assertEquals(setOf(PoiSource.OSM), PoiSources.sources(berlin, g))
        }
    }

    /** Le complement coupe, DATAtourisme reprend tout en France : six restaurants valent mieux que zero. */
    @Test fun `le complement eteint rend la France a DATAtourisme seul`() {
        PoiGroup.entries.forEach { g ->
            assertEquals(setOf(PoiSource.DATATOURISME), PoiSources.sources(grenoble, g, complement = false))
        }
    }

    /** Le meme reglage hors de France est ignore : OSM y est la seule source, et l'ecouter viderait tout. */
    @Test fun `le complement eteint ne vide pas la couche hors de France`() {
        PoiGroup.entries.forEach { g ->
            assertEquals(setOf(PoiSource.OSM), PoiSources.sources(logrono, g, complement = false))
        }
    }

    /** Une cellule a cheval sur la frontiere a besoin des deux : la part francaise de DATAtourisme,
     *  l'autre d'OpenStreetMap. */
    @Test fun `a cheval sur la frontiere, les deux sources`() {
        val bale = cellule(7.55, 47.55)
        assertEquals(PoiSources.Coverage.PARTIAL, PoiSources.coverage(bale))
        assertEquals(setOf(PoiSource.DATATOURISME, PoiSource.OSM), PoiSources.sources(bale, PoiGroup.LODGING))
        assertEquals(setOf(PoiSource.OSM), PoiSources.sources(bale, PoiGroup.FOOD))
    }

    // ---------- Ce qu'on demande a chaque source ----------

    /** Tout le groupe, coche ou non : le filtre s'applique ensuite a ce qu'on a, sans requete. */
    @Test fun `une source recoit le groupe entier`() {
        val food = PoiSources.categories(PoiSource.OSM, setOf(PoiGroup.FOOD))
        assertEquals(PoiCategory.of(PoiGroup.FOOD).toSet(), food)
    }

    /** Une categorie sans etiquette OSM n'est pas demandee a OSM : il n'y a rien a y chercher. */
    @Test fun `une source ne recoit que ce qu'elle sait decrire`() {
        val osm = PoiSources.categories(PoiSource.OSM, setOf(PoiGroup.LODGING))
        assertFalse(PoiCategory.UNUSUAL in osm)
        assertTrue(PoiCategory.HOTELS in osm)
        val dt = PoiSources.categories(PoiSource.DATATOURISME, setOf(PoiGroup.PRACTICAL))
        assertFalse("DATAtourisme ne connait pas les epiceries", PoiCategory.GROCERY in dt)
        assertTrue(PoiCategory.WATER in dt)
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
}
