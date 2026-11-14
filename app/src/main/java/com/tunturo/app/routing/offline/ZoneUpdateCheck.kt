package com.tunturo.app.routing.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.tunturo.app.MainActivity
import com.tunturo.app.R
import com.tunturo.app.ui.settings.zoneNameRes

/**
 * La verification du lancement : consulter le serveur, et notifier les zones telechargees qui ont une
 * mise a jour (cf. [ZoneUpdateNotice] pour quand, et pourquoi).
 *
 * Toucher la notification ouvre les reglages sur les zones d'itineraire, ou chaque zone perimee porte sa
 * pastille et son bouton "Mettre a jour". Rien n'est telecharge sans qu'on le demande : un carre pese
 * jusqu'a deux cent cinquante megaoctets, et le reseau du moment n'est peut-etre pas le bon.
 */
object ZoneUpdateCheck {
    private const val CHANNEL_ID = "zone_updates"

    /** Identifiant propre : 4918 a 4931 et 4940 sont deja pris par d'autres notifications de l'application. */
    private const val NOTIFICATION_ID = 4941

    /** Action de l'intention de la notification : l'activite ouvre les zones d'itineraire. */
    const val ACTION_OPEN_ZONES = "com.tunturo.app.routing.OPEN_ZONES"

    private const val PREFS = "zone_updates"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_LAST_NOTIFIED = "last_notified_version"

    suspend fun runIfDue(context: Context, downloads: BrouterDownloads, nowMs: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!ZoneUpdateNotice.checkDue(prefs.getLong(KEY_LAST_CHECK, 0L), nowMs)) return
        // Rien de telecharge : rien a verifier, et pas de requete pour rien.
        if (downloads.state.value.zones.isEmpty()) return
        val state = downloads.refreshRemoteNow() ?: return
        prefs.edit { putLong(KEY_LAST_CHECK, nowMs) }
        val outdated = ZoneUpdateNotice.outdatedZones(state)
        if (outdated.isEmpty()) {
            cancel(app)
            return
        }
        val version = ZoneUpdateNotice.serverVersion(state, outdated)
        if (!ZoneUpdateNotice.shouldNotify(version, prefs.getLong(KEY_LAST_NOTIFIED, 0L))) return
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return
        (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, app.getString(R.string.routing_zone_updates_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val names = outdated.map { app.getString(zoneNameRes(it)) }
        if (runCatching { manager.notify(NOTIFICATION_ID, notification(app, names)) }.isSuccess) {
            prefs.edit { putLong(KEY_LAST_NOTIFIED, version) }
        }
    }

    /** La notification elle-meme, a part pour etre verifiee sans serveur ni autorisation. */
    internal fun notification(context: Context, zoneNames: List<String>): Notification {
        val open = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_ZONES)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = context.getString(R.string.routing_zone_updates_text, zoneNames.joinToString(", "))
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zone_update)
            .setContentTitle(context.getString(R.string.routing_zone_updates_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
}
