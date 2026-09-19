package fr.lc4918.trailog.ui.poi

import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiGroup
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.poi.Poi
import fr.lc4918.trailog.poi.PoiCells
import fr.lc4918.trailog.poi.PoiSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les regles qui decident QUOI demander pour une vue : les cellules, leur ordre, et ce qu'on ecarte.
 *
 * Ce sont elles qui fixent le nombre de requetes envoyees aux services. Une regle relachee ici ne casse
 * rien - elle multiplie les appels en silence, et le service finit par refuser.
 */
class PoiLoadingTest {

    private fun box(w: Double, s: Double, e: Double, n: Double) = Bbox.of(w, s, e, n)
    private val tous = PoiGroup.entries.toSet()

    /** L'emprise demandee deborde l'ecran d'un cheveu : les marqueurs juste au bord sont deja la. */
    @Test fun `l'emprise demandee est a peine plus large que l'ecran`() {
        val vue = box(5.0, 45.0, 6.0, 46.0)
        val large = PoiLoading.grow(vue)
        assertEquals(4.95, large.west, 1e-9)
        assertEquals(6.05, large.east, 1e-9)
    }

    /** Un elargissement pres des poles ou de l'antimeridien ne doit pas sortir du monde. */
    @Test fun `l'elargissement reste dans les bornes du monde`() {
        val large = PoiLoading.grow(box(-179.0, -84.0, 179.0, 84.0))
        assertTrue(large.west >= -180.0 && large.east <= 180.0)
        assertTrue(large.south >= -85.0 && large.north <= 85.0)
    }

    /** Un ecran de ville : quelques cellules, et chaque groupe coche aupres de sa source. */
    @Test fun `une vue demande les cellules qu'elle touche`() {
        val vue = box(-2.48, 42.44, -2.42, 42.48)   // Logrono
        val plan = PoiLoading.plan(vue, setOf(PoiGroup.FOOD), true, emptyList(), 0)
        assertFalse(plan.capped)
        assertFalse(plan.away)
        assertTrue(plan.units.isNotEmpty())
        assertTrue(plan.units.all { it.group == PoiGroup.FOOD && it.source == PoiSource.OSM })
        assertEquals(PoiCells.covering(PoiLoading.grow(vue)).toSet(), plan.units.map { it.cell }.toSet())
    }

    /** Du centre vers les bords : ce qu'on regarde se remplit d'abord. */
    @Test fun `la cellule du centre passe en premier`() {
        val vue = box(1.30, 43.50, 1.60, 43.70)
        val plan = PoiLoading.plan(vue, setOf(PoiGroup.FOOD), true, emptyList(), 0)
        assertEquals(PoiCells.of(1.45, 43.60), plan.units.first().cell)
    }

    /** Une vue trop large s'arrete aux cellules les plus proches du centre, et le dit. */
    @Test fun `une vue trop large est bornee`() {
        val vue = box(0.0, 43.0, 2.0, 45.0)
        val plan = PoiLoading.plan(vue, setOf(PoiGroup.FOOD), true, emptyList(), 0)
        assertTrue(plan.capped)
        assertEquals(PoiLoading.MAX_CELLS, plan.units.map { it.cell }.distinct().size)
    }

    /** Loin de toute trace, rien n'est demande - et la carte peut le dire. */
    @Test fun `loin des traces, rien n'est demande`() {
        val traces = listOf(listOf(-1.5 to 46.0, -1.4 to 46.1))
        val plan = PoiLoading.plan(box(5.0, 45.0, 5.2, 45.2), tous, true, traces, 2000)
        assertTrue(plan.units.isEmpty())
        assertTrue(plan.away)
    }

