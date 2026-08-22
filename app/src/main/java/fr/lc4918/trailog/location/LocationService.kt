package fr.lc4918.trailog.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import fr.lc4918.trailog.MainActivity
import fr.lc4918.trailog.R
import fr.lc4918.trailog.TrailogApp
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.data.db.offTrackAlertVisible
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.domain.geo.TrackMeasure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Le suivi de position, hors de l'ecran.
 *
 * **Pourquoi un service de premier plan.** Le capteur etait ecoute depuis la carte : l'ecran eteint, la
 * composition s'arretait et le suivi avec elle. C'est l'inverse de l'usage vise - le telephone finit en
 * poche ou sur un guidon qui met l'ecran en veille au bout d'une minute, et c'est la que la position sert
 * le plus. Un service de premier plan est le seul moyen qu'Android offre de tenir le capteur ouvert dans
 * cette situation, et sa notification permanente est le prix a payer : elle dit exactement ce qui tourne,
 * et l'arret reste a un tap.
 *
 * C'est aussi ce sur quoi reposera un enregistreur de trace : ecrire un point toutes les deux secondes
 * demande la meme chose.
 *
 * **Sans autorisation d'arriere-plan** (`ACCESS_BACKGROUND_LOCATION`) : un service de premier plan de type
 * `location`, demarre alors que l'application est visible, lit le capteur sans elle. La demander serait
 * reclamer beaucoup plus que ce dont on se sert - le suivi ne demarre jamais que d'un geste.
 *
 * **La veille sur la trace suivie tourne ici aussi** (cf. [TrackWatch]), et pas seulement le capteur :
 * une alerte d'eloignement qui ne se declenche que sous les yeux de celui qu'elle doit prevenir
 * n'alerterait personne.
 */
