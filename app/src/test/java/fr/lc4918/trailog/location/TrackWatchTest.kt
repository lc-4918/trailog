package fr.lc4918.trailog.location

import fr.lc4918.trailog.domain.model.Sample
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La veille sur la trace suivie : ce qui declenche l'alerte, ce qui la tait, et ce qui la rearme.
 *
 * Cet etat vit hors de l'ecran depuis que le suivi tourne dans un service (cf. [LocationService]) : c'est
 * lui, et non la composition, qui decide qu'il faut sonner. Une faute ici ne se voit pas a l'ecran - elle
 * se constate a dix kilometres, quand rien n'a prevenu.
 */
class TrackWatchTest {

    private val trace = TrackWatch.Followed(
        layerId = 1, layerName = "GR 9", trackIndex = 0, trackCount = 1,
        samples = listOf(Sample(0.0, 0.0, 0.0, null, 5.7, 45.2)),
    )

    @After fun raz() = TrackWatch.stop()

    @Test fun `sans trace suivie, rien n'est en alerte`() {
        assertEquals(null, TrackWatch.followed.value)
        assertFalse(TrackWatch.alerting.value)
    }

    /** L'ecart de la premiere mesure est connu d'emblee : c'est celui qui a servi a classer les candidates. */
    @Test fun `la trace retenue arrive avec son ecart`() {
        TrackWatch.follow(trace, awayM = 12.0, thresholdM = 50.0)
        assertEquals(12.0, TrackWatch.awayM.value!!, 1e-9)
        assertFalse(TrackWatch.alerting.value)
    }

    /** Une trace reconnue part cloche eteinte : rien ne sonne tant qu'on ne l'a pas armee soi-meme. */
    @Test fun `une trace reconnue part sans alerte`() {
        TrackWatch.follow(trace, awayM = 80.0, thresholdM = 50.0)
        assertFalse(TrackWatch.armed.value)
        assertFalse(TrackWatch.alerting.value)
    }

    /** Le son accompagne l'ENTREE en alerte, une fois : il annonce le franchissement, il ne sonne pas tant
     *  qu'on est loin. C'est ce que dit la valeur rendue par update. */
    @Test fun `seule l'entree en alerte est annoncee`() {
        TrackWatch.follow(trace, awayM = 10.0, thresholdM = 50.0)
        assertTrue("le franchissement s'annonce", TrackWatch.update(60.0, 50.0))
        assertFalse("rester loin ne s'annonce pas", TrackWatch.update(120.0, 50.0))
    }

    /**
     * La marge de retour : l'alerte se declenche au seuil et ne se lache qu'a 80 % de celui-ci. Sans elle,
     * une position qui oscille autour du seuil rallumerait la banniere toutes les deux secondes.
     */
    @Test fun `la zone morte empeche le clignotement`() {
        TrackWatch.follow(trace, awayM = 60.0, thresholdM = 50.0)
        TrackWatch.update(60.0, 50.0)
        TrackWatch.update(45.0, 50.0)
        assertTrue("entre 40 et 50 m, rien ne change", TrackWatch.alerting.value)
        TrackWatch.update(39.0, 50.0)
        assertFalse(TrackWatch.alerting.value)
    }

    /** La croix tait l'ecart du moment, elle n'arrete pas le suivi. */
    @Test fun `le silence laisse le suivi en place`() {
        TrackWatch.follow(trace, awayM = 80.0, thresholdM = 50.0)
        TrackWatch.update(80.0, 50.0)
        TrackWatch.silence()
        assertTrue(TrackWatch.silenced.value)
        assertTrue(TrackWatch.alerting.value)
        assertEquals(trace, TrackWatch.followed.value)
    }

    /** Revenir sur la trace leve le silence : l'alerte suivante se dira. */
    @Test fun `revenir sous le seuil rearme l'annonce`() {
        TrackWatch.follow(trace, awayM = 80.0, thresholdM = 50.0)
        TrackWatch.update(80.0, 50.0)
        TrackWatch.silence()
        TrackWatch.update(10.0, 50.0)
        assertFalse(TrackWatch.silenced.value)
        assertTrue("et le franchissement suivant s'annonce", TrackWatch.update(60.0, 50.0))
    }

    /** Fin du suivi : plus de trace, plus d'ecart, plus d'alerte, plus de silence. */
    @Test fun `l'arret efface tout`() {
        TrackWatch.follow(trace, awayM = 80.0, thresholdM = 50.0)
        TrackWatch.update(80.0, 50.0)
        TrackWatch.silence()
        TrackWatch.stop()
        assertEquals(null, TrackWatch.followed.value)
        assertEquals(null, TrackWatch.awayM.value)
        assertFalse(TrackWatch.alerting.value)
        assertFalse(TrackWatch.silenced.value)
    }

