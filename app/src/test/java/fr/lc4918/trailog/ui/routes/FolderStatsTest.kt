package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Totaux d'un dossier.
 *
 * Deux fautes possibles, et toutes deux passent inapercues : un total qui oublie les sous-dossiers ne
 * correspond a rien de ce que l'ecran montre - le dossier applique deja tout le reste a ce qu'il contient -,
 * et une duree partielle est plus PETITE que le temps reellement passe, ce qui se lit comme une sortie plus
 * rapide qu'elle ne l'a ete.
 */
class FolderStatsTest {

    private fun folder(id: Long, parent: Long? = null) = FolderEntity(id = id, name = "d$id", parentId = parent)

    private fun layer(
        id: Long, folderId: Long?, distance: Double = 0.0, ascent: Double = 0.0, descent: Double = 0.0,
        movingTime: Double? = null, hasLine: Boolean = true, hasPoints: Boolean = false,
    ) = LayerEntity(
        id = id, name = "c$id", folderId = folderId, geometryFile = "g$id",
        distance = distance, ascent = ascent, descent = descent, movingTime = movingTime,
        hasLine = hasLine, hasPoints = hasPoints,
    )

    /** Recursif comme la visibilite, la couleur et la suppression : un dossier repond de tout ce qu'il
     *  contient, sous-dossiers compris. */
    @Test fun `les sous-dossiers comptent dans le total`() {
        val folders = listOf(folder(1), folder(2, parent = 1), folder(3, parent = 2))
        val layers = listOf(
            layer(10, 1, distance = 1000.0, ascent = 100.0, descent = 50.0),
            layer(11, 2, distance = 2000.0, ascent = 200.0, descent = 150.0),
            layer(12, 3, distance = 4000.0, ascent = 400.0, descent = 350.0),
        )
        val s = folderStats(1, folders, layers)
        assertEquals(3, s.layers)
        assertEquals(7000.0, s.distance, 0.001)
        assertEquals(700.0, s.ascent, 0.001)
        assertEquals(550.0, s.descent, 0.001)
    }

    @Test fun `un dossier voisin n'entre pas dans le compte`() {
        val folders = listOf(folder(1), folder(2))
        val layers = listOf(layer(10, 1, distance = 1000.0), layer(11, 2, distance = 9000.0))
        assertEquals(1000.0, folderStats(1, folders, layers).distance, 0.001)
    }

    /** Une couche de marqueurs n'a ni distance ni denivele : elle est comptee comme couche, pas comme trace. */
    @Test fun `les couches de marqueurs se comptent a part`() {
        val folders = listOf(folder(1))
        val layers = listOf(
            layer(10, 1, distance = 1000.0),
            layer(11, 1, hasLine = false, hasPoints = true),
        )
        val s = folderStats(1, folders, layers)
        assertEquals(2, s.layers)
        assertEquals(1, s.tracks)
        assertEquals(1, s.markers)
        assertEquals(1000.0, s.distance, 0.001)
    }

    /** Additionner les seules durees connues donnerait un total plus petit que le temps reellement passe,
     *  sans que rien ne le dise : on prefere ne rien annoncer. */
    @Test fun `une seule trace sans horodatage retire la duree du total`() {
        val folders = listOf(folder(1))
        val avec = listOf(layer(10, 1, movingTime = 600.0), layer(11, 1, movingTime = 1200.0))
        assertEquals(1800.0, folderStats(1, folders, avec).movingTime!!, 0.001)

        val sans = avec + layer(12, 1, movingTime = null)
        assertNull(folderStats(1, folders, sans).movingTime)
    }

    @Test fun `un dossier vide ne totalise rien`() {
        val s = folderStats(1, listOf(folder(1)), emptyList())
        assertEquals(0, s.layers)
        assertEquals(0.0, s.distance, 0.001)
        assertNull("aucune trace : aucune duree", s.movingTime)
    }
}
