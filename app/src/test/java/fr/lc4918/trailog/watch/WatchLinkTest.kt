package fr.lc4918.trailog.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchLinkTest {

    private fun link(
        devices: List<PairedDevice>, garminConnect: Boolean = true, bluetooth: Boolean = true, permission: Boolean = true,
    ) = watchLinkOf(garminConnect, bluetooth, permission, devices)

    private val watch = PairedDevice("fenix 6X Pro", connected = true)
    private val headphones = PairedDevice("WH-1000XM4", connected = true)

    @Test fun `garmin watch names are recognised`() {
        listOf("fenix 6X Pro", "Forerunner 245", "vívoactive 4", "Venu 2", "Instinct Solar", "Edge 530", "Garmin Index")
            .forEach { assertTrue(it, isGarminWatchName(it)) }
        listOf("WH-1000XM4", "Pixel Buds", "", null).forEach { assertFalse(it.toString(), isGarminWatchName(it)) }
    }

    @Test fun `without garmin connect nothing can be relayed`() {
        assertEquals(WatchLink.GarminConnectMissing, link(listOf(watch), garminConnect = false))
    }

    @Test fun `a missing permission is reported before the bluetooth state`() {
        assertEquals(WatchLink.PermissionNeeded, link(emptyList(), permission = false, bluetooth = false))
    }

    @Test fun `bluetooth off is reported`() {
        assertEquals(WatchLink.BluetoothOff, link(listOf(watch), bluetooth = false))
    }

    /** Un casque appaire n'est pas une montre. */
    @Test fun `other paired devices are not watches`() {
        assertEquals(WatchLink.NoWatch, link(listOf(headphones)))
        assertEquals(WatchLink.NoWatch, link(emptyList()))
    }

    @Test fun `a paired watch out of reach is named as disconnected`() {
        assertEquals(WatchLink.Disconnected("fenix 6X Pro"), link(listOf(watch.copy(connected = false))))
    }

    /** On peut avoir appaire plusieurs montres : c'est celle qu'on porte qui recevra les tuiles. */
    @Test fun `the connected watch wins over the others`() {
        val devices = listOf(PairedDevice("Forerunner 245", connected = false), watch)
        assertEquals(WatchLink.Connected("fenix 6X Pro"), link(devices))
    }
}
