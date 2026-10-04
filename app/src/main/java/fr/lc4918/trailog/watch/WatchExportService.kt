package fr.lc4918.trailog.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import fr.lc4918.trailog.MainActivity
import fr.lc4918.trailog.R
import fr.lc4918.trailog.TrailogApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Le service de premier plan qui garde l'envoi vers la montre ouvert, Trailog en arriere-plan.
 *
 * La montre ne synchronise que lorsqu'on est passe sur elle : sans ce service, Android tuerait le processus
 * - et le serveur avec lui - avant qu'elle ait tout recu. Il ne sert rien lui-meme ([WatchExportSession]),
 * dit ou en est la montre dans sa notification, et s'arrete avec l'envoi : tout recu, delai d'inactivite,
 * ou bouton "Arreter".
 */
class WatchExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateWatcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = (application as TrailogApp).watchExport
        if (intent?.action == ACTION_STOP) {
            session.stop()
            return START_NOT_STICKY
        }
        // La notification d'abord : Android tue un service qui se declare trop tard en premier plan.
        val declared = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(session.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification(session.state.value))
            }
        }.isSuccess
        if (!declared) {
            session.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        if (stateWatcher == null) {
            stateWatcher = scope.launch {
                session.state.collect { state ->
                    if (state == null || state.complete) {
                        if (state != null) {
                            announceCompletion(state)
                            session.stop()
                        }
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(state))
                    }
                }
            }
        }
        // Un processus relance n'a plus de serveur ouvert : rien a reprendre, la montre redemandera.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this, 1, Intent(this, WatchExportService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notification(state: WatchExportSession.State?): Notification {
        ensureChannel()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.watch_send_notice_title, state?.name.orEmpty()))
            .setContentIntent(openApp())
            .addAction(0, getString(R.string.watch_send_stop), stopIntent())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (state == null || state.servedCount == 0) {
            builder.setContentText(getString(R.string.watch_send_waiting))
            builder.setProgress(0, 0, true)
        } else {
            builder.setContentText(getString(R.string.watch_send_progress, state.servedCount, state.tileCount))
            builder.setProgress(state.tileCount, state.servedCount, false)
        }
        return builder.build()
    }

    /** La fin, dite dans le volet : on regarde la montre, pas le telephone. */
    private fun announceCompletion(state: WatchExportSession.State) {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        val notice = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(getString(R.string.watch_send_done_title))
            .setContentText(getString(R.string.watch_send_done_text, state.tileCount, state.name))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(DONE_NOTIFICATION_ID, notice)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.watch_send_channel_name),
                NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
    }

    companion object {
        private const val CHANNEL_ID = "envoi-montre"
        private const val NOTIFICATION_ID = 4930
        private const val DONE_NOTIFICATION_ID = 4931
        private const val ACTION_STOP = "fr.lc4918.trailog.watch.STOP"

        /** Demarre le service. Sans effet s'il tourne deja. */
        fun start(context: Context) {
            val intent = Intent(context, WatchExportService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }
    }
}
