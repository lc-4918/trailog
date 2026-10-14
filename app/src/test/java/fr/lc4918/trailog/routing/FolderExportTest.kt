package fr.lc4918.trailog.routing

import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ce qu'emporte l'export d'un dossier : ses couches et celles de ses sous-dossiers, sous des chemins qui ne se heurtent pas. */
class FolderExportTest {

    private fun folder(id: Long, name: String, parentId: Long? = null, sortOrder: Int = 0) =
        FolderEntity(id = id, name = name, parentId = parentId, sortOrder = sortOrder)

    private fun layer(id: Long, name: String, folderId: Long?, hasLine: Boolean = true, sortOrder: Int = 0) =
        LayerEntity(id = id, name = name, folderId = folderId, geometryFile = "$id.geojson", hasLine = hasLine, sortOrder = sortOrder)

    private fun paths(entries: List<FolderExport.Entry>) = entries.map { it.path }

    @Test fun `les traces du dossier et de ses sous-dossiers sont rangees comme dans l'arborescence`() {
        val folders = listOf(folder(1, "Pyrenees"), folder(2, "Ete 2026", parentId = 1), folder(3, "Autre"))
        val layers = listOf(
            layer(10, "Boucle du lac", 1), layer(11, "Col d'Aspin", 2), layer(12, "Hors dossier", 3), layer(13, "Racine", null),
        )
        assertEquals(
            listOf("Boucle-du-lac.gpx", "Ete-2026/Col-d-Aspin.gpx"),
            paths(FolderExport.entries(1, folders, layers, "gpx")),
        )
    }

    @Test fun `une couche de marqueurs seuls est exportee comme une trace`() {
        val layers = listOf(layer(10, "Refuges", 1, hasLine = false), layer(11, "Trace", 1, sortOrder = 1))
        assertEquals(
            listOf("Refuges.geojson", "Trace.geojson"),
            paths(FolderExport.entries(1, listOf(folder(1, "D")), layers, "geojson")),
        )
    }

    @Test fun `deux couches de meme nom ne s'ecrasent pas`() {
        val layers = listOf(layer(10, "Boucle", 1, sortOrder = 0), layer(11, "boucle", 1, sortOrder = 1), layer(12, "Boucle", 1, sortOrder = 2))
        assertEquals(
            listOf("Boucle.gpx", "boucle-2.gpx", "Boucle-3.gpx"),
            paths(FolderExport.entries(1, listOf(folder(1, "D")), layers, "gpx")),
        )
    }

    @Test fun `deux sous-dossiers de meme nom restent separes`() {
        val folders = listOf(folder(1, "D"), folder(2, "Sud", 1, sortOrder = 0), folder(3, "Sud", 1, sortOrder = 1))
        val layers = listOf(layer(10, "A", 2), layer(11, "A", 3))
        assertEquals(listOf("Sud/A.gpx", "Sud-2/A.gpx"), paths(FolderExport.entries(1, folders, layers, "gpx")))
    }

    @Test fun `un nom fait de caracteres interdits donne un nom de repli`() {
        val folders = listOf(folder(1, "D"), folder(2, "///", 1))
        val layers = listOf(layer(10, "???", 2))
        assertEquals(listOf("dossier/itineraire.gpx"), paths(FolderExport.entries(1, folders, layers, "gpx")))
    }

    @Test fun `un dossier sans couche ne donne rien`() {
        assertTrue(FolderExport.entries(1, listOf(folder(1, "D")), emptyList(), "gpx").isEmpty())
    }

    @Test fun `un cycle de dossiers ne boucle pas`() {
        // Ne devrait pas exister (le deplacement l'interdit), mais l'export ne doit pas se figer s'il existe.
        val folders = listOf(folder(1, "A", parentId = 2), folder(2, "B", parentId = 1))
        FolderExport.entries(1, folders, listOf(layer(10, "T", 1)), "gpx")
    }
}
