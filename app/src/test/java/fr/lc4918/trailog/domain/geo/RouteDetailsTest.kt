package fr.lc4918.trailog.domain.geo

import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.SurfaceKind
import fr.lc4918.trailog.domain.model.WayKind
import fr.lc4918.trailog.domain.model.WaySegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les "Details" d'un itineraire : la part de chaque revetement et de chaque type de voie, et ou ils passent.
 *
 * Les fautes que ces cas attrapent ne levent rien : un revetement range dans la mauvaise famille, des
 * parts qui ne font pas 100 %, ou une mise en evidence decalee le long du trace.
 */
class RouteDetailsTest {

    private fun seg(m: Double, tags: String) = WaySegment(m, tags)

    @Test fun `les attributs se lisent en table`() {
        assertEquals(
            mapOf("reversedirection" to "yes", "highway" to "service", "surface" to "asphalt"),
            RouteDetails.tagsOf("reversedirection=yes highway=service surface=asphalt"),
        )
        assertTrue(RouteDetails.tagsOf("").isEmpty())
    }

    @Test fun `chaque revetement tombe dans sa famille`() {
        fun s(v: String) = RouteDetails.declaredSurfaceOf(mapOf("surface" to v))
        assertEquals(SurfaceKind.ASPHALT, s("asphalt"))
        assertEquals(SurfaceKind.PAVED, s("paved"))
        assertEquals(SurfaceKind.CONCRETE, s("concrete:plates"))
        assertEquals(SurfaceKind.SETT, s("paving_stones"))
        assertEquals(SurfaceKind.SETT, s("cobblestone"))
        assertEquals(SurfaceKind.COMPACTED, s("compacted"))
        assertEquals(SurfaceKind.FINE_GRAVEL, s("fine_gravel"))
        assertEquals(SurfaceKind.GRAVEL, s("gravel"))
        assertEquals(SurfaceKind.UNPAVED, s("unpaved"))
        assertEquals(SurfaceKind.GROUND, s("dirt"))
        assertEquals(SurfaceKind.GRASS, s("grass"))
        assertEquals(SurfaceKind.SAND, s("sand"))
        assertEquals(SurfaceKind.ROCK, s("rock"))
        assertNull(s("valeur_inventee"))
    }

    /**
     * Sans `surface`, le revetement s'estime : d'apres le `tracktype` d'abord, fait pour cela, puis d'apres
     * le type de voie. Un tiers des troncons reels n'en a pas, des routes surtout : les laisser inconnus
     * noyait la rubrique.
     */
    @Test fun `une voie sans revetement renseigne en recoit un estime`() {
        fun s(vararg kv: Pair<String, String>) = RouteDetails.surfaceOf(kv.toMap())
        assertEquals(SurfaceKind.PAVED, s("highway" to "tertiary"))
        assertEquals(SurfaceKind.PAVED, s("highway" to "residential"))
        assertEquals(SurfaceKind.PAVED, s("highway" to "cycleway"))
        assertEquals(SurfaceKind.UNPAVED, s("highway" to "track"))
        assertEquals(SurfaceKind.UNPAVED, s("highway" to "path", "bicycle" to "designated"))
        assertEquals(SurfaceKind.PAVED, s("highway" to "track", "tracktype" to "grade1"))
        assertEquals(SurfaceKind.COMPACTED, s("highway" to "path", "tracktype" to "grade2"))
        assertEquals(SurfaceKind.UNPAVED, s("highway" to "track", "tracktype" to "grade3"))
        assertEquals(SurfaceKind.GROUND, s("highway" to "track", "tracktype" to "grade5"))
        assertEquals("rien pour estimer", SurfaceKind.UNKNOWN, s("route" to "ferry"))
    }

    /** Ce qu'une voie DECLARE l'emporte toujours sur l'estimation. */
    @Test fun `le revetement declare prime sur l'estimation`() {
        assertEquals(SurfaceKind.GRAVEL,
            RouteDetails.surfaceOf(mapOf("highway" to "tertiary", "surface" to "gravel")))
        assertEquals(SurfaceKind.ASPHALT,
            RouteDetails.surfaceOf(mapOf("highway" to "track", "tracktype" to "grade4", "surface" to "asphalt")))
        // Une valeur hors vocabulaire ne vaut pas declaration : on estime.
        assertEquals(SurfaceKind.PAVED,
            RouteDetails.surfaceOf(mapOf("highway" to "primary", "surface" to "valeur_inventee")))
    }

    /** La part estimee se dit a part : elle ne compte ni ce qui est declare, ni ce qui reste inconnu. */
    @Test fun `la part estimee ne compte que les estimations`() {
        val f = RouteDetails.estimatedFraction(listOf(
            seg(500.0, "highway=primary surface=asphalt"),
            seg(300.0, "highway=tertiary"),
            seg(200.0, "route=ferry"),
        ))
        assertEquals(0.3, f, 1e-9)
        assertEquals(0.0, RouteDetails.estimatedFraction(emptyList()), 0.0)
    }

    @Test fun `revetu, non revetu, inconnu`() {
        assertNull(SurfaceKind.UNKNOWN.paved)
        assertEquals(true, SurfaceKind.ASPHALT.paved)
        assertEquals(false, SurfaceKind.GRAVEL.paved)
    }

