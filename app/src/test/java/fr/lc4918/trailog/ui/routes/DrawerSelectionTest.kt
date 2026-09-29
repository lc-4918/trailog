package fr.lc4918.trailog.ui.routes

import androidx.compose.ui.state.ToggleableState
import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La selection multiple du menu lateral, et ce qu'elle supprime (cf. DrawerSelection, deletionPlan). */
class DrawerSelectionTest {

    // Voyages (1) > Pyrenees (2) > GR10 (couche 20) ; Voyages > couche 10 ; a la racine : couche 30.
    private val dossiers = listOf(
        FolderEntity(id = 1, name = "Voyages"),
        FolderEntity(id = 2, name = "Pyrenees", parentId = 1),
    )
    private fun couche(id: Long, dossier: Long?) = LayerEntity(id = id, name = "c$id", folderId = dossier, geometryFile = "$id")
    private val couches = listOf(couche(10, 1), couche(20, 2), couche(30, null))

    // ---------- Etat ----------

    /** L'appui long ouvre la selection, l'element touche coche. */
    @Test fun `l'appui long ouvre la selection sur l'element touche`() {
        val s = DrawerSelection()
        assertFalse(s.active)
        s.start("layer" to 10L)
        assertTrue(s.active)
        assertEquals(setOf("layer" to 10L), s.selected)
    }

    /** Selection deja ouverte : un autre appui long coche ou decoche, sans repartir de zero. */
    @Test fun `un appui long en selection coche ou decoche`() {
        val s = DrawerSelection()
        s.start("layer" to 10L)
        s.start("folder" to 1L)
        assertEquals(setOf("layer" to 10L, "folder" to 1L), s.selected)
        s.start("layer" to 10L)
        assertEquals(setOf("folder" to 1L), s.selected)
    }

    @Test fun `la case de l'en-tete coche tout, puis decoche tout`() {
        val s = DrawerSelection()
        val tous = allTreeItems(dossiers, couches)
        s.start("layer" to 10L)
        assertEquals(ToggleableState.Indeterminate, s.allState(tous))
        s.toggleAll(tous)
        assertEquals(ToggleableState.On, s.allState(tous))
        assertEquals(5, s.selected.size)
        s.toggleAll(tous)
        assertEquals(ToggleableState.Off, s.allState(tous))
    }

    @Test fun `annuler referme la selection et vide les cases`() {
        val s = DrawerSelection()
        s.start("layer" to 10L)
        s.cancel()
        assertFalse(s.active)
        assertTrue(s.selected.isEmpty())
    }

    // ---------- Suppression ----------

    /** Un dossier coche emporte son contenu : ce qui est dedans, meme coche, n'est pas supprime a part. */
    @Test fun `ce qui est dans un dossier coche part avec lui`() {
        val sel = setOf("folder" to 1L, "folder" to 2L, "layer" to 20L, "layer" to 10L)
        val (d, c) = deletionPlan(sel, dossiers, couches)
        assertEquals(listOf(1L), d.map { it.id })
        assertTrue(c.isEmpty())
    }

    @Test fun `les couches hors des dossiers coches sont supprimees une a une`() {
        val sel = setOf("folder" to 2L, "layer" to 10L, "layer" to 30L, "layer" to 20L)
        val (d, c) = deletionPlan(sel, dossiers, couches)
        assertEquals(listOf(2L), d.map { it.id })
        assertEquals(listOf(10L, 30L), c.map { it.id })
    }

    /** Une boucle dans les parents (base abimee) ne fait pas tourner le calcul sans fin. */
    @Test fun `une boucle dans les dossiers ne bloque pas le calcul`() {
        val boucle = listOf(FolderEntity(id = 5, name = "a", parentId = 6), FolderEntity(id = 6, name = "b", parentId = 5))
        val (d, _) = deletionPlan(setOf("folder" to 5L), boucle, emptyList())
        assertEquals(listOf(5L), d.map { it.id })
    }

    // ---------- Fond d'appui des noms (infobulle) ----------

    /** Le fond gris ne parait qu'a l'ouverture : ni infobulle ouverte, ni appui qui vient d'en fermer une. */
    @Test fun `le fond d'appui ne parait que pour ouvrir une infobulle`() {
        val b = TooltipHolder()
        assertTrue("rien d'ouvert : l'appui peut ouvrir", b.pressFeedback)
        b.onPress(); b.onLabelClick("a")
        assertFalse("une infobulle ouverte : un appui la fermera", b.pressFeedback)
        b.onPress()
        assertFalse("l'appui qui ferme reste sans fond jusqu'au bout", b.pressFeedback)
        b.onLabelClick("b")
        assertEquals("et il n'ouvre rien", null, b.open)
        b.onPress()
        assertTrue("l'appui suivant peut de nouveau ouvrir", b.pressFeedback)
    }
}