class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var locationManager: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null

    /** Ce que la notification annonce, recalcule a chaque position et a chaque changement de trace. */
    private val notice = MutableStateFlow<String?>(null)

    private val listener = LocationListener { loc -> LocationHub.publish(loc) }

    /** Derniere version connue des reglages (seuil d'alerte, son, unites). */
    @Volatile private var settings: SettingsEntity? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        watchSettings()
        watchTrack()
        watchNotice()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // L'autorisation AVANT tout le reste : depuis Android 14, se declarer en premier plan de type
        // "location" sans elle leve une SecurityException - et le systeme peut nous relancer (START_STICKY)
        // longtemps apres que l'utilisateur l'a retiree.
        if (!hasPermission() || enabledProvider() == null) {
            // Rien a ecouter, et un service qui tourne pour rien laisserait une notification mensongere.
            stopSelf()
            return START_NOT_STICKY
        }
        // Puis la notification, AVANT le capteur : Android tue un service qui se declare en premier plan
        // trop tard, et une position recue entre-temps n'aurait personne pour la lire. Sous garde malgre
        // tout - un demarrage refuse (arriere-plan, Android 12+) ne doit pas emporter l'application.
        val declare = runCatching { startForeground(NOTIF_ID, notification(notice.value)) }.isSuccess
        if (!declare || !subscribe()) {
            stopSelf()
            return START_NOT_STICKY
        }
        LocationHub.setTracking(true)
        // Le systeme peut nous relancer apres avoir eu besoin de memoire : le suivi reprend alors seul,
        // ce qu'on attend d'une fonction qu'on a demandee pour la duree d'une sortie.
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(listener) }
        releaseWakeLock()
        LocationHub.setTracking(false)
        TrackWatch.stop()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * L'application balayee des recentes alors que le suivi tourne.
     *
     * On s'arrete : le suivi est une fonction de l'application, pas un agent qui lui survit. La position
     * consomme la batterie et se lit dans la notification - la laisser tourner apres que l'utilisateur a
     * ferme l'application serait exactement ce qu'on reproche aux applications qui le font.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    // ---------- capteur ----------

    @SuppressLint("MissingPermission")
    private fun subscribe(): Boolean {
        if (!hasPermission()) return false
        val provider = enabledProvider() ?: return false
        return runCatching {
            locationManager.requestLocationUpdates(provider, INTERVAL_MS, MIN_DISTANCE_M, listener, mainLooper)
            // La derniere position connue tout de suite : le systeme la tient deja, et l'attendre laisserait
            // la carte sans repere pendant les secondes que le GPS met a se fixer.
            locationManager.getLastKnownLocation(provider)?.let { LocationHub.publish(it) }
            acquireWakeLock()
            true
        }.getOrDefault(false)
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun enabledProvider(): String? = when {
        !LocationManagerCompat.isLocationEnabled(locationManager) -> null
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    /**
     * Le verrou processeur, en plus du service.
     *
     * Le premier plan garantit que le processus n'est pas tue ; il ne garantit pas que le processeur reste
     * eveille entre deux mesures. Sans ce verrou, l'appareil endormi ne traite les positions que par
     * salves, au reveil - suffisant pour un repere sur une carte que personne ne regarde, insuffisant pour
     * une alerte qui doit sonner au moment ou l'on quitte le chemin, et pour un enregistrement qui doit
     * porter des points reguliers.
     */
    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = runCatching {
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    // ---------- veille sur la trace suivie ----------

    /**
     * L'ecart a la trace suivie, une position de plus, et le son a l'entree en alerte.
     *
     * Sur [Dispatchers.Default] : c'est un balayage de toute la trace, celui-la meme que fait la ligne du
     * restant du profil, et il n'a rien a faire sur le fil principal.
     */
    private fun watchTrack() = scope.launch {
        LocationHub.fix.filterNotNull().collect { fix ->
            val followed = TrackWatch.followed.value
            if (followed == null) { notice.value = null; return@collect }
            val reglages = settings
            val seuil = (reglages?.offTrackAlertDistanceM ?: DEFAULT_ALERT_M).toDouble()
            val away = TrackMeasure.project(followed.samples, fix.lon, fix.lat)?.awayM ?: return@collect
            val entree = TrackWatch.update(away, seuil)
            notice.value = getString(
                R.string.location_notice_following,
                followed.layerName,
                Format.shortDistance(away, reglages?.units == "imperial"),
            )
            if (entree && reglages?.offTrackAlertSound == true) {
                playAlertSound(this@LocationService, reglages.offTrackAlertSoundUri)
            }
            // Le reglage eteint pendant le suivi : la trace n'est plus suivie de personne.
            if (reglages != null && !reglages.offTrackAlertVisible) TrackWatch.stop()
        }
    }

    /**
     * Les reglages, gardes a jour plutot que relus a chaque position : une lecture par mesure serait une
     * requete Room toutes les deux secondes pour une ligne qui ne change qu'au reglage.
     */
    private fun watchSettings() = scope.launch {
        (application as TrailogApp).repository.settingsFlow.collect { settings = it }
    }

    /** La notification suit ce qu'elle a a dire, sans etre reposee a chaque position quand rien ne change. */
    private fun watchNotice() = scope.launch {
        notice.collect { texte ->
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return@collect
            runCatching { nm.notify(NOTIF_ID, notification(texte)) }
        }
    }

    // ---------- notification ----------

    private fun notification(texte: String?): Notification {
        ensureChannel()
        val ouvrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val arreter = PendingIntent.getService(
            this, 1, Intent(this, LocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle(getString(R.string.location_notice_title))
            .setContentText(texte ?: getString(R.string.location_notice_idle))
            .setContentIntent(ouvrir)
            .addAction(0, getString(R.string.location_notice_stop), arreter)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        // Importance basse : la notification doit se lire dans le volet, jamais s'imposer par-dessus la
        // carte. Le son de l'alerte, lui, ne passe pas par elle (cf. playAlertSound).
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.location_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
    }

    companion object {
        private const val CHANNEL_ID = "suivi-position"
        private const val NOTIF_ID = 4918
        private const val WAKE_LOCK_TAG = "trailog:suivi-position"
        const val ACTION_STOP = "fr.lc4918.trailog.STOP_LOCATION"

        /** Les memes cadences qu'avant le service : deux secondes, cinq metres. */
        private const val INTERVAL_MS = 2000L
        private const val MIN_DISTANCE_M = 5f

        /** Repli quand les reglages ne sont pas encore lus - la meme valeur que leur defaut. */
        private const val DEFAULT_ALERT_M = 50

        /**
         * Demarre le suivi. Sans effet si le service tourne deja : un second demarrage ne fait que
         * reposer la meme notification.
         */
        fun start(ctx: Context) {
            val intent = Intent(ctx, LocationService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
                else ctx.startService(intent)
            }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, LocationService::class.java)) }
        }
    }
}
