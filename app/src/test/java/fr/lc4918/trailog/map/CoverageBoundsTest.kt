package fr.lc4918.trailog.map

import fr.lc4918.trailog.data.seed.Providers
import fr.lc4918.trailog.map.offline.Bbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Emprises des fonds nationaux. Une faute ici ne casse rien a la compilation : elle envoie la carte
 *  au mauvais endroit, ou l'y laisse alors qu'il n'y a rien a voir. */
class CoverageBoundsTest {
    private val pays = Providers.defaults().filter { it.groupName == "Pays" }

    @Test fun `chaque fond national declare son emprise`() {
        pays.forEach { assertTrue("${it.id} (${it.name}) sans emprise", CoverageBounds.of(it) != null) }
    }

    /** L'inverse aussi : une emprise orpheline signale un fond renomme ou retire. */
    @Test fun `aucune emprise ne vise un fond inexistant`() {
        assertEquals(emptySet<String>(), CoverageBounds.ids() - pays.map { it.id }.toSet())
    }

    @Test fun `les bornes sont ordonnees et dans le monde`() {
        pays.forEach {
            val b = CoverageBounds.of(it)!!
            assertTrue("${it.id} : west >= east", b.west < b.east)
            assertTrue("${it.id} : south >= north", b.south < b.north)
            assertTrue("${it.id} : longitudes hors monde", b.west >= -180.0 && b.east <= 180.0)
            // Web Mercator ne va pas au-dela : une borne plus haute ne serait jamais atteignable.
            assertTrue("${it.id} : latitudes hors Mercator", b.south >= -85.05 && b.north <= 85.05)
        }
    }

    /** Une emprise a la fois assez large pour englober le pays et assez etroite pour que le recadrage
     *  ait un sens : couvrir un quart de l'Europe reviendrait a ne pas recadrer. */
    @Test fun `les emprises ont une taille plausible`() {
        pays.forEach {
            val b = CoverageBounds.of(it)!!
            val w = b.east - b.west
            val h = b.north - b.south
            assertTrue("${it.id} : emprise minuscule ($w x $h)", w > 0.5 && h > 0.5)
            assertTrue("${it.id} : emprise demesuree ($w x $h)", w < 40.0 && h < 30.0)
        }
    }

    /** Un fond hors groupe "Pays" ne doit jamais declencher de recadrage, meme si son id collisionne. */
    @Test fun `un fond hors groupe Pays n a pas d emprise`() {
        val osm = Providers.defaults().first { it.id == "osm" }
        assertNull(CoverageBounds.of(osm))
    }

    @Test fun `un fond inconnu n a pas d emprise`() {
        assertNull(CoverageBounds.of("fond_ajoute_par_l_utilisateur"))
    }

    @Test fun `l intersection reconnait le chevauchement`() {
        val pologne = CoverageBounds.of("pl")!!
        val varsovie = Bbox.of(20.9, 52.1, 21.1, 52.3)
        val grenoble = Bbox.of(5.6, 45.1, 5.8, 45.3)
        assertTrue(CoverageBounds.intersects(pologne, varsovie))
        assertFalse(CoverageBounds.intersects(pologne, grenoble))
    }

    /** Bornes incluses : deux zones qui se touchent par un bord partagent bien une bande de carte. */
    @Test fun `l intersection inclut les bords`() {
        val a = Bbox(0.0, 0.0, 10.0, 10.0)
        assertTrue(CoverageBounds.intersects(a, Bbox(10.0, 0.0, 20.0, 10.0)))
        assertFalse(CoverageBounds.intersects(a, Bbox(10.001, 0.0, 20.0, 10.0)))
    }

    /** Cas qui a motive une emprise reelle plutot que politique : le service portugais s'arrete au
     *  continent, Madere et les Acores n'y sont pas. */
    @Test fun `le Portugal ne couvre ni Madere ni les Acores`() {
        val pt = CoverageBounds.of("pt")!!
        assertFalse("Madere", CoverageBounds.intersects(pt, Bbox.of(-17.3, 32.6, -16.6, 32.9)))
        assertFalse("Acores", CoverageBounds.intersects(pt, Bbox.of(-28.6, 38.3, -28.0, 38.6)))
        assertTrue("Serra da Estrela", CoverageBounds.intersects(pt, Bbox.of(-7.7, 40.2, -7.5, 40.4)))
    }
}
