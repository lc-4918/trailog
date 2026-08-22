package fr.lc4918.trailog.ui.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.lc4918.trailog.location.LocationHub
import fr.lc4918.trailog.location.LocationService
import fr.lc4918.trailog.ui.components.MapController
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

/** Cadence de relecture de l'age de la position : la seconde suffit pour un seuil de trente. */
private const val STALE_TICK_MS = 1_000L

/**
 * Tout ce que l'ecran de carte doit savoir faire avec la position : l'allumer, l'eteindre, en demander
 * une seule, et savoir si le telephone veut bien en donner.
 *
 * **Pourquoi un porteur d'etat.** Ces deux cent lignes vivaient au milieu de `MainScreen`, entre le
 * telechargement hors-ligne et l'alerte d'eloignement, alors qu'elles ne parlent qu'entre elles : trois
 * autorisations, un recepteur systeme, un service de premier plan et deux cadrages de camera. Ce qui les
 * lie au reste de l'ecran tient en six lectures - [gpsActive], [lastUserLocation], [sensorEnabled] - et
 * quatre gestes. C'est ce que ce porteur expose, et rien d'autre.
 *
 * **Ce qu'il ne fait pas.** Il ne lit pas le capteur : c'est [LocationService] qui l'ecoute, depuis un
 * service de premier plan, et [LocationHub] qui en publie le flux. C'est ce qui fait survivre le suivi a
 * l'extinction de l'ecran - la composition, elle, s'arrete des que la carte n'est plus visible, et le
 * suivi s'arretait avec elle.
 *
 * Il reste ici les deux choses que le service ne fait pas : demander le capteur pour une seule question
 * (cf. [currentPosition]), et savoir si la localisation est allumee dans le telephone (cf.
 * [sensorEnabled]) - deux lectures qui ne demandent rien a personne.
 *
 * Il ne dessine rien non plus : le symbole du repere, sa couleur et son orientation restent a l'ecran,
 * qui les tire des reglages et les pose sur le controleur avec le reste de ce qu'il affiche.
 *
 * S'obtient par [rememberLocationControls] : les autorisations et les effets qu'il porte ne peuvent
 * naitre que dans une composition.
 */
