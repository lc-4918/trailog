package fr.lc4918.trailog.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import fr.lc4918.trailog.MainActivity
import fr.lc4918.trailog.R
import fr.lc4918.trailog.TrailogApp
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.data.db.offTrackAlertVisible
import fr.lc4918.trailog.domain.geo.AutoFollow
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.domain.geo.RestDetector
import fr.lc4918.trailog.domain.geo.TrackMeasure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    private val listener = LocationListener { loc ->
        LocationHub.publish(loc)
        pace(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else 0f)
    }

    /** L'arret en cours, ou non (cf. [RestDetector]) : sur le fil principal, celui du listener. */
    private var rest = RestDetector.State()

    /** Les demandes sont espacees : on est a l'arret depuis une minute. */
    private var resting = false

    /**
     * La cadence des demandes, selon qu'on bouge ou non.
     *
     * En marche : toutes les deux secondes, sans distance minimale - la vitesse du tableau de bord reste a
     * jour, meme a pas lent. A l'arret depuis une minute : une position seulement apres [REST_DISTANCE_M]
     * de deplacement. Plus rien n'arrive tant qu'on ne bouge pas, et la premiere position qui arrive dit
     * qu'on est reparti : la cadence de marche revient aussitot.
     */
    @SuppressLint("MissingPermission")
    private fun pace(lat: Double, lon: Double, accuracyM: Float) {
        val (etat, repos) = RestDetector.step(rest, lat, lon, accuracyM, SystemClock.elapsedRealtime())
        rest = etat
        if (repos == resting || !subscribed) return
        resting = repos
        val provider = enabledProvider() ?: return
        runCatching {
            if (repos) locationManager.requestLocationUpdates(provider, REST_INTERVAL_MS, REST_DISTANCE_M, listener, mainLooper)
            else locationManager.requestLocationUpdates(provider, INTERVAL_MS, MIN_DISTANCE_M, listener, mainLooper)
        }
    }

    /** Les couches de la bibliotheque (cf. [watchLayers]). */
    @Volatile private var visibleLayers: List<LayerEntity> = emptyList()

    /** Les traces deja lues, par couche, avec l'entite qui les a donnees (cf. [candidatesNear]). */
    private val layerCache = HashMap<Long, Pair<LayerEntity, List<AutoFollow.Candidate>>>()

    /** Derniere version connue des reglages (seuil d'alerte, son, unites). */
    @Volatile private var settings: SettingsEntity? = null

    /**
     * Pourquoi ce service s'arretera, quand il s'arretera.
     *
     * [Service.onDestroy] ne dit jamais pourquoi il est appele. La raison se pose donc AVANT, la ou elle
     * est connue : un tap sur "Arreter", l'application balayee des recentes. Ce qui reste est subi, et
     * c'est la valeur par defaut.
     */
    @Volatile private var stopReason = LocationHub.StopReason.SYSTEM

    /** Le capteur est ecoute a cet instant. Faux quand le service attend qu'il revienne. */
    @Volatile private var subscribed = false

    /**
     * La bascule de la localisation dans le telephone, ecoutee par le SERVICE et non par l'ecran.
     *
     * C'est ce qui permet d'attendre le capteur au lieu d'abandonner : l'economie d'energie coupe la
     * localisation en cours de sortie, et rien ne la rallumait cote application - il fallait rouvrir la
     * carte et retaper sur le bouton, ce que personne ne fait sans savoir qu'il le faut.
     */
    private val providerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { retryIfPossible() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val filter = IntentFilter(LocationManager.MODE_CHANGED_ACTION).apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(this, providerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        watchSettings()
        watchFollowed()
        watchBell()
        watchLayers()
        watchTrip()
        watchTrack()
        watchNotice()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Le bouton de la notification : un geste, donc rien a annoncer et rien a rallumer.
            LocationHub.stopRequestedByUser()
            stopReason = LocationHub.StopReason.USER
            stopSelf()
            return START_NOT_STICKY
        }
        // L'autorisation AVANT tout le reste : depuis Android 14, se declarer en premier plan de type
        // "location" sans elle leve une SecurityException - et le systeme peut nous relancer (START_STICKY)
        // longtemps apres que l'utilisateur l'a retiree.
        //
        // Elle seule est redhibitoire. L'ABSENCE DE CAPTEUR ne l'est plus : c'est un etat passager - une
        // economie d'energie qui coupe la localisation, un mode avion de trente secondes - et y renoncer
        // definitivement, en rendant START_NOT_STICKY, est precisement ce qui laissait quelqu'un rouler
        // sans repere et sans que rien ne le dise.
        if (!hasPermission()) {
            LocationHub.stopRequestedByUser()   // rien a rallumer : l'autorisation manque
            stopSelf()
            return START_NOT_STICKY
        }
        // Puis la notification, AVANT le capteur : Android tue un service qui se declare en premier plan
        // trop tard, et une position recue entre-temps n'aurait personne pour la lire. Sous garde malgre
        // tout - un demarrage refuse (arriere-plan, Android 12+) ne doit pas emporter l'application.
        val declare = runCatching { startForeground(NOTIF_ID, notification(notice.value)) }.isSuccess
        if (!declare) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Un demarrage est toujours une intention : celle du bouton, ou celle que le systeme rejoue en
        // nous relancant apres avoir repris sa memoire.
        LocationHub.wantTracking()
        stopReason = LocationHub.StopReason.SYSTEM
        if (subscribe()) LocationHub.setTracking(true) else awaitProvider()
        // Le systeme peut nous relancer apres avoir eu besoin de memoire : le suivi reprend alors seul,
        // ce qu'on attend d'une fonction qu'on a demandee pour la duree d'une sortie.
        return START_STICKY
    }

    /**
     * Le capteur n'a rien a offrir pour l'instant : on reste en vie et on l'attend.
     *
     * La notification le dit, plutot que d'annoncer un suivi qui ne recoit rien - c'est la raison qui
     * faisait arreter le service ici, et elle etait bonne ; ce qui ne l'etait pas est d'en conclure qu'il
     * fallait renoncer. [providerReceiver] reveille des que la localisation revient.
     */
    private fun awaitProvider() {
        LocationHub.setTracking(false, LocationHub.StopReason.SENSOR_OFF)
        notice.value = getString(R.string.location_notice_waiting)
        postStoppedAlert(LocationHub.StopReason.SENSOR_OFF)
    }

    /**
     * L'arret subi, dit la ou il sera lu : une notification qui sonne.
     *
     * La banniere de la carte ne suffit pas, et c'est tout le probleme de ce defaut - le suivi sert
     * precisement quand personne ne regarde l'ecran, telephone en poche ou sur un guidon. Une annonce qui
     * attend qu'on rallume l'ecran arrive apres les vingt kilometres, pas avant.
     *
     * Canal distinct de celui du suivi, et d'importance HAUTE : celui du suivi est volontairement discret
     * - il porte une notification permanente qui ne doit jamais s'imposer -, et une importance ne se
     * change plus une fois le canal cree.
     */
    private fun postStoppedAlert(reason: LocationHub.StopReason) {
        if (!LocationHub.wanted.value) return
        ensureAlertChannel()
        val texte = getString(
            if (reason == LocationHub.StopReason.SENSOR_OFF) R.string.location_stopped_sensor
            else R.string.location_stopped_system,
        )
        val ouvrir = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle(getString(R.string.location_notice_title))
            .setContentText(texte)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texte))
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { nm.notify(ALERT_NOTIF_ID, notif) }
    }

    private fun ensureAlertChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(ALERT_CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID, getString(R.string.location_alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    /** La localisation vient de basculer : on se rebranche si elle est revenue, on lache si elle est partie. */
    private fun retryIfPossible() {
        if (!hasPermission()) return
        if (enabledProvider() != null) {
            if (subscribed) return
            if (subscribe()) {
                notice.value = null
                LocationHub.setTracking(true)
                // Le suivi a repris de lui-meme : l'annonce d'arret n'a plus d'objet, et la laisser
                // afficher un probleme resolu serait la rendre inutile a la fois suivante.
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                runCatching { nm?.cancel(ALERT_NOTIF_ID) }
            }
        } else if (subscribed) {
            runCatching { locationManager.removeUpdates(listener) }
            subscribed = false
            awaitProvider()
        }
    }

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(listener) }
        runCatching { unregisterReceiver(providerReceiver) }
        subscribed = false
        releaseWakeLock()
        // L'annonce AVANT l'arret : setTracking efface l'intention lue par postStoppedAlert.
        if (stopReason != LocationHub.StopReason.USER) postStoppedAlert(stopReason)
        LocationHub.setTracking(false, stopReason)
        // La trace suivie SURVIT au service. Elle etait arretee ici, si bien que l'alerte d'eloignement -
        // la fonction faite pour dire "tu quittes le chemin" - etait demontee par l'evenement meme qui la
        // rendait necessaire, et sans un mot. Elle attend desormais que les positions reviennent.
        scope.cancel()
        // Les compteurs, eux, s'ecrivent au plus toutes les quinze secondes : ce qui a couru depuis.
        TripStore.write(this, TripWatch.trip.value)
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
        LocationHub.stopRequestedByUser()
        stopReason = LocationHub.StopReason.USER
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
            rest = RestDetector.State()
            resting = false
            subscribed = true
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
     * Une position de plus : les compteurs de la sortie, la trace qu'on se met a suivre, l'ecart a celle
     * qu'on suit, et le son a l'entree en alerte.
     *
     * Sur [Dispatchers.Default] : ce sont des balayages de traces entieres, celui-la meme que fait la ligne
     * du restant du profil, et ils n'ont rien a faire sur le fil principal.
     */
    private fun watchTrack() = scope.launch {
        LocationHub.fix.filterNotNull().collect { fix ->
            // Les compteurs d'abord, et quoi qu'il arrive ensuite : ils tournent tant que la localisation
            // tourne, tableau de bord ouvert ou non.
            TripWatch.add(fix)
            val reglages = settings
            // Le reglage eteint, ou le tableau de bord ferme : aucune trace n'est plus suivie de personne.
            if ((reglages != null && !reglages.offTrackAlertVisible) || !TrackWatch.dashboard.value) {
                if (TrackWatch.followed.value != null) TrackWatch.stop()
                notice.value = null
                return@collect
            }
            val seuil = (reglages?.offTrackAlertDistanceM ?: DEFAULT_ALERT_M).toDouble()
            val suivie = TrackWatch.followed.value
            if (suivie == null) {
                notice.value = null
                val candidates = listOfNotNull(FollowCatalog.route.value) + candidatesNear(fix.lat, fix.lon)
                TrackWatch.detect(fix.lat, fix.lon, candidates, seuil, SystemClock.elapsedRealtime())
                return@collect
            }
            // La projection rend l'ecart ET le kilometrage : c'est le second qui dit ou l'on en est de la
            // trace (cf. FollowProgressMath), et dans quel sens on la parcourt.
            val projete = TrackMeasure.project(suivie.samples, fix.lon, fix.lat) ?: return@collect
            val away = projete.awayM
            when (TrackWatch.step(away, seuil, projete.alongM)) {
                TrackWatch.Step.Leave -> { TrackWatch.stop(); notice.value = null; return@collect }
                TrackWatch.Step.Alert -> if (reglages?.offTrackAlertSound == true) {
                    playAlertSound(this@LocationService, reglages.offTrackAlertSoundUri)
                }
                TrackWatch.Step.Stay -> Unit
            }
            notice.value = getString(
                R.string.location_notice_following,
                suivie.layerName,
                Format.shortDistance(away, reglages?.units == "imperial"),
            )
        }
    }

    /**
     * Les traces des couches a portee de la position, lues a la premiere occasion puis gardees : on passe
     * des heures sur les memes, et relire leur profil a chaque position serait refaire sans cesse le meme
     * travail. Une couche modifiee - son entite change - se relit ; une couche sortie de portee s'oublie.
     *
     * Appelee du seul collecteur des positions : le cache n'a qu'un lecteur, et pas de verrou.
     */
    private suspend fun candidatesNear(lat: Double, lon: Double): List<AutoFollow.Candidate> {
        val near = FollowCatalog.layersNear(visibleLayers, lat, lon)
        layerCache.keys.retainAll(near.mapTo(HashSet()) { it.id })
        val repo = (application as TrailogApp).repository
        return near.flatMap { ly ->
            layerCache[ly.id]?.takeIf { it.first == ly }?.second ?: runCatching {
                val profils = repo.loadProfiles(ly)
                profils.mapIndexedNotNull { i, ct ->
                    ct.samples.takeIf { it.size >= 2 }?.let { FollowCatalog.candidate(ly.id, ly.name, i, profils.size, it) }
                }
            }.getOrDefault(emptyList()).also { layerCache[ly.id] = ly to it }
        }
    }

    /** Les couches de la bibliotheque, tenues a jour pour la detection (cf. [candidatesNear]). */
    private fun watchLayers() = scope.launch {
        (application as TrailogApp).repository.layers.all().collect { visibleLayers = it }
    }

    /**
     * Les compteurs de la sortie, repris du disque puis ecrits dessus - au plus toutes les quinze
     * secondes : une ecriture par position serait un fichier reecrit toutes les deux secondes, pour une
     * perte, a la mort du processus, de quelques metres au pire.
     *
     * Le flux ne garde que sa derniere valeur : apres l'attente, c'est l'etat le plus recent qui s'ecrit.
     */
    private fun watchTrip() = scope.launch {
        TripWatch.restore(TripStore.load(this@LocationService))
        TripWatch.trip.collect { t ->
            TripStore.save(this@LocationService, t)
            delay(TRIP_SAVE_MS)
        }
    }

    /**
     * La trace suivie, reprise du disque puis tenue a jour dessus (cf. [FollowedStore]).
     *
     * **La reprise d'abord, l'ecriture ensuite, et dans la MEME coroutine** : l'ordre est la regle. En
     * collectant avant de reprendre, la premiere valeur emise serait le null de depart, qui effacerait le
     * fichier - on aurait detruit la trace a reprendre en s'appretant a la reprendre.
     *
     * La reprise ne s'impose pas a un suivi en cours (cf. [TrackWatch.restore]) : le service peut etre
     * relance alors que l'ecran vient de designer une autre trace.
     */
    private fun watchFollowed() = scope.launch {
        TrackWatch.restore(FollowedStore.load(this@LocationService), SystemClock.elapsedRealtime())
        TrackWatch.followed.collect { suivie ->
            FollowedStore.save(this@LocationService, suivie)
            // Plus rien a suivre : la notification le dit TOUT DE SUITE. Elle ne se recalculait qu'a la
            // position suivante (cf. watchTrack), si bien qu'apres un "Ne plus suivre" elle continuait
            // d'annoncer l'ecart a une trace qu'on venait d'abandonner - et sous un couvert, sans mesure
            // nouvelle, elle pouvait le faire longtemps.
            if (suivie == null) notice.value = null
        }
    }

    /** La cloche, reprise du disque puis tenue a jour dessus - dans cet ordre, comme la trace suivie. */
    private fun watchBell() = scope.launch {
        TrackWatch.restoreBell(FollowedStore.loadBell(this@LocationService))
        TrackWatch.bellKey.collect { FollowedStore.saveBell(this@LocationService, it) }
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
        private const val ALERT_CHANNEL_ID = "suivi-position-arrete"
        private const val NOTIF_ID = 4918
        private const val ALERT_NOTIF_ID = 4919
        private const val WAKE_LOCK_TAG = "trailog:suivi-position"
        const val ACTION_STOP = "fr.lc4918.trailog.STOP_LOCATION"

        /**
         * Une position toutes les deux secondes, SANS distance minimale.
         *
         * Il y en avait une, de cinq metres : a pied lent, les positions s'espacaient, et la vitesse du
         * tableau de bord restait figee sur la derniere mesuree. Le tremblement des positions a l'arret,
         * lui, ne compte pas dans les compteurs (cf. TripStats). A l'arret prolonge, la cadence s'espace
         * d'elle-meme (cf. [pace]).
         */
        private const val INTERVAL_MS = 2000L
        private const val MIN_DISTANCE_M = 0f

        /** La cadence de repos : une position toutes les dix secondes au plus, et seulement apres 10 m. */
        private const val REST_INTERVAL_MS = 10_000L
        private const val REST_DISTANCE_M = 10f

        /** Repli quand les reglages ne sont pas encore lus - la meme valeur que leur defaut. */
        private const val DEFAULT_ALERT_M = 50
        private const val TRIP_SAVE_MS = 15_000L

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
