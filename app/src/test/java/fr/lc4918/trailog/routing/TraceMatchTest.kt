package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.geo.RouteDetails
import fr.lc4918.trailog.domain.model.SurfaceKind
import fr.lc4918.trailog.domain.model.WayKind
import fr.lc4918.trailog.domain.model.WaySegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recalage d'une trace enregistree sur les voies OSM (cf. [TraceMatch]).
 *
 * Les reponses ci-dessous reprennent la forme de reponses reelles de l'instance publique, relevees sur
 * Soreze - Arfons et sur le VTT noir de Rignac pendant la mise au point.
 */
class TraceMatchTest {

    // ---------- Requete ----------

    /** Le recalage est le chemin frere du calcul, sur la meme instance que le reglage designe. */
    @Test fun `l'url du recalage remplace le chemin du calcul`() {
        assertEquals("https://valhalla1.openstreetmap.de/trace_attributes",
            TraceMatch.traceUrl("https://valhalla1.openstreetmap.de/route"))
        assertEquals("https://x/valhalla/trace_attributes", TraceMatch.traceUrl("https://x/valhalla/"))
    }

    @Test fun `la chaine de requete de l'url de base passe derriere le chemin`() {
        assertEquals("https://x/trace_attributes?key=abc", TraceMatch.traceUrl("https://x/route?key=abc"))
    }

    @Test fun `le corps demande le recalage colle aux voies et les attributs lus`() {
        val b = TraceMatch.body(listOf(43.5 to 2.0, 43.6 to 2.1), "pedestrian")
        assertTrue(b, """"shape":[{"lat":43.5,"lon":2.0},{"lat":43.6,"lon":2.1}]""" in b)
        assertTrue(b, """"costing":"pedestrian"""" in b)
        assertTrue(b, """"shape_match":"map_snap"""" in b)
        for (a in listOf("edge.way_id", "edge.length", "edge.surface", "edge.use", "matched.edge_index"))
            assertTrue(a, "\"$a\"" in b)
    }

    /** Une virgule decimale francaise rendrait un JSON invalide, et le service une erreur. */
    @Test fun `les coordonnees gardent le point decimal en locale francaise`() {
        val defaut = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE)
            assertTrue(""""lat":43.56""" in TraceMatch.body(listOf(43.56 to 6.08, 45.0 to 5.0), "bicycle"))
        } finally {
            java.util.Locale.setDefault(defaut)
        }
    }

    // ---------- Decoupage ----------

    /** Points espaces d'environ 1,11 km le long d'un meridien. */
    private fun meridien(n: Int) = List(n) { 43.0 + it * 0.01 to 2.0 }

    @Test fun `une trace courte part d'un seul morceau`() {
        assertEquals(listOf(0..9), TraceMatch.chunks(meridien(10)))
    }

    /**
     * Au-dela de la limite de longueur, la trace est coupee, et deux morceaux voisins partagent leur point
     * de jonction : sans lui, le bout qui les separe ne serait recale par personne.
     */
    @Test fun `une longue trace est coupee en morceaux qui se touchent`() {
        val c = TraceMatch.chunks(meridien(10), maxMeters = 3_000.0)
        assertEquals(listOf(0..3, 3..6, 6..9), c)
    }

    @Test fun `une trace trop dense est coupee au nombre de points`() {
        val c = TraceMatch.chunks(meridien(7), maxPoints = 3)
        assertEquals(listOf(0..2, 2..4, 4..6), c)
    }

    @Test fun `moins de deux points ne font aucun morceau`() {
        assertTrue(TraceMatch.chunks(meridien(1)).isEmpty())
    }

    // ---------- Reponse ----------

    private val reponse = """
        {"edges":[
          {"way_id":1071981331,"surface":"paved_smooth","use":"road","road_class":"residential","length":0.067},
          {"way_id":807287666,"surface":"gravel","use":"path","road_class":"service_other","length":0.178},
          {"way_id":130670192,"surface":"paved_smooth","use":"road","road_class":"residential","length":0.071}],
         "matched_points":[
          {"type":"matched","edge_index":0},
          {"type":"interpolated","edge_index":1},
          {"type":"unmatched"},
          {"type":"unmatched"},
          {"type":"matched","edge_index":2}],
         "units":"kilometers"}
    """.trimIndent()

