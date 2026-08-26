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
     * Une zone qui vient d'echouer n'est pas redemandee au geste suivant.
     *
     * Le cas releve a Albi : Overpass refusait, l'emprise n'etait donc pas retenue, et chaque geste de
     * carte relancait aussitot la requete que le service venait de refuser - huit gestes, vingt-cinq
     * requetes, vingt-cinq refus. L'insistance faisait le bannissement.
     */
    @Test fun `une zone en echec attend avant d'etre redemandee`() {
        val poi = couche()
        poi.publish(vue, filtres, listOf(lieu), complete = false)
        poi.loadFailed(vue, now = 1_000L)
        assertFalse("juste apres l'echec, on ne redemande pas",
            poi.needsLoad(vue, filtres, osm = true, now = 1_000L + PoiLoading.RETRY_AFTER_FAIL_MS / 2))
        assertTrue("le delai passe, on retente",
            poi.needsLoad(vue, filtres, osm = true, now = 1_000L + PoiLoading.RETRY_AFTER_FAIL_MS + 1))
    }

    /** Le frein ne vaut que pour la zone en cause : ailleurs, la carte redemande normalement. */
    @Test fun `l'echec d'une zone n'en bloque pas une autre`() {
        val poi = couche()
        poi.loadFailed(vue, now = 0L)
        val ailleurs = Bbox.of(vue.east + 1.0, vue.south, vue.east + 2.0, vue.north)
        assertTrue(poi.needsLoad(ailleurs, filtres, osm = true, now = 1_000L))
    }

    /** Rallumer la couche est un geste : il merite une nouvelle tentative, sans attendre le delai. */
    @Test fun `rallumer la couche oublie l'echec`() {
        val poi = couche()
        poi.loadFailed(vue, now = 0L)
        poi.hide()
        assertTrue(poi.needsLoad(vue, filtres, osm = true, now = 1_000L))
    }

    /** L'attente survit a la premiere source : la seconde travaille encore. */
    @Test fun `publier n'eteint pas l'attente`() {
        val poi = couche()
        poi.beginLoad()
        poi.publish(vue, filtres, listOf(lieu), complete = true)
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
        poi.publish(vue, filtres, listOf(lieu), complete = true)
        val dedans = Bbox(west = 5.65, south = 45.15, east = 5.75, north = 45.25)
        assertFalse(poi.needsLoad(dedans, filtres, osm = true, now = 0L))
        val dehors = Bbox(west = 5.65, south = 45.15, east = 5.95, north = 45.25)
        assertTrue(poi.needsLoad(dehors, filtres, osm = true, now = 0L))
    }

    /** Eteindre la couche oublie tout, message et attente compris : la rallumer repart d'une page vierge.
     *  Elle s'eteint quand le filtre ne retient plus rien - tout masquer depuis la bulle de la carte. */
    @Test fun `eteindre la couche efface son etat`() {
        val poi = couche()
        poi.beginLoad()
        poi.publish(vue, filtres, listOf(lieu), complete = true)
        poi.tooFar()
        poi.showLayer(false)
        assertFalse(poi.visible)
        assertFalse(poi.tooFar)
        assertFalse(poi.loading)
        assertTrue(poi.pois.isEmpty())
        assertTrue("et la prochaine vue sera redemandee", poi.needsLoad(vue, filtres, osm = true, now = 0L))
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
        poi.publish(vue, filtres, listOf(lieu), incomplete = true, complete = false)
        assertTrue(poi.partial)
        val plusServre = Bbox(west = 5.65, south = 45.15, east = 5.75, north = 45.25)
        assertTrue("le zoom doit redemander", poi.needsLoad(plusServre, filtres, osm = true, now = 0L))
        assertTrue("un deplacement aussi", poi.needsLoad(vue, filtres, osm = true, now = 0L))
    }

    /** Rendue en entier, la meme emprise ne se redemande pas : c'est ce qui tient les appels loin du quota. */
    @Test fun `une zone complete ne se redemande pas`() {
        val poi = couche()
        poi.publish(vue, filtres, listOf(lieu), incomplete = false, complete = true)
        val plusServre = Bbox(west = 5.65, south = 45.15, east = 5.75, north = 45.25)
        assertFalse(poi.needsLoad(plusServre, filtres, osm = true, now = 0L))
    }
}
