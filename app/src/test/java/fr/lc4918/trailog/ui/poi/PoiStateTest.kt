package fr.lc4918.trailog.ui.poi

import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.poi.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que la couche des points d'interet dit d'elle-meme : le message de zoom, et l'attente.
 *
 * Trois etats qui se ressemblent a l'ecran et ne veulent pas dire la meme chose - trop loin, en train de
 * charger, charge - et dont les transitions ont ete signalees comme trompeuses a l'usage. Chacune est donc
 * verrouillee ici.
 */
class PoiStateTest {

    private val filtres = PoiFilters.of(null)
    private val lieu = Poi("1", "Fontaine", 45.2, 5.7, PoiCategory.WATER)

    /** La couche allumee. Elle ne s'allume plus par un interrupteur mais parce que le filtre retient
     *  quelque chose (cf. PoiFilters) : l'ecran le lui dit, et c'est ce que reproduit cet appel. */
    private fun couche() = PoiState().apply { showLayer(true) }

    /**
     * Le message de zoom se leve DES QUE le zoom est bon, sans attendre les points.
     *
     * Il ne se levait qu'a la publication - une demi-seconde d'attente plus une requete reseau plus tard -
     * et l'ecran continuait donc de reclamer un zoom qu'on venait de faire. On zoomait encore, croyant
     * n'etre jamais assez pres.
     */
    @Test fun `le message de zoom se leve avant les points`() {
        val poi = couche()
        poi.tooFar()
        assertTrue(poi.tooFar)
        poi.nearEnough()
        assertFalse("le message doit tomber tout de suite", poi.tooFar)
        assertTrue("et rien n'est encore charge", poi.pois.isEmpty())
    }

    /**
     * **Le message ne revient pas au dezoom suivant**, et ce cas vient du terrain.
     *
     * Il ne repondait qu'a la question du zoom, si bien qu'il resurgissait chaque fois que la vue
     * redevenait large - au moment precis ou l'on prend du recul pour se situer, c'est-a-dire quand on ne
     * cherche justement pas de point d'interet. Il devenait un decor, et un decor ne se lit plus.
     *
     * Il n'est du qu'a celui qui vient d'allumer la couche et a qui la carte ne repond rien. Zoomer assez
     * repond a la question, et elle ne se repose pas.
     */
    @Test fun `le message de zoom ne revient pas au dezoom suivant`() {
        val poi = couche()
        poi.tooFar()
        assertTrue("il se dit une fois", poi.tooFar)
        poi.nearEnough()
        poi.tooFar()
        assertFalse("mais plus jamais ensuite", poi.tooFar)
    }

    /** Rallumer la couche le REARME : c'est une nouvelle demande, et la carte ne lui repond toujours rien. */
    @Test fun `rallumer la couche rearme le message de zoom`() {
        val poi = couche()
        poi.nearEnough()
        poi.showLayer(false)
        poi.showLayer(true)
        poi.tooFar()
        assertTrue(poi.tooFar)
    }

    /** La couche eteinte, il n'y a rien a avertir : le message ne se leve pas, meme trop loin. */
    @Test fun `couche eteinte, pas de message de zoom`() {
        val poi = PoiState()
        poi.tooFar()
        assertFalse(poi.tooFar)
    }

    /**
     * L'oeil range la couche SANS toucher au filtre, et c'est toute sa raison d'etre : la poubelle decoche
     * tout, et revoir ce qu'on avait choisi demande alors de le recocher categorie par categorie.
     *
     * Les lieux deja recus restent en memoire : remettre la couche ne coute aucune requete.
     */
    @Test fun `mettre la couche de cote garde ses lieux`() {
        val poi = couche()
        poi.show(listOf(lieu), pending = false, cache = false, missing = false)
        poi.toggleMask()
        assertTrue("le filtre n'a pas bouge", poi.visible)
        assertFalse("mais rien n'est pose sur la carte", poi.showingMarkers)
        assertEquals("et les lieux sont gardes", listOf(lieu), poi.pois)
        poi.toggleMask()
        assertTrue(poi.showingMarkers)
    }

    /** L'infobulle decrirait un marqueur qui n'est plus la : elle se ferme avec la couche. */
    @Test fun `mettre la couche de cote ferme l'infobulle`() {
        val poi = couche()
        poi.show(listOf(lieu), pending = false, cache = false, missing = false)
        poi.selectById(lieu.uuid)
        poi.toggleMask()
        assertNull(poi.selected)
    }

    /**
     * La mise de cote est un REGLAGE, et l'allumage de la couche a lieu a chaque lancement : il ne doit
     * pas la lever, sans quoi l'oeil ferme se rouvrait tout seul au redemarrage. C'est cocher une categorie
     * qui la leve, dans le ViewModel qui l'enregistre.
     */
    @Test fun `rallumer la couche garde la mise de cote`() {
        val poi = couche()
        poi.toggleMask()
        poi.showLayer(false)
        poi.showLayer(true)
        assertTrue(poi.masked)
    }

    /** Le reglage relu au lancement pose la mise de cote, et ferme une infobulle qui n'aurait plus d'epingle. */
    @Test fun `la mise de cote se restaure depuis le reglage`() {
        val poi = couche()
        poi.show(listOf(lieu), pending = false, cache = false, missing = false)
        poi.selectById(lieu.uuid)
        poi.restoreMask(true)
        assertTrue(poi.masked)
        assertFalse(poi.showingMarkers)
        assertNull(poi.selected)
        poi.restoreMask(false)
        assertTrue(poi.showingMarkers)
    }

    /** L'oeil rend la nouvelle valeur : c'est elle que l'appelant enregistre. */
    @Test fun `l'oeil rend la valeur a enregistrer`() {
        val poi = couche()
        assertTrue(poi.toggleMask())
        assertFalse(poi.toggleMask())
    }

    /** Decocher une categorie emporte l'infobulle ouverte sur l'un de ses lieux, en meme temps que son
     *  marqueur. */
    @Test fun `decocher une categorie ferme l'infobulle de ses lieux`() {
        val poi = couche()
        poi.show(listOf(lieu), pending = false, cache = false, missing = false)
        poi.selectById(lieu.uuid)
        poi.dropSelectionIfHidden(filtres.toggle(PoiCategory.WATER))
        assertNull(poi.selected)
    }
}