    @Test fun `la reponse est lue`() {
        val r = TraceMatch.parse(reponse)
        assertNotNull(r)
        assertEquals(3, r!!.edges.size)
        assertEquals(807287666L, r.edges[1].wayId)
        assertEquals(0.178, r.edges[1].length, 1e-9)
        assertNull(r.matchedPoints[2].edgeIndex)
    }

    /** Une erreur du service (trace trop longue, aucune voie) ne porte aucune arete. */
    @Test fun `une reponse d'erreur ne se lit pas`() {
        assertNull(TraceMatch.parse("""{"error_code":154,"error":"Path distance exceeds the max distance limit"}"""))
        assertNull(TraceMatch.parse("pas du json"))
    }

    /**
     * Les points 2 et 3 ne sont sur aucune voie : le trou va du point 1 (dernier recale) au point 4
     * (premier recale apres), soit trois intervalles, et se range apres l'arete du point 1.
     */
    @Test fun `un bout hors reseau est mesure sur la trace et range a sa place`() {
        val r = TraceMatch.parse(reponse)!!
        val g = TraceMatch.gapsOf(meridien(5), r)
        assertEquals(setOf(1), g.keys)
        assertEquals(3 * 1112.0, g.getValue(1), 5.0)
    }

    @Test fun `un trou en tete de trace se range avant la premiere arete`() {
        val r = TraceMatch.parse(
            """{"edges":[{"way_id":1,"length":1.0}],"matched_points":[{"type":"unmatched"},{"type":"matched","edge_index":0}]}"""
        )!!
        assertEquals(setOf(-1), TraceMatch.gapsOf(meridien(2), r).keys)
    }

    /** Des points qui ne correspondent pas a la trace envoyee : on ne sait pas placer les trous. */
    @Test fun `des points en nombre inattendu ne donnent aucun trou`() {
        assertTrue(TraceMatch.gapsOf(meridien(3), TraceMatch.parse(reponse)!!).isEmpty())
    }

    @Test fun `les morceaux suivent les aretes et les trous a leur place`() {
        val r = TraceMatch.parse(reponse)!!
        val s = TraceMatch.segmentsOf(r, mapOf(1 to 500.0)) { "w=${it.wayId}" }
        assertEquals(
            listOf(
                WaySegment(67.0, "w=1071981331"), WaySegment(178.0, "w=807287666"),
                WaySegment(500.0, ""), WaySegment(71.0, "w=130670192"),
            ),
            s.map { WaySegment(Math.round(it.meters).toDouble(), it.tags) },
        )
    }

    /** Une voie coupee en autant d'aretes que de carrefours n'en fait qu'un morceau. */
    @Test fun `deux aretes voisines aux memes attributs n'en font qu'une`() {
        val r = TraceMatch.parse(reponse)!!
        val s = TraceMatch.segmentsOf(r, emptyMap()) { "highway=residential" }
        assertEquals(1, s.size)
        assertEquals(316.0, s.single().meters, 1e-6)
    }

    /** Hors reseau, pas d'attribut : le classement le dit inconnu, et non estime. */
    @Test fun `un bout hors reseau se classe inconnu`() {
        val trou = WaySegment(100.0, "")
        assertEquals(SurfaceKind.UNKNOWN, RouteDetails.surfaceOf(trou))
        assertEquals(WayKind.OTHER, RouteDetails.wayOf(trou))
    }

    // ---------- Repli sur les attributs de Valhalla ----------

    private fun repli(use: String?, roadClass: String?, surface: String?) =
        TraceMatch.fallbackTagsOf(TraceMatch.Edge(1, 0.1, surface, roadClass, use))

