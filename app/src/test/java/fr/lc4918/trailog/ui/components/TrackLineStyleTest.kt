package fr.lc4918.trailog.ui.components

import fr.lc4918.trailog.data.repo.LayerGeoJson
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.TrackPoint
import fr.lc4918.trailog.ui.profile.SlopeRamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les troncons colories d'une ligne, et la taille du chevron du sens de parcours. */
class TrackLineStyleTest {

    private fun s(i: Int, slope: Double) = Sample(i * 100.0, 0.0, slope, null, 6.0 + i * 0.001, 45.0)

    private fun colors(json: String) = Regex("\"color\":\"(#[0-9A-F]{6})\"").findAll(json).map { it.groupValues[1] }.toList()

    @Test fun `sans pente, un seul troncon de la couleur demandee`() {
        val json = SlopeLines.features(listOf(s(0, 0.0), s(1, 8.0), s(2, -8.0)), null, "#000000")
        assertEquals(listOf("#000000"), colors(json))
    }

    /** Une plage par classe, et chaque plage reprend le dernier point de la precedente. */
    @Test fun `une plage par classe, jointes bout a bout`() {
        val pts = listOf(s(0, 0.0), s(1, 6.0), s(2, 6.2), s(3, -12.0))
        val json = SlopeLines.features(pts, 50, "#000000")
        assertEquals(listOf(SlopeRamp.hexFor(6.0, 50), SlopeRamp.hexFor(-12.0, 50)), colors(json))
        // Le point 2 termine la premiere plage et ouvre la seconde.
        assertEquals(2, Regex("\\[6.002,45.0]").findAll(json).count())
    }

    /**
     * Le support des chevrons : chaque trace d'UN tenant, meme quand sa pente change a chaque point. Sur
     * les troncons par couleur, MapLibre reprenait l'ecart a zero a chaque troncon, et les chevrons se
     * tassaient la ou le relief varie.
     */
    @Test fun `les chevrons suivent chaque trace d'un seul tenant`() {
        val relief = listOf(s(0, 0.0), s(1, 9.0), s(2, -9.0), s(3, 15.0), s(4, 0.0))
        val plat = listOf(s(10, 0.0), s(11, 0.0))
        val json = SlopeLines.continuous(listOf(relief, plat, listOf(s(20, 0.0))))
        assertEquals("une ligne par trace, la trace d'un point ecartee", 2, Regex("LineString").findAll(json).count())
        // Aucun point repete : sans decoupe, pas de jonction.
        assertEquals(1, Regex("\\[6.002,45.0]").findAll(json).count())
        assertTrue(json.startsWith("""{"type":"FeatureCollection","features":["""))
    }

    @Test fun `une collection vide reste une collection valide`() {
        assertEquals("""{"type":"FeatureCollection","features":[]}""", SlopeLines.collection(emptyList(), 5))
    }

    /**
     * En travers du trait, l'encre du chevron deborde d'au plus 10 % la largeur de la ligne, sans rester
     * en deca de 80 % : il se voit, sans la couvrir. Il gagne en longueur, et son encre s'epaissit.
     */
    @Test fun `le chevron deborde d'au plus dix pour cent le trait`() {
        for (d in listOf(2f, 2.625f, 3f)) for (w in listOf(2f, 4f, 6f, 8f, 12f)) {
            val h = TrackChevron.heightPx(w, d)
            val ink = TrackChevron.inkPx(h)
            assertTrue("largeur $w, densite $d : encre $ink", ink <= w * 1.10f * d && ink >= w * 0.8f * d)
            assertTrue("plus long que haut de moitie au moins", TrackChevron.widthPx(h) >= h * 0.75f)
            assertTrue("encre epaisse", TrackChevron.strokePx(h) >= h * 0.2f)
            assertTrue("le halo tient dans l'image", TrackChevron.haloPx(h) < h)
        }
    }

    /**
     * Une boucle de 3001 points, que le profil ne garde qu'en 2000 echantillons : le trait de pente doit
     * garder CHAQUE point de la trace, et non couper entre deux echantillons - sans quoi les chevrons,
     * poses sur la vraie geometrie, s'en ecartent dans les virages.
     */
    @Test fun `le trait de pente suit chaque point de la trace, pas les echantillons du profil`() {
        val n = 3001
        val pts = List(n) { i ->
            val a = i * 2 * Math.PI / (n - 1)
            TrackPoint(6.0 + 0.01 * Math.cos(a), 45.0 + 0.01 * Math.sin(a), 1000.0 + 100 * Math.sin(a), null)
        }
        val profil = TrackMath.compute(pts, smoothingM = 0.0).samples
        assertTrue("le profil est decime", profil.size < n)
        val trait = SlopeLines.alongTrack(pts, profil)
        assertEquals(n, trait.size)
        trait.forEachIndexed { i, p -> assertEquals(pts[i].lon, p.lon, 0.0); assertEquals(pts[i].lat, p.lat, 0.0) }
        // La pente de chaque point est celle du segment de profil ou il tombe.
        for (i in 1 until n step 97) {
            val k = profil.indexOfFirst { it.x >= trait[i].x }
            assertEquals(profil[k].slope, trait[i].slope, 0.0)
        }
        assertEquals(profil.last().x, trait.last().x, 1e-6)
    }

    /** Simplifie pour le rendu, il garde les memes points que le fichier de la carte, ou sont les chevrons. */
    @Test fun `simplifie, le trait de pente garde les points de la carte`() {
        val pts = List(500) { i -> TrackPoint(6.0 + i * 0.0001, 45.0 + if (i % 50 < 25) 0.0 else 0.0005, 0.0, null) }
        val carte = LayerGeoJson.simplifyLine(pts, 1.0)
        val profil = TrackMath.compute(pts, smoothingM = 0.0).samples
        val trait = SlopeLines.alongTrack(pts, profil, LayerGeoJson.simplifyKeep(pts, 1.0))
        assertTrue(carte.size < pts.size)
        assertEquals(carte.map { it.lon to it.lat }, trait.map { it.lon to it.lat })
    }

    // ---------- Largeur de la mise en evidence ----------

    /** Cadree sur une sortie de la journee (zoom 11 et plus) : la largeur qui convenait, inchangee. */
    @Test fun `la mise en evidence garde sa largeur aux grandes echelles`() {
        assertEquals(5f + 3f, HighlightWidth.at(HighlightWidth.lineStops(5f), 11f), 1e-4f)
        assertEquals(5f + 7f, HighlightWidth.at(HighlightWidth.casingStops(5f), 14f), 1e-4f)
    }

    /** Cadree sur EV1 (zoom 6) : a peine plus large que la trace, lisere compris. */
    @Test fun `la mise en evidence s'affine aux petites echelles`() {
        val trait = HighlightWidth.at(HighlightWidth.lineStops(5f), 6f)
        val lisere = HighlightWidth.at(HighlightWidth.casingStops(5f), 4f)
        assertTrue("trait : $trait", trait <= 5f + 0.5f)
        assertTrue("lisere : $lisere", lisere <= 5f + 2f)
        // Toujours au-dessus du trait qu'elle recouvre, et le lisere autour d'elle.
        assertTrue(trait > 5f && lisere > trait)
    }

    @Test fun `la mise en evidence s'elargit continument avec le zoom`() {
        val l = HighlightWidth.lineStops(4f)
        val largeurs = (40..130).map { HighlightWidth.at(l, it / 10f) }
        assertTrue(largeurs.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(4f + (0.25f + 3f) / 2, HighlightWidth.at(l, 8.5f), 1e-4f)
    }
}
