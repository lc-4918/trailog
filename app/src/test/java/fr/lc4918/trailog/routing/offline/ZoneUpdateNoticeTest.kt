package fr.lc4918.trailog.routing.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quand annoncer une zone a mettre a jour : une fois par jour au plus, une fois par version du serveur. */
class ZoneUpdateNoticeTest {
    private val day = ZoneUpdateNotice.CHECK_INTERVAL_MS
    private val pyrenees = BrouterZones.byId("pyrenees")!!
    private val france = BrouterZones.byId("fr")!!
    private val spain = BrouterZones.byId("es")!!

    private val west = BrouterTile(-5, 40)
    private val east = BrouterTile(0, 40)

    /** Les Pyrenees telechargees, leurs deux carres a jour pour l'itineraire ; l'index de l'ouest regenere. */
    private fun state(westGeocodeMs: Long = 9_000_000L, zones: Set<String> = setOf("pyrenees")) = BrouterDownloads.State(
        installed = listOf(
            BrouterSegments.Installed(west, 90, 1_000_000L, geocodeModifiedMs = 1_000_000L),
            BrouterSegments.Installed(east, 120, 1_000_000L, geocodeModifiedMs = 1_000_000L),
        ),
        remote = mapOf(
            west to BrouterSegments.Remote(west, 92, 1_000_000L, BrouterSegments.GeocodeRemote(10, 50, westGeocodeMs)),
            east to BrouterSegments.Remote(east, 122, 1_000_000L, BrouterSegments.GeocodeRemote(10, 50, 1_000_000L)),
        ),
        zones = zones,
    )

    @Test fun `le serveur se consulte au plus une fois par jour`() {
        assertTrue("jamais consulte", ZoneUpdateNotice.checkDue(0L, 10 * day))
        assertFalse(ZoneUpdateNotice.checkDue(10 * day, 10 * day + day - 1))
        assertTrue(ZoneUpdateNotice.checkDue(10 * day, 11 * day))
        assertTrue("horloge revenue en arriere", ZoneUpdateNotice.checkDue(10 * day, 9 * day))
    }

    @Test fun `seules les zones telechargees et perimees sont annoncees`() {
        assertEquals(listOf(pyrenees), ZoneUpdateNotice.outdatedZones(state(), listOf(france, spain, pyrenees)))
        assertTrue("a jour", ZoneUpdateNotice.outdatedZones(state(westGeocodeMs = 1_000_000L)).isEmpty())
        assertTrue("pas telechargee", ZoneUpdateNotice.outdatedZones(state(zones = emptySet())).isEmpty())
    }

    @Test fun `la version annoncee est la date la plus recente des carres perimes`() {
        assertEquals(9_000_000L, ZoneUpdateNotice.serverVersion(state(), listOf(pyrenees)))
        assertEquals(0L, ZoneUpdateNotice.serverVersion(state(westGeocodeMs = 1_000_000L), listOf(pyrenees)))
    }

    @Test fun `une version deja annoncee ne l'est pas deux fois, une plus recente si`() {
        assertTrue(ZoneUpdateNotice.shouldNotify(9_000_000L, 0L))
        assertFalse(ZoneUpdateNotice.shouldNotify(9_000_000L, 9_000_000L))
        assertTrue(ZoneUpdateNotice.shouldNotify(12_000_000L, 9_000_000L))
    }

    @Test fun `toucher la notification ouvre les zones une seule fois`() {
        ZoneUpdateNotice.consumeOpenZones()
        ZoneUpdateNotice.requestOpenZones()
        assertTrue(ZoneUpdateNotice.openZonesPending.value)
        assertTrue(ZoneUpdateNotice.consumeOpenZones())
        assertFalse(ZoneUpdateNotice.consumeOpenZones())
        assertFalse(ZoneUpdateNotice.openZonesPending.value)
    }
}
