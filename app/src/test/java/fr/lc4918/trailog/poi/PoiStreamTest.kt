package fr.lc4918.trailog.poi

import fr.lc4918.trailog.domain.model.PoiCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'ordre d'affichage des deux sources : qui publie, quand, et ce que porte chaque emission.
 *
 * C'est ce qui decide de ce que voit l'utilisateur pendant les trente secondes ou OpenStreetMap travaille
 * sur une ville dense - et rien de tout cela ne se voit dans une liste finale. D'ou un test sur le flux
 * lui-meme, avec deux sources bidon dont on choisit l'ordre d'arrivee.
 */
class PoiStreamTest {

    private fun lieu(uuid: String, cat: PoiCategory, lat: Double = 45.0, lon: Double = 5.0) =
        Poi(uuid = uuid, label = uuid, lat = lat, lon = lon, category = cat)

    private val hotel = lieu("dt1", PoiCategory.HOTELS)
    private val fontaine = lieu("osm1", PoiCategory.WATER, lat = 45.5)

    @Test fun `chaque source publie des son arrivee`() = runTest {
        val emissions = poiStream(
            datatourisme = { PoiBatch(listOf(hotel), false) },
            osm = listOf({ delay(5_000); PoiBatch(listOf(fontaine), false) }),
            garder = {},
            cache = { emptyList() },
        ).toList()
        assertEquals("une emission par source", 2, emissions.size)
        assertEquals(listOf("dt1"), emissions[0].pois.map { it.uuid })
        assertEquals(listOf("dt1", "osm1"), emissions[1].pois.map { it.uuid })
    }

    /** Chaque emission porte TOUT ce qu'on sait : l'ecran remplace sa liste, il n'accumule rien. */
    @Test fun `la source lente n'efface pas la rapide`() = runTest {
        val emissions = poiStream(
            datatourisme = { delay(9_000); PoiBatch(listOf(hotel), false) },
            osm = listOf({ PoiBatch(listOf(fontaine), false) }),
            garder = {},
            cache = { emptyList() },
        ).toList()
        assertEquals(listOf("osm1"), emissions[0].pois.map { it.uuid })
        assertEquals(setOf("dt1", "osm1"), emissions[1].pois.map { it.uuid }.toSet())
    }

    /** Une source muette n'emet pas : une emission de plus ferait clignoter la carte pour rien. */
    @Test fun `une source muette ne publie rien`() = runTest {
        val emissions = poiStream(
            datatourisme = { PoiBatch(listOf(hotel), false) },
            osm = listOf({ PoiBatch(emptyList(), false) }),
            garder = {},
            cache = { emptyList() },
        ).toList()
        assertEquals(1, emissions.size)
        assertFalse(emissions.single().fromCache)
    }

    /** Les deux muettes : le cache prend le relais, et l'emission le dit. */
    @Test fun `sans reponse, le cache prend le relais`() = runTest {
        val emissions = poiStream(
            datatourisme = { PoiBatch(emptyList(), false) },
            osm = listOf({ PoiBatch(emptyList(), false) }),
            garder = {},
            cache = { listOf(hotel) },
        ).toList()
        assertEquals(1, emissions.size)
        assertTrue(emissions.single().fromCache)
        assertEquals(listOf("dt1"), emissions.single().pois.map { it.uuid })
    }

    /**
     * Rien nulle part : on emet quand meme, et vide. C'est cette emission qui apprend a l'ecran que
     * l'emprise est chargee - sans elle, il la redemanderait a chaque geste de carte.
     */
    @Test fun `rien nulle part se dit quand meme`() = runTest {
        val emissions = poiStream({ PoiBatch(emptyList(), false) }, listOf({ PoiBatch(emptyList(), false) }), {}, { emptyList() }).toList()
        assertEquals(1, emissions.size)
        assertTrue(emissions.single().pois.isEmpty())
        assertFalse("une liste vide ne vient pas du cache", emissions.single().fromCache)
    }

    /** Ce qu'une source rend est garde AU FIL DE L'EAU, et non a la fin : la source lente ne doit pas
     *  retarder la mise au cache de la rapide - c'est elle qu'on relira sans reseau. */
    @Test fun `chaque source est mise au cache des son arrivee`() = runTest {
        val gardes = mutableListOf<List<String>>()
        poiStream(
            datatourisme = { PoiBatch(listOf(hotel), false) },
            osm = listOf({ delay(5_000); PoiBatch(listOf(fontaine), false) }),
            garder = { lieux -> gardes += lieux.map { it.uuid } },
            cache = { emptyList() },
        ).toList()
        assertEquals(listOf(listOf("dt1"), listOf("osm1")), gardes)
    }

