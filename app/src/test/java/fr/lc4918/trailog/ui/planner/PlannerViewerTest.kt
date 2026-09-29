package fr.lc4918.trailog.ui.planner

import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.SurfaceKind
import fr.lc4918.trailog.domain.model.TrackStats
import fr.lc4918.trailog.domain.model.WayKind
import fr.lc4918.trailog.domain.model.WaySegment
import fr.lc4918.trailog.geocode.GeocodePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les affichages VIEWER du planificateur et la zone "Details", cote etat.
 *
 * En JUnit nu, comme `RouteReuseTest` : un porteur d'etat Compose instancie sous Robolectric empoisonne la
 * JVM des tests d'interface qui passent apres (cf. PlannerStoreTest).
 */
class PlannerViewerTest {

    private val segments = listOf(
        WaySegment(800.0, "highway=primary surface=asphalt"),
        WaySegment(200.0, "highway=track surface=gravel"),
    )

    private fun calcule(): RoutePlannerState = RoutePlannerState().apply {
        openPlanner()
        choose(steps.first(), StepTarget.Place(GeocodePlace("A", 5.70, 45.20)))
        choose(steps.last(), StepTarget.Place(GeocodePlace("B", 5.71, 45.21)))
        val track = ComputedTrack(
            samples = listOf(Sample(0.0, 210.0, 0.0, null, 5.70, 45.20), Sample(1000.0, 224.0, 1.4, null, 5.71, 45.21)),
            stats = TrackStats(1000.0, 14.0, 0.0, 210.0, 224.0, 1.4, null, 2),
            hasZ = true, hasTime = false,
        )
        publish(RouteState.Done(1000.0, 300.0, track, segments = segments))
    }

    @Test fun `sans parcours, aucun viewer ne s'ouvre`() {
        val etat = RoutePlannerState().apply { openPlanner() }
        etat.openViewer(PlannerViewer.PROFILE)
        assertNull(etat.viewer)
    }

    @Test fun `un viewer s'ouvre avec sa categorie, et se referme sur la bande`() {
        val etat = calcule()
        etat.openViewer(PlannerViewer.SURFACES, SurfaceKind.ASPHALT)
        assertEquals(PlannerViewer.SURFACES, etat.viewer)
        assertEquals(SurfaceKind.ASPHALT, etat.highlight)
        assertTrue("la bande reste deployee dessous", etat.expanded)
        etat.select(SurfaceKind.GRAVEL)
        assertEquals(SurfaceKind.GRAVEL, etat.highlight)
        etat.closeViewer()
        assertNull(etat.viewer)
        assertNull("plus rien d'eclaire sur la carte", etat.highlight)
    }

    /** Passer d'un viewer a l'autre change la famille mise en evidence : un revetement n'est pas une voie. */
    @Test fun `changer de viewer change la categorie mise en evidence`() {
        val etat = calcule()
        etat.openViewer(PlannerViewer.SURFACES, SurfaceKind.ASPHALT)
        etat.openViewer(PlannerViewer.WAYS, WayKind.MAIN_ROAD)
        assertEquals(WayKind.MAIN_ROAD, etat.highlight)
        etat.openViewer(PlannerViewer.PROFILE)
        assertNull(etat.highlight)
    }

    /** La carte se cadre sur TOUT le parcours en entrant : un profil grossi ne lui correspondrait plus. */
    @Test fun `entrer dans un viewer remet le profil a sa vue complete`() {
        val etat = calcule()
        etat.zoomBy(4f, 0.5f, 100)
        assertTrue(etat.zoomed)
        etat.openViewer(PlannerViewer.SURFACES, SurfaceKind.ASPHALT)
        assertFalse(etat.zoomed)
    }

    /** Un viewer montre le parcours d'avant : toucher aux etapes rend la bande, qui dira le recalcul. */
    @Test fun `changer le trajet referme le viewer`() {
        val etat = calcule()
        etat.openViewer(PlannerViewer.WAYS, WayKind.TRACK)
        etat.addWaypoint(GeocodePlace("C", 5.705, 45.205))
        assertNull(etat.viewer)
        assertNull(etat.highlight)
    }

    @Test fun `ranger la bande, ou tout effacer, referme le viewer`() {
        val etat = calcule()
        etat.openViewer(PlannerViewer.PROFILE)
        etat.collapse(true)
        assertNull(etat.viewer)
        etat.collapse(false)
        etat.openViewer(PlannerViewer.SURFACES, SurfaceKind.GRAVEL)
        etat.reset()
        assertNull(etat.viewer)
        assertNull(etat.highlight)
    }

    /** La zone "Details" se replie comme le profil, et s'efface de meme pendant la saisie d'une etape. */
    @Test fun `les details s'affichent ouverts, sur un parcours, hors saisie`() {
        val etat = calcule()
        assertFalse("replies par defaut", etat.detailsShown)
        etat.toggleDetails()
        assertTrue(etat.detailsShown)
        etat.setEditing(etat.steps.first(), true)
        assertFalse(etat.detailsShown)
        etat.setEditing(etat.steps.first(), false)
        assertTrue(etat.detailsShown)
        val vierge = RoutePlannerState().apply { openPlanner(); toggleDetails() }
        assertFalse("rien de calcule, rien a detailler", vierge.detailsShown)
    }

    /** Les attributs des voies voyagent avec le parcours garde sur le disque. */
    @Test fun `les attributs des voies survivent a la reprise`() {
        val garde = calcule().snapshot()!!
        assertEquals(segments, garde.segments)
        val repris = RoutePlannerState().apply { restore(garde) }
        assertEquals(segments, repris.done!!.segments)
    }
}
