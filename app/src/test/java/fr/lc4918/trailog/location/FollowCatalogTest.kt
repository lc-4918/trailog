package fr.lc4918.trailog.location

import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.domain.model.Sample
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Les traces que le suivi automatique peut reconnaitre. Ce qui se decide ici est le TRAVAIL FOURNI : a
 * chaque position, seules les couches a portee sont ouvertes, et une couche qu'on ne voit pas n'entre pas.
 */
class FollowCatalogTest {

    @After fun raz() = FollowCatalog.setRoute(null, "")

    /** Une couche dont l'emprise est un petit carre autour de (lat, lon). */
    private fun couche(id: Long, lat: Double, lon: Double, visible: Boolean = true, hasLine: Boolean = true) =
        LayerEntity(
            id = id, name = "couche$id", folderId = null, geometryFile = "couche$id.geojson",
            visible = visible, hasLine = hasLine,
            west = lon - 0.01, south = lat - 0.01, east = lon + 0.01, north = lat + 0.01,
        )

    @Test fun `seules les couches a portee sont ouvertes`() {
        val dedans = couche(1, 43.0, 2.0)
        val loin = couche(2, 43.5, 2.0)
        assertEquals(listOf(1L), FollowCatalog.layersNear(listOf(loin, dedans), 43.0, 2.0).map { it.id })
    }

    /** Une couche masquee n'est pas a l'ecran ; une couche de points seuls n'a pas de trace a suivre. */
    @Test fun `couches masquees et sans ligne ecartees`() {
        val masquee = couche(1, 43.0, 2.0, visible = false)
        val points = couche(2, 43.0, 2.0, hasLine = false)
        assertEquals(emptyList<LayerEntity>(), FollowCatalog.layersNear(listOf(masquee, points), 43.0, 2.0))
    }

    @Test fun `le nombre de couches ouvertes est borne`() {
        val empilees = (1..40L).map { couche(it, 43.0, 2.0) }
        assertEquals(FollowCatalog.MAX_LAYERS, FollowCatalog.layersNear(empilees, 43.0, 2.0).size)
    }

    private fun parcours(vararg points: Pair<Double, Double>) = points.map { (lat, lon) ->
        Sample(x = 0.0, z = 0.0, slope = 0.0, t = null, lon = lon, lat = lat)
    }

    /** Le parcours du planificateur se reconnait comme une trace, sous son identifiant a lui. */
    @Test fun `le parcours calcule devient une candidate`() {
        FollowCatalog.setRoute(parcours(43.0 to 2.0, 43.0 to 2.1), "En cours")
        val c = FollowCatalog.route.value!!
        assertEquals(FollowCatalog.ROUTE_ID, c.id)
        assertEquals("En cours", c.name)
        assertEquals(2.0, c.west, 1e-9)
        assertEquals(2.1, c.east, 1e-9)
    }

    @Test fun `sans parcours, aucune candidate`() {
        FollowCatalog.setRoute(parcours(43.0 to 2.0), "En cours")
        assertNull(FollowCatalog.route.value)
        FollowCatalog.setRoute(null, "En cours")
        assertNull(FollowCatalog.route.value)
    }
}
