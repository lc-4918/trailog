package fr.lc4918.trailog.map.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tuiles qui bordent un parcours.
 *
 * Deux fautes possibles, et l'utilisateur ne les decouvre qu'apres coup : un couloir troue laisse des
 * carres gris au milieu de la randonnee, une fois hors reseau ; un couloir qui compte deux fois les memes
 * tuiles annonce un poids faux et telecharge en double.
 */
class TileCorridorTest {

    /** Un parcours est-ouest de deux kilometres, aux environs de Grenoble. */
    private val est = listOf(5.70 to 45.19, 5.726 to 45.19)

    private fun tiles(points: List<Pair<Double, Double>>, zoom: Int, radiusM: Double) =
        TileMath.tilesAlong(points, zoom, radiusM)

    // ---------- Ce que le couloir contient ----------

    /** Les tuiles du parcours lui-meme y sont, sinon le couloir aurait des trous la ou l'on marche. */
    @Test fun `les tuiles traversees par le parcours sont dans le couloir`() {
        val z = 14
        val couloir = tiles(est, z, 100.0).toSet()
        listOf(est.first(), est.last(), (est[0].first + est[1].first) / 2 to 45.19).forEach { (lon, lat) ->
            val (x, y) = TileMath.tileAt(lon, lat, z)
            assertTrue("tuile ($x, $y) absente du couloir", Triple(x, y, z) in couloir)
        }
    }

    /**
     * Deux sommets distants - une ligne droite tracee a la regle - ne doivent pas faire sauter les tuiles
     * entre eux : le parcours est echantillonne, il n'est pas reduit a ses sommets.
     */
    @Test fun `un long troncon ne laisse pas de trou entre ses deux bouts`() {
        val z = 14
        val couloir = tiles(est, z, 0.0).toSet()
        val (x1, _) = TileMath.tileAt(est[0].first, est[0].second, z)
        val (x2, _) = TileMath.tileAt(est[1].first, est[1].second, z)
        // Toutes les colonnes entre les deux bouts sont representees, sans exception.
        (minOf(x1, x2)..maxOf(x1, x2)).forEach { x ->
            assertTrue("colonne $x absente", couloir.any { it.first == x })
        }
    }

    @Test fun `un parcours vide ne donne aucune tuile`() {
        assertEquals(emptyList<Triple<Int, Int, Int>>(), tiles(emptyList(), 14, 500.0))
    }

    // ---------- Ce qu'il ne contient pas ----------

    /**
     * Tout l'interet du couloir : il ne prend pas ce que prendrait le rectangle englobant.
     *
     * L'ecart CROIT AVEC LE ZOOM, et c'est ce qui compte : au zoom 14 une tuile fait presque deux
     * kilometres de cote, le couloir en occupe donc trois de large et n'economise que la moitie ; au zoom
     * 16, ou l'on telecharge vraiment pour marcher, la meme largeur de couloir represente une fraction bien
     * plus mince du rectangle - et c'est la que se trouve l'essentiel du poids.
     */
    @Test fun `un parcours en diagonale coute bien moins qu'une emprise rectangulaire`() {
        val diagonale = listOf(5.70 to 45.10, 5.90 to 45.30)
        val emprise = Bbox.of(5.70, 45.10, 5.90, 45.30)

        val couloir14 = tiles(diagonale, 14, 200.0).size
        val rect14 = TileMath.tileCount(emprise, 14).toInt()
        assertTrue("zoom 14 : couloir $couloir14, rectangle $rect14", couloir14 < rect14)

        val couloir16 = tiles(diagonale, 16, 200.0).size
        val rect16 = TileMath.tileCount(emprise, 16).toInt()
        assertTrue("zoom 16 : couloir $couloir16, rectangle $rect16", couloir16 * 5 < rect16)
    }

    @Test fun `une largeur plus grande prend plus de tuiles`() {
        val etroit = tiles(est, 14, 100.0).size
        val large = tiles(est, 14, 2000.0).size
        assertTrue("$etroit puis $large", large > etroit)
    }

    // ---------- Pas de doublon ----------

    /**
     * Une trace qui revient sur elle-meme - un aller-retour, un lacet, une boucle - repasse sur ses propres
     * tuiles. Chacune ne doit etre comptee, donc telechargee, qu'une seule fois.
     */
    @Test fun `un aller-retour ne compte pas deux fois les memes tuiles`() {
        val aller = tiles(est, 14, 500.0)
        val allerRetour = tiles(est + est.reversed(), 14, 500.0)
        assertEquals(aller.size, allerRetour.size)
        assertEquals(allerRetour.size, allerRetour.toSet().size)
    }

    @Test fun `une boucle ne porte aucune tuile en double`() {
        val boucle = listOf(
            5.70 to 45.19, 5.73 to 45.19, 5.73 to 45.21, 5.70 to 45.21, 5.70 to 45.19,
        )
        val couloir = tiles(boucle, 15, 300.0)
        assertEquals("des tuiles sont en double", couloir.size, couloir.toSet().size)
    }

    /** Le total sur la plage de zoom additionne des ensembles distincts : une tuile n'existe qu'a un zoom. */
    @Test fun `le total par plage de zoom est la somme des niveaux`() {
        val attendu = (12..15).sumOf { tiles(est, it, 300.0).size.toLong() }
        assertEquals(attendu, TileMath.totalTileCountAlong(est, 12, 15, 300.0))
    }
}
