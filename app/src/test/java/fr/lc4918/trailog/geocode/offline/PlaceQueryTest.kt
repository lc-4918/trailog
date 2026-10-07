package fr.lc4918.trailog.geocode.offline

import fr.lc4918.trailog.routing.offline.BrouterTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que l'index hors ligne comprend de la frappe, et comment il classe ce qu'il rend. Rien ici ne touche
 * une base : ce sont les parties ou une faute est silencieuse.
 */
class PlaceQueryTest {

    // ---------- La frappe ----------

    @Test fun `chaque mot est cite, le dernier cherche en prefixe`() {
        assertEquals("\"mont\" \"bla\"*", PlaceQuery.match("mont bla"))
        // La casse est gardee : c'est le tokenizer de la base qui la neutralise (cf. PlaceIndexTest).
        assertEquals("\"Revel\"*", PlaceQuery.match("Revel"))
    }

    /** La syntaxe de la requete n'est pas celle de l'utilisateur : ni "OR", ni tiret, ni guillemet. */
    @Test fun `la syntaxe de la requete ne passe pas`() {
        assertEquals("\"Saint\" \"Jean\" \"de\" \"Maurienne\"*", PlaceQuery.match("Saint-Jean-de-Maurienne"))
        assertEquals("\"a\" \"OR\" \"b\"*", PlaceQuery.match("a OR \"b"))
        assertEquals("\"cafe\" \"du\" \"nord\"*", PlaceQuery.match("  cafe: du (nord) "))
    }

    @Test fun `une frappe sans mot ne cherche rien`() {
        assertNull(PlaceQuery.match(""))
        assertNull(PlaceQuery.match(" - ( ) \" "))
    }

    @Test fun `les chiffres et les accents sont des mots`() {
        assertEquals("\"route\" \"d\" \"évian\" \"74\"*", PlaceQuery.match("route d'évian 74"))
    }

    @Test fun `deux noms se comparent sans accents ni casse`() {
        assertEquals("evian-les-bains", PlaceQuery.fold("Évian-les-Bains"))
        assertEquals("ile d'yeu", PlaceQuery.fold("Île d'Yeu"))
    }

    // ---------- Les cases ----------

    /** La meme arithmetique que le script : 45,19 degres nord, 5,72 est. */
    @Test fun `une case se calcule en entiers`() {
        assertEquals(4519L * 100_000 + 572 + 20_000, PlaceQuery.cellOf(4_519_000, 572_000))
        assertEquals("deux points de la meme case", PlaceQuery.cellOf(4_519_000, 572_000), PlaceQuery.cellOf(4_519_999, 572_999))
    }

    /** A l'ouest de Greenwich, -0,5 degre est dans la case -1 : le plancher, pas la troncature. */
    @Test fun `une longitude negative tombe dans la case d'en dessous`() {
        assertEquals(4500L * 100_000 - 1 + 20_000, PlaceQuery.cellOf(4_500_000, -1))
        assertEquals(4500L * 100_000 - 50 + 20_000, PlaceQuery.cellOf(4_500_000, -50_000))
    }

    @Test fun `une latitude sud donne une case negative`() {
        assertEquals(-3301L * 100_000 + 1800 + 20_000, PlaceQuery.cellOf(-3_300_001, 1_800_000))
    }

    @Test fun `les plages de cases couvrent le point et son entourage`() {
        val ranges = PlaceQuery.cellRanges(45.19, 5.72, radius = 2)
        assertEquals(5, ranges.size)
        // La rangee du milieu contient la case du point, a deux cases de chaque bord.
        val cell = PlaceQuery.cellOf(4_519_000, 572_000)
        assertTrue(cell in ranges[2])
        assertEquals(5, ranges[2].count())
        assertEquals(cell - 2, ranges[2].first)
        assertEquals(cell + 2, ranges[2].last)
        assertEquals("une rangee de latitude plus loin : cent mille cases", ranges[3].first - ranges[2].first, 100_000L)
    }

    @Test fun `un point pres d'un bord de carre ouvre aussi le carre voisin`() {
        // 44,99 N : a 0,01 degre du carre du nord.
        assertEquals(setOf(BrouterTile(5, 40), BrouterTile(5, 45)), PlaceQuery.tilesAround(7.0, 44.99))
        assertEquals(setOf(BrouterTile(5, 45)), PlaceQuery.tilesAround(7.0, 47.0))
        // Un coin : quatre carres.
        assertEquals(4, PlaceQuery.tilesAround(5.0, 45.0).size)
    }

    // ---------- Le classement ----------

    private fun hit(name: String, kind: Int, lon: Double = 1.0, lat: Double = 43.0, locality: String? = null) =
        PlaceHit(name, kind, lon, lat, locality)

