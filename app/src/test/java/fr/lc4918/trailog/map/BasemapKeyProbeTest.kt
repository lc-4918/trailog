package fr.lc4918.trailog.map

import fr.lc4918.trailog.data.db.ProviderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La sonde qui decide si le fond par defaut peut encore servir.
 *
 * Ce qu'elle protege : depuis que le fond de demarrage est Mapbox Outdoors, tout le premier ecran depend
 * d'une cle tierce a quota. Sans repli, le jour ou elle tombe, l'application s'ouvre sur une carte GRISE
 * sans que rien ne dise pourquoi.
 *
 * Ce qu'elle ne doit surtout pas faire : basculer sur OSM parce que le train est passe sous un tunnel.
 */
class BasemapKeyProbeTest {

    private fun fond(id: String, gabarit: String, type: String = "XYZ") =
        ProviderEntity(id, id, "Monde", type, gabarit)

    // ---------- Qui merite d'etre sonde ----------

    /** Sans {KEY} dans son gabarit, il n'y a rien a refuser : sonder OSM couterait une requete a chaque
     *  demarrage pour n'apprendre jamais rien. */
    @Test fun `un fond sans cle ne se sonde pas`() {
        assertFalse(BasemapKeyProbe.needsKey(fond("osm", "https://tile.osm.org/{z}/{x}/{y}.png")))
        assertTrue(BasemapKeyProbe.needsKey(fond("mapbox", "https://api.mapbox.com/...{z}/{x}/{y}?access_token={KEY}")))
    }

    /** Un MBTiles est un fichier local : il n'y a pas de service pour refuser quoi que ce soit. */
    @Test fun `un fond local ne se sonde pas`() {
        assertFalse(BasemapKeyProbe.needsKey(fond("local", "/sdcard/x.mbtiles{KEY}", type = "MBTILES")))
    }

    // ---------- Le verdict ----------

    /** Les quatre facons dont un service dit non a une cle : absente, invalide, impayee, epuisee. */
    @Test fun `un refus explicite se reconnait`() {
        listOf(401, 402, 403, 429).forEach {
            assertEquals("statut $it", BasemapKeyProbe.Verdict.REFUSED, BasemapKeyProbe.verdictOf(it, 0))
        }
    }

    /**
     * Et surtout PAS le reste. Une panne reseau, un delai depasse, un 500 laissent le fond choisi en
     * place : hors ligne, les tuiles deja en cache s'affichent encore, et basculer sur OSM n'apporterait
     * qu'une carte tout aussi vide.
     */
    @Test fun `une panne ne fait pas changer de fond`() {
        listOf(0, 500, 502, 504).forEach {
            assertEquals("statut $it", BasemapKeyProbe.Verdict.UNKNOWN, BasemapKeyProbe.verdictOf(it, 0))
        }
    }

    /**
     * Un 404 non plus : c'est une tuile absente, pas une cle morte. On ne change pas de fond entier pour
     * un trou de couverture.
     */
    @Test fun `une tuile absente n'est pas une cle morte`() {
        assertEquals(BasemapKeyProbe.Verdict.UNKNOWN, BasemapKeyProbe.verdictOf(404, 0))
    }

    @Test fun `une tuile servie vaut acceptation`() {
        assertEquals(BasemapKeyProbe.Verdict.OK, BasemapKeyProbe.verdictOf(200, 2048))
    }

    /** Un 200 au corps vide n'est pas une tuile : dans le doute, on ne touche a rien. */
    @Test fun `un corps vide ne prouve rien`() {
        assertEquals(BasemapKeyProbe.Verdict.UNKNOWN, BasemapKeyProbe.verdictOf(200, 0))
    }
}