    /** Le couloir borne les cellules : un long trajet vu de haut ne demande que celles qu'il traverse. */
    @Test fun `le couloir ne garde que les cellules que la trace traverse`() {
        val trace = listOf(1.05 to 43.55, 1.95 to 43.55)   // plein est, sur une rangee de cellules
        val vue = box(1.0, 43.0, 2.0, 44.0)
        val sans = PoiLoading.plan(vue, setOf(PoiGroup.FOOD), true, emptyList(), 0, maxCells = 1000)
        val avec = PoiLoading.plan(vue, setOf(PoiGroup.FOOD), true, listOf(trace), 1000, maxCells = 1000)
        assertTrue(avec.units.size < sans.units.size / 3)
        assertTrue(avec.units.all { it.cell.iy in 434..436 })
    }

    /** Rien de coche, rien a demander. */
    @Test fun `sans groupe, aucune unite`() {
        assertTrue(PoiLoading.plan(box(5.0, 45.0, 5.1, 45.1), emptySet(), true, emptyList(), 0).units.isEmpty())
    }

    // ---------- Ce que l'ecran montre ----------

    /** Un point d'interet disparu ne doit pas laisser son infobulle ouverte sur un marqueur absent. */
    @Test fun `l'infobulle se ferme si son point d'interet a disparu`() {
        val etat = PoiState()
        etat.show(listOf(poi("a"), poi("b")), pending = false, cache = false, missing = false)
        etat.selectById("a")
        assertEquals("a", etat.selected?.uuid)
        etat.show(listOf(poi("b")), pending = false, cache = false, missing = false)
        assertNull(etat.selected)
    }

    /** ... mais elle reste ouverte tant que son point est encore la. */
    @Test fun `l'infobulle survit a une arrivee qui garde son point`() {
        val etat = PoiState()
        etat.show(listOf(poi("a")), pending = true, cache = false, missing = false)
        etat.selectById("a")
        etat.show(listOf(poi("a"), poi("c")), pending = false, cache = false, missing = false)
        assertEquals("a", etat.selected?.uuid)
    }

    /** Eteindre la couche vide l'ecran. */
    @Test fun `eteindre la couche oublie ce qui etait montre`() {
        val etat = PoiState().apply { showLayer(true) }
        etat.show(listOf(poi("a")), pending = true, cache = true, missing = false)
        etat.select(poi("a"))
        etat.hide()
        assertTrue(etat.pois.isEmpty())
        assertNull(etat.selected)
        assertFalse(etat.loading)
        assertFalse(etat.fromCache)
    }

    /** Une couleur par groupe, et quatre distinctes : c'est le groupe qui se lit d'un coup d'oeil. */
    @Test fun `chaque groupe a sa couleur, et elles different`() {
        val couleurs = PoiGroup.entries.map { poiGroupColor(it) }
        assertEquals(PoiGroup.entries.size, couleurs.distinct().size)
        couleurs.forEach { assertTrue(it, Regex("^#[0-9A-Fa-f]{6}$").matches(it)) }
    }

    /** Trop dezoome : on ne charge pas, l'ecran le dit, et ce qui etait montre reste. */
    @Test fun `trop dezoome se signale sans vider la carte`() {
        val etat = PoiState().apply { showLayer(true) }
        etat.show(listOf(poi("a")), pending = false, cache = false, missing = false)
        etat.viewed(capped = true, away = false)
        etat.tooFar()
        assertTrue(etat.tooFar)
        assertFalse(etat.partial)
        assertEquals(1, etat.pois.size)
    }

    /** Ce que le chargeur sait se lit tel quel : attente, cache, manque. */
    @Test fun `les constats du chargeur se lisent tels quels`() {
        val etat = PoiState()
        etat.show(emptyList(), pending = true, cache = false, missing = true)
        assertTrue(etat.loading)
        assertTrue(etat.needsNetwork)
        assertFalse(etat.fromCache)
        etat.show(listOf(poi("a")), pending = false, cache = true, missing = false)
        assertFalse(etat.loading)
        assertTrue(etat.fromCache)
        assertFalse(etat.needsNetwork)
    }

    private fun poi(id: String) = Poi(id, "lieu $id", 45.0, 5.0, PoiCategory.HOTELS)
}
