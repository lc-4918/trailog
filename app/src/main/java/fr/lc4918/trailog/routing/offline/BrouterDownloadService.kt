package fr.lc4918.trailog.routing.offline

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
import fr.lc4918.trailog.map.offline.TileMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Le service de premier plan qui laisse les donnees d'itineraire se telecharger ecran eteint, ou
 * l'application fermee.
 *
 * **Il ne telecharge rien lui-meme** : la file vit dans l'application (cf. [BrouterDownloads]). Il la tient
 * seulement en vie - un processus sans service de premier plan est tue en quelques minutes une fois
 * l'application quittee, et une zone de neuf cents megaoctets en demande bien plus. Sa notification dit ou
 * en est le transfert, et il s'arrete de lui-meme quand la file est vide.
 *
 * S'il est tue malgre tout - memoire, redemarrage -, Android le relance (START_STICKY), et il reprend les
 * zones en cours la ou elles s'etaient arretees : leur liste est ecrite sur disque, et le debut de chaque
 * carre est garde.
 */
class BrouterDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var suivi: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val data = (application as TrailogApp).brouterData
        // La notification d'abord : Android tue un service qui se declare trop tard en premier plan. Sous
        // garde - un demarrage refuse (arriere-plan, Android 12+) ne doit pas emporter l'application.
        val declare = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, notification(null))
            }
        }.isSuccess
        if (!declare) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Relance apres un arret : rien n'est en cours dans ce processus neuf, mais le disque sait quoi
        // reprendre.
        if (data.state.value.pending.isEmpty()) data.resumePending()
        if (suivi == null) {
            suivi = scope.launch {
                data.state
                    .map { it.pending.isEmpty() to it.overall()?.let { (a, b) -> a / 1_000_000 to b } }
                    .distinctUntilChanged()
                    .collect { (fini, progression) ->
                        if (fini) {
                            annoncerFin(data.state.value.finished)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            val nm = getSystemService(NotificationManager::class.java)
                            nm?.notify(NOTIF_ID, notification(progression?.let { (mo, total) -> mo * 1_000_000 to total }))
                        }
                    }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ouvrir(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notification(progression: Pair<Long, Long?>?): Notification {
        ensureChannel()
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.routing_download_notice_title))
            .setContentIntent(ouvrir())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        val (recu, attendu) = progression ?: (0L to null)
        if (attendu != null && attendu > 0) {
            b.setProgress(1000, ((recu * 1000) / attendu).toInt().coerceIn(0, 1000), false)
            b.setContentText(getString(R.string.routing_zone_downloading, TileMath.formatSize(recu), TileMath.formatSize(attendu)))
        } else {
            b.setProgress(0, 0, true)
        }
        return b.build()
    }

    /** La fin, dite dans le volet : l'application est peut-etre fermee, et la carte ne la montrera pas. */
    private fun annoncerFin(fins: List<BrouterDownloads.Finished>) {
        if (fins.isEmpty()) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val ok = fins.all { it.ok }
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(
                if (ok) R.string.routing_download_done_title else R.string.routing_download_failed_title))
            .setContentIntent(ouvrir())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        nm.notify(DONE_NOTIF_ID, n)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.routing_download_channel_name),
                NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
    }

    companion object {
        private const val CHANNEL_ID = "donnees-itineraire"
        private const val NOTIF_ID = 4920
        private const val DONE_NOTIF_ID = 4921

        /** Demarre le service. Sans effet s'il tourne deja. */
        fun start(ctx: Context) {
            val intent = Intent(ctx, BrouterDownloadService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
                else ctx.startService(intent)
            }
        }
    }
}