    /** Les doublons entre sources sont ecartes a l'emission, pas seulement a la fin. */
    @Test fun `un doublon entre sources ne parait qu'une fois`() = runTest {
        val meme = lieu("osm2", PoiCategory.HOTELS, lat = 45.0, lon = 5.0)   // au meme endroit que dt1
        val emissions = poiStream(
            datatourisme = { PoiBatch(listOf(hotel), false) },
            osm = listOf({ PoiBatch(listOf(meme), false) }),
            garder = {},
            cache = { emptyList() },
        ).toList()
        assertEquals(listOf("dt1"), emissions.last().pois.map { it.uuid })
    }

    // ---------- le decoupage par groupe ----------

    /** Chaque groupe d'OpenStreetMap publie pour son compte : quatre requetes, quatre affichages. */
    @Test fun `chaque groupe publie des son arrivee`() = runTest {
        val musee = lieu("osm2", PoiCategory.CULTURAL_SITES, lat = 45.9)
        val emissions = poiStream(
            datatourisme = { PoiBatch(emptyList(), false) },
            osm = listOf(
                { PoiBatch(listOf(fontaine), false) },
                { delay(7_000); PoiBatch(listOf(musee), false) },
            ),
            garder = {},
            cache = { emptyList() },
        ).toList()
        assertEquals(2, emissions.size)
        assertEquals(listOf("osm1"), emissions[0].pois.map { it.uuid })
        assertEquals(setOf("osm1", "osm2"), emissions[1].pois.map { it.uuid }.toSet())
    }

    /**
     * Le meme objet rendu par deux groupes ne pose qu'un marqueur.
     *
     * Un hotel-restaurant d'OpenStreetMap repond a la requete des hebergements ET a celle de la
     * restauration, sous deux categories, avec le meme identifiant. C'est l'ordre de resolution qui tranche,
     * et non l'ordre d'arrivee : l'hotel-restaurant reste un hotel, que la restauration reponde avant ou
     * apres.
     */
    @Test fun `un objet rendu par deux groupes garde la categorie qui prime`() = runTest {
        val commeHotel = lieu("osm:way/9", PoiCategory.HOTELS)
        val commeResto = lieu("osm:way/9", PoiCategory.RESTAURANTS)
        val hebergementDAbord = poiStream(
            { PoiBatch(emptyList(), false) },
            listOf({ PoiBatch(listOf(commeHotel), false) }, { delay(3_000); PoiBatch(listOf(commeResto), false) }),
            {}, { emptyList() },
        ).toList().last()
        assertEquals(listOf(PoiCategory.HOTELS), hebergementDAbord.pois.map { it.category })

        val restaurationDAbord = poiStream(
            { PoiBatch(emptyList(), false) },
            listOf({ delay(3_000); PoiBatch(listOf(commeHotel), false) }, { PoiBatch(listOf(commeResto), false) }),
            {}, { emptyList() },
        ).toList().last()
        assertEquals("l'ordre d'arrivee ne doit rien changer",
            listOf(PoiCategory.HOTELS), restaurationDAbord.pois.map { it.category })
    }

    // ---------- l'affichage partiel ----------

    /**
     * Une source qui bute sur son plafond le dit, et la carte l'annonce.
     *
     * C'est le correctif d'une faute relevee sur le terrain : le service connaissait 149 lieux, on n'en
     * demandait que 100, et les 49 ecartes changeaient d'une requete a l'autre - un loueur de canoes
     * apparaissait puis disparaissait, sans que rien ne l'explique.
     */
    @Test fun `une source tronquee rend l'affichage partiel`() = runTest {
        val charge = poiStream(
            datatourisme = { PoiBatch(listOf(hotel), tronque = true) },
            osm = listOf({ PoiBatch(listOf(fontaine), tronque = false) }),
            garder = {},
            cache = { emptyList() },
        ).toList().last()
        assertTrue(charge.partial)
    }

    /** Le drapeau ne se defait pas : la reponse d'a cote ne rendra pas les lieux que la premiere a laisses. */
    @Test fun `l'affichage partiel ne se defait pas`() = runTest {
        val emissions = poiStream(
            datatourisme = { PoiBatch(listOf(hotel), tronque = true) },
            osm = listOf({ delay(3_000); PoiBatch(listOf(fontaine), tronque = false) }),
            garder = {},
            cache = { emptyList() },
        ).toList()
        assertTrue("des la premiere", emissions.first().partial)
        assertTrue("et toujours a la derniere", emissions.last().partial)
    }

    /** Rien de tronque, rien a annoncer : le message ne doit pas s'afficher pour une zone bien rendue. */
    @Test fun `sans troncature, l'affichage est complet`() = runTest {
        val charge = poiStream(
            { PoiBatch(listOf(hotel), false) }, listOf({ PoiBatch(listOf(fontaine), false) }), {}, { emptyList() },
        ).toList().last()
        assertFalse(charge.partial)
    }
}
