package fr.lc4918.trailog.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.lc4918.trailog.BuildConfig
import fr.lc4918.trailog.R

/**
 * Annonce la fin d'une mise a jour : le systeme remplace l'appli en la fermant, et l'utilisateur ne sait
 * pas quand elle est de nouveau la. Android 10 interdit de la rouvrir depuis l'arriere-plan ; la
 * notification, elle, se touche.
 */
object UpdateDone {
    private const val CHANNEL_ID = "update_done"
    private const val NOTIFICATION_ID = 4918

    /**
     * Pas en debug - une installation par adb ne mérite pas d'annonce, et le paquet debug n'est jamais mis a
     * jour par ce mecanisme -, et pas sans l'autorisation de notifier.
     */
    fun shouldNotify(isDebugBuild: Boolean, notificationsAllowed: Boolean): Boolean =
        !isDebugBuild && notificationsAllowed

    fun show(context: Context) {
        val app = context.applicationContext
        val manager = NotificationManagerCompat.from(app)
        if (!shouldNotify(BuildConfig.DEBUG, manager.areNotificationsEnabled())) return
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: return
        val open = PendingIntent.getActivity(app, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, app.getString(R.string.update_done_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(app.getString(R.string.update_done_title))
            .setContentIntent(open)
            .addAction(0, app.getString(R.string.update_done_open), open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}

/** Recoit la fin du remplacement de l'appli par une nouvelle version (declare au manifeste, non exporte). */
class UpdateDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) UpdateDone.show(context)
    }
}