    @Test fun `le repli traduit les attributs de valhalla dans le vocabulaire d'osm`() {
        assertEquals("highway=track surface=gravel", repli("track", "service_other", "gravel"))
        assertEquals("highway=residential surface=asphalt", repli("road", "residential", "paved_smooth"))
        assertEquals("highway=path surface=ground", repli("path", "service_other", "dirt"))
        assertEquals("route=ferry", repli("ferry", "service_other", "paved"))
    }

    /** `surface=path` ne dit rien du revetement : le classement l'estime d'apres le type de voie. */
    @Test fun `le repli laisse estimer un revetement que valhalla ne dit pas`() {
        val tags = repli("footway", "service_other", "path")
        assertEquals("highway=footway", tags)
        assertTrue(RouteDetails.isSurfaceEstimated(WaySegment(1.0, tags)))
    }

    @Test fun `le repli classe les voies comme le reste`() {
        fun voie(use: String, rc: String) = RouteDetails.wayOf(WaySegment(1.0, repli(use, rc, null)))
        assertEquals(WayKind.MAIN_ROAD, voie("road", "primary"))
        assertEquals(WayKind.ROAD, voie("road", "tertiary"))
        assertEquals(WayKind.SERVICE, voie("driveway", "service_other"))
        assertEquals(WayKind.CYCLEWAY, voie("cycleway", "service_other"))
        assertEquals(WayKind.STEPS, voie("steps", "service_other"))
    }

    // ---------- Lignes d'une couche ----------

    private fun fait(vararg s: WaySegment, horsReseau: Double = 0.0, osm: Boolean = true) =
        TraceMatch.Outcome.Done(TraceMatch.WayMatch(s.toList(), horsReseau, osm))

    @Test fun `les voies se rangent par ligne, dans l'ordre des lignes`() {
        val o = TraceMatch.combine(
            listOf(fait(WaySegment(100.0, "highway=track")), fait(WaySegment(50.0, "highway=path"), horsReseau = 5.0)),
            listOf(100.0, 50.0),
        ) as TraceMatch.LayerOutcome.Done
        assertEquals(listOf(listOf(WaySegment(100.0, "highway=track")), listOf(WaySegment(50.0, "highway=path"))),
            o.ways.lines)
        assertEquals(5.0, o.ways.offNetworkMeters, 1e-9)
        assertTrue(o.ways.osmTags)
    }

    /** Une ligne non recalee reste dans les parts, toute entiere "inconnue", et compte hors reseau. */
    @Test fun `une ligne non recalee compte toute entiere hors reseau`() {
        val o = TraceMatch.combine(
            listOf(fait(WaySegment(100.0, "highway=track")), TraceMatch.Outcome.NoMatch),
            listOf(100.0, 30.0),
        ) as TraceMatch.LayerOutcome.Done
        assertEquals(listOf(WaySegment(30.0, "")), o.ways.lines[1])
        assertEquals(30.0, o.ways.offNetworkMeters, 1e-9)
        assertEquals(2, o.ways.lines.size)
    }

    @Test fun `aucune ligne recalee ne donne rien a montrer`() {
        assertEquals(TraceMatch.LayerOutcome.NoMatch,
            TraceMatch.combine(listOf(TraceMatch.Outcome.NoMatch), listOf(10.0)))
    }

    @Test fun `une ligne injoignable fait echouer la couche`() {
        assertEquals(TraceMatch.LayerOutcome.Unreachable,
            TraceMatch.combine(listOf(fait(WaySegment(1.0, "")), TraceMatch.Outcome.Unreachable), listOf(1.0, 1.0)))
    }

    /** Un seul repli sur les attributs de Valhalla suffit a dire l'ensemble approximatif. */
    @Test fun `les attributs ne sont dits d'osm que si toutes les lignes le sont`() {
        val o = TraceMatch.combine(
            listOf(fait(WaySegment(1.0, "a")), fait(WaySegment(1.0, "b"), osm = false)), listOf(1.0, 1.0),
        ) as TraceMatch.LayerOutcome.Done
        assertTrue(!o.ways.osmTags)
    }
}
