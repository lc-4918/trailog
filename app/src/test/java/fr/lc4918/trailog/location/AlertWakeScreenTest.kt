package fr.lc4918.trailog.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quand l'autorisation de reveiller l'ecran se demande.
 *
 * Ce qui se verifie ici est la REGLE, pas le renvoi vers le reglage : celui-la ouvre une page du systeme,
 * qu'aucun test ne peut suivre. La regle, elle, decide d'une boite qui parait sous les yeux, et se trompe
 * de deux facons - ne rien dire quand l'ecran restera noir, ou redemander une autorisation deja donnee.
 */
class AlertWakeScreenTest {

    @Test fun `allumer le son sans l'autorisation previent`() {
        assertTrue(AlertWakeScreen.shouldAsk(turningOn = true, granted = false))
    }

    /** Avant Android 14, `granted` est toujours vrai : la boite ne parait jamais sur ces telephones. */
    @Test fun `l'autorisation deja donnee ne se redemande pas`() {
        assertFalse(AlertWakeScreen.shouldAsk(turningOn = true, granted = true))
    }

    /** Eteindre le son ne demande rien : on vient de renoncer a l'alerte sonore, pas de l'allumer. */
    @Test fun `eteindre le son ne demande rien`() {
        assertFalse(AlertWakeScreen.shouldAsk(turningOn = false, granted = false))
        assertFalse(AlertWakeScreen.shouldAsk(turningOn = false, granted = true))
    }
}
