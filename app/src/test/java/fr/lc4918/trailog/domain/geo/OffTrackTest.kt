package fr.lc4918.trailog.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les deux calculs de l'alerte d'eloignement : le pre-tri des couches, et la bascule de l'alerte.
 *
 * Le second est le plus important des deux. Une bascule sans zone morte donnerait une banniere qui
 * clignote et un son qui sonne toutes les deux secondes des qu'on marche a l'exact seuil regle - ce que
 * fait un GPS de telephone une bonne partie du temps.
 */
class OffTrackTest {

    // ---------- distance au rectangle englobant ----------

    @Test fun `une position dans l'emprise est a distance nulle`() {
        assertEquals(0.0, OffTrack.bboxDistanceM(45.0, 6.0, 5.0, 44.0, 7.0, 46.0), 0.0)
    }

    @Test fun `une position au nord de l'emprise mesure l'ecart en latitude`() {
        // Un degre de latitude vaut environ 111 km, quelle que soit la longitude.
        val d = OffTrack.bboxDistanceM(47.0, 6.0, 5.0, 44.0, 7.0, 46.0)
        assertEquals(111_000.0, d, 2_000.0)
    }

    @Test fun `une position en coin mesure la diagonale, plus loin que chaque cote`() {
        val coin = OffTrack.bboxDistanceM(47.0, 8.0, 5.0, 44.0, 7.0, 46.0)
        val nord = OffTrack.bboxDistanceM(47.0, 6.0, 5.0, 44.0, 7.0, 46.0)
        val est = OffTrack.bboxDistanceM(45.0, 8.0, 5.0, 44.0, 7.0, 46.0)
        assertTrue("le coin doit etre plus loin que chacun des deux cotes", coin > nord && coin > est)
    }

    /** Une couche vide porte quatre zeros : le rabattement ne doit pas s'y casser. */
    @Test fun `une emprise degeneree se comporte comme un point`() {
        val d = OffTrack.bboxDistanceM(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        assertEquals(0.0, d, 0.0)
    }

    /** Bornes prises a l'envers : on ne suppose pas que west < east. */
    @Test fun `une emprise inversee donne la meme distance qu'a l'endroit`() {
        val endroit = OffTrack.bboxDistanceM(47.0, 6.0, 5.0, 44.0, 7.0, 46.0)
        val envers = OffTrack.bboxDistanceM(47.0, 6.0, 7.0, 46.0, 5.0, 44.0)
        assertEquals(endroit, envers, 0.0)
    }

    // ---------- bascule de l'alerte ----------

    @Test fun `au-dela du seuil, l'alerte s'allume`() {
        assertTrue(OffTrack.alerting(current = false, awayM = 60.0, thresholdM = 50.0))
    }

    @Test fun `sous la marge de retour, l'alerte s'eteint`() {
        // 80 % de 50 m = 40 m : en deca, on est revenu sur la trace.
        assertFalse(OffTrack.alerting(current = true, awayM = 39.0, thresholdM = 50.0))
    }

    /** Le coeur du sujet : entre la marge et le seuil, l'etat precedent tient. */
    @Test fun `dans la zone morte, rien ne change`() {
        assertTrue("en alerte, on y reste", OffTrack.alerting(current = true, awayM = 45.0, thresholdM = 50.0))
        assertFalse("au repos, on y reste", OffTrack.alerting(current = false, awayM = 45.0, thresholdM = 50.0))
    }

    @Test fun `le seuil exact allume, sa marge exacte eteint`() {
        assertTrue(OffTrack.alerting(current = false, awayM = 50.0, thresholdM = 50.0))
        assertFalse(OffTrack.alerting(current = true, awayM = 40.0, thresholdM = 50.0))
    }

    /** Une position qui oscille autour du seuil ne doit basculer qu'une fois. */
    @Test fun `une position qui oscille autour du seuil ne rallume pas l'alerte`() {
        var state = false
        var bascules = 0
        for (away in listOf(30.0, 52.0, 48.0, 51.0, 47.0, 49.0, 53.0)) {
            val next = OffTrack.alerting(state, away, 50.0)
            if (next != state) bascules++
            state = next
        }
        assertEquals("une seule bascule sur toute la serie", 1, bascules)
        assertTrue(state)
    }
}
