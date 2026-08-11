package fr.lc4918.trailog.domain.geo

import fr.lc4918.trailog.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retouches d'une trace importee. Ce sont les seules operations de l'application qui MODIFIENT une trace
 * recue : une faute ici ne se repare pas, le fichier d'origine n'est plus la.
 */
class TrackEditTest {

    /** Une trace d'un seul segment, quatre points qui montent vers le nord-est, horodates. */
    private val seg = (0 until 4).map { i ->
        TrackPoint(6.0 + i * 0.001, 45.0 + i * 0.001, 900.0 + i * 10, 1_600_000_000_000 + i * 60_000L)
    }

    // ---------- Inverser ----------

    @Test fun `inverser retourne les points et l'ordre des segments`() {
        val a = listOf(TrackPoint(6.0, 45.0), TrackPoint(6.1, 45.1))
        val b = listOf(TrackPoint(7.0, 46.0), TrackPoint(7.1, 46.1))
        val out = TrackEdit.reverse(listOf(a, b))
        assertEquals(listOf(7.1, 7.0), out[0].map { it.lon })
        assertEquals(listOf(6.1, 6.0), out[1].map { it.lon })
    }

    /**
     * Les horodatages tombent. Un temps qui recule n'est pas une trace parcourue a l'envers : le GPX exige
     * des dates croissantes, et le temps en mouvement compterait des durees negatives.
     */
    @Test fun `inverser efface les horodatages et garde les altitudes`() {
        val out = TrackEdit.reverse(listOf(seg))[0]
        assertTrue("un horodatage a survecu", out.all { it.timeMs == null })
        assertEquals(listOf(930.0, 920.0, 910.0, 900.0), out.map { it.ele })
    }

    @Test fun `inverser deux fois rend la geometrie de depart`() {
        val out = TrackEdit.reverse(TrackEdit.reverse(listOf(seg)))
        assertEquals(seg.map { it.lon to it.ele }, out[0].map { it.lon to it.ele })
    }

    // ---------- Couper ----------

    /** Le point de coupe appartient AUX DEUX morceaux : sans cela, les deux traces s'arreteraient a un
     *  point d'ecart et un trou apparaitrait a la jointure, visible et compte en moins des deux cotes. */
    @Test fun `le point de coupe termine le premier morceau et commence le second`() {
        val (head, tail) = TrackEdit.splitAt(listOf(seg), 0, 1)!!
        assertEquals(listOf(6.0, 6.001), head[0].map { it.lon })
        assertEquals(listOf(6.001, 6.002, 6.003), tail[0].map { it.lon })
    }

    @Test fun `couper laisse les segments voisins de part et d'autre`() {
        val avant = listOf(TrackPoint(5.0, 44.0), TrackPoint(5.1, 44.1))
        val apres = listOf(TrackPoint(8.0, 47.0), TrackPoint(8.1, 47.1))
        val (head, tail) = TrackEdit.splitAt(listOf(avant, seg, apres), 1, 2)!!
        assertEquals(2, head.size)
        assertEquals(listOf(5.0, 5.1), head[0].map { it.lon })
        assertEquals(2, tail.size)
        assertEquals(listOf(8.0, 8.1), tail[1].map { it.lon })
    }

    /** Couper au tout debut ou a la toute fin laisserait un morceau d'un seul point : ni distance, ni
     *  profil, ni trace a dessiner. On refuse plutot que de creer une couche vide de sens. */
    @Test fun `une coupe qui ne donne pas deux morceaux est refusee`() {
        assertNull(TrackEdit.splitAt(listOf(seg), 0, 0))
        assertNull(TrackEdit.splitAt(listOf(seg), 0, seg.lastIndex))
        assertNull(TrackEdit.splitAt(listOf(seg), 3, 1))
        assertNull(TrackEdit.splitAt(emptyList(), 0, 0))
    }

    // ---------- Le point le plus proche ----------

    @Test fun `la coupe se cale sur le point de la trace le plus proche`() {
        // Un peu a cote du troisieme point : c'est lui qui doit sortir.
        assertEquals(0 to 2, TrackEdit.nearest(listOf(seg), 6.00201, 45.00199))
        assertNull(TrackEdit.nearest(emptyList(), 6.0, 45.0))
    }

    @Test fun `le point le plus proche se cherche dans tous les segments`() {
        val autre = listOf(TrackPoint(8.0, 47.0), TrackPoint(8.001, 47.001))
        assertEquals(1 to 1, TrackEdit.nearest(listOf(seg, autre), 8.0011, 47.0011))
    }

    // ---------- Viser un point entre deux sommets ----------

    /**
     * Ce qui manquait a la coupe : viser AILLEURS que sur un sommet.
     *
     * Une trace dessinee a la regle ne porte que ses extremites ; couper "au sommet le plus proche" y
     * revenait a ne pouvoir couper qu'aux deux bouts, c'est-a-dire nulle part.
     */
    @Test fun `le point vise est interpole sur le troncon, pas rabattu sur un sommet`() {
        val droite = listOf(TrackPoint(6.0, 45.0, 900.0), TrackPoint(6.010, 45.0, 1000.0))
        val hit = TrackEdit.locate(listOf(droite), 6.005, 45.0)!!
        assertEquals(0, hit.segment)
        assertEquals(0, hit.index)
        assertEquals(6.005, hit.point.lon, 1e-9)
        // L'altitude suit la meme interpolation : a mi-chemin entre 900 et 1000.
        assertEquals(950.0, hit.point.ele!!, 0.5)
    }

