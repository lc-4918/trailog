package fr.lc4918.trailog.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le suivi abonne au capteur, et plus une seule position.
 *
 * **Ce test existe a cause d'une sortie reelle.** Mode economie d'energie, ecran eteint : le telephone
 * eteint le GPS sans couper la localisation, donc sans rien annoncer. L'application se croyait en
 * marche, la carte aussi, et l'alerte d'eloignement n'a sonne qu'au rallumage de l'ecran - a dix fois
 * le seuil. Rien ne surveillait le silence, parce que rien ne l'annoncait.
 */
class FixWatchdogTest {

    private fun apres(ms: Long) = ms

    /** Deux delais : on tente d'abord de reprendre la main, on n'annonce qu'ensuite. */
    @Test fun `le silence fait d'abord se rabonner, puis alerte`() {
        var etat = FixWatchdog.onFix(0L)

        val (a, rien) = FixWatchdog.check(etat, apres(10_000L), walking = true)
        etat = a
        assertEquals("dix secondes de silence ne veulent rien dire", FixWatchdog.Action.None, rien)

        val (b, reabonnement) = FixWatchdog.check(etat, apres(FixWatchdog.RESUBSCRIBE_MS), walking = true)
        etat = b
        assertEquals(FixWatchdog.Action.Resubscribe, reabonnement)

        val (c, encore) = FixWatchdog.check(etat, apres(FixWatchdog.RESUBSCRIBE_MS + 5_000L), walking = true)
        etat = c
        assertEquals("on ne se rabonne pas toutes les cinq secondes", FixWatchdog.Action.None, encore)

        val (d, alerte) = FixWatchdog.check(etat, apres(FixWatchdog.ALERT_MS), walking = true)
        etat = d
        assertEquals(FixWatchdog.Action.Alert, alerte)

        val (_, apresAlerte) = FixWatchdog.check(etat, apres(FixWatchdog.ALERT_MS + 30_000L), walking = true)
        assertEquals("et l'annonce ne se repose pas sans fin", FixWatchdog.Action.None, apresAlerte)
    }

    /**
     * A la cadence de repos, plus aucune position n'arrive tant qu'on ne bouge pas : c'est voulu, et
     * crier pour cela serait crier a chaque pause.
     */
    @Test fun `au repos, le silence ne dit rien`() {
        val etat = FixWatchdog.onFix(0L)
        val (suite, action) = FixWatchdog.check(etat, apres(FixWatchdog.ALERT_MS * 3), walking = false)
        assertEquals(FixWatchdog.Action.None, action)
        // L'horloge suit le temps qui passe : repartir apres une heure de pause ne doit pas alerter
        // aussitot, faute de la moindre position recue entre-temps.
        val (_, aussitotApres) = FixWatchdog.check(suite, apres(FixWatchdog.ALERT_MS * 3 + 1_000L), walking = true)
        assertEquals(FixWatchdog.Action.None, aussitotApres)
    }

    /** Une position revenue efface le trou : ce qui a ete tente pour lui n'a plus cours. */
    @Test fun `une position revenue remet l'horloge a zero`() {
        var etat = FixWatchdog.onFix(0L)
        etat = FixWatchdog.check(etat, apres(FixWatchdog.ALERT_MS), walking = true).first
        etat = FixWatchdog.onFix(apres(FixWatchdog.ALERT_MS + 1_000L))
        val (_, action) = FixWatchdog.check(etat, apres(FixWatchdog.ALERT_MS + 20_000L), walking = true)
        assertEquals(FixWatchdog.Action.None, action)
    }
}