    // ---------- La reprise apres une mort du processus ----------

    /**
     * Le service est relance par le systeme (START_STICKY) apres qu'il a repris sa memoire : la trace
     * retrouvee sur le disque redevient la trace suivie.
     *
     * Sans cela, le capteur repartait et la notification aussi, mais plus personne ne veillait - l'alerte
     * d'eloignement se retrouvait desarmee SANS UN MOT, telephone en poche.
     */
    @Test fun `la trace retrouvee redevient la trace suivie`() {
        TrackWatch.restore(trace)
        assertEquals("GR 9", TrackWatch.followed.value?.layerName)
        assertEquals(trace.samples, TrackWatch.followed.value?.samples)
    }

    /** L'ecart et l'alerte repartent a zero : ils se mesurent a la prochaine position, et celui d'avant
     *  l'arret ne dit rien d'ou l'on est maintenant. */
    @Test fun `la reprise ne rejoue pas l'ecart d'avant`() {
        TrackWatch.restore(trace)
        assertEquals(null, TrackWatch.awayM.value)
        assertFalse(TrackWatch.alerting.value)
        assertFalse(TrackWatch.silenced.value)
    }

    /**
     * Elle ne s'impose JAMAIS a un suivi en cours.
     *
     * Le service peut etre relance alors que l'ecran vient de designer une autre trace : la trace d'hier
     * ne doit pas reprendre la place de celle qu'on vient de choisir.
     */
    @Test fun `la reprise ne recouvre pas une trace deja choisie`() {
        TrackWatch.follow(trace.copy(layerId = 2, layerName = "GR 5"), awayM = 5.0, thresholdM = 50.0)
        TrackWatch.restore(trace)
        assertEquals("GR 5", TrackWatch.followed.value?.layerName)
        assertEquals(5.0, TrackWatch.awayM.value!!, 1e-9)
    }

    /** Rien a reprendre : le fichier n'existait pas, ou ne se relit plus. */
    @Test fun `une reprise vide ne change rien`() {
        TrackWatch.restore(null)
        assertEquals(null, TrackWatch.followed.value)
    }

    /** Changer de trace repart d'une alerte vierge : l'ecart de la precedente n'a rien a dire de celle-ci. */
    @Test fun `changer de trace repart d'une alerte vierge`() {
        TrackWatch.follow(trace, awayM = 80.0, thresholdM = 50.0)
        TrackWatch.update(80.0, 50.0)
        TrackWatch.silence()
        TrackWatch.follow(trace.copy(layerId = 2, layerName = "GR 5"), awayM = 5.0, thresholdM = 50.0)
        assertFalse(TrackWatch.alerting.value)
        assertFalse(TrackWatch.silenced.value)
        assertEquals("GR 5", TrackWatch.followed.value?.layerName)
    }

    // ---------- La cloche et le suivi automatique ----------

    /** Cloche armee : s'ecarter est l'alerte, annoncee a l'entree, et le suivi continue. */
    @Test fun `cloche armee, s'ecarter alerte`() {
        TrackWatch.follow(trace, awayM = 5.0, thresholdM = 50.0, alongM = 100.0)
        TrackWatch.setArmed(true)
        assertEquals(TrackWatch.Step.Alert, TrackWatch.step(80.0, 50.0, 110.0))
        assertEquals(TrackWatch.Step.Stay, TrackWatch.step(90.0, 50.0, 120.0))
        assertTrue(TrackWatch.alerting.value)
        assertEquals(trace, TrackWatch.followed.value)
    }

    /** Cloche eteinte : s'ecarter ne sonne pas, et deux positions au-dela du seuil lachent la trace. */
    @Test fun `cloche eteinte, s'ecarter lache la trace sans alerte`() {
        TrackWatch.follow(trace, awayM = 5.0, thresholdM = 50.0, alongM = 100.0)
        assertEquals(TrackWatch.Step.Stay, TrackWatch.step(80.0, 50.0, 110.0))
        assertFalse(TrackWatch.alerting.value)
        assertEquals(TrackWatch.Step.Leave, TrackWatch.step(90.0, 50.0, 120.0))
    }

    /** Une seule mesure aberrante, puis retour sur la trace : on la garde. */
    @Test fun `un ecart isole ne lache pas la trace`() {
        TrackWatch.follow(trace, awayM = 5.0, thresholdM = 50.0, alongM = 100.0)
        TrackWatch.step(80.0, 50.0, 110.0)
        TrackWatch.step(10.0, 50.0, 120.0)
        assertEquals(TrackWatch.Step.Stay, TrackWatch.step(80.0, 50.0, 130.0))
    }

