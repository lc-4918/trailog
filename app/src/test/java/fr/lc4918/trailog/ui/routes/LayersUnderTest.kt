package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce sur quoi porte une action de dossier : ses couches, celles de ses sous-dossiers comprises.
 *
 * Le risque est dans la recursivite, et il est muet : s'arreter aux couches directes laisserait, sous un
 * dossier qu'on vient de colorer d'un bloc, des sous-dossiers d'une autre couleur - c'est-a-dire
 * exactement ce que l'action cherchait a defaire. L'oeil du dossier et son cadrage en repondent aussi.
 */
class LayersUnderTest {

    private val racine = FolderEntity(id = 1, name = "Fait")
    private val sous = FolderEntity(id = 2, name = "2026", parentId = 1)
    private val petitFils = FolderEntity(id = 3, name = "Ete", parentId = 2)
    private val voisin = FolderEntity(id = 4, name = "Demo")
    private val folders = listOf(racine, sous, petitFils, voisin)

    private fun layer(id: Long, folderId: Long?) =
        LayerEntity(id = id, name = "trace $id", folderId = folderId, geometryFile = "t$id.geojson")

    @Test fun `les couches des sous-dossiers, a toute profondeur, en sont`() {
        val layers = listOf(layer(10, 1), layer(11, 2), layer(12, 3))
        assertEquals(listOf(10L, 11L, 12L), layersUnder(1, folders, layers).map { it.id })
    }

    /** Un dossier voisin et la racine (folderId nul) ne suivent pas : colorer un dossier ne doit pas
     *  deteindre sur la bibliotheque entiere. */
    @Test fun `les couches d'ailleurs n'en sont pas`() {
        val layers = listOf(layer(10, 1), layer(20, 4), layer(30, null))
        assertEquals(listOf(10L), layersUnder(1, folders, layers).map { it.id })
    }

    /** Depuis un sous-dossier, l'action ne remonte pas vers son parent. */
    @Test fun `l'action ne remonte pas l'arborescence`() {
        val layers = listOf(layer(10, 1), layer(11, 2), layer(12, 3))
        assertEquals(listOf(11L, 12L), layersUnder(2, folders, layers).map { it.id })
    }

    @Test fun `un dossier vide ne porte aucune couche`() {
        assertTrue(layersUnder(4, folders, listOf(layer(10, 1))).isEmpty())
    }
}
