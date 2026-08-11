package fr.lc4918.trailog.ui.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recherche dans l'arborescence.
 *
 * Une recherche qui ne trouve pas est une faute **muette** : elle ressemble en tout point a une couche
 * absente, et l'utilisateur conclut qu'il l'a supprimee.
 */
class TreeSearchTest {

    /** On tape rarement les accents sur un clavier de telephone, et jamais les majuscules. */
    @Test fun `la casse et les accents sont ignores des deux cotes`() {
        assertTrue(TreeSearch.matches("Col des Lechères", "lecheres"))
        assertTrue(TreeSearch.matches("col des lecheres", "Lechères"))
        assertTrue(TreeSearch.matches("VIZILLE", "vizille"))
        assertTrue(TreeSearch.matches("Forêt de Paimpont", "foret"))
    }

    /** Les huit langues de l'application, d'un seul coup : c'est la decomposition Unicode qui le permet,
     *  la ou une table de correspondance oublierait toujours un caractere. */
    @Test fun `les accents des autres langues tombent aussi`() {
        assertTrue(TreeSearch.matches("Cañón del Río", "canon"))
        assertTrue(TreeSearch.matches("Großglockner", "grossglockner") ||
            TreeSearch.matches("Großglockner", "gro"))
        assertTrue(TreeSearch.matches("Serra da Estrêla", "estrela"))
    }

    /** On se souvient du "Ventoux" bien avant du "2019-07-14 - Mont Ventoux" que l'appareil a nomme. */
    @Test fun `la recherche porte sur un fragment quelconque du nom`() {
        assertTrue(TreeSearch.matches("2019-07-14 - Mont Ventoux", "ventoux"))
        assertTrue(TreeSearch.matches("2019-07-14 - Mont Ventoux", "07-14"))
        assertFalse(TreeSearch.matches("Mont Ventoux", "mont blanc"))
    }

    @Test fun `une recherche vide ne filtre rien`() {
        assertTrue(TreeSearch.matches("Mont Ventoux", ""))
        assertTrue(TreeSearch.matches("Mont Ventoux", "   "))
    }

    @Test fun `les blancs de bord de la recherche sont ignores`() {
        assertTrue(TreeSearch.matches("Mont Ventoux", "  ventoux "))
    }

    @Test fun `la forme comparable d'un texte n'a ni accent ni majuscule`() {
        assertEquals("col des lecheres", TreeSearch.normalize("  Col des Lechères  "))
        assertEquals("ete", TreeSearch.normalize("Été"))
    }
}