@Stable
class LocationControls internal constructor(
    private val ctx: Context,
    private val controller: MapController,
    private val locationManager: LocationManager,
    private val trackingState: State<Boolean>,
    private val fixState: State<LocationHub.Fix?>,
) {

    /** Le suivi tourne : le service ecoute et le repere est pose sur la carte. */
    val gpsActive: Boolean get() = trackingState.value

    /** La derniere position connue, en (lat, lon), ou null tant que le suivi n'a rien recu. */
    val lastUserLocation: Pair<Double, Double>? get() = fixState.value?.let { it.lat to it.lon }

    /**
     * La localisation est-elle allumee dans le telephone.
     *
     * A ne pas confondre avec [gpsActive], qui dit seulement si le REPERE est pose sur la carte. Ce qui se
     * CALCULE a partir de la position - partir d'ou l'on est dans le planificateur, mesurer une distance
     * depuis la position - ne demande que le capteur : le reglage "Localisation GPS" et son bouton ne
     * commandent, eux, que l'affichage du repere.
     *
     * Relu a chaque bascule de la localisation, que le systeme diffuse, et au retour au premier plan :
     * sans cela, l'eteindre depuis le volet des reglages rapides laissait des propositions qui ne
     * pouvaient plus aboutir, et l'allumer n'en ramenait aucune.
     */
    var sensorEnabled by mutableStateOf(false)
        private set

    /** Le suivi a ete demande alors que la localisation est eteinte : l'ecran propose d'aller l'allumer. */
    var showDisabledDialog by mutableStateOf(false)

    /**
     * Instant du dernier geste sur la carte (0 = aucun), en temps depuis le demarrage de l'appareil et non
     * en heure murale : le suivi de position s'y refere, et un changement d'heure - fuseau, mise a l'heure
     * du reseau - ne doit pas le suspendre pour l'eternite ni le reveiller trop tot.
     */
    var lastUserGestureAt by mutableLongStateOf(0L)
        private set

    /** Vrai tant qu'un recentrage automatique est du (activation du capteur, ou retour au premier plan) :
     *  consomme des que la prochaine position arrive. */
    private var pendingCenter by mutableStateOf(false)

    /** Geste mis en attente de l'autorisation : rejoue des qu'elle est accordee. L'autorisation se demande
     *  depuis plusieurs endroits (le bouton de position, mais aussi le planificateur et la mesure depuis la
     *  position), et chacun a sa propre suite a donner. */
    private var pendingAction: (() -> Unit)? = null

    // Les trois lanceurs d'autorisation, poses par [rememberLocationControls] : ils ne peuvent naitre que
    // dans une composition, et leurs suites appellent des methodes d'ici - d'ou l'aller-retour.
    internal lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    internal lateinit var locationPermissionLauncher: ActivityResultLauncher<String>
    internal lateinit var locationSettingsLauncher: ActivityResultLauncher<Intent>

    /** Un geste sur la carte vient d'avoir lieu : le suivi de position s'y refere (cf. MapFollow). */
    fun noteUserGesture() { lastUserGestureAt = SystemClock.elapsedRealtime() }

    fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    internal fun refreshSensorEnabled() { sensorEnabled = LocationManagerCompat.isLocationEnabled(locationManager) }

    /**
     * L'autorisation de notifier, demandee au premier allumage du suivi et a lui seul.
     *
     * Depuis Android 13, une notification refusee ne s'affiche pas - le service, lui, tourne quand meme.
     * Ce serait une position qui consomme la batterie sans que rien ne le dise ni ne l'arrete, exactement
     * ce qu'on reproche aux applications qui suivent en cachette. Un refus n'empeche donc rien : il est
     * pris pour ce qu'il est, et le bouton de la carte reste le second moyen d'arreter.
     */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val accordee = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!accordee) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Fin du suivi DEMANDEE : le service s'arrete, et le repere disparait avec lui.
     *
     * Le geste est enregistre avant l'arret. C'est ce qui distingue cet arret-ci de tous les autres :
     * il n'a rien a annoncer, et rien ne doit le rallumer.
     */
    fun stopGps() {
        LocationHub.stopRequestedByUser()
        LocationService.stop(ctx)
    }

    /** L'annonce d'un arret subi a ete lue : la banniere se retire. */
    fun dismissStopNotice() { LocationHub.clearStopNotice() }

    /** Le fournisseur de position a interroger : le GPS s'il est allume, le reseau sinon, rien si les
     *  deux sont eteints. */
    private fun enabledProvider(): String? = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    @SuppressLint("MissingPermission")
    fun startGps() {
        if (!hasLocationPermission()) return
        val provider = enabledProvider() ?: return
        askNotificationPermission()
        LocationHub.wantTracking()
        LocationService.start(ctx)
        // Le cadrage reste ici, et lui seul : le service pose le repere, l'ecran decide de ce que la camera
        // en fait. La derniere position connue est relue plutot qu'attendue du flux - c'est elle qui
        // distingue les deux cadrages, un saut au zoom 15 sur un point qu'on tient deja, un simple
        // recentrage a l'arrivee de la premiere mesure.
        val last = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        if (last != null) {
            controller.moveTo(last.latitude, last.longitude, 15.0)
            pendingCenter = false
        } else {
            pendingCenter = true   // pas de derniere position connue : on centre des que le capteur en donne une
        }
    }

    /** Recentre la carte sur la derniere position GPS connue (bouton de recentrage). */
    fun recenterOnGps() { lastUserLocation?.let { (la, lo) -> controller.centerOn(la, lo) } }

    /**
     * Une position, en (lat, lon), pour ce qui se CALCULE a partir d'elle - sans rien poser sur la carte.
     *
     * Le repere affiche donne deja un flux de positions : on s'en sert telle quelle. Sinon on demande au
     * systeme une position ponctuelle, qu'il rend tout de suite s'il en a une assez fraiche et qu'il va
     * chercher au capteur autrement. C'est ce qui affranchit le planificateur et les mesures de
     * l'affichage du repere : ils ne l'allument pas, ils lisent le capteur le temps d'une question.
     *
     * Null si l'autorisation manque ou si la localisation est eteinte - et aussi si le capteur n'a rien
     * rendu dans le delai que le systeme s'accorde, sous un toit ou dans un tunnel.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentPosition(): Pair<Double, Double>? {
        lastUserLocation?.let { return it }
        if (!hasLocationPermission()) return null
        val provider = enabledProvider() ?: return null
        return suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            runCatching {
                LocationManagerCompat.getCurrentLocation(
                    locationManager, provider, signal, ContextCompat.getMainExecutor(ctx),
                ) { loc -> if (cont.isActive) cont.resume(loc?.let { it.latitude to it.longitude }) }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    /** Fait [action] avec le capteur, en demandant d'abord l'autorisation si elle manque. */
    fun withLocationPermission(action: () -> Unit) {
        if (hasLocationPermission()) action()
        else {
            pendingAction = action
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /** Le bouton de position : il allume le suivi, ou l'eteint s'il tourne. */
    fun onGpsButtonTap() {
        if (gpsActive) { stopGps(); return }
        withLocationPermission {
            if (LocationManagerCompat.isLocationEnabled(locationManager)) startGps()
            else showDisabledDialog = true
        }
    }

    /** Ouvre les reglages de localisation du systeme (proposition de la boite de dialogue). */
    fun openLocationSettings() {
        locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    internal fun onPermissionResult(granted: Boolean) {
        val action = pendingAction
        pendingAction = null
        if (granted) action?.invoke()
    }

    internal fun onReturnFromSettings() {
        refreshSensorEnabled()
        if (LocationManagerCompat.isLocationEnabled(locationManager)) startGps()
    }

    /** Une position de plus : le repere se deplace, et la camera le suit si un recentrage etait du. */
    internal fun onFix(fix: LocationHub.Fix) {
        controller.setUserLocation(fix.lon, fix.lat, fix.accuracyM)
        if (pendingCenter) { controller.centerOn(fix.lat, fix.lon); pendingCenter = false }
    }

    /** Suivi arrete : plus de repere. */
    internal fun onTrackingStopped() {
        controller.clearUserLocation()
        pendingCenter = false
    }

    /**
     * L'age de la derniere mesure, en millisecondes, ou null s'il n'y en a aucune.
     *
     * Recalcule a la demande : ce n'est pas la position qui vieillit dans un etat, c'est l'horloge qui
     * avance. Lu par [positionStale], que l'ecran surveille.
     */
    private fun fixAgeMs(): Long? = fixState.value?.let { SystemClock.elapsedRealtime() - it.receivedAtMs }

    /** Depuis combien de temps le capteur n'a plus rien donne, ou null s'il n'y a pas de repere. */
    val positionAgeMs: Long? get() = fixAgeMs()

    /**
     * Le repere ment : le capteur n'a plus rien donne depuis [STALE_AFTER_MS].
     *
     * Un repere fige est visuellement identique a un repere juste - c'est le meme accident que la
     * disparition, sous une forme plus sournoise : on regarde un point qui affirme ou l'on est, et il a
     * raison depuis dix minutes.
     */
    var positionStale by mutableStateOf(false)
        private set

    internal fun refreshStale() {
        val age = fixAgeMs()
        positionStale = gpsActive && age != null && age > STALE_AFTER_MS
    }

    companion object {
        /**
         * Au-dela, le repere est declare vieux.
         *
         * Le capteur est demande toutes les deux secondes. Trente, c'est quinze mesures manquees - assez
         * pour n'etre pas un trou passager sous un pont, assez peu pour qu'a velo cela ne fasse que
         * cent cinquante metres d'erreur.
         */
        const val STALE_AFTER_MS = 30_000L
    }
}

/**
 * Le porteur de la position, avec les autorisations et les effets qui le tiennent en vie.
 *
 * [showGpsButton] est le reglage "afficher le bouton GPS" : l'eteindre pendant que la position tourne
 * coupe le suivi, faute de quoi il resterait allume sans plus rien a l'ecran pour l'arreter.
 */
@Composable
fun rememberLocationControls(controller: MapController, showGpsButton: Boolean?): LocationControls {
    val ctx = LocalContext.current
    val locationManager = remember(ctx) { ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val trackingState = LocationHub.tracking.collectAsState()
    val fixState = LocationHub.fix.collectAsState()
    val controls = remember(ctx, controller, locationManager, trackingState, fixState) {
        LocationControls(ctx, controller, locationManager, trackingState, fixState)
    }

    controls.notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    controls.locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { controls.onPermissionResult(it) }
    controls.locationSettingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { controls.onReturnFromSettings() }

    // Le repere suit le flux du concentrateur : une position de plus, un repere deplace. Le rechargement
    // de style, lui, ne demande rien - le controleur garde la derniere position et la repose lui-meme.
    val fix by fixState
    LaunchedEffect(fix) { fix?.let { controls.onFix(it) } }
    // Suivi arrete : plus de repere. Ici et non dans stopGps, parce que le service peut aussi s'arreter de
    // lui-meme - la notification, ou le systeme qui reprend sa memoire.
    val tracking by trackingState
    LaunchedEffect(tracking) { if (!tracking) controls.onTrackingStopped() }

    // La bascule de la localisation dans le telephone, et le retour au premier plan (cf. sensorEnabled).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controls) {
        controls.refreshSensorEnabled()
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) controls.refreshSensorEnabled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { controls.refreshSensorEnabled() }
        }
        // Les deux annonces : la bascule generale de la localisation, et l'allumage ou l'extinction d'un
        // fournisseur en particulier - c'est celle-la que les anciennes versions d'Android envoient.
        val filter = IntentFilter(LocationManager.MODE_CHANGED_ACTION).apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { ctx.unregisterReceiver(receiver) }
        }
    }

    // Le reglage "afficher le bouton GPS" desactive pendant que la position est active coupe la position
    // (mais ne desactive pas le capteur lui-meme, seulement les mises a jour cote appli). Un geste, donc :
    // arret DEMANDE, qui ne s'annonce pas et ne se rallume pas.
    LaunchedEffect(showGpsButton) { if (showGpsButton == false && controls.gpsActive) controls.stopGps() }

    /*
     * La localisation du telephone n'est PLUS surveillee ici.
     *
     * L'ecran la coupait lui-meme quand elle s'eteignait, et personne ne la rallumait : une coupure d'une
     * seconde - l'economie d'energie d'un Samsung en fin de batterie - arretait le suivi pour le reste de
     * la sortie. Le service s'en charge desormais de bout en bout : il attend le capteur au lieu de
     * renoncer, se rebranche des qu'il revient, et le dit dans les deux sens. Un seul proprietaire, et
     * c'est celui qui survit a l'ecran.
     *
     * [LocationControls.sensorEnabled] reste lu, mais pour ce qui INTERROGE le capteur sans afficher de
     * repere - le planificateur, les mesures depuis la position.
     */

    // L'age de la derniere mesure : ce n'est pas elle qui change, c'est l'horloge qui avance. D'ou une
    // relecture reguliere, et seulement tant que le suivi tourne - rien a surveiller sinon.
    LaunchedEffect(tracking, fix) {
        controls.refreshStale()
        while (tracking) { delay(STALE_TICK_MS); controls.refreshStale() }
    }

    /*
     * Rien n'est desabonne a la disparition de l'ecran, et c'est tout l'objet du service : le suivi doit
     * survivre a la carte qu'on quitte, a l'ecran qui s'endort, et a l'application passee en arriere-plan.
     * Il ne s'arrete que sur un geste - le bouton, la notification - ou quand la localisation s'eteint.
     */
    return controls
}

