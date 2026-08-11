package fr.lc4918.trailog.elevation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Lecture d'une grille de terrain et interpolation.
 *
 * C'est ici que se joue la justesse des altitudes rendues au monde entier : une grille lue a l'envers ou
 * decalee d'une demi-cellule ne leve rien, elle pose seulement des altitudes voisines - un profil
 * plausible, et faux.
 */
class AsciiGridTest {

    /**
     * Trois cellules de cote, un degre par cellule, coin sud-ouest en (0, 0). Les altitudes croissent vers
     * l'est ET vers le nord, ce qui distingue toute confusion de sens.
     *
     * Le fichier ecrit ses lignes du NORD vers le sud : la derniere ligne du texte est la plus au sud.
     */
    private val grid = """
        ncols 3
        nrows 3
        xllcorner 0.0
        yllcorner 0.0
        cellsize 1.0
        NODATA_value -9999
        200 210 220
        100 110 120
        0 10 20
    """.trimIndent()

    private fun parsed(text: String = grid) = AsciiGrid.parse(text)

    @Test fun `le centre de chaque cellule rend son altitude`() {
        val g = parsed()!!
        // Coin sud-ouest declare : le centre de la premiere cellule est a une demi-cellule de la.
        assertEquals(0.0, g.sample(0.5, 0.5)!!, 1e-9)
        assertEquals(20.0, g.sample(2.5, 0.5)!!, 1e-9)
        assertEquals(200.0, g.sample(0.5, 2.5)!!, 1e-9)
        assertEquals(220.0, g.sample(2.5, 2.5)!!, 1e-9)
    }

    /** Le sens du nord : la premiere ligne du fichier est la plus haute, pas la plus basse. */
    @Test fun `le nord est en tete du fichier`() {
        val g = parsed()!!
        assertEquals(200.0, g.sample(0.5, 2.5)!!, 1e-9)
        assertEquals(0.0, g.sample(0.5, 0.5)!!, 1e-9)
    }

    @Test fun `un point entre quatre cellules est interpole`() {
        val g = parsed()!!
        // Milieu exact des quatre premieres cellules du sud-ouest : (0 + 10 + 100 + 110) / 4.
        assertEquals(55.0, g.sample(1.0, 1.0)!!, 1e-9)
        // A mi-chemin sur une seule direction : moitie de l'ecart est-ouest.
        assertEquals(5.0, g.sample(1.0, 0.5)!!, 1e-9)
    }

    @Test fun `un point hors de la grille ne rend rien`() {
        val g = parsed()!!
        assertNull(g.sample(4.0, 1.0))
        assertNull(g.sample(1.0, -2.0))
    }

    /** Une cellule sans mesure contamine ses voisines : mieux vaut pas d'altitude qu'une altitude devinee
     *  au bord d'un trou du modele. */
    @Test fun `un trou du modele ne se comble pas`() {
        val g = AsciiGrid.parse(grid.replace("100 110 120", "-9999 110 120"))!!
        assertNull(g.sample(0.5, 1.5))
        assertNull(g.sample(1.0, 1.0))
        // Loin du trou, la grille repond normalement.
        assertEquals(120.0, g.sample(2.5, 1.5)!!, 1e-9)
    }

    /** Le SRTM marque ses trous par -32768 ; une grille qui ne declare pas sa valeur d'absence les
     *  laisserait passer pour des altitudes de onze kilometres sous la mer. Ce n'est pas un cas d'ecole :
     *  les grilles Copernicus, celles que l'application demande, arrivent sans cette ligne d'entete. */
    @Test fun `une valeur sentinelle est refusee meme sans NODATA declare`() {
        val g = AsciiGrid.parse(
            grid.replace("NODATA_value -9999\n", "").replace("0 10 20", "-32768 10 20")
        )!!
        assertNull(g.sample(0.5, 0.5))
        assertEquals(20.0, g.sample(2.5, 0.5)!!, 1e-9)
    }

    /** L'autre facon de poser une grille : les coordonnees designent le CENTRE de la cellule du coin. */
    @Test fun `les entetes xllcenter et yllcenter placent la grille sans demi-cellule`() {
        val g = AsciiGrid.parse(grid.replace("xllcorner", "xllcenter").replace("yllcorner", "yllcenter"))!!
        assertEquals(0.0, g.sample(0.0, 0.0)!!, 1e-9)
        assertEquals(220.0, g.sample(2.0, 2.0)!!, 1e-9)
    }

    /** La specification n'impose pas une ligne de grille par ligne de texte : seuls comptent l'ordre des
     *  valeurs et leur nombre. */
    @Test fun `le decoupage en lignes du corps est sans importance`() {
        val g = AsciiGrid.parse(grid.replace("200 210 220\n100", "200 210\n220 100"))!!
        assertEquals(200.0, g.sample(0.5, 2.5)!!, 1e-9)
        assertEquals(0.0, g.sample(0.5, 0.5)!!, 1e-9)
    }

    @Test fun `un corps incomplet ou un texte etranger ne donne pas de grille`() {
        assertNull(AsciiGrid.parse(grid.replace("0 10 20", "0 10")))
        assertNull(AsciiGrid.parse("""{"error":"quota depasse"}"""))
        assertNull(AsciiGrid.parse(""))
    }
}
