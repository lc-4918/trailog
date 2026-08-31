package fr.lc4918.trailog.ui.poi

import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.domain.model.PoiGroup
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.poi.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les regles qui decident QUAND redemander des points d'interet au service.
 *
 * Ce sont elles, et non le delai d'attente, qui tiennent les appels loin du quota : un deplacement de
 * carte emet des dizaines d'evenements, et la carte bouge sans arret. Une regle relachee ici ne casse
 * rien - elle epuise le quota horaire en silence, et les points d'interet cessent d'arriver.
 */
class PoiLoadingTest {

    private val tousFiltres = PoiFilters()

    private fun box(w: Double, s: Double, e: Double, n: Double) = Bbox.of(w, s, e, n)

    /**
     * L'emprise demandee deborde l'ecran : c'est ce qui rend gratuits les petits deplacements.
     *
     * Mais d'un CHEVEU : la marge a ete ramenee de 0,25 a 0,05. A 0,25, l'emprise faisait une fois et
     * demie l'ecran dans chaque dimension, soit 2,25 fois sa surface - donc deux fois plus de lieux a
     * faire tenir sous le plafond d'une requete, pour une marge dont on ne profitait qu'en se deplacant
     * de peu.
     */
    @Test fun `l'emprise demandee est plus large que l'ecran`() {
        val vue = box(5.0, 45.0, 6.0, 46.0)
        val large = PoiLoading.grow(vue)
        assertTrue(large.west < vue.west && large.east > vue.east)
        assertTrue(large.south < vue.south && large.north > vue.north)
        assertEquals(4.95, large.west, 1e-9)
        assertEquals(6.05, large.east, 1e-9)
    }

    /** La surface demandee ne doit pas depasser d'un tiers celle de l'ecran : au-dela, on fait travailler
     *  le service pour des lieux qu'on ne montrera pas. */
    @Test fun `l'emprise demandee reste proche de la surface de l'ecran`() {
        val vue = box(5.0, 45.0, 6.0, 46.0)
        val large = PoiLoading.grow(vue)
        val rapport = ((large.east - large.west) * (large.north - large.south)) /
            ((vue.east - vue.west) * (vue.north - vue.south))
        assertTrue("surface demandee : ${rapport}x l'ecran", rapport < 1.33)
    }

    /** Un elargissement pres des poles ou de l'antimeridien ne doit pas sortir du monde. */
    @Test fun `l'elargissement reste dans les bornes du monde`() {
        val large = PoiLoading.grow(box(-179.0, -84.0, 179.0, 84.0))
        assertTrue(large.west >= -180.0 && large.east <= 180.0)
        assertTrue(large.south >= -85.0 && large.north <= 85.0)
    }

    /** Rien de charge : il faut demander. */
    @Test fun `le premier affichage demande toujours`() {
        assertTrue(PoiState().needsLoad(box(5.0, 45.0, 6.0, 46.0), tousFiltres, osm = true, now = 0L))
    }

