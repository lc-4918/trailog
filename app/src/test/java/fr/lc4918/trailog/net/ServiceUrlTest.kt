package fr.lc4918.trailog.net

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Portee des URL de service (geocodeur, moteur d'itineraire). Decide si l'absence de connexion doit etre
 * signalee avant d'ouvrir une recherche ou de lancer une mesure. Compter une instance du reseau local comme
 * externe priverait de la fonction celui qui l'heberge et se trouve en wifi sans sortie Internet, c'est-a-dire
 * exactement le cas que l'auto-hebergement sert a couvrir.
 */
class ServiceUrlTest {

    @Test fun `les instances publiques exigent internet`() {
        assertTrue(ServiceUrl.needsInternet("https://photon.komoot.io/api"))
        assertTrue(ServiceUrl.needsInternet("https://valhalla1.openstreetmap.de/route"))
        assertTrue(ServiceUrl.needsInternet("https://geo.exemple.fr/api"))
    }

    @Test fun `une instance du reseau local n'exige pas internet`() {
        listOf(
            "http://192.168.1.10:2322/api",
            "http://10.0.0.5/api",
            "http://172.16.0.1/api",
            "http://172.31.255.254/api",
            "http://127.0.0.1:2322/api",
            "http://localhost:8002/route",
            "http://valhalla.local/route",
            "http://nas:2322/api",          // nom de machine seul, resolu sur le reseau local
        ).forEach { assertTrue("compte a tort comme externe : $it", !ServiceUrl.needsInternet(it)) }
    }

    /** 172.16.0.0/12 s'arrete a 172.31 : au-dela, l'adresse est publique et routable. */
    @Test fun `les adresses hors plages privees exigent internet`() {
        assertTrue(ServiceUrl.needsInternet("http://172.32.0.1/api"))
        assertTrue(ServiceUrl.needsInternet("http://172.15.0.1/api"))
        assertTrue(ServiceUrl.needsInternet("http://193.168.1.10/api"))
    }

    /** Prevenir a tort vaut mieux que laisser une requete echouer sans explication. */
    @Test fun `une url illisible est comptee comme externe`() {
        assertTrue(ServiceUrl.needsInternet("pas une url"))
        assertTrue(ServiceUrl.needsInternet(""))
    }
}
