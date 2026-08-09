package fr.lc4918.trailog.ui.points

import fr.lc4918.trailog.domain.model.BubblePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Placement de l'infobulle : geometrie pure, verifiable sans appareil. Valeurs proches du reel
 *  (1080x2280, densite 3) : bulle de 280dp de large = 840 px, epingle de 36dp = 108 px. */
class BubblePlacementTest {

    private val viewW = 1080
    private val viewH = 2280
    private val topInset = 90
    private val margin = 24
    private val gap = 24
    private val markerH = 108
    private val bubbleW = 840
    private val bubbleH = 600

    private fun place(pos: BubblePosition, mx: Int, my: Int, w: Int = bubbleW, h: Int = bubbleH) =
        computeBubblePlacement(pos, mx, my, w, h, viewW, viewH, topInset, margin, gap, markerH)

    // ---- AUTO : comportement historique, la carte ne bouge jamais ----

    @Test
    fun auto_pose_la_bulle_sous_le_marqueur_quand_ca_tient() {
        val p = place(BubblePosition.AUTO, 540, 500)
        assertEquals(500 + gap, p.y)
        assertEquals(540 - bubbleW / 2, p.x)
        assertEquals(0, p.panX); assertEquals(0, p.panY)
    }

    @Test
    fun auto_bascule_au_dessus_quand_ca_deborde_en_bas() {
        val p = place(BubblePosition.AUTO, 540, 2000)
        assertEquals(2000 - bubbleH - gap, p.y)
        assertEquals(0, p.panY)
    }

    @Test
    fun auto_ne_demande_jamais_de_decalage_de_carte() {
        // Marqueur dans un coin, bulle largement debordante : AUTO borne, mais ne bouge pas la carte.
        val p = place(BubblePosition.AUTO, 5, 5)
        assertEquals(0, p.panX); assertEquals(0, p.panY)
        assertTrue(p.x >= margin)
        assertTrue(p.y >= topInset + margin)
    }

    // ---- Grille 3x3 : placement demande respecte quand il tient ----

    @Test
    fun haut_place_la_bulle_au_dessus_en_degageant_l_epingle() {
        val p = place(BubblePosition.TOP, 540, 1500)
        assertEquals(1500 - markerH - gap - bubbleH, p.y)
        assertEquals(540 - bubbleW / 2, p.x)
        assertEquals(0, p.panX); assertEquals(0, p.panY)
    }

    @Test
    fun centre_recouvre_le_point() {
        val p = place(BubblePosition.CENTER, 540, 1140)
        assertEquals(540 - bubbleW / 2, p.x)
        assertEquals(1140 - bubbleH / 2, p.y)
    }

    @Test
    fun bas_droit_place_la_bulle_en_bas_a_droite_du_point() {
        val p = place(BubblePosition.BOTTOM_RIGHT, 100, 500, w = 300)
        assertEquals(100 + gap, p.x)
        assertEquals(500 + gap, p.y)
    }

    @Test
    fun milieu_gauche_place_la_bulle_a_gauche_centree_verticalement() {
        val p = place(BubblePosition.MIDDLE_LEFT, 900, 1140, w = 300)
        assertEquals(900 - gap - 300, p.x)
        assertEquals(1140 - bubbleH / 2, p.y)
    }

    // ---- Debordement : la bulle rentre, et le decalage rend le placement exact ----

    @Test
    fun debordement_ramene_la_bulle_dans_l_ecran_et_demande_un_decalage() {
        val p = place(BubblePosition.TOP, 540, 300)   // pas la place au-dessus
        assertTrue("la bulle doit rentrer", p.y >= topInset + margin)
        assertTrue("un decalage vers le bas est attendu", p.panY > 0)
    }

    // ---- Geocodage : le coin qui deplace le moins la carte ----

    private fun corner(mx: Int, my: Int, w: Int = bubbleW, h: Int = bubbleH) =
        computeGeocodePlacement(mx, my, w, h, viewW, viewH, topInset, margin, gap, markerH)

    /** Point en plein ecran : les quatre coins tiennent, la carte n'a aucune raison de bouger. Le premier
     *  de la liste tranche, et c'est le bas-droit : il n'a pas a degager l'epingle. */
    @Test
    fun geocodage_au_centre_ne_bouge_pas_la_carte_et_prend_le_bas_droit() {
        val p = corner(400, 900, w = 300, h = 300)
        assertEquals(0, p.panX); assertEquals(0, p.panY)
        assertEquals(400 + gap, p.x)
        assertEquals(900 + gap, p.y)
    }

