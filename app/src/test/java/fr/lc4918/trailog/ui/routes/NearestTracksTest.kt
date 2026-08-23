package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.ui.alert.PlannedRouteLayerId
import fr.lc4918.trailog.ui.alert.TrackCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // ---------- Le parcours du planificateur, propose sans etre importe ----------

    /** Une ligne droite de deux points, comme celle qu'un moteur d'itineraire rend. */
    private fun parcours(vararg points: Pair<Double, Double>) = points.map { (lat, lon) ->
        Sample(x = 0.0, z = 0.0, slope = 0.0, t = null, lon = lon, lat = lat)
    }

    /**
     * Le parcours calcule se suit comme une trace, sans passer par la bibliotheque : c'est meme le cas le
     * plus courant - on compose son trajet, on part, et on veut etre prevenu si on le quitte.
     */
    @Test fun `le parcours calcule devient une candidate`() {
        val c = plannedCandidate(parcours(43.0 to 2.0, 43.0 to 2.1), "En cours", lat = 43.0, lon = 2.05)
        assertNotNull(c)
        assertEquals(PlannedRouteLayerId, c!!.layerId)
        assertEquals("En cours", c.layerName)
        assertEquals("un seul segment : la liste ne le numerote pas", 1, c.trackCount)
        assertTrue("la position est sur le trace", c.awayM < 1.0)
    }

    /** L'ecart est reellement mesure : c'est lui qui permet de reconnaitre le parcours sur la carte. */
    @Test fun `l'ecart au parcours est mesure`() {
        val c = plannedCandidate(parcours(43.0 to 2.0, 43.0 to 2.1), "En cours", lat = 43.01, lon = 2.05)
        assertNotNull(c)
        assertTrue("environ un kilometre plus au nord", c!!.awayM > 900.0 && c.awayM < 1200.0)
    }

    /** Rien de calcule, ou une geometrie d'un seul point : il n'y a pas de trace a suivre. */
    @Test fun `sans parcours, aucune candidate`() {
        assertNull(plannedCandidate(null, "En cours", 43.0, 2.0))
        assertNull(plannedCandidate(emptyList(), "En cours", 43.0, 2.0))
        assertNull(plannedCandidate(parcours(43.0 to 2.0), "En cours", 43.0, 2.0))
    }

    /**
     * Il ne se classe PAS avec les autres : il passe en tete quoi qu'il arrive.
     *
     * C'est celui qu'on vient de composer, et une trace de la bibliotheque qui passerait dix metres plus
     * pres ne repond pas a la question qu'on pose en touchant la cloche. Le tri des autres ne le voit donc
     * jamais - il s'ajoute apres.
     */
    @Test fun `le parcours n'entre pas dans le classement des traces`() {
        val enCours = plannedCandidate(parcours(43.0 to 2.0, 43.0 to 2.1), "En cours", 43.01, 2.05)
        val liste = listOfNotNull(enCours) + closestCandidates(listOf(candidate(5.0, id = 7)))
        assertEquals(PlannedRouteLayerId, liste.first().layerId)
        assertTrue("et il est pourtant le plus loin", liste.first().awayM > liste.last().awayM)
    }
}
