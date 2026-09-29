package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.LayerWays
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.SurfaceKind
import fr.lc4918.trailog.domain.model.WayKind
import fr.lc4918.trailog.domain.model.WaySegment
import fr.lc4918.trailog.ui.planner.PlannerViewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le VIEWER des voies d'une trace de la bibliotheque : son etat, et ce qu'il met en evidence sur la carte.
 *
 * Une couche peut porter plusieurs lignes, chacune recalee a part : la faute a craindre est une categorie
 * posee sur la mauvaise ligne, ou a l'echelle d'une autre - rien ne leve, la mise en evidence tombe
 * simplement a cote de la trace.
 */
class LayerWaysViewerTest {

    private val couche = LayerEntity(id = 7, name = "Soreze", folderId = null, geometryFile = "x.geojson")

    /** Une ligne droite le long d'un parallele, un echantillon tous les [pas] metres. */
    private fun ligne(lon0: Double, n: Int, pas: Double = 100.0) = List(n) { i ->
        Sample(x = i * pas, z = 0.0, slope = 0.0, t = null, lon = lon0 + i * 0.001, lat = 43.0)
    }

    /** Le profil d'une ligne faite de ces echantillons, altitudes comprises ou non. */
    private fun profil(s: List<Sample>, z: Boolean = true) = ComputedTrack(s, TrackMath.statsOf(s), hasZ = z, hasTime = false)

    private val profils = listOf(profil(ligne(2.0, 11)), profil(ligne(3.0, 3)))

    private val voies = LayerWays(
        lines = listOf(
            listOf(WaySegment(500.0, "highway=track surface=gravel"), WaySegment(500.0, "highway=tertiary surface=asphalt")),
            listOf(WaySegment(200.0, "highway=track surface=gravel")),
        ),
    )

    // ---------- Etat ----------

    @Test fun `ouvrir montre la couche, le viewer et la categorie initiale`() {
        val s = LayerWaysViewerState()
        assertFalse(s.isOpen)
        s.open(couche, voies, profils, PlannerViewer.WAYS, WayKind.TRACK)
        assertTrue(s.isOpen)
        assertEquals(PlannerViewer.WAYS, s.viewer)
        assertEquals(WayKind.TRACK, s.highlight)
    }

    @Test fun `passer d'un viewer a l'autre change la categorie mise en evidence`() {
        val s = LayerWaysViewerState()
        s.open(couche, voies, profils, PlannerViewer.SURFACES, SurfaceKind.GRAVEL)
        s.switch(PlannerViewer.WAYS, WayKind.ROAD)
        assertEquals(PlannerViewer.WAYS, s.viewer)
        assertEquals(WayKind.ROAD, s.highlight)
        s.select(WayKind.TRACK)
        assertEquals(WayKind.TRACK, s.highlight)
    }

    @Test fun `fermer oublie la couche et la mise en evidence`() {
        val s = LayerWaysViewerState()
        s.open(couche, voies, profils, PlannerViewer.SURFACES, SurfaceKind.GRAVEL)
        s.close()
        assertFalse(s.isOpen)
        assertNull(s.layer)
        assertNull(s.highlight)
        // Ferme, basculer ne rouvre rien.
        s.switch(PlannerViewer.WAYS, WayKind.ROAD)
        assertFalse(s.isOpen)
    }

    // ---------- Mise en evidence ----------

    /** La premiere ligne fait 1 km, la seconde 200 m : le gravier est au debut de l'une et partout sur l'autre. */
    @Test fun `chaque ligne se met en evidence sur ses propres echantillons`() {
        val a = ligne(2.0, 11)          // 0 a 1000 m
        val b = ligne(3.0, 3)           // 0 a 200 m, ailleurs
        val p = layerHighlightPieces(voies, listOf(a, b), SurfaceKind.GRAVEL)
        assertEquals(2, p.size)
        // Premiere ligne : la premiere moitie, de lon 2.000 a 2.005.
        assertEquals(2.0, p[0].first().first, 1e-9)
        assertEquals(2.005, p[0].last().first, 1e-9)
        // Seconde ligne : toute entiere, sur SES points.
        assertEquals(3.0, p[1].first().first, 1e-9)
        assertEquals(3.002, p[1].last().first, 1e-9)
    }

    @Test fun `un type de voie se met en evidence comme un revetement`() {
        val p = layerHighlightPieces(voies, listOf(ligne(2.0, 11), ligne(3.0, 3)), WayKind.ROAD)
        assertEquals(1, p.size)
        assertEquals(2.005, p[0].first().first, 1e-9)
        assertEquals(2.01, p[0].last().first, 1e-9)
    }

    @Test fun `rien de choisi, rien de mis en evidence`() {
        assertTrue(layerHighlightPieces(voies, listOf(ligne(2.0, 11), ligne(3.0, 3)), null).isEmpty())
    }

