package fr.lc4918.trailog.ui.components

import fr.lc4918.trailog.data.db.BasemapFolderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Drag & drop du gestionnaire de fonds : arbre "father > child" et "mother" a la racine, lignes de 40 px
 * contigues, dans l'ordre d'affichage.
 *
 *   y=  0..40  father   (racine)
 *   y= 40..80  child    (dans father)
 *   y= 80..120 mother   (racine)
 */
class BasemapHoverTargetTest {
    private val father = BasemapFolderEntity(id = 1, name = "father", parentId = null)
    private val child = BasemapFolderEntity(id = 2, name = "child", parentId = 1)
    private val mother = BasemapFolderEntity(id = 3, name = "mother", parentId = null)
    private val folders = listOf(father, child, mother)

    private val rows = mapOf(
        ("folder" to "1") to BRowBounds(0f, 40f),
        ("folder" to "2") to BRowBounds(40f, 40f),
        ("folder" to "3") to BRowBounds(80f, 40f),
    )

    /** Centre de la ligne glissee amene sur [targetCenterY] : offset = cible - centre d'origine. */
    private fun dragFolderTo(id: String, targetCenterY: Float): BHoverTarget? {
        val b = rows["folder" to id]!!
        val offset = targetCenterY - (b.top + b.height / 2f)
        return basemapHoverTarget(BDragInfo("folder", id, offset), rows, folders, emptyList(), emptyList())
    }

    @Test fun `child se depose dans mother`() {
        // centre de mother (y=100) : milieu de la ligne -> zone INTO
        val t = dragFolderTo("2", 100f)
        assertEquals(BHoverTarget("folder", "3", BHoverZone.INTO), t)
    }

    @Test fun `child se depose a la racine avant father`() {
        val t = dragFolderTo("2", 5f)   // quart haut de father -> avant lui, donc a la racine
        assertEquals(BHoverTarget("folder", "1", BHoverZone.BEFORE), t)
    }

    @Test fun `father ne peut pas etre depose dans son propre enfant`() {
        assertNull(dragFolderTo("1", 60f))          // milieu de child -> INTO refuse
    }

    @Test fun `father ne peut pas etre depose dans sa descendance profonde`() {
        val grand = BasemapFolderEntity(id = 4, name = "grand", parentId = 2)   // father > child > grand
        val f2 = folders + grand
        val rows2 = rows + (("folder" to "4") to BRowBounds(120f, 40f))
        val b = rows2["folder" to "1"]!!
        val offset = 140f - (b.top + b.height / 2f)
        assertNull(basemapHoverTarget(BDragInfo("folder", "1", offset), rows2, f2, emptyList(), emptyList()))
    }

    @Test fun `un dossier ne peut pas etre depose dans lui-meme`() {
        // La ligne glissee est exclue de la recherche : rien sous elle -> aucune cible.
        assertNull(dragFolderTo("2", 60f))
    }
}
