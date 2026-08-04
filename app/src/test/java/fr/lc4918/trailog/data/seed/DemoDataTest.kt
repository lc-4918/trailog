package fr.lc4918.trailog.data.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Reecriture des chemins de photos du GPX de demonstration. Une faute ici ne casse rien : elle donne
 *  des waypoints sans image sur une installation neuve, ce que personne ne verra en developpement. */
class DemoDataTest {
    private val dir = File("/data/user/0/fr.lc4918.trailog/cache/demo_staging")

    @Test fun `un nom de fichier nu devient un chemin absolu`() {
        val gpx = "<wpt lat=\"45\" lon=\"5\"><photo>cascade.jpg</photo></wpt>"
        assertEquals(
            "<wpt lat=\"45\" lon=\"5\"><photo>${dir.absolutePath}/cascade.jpg</photo></wpt>",
            DemoData.rewritePhotoPaths(gpx, dir),
        )
    }

    @Test fun `plusieurs photos d un meme waypoint sont toutes reecrites`() {
        val out = DemoData.rewritePhotoPaths("<photo>a.jpg</photo><photo>b.jpg</photo>", dir)
        assertTrue(out.contains("${dir.absolutePath}/a.jpg"))
        assertTrue(out.contains("${dir.absolutePath}/b.jpg"))
    }

    /** L'import sait deja traiter ces formes : les reecrire les casserait. */
    @Test fun `un chemin absolu ou une URL est laisse tel quel`() {
        listOf("/sdcard/x.jpg", "file:///sdcard/x.jpg", "https://exemple.fr/x.jpg", "http://exemple.fr/x.jpg")
            .forEach {
                val gpx = "<photo>$it</photo>"
                assertEquals(gpx, DemoData.rewritePhotoPaths(gpx, dir))
            }
    }

    @Test fun `une balise photo vide est laissee telle quelle`() {
        assertEquals("<photo></photo>", DemoData.rewritePhotoPaths("<photo></photo>", dir))
    }

    /** Le GPX de demonstration doit employer <photo> et non <link href> : l'import rejette un href
     *  relatif (cf. LayerImporter.isResolvablePhoto). La reecriture ne doit donc pas y toucher, sous
     *  peine de laisser croire qu'un <link> relatif fonctionnerait. */
    @Test fun `un link href n est pas touche`() {
        val gpx = "<link href=\"cascade.jpg\"/>"
        assertEquals(gpx, DemoData.rewritePhotoPaths(gpx, dir))
    }

    @Test fun `un gpx sans photo est rendu intact`() {
        val gpx = "<gpx><wpt lat=\"45\" lon=\"5\"><name>Col</name></wpt></gpx>"
        assertEquals(gpx, DemoData.rewritePhotoPaths(gpx, dir))
    }

    // --- photoNames : ce qu'il faut extraire des assets ---

    @Test fun `les noms cites sont releves dans l ordre et sans doublon`() {
        val gpx = "<photo>a.jpg</photo><photo>b.jpg</photo><photo>a.jpg</photo>"
        assertEquals(listOf("a.jpg", "b.jpg"), DemoData.photoNames(gpx))
    }

    /** Rien a extraire pour ces formes : elles ne designent pas un asset. */
    @Test fun `les chemins absolus et les URL ne sont pas des noms a extraire`() {
        val gpx = "<photo>/sdcard/x.jpg</photo><photo>https://exemple.fr/y.jpg</photo><photo>z.jpg</photo>"
        assertEquals(listOf("z.jpg"), DemoData.photoNames(gpx))
    }

    @Test fun `un gpx sans photo ne cite aucun nom`() {
        assertEquals(emptyList<String>(), DemoData.photoNames("<gpx></gpx>"))
    }

    /** Les exports ne s'accordent pas sur la casse des balises. */
    @Test fun `la casse de la balise est ignoree`() {
        assertEquals(listOf("a.jpg"), DemoData.photoNames("<Photo>a.jpg</Photo>"))
    }
}
