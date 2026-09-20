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
import android.os.Handler
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
import fr.lc4918.trailog.domain.geo.OffTrack
import fr.lc4918.trailog.domain.geo.RestDetector
import fr.lc4918.trailog.domain.geo.TrackMeasure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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

    /** Le fil principal, pour les changements de cadence venus d'ailleurs (cf. [watchDetectionPace]). */
    private val mainHandler by lazy { Handler(mainLooper) }

    /** Les demandes sont espacees : on est a l'arret depuis une minute. */
    private var resting = false

    /**
     * La cadence des demandes, selon qu'on bouge ou non.
     *
     * En marche : toutes les deux secondes, sans distance minimale - la vitesse du tableau de bord reste a
     * jour, meme a pas lent. A l'arret depuis une minute : une position seulement apres [REST_DISTANCE_M]
     * de deplacement. Plus rien n'arrive tant qu'on ne bouge pas, et la premiere position qui arrive dit
     * qu'on est reparti : la cadence de marche revient aussitot.
     *
     * **Sauf quand une trace reste a reconnaitre** (cf. [awaitingTrack]) : la detection se nourrit de
     * positions, et la cadence de repos n'en rend plus aucune tant qu'on n'a pas fait dix metres. Accrocher
     * une trace a l'arret - ce qui est justement ce qu'on attend, pose au depart - serait alors impossible.
     */
    @SuppressLint("MissingPermission")
    private fun pace(lat: Double, lon: Double, accuracyM: Float) {
        val (etat, brut) = RestDetector.step(rest, lat, lon, accuracyM, SystemClock.elapsedRealtime())
        rest = etat
        applyPace(brut && !awaitingTrack())
    }

    /** Une trace reste a reconnaitre : le tableau de bord est ouvert, et rien n'est encore suivi. */
    private fun awaitingTrack(): Boolean =
        TrackWatch.dashboard.value && TrackWatch.followed.value == null

    /** Passe a la cadence demandee, si ce n'est pas deja celle en cours. Sur le fil principal. */
    @SuppressLint("MissingPermission")
    private fun applyPace(repos: Boolean) {
        if (repos == resting || !subscribed) return
        resting = repos
        val provider = enabledProvider() ?: return
        runCatching {
            if (repos) locationManager.requestLocationUpdates(provider, REST_INTERVAL_MS, REST_DISTANCE_M, listener, mainLooper)
            else locationManager.requestLocationUpdates(provider, INTERVAL_MS, MIN_DISTANCE_M, listener, mainLooper)
        }
    }

    /**
     * La detection reprend la main sur la cadence de repos.
     *
     * [pace] ne se prononce qu'a l'arrivee d'une position, et au repos il n'en arrive plus : ouvrir le
     * tableau de bord alors qu'on est deja pose depuis une minute laisserait la detection sans matiere,
     * indefiniment. C'est donc l'ETAT - tableau de bord, trace suivie - qui rend la cadence de marche,
     * sans attendre une position qui ne viendra pas.
     *
     * Sur le fil principal : [rest] et [resting] sont ceux du listener, et deux fils qui redemandent des
     * positions en meme temps en laisseraient une demande derriere eux.
     */
    private fun watchDetectionPace() = scope.launch {
        combine(TrackWatch.dashboard, TrackWatch.followed) { ouvert, suivie -> ouvert && suivie == null }
            .distinctUntilChanged()
            .collect { attente -> if (attente) mainHandler.post { applyPace(false) } }
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
        watchDetectionPace()
        watchAlertRing()
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
        if (intent?.action == ACTION_SILENCE_ALERT) {
            // "Taire", depuis la notification de l'ecart : le meme geste que la banniere de la carte, et le
            // meme effet - la sonnerie s'arrete, le suivi continue (cf. [TrackWatch.silence]).
            TrackWatch.silence()
            return START_STICKY
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
        // La sonnerie ne survit pas au service : elle boucle, et une boucle sans personne pour la couper
        // sonnerait jusqu'a la batterie vide.
        AlertSound.stop()
        cancelOffTrackAlert()
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
                // L'entree en alerte ne joue plus le son elle-meme : la sonnerie boucle tant que l'ecart
                // dure, et c'est donc l'ETAT de l'alerte qui la commande (cf. [watchAlertRing]).
                TrackWatch.Step.Alert, TrackWatch.Step.Stay -> Unit
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

    /**
     * La sonnerie de l'ecart et la notification qui l'accompagne, commandees par l'ETAT de l'alerte.
     *
     * **Pourquoi un etat et non un evenement.** Le son se jouait une fois, a l'entree en alerte, et une
     * seule condition suffisait donc a le declencher. Il boucle desormais jusqu'a ce qu'on reponde : ce qui
     * l'arrete compte alors autant que ce qui le lance - un tap sur la banniere ou sur la notification, un
     * retour sur la trace, la cloche desarmee, le reglage eteint en pleine alerte. Ces quatre sorties se
     * lisent dans les etats que la veille publie, pas dans l'evenement qui a ouvert l'alerte.
     *
     * **Le reglage du son vient du depot et non du champ [settings]** : eteindre "Emettre un son" alors que
     * la sonnerie sonne doit la couper, et un champ relu ne reveille personne.
     *
     * La notification, elle, parait des que l'alerte parait - son ou pas : c'est elle qui porte l'alerte sur
     * l'ecran de verrouillage et dans la barre de statut, la ou on la voit sans deverrouiller.
     */
    private fun watchAlertRing() = scope.launch {
        val son = (application as TrailogApp).repository.settingsFlow
            .map { (it?.offTrackAlertSound ?: false) to (it?.offTrackAlertSoundUri ?: "") }
            .distinctUntilChanged()
            // Les reglages arrivent de la base un instant apres le service : sans ce premier couple, rien
            // ne serait combine tant qu'ils ne sont pas lus, et une alerte de la premiere seconde se
            // tairait. "Pas de son" est le bon repli - le son est un reglage qu'on allume.
            .onStart { emit(false to "") }
        combine(TrackWatch.alerting, TrackWatch.silenced, TrackWatch.armed, son) { alerting, silenced, armed, (actif, uri) ->
            Ring(
                notifier = OffTrack.announcing(armed, alerting, silenced),
                sonner = OffTrack.ringing(actif, armed, alerting, silenced),
                uri = uri,
            )
        }.distinctUntilChanged().collect { etat ->
            if (etat.notifier) postOffTrackAlert() else cancelOffTrackAlert()
            if (etat.sonner) AlertSound.start(this@LocationService, etat.uri) else AlertSound.stop()
        }
    }

    /** Ce que l'alerte demande a cet instant : la notification, la sonnerie, et le son a jouer. */
    private data class Ring(val notifier: Boolean, val sonner: Boolean, val uri: String)

    /**
     * L'ecart a la trace, dit la ou on le lira sans rien deverrouiller : l'ecran de verrouillage et la barre
     * de statut.
     *
     * **Pourquoi elle est necessaire.** La banniere de la carte ne se voit que carte ouverte et ecran
     * allume, ce qui est justement ce qui n'arrive pas quand l'alerte sert : le telephone est en poche.
     *
     * **Muette, alors qu'elle sonne.** Le son ne vient pas d'elle mais de [AlertSound], parce qu'une
     * notification ne sait pas boucler : son canal est donc cree sans sonnerie, et la vibration reste - elle
     * dit la meme chose au poignet.
     *
     * **Intention plein ecran** : c'est le seul moyen qu'Android offre d'allumer l'ecran sur une alerte et
     * d'y poser l'application par-dessus le verrouillage. Non accordee (Android 14+), elle retombe d'
     * elle-meme en banniere par-dessus l'ecran en cours, ce qui reste le comportement voulu.
     */
    private fun postOffTrackAlert() {
        ensureOffTrackChannel()
        val suivie = TrackWatch.followed.value ?: return
        val reglages = settings
        val ecart = TrackWatch.awayM.value ?: (reglages?.offTrackAlertDistanceM ?: DEFAULT_ALERT_M).toDouble()
        val texte = getString(
            R.string.alert_off_track_banner,
            Format.shortDistance(ecart, reglages?.units == "imperial"),
            suivie.layerName,
        )
        // Deux intentions vers la meme carte, et deux actions differentes : le tap REPOND a l'alerte, le
        // plein ecran ne fait que l'amener sous les yeux (cf. AlertAnswer). Deux codes de requete, sans
        // quoi la seconde ecraserait la premiere - PendingIntent ne distingue pas les intentions par leur
        // action.
        val ouvrir = alertActivityIntent(3, ACTION_SHOW_ALERT)
        val reveiller = alertActivityIntent(5, ACTION_WAKE_ALERT)
        val taire = PendingIntent.getService(
            this, 4, Intent(this, LocationService::class.java).setAction(ACTION_SILENCE_ALERT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, OFF_TRACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle(getString(R.string.alert_off_track_title))
            .setContentText(texte)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texte))
            .setContentIntent(ouvrir)
            .addAction(0, getString(R.string.alert_off_track_silence), taire)
            .setFullScreenIntent(reveiller, true)
            .setAutoCancel(true)
            // Elle ne se balaie pas : une alerte a laquelle on n'a pas repondu sonne toujours, et la faire
            // disparaitre d'un geste qui ne l'arrete pas laisserait un telephone qui sonne sans raison lisible.
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            // Lisible verrouille : c'est tout son objet, et elle ne dit rien de personnel - un nom de trace
            // et une distance.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { nm.notify(OFF_TRACK_NOTIF_ID, notif) }
    }

    /** La carte, ouverte depuis la notification de l'ecart, sous l'action qui dit comment on y arrive. */
    private fun alertActivityIntent(requestCode: Int, action: String): PendingIntent =
        PendingIntent.getActivity(
            this, requestCode,
            Intent(this, MainActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cancelOffTrackAlert() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { nm.cancel(OFF_TRACK_NOTIF_ID) }
    }

    /** Canal de l'ecart : importance haute pour paraitre par-dessus, et MUET - la sonnerie vient de nous. */
    private fun ensureOffTrackChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(OFF_TRACK_CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                OFF_TRACK_CHANNEL_ID, getString(R.string.location_off_track_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(null, null)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
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
        private const val OFF_TRACK_CHANNEL_ID = "ecart-trace"
        private const val OFF_TRACK_NOTIF_ID = 4920
        private const val WAKE_LOCK_TAG = "trailog:suivi-position"
        const val ACTION_STOP = "fr.lc4918.trailog.STOP_LOCATION"

        /** "Taire" sur la notification de l'ecart : la sonnerie s'arrete, le suivi continue. */
        const val ACTION_SILENCE_ALERT = "fr.lc4918.trailog.SILENCE_OFF_TRACK_ALERT"

        /**
         * La notification de l'ecart, tapee : l'application s'ouvre sur la carte, et l'alerte se tait -
         * on a repondu (cf. MainActivity).
         */
        const val ACTION_SHOW_ALERT = "fr.lc4918.trailog.SHOW_OFF_TRACK_ALERT"

        /**
         * L'intention plein ecran de la notification de l'ecart : Android l'a declenchee tout seul pour
         * allumer l'ecran. L'application se pose par-dessus le verrouillage, et l'alerte CONTINUE - elle
         * n'a encore ete lue de personne (cf. AlertAnswer).
         */
        const val ACTION_WAKE_ALERT = "fr.lc4918.trailog.WAKE_OFF_TRACK_ALERT"

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
