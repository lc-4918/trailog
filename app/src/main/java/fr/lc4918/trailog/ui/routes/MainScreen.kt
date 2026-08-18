package fr.lc4918.trailog.ui.routes

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.location.LocationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.DefaultGpsMarkerSizeDp
import fr.lc4918.trailog.data.db.DefaultOffTrackAlertM
import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.MinMapButtonSizeDp
import fr.lc4918.trailog.data.db.offTrackAlertVisible
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.data.db.routeUrl
import fr.lc4918.trailog.data.db.routePrefs
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Link
import fr.lc4918.trailog.ui.components.MapActionBar
import fr.lc4918.trailog.ui.edit.CutBubblePlacement
import fr.lc4918.trailog.ui.edit.CutTarget
import fr.lc4918.trailog.ui.edit.EditTool
import fr.lc4918.trailog.ui.edit.SegmentRef
import fr.lc4918.trailog.ui.edit.TrackEditState
import fr.lc4918.trailog.data.repo.TrailogRepository
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.domain.geo.TrackEdit
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.geo.TrackMeasure
import fr.lc4918.trailog.domain.model.BubblePosition
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.GpsMarkerStyle
import fr.lc4918.trailog.geocode.GeocodePlace
import fr.lc4918.trailog.geocode.NetworkStatus
import fr.lc4918.trailog.geocode.Photon
import fr.lc4918.trailog.net.ServiceUrl
import fr.lc4918.trailog.map.CoverageBounds
import fr.lc4918.trailog.map.CoverageProbe
import fr.lc4918.trailog.map.compositeIdFromBasemapId
import fr.lc4918.trailog.map.legendAssetModel
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.map.offline.OfflinePhase
import fr.lc4918.trailog.ui.alert.OffTrackAlertBar
import fr.lc4918.trailog.ui.alert.OffTrackAlertState
import fr.lc4918.trailog.ui.alert.TrackChooserDialog
import fr.lc4918.trailog.ui.alert.playAlertSound
import fr.lc4918.trailog.ui.components.Avatar
import fr.lc4918.trailog.ui.components.BasemapControlPanel
import fr.lc4918.trailog.ui.components.ColorPickerDialog
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.MapLibreView
import fr.lc4918.trailog.ui.components.MapPromptBar
import fr.lc4918.trailog.ui.mappoint.AddressState
import fr.lc4918.trailog.ui.mappoint.MapPointBubble
import fr.lc4918.trailog.ui.mappoint.MapPointState
import fr.lc4918.trailog.ui.mappoint.MeasureState
import fr.lc4918.trailog.ui.measure.MeasureAnchor
import fr.lc4918.trailog.ui.measure.MeasureBubble
import fr.lc4918.trailog.ui.measure.TrackMeasureState
import fr.lc4918.trailog.ui.geocode.GeocodeBubble
import fr.lc4918.trailog.ui.geocode.GeocodeSearchBar
import fr.lc4918.trailog.ui.geocode.GeocodeSearchState
import fr.lc4918.trailog.ui.offline.BboxDrawingOverlay
import fr.lc4918.trailog.ui.offline.OfflineDownloadCard
import fr.lc4918.trailog.ui.offline.OfflineDownloadConfigScreen
import fr.lc4918.trailog.ui.offline.OfflineMinimizedButton
import fr.lc4918.trailog.ui.points.BubblePlacement
import fr.lc4918.trailog.ui.points.InfoBubble
import fr.lc4918.trailog.ui.points.InfoBubbleLoading
import fr.lc4918.trailog.ui.points.PropertyEditor
import fr.lc4918.trailog.ui.points.computeBubblePlacement
import fr.lc4918.trailog.ui.points.computeGeocodePlacement
import fr.lc4918.trailog.domain.model.RouteEngine
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.data.db.AppDatabase
import fr.lc4918.trailog.poi.Datatourisme
import fr.lc4918.trailog.poi.PoiRepository
import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.ui.poi.PoiState
import fr.lc4918.trailog.ui.poi.PoiLoading
import fr.lc4918.trailog.ui.poi.poiGroupColor
import fr.lc4918.trailog.ui.poi.poiIcon
import fr.lc4918.trailog.ui.components.PoiMarker
import fr.lc4918.trailog.routing.GpxWriter
import fr.lc4918.trailog.routing.Router
import fr.lc4918.trailog.ui.planner.GeocodingParams
import fr.lc4918.trailog.ui.planner.PlannerStep
import fr.lc4918.trailog.ui.planner.RoutePlannerBand
import fr.lc4918.trailog.ui.planner.RoutePlannerState
import fr.lc4918.trailog.ui.planner.RouteState
import fr.lc4918.trailog.ui.planner.StepTarget
import fr.lc4918.trailog.ui.planner.defaultRouteName
import fr.lc4918.trailog.ui.settings.routingProfileLabel
import fr.lc4918.trailog.ui.settings.ProvideSettingsPalette
import fr.lc4918.trailog.ui.settings.settingsPalette
import fr.lc4918.trailog.ui.settings.SettingsCard
import fr.lc4918.trailog.ui.settings.SetRow
import fr.lc4918.trailog.ui.settings.RowDivider
import fr.lc4918.trailog.ui.settings.ValueText
import fr.lc4918.trailog.ui.settings.Hint
import fr.lc4918.trailog.ui.settings.RowIcon
import androidx.compose.material.icons.filled.KeyboardArrowRight
import fr.lc4918.trailog.ui.theme.isDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import fr.lc4918.trailog.ui.profile.ElevationProfile
import fr.lc4918.trailog.ui.profile.SlopeLegend
import fr.lc4918.trailog.ui.profile.TrackInfoColumns
import fr.lc4918.trailog.ui.profile.cursorInfos
import fr.lc4918.trailog.ui.profile.titleInfos
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MainScreen(onSettings: () -> Unit, settingsOpen: Boolean = false, vm: MainViewModel = viewModel()) {
    val folders by vm.folders.collectAsState()
    val layers by vm.layers.collectAsState()
    val providers by vm.providers.collectAsState()
    val composites by vm.composites.collectAsState()
    val basemapFolders by vm.basemapFolders.collectAsState()
    val settings by vm.settings.collectAsState()
    var basemapControlOpen by remember { mutableStateOf(false) }
    var legendOpen by remember { mutableStateOf(false) }
    // Coin haut-gauche du bouton "info", en px fenêtre : la légende s'y adosse (cf. BasemapLegend).
    var legendAnchor by remember { mutableStateOf(IntOffset.Zero) }

    // ---------- téléchargement de carte hors-ligne (SPEC offline_map.md) ----------
    var offlineDrawingActive by remember { mutableStateOf(false) }
    var offlineBboxPoints by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }  // (lon, lat)
    var offlineConfigBbox by remember { mutableStateOf<Bbox?>(null) }
    // Deux facons de dire CE QU'ON TELECHARGE, proposees a l'appui du bouton (cf. OfflineExtentDialog) :
    // un rectangle trace sur la carte, ou le couloir qui borde une trace.
    var offlineExtentChoice by remember { mutableStateOf(false) }
    var offlinePickTrack by remember { mutableStateOf(false) }
    // Trace a border, et son parcours : garde en memoire le temps de l'ecran de configuration.
    var offlineCorridor by remember { mutableStateOf<Pair<LayerEntity, List<Pair<Double, Double>>>?>(null) }
    // Referme complètement le flux (annulation ou fin de config) : sans réinitialiser les points, le
    // rectangle tracé resterait affiché indéfiniment sur la carte une fois l'écran de config quitté.
    fun closeOfflineFlow() {
        offlineConfigBbox = null; offlineBboxPoints = emptyList(); offlineCorridor = null
    }
    // Quitte le tracé de bbox (bouton "Annuler" ou retour système) : contrairement à "Réinitialiser",
    // referme aussi entièrement le mode de saisie, pas seulement les points déjà posés.
    fun cancelOfflineDrawing() { offlineDrawingActive = false; offlineBboxPoints = emptyList() }
    // Hauteur mesurée de la barre de tracé bbox, pour décaler l'échelle graphique au-dessus (SPEC).
    var offlineBarHeightPx by remember { mutableIntStateOf(0) }
    // Hauteur mesurée de la barre de consigne du géocodage, même usage que ci-dessus.
    // Hauteur mesurée du panneau de profil (superposé à la carte, qui garde toujours sa taille pleine),
    // pour décaler les infos du curseur juste au-dessus.
    var profileBarHeightPx by remember { mutableIntStateOf(0) }
    // Visible seulement pour un fond online standard (ni composite, ni MBTiles, ni relief) : cf. SPEC section 1.
    // Masqué aussi pour OSM (tile.openstreetmap.org), dont la politique d'usage interdit le
    // téléchargement en masse et renvoie des tuiles "access blocked".
    val offlineButtonVisible = compositeIdFromBasemapId(settings?.defaultBasemapId ?: "") == null &&
        providers.firstOrNull { it.id == settings?.defaultBasemapId }?.let {
            it.type != "MBTILES" && it.type != "DEM" && !it.urlTemplate.contains("tile.openstreetmap.org", ignoreCase = true)
        } == true

    val renderLayers by vm.renderLayers.collectAsState()
    // Fichiers refusés à l'import : présentés en une seule popup, et seulement quand plus aucun import
    // n'est en cours. Les afficher au fil de l'eau ouvrirait une popup par fichier fautif, en plein lot.
    val importInProgress by vm.importing.collectAsState()
    val importFailures by vm.importFailures.collectAsState()
    var importReport by remember { mutableStateOf<List<MainViewModel.ImportFailure>>(emptyList()) }
    LaunchedEffect(importInProgress.isEmpty(), importFailures.isNotEmpty()) {
        if (importInProgress.isEmpty() && importFailures.isNotEmpty()) importReport = vm.consumeImportFailures()
    }
    val offlineDownload by vm.offlineDownload.collectAsState()
    val activeLayerId by vm.activeLayerId.collectAsState()
    val computed by vm.computed.collectAsState()
    val profileLoading by vm.profileLoading.collectAsState()
    val cursor by vm.cursor.collectAsState()
    val profileZoom by vm.profileZoom.collectAsState()
    val selectedMarkerId by vm.selectedMarkerId.collectAsState()
    val selectedMarkerPos by vm.selectedMarkerPos.collectAsState()
    val markerLayerData by vm.markerLayerData.collectAsState()
    val activeLegends by vm.activeLegends.collectAsState()
    // Marqueur sélectionné, dérivé des états collectés (et non lu via vm.selectedFeature(), qui lit
    // StateFlow.value sans que Compose ne s'y abonne : l'arrivée des propriétés ne déclencherait alors
    // aucune recomposition et l'infobulle resterait bloquée sur son spinner).
    val selectedFeature = remember(markerLayerData, selectedMarkerId) {
        markerLayerData?.features?.firstOrNull { it.id == selectedMarkerId }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val controller = remember { MapController() }
    val ctx = LocalContext.current

    val mode = settings?.sideMenuMode ?: "both"
    controller.tapToleranceDp = settings?.tapToleranceDp ?: 10
    controller.lineTapToleranceDp = settings?.lineTapToleranceDp ?: 16
    controller.rotateGesturesEnabled = settings?.rotateGesturesEnabled ?: false
    val style by vm.mapStyle.collectAsState()
    var styleTick by remember { mutableIntStateOf(0) }

    // ---------- position GPS ----------
    var gpsActive by remember { mutableStateOf(false) }
    // vrai tant qu'un recentrage automatique est dû (activation du capteur, ou retour au premier plan) :
    // consommé dès que la prochaine position arrive
    var pendingCenter by remember { mutableStateOf(false) }
    var lastUserLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) } // (lat, lon)
    // Instant du dernier geste sur la carte (0 = aucun), en temps depuis le démarrage de l'appareil et non
    // en heure murale : le suivi de position s'y réfère, et un changement d'heure - fuseau, mise à l'heure
    // du réseau - ne doit pas le suspendre pour l'éternité ni le réveiller trop tôt.
    var lastUserGestureAt by remember { mutableLongStateOf(0L) }
    var showLocationDisabledDialog by remember { mutableStateOf(false) }
    val locationManager = remember { ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val locationListener = remember {
        LocationListener { loc ->
            controller.setUserLocation(loc.longitude, loc.latitude, loc.accuracy)
            lastUserLocation = loc.latitude to loc.longitude
            if (pendingCenter) { controller.centerOn(loc.latitude, loc.longitude); pendingCenter = false }
        }
    }

    // Symbole du repère de position et sa couleur : celle réglée, sinon celle propre au symbole (le bleu de
    // la puce, le rouge des flèches et de la croix) - une couleur vide n'est pas un choix, c'est l'absence
    // de choix, et elle doit suivre le symbole quand on en change.
    val gpsMarker = GpsMarkerStyle.of(settings?.gpsMarkerStyle)
    val gpsMarkerColor = settings?.gpsMarkerColor?.takeIf { it.isNotBlank() } ?: gpsMarker.defaultColor
    val gpsMarkerSizeDp = (settings?.gpsMarkerSizeDp ?: DefaultGpsMarkerSizeDp).toFloat()

    /*
     * Orientation du téléphone, pour les symboles qui en portent une (les deux flèches).
     *
     * Le capteur n'est écouté que quand la position est affichée ET que le symbole a une direction à
     * montrer : une boussole tourne en permanence, elle n'a pas à le faire pour une puce ronde.
     *
     * Le vecteur de rotation plutôt que l'accéléromètre et le magnétomètre bruts : le système en tire déjà
     * une orientation fusionnée et lissée, là où recombiner les deux à la main donne une flèche qui tremble.
     */
    val declination = remember(lastUserLocation) {
        // Le capteur donne le nord MAGNÉTIQUE, la carte est orientée au nord vrai : l'écart entre les deux
        // dépend du lieu, et atteint plusieurs degrés en Europe. Recalculé à chaque position reçue, ce qui
        // le laisse hors de la boucle du capteur - il ne varie pas d'un pas à l'autre.
        lastUserLocation?.let { (la, lo) ->
            GeomagneticField(la.toFloat(), lo.toFloat(), 0f, System.currentTimeMillis()).declination
        } ?: 0f
    }
    val currentDeclination by rememberUpdatedState(declination)
    val displayRotation = rememberDisplayRotation()
    val headingNeeded = gpsActive && gpsMarker.oriented
    DisposableEffect(headingNeeded, displayRotation) {
        val sensors = (ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            ?.takeIf { headingNeeded }
        // Aucun capteur d'orientation : la fleche reste alors pointee au nord, ce que fait deja tout le
        // reste de la carte - un appareil sans boussole n'a rien d'autre a montrer.
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensors == null || sensor == null) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            private var last = Float.NaN
            override fun onSensorChanged(e: SensorEvent) {
                val deg = azimuthDegrees(e.values, displayRotation) + currentDeclination
                val heading = ((deg % 360f) + 360f) % 360f
                // Sous le degré, le symbole ne bougerait pas d'un pixel : on épargne à MapLibre un
                // redessin par mesure, à 60 ms d'intervalle.
                if (!last.isNaN() && angleGap(heading, last) < 1f) return
                last = heading
                controller.setUserHeading(heading)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensors.unregisterListener(listener) }
    }

    fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun stopGps() {
        locationManager.removeUpdates(locationListener)
        controller.clearUserLocation()
        gpsActive = false
        pendingCenter = false
        lastUserLocation = null
    }

    fun startGps() {
        if (!hasLocationPermission()) return
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        } ?: return
        runCatching {
            locationManager.requestLocationUpdates(provider, 2000L, 5f, locationListener)
            gpsActive = true
            val last = locationManager.getLastKnownLocation(provider)
            if (last != null) {
                controller.setUserLocation(last.longitude, last.latitude, last.accuracy)
                lastUserLocation = last.latitude to last.longitude
                controller.moveTo(last.latitude, last.longitude, 15.0)
                pendingCenter = false
            } else {
                pendingCenter = true   // pas de dernière position connue : on centre dès que le capteur en donne une
            }
        }
    }

    /** Recentre la carte sur la dernière position GPS connue (bouton de recentrage). */
    fun recenterOnGps() { lastUserLocation?.let { (la, lo) -> controller.centerOn(la, lo) } }

    val locationSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (LocationManagerCompat.isLocationEnabled(locationManager)) startGps()
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (LocationManagerCompat.isLocationEnabled(locationManager)) startGps() else showLocationDisabledDialog = true
        }
    }

    fun onGpsButtonTap() {
        when {
            gpsActive -> stopGps()
            !hasLocationPermission() -> locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            !LocationManagerCompat.isLocationEnabled(locationManager) -> showLocationDisabledDialog = true
            else -> startGps()
        }
    }

    // le réglage "afficher le bouton GPS" désactivé pendant que la position est active coupe la position
    // (mais ne désactive pas le capteur lui-même, seulement les mises à jour côté appli)
    LaunchedEffect(settings?.showGpsButton) {
        if (settings?.showGpsButton == false && gpsActive) stopGps()
    }
    DisposableEffect(Unit) { onDispose { locationManager.removeUpdates(locationListener) } }

    // ---------- alerte d'éloignement de la trace suivie ----------
    /*
     * Une trace choisie, la position projetée dessus à chaque mesure du capteur, et une bannière rouge
     * quand l'écart passe le seuil réglé.
     *
     * Tout est suspendu au capteur : sans position, il n'y a rien à projeter. La cloche demande donc de
     * l'allumer avant d'ouvrir son choix de traces, et le suivi s'arrête de lui-même dès qu'il s'éteint -
     * une trace suivie sans position ne dirait plus rien, et se réveillerait au hasard d'un rallumage.
     */
    val alert = remember { OffTrackAlertState() }
    val alertEnabled = settings?.offTrackAlertVisible == true
    val alertDistanceM = settings?.offTrackAlertDistanceM ?: DefaultOffTrackAlertM
    var showAlertNeedsGpsDialog by remember { mutableStateOf(false) }
    // Choix demandé alors que le capteur était éteint : il s'ouvrira dès qu'il sera allumé, et non au
    // retour de la boîte de dialogue - l'utilisateur passe par les réglages du système entre-temps.
    var alertChooserPending by remember { mutableStateOf(false) }

    fun onAlertButtonTap() {
        if (gpsActive) alert.openChooser() else showAlertNeedsGpsDialog = true
    }

    LaunchedEffect(gpsActive, alertChooserPending) {
        if (alertChooserPending && gpsActive) { alertChooserPending = false; alert.openChooser() }
    }
    // Recherche des traces les plus proches : relancée tant que le choix est ouvert et sans réponse, ce qui
    // couvre le cas du capteur allumé mais pas encore fixé - la liste arrive avec la première position.
    LaunchedEffect(alert.chooserOpen, alert.candidates, lastUserLocation) {
        if (!alert.chooserOpen || alert.candidates != null) return@LaunchedEffect
        val (la, lo) = lastUserLocation ?: return@LaunchedEffect
        vm.nearestTracks(la, lo) { alert.candidates = it }
    }
    // Écart à la trace suivie, une position de plus. Sur Default : c'est un balayage de toute la trace,
    // celui-là même que fait déjà la ligne du restant du profil, et il ne tient pas sur le fil principal.
    LaunchedEffect(lastUserLocation, alert.followed, alertDistanceM) {
        val followed = alert.followed ?: return@LaunchedEffect
        val (la, lo) = lastUserLocation ?: return@LaunchedEffect
        val away = withContext(Dispatchers.Default) {
            TrackMeasure.project(followed.samples, lo, la)?.awayM
        } ?: return@LaunchedEffect
        alert.update(away, alertDistanceM.toDouble())
    }
    // Le son accompagne l'entrée en alerte, une fois : il annonce le franchissement, il ne sonne pas tant
    // qu'on est loin. Le retour sous le seuil réarme la suivante (cf. OffTrackAlertState.update).
    val alertSoundOn = settings?.offTrackAlertSound == true
    val alertSoundUri = settings?.offTrackAlertSoundUri.orEmpty()
    LaunchedEffect(alert.alerting) {
        if (alert.alerting && alertSoundOn) playAlertSound(ctx, alertSoundUri)
    }
    // Le réglage éteint, ou le capteur coupé : plus rien à suivre. La liste ouverte se referme avec.
    LaunchedEffect(alertEnabled, gpsActive) {
        if (!alertEnabled || !gpsActive) {
            alert.stop()
            alert.closeChooser()
            alertChooserPending = false
            showAlertNeedsGpsDialog = false
        }
    }
    // Couche supprimée ou masquée en cours de suivi : elle n'est plus sur la carte, on ne la suit plus.
    LaunchedEffect(layers, alert.followed) {
        val followed = alert.followed ?: return@LaunchedEffect
        if (layers.none { it.id == followed.layerId && it.visible }) alert.stop()
    }

    // ---------- recherche de lieu / adresse (géocodage) ----------
    val geo = remember { GeocodeSearchState() }
    var showNoConnectionDialog by remember { mutableStateOf(false) }

    // Interrogation du géocodeur, une frappe stabilisée. Sans ce délai, chaque lettre partirait en requête :
    // le service public le refuserait, et les réponses arriveraient dans le désordre.
    LaunchedEffect(geo.query, settings?.geocodingUrl) {
        val q = geo.query.trim()
        if (q.length < 3) { geo.results = emptyList(); geo.searching = false; return@LaunchedEffect }
        geo.searching = true
        delay(350)
        // Une seconde tentative avant d'abandonner, comme dans le planificateur : le premier appel paie
        // l'ouverture de la liaison et echoue parfois au delai. Un echec reste ici une liste vide - la
        // barre de recherche de la carte n'a pas de place pour un message.
        val base = settings?.geocodingUrl?.takeIf { it.isNotBlank() } ?: Photon.DEFAULT_URL
        val lang = ctx.resources.configuration.locales[0].language
        geo.results = (Photon.search(base, q, lang, GeocodeResultLimit)
            ?: Photon.search(base, q, lang, GeocodeResultLimit)).orEmpty()
        geo.searching = false
    }
    // Le géocodage désactivé dans les réglages alors qu'une recherche est en cours efface tout : sans cela
    // le marqueur noir et son infobulle survivraient au réglage qui les a fait naître.
    LaunchedEffect(settings?.geocodingEnabled) { if (settings?.geocodingEnabled == false) geo.clear() }

    // ---------- mesure sur trace ----------
    val measure = remember { TrackMeasureState() }
    // Hauteur mesurée de la bande de consigne, pour décaler l'échelle graphique au-dessus (cf. bbox).
    var measureBarHeightPx by remember { mutableIntStateOf(0) }
    // La mesure désactivée dans les réglages alors qu'elle est en cours efface tout : sans cela les
    // marqueurs noirs et leur infobulle survivraient au réglage qui les a fait naître (cf. géocodage).
    LaunchedEffect(settings?.trackMeasureEnabled) {
        if (settings?.trackMeasureEnabled == false) measure.clear()
    }
    // Trace masquée ou supprimée alors qu'elle portait la mesure : celle-ci n'a plus de support. La laisser
    // afficherait deux marqueurs noirs et une distance au milieu d'une carte vide, sans rien à quoi les
    // rapporter.
    LaunchedEffect(layers, measure.start) {
        val id = measure.start?.layerId ?: return@LaunchedEffect
        if (layers.none { it.id == id && it.visible }) measure.clear()
    }

    // ---------- services et réglages partagés (point de la carte, planificateur) ----------
    val imperialUnits = settings?.units == "imperial"
    val routeEngine = RouteEngine.of(settings?.routeEngine)
    val routingUrl = Router.baseOf(routeEngine, settings?.routeUrl(routeEngine))
    val geocodingBase = settings?.geocodingUrl?.takeIf { it.isNotBlank() } ?: Photon.DEFAULT_URL
    // Lissage de l'altitude, réglage commun au profil des traces : un itinéraire calculé n'a pas de raison
    // d'être coloré selon d'autres classes de pente que celles d'une trace, au même endroit.
    val profileSmoothingM = (settings?.profileSmoothingM ?: 5).toDouble()

    // ---------- point quelconque de la carte (appui long) ----------
    val mapPoint = remember { MapPointState() }
    // Hauteur mesurée de la barre de consigne du choix d'un point, pour décaler l'échelle graphique
    // au-dessus (cf. bbox et mesure sur trace).
    var pointBarHeightPx by remember { mutableIntStateOf(0) }
    // Discipline des mesures : celle réglée dans les réglages, et non celle du planificateur, qui la choisit
    // pour le trajet qu'on y compose. Une distance demandée sur la carte n'a pas de composition derrière elle.
    val routingProfile = RoutingProfile.of(settings?.routingProfile)
    // Préférences de tracé de cette discipline (voies, relief, revêtement). Prises aux réglages, comme la
    // discipline elle-même ; sans réglages chargés, celles que la discipline porte par défaut.
    val measurePrefs = settings?.routePrefs(routingProfile) ?: RoutingPrefs.defaultFor(routingProfile)

    /**
     * Adresse du point désigné (géocodage inverse).
     *
     * Relancée sur le seul point : le service, lui, ne change qu'en réglages, et l'adresse d'un point déjà
     * affiché n'a aucune raison de changer sous les yeux de qui la lit.
     *
     * Une seconde tentative avant d'abandonner, comme la recherche par le nom : le premier appel paie
     * l'ouverture de la liaison et échoue parfois au délai.
     */
    LaunchedEffect(mapPoint.point) {
        val (lon, lat) = mapPoint.point ?: return@LaunchedEffect
        mapPoint.publishAddress(AddressState.Loading)
        val lang = ctx.resources.configuration.locales[0].language
        val r = Photon.reverse(geocodingBase, lon, lat, lang) ?: Photon.reverse(geocodingBase, lon, lat, lang)
        mapPoint.publishAddress(when {
            r == null -> AddressState.Failed
            r.isEmpty() -> AddressState.NotFound
            else -> AddressState.Done(r.first().lines)
        })
    }

    /**
     * Un itinéraire depuis (fromLat, fromLon) jusqu'au point désigné, traduit en état affichable. Les
     * deux points sont donnés en (lat, lon), comme les attend le moteur.
     *
     * Les points rendus par le moteur passent par le même calcul que les traces importées : distance
     * cumulée, lissage de l'altitude au réglage de l'utilisateur, pente par point. La ligne sur la carte et
     * sa teinte sortent ensuite de ces mêmes points.
     *
     * Aucune décimation (contrairement aux traces, ramenées à 2000 points) : ici les points dessinent aussi
     * la ligne sur la carte, et en retirer un sur deux couperait les virages du tracé affiché.
     */
    suspend fun measureTo(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): MeasureState {
        val r = Router.route(ctx, routeEngine, routingUrl, listOf(fromLat to fromLon, toLat to toLon),
            routingProfile, measurePrefs) ?: return MeasureState.Failed
        val track = withContext(Dispatchers.Default) {
            if (r.points.size < 2) null
            else TrackMath.compute(r.points, smoothingM = profileSmoothingM, maxPoints = 0, ignoreStops = false)
        }
        return MeasureState.Done(r.meters, r.seconds, track)
    }

    // Origine de la mesure depuis la position, figée à la première position reçue après la demande : le
    // capteur en livre une toutes les 2 s, et les suivre lancerait autant de requêtes d'itinéraire.
    LaunchedEffect(mapPoint.positionMeasure, lastUserLocation) {
        if (mapPoint.positionMeasure == MeasureState.Loading) {
            lastUserLocation?.let { (la, lo) -> mapPoint.fixPositionOrigin(la, lo) }
        }
    }
    // Capteur éteint alors qu'une mesure attendait encore sa position d'origine : elle ne partira jamais,
    // et son spinner tournerait indéfiniment. On l'abandonne, ce qui est exactement ce qu'elle est devenue.
    LaunchedEffect(gpsActive) {
        if (!gpsActive && mapPoint.positionMeasure == MeasureState.Loading && mapPoint.positionOrigin == null) {
            mapPoint.publishPositionMeasure(MeasureState.Failed)
        }
    }
    LaunchedEffect(mapPoint.point, mapPoint.positionOrigin, routingUrl, routingProfile, measurePrefs) {
        val (lon, lat) = mapPoint.point ?: return@LaunchedEffect
        val (la, lo) = mapPoint.positionOrigin ?: return@LaunchedEffect
        mapPoint.publishPositionMeasure(MeasureState.Loading)
        mapPoint.publishPositionMeasure(measureTo(la, lo, lat, lon))
    }
    LaunchedEffect(mapPoint.point, mapPoint.refPoint, routingUrl, routingProfile, measurePrefs) {
        val (lon, lat) = mapPoint.point ?: return@LaunchedEffect
        val (refLon, refLat) = mapPoint.refPoint ?: return@LaunchedEffect
        mapPoint.publishPointMeasure(MeasureState.Loading)
        mapPoint.publishPointMeasure(measureTo(refLat, refLon, lat, lon))
    }

    // ---------- planificateur d'itinéraire ----------
    val planner = remember { RoutePlannerState() }
    // ---------- retouche des traces ----------
    val edit = remember { TrackEditState() }
    val canUndo by vm.canUndo.collectAsState()
    val cutGeometry by vm.cutGeometry.collectAsState()
    // Le marqueur retire : la geometrie gardee pour lui n'a plus d'objet, et elle pese quelques megaoctets.
    LaunchedEffect(edit.cut == null) { if (edit.cut == null) vm.loadCutGeometry(null) }
    // Le reglage coupe : la barre se referme et rend les taps a la carte, comme le geocodage et la mesure.
    LaunchedEffect(settings?.trackEditEnabled) {
        if (settings?.trackEditEnabled == false) edit.close()
    }
    var reverseConfirm by remember { mutableStateOf<LayerEntity?>(null) }
    val sameSegmentMessage = stringResource(R.string.edit_same_segment)
    val cannotSplitMessage = stringResource(R.string.split_impossible)
    val routedFellBack = stringResource(R.string.join_fell_back_straight)

    /**
     * Tap sur une trace en mode retouche : selon l'outil, il inverse, pose le marqueur de coupe, ou
     * designe un segment a joindre.
     *
     * Le point retenu n'est jamais celui du doigt mais son PROJETE sur la geometrie complete (cf.
     * TrackEdit.locate) : c'est ce qui permet de couper entre deux sommets, la ou le curseur du profil ne
     * savait designer que des points deja presents.
     */
    fun onEditTap(key: String, lon: Double, lat: Double) {
        val id = key.removePrefix("ly").toLongOrNull() ?: return
        val layer = layers.firstOrNull { it.id == id }?.takeIf { it.hasLine } ?: return
        when (edit.tool) {
            EditTool.REVERSE -> {
                if (layer.hasTime) reverseConfirm = layer else vm.reverseLayer(layer)
                edit.choose(EditTool.NONE)
            }
            EditTool.CUT -> {
                edit.busy = true
                // La geometrie sert a la bulle du marqueur, qui doit savoir ce qu'elle recouvrirait.
                vm.loadCutGeometry(layer)
                vm.locateOnLayer(layer, lon, lat) { hit ->
                    edit.busy = false
                    if (hit != null) edit.placeCut(CutTarget(layer.id, layer.name, hit))
                }
            }
            EditTool.JOIN -> {
                edit.busy = true
                vm.locateOnLayer(layer, lon, lat) { hit ->
                    edit.busy = false
                    if (hit != null) edit.pick(SegmentRef(layer.id, layer.name, hit.segment), sameSegmentMessage)
                }
            }
            EditTool.NONE -> Unit
        }
    }
    var importDialog by remember { mutableStateOf(false) }
    /**
     * Fond des boutons poses sur la carte, quand le reglage le demande.
     *
     * Sa taille est reglee (cf. SettingsEntity.mapButtonSizeDp) et ne touche QUE le carre dessine : la zone
     * tactile reste aux 48 dp de Material, quel que soit le curseur.
     *
     * Tous les boutons le recoivent, y compris le GPS allume : son etat se lit desormais a la couleur de
     * son dessin, non a un aplat qui le distinguait de ses voisins.
     */
    val mapButtonSize = (settings?.mapButtonSizeDp ?: MinMapButtonSizeDp).dp
    // Ornements poses sur la carte : en theme sombre, le fond et le dessin s'echangent (cf. MapChromeBg).
    val darkChrome = isDarkTheme(settings?.theme)
    val chromeBg = mapChromeBg(darkChrome)
    val chromeFg = mapChromeFg(darkChrome)
    val controlBg: Modifier = if (settings?.controlButtonsBackground != true) Modifier
        else Modifier.mapButtonBackground(chromeBg.copy(alpha = ControlButtonBgAlpha), mapButtonSize)
    // Hauteur reelle de la bande, mesuree : le cadrage du parcours doit degager ce qu'elle recouvre, et
    // elle varie avec le nombre d'etapes et la presence du profil.
    var plannerBandHeightPx by remember { mutableIntStateOf(0) }
    /*
     * Hauteur a degager en haut de la carte lors d'un cadrage.
     *
     * Ce n'est pas seulement la barre de statut, sous laquelle la carte passe en mode bord-a-bord : la
     * colonne de boutons du coin haut-gauche (menu, GPS, recentrage, geocodeur, planificateur) la recouvre
     * aussi, et un parcours cadre au plus juste passait dessous. On mesure donc cette colonne - sa hauteur
     * varie avec le nombre de boutons affiches - et elle comprend deja la marge de barre de statut.
     */
    var topControlsHeightPx by remember { mutableIntStateOf(0) }
    val statusBarTopPx = maxOf(
        WindowInsets.statusBars.getTop(LocalDensity.current),
        topControlsHeightPx,
    )

    /**
     * Calcul du parcours, relancé par tout ce qui le rend caduc (cf. RoutePlannerState.revision).
     *
     * La position actuelle est résolue ICI et non au moment où l'étape a été choisie : on pose souvent ses
     * étapes puis on se déplace avant de lancer, et c'est bien d'où l'on est alors qu'on veut partir.
     */
    // La carte cadre le parcours ENTIER a son apparition, et seulement alors : une fois le trajet a
    // l'ecran, l'utilisateur le fait glisser et zoome a sa guise, et recadrer a chaque etape ajoutee
    // defairait son travail. Un parcours qui disparait (moins de deux etapes, ou echec) remet le drapeau,
    // pour que le suivant soit cadre a son tour - c'est le cas des trois etapes dont on en retire deux.
    var routeFramed by remember { mutableStateOf(false) }
    var framePending by remember { mutableStateOf(false) }
    /*
     * La vue courante est-elle encore celle que NOUS avons cadree ?
     *
     * Tant qu'elle l'est, tout changement de la surface de carte reellement visible - la bande qui se
     * replie, se redeploie ou se ferme - doit rejouer le cadrage sur cette nouvelle surface. Des que
     * l'utilisateur touche la carte, en la faisant glisser ou en pincant, ce drapeau retombe : la vue est
     * desormais la sienne, et rien ne doit plus la lui reprendre.
     */
    var autoFramed by remember { mutableStateOf(false) }
    // Les préférences suivent la discipline CHOISIE DANS LA BANDE, non celle des réglages : le planificateur
    // change de discipline sans quitter la carte, et passer au VTT doit amener avec lui ce qu'on demande à
    // un VTT. Changer un réglage pendant qu'un parcours est affiché le recalcule (la clé de l'effet).
    val plannerPrefs = settings?.routePrefs(planner.profile) ?: RoutingPrefs.defaultFor(planner.profile)
    LaunchedEffect(planner.revision, routingUrl, planner.profile, plannerPrefs, profileSmoothingM) {
        val targets = planner.targets
        if (targets.size < 2) {
            planner.publish(RouteState.Idle); routeFramed = false; framePending = false
            return@LaunchedEffect
        }
        planner.beginRecompute()
        val pts = targets.map { t ->
            when (t) {
                is StepTarget.Place -> t.place.lat to t.place.lon
                StepTarget.CurrentPosition -> lastUserLocation ?: run {
                    planner.publish(RouteState.Failed); routeFramed = false; return@LaunchedEffect
                }
            }
        }
        val r = Router.route(ctx, routeEngine, routingUrl, pts, planner.profile, plannerPrefs)
        if (r == null || r.points.size < 2) {
            planner.publish(RouteState.Failed); routeFramed = false; return@LaunchedEffect
        }
        val track = withContext(Dispatchers.Default) {
            TrackMath.compute(r.points, smoothingM = profileSmoothingM, maxPoints = 0, ignoreStops = false)
        }
        planner.publish(RouteState.Done(r.meters, r.seconds, track))
        if (!routeFramed) framePending = true
    }
    /*
     * Cadrage differe, et non enchaine au calcul : au moment ou le parcours arrive, la bande n'a pas encore
     * grandi de sa zone resultats, et sa hauteur mesuree est celle d'AVANT. Cadrer tout de suite laissait
     * donc la fin du trajet cachee derriere la bande, qui s'etendait juste apres.
     *
     * L'effet depend de la hauteur de la bande : il se relance a chaque etape de sa croissance, et le court
     * delai annule les passes intermediaires. Le cadrage n'a lieu qu'une fois la bande stabilisee.
     */
    LaunchedEffect(framePending, plannerBandHeightPx, planner.route) {
        if (!framePending) return@LaunchedEffect
        val s = planner.done?.track?.samples ?: return@LaunchedEffect
        delay(120)
        controller.fitTo(
            s.minOf { it.lon }, s.minOf { it.lat }, s.maxOf { it.lon }, s.maxOf { it.lat },
            topPaddingPx = statusBarTopPx, bottomPaddingPx = plannerBandHeightPx,
        )
        routeFramed = true
        autoFramed = true
        framePending = false
    }
    /*
     * La carte suit le zoom du profil : grossir une portion du graphique recadre la carte sur cette
     * portion, revenir a la vue complete la recadre sur tout le parcours. Les deux representent le meme
     * trajet, et regarder de pres sur l'un sans l'autre oblige a refaire le rapprochement de tete.
     *
     * Relance sur la seule fenetre de zoom, et non sur le parcours : un recalcul ne doit pas deplacer la
     * carte (cf. routeFramed), et la fenetre retombant a null au meme moment, la cle ne change pas.
     */
    LaunchedEffect(planner.zoomRange) {
        val all = planner.done?.track?.samples ?: return@LaunchedEffect
        val s = planner.zoomRange?.let { z -> all.subList(z.first, (z.last + 1).coerceAtMost(all.size)) } ?: all
        if (s.size < 2) return@LaunchedEffect
        controller.fitTo(
            s.minOf { it.lon }, s.minOf { it.lat }, s.maxOf { it.lon }, s.maxOf { it.lat },
            topPaddingPx = statusBarTopPx, bottomPaddingPx = plannerBandHeightPx,
        )
    }
    // Le planificateur désactivé dans les réglages pendant qu'il est ouvert le referme : sans cela sa bande
    // survivrait au réglage qui l'a fait naître.
    LaunchedEffect(settings?.routePlannerEnabled) {
        if (settings?.routePlannerEnabled == false && planner.open) planner.close()
    }
    // Tracés posés sur la carte, teintés par classe de pente comme l'aire du profil : le parcours du
    // planificateur, et les itinéraires mesurés depuis un point de la carte. Un seul calque pour les trois :
    // ils ont même style, et rien n'impose d'ordre entre eux.
    // Relancé sur le numéro d'ordre des mesures et non sur les itinéraires eux-mêmes (cf. measureRevision).
    val routeSlopeTint = settings?.profileSlope != false
    LaunchedEffect(planner.revision, planner.route, mapPoint.measureRevision, styleTick, routeSlopeTint) {
        controller.setRouteLines(listOfNotNull(planner.done?.track) + mapPoint.routeTracks, routeSlopeTint)
    }
    // Curseur du profil du planificateur : il n'entre pas en concurrence avec celui d'une trace, le
    // planificateur fermant le profil ouvert quand il s'ouvre (cf. plus bas).
    LaunchedEffect(planner.cursor, planner.route) {
        val s = planner.done?.track?.samples ?: return@LaunchedEffect
        val p = planner.cursor?.let { TrackMath.sampleAt(s, it) }
        if (p != null) controller.setCursor(p.lon, p.lat) else controller.clearCursor()
    }

    /** Octets GPX du parcours calculé, sous [name] : servent au téléchargement comme à l'import. */
    fun routeGpx(name: String): ByteArray? {
        val t = planner.done?.track ?: return null
        return GpxWriter.write(name, t.samples, t.hasZ)
    }

    val currentPositionLabel = stringResource(R.string.planner_current_position)
    // Téléchargement : on laisse le système choisir où écrire (SAF), plutôt que d'imposer un dossier -
    // le fichier a vocation à sortir de l'application, vers un GPS ou un autre appareil.
    val gpxSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        val name = defaultRouteName(planner.targets, currentPositionLabel)
        val bytes = routeGpx(name)
        if (uri != null && bytes != null) {
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
        }
    }

    /*
     * Les deux mesures s'assurent d'abord qu'elles pourront aboutir : un itinéraire est une requête, et
     * sans réseau la mesure ne rendrait qu'un échec, que rien n'expliquerait. Le bouton "depuis la
     * position" n'est proposé que le capteur allumé (cf. MapPointBubble), il n'a donc pas à s'en soucier.
     */
    fun onDistanceFromPositionTap() {
        if (ServiceUrl.needsInternet(routingUrl) && !NetworkStatus.hasInternet(ctx)) showNoConnectionDialog = true
        else mapPoint.requestDistanceFromPosition()
    }

    /** "Distance depuis un point" : passe en mode de saisie, la mesure part au tap sur la carte. */
    fun onDistanceFromPointTap() {
        if (ServiceUrl.needsInternet(routingUrl) && !NetworkStatus.hasInternet(ctx)) showNoConnectionDialog = true
        else mapPoint.startPickingPoint()
    }

    /** Ouvre (ou referme) la barre de recherche, après s'être assuré qu'elle pourra aboutir : ouvrir un
     *  champ dont aucune frappe ne rendra jamais rien laisserait croire à un service muet. */
    fun onGeocodeButtonTap() {
        val base = settings?.geocodingUrl?.takeIf { it.isNotBlank() } ?: Photon.DEFAULT_URL
        when {
            geo.searchOpen -> geo.closeSearch()
            ServiceUrl.needsInternet(base) && !NetworkStatus.hasInternet(ctx) -> showNoConnectionDialog = true
            else -> geo.openSearch()
        }
    }

    // barre de statut : icônes noires en mode transparent, sinon inverse du thème
    val dark = when (settings?.theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val transparentBar = settings?.statusBarTransparent ?: false
    val view = LocalView.current
    LaunchedEffect(dark, transparentBar, drawerState.isOpen, settingsOpen) {
        val light = when {
            settingsOpen || drawerState.isOpen -> !dark
            transparentBar -> true                 // toujours noir au-dessus de la carte transparente
            else -> !dark
        }
        androidx.core.view.WindowCompat.getInsetsController(
            (view.context as android.app.Activity).window, view).isAppearanceLightStatusBars = light
    }

    // Compteur de mouvement de camera : incremente a CHAQUE image d'un deplacement, la ou idleTick
    // n'attend que l'immobilisation. Ce qui doit rester colle a son point de carte le suit (l'orientation
    // de la boussole, les infobulles du geocodage) ; le reste s'en passe et se recalcule a l'arret.
    var moveTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(controller) {
        controller.onCameraMove = { moveTick++ }
        controller.onUserMoveBegin = {
            autoFramed = false
            // Un geste rend la carte à son propriétaire : le suivi de position se tait le temps du silence
            // (cf. MapFollow). Ce rappel ne se déclenche que sur un geste humain - centerOn et fitTo ne le
            // font pas -, sans quoi le suivi s'interdirait lui-même à son premier recentrage.
            lastUserGestureAt = SystemClock.elapsedRealtime()
        }
        // Appui long sur un endroit quelconque : le contrôleur a déjà écarté les traces, les marqueurs et
        // les modes de saisie exclusifs (cf. handleLongPress), il ne reste ici qu'à ouvrir le point.
        controller.onLongPressEmpty = { lon, lat -> mapPoint.open(lon, lat) }
    }

    /*
     * La carte suit le porteur : elle se recentre à chaque position reçue, et se tait cinq secondes après
     * chaque geste (cf. MapFollow, qui porte les règles et leurs raisons).
     *
     * L'effet se relance sur la position ET sur l'heure du dernier geste, et c'est ce qui le fait marcher
     * dans les deux sens : une position pendant le silence attend ce qu'il en reste, et un geste annule le
     * recentrage en cours de préparation pour repartir sur cinq secondes pleines. Le retour a donc lieu
     * même immobile, l'attente du geste arrivant à son terme sans qu'aucune position nouvelle ne l'y aide.
     */
    val followsPosition = MapFollow.follows(
        enabled = settings?.mapFollowPosition != false,
        gpsActive = gpsActive,
        plannerOpen = planner.open,
        layerOpen = activeLayerId != null,
    )
    LaunchedEffect(followsPosition, lastUserLocation, lastUserGestureAt) {
        if (!followsPosition) return@LaunchedEffect
        val (la, lo) = lastUserLocation ?: return@LaunchedEffect
        val wait = MapFollow.waitMs(SystemClock.elapsedRealtime(), lastUserGestureAt)
        if (wait > 0L) delay(wait)
        controller.centerOn(la, lo)
    }
    // Modes de saisie exclusifs (tracé de la bounding box hors-ligne, choix des points de mesure, choix du
    // point de référence d'une distance) : tout tap leur revient, y compris sur une trace ou un marqueur,
    // qui n'ouvrent alors ni profil ni infobulle. Hors de ces modes, la sélection habituelle reprend.
    //
    // Ils ne sont jamais actifs ensemble (le tracé de bbox part du menu latéral, qui ferme la carte ; les
    // deux autres partent d'une barre ou d'une infobulle que l'autre a fait disparaître), mais l'ordre
    // reste explicite : celui qui occupe déjà l'écran garde les taps.
    LaunchedEffect(controller, offlineDrawingActive, measure.picking, mapPoint.pickingPoint, edit.awaitingTap) {
        when {
            offlineDrawingActive -> controller.onRawTap = { lon, lat ->
                if (offlineBboxPoints.size < 2) offlineBboxPoints = offlineBboxPoints + (lon to lat)
            }
            // Le point retenu n'est pas celui du doigt mais son projeté sur la trace : le calcul passe par
            // le ViewModel, seul à savoir lire les profils des couches (cf. pickMeasureStart).
            measure.picking -> controller.onRawTap = { lon, lat ->
                val started = measure.start
                if (started == null) {
                    // Couches candidates demandées d'abord à l'index de rendu de la carte : lui seul sait
                    // dire, sans rien lire, quelles traces passent vraiment sous le doigt.
                    val keys = controller.lineKeysNear(lon, lat)
                    vm.pickMeasureStart(lon, lat, keys) { p -> if (p != null && measure.picking) measure.chooseStart(p) }
                } else {
                    vm.pickMeasureEnd(started, lon, lat) { p, path -> if (measure.picking) measure.chooseEnd(p, path) }
                }
            }
            mapPoint.pickingPoint -> controller.onRawTap = { lon, lat -> mapPoint.chooseRefPoint(lon, lat) }
            // Retouche : les taps sur une trace lui reviennent, et n'ouvrent donc pas de profil. Le vide
            // ne referme rien - sortir du mode se fait par la barre, pas par un tap a cote.
            edit.awaitingTap -> {
                controller.onRawTap = null
                controller.onPickPoint = null
                controller.onPickLine = { key, lon, lat -> onEditTap(key, lon, lat) }
                controller.onTapEmpty = null
            }
            else -> {
                controller.onRawTap = null
                controller.onPickPoint = { key, fid, lon, lat -> vm.onPickPoint(key, fid, lon, lat) }
                controller.onPickLine = { key, lon, lat -> vm.onPickLine(key, lon, lat) }
                controller.onTapEmpty = { vm.closeOnEmpty() }
            }
        }
    }
    val bearing = remember(moveTick) { controller.bearing() }
    // cadrage sur les couches récemment importées à la fermeture du menu
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed) {
            vm.consumePendingFit()?.let { b -> controller.fitTo(b[0], b[1], b[2], b[3]) }
        }
    }

    // import : dossier -> fichier (une couche peut contenir points et/ou traces, pas de choix de type en amont)
    var pendingFolder by remember { mutableStateOf<Long?>(null) }
    var folderPicker by remember { mutableStateOf(false) }
    var newFolderDialog by remember { mutableStateOf(false) }
    val defaultFolderName = stringResource(R.string.label_new_folder)
    var newFolderName by remember { mutableStateOf(defaultFolderName) }
    val picker = rememberLauncherForActivityResult(remember { PickFile() }) { uris ->
        uris.forEach { uri ->
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            } ?: "import"
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@forEach
            vm.importLayer(bytes, name, pendingFolder)
        }
    }
    // Photos locales des waypoints (GPX OruxMaps/OsmAnd/Locus/Garmin) : lues en acces fichier direct dans le
    // stockage partage a l'import (resolveLocalImages) -> permission de lecture des images. Demandee juste
    // avant le selecteur de fichier ; un refus n'empeche pas l'import (les photos afficheront "introuvable").
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    fun doLaunchPicker() { picker.launch(settings?.importDir?.takeIf { it.isNotBlank() }?.toUri()) }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { doLaunchPicker() }
    fun launchPicker() {
        if (ContextCompat.checkSelfPermission(ctx, mediaPermission) == PackageManager.PERMISSION_GRANTED) doLaunchPicker()
        else mediaPermissionLauncher.launch(mediaPermission)
    }

    // import d'image pour un champ IMAGE d'infobulle (PropertyEditor) : callback enregistré au moment du tap
    var pendingImageCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u -> pendingImageCallback?.let { cb -> vm.importFeatureImage(u, cb) } }
    }

    // appliquer les couches à la carte
    val density = LocalDensity.current
    val markerPx = with(density) { (settings?.markerSize ?: 36).dp.toPx() }
    LaunchedEffect(renderLayers, styleTick, markerPx) { if (controller.style != null) controller.setLayers(renderLayers, markerPx) }
    // Coins/rectangle du tracé bbox hors-ligne (SPEC section 2) : source/couches dédiées (croix "viseur"),
    // indépendantes du système de couches importées ci-dessus.
    LaunchedEffect(offlineBboxPoints, styleTick) { if (controller.style != null) controller.setBboxDraw(offlineBboxPoints) }
    // Marqueurs noirs du géocodage : le lieu trouvé, et le point de référence d'une mesure de distance.
    // Calques carte (comme le marqueur sélectionné) : ils suivent seuls le pan et le zoom.
    LaunchedEffect(geo.place, styleTick, markerPx) {
        controller.setGeocodeMarker(false, geo.place?.lon, geo.place?.lat, markerPx)
    }
    // Marqueurs noirs des deux bouts d'une mesure sur trace : mêmes épingles, même calque carte.
    LaunchedEffect(measure.markers, styleTick, markerPx) {
        controller.setMeasureMarkers(measure.markers, markerPx)
    }
    // Épingles noires du point désigné par un appui long et de son point de référence : mêmes épingles
    // encore, sur leur propre calque - les deux fonctions peuvent être à l'écran en même temps.
    LaunchedEffect(mapPoint.markers, styleTick, markerPx) {
        controller.setMapPointMarkers(mapPoint.markers, markerPx)
    }
    // Symbole du repère de position, tel que les réglages le décrivent. Rejoué sur styleTick : un
    // changement de fond recharge le style, qui emporte sources et couches - le repère est reposé avec la
    // dernière position connue, sans attendre que le capteur en donne une nouvelle.
    LaunchedEffect(gpsMarker, gpsMarkerColor, gpsMarkerSizeDp, styleTick) {
        controller.setUserMarker(gpsMarker, gpsMarkerColor, gpsMarkerSizeDp)
    }
    LaunchedEffect(cursor, computed) {
        val along = cursor; val s = computed?.samples
        val p = if (along != null && s != null) TrackMath.sampleAt(s, along) else null
        if (p != null) {
            controller.setCursor(p.lon, p.lat)
            // Curseur sorti de l'ecran : la carte le rejoint. Deplacer le curseur sur le profil, c'est
            // demander a voir cet endroit-la ; le laisser hors champ rendrait le geste muet des qu'on
            // s'eloigne de la portion visible.
            if (!controller.isOnScreen(p.lon, p.lat)) controller.centerOn(p.lat, p.lon)
        } else {
            controller.clearCursor()
        }
    }
    // Synchronisation carte <-> zoom du profil : on recadre UNIQUEMENT sur l'emprise de la portion zoomée
    // (sélection A/B). Un simple tap sur une trace, sans zoom actif, ne déplace jamais la carte : on garde la
    // vue courante de l'utilisateur (pas de "zoom global" sur toute la trace, qui recadrait brutalement et
    // dont l'animation ralentissait l'affichage du premier profil). L'"expand" jusqu'à la vue complète laisse
    // donc la carte là où elle est. Fermer le profil ne redéclenche rien ici (profileZoomStack revidé).
    LaunchedEffect(profileZoom, computed) {
        val range = profileZoom ?: return@LaunchedEffect
        val samples = computed?.samples ?: return@LaunchedEffect
        if (range.last >= samples.size) return@LaunchedEffect
        val sub = samples.subList(range.first, range.last + 1)
        controller.fitTo(sub.minOf { it.lon }, sub.minOf { it.lat }, sub.maxOf { it.lon }, sub.maxOf { it.lat })
    }

    // positionnement initial : dernier affichage si enregistré, sinon données visibles, sinon France
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(styleTick, settings, renderLayers) {
        val st = settings ?: return@LaunchedEffect
        if (positioned || styleTick == 0) return@LaunchedEffect
        if (st.hasCamera) {
            controller.moveTo(st.lastLat, st.lastLon, st.lastZoom); positioned = true
        } else {
            val ls = layers.filter { it.visible }
            val w = ls.map { it.west }.filter { it != 0.0 }.minOrNull()
            val s = ls.map { it.south }.filter { it != 0.0 }.minOrNull()
            val e = ls.map { it.east }.filter { it != 0.0 }.maxOrNull()
            val n = ls.map { it.north }.filter { it != 0.0 }.maxOrNull()
            if (w != null && s != null && e != null && n != null) controller.fitTo(w, s, e, n)
            else controller.moveTo(46.6, 2.4, 4.8)   // centre France
            positioned = true
        }
    }

    // Activer un fond national alors que la carte regarde un autre pays ne montre rien : le service ne
    // sert pas cette zone, il ne reste que le gris de no-tile-background ou le blanc que renvoient les
    // WMS hors de chez eux. On recadre alors sur l'emprise du fond. Les 2 s laissent aux tuiles le temps
    // d'arriver sur une connexion lente : recadrer une carte qui allait s'afficher serait pire que ne
    // rien faire. Relancé sur styleTick, pas seulement sur l'identifiant : le style met un instant à
    // s'appliquer, et sonder avant que la caméra ne soit posée donnerait une emprise sans rapport.
    LaunchedEffect(settings?.defaultBasemapId, styleTick) {
        if (styleTick == 0 || !positioned) return@LaunchedEffect
        val id = settings?.defaultBasemapId ?: return@LaunchedEffect
        val provider = providers.firstOrNull { it.id == id } ?: return@LaunchedEffect
        val bounds = CoverageBounds.of(provider) ?: return@LaunchedEffect
        delay(2_000)
        val viewport = controller.visibleBounds() ?: return@LaunchedEffect
        val zoom = controller.cameraState()?.third?.toInt() ?: return@LaunchedEffect
        if (CoverageProbe.probe(provider, viewport, zoom) != CoverageProbe.Coverage.EMPTY) return@LaunchedEffect
        controller.fitTo(bounds.west, bounds.south, bounds.east, bounds.north)
    }

    // infobulle
    var idleTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(controller) {
        controller.onCameraIdle = {
            idleTick++
            if (positioned) controller.cameraState()?.let { (la, lo, z) -> vm.saveCameraState(la, lo, z) }
        }
    }

    /*
     * ---------- Points d'interet (DATAtourisme) ----------
     *
     * Charges sur l'emprise visible, apres un temps d'arret : un deplacement de carte emet des dizaines
     * d'evenements, et sans cette attente chacun partirait en requete. On ne redemande rien tant que la vue
     * reste dans ce qui a deja ete charge - c'est ce qui tient les appels loin du quota du service, bien
     * plus que le delai lui-meme (cf. PoiLoading).
     */
    val poi = remember { PoiState() }
    val poiRepo = remember(ctx) { PoiRepository(AppDatabase.get(ctx).pois()) }
    LaunchedEffect(settings?.poiEnabled) {
        if (settings?.poiEnabled == false) poi.hide()
    }
    val poiFilters = remember(settings?.poiHiddenCategories, settings?.poiBikeGroups) {
        PoiFilters.of(settings?.poiHiddenCategories, settings?.poiBikeGroups)
    }
    LaunchedEffect(poi.visible, idleTick, poiFilters) {
        if (!poi.visible) return@LaunchedEffect
        delay(PoiLoading.DEBOUNCE_MS)
        val zoom = controller.cameraState()?.third ?: return@LaunchedEffect
        if (zoom < PoiLoading.MIN_ZOOM) { poi.tooFar(); return@LaunchedEffect }
        val vue = controller.visibleBounds() ?: return@LaunchedEffect
        if (!poi.needsLoad(vue, poiFilters)) return@LaunchedEffect
        val box = PoiLoading.grow(vue)
        poi.beginLoad()
        // Deux requetes au plus : celles qui se contentent du catalogue, et celles limitees au theme
        // velo. Un groupe sans categorie cochee ne pese dans aucune des deux. Le depot se charge du
        // cache - service d'abord, dernier connu si le reseau manque (cf. PoiRepository).
        val (libres, velo) = poiFilters.queries()
        val charge = poiRepo.load(Datatourisme.DEFAULT_URL, box, libres, velo)
        // Rien a montrer ET pas de reseau : on ne sait pas si la zone est vide ou si le service n'a pas
        // repondu. L'ecran le dit, plutot que de laisser croire a une region sans un seul cafe.
        val horsLigne = charge.pois.isEmpty() && !NetworkStatus.hasInternet(ctx)
        poi.publish(box, poiFilters, charge.pois, charge.fromCache, horsLigne)
        poi.dropSelectionIfGone()
    }
    // Les marqueurs suivent la liste et l'extinction de la couche, et rien d'autre : les redessiner a
    // chaque image d'un deplacement couterait un aller-retour vers MapLibre pour un resultat identique.
    LaunchedEffect(poi.pois, poi.visible, markerPx) {
        controller.setPoiMarkers(
            if (poi.visible) poi.pois.map {
                PoiMarker(it.uuid, it.lon, it.lat, poiGroupColor(it.category.group), poiIcon(it.category))
            } else emptyList(),
            markerPx,
        )
    }
    LaunchedEffect(controller) {
        controller.onPickPoi = { uuid, _, _ -> poi.selectById(uuid) }
    }
    // Position écran du marqueur sélectionné. Basée sur selectedMarkerPos (connue dès le tap) et non sur la
    // feature chargée : l'infobulle peut ainsi se placer avant l'arrivée de ses propriétés.
    // Projetée pendant la composition et non dans un effet : un effet ne s'exécute qu'après la composition,
    // si bien qu'en passant d'un marqueur à l'autre (infobulle déjà ouverte) une première passe se serait
    // faite avec la position de l'ancien marqueur -> recentrage de carte calculé sur un placement faux.
    val bubbleOffset = remember(selectedMarkerPos, idleTick, renderLayers) {
        selectedMarkerPos?.let { (lon, lat) ->
            controller.screenOf(lon, lat)?.let { p -> IntOffset(p.x.toInt(), p.y.toInt()) }
        }
    }
    var editing by remember { mutableStateOf(false) }
    // conserve le dernier profil pour l'animation de disparition
    var lastComputed by remember { mutableStateOf<ComputedTrack?>(null) }
    LaunchedEffect(computed) { if (computed != null) lastComputed = computed }
    // Marqueur sélectionné (calques carte) : ombre portée sous les pins de sa trace, et copie du pin au
    // sommet pour qu'il passe devant ceux qui le chevauchent. Position connue dès le tap ; retiré à la
    // désélection. Suit seul la carte.
    val markerLayerId by vm.markerLayerId.collectAsState()
    LaunchedEffect(selectedMarkerPos, markerLayerId, markerPx, renderLayers, poi.selected) {
        // Un point d'interet ouvert prend la main : les deux infobulles ne peuvent pas etre ouvertes en
        // meme temps, et les deux se disputeraient les memes calques d'ombre et de pin de tete.
        val p = poi.selected
        if (p != null) {
            controller.setSelectedMarker(
                p.lon, p.lat, null, poiGroupColor(p.category.group), markerPx, poiIcon(p.category),
            )
            return@LaunchedEffect
        }
        val pos = selectedMarkerPos
        val key = markerLayerId?.let { "ly$it" }
        val color = renderLayers.firstOrNull { it.key == key }?.color
        controller.setSelectedMarker(pos?.first, pos?.second, key, color, markerPx)
    }
    // titre + couleur de la trace active : mis à jour dès le tap (avant le calcul du profil), et conservés
    // pendant l'animation de fermeture (activeLayerId repassé à null).
    var profileTitle by remember { mutableStateOf("") }
    var profileLineColor by remember { mutableStateOf(Color.Unspecified) }
    val profilePrimary = MaterialTheme.colorScheme.primary
    LaunchedEffect(activeLayerId) {
        vm.activeLayer()?.let { ly ->
            profileTitle = ly.name
            profileLineColor = runCatching { Color(ly.color.toColorInt()) }.getOrDefault(profilePrimary)
        }
    }
    // 1er retour Android : ferme le profil s'il est affiché, au lieu du comportement par défaut.
    BackHandler(enabled = computed != null) { vm.closeProfile() }
    // Priorité plus haute (déclaré après = intercepté en premier) : si le menu latéral est ouvert,
    // le retour le referme d'abord, avant tout autre comportement (y compris le retour système par défaut).
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    // Retour = annule le tracé bbox / ferme la config, plutôt que de quitter l'app (priorité la plus haute :
    // ces deux états ne sont jamais actifs simultanément, mais l'ordre reflète "config au-dessus du tracé").
    BackHandler(enabled = offlineDrawingActive) { cancelOfflineDrawing() }
    BackHandler(enabled = offlineConfigBbox != null) { closeOfflineFlow() }
    // Géocodage, du plus général au plus prioritaire (déclaré après = intercepté en premier) : le retour
    // ferme d'abord le lieu affiché, puis la barre de recherche, puis sort du choix d'un point.
    // Le retour système replie d'abord la bande, puis la ferme : deux appuis pour perdre un trajet saisi,
    // et non un seul. Placé avant les gestes du géocodage, plus anodins.
    BackHandler(enabled = planner.open && !planner.collapsed) { planner.collapse(true) }
    BackHandler(enabled = planner.open && planner.collapsed) { planner.close() }
    BackHandler(enabled = geo.place != null) { geo.clear() }
    BackHandler(enabled = geo.searchOpen) { geo.closeSearch() }
    // Mesure sur trace, du plus général au plus prioritaire : le retour ferme d'abord le résultat affiché,
    // et sort en priorité du choix des points, qui est le mode de saisie en cours.
    BackHandler(enabled = measure.mid != null) { measure.clear() }
    BackHandler(enabled = measure.picking) { measure.closeBand() }
    // Point désigné par un appui long, même gradation : le retour ferme d'abord son infobulle, et sort en
    // priorité du choix d'un point de référence, qui est le mode de saisie en cours.
    BackHandler(enabled = mapPoint.point != null) { mapPoint.clear() }
    BackHandler(enabled = mapPoint.pickingPoint) { mapPoint.cancelPickingPoint() }
    // Popup de progression ouverte : Retour la réduit (si en cours) ou la ferme (fin/erreur).
    BackHandler(enabled = offlineDownload?.minimized == false) {
        val dl = offlineDownload
        if (dl?.phase == OfflinePhase.RUNNING) vm.setOfflineDownloadMinimized(true) else vm.dismissOfflineDownload()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = mode != "burger" && drawerState.isOpen,   // swipe-fermeture uniquement quand ouvert
        drawerContent = {
            // Fond de la liste : la surface la plus claire du theme. C'est l'en-tete qui porte une teinte
            // (cf. LegendContent), pas la liste - l'inverse noierait les lignes dans un aplat.
            ModalDrawerSheet(Modifier.fillMaxWidth(), drawerContainerColor = MaterialTheme.colorScheme.surface) {
                LegendContent(
                    folders = folders, layers = layers, settings = settings, vm = vm,
                    open = drawerState.isOpen,
                    onSettings = { scope.launch { drawerState.snapTo(DrawerValue.Closed) }; onSettings() },
                    onClose = { scope.launch { drawerState.close() } },
                    onImport = { folderPicker = true },
                    showOfflineButton = offlineButtonVisible,
                    onDownloadOffline = {
                        scope.launch { drawerState.close() }
                        offlineBboxPoints = emptyList()
                        offlineExtentChoice = true
                    },
                    onZoom = { kind, id ->
                        scope.launch { drawerState.close() }
                        when (kind) {
                            "layer" -> layers.firstOrNull { it.id == id }?.let { controller.fitTo(it.west, it.south, it.east, it.north) }
                            "folder" -> folderBbox(id, folders, layers)?.let { controller.fitTo(it[0], it[1], it[2], it[3]) }
                        }
                    },
                )
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                MapLibreView(
                    modifier = Modifier.fillMaxSize(), controller = controller,
                    styleJson = style?.styleJson, styleUrl = style?.styleUrl,
                    onReady = { styleTick++ },
                )
                // bande de barre de statut : couleur du thème, ou transparente (carte dessous)
                val transparent = settings?.statusBarTransparent ?: false
                Box(Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(if (transparent) Color.Transparent else MaterialTheme.colorScheme.background))
                // Colonne des boutons du coin haut-gauche : la barre habituelle, puis le bouton de recherche
                // de lieu juste dessous, à l'écart vertical qui sépare déjà le burger du bouton GPS.
                //
                // Les icones sont toutes AU TRAIT. Ce n'est pas une preference de style : le menu, la
                // loupe et la regle le sont deja dans le jeu plein de Material - ce sont des dessins
                // lineaires par nature -, tandis que l'epingle, le marteau et le panneau de direction y
                // sont des aplats. Melangees, trois taches pleines cotoyaient quatre traits dans la meme
                // colonne. Leurs variantes "Outlined" remettent tout le monde au meme poids.
                Column(
                    Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
                        .onGloballyPositioned { topControlsHeightPx = it.size.height },
                    verticalArrangement = Arrangement.spacedBy(MapControlSpacing),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MapControlSpacing)) {
                        if (mode != "swipe") {
                            IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = controlBg) {
                                Icon(Icons.Filled.Menu, stringResource(R.string.action_menu), tint = chromeFg)
                            }
                        }
                        if (settings?.showGpsButton == true) {
                            IconButton(
                                onClick = { onGpsButtonTap() },
                                // Le fond ne change pas avec l'etat : allume, c'est le DESSIN qui passe au
                                // bleu, comme le bouton de retouche. Le bouton portait auparavant un aplat
                                // bleu plein, seul de son espece dans la colonne - il se lisait comme un
                                // objet d'une autre famille, la ou tous les autres gardent le fond commun
                                // des ornements de carte.
                                modifier = controlBg,
                            ) {
                                val gpsTint = if (gpsActive) MapChromeActive else chromeFg
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.Place, stringResource(R.string.content_desc_gps_position),
                                        modifier = Modifier.size(GpsIconSize), tint = gpsTint)
                                    Text(stringResource(R.string.gps_label), fontSize = GpsLabelSp.sp,
                                        lineHeight = GpsLabelSp.sp, color = gpsTint)
                                }
                            }
                        }
                        // Popup de progression réduite : bouton orange à droite de l'emplacement du bouton
                        // GPS, dans la même barre (donc même espacement latéral de 4.dp).
                        offlineDownload?.takeIf { it.minimized }?.let { dl ->
                            OfflineMinimizedButton(state = dl, onClick = { vm.setOfflineDownloadMinimized(false) })
                        }
                    }
                    // La recherche de lieu ouvre sa barre de saisie juste dessous : elle reste donc dans la
                    // colonne du haut, là où la barre a la place de se déplier.
                    if (settings?.geocodingEnabled == true) {
                        IconButton(onClick = { onGeocodeButtonTap() }, modifier = controlBg) {
                            // Loupe posée sur un globe, et non la cible de visée d'avant : celle-ci disait
                            // "se repérer", quand ce bouton cherche un lieu par son nom. Le globe distingue
                            // au passage cette recherche-là d'une recherche de texte dans l'application.
                            Icon(Icons.Filled.Search, stringResource(R.string.content_desc_geocode_search), tint = chromeFg)
                        }
                    }
                    // Sous la recherche, et à sa place quand elle est masquée : la colonne se resserre
                    // d'elle-même, aucun des deux boutons ne réserve son rang.
                    // Masqué pendant le choix des points, que sa bande porte déjà entièrement.
                    if (settings?.trackMeasureEnabled == true && !measure.picking) {
                        IconButton(onClick = {
                            // Le bas de l'écran revient à la bande de consigne : le profil se ferme, le
                            // planificateur se replie dans son coin (son trajet, lui, est conservé).
                            vm.closeProfile()
                            if (planner.open) planner.collapse(true)
                            measure.open()
                        }, modifier = controlBg) {
                            Icon(Icons.Filled.Straighten, stringResource(R.string.measure_title), tint = chromeFg)
                        }
                    }
                    // Retouche des traces : un bouton, et non une barre permanente. Ouvrir le mode est un
                    // geste conscient - il detourne les taps de la carte, qui n'ouvrent plus de profil tant
                    // qu'un outil attend son point. Dans la colonne de gauche, sous le burger, avec les
                    // deux autres fonctions qui s'ouvrent en mode.
                    if (settings?.trackEditEnabled == true) {
                        IconButton(onClick = { edit.toggleBar() }, modifier = controlBg) {
                            Icon(
                                // Un crayon plutot qu'un marteau : deux traits contre une silhouette
                                // pleine d'outils croises, illisible a 22 dp au-dessus d'une carte.
                                Icons.Outlined.Edit, stringResource(R.string.edit_toolbar),
                                tint = if (edit.open) MapChromeActive else chromeFg,
                            )
                        }
                    }
                    if (geo.searchOpen) {
                        GeocodeSearchBar(
                            query = geo.query, results = geo.results, searching = geo.searching,
                            onQueryChange = { geo.query = it },
                            onPick = { place ->
                                geo.select(place)
                                controller.centerOnAtLeast(place.lat, place.lon, GeocodeMinZoom)
                            },
                            onClose = { geo.closeSearch() },
                        )
                    }
                }
                // réinitialisation de l'orientation (visible seulement si la carte est tournée) + Basemap Control
                Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(MapControlSpacing)) {
                    if (kotlin.math.abs(bearing) > 0.5) {
                        IconButton(onClick = { controller.resetNorth() }, modifier = controlBg) {
                            Icon(Icons.Filled.ArrowUpward, stringResource(R.string.action_reset_north),
                                modifier = Modifier.graphicsLayer { rotationZ = -bearing.toFloat() }, tint = chromeFg)
                        }
                    }
                    // Légende du fond affiché, s'il en fournit une (cf. ProviderEntity.legendAsset) : à
                    // gauche du gestionnaire de couches, l'image se déployant vers la gauche depuis ici.
                    // Le bouton reporte sa position : la légende s'y adosse sans supposer de largeur de
                    // barre, celle-ci variant selon les boutons affichés (nord, gestionnaire de couches).
                    if (activeLegends.isNotEmpty()) {
                        IconButton(
                            onClick = { legendOpen = !legendOpen },
                            modifier = Modifier.onGloballyPositioned {
                                val p = it.positionInRoot()          // coin haut-gauche du bouton
                                legendAnchor = IntOffset(p.x.roundToInt(), p.y.roundToInt())
                            },
                        ) {
                            Icon(Icons.Outlined.Info, stringResource(R.string.content_desc_basemap_legend), tint = chromeFg)
                        }
                    }
                    if (settings?.showBasemapControlButton == true) {
                        IconButton(onClick = { basemapControlOpen = true }, modifier = controlBg) {
                            // Outlined plutôt que Filled : la version pleine a sa couche du haut remplie
                            // en noir, ce qui contraste avec les autres boutons de la carte (tous en contour).
                            Icon(Icons.Outlined.Layers, stringResource(R.string.content_desc_basemap_control), tint = chromeFg)
                        }
                    }
                }
                // échelle graphique, dans le coin bas-gauche (uniquement quand ni le profil ni la bande du
                // planificateur déployée n'occupent le bas de l'écran) : décalée au-dessus de la barre de
                // consigne du moment tant qu'elle est affichée, pour ne pas être recouverte. Réduit, le
                // planificateur la laisse revenir : il ne prend plus qu'un bouton de coin, et la carte
                // redevient l'objet du regard.
                //
                // Elle se décale, là où les boutons du coin bas-droit restent fixes : une échelle est une
                // lecture, elle doit rester lisible ; un bouton est une cible, il doit rester où la main
                // l'a laissé.
                //
                // Posée AVANT les infobulles, donc dessous : une infobulle dit ce qu'on vient de demander,
                // l'échelle est là en permanence. C'est à elle de passer derrière.
                val plannerExpanded = planner.open && !planner.collapsed
                if (activeLayerId == null && !plannerExpanded && settings?.showScale != false) {
                    // Ces barres portent déjà leur propre marge de barre de navigation, d'où le repli sur
                    // navigationBarsPadding quand il n'y en a aucune.
                    val bottomBarPx = when {
                        offlineDrawingActive -> offlineBarHeightPx
                        measure.picking -> measureBarHeightPx
                        mapPoint.pickingPoint -> pointBarHeightPx
                        else -> 0
                    }
                    val base = Modifier.align(Alignment.BottomStart)
                    ScaleBar(controller, idleTick, maxWidthPx = constraints.maxWidth * 0.40f,
                        bg = chromeBg, fg = chromeFg,
                        modifier = (if (bottomBarPx > 0) base.padding(bottom = with(density) { bottomBarPx.toDp() } + 8.dp)
                            else base.padding(bottom = 8.dp).navigationBarsPadding()).padding(start = 16.dp))
                }
                BasemapLegend(
                    legends = activeLegends,
                    visible = legendOpen && activeLegends.isNotEmpty(),
                    anchor = legendAnchor,
                    onDismiss = { legendOpen = false },
                )
                // Infobulle. Affichée dès le tap (spinner tant que les propriétés chargent), placée selon le
                // réglage Carte / Infobulles. Le placement est calculé dans la phase de layout, une fois la
                // taille réelle mesurée : la bulle apparaît donc directement au bon endroit, sans le saut que
                // provoquait un premier passage à taille nulle.
                //
                // Le décalage de carte qu'impose ce placement attend la même mesure : il part à l'instant où
                // la bulle remplace le spinner, les deux mouvements se lisant alors comme un seul. Décaler
                // dès le tap a été essayé et retiré : la hauteur de la bulle étant inconnue tant que ses
                // propriétés chargent, il fallait réserver l'encombrement maximal possible, et la carte
                // bougeait le plus souvent bien plus que nécessaire - parfois là où la bulle réelle, plus
                // courte, n'exigeait aucun mouvement.
                val off = bubbleOffset
                if (off != null && selectedMarkerId != null && !editing) {
                    val maxH = constraints.maxHeight
                    val topInset = WindowInsets.statusBars.getTop(density)
                    val margin = with(density) { 8.dp.roundToPx() }
                    val gap = with(density) { 10.dp.roundToPx() }
                    val markerPxI = markerPx.toInt()
                    // Hauteur max de l'infobulle : elle tient sous la barre de statut (avec marges) sans
                    // jamais couvrir plus de 60 % de l'écran, pour laisser voir la carte autour.
                    val maxBubbleHeightDp = with(density) {
                        minOf(maxH - topInset - 2 * margin, (maxH * BubbleMaxHeightRatio).toInt()).toDp()
                    }
                    val bubblePos = BubblePosition.of(settings?.bubblePosition)
                    // Dernier placement calculé au layout : sert au recentrage de carte (hors AUTO).
                    // Publié seulement une fois les propriétés arrivées : mesurée à la taille du spinner, la
                    // bulle tient presque toujours à l'écran et le recentrage (à usage unique) aurait été
                    // consommé pour rien, laissant la vraie bulle simplement bornée dans l'écran.
                    var placement by remember(selectedMarkerId) { mutableStateOf<BubblePlacement?>(null) }
                    val contentReady = selectedFeature != null
                    Layout(
                        content = {
                            if (selectedFeature != null) {
                                InfoBubble(feature = selectedFeature, schema = markerLayerData?.schema ?: emptyList(),
                                    fontSp = settings?.bubbleFont ?: 14, bold = settings?.bubbleBold ?: false,
                                    titleFontSp = settings?.bubbleTitleFont ?: 14, titleBold = settings?.bubbleTitleBold ?: true,
                                    maxHeightDp = maxBubbleHeightDp,
                                    backgroundAlpha = (settings?.bubbleOpacityPct ?: 100) / 100f,
                                    onEdit = { editing = true }, onClose = { vm.closeMarker() })
                            } else {
                                InfoBubbleLoading()
                            }
                        },
                    ) { measurables, cs ->
                        val p = measurables.first().measure(cs.copy(minWidth = 0, minHeight = 0))
                        val pl = computeBubblePlacement(
                            pos = bubblePos, markerX = off.x, markerY = off.y,
                            bubbleW = p.width, bubbleH = p.height,
                            viewW = cs.maxWidth, viewH = cs.maxHeight,
                            topInset = topInset, margin = margin, gap = gap, markerHeight = markerPxI,
                        )
                        if (contentReady && placement != pl) placement = pl
                        layout(cs.maxWidth, cs.maxHeight) { p.place(pl.x, pl.y) }
                    }
                    // Recentrage de la carte quand le placement demandé ne tient pas (jamais en AUTO). Le
                    // placement n'étant publié qu'à la bulle réelle, le mouvement part quand le spinner
                    // s'efface : la bulle est déjà posée à sa place définitive à l'écran, et c'est la carte
                    // qui vient se ranger dessous.
                    //
                    // Une seule fois par marqueur : la carte bouge -> le marqueur bouge -> nouveau placement,
                    // qui tient cette fois ; sans ce garde-fou, les deux se relanceraient mutuellement, et un
                    // déplacement fait à la main serait défait.
                    var pannedFor by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(selectedMarkerId, placement) {
                        val pl = placement ?: return@LaunchedEffect
                        if (bubblePos == BubblePosition.AUTO || pannedFor == selectedMarkerId) return@LaunchedEffect
                        if (pl.panX != 0 || pl.panY != 0) controller.panByScreen(pl.panX.toFloat(), pl.panY.toFloat())
                        pannedFor = selectedMarkerId
                    }
                }
                // Infobulle du lieu trouvé : posée dans celui des quatre coins du lieu qui déplace le moins
                // la carte (cf. computeGeocodePlacement), et non à la position réglée pour les marqueurs.
                val gPlace = geo.place
                if (gPlace != null) {
                    // Recalculee a chaque image du deplacement (moveTick) et non a la seule immobilisation :
                    // l'infobulle reste ainsi collee a son epingle pendant tout le geste, au lieu de rester
                    // sur place puis de la rejoindre d'un saut. Seul son coin change en cours de route.
                    val gOff = remember(gPlace, idleTick, moveTick) {
                        controller.screenOf(gPlace.lon, gPlace.lat)?.let { p -> IntOffset(p.x.toInt(), p.y.toInt()) }
                    }
                    if (gOff != null) {
                        val topInset = WindowInsets.statusBars.getTop(density)
                        val margin = with(density) { 8.dp.roundToPx() }
                        val gap = with(density) { 10.dp.roundToPx() }
                        var placement by remember(gPlace) { mutableStateOf<BubblePlacement?>(null) }
                        Layout(
                            content = {
                                GeocodeBubble(
                                    lines = gPlace.lines,
                                    onClose = { geo.clear() },
                                    fontSp = settings?.bubbleFont ?: 14,
                                    backgroundAlpha = (settings?.bubbleOpacityPct ?: 100) / 100f,
                                )
                            },
                        ) { measurables, cs ->
                            val p = measurables.first().measure(cs.copy(minWidth = 0, minHeight = 0))
                            val pl = computeGeocodePlacement(
                                markerX = gOff.x, markerY = gOff.y,
                                bubbleW = p.width, bubbleH = p.height,
                                viewW = cs.maxWidth, viewH = cs.maxHeight,
                                topInset = topInset, margin = margin, gap = gap, markerHeight = markerPx.toInt(),
                            )
                            if (placement != pl) placement = pl
                            layout(cs.maxWidth, cs.maxHeight) { p.place(pl.x, pl.y) }
                        }
                        // Décalage de carte pour que l'épingle ET la bulle tiennent à l'écran ; le lieu
                        // hors de la vue courante ramène l'ensemble au centre (cf. computeGeocodePlacement).
                        //
                        // Attendu que la caméra soit arrêtée : retenir un lieu la lance vers lui
                        // (centerOnAtLeast), et tant qu'elle vole, la projection lue est celle d'AVANT le
                        // vol - un décalage calculé dessus s'ajouterait au mouvement en cours au lieu de le
                        // corriger, et posait le lieu hors de la carte. Son immobilisation incrémente
                        // idleTick, d'où la comparaison au tick du moment où le lieu a été retenu.
                        //
                        // Une seule fois par lieu, comme pour un marqueur : sans ce garde-fou, un
                        // déplacement fait à la main serait aussitôt défait.
                        val pickedAtTick = remember(gPlace) { idleTick }
                        var pannedFor by remember { mutableStateOf<GeocodePlace?>(null) }
                        LaunchedEffect(gPlace, placement, idleTick) {
                            val pl = placement ?: return@LaunchedEffect
                            if (idleTick == pickedAtTick || pannedFor == gPlace) return@LaunchedEffect
                            if (pl.panX != 0 || pl.panY != 0) controller.panByScreen(pl.panX.toFloat(), pl.panY.toFloat())
                            pannedFor = gPlace
                        }
                    }
                }
                // Infobulle du point désigné par un appui long : même placement que celle d'un lieu trouvé,
                // dans celui des quatre coins du point qui déplace le moins la carte.
                val mPoint = mapPoint.point
                if (mPoint != null && mapPoint.bubbleVisible) {
                    // Suit l'epingle image par image, comme celle d'un lieu trouve (cf. gOff).
                    val mOff = remember(mPoint, idleTick, moveTick) {
                        controller.screenOf(mPoint.first, mPoint.second)
                            ?.let { p -> IntOffset(p.x.toInt(), p.y.toInt()) }
                    }
                    if (mOff != null) {
                        val topInset = WindowInsets.statusBars.getTop(density)
                        val margin = with(density) { 8.dp.roundToPx() }
                        val gap = with(density) { 10.dp.roundToPx() }
                        // Dernier placement calculé au layout : sert au recentrage de carte. Publié une
                        // fois l'adresse arrivée : mesurée au spinner, la bulle est plus courte que la
                        // bulle réelle, et le recentrage - à usage unique - serait consommé sur une
                        // hauteur qui n'est pas la sienne.
                        var placement by remember(mPoint) { mutableStateOf<BubblePlacement?>(null) }
                        val addressReady = mapPoint.address != AddressState.Loading
                        Layout(
                            content = {
                                MapPointBubble(
                                    address = mapPoint.address,
                                    profileLabel = routingProfileLabel(routingProfile),
                                    // Le capteur éteint, la ligne disparaît : une mesure depuis une position
                                    // inconnue ne partirait jamais (cf. MapPointBubble). Une mesure déjà
                                    // demandée la retient, elle : son résultat vaut pour l'endroit d'où elle
                                    // est partie, et son tracé est sur la carte - rien ne l'expliquerait plus.
                                    showPositionRow = gpsActive || mapPoint.positionMeasure != null,
                                    positionMeasure = mapPoint.positionMeasure,
                                    pointMeasure = mapPoint.pointMeasure,
                                    imperial = imperialUnits,
                                    onDistanceFromPosition = { onDistanceFromPositionTap() },
                                    onDistanceFromPoint = { onDistanceFromPointTap() },
                                    onClose = { mapPoint.clear() },
                                    fontSp = settings?.bubbleFont ?: 14,
                                    backgroundAlpha = (settings?.bubbleOpacityPct ?: 100) / 100f,
                                )
                            },
                        ) { measurables, cs ->
                            val p = measurables.first().measure(cs.copy(minWidth = 0, minHeight = 0))
                            val pl = computeGeocodePlacement(
                                markerX = mOff.x, markerY = mOff.y,
                                bubbleW = p.width, bubbleH = p.height,
                                viewW = cs.maxWidth, viewH = cs.maxHeight,
                                topInset = topInset, margin = margin, gap = gap, markerHeight = markerPx.toInt(),
                            )
                            if (addressReady && placement != pl) placement = pl
                            layout(cs.maxWidth, cs.maxHeight) { p.place(pl.x, pl.y) }
                        }
                        // Décalage de carte seulement si aucun des quatre coins ne tenait : le point désigné
                        // est en plein écran dans le cas ordinaire, et rien ne bouge. L'épingle, elle, ne
                        // bouge jamais d'un pouce : elle reste sur le point, c'est la carte qui glisse.
                        //
                        // Une seule fois par point, comme pour un marqueur : la carte bouge -> le point
                        // bouge à l'écran -> nouveau placement, qui tient cette fois. Sans ce garde-fou, un
                        // déplacement fait à la main serait aussitôt défait.
                        var pannedFor by remember { mutableStateOf<Pair<Double, Double>?>(null) }
                        LaunchedEffect(mPoint, placement) {
                            val pl = placement ?: return@LaunchedEffect
                            if (pannedFor == mPoint) return@LaunchedEffect
                            if (pl.panX != 0 || pl.panY != 0) controller.panByScreen(pl.panX.toFloat(), pl.panY.toFloat())
                            pannedFor = mPoint
                        }
                    }
                }
                // Position hors du centre de la carte : c'est ce qui fait apparaître le bouton de
                // recentrage, et rien d'autre.
                //
                // Mesuré à l'écran plutôt que sur les coordonnées : la question est "la position est-elle
                // au milieu de ce que je vois", et sa réponse doit valoir à tout zoom. Suivie à chaque
                // image du déplacement (moveTick), comme les infobulles.
                val centerTolPx = with(density) { 16.dp.toPx() }
                val viewCenterX = constraints.maxWidth / 2f
                val viewCenterY = constraints.maxHeight / 2f
                val positionOffCenter = remember(lastUserLocation, moveTick, idleTick, viewCenterX, viewCenterY) {
                    lastUserLocation?.let { (la, lo) -> controller.screenOf(lo, la) }?.let { p ->
                        hypot(p.x - viewCenterX, p.y - viewCenterY) > centerTolPx
                    } ?: false
                }
                // Commandes du coin bas-droit, à portée du pouce : le recentrage sur la position, puis le
                // planificateur au plus près du coin - c'est celui qui reste, l'autre n'apparaissant que le
                // capteur allumé, et un bouton qui change de place au gré du GPS se chercherait à chaque fois.
                //
                // Position FIXE, à la seule barre de navigation près : elles ne se rangent pas au-dessus de
                // ce qui occupe le bas (profil, bande du planificateur), contrairement à l'échelle. Un
                // bouton qui saute de trois cents pixels à l'ouverture d'un panneau se cherche à chaque
                // fois ; il vaut mieux qu'il attende sous lui, là où la main l'a laissé.
                //
                // Posées AVANT tout ce qui occupe le bas, donc DESSOUS : la bande du planificateur, les
                // consignes de saisie et le profil les recouvrent au lieu de les pousser. Elles restent
                // au-dessus des infobulles, elles, qui ne doivent pas rendre un bouton intouchable.
                // Meme ecart sous le dernier bouton qu'entre les deux : la colonne se lit alors d'un
                // bloc, sans que le bas de l'ecran serre plus que ses propres intervalles. La barre de
                // navigation l'emporte si elle est plus haute - un bouton ne se glisse pas dessous.
                val navBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
                Column(
                    Modifier.align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = maxOf(MapControlSpacing, navBottomDp)),
                    verticalArrangement = Arrangement.spacedBy(MapControlSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Affiche seulement quand il a quelque chose a faire : la position centree, le bouton
                    // disparait plutot que de proposer un geste sans effet. Il s'efface donc de lui-meme au
                    // bout du recentrage qu'on vient de lui demander.
                    //
                    // Le planificateur, lui, ne bouge pas pour autant : la colonne est alignee en bas, et
                    // c'est ce bouton-ci qui s'ajoute ou se retire par le haut.
                    if (gpsActive && positionOffCenter) {
                        IconButton(onClick = { recenterOnGps() }, modifier = controlBg) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.MyLocation, stringResource(R.string.action_center_on_location), tint = chromeFg)
                                // Disque central au bleu du point de position : le bouton n'existant que
                                // hors centre, c'est sa seule teinte.
                                Canvas(Modifier.size(MyLocationDotSize)) { drawCircle(color = RecenterDotColor) }
                            }
                        }
                    }
                    // Cloche de l'alerte d'éloignement, juste au-dessus du planificateur : la fonction sert
                    // PENDANT la sortie, et le pouce la trouve au même endroit que le reste.
                    //
                    // Elle se lit à la couleur de son dessin, comme les autres commandes de la carte : gris
                    // tant qu'aucune trace n'est suivie, bleu dès qu'on en suit une, rouge quand on s'en est
                    // écarté - la bannière du bas dit alors de combien, mais la cloche l'annonce déjà à qui
                    // regarde la carte.
                    if (alertEnabled) {
                        IconButton(onClick = { onAlertButtonTap() }, modifier = controlBg) {
                            Icon(
                                Icons.Outlined.NotificationsNone,
                                stringResource(R.string.content_desc_off_track_alert),
                                tint = when {
                                    alert.alerting -> OffTrackAlertColor
                                    alert.followed != null -> MapChromeActive
                                    else -> chromeFg
                                },
                            )
                        }
                    }
                    // Points d'interet : la pastille s'allume quand la couche est affichee, comme le
                    // suivi de position et l'alerte d'eloignement le font pour la leur.
                    //
                    // Un marque-page, et non l'epingle : celle-ci est deja le dessin des marqueurs que ce
                    // bouton POSE sur la carte, et celui du bouton GPS juste a cote - trois epingles pour
                    // trois choses differentes. Le marque-page dit ce qu'on cherche ici, des endroits
                    // qu'on retient le long du parcours.
                    if (settings?.poiEnabled == true) {
                        IconButton(onClick = { poi.toggle() }, modifier = controlBg) {
                            Icon(
                                Icons.Outlined.BookmarkBorder, stringResource(R.string.poi_layer_title),
                                tint = if (poi.visible) MapChromeActive else chromeFg,
                            )
                        }
                    }
                    // Masqué tant que sa bande est ouverte, qu'il ne servirait qu'à rouvrir.
                    if (settings?.routePlannerEnabled == true && !planner.open) {
                        IconButton(onClick = {
                            if (ServiceUrl.needsInternet(routingUrl) && !NetworkStatus.hasInternet(ctx)) {
                                showNoConnectionDialog = true
                            } else {
                                vm.closeProfile()          // les deux occupent le bas de l'écran
                                planner.openPlanner()
                                planner.chooseProfile(RoutingProfile.of(settings?.routingProfile))
                            }
                        }, modifier = controlBg) {
                            Icon(Icons.Outlined.Directions, stringResource(R.string.planner_title), tint = chromeFg)
                        }
                    }
                }

                /*
                 * Barre de retouche : verticale, du MEME cote que le bouton qui l'ouvre - la colonne de
                 * gauche -, sans quoi le regard traverserait l'ecran entre le geste et son resultat.
                 *
                 * Centree verticalement, mais jamais sous les boutons du haut : sur un petit ecran, ou avec
                 * la recherche de lieu et la mesure affichees, le milieu de l'ecran tombe dans la colonne
                 * des commandes. Elle descend alors juste au-dessous, gardant l'espacement des boutons.
                 */
                if (edit.open) {
                    var toolbarHeightPx by remember { mutableIntStateOf(0) }
                    val gapPx = with(density) { MapControlSpacing.roundToPx() }
                    val topPx = maxOf(
                        (constraints.maxHeight - toolbarHeightPx) / 2,
                        topControlsHeightPx + gapPx,
                    )
                    TrackEditToolbar(
                        state = edit, canUndo = canUndo, chromeBg = chromeBg, chromeFg = chromeFg,
                        onUndo = { vm.undoLastEdit() },
                        modifier = Modifier.align(Alignment.TopStart)
                            .offset { IntOffset(0, topPx) }
                            .padding(start = 8.dp)
                            .onGloballyPositioned { toolbarHeightPx = it.size.height },
                    )
                }
                /*
                 * Hauteur maximale de la bande : 60 % de l'écran, MAIS jamais plus que ce qui reste
                 * réellement visible entre la barre de statut et le bas occupé (clavier, ou barre de
                 * navigation à défaut).
                 *
                 * La seconde borne n'est pas une précaution : sans elle, la marge basse qui dégage le
                 * clavier s'ajoutait à une hauteur déjà calculée sur l'écran ENTIER. Le total dépassait
                 * l'écran, et la bande, alignée en bas d'une boîte trop petite pour elle, était projetée
                 * vers le haut - son en-tête sortant de l'écran et passant sous la barre de statut.
                 */
                val imeBottomPx = WindowInsets.ime.getBottom(density)
                val navInsetPx = WindowInsets.navigationBars.getBottom(density)
                val statusTopPx = WindowInsets.statusBars.getTop(density)
                val plannerMaxHeight = with(density) {
                    val visible = constraints.maxHeight - statusTopPx - maxOf(imeBottomPx, navInsetPx)
                    minOf((constraints.maxHeight * PlannerMaxHeightRatio).toInt(), visible.coerceAtLeast(0)).toDp()
                }
                // Bande du planificateur. Réduite, elle se range au coin bas-gauche ; déployée, elle
                // occupe toute la largeur. Deux alignements pour un même composable, d'où le choix ici.
                if (planner.open) {
                    RoutePlannerBand(
                        state = planner,
                        imperial = imperialUnits,
                        settings = settings,
                        lastLabelInsetPx = 0f,
                        maxHeight = plannerMaxHeight,
                        onPickCurrentPosition = { step -> planner.choose(step, StepTarget.CurrentPosition) },
                        gpsActive = gpsActive,
                        geocoding = GeocodingParams(geocodingBase,
                            ctx.resources.configuration.locales[0].language, GeocodeResultLimit),
                        onImport = { importDialog = true },
                        onDownload = {
                            val name = defaultRouteName(planner.targets, currentPositionLabel)
                            gpxSaver.launch(GpxWriter.fileName(name))
                        },
                        modifier = Modifier
                            .align(if (planner.collapsed) Alignment.BottomStart else Alignment.BottomCenter)
                            // Le clavier pousse la bande au-dessus de lui : sans cela il recouvrirait les
                            // propositions du champ qui vient de prendre le focus, c'est-a-dire
                            // precisement ce qu'on cherche a lire en tapant.
                            // L'UNION des deux marges, et non les deux appliquees l'une apres l'autre :
                            // elle en prend le maximum, la sur ou la barre de navigation s'additionnerait
                            // a un clavier qui la recouvre deja.
                            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                            .onGloballyPositioned { plannerBandHeightPx = it.size.height },
                    )
                }
                // Consigne de la mesure sur trace : le point à poser, et la croix qui referme la fonction.
                // Elle s'efface d'elle-même une fois le second point posé (cf. TrackMeasureState.chooseEnd).
                if (measure.picking) {
                    val started = measure.start
                    MapPromptBar(
                        text = if (started == null) stringResource(R.string.measure_pick_start)
                            else stringResource(R.string.measure_pick_end, started.layerName),
                        onClose = { measure.closeBand() },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                            .onGloballyPositioned { measureBarHeightPx = it.size.height },
                    )
                }
                // Consigne du choix d'un point de référence : même barre que la mesure sur trace, l'une et
                // l'autre ne demandant qu'un tap sur la carte.
                if (mapPoint.pickingPoint) {
                    MapPromptBar(
                        text = stringResource(R.string.geocode_pick_point_prompt),
                        onClose = { mapPoint.cancelPickingPoint() },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                            .onGloballyPositioned { pointBarHeightPx = it.size.height },
                    )
                }
                // ---------- retouche : marqueur de coupe, consignes et confirmations ----------
                // Le marqueur suit la carte au deplacement et au zoom (moveTick), comme les infobulles :
                // il designe un point du terrain, pas un point de l'ecran.
                edit.cut?.let { target ->
                    val p = target.hit.point
                    // La bulle SUIT la carte a chaque image : c'est le point du terrain qu'elle designe.
                    val off = remember(moveTick, target) { controller.screenOf(p.lon, p.lat) }
                    /*
                     * Le COTE, lui, ne se recalcule pas a chaque image : recalcule en continu, il change
                     * en cours de geste et la bulle tremble. Il est donc fige, et revu seulement :
                     *
                     * - a la pose du marqueur ;
                     * - a la FIN d'un deplacement ou d'un dezoom (idleTick), une seule fois.
                     *
                     * Jamais a un zoom avant : ce qui etait degage a l'echelle du dessus l'est encore en
                     * s'approchant, la trace ne faisant que s'ecarter d'elle-meme.
                     */
                    val cutPlacedTick = remember(target) { idleTick }
                    var cutSide by remember(target) { mutableStateOf<CutBubblePlacement.Side?>(null) }
                    var cutZoom by remember(target) { mutableStateOf(Double.MAX_VALUE) }
                    val bubbleWpx = with(density) { CutBubbleWidth.roundToPx() }
                    val bubbleHpx = with(density) { CutBubbleHeight.roundToPx() }
                    val tailPx = with(density) { CutTailHeight.roundToPx() }
                    val marginPx = with(density) { CutBubbleMargin.roundToPx() }
                    val topInsetPx = WindowInsets.statusBars.getTop(density)

                    LaunchedEffect(target, idleTick) {
                        val zoom = controller.cameraState()?.third ?: return@LaunchedEffect
                        if (cutSide != null && zoom > cutZoom) return@LaunchedEffect
                        val here = controller.screenOf(p.lon, p.lat) ?: return@LaunchedEffect
                        // Ce que la bulle pourrait recouvrir : les portions de trace assez proches pour
                        // tomber sous elle, converties en metres a l'echelle du moment.
                        val reachPx = kotlin.math.hypot(
                            (bubbleWpx + tailPx).toDouble(), (bubbleHpx + tailPx).toDouble(),
                        ) + marginPx
                        val radiusM = reachPx * controller.metersPerPixel(p.lat)
                        val runs = withContext(Dispatchers.Default) {
                            TrackEdit.nearbyRuns(cutGeometry, p.lon, p.lat, radiusM)
                        }
                        val screenRuns = runs.map { run ->
                            run.mapNotNull { q -> controller.screenOf(q.lon, q.lat)?.let { it.x to it.y } }
                        }.filter { it.size >= 2 }
                        val pl = CutBubblePlacement.choose(
                            pointX = here.x.toInt(), pointY = here.y.toInt(), track = screenRuns,
                            bubbleW = bubbleWpx, bubbleH = bubbleHpx, tail = tailPx,
                            viewW = constraints.maxWidth, viewH = constraints.maxHeight,
                            topInset = topInsetPx, margin = marginPx,
                        )
                        cutSide = pl.side
                        cutZoom = zoom
                        // Le decalage de carte n'a lieu qu'a la POSE du marqueur : le rejouer a chaque fin
                        // de deplacement ramenerait la carte de force des qu'on la pousse pour regarder
                        // ailleurs.
                        if (idleTick == cutPlacedTick && (pl.panX != 0 || pl.panY != 0)) {
                            controller.panByScreen(pl.panX.toFloat(), pl.panY.toFloat())
                        }
                    }
                    val side = cutSide
                    if (off != null && side != null) {
                        val totalW = if (side == CutBubblePlacement.Side.LEFT || side == CutBubblePlacement.Side.RIGHT)
                            bubbleWpx + tailPx else bubbleWpx
                        val totalH = if (side == CutBubblePlacement.Side.TOP || side == CutBubblePlacement.Side.BOTTOM)
                            bubbleHpx + tailPx else bubbleHpx
                        val bx = when (side) {
                            CutBubblePlacement.Side.LEFT -> off.x.toInt() - totalW
                            CutBubblePlacement.Side.RIGHT -> off.x.toInt()
                            else -> off.x.toInt() - totalW / 2
                        }
                        val by = when (side) {
                            CutBubblePlacement.Side.TOP -> off.y.toInt() - totalH
                            CutBubblePlacement.Side.BOTTOM -> off.y.toInt()
                            else -> off.y.toInt() - totalH / 2
                        }
                        CutMarkerBubble(
                            side = side, bg = chromeBg, fg = chromeFg,
                            modifier = Modifier.offset { IntOffset(bx, by) },
                        )
                    }
                }
                if (edit.open && edit.tool != EditTool.NONE) {
                    val barModifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(bottom = with(density) { profileBarHeightPx.toDp() })
                    val cutTarget = edit.cut
                    val a = edit.first
                    val b = edit.second
                    when {
                        // Coupe : le marqueur est pose, on demande confirmation. Un nouveau tap ailleurs
                        // le deplace tant qu'on n'a pas confirme.
                        cutTarget != null -> MapActionBar(
                            text = stringResource(R.string.edit_cut_confirm, cutTarget.layerName),
                            onClose = { edit.choose(EditTool.NONE) }, modifier = barModifier,
                        ) {
                            MapBarAction(stringResource(R.string.action_cancel)) { edit.choose(EditTool.NONE) }
                            MapBarAction(stringResource(R.string.action_split_track), primary = true) {
                                val layer = layers.firstOrNull { it.id == cutTarget.layerId }
                                edit.choose(EditTool.NONE)
                                if (layer != null) vm.splitLayerAt(layer, cutTarget.hit) { ok ->
                                    if (!ok) edit.message = cannotSplitMessage
                                }
                            }
                        }
                        // Jonction : les deux segments sont designes, reste a dire comment les relier.
                        a != null && b != null -> MapActionBar(
                            text = stringResource(R.string.edit_join_how, a.layerName, b.layerName),
                            onClose = { edit.choose(EditTool.NONE) }, modifier = barModifier,
                        ) {
                            val apply: (TrailogRepository.JoinMode) -> Unit = { mode ->
                                val la = layers.firstOrNull { it.id == a.layerId }
                                val lb = layers.firstOrNull { it.id == b.layerId }
                                edit.choose(EditTool.NONE)
                                if (la != null && lb != null) {
                                    vm.joinSegments(la, a.segment, lb, b.segment, mode) { applied ->
                                        if (applied != mode) edit.message = routedFellBack
                                    }
                                }
                            }
                            MapBarAction(stringResource(R.string.join_straight)) { apply(TrailogRepository.JoinMode.STRAIGHT) }
                            MapBarAction(stringResource(R.string.join_routed), primary = true) { apply(TrailogRepository.JoinMode.ROUTED) }
                            MapBarAction(stringResource(R.string.join_none)) { apply(TrailogRepository.JoinMode.NONE) }
                        }
                        // Sinon : la consigne de l'outil en cours.
                        else -> MapPromptBar(
                            text = when (edit.tool) {
                                EditTool.REVERSE -> stringResource(R.string.edit_pick_reverse)
                                EditTool.CUT -> stringResource(R.string.edit_pick_cut)
                                EditTool.JOIN -> if (a == null) stringResource(R.string.edit_pick_first)
                                    else stringResource(R.string.edit_pick_second, a.layerName)
                                EditTool.NONE -> ""
                            },
                            onClose = { edit.choose(EditTool.NONE) },
                            modifier = barModifier,
                        )
                    }
                }
                // Infobulle de la mesure : ancrée sur le parcours mesuré au plus près de son milieu, et
                // suivant la carte au pan et au zoom (d'où idleTick, comme l'infobulle d'un lieu). Le zoom
                // posé pour viser le second point laisse souvent le milieu hors de l'écran ; l'ancre glisse
                // alors le long du parcours jusqu'au premier point visible (cf. MeasureAnchor).
                val measurePath = measure.path
                val measureMeters = measure.meters
                if (measurePath.isNotEmpty() && measureMeters != null) {
                    val topInset = WindowInsets.statusBars.getTop(density)
                    val margin = with(density) { 8.dp.roundToPx() }
                    // Emprise où la pointe a le droit de se poser : la carte, moins une marge de confort
                    // (jamais collée au bord) et moins ce qui la recouvre en bas : le profil quand il est
                    // ouvert, la barre de navigation sinon. Rien à réserver pour la hauteur de la bulle :
                    // elle bascule au-dessus ou en dessous de sa pointe selon la place (cf. MeasureBubble).
                    val inset = with(density) { 24.dp.roundToPx() }
                    val bottomCover = if (activeLayerId != null) profileBarHeightPx
                        else WindowInsets.navigationBars.getBottom(density)
                    val left = inset
                    val right = (constraints.maxWidth - inset).coerceAtLeast(left)
                    val top = topInset + inset
                    val bottom = (constraints.maxHeight - bottomCover - inset).coerceAtLeast(top)
                    val tip = remember(measurePath, idleTick, left, right, top, bottom) {
                        var found: IntOffset? = null
                        MeasureAnchor.pick(measurePath.size) { i ->
                            val (lon, lat) = measurePath[i]
                            val p = controller.screenOf(lon, lat) ?: return@pick false
                            val inside = p.x >= left && p.x <= right && p.y >= top && p.y <= bottom
                            if (inside) found = IntOffset(p.x.toInt(), p.y.toInt())
                            inside
                        }
                        // Parcours entièrement hors de l'écran (la carte est partie ailleurs) : la bulle se
                        // range du côté où il se trouve plutôt que de disparaître avec sa croix, seul moyen
                        // à l'écran de refermer la mesure.
                        found ?: measure.mid?.let { (lon, lat) ->
                            controller.screenOf(lon, lat)?.let { p ->
                                IntOffset(p.x.toInt().coerceIn(left, right), p.y.toInt().coerceIn(top, bottom))
                            }
                        }
                    }
                    if (tip != null) {
                        MeasureBubble(
                            text = Format.shortDistance(measureMeters, imperialUnits),
                            tipX = tip.x, tipY = tip.y,
                            topInset = topInset,
                            margin = margin,
                            onClose = { measure.clear() },
                            fontSp = settings?.bubbleFont ?: 14,
                            backgroundAlpha = (settings?.bubbleOpacityPct ?: 100) / 100f,
                        )
                    }
                }
                // tracé de la bounding box hors-ligne (SPEC section 2)
                if (offlineDrawingActive) {
                    BboxDrawingOverlay(
                        pointCount = offlineBboxPoints.size,
                        dark = darkChrome,
                        onCancelPoint = { offlineBboxPoints = offlineBboxPoints.dropLast(1) },
                        onCancelAll = { cancelOfflineDrawing() },
                        onValidate = {
                            val (lon1, lat1) = offlineBboxPoints[0]
                            val (lon2, lat2) = offlineBboxPoints[1]
                            offlineConfigBbox = Bbox.of(lon1, lat1, lon2, lat2)
                            offlineDrawingActive = false
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .onGloballyPositioned { offlineBarHeightPx = it.size.height },
                    )
                }
                // Décalage du dernier label de l'axe X pour dégager l'angle arrondi bas-droit de l'écran. On
                // calcule l'intrusion réelle de l'arc À LA HAUTEUR du label (et non le rayon plein, qui n'est
                // atteint que tout en bas) : le label est remonté par la barre de navigation, l'angle y mord
                // donc bien moins.
                //   - r = rayon de l'angle (px, API 31+, sinon 0 = écran plat)
                //   - dy = distance verticale du label au bord bas de l'écran (barre de nav + ~6 px entre la
                //     ligne de base du label et le bas du tracé)
                //   - intrusion = r - sqrt(r^2 - (r - dy)^2) tant que dy < r, sinon 0
                //   - on retranche le dégagement déjà présent (~10 dp : padding + marge interne)
                // Commun au profil d'une trace et à celui d'un itinéraire planifié, posés au même endroit.
                val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
                val lastLabelInsetPx = remember(view, navBottomPx) {
                    val r = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        view.rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
                    else 0).toFloat()
                    if (r <= 0f) 0f else {
                        val dy = navBottomPx + 6f
                        val intrusion = if (dy >= r) 0f else r - kotlin.math.sqrt(r * r - (r - dy) * (r - dy))
                        (intrusion - with(density) { 10.dp.toPx() }).coerceAtLeast(0f)
                    }
                }
                // profil à afficher : le calcul courant sinon le dernier connu (animation de fermeture) ;
                // pendant un chargement (tap/changement de trace) on n'affiche aucun graphique -> spinner.
                val shown = computed ?: if (!profileLoading) lastComputed else null
                // Portion actuellement affichée (zoom A/B) : sous-liste de shown.samples, ou la trace
                // complète si aucun zoom. Le kilométrage (Sample.x) n'est jamais remis à zéro (cumulé depuis
                // le début de la trace) ; seules les infos (distance/D+/D- du bandeau titre) sont recalculées
                // pour la seule portion visible (cf. TrackMath.statsOf, réutilisable sur une sous-plage).
                val zoomRange = profileZoom
                val windowStart = zoomRange?.first ?: 0
                // Mémorisé sur (shown, zoomRange) : sinon subList()/statsOf() recréaient une liste d'identité
                // différente à CHAQUE recomposition (déplacement du curseur, etc.), invalidant le cache de
                // rendu de ElevationProfile (comparaison par référence) -> reconstruction de tous les chemins
                // à chaque frame pendant un zoom (jusqu'à ~2000 points), d'où une surcharge CPU.
                val windowSamples = remember(shown, zoomRange) {
                    shown?.samples?.let { s ->
                        if (zoomRange != null && zoomRange.last < s.size) s.subList(zoomRange.first, zoomRange.last + 1) else s
                    }
                }
                val windowStats = remember(shown, zoomRange, windowSamples) {
                    if (zoomRange != null && windowSamples != null) TrackMath.statsOf(windowSamples) else shown?.stats
                }
                // Le curseur est une abscisse absolue : la fenetre zoomee n'a plus rien a lui retrancher,
                // et l'echantillon qu'il designe s'obtient par interpolation (cf. TrackMath.sampleAt).
                val cursorSample = remember(cursor, windowSamples) {
                    val along = cursor
                    if (along == null || windowSamples == null) null
                    else TrackMath.sampleAt(windowSamples, along)?.takeIf { along >= windowSamples.first().x && along <= windowSamples.last().x }
                }

                // Infos du point courant : flottent au-dessus de la carte, juste au-dessus du titre du profil
                // (décalées de la hauteur mesurée du panneau, superposé à la carte (Cf. profileBarHeightPx).
                if (computed != null && cursorSample != null) {
                    val imp = settings?.units == "imperial"
                    val cursorBottomDp = with(density) { profileBarHeightPx.toDp() }
                    // Memes colonnes que les infos de la trace, en plus petit : c'est la meme lecture, sur
                    // un point plutot que sur un parcours. A droite, ou le bouton de zoom se tenait : lui
                    // est seul et va a gauche, ces infos-ci sont trois ou quatre et prennent la largeur.
                    CompositionLocalProvider(LocalContentColor provides Color.Black) {
                        TrackInfoColumns(
                            cursorInfos(cursorSample, settings?.cursorInfos ?: "dist,ele,slope", imp),
                            fontSp = settings?.profCursorFont ?: 11,
                            bold = settings?.profCursorBold == true,
                            arrangement = Arrangement.spacedBy(14.dp),
                            // Meme ecart a droite qu'entre le bas du bloc et le profil : le coin se lit
                            // alors comme un coin, et non comme deux marges qui ne se repondent pas.
                            modifier = Modifier.align(Alignment.BottomEnd)
                                .padding(end = CursorInfoGap, bottom = cursorBottomDp + CursorInfoGap)
                                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                // Bouton de zoom du profil : même hauteur que les infos du point ci-dessus, mais à gauche -
                // elles occupent désormais la droite.
                if (activeLayerId != null && shown != null) {
                    val cursorBottomDp = with(density) { profileBarHeightPx.toDp() }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = cursorBottomDp + 4.dp)
                            .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp)),
                    ) {
                        // Seul bouton restant : le retour a la vue complete. Le zoom lui-meme se fait aux
                        // doigts sur le graphique (ecartement ou double-tap), comme dans le planificateur.
                        if (profileZoom != null) {
                            ProfileZoomButton(R.drawable.ic_profile_zoom_expand,
                                stringResource(R.string.content_desc_profile_zoom_expand), active = false) { vm.expandProfileZoom() }
                        }
                    }
                }
                // Profil (visible dès le tap sur une trace) superposé à la carte, qui garde toujours sa
                // taille pleine : ne jamais la redimensionner ici, une AndroidView type SurfaceView flashe en
                // noir le temps de son prochain frame quand elle est redimensionnée pendant une animation.
                // Le panneau apparaît immédiatement (titre + spinner) ; le graphique le remplace une fois calculé.
                AnimatedVisibility(
                    visible = activeLayerId != null, enter = expandVertically(), exit = shrinkVertically(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    val imp = settings?.units == "imperial"
                    Column(
                        Modifier.fillMaxWidth().background(Color.White)
                            .padding(horizontal = 8.dp).navigationBarsPadding()
                            .onGloballyPositioned { profileBarHeightPx = it.size.height },
                    ) {
                        // Le titre a sa ligne, les infos la leur : elles s'etalent alors sur toute la
                        // largeur, en colonnes libellees, la ou les serrer a la suite du titre les
                        // reduisait a une file de valeurs sans nom.
                        //
                        /*
                         * Le "i" de la legende des pentes se pose en EXPOSANT au bout de la premiere ligne
                         * du titre, et le titre lui reserve sa place.
                         *
                         * Il passait auparavant PAR-DESSUS le titre, qui filait dessous : un nom long se
                         * lisait alors sous un rond blanc. Le titre s'ecrit desormais sur deux lignes au
                         * besoin, dans la largeur qui reste - c'est-a-dire que la place du "i" est retiree
                         * a la colonne de texte, non prise au titre.
                         */
                        val legendShown = settings?.profileSlope != false && settings?.profileSlopeLegend == true
                        val hasLegendButton = settings?.profileSlope != false
                        Box(Modifier.fillMaxWidth().padding(vertical = ProfileTitleGap)) {
                            Text(profileTitle, fontSize = (settings?.profTitleFont ?: 16).sp,
                                fontWeight = if (settings?.profTitleBold != false) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = if (hasLegendButton) SlopeLegendGutter else 0.dp))
                            if (hasLegendButton) {
                                SlopeLegendButton(
                                    shown = legendShown,
                                    // En haut, non centre : sur un titre de deux lignes, un "i" centre
                                    // tomberait entre les deux, ou il n'appartiendrait plus a aucune.
                                    modifier = Modifier.align(Alignment.TopEnd),
                                ) { vm.setSlopeLegend(!legendShown) }
                            }
                        }
                        if (windowStats != null) {
                            // Temps de marche estime, pour une trace qui n'a pas d'horodatage : calcule sur
                            // les echantillons affiches, il suit donc le zoom du profil comme les autres
                            // totaux. Retenu tant qu'ils ne changent pas - une recomposition ne doit pas
                            // relancer un balayage de deux mille points.
                            val tobler = remember(windowSamples) {
                                windowSamples?.let { TrackMath.toblerSeconds(it) }
                            }
                            TrackInfoColumns(
                                titleInfos(windowStats, settings?.titleInfos ?: "dist,asc,desc", imp, tobler),
                                fontSp = settings?.profBarFont ?: 11,
                                bold = settings?.profBarBold == true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        // Ou l'on en est SUR CETTE TRACE, capteur allume : ce qui reste a parcourir, et le
                        // denivele qui reste a monter. C'est la seule ligne de l'application qui serve
                        // PENDANT la sortie et non avant ou apres - d'ou sa place, sous les totaux du
                        // parcours entier, qu'elle vient nuancer.
                        //
                        // Calculee sur la trace COMPLETE et non sur la fenetre zoomee : la question est
                        // "combien me reste-t-il jusqu'au bout", pas "jusqu'au bord du graphique".
                        val whole = computed?.samples
                        val position = lastUserLocation
                        val onTrack = remember(position, whole) {
                            if (position == null || whole.isNullOrEmpty()) null
                            else TrackMeasure.project(whole, position.second, position.first)
                                ?.let { it to TrackMath.remaining(whole, it.alongM) }
                        }
                        if (gpsActive && onTrack != null && settings?.profileRemaining != false) {
                            RemainingOnTrackRow(
                                projection = onTrack.first, remaining = onTrack.second, imperial = imp,
                                fontSp = settings?.profBarFont ?: 11,
                            )
                        }
                        if (windowStats != null && legendShown) {
                            SlopeLegend(windowStats.maxAbsSlope, settings?.profLegendFont ?: 9,
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                bold = settings?.profLegendBold == true)
                        } else {
                            // Sans legende, les infos toucheraient le graphique : elle tenait lieu de
                            // respiration entre les deux.
                            Spacer(Modifier.height(ProfileGraphGap))
                        }
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            // Trace sans altitude : le profil serait une ligne plate et muette. On le
                            // remplace par un avertissement, plutôt que de laisser croire à un parcours plat.
                            if (shown != null && !shown.hasZ && !profileLoading) {
                                NoElevationBanner(Modifier.fillMaxSize())
                            } else if (windowSamples != null && windowStats != null && !profileLoading) {
                                ElevationProfile(
                                    samples = windowSamples, stats = windowStats,
                                    grid = settings?.profileGrid ?: true,
                                    slope = settings?.profileSlope ?: true,
                                    lineColor = if (profileLineColor != Color.Unspecified) profileLineColor else MaterialTheme.colorScheme.primary,
                                    axisFontSp = settings?.profAxisFont ?: 9,
                                    axisBold = settings?.profAxisBold == true,
                                    cursorX = cursor, onScrub = { vm.onProfileTap(it) },
                                    onZoom = { scale, fraction -> vm.zoomProfile(scale, fraction) },
                                    onDoubleTap = { fraction -> vm.zoomProfile(2f, fraction) },
                                    lastLabelInsetPx = lastLabelInsetPx,
                                    verticalScaleMPerCm = settings?.profileVerticalScaleMPerCm ?: 0,
                                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                )
                            } else {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
                /*
                 * Bannière de l'alerte d'éloignement, posée EN DERNIER : elle passe donc par-dessus tout ce
                 * qui occupe le bas de l'écran - profil, bande du planificateur, consignes de saisie.
                 *
                 * C'est la seule barre du bas à s'accorder ce droit, et c'est ce qui la distingue : les
                 * autres accompagnent un geste qu'on vient de faire et peuvent attendre leur tour, celle-ci
                 * dit qu'on ne suit plus le chemin prévu. Une alerte qu'un panneau recouvre n'alerte
                 * personne, et la refermer d'un tap sur sa croix reste à un doigt.
                 */
                alert.followed?.takeIf { alert.banner }?.let { followed ->
                    OffTrackAlertBar(
                        trackName = followed.layerName,
                        awayM = alert.awayM ?: alertDistanceM.toDouble(),
                        imperial = imperialUnits,
                        onClose = { alert.silence() },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                    )
                }
            }
            // ouverture du menu par swipe depuis le bord gauche
            if (mode != "burger") {
                Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(24.dp)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 40f) scope.launch { drawerState.open() } },
                            onDragCancel = { total = 0f },
                        ) { _, d -> total += d }
                    })
            }
            // Basemap Control : panneau latéral droit (fonds activés, dossiers, drag & drop)
            if (basemapControlOpen) {
                Box(Modifier.fillMaxSize().clickable(
                    indication = null, interactionSource = remember { MutableInteractionSource() },
                ) { basemapControlOpen = false })
                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
                    BasemapControlPanel(
                        folders = basemapFolders, providers = providers, composites = composites,
                        currentBasemapId = settings?.defaultBasemapId ?: "",
                        widthFraction = (settings?.basemapControlWidthPct ?: 50) / 100f,
                        backgroundAlpha = (settings?.basemapControlOpacityPct ?: 80) / 100f,
                        onSelect = { id -> vm.selectBasemap(id); basemapControlOpen = false },
                        onCreateFolder = { name, parentId -> vm.createBasemapFolder(name, parentId) },
                        onReorderDrop = { k, id, tk, tid, pos -> vm.reorderBasemapDrop(k, id, tk, tid, pos) },
                        reliefOn = settings?.hillshadeOn == true,
                        onToggleRelief = { vm.toggleHillshade() },
                        onClose = { basemapControlOpen = false },
                    )
                }
            }
            // Configuration du téléchargement hors-ligne (SPEC section 3), plein écran par-dessus tout le reste.
        if (offlineExtentChoice) {
            OfflineExtentDialog(
                dark = isDarkTheme(settings?.theme),
                onDismiss = { offlineExtentChoice = false },
                onArea = {
                    offlineExtentChoice = false
                    offlineCorridor = null
                    offlineDrawingActive = true
                },
                onTrack = { offlineExtentChoice = false; offlinePickTrack = true },
            )
        }
        if (offlinePickTrack) {
            val candidates = layers.filter { it.hasLine }
            AlertDialog(
                onDismissRequest = { offlinePickTrack = false },
                title = { Text(stringResource(R.string.offline_extent_track)) },
                text = {
                    if (candidates.isEmpty()) Text(stringResource(R.string.offline_extent_no_track))
                    else Column(Modifier.verticalScroll(rememberScrollState())) {
                        candidates.forEach { l ->
                            TextButton(onClick = {
                                offlinePickTrack = false
                                // La geometrie est relue ICI et non a l'affichage de l'ecran suivant : le
                                // couloir se calcule sur les points reels, et l'estimation doit etre juste des
                                // la premiere image.
                                vm.trackPointsOf(l) { pts ->
                                    if (pts.isNotEmpty()) {
                                        offlineCorridor = l to pts
                                        offlineConfigBbox = Bbox.of(
                                            pts.minOf { it.first }, pts.minOf { it.second },
                                            pts.maxOf { it.first }, pts.maxOf { it.second },
                                        )
                                    }
                                }
                            }) { Text(l.name) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { offlinePickTrack = false }) { Text(stringResource(R.string.action_close)) } },
            )
        }
            offlineConfigBbox?.let { bbox ->
                val currentProvider = providers.firstOrNull { it.id == settings?.defaultBasemapId }
                OfflineDownloadConfigScreen(
                    bbox = bbox,
                    corridorPoints = offlineCorridor?.second,
                    corridorName = offlineCorridor?.first?.name.orEmpty(),
                    providerMinZoom = currentProvider?.minZoom ?: 0,
                    providerMaxZoom = currentProvider?.maxZoom ?: 19,
                    dark = darkChrome,
                    styleJson = style?.styleJson, styleUrl = style?.styleUrl,
                    onDismiss = { closeOfflineFlow() },
                    onDownload = { request ->
                        // Domaine B : lance le moteur, puis revient à la carte ou la popup de progression
                        // (observée via vm.offlineDownload) prend le relais.
                        vm.startOfflineDownload(request)
                        closeOfflineFlow()
                    },
                )
            }
            // Popup de progression du téléchargement hors-ligne (SPEC section 4), par-dessus la carte. Le mode
            // réduit (bouton orange) est rendu dans la barre de boutons en haut à gauche, pas ici.
            offlineDownload?.let { dl ->
                if (!dl.minimized) {
                    // Scrim opaque : bloque les interactions avec la carte derrière la popup.
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {})
                    OfflineDownloadCard(
                        state = dl,
                        onMinimize = { vm.setOfflineDownloadMinimized(true) },
                        onCancel = { vm.cancelOfflineDownload() },
                        onClose = { vm.dismissOfflineDownload() },
                        modifier = Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 420.dp),
                    )
                }
            }
        }
    }

    // choix du dossier de destination avant le sélecteur de fichier
    if (folderPicker) {
        AlertDialog(
            onDismissRequest = { folderPicker = false },
            title = { Text(stringResource(R.string.dialog_import_into_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { newFolderName = ""; folderPicker = false; newFolderDialog = true }) {
                        Icon(Icons.Outlined.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.label_new_folder))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    TextButton(onClick = { pendingFolder = null; folderPicker = false; launchPicker() }) { Text(stringResource(R.string.label_root)) }
                    folders.forEach { f ->
                        TextButton(onClick = { pendingFolder = f.id; folderPicker = false; launchPicker() }) { Text(f.name) }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { folderPicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    // Import du parcours calculé en couche : on demande d'abord son nom, puis son dossier d'accueil - dans
    // cet ordre parce que le nom est obligatoire et le dossier facultatif. Le choix de dossier ne s'affiche
    // que s'il y en a : sans dossier, la couche va forcément à la racine, et l'offrir serait une question
    // sans réponse possible.
    // Inverser efface les horodatages (cf. TrackEdit.reverse) : on le demande avant, et seulement quand la
    // trace en porte - une confirmation pour rien s'apprend a ignorer.
    reverseConfirm?.let { layer ->
        AlertDialog(
            onDismissRequest = { reverseConfirm = null },
            title = { Text(stringResource(R.string.reverse_confirm_title)) },
            text = { Text(stringResource(R.string.reverse_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.reverseLayer(layer); reverseConfirm = null }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = { TextButton(onClick = { reverseConfirm = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    // Ce que la retouche a refuse, ou ce sur quoi elle s'est repliee.
    edit.message?.let { message ->
        AlertDialog(
            onDismissRequest = { edit.message = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { edit.message = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }

    if (importDialog) {
        var layerName by remember { mutableStateOf(defaultRouteName(planner.targets, currentPositionLabel)) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        fun doImport(folderId: Long?) {
            routeGpx(layerName)?.let { vm.importLayer(it, GpxWriter.fileName(layerName), folderId) }
            importDialog = false
        }
        AlertDialog(
            onDismissRequest = { importDialog = false },
            title = { Text(stringResource(R.string.planner_import_layer)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CompactOutlinedTextField(
                        value = layerName, onValueChange = { layerName = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        label = { Text(stringResource(R.string.planner_layer_name)) },
                    )
                    if (folders.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(stringResource(R.string.dialog_import_into_title),
                            style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { doImport(null) }) { Text(stringResource(R.string.label_root)) }
                        folders.forEach { f -> TextButton(onClick = { doImport(f.id) }) { Text(f.name) } }
                    }
                }
            },
            // Sans dossier, rien ne reste à choisir : le bouton de validation suffit à conclure. Avec des
            // dossiers, c'est le tap sur l'un d'eux qui conclut, et ce bouton disparaît.
            confirmButton = {
                if (folders.isEmpty()) {
                    TextButton(onClick = { doImport(null) }, enabled = layerName.isNotBlank()) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { importDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // création d'un dossier puis poursuite de l'import dedans
    if (newFolderDialog) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text(stringResource(R.string.label_new_folder)) },
            text = {
                CompactOutlinedTextField(newFolderName, { newFolderName = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = newFolderName.ifBlank { defaultFolderName }
                    newFolderDialog = false
                    vm.createFolder(n, null) { id -> pendingFolder = id; launchPicker() }
                }) { Text(stringResource(R.string.action_create_and_import)) }
            },
            dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (editing) {
        // Même dérivation que l'infobulle : un vm.selectedFeature() ici ne serait pas observé par Compose.
        if (selectedFeature != null) PropertyEditor(
            feature = selectedFeature, schema = markerLayerData?.schema ?: emptyList(),
            onSave = { vm.saveFeature(it); editing = false }, onCancel = { editing = false },
            onDelete = { vm.deleteFeature(selectedFeature); editing = false },
            onPickImage = { onImported -> pendingImageCallback = onImported; imagePicker.launch("image/*") },
        )
    }

    if (importReport.isNotEmpty()) {
        val res = LocalContext.current.resources
        val invalid = importReport.filter { it.error == MainViewModel.ImportError.INVALID }.map { it.fileName }
        val empty = importReport.filter { it.error == MainViewModel.ImportError.EMPTY }.map { it.fileName }
        AlertDialog(
            onDismissRequest = { importReport = emptyList() },
            title = { Text(stringResource(R.string.dialog_import_result_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Un lot peut contenir les deux sortes de refus : chacune a sa phrase, accordée en nombre.
                    if (invalid.isNotEmpty()) {
                        Text(res.getQuantityString(R.plurals.import_invalid_files, invalid.size, invalid.joinToString(", ")))
                    }
                    if (empty.isNotEmpty()) {
                        Text(res.getQuantityString(R.plurals.import_empty_files, empty.size, empty.joinToString(", ")))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { importReport = emptyList() }) { Text(stringResource(R.string.action_ok)) } },
        )
    }

    // Recherche demandée sans accès à Internet, alors que le service visé en exige un (cf. needsInternet :
    // une instance auto-hébergée sur le réseau local n'est pas concernée).
    if (showNoConnectionDialog) {
        AlertDialog(
            onDismissRequest = { showNoConnectionDialog = false },
            title = { Text(stringResource(R.string.dialog_no_connection_title)) },
            text = { Text(stringResource(R.string.dialog_no_connection_text)) },
            confirmButton = {
                TextButton(onClick = { showNoConnectionDialog = false }) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }

    // "Distance depuis la position" demandée sans localisation active : on propose de l'activer. Répondre
    // oui fait aussi apparaître le bouton GPS sur la carte, sans quoi la position s'allumerait sans que
    // rien ne le montre ni ne permette de l'éteindre. La suite du parcours (permission, capteur éteint)
    // est celle du bouton lui-même, et la distance s'affiche dès la première position reçue.

    // Cloche tapée capteur éteint : l'alerte n'a rien à surveiller tant qu'aucune position n'arrive. On
    // propose donc de l'allumer, et répondre oui emprunte exactement le chemin du bouton GPS - permission,
    // puis réglages du système si le capteur est coupé, puis démarrage. Le choix de la trace attend la fin
    // de ce parcours (alertChooserPending), qui passe par des écrans hors de l'application.
    if (showAlertNeedsGpsDialog) {
        AlertDialog(
            onDismissRequest = { showAlertNeedsGpsDialog = false },
            title = { Text(stringResource(R.string.dialog_location_off_title)) },
            text = { Text(stringResource(R.string.alert_needs_gps_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showAlertNeedsGpsDialog = false
                    alertChooserPending = true
                    onGpsButtonTap()
                }) { Text(stringResource(R.string.action_enable)) }
            },
            dismissButton = {
                TextButton(onClick = { showAlertNeedsGpsDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (alert.chooserOpen) {
        TrackChooserDialog(
            candidates = alert.candidates,
            followed = alert.followed,
            imperial = imperialUnits,
            onPick = { alert.follow(it, alertDistanceM.toDouble()) },
            onStop = { alert.stop(); alert.closeChooser() },
            onDismiss = { alert.closeChooser() },
        )
    }

    if (showLocationDisabledDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDisabledDialog = false },
            title = { Text(stringResource(R.string.dialog_gps_disabled_title)) },
            text = { Text(stringResource(R.string.dialog_gps_disabled_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showLocationDisabledDialog = false
                    locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) { Text(stringResource(R.string.action_enable)) }
            },
            dismissButton = { TextButton(onClick = { showLocationDisabledDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Petit bouton carré (24 dp) des contrôles de zoom du profil (début/fin/expand). Contrairement à
 *  IconButton, sa couche d'état (ripple + fond bleu de sélection) est bornée à sa propre taille via clip :
 *  IconButton force une couche d'état de 40 dp qui déborderait de ce bouton réduit. */
@Composable
private fun ProfileZoomButton(
    @androidx.annotation.DrawableRes iconRes: Int, contentDesc: String, active: Boolean, onClick: () -> Unit,
) {
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDesc, modifier = Modifier.size(16.dp),
            tint = if (active) Color.White else LocalContentColor.current)
    }
}

/* ----------------------- Légende ----------------------- */

/** Zone de dépose visée dans la ligne survolée. */
private enum class HoverZone { BEFORE, INTO, AFTER }

/** État d'un drag en cours : type/id de l'item déplacé et l'écart cumulé (non borné) depuis le début. */
private data class DragInfo(val kind: String, val id: Long, val offset: Float)

/** Ligne actuellement survolée et zone visée dedans. */
private data class HoverTarget(val kind: String, val id: Long, val zone: HoverZone)

/** Contexte partagé transmis à tout l'arbre pour le drag & drop (positions des lignes, item en cours de drag, cible de dépose). */
private class DragCtx(
    val rowBounds: MutableMap<Pair<String, Long>, Float>,
    val dragInfo: DragInfo?,
    val hoverTarget: HoverTarget?,
    val onStart: (String, Long) -> Unit,
    val onDrag: (String, Long, Float) -> Unit,
    val onEnd: (String, Long) -> Unit,
)

/** Vrai si `candidateId` est un descendant (direct ou indirect) de `ancestorId`, pour éviter les cycles au drop. */
private fun isDescendantFolder(candidateId: Long, ancestorId: Long, folders: List<FolderEntity>): Boolean {
    var cur = folders.firstOrNull { it.id == candidateId }?.parentId
    while (cur != null) {
        if (cur == ancestorId) return true
        cur = folders.firstOrNull { it.id == cur }?.parentId
    }
    return false
}

private fun parentIdOf(
    kind: String, id: Long, folders: List<FolderEntity>, layers: List<LayerEntity>,
): Long? = when (kind) {
    "folder" -> folders.firstOrNull { it.id == id }?.parentId
    else -> layers.firstOrNull { it.id == id }?.folderId
}

/** Fusionne dossiers + couches d'un même parent en une seule liste triée par ordre unifié. */
private fun combinedChildren(parentId: Long?, folders: List<FolderEntity>, layers: List<LayerEntity>): List<Any> {
    val f = folders.filter { it.parentId == parentId }
    val l = layers.filter { it.folderId == parentId }
    fun order(e: Any): Int = when (e) { is FolderEntity -> e.sortOrder; is LayerEntity -> e.sortOrder; else -> 0 }
    fun typeRank(e: Any): Int = if (e is FolderEntity) 0 else 1
    fun idOf(e: Any): Long = when (e) { is FolderEntity -> e.id; is LayerEntity -> e.id; else -> 0L }
    return (f + l).sortedWith(compareBy({ order(it) }, { typeRank(it) }, { idOf(it) }))
}

/** Vibration plus marquée que le retour haptique système par défaut, pour le démarrage d'un drag. */
private fun strongHaptic(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createOneShot(50, 200))
    } else {
        @Suppress("DEPRECATION") vibrator?.vibrate(50)
    }
}

@Composable
private fun LegendContent(
    folders: List<FolderEntity>, layers: List<LayerEntity>, settings: SettingsEntity?,
    vm: MainViewModel,
    // Le tiroir reste COMPOSE une fois referme : sans ce drapeau, son contenu n'a aucun moyen de savoir
    // qu'il a disparu de l'ecran, et garde l'etat dans lequel on l'a laisse.
    open: Boolean,
    onSettings: () -> Unit, onClose: () -> Unit, onImport: () -> Unit,
    showOfflineButton: Boolean, onDownloadOffline: () -> Unit,
    onZoom: (String, Long) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val rowPx = with(LocalDensity.current) { 52.dp.toPx() }
    val scope = rememberCoroutineScope()

    // Positions (Y, coord. racine) de chaque ligne affichée, pour détecter au vol la ligne survolée pendant un drag.
    val rowBounds = remember { mutableStateMapOf<Pair<String, Long>, Float>() }
    var dragInfo by remember { mutableStateOf<DragInfo?>(null) }
    val hoverTarget: HoverTarget? = dragInfo?.let { info ->
        val startTop = rowBounds[info.kind to info.id] ?: return@let null
        val centerY = startTop + rowPx / 2f + info.offset
        val hit = rowBounds.entries.firstOrNull { (k, top) ->
            centerY in top..(top + rowPx) && !(k.first == info.kind && k.second == info.id)
        } ?: return@let null
        val (key, top) = hit
        val rel = (centerY - top) / rowPx
        val zone = when {
            rel < 0.25f -> HoverZone.BEFORE
            rel > 0.75f -> HoverZone.AFTER
            key.first == "folder" -> HoverZone.INTO
            rel < 0.5f -> HoverZone.BEFORE
            else -> HoverZone.AFTER
        }
        // Empêche de déposer un dossier dans lui-même ou dans l'un de ses propres descendants (créerait un cycle).
        if (info.kind == "folder") {
            val prospectiveParent = if (zone == HoverZone.INTO) key.second else parentIdOf(key.first, key.second, folders, layers)
            if (prospectiveParent == info.id || (prospectiveParent != null && isDescendantFolder(prospectiveParent, info.id, folders))) {
                return@let null
            }
        }
        HoverTarget(key.first, key.second, zone)
    }
    val dctx = DragCtx(
        rowBounds = rowBounds,
        dragInfo = dragInfo,
        hoverTarget = hoverTarget,
        onStart = { kind, id -> dragInfo = DragInfo(kind, id, 0f) },
        onDrag = { kind, id, total -> if (dragInfo?.kind == kind && dragInfo?.id == id) dragInfo = dragInfo!!.copy(offset = total) },
        onEnd = { kind, id ->
            val info = dragInfo
            val target = hoverTarget
            if (info != null && info.kind == kind && info.id == id && target != null) {
                val position = when (target.zone) {
                    HoverZone.BEFORE -> DropPosition.BEFORE
                    HoverZone.INTO -> DropPosition.INTO
                    HoverZone.AFTER -> DropPosition.AFTER
                }
                // Garde la ligne "en drag" (donc à sa position de dépose) jusqu'à ce que l'écriture soit faite,
                // pour éviter qu'elle ne revienne un instant à sa place d'origine avant de sauter à la nouvelle.
                scope.launch {
                    vm.reorderDrop(kind, id, target.kind, target.id, position)
                    dragInfo = null
                }
            } else {
                dragInfo = null
            }
        },
    )

    val openRename: (String, Long, String) -> Unit = { k, id, n -> renameTarget = k to id; renameValue = n }
    val openMove: (String, Long) -> Unit = { k, id -> moveTarget = k to id }

    var newFolderDialog by remember { mutableStateOf(false) }
    var newFolderParent by remember { mutableStateOf<Long?>(null) }
    var newFolderName by remember { mutableStateOf("") }
    val defaultFolderName = stringResource(R.string.label_new_folder)
    val openNewFolder: (Long?) -> Unit = { parentId -> newFolderParent = parentId; newFolderName = ""; newFolderDialog = true }

    // Dossiers avec un import en cours (null = racine) : spinner ; et confirmation de suppression de dossier.
    val importing by vm.importing.collectAsState()
    val elevating by vm.elevating.collectAsState()
    val importingIds = ImportSpinners(importing.keys, elevating.keys)
    var deleteFolderTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var statsTarget by remember { mutableStateOf<FolderEntity?>(null) }

    // ---------- sorties d'une couche : enregistrer un GPX, ou l'envoyer ailleurs ----------
    // Le selecteur de fichier est UNIQUE et vit ici, non dans chaque ligne de l'arborescence : une couche
    // par ligne en ouvrirait autant, pour un geste qui ne concerne jamais qu'une couche a la fois. La
    // couche visee attend donc son tour dans un etat, que le retour du selecteur relit.
    val drawerCtx = LocalContext.current
    var gpxPending by remember { mutableStateOf<ByteArray?>(null) }
    val gpxExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val bytes = gpxPending
        gpxPending = null
        if (uri != null && bytes != null) {
            runCatching { drawerCtx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
        }
    }
    // L'export se fait en DEUX temps : on choisit d'abord le format, on nomme le fichier ensuite. Les deux
    // formats ne portent pas la meme chose (cf. ExportFormatDialog), et ce choix ne se devine pas depuis le
    // selecteur de fichier du systeme, qui ne montre qu'un nom et un dossier.
    var exportTarget by remember { mutableStateOf<LayerEntity?>(null) }
    val onExportLayer: (LayerEntity) -> Unit = { layer -> exportTarget = layer }
    val shareLabel = stringResource(R.string.action_share)
    val onShareLayer: (LayerEntity) -> Unit = { layer ->
        vm.shareLayerGpx(layer) { uri ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, layer.name)
                // Le droit de lecture est accorde a l'application QUI RECOIT, et pour cette URI seulement :
                // sans ce drapeau, elle obtient une adresse qu'elle n'a pas le droit d'ouvrir.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { drawerCtx.startActivity(Intent.createChooser(send, shareLabel)) }
        }
    }
    val layerActions = LayerActions(onExport = onExportLayer, onShare = onShareLayer)
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Tiroir referme : la recherche se referme avec lui. On rouvre le menu pour consulter l'arborescence,
    // pas pour retrouver un filtre pose la fois d'avant - et le bouton allume, seul temoin de ce filtre,
    // n'est plus la pour le dire.
    LaunchedEffect(open) {
        if (!open && searchOpen) {
            searchOpen = false
            searchQuery = ""
            focusManager.clearFocus()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header 2 lignes à hauteur totale inchangée (SPEC section 6.1) : l'ancien Row faisait 48dp de
        // contenu (IconButton) + 32dp de padding vertical = 80dp. On désactive le plancher tactile
        // de 48dp de Material3 (cf. Groupe N) pour tenir 2 lignes de 32dp dans le même budget.
        Box(Modifier.fillMaxWidth()) {
            // Plus de marge sous la bande grise : l'arborescence commence juste dessous, la bande faisant
            // desormais la separation a elle seule.
            Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(34.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clickable(onClick = onSettings)) {
                            Avatar(settings?.avatarSource ?: "", size = 30.dp, contentDescription = stringResource(R.string.settings_title))
                        }
                        Spacer(Modifier.width(8.dp))
                        // Meme taille et meme graisse que le titre "Reglages" (17 sp, semi-gras) : ce sont
                        // les deux titres d'ecran de l'application, et rien ne justifierait qu'ils se lisent
                        // a deux tailles. La maquette du tiroir en donnait 15 ; celle des reglages, plus
                        // recente, en donne 17, et c'est elle qui fait foi pour les deux.
                        // Fallback traduit si le titre personnalise est vide, au lieu de ne rien afficher.
                        val title = settings?.customTitle?.ifBlank { stringResource(R.string.drawer_default_title) }
                            ?: stringResource(R.string.drawer_default_title)
                        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(11.dp))
                    // Les actions du header, sur la seule bande grise de l'en-tete : c'est elle qui les
                    // rassemble, et le titre au-dessus s'en trouve rendu au fond du tiroir.
                    //
                    // "Importer" et "Telecharger" portent leur libelle, et tout le bouton - icone comme
                    // texte - declenche l'action : ce sont les deux gestes qu'on vient chercher ici, et
                    // une icone seule ne dit pas ce qu'elle importe ni ce qu'elle telecharge. "Nouveau
                    // dossier" garde l'icone seule, universelle, et laisse la place aux deux autres.
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeaderAction(Icons.Outlined.CreateNewFolder, stringResource(R.string.label_new_folder)) { openNewFolder(null) }
                        HeaderAction(Icons.Outlined.FileUpload, stringResource(R.string.action_import),
                            showLabel = true, onClick = onImport)
                        if (showOfflineButton) {
                            HeaderAction(Icons.Outlined.FileDownload, stringResource(R.string.offline_action_download),
                                showLabel = true, onClick = onDownloadOffline)
                        }
                        // La recherche est a l'oppose des trois autres : elle ne cree ni n'importe rien,
                        // elle change la facon de LIRE ce qui est en dessous. Le vide entre elle et les
                        // autres dit cette difference mieux qu'un filet.
                        Spacer(Modifier.weight(1f))
                        HeaderAction(
                            Icons.Filled.Search, stringResource(R.string.search_placeholder),
                            active = searchOpen,
                        ) {
                            searchOpen = !searchOpen
                            // Refermer la barre efface la recherche : la garder filtrerait l'arborescence
                            // sans que rien a l'ecran ne dise pourquoi elle est incomplete.
                            if (!searchOpen) { searchQuery = ""; focusManager.clearFocus() }
                        }
                    }
                }
                // Décalé au maximum vers l'angle haut-droit (SPEC section 6.1), superposé aux 2 lignes ci-dessus
                // sans agrandir la hauteur du Box (32dp < hauteur totale du Column).
                // Meme marge de bord que les lignes de l'arborescence, et meme cible de 30 dp : la croix
                // tombe donc exactement sur la colonne des menus "trois points" des couches. A 14 dp, elle
                // s'en decalait de sept, ce qui se voyait comme un defaut d'alignement sans qu'on sache
                // lequel des deux etait de travers.
                Box(
                    Modifier.align(Alignment.TopEnd).padding(end = DrawerRowPadH).size(DrawerHitSize)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close_menu), Modifier.size(17.dp))
                }
            }
        }

        // Recherche : elle remplace l'arbre par la liste a plat de ce qu'elle trouve, plutot que de
        // deplier les dossiers autour des resultats. Une couche trouvee est une couche qu'on veut voir
        // MAINTENANT - son rangement, on le connait deja, c'est meme pour ne pas avoir a le parcourir
        // qu'on a tape son nom.
        if (searchOpen) {
            // Le focus est pris A L'OUVERTURE, et la seule : demande a chaque recomposition, le champ
            // reprendrait le clavier des qu'on le lache.
            LaunchedEffect(Unit) { searchFocus.requestFocus() }
            SearchField(searchQuery, searchFocus) { searchQuery = it }
        }

        /*
         * Un toucher dans l'arborescence rend le focus au tiroir, donc referme le clavier.
         *
         * Sur la passe INITIALE et sans consommer l'evenement : la ligne touchee le recoit ensuite
         * normalement. Un simple clickable englobant, lui, aurait vole les taps des lignes.
         */
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            if (e.type == PointerEventType.Press) focusManager.clearFocus()
                        }
                    }
                }
                .verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
        ) {
            val query = searchQuery.trim()
            if (query.isNotEmpty()) {
                val found = layers.filter { TreeSearch.matches(it.name, query) }
                if (found.isEmpty()) {
                    Text(
                        stringResource(R.string.search_no_result), fontSize = DrawerNameSp.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                found.forEach { item ->
                    key("found", item.id) {
                        LayerRow(item, 0, vm, dctx, openRename, openMove, onZoom, layerActions)
                    }
                }
                return@Column
            }
            importingIds.state(null)?.let { ImportSpinnerRow(0, it) }
            combinedChildren(null, folders, layers).forEach { item ->
                when (item) {
                    is FolderEntity -> key("folder", item.id) {
                        FolderNode(item, folders, layers, 0, vm, dctx, openRename, openMove, openNewFolder, onZoom, importingIds,
                            layerActions, onStats = { statsTarget = it }) { deleteFolderTarget = it }
                    }
                    is LayerEntity -> key("layer", item.id) { LayerRow(item, 0, vm, dctx, openRename, openMove, onZoom, layerActions) }
                }
            }
        }
    }

    exportTarget?.let { layer ->
        ExportFormatDialog(
            dark = isDarkTheme(settings?.theme),
            onDismiss = { exportTarget = null },
            onPick = { geoJson ->
                exportTarget = null
                val extension = if (geoJson) "geojson" else "gpx"
                val ready: (ByteArray) -> Unit = { bytes ->
                    gpxPending = bytes
                    gpxExporter.launch(GpxWriter.fileName(layer.name, extension))
                }
                if (geoJson) vm.layerGeoJson(layer, ready) else vm.layerGpx(layer, ready)
            },
        )
    }

    statsTarget?.let { f ->
        FolderStatsDialog(
            folder = f, folders = folders, layers = layers,
            imperial = settings?.units == "imperial", dark = isDarkTheme(settings?.theme),
            onDismiss = { statsTarget = null },
        )
    }

    deleteFolderTarget?.let { f ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            title = { Text(stringResource(R.string.dialog_delete_folder_title)) },
            text = { Text(stringResource(R.string.dialog_delete_folder_text)) },
            // "Oui" (confirmButton, à droite) supprime aussi le contenu ; "Non" (à gauche) le remonte au parent.
            confirmButton = { TextButton(onClick = { vm.deleteFolder(f, deleteContents = true); deleteFolderTarget = null }) { Text(stringResource(R.string.action_yes)) } },
            dismissButton = { TextButton(onClick = { vm.deleteFolder(f, deleteContents = false); deleteFolderTarget = null }) { Text(stringResource(R.string.action_no)) } },
        )
    }

    if (newFolderDialog) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text(stringResource(R.string.label_new_folder)) },
            text = {
                CompactOutlinedTextField(newFolderName, { newFolderName = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = newFolderName.ifBlank { defaultFolderName }
                    newFolderDialog = false
                    vm.createFolder(n, newFolderParent)
                }) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    renameTarget?.let { (kind, id) ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.action_rename)) },
            text = { CompactOutlinedTextField(renameValue, { renameValue = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    when (kind) { "folder" -> vm.renameFolder(id, renameValue); "layer" -> vm.renameLayer(id, renameValue) }
                    renameTarget = null
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    moveTarget?.let { (kind, id) ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text(stringResource(R.string.dialog_move_to_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { applyMove(vm, kind, id, null); moveTarget = null }) { Text(stringResource(R.string.label_root)) }
                    folders.forEach { f -> TextButton(onClick = { applyMove(vm, kind, id, f.id); moveTarget = null }) { Text(f.name) } }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { moveTarget = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

private fun applyMove(vm: MainViewModel, kind: String, id: Long, target: Long?) {
    when (kind) { "folder" -> vm.moveFolder(id, target); "layer" -> vm.moveLayer(id, target) }
}

@Composable
private fun FolderNode(
    folder: FolderEntity, allFolders: List<FolderEntity>, allLayers: List<LayerEntity>,
    depth: Int, vm: MainViewModel, dctx: DragCtx,
    onRename: (String, Long, String) -> Unit, onMove: (String, Long) -> Unit, onNewFolder: (Long?) -> Unit, onZoom: (String, Long) -> Unit,
    importingIds: ImportSpinners, layerActions: LayerActions,
    onStats: (FolderEntity) -> Unit, onDeleteFolder: (FolderEntity) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    var showColor by remember { mutableStateOf(false) }
    // Les couches sur lesquelles portent les actions du dossier : sous-dossiers compris, comme la
    // suppression et l'oeil. Calculees une fois, l'oeil et le choix de couleur en repondent tous deux.
    val contents = layersUnder(folder.id, allFolders, allLayers)
    val context = LocalContext.current
    val isDragging = dctx.dragInfo?.kind == "folder" && dctx.dragInfo.id == folder.id
    val offset = if (isDragging) dctx.dragInfo.offset else 0f
    val hoverZone = dctx.hoverTarget?.takeIf { it.kind == "folder" && it.id == folder.id }?.zone

    if (hoverZone == HoverZone.BEFORE) DropIndicatorLine()
    DrawerRow(
        depth = depth, dragging = isDragging, offset = offset, hovered = hoverZone == HoverZone.INTO,
        onPositioned = { dctx.rowBounds["folder" to folder.id] = it },
    ) {
        val allVisible = contents.isEmpty() || contents.all { it.visible }
        DrawerIcon(
            if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
            if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
            size = DrawerChevronSize, onClick = { expanded = !expanded },
        )
        DrawerIcon(
            if (allVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            if (allVisible) stringResource(R.string.action_hide_folder) else stringResource(R.string.action_show_folder),
            onClick = { vm.setFolderVisible(folder.id, !allVisible) },
        )
        // Couleur adaptée au thème (clair/sombre), pas figée : contour (fermé) ou remplissage (ouvert)
        // noir en thème clair, blanc en thème sombre (bug 4.1). Même silhouette pleine (Filled.Folder) dans
        // les deux états : Filled.FolderOpen ne remplit que l'onglet arrière, pas tout le dossier.
        Icon(if (expanded) Icons.Filled.Folder else Icons.Outlined.Folder, null,
            Modifier.size(DrawerIconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        // Nom d'un dossier : gras et en capitales. Il ne porte pas de couleur, contrairement a une couche,
        // et n'a que sa graisse pour se distinguer de ce qu'il contient.
        Text(folder.name.uppercase(), fontSize = DrawerNameSp.sp, lineHeight = (DrawerNameSp * 1.25f).sp,
            fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        // Nombre de couches sous le dossier, sous-dossiers compris : c'est ce que ses actions touchent
        // (l'oeil, la couleur commune), et ce qu'un dossier replie cache.
        if (contents.isNotEmpty()) {
            Text("${contents.size}", fontSize = DrawerCountSp.sp, lineHeight = (DrawerCountSp * 1.3f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Poignee et menu colles : ce sont les deux prises de la ligne, pas deux elements a distinguer.
        // L'ecart de la ligne les separerait autant que le nom du compteur, qui n'ont rien a voir entre eux.
        RowEndActions {
            DragHandle(
                onStart = { strongHaptic(context); dctx.onStart("folder", folder.id) },
                onDrag = { dctx.onDrag("folder", folder.id, it) },
                onEnd = { dctx.onEnd("folder", folder.id) })
            RowMenu(onRename = { onRename("folder", folder.id, folder.name) }, onMove = { onMove("folder", folder.id) },
                onNewSub = { onNewFolder(folder.id) }, onDelete = { onDeleteFolder(folder) },
                onZoom = { onZoom("folder", folder.id) },
                onColor = if (contents.isEmpty()) null else ({ showColor = true }),
                onStats = { onStats(folder) })
        }
    }
    if (hoverZone == HoverZone.AFTER) DropIndicatorLine()
    // Coche posee sur la couleur commune aux couches du dossier, s'il y en a une : autrement elles sont de
    // plusieurs couleurs, et en designer une comme "celle du dossier" serait faux.
    if (showColor) {
        ColorPickerDialog(
            current = contents.map { it.color }.distinct().singleOrNull() ?: "",
            onPick = { vm.setFolderColor(folder.id, it); showColor = false },
            onDismiss = { showColor = false },
        )
    }
    if (expanded) {
        // Spinner d'import entre la ligne du dossier et sa première couche (SPEC).
        importingIds.state(folder.id)?.let { ImportSpinnerRow(depth + 1, it) }
        combinedChildren(folder.id, allFolders, allLayers).forEach { item ->
            when (item) {
                is FolderEntity -> key("folder", item.id) {
                    FolderNode(item, allFolders, allLayers, depth + 1, vm, dctx, onRename, onMove, onNewFolder, onZoom,
                        importingIds, layerActions, onStats, onDeleteFolder)
                }
                is LayerEntity -> key("layer", item.id) { LayerRow(item, depth + 1, vm, dctx, onRename, onMove, onZoom, layerActions) }
            }
        }
    }
}

/** Ligne « import en cours » : petit spinner, indenté comme les couches du dossier. */
@Composable
private fun ImportSpinnerRow(depth: Int, elevation: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = (4 + depth * 20).dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        // Le libelle dit CE QU'ON ATTEND, et non ce qu'on a demande : l'altimetrie interroge deux services
        // distants et peut durer, la ou le reste de l'import ne tient qu'a la machine. Sans cette
        // distinction, un import qui semble bloque n'est qu'un import qui attend le reseau.
        Text(
            stringResource(if (elevation) R.string.elevation_in_progress else R.string.import_in_progress),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Dossiers dont un import est en cours, et ceux dont l'import en est au calcul de l'altimetrie.
 *
 * Un porteur plutot que deux ensembles traverses cote a cote : l'arborescence les passe de dossier en
 * dossier sur toute sa profondeur, et le second aurait suivi le premier a chaque appel.
 */
class ImportSpinners(private val importing: Set<Long?>, private val elevating: Set<Long?>) {
    /** Null quand ce dossier n'attend rien ; sinon, vrai si ce qu'il attend est l'altimetrie. */
    fun state(folderId: Long?): Boolean? =
        if (folderId !in importing) null else folderId in elevating
}

/** Icône globe si la couche a des points ET des lignes, ligne (trace) si lignes seules, sinon point. */
@Composable
private fun LayerRow(
    layer: LayerEntity, depth: Int, vm: MainViewModel, dctx: DragCtx,
    onRename: (String, Long, String) -> Unit, onMove: (String, Long) -> Unit, onZoom: (String, Long) -> Unit,
    actions: LayerActions,
) {
    LayerLine(
        kind = "layer", id = layer.id,
        depth = depth, color = layer.color, name = layer.name, visible = layer.visible,
        icon = when {
            layer.hasLine && layer.hasPoints -> R.drawable.ic_layer_globe
            layer.hasLine -> R.drawable.ic_layer_route
            else -> R.drawable.ic_layer_place
        },
        onToggle = { vm.setLayerVisible(layer, it) }, onColor = { vm.setLayerColor(layer, it) }, dctx = dctx,
        onRename = { onRename("layer", layer.id, layer.name) }, onMove = { onMove("layer", layer.id) },
        onDelete = { vm.deleteLayer(layer) }, onZoom = { onZoom("layer", layer.id) },
        layerActions = actions, layer = layer,
    )
}

/** Ligne couche : œil + symbole couleur + nom + poignée + menu. */
@Composable
private fun LayerLine(
    kind: String, id: Long,
    depth: Int, color: String, name: String, visible: Boolean,
    @DrawableRes icon: Int,
    onToggle: (Boolean) -> Unit, onColor: (String) -> Unit, dctx: DragCtx,
    onRename: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit, onZoom: () -> Unit,
    // Une couche et ce qu'on peut en faire ; null pour un dossier, dont le menu n'a ni sortie ni retouche.
    layerActions: LayerActions? = null, layer: LayerEntity? = null,
) {
    var showColor by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isDragging = dctx.dragInfo?.kind == kind && dctx.dragInfo.id == id
    val offset = if (isDragging) dctx.dragInfo.offset else 0f
    val hoverZone = dctx.hoverTarget?.takeIf { it.kind == kind && it.id == id }?.zone

    if (hoverZone == HoverZone.BEFORE) DropIndicatorLine()
    DrawerRow(
        depth = depth, dragging = isDragging, offset = offset, hovered = false,
        onPositioned = { dctx.rowBounds[kind to id] = it },
    ) {
        // Place du chevron d'un dossier, laissee vide : sans elle, l'oeil d'une couche remonterait sous
        // celui de son dossier et l'arbre perdrait sa colonne.
        Spacer(Modifier.width(DrawerChevronSize))
        DrawerIcon(
            if (visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            if (visible) stringResource(R.string.action_hide) else stringResource(R.string.action_show),
            onClick = { onToggle(!visible) },
        )
        // Le symbole palit avec le nom quand la couche est masquee : une ligne eteinte doit se lire
        // eteinte d'un bout a l'autre, pas seulement a son oeil barre.
        DrawerIcon(
            painter = painterResource(icon), contentDescription = stringResource(R.string.action_color),
            tint = Color(color.toColorInt()).copy(alpha = if (visible) 1f else 0.4f),
            onClick = { showColor = true },
        )
        Text(name, fontSize = DrawerNameSp.sp, lineHeight = (DrawerNameSp * 1.25f).sp, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.weight(1f))
        RowEndActions {
            DragHandle(
                onStart = { strongHaptic(context); dctx.onStart(kind, id) },
                onDrag = { dctx.onDrag(kind, id, it) },
                onEnd = { dctx.onEnd(kind, id) })
            RowMenu(
                onRename = onRename, onMove = onMove, onNewSub = null, onDelete = onDelete, onZoom = onZoom,
                layer = layer, layerActions = layerActions,
            )
        }
    }
    if (hoverZone == HoverZone.AFTER) DropIndicatorLine()
    if (showColor) ColorPickerDialog(color, onPick = { onColor(it); showColor = false }, onDismiss = { showColor = false })
}

/**
 * Ou l'on en est sur la trace affichee : ce qui reste a parcourir, et a monter.
 *
 * L'ecart a la trace n'est dit qu'au-dela de [OffTrackThresholdM] : sur la trace, il vaut la precision du
 * capteur et ne veut rien dire ; loin d'elle, il est la seule information qui compte - le "restant" ne
 * decrit alors plus le chemin qu'on suit.
 */
@Composable
private fun RemainingOnTrackRow(
    projection: TrackMeasure.Projection, remaining: TrackMath.Remaining, imperial: Boolean, fontSp: Int,
) {
    val off = projection.awayM >= OffTrackThresholdM
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Place, null, Modifier.size((fontSp + 3).dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.track_remaining,
                Format.distance(remaining.distance, imperial), Format.elevation(remaining.ascent, imperial)),
            fontSize = fontSp.sp, fontWeight = FontWeight.Medium, color = Color.Black,
        )
        if (off) {
            Text(
                stringResource(R.string.track_off_track, Format.distance(projection.awayM, imperial)),
                fontSize = (fontSp - 1).sp, color = MaterialTheme.colorScheme.error,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/** Au-dela de cet ecart, on ne suit plus la trace : c'est le moment de le dire. Cinquante metres passent
 *  la precision d'un GPS de telephone sous couvert forestier, sans attendre qu'on soit vraiment perdu. */
private const val OffTrackThresholdM = 50.0

/**
 * Champ de recherche du menu lateral : la loupe DANS le champ, la croix a l'autre bout.
 *
 * Les deux icones sont a l'interieur du contour, et non posees de part et d'autre : dehors, la loupe
 * mangeait la marge gauche et le champ n'etait plus centre entre les deux bords du tiroir. Dedans, elles
 * appartiennent au champ - ce qu'elles decrivent - et les marges redeviennent egales.
 *
 * La croix n'apparait qu'une fois la saisie commencee : c'est le seul moment ou elle a quelque chose a
 * faire.
 */
@Composable
private fun SearchField(query: String, focus: FocusRequester, onQuery: (String) -> Unit) {
    CompactOutlinedTextField(
        value = query, onValueChange = onQuery, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).focusRequester(focus),
        placeholder = { Text(stringResource(R.string.search_placeholder), fontSize = DrawerNameSp.sp) },
        leadingIcon = {
            Icon(Icons.Filled.Search, null, Modifier.size(DrawerIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(DrawerHitSize).clip(CircleShape).clickable { onQuery("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_clear_search),
                        Modifier.size(DrawerIconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

/*
 * Bulle du marqueur de coupe. Sa POINTE marque le point, le corps se tenant a cote - au-dessus, en
 * dessous, a gauche ou a droite selon ce que la trace laisse libre (cf. CutBubblePlacement) : c'est la
 * seule facon de designer un endroit sans le masquer, et cet endroit-la est precisement celui qu'on
 * regarde avant de confirmer.
 */
private val CutBubbleWidth = 30.dp
private val CutBubbleHeight = 28.dp
private val CutTailHeight = 8.dp
private val CutTailWidth = 11.dp

/** Air laisse autour de la bulle quand la carte se decale pour la degager : sans elle, la bulle
 *  affleurerait le bord de l'ecran, ce qui se lit comme un objet coupe. */
private val CutBubbleMargin = 12.dp

/**
 * Marqueur de coupe : les ciseaux dans une bulle dont la pointe touche le point de coupe.
 *
 * Pose par son coin haut-gauche (cf. son appelant) : la pointe tombe alors exactement sur le point vise.
 */
@Composable
private fun CutMarkerBubble(
    side: CutBubblePlacement.Side, bg: Color, fg: Color, modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // La pointe change de cote AVEC le cote retenu : bulle en haut, elle descend de son bord bas ; bulle a
    // droite, elle part de son bord gauche. Dans les quatre cas, son sommet tombe sur le point.
    val shape = remember(density, side) {
        val tail = with(density) { CutTailHeight.toPx() }
        val tailW = with(density) { CutTailWidth.toPx() }
        val radius = with(density) { 8.dp.toPx() }
        GenericShape { size, _ ->
            // Le corps recule du cote de la pointe, qui occupe la place ainsi liberee.
            val left = if (side == CutBubblePlacement.Side.RIGHT) tail else 0f
            val top = if (side == CutBubblePlacement.Side.BOTTOM) tail else 0f
            val right = size.width - if (side == CutBubblePlacement.Side.LEFT) tail else 0f
            val bottom = size.height - if (side == CutBubblePlacement.Side.TOP) tail else 0f
            addRoundRect(RoundRect(Rect(left, top, right, bottom), CornerRadius(radius, radius)))
            val cx = (left + right) / 2
            val cy = (top + bottom) / 2
            when (side) {
                CutBubblePlacement.Side.TOP -> {
                    moveTo(cx - tailW / 2, bottom); lineTo(cx, size.height); lineTo(cx + tailW / 2, bottom)
                }
                CutBubblePlacement.Side.BOTTOM -> {
                    moveTo(cx - tailW / 2, top); lineTo(cx, 0f); lineTo(cx + tailW / 2, top)
                }
                CutBubblePlacement.Side.LEFT -> {
                    moveTo(right, cy - tailW / 2); lineTo(size.width, cy); lineTo(right, cy + tailW / 2)
                }
                CutBubblePlacement.Side.RIGHT -> {
                    moveTo(left, cy - tailW / 2); lineTo(0f, cy); lineTo(left, cy + tailW / 2)
                }
            }
            close()
        }
    }
    val horizontal = side == CutBubblePlacement.Side.LEFT || side == CutBubblePlacement.Side.RIGHT
    Box(
        modifier
            .size(
                width = CutBubbleWidth + if (horizontal) CutTailHeight else 0.dp,
                height = CutBubbleHeight + if (horizontal) 0.dp else CutTailHeight,
            )
            .shadow(3.dp, shape)
            // Les memes couleurs que les boutons poses sur la carte : blanc sur noir en theme sombre,
            // noir sur blanc en clair. La bulle est un ornement de carte comme eux, elle ne peut pas
            // suivre une autre regle a deux centimetres de la barre d'outils.
            .background(bg, shape)
            .padding(
                start = if (side == CutBubblePlacement.Side.RIGHT) CutTailHeight else 0.dp,
                top = if (side == CutBubblePlacement.Side.BOTTOM) CutTailHeight else 0.dp,
                end = if (side == CutBubblePlacement.Side.LEFT) CutTailHeight else 0.dp,
                bottom = if (side == CutBubblePlacement.Side.TOP) CutTailHeight else 0.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.ContentCut, null, Modifier.size(16.dp), tint = fg)
    }
}

/**
 * Barre de retouche des traces : inverser, couper, joindre, et defaire.
 *
 * Verticale et du meme cote que le bouton qui l'ouvre - la colonne de gauche -, sans quoi le regard
 * traverserait l'ecran entre le geste et son resultat. Les trois premiers boutons sont des OUTILS : ils
 * s'allument, et le prochain tap sur une trace leur revient. Le quatrieme est une action immediate, d'ou
 * sa separation.
 */
@Composable
private fun TrackEditToolbar(
    state: TrackEditState,
    canUndo: Boolean,
    chromeBg: Color,
    chromeFg: Color,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.background(chromeBg.copy(alpha = ControlButtonBgAlpha), RoundedCornerShape(ControlButtonRadius))
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EditToolButton(Icons.Filled.Autorenew, stringResource(R.string.action_reverse_track),
            active = state.tool == EditTool.REVERSE, chromeFg = chromeFg) { state.choose(EditTool.REVERSE) }
        EditToolButton(Icons.Filled.ContentCut, stringResource(R.string.action_split_track),
            active = state.tool == EditTool.CUT, chromeFg = chromeFg) { state.choose(EditTool.CUT) }
        EditToolButton(Icons.Filled.Link, stringResource(R.string.action_join_track),
            active = state.tool == EditTool.JOIN, chromeFg = chromeFg) { state.choose(EditTool.JOIN) }
        // Un filet entre les outils et l'annulation : celle-ci n'attend aucun tap sur la carte, elle
        // s'applique en se touchant. Les confondre ferait chercher un point a designer.
        Box(Modifier.padding(vertical = 2.dp).size(width = 18.dp, height = 1.dp)
            .background(chromeFg.copy(alpha = 0.25f)))
        EditToolButton(
            Icons.AutoMirrored.Filled.Undo, stringResource(R.string.action_undo),
            active = false, chromeFg = chromeFg, enabled = canUndo, onClick = onUndo,
        )
    }
}

/** Un bouton de la barre : allume quand son outil attend un tap, eteint sinon. */
@Composable
private fun EditToolButton(
    icon: ImageVector, label: String, active: Boolean, chromeFg: Color,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            icon, label, Modifier.size(20.dp),
            tint = when {
                !enabled -> chromeFg.copy(alpha = 0.3f)
                active -> MapChromeActive
                else -> chromeFg
            },
        )
    }
}

/** Un bouton d'une barre du bas : le choix principal se distingue par son fond. */
@Composable
private fun MapBarAction(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
        color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (primary) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/**
 * Ce que porte un dossier, en chiffres.
 *
 * Reprend la grammaire des reglages - carte blanche, une ligne par valeur, l'accent sur la valeur et non
 * sur son libelle : c'est le meme genre d'objet, une liste de couples nom/valeur qu'on parcourt de l'oeil.
 *
 * La duree ne s'affiche QUE si toutes les traces du dossier sont horodatees (cf. [FolderStats]) : un total
 * partiel serait plus petit que le temps reellement passe, sans que rien ne le dise.
 */
@Composable
private fun FolderStatsDialog(
    folder: FolderEntity,
    folders: List<FolderEntity>,
    layers: List<LayerEntity>,
    imperial: Boolean,
    dark: Boolean,
    onDismiss: () -> Unit,
) {
    val stats = remember(folder, folders, layers) { folderStats(folder.id, folders, layers) }
    ProvideSettingsPalette(dark = dark) {
        val p = settingsPalette
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = p.screen,
            title = { Text(folder.name, color = p.label, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            text = {
                SettingsCard {
                    SetRow(stringResource(R.string.stats_layers)) { ValueText("${stats.layers}") }
                    RowDivider()
                    SetRow(stringResource(R.string.stats_tracks)) { ValueText("${stats.tracks}") }
                    if (stats.markers > 0) {
                        RowDivider()
                        SetRow(stringResource(R.string.stats_marker_layers)) { ValueText("${stats.markers}") }
                    }
                    // Les mesures n'ont de sens que s'il y a des traces : un dossier de marqueurs
                    // afficherait sinon trois zeros, qui se lisent comme une mesure ratee.
                    if (stats.tracks > 0) {
                        RowDivider()
                        SetRow(stringResource(R.string.chip_distance)) {
                            ValueText(Format.distance(stats.distance, imperial))
                        }
                        RowDivider()
                        SetRow(stringResource(R.string.info_name_ascent)) {
                            ValueText(Format.elevation(stats.ascent, imperial))
                        }
                        RowDivider()
                        SetRow(stringResource(R.string.info_name_descent)) {
                            ValueText(Format.elevation(stats.descent, imperial))
                        }
                        stats.movingTime?.let {
                            RowDivider()
                            SetRow(stringResource(R.string.info_name_duration)) { ValueText(Format.duration(it)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = p.accent) }
            },
        )
    }
}

/**
 * Choix de ce que l'on telecharge : un rectangle, ou le couloir qui borde une trace.
 *
 * Le rectangle etait le seul mode, et il reste le bon pour un secteur qu'on ne connait pas encore. Pour
 * une sortie deja tracee, il fait telecharger tout ce qui l'entoure - trois quarts de tuiles qu'on ne
 * verra jamais sur une diagonale de soixante kilometres.
 */
@Composable
private fun OfflineExtentDialog(
    dark: Boolean, onDismiss: () -> Unit, onArea: () -> Unit, onTrack: () -> Unit,
) {
    ProvideSettingsPalette(dark = dark) {
        val p = settingsPalette
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = p.screen,
            title = { Text(stringResource(R.string.offline_extent_title), color = p.label) },
            text = {
                SettingsCard {
                    SetRow(stringResource(R.string.offline_extent_area), onClick = onArea) {
                        RowIcon(Icons.Filled.KeyboardArrowRight, null)
                    }
                    Hint(stringResource(R.string.offline_extent_area_hint))
                    RowDivider()
                    SetRow(stringResource(R.string.offline_extent_track), onClick = onTrack) {
                        RowIcon(Icons.Filled.KeyboardArrowRight, null)
                    }
                    Hint(stringResource(R.string.offline_extent_track_hint))
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = p.accent) }
            },
        )
    }
}

/**
 * Choix du format d'export : ce que chacun garde, et ce qu'il perd.
 *
 * Un sous-menu de plus dans la ligne de la couche aurait suffi a lancer l'export, mais pas a CHOISIR : les
 * deux formats ne portent pas la meme chose, et l'ecart ne se devine ni du nom du format ni du selecteur de
 * fichier du systeme, qui ne montre qu'un nom et un dossier. D'ou une etape a part, ou chaque format dit ce
 * qu'il vaut.
 *
 * Meme grammaire que les reglages - carte, ligne, texte d'aide dessous : c'est le meme genre d'objet, un
 * choix explique.
 */
@Composable
private fun ExportFormatDialog(dark: Boolean, onDismiss: () -> Unit, onPick: (geoJson: Boolean) -> Unit) {
    ProvideSettingsPalette(dark = dark) {
        val p = settingsPalette
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = p.screen,
            title = { Text(stringResource(R.string.action_export_layer), color = p.label) },
            text = {
                SettingsCard {
                    SetRow(stringResource(R.string.export_format_gpx), onClick = { onPick(false) }) {
                        RowIcon(Icons.Filled.KeyboardArrowRight, null)
                    }
                    Hint(stringResource(R.string.export_format_gpx_hint))
                    RowDivider()
                    SetRow(stringResource(R.string.export_format_geojson), onClick = { onPick(true) }) {
                        RowIcon(Icons.Filled.KeyboardArrowRight, null)
                    }
                    Hint(stringResource(R.string.export_format_geojson_hint))
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = p.accent) }
            },
        )
    }
}

@Composable
private fun DropIndicatorLine() {
    Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary))
}

/** Part de la hauteur d'écran que l'infobulle ne dépasse pas ; au-delà, ses propriétés défilent. */
private const val BubbleMaxHeightRatio = 0.6f

/** Propositions demandées au géocodeur. Plus que les 4 visibles : le défilement de la liste n'a de sens
 *  que s'il y a de quoi défiler, et le service facture le même aller-retour dans les deux cas. */
private const val GeocodeResultLimit = 10

/** Opacite du fond des boutons de controle. Deux crans au-dessus de l'echelle graphique (0,7) : elle ne
 *  porte qu'un trait et deux chiffres, la ou un bouton doit rester franchement lisible sur une orthophoto
 *  ou un fond satellite. */
private const val ControlButtonBgAlpha = 0.9f

/**
 * Fond des ornements poses sur la carte - echelle graphique et boutons de controle - en theme sombre.
 *
 * Clair, ils s'ecrivent en noir sur blanc ; sombre, les deux s'echangent, faute de quoi un pave blanc
 * troue un ecran par ailleurs sombre et eblouit de nuit, quand on s'en sert le plus.
 *
 * Gris tres fonce et non noir : un aplat franchement noir sur un fond de carte sombre ne se lit plus
 * comme un objet POSE dessus mais comme un trou dedans, et ses angles arrondis disparaissent.
 */
private val MapChromeDarkBg = Color(0xFF202124)

/**
 * Le dessin d'un bouton de carte ALLUME.
 *
 * Le bleu de la puce de position, et non l'accent du theme : celui-ci est un vert sombre, qui allume un
 * trait de 2 dp sans qu'on le remarque au-dessus d'une carte - or c'est le seul signe qui distingue un
 * bouton actif d'un bouton au repos. Le bleu du repere GPS, lui, ne se confond avec aucun fond
 * topographique, et l'ecran gagne au passage une couleur d'etat unique : ce qui est en marche est bleu.
 */
private val MapChromeActive = Color(GpsMarkerStyle.DOT.defaultColor.toColorInt())

/**
 * Cloche allumee ET en alerte : le rouge de la banniere du bas, pas le bleu des commandes en marche.
 *
 * C'est la seule entorse a la couleur d'etat unique, et elle se justifie : suivre une trace est un etat
 * comme un autre (bleu), s'en etre ecarte est un evenement, et les deux doivent se distinguer sur le meme
 * bouton. La cloche est d'ailleurs souvent le seul signe visible quand la banniere vient d'etre tue.
 */
private val OffTrackAlertColor = Color(0xFFB3261E)

/**
 * Rotation de l'ECRAN par rapport a l'orientation naturelle de l'appareil (cf. [azimuthDegrees]).
 *
 * Relue a chaque changement de configuration, seul signal dont dispose Compose pour dire qu'on vient de
 * tourner le telephone : le capteur, lui, parle toujours dans le repere de l'appareil, pas dans celui de
 * l'ecran, et la difference entre les deux est exactement ce quart de tour.
 */
@Composable
private fun rememberDisplayRotation(): Int {
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ctx.display!!.rotation
            else @Suppress("DEPRECATION")
            (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }.getOrDefault(Surface.ROTATION_0)
    }
}

/**
 * Cap du telephone, en degres depuis le nord magnetique, tire du vecteur de rotation du systeme.
 *
 * Le repere du capteur est celui de l'APPAREIL dans son orientation naturelle ; [displayRotation] le
 * ramene a celui de l'ecran tel qu'il est tenu, faute de quoi la fleche serait a un quart de tour de la
 * verite des qu'on passe en paysage.
 *
 * Le vecteur est tronque a ses quatre premieres composantes : certains appareils en publient cinq, que
 * getRotationMatrixFromVector refuse.
 */
private fun azimuthDegrees(rotationVector: FloatArray, displayRotation: Int): Float {
    val v = if (rotationVector.size > 4) rotationVector.copyOf(4) else rotationVector
    val m = FloatArray(9)
    SensorManager.getRotationMatrixFromVector(m, v)
    val (axisX, axisY) = when (displayRotation) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }
    val screen = FloatArray(9)
    SensorManager.remapCoordinateSystem(m, axisX, axisY, screen)
    val orientation = FloatArray(3)
    SensorManager.getOrientation(screen, orientation)
    return Math.toDegrees(orientation[0].toDouble()).toFloat()
}

/** Ecart entre deux caps, par le plus court des deux chemins : 359 et 1 sont a 2 degres l'un de l'autre. */
private fun angleGap(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return if (d > 180f) 360f - d else d
}

/** Le fond d'un ornement de carte selon le theme. */
private fun mapChromeBg(dark: Boolean): Color = if (dark) MapChromeDarkBg else Color.White

/** Ce qui se dessine dessus : l'inverse exact du fond, sans demi-teinte - ces objets sont petits et se
 *  lisent par-dessus n'importe quel fond de carte. */
private fun mapChromeFg(dark: Boolean): Color = if (dark) Color.White else Color.Black

/**
 * Fond d'un bouton pose sur la carte : un carre a peine adouci, resserre autour de l'icone.
 *
 * Dessine SOUS le contenu plutot que pose sur toute la surface : un IconButton mesure 48 dp pour la zone
 * tactile, l'icone n'en occupe que 24. Un fond plein donnerait une pastille deux fois plus large que ce
 * qu'elle habille, et se superposerait a l'ondulation que l'IconButton peint lui-meme.
 */
private fun Modifier.mapButtonBackground(color: Color, square: Dp): Modifier = drawBehind {
    // Le carre est CENTRE dans le bouton, et borne a sa taille : la zone tactile ne bouge pas, seul le
    // fond grandit ou se resserre en son milieu (cf. SettingsEntity.mapButtonSizeDp).
    val side = square.toPx().coerceAtMost(minOf(size.width, size.height))
    val r = ControlButtonRadius.toPx().coerceAtMost(side / 2)
    drawRoundRect(
        color = color,
        topLeft = Offset((size.width - side) / 2, (size.height - side) / 2),
        size = Size(side, side),
        cornerRadius = CornerRadius(r, r),
    )
}

/** Angles du fond : ceux du bouton d'itineraire de Google Maps, borne a la moitie du cote pour qu'un
 *  petit bouton s'arrondisse sans jamais depasser le cercle. */
private val ControlButtonRadius = 16.dp

/**
 * Bouton "i" de la legende des pentes, au bout de la ligne de titre du profil.
 *
 * La legende n'a plus de reglage : elle se demande la, sur le profil qu'elle explique, et se referme du
 * meme geste. Un reglage aurait demande d'aller le chercher dans un autre ecran pour lire une echelle de
 * couleurs qu'on ne consulte qu'une fois.
 *
 * Fond blanc a 60 % : le bouton flotte au-dessus du titre, qu'un nom long fait passer dessous.
 */
@Composable
private fun SlopeLegendButton(shown: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        // Plus de fond blanc : il servait a garder le "i" lisible quand il chevauchait le titre, ce qui
        // n'arrive plus - le titre lui reserve sa gouttiere (cf. SlopeLegendGutter).
        modifier.size(SlopeLegendButtonSize).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (shown) Icons.Filled.Info else Icons.Outlined.Info,
            stringResource(R.string.settings_profile_slope_legend),
            Modifier.size(17.dp), tint = Color(0xFF3F4A55),
        )
    }
}

/** Cote du "i" de la legende des pentes. */
private val SlopeLegendButtonSize = 22.dp

/** Largeur que le titre du profil laisse au "i" : le bouton, et l'air qui l'ecarte du texte. C'est cette
 *  gouttiere qui empeche un titre long de passer dessous. */
private val SlopeLegendGutter = SlopeLegendButtonSize + 4.dp

/** Air autour du titre du bandeau de profil : au-dessus comme au-dessous, dans tous les cas. */
private val ProfileTitleGap = 4.dp

/** Air entre les infos de la trace et le graphique quand la legende des pentes n'est pas affichee :
 *  c'est elle qui tenait cette place, et elle en prenait plus que six points. */
private val ProfileGraphGap = 12.dp

/** Ecart du bloc d'infos du point courant : le meme a droite de l'ecran qu'au-dessus du profil. */
private val CursorInfoGap = 4.dp

/*
 * Dessin du bouton GPS : son epingle, et le mot "GPS" dessous.
 *
 * Il porte DEUX elements la ou ses voisins n'en ont qu'un, et se dessinait donc plus petit qu'eux - une
 * epingle de 16 dp contre les 24 dp d'une icone ordinaire. Les deux ont grandi ensemble, en gardant leur
 * rapport : le mot doit rester lisible sans concurrencer l'epingle, qui porte le sens.
 *
 * La borne haute n'est pas le confort mais le CARRE DE FOND, dont le cote se regle a partir de 36 dp
 * (cf. MinMapButtonSizeDp) : a 20 dp d'epingle et 8 sp de mot, l'ensemble tient dans le plus petit carre
 * avec de l'air de chaque cote. Au-dela, le dessin toucherait le bord de son fond au premier cran du
 * curseur.
 */
private val GpsIconSize = 20.dp
private const val GpsLabelSp = 8f

/** Ecart entre deux boutons poses sur la carte. Le triple de ce qu'il etait : les fonds arrondis se
 *  touchaient presque, et la colonne se lisait comme un seul bloc. */
private val MapControlSpacing = 24.dp

/**
 * Bleu du disque de recentrage quand la position n'est plus au centre.
 *
 * Exactement celui du point de position pose sur la carte (cf. MapController.setUserLocation) : le bouton
 * reprend la teinte de ce qu'il vise, et les deux se repondent d'un bout a l'autre de l'ecran. Fixe et non
 * pris au theme, comme le point qu'il rappelle.
 */
private val RecenterDotColor = Color(0xFF4285F4)

/**
 * Diametre du disque repeint au centre de l'icone "centrer sur ma position".
 *
 * Le trace Material `my_location` porte ce disque a un rayon de 4 sur une grille de 24, soit 8 dp pour
 * une icone de 24 : un poil plus large pour couvrir son bord lisse, et toujours en deca de l'anneau qui
 * l'entoure (rayon 5, soit 10 dp).
 */
private val MyLocationDotSize = 9.dp

/** Part de la hauteur d'ecran que la bande du planificateur ne depasse jamais. */
private const val PlannerMaxHeightRatio = 0.6f

/** Zoom minimal garanti sur le lieu trouvé, un zoom plus serré étant conservé. À 12, la ville et ses
 *  abords tiennent à l'écran : de quoi situer l'épingle, sans plonger sur une adresse à la parcelle. */
private const val GeocodeMinZoom = 12.0

/** Teintes de l'avertissement "sans altimétrie" : figées, pour rester lisibles sur le fond blanc du
 *  panneau de profil quel que soit le thème. */
private val NoElevationBorder = Color(0xFFE8850C)
private val NoElevationFill = Color(0xFFFFF3E0)
private val NoElevationText = Color(0xFFB35309)

/** Avertissement affiché à la place du tracé quand la trace n'a aucune altitude : 80 % de la largeur et
 *  50 % de la hauteur de la zone de dessin, centré dedans. */
@Composable
private fun NoElevationBanner(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.5f)
                .background(NoElevationFill, RoundedCornerShape(8.dp))
                .border(1.dp, NoElevationBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.profile_no_elevation), color = NoElevationText,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

/**
 * Panneau de profil d'un itinéraire mesuré depuis l'infobulle d'un lieu.
 *
 * Reprend la forme du profil d'une trace - mêmes réglages d'apparence, même légende, même graphique - pour
 * qu'une pente s'y lise de la même façon. Trois différences, toutes tenant à la nature de l'objet : pas de
 * spinner (l'itinéraire est calculé avant que le bouton n'apparaisse), pas de zoom A/B (un parcours court,
 * d'un seul tenant), et une croix de fermeture, car rien sur la carte ne permettrait de le rouvrir.
 *
 * Aucun garde-fou sur l'altimétrie : le bouton qui ouvre ce panneau n'existe que si le moteur a rendu des
 * altitudes (cf. GeocodeBubble).
 */
@Composable
private fun RouteProfilePanel(
    track: ComputedTrack,
    title: String,
    settings: SettingsEntity?,
    imperial: Boolean,
    lineColor: Color,
    cursorX: Double?,
    lastLabelInsetPx: Float,
    onScrub: (Double) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp).navigationBarsPadding(),
    ) {
        // Meme mise en page que le bandeau d'une trace : le titre sur sa ligne (la croix a son bout),
        // les infos en colonnes sur toute la largeur en dessous.
        Row(
            Modifier.padding(vertical = ProfileTitleGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = (settings?.profTitleFont ?: 16).sp,
                fontWeight = if (settings?.profTitleBold != false) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(18.dp))
                }
            }
        }
        TrackInfoColumns(
            titleInfos(
                track.stats, settings?.titleInfos ?: "dist,asc,desc", imperial,
                remember(track.samples) { TrackMath.toblerSeconds(track.samples) },
            ),
            fontSp = settings?.profBarFont ?: 11,
            bold = settings?.profBarBold == true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (settings?.profileSlope != false && settings?.profileSlopeLegend == true) {
            SlopeLegend(track.stats.maxAbsSlope, settings?.profLegendFont ?: 9,
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                bold = settings?.profLegendBold == true)
        } else {
            Spacer(Modifier.height(ProfileGraphGap))
        }
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            ElevationProfile(
                samples = track.samples, stats = track.stats,
                grid = settings?.profileGrid ?: true,
                slope = settings?.profileSlope ?: true,
                lineColor = lineColor,
                axisFontSp = settings?.profAxisFont ?: 9,
                axisBold = settings?.profAxisBold == true,
                cursorX = cursorX, onScrub = onScrub,
                lastLabelInsetPx = lastLabelInsetPx,
                verticalScaleMPerCm = settings?.profileVerticalScaleMPerCm ?: 0,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            )
        }
    }
}

/*
 * Mesures du menu lateral, reprises telles quelles de la maquette (captures/trailog-drawer-styles.html).
 * Elles sont ici et non a l'appel : une ligne d'arbre est faite de cinq elements que trois composables se
 * partagent, et les voir cote a cote est le seul moyen de garder la grille droite.
 */
/*
 * Toutes ces mesures ont ete relevees d'un cran (~15 %) par rapport a la maquette : a l'echelle du dessin,
 * l'arbre se lisait juste, mais du bout du doigt il se visait mal. Elles montent ENSEMBLE - le rapport
 * entre le chevron, l'oeil, le nom et la poignee fait la ligne, pas leurs valeurs prises une a une.
 */
/** Retrait de chaque niveau d'imbrication. */
private val DrawerIndent = 17.dp
/** Marges d'une ligne : le retrait de depart, puis ce qui la separe de la suivante. */
private val DrawerRowPadH = 7.dp
private val DrawerRowPadV = 6.dp
/** Ecart entre deux elements d'une ligne (chevron, oeil, symbole, nom...). */
private val DrawerRowGap = 7.dp
/** Chevron, oeil, symbole : trois tailles voisines, pas une seule - l'oeil et le symbole portent la ligne,
 *  le chevron n'est qu'un accessoire de pliage. */
private val DrawerChevronSize = 16.dp
private val DrawerIconSize = 17.dp
/** Cible tactile posee autour de ces petites icones : ce qu'on peut prendre sans grossir le dessin. */
private val DrawerHitSize = 30.dp
/** Nom d'une couche, et compteur d'un dossier. */
private val DrawerNameSp = 13f
private val DrawerCountSp = 10.5f

/** Au-delà, la légende ne gagne plus en lisibilité : l'image ne ferait que s'étirer (500 px de large). */
private val LegendMaxWidth = 260.dp

/**
 * Légende du fond de plan affiché, dépliée depuis le bouton "info" auquel elle s'adosse : [anchor] est le
 * coin haut-gauche de ce bouton, en px fenêtre. L'image occupe donc au plus la place entre le bord gauche
 * de l'écran et le bouton, ce qui la garde entière quels que soient les autres boutons de la barre.
 * Un tap n'importe où ailleurs la referme, via un voile transparent posé sur la carte le temps qu'elle
 * s'affiche. Plusieurs légendes s'empilent : un composite peut afficher deux fonds qui en ont chacun une.
 */
@Composable
private fun BasemapLegend(legends: List<String>, visible: Boolean, anchor: IntOffset, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        if (visible) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })
        }
        AnimatedVisibility(
            visible = visible,
            // Depliement vers la gauche depuis le bouton : le mouvement dit d'ou vient l'image.
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
            modifier = Modifier.offset { IntOffset(0, anchor.y) }
                .width(with(density) { anchor.x.toDp() }),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Column(
                    Modifier.padding(start = 8.dp).widthIn(max = LegendMaxWidth)
                        .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(6.dp),
                ) {
                    legends.forEach { asset ->
                        AsyncImage(model = legendAssetModel(asset), contentDescription = null,
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Action de l'en-tete du menu : cible de 38 dp, dessin de 18 - un cran au-dessus de la maquette, comme
 *  le reste du tiroir. */
@Composable
private fun HeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
    showLabel: Boolean = false, active: Boolean = false, onClick: () -> Unit,
) {
    // Allume, seul le DESSIN change de couleur. Un aplat derriere lui - meme leger - lui donnait un poids
    // que ses voisins n'ont pas, et le bouton paraissait plus gros alors que sa cible fait les memes
    // 44 dp. C'est la regle des boutons de la carte, ou l'etat se lit a la couleur du trait.
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    if (!showLabel) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size(22.dp), tint = tint)
        }
        return
    }
    // Icone et libelle dans une meme pastille cliquable : le texte n'est pas une legende posee a cote du
    // bouton, il en fait partie, et un appui dessus vaut un appui sur l'icone.
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick)
            .padding(start = 11.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tint, maxLines = 1)
    }
}

/**
 * Une ligne de l'arbre du menu lateral : le retrait de son niveau, et ce que le drag lui fait.
 *
 * Le retrait est celui de la maquette (15 dp par niveau) et non l'indentation d'une liste ordinaire : cet
 * arbre descend a trois ou quatre niveaux sur un ecran de telephone, et 15 dp est ce qui reste lisible
 * sans manger la moitie de la largeur au dernier.
 */
@Composable
private fun DrawerRow(
    depth: Int, dragging: Boolean, offset: Float, hovered: Boolean,
    onPositioned: (Float) -> Unit, content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { onPositioned(it.positionInRoot().y) }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = offset; alpha = if (dragging) 0.85f else 1f }
            .background(if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .padding(
                start = DrawerRowPadH + DrawerIndent * depth, end = DrawerRowPadH,
                top = DrawerRowPadV, bottom = DrawerRowPadV,
            ),
        horizontalArrangement = Arrangement.spacedBy(DrawerRowGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Icone d'une ligne d'arbre : petite au dessin, large au doigt.
 *
 * Le dessin fait 15 dp comme dans la maquette, la zone tapee 26 : sans cet ecart, il faudrait choisir
 * entre une ligne haute de 48 dp et des cibles qu'on rate. La cible reste sous le minimum Material,
 * assume - une ligne d'arbre en aligne cinq, et l'arbre en empile une vingtaine.
 */
@Composable
private fun DrawerIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?, size: Dp = DrawerIconSize,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit,
) {
    Box(Modifier.size(DrawerHitSize).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(imageVector, contentDescription, Modifier.size(size), tint = tint)
    }
}

/** Meme icone, dessinee a partir d'un trace du projet (symboles de couche, cf. ic_layer_route). */
@Composable
private fun DrawerIcon(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String?, tint: Color, onClick: () -> Unit,
) {
    Box(Modifier.size(DrawerHitSize).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painter, contentDescription, Modifier.size(DrawerIconSize), tint = tint)
    }
}

/** Les deux prises de fin de ligne, poignee et menu, serrees l'une contre l'autre. */
@Composable
private fun RowEndActions(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Poignée : appui long -> drag de réordonnancement (avec animation/haptique gérées par la ligne). */
@Composable
private fun DragHandle(onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit) {
    // pointerInput(Unit) ne relance jamais son bloc : sans rememberUpdatedState, la coroutine de geste
    // resterait figée sur les callbacks de la toute première composition (hoverTarget alors toujours null).
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnEnd by rememberUpdatedState(onEnd)
    Icon(
        Icons.Filled.DragIndicator, stringResource(R.string.action_drag_to_move),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.size(DrawerIconSize).pointerInput(Unit) {
            var total = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { total = 0f; currentOnStart() },
                onDrag = { change, amount -> change.consume(); total += amount.y; currentOnDrag(total) },
                onDragEnd = { currentOnEnd() }, onDragCancel = { currentOnEnd() },
            )
        },
    )
}

/** 3 points : menu contextuel (appui simple). */
@Composable
private fun RowMenu(
    onRename: () -> Unit, onMove: () -> Unit, onNewSub: (() -> Unit)?, onDelete: () -> Unit, onZoom: () -> Unit,
    // Propre au dossier, et seulement s'il porte des couches : une couche a deja sa pastille de couleur
    // dans sa ligne, et un dossier vide n'a rien a colorer - l'entree disparait plutot que de ne rien faire.
    onColor: (() -> Unit)? = null,
    layer: LayerEntity? = null, layerActions: LayerActions? = null,
    // Propre au dossier : le total de ce qu'il contient, sous-dossiers compris.
    onStats: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(DrawerHitSize).clickable { open = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more), Modifier.size(DrawerIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // Un dossier cadre TOUTES ses couches, pas une : le libelle le dit au pluriel.
            DropdownMenuItem(
                text = {
                    Text(stringResource(
                        if (onNewSub != null) R.string.action_zoom_to_layers else R.string.action_zoom_to_layer
                    ))
                },
                onClick = { open = false; onZoom() },
            )
            if (onStats != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_folder_stats)) },
                    onClick = { open = false; onStats() })
            }
            if (onColor != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_color_layers)) },
                    onClick = { open = false; onColor() })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { open = false; onRename() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_move)) }, onClick = { open = false; onMove() })
            if (onNewSub != null) DropdownMenuItem(text = { Text(stringResource(R.string.action_new_subfolder)) }, onClick = { open = false; onNewSub() })
            // Les SORTIES d'une couche seulement. Les retouches, elles, ont quitte ce menu pour la barre
            // d'outils de la carte : elles agissent sur un segment, parfois sur deux, et designer un
            // segment se fait du doigt sur la carte - pas dans le menu d'une ligne d'arborescence.
            if (layer != null && layerActions != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_export_layer)) },
                    onClick = { open = false; layerActions.onExport(layer) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_share)) },
                    onClick = { open = false; layerActions.onShare(layer) })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { open = false; onDelete() })
        }
    }
}

/**
 * Ce qu'on peut faire d'une couche depuis son menu, au-dela de ce qu'un dossier sait faire aussi.
 *
 * Un porteur plutot que cinq lambdas passees de main en main : l'arborescence les traverse sur toute sa
 * profondeur, et chacune aurait suivi les autres a chaque appel. Toutes visent la couche entiere, d'ou le
 * parametre commun - la ligne qui les declenche sait laquelle, pas ce qu'il faut en faire.
 */
class LayerActions(
    val onExport: (LayerEntity) -> Unit,
    val onShare: (LayerEntity) -> Unit,
)

@SuppressLint("DefaultLocale")
@Composable
private fun ScaleBar(
    controller: MapController, tick: Int, maxWidthPx: Float,
    bg: Color, fg: Color, modifier: Modifier = Modifier,
) {
    if (maxWidthPx <= 0f) return
    val cam = remember(tick) { controller.cameraState() }
    val mpp = remember(tick) { cam?.let { controller.metersPerPixel(it.first) } ?: 0.0 }
    if (mpp <= 0.0) return
    val nice = niceDistance(maxWidthPx * mpp)
    val barPx = (nice / mpp).toFloat()
    val density = LocalDensity.current
    val barDp = with(density) { barPx.toDp() }
    val fontSizeSp = 11f
    val tickHeightDp = with(density) { (fontSizeSp * 0.5f * 1.5f).sp.toDp() }
    val label = if (nice >= 1000) {
        val km = nice / 1000.0; (if (km % 1.0 == 0.0) "${km.toInt()}" else String.format("%.1f", km)) + " km"
    } else "${nice.toInt()} m"
    val strokeColor = fg.copy(alpha = 0.7f)
    val bgAlpha = 0.7f
    Column(
        // Padding du haut supprimé (le fond ne doit pas déborder au-dessus du texte) ; celui du bas
        // reste pour ne pas coller le trait horizontal au bord de la carte.
        modifier.background(bg.copy(alpha = bgAlpha)).padding(start = 2.dp, end = 2.dp, top = 0.dp, bottom = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // lineHeight réduit sous fontSize : resserre vraiment la boîte du texte (hauteur réservée), au lieu
        // de juste déplacer son rendu. Décalé vers le bas de la moitié de la hauteur des ticks pour que le
        // texte descende plus bas que leur extrémité haute (sans quoi il reste entièrement au-dessus).
        Text(
            label, fontSize = fontSizeSp.sp, lineHeight = (fontSizeSp * 0.8f).sp, color = fg,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            modifier = Modifier.offset(y = tickHeightDp / 2),
        )
        Box(Modifier.width(barDp).height(tickHeightDp)) {
            // trait horizontal en bas, traits verticaux qui ne remontent qu'au-dessus (jamais sous la ligne)
            Box(Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter).background(strokeColor))
            Box(Modifier.width(2.dp).height(tickHeightDp).align(Alignment.BottomStart).background(strokeColor))
            Box(Modifier.width(2.dp).height(tickHeightDp).align(Alignment.BottomEnd).background(strokeColor))
        }
    }
}

private fun niceDistance(max: Double): Double {
    if (max <= 0) return 1.0
    val pow = 10.0.pow(floor(log10(max)))
    return when { max / pow >= 5 -> 5 * pow; max / pow >= 2 -> 2 * pow; else -> pow }
}

/** Les couches que porte un dossier, sous-dossiers compris : ce sur quoi portent ses actions (oeil,
 *  couleur, cadrage). Cote base, cf. MainViewModel.descendantFolderIds. */
internal fun layersUnder(folderId: Long, folders: List<FolderEntity>, layers: List<LayerEntity>): List<LayerEntity> {
    val ids = HashSet<Long>(); val stack = ArrayDeque<Long>(); stack.add(folderId)
    while (stack.isNotEmpty()) { val f = stack.removeLast(); if (ids.add(f)) folders.filter { it.parentId == f }.forEach { stack.add(it.id) } }
    return layers.filter { it.folderId in ids }
}

private fun folderBbox(folderId: Long, folders: List<FolderEntity>, layers: List<LayerEntity>): DoubleArray? {
    val ls = layersUnder(folderId, folders, layers)
    val w = ls.map { it.west }.filter { it != 0.0 }.minOrNull() ?: return null
    val s = ls.map { it.south }.filter { it != 0.0 }.minOrNull() ?: return null
    val e = ls.map { it.east }.filter { it != 0.0 }.maxOrNull() ?: return null
    val n = ls.map { it.north }.filter { it != 0.0 }.maxOrNull() ?: return null
    return doubleArrayOf(w, s, e, n)
}

/** Sélecteur de fichier qui démarre la navigation dans un dossier choisi (pas celui des MBTiles). */
private class PickFile : ActivityResultContract<Uri?, List<Uri>>() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            // Filtre par type MIME (les dossiers restent toujours visibles/navigables dans le sélecteur système).
            // KML a un type MIME officiel IANA (application/vnd.google-earth.kml+xml) ; GPX n'en a pas et
            // circule sous plusieurs conventions (gpx+xml, x-gpx+xml) selon les outils/fournisseurs. Beaucoup
            // de fournisseurs de stockage retombent sur application/octet-stream pour ces extensions non
            // reconnues : on l'inclut donc en repli, ce qui peut laisser passer d'autres fichiers à extension
            // non reconnue selon le fournisseur (limite de l'API Android, pas de filtrage par extension).
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/gpx+xml",
                "application/x-gpx+xml",
                "application/vnd.google-earth.kml+xml",
                "application/vnd.google-earth.kmz",
                "application/zip",
                "application/geo+json",
                "application/json",
                "text/xml",
                "application/xml",
                "application/octet-stream",
            ))
            if (input != null) {
                val initial = runCatching {
                    DocumentsContract.buildDocumentUriUsingTree(input, DocumentsContract.getTreeDocumentId(input))
                }.getOrNull() ?: input
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
            }
        }
    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        val clip = intent?.clipData
        if (clip != null) return (0 until clip.itemCount).map { clip.getItemAt(it).uri }
        return intent?.data?.let { listOf(it) } ?: emptyList()
    }
}
