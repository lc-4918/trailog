package fr.lc4918.trailog.ui.routes

import android.annotation.SuppressLint
import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.DefaultGpsMarkerSizeDp
import fr.lc4918.trailog.data.db.DefaultOffTrackAlertM
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.MinMapButtonSizeDp
import fr.lc4918.trailog.data.db.offTrackAlertVisible
import fr.lc4918.trailog.data.db.routePrefs
import fr.lc4918.trailog.data.db.routeUrl
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.domain.model.BubblePosition
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.GpsMarkerStyle
import fr.lc4918.trailog.domain.model.PlannerHistory
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.domain.model.RouteEngine
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.geocode.GeocodePlace
import fr.lc4918.trailog.geocode.NetworkStatus
import fr.lc4918.trailog.geocode.Photon
import fr.lc4918.trailog.location.LocationHub
import fr.lc4918.trailog.location.TrackWatch
import fr.lc4918.trailog.map.compositeIdFromBasemapId
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.map.offline.OfflinePhase
import fr.lc4918.trailog.net.ServiceUrl
import fr.lc4918.trailog.routing.GpxWriter
import fr.lc4918.trailog.routing.Router
import fr.lc4918.trailog.ui.alert.OffTrackAlertBar
import fr.lc4918.trailog.ui.alert.OffTrackAlertState
import fr.lc4918.trailog.ui.alert.TrackChooserDialog
import fr.lc4918.trailog.ui.components.BasemapControlPanel
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.MapLibreSurface
import fr.lc4918.trailog.ui.components.MapPromptBar
import fr.lc4918.trailog.ui.components.MapSurface
import fr.lc4918.trailog.ui.edit.CutTarget
import fr.lc4918.trailog.ui.edit.EditTool
import fr.lc4918.trailog.ui.edit.SegmentRef
import fr.lc4918.trailog.ui.edit.TrackEditState
import fr.lc4918.trailog.ui.geocode.GeocodeBubble
import fr.lc4918.trailog.ui.geocode.GeocodeSearchBar
import fr.lc4918.trailog.ui.geocode.GeocodeSearchState
import fr.lc4918.trailog.ui.location.KeepScreenOnEffect
import fr.lc4918.trailog.ui.location.LocationNoticeBar
import fr.lc4918.trailog.ui.location.rememberLocationControls
import fr.lc4918.trailog.ui.mappoint.AddressState
import fr.lc4918.trailog.ui.mappoint.MapPointBubble
import fr.lc4918.trailog.ui.mappoint.MapPointEffects
import fr.lc4918.trailog.ui.mappoint.MapPointState
import fr.lc4918.trailog.ui.measure.MeasureBubbleLayer
import fr.lc4918.trailog.ui.measure.TrackMeasureState
import fr.lc4918.trailog.ui.offline.BboxDrawingOverlay
import fr.lc4918.trailog.ui.offline.OfflineDownloadCard
import fr.lc4918.trailog.ui.offline.OfflineDownloadConfigScreen
import fr.lc4918.trailog.ui.offline.OfflineFlowState
import fr.lc4918.trailog.ui.offline.OfflineMinimizedButton
import fr.lc4918.trailog.ui.planner.GeocodingParams
import fr.lc4918.trailog.ui.planner.PlannerEffects
import fr.lc4918.trailog.ui.planner.RoutePlannerBand
import fr.lc4918.trailog.ui.planner.RoutePlannerState
import fr.lc4918.trailog.ui.planner.StepTarget
import fr.lc4918.trailog.ui.planner.defaultRouteName
import fr.lc4918.trailog.ui.poi.PoiBubble
import fr.lc4918.trailog.ui.poi.PoiEffects
import fr.lc4918.trailog.ui.poi.PoiLoading
import fr.lc4918.trailog.ui.poi.PoiState
import fr.lc4918.trailog.ui.poi.poiCategoryLabelRes
import fr.lc4918.trailog.ui.poi.poiGroupColor
import fr.lc4918.trailog.ui.poi.poiIcon
import fr.lc4918.trailog.ui.points.AnchoredBubble
import fr.lc4918.trailog.ui.points.BubbleGeometry
import fr.lc4918.trailog.ui.points.InfoBubble
import fr.lc4918.trailog.ui.points.InfoBubbleLoading
import fr.lc4918.trailog.ui.points.PropertyEditor
import fr.lc4918.trailog.ui.settings.routingProfileLabel
import fr.lc4918.trailog.ui.theme.isDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MainScreen(
    onSettings: () -> Unit,
    settingsOpen: Boolean = false,
    vm: MainViewModel = viewModel(),
    // La carte, en parametre pour qu'un test puisse composer l'ecran sans les natifs de MapLibre (cf.
    // MapSurface). En production, c'est la vraie : l'appel de AppRoot ne la nomme pas.
    map: MapSurface = MapLibreSurface,
) {
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
    val offline = remember { OfflineFlowState() }
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

    /*
     * ---------- position GPS ----------
     *
     * Allumer le suivi et l'eteindre, demander une position pour une seule question, savoir si la
     * localisation est allumee dans le telephone : tout cela vit dans [LocationControls], avec les trois
     * autorisations et le recepteur systeme qui vont avec.
     *
     * L'ecran n'en garde que ce qu'il AFFICHE - le symbole du repere, ci-dessous - et ce qu'il en lit.
     */
    val location = rememberLocationControls(controller, settings?.showGpsButton)
    // Ecran maintenu allume tant que le suivi tourne, si le reglage le demande (cf. KeepScreenOnEffect).
    // L'annonce d'un arret subi du suivi : voir la banniere posee avec celle de l'alerte d'eloignement.
    val stopNotice by LocationHub.stopNotice.collectAsState()
    KeepScreenOnEffect(settings?.keepScreenOn == true && location.gpsActive)

    // Symbole du repère de position et sa couleur : celle réglée, sinon celle propre au symbole (le bleu de
    // la puce, le rouge des flèches et de la croix) - une couleur vide n'est pas un choix, c'est l'absence
    // de choix, et elle doit suivre le symbole quand on en change.
    val gpsMarker = GpsMarkerStyle.of(settings?.gpsMarkerStyle)
    val gpsMarkerColorReglee = settings?.gpsMarkerColor?.takeIf { it.isNotBlank() } ?: gpsMarker.defaultColor
    /**
     * Le repere passe au GRIS quand il ne dit plus la verite.
     *
     * Un repere fige est visuellement identique a un repere juste : on regarde un point qui affirme ou
     * l'on est, et il a raison depuis dix minutes. La banniere le dit en toutes lettres, mais elle
     * s'adresse a qui lit le bas de l'ecran ; la couleur s'adresse a qui regarde la carte, c'est-a-dire a
     * tout le monde. C'est la seule teinte de l'application qui dise un doute, et elle vaut pour tous les
     * symboles de repere - le reglage de couleur reprend la main des que le capteur repond.
     */
    val gpsMarkerColor = if (location.positionStale) GpsStaleColor else gpsMarkerColorReglee
    val gpsMarkerSizeDp = (settings?.gpsMarkerSizeDp ?: DefaultGpsMarkerSizeDp).toFloat()

    // Orientation du telephone, quand le symbole choisi en porte une (cf. HeadingEffect).
    HeadingEffect(location.gpsActive && gpsMarker.oriented, location.lastUserLocation) { controller.setUserHeading(it) }

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
        if (location.gpsActive) alert.openChooser() else showAlertNeedsGpsDialog = true
    }

    LaunchedEffect(location.gpsActive, alertChooserPending) {
        if (alertChooserPending && location.gpsActive) { alertChooserPending = false; alert.openChooser() }
    }
    // Recherche des traces les plus proches : relancée tant que le choix est ouvert et sans réponse, ce qui
    // couvre le cas du capteur allumé mais pas encore fixé - la liste arrive avec la première position.
    LaunchedEffect(alert.chooserOpen, alert.candidates, location.lastUserLocation) {
        if (!alert.chooserOpen || alert.candidates != null) return@LaunchedEffect
        val (la, lo) = location.lastUserLocation ?: return@LaunchedEffect
        vm.nearestTracks(la, lo) { alert.candidates = it }
    }
    /*
     * L'ecart et le son ne sont plus calcules ici mais dans le service (cf. LocationService.watchTrack) :
     * l'ecran eteint, la composition s'arrete, et une alerte qui ne se declenche que sous les yeux de
     * celui qu'elle doit prevenir n'alerte personne. L'ecran n'en lit que le resultat.
     */
    val followed by TrackWatch.followed.collectAsState()
    val alerting by TrackWatch.alerting.collectAsState()
    val silenced by TrackWatch.silenced.collectAsState()
    val awayM by TrackWatch.awayM.collectAsState()
    val alertBanner = alerting && !silenced
    // Le réglage éteint, ou le capteur coupé : plus rien à suivre. La liste ouverte se referme avec.
    LaunchedEffect(alertEnabled, location.gpsActive) {
        if (!alertEnabled || !location.gpsActive) {
            TrackWatch.stop()
            alert.closeChooser()
            alertChooserPending = false
            showAlertNeedsGpsDialog = false
        }
    }
    // Couche supprimée ou masquée en cours de suivi : elle n'est plus sur la carte, on ne la suit plus.
    LaunchedEffect(layers, followed) {
        val suivie = followed ?: return@LaunchedEffect
        if (layers.none { it.id == suivie.layerId && it.visible }) TrackWatch.stop()
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

    MapPointEffects(
        state = mapPoint,
        geocodingBase = geocodingBase,
        routeEngine = routeEngine,
        routingUrl = routingUrl,
        routingProfile = routingProfile,
        prefs = measurePrefs,
        smoothingM = profileSmoothingM,
        currentPosition = { location.currentPosition() },
        onPlaceFound = { lieu -> vm.rememberPlannerPlace(lieu) },
    )

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
    PlannerEffects(
        state = planner,
        controller = controller,
        routeEngine = routeEngine,
        routingUrl = routingUrl,
        // Les préférences suivent la discipline choisie dans la BANDE, non celle des réglages : le
        // planificateur change de discipline sans quitter la carte.
        prefs = settings?.routePrefs(planner.profile) ?: RoutingPrefs.defaultFor(planner.profile),
        smoothingM = profileSmoothingM,
        slopeTint = settings?.profileSlope != false,
        styleTick = styleTick,
        enabled = settings?.routePlannerEnabled,
        topPaddingPx = statusBarTopPx,
        bandHeightPx = plannerBandHeightPx,
        routeTracks = mapPoint.routeTracks,
        routeRevision = mapPoint.measureRevision,
        currentPosition = { location.currentPosition() },
    )

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
        else location.withLocationPermission { mapPoint.requestDistanceFromPosition() }
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
            // Un geste rend la carte à son propriétaire : le suivi de position se tait le temps du silence
            // (cf. MapFollow). Ce rappel ne se déclenche que sur un geste humain - centerOn et fitTo ne le
            // font pas -, sans quoi le suivi s'interdirait lui-même à son premier recentrage.
            location.noteUserGesture()
        }
        // Appui long sur un endroit quelconque : le contrôleur a déjà écarté les traces, les marqueurs et
        // les modes de saisie exclusifs (cf. handleLongPress), il ne reste ici qu'à ouvrir le point.
        controller.onLongPressEmpty = { lon, lat -> mapPoint.open(lon, lat) }
    }

    // Modes de saisie exclusifs (tracé de la bounding box hors-ligne, choix des points de mesure, choix du
    // point de référence d'une distance) : tout tap leur revient, y compris sur une trace ou un marqueur,
    // qui n'ouvrent alors ni profil ni infobulle. Hors de ces modes, la sélection habituelle reprend.
    //
    // Ils ne sont jamais actifs ensemble (le tracé de bbox part du menu latéral, qui ferme la carte ; les
    // deux autres partent d'une barre ou d'une infobulle que l'autre a fait disparaître), mais l'ordre
    // reste explicite : celui qui occupe déjà l'écran garde les taps.
    LaunchedEffect(controller, offline.drawingActive, measure.picking, mapPoint.pickingPoint, edit.awaitingTap) {
        when {
            offline.drawingActive -> controller.onRawTap = { lon, lat ->
                if (offline.bboxPoints.size < 2) offline.bboxPoints = offline.bboxPoints + (lon to lat)
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
    val importFlow = rememberImportFlow(
        importDir = settings?.importDir,
        onImportLayer = { bytes, name, folderId -> vm.importLayer(bytes, name, folderId) },
        // Un import venu d'ailleurs ouvre le menu : sa couche arrive dans un dossier que rien a l'ecran ne
        // montre, et sans cela l'application ne repondrait que par le silence. Sa fermeture cadre ensuite la
        // carte sur ce qui a ete importe, comme apres n'importe quel import.
        onFilesEntrusted = { scope.launch { drawerState.open() } },
    )

    // import d'image pour un champ IMAGE d'infobulle (PropertyEditor) : callback enregistré au moment du tap
    var pendingImageCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u -> pendingImageCallback?.let { cb -> vm.importFeatureImage(u, cb) } }
    }

    val density = LocalDensity.current
    val markerPx = with(density) { (settings?.markerSize ?: 36).dp.toPx() }
    MapOverlayEffects(
        controller = controller,
        styleTick = styleTick,
        markerPx = markerPx,
        renderLayers = renderLayers,
        bboxPoints = offline.bboxPoints,
        geo = geo,
        measure = measure,
        mapPoint = mapPoint,
        gpsMarker = gpsMarker,
        gpsMarkerColor = gpsMarkerColor,
        gpsMarkerSizeDp = gpsMarkerSizeDp,
    )
    ProfileCursorEffects(
        controller = controller,
        cursor = cursor,
        computed = computed,
        profileZoom = profileZoom,
    )

    // `by` et non `=` : ce drapeau est relu a chaque arret de la camera, depuis un rappel pose une seule
    // fois (cf. rememberCameraPlacement).
    val positioned by rememberCameraPlacement(
        controller = controller,
        styleTick = styleTick,
        settings = settings,
        layers = layers,
        renderLayers = renderLayers,
        providers = providers,
    )

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
    PoiEffects(
        state = poi,
        controller = controller,
        repo = vm.poiRepository,
        enabled = settings?.poiEnabled,
        osmComplement = settings?.poiOsmComplement != false,
        filters = remember(settings?.poiHiddenCategories, settings?.poiBikeGroups) {
            PoiFilters.of(settings?.poiHiddenCategories, settings?.poiBikeGroups)
        },
        idleTick = idleTick,
        markerPx = markerPx,
        styleTick = styleTick,
    )
    // Un point d'interet devient une etape comme un lieu cherche : meme type, donc meme chemin dans le
    // planificateur, et rien a dupliquer.
    fun placeOf(p: fr.lc4918.trailog.poi.Poi) = GeocodePlace(
        // Le nom de la categorie a defaut de nom propre, comme dans l'infobulle : un lieu d'OpenStreetMap
        // en est souvent depourvu, et une etape sans libelle ne se relit pas dans l'historique.
        listOfNotNull(p.label.ifBlank { ctx.getString(poiCategoryLabelRes(p.category)) }, p.city),
        p.lon, p.lat,
    )
    /*
     * Un point d'interet CONSULTE alimente l'historique du planificateur (cf. PlannerHistory), sans
     * attendre qu'on en fasse une etape : ouvrir son infobulle, c'est deja s'y interesser, et les trois
     * boutons de la bulle ne sont pas le seul chemin vers un trajet - on regarde d'abord, on compose
     * ensuite, parfois bien plus tard.
     *
     * Sur l'uuid et non sur le POI : c'est lui qui identifie le lieu, et deux chargements successifs de la
     * couche rendent des objets distincts pour le meme endroit, qui relanceraient l'effet pour rien.
     */
    LaunchedEffect(poi.selected?.uuid) {
        poi.selected?.let { vm.rememberPlannerPlace(placeOf(it)) }
    }
    // Ouvre le planificateur pour y recevoir un point, en fermant ce qui lui prendrait la place.
    fun ouvrePlanificateur() {
        if (planner.open) return
        vm.closeProfile()
        planner.openPlanner()
        planner.chooseProfile(RoutingProfile.of(settings?.routingProfile))
    }
    // Le planificateur refuse au-dela de 25 etapes : le dire, plutot que de laisser un tap sans effet.
    var plannerFullMessage by remember { mutableStateOf(false) }
    /*
     * Le point d'un appui long, en lieu d'etape.
     *
     * Son adresse quand on la connait, ses COORDONNEES sinon : un point au milieu d'un bois est une etape
     * parfaitement legitime - c'est peut-etre le depart du sentier - et refuser d'en faire une parce que le
     * geocodeur n'a pas de nom a lui donner reviendrait a n'accepter que les endroits qui ont une adresse.
     *
     * Point decimal impose : la virgule d'une locale francaise separerait a la fois les decimales et les
     * deux valeurs, et donnerait "44,56, 6,08" (cf. Photon.parse, meme repli).
     */
    fun placeOfPoint(): GeocodePlace {
        val (lon, lat) = mapPoint.point ?: (0.0 to 0.0)
        val adresse = (mapPoint.address as? AddressState.Done)?.lines
        return GeocodePlace(
            adresse ?: listOf("%.5f, %.5f".format(java.util.Locale.US, lat, lon)), lon, lat,
        )
    }

    /*
     * La carte suit le porteur : elle se recentre à chaque position reçue, et se tait cinq secondes après
     * chaque geste (cf. MapFollow, qui porte les règles et leurs raisons).
     *
     * L'effet se relance sur la position ET sur l'heure du dernier geste, et c'est ce qui le fait marcher
     * dans les deux sens : une position pendant le silence attend ce qu'il en reste, et un geste annule le
     * recentrage en cours de préparation pour repartir sur cinq secondes pleines. Le retour a donc lieu
     * même immobile, l'attente du geste arrivant à son terme sans qu'aucune position nouvelle ne l'y aide.
     *
     * Placé ici, et non auprès des rappels de caméra : le suivi doit connaître les infobulles, dont celle
     * des points d'intérêt, qui n'existent qu'à partir de cette ligne.
     */
    // Une infobulle est accrochée à un endroit précis de la carte : un recentrage l'emporterait hors de
    // l'écran, avec son épingle, au milieu de la lecture. Les quatre comptent - waypoint (son éditeur de
    // propriétés compris, qui garde le marqueur sélectionné), point d'intérêt, lieu trouvé, point d'un
    // appui long. Ce dernier compte tant qu'il est POSÉ, même pendant le choix d'un point de référence,
    // qui masque l'infobulle sans clore ce qui se joue.
    val bubbleOpen = selectedMarkerId != null || poi.selected != null || geo.place != null ||
        mapPoint.point != null
    // La fermeture vaut geste : les cinq secondes de silence repartent de là, sans quoi la carte sauterait
    // sur la position à l'instant même où l'on referme l'infobulle.
    //
    // Relevée dans un onDispose plutôt que dans un effet voisin : Compose délivre les oublis AVANT les
    // lancements d'une même passe, si bien que l'heure est à jour quand l'effet du suivi, relancé par cette
    // même fermeture, va la lire. Deux effets côte à côte n'auraient tenu que par l'ordre de déclaration.
    if (bubbleOpen) {
        DisposableEffect(Unit) {
            onDispose { location.noteUserGesture() }
        }
    }
    val followsPosition = MapFollow.follows(
        enabled = settings?.mapFollowPosition != false,
        gpsActive = location.gpsActive,
        plannerOpen = planner.open,
        layerOpen = activeLayerId != null,
        bubbleOpen = bubbleOpen,
    )
    LaunchedEffect(followsPosition, location.lastUserLocation, location.lastUserGestureAt) {
        if (!followsPosition) return@LaunchedEffect
        val (la, lo) = location.lastUserLocation ?: return@LaunchedEffect
        val wait = MapFollow.waitMs(SystemClock.elapsedRealtime(), location.lastUserGestureAt)
        if (wait > 0L) delay(wait)
        controller.centerOn(la, lo)
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
    BackHandler(enabled = offline.drawingActive) { offline.cancelDrawing() }
    BackHandler(enabled = offline.configBbox != null) { offline.closeFlow() }
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
            // (cf. DrawerContent), pas la liste - l'inverse noierait les lignes dans un aplat.
            ModalDrawerSheet(Modifier.fillMaxWidth(), drawerContainerColor = MaterialTheme.colorScheme.surface) {
                DrawerContent(
                    folders = folders, layers = layers, settings = settings, vm = vm,
                    open = drawerState.isOpen,
                    onSettings = { scope.launch { drawerState.snapTo(DrawerValue.Closed) }; onSettings() },
                    onClose = { scope.launch { drawerState.close() } },
                    onImport = { importFlow.askFolder() },
                    showOfflineButton = offlineButtonVisible,
                    onDownloadOffline = {
                        scope.launch { drawerState.close() }
                        offline.bboxPoints = emptyList()
                        offline.extentChoice = true
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
                map.Render(
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
                        /*
                         * `!= false` et non `== true` : tant que les reglages ne sont pas revenus de la
                         * base, ils valent null, et `== true` faisait alors disparaitre le bouton.
                         *
                         * Ce n'est pas une precaution theorique. Un testeur a decrit exactement cela : plus
                         * de repere, et "le bouton a disparu". Le processus tue en cours de sortie, l'ecran
                         * rallume, l'activite recreee - et la carte ne portait plus QUE le burger, le temps
                         * que Room rende une ligne. Trois lectures traitaient l'inconnu comme un "non" alors
                         * que le defaut du reglage est "oui" ; tout le reste de ce fichier lit deja son
                         * defaut (cf. showScale plus bas). C'etaient les trois seules.
                         */
                        if (settings?.showGpsButton != false) {
                            IconButton(
                                onClick = { location.onGpsButtonTap() },
                                // Le fond ne change pas avec l'etat : allume, c'est le DESSIN qui passe au
                                // bleu, comme le bouton de retouche. Le bouton portait auparavant un aplat
                                // bleu plein, seul de son espece dans la colonne - il se lisait comme un
                                // objet d'une autre famille, la ou tous les autres gardent le fond commun
                                // des ornements de carte.
                                modifier = controlBg,
                            ) {
                                val gpsTint = if (location.gpsActive) MapChromeActive else chromeFg
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
                                // Un lieu cherche par son nom alimente l'historique du planificateur : le
                                // chercher est deja dire qu'il nous interesse, et le retaper demain dans un
                                // champ d'etape serait refaire le meme travail (cf. PlannerHistory).
                                vm.rememberPlannerPlace(place)
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
                    // `!= false` : le defaut du reglage est "oui", et un reglage non encore charge ne doit
                    // pas se lire comme un "non" (cf. le bouton GPS).
                    if (settings?.showBasemapControlButton != false) {
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
                        offline.drawingActive -> offline.barHeightPx
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
                // Geometrie commune aux quatre infobulles : la barre de statut a degager, l'air qu'elles
                // gardent au bord de l'ecran, et l'ecart qui les separe du point designe.
                val bubbleGeom = BubbleGeometry(
                    topInset = WindowInsets.statusBars.getTop(density),
                    margin = with(density) { 8.dp.roundToPx() },
                    gap = with(density) { 10.dp.roundToPx() },
                    markerHeight = markerPx.toInt(),
                )
                val bubblePos = BubblePosition.of(settings?.bubblePosition)
                val panMap: (Int, Int) -> Unit = { x, y -> controller.panByScreen(x.toFloat(), y.toFloat()) }

                val off = bubbleOffset
                if (off != null && selectedMarkerId != null && !editing) {
                    val maxH = constraints.maxHeight
                    // Hauteur max de l'infobulle : elle tient sous la barre de statut (avec marges) sans
                    // jamais couvrir plus de 60 % de l'écran, pour laisser voir la carte autour.
                    val maxBubbleHeightDp = with(density) {
                        minOf(maxH - bubbleGeom.topInset - 2 * bubbleGeom.margin,
                            (maxH * BubbleMaxHeightRatio).toInt()).toDp()
                    }
                    AnchoredBubble(
                        key = selectedMarkerId,
                        publish = selectedFeature != null,
                        panAllowed = bubblePos != BubblePosition.AUTO,
                        onPan = panMap,
                        placement = { bw, bh, vw, vh -> bubbleGeom.at(bubblePos, off.x, off.y, bw, bh, vw, vh) },
                    ) {
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
                    }
                }
                /*
                 * Deux mots discrets sous les commandes du haut, quand la couche est allumee mais qu'elle
                 * ne peut rien montrer : trop dezoome, ou pas de reseau et rien que le cache. Sans eux, la
                 * carte reste simplement vide, et l'on croit la couche cassee.
                 */
                if (poi.visible && (poi.tooFar || poi.needsNetwork || poi.fromCache || poi.partial)) {
                    /*
                     * Le message du zoom se TAPE, et zoome. Les deux autres ne sont que des constats -
                     * pas de reseau, points du cache - que rien ni personne ne leve d'un doigt, et les
                     * rendre tapables promettrait une action qui n'existe pas.
                     *
                     * Une consigne qu'on peut executer soi-meme est une consigne de trop : "Zoomez pour
                     * voir les points d'interet" dit exactement le geste que ce tap fait a notre place.
                     */
                    val zoomable = poi.tooFar
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        // Couleur de contenu imposee : un fond translucide n'est plus l'une des couleurs du
                        // theme, et contentColorFor n'y reconnait donc rien. Sans elle le texte hérite du
                        // LocalContentColor ambiant, dont le defaut est le noir - illisible sur le fond
                        // sombre de ce meme bandeau en theme sombre (cf. GeocodeSearchBar, meme remede).
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.TopCenter)
                            .padding(top = with(density) { (topControlsHeightPx + 16).toDp() })
                            .then(
                                if (!zoomable) Modifier
                                else Modifier.clickable(role = Role.Button) {
                                    // Le zoom minimum qui charge, pas un cran de plus : c'est le plus grand
                                    // territoire que le service accepte de peupler, donc celui qui montre le
                                    // plus de lieux d'un coup (cf. PoiLoading.MIN_ZOOM).
                                    //
                                    // Autour du centre courant, qu'on ne deplace pas : c'est la zone qu'on
                                    // regarde qu'on veut voir peuplee, et une carte qui saute ailleurs au
                                    // moment ou elle se remplit ferait perdre l'endroit qu'on tenait.
                                    //
                                    // Rien de plus a declencher : la camera qui s'immobilise relance le
                                    // chargement, comme apres un geste de la main.
                                    controller.cameraState()?.let { (la, lo, _) ->
                                        controller.centerOnAtLeast(la, lo, PoiLoading.MIN_ZOOM)
                                    }
                                }
                            ),
                    ) {
                        Text(
                            stringResource(
                                when {
                                    poi.tooFar -> R.string.poi_zoom_in
                                    poi.needsNetwork -> R.string.poi_needs_network
                                    poi.fromCache -> R.string.poi_from_cache
                                    // En dernier : les trois autres disent pourquoi la carte est vide ou
                                    // vieille, celui-ci pourquoi elle est incomplete - c'est le moins grave.
                                    else -> R.string.poi_partial
                                }
                            ),
                            fontSize = 12.sp,
                            // La couleur des commandes quand le message en est une, celle du texte ordinaire
                            // sinon : sans cela, rien ne distinguerait la consigne qu'on peut suivre d'un
                            // doigt des deux constats qu'on ne peut que lire.
                            color = if (zoomable) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                val selPoi = poi.selected
                if (selPoi != null) {
                    // idleTick SEUL, sans moveTick : l'infobulle garde sa place pres de son point pendant
                    // le geste, comme celle d'un waypoint, au lieu de courir apres la carte a chaque image.
                    val pOff = remember(selPoi, idleTick) {
                        controller.screenOf(selPoi.lon, selPoi.lat)?.let { p -> IntOffset(p.x.toInt(), p.y.toInt()) }
                    }
                    if (pOff != null) {
                        AnchoredBubble(
                            key = selPoi.uuid,
                            publish = true,
                            // La position reglee pour les infobulles (cf. BubblePosition), et non le coin
                            // qui deplace le moins la carte : un point d'interet est un marqueur comme un
                            // autre, son infobulle doit s'ouvrir la ou l'utilisateur l'attend.
                            panAllowed = bubblePos != BubblePosition.AUTO,
                            onPan = panMap,
                            placement = { bw, bh, vw, vh -> bubbleGeom.at(bubblePos, pOff.x, pOff.y, bw, bh, vw, vh) },
                        ) {
                            PoiBubble(
                                poi = selPoi,
                                onOpenWeb = { url ->
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                    }
                                },
                                // Les trois actions remplissent le planificateur et l'ouvrent : c'est
                                // l'ecran qui l'ouvre, parce que lui seul sait ce qu'il doit fermer
                                // pour lui laisser la place (cf. RoutePlannerState).
                                onSetStart = { ouvrePlanificateur(); planner.setStart(placeOf(selPoi)); poi.select(null) },
                                onSetEnd = { ouvrePlanificateur(); planner.setEnd(placeOf(selPoi)); poi.select(null) },
                                onAddStep = {
                                    ouvrePlanificateur()
                                    if (planner.addWaypoint(placeOf(selPoi))) poi.select(null)
                                    else plannerFullMessage = true
                                },
                                onClose = { poi.select(null) },
                                fontSp = settings?.bubbleFont ?: 14,
                                backgroundAlpha = (settings?.bubbleOpacityPct ?: 100) / 100f,
                            )
                        }
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
                        // Décalage de carte pour que l'épingle ET la bulle tiennent à l'écran ; le lieu
                        // hors de la vue courante ramène l'ensemble au centre (cf. computeGeocodePlacement).
                        //
                        // Attendu que la caméra soit arrêtée : retenir un lieu la lance vers lui
                        // (centerOnAtLeast), et tant qu'elle vole, la projection lue est celle d'AVANT le
                        // vol - un décalage calculé dessus s'ajouterait au mouvement en cours au lieu de le
                        // corriger, et posait le lieu hors de la carte. Son immobilisation incrémente
                        // idleTick, d'où la comparaison au tick du moment où le lieu a été retenu.
                        val pickedAtTick = remember(gPlace) { idleTick }
                        AnchoredBubble(
                            key = gPlace,
                            publish = true,
                            panAllowed = idleTick != pickedAtTick,
                            onPan = panMap,
                            placement = { bw, bh, vw, vh -> bubbleGeom.atNearestCorner(gOff.x, gOff.y, bw, bh, vw, vh) },
                        ) {
                            GeocodeBubble(
                                lines = gPlace.lines,
                                // Le lieu part tel quel dans le planificateur : il porte deja son
                                // adresse et ses coordonnees, c'est exactement ce qu'attend une etape.
                                onSetStart = { ouvrePlanificateur(); planner.setStart(gPlace); geo.clear() },
                                onSetEnd = { ouvrePlanificateur(); planner.setEnd(gPlace); geo.clear() },
                                onAddStep = {
                                    ouvrePlanificateur()
                                    if (planner.addWaypoint(gPlace)) geo.clear()
                                    else plannerFullMessage = true
                                },
                                onClose = { geo.clear() },
                                fontSp = settings?.bubbleFont ?: 14,
                                backgroundAlpha = (settings?.bubbleOpacityPct ?: 100) / 100f,
                            )
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
                        // Décalage de carte seulement si aucun des quatre coins ne tenait : le point désigné
                        // est en plein écran dans le cas ordinaire, et rien ne bouge. L'épingle, elle, ne
                        // bouge jamais d'un pouce : elle reste sur le point, c'est la carte qui glisse.
                        AnchoredBubble(
                            key = mPoint,
                            // Publié une fois l'adresse arrivée : mesurée au spinner, la bulle est plus
                            // courte que la bulle réelle, et le recentrage - à usage unique - serait
                            // consommé sur une hauteur qui n'est pas la sienne.
                            publish = mapPoint.address != AddressState.Loading,
                            panAllowed = true,
                            onPan = panMap,
                            placement = { bw, bh, vw, vh -> bubbleGeom.atNearestCorner(mOff.x, mOff.y, bw, bh, vw, vh) },
                        ) {
                            MapPointBubble(
                                address = mapPoint.address,
                                profileLabel = routingProfileLabel(routingProfile),
                                // La localisation éteinte dans le téléphone, la ligne disparaît : une
                                // mesure depuis une position inconnue ne partirait jamais (cf.
                                // MapPointBubble). L'affichage du repère, lui, n'y est pour rien - le
                                // capteur suffit. Une mesure déjà demandée retient la ligne : son
                                // résultat vaut pour l'endroit d'où elle est partie, et son tracé est
                                // sur la carte - rien ne l'expliquerait plus.
                                showPositionRow = location.sensorEnabled || mapPoint.positionMeasure != null,
                                positionMeasure = mapPoint.positionMeasure,
                                pointMeasure = mapPoint.pointMeasure,
                                imperial = imperialUnits,
                                onDistanceFromPosition = { onDistanceFromPositionTap() },
                                onDistanceFromPoint = { onDistanceFromPointTap() },
                                onSetStart = { ouvrePlanificateur(); planner.setStart(placeOfPoint()); mapPoint.clear() },
                                onSetEnd = { ouvrePlanificateur(); planner.setEnd(placeOfPoint()); mapPoint.clear() },
                                onAddStep = {
                                    ouvrePlanificateur()
                                    if (planner.addWaypoint(placeOfPoint())) mapPoint.clear()
                                    else plannerFullMessage = true
                                },
                                onClose = { mapPoint.clear() },
                                fontSp = settings?.bubbleFont ?: 14,
                                backgroundAlpha = (settings?.bubbleOpacityPct ?: 100) / 100f,
                            )
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
                val positionOffCenter = remember(location.lastUserLocation, moveTick, idleTick, viewCenterX, viewCenterY) {
                    location.lastUserLocation?.let { (la, lo) -> controller.screenOf(lo, la) }?.let { p ->
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
                    if (location.gpsActive && positionOffCenter) {
                        IconButton(onClick = { location.recenterOnGps() }, modifier = controlBg) {
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
                                    alerting -> OffTrackAlertColor
                                    followed != null -> MapChromeActive
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
                            val teinte = if (poi.visible) MapChromeActive else chromeFg
                            /*
                             * L'attente prend la place du pictogramme, DANS le bouton.
                             *
                             * Le chargement ne se voyait nulle part : on deplacait la carte et les
                             * marqueurs arrivaient une seconde ou deux plus tard, sans que rien n'ait dit
                             * qu'on les cherchait. Ici, la ou l'on vient de taper.
                             *
                             * Exactement la taille du pictogramme qu'il remplace (24 dp, la taille par
                             * defaut d'une icone Material) : un rond plus petit ferait sauter le bouton a
                             * chaque requete, et c'est le genre de tressautement qu'on remarque bien plus
                             * que l'attente elle-meme.
                             */
                            if (poi.loading) {
                                CircularProgressIndicator(
                                    Modifier.size(PoiButtonIconSize), color = teinte, strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.BookmarkBorder, stringResource(R.string.poi_layer_title),
                                    tint = teinte,
                                )
                            }
                        }
                    }
                    // Masqué tant que sa bande est ouverte, qu'il ne servirait qu'à rouvrir.
                    // `!= false` : meme raison que le bouton GPS. C'est l'autre bouton que le testeur a vu
                    // disparaitre, et par le meme chemin.
                    if (settings?.routePlannerEnabled != false && !planner.open) {
                        IconButton(onClick = {
                            if (ServiceUrl.needsInternet(routingUrl) && !NetworkStatus.hasInternet(ctx)) {
                                showNoConnectionDialog = true
                            } else {
                                vm.closeProfile()          // les deux occupent le bas de l'écran
                                // Le suivi allumé : on part d'où l'on est. Le bouton de la carte
                                // seulement - les infobulles, elles, ouvrent le planificateur POUR y
                                // poser le lieu qu'on vient de toucher, et pré-remplir le départ
                                // décalerait ce que leur "Étape" va remplir.
                                planner.openPlanner(fromCurrentPosition = location.gpsActive)
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
                    TrackEditToolbar(
                        state = edit, canUndo = canUndo, chromeBg = chromeBg, chromeFg = chromeFg,
                        topControlsHeightPx = topControlsHeightPx, onUndo = { vm.undoLastEdit() },
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
                        onPickCurrentPosition = { step ->
                            location.withLocationPermission { planner.choose(step, StepTarget.CurrentPosition) }
                        },
                        sensorEnabled = location.sensorEnabled,
                        geocoding = GeocodingParams(geocodingBase,
                            ctx.resources.configuration.locales[0].language, GeocodeResultLimit),
                        history = PlannerHistory.of(settings?.plannerHistory),
                        // Un lieu retenu remonte en tete de l'historique - la bande ne connait pas la base.
                        onPlaceChosen = { lieu -> vm.rememberPlannerPlace(lieu) },
                        onPlaceForgotten = { lieu -> vm.forgetPlannerPlace(lieu.label) },
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
                TrackEditPrompts(
                    edit = edit,
                    layers = layers,
                    cutGeometry = cutGeometry,
                    controller = controller,
                    chromeBg = chromeBg,
                    chromeFg = chromeFg,
                    moveTick = moveTick,
                    idleTick = idleTick,
                    bottomCoverPx = profileBarHeightPx,
                    vm = vm,
                )
                MeasureBubbleLayer(
                    state = measure,
                    controller = controller,
                    idleTick = idleTick,
                    // Le profil quand il est ouvert, la barre de navigation sinon.
                    bottomCoverPx = if (activeLayerId != null) profileBarHeightPx
                        else WindowInsets.navigationBars.getBottom(density),
                    imperial = imperialUnits,
                    fontSp = settings?.bubbleFont ?: 14,
                    backgroundAlpha = (settings?.bubbleOpacityPct ?: 100) / 100f,
                )
                // tracé de la bounding box hors-ligne (SPEC section 2)
                if (offline.drawingActive) {
                    BboxDrawingOverlay(
                        pointCount = offline.bboxPoints.size,
                        dark = darkChrome,
                        onCancelPoint = { offline.bboxPoints = offline.bboxPoints.dropLast(1) },
                        onCancelAll = { offline.cancelDrawing() },
                        onValidate = {
                            val (lon1, lat1) = offline.bboxPoints[0]
                            val (lon2, lat2) = offline.bboxPoints[1]
                            offline.configBbox = Bbox.of(lon1, lat1, lon2, lat2)
                            offline.drawingActive = false
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .onGloballyPositioned { offline.barHeightPx = it.size.height },
                    )
                }
                TrackProfileLayer(
                    activeLayerId = activeLayerId,
                    computed = computed,
                    lastComputed = lastComputed,
                    loading = profileLoading,
                    zoom = profileZoom,
                    cursor = cursor,
                    title = profileTitle,
                    lineColor = profileLineColor,
                    settings = settings,
                    imperial = imperialUnits,
                    gpsActive = location.gpsActive,
                    userLocation = location.lastUserLocation,
                    onHeightChange = { profileBarHeightPx = it },
                    onExpandZoom = { vm.expandProfileZoom() },
                    onToggleSlopeLegend = { vm.setSlopeLegend(it) },
                    onScrub = { vm.onProfileTap(it) },
                    onZoom = { scale, fraction -> vm.zoomProfile(scale, fraction) },
                    onDoubleTapZoom = { fraction -> vm.zoomProfile(2f, fraction) },
                )
                /*
                 * Bannière de l'alerte d'éloignement, posée EN DERNIER : elle passe donc par-dessus tout ce
                 * qui occupe le bas de l'écran - profil, bande du planificateur, consignes de saisie.
                 *
                 * C'est la seule barre du bas à s'accorder ce droit, et c'est ce qui la distingue : les
                 * autres accompagnent un geste qu'on vient de faire et peuvent attendre leur tour, celle-ci
                 * dit qu'on ne suit plus le chemin prévu. Une alerte qu'un panneau recouvre n'alerte
                 * personne, et la refermer d'un tap sur sa croix reste à un doigt.
                 */
                followed?.takeIf { alertBanner }?.let { suivie ->
                    OffTrackAlertBar(
                        trackName = suivie.layerName,
                        awayM = awayM ?: alertDistanceM.toDouble(),
                        imperial = imperialUnits,
                        onClose = { TrackWatch.silence() },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                    )
                }
                /*
                 * Le suivi s'est arrete tout seul, ou le repere ne bouge plus.
                 *
                 * Au meme endroit et au meme rang que l'alerte d'eloignement : ce sont les deux seules
                 * choses que la carte annonce d'elle-meme, et aucun panneau ne doit les recouvrir. Elles
                 * ne se disputent jamais la place - un suivi arrete n'a plus d'ecart a mesurer, et une
                 * position figee ne se mesure pas davantage.
                 *
                 * L'arret l'emporte sur la peremption : le repere efface est le fait le plus grave, et le
                 * dire deux fois n'en dirait pas plus.
                 */
                val arret = stopNotice
                if (arret != null) {
                    LocationNoticeBar(
                        text = stringResource(
                            if (arret == LocationHub.StopReason.SENSOR_OFF) R.string.location_stopped_sensor
                            else R.string.location_stopped_system,
                        ),
                        onDismiss = { location.dismissStopNotice() },
                        actionLabel = stringResource(R.string.location_stopped_resume),
                        onAction = { location.dismissStopNotice(); location.onGpsButtonTap() },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                    )
                } else if (location.positionStale) {
                    LocationNoticeBar(
                        text = stringResource(
                            R.string.location_stale_banner,
                            Format.duration((location.positionAgeMs ?: 0L) / 1000.0),
                        ),
                        onDismiss = { },
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
            if (offline.extentChoice) {
                OfflineExtentDialog(
                    dark = isDarkTheme(settings?.theme),
                    onDismiss = { offline.extentChoice = false },
                    onArea = {
                        offline.extentChoice = false
                        offline.corridor = null
                        offline.drawingActive = true
                    },
                    onTrack = { offline.extentChoice = false; offline.pickTrack = true },
                )
            }
            if (offline.pickTrack) {
                OfflineTrackPickDialog(
                    candidates = layers.filter { it.hasLine },
                    onDismiss = { offline.pickTrack = false },
                    onPick = { l ->
                        offline.pickTrack = false
                        // La geometrie est relue ICI et non a l'affichage de l'ecran suivant : le couloir se
                        // calcule sur les points reels, et l'estimation doit etre juste des la premiere image.
                        vm.trackPointsOf(l) { pts ->
                            if (pts.isNotEmpty()) {
                                offline.corridor = l to pts
                                offline.configBbox = Bbox.of(
                                    pts.minOf { it.first }, pts.minOf { it.second },
                                    pts.maxOf { it.first }, pts.maxOf { it.second },
                                )
                            }
                        }
                    },
                )
            }
            offline.configBbox?.let { bbox ->
                val currentProvider = providers.firstOrNull { it.id == settings?.defaultBasemapId }
                OfflineDownloadConfigScreen(
                    bbox = bbox,
                    corridorPoints = offline.corridor?.second,
                    corridorName = offline.corridor?.first?.name.orEmpty(),
                    providerMinZoom = currentProvider?.minZoom ?: 0,
                    providerMaxZoom = currentProvider?.maxZoom ?: 19,
                    dark = darkChrome,
                    styleJson = style?.styleJson, styleUrl = style?.styleUrl,
                    // La case n'apparait que si la couche des points d'interet est allumee : proposer
                    // d'emporter ce qu'on ne peut pas afficher n'aurait aucun sens.
                    poiAvailable = settings?.poiEnabled == true,
                    onDismiss = { offline.closeFlow() },
                    onDownload = { request ->
                        // Domaine B : lance le moteur, puis revient à la carte ou la popup de progression
                        // (observée via vm.offlineDownload) prend le relais.
                        vm.startOfflineDownload(request)
                        offline.closeFlow()
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
    if (importFlow.folderPicker) {
        ImportFolderDialog(
            folders = folders,
            onNewFolder = { importFlow.folderPicker = false; importFlow.newFolderDialog = true },
            onPick = { folderId -> importFlow.folderPicker = false; importFlow.proceed(folderId) },
            // Renoncer au dossier, c'est renoncer a l'import : les fichiers qu'une autre application nous a
            // confies sont relaches, sans quoi ils repartiraient au prochain import, celui d'autre chose.
            onDismiss = { importFlow.cancel() },
        )
    }

    reverseConfirm?.let { layer ->
        ReverseConfirmDialog(
            onConfirm = { vm.reverseLayer(layer); reverseConfirm = null },
            onDismiss = { reverseConfirm = null },
        )
    }
    edit.message?.let { message ->
        EditMessageDialog(message = message, onDismiss = { edit.message = null })
    }

    if (importDialog) {
        RouteImportDialog(
            defaultName = defaultRouteName(planner.targets, currentPositionLabel),
            folders = folders,
            onImport = { name, folderId ->
                routeGpx(name)?.let { vm.importLayer(it, GpxWriter.fileName(name), folderId) }
                importDialog = false
            },
            onDismiss = { importDialog = false },
        )
    }

    // création d'un dossier puis poursuite de l'import dedans
    if (importFlow.newFolderDialog) {
        NewFolderDialog(
            fallbackName = stringResource(R.string.label_new_folder),
            onCreate = { n ->
                importFlow.newFolderDialog = false
                vm.createFolder(n, null) { id -> importFlow.proceed(id) }
            },
            onDismiss = { importFlow.newFolderDialog = false },
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
        ImportReportDialog(failures = importReport, onDismiss = { importReport = emptyList() })
    }

    if (plannerFullMessage) {
        PlannerFullDialog(onDismiss = { plannerFullMessage = false })
    }

    if (showNoConnectionDialog) {
        NoConnectionDialog(onDismiss = { showNoConnectionDialog = false })
    }

    if (showAlertNeedsGpsDialog) {
        AlertNeedsGpsDialog(
            onEnable = {
                showAlertNeedsGpsDialog = false
                alertChooserPending = true
                location.onGpsButtonTap()
            },
            onDismiss = { showAlertNeedsGpsDialog = false },
        )
    }

    if (alert.chooserOpen) {
        TrackChooserDialog(
            candidates = alert.candidates,
            followed = followed,
            imperial = imperialUnits,
            onPick = { alert.follow(it, alertDistanceM.toDouble()) },
            onStop = { TrackWatch.stop(); alert.closeChooser() },
            onDismiss = { alert.closeChooser() },
        )
    }

    if (location.showDisabledDialog) {
        LocationDisabledDialog(
            onEnable = { location.showDisabledDialog = false; location.openLocationSettings() },
            onDismiss = { location.showDisabledDialog = false },
        )
    }
}

/** Part de la hauteur d'écran que l'infobulle ne dépasse pas ; au-delà, ses propriétés défilent. */
private const val BubbleMaxHeightRatio = 0.6f

/** Propositions demandées au géocodeur. Plus que les 4 visibles : le défilement de la liste n'a de sens
 *  que s'il y a de quoi défiler, et le service facture le même aller-retour dans les deux cas. */
private const val GeocodeResultLimit = 10

/** Part de la hauteur d'ecran que la bande du planificateur ne depasse jamais. */
private const val PlannerMaxHeightRatio = 0.6f

/** Zoom minimal garanti sur le lieu trouvé, un zoom plus serré étant conservé. À 12, la ville et ses
 *  abords tiennent à l'écran : de quoi situer l'épingle, sans plonger sur une adresse à la parcelle. */
private const val GeocodeMinZoom = 12.0
