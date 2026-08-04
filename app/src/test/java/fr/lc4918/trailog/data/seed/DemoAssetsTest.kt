package fr.lc4918.trailog.data.seed

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.data.imp.LayerImporter
import fr.lc4918.trailog.domain.model.PropValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Le jeu de demonstration reellement livre dans les assets, relu par le vrai importeur.
 *
 * Ces cas ne verifient pas du code mais des DONNEES, et c'est voulu : une balise <photo> qui ne
 * correspond a aucun fichier, ou une photo oubliee dans les assets, ne casse ni la compilation ni le
 * demarrage. Elle donne un waypoint sans image sur une installation neuve -- et personne ne relance
 * une installation neuve en developpement.
 */
@RunWith(RobolectricTestRunner::class)
class DemoAssetsTest {
    private val ctx = ApplicationProvider.getApplicationContext<Application>()
    private val files = ctx.assets.list(DemoData.ASSET_DIR)?.toList().orEmpty()
    private val gpxName = files.first { it.endsWith(".gpx", ignoreCase = true) }
    private val gpx = ctx.assets.open("${DemoData.ASSET_DIR}/$gpxName").use { it.readBytes() }

    @Test fun `les assets portent un gpx et ses photos`() {
        assertEquals("un seul gpx attendu", 1, files.count { it.endsWith(".gpx", true) })
        assertTrue("aucune photo livree", files.any { it.endsWith(".jpg", true) })
    }

    /** Le coeur du garde-fou : chaque photo citee doit exister dans les assets. */
    @Test fun `chaque photo citee est livree`() {
        val cites = DemoData.photoNames(gpx.decodeToString())
        assertTrue("le gpx ne cite aucune photo", cites.isNotEmpty())
        cites.forEach { assertTrue("photo citee mais absente des assets : $it", it in files) }
    }

    /** L'inverse : une photo livree que personne ne cite alourdit l'apk pour rien. */
    @Test fun `aucune photo livree n est orpheline`() {
        val cites = DemoData.photoNames(gpx.decodeToString()).toSet()
        val orphelines = files.filter { it.endsWith(".jpg", true) } - cites
        assertEquals("photos livrees mais jamais citees", emptyList<String>(), orphelines)
    }

    /** Les noms d'assets restent en ASCII sans espace : les accents et apostrophes des noms d'origine
     *  traversent la chaine de build et le systeme de fichiers de facons trop variees pour qu'on parie
     *  dessus, et rien ne les affiche a l'utilisateur. */
    @Test fun `les noms de fichiers sont en ascii sans espace`() {
        files.forEach {
            assertTrue("nom d'asset a risque : $it", it.matches(Regex("[a-z0-9._-]+")))
        }
    }

    // ---------- Relu par le vrai importeur ----------

    private val parsed by lazy { LayerImporter.parse(gpx, gpxName) }

    @Test fun `le gpx de demo porte une trace et des waypoints`() {
        assertEquals(1, parsed.lines.size)
        assertTrue("trace trop courte pour une demo", parsed.lines[0].size > 100)
        assertTrue("pas de waypoints", parsed.points.size > 1)
    }

    /** Le nom de la couche vient du <name> de <metadata>, place avant celui de <author> chez Wikiloc.
     *  Si l'ordre changeait, la couche s'appellerait du nom de l'auteur. */
    @Test fun `la couche prend le nom de la trace et non celui de l auteur`() {
        assertEquals("EV1 Nantes-Hendaye", parsed.name)
    }

    @Test fun `les waypoints photographies portent bien leur image`() {
        val avecImage = parsed.points.filter { p -> p.props.values.any { it is PropValue.Image } }
        assertEquals(DemoData.photoNames(gpx.decodeToString()).size,
            avecImage.sumOf { p -> p.props.values.count { it is PropValue.Image } })
        // Premiere image = image de garde de l'infobulle.
        avecImage.forEach { assertEquals("image_1", it.pinnedImageKey) }
    }

    /** Les chemins du gpx livre sont des noms nus : c'est ce que DemoData.rewritePhotoPaths attend,
     *  et ce que l'import refuserait sous forme de <link href> relatif. */
    @Test fun `les images citees sont des noms de fichiers nus`() {
        parsed.points.flatMap { it.props.values }.filterIsInstance<PropValue.Image>().forEach {
            assertNotNull(it.path)
            assertTrue("chemin non nu : ${it.path}", !it.path.contains('/') && !it.path.contains(':'))
        }
    }
}
