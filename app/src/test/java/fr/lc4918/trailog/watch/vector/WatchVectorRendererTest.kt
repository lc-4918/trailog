package fr.lc4918.trailog.watch.vector

import fr.lc4918.trailog.watch.WatchTilePixels
import fr.lc4918.trailog.watch.WatchTiles
import fr.lc4918.trailog.watch.WatchTiles.WatchTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/**
 * Le dessin du fond vectoriel, sur de vraies tuiles PLAN.IGN (Esquirol a Toulouse, calanque de Morgiou) au zoom 17, niveau de detail
 * 17,0 : une tuile source de 4096 unites fait 256 px, une tuile montre en recouvre un quart (128 px), donc 1 px = 16 unites.
 * En rendu natif : celui par defaut de Robolectric ne dessine rien.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatchVectorRendererTest {

    private val tileSize = WatchTiles.WATCH_TILE_PIXELS
    private val city = tile("17_66062_47861")
    private val mountain = tile("17_67516_48055")

    private fun tile(name: String) = VectorTile.decode(javaClass.getResourceAsStream("/watch/planign/$name.pbf")!!.readBytes())

    private fun index(rgb: Int) = WatchTilePixels.PALETTE.indexOf(rgb)

    /** Le quart (column, row) de la tuile source (x, y), rendu seul : toutes les autres tuiles sont absentes. */
    private fun render(source: VectorTile, x: Int, y: Int, column: Int, row: Int, zoom: Double = 17.0, withText: Boolean = false): IntArray {
        val tile = WatchTile(2 * x + column, 2 * y + row)
        return WatchVectorRenderer.render(tile, zoom, 17, withText) { sx, sy -> if (sx == x && sy == y) source else null }!!
    }

    private fun IntArray.at(x: Double, y: Double) = this[y.toInt().coerceIn(0, tileSize - 1) * tileSize + x.toInt().coerceIn(0, tileSize - 1)]

    /** Points tous les pixels le long d'une ligne, en pixels de la tuile montre du quart (column, row). */
    private fun walk(line: List<VectorTile.Point>, column: Int, row: Int): List<Pair<Double, Double>> {
        val points = line.map { (it.x / 16.0 - column * tileSize) to (it.y / 16.0 - row * tileSize) }
        val samples = ArrayList<Pair<Double, Double>>()
        for (i in 0 until points.size - 1) {
            val (ax, ay) = points[i]
            val (bx, by) = points[i + 1]
            val steps = hypot(bx - ax, by - ay).toInt().coerceAtLeast(1)
            for (step in 0 until steps) samples.add((ax + (bx - ax) * step / steps) to (ay + (by - ay) * step / steps))
        }
        return samples.filter { it.first in 2.0..(tileSize - 3.0) && it.second in 2.0..(tileSize - 3.0) }
    }

    private fun features(source: VectorTile, layer: String, symbo: String) =
        source.layers.getValue(layer).features.filter { it.text("symbo") == symbo }

    @Test fun `a tile without any readable source tile has no drawing`() {
        assertNull(WatchVectorRenderer.render(WatchTile(0, 0), 17.0, 17, true) { _, _ -> null })
    }

    @Test fun `only colours of the 64 colour grid are used`() {
        val pixels = render(city, 66062, 47861, 0, 0)
        assertEquals(tileSize * tileSize, pixels.size)
        assertTrue(pixels.all { it in 0 until WatchTilePixels.PALETTE.size })
        // Une carte de ville : du blanc (rues), du gris (batiments), du gris fonce (contours) au moins.
        val used = pixels.toSet()
        assertTrue(index(0xFFFFFF) in used)
        assertTrue(index(0xAAAAAA) in used)
        assertTrue(index(0x555555) in used)
    }

    @Test fun `building interiors are grey and streets are white`() {
        val pixels = render(city, 66062, 47861, 0, 0)
        val layer = city.layers.getValue("bati_surf")
        val greys = setOf(index(0xAAAAAA), index(0xFFAAAA), index(0xFFAAFF))
        var inspected = 0
        var matching = 0
        for (building in layer.features) {
            val ring = building.parts.firstOrNull() ?: continue
            val xs = ring.map { it.x / 16.0 }
            val ys = ring.map { it.y / 16.0 }
            if (xs.min() < 8 || xs.max() > tileSize - 8 || ys.min() < 8 || ys.max() > tileSize - 8) continue
            val centreX = (xs.min() + xs.max()) / 2
            val centreY = (ys.min() + ys.max()) / 2
            if (!contains(ring, centreX * 16, centreY * 16) || (xs.max() - xs.min()) < 6 || (ys.max() - ys.min()) < 6) continue
            inspected++
            if (pixels.at(centreX, centreY) in greys) matching++
        }
        assertTrue("batiments examines : $inspected", inspected >= 5)
        assertTrue("$matching sur $inspected au centre gris", matching >= inspected * 0.8)
        // L'axe d'une rue sans nom de classe est blanc d'un bout a l'autre : pas de contour en travers a la jonction de deux troncons.
        val street = features(city, "routier_route", "NON_CLASSEE").maxBy { f -> walk(f.parts.first(), 0, 0).size }
        val samples = walk(street.parts.first(), 0, 0)
        assertTrue(samples.size > 30)
        val white = samples.count { pixels.at(it.first, it.second) == index(0xFFFFFF) }
        assertTrue("$white sur ${samples.size} blancs sur l'axe de la rue", white >= samples.size * 0.9)
    }

    @Test fun `a regional road is yellow along its axis`() {
        // Les routes regionales de cette tuile longent son bord sud : quart du bas a gauche.
        val pixels = render(city, 66062, 47861, 0, 1)
        val road = city.layers.getValue("routier_route").features.filter { it.text("symbo")?.startsWith("REGIONALE") == true }
            .maxBy { walk(it.parts.first(), 0, 1).size }
        val samples = walk(road.parts.first(), 0, 1)
        assertTrue(samples.size > 30)
        val yellow = samples.count { pixels.at(it.first, it.second) == index(0xFFFF00) }
        assertTrue("$yellow sur ${samples.size} jaunes sur l'axe", yellow >= samples.size * 0.8)
    }

    @Test fun `a bridge over the river is drawn as a road above the water`() {
        // Le Pont Neuf de Toulouse : sa route (couche routier_route_sup) traverse la Garonne (hydro_surf).
        val bridge = tile("17_66059_47862")
        val road = bridge.layers.getValue("routier_route_sup").features.first { it.text("symbo") == "REGIONALE_3_SUP" }
        var samplesCount = 0
        var yellowCount = 0
        var waterCount = 0
        for (column in 0..1) for (row in 0..1) {
            val pixels = render(bridge, 66059, 47862, column, row)
            val samples = walk(road.parts.first(), column, row)
            samplesCount += samples.size
            yellowCount += samples.count { pixels.at(it.first, it.second) == index(0xFFFF00) }
            waterCount += pixels.count { it == index(0x55AAFF) }
        }
        assertTrue("assez de pont pour juger : $samplesCount", samplesCount > 50)
        assertTrue("$yellowCount sur $samplesCount jaunes sur l'axe du pont", yellowCount >= samplesCount * 0.8)
        assertTrue("la riviere est dessinee : $waterCount pixels d'eau", waterCount > 2000)
    }

    @Test fun `a footpath is dashed and a contour line is brown`() {
        // La calanque de Morgiou : on cherche, dans les quatre quarts de la tuile, un sentier assez long et une courbe de niveau.
        var dashed = false
        var brown = false
        for (column in 0..1) for (row in 0..1) {
            val pixels = render(mountain, 67516, 48055, column, row)
            for (path in features(mountain, "routier_chemin", "SENTIER")) {
                val samples = walk(path.parts.first(), column, row)
                if (samples.size < 40) continue
                val dark = samples.map { pixels.at(it.first, it.second) == index(0x555555) }
                val transitions = dark.zipWithNext().count { it.first != it.second }
                if (transitions >= 4) dashed = true
            }
            for (contour in features(mountain, "oro_courbe", "CNV_NORMALE")) {
                val samples = walk(contour.parts.first(), column, row)
                if (samples.size < 40) continue
                if (samples.count { pixels.at(it.first, it.second) == index(0xAA5500) } >= samples.size * 0.3) brown = true
            }
        }
        assertTrue("sentier en tirets", dashed)
        assertTrue("courbe de niveau brune", brown)
    }

    /** Les noms de rues sont ecrits en noir : sans texte une tuile de ville n'a aucun pixel noir, avec texte elle en a. */
    @Test fun `street names are written in black with a white halo`() {
        val pixels = render(city, 66062, 47861, 0, 0, withText = true)
        val withoutText = render(city, 66062, 47861, 0, 0)
        assertEquals(0, withoutText.count { it == index(0x000000) })
        val black = pixels.count { it == index(0x000000) }
        assertTrue("pixels noirs : $black", black > 40)
    }

    @Test fun `roads are drawn thinner at a lower level of detail`() {
        val fine = render(city, 66062, 47861, 0, 0, 17.0)
        // Au niveau 16,0 la tuile montre couvre la meme zone a deux fois moins de pixels par metre : la route principale y est plus mince.
        assertTrue(WatchVectorStyle.roadWidth("REGIONALE_3", 16.0) < WatchVectorStyle.roadWidth("REGIONALE_3", 17.0))
        assertEquals(WatchVectorStyle.roadWidth("REGIONALE_3", 17.0), 7f, 0.001f)
        assertNotNull(fine)
    }

    @Test fun `road width has a floor and a main road is wider than a street`() {
        assertEquals(2.5f, WatchVectorStyle.roadWidth("NON_CLASSEE", 14.0), 0.001f)
        assertTrue(WatchVectorStyle.roadWidth("REGIONALE_2", 16.0) > WatchVectorStyle.roadWidth("NON_CLASSEE", 16.0))
        assertEquals(WatchVectorStyle.ROAD_MAIN_FILL, WatchVectorStyle.roadFill("REGIONALE_1"))
        assertEquals(WatchVectorStyle.ROAD_LOCAL_FILL, WatchVectorStyle.roadFill("LOCALE_2"))
        assertEquals(WatchVectorStyle.ROAD_STREET_FILL, WatchVectorStyle.roadFill("NON_CLASSEE"))
    }

    /** Appartenance d'un point a un anneau (tirer un rayon vers la droite), en coordonnees de tuile. */
    private fun contains(ring: List<VectorTile.Point>, x: Double, y: Double): Boolean {
        var inside = false
        for (i in 0 until ring.size - 1) {
            val a = ring[i]
            val b = ring[i + 1]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y).toDouble() / (b.y - a.y) + a.x) inside = !inside
        }
        return inside
    }
}
