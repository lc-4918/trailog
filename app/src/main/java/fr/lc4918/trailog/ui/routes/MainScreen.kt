package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.ui.offline.basemapLabel
import fr.lc4918.trailog.ui.offline.offlineDownloadAvailable
import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.DefaultGpsMarkerSizeDp
import fr.lc4918.trailog.data.db.DefaultOffTrackAlertM
import fr.lc4918.trailog.data.db.offTrackAlertVisible
import fr.lc4918.trailog.data.db.routePrefs
import fr.lc4918.trailog.data.db.routeUrl
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.GpsMarkerStyle
import fr.lc4918.trailog.domain.model.PlannerHistory
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.domain.model.RouteEngine
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.geocode.NetworkStatus
import fr.lc4918.trailog.geocode.Photon
import fr.lc4918.trailog.location.LocationHub
import fr.lc4918.trailog.location.TrackWatch
import fr.lc4918.trailog.location.TripStore
import fr.lc4918.trailog.location.TripWatch
import fr.lc4918.trailog.ui.alert.Dashboard
import fr.lc4918.trailog.ui.alert.DashboardField
import fr.lc4918.trailog.ui.alert.DashboardMath
import fr.lc4918.trailog.map.compositeIdFromBasemapId
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.net.ServiceUrl
import fr.lc4918.trailog.routing.GpxWriter
import fr.lc4918.trailog.routing.Router
import fr.lc4918.trailog.ui.alert.OffTrackAlertState
import fr.lc4918.trailog.ui.alert.FollowProgressMath
import fr.lc4918.trailog.ui.components.BusySpinner
import fr.lc4918.trailog.ui.components.BasemapControlPanel
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.MapLibreSurface
import fr.lc4918.trailog.ui.components.MapPromptBar
import fr.lc4918.trailog.ui.components.MapSurface
import fr.lc4918.trailog.ui.edit.TrackEditState
import fr.lc4918.trailog.ui.geocode.GeocodeSearchEffects
import fr.lc4918.trailog.ui.geocode.GeocodeSearchState
import fr.lc4918.trailog.ui.location.KeepScreenOnEffect
import fr.lc4918.trailog.ui.location.rememberLocationControls
import fr.lc4918.trailog.ui.mappoint.MapPointEffects
import fr.lc4918.trailog.ui.mappoint.MapPointState
import fr.lc4918.trailog.ui.mappoint.PointMeasureEffects
import fr.lc4918.trailog.ui.mappoint.PointMeasures
import fr.lc4918.trailog.ui.measure.MeasureBubbleLayer
import fr.lc4918.trailog.ui.measure.TrackMeasureState
import fr.lc4918.trailog.ui.offline.BboxEditorOverlay
import fr.lc4918.trailog.ui.offline.OfflineFlowState
import fr.lc4918.trailog.ui.offline.OfflineFlowUi
import fr.lc4918.trailog.ui.planner.GeocodingParams
import fr.lc4918.trailog.ui.planner.PlannerEffects
import fr.lc4918.trailog.ui.planner.RoutePlannerBand
import fr.lc4918.trailog.ui.planner.RoutePlannerState
import fr.lc4918.trailog.ui.planner.StepTarget
import fr.lc4918.trailog.ui.planner.defaultRouteName
import fr.lc4918.trailog.ui.poi.PoiEffects
import fr.lc4918.trailog.ui.poi.PoiState
import fr.lc4918.trailog.ui.poi.PoiStatusBanner
import fr.lc4918.trailog.ui.poi.poiGroupColor
import fr.lc4918.trailog.ui.poi.poiIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MainScreen(
    onSettings: () -> Unit,
    settingsOpen: Boolean = false,
    /** Compteur des demandes de telechargement d'une zone, venues des reglages (cf. AppRoot). */
    downloadAreaRequest: Int = 0,
    /** Le cadrage d'une zone est ouvert : ce qui l'attendait peut cesser de l'annoncer (cf. AppRoot). */
    onDownloadAreaReady: () -> Unit = {},
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
    /*
     * Le travail en cours de l'ecran, garde le temps d'une rotation (cf. MapScreenStates). Un ViewModel
     * et non des `remember` : l'activite n'annonce aucun `configChanges`, un quart de tour la recree, et
     * tout ce qui etait en cours partait avec elle - l'itineraire compose etape par etape, la mesure dont
     * le premier point venait d'etre pose, l'emprise a moitie tracee.
     */
    val screen: MapScreenStates = viewModel()
    // Ce que les commandes du haut ouvrent : la legende du fond, le gestionnaire de couches.
    val chromeState = remember { MapChromeState() }

    // ---------- téléchargement de carte hors-ligne (SPEC offline_map.md) ----------
    val offline = screen.offline
    // Ce que les bandes de l'ecran recouvrent, mesure a l'affichage : la colonne de boutons du haut, le
    // panneau de profil, la bande du planificateur, la barre de consigne du moment (cf. MapInsetsState).
    val insets = remember { MapInsetsState() }
    // Le telechargement pour le hors-ligne n'est propose que pour un fond qui s'y prete (cf.
    // offlineDownloadAvailable) : le menu d'une trace n'offre "Telecharger la carte" qu'a cette condition.
    val offlineButtonVisible = offlineDownloadAvailable(settings.defaultBasemapId, providers)
    // Ce qu'on repond quand le fond affiche ne se telecharge pas : on le NOMME, l'utilisateur en ayant
    // souvent plusieurs et venant peut-etre d'en changer.
    val nomFond = basemapLabel(settings.defaultBasemapId, providers, composites)
    val refusTelechargement = if (nomFond.isBlank()) stringResource(R.string.offline_unavailable)
        else stringResource(R.string.offline_unavailable_named, nomFond)
    // Une zone a telecharger, demandee depuis les reglages : le cadrage s'ouvre sur la carte, profil et
    // tableau de bord refermes (cf. OfflineFlowState.startDrawing).
    LaunchedEffect(downloadAreaRequest) {
        if (downloadAreaRequest > 0) {
            offline.startDrawing { vm.closeProfile() }
            onDownloadAreaReady()
        }
    }

    val renderLayers by vm.renderLayers.collectAsState()
    val importInProgress by vm.importing.collectAsState()
    val importFailures by vm.importFailures.collectAsState()
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

    val mode = settings.sideMenuMode
    controller.tapToleranceDp = settings.tapToleranceDp
    controller.lineTapToleranceDp = settings.lineTapToleranceDp
    controller.rotateGesturesEnabled = settings.rotateGesturesEnabled
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
    val location = rememberLocationControls(controller, settings.showGpsButton)
    // Ecran maintenu allume tant que le suivi tourne, si le reglage le demande (cf. KeepScreenOnEffect).
    // L'annonce d'un arret subi du suivi : voir la banniere posee avec celle de l'alerte d'eloignement.
    val stopNotice by LocationHub.stopNotice.collectAsState()
    KeepScreenOnEffect(settings.keepScreenOn && location.gpsActive)

    // Symbole du repère de position et sa couleur : celle réglée, sinon celle propre au symbole (le bleu de
    // la puce, le rouge des flèches et de la croix) - une couleur vide n'est pas un choix, c'est l'absence
    // de choix, et elle doit suivre le symbole quand on en change.
    val gpsMarker = GpsMarkerStyle.of(settings.gpsMarkerStyle)
    val gpsMarkerColorReglee = settings.gpsMarkerColor.takeIf { it.isNotBlank() } ?: gpsMarker.defaultColor
    /**
     * Le repere passe au GRIS quand il n'est plus suivi.
     *
     * **Ce n'est plus l'immobilite qui le grise.** Il virait au gris des que le capteur n'avait plus rien
     * donne depuis trente secondes - l'idee etant qu'un repere fige ressemble a un repere juste. Mais un
     * cycliste s'arrete, et le repere annoncait alors un doute qui n'existait pas, sur ce qui est le plus
     * regarde de la carte.
     *
     * Le gris dit desormais quelque chose de sur : ce point est la derniere position MESUREE, le suivi
     * est arrete, et il ne bougera plus (cf. LocationControls.lastFixShown). Le reglage de couleur reprend
     * la main des que le suivi repart.
     */
    val gpsMarkerColor =
        if (location.lastFixShown != null) GpsStaleColor else gpsMarkerColorReglee
    val gpsMarkerSizeDp = (settings.gpsMarkerSizeDp).toFloat()

    // Orientation du telephone, quand le symbole choisi en porte une (cf. HeadingEffect).
    // La mesure entiere et non les seules coordonnees : sa vitesse et son cap decident si la fleche montre
    // le deplacement ou la boussole (cf. HeadingFusion).
    HeadingEffect(
        active = location.gpsActive && gpsMarker.oriented,
        position = location.lastUserLocation,
        fix = location.lastFix,
    ) { controller.setUserHeading(it) }

    // ---------- alerte d'éloignement de la trace suivie ----------
    /*
     * Une trace choisie, la position projetée dessus à chaque mesure du capteur, et une bannière rouge
     * quand l'écart passe le seuil réglé.
     *
     * Tout est suspendu au capteur : sans position, il n'y a rien à projeter. La cloche demande donc de
     * l'allumer avant d'ouvrir son choix de traces, et le suivi s'arrête de lui-même dès qu'il s'éteint -
     * une trace suivie sans position ne dirait plus rien, et se réveillerait au hasard d'un rallumage.
     */
    val alert = screen.alert
    val alertEnabled = settings.offTrackAlertVisible
    val alertDistanceM = settings.offTrackAlertDistanceM
    /*
     * L'ecart et le son ne sont plus calcules ici mais dans le service (cf. LocationService.watchTrack) :
     * l'ecran eteint, la composition s'arrete, et une alerte qui ne se declenche que sous les yeux de
     * celui qu'elle doit prevenir n'alerte personne. L'ecran n'en lit que le resultat.
     */
    val followed by TrackWatch.followed.collectAsState()
    val alerting by TrackWatch.alerting.collectAsState()
    val silenced by TrackWatch.silenced.collectAsState()
    val awayM by TrackWatch.awayM.collectAsState()
    val alongM by TrackWatch.alongM.collectAsState()
    // Le tableau de bord : ouvert ou non, la cloche, le sens de parcours, et les compteurs de la sortie.
    val dashboardOpen by TrackWatch.dashboard.collectAsState()
    val armed by TrackWatch.armed.collectAsState()
    val direction by TrackWatch.direction.collectAsState()
    val trip by TripWatch.trip.collectAsState()
    /*
     * Ce qui reste de la trace suivie, dans le sens ou on la parcourt : recalcule a chaque position, le
     * tableau de bord ouvert seulement - c'est un parcours de la trace entiere, et le faire tourner
     * pendant toute une sortie pour un tableau ferme ne servirait personne.
     */
    /*
     * L'horloge du tableau de bord, a la seconde et tableau ouvert seulement : c'est elle qui ramene la
     * vitesse a zero quand les positions cessent d'arriver (cf. DashboardMath.speed).
     */
    var dashboardNow by remember { mutableStateOf(0L) }
    LaunchedEffect(dashboardOpen) {
        while (dashboardOpen) {
            dashboardNow = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val dashboardSpeed = location.lastFix.let { fix ->
        DashboardMath.speed(
            fix?.speedMps, fix?.speedAccuracyMps,
            fix?.let { (dashboardNow - it.receivedAtMs).coerceAtLeast(0L) },
            trip.anchorTimeMs?.let { (maxOf(dashboardNow, fix?.receivedAtMs ?: 0L) - it).coerceAtLeast(0L) },
        )
    }
    val followProgress = remember(followed, alongM, direction, dashboardOpen) {
        val f = followed ?: return@remember null
        val along = alongM ?: return@remember null
        if (!dashboardOpen) return@remember null
        FollowProgressMath.of(f.samples, along, null, 0L, 0L, direction)
    }
    val alertBanner = alerting && !silenced

    // ---------- recherche de lieu / adresse (géocodage) ----------
    val geo = screen.geo
    // Les deux boites que l'ecran ouvre pour son compte : service injoignable, editeur de proprietes.
    val dialogs = screen.dialogs

    // ---------- mesure sur trace ----------
    val measure = screen.measure
    // La mesure désactivée dans les réglages alors qu'elle est en cours efface tout : sans cela les
    // marqueurs noirs et leur infobulle survivraient au réglage qui les a fait naître (cf. géocodage).
    LaunchedEffect(settings.trackMeasureEnabled) {
        if (!settings.trackMeasureEnabled) measure.clear()
    }
    // Trace masquée ou supprimée alors qu'elle portait la mesure : celle-ci n'a plus de support. La laisser
    // afficherait deux marqueurs noirs et une distance au milieu d'une carte vide, sans rien à quoi les
    // rapporter.
    LaunchedEffect(layers, measure.start) {
        val id = measure.start?.layerId ?: return@LaunchedEffect
        if (layers.none { it.id == id && it.visible }) measure.clear()
    }

    // ---------- services et réglages partagés (point de la carte, planificateur) ----------
    val imperialUnits = settings.units == "imperial"
    val routeEngine = RouteEngine.of(settings.routeEngine)
    val routingUrl = Router.baseOf(routeEngine, settings.routeUrl(routeEngine))
    val geocodingBase = settings.geocodingUrl.takeIf { it.isNotBlank() } ?: Photon.DEFAULT_URL
    // Lissage de l'altitude, réglage commun au profil des traces : un itinéraire calculé n'a pas de raison
    // d'être coloré selon d'autres classes de pente que celles d'une trace, au même endroit.
    val profileSmoothingM = (settings.profileSmoothingM).toDouble()

    // ---------- point quelconque de la carte (appui long) ----------
    val mapPoint = screen.mapPoint
    val poiMeasures = screen.poiMeasures
    // Discipline des mesures : celle réglée dans les réglages, et non celle du planificateur, qui la choisit
    // pour le trajet qu'on y compose. Une distance demandée sur la carte n'a pas de composition derrière elle.
    val routingProfile = RoutingProfile.of(settings.routingProfile)
    // Préférences de tracé de cette discipline (voies, relief, revêtement). Prises aux réglages, comme la
    // discipline elle-même ; sans réglages chargés, celles que la discipline porte par défaut.
    val measurePrefs = settings.routePrefs(routingProfile)

    MapPointEffects(
        state = mapPoint,
        geocodingBase = geocodingBase,
        onPlaceFound = { lieu -> vm.rememberPlannerPlace(lieu) },
    )
    /*
     * Les deux mesures de distance, une paire par bulle qui les propose : le point d'appui long, et le
     * point d'interet ouvert.
     *
     * Deux porteurs et deux effets, et non un seul partage : les deux bulles peuvent etre a l'ecran en
     * meme temps, et les chiffres de l'une s'afficheraient sous l'autre.
     */
    PointMeasureEffects(
        measures = mapPoint.measures,
        routeEngine = routeEngine,
        routingUrl = routingUrl,
        routingProfile = routingProfile,
        prefs = measurePrefs,
        smoothingM = profileSmoothingM,
        currentPosition = { location.currentPosition() },
    )
    PointMeasureEffects(
        measures = poiMeasures,
        routeEngine = routeEngine,
        routingUrl = routingUrl,
        routingProfile = routingProfile,
        prefs = measurePrefs,
        smoothingM = profileSmoothingM,
        currentPosition = { location.currentPosition() },
    )

    // ---------- planificateur d'itinéraire ----------
    val planner = screen.planner
    // Tenu a jour a chaque composition, comme les proprietes du controleur de carte plus haut : le
    // planificateur deploye interdit le premier saut de camera a l'activation du GPS (cf.
    // LocationControls.startGps), pour la meme raison que le suivi continu s'y suspend deja.
    location.plannerExpanded = planner.expanded
    // Le reglage de recentrage, tenu a jour comme le precedent : l'allumage du capteur ne deplace la
    // camera que si on le lui a demande, et ne touche jamais au zoom (cf. LocationControls.startGps).
    location.recenterOnStart = settings.gpsRecenterOnStart
    // Le reglage "Afficher la derniere position mesuree" : lu ici, applique a l'arret du suivi.
    location.showLastFix = settings.gpsShowLastFix
    // ---------- retouche des traces ----------
    val edit = screen.edit
    val canUndo by vm.canUndo.collectAsState()
    val cutGeometry by vm.cutGeometry.collectAsState()
    // Le marqueur retire : la geometrie gardee pour lui n'a plus d'objet, et elle pese quelques megaoctets.
    LaunchedEffect(edit.cut == null) { if (edit.cut == null) vm.loadCutGeometry(null) }
    // Le reglage coupe : la barre se referme et rend les taps a la carte, comme le geocodage et la mesure.
    LaunchedEffect(settings.trackEditEnabled) {
        if (!settings.trackEditEnabled) edit.close()
    }
    // Ornements poses sur la carte, les deux couleurs et le fond des boutons, qui se lisent ensemble
    // (cf. MapChrome). Tous les boutons recoivent ce fond, y compris le GPS allume : son etat se lit a la
    // couleur de son dessin, non a un aplat qui le distinguait de ses voisins.
    val chrome = rememberMapChrome(settings)
    val chromeBg = chrome.bg
    val chromeFg = chrome.fg
    val darkChrome = chrome.dark
    // Ce qu'il faut degager en haut d'un cadrage : la colonne de boutons du coin haut-gauche, plus basse
    // que la barre de statut sous laquelle la carte passe en bord-a-bord (cf. MapInsetsState.topCoverPx).
    val statusBarTopPx = insets.topCoverPx(WindowInsets.statusBars.getTop(LocalDensity.current))

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
    PlannerEffects(
        state = planner,
        controller = controller,
        routeEngine = routeEngine,
        routingUrl = routingUrl,
        // Les préférences suivent la discipline choisie dans la BANDE, non celle des réglages : le
        // planificateur change de discipline sans quitter la carte.
        prefs = settings.routePrefs(planner.profile),
        smoothingM = profileSmoothingM,
        slopeTint = settings.routeSlopeLine,
        slopeClassTenths = settings.slopeClassTenths,
        styleTick = styleTick,
        enabled = settings.routePlannerEnabled,
        topPaddingPx = statusBarTopPx,
        bandHeightPx = insets.plannerBandPx,
        routeTracks = mapPoint.measures.routeTracks + poiMeasures.routeTracks,
        routeRevision = mapPoint.measures.revision + poiMeasures.revision,
        geocodingBase = geocodingBase,
        currentPosition = { location.currentPosition() },
    )
    /*
     * Le parcours du planificateur se suit comme une trace de la bibliothèque, SANS y être importé : on
     * compose son trajet, on part, et on veut être prévenu si on le quitte - l'importer d'abord serait un
     * détour, et laisserait derrière soi une couche dont on ne voulait pas. Il est proposé en tête de la
     * cloche tant qu'il est calculé (cf. plannedCandidate).
     *
     * Déclaré ICI et non avec les autres lectures de la veille, en haut : il lui faut le planificateur,
     * qui naît quelques lignes plus haut.
     */
    OffTrackAlertEffects(
        alert = alert,
        location = location,
        layers = layers,
        followed = followed,
        dashboardOpen = dashboardOpen,
        alertEnabled = alertEnabled,
        routeSamples = planner.done?.track?.samples,
        // Le nom que le parcours prendrait a l'import - "Position actuelle - Saint-Girons" -, celui qu'on
        // lui connait deja ; le libelle generique seulement quand il n'a pas encore d'etapes nommees.
        routeLabel = defaultRouteName(planner.targets, stringResource(R.string.planner_current_position))
            .ifBlank { stringResource(R.string.alert_track_planned) },
    )
    // Bande repliée ou fermée : elle ne pose plus rien, et la hauteur relevée à sa dernière pose ne vaut
    // plus. Sans cette remise à zéro, le cadrage d'un parcours dégagerait un bas d'écran désormais vide -
    // `onGloballyPositioned` ne se rappelle pas d'un composable qui a disparu.
    LaunchedEffect(planner.open, planner.collapsed) {
        if (!planner.open || planner.collapsed) insets.plannerBandPx = 0
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
        // Renoncer au selecteur (uri null) n'est pas un echec : on n'a rien demande de plus. Tout le
        // reste en est un, et se DIT - un tap qui ne rend ni fichier ni message laisse croire au fichier
        // ecrit, et c'est la pire des deux issues.
        if (uri == null) return@rememberLauncherForActivityResult
        val name = defaultRouteName(planner.targets, currentPositionLabel)
        val bytes = routeGpx(name)
        val ecrit = bytes != null && runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        }.getOrDefault(false)
        if (!ecrit) dialogs.failed(R.string.error_file_not_written)
    }

    /*
     * Les deux mesures s'assurent d'abord qu'elles pourront aboutir : un itinéraire est une requête, et
     * sans réseau la mesure ne rendrait qu'un échec, que rien n'expliquerait. Le bouton "depuis la
     * position" n'est proposé que le capteur allumé (cf. MapPointBubble), il n'a donc pas à s'en soucier.
     */
    fun onDistanceFromPositionTap(measures: PointMeasures) {
        if (ServiceUrl.needsInternet(routingUrl) && !NetworkStatus.hasInternet(ctx)) dialogs.noConnection = true
        else location.withLocationPermission { measures.requestDistanceFromPosition() }
    }

    /** "Distance depuis un point" : passe en mode de saisie, la mesure part au tap sur la carte. */
    fun onDistanceFromPointTap(measures: PointMeasures) {
        if (ServiceUrl.needsInternet(routingUrl) && !NetworkStatus.hasInternet(ctx)) dialogs.noConnection = true
        else measures.startPickingPoint()
    }

    /** Ouvre (ou referme) la barre de recherche, après s'être assuré qu'elle pourra aboutir : ouvrir un
     *  champ dont aucune frappe ne rendra jamais rien laisserait croire à un service muet. */
    fun onGeocodeButtonTap() {
        val base = settings.geocodingUrl.takeIf { it.isNotBlank() } ?: Photon.DEFAULT_URL
        when {
            geo.searchOpen -> geo.closeSearch()
            ServiceUrl.needsInternet(base) && !NetworkStatus.hasInternet(ctx) -> dialogs.noConnection = true
            else -> geo.openSearch()
        }
    }

    StatusBarAppearanceEffect(
        settings = settings,
        overlayOpen = settingsOpen || drawerState.isOpen,
    )

    MapTapRouting(
        controller = controller,
        offline = offline,
        measure = measure,
        mapPoint = mapPoint,
        measures = listOf(mapPoint.measures, poiMeasures),
        planner = planner,
        edit = edit,
        layers = layers,
        vm = vm,
    )
    // cadrage sur les couches récemment importées à la fermeture du menu
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed) {
            vm.consumePendingFit()?.let { b -> controller.fitTo(b[0], b[1], b[2], b[3]) }
        }
    }

    // import : dossier -> fichier (une couche peut contenir points et/ou traces, pas de choix de type en amont)
    val importFlow = rememberImportFlow(
        importDir = settings.importDir,
        onImportLayer = { bytes, name, folderId -> vm.importLayer(bytes, name, folderId) },
        // Un import venu d'ailleurs ouvre le menu : sa couche arrive dans un dossier que rien a l'ecran ne
        // montre, et sans cela l'application ne repondrait que par le silence. Sa fermeture cadre ensuite la
        // carte sur ce qui a ete importe, comme apres n'importe quel import.
        onFilesEntrusted = { scope.launch { drawerState.open() } },
    )
    // Fichiers refusés à l'import : présentés en une seule popup, et seulement quand plus aucun import
    // n'est en cours. Les afficher au fil de l'eau ouvrirait une popup par fichier fautif, en plein lot.
    LaunchedEffect(importInProgress.isEmpty(), importFailures.isNotEmpty()) {
        if (importInProgress.isEmpty() && importFailures.isNotEmpty()) importFlow.report = vm.consumeImportFailures()
    }

    // import d'image pour un champ IMAGE d'infobulle (PropertyEditor) : le rendez-vous est pris au tap
    // (cf. MainDialogState.awaitImage), et tenu ici au retour du selecteur.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u -> dialogs.pendingImageCallback?.let { cb -> vm.importFeatureImage(u, cb) } }
    }

    val density = LocalDensity.current
    val markerPx = with(density) { (settings.markerSize).dp.toPx() }
    // Geometrie commune aux quatre infobulles : ce qu'elles degagent, ou elles se posent, et le glissement
    // de carte qu'elles demandent pour tenir a l'ecran (cf. BubbleFrame).
    val bubbleFrame = rememberBubbleFrame(settings, markerPx, controller)
    MapOverlayEffects(
        controller = controller,
        styleTick = styleTick,
        markerPx = markerPx,
        renderLayers = renderLayers,
        trackWidthDp = settings.trackLineWidth,
        trackArrows = settings.expertMode && settings.trackDirectionArrows,
        geo = geo,
        measure = measure,
        mapPoint = mapPoint,
        planner = planner,
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
        // Le suivi de la carte, dans sa forme la plus simple : le reglage et le capteur. Les trois choses
        // qui SUSPENDENT le suivi continu (bande deployee, profil, infobulle) ne valent pas ici - elles
        // protegent une camera que l'utilisateur a visee, et au moment du placement il n'y en a pas
        // encore : la MapView vient de naitre.
        followsPosition = settings.mapFollowPosition && location.gpsActive,
        userLocation = location.lastUserLocation,
    )

    // Les deux compteurs que la camera fait avancer, et les rappels qui vont avec (cf. MapCameraCallbacks).
    val ticks = rememberMapCameraCallbacks(
        controller = controller,
        location = location,
        mapPoint = mapPoint,
        vm = vm,
        // Une lambda et non un booleen : le rappel est pose une seule fois et vit bien plus longtemps que
        // la composition qui l'a pose. Un booleen y serait fige a sa valeur d'alors, et l'ecran
        // enregistrerait un cadrage qui n'est pas encore celui de l'utilisateur.
        isPositioned = { positioned },
    )
    val moveTick = ticks.move
    val idleTick = ticks.idle
    val bearing = remember(moveTick) { controller.bearing() }

    /*
     * Autour de quoi le geocodeur cherche : la position du porteur, le centre de la carte a defaut.
     *
     * Les propositions se classent sur cette reference (cf. Photon.rank) : on cherche presque toujours un
     * lieu autour de la ou l'on est, et a defaut autour de ce qu'on regarde - preparer une sortie depuis
     * chez soi commence par amener la carte sur la region.
     *
     * [LocationControls.knownPosition] repond instantanement, sans rien demander au capteur : une frappe ne
     * doit pas attendre un point, et le systeme tient deja une derniere position connue - y compris quand
     * le suivi est eteint. Le centre de la carte se relit a chaque immobilisation, la ou une mesure prise a
     * chaque image du geste ne changerait rien au classement.
     */
    val vueCentre = remember(idleTick) { controller.cameraState()?.let { (la, lo, _) -> lo to la } }
    val geocodeCenter = location.knownPosition() ?: vueCentre

    GeocodeSearchEffects(geo = geo, settings = settings, resultLimit = GeocodeResultLimit,
        center = geocodeCenter)

    /*
     * ---------- Points d'interet (DATAtourisme) ----------
     *
     * Charges sur l'emprise visible, apres un temps d'arret : un deplacement de carte emet des dizaines
     * d'evenements, et sans cette attente chacun partirait en requete. On ne redemande rien tant que la vue
     * reste dans ce qui a deja ete charge - c'est ce qui tient les appels loin du quota du service, bien
     * plus que le delai lui-meme (cf. PoiLoading).
     */
    val poi = screen.poi
    /*
     * Les categories retenues, et la couche qui les SUIT.
     *
     * Il n'y a plus d'interrupteur separe : la carte montre exactement ce que le filtre retient, et tout
     * masquer l'eteint (cf. PoiFilters). Les deux se contredisaient - couche allumee, toutes les
     * categories decochees, c'est-a-dire une carte vide qu'aucun reglage n'expliquait.
     */
    val poiFilters = remember(settings.poiHiddenCategories) {
        PoiFilters.of(settings.poiHiddenCategories)
    }
    // Les traces affichees, pour le couloir des points d'interet (cf. PoiCorridor). Collectees ici et non
    // dans les effets : c'est le ViewModel qui les tient, deja decimees et deja lues.
    val trackCorridor by vm.trackCorridor.collectAsState()
    /*
     * **Le parcours qu'on vient de calculer borde le couloir comme une trace de la bibliotheque.**
     *
     * C'est meme le cas qui compte le plus : on cherche ou dormir et ou manger LE LONG DU TRAJET QU'ON
     * PREPARE, et ce trajet-la n'est pas encore une couche - il ne le devient qu'en l'enregistrant. La
     * carte restait donc vide au-dessus de l'itineraire affiche, a moins d'avoir par chance une trace
     * importee dans les parages.
     *
     * Decime a [CorridorMaxPoints] : le couloir se mesure en kilometres, et un sommet tous les cent metres
     * y repond aussi bien qu'un sommet tous les cinq. Le filtre compare chaque lieu a chaque sommet, et un
     * parcours de trois cents kilometres en porte des dizaines de milliers.
     */
    val routeCorridor = remember(planner.done?.track) {
        val s = planner.done?.track?.samples.orEmpty()
        if (s.isEmpty()) null
        else {
            val pas = (s.size / CorridorMaxPoints + 1)
            s.filterIndexed { i, _ -> i % pas == 0 }.map { it.lon to it.lat }
        }
    }
    val corridorTracks = remember(trackCorridor, routeCorridor) {
        if (routeCorridor == null) trackCorridor else trackCorridor + listOf(routeCorridor)
    }
    LaunchedEffect(poiFilters, settings.poiEnabled) {
        poi.showLayer(settings.poiEnabled && !poiFilters.nothingShown)
    }
    // La mise de cote est un reglage : l'ecran la recopie a chaque changement, lancement compris.
    LaunchedEffect(settings.poiMasked) { poi.restoreMask(settings.poiMasked) }
    // Le reglage qui pose le bouton sur la carte s'eteint : la bulle qu'il ouvrait n'a plus de bouton pour
    // la refermer.
    LaunchedEffect(settings.poiEnabled) { if (!settings.poiEnabled) poi.closeBubble() }
    PoiEffects(
        state = poi,
        controller = controller,
        repo = vm.poiRepository,
        enabled = settings.poiEnabled,
        osmComplement = settings.poiOsmComplement,
        osmUrl = settings.poiOsmUrl,
        filters = poiFilters,
        corridorTracks = corridorTracks,
        corridorM = settings.poiTrackCorridorM,
        idleTick = idleTick,
        markerPx = markerPx,
        styleTick = styleTick,
        positioned = positioned,
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
        poi.selected?.let { vm.rememberPlannerPlace(placeOfPoi(it, ctx)) }
    }
    /*
     * Une infobulle de point s'ouvre pendant qu'un trajet se compose : la bande se range d'elle-meme.
     *
     * **Elles se disputent le meme bas d'ecran.** La bande deployee occupe jusqu'a 60 % de la hauteur, et
     * elle est dessinee APRES les infobulles : une bulle qui tombait dans cette zone passait dessous, avec
     * ses boutons d'itineraire - c'est-a-dire precisement ce qu'on venait chercher en designant le point.
     *
     * Vaut pour les trois : l'appui long, le point d'interet et le lieu trouve par la recherche. Ce sont
     * trois facons de montrer un endroit, et les trois memes actions les terminent.
     *
     * La bande ne revient que par une des actions d'itineraire (cf. [ouvrePlanificateur]) : refermer la
     * bulle sans rien en faire, c'est avoir regarde ailleurs, et rien ne dit qu'on en a fini avec la carte.
     * Le bouton du coin bas-droit la redeploie a la demande.
     */
    LaunchedEffect(mapPoint.point, poi.selected?.uuid, geo.place) {
        val bulleOuverte = mapPoint.point != null || poi.selected != null || geo.place != null
        if (bulleOuverte && planner.expanded) planner.collapse(true)
    }
    // Ouvre le planificateur pour y recevoir un point, en fermant ce qui lui prendrait la place.
    fun ouvrePlanificateur() {
        // Deja DEPLOYE : il n'y a rien a ouvrir, et rien a ecraser. Reduit, en revanche, il se redeploie -
        // le lieu qu'on vient de poser depuis une infobulle doit se voir arriver dans le trajet, sans quoi
        // le bouton "Etape" remplit une bande que personne ne regarde.
        if (planner.expanded) return
        vm.closeProfile()
        if (planner.open) planner.collapse(false) else {
            planner.openPlanner()
            planner.chooseProfile(RoutingProfile.of(settings.routingProfile))
        }
    }
    MapFollowEffect(
        settings = settings,
        location = location,
        planner = planner,
        controller = controller,
        poi = poi,
        geo = geo,
        mapPoint = mapPoint,
        selectedMarkerId = selectedMarkerId,
        layerOpen = activeLayerId != null,
    )

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
    MapBackHandlers(
        drawerState = drawerState,
        scope = scope,
        offline = offline,
        planner = planner,
        geo = geo,
        measure = measure,
        mapPoint = mapPoint,
        measures = listOf(mapPoint.measures, poiMeasures),
        offlineDownload = offlineDownload,
        vm = vm,
        profileOpen = computed != null,
    )

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
                    // Le long d'une trace, depuis son menu : on part directement sur la configuration, la trace
                    // choisie - masquee ou non, sa geometrie se relit sur le disque.
                    // L'entree reste OFFERTE meme quand le fond ne se telecharge pas : elle disparaissait,
                    // et l'on cherchait une fonction qu'on avait deja utilisee, sans savoir que le fond
                    // choisi entre-temps l'interdisait. Elle le dit desormais, en nommant ce fond.
                    onDownloadMap = ({ l ->
                        scope.launch { drawerState.close() }
                        if (!offlineButtonVisible) dialogs.failedText(refusTelechargement)
                        else {
                            // La geometrie se relit du disque : sur une longue trace, l'ecran de
                            // configuration se fait attendre, et la carte doit dire qu'il vient.
                            offline.preparing = true
                            vm.trackPointsOf(l) { pts ->
                                offline.preparing = false
                                if (pts.isNotEmpty()) {
                                    offline.corridor = l to pts
                                    offline.configBbox = Bbox.of(
                                        pts.minOf { it.first }, pts.minOf { it.second },
                                        pts.maxOf { it.first }, pts.maxOf { it.second },
                                    )
                                }
                            }
                        }
                    }),
                    onFailure = { message -> dialogs.failed(message) },
                    onZoom = { kind, id ->
                        scope.launch { drawerState.close() }
                        when (kind) {
                            // Cadrer une couche masquee montrerait une carte vide a l'endroit ou elle devrait
                            // etre : on vient la regarder, elle s'allume donc au passage.
                            "layer" -> layers.firstOrNull { it.id == id }?.let {
                                if (!it.visible) vm.setLayerVisible(it, true)
                                controller.fitTo(it.west, it.south, it.east, it.north)
                            }
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
                    // Le reglage de cache de tuiles, enfin branche : il vivait dans la base et s'affichait
                    // en reglages sans rien commander, MapLibre restant sur ses 50 Mo (cf. AmbientCache).
                    ambientCacheMb = settings.ambientCacheMb,
                    onReady = { styleTick++ },
                )
                // bande de barre de statut : couleur du thème, ou transparente (carte dessous)
                val transparent = settings.statusBarTransparent
                Box(Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(if (transparent) Color.Transparent else MaterialTheme.colorScheme.background))
                MapTopLeftControls(
                    settings = settings,
                    chrome = chrome,
                    insets = insets,
                    controller = controller,
                    location = location,
                    geo = geo,
                    measure = measure,
                    planner = planner,
                    edit = edit,
                    vm = vm,
                    offlineDownload = offlineDownload,
                    burgerVisible = mode != "swipe",
                    onMenu = { scope.launch { drawerState.open() } },
                    onGeocodeTap = { onGeocodeButtonTap() },
                )
                MapTopRightControls(
                    settings = settings,
                    chrome = chrome,
                    chromeState = chromeState,
                    controller = controller,
                    bearing = bearing,
                    activeLegends = activeLegends,
                )
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
                if (activeLayerId == null && !planner.expanded && settings.showScale) {
                    // Ces barres portent déjà leur propre marge de barre de navigation, d'où le repli sur
                    // navigationBarsPadding quand il n'y en a aucune.
                    val bottomBarPx = insets.bottomBarPx
                    val base = Modifier.align(Alignment.BottomStart)
                    ScaleBar(controller, idleTick, maxWidthPx = constraints.maxWidth * 0.40f,
                        bg = chromeBg, fg = chromeFg,
                        modifier = (if (bottomBarPx > 0) base.padding(bottom = with(density) { bottomBarPx.toDp() } + 8.dp)
                            else base.padding(bottom = 8.dp).navigationBarsPadding()).padding(start = 16.dp))
                }
                BasemapLegend(
                    legends = activeLegends,
                    visible = chromeState.legendOpen && activeLegends.isNotEmpty(),
                    anchor = chromeState.legendAnchor,
                    onDismiss = { chromeState.legendOpen = false },
                )
                MarkerBubbleLayer(
                    settings = settings,
                    frame = bubbleFrame,
                    vm = vm,
                    dialogs = dialogs,
                    selectedMarkerId = selectedMarkerId,
                    selectedFeature = selectedFeature,
                    schema = markerLayerData?.schema ?: emptyList(),
                    bubbleOffset = bubbleOffset,
                    maxHeightPx = constraints.maxHeight,
                )
                PoiStatusBanner(poi = poi, controller = controller, topControlsPx = insets.topControlsPx)
                PlaceBubblesLayer(
                    settings = settings,
                    frame = bubbleFrame,
                    controller = controller,
                    poi = poi,
                    geo = geo,
                    mapPoint = mapPoint,
                    poiMeasures = poiMeasures,
                    planner = planner,
                    location = location,
                    routingProfile = routingProfile,
                    imperial = imperialUnits,
                    idleTick = idleTick,
                    moveTick = moveTick,
                    bandHeightPx = insets.plannerBandPx,
                    onOpenPlanner = { ouvrePlanificateur() },
                    onDistanceFromPosition = { m -> onDistanceFromPositionTap(m) },
                    onDistanceFromPoint = { m -> onDistanceFromPointTap(m) },
                    onFailure = { message -> dialogs.failed(message) },
                )
                MapBottomRightControls(
                    settings = settings,
                    chrome = chrome,
                    controller = controller,
                    location = location,
                    poi = poi,
                    planner = planner,
                    vm = vm,
                    routingUrl = routingUrl,
                    dashboardEnabled = alertEnabled,
                    dashboardOpen = dashboardOpen,
                    alerting = alerting,
                    dashboardPx = insets.dashboardPx,
                    poiFilters = poiFilters,
                    onPoiFilters = { vm.savePoiFilters(it) },
                    onPoiMasked = { vm.savePoiMasked(it) },
                    moveTick = moveTick,
                    idleTick = idleTick,
                    maxWidthPx = constraints.maxWidth,
                    maxHeightPx = constraints.maxHeight,
                    /*
                     * Le bouton du tableau de bord l'ouvre et le ferme. L'ouvrir allume le capteur s'il
                     * dort - les compteurs n'ont que la position pour matiere -, et c'est la fermeture qui
                     * le rendra (cf. OffTrackAlertEffects). Le CAPTEUR du telephone coupe, on propose
                     * d'aller l'allumer.
                     */
                    onDashboardTap = {
                        if (dashboardOpen) TrackWatch.setDashboard(false)
                        else {
                            TrackWatch.setDashboard(true)
                            alert.onOpen(location.sensorEnabled)
                            if (location.sensorEnabled && !location.gpsActive) location.startGps(forFollow = true)
                        }
                    },
                    onNoConnection = { dialogs.noConnection = true },
                )

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
                        topControlsHeightPx = insets.topControlsPx, onUndo = { vm.undoLastEdit() },
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
                // Bande du planificateur, sur toute la largeur du bas. Réduite, elle ne pose plus rien :
                // c'est le bouton du coin bas-droit qui la redéploie (cf. MapBottomRightControls).
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
                        // La bande se range d'elle-meme pour rendre la carte, et la consigne du haut prend
                        // le relais (cf. RoutePlannerState.startPickingOnMap).
                        onPickOnMap = { step -> planner.startPickingOnMap(step) },
                        sensorEnabled = location.sensorEnabled,
                        geocoding = GeocodingParams(geocodingBase,
                            ctx.resources.configuration.locales[0].language, GeocodeResultLimit,
                            geocodeCenter),
                        history = PlannerHistory.of(settings.plannerHistory),
                        // Un lieu retenu remonte en tete de l'historique - la bande ne connait pas la base.
                        onPlaceChosen = { lieu -> vm.rememberPlannerPlace(lieu) },
                        onPlaceForgotten = { lieu -> vm.forgetPlannerPlace(lieu.label) },
                        onImport = { planner.importDialog = true },
                        onDownload = {
                            val name = defaultRouteName(planner.targets, currentPositionLabel)
                            gpxSaver.launch(GpxWriter.fileName(name))
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            // Le clavier pousse la bande au-dessus de lui : sans cela il recouvrirait les
                            // propositions du champ qui vient de prendre le focus, c'est-a-dire
                            // precisement ce qu'on cherche a lire en tapant.
                            // L'UNION des deux marges, et non les deux appliquees l'une apres l'autre :
                            // elle en prend le maximum, la sur ou la barre de navigation s'additionnerait
                            // a un clavier qui la recouvre deja.
                            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                            .onGloballyPositioned { insets.plannerBandPx = it.size.height },
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
                            .onGloballyPositioned { insets.measureBarPx = it.size.height },
                    )
                    // Sa hauteur retombe a zero quand elle se retire : sans cela, l'echelle restait calee
                    // au-dessus d'une barre disparue (cf. MapInsetsState.promptBarPx).
                    DisposableEffect(Unit) { onDispose { insets.measureBarPx = 0 } }
                }
                /*
                 * Consigne du choix d'une étape sur la carte, EN HAUT de l'écran - la seule des consignes
                 * qui n'est pas en bas.
                 *
                 * Le bas est occupé : la bande du planificateur vient tout juste de s'y ranger, et le
                 * bouton qui la redéploie s'y trouve encore. Une consigne posée par-dessus se serait lue
                 * comme un morceau de la bande, alors qu'elle dit précisément qu'on l'a quittée. Elle
                 * rejoint donc les avertissements du haut, sous la colonne des boutons.
                 *
                 * Sa croix rend la bande telle qu'on l'avait laissée (cf. RoutePlannerState.collapse).
                 */
                if (planner.pickingOnMap) {
                    MapPromptBar(
                        text = stringResource(R.string.planner_pick_on_map_prompt),
                        onClose = { planner.cancelPickingOnMap() },
                        modifier = Modifier.align(Alignment.TopCenter)
                            .padding(top = with(density) { insets.topControlsPx.toDp() } + 16.dp),
                    )
                }
                /*
                 * Consigne du choix d'un point de référence : même barre que la mesure sur trace, l'une et
                 * l'autre ne demandant qu'un tap sur la carte.
                 *
                 * Les deux paires de mesures - celle d'un appui long, celle d'un point d'intérêt - la
                 * partagent : elles ne peuvent pas être en attente ensemble, le choix effaçant la bulle
                 * qui l'a demandé, et deux barres identiques l'une sur l'autre ne diraient rien de plus.
                 */
                val enAttenteDePoint = listOf(mapPoint.measures, poiMeasures).firstOrNull { it.pickingPoint }
                if (enAttenteDePoint != null) {
                    MapPromptBar(
                        text = stringResource(R.string.geocode_pick_point_prompt),
                        onClose = { enAttenteDePoint.cancelPickingPoint() },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                            .onGloballyPositioned { insets.pointBarPx = it.size.height },
                    )
                    // Sa hauteur retombe a zero quand elle se retire : sans cela, l'echelle restait calee
                    // au-dessus d'une barre disparue (cf. MapInsetsState.promptBarPx).
                    DisposableEffect(Unit) { onDispose { insets.pointBarPx = 0 } }
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
                    bottomCoverPx = insets.profilePanelPx,
                    vm = vm,
                )
                MeasureBubbleLayer(
                    state = measure,
                    controller = controller,
                    idleTick = idleTick,
                    // Le profil quand il est ouvert, la barre de navigation sinon.
                    bottomCoverPx = if (activeLayerId != null) insets.profilePanelPx
                        else WindowInsets.navigationBars.getBottom(density),
                    imperial = imperialUnits,
                    fontSp = settings.bubbleFont,
                    backgroundAlpha = (settings.bubbleOpacityPct) / 100f,
                )
                // Reglage de l'emprise hors-ligne : un cadre a poignees, la carte dessous.
                if (offline.drawingActive) {
                    BboxEditorOverlay(
                        controller = controller,
                        dark = darkChrome,
                        topInsetPx = insets.topControlsPx,
                        onCancel = { offline.cancelDrawing() },
                        onNext = { bbox ->
                            offline.configBbox = bbox
                            offline.drawingActive = false
                        },
                        onBarHeight = { insets.offlineBarPx = it },
                    )
                    // La barre partie, l'echelle redescend (cf. MapInsetsState.promptBarPx).
                    DisposableEffect(Unit) { onDispose { insets.offlineBarPx = 0 } }
                }
                /*
                 * Le tableau de bord, en bas de la carte. Il s'efface tant que le bas est occupe - le
                 * profil, la bande deployee du planificateur, une consigne de saisie - et revient ensuite ;
                 * sa hauteur remonte l'echelle et les boutons du coin (cf. MapInsetsState.dashboardPx).
                 */
                if (alertEnabled && dashboardOpen && activeLayerId == null && !planner.expanded &&
                    insets.promptBarPx == 0
                ) {
                    val nomSuivi = followed?.let {
                        if (it.trackCount <= 1) it.layerName
                        else stringResource(R.string.alert_track_segment, it.layerName, it.trackIndex + 1, it.trackCount)
                    }
                    Dashboard(
                        trip = trip,
                        speedMps = dashboardSpeed,
                        progress = followProgress,
                        trackName = nomSuivi,
                        armed = armed,
                        alerting = alerting,
                        hidden = remember(settings.dashboardHidden) { DashboardField.hidden(settings.dashboardHidden) },
                        fontSizes = remember(settings.dashboardFontSizes) {
                            DashboardField.fontSizes(settings.dashboardFontSizes)
                        },
                        imperial = imperialUnits,
                        bg = chromeBg,
                        fg = chromeFg,
                        onBell = { TrackWatch.setArmed(!armed) },
                        onReset = {
                            TripWatch.reset()
                            scope.launch { TripStore.save(ctx, TripWatch.trip.value) }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .onGloballyPositioned { insets.dashboardPx = it.size.height },
                    )
                    DisposableEffect(Unit) { onDispose { insets.dashboardPx = 0 } }
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
                    onHeightChange = { insets.profilePanelPx = it },
                    onExpandZoom = { vm.expandProfileZoom() },
                    onToggleSlopeLegend = { vm.setSlopeLegend(it) },
                    onScrub = { vm.onProfileTap(it) },
                    onZoom = { scale, fraction -> vm.zoomProfile(scale, fraction) },
                    onDoubleTapZoom = { fraction -> vm.zoomProfile(2f, fraction) },
                )
                MapNoticeLayer(
                    location = location,
                    followed = followed,
                    alerting = alertBanner,
                    awayM = awayM,
                    alertDistanceM = alertDistanceM,
                    stopNotice = stopNotice,
                    imperial = imperialUnits,
                    topControlsPx = insets.topControlsPx,
                )
            }
            /*
             * Ouverture du menu par glissement du bord gauche.
             *
             * **Elle s'arrête au-dessus de la bande du planificateur.** Sur toute la hauteur, ses 24 dp
             * recouvraient le centre du bouton "Réduire" de l'en-tête, qui est justement collé au bord
             * gauche : posée par-dessus la bande, elle prenait le tap, et le calcul d'itinéraire ne se
             * repliait pas. Le doigt tombant rarement pile au milieu, le bouton répondait une fois sur
             * deux - le genre de défaut qu'on met sur le compte de sa propre maladresse.
             *
             * La marge basse reprend celle de la bande elle-même (clavier ou barre de navigation, cf.
             * RoutePlannerBand), sans quoi la bande de geste remonterait dans son bas.
             */
            if (mode != "burger") {
                val bandePx = insets.plannerBandPx
                val basCouvert = with(density) {
                    if (bandePx == 0) 0.dp
                    else (bandePx + maxOf(
                        WindowInsets.ime.getBottom(this), WindowInsets.navigationBars.getBottom(this),
                    )).toDp()
                }
                Box(Modifier.align(Alignment.TopStart).padding(bottom = basCouvert)
                    .fillMaxHeight().width(24.dp)
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
            if (chromeState.basemapControlOpen) {
                Box(Modifier.fillMaxSize().clickable(
                    indication = null, interactionSource = remember { MutableInteractionSource() },
                ) { chromeState.basemapControlOpen = false })
                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
                    BasemapControlPanel(
                        folders = basemapFolders, providers = providers, composites = composites,
                        currentBasemapId = settings.defaultBasemapId,
                        widthFraction = (settings.basemapControlWidthPct) / 100f,
                        backgroundAlpha = (settings.basemapControlOpacityPct) / 100f,
                        onSelect = { id -> vm.selectBasemap(id); chromeState.basemapControlOpen = false },
                        onCreateFolder = { name, parentId -> vm.createBasemapFolder(name, parentId) },
                        onReorderDrop = { k, id, tk, tid, pos -> vm.reorderBasemapDrop(k, id, tk, tid, pos) },
                        reliefOn = settings.hillshadeOn,
                        onToggleRelief = { vm.toggleHillshade() },
                        onClose = { chromeState.basemapControlOpen = false },
                    )
                }
            }
            OfflineFlowUi(
                offline = offline,
                download = offlineDownload,
                chrome = chrome,
                vm = vm,
                currentProvider = providers.firstOrNull { it.id == settings.defaultBasemapId },
                styleJson = style?.styleJson,
                styleUrl = style?.styleUrl,
                // La case n'apparait que si la couche des points d'interet est allumee : proposer
                // d'emporter ce qu'on ne peut pas afficher n'aurait aucun sens.
                poiAvailable = settings.poiEnabled,
            )
            // Le rond d'attente, en DERNIER dans la pile de la carte : il annonce ce qui se prepare, et
            // doit se voir par-dessus les calques comme par-dessus les commandes.
            if (offline.preparing) BusySpinner()
        }
    }

    /*
     * Tout ce qui se pose PAR-DESSUS la carte : treize boites, rendues d'un seul endroit (cf. MainDialogs).
     *
     * Hors du tiroir et hors de la boite de la carte, comme elles l'ont toujours ete : une boite de
     * dialogue se place elle-meme au-dessus de tout, et l'inclure dans la pile de la carte lui donnerait
     * pour voisins des calques dont elle n'a que faire.
     */
    MainDialogs(
        folders = folders,
        importFlow = importFlow,
        dialogs = dialogs,
        edit = edit,
        alert = alert,
        planner = planner,
        location = location,
        vm = vm,
        selectedFeature = selectedFeature,
        schema = markerLayerData?.schema ?: emptyList(),
        currentPositionLabel = currentPositionLabel,
        onPickImage = { onImported -> dialogs.awaitImage(onImported); imagePicker.launch("image/*") },
        routeGpx = { name -> routeGpx(name) },
    )
}

/** Propositions demandées au géocodeur. Plus que les 4 visibles : le défilement de la liste n'a de sens
 *  que s'il y a de quoi défiler, et le service facture le même aller-retour dans les deux cas. */
private const val GeocodeResultLimit = 10

/** Nombre maximal de sommets retenus du parcours calcule pour border le couloir des points d'interet
 *  (cf. PoiCorridor) : le couloir se mesure en kilometres, un sommet tous les cent metres y suffit. */
private const val CorridorMaxPoints = 1_000

/** Part de la hauteur d'ecran que la bande du planificateur ne depasse jamais. */
private const val PlannerMaxHeightRatio = 0.6f