    /** Des echantillons pas encore lus : rien plutot qu'une mise en evidence au hasard. */
    @Test fun `sans echantillons, rien n'est mis en evidence`() {
        assertTrue(layerHighlightPieces(voies, emptyList(), SurfaceKind.GRAVEL).isEmpty())
    }

    // ---------- Profil ----------

    /** Une couche a plusieurs lignes n'a pas UN profil : on montre celui de la plus longue. */
    @Test fun `le profil montre est celui de la ligne la plus longue`() {
        assertEquals(profils[0], profileTrackOf(listOf(profils[1], profils[0])))
        assertNull(profileTrackOf(emptyList()))
    }

    @Test fun `le profil s'ouvre sur le point touche`() {
        val s = LayerWaysViewerState()
        s.open(couche, voies, profils, PlannerViewer.PROFILE, 420.0)
        assertEquals(PlannerViewer.PROFILE, s.viewer)
        assertEquals(420.0, s.cursor!!, 1e-9)
        assertNull(s.highlight)
    }

    @Test fun `les viewer proposes sont ceux qui ont quelque chose a montrer`() {
        val s = LayerWaysViewerState()
        s.open(couche, voies, profils, PlannerViewer.SURFACES, null)
        assertEquals(listOf(PlannerViewer.PROFILE, PlannerViewer.SURFACES, PlannerViewer.WAYS), s.offered)
        // Sans altitudes, pas de profil ; sans voies retrouvees, pas de voies.
        s.open(couche, voies, profils.map { it.copy(hasZ = false) }, PlannerViewer.SURFACES, null)
        assertEquals(listOf(PlannerViewer.SURFACES, PlannerViewer.WAYS), s.offered)
        s.open(couche, null, profils, PlannerViewer.PROFILE, null)
        assertEquals(listOf(PlannerViewer.PROFILE), s.offered)
    }

    /** Les voies pas encore retrouvees : demander les surfaces montre le profil, seul propose. */
    @Test fun `un viewer sans rien a montrer cede la place`() {
        val s = LayerWaysViewerState()
        s.open(couche, null, profils, PlannerViewer.SURFACES, SurfaceKind.GRAVEL)
        assertEquals(PlannerViewer.PROFILE, s.viewer)
        assertNull(s.highlight)
    }

    @Test fun `grossir le profil efface le point, changer de viewer rend tout le profil`() {
        val s = LayerWaysViewerState()
        // Assez d'echantillons pour qu'une fenetre grossie en laisse moins que le tout.
        s.open(couche, voies, listOf(profil(ligne(2.0, 400, pas = 10.0))), PlannerViewer.PROFILE, 300.0)
        s.zoomBy(2f, 0.5f)
        assertTrue(s.zoomRange != null)
        assertNull(s.cursor)
        s.switch(PlannerViewer.SURFACES, SurfaceKind.GRAVEL)
        assertNull(s.zoomRange)
        s.switch(PlannerViewer.PROFILE, null)
        s.zoomBy(2f, 0.5f)
        s.resetZoom()
        assertNull(s.zoomRange)
    }

    // ---------- Panneau du profil d'une trace touchee ----------

    /** Une trace touchee s'ouvre sur son profil, point courant compris. */
    @Test fun `le panneau s'ouvre sur le profil`() {
        assertEquals(PlannerViewer.PROFILE, TrackPanelView.Initial.viewer)
        assertTrue(TrackPanelView.Initial.cursorShown)
    }

    /** Hors du profil, le point courant ne designe rien : il se retire, et revient avec le profil. */
    @Test fun `le point courant ne se montre que sur le profil`() {
        val segments = voies.lines[0]
        val surfaces = TrackPanelView.Initial.showing(PlannerViewer.SURFACES, segments)
        assertFalse(surfaces.cursorShown)
        assertFalse(surfaces.showing(PlannerViewer.WAYS, segments).cursorShown)
        assertTrue(surfaces.showing(PlannerViewer.PROFILE, segments).cursorShown)
    }

    @Test fun `changer de viewer met en evidence la plus longue categorie de la ligne`() {
        // Premiere ligne : 500 m de gravier et 500 m d'asphalte ; la seconde : du gravier seul.
        val v = TrackPanelView.Initial.showing(PlannerViewer.WAYS, voies.lines[1])
        assertEquals(WayKind.TRACK, v.highlight)
        assertEquals(SurfaceKind.ASPHALT, v.selecting(SurfaceKind.ASPHALT).highlight)
        assertNull(v.showing(PlannerViewer.PROFILE, voies.lines[1]).highlight)
    }

    @Test fun `les voies d'une ligne absente sont vides`() {
        assertEquals(voies.lines[1], lineWays(voies, 1))
        assertTrue(lineWays(voies, 5).isEmpty())
    }
}
