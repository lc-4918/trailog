package fr.lc4918.trailog.ui.edit

import fr.lc4918.trailog.ui.edit.CutBubblePlacement.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Placement de la bulle du marqueur de coupe.
 *
 * Deux fautes possibles, toutes deux muettes : une bulle posee SUR la trace masque l'endroit meme qu'on
 * examine avant de couper, et une bulle poussee hors de l'ecran ne se voit pas du tout - dans les deux
 * cas, rien ne leve, l'utilisateur constate seulement qu'il ne voit pas ce qu'il vise.
 *
 * Le choix se fait par RECOUVREMENT REEL, et c'est ce que verrouillent ces tests. Une premiere version
 * jugeait sur l'orientation de la trace au point vise : cela ne vaut que pour une trace droite, et dans un
 * lacet ou une boucle, le cote "oppose a la direction" tombait en plein sur une autre portion du trace.
 */
class CutBubblePlacementTest {

    private val bubbleW = 90
    private val bubbleH = 84
    private val tail = 24
    private val viewW = 1080
    private val viewH = 1920
    private val topInset = 60
    private val margin = 36
    private val px = 540
    private val py = 960

    private fun place(
        x: Int = px, y: Int = py, track: List<List<Pair<Float, Float>>> = emptyList(),
    ) = CutBubblePlacement.choose(
        pointX = x, pointY = y, track = track,
        bubbleW = bubbleW, bubbleH = bubbleH, tail = tail,
        viewW = viewW, viewH = viewH, topInset = topInset, margin = margin,
    )

    /** Une polyligne ecran, ecrite en couples pour rester lisible. */
    private fun run(vararg pts: Pair<Int, Int>) = listOf(pts.map { it.first.toFloat() to it.second.toFloat() })

    private fun rect(p: CutBubblePlacement.Placement): List<Int> {
        val w = p.width(bubbleW, tail)
        val h = p.height(bubbleH, tail)
        return listOf(p.x + p.panX, p.y + p.panY, p.x + p.panX + w, p.y + p.panY + h)
    }

    // ---------- Ne pas recouvrir la trace ----------

    /** Trace verticale traversant le point : haut et bas sont pris, la bulle part sur un cote. */
    @Test fun `une trace nord-sud pousse la bulle sur le cote`() {
        val p = place(track = run(px to py - 400, px to py + 400))
        assertTrue("cote attendu, obtenu ${p.side}", p.side == Side.LEFT || p.side == Side.RIGHT)
    }

    @Test fun `une trace est-ouest pousse la bulle en haut ou en bas`() {
        val p = place(track = run(px - 400 to py, px + 400 to py))
        assertTrue("haut ou bas attendu, obtenu ${p.side}", p.side == Side.TOP || p.side == Side.BOTTOM)
    }

    /**
     * Le cas qui mettait l'ancienne version en defaut : trois cotes occupes par des portions de trace qui
     * n'ont rien a voir avec la direction locale - un lacet, une boucle, deux traces qui se croisent. Seul
     * le recouvrement reel sait trouver le quatrieme.
     */
    @Test fun `le seul cote libre est retenu, quelle que soit la direction de la trace`() {
        val trois = listOf(
            // Au-dessus, en travers du corps de la bulle du haut.
            listOf((px - 200).toFloat() to (py - 66).toFloat(), (px + 200).toFloat() to (py - 66).toFloat()),
            // A gauche et a droite, en travers des corps lateraux.
            listOf((px - 69).toFloat() to (py - 200).toFloat(), (px - 69).toFloat() to (py + 200).toFloat()),
            listOf((px + 69).toFloat() to (py - 200).toFloat(), (px + 69).toFloat() to (py + 200).toFloat()),
        )
        assertEquals(Side.BOTTOM, place(track = trois).side)
    }

    /** La pointe, elle, touche la trace par construction : la trace passe PAR le point de coupe. La compter
     *  donnerait quatre cotes occupes et un choix sans objet - seul le corps est juge. */
    @Test fun `la trace qui passe par le point n'ecarte aucun cote`() {
        // Une trace qui s'arrete au point exact, venant du sud-ouest : elle n'occupe aucun corps de bulle.
        val p = place(track = run(px - 300 to py + 300, px to py))
        assertEquals(Side.TOP, p.side)
    }