    /** Point pres du bord droit : le bas-droit deborderait, le bas-gauche tient. La bulle change de cote
     *  plutot que de tirer la carte, ce qui est tout l'objet de ce placement. */
    @Test
    fun geocodage_pres_du_bord_droit_passe_a_gauche_sans_bouger_la_carte() {
        val p = corner(1000, 900, w = 300, h = 300)
        assertEquals(0, p.panX); assertEquals(0, p.panY)
        assertEquals(1000 - gap - 300, p.x)
    }

    /** Point tout en bas : plus de place en dessous, la rangee haute prend le relais, toujours sans
     *  decalage - et en degageant la hauteur de l'epingle. */
    @Test
    fun geocodage_tout_en_bas_passe_au_dessus_sans_bouger_la_carte() {
        val p = corner(400, 2200, w = 300, h = 300)
        assertEquals(0, p.panX); assertEquals(0, p.panY)
        assertEquals(2200 - markerH - gap - 300, p.y)
    }

    /** Appui long contre le bord haut : l'epingle, posee par sa pointe sur le point, monte de sa hauteur
     *  au-dessus de lui et s'y trouve coupee. La carte descend juste ce qu'il faut pour la degager. */
    @Test
    fun geocodage_contre_le_bord_haut_degage_l_epingle_entiere() {
        val p = corner(540, 10, w = 300, h = 300)
        assertTrue("la carte doit descendre", p.panY > 0)
        assertEquals("le sommet de l'epingle doit arriver sous la marge", topInset + margin, 10 + p.panY - markerH)
    }

    /** Meme regle sur le bord gauche : l'epingle est large, elle deborde alors que le point est encore
     *  dans l'ecran. */
    @Test
    fun geocodage_contre_le_bord_gauche_degage_l_epingle_entiere() {
        val p = corner(5, 1140, w = 300, h = 300)
        assertEquals("le bord gauche de l'epingle doit arriver a la marge", margin, 5 + p.panX - markerH / 2)
    }

    /** Lieu trouve hors de la vue courante : le ramener au bord le plus proche le poserait dans un coin.
     *  L'ensemble epingle + bulle est centre. */
    @Test
    fun geocodage_hors_de_la_carte_centre_l_ensemble() {
        val mx = -500; val w = 300
        val p = corner(mx, 1140, w = w, h = 300)
        val left = minOf(p.x, mx + p.panX - markerH / 2)
        val right = maxOf(p.x + w, mx + p.panX + markerH / 2)
        assertEquals("l'ensemble doit etre centre horizontalement", viewW / 2, (left + right) / 2)
    }

    /** Ensemble trop grand pour l'ecran : aucun decalage ne le ferait tenir, on le centre plutot que de
     *  le coller a un bord. */
    @Test
    fun geocodage_trop_grand_pour_l_ecran_est_centre() {
        val my = 1140; val h = 2400
        val p = corner(540, my, h = h)
        val top = minOf(p.y, my + p.panY - markerH)
        val bottom = maxOf(p.y + h, my + p.panY)
        assertEquals("l'ensemble doit etre centre verticalement", (topInset + margin + viewH - margin) / 2,
            (top + bottom) / 2)
    }

    /** Le placement rendu reste l'un des quatre coins, decalage applique : meme invariant que la grille. */
    @Test
    fun geocodage_apres_le_decalage_le_coin_tient_sans_nouveau_decalage() {
        for ((mx, my) in listOf(20 to 20, 1060 to 20, 20 to 2260, 1060 to 2260, 540 to 1140)) {
            val first = corner(mx, my)
            val after = corner(mx + first.panX, my + first.panY)
            assertEquals("depuis ($mx,$my) : plus aucun decalage attendu", 0, after.panX)
            assertEquals("depuis ($mx,$my) : plus aucun decalage attendu", 0, after.panY)
        }
    }

    /** Invariant central : apres le decalage de carte, le marqueur a bouge de (panX, panY) et le placement
     *  demande tient alors exactement - sans que la bulle ait bouge a l'ecran, et sans nouveau decalage. */
    @Test
    fun apres_le_decalage_le_placement_demande_est_atteint_sans_nouveau_decalage() {
        for (pos in BubblePosition.entries.filter { it != BubblePosition.AUTO }) {
            for ((mx, my) in listOf(20 to 20, 1060 to 20, 20 to 2260, 1060 to 2260, 540 to 1140)) {
                val first = place(pos, mx, my)
                val after = place(pos, mx + first.panX, my + first.panY)
                assertEquals("$pos depuis ($mx,$my) : la bulle ne doit pas bouger a l'ecran", first.x, after.x)
                assertEquals("$pos depuis ($mx,$my) : la bulle ne doit pas bouger a l'ecran", first.y, after.y)
                assertEquals("$pos depuis ($mx,$my) : plus aucun decalage attendu", 0, after.panX)
                assertEquals("$pos depuis ($mx,$my) : plus aucun decalage attendu", 0, after.panY)
            }
        }
    }
}
