package fr.lc4918.trailog.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quand annoncer la fin d'une mise a jour. */
class UpdateDoneTest {

    @Test fun `une release avec l'autorisation de notifier annonce`() {
        assertTrue(UpdateDone.shouldNotify(isDebugBuild = false, notificationsAllowed = true))
    }

    @Test fun `sans l'autorisation de notifier, rien`() {
        assertFalse(UpdateDone.shouldNotify(isDebugBuild = false, notificationsAllowed = false))
    }

    @Test fun `un build debug n'annonce jamais`() {
        assertFalse(UpdateDone.shouldNotify(isDebugBuild = true, notificationsAllowed = true))
    }
}
