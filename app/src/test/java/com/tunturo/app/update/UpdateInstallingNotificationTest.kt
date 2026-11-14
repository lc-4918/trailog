package com.tunturo.app.update

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tunturo.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La notification d'attente pendant l'installation d'une mise a jour : elle dit d'attendre la fin avant de
 * rouvrir l'application, porte un spinner, ne fait pas de bruit, et ne reste pas affichee pour toujours.
 */
@RunWith(RobolectricTestRunner::class)
class UpdateInstallingNotificationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notification = UpdateDone.installingNotification(context)

    @Test fun `elle dit qu'il faut attendre la fin de l'installation`() {
        assertEquals(context.getString(R.string.update_installing_title),
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(context.getString(R.string.update_installing_text),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test fun `elle porte un spinner et reste affichee pendant l'installation`() {
        assertTrue("spinner", notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertTrue("permanente", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    /** Les fleches en cercle de l'attente, et non la coche de la mise a jour terminee. */
    @Test fun `son icone n'est pas celle de la fin de l'installation`() {
        assertEquals(R.drawable.ic_stat_update_installing, notification.smallIcon.resId)
        assertNotEquals(R.drawable.ic_stat_update, notification.smallIcon.resId)
    }

    @Test fun `elle disparait d'elle-meme si l'installation n'aboutit pas`() {
        assertEquals(UpdateDone.INSTALLING_TIMEOUT_MS, notification.timeoutAfter)
    }
}
