package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ou la camera se pose a l'ouverture de l'ecran - et a chaque rotation, qui le recree.
 *
 * **Ce test vient du terrain.** On decentrait la carte de sa position, on tournait l'ecran, et la carte ne
 * revenait pas : elle sautait au cadrage enregistre, ou, faute de camera enregistree, dezoomait pour
 * prendre toute la couche. La rotation recree l'activite et la `MapView` avec elle, si bien que ce
 * placement - qu'on croyait reserve a l'ouverture - se rejoue a chaque quart de tour, apres coup, et
 * defaisait ce que l'utilisateur venait de faire.
 *
 * Ce qui se verifie ici est l'ORDRE des quatre branches. Il ne se verifie nulle part ailleurs :
 * `MapController` ne bouge aucune camera tant qu'aucune `MapView` n'existe, et il n'en existe pas sur la
 * JVM (cf. la note de `MainScreenUiTest`).
 */
class CameraPlacementTest {

    private val position = 45.18 to 5.72

    private fun couche(id: Long, lat: Double, lon: Double, visible: Boolean = true) = LayerEntity(
        id = id, name = "couche$id", folderId = null, geometryFile = "c$id.geojson",
        visible = visible, hasLine = true,
        west = lon - 0.1, south = lat - 0.1, east = lon + 0.1, north = lat + 0.1,
    )

    /** Des reglages qui portent un cadrage enregistre - ailleurs que la position, pour les distinguer. */
    private val avecCamera = SettingsEntity(
        hasCamera = true, lastLat = 43.6, lastLon = 1.44, lastZoom = 13.0,
    )

    private fun ou(
        suit: Boolean = true,
        pos: Pair<Double, Double>? = position,
        reglages: SettingsEntity = SettingsEntity(),
        couches: List<LayerEntity> = emptyList(),
    ) = CameraPlacement.target(suit, pos, reglages, couches)

    // ---------- 1. la position passe avant tout ----------

    /**
     * LE cas du terrain : un cadrage est enregistre, la carte suit la position, et c'est la POSITION qui
     * gagne. Le cadrage dit ou l'on regardait ; il ne dit pas ou l'on est.
     */
    @Test fun `la position l'emporte sur le cadrage enregistre`() {
        assertEquals(
            CameraTarget.Point(45.18, 5.72, 13.0),
            ou(reglages = avecCamera),
        )
    }

    /** Le zoom enregistre voyage avec : une rotation n'est pas une demande de changer d'echelle. */
    @Test fun `le zoom enregistre suit la position`() {
        val cible = ou(reglages = avecCamera) as CameraTarget.Point
        assertEquals(13.0, cible.zoom!!, 1e-9)
    }

    /** Sans cadrage enregistre, aucun zoom n'est impose : on garde celui ou la carte vient de s'ouvrir. */
    @Test fun `sans cadrage enregistre, le zoom n'est pas force`() {
        assertEquals(CameraTarget.Point(45.18, 5.72, null), ou())
    }

    @Test fun `la position l'emporte aussi sur les couches`() {
        assertEquals(
            CameraTarget.Point(45.18, 5.72, null),
            ou(couches = listOf(couche(1, 48.85, 2.35))),
        )
    }

    // ---------- 2. sinon, le dernier cadrage ----------

    /** Le suivi eteint : la carte rouvre la ou on l'a laissee, comme avant. */
    @Test fun `sans suivi, le cadrage enregistre reprend la main`() {
        assertEquals(
            CameraTarget.Point(43.6, 1.44, 13.0),
            ou(suit = false, reglages = avecCamera),
        )
    }

    /** Le suivi allume mais aucune position connue - le capteur n'a pas encore repondu : on ne peut pas se
     *  poser sur ce qu'on ignore, et le cadrage enregistre reste la meilleure reponse. */
    @Test fun `le suivi sans position ne change rien`() {
        assertEquals(
            CameraTarget.Point(43.6, 1.44, 13.0),
            ou(pos = null, reglages = avecCamera),
        )
    }

    // ---------- 3. sinon, les couches visibles ----------

    @Test fun `premier lancement avec des traces, on cadre leur emprise`() {
        val cible = ou(suit = false, couches = listOf(couche(1, 45.0, 5.0), couche(2, 46.0, 6.0)))
        assertEquals(CameraTarget.Bounds(4.9, 44.9, 6.1, 46.1), cible)
    }

    /** Une couche eteinte n'est pas a l'ecran : elle n'a pas a etirer le cadrage. */
    @Test fun `une couche masquee ne compte pas dans l'emprise`() {
        val cible = ou(suit = false, couches = listOf(couche(1, 45.0, 5.0), couche(2, 10.0, 100.0, visible = false)))
        assertEquals(CameraTarget.Bounds(4.9, 44.9, 5.1, 45.1), cible)
    }

    /**
     * Une couche dont l'emprise n'a pas ete calculee porte des zeros, et les garder etirerait le cadrage
     * jusqu'au golfe de Guinee - le point (0, 0).
     */
    @Test fun `une emprise a zero est ecartee`() {
        val vierge = couche(2, 45.0, 5.0).copy(west = 0.0, south = 0.0, east = 0.0, north = 0.0)
        assertEquals(
            CameraTarget.Bounds(4.9, 44.9, 5.1, 45.1),
            ou(suit = false, couches = listOf(couche(1, 45.0, 5.0), vierge)),
        )
    }

    // ---------- 4. sinon, la France ----------

    @Test fun `sans rien du tout, la France entiere`() {
        assertEquals(CameraPlacement.France, ou(suit = false))
        assertEquals(CameraPlacement.France, ou(suit = false, couches = listOf(couche(1, 45.0, 5.0, visible = false))))
    }
}
