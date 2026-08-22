package fr.lc4918.trailog.ui.routes

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ou se pose un element qu'on lache dans une arborescence.
 *
 * Ce calcul etait ecrit DEUX FOIS, mot pour mot : une fois pour le menu lateral (dossiers et couches),
 * une fois pour le catalogue des fonds (dossiers, fonds et composites). Les identifiants different, la
 * regle non. Une correction sur l'une aurait laisse l'autre en place, et le defaut ne se serait vu que
 * dans un seul des deux ecrans.
 */
class TreeReorderTest {

    private val fratrie = listOf("a", "b", "c")

    @Test fun `deposer avant la cible prend son rang`() {
        assertEquals(listOf("a", "x", "b", "c"),
            droppedOrder(fratrie, "x", targetIndex = 1, position = DropPosition.BEFORE))
    }

    @Test fun `deposer apres la cible prend le rang suivant`() {
        assertEquals(listOf("a", "b", "x", "c"),
            droppedOrder(fratrie, "x", targetIndex = 1, position = DropPosition.AFTER))
    }

    @Test fun `deposer avant le premier met en tete`() {
        assertEquals(listOf("x", "a", "b", "c"),
            droppedOrder(fratrie, "x", targetIndex = 0, position = DropPosition.BEFORE))
    }

    @Test fun `deposer apres le dernier met en queue`() {
        assertEquals(listOf("a", "b", "c", "x"),
            droppedOrder(fratrie, "x", targetIndex = 2, position = DropPosition.AFTER))
    }

    /** Lacher DANS un dossier ne dit rien du rang : l'element se range au bout de ce qu'il contient. */
    @Test fun `deposer dans un dossier met en queue`() {
        assertEquals(listOf("a", "b", "c", "x"),
            droppedOrder(fratrie, "x", targetIndex = 1, position = DropPosition.INTO))
    }

    /**
     * La cible introuvable n'est pas un cas theorique : on lache sur un dossier ferme, ou sur un voisin
     * qui vient d'etre supprime. Aller a la fin est la seule reponse qui ne perde pas l'element.
     */
    @Test fun `cible introuvable renvoie en queue`() {
        assertEquals(listOf("a", "b", "c", "x"),
            droppedOrder(fratrie, "x", targetIndex = -1, position = DropPosition.BEFORE))
        assertEquals(listOf("a", "b", "c", "x"),
            droppedOrder(fratrie, "x", targetIndex = -1, position = DropPosition.AFTER))
    }

    @Test fun `une fratrie vide accueille quand meme`() {
        assertEquals(listOf("x"),
            droppedOrder(emptyList(), "x", targetIndex = -1, position = DropPosition.INTO))
    }

    /** Le calcul ne connait pas le type des identifiants : c'est ce qui lui permet de servir aux deux
     *  arbres, l'un indexe par des entiers, l'autre par des chaines. */
    @Test fun `le meme calcul vaut pour les deux arbres`() {
        val couches = listOf(Triple("folder", 1L, 0), Triple("layer", 7L, 1))
        assertEquals(
            listOf(Triple("folder", 1L, 0), Triple("layer", 9L, 0), Triple("layer", 7L, 1)),
            droppedOrder(couches, Triple("layer", 9L, 0), targetIndex = 1, position = DropPosition.BEFORE),
        )
        val fonds = listOf(Triple("provider", "ign", 0), Triple("composite", "3", 1))
        assertEquals(
            listOf(Triple("provider", "ign", 0), Triple("composite", "3", 1), Triple("provider", "osm", 0)),
            droppedOrder(fonds, Triple("provider", "osm", 0), targetIndex = 1, position = DropPosition.AFTER),
        )
    }

    /** L'element depose ne doit jamais figurer deux fois : l'appelant le retire de la fratrie avant. */
    @Test fun `la fratrie recue ne contient pas l'element deplace`() {
        val sansB = fratrie.filterNot { it == "b" }
        assertEquals(listOf("a", "b", "c"),
            droppedOrder(sansB, "b", targetIndex = 1, position = DropPosition.BEFORE))
    }
}
