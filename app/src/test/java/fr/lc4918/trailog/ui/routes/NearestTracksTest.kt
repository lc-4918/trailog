package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.ui.alert.TrackCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tri en deux temps qui trouve la trace a suivre sans tout relire.
 *
 * Ce qui se decide ici est le TRAVAIL FOURNI : projeter la position sur une trace demande de lire son
 * profil, des milliers de points. La premiere passe ne lit rien et n'en retient qu'une poignee ; sans elle,
 * un tap sur la cloche ouvrirait les cinquante couches de quelqu'un qui en a cinquante.
 */
class NearestTracksTest {

    /** Une couche dont l'emprise est un petit carre autour de (lat, lon). */
    private fun couche(
        id: Long, lat: Double, lon: Double,
        visible: Boolean = true, hasLine: Boolean = true,
    ) = LayerEntity(
        id = id, name = "couche$id", folderId = null, geometryFile = "couche$id.geojson",
        visible = visible, hasLine = hasLine,
        west = lon - 0.01, south = lat - 0.01, east = lon + 0.01, north = lat + 0.01,
    )

    // ---------- Premiere passe : quelles couches ouvrir ----------

    @Test fun `les couches sont classees par distance a leur emprise`() {
        val loin = couche(1, 44.0, 2.0)
        val pres = couche(2, 43.0, 2.0)
        val moyen = couche(3, 43.5, 2.0)
        assertEquals(listOf(2L, 3L, 1L),
            layersToScan(listOf(loin, pres, moyen), lat = 43.0, lon = 2.0).map { it.id })
    }

    /** Une couche eteinte n'est pas a l'ecran : la proposer ferait suivre une trace qu'on ne voit pas. */
    @Test fun `une couche eteinte est ecartee`() {
        val eteinte = couche(1, 43.0, 2.0, visible = false)
        val allumee = couche(2, 44.0, 2.0)
        assertEquals(listOf(2L), layersToScan(listOf(eteinte, allumee), 43.0, 2.0).map { it.id })
    }

    /** Une couche de points seuls n'a pas de trace a suivre. */
    @Test fun `une couche sans ligne est ecartee`() {
        val points = couche(1, 43.0, 2.0, hasLine = false)
        val trace = couche(2, 44.0, 2.0)
        assertEquals(listOf(2L), layersToScan(listOf(points, trace), 43.0, 2.0).map { it.id })
    }

    /**
     * La borne est ce qui tient le cout : au-dela, on paie des lectures pour des traces qui sont a des
     * kilometres.
     */
    @Test fun `le nombre de couches ouvertes est borne`() {
        val beaucoup = (1..40L).map { couche(it, 43.0 + it * 0.1, 2.0) }
        assertEquals(NEAREST_SCAN_LAYERS, layersToScan(beaucoup, 43.0, 2.0).size)
        assertEquals(3, layersToScan(beaucoup, 43.0, 2.0, limit = 3).size)
    }

    @Test fun `sans couche eligible, on n'ouvre rien`() {
        assertEquals(emptyList<LayerEntity>(),
            layersToScan(listOf(couche(1, 43.0, 2.0, visible = false)), 43.0, 2.0))
        assertEquals(emptyList<LayerEntity>(), layersToScan(emptyList(), 43.0, 2.0))
    }

    /**
     * La distance a l'emprise minore la distance a la trace, le rectangle contenant la trace. Un mauvais
     * classement ne peut donc qu'ouvrir une couche pour rien, jamais ecarter une couche plus proche.
     */
    @Test fun `une position dans l'emprise est a distance nulle`() {
        val dedans = couche(1, 43.0, 2.0)
        val dehors = couche(2, 43.5, 2.0)
        assertEquals(listOf(1L, 2L), layersToScan(listOf(dehors, dedans), 43.0, 2.0).map { it.id })
    }

    // ---------- Seconde passe : quels segments proposer ----------

    private fun candidate(away: Double, id: Long = 1) =
        TrackCandidate(layerId = id, layerName = "c$id", trackIndex = 0, trackCount = 1,
            awayM = away, samples = emptyList())

    @Test fun `les segments sont classes du plus proche au plus loin`() {
        val r = closestCandidates(listOf(candidate(50.0), candidate(5.0), candidate(20.0)))
        assertEquals(listOf(5.0, 20.0, 50.0), r.map { it.awayM })
    }

    /** Au-dela, on ne choisit plus, on cherche : une liste qui demande elle-meme a etre parcourue n'a pas
     *  rendu le service qu'on lui demandait. */
    @Test fun `le nombre de propositions est borne`() {
        val vingt = (1..20).map { candidate(it.toDouble()) }
        assertEquals(NEAREST_TRACK_COUNT, closestCandidates(vingt).size)
        assertTrue("et ce sont les plus proches", closestCandidates(vingt).all { it.awayM <= 8.0 })
    }

    @Test fun `sans candidat, la liste reste vide`() {
        assertEquals(emptyList<TrackCandidate>(), closestCandidates(emptyList()))
    }
}
