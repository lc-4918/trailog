package fr.lc4918.trailog.ui.alert

import fr.lc4918.trailog.domain.model.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les neuf chiffres du tableau de bord du suivi.
 *
 * La faute que ces cas attrapent est silencieuse : un denivele restant faux ne leve rien, il fait
 * seulement renoncer a un col qu'on aurait passe - ou l'inverse, ce qui est pire.
 */
class FollowProgressTest {

    /** Une trace de 1000 m : monte de 100 a 300 m sur la premiere moitie, redescend a 150 sur la seconde. */
    private val trace = listOf(
        Sample(x = 0.0, z = 100.0, slope = 0.0, t = null, lon = 5.0, lat = 45.0),
        Sample(x = 500.0, z = 300.0, slope = 0.0, t = null, lon = 5.01, lat = 45.0),
        Sample(x = 1000.0, z = 150.0, slope = 0.0, t = null, lon = 5.02, lat = 45.0),
    )

    private fun au(alongM: Double, elapsedMs: Long = 600_000L, speed: Float? = 3f) =
        FollowProgressMath.of(trace, alongM, speed, startedAtMs = 0L, nowMs = elapsedMs)

    @Test fun `au depart, tout reste a faire`() {
        val p = au(0.0)
        assertEquals(0.0, p.doneM, 1e-6)
        assertEquals(1000.0, p.remainingM, 1e-6)
        assertEquals(0.0, p.doneAscentM, 1e-6)
        assertEquals(200.0, p.remainingAscentM, 1e-6)
        assertEquals(150.0, p.remainingDescentM, 1e-6)
    }

    @Test fun `a l'arrivee, il ne reste rien`() {
        val p = au(1000.0)
        assertEquals(1000.0, p.doneM, 1e-6)
        assertEquals(0.0, p.remainingM, 1e-6)
        assertEquals(200.0, p.doneAscentM, 1e-6)
        assertEquals(150.0, p.doneDescentM, 1e-6)
        assertEquals(0.0, p.remainingAscentM, 1e-6)
    }

    /**
     * **Au milieu d'un segment, l'altitude est INTERPOLEE.**
     *
     * A 250 m on est a mi-pente du premier segment : 100 m de monte faits, 100 restants. Compter le segment
     * en cours en entier ferait sauter le denivele d'un coup a chaque sommet de la ligne brisee, et le
     * chiffre bondirait sous les yeux sans qu'on ait rien fait de particulier.
     */
    @Test fun `au milieu d'un segment, le denivele est partage`() {
        val p = au(250.0)
        assertEquals(100.0, p.doneAscentM, 1e-6)
        assertEquals(100.0, p.remainingAscentM, 1e-6)
        assertEquals("la descente est entierement devant", 150.0, p.remainingDescentM, 1e-6)
        assertEquals(0.0, p.doneDescentM, 1e-6)
    }

    /** Le fait et le restant se completent toujours : c'est le controle qui attrape une borne oubliee. */
    @Test fun `le fait et le restant somment le total`() {
        listOf(0.0, 123.0, 500.0, 777.0, 1000.0).forEach { x ->
            val p = au(x)
            assertEquals("distance a $x", 1000.0, p.doneM + p.remainingM, 1e-6)
            assertEquals("montee a $x", 200.0, p.doneAscentM + p.remainingAscentM, 1e-6)
            assertEquals("descente a $x", 150.0, p.doneDescentM + p.remainingDescentM, 1e-6)
        }
    }

    /** Une position hors de la trace - avant son debut, apres sa fin - est ramenee sur elle : le suivi
     *  mesure une trace, pas la position dans le vide. */
    @Test fun `une position hors de la trace est ramenee sur elle`() {
        assertEquals(0.0, au(-500.0).doneM, 1e-6)
        assertEquals(1000.0, au(5_000.0).doneM, 1e-6)
    }

    // ---------- Le temps ----------

    @Test fun `le temps ecoule se compte depuis le debut du suivi`() {
        assertEquals(600_000L, au(500.0, elapsedMs = 600_000L).elapsedMs)
    }

    /**
     * L'estimation part de la vitesse MOYENNE, non de l'instantanee : celle-ci tombe a zero a chaque arret
     * et annoncerait l'infini, quand la moyenne porte deja les arrets et les montees qu'on a faits.
     *
     * 500 m en 10 min, soit 50 m/min : les 500 m restants demandent 10 min de plus.
     */
    @Test fun `le temps restant suit la vitesse moyenne tenue`() {
        val p = au(500.0, elapsedMs = 600_000L, speed = 0f)
        assertEquals(600_000.0, p.etaMs!!.toDouble(), 1_000.0)
    }

    /** Trop tot pour estimer : en deca d'une minute, la moyenne est dominee par le demarrage et
     *  annoncerait des heures au hasard. Un tiret vaut mieux qu'un chiffre faux. */
    @Test fun `un suivi trop jeune ne s'estime pas`() {
        assertNull(au(50.0, elapsedMs = 10_000L).etaMs)
    }

    /** Pas encore avance : rien a extrapoler. */
    @Test fun `sans avoir avance, rien ne s'estime`() {
        assertNull(au(0.0, elapsedMs = 600_000L).etaMs)
    }

    /** Arrive : zero, et non "on ne sait pas". */
    @Test fun `a l'arrivee le temps restant est nul`() {
        assertEquals(0L, au(1000.0).etaMs)
    }

    /** La vitesse instantanee traverse telle quelle : c'est la seule des neuf valeurs qui vienne du
     *  capteur et non de la trace. */
    @Test fun `la vitesse instantanee est celle du capteur`() {
        assertEquals(4.5f, au(500.0, speed = 4.5f).speedMps)
        assertNull(au(500.0, speed = null).speedMps)
    }

    /** Une trace trop courte pour avoir une longueur ne fait pas lever : elle rend des zeros, et le
     *  tableau de bord affiche des zeros plutot que de disparaitre. */
    @Test fun `une trace vide ne leve pas`() {
        val p = FollowProgressMath.of(emptyList(), 0.0, null, 0L, 1_000L)
        assertEquals(0.0, p.remainingM, 1e-6)
        assertEquals(1_000L, p.elapsedMs)
    }

    /** Une horloge qui recule - reprise apres coup, valeur reposee - ne rend pas une duree negative. */
    @Test fun `une duree ne devient jamais negative`() {
        val p = FollowProgressMath.of(trace, 0.0, null, startedAtMs = 5_000L, nowMs = 1_000L)
        assertTrue(p.elapsedMs >= 0L)
    }
}
