package fr.lc4918.trailog.watch.vector

import fr.lc4918.trailog.watch.WatchTilePixels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchOsmStyleTest {

    private fun rgb(color: Int) = color and 0xFFFFFF

    @Test fun `every colour of the style is a colour of the watch grid`() {
        val colors = listOf(WatchOsmStyle.WOOD, WatchOsmStyle.GRASS, WatchOsmStyle.PARK, WatchOsmStyle.FARMLAND, WatchOsmStyle.SAND, WatchOsmStyle.WETLAND,
            WatchOsmStyle.PITCH, WatchOsmStyle.SCHOOL, WatchOsmStyle.HOSPITAL, WatchOsmStyle.INDUSTRIAL, WatchOsmStyle.WATER)
        for (color in colors) assertTrue("%06X".format(rgb(color)), rgb(color) in WatchTilePixels.PALETTE)
    }

    @Test fun `woods stay green and water stays blue when the grid has no exact shade`() {
        assertEquals(0xAAFFAA, rgb(WatchOsmStyle.WOOD))
        assertEquals(rgb(WatchVectorStyle.WATER), rgb(WatchOsmStyle.WATER))
        assertNotEquals(rgb(WatchOsmStyle.WOOD), rgb(WatchOsmStyle.FARMLAND))
    }

    @Test fun `roads are mapped to the road widths of the other vector basemap`() {
        assertEquals("AUTOROUTE", WatchOsmStyle.roadSymbol("motorway"))
        assertEquals("REGIONALE_1", WatchOsmStyle.roadSymbol("primary"))
        assertEquals("NON_CLASSEE", WatchOsmStyle.roadSymbol("minor"))
        assertEquals("NON_CLASSEE_RESTREINT", WatchOsmStyle.roadSymbol("service"))
        assertNull(WatchOsmStyle.roadSymbol("path"))
        assertNull(WatchOsmStyle.roadSymbol("rail"))
        assertNull(WatchOsmStyle.roadSymbol("primary_construction"))
        assertTrue(WatchVectorStyle.roadWidth(WatchOsmStyle.roadSymbol("motorway"), 17.0) > WatchVectorStyle.roadWidth(WatchOsmStyle.roadSymbol("service"), 17.0))
    }

    @Test fun `a footpath is dashed and a track is plain`() {
        assertTrue(WatchOsmStyle.pathStroke("path", "path")!!.dashes.isNotEmpty())
        assertTrue(WatchOsmStyle.pathStroke("path", "footway")!!.dashes.isNotEmpty())
        assertEquals(WatchVectorStyle.PATHS["PISTE_CYCLABLE"], WatchOsmStyle.pathStroke("path", "cycleway"))
        assertEquals(WatchVectorStyle.PATHS["ESCALIER"], WatchOsmStyle.pathStroke("path", "steps"))
        assertTrue(WatchOsmStyle.pathStroke("track", null)!!.dashes.isEmpty())
        assertNull(WatchOsmStyle.pathStroke("path", "crossing"))
        assertNull(WatchOsmStyle.pathStroke("minor", null))
    }

    @Test fun `land cover and land use are drawn only for the classes of the style`() {
        assertNotNull(WatchOsmStyle.landcover("wood", "forest"))
        assertEquals(WatchOsmStyle.PARK, WatchOsmStyle.landcover("grass", "park")!!.color)
        assertEquals(WatchOsmStyle.GRASS, WatchOsmStyle.landcover("grass", "grass")!!.color)
        assertEquals(WatchVectorStyle.FillKind.DOTS, WatchOsmStyle.landcover("rock", "scree")!!.kind)
        assertNull(WatchOsmStyle.landcover("unknown", null))
        assertNotNull(WatchOsmStyle.landuse("school"))
        assertNull(WatchOsmStyle.landuse("residential"))
    }

    @Test fun `only rivers canals and streams are drawn as waterways`() {
        assertTrue(WatchOsmStyle.waterwayWidth("river")!! > WatchOsmStyle.waterwayWidth("stream")!!)
        assertNull(WatchOsmStyle.waterwayWidth("ditch"))
        assertNull(WatchOsmStyle.waterwayWidth("drain"))
    }
}
