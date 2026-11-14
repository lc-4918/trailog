package fr.lc4918.trailog.routing.offline

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** La notification d'une zone a mettre a jour : elle nomme les zones, et ouvre l'ecran qui les met a jour. */
@RunWith(RobolectricTestRunner::class)
class ZoneUpdateNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notification = ZoneUpdateCheck.notification(context, listOf("France", "Alpes"))

    @Test fun `elle nomme les zones a mettre a jour`() {
        assertEquals(context.getString(R.string.routing_zone_updates_title),
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertEquals(context.getString(R.string.routing_zone_updates_text, "France, Alpes"), text)
        assertTrue(text.startsWith("France, Alpes"))
    }

    @Test fun `la toucher ouvre les zones d'itineraire et la retire`() {
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(ZoneUpdateCheck.ACTION_OPEN_ZONES, intent.action)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test fun `elle a sa propre icone`() {
        assertEquals(R.drawable.ic_stat_zone_update, notification.smallIcon.resId)
    }
}
