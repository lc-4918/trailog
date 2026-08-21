package fr.lc4918.trailog.ui.poi

import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.poi.Poi
import org.junit.Assert.assertFalse
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

    private val vue = Bbox(west = 5.6, south = 45.1, east = 5.8, north = 45.3)
    private val filtres = PoiFilters.of(null, null)
    private val lieu = Poi("1", "Fontaine", 45.2, 5.7, PoiCategory.WATER)

    private fun couche() = PoiState().apply { toggle() }   // allumee

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

    /** L'attente survit a la premiere source : la seconde travaille encore. */
    @Test fun `publier n'eteint pas l'attente`() {
        val poi = couche()
        poi.beginLoad()
        poi.publish(vue, filtres, listOf(lieu))
        assertTrue("une source de plus est peut-etre en route", poi.loading)
        poi.endLoad()
        assertFalse(poi.loading)
    }

    /** Trop loin : plus d'attente, et le message reprend la main. */
    @Test fun `s'eloigner coupe l'attente`() {
        val poi = couche()
        poi.beginLoad()
        poi.tooFar()
        assertFalse(poi.loading)
        assertTrue(poi.tooFar)
    }

    /** Une vue deja chargee ne se redemande pas ; une vue qui deborde, si. */
    @Test fun `on ne redemande que ce qui deborde`() {
        val poi = couche()
        poi.publish(vue, filtres, listOf(lieu))
        val dedans = Bbox(west = 5.65, south = 45.15, east = 5.75, north = 45.25)
        assertFalse(poi.needsLoad(dedans, filtres))
        val dehors = Bbox(west = 5.65, south = 45.15, east = 5.95, north = 45.25)
        assertTrue(poi.needsLoad(dehors, filtres))
    }

    /** Eteindre la couche oublie tout, message et attente compris : la rallumer repart d'une page vierge. */
    @Test fun `eteindre la couche efface son etat`() {
        val poi = couche()
        poi.beginLoad()
        poi.publish(vue, filtres, listOf(lieu))
        poi.tooFar()
        poi.toggle()
        assertFalse(poi.visible)
        assertFalse(poi.tooFar)
        assertFalse(poi.loading)
        assertTrue(poi.pois.isEmpty())
        assertTrue("et la prochaine vue sera redemandee", poi.needsLoad(vue, filtres))
    }

    /**
     * Une emprise tronquee doit se redemander des qu'on bouge, ZOOM COMPRIS.
     *
     * C'est le pendant du message d'affichage partiel : la vue plus serree est contenue dans la precedente,
     * et la regle ordinaire repondait donc "rien a redemander". On restait avec les 250 lieux tires au
     * hasard de la vue large, dont une poignee seulement tombe dans le nouveau cadre - et le zoom, qui est
     * justement le geste qui rend la reponse complete, ne servait a rien.
     */
    @Test fun `une zone tronquee se redemande des qu'on zoome`() {
        val poi = couche()
        poi.publish(vue, filtres, listOf(lieu), incomplete = true)
        assertTrue(poi.partial)
        val plusServre = Bbox(west = 5.65, south = 45.15, east = 5.75, north = 45.25)
        assertTrue("le zoom doit redemander", poi.needsLoad(plusServre, filtres))
        assertTrue("un deplacement aussi", poi.needsLoad(vue, filtres))
    }

    /** Rendue en entier, la meme emprise ne se redemande pas : c'est ce qui tient les appels loin du quota. */
    @Test fun `une zone complete ne se redemande pas`() {
        val poi = couche()
        poi.publish(vue, filtres, listOf(lieu), incomplete = false)
        val plusServre = Bbox(west = 5.65, south = 45.15, east = 5.75, north = 45.25)
        assertFalse(poi.needsLoad(plusServre, filtres))
    }
}