    @Test fun `chaque voie tombe dans son type`() {
        fun w(vararg kv: Pair<String, String>) = RouteDetails.wayOf(kv.toMap())
        assertEquals(WayKind.MAIN_ROAD, w("highway" to "primary"))
        assertEquals(WayKind.MAIN_ROAD, w("highway" to "trunk_link"))
        assertEquals(WayKind.ROAD, w("highway" to "tertiary"))
        assertEquals(WayKind.MINOR_ROAD, w("highway" to "unclassified"))
        assertEquals(WayKind.STREET, w("highway" to "residential"))
        assertEquals(WayKind.SERVICE, w("highway" to "service"))
        assertEquals(WayKind.CYCLEWAY, w("highway" to "cycleway"))
        assertEquals(WayKind.TRACK, w("highway" to "track"))
        assertEquals(WayKind.PATH, w("highway" to "path"))
        assertEquals(WayKind.FOOTWAY, w("highway" to "footway"))
        assertEquals(WayKind.STEPS, w("highway" to "steps"))
        assertEquals(WayKind.FERRY, w("route" to "ferry"))
        assertEquals(WayKind.OTHER, w())
    }

    /** Une voie verte : un sentier reserve aux velos se lit en piste cyclable. */
    @Test fun `un sentier reserve aux velos est une piste cyclable`() {
        assertEquals(WayKind.CYCLEWAY, RouteDetails.wayOf(mapOf("highway" to "path", "bicycle" to "designated")))
        assertEquals(WayKind.CYCLEWAY, RouteDetails.wayOf(mapOf("highway" to "footway", "bicycle" to "designated")))
    }

    private val trajet = listOf(
        seg(500.0, "highway=primary surface=asphalt"),
        seg(200.0, "highway=track surface=gravel"),
        seg(300.0, "highway=residential surface=asphalt"),
    )

    @Test fun `les parts font 100 pour cent, de la plus longue a la plus courte`() {
        val surfaces = RouteDetails.surfaces(trajet)
        assertEquals(listOf(SurfaceKind.ASPHALT, SurfaceKind.GRAVEL), surfaces.map { it.kind })
        assertEquals(800.0, surfaces[0].meters, 1e-9)
        assertEquals(0.8, surfaces[0].fraction, 1e-9)
        assertEquals(1.0, surfaces.sumOf { it.fraction }, 1e-9)
        val voies = RouteDetails.ways(trajet)
        assertEquals(listOf(WayKind.MAIN_ROAD, WayKind.STREET, WayKind.TRACK), voies.map { it.kind })
    }

    @Test fun `un trajet sans troncons n'a pas de parts`() {
        assertTrue(RouteDetails.surfaces(emptyList()).isEmpty())
    }

    /** Revetu, non revetu, inconnu : toujours dans cet ordre, quelle que soit la part de chacun. */
    @Test fun `la synthese revetu non revetu garde son ordre`() {
        val p = RouteDetails.paved(listOf(
            seg(100.0, "route=ferry"),
            seg(600.0, "highway=track surface=gravel"),
            seg(300.0, "highway=primary surface=asphalt"),
        ))
        assertEquals(listOf(true, false, null), p.map { it.kind })
        assertEquals(listOf(0.3, 0.6, 0.1), p.map { it.fraction })
    }

    /**
     * Les plages d'une categorie, en abscisses du TRACE : la longueur mesuree par l'application (ici 2000 m)
     * n'est pas celle du moteur (1000 m), et les plages sont ramenees a la premiere. Les morceaux voisins
     * de meme categorie ne font qu'une plage.
     */
    @Test fun `les plages sont a l'echelle du trace et fusionnees`() {
        val r = RouteDetails.ranges(
            trajet + seg(0.0, "highway=primary surface=asphalt"), 2000.0, { RouteDetails.surfaceOf(it) },
            SurfaceKind.ASPHALT,
        )
        assertEquals(listOf(0.0..1000.0, 1400.0..2000.0), r)
        val routes = RouteDetails.ranges(
            listOf(seg(100.0, "highway=track"), seg(100.0, "highway=track"), seg(100.0, "highway=path")),
            300.0, { RouteDetails.wayOf(it) }, WayKind.TRACK,
        )
        assertEquals(listOf(0.0..200.0), routes)
    }

    @Test fun `le trace d'une plage s'arrete a ses bouts interpoles`() {
        val samples = (0..4).map { i -> Sample(x = i * 100.0, z = 0.0, slope = 0.0, t = null, lon = i.toDouble(), lat = 0.0) }
        val p = RouteDetails.pieces(samples, listOf(150.0..250.0))
        assertEquals(1, p.size)
        assertEquals(listOf(1.5 to 0.0, 2.0 to 0.0, 2.5 to 0.0), p[0])
        assertTrue(RouteDetails.pieces(samples.take(1), listOf(0.0..10.0)).isEmpty())
    }

    /** Le doigt sur la barre des parts : la categorie sous lui, les bords compris. */
    @Test fun `la categorie sous le doigt`() {
        val parts = RouteDetails.surfaces(trajet)
        assertEquals(SurfaceKind.ASPHALT, RouteDetails.kindAt(parts, 0.0))
        assertEquals(SurfaceKind.ASPHALT, RouteDetails.kindAt(parts, 0.79))
        assertEquals(SurfaceKind.GRAVEL, RouteDetails.kindAt(parts, 0.81))
        assertEquals(SurfaceKind.GRAVEL, RouteDetails.kindAt(parts, 1.0))
        assertNull(RouteDetails.kindAt(emptyList<RouteDetails.Share<SurfaceKind>>(), 0.5))
    }
}
