package com.tunturo.app.watch.vector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le decodeur lit de vraies tuiles PLAN.IGN (Geoplateforme) : Esquirol a Toulouse et la calanque de Morgiou, au zoom 17. */
class VectorTileTest {

    private fun tile(name: String): VectorTile =
        VectorTile.decode(javaClass.getResourceAsStream("/watch/planign/$name.pbf")!!.readBytes())

    @Test fun `every layer of a real city tile is read with its feature count`() {
        val counts = tile("17_66062_47861").layers.mapValues { it.value.features.size }
        assertEquals(
            mapOf(
                "bati_ponc" to 6, "bati_surf" to 164, "bati_zai" to 2, "fond_opaque" to 1, "ocs_vegetation_surf" to 3,
                "routier_chemin" to 2, "routier_ponc" to 1, "routier_route" to 29, "routier_surf" to 1,
                "toponyme_bati_ponc" to 3, "toponyme_localite_ponc" to 1, "toponyme_routier_borne" to 31,
                "toponyme_routier_odonyme_lin" to 17,
            ),
            counts,
        )
    }

    @Test fun `a mountain tile carries contour lines, paths and limits`() {
        val layers = tile("17_67516_48055").layers
        assertEquals(5, layers.getValue("oro_courbe").features.size)
        assertEquals(8, layers.getValue("routier_chemin").features.size)
        assertEquals(4, layers.getValue("limite_lin").features.size)
        assertEquals(setOf("CNV_NORMALE", "CNV_MAITRESSE"), layers.getValue("oro_courbe").features.map { it.text("symbo") }.toSet())
    }

    @Test fun `a building is a closed polygon in tile coordinates`() {
        val layer = tile("17_66062_47861").layers.getValue("bati_surf")
        assertEquals(4096, layer.extent)
        val building = layer.features.first()
        assertEquals(VectorTile.GeometryType.POLYGON, building.type)
        assertEquals("BATI_QQUE", building.text("symbo"))
        val ring = building.parts.single()
        assertEquals(VectorTile.Point(-71, -80), ring.first())
        assertEquals(ring.first(), ring.last())
        assertEquals(listOf(VectorTile.Point(-71, -80), VectorTile.Point(-80, -69), VectorTile.Point(-80, -80)), ring.take(3).let { listOf(it[0], it[1], it[2]) })
    }

    @Test fun `a road is a line with its class and a locality is a point with its name`() {
        val tile = tile("17_66062_47861")
        val road = tile.layers.getValue("routier_route").features.first()
        assertEquals(VectorTile.GeometryType.LINE, road.type)
        assertEquals("NON_CLASSEE", road.text("symbo"))
        assertEquals(false, road.properties["rond_point"])
        assertEquals(listOf(VectorTile.Point(-80, 35), VectorTile.Point(15, -80)), road.parts.single())
        val locality = tile.layers.getValue("toponyme_localite_ponc").features.single()
        assertEquals(VectorTile.GeometryType.POINT, locality.type)
        assertEquals("Alsace", locality.text("texte"))
        assertEquals(VectorTile.Point(2616, 371), locality.parts.single().single())
    }

    @Test fun `street names are available to label the roads`() {
        val names = tile("17_66062_47861").layers.getValue("toponyme_routier_odonyme_lin").features.mapNotNull { it.text("nom_gauche") }
        assertTrue(names.any { it.contains("ALSACE LORRAINE") })
        assertNotNull(names.firstOrNull())
    }

    @Test fun `an empty tile has no layer`() {
        assertTrue(VectorTile.decode(ByteArray(0)).layers.isEmpty())
    }

    @Test fun `protobuf zigzag decoding handles negative and positive values`() {
        assertEquals(0L, ProtoReader.zigZag(0))
        assertEquals(-1L, ProtoReader.zigZag(1))
        assertEquals(1L, ProtoReader.zigZag(2))
        assertEquals(-80L, ProtoReader.zigZag(159))
    }
}