    @Test fun `le nom exactement tape passe d'abord, puis l'importance`() {
        val ordered = PlaceQuery.order(
            listOf(
                hit("Revel-Tourdan", PlaceKind.VILLAGE, lat = 45.0),
                hit("Revel", PlaceKind.HAMLET, lat = 44.0),
                hit("Revel", PlaceKind.TOWN, lat = 43.5),
                hit("Revelles", PlaceKind.CITY, lat = 49.0),
            ),
            "revel",
        )
        assertEquals(listOf("Revel", "Revel", "Revelles", "Revel-Tourdan"), ordered.map { it.name })
        assertEquals(PlaceKind.TOWN, ordered[0].kind)
    }

    /** Une commune est parfois a la fois un town et un village, d'un carre a l'autre. */
    @Test fun `un meme lieu rencontre deux fois ne compte qu'une`() {
        val ordered = PlaceQuery.order(
            listOf(
                hit("Revel", PlaceKind.TOWN, 1.99, 43.46),
                hit("Revel", PlaceKind.VILLAGE, 1.991, 43.461),
                hit("Revel", PlaceKind.VILLAGE, 5.5, 45.0),
            ),
            "Revel",
        )
        assertEquals(2, ordered.size)
        assertEquals(PlaceKind.TOWN, ordered[0].kind)
    }

    @Test fun `les rues viennent apres les lieux a nom egal`() {
        val ordered = PlaceQuery.order(
            listOf(hit("Rue Revel", PlaceKind.STREET, lat = 43.1), hit("Revel", PlaceKind.LOCALITY, lat = 43.2)), "revel")
        assertEquals(PlaceKind.LOCALITY, ordered[0].kind)
    }

    @Test fun `la commune d'une rue la nomme, celle d'une commune ne se repete pas`() {
        assertEquals(listOf("Rue de la Republique", "Grenoble"),
            hit("Rue de la Republique", PlaceKind.STREET, locality = "Grenoble").toPlace().lines)
        assertEquals(listOf("Revel"), hit("Revel", PlaceKind.TOWN, locality = "Revel").toPlace().lines)
        assertEquals(listOf("Revel"), hit("Revel", PlaceKind.TOWN).toPlace().lines)
    }

    // ---------- Le geocodage inverse ----------

    @Test fun `une rue toute proche dit ou l'on est`() {
        val rue = hit("Rue du Four", PlaceKind.STREET, 5.7200, 45.1900, "Grenoble")
        val village = hit("Meylan", PlaceKind.VILLAGE, 5.7250, 45.1950)
        assertEquals(rue, PlaceQuery.pickReverse(listOf(village, rue), 5.7201, 45.1901))
    }

    /** Le point d'une rue est le milieu d'un troncon : a 600 m, il ne dit rien du lieu montre. */
    @Test fun `une rue lointaine est ecartee au profit du lieu habite`() {
        val rue = hit("Rue du Four", PlaceKind.STREET, 5.7200, 45.1900)
        val village = hit("Meylan", PlaceKind.VILLAGE, 5.7300, 45.1950, "Isere")
        assertEquals(village, PlaceQuery.pickReverse(listOf(rue, village), 5.7260, 45.1905))
    }

    @Test fun `a defaut de lieu habite, le plus proche de tout genre`() {
        val col = hit("Col du Lautaret", 13, 6.40, 45.03)
        val rue = hit("Rue X", PlaceKind.STREET, 6.41, 45.04)
        assertEquals(col, PlaceQuery.pickReverse(listOf(rue, col), 6.4005, 45.0302))
    }

    @Test fun `rien a moins de cinq kilometres, rien a dire`() {
        assertNull(PlaceQuery.pickReverse(listOf(hit("Loin", PlaceKind.VILLAGE, 7.0, 46.0)), 5.0, 45.0))
        assertNull(PlaceQuery.pickReverse(emptyList(), 5.0, 45.0))
    }

    @Test fun `les genres se rangent par importance`() {
        assertTrue(PlaceKind.tier(PlaceKind.CITY) < PlaceKind.tier(PlaceKind.TOWN))
        assertTrue(PlaceKind.tier(PlaceKind.TOWN) < PlaceKind.tier(PlaceKind.VILLAGE))
        assertTrue(PlaceKind.tier(PlaceKind.VILLAGE) < PlaceKind.tier(PlaceKind.HAMLET))
        assertTrue(PlaceKind.tier(PlaceKind.HAMLET) < PlaceKind.tier(PlaceKind.LOCALITY))
        assertTrue(PlaceKind.tier(PlaceKind.LOCALITY) < PlaceKind.tier(PlaceKind.STREET))
        assertEquals("un sommet vaut un village de peu moins", 4, PlaceKind.tier(10))
    }
}