    /**
     * Le coeur de l'economie de requetes : tant que la vue reste DANS ce qui a ete charge, on ne redemande
     * rien. Un frisson de la carte ne coute alors pas un appel.
     */
    @Test fun `une vue contenue dans le charge ne redemande rien`() {
        val etat = PoiState()
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, emptyList(), complete = true)
        assertFalse(etat.needsLoad(box(5.0, 45.0, 6.0, 46.0), tousFiltres, osm = true, now = 0L))
        assertFalse("la meme vue exactement", etat.needsLoad(box(4.0, 44.0, 7.0, 47.0), tousFiltres, osm = true, now = 0L))
    }

    /** Des que la vue deborde, ne serait-ce que d'un cote, il manque des points d'interet a l'ecran. */
    @Test fun `une vue qui deborde redemande`() {
        val etat = PoiState()
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, emptyList(), complete = true)
        assertTrue("vers l'ouest", etat.needsLoad(box(3.0, 45.0, 6.0, 46.0), tousFiltres, osm = true, now = 0L))
        assertTrue("vers le nord", etat.needsLoad(box(5.0, 45.0, 6.0, 48.0), tousFiltres, osm = true, now = 0L))
        assertTrue("dezoome", etat.needsLoad(box(0.0, 40.0, 10.0, 50.0), tousFiltres, osm = true, now = 0L))
    }

    /** Eteindre la couche jette tout : garder des marqueurs invisibles n'apporte rien, et le geste qui la
     *  rallume justifie sa requete. */
    @Test fun `eteindre la couche oublie ce qui etait charge`() {
        val etat = PoiState()
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, listOf(poi("a")), complete = true)
        etat.select(poi("a"))
        etat.hide()
        assertTrue(etat.pois.isEmpty())
        assertNull(etat.selected)
        assertTrue("le charge doit etre oublie aussi", etat.needsLoad(box(5.0, 45.0, 6.0, 46.0), tousFiltres, osm = true, now = 0L))
    }

    /** Un point d'interet disparu du dernier chargement ne doit pas laisser son infobulle ouverte : elle
     *  decrirait un marqueur qui n'est plus sur la carte. */
    @Test fun `l'infobulle se ferme si son point d'interet a disparu`() {
        val etat = PoiState()
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, listOf(poi("a"), poi("b")), complete = true)
        etat.selectById("a")
        assertEquals("a", etat.selected?.uuid)
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, listOf(poi("b")), complete = true)
        etat.dropSelectionIfGone()
        assertNull(etat.selected)
    }

    /** ... mais elle reste ouverte tant que son point est encore la. */
    @Test fun `l'infobulle survit a un rechargement qui garde son point`() {
        val etat = PoiState()
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, listOf(poi("a")), complete = true)
        etat.selectById("a")
        etat.publish(box(4.0, 44.0, 8.0, 47.0), tousFiltres, listOf(poi("a"), poi("c")), complete = true)
        etat.dropSelectionIfGone()
        assertEquals("a", etat.selected?.uuid)
    }

    /** Une couleur par groupe, et quatre distinctes : c'est le groupe qui se lit d'un coup d'oeil sur la
     *  carte, la categorie s'annonce dans l'infobulle. */
    @Test fun `chaque groupe a sa couleur, et elles different`() {
        val couleurs = PoiGroup.entries.map { poiGroupColor(it) }
        assertEquals(PoiGroup.entries.size, couleurs.distinct().size)
        couleurs.forEach { assertTrue(it, Regex("^#[0-9A-Fa-f]{6}$").matches(it)) }
    }

    /**
     * Changer les filtres invalide le charge, meme si la vue n'a pas bouge : la reponse precedente ne
     * portait pas les memes lieux. Sans cela, decocher une categorie n'aurait aucun effet visible tant
     * qu'on ne deplace pas la carte.
     */
    @Test fun `changer les filtres force un rechargement`() {
        val etat = PoiState()
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, listOf(poi("a")), complete = true)
        val autres = tousFiltres.toggle(PoiCategory.BARS)
        assertTrue(etat.needsLoad(box(5.0, 45.0, 6.0, 46.0), autres, osm = true, now = 0L))
        assertFalse("les memes filtres ne forcent rien", etat.needsLoad(box(5.0, 45.0, 6.0, 46.0), tousFiltres, osm = true, now = 0L))
    }

    /**
     * **Basculer "Completer avec OpenStreetMap" force un rechargement**, et ce cas vient du terrain.
     *
     * Le reglage ajoute une source entiere a la requete, mais il n'entrait ni dans la cle de l'effet ni
     * dans cette decision : la vue etait deja tenue pour chargee, et l'allumer ne changeait donc rien
     * a l'ecran tant qu'on ne deplacait pas la carte. On restait devant une carte inchangee en croyant
     * le reglage sans effet - c'est exactement ce qui a ete rapporte.
     */
    @Test fun `basculer le complement OSM force un rechargement`() {
        val etat = PoiState()
        val vue = box(5.0, 45.0, 6.0, 46.0)
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, listOf(poi("a")), osmComplement = false,
            complete = true)
        assertFalse("meme reglage, rien a refaire", etat.needsLoad(vue, tousFiltres, osm = false, now = 0L))
        assertTrue("l'allumer redemande tout", etat.needsLoad(vue, tousFiltres, osm = true, now = 0L))
    }

    /** Et dans l'autre sens : l'eteindre aussi, la reponse portait des lieux qu'on ne veut plus. */
    @Test fun `eteindre le complement OSM force aussi un rechargement`() {
        val etat = PoiState()
        val vue = box(5.0, 45.0, 6.0, 46.0)
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, listOf(poi("a")), osmComplement = true,
            complete = true)
        assertTrue(etat.needsLoad(vue, tousFiltres, osm = false, now = 0L))
    }

    /** Trop dezoome : on ne charge pas, et l'ecran le dit plutot que de laisser une carte vide.
     *
     *  La couche allumee, et c'est indispensable : l'avertissement n'est du qu'a celui qui vient de la
     *  demander (cf. PoiStateTest, "le message de zoom ne revient pas au dezoom suivant"). */
    @Test fun `trop dezoome se signale et n'efface pas la marque de chargement`() {
        val etat = PoiState().apply { showLayer(true) }
        etat.beginLoad()
        etat.tooFar()
        assertTrue(etat.tooFar)
        assertFalse(etat.loading)
    }

    /** Des points venus du cache se signalent : une liste incomplete ne doit pas passer pour une reponse
     *  fraiche du service. */
    @Test fun `les points du cache se declarent comme tels`() {
        val etat = PoiState()
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, listOf(poi("a")), cache = true, complete = true)
        assertTrue(etat.fromCache)
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, listOf(poi("a")), complete = true)
        assertFalse(etat.fromCache)
    }

    /**
     * Rien a montrer ET pas de reseau : la zone n'a peut-etre aucun point d'interet, mais on n'en sait
     * rien. Le dire vaut mieux que de laisser croire a une region sans un seul cafe.
     */
    @Test fun `sans reseau et sans rien a montrer, la connexion se reclame`() {
        val etat = PoiState()
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, emptyList(), offline = true, complete = true)
        assertTrue(etat.needsNetwork)
        assertFalse("ce n'est pas le cas du cache", etat.fromCache)
    }

    /** Une zone reellement vide, avec du reseau, ne reclame rien : c'est une reponse, pas une panne. */
    @Test fun `une zone vide avec du reseau ne reclame rien`() {
        val etat = PoiState()
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, emptyList(), complete = true)
        assertFalse(etat.needsNetwork)
    }

    /** Les trois messages s'excluent : trop dezoome l'emporte, on ne saurait pas encore s'il y a du
     *  reseau puisqu'on n'a rien demande. */
    @Test fun `trop dezoome efface la reclamation de connexion`() {
        val etat = PoiState().apply { showLayer(true) }
        etat.publish(box(4.0, 44.0, 7.0, 47.0), tousFiltres, emptyList(), offline = true, complete = true)
        etat.tooFar()
        assertTrue(etat.tooFar)
        assertFalse(etat.needsNetwork)
    }

    private fun poi(id: String) = Poi(id, "lieu $id", 45.0, 5.0, PoiCategory.HOTELS)
}
