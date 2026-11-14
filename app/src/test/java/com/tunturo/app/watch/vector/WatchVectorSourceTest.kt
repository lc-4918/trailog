package com.tunturo.app.watch.vector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchVectorSourceTest {

    private val realTile = javaClass.getResourceAsStream("/watch/planign/17_66062_47861.pbf")!!.readBytes()

    @Test fun `the tile is asked at the plan ign address and decoded`() {
        val asked = mutableListOf<String>()
        val source = WatchVectorSource(fetch = { url -> asked.add(url); realTile })
        val tile = source.tile(17, 66062, 47861)
        assertEquals(listOf("https://data.geopf.fr/tms/1.0.0/PLAN.IGN/17/66062/47861.pbf"), asked)
        assertEquals(164, tile!!.layers.getValue("bati_surf").features.size)
    }

    @Test fun `a service that does not answer gives no tile`() {
        assertNull(WatchVectorSource(fetch = { null }).tile(17, 1, 2))
    }

    @Test fun `an unreadable answer gives no tile`() {
        assertNull(WatchVectorSource(fetch = { byteArrayOf(0x1A, 0x7F, 0x05, 0x01) }).tile(17, 1, 2))
    }

    @Test fun `the address can be replaced`() {
        val asked = mutableListOf<String>()
        WatchVectorSource(urlTemplate = "http://local/{z}-{x}-{y}", fetch = { asked.add(it); null }).tile(16, 3, 4)
        assertEquals(listOf("http://local/16-3-4"), asked)
    }

    @Test fun `the vector basemap is listed with a translated name and recognised by its identifier`() {
        val provider = WatchVectorSource.provider("Scan 25 vectoriel")
        assertEquals("Scan 25 vectoriel", provider.name)
        assertTrue(WatchVectorSource.isVector(provider))
        assertFalse(WatchVectorSource.isVector(provider.copy(id = "ign_fr")))
        assertNotNull(provider.urlTemplate)
    }
}