    /** Une trace qui passe a cote sans traverser la bulle ne doit pas ecarter ce cote : sinon la bulle
     *  fuirait des portions qu'elle ne couvre pas, et finirait par n'avoir nulle part ou aller. */
    @Test fun `une trace lointaine ne gene aucun cote`() {
        val loin = run(px + 500 to py - 500, px + 500 to py + 500)
        assertEquals(Side.TOP, place(track = loin).side)
    }

    /** Sans trace connue, l'ordre de preference tranche. */
    @Test fun `sans trace, la bulle se pose au-dessus par defaut`() {
        assertEquals(Side.TOP, place().side)
    }

    /** Une trace qui traverse un candidat de part en part, sans qu'aucun de ses sommets n'y soit, doit
     *  quand meme etre vue : c'est le cas d'un long troncon droit. */
    @Test fun `un long troncon traversant la bulle est detecte`() {
        // Horizontale tres au-dessus du point, traversant la zone ou irait la bulle du haut.
        val traverse = run(px - 2000 to py - 100, px + 2000 to py - 100)
        assertTrue("le haut doit etre ecarte", place(track = traverse).side != Side.TOP)
    }

    /** Toutes les portions comptent, pas seulement celle qu'on coupe : deux traces peuvent se croiser. */
    @Test fun `plusieurs portions sont prises en compte`() {
        val deux = listOf(
            // Horizontale en travers des corps lateraux.
            listOf((px - 400).toFloat() to py.toFloat(), (px + 400).toFloat() to py.toFloat()),
            // Verticale en travers du corps du haut.
            listOf(px.toFloat() to (py - 400).toFloat(), px.toFloat() to (py - 40).toFloat()),
        )
        assertEquals(Side.BOTTOM, place(track = deux).side)
    }

    // ---------- Ou la bulle se pose ----------

    @Test fun `la pointe touche le point et la bulle se centre dessus`() {
        val haut = place()
        assertEquals(Side.TOP, haut.side)
        assertEquals(px - bubbleW / 2, haut.x)
        assertEquals(py - (bubbleH + tail), haut.y)

        val droite = place(track = run(px - 400 to py, px + 400 to py, px + 400 to py - 400))
        if (droite.side == Side.RIGHT) {
            assertEquals(px, droite.x)
            assertEquals(py - bubbleH / 2, droite.y)
        }
    }

    // ---------- Tenir a l'ecran ----------

    @Test fun `au milieu de l'ecran, la carte ne bouge pas`() {
        val p = place()
        assertEquals(0, p.panX)
        assertEquals(0, p.panY)
    }

    @Test fun `une bulle qui sort par le haut fait descendre la carte`() {
        val p = place(y = 20)
        assertTrue("la carte doit descendre", p.panY > 0)
        assertTrue("bord haut : ${rect(p)[1]}", rect(p)[1] >= topInset + margin)
    }

    @Test fun `une bulle qui sort par le bas fait remonter la carte`() {
        val p = place(y = viewH - 10, track = run(px - 400 to viewH - 10, px + 400 to viewH - 10))
        assertTrue("bord bas : ${rect(p)[3]}", rect(p)[3] <= viewH - margin)
    }

    @Test fun `une bulle qui sort sur un cote decale la carte lateralement`() {
        val gauche = place(x = 5, track = run(5 to py - 400, 5 to py + 400))
        assertTrue("bord gauche : ${rect(gauche)[0]}", rect(gauche)[0] >= margin)

        val droite = place(x = viewW - 5, track = run(viewW - 5 to py - 400, viewW - 5 to py + 400))
        assertTrue("bord droit : ${rect(droite)[2]}", rect(droite)[2] <= viewW - margin)
    }

    @Test fun `dans un coin d'ecran, la bulle tient dans les deux sens`() {
        val p = place(x = 4, y = 4)
        val r = rect(p)
        assertTrue("gauche ${r[0]}", r[0] >= margin)
        assertTrue("haut ${r[1]}", r[1] >= topInset + margin)
        assertTrue("droite ${r[2]}", r[2] <= viewW - margin)
        assertTrue("bas ${r[3]}", r[3] <= viewH - margin)
    }

    @Test fun `l'encombrement suit le cote retenu`() {
        val haut = place()
        assertEquals(bubbleW, haut.width(bubbleW, tail))
        assertEquals(bubbleH + tail, haut.height(bubbleH, tail))

        val cote = place(track = run(px to py - 400, px to py + 400))
        assertEquals(bubbleW + tail, cote.width(bubbleW, tail))
        assertEquals(bubbleH, cote.height(bubbleH, tail))
    }
}