/**
 * Ecran maintenu allume tant que [enabled], c'est-a-dire tant que le suivi tourne et que le reglage le
 * demande.
 *
 * Ici parce que son declencheur est le suivi de position, et qu'il n'a de sens qu'avec lui : ce n'est plus
 * ce qui garde le suivi EN VIE - le service s'en charge, veille comprise - mais ce qui garde la carte
 * LISIBLE, telephone sur un guidon, ou consulte d'un coup d'oeil sans y toucher. D'ou le lien avec le
 * suivi plutot qu'avec l'ecran de carte : allumer indefiniment l'ecran de quelqu'un qui consulte ses
 * traces sur son canape n'aurait aucun sens.
 *
 * Le drapeau est pose sur la FENETRE et retire au depart de l'ecran : laisse en place, il survivrait a la
 * carte, et rien a l'ecran ne dirait plus pourquoi le telephone ne s'endort pas.
 */
@Composable
fun KeepScreenOnEffect(enabled: Boolean) {
    val ctx = LocalContext.current
    // L'activite se cherche en remontant les contextes : celui de la composition est parfois un habillage -
    // c'en est un ici, la langue etant posee par attachBaseContext (cf. LocalePrefs).
    val fenetre = remember(ctx) {
        generateSequence(ctx) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()?.window
    }
    DisposableEffect(enabled, fenetre) {
        if (enabled) fenetre?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else fenetre?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { fenetre?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