    /** Desarmer la cloche en pleine alerte tait l'alerte. */
    @Test fun `desarmer tait l'alerte en cours`() {
        TrackWatch.follow(trace, awayM = 5.0, thresholdM = 50.0, alongM = 100.0)
        TrackWatch.setArmed(true)
        TrackWatch.step(80.0, 50.0, 110.0)
        TrackWatch.setArmed(false)
        assertFalse(TrackWatch.alerting.value)
    }

    /** Le sens de parcours suit le kilometrage : reculer franchement sur la trace, c'est la remonter. */
    @Test fun `le sens suit le kilometrage`() {
        TrackWatch.follow(trace, awayM = 5.0, thresholdM = 50.0, alongM = 100.0)
        TrackWatch.step(5.0, 50.0, 90.0)
        assertEquals(-1, TrackWatch.direction.value)
        TrackWatch.step(5.0, 50.0, 92.0)
        assertEquals("un pas de deux metres ne dit rien", -1, TrackWatch.direction.value)
    }

    /** Fermer le tableau de bord arrete le suivi : il n'aurait plus nulle part ou se dire. */
    @Test fun `fermer le tableau de bord arrete le suivi`() {
        TrackWatch.setDashboard(true)
        TrackWatch.follow(trace, awayM = 5.0, thresholdM = 50.0)
        TrackWatch.setArmed(true)
        TrackWatch.setDashboard(false)
        assertEquals(null, TrackWatch.followed.value)
        assertFalse(TrackWatch.armed.value)
    }

    /** Marcher sur une trace candidate la fait suivre, dans le sens de la marche, cloche eteinte. */
    @Test fun `la detection commence le suivi`() {
        val pts = (0..100).map { i -> Sample(i * 10.0, 0.0, 0.0, null, 6.0, 45.0 + i * 10.0 / 111_195.0) }
        val c = FollowCatalog.candidate(7, "GR 5", 0, 1, pts)
        var suivi = false
        for (m in listOf(800.0, 750.0, 700.0, 650.0)) {
            suivi = suivi || TrackWatch.detect(45.0 + m / 111_195.0, 6.0, listOf(c), 50.0, 0L)
        }
        assertTrue(suivi)
        assertEquals("GR 5", TrackWatch.followed.value?.layerName)
        assertEquals(-1, TrackWatch.direction.value)
        assertFalse(TrackWatch.armed.value)
    }

    // ---------- La cloche a travers une coupure ----------

    /** Localisation coupee puis rallumee : la meme trace, reconnue a nouveau, retrouve sa cloche armee. */
    @Test fun `la cloche survit a une coupure`() {
        TrackWatch.follow(trace, awayM = 5.0, thresholdM = 50.0)
        TrackWatch.setArmed(true)
        TrackWatch.stop(forgetBell = false)
        assertFalse(TrackWatch.armed.value)
        TrackWatch.follow(trace, awayM = 5.0, thresholdM = 50.0)
        assertTrue(TrackWatch.armed.value)
    }

    /** Une autre trace reconnue apres la coupure ne s'arme pas pour autant. */
    @Test fun `la cloche ne passe pas a une autre trace`() {
        TrackWatch.follow(trace, awayM = 5.0, thresholdM = 50.0)
        TrackWatch.setArmed(true)
        TrackWatch.stop(forgetBell = false)
        TrackWatch.follow(trace.copy(layerId = 2, layerName = "GR 5"), awayM = 5.0, thresholdM = 50.0)
        assertFalse(TrackWatch.armed.value)
    }

    /** Un arret voulu - fermer le tableau de bord, desarmer - oublie la cloche. */
    @Test fun `un arret voulu oublie la cloche`() {
        TrackWatch.follow(trace, awayM = 5.0, thresholdM = 50.0)
        TrackWatch.setArmed(true)
        TrackWatch.stop()
        TrackWatch.follow(trace, awayM = 5.0, thresholdM = 50.0)
        assertFalse(TrackWatch.armed.value)
        assertEquals(null, TrackWatch.bellKey.value)
    }

    /** Apres une mort du processus : la trace et sa cloche reprises du disque, dans un ordre ou l'autre. */
    @Test fun `la cloche reprise du disque arme la trace reprise`() {
        TrackWatch.restoreBell("1/0")
        TrackWatch.restore(trace)
        assertTrue(TrackWatch.armed.value)
        TrackWatch.stop()
        TrackWatch.restore(trace)
        TrackWatch.restoreBell("1/0")
        assertTrue(TrackWatch.armed.value)
    }
}