    /** Un point insere n'a pas ete parcouru a une heure connue : lui en inventer une le ferait entrer dans
     *  le calcul du temps de mouvement. */
    @Test fun `un point insere ne porte pas d'horodatage`() {
        val hit = TrackEdit.locate(listOf(seg), 6.0015, 45.0015)!!
        assertNull(hit.point.timeMs)
    }

    @Test fun `le point vise se cherche dans tous les segments`() {
        val autre = listOf(TrackPoint(8.0, 47.0), TrackPoint(8.01, 47.0))
        val hit = TrackEdit.locate(listOf(seg, autre), 8.005, 47.0)!!
        assertEquals(1, hit.segment)
        assertNull(TrackEdit.locate(emptyList(), 6.0, 45.0))
    }

    @Test fun `couper au point vise insere ce point dans les deux morceaux`() {
        val droite = listOf(TrackPoint(6.0, 45.0), TrackPoint(6.010, 45.0))
        val hit = TrackEdit.locate(listOf(droite), 6.005, 45.0)!!
        val (head, tail) = TrackEdit.splitAtHit(listOf(droite), hit)!!
        assertEquals(listOf(6.0, 6.005), head[0].map { it.lon })
        assertEquals(listOf(6.005, 6.010), tail[0].map { it.lon })
    }

    /** Viser un sommet ne doit pas y poser un second point au meme endroit : la trace y gagnerait un
     *  doublon invisible, compte dans ses statistiques. */
    @Test fun `couper sur un sommet n'ajoute pas de point en double`() {
        val hit = TrackEdit.locate(listOf(seg), seg[1].lon, seg[1].lat)!!
        val (head, tail) = TrackEdit.splitAtHit(listOf(seg), hit)!!
        assertEquals(2, head[0].size)
        assertEquals(3, tail[0].size)
        assertEquals(seg[1].lon, head[0].last().lon, 1e-9)
        assertEquals(seg[1].lon, tail[0].first().lon, 1e-9)
    }

    // ---------- Joindre ----------

    /** Deux morceaux qui se touchent se joignent par leurs extremites LES PLUS PROCHES : celui qui a ete
     *  enregistre a l'envers est retourne pour l'occasion, sans qu'on ait a s'en occuper. */
    @Test fun `la jonction se fait par les extremites les plus proches`() {
        val a = listOf(TrackPoint(6.0, 45.0), TrackPoint(6.01, 45.0))
        // b commence loin et finit juste apres a : il doit etre retourne.
        val b = listOf(TrackPoint(6.05, 45.0), TrackPoint(6.011, 45.0))
        val out = TrackEdit.join(listOf(a, b), 0, 1)!!
        assertEquals(1, out.size)
        assertEquals(listOf(6.0, 6.01, 6.011, 6.05), out[0].map { it.lon })
    }

    @Test fun `un pont s'intercale entre les deux segments`() {
        val a = listOf(TrackPoint(6.0, 45.0), TrackPoint(6.01, 45.0))
        val b = listOf(TrackPoint(6.02, 45.0), TrackPoint(6.03, 45.0))
        val pont = listOf(TrackPoint(6.013, 45.001), TrackPoint(6.017, 45.001))
        val out = TrackEdit.join(listOf(a, b), 0, 1, pont)!!
        assertEquals(listOf(6.0, 6.01, 6.013, 6.017, 6.02, 6.03), out[0].map { it.lon })
    }

    /** Le segment retourne pour la jonction perd ses horodatages, pour la meme raison que l'inversion. */
    @Test fun `le segment retourne par la jonction perd ses horodatages`() {
        val a = listOf(TrackPoint(6.0, 45.0), TrackPoint(6.01, 45.0))
        val b = seg.map { it.copy(lon = it.lon + 0.05) }     // commence loin, finit encore plus loin
        val out = TrackEdit.join(listOf(b, a), 0, 1)!!
        // b a du etre retourne pour joindre son debut a la fin de a : ses temps sont tombes.
        assertTrue("un horodatage a survecu au retournement", out[0].take(4).all { it.timeMs == null })
    }

    @Test fun `les autres segments gardent leur place`() {
        val a = listOf(TrackPoint(6.0, 45.0), TrackPoint(6.01, 45.0))
        val b = listOf(TrackPoint(6.02, 45.0), TrackPoint(6.03, 45.0))
        val loin = listOf(TrackPoint(9.0, 48.0), TrackPoint(9.01, 48.0))
        val out = TrackEdit.join(listOf(a, loin, b), 0, 2)!!
        assertEquals(2, out.size)
        assertEquals(listOf(9.0, 9.01), out[1].map { it.lon })
    }

    @Test fun `joindre un segment a lui-meme est refuse`() {
        assertNull(TrackEdit.join(listOf(seg), 0, 0))
        assertNull(TrackEdit.join(listOf(seg), 0, 5))
    }

    // ---------- Fusionner ----------

    /**
     * Les segments restent distincts. Recoller les deux polylignes en une seule ferait apparaitre entre
     * elles une droite qu'on n'a jamais parcourue - dessinee sur la carte, et comptee dans la distance.
     */
    @Test fun `fusionner met les segments bout a bout sans les recoller`() {
        val autre = listOf(TrackPoint(8.0, 47.0), TrackPoint(8.1, 47.1))
        val out = TrackEdit.merge(listOf(seg), listOf(autre))
        assertEquals(2, out.size)
        assertEquals(seg.map { it.lon }, out[0].map { it.lon })
        assertEquals(autre.map { it.lon }, out[1].map { it.lon })
    }

    @Test fun `fusionner avec une geometrie vide ne change rien`() {
        assertEquals(listOf(seg), TrackEdit.merge(listOf(seg), emptyList()))
        assertEquals(listOf(seg), TrackEdit.merge(emptyList(), listOf(seg)))
    }
}
