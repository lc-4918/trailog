package fr.lc4918.trailog.ui.routes

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.ui.components.Avatar
import fr.lc4918.trailog.ui.components.BasemapControlPanel
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.MapLibreView
import fr.lc4918.trailog.ui.points.InfoBubble
import fr.lc4918.trailog.ui.points.PropertyEditor
import fr.lc4918.trailog.ui.profile.ElevationProfile
import fr.lc4918.trailog.ui.profile.SlopeLegend
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.net.toUri
import kotlin.math.floor

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

    val renderLayers by vm.renderLayers.collectAsState()
    val activeLayerId by vm.activeLayerId.collectAsState()
    val computed by vm.computed.collectAsState()
    val cursor by vm.cursor.collectAsState()
    val selectedMarkerId by vm.selectedMarkerId.collectAsState()
    val markerLayerData by vm.markerLayerData.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val controller = remember { MapController() }
    val ctx = LocalContext.current

    val mode = settings?.sideMenuMode ?: "both"
    controller.tapToleranceDp = settings?.tapToleranceDp ?: 16
    controller.rotateGesturesEnabled = settings?.rotateGesturesEnabled ?: false
    val style by vm.mapStyle.collectAsState()
    var styleTick by remember { mutableIntStateOf(0) }

    // ---------- position GPS ----------
    var gpsActive by remember { mutableStateOf(false) }
    // légère transparence du bouton GPS dès que la carte est bougée à la main ou qu'un menu s'ouvre
    var gpsButtonDimmed by remember { mutableStateOf(false) }
    // vrai tant qu'un recentrage automatique est dû (activation du capteur, ou retour au premier plan) :
    // consommé dès que la prochaine position arrive
    var pendingCenter by remember { mutableStateOf(false) }
    var lastUserLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) } // (lat, lon)
    var showLocationDisabledDialog by remember { mutableStateOf(false) }
    val locationManager = remember { ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val locationListener = remember {
        LocationListener { loc ->
            controller.setUserLocation(loc.longitude, loc.latitude, loc.accuracy)
            lastUserLocation = loc.latitude to loc.longitude
            if (pendingCenter) { controller.centerOn(loc.latitude, loc.longitude); pendingCenter = false }
        }
    }

    fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun stopGps() {
        locationManager.removeUpdates(locationListener)
        controller.clearUserLocation()
        gpsActive = false
        gpsButtonDimmed = false
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
            gpsButtonDimmed = false
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
    LaunchedEffect(drawerState.isOpen, settingsOpen) {
        if (gpsActive && (drawerState.isOpen || settingsOpen)) gpsButtonDimmed = true
    }
    DisposableEffect(Unit) { onDispose { locationManager.removeUpdates(locationListener) } }

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

    var bearingTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(controller) {
        controller.onPickPoint = { key, fid -> vm.onPickPoint(key, fid) }
        controller.onPickLine = { key, lon, lat -> vm.onPickLine(key, lon, lat) }
        controller.onTapEmpty = { vm.closeOnEmpty() }
        controller.onCameraMove = { bearingTick++ }
        controller.onUserMoveBegin = { if (gpsActive) gpsButtonDimmed = true }
    }
    val bearing = remember(bearingTick) { controller.bearing() }
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
    fun launchPicker() { picker.launch(settings?.importDir?.takeIf { it.isNotBlank() }?.let { it.toUri() }) }

    // import d'image pour un champ IMAGE d'infobulle (PropertyEditor) : callback enregistré au moment du tap
    var pendingImageCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u -> pendingImageCallback?.let { cb -> vm.importFeatureImage(u, cb) } }
    }

    // appliquer les couches à la carte
    val density = LocalDensity.current
    val markerPx = with(density) { (settings?.markerSize ?: 36).dp.toPx() }
    LaunchedEffect(renderLayers, styleTick, markerPx) { if (controller.style != null) controller.setLayers(renderLayers, markerPx) }
    LaunchedEffect(cursor, computed) {
        val idx = cursor; val s = computed?.samples
        if (idx != null && s != null && idx in s.indices) controller.setCursor(s[idx].lon, s[idx].lat)
        else controller.clearCursor()
    }
    LaunchedEffect(activeLayerId) { vm.activeLayer()?.let { controller.fitTo(it.west, it.south, it.east, it.north) } }

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

    // infobulle
    var idleTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(controller) {
        controller.onCameraIdle = {
            idleTick++
            if (positioned) controller.cameraState()?.let { (la, lo, z) -> vm.saveCameraState(la, lo, z) }
        }
    }
    var bubbleOffset by remember { mutableStateOf<IntOffset?>(null) }
    LaunchedEffect(selectedMarkerId, idleTick, renderLayers) {
        val f = vm.selectedFeature()
        bubbleOffset = f?.let { controller.screenOf(it.lon, it.lat)?.let { p -> IntOffset(p.x.toInt(), p.y.toInt()) } }
    }
    var editing by remember { mutableStateOf(false) }
    // conserve le dernier profil pour l'animation de disparition
    var lastComputed by remember { mutableStateOf<ComputedTrack?>(null) }
    LaunchedEffect(computed) { if (computed != null) lastComputed = computed }
    // 1er retour Android : ferme le profil s'il est affiché, au lieu du comportement par défaut.
    BackHandler(enabled = computed != null) { vm.closeProfile() }
    // Priorité plus haute (déclaré après = intercepté en premier) : si le menu latéral est ouvert,
    // le retour le referme d'abord, avant tout autre comportement (y compris le retour système par défaut).
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = mode != "burger" && drawerState.isOpen,   // swipe-fermeture uniquement quand ouvert
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxWidth()) {
                LegendContent(
                    folders = folders, layers = layers, settings = settings, vm = vm,
                    onSettings = { scope.launch { drawerState.snapTo(DrawerValue.Closed) }; onSettings() },
                    onClose = { scope.launch { drawerState.close() } },
                    onImport = { folderPicker = true },
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
            Column(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
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
                    Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (mode != "swipe") {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, stringResource(R.string.action_menu))
                            }
                        }
                        if (settings?.showGpsButton == true) {
                            IconButton(
                                onClick = { onGpsButtonTap() },
                                modifier = Modifier
                                    .alpha(if (gpsButtonDimmed) 0.6f else 1f)
                                    .background(
                                        if (gpsActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(8.dp)),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Place, stringResource(R.string.content_desc_gps_position), modifier = Modifier.size(16.dp),
                                        tint = if (gpsActive) Color.White else LocalContentColor.current)
                                    Text(stringResource(R.string.gps_label), fontSize = 7.sp, lineHeight = 7.sp,
                                        color = if (gpsActive) Color.White else LocalContentColor.current)
                                }
                            }
                            // recentre la carte sur la position GPS courante ; même style que le bouton menu
                            // (couleur, taille, transparence par défaut d'un IconButton)
                            if (gpsActive) {
                                IconButton(onClick = { recenterOnGps() }) {
                                    Icon(Icons.Filled.MyLocation, stringResource(R.string.action_center_on_location))
                                }
                            }
                        }
                    }
                    // réinitialisation de l'orientation (visible seulement si la carte est tournée) + Basemap Control
                    Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (kotlin.math.abs(bearing) > 0.5) {
                            IconButton(onClick = { controller.resetNorth() }) {
                                Icon(Icons.Filled.ArrowUpward, stringResource(R.string.action_reset_north),
                                    modifier = Modifier.graphicsLayer { rotationZ = -bearing.toFloat() })
                            }
                        }
                        if (settings?.showBasemapControlButton == true) {
                            IconButton(onClick = { basemapControlOpen = true }) {
                                // Outlined plutôt que Filled : la version pleine a sa couche du haut remplie
                                // en noir, ce qui contraste avec les autres boutons de la carte (tous en contour).
                                Icon(Icons.Outlined.Layers, stringResource(R.string.content_desc_basemap_control))
                            }
                        }
                    }
                    // infobulle bornée à l'écran
                    val feature = vm.selectedFeature()
                    val off = bubbleOffset
                    if (feature != null && off != null && !editing) {
                        val d = LocalDensity.current
                        val cardW = with(d) { 280.dp.toPx() }; val cardH = with(d) { 240.dp.toPx() }
                        val maxW = constraints.maxWidth.toFloat(); val maxH = constraints.maxHeight.toFloat()
                        var bx = off.x - cardW / 2f
                        var by = off.y + 30f
                        if (by + cardH > maxH) by = off.y - cardH - 30f
                        bx = bx.coerceIn(8f, (maxW - cardW - 8f).coerceAtLeast(8f))
                        by = by.coerceIn(8f, (maxH - cardH - 8f).coerceAtLeast(8f))
                        Box(Modifier.offset { IntOffset(bx.toInt(), by.toInt()) }) {
                            InfoBubble(feature = feature, schema = markerLayerData?.schema ?: emptyList(),
                                fontSp = settings?.bubbleFont ?: 14, bold = settings?.bubbleBold ?: false,
                                titleFontSp = settings?.bubbleTitleFont ?: 14, titleBold = settings?.bubbleTitleBold ?: true,
                                onEdit = { editing = true }, onClose = { vm.closeMarker() })
                        }
                    }
                    // échelle graphique (uniquement quand le profil n'est pas actif)
                    if (computed == null && settings?.showScale != false) {
                        ScaleBar(controller, idleTick, maxWidthPx = constraints.maxWidth * 0.40f,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp, end = 16.dp).navigationBarsPadding())
                    }
                    // infos du point courant : flottent au-dessus de la carte, juste au-dessus du titre du profil,
                    // pas incluses dans le fond du panneau de profil (largeur adaptée au contenu, pas plein écran)
                    val cIdx = cursor; val cComputed = lastComputed
                    if (computed != null && cComputed != null && cIdx != null && cIdx in cComputed.samples.indices) {
                        val imp = settings?.units == "imperial"
                        Text(cursorInfoText(cComputed.samples[cIdx], settings?.cursorInfos ?: "dist,ele,slope", imp),
                            fontSize = (settings?.profCursorFont ?: 11).sp,
                            fontWeight = if (settings?.profCursorBold == true) FontWeight.Bold else null,
                            color = Color.Black,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp)
                                .background(Color.White.copy(alpha = 0.7f))
                                .padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                // profil (visible au tap sur une trace) — animé pour un décalage doux de la carte
                AnimatedVisibility(visible = computed != null, enter = expandVertically(), exit = shrinkVertically()) {
                    val c = lastComputed
                    if (c != null) {
                        val imp = settings?.units == "imperial"
                        val profileLayer = vm.activeLayer()
                        val lineCol = profileLayer?.let { Color(android.graphics.Color.parseColor(it.color)) }
                            ?: MaterialTheme.colorScheme.primary
                        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).navigationBarsPadding()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(profileLayer?.name ?: "", fontSize = (settings?.profTitleFont ?: 13).sp,
                                    fontWeight = if (settings?.profTitleBold != false) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1, modifier = Modifier.padding(end = 8.dp))
                                Text(titleInfoText(c.stats, settings?.titleInfos ?: "dist,asc,desc,dur", imp),
                                    fontSize = (settings?.profBarFont ?: 11).sp,
                                    fontWeight = if (settings?.profBarBold == true) FontWeight.Bold else null,
                                    modifier = Modifier.weight(1f))
                            }
                            if (settings?.profileSlope != false && settings?.profileSlopeLegend != false) {
                                SlopeLegend(c.stats.maxAbsSlope, settings?.profLegendFont ?: 9,
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    bold = settings?.profLegendBold == true)
                            }
                            ElevationProfile(
                                samples = c.samples, stats = c.stats,
                                grid = settings?.profileGrid ?: true,
                                slope = settings?.profileSlope ?: true,
                                lineColor = lineCol,
                                axisFontSp = settings?.profAxisFont ?: 9,
                                axisBold = settings?.profAxisBold == true,
                                cursorIndex = cursor, onScrub = { vm.setCursor(it) },
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                            )
                        }
                    }
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
                        backgroundAlpha = (settings?.basemapControlOpacityPct ?: 20) / 100f,
                        onSelect = { id -> vm.selectBasemap(id); basemapControlOpen = false },
                        onCreateFolder = { name, parentId -> vm.createBasemapFolder(name, parentId) },
                        onReorderDrop = { k, id, tk, tid, pos -> vm.reorderBasemapDrop(k, id, tk, tid, pos) },
                        onToggleRelief = { id -> vm.toggleProviderEnabled(id) },
                        onClose = { basemapControlOpen = false },
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
                        Icon(Icons.Filled.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.label_new_folder))
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

    // création d'un dossier puis poursuite de l'import dedans
    if (newFolderDialog) {
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text(stringResource(R.string.label_new_folder)) },
            text = {
                CompactOutlinedTextField(newFolderName, { newFolderName = it }, singleLine = true,
                    modifier = Modifier.focusRequester(focus))
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
        val f = vm.selectedFeature()
        if (f != null) PropertyEditor(
            feature = f, schema = markerLayerData?.schema ?: emptyList(),
            onSave = { vm.saveFeature(it); editing = false }, onCancel = { editing = false },
            onPickImage = { onImported -> pendingImageCallback = onImported; imagePicker.launch("image/*") },
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
    onSettings: () -> Unit, onClose: () -> Unit, onImport: () -> Unit,
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

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        // Header 2 lignes à hauteur totale inchangée (SPEC §6.1) : l'ancien Row faisait 48dp de
        // contenu (IconButton) + 32dp de padding vertical = 80dp. On désactive le plancher tactile
        // de 48dp de Material3 (cf. Groupe N) pour tenir 2 lignes de 32dp dans le même budget.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSettings, modifier = Modifier.size(32.dp)) {
                            Avatar(settings?.avatarSource ?: "", size = 30.dp, contentDescription = stringResource(R.string.settings_title))
                        }
                        Spacer(Modifier.width(8.dp))
                        // Même taille/graisse que le titre "Réglages" (TopAppBar, titleLarge) : cohérence
                        // entre les deux menus (bug 2.1). Fallback traduit si le titre personnalisé est vide,
                        // au lieu de ne rien afficher.
                        val title = settings?.customTitle?.ifBlank { stringResource(R.string.drawer_default_title) }
                            ?: stringResource(R.string.drawer_default_title)
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // Boutons avec contour + libellé (au lieu de simples icônes), centrés horizontalement
                        // dans la ligne (poids égaux de chaque côté), espacement entre eux agrandi de 50 % (4dp -> 6dp).
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = onImport, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            Icon(Icons.Filled.FileUpload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_import))
                        }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = { openNewFolder(null) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.label_new_folder))
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
                // Décalé au maximum vers l'angle haut-droit (SPEC §6.1), superposé aux 2 lignes ci-dessus
                // sans agrandir la hauteur du Box (32dp < hauteur totale du Column).
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close_menu), Modifier.size(18.dp))
                }
            }
        }
        HorizontalDivider()

        combinedChildren(null, folders, layers).forEach { item ->
            when (item) {
                is FolderEntity -> key("folder", item.id) {
                    FolderNode(item, folders, layers, 0, vm, dctx, openRename, openMove, openNewFolder, onZoom)
                }
                is LayerEntity -> key("layer", item.id) { LayerRow(item, 0, vm, dctx, openRename, openMove, onZoom) }
            }
        }
    }

    if (newFolderDialog) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text(stringResource(R.string.label_new_folder)) },
            text = {
                CompactOutlinedTextField(newFolderName, { newFolderName = it }, singleLine = true,
                    modifier = Modifier.focusRequester(focus))
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
) {
    var expanded by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val isDragging = dctx.dragInfo?.kind == "folder" && dctx.dragInfo?.id == folder.id
    val offset = if (isDragging) dctx.dragInfo!!.offset else 0f
    val hoverZone = dctx.hoverTarget?.takeIf { it.kind == "folder" && it.id == folder.id }?.zone

    if (hoverZone == HoverZone.BEFORE) DropIndicatorLine()
    Row(Modifier.fillMaxWidth()
        .onGloballyPositioned { dctx.rowBounds["folder" to folder.id] = it.positionInRoot().y }
        .zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer { translationY = offset; alpha = if (isDragging) 0.85f else 1f }
        .background(if (hoverZone == HoverZone.INTO) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
        .padding(start = (4 + depth * 16).dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val allVisible = folderAllVisible(folder.id, allFolders, allLayers)
        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(40.dp)) {
            Icon(if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight, if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand))
        }
        IconButton(onClick = { vm.setFolderVisible(folder.id, !allVisible) }, modifier = Modifier.size(40.dp)) {
            Icon(if (allVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                if (allVisible) stringResource(R.string.action_hide_folder) else stringResource(R.string.action_show_folder))
        }
        // Couleur adaptée au thème (clair/sombre), pas figée : contour (fermé) ou remplissage (ouvert)
        // noir en thème clair, blanc en thème sombre (bug 4.1). Même silhouette pleine (Filled.Folder) dans
        // les deux états : Filled.FolderOpen ne remplit que l'onglet arrière, pas tout le dossier.
        Icon(if (expanded) Icons.Filled.Folder else Icons.Outlined.Folder, null, Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(6.dp))
        Text(folder.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        DragHandle(
            onStart = { strongHaptic(context); dctx.onStart("folder", folder.id) },
            onDrag = { dctx.onDrag("folder", folder.id, it) },
            onEnd = { dctx.onEnd("folder", folder.id) })
        Spacer(Modifier.width(6.dp))
        RowMenu(onRename = { onRename("folder", folder.id, folder.name) }, onMove = { onMove("folder", folder.id) },
            onNewSub = { onNewFolder(folder.id) }, onDelete = { vm.deleteFolder(folder) },
            onZoom = { onZoom("folder", folder.id) })
    }
    if (hoverZone == HoverZone.AFTER) DropIndicatorLine()
    if (expanded) {
        combinedChildren(folder.id, allFolders, allLayers).forEach { item ->
            when (item) {
                is FolderEntity -> key("folder", item.id) {
                    FolderNode(item, allFolders, allLayers, depth + 1, vm, dctx, onRename, onMove, onNewFolder, onZoom)
                }
                is LayerEntity -> key("layer", item.id) { LayerRow(item, depth + 1, vm, dctx, onRename, onMove, onZoom) }
            }
        }
    }
}

/** Icône globe si la couche a points ET lignes, ligne (trace) si lignes seules, sinon point. */
@Composable
private fun LayerRow(
    layer: LayerEntity, depth: Int, vm: MainViewModel, dctx: DragCtx,
    onRename: (String, Long, String) -> Unit, onMove: (String, Long) -> Unit, onZoom: (String, Long) -> Unit,
) {
    LayerLine(
        kind = "layer", id = layer.id,
        depth = depth, color = layer.color, name = layer.name, visible = layer.visible,
        icon = when {
            layer.hasLine && layer.hasPoints -> Icons.Filled.Public
            layer.hasLine -> Icons.Filled.Route
            else -> Icons.Filled.Place
        },
        onToggle = { vm.setLayerVisible(layer, it) }, onColor = { vm.setLayerColor(layer, it) }, dctx = dctx,
        onRename = { onRename("layer", layer.id, layer.name) }, onMove = { onMove("layer", layer.id) },
        onDelete = { vm.deleteLayer(layer) }, onZoom = { onZoom("layer", layer.id) },
    )
}

/** Ligne couche : œil + symbole couleur + nom + poignée + menu. */
@Composable
private fun LayerLine(
    kind: String, id: Long,
    depth: Int, color: String, name: String, visible: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onToggle: (Boolean) -> Unit, onColor: (String) -> Unit, dctx: DragCtx,
    onRename: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit, onZoom: () -> Unit,
) {
    var showColor by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isDragging = dctx.dragInfo?.kind == kind && dctx.dragInfo?.id == id
    val offset = if (isDragging) dctx.dragInfo!!.offset else 0f
    val hoverZone = dctx.hoverTarget?.takeIf { it.kind == kind && it.id == id }?.zone

    if (hoverZone == HoverZone.BEFORE) DropIndicatorLine()
    Row(Modifier.fillMaxWidth()
        .onGloballyPositioned { dctx.rowBounds[kind to id] = it.positionInRoot().y }
        .zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer { translationY = offset; alpha = if (isDragging) 0.85f else 1f }
        .padding(start = (4 + depth * 16).dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onToggle(!visible) }, modifier = Modifier.size(40.dp)) {
            Icon(if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                if (visible) stringResource(R.string.action_hide) else stringResource(R.string.action_show))
        }
        IconButton(onClick = { showColor = true }, modifier = Modifier.size(40.dp)) {
            Icon(icon, stringResource(R.string.action_color), tint = Color(android.graphics.Color.parseColor(color)))
        }
        Spacer(Modifier.width(2.dp))
        Text(name, modifier = Modifier.weight(1f),
            color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        DragHandle(
            onStart = { strongHaptic(context); dctx.onStart(kind, id) },
            onDrag = { dctx.onDrag(kind, id, it) },
            onEnd = { dctx.onEnd(kind, id) })
        Spacer(Modifier.width(6.dp))
        RowMenu(onRename = onRename, onMove = onMove, onNewSub = null, onDelete = onDelete, onZoom = onZoom)
    }
    if (hoverZone == HoverZone.AFTER) DropIndicatorLine()
    if (showColor) ColorPickerDialog(color, onPick = { onColor(it); showColor = false }, onDismiss = { showColor = false })
}

@Composable
private fun DropIndicatorLine() {
    Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary))
}

private val PALETTE = listOf(
    "#1F6FB2", "#1098AD", "#2F9E44", "#7CB342", "#F4B400", "#F08C00", "#E8590C", "#E03131",
    "#C2185B", "#9C36B5", "#6741D9", "#3949AB", "#00897B", "#6D4C41", "#546E7A", "#212121",
)

@Composable
private fun ColorPickerDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_color_title)) },
        text = {
            Column {
                PALETTE.chunked(4).forEach { row ->
                    Row {
                        row.forEach { hex ->
                            Box(
                                Modifier.padding(6.dp).size(40.dp).clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .clickable { onPick(hex) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (hex.equals(current, true)) Icon(Icons.Filled.Check, null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
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
        Icons.Filled.DragHandle, stringResource(R.string.action_drag_to_move),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(26.dp).pointerInput(Unit) {
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
private fun RowMenu(onRename: () -> Unit, onMove: () -> Unit, onNewSub: (() -> Unit)?, onDelete: () -> Unit, onZoom: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_zoom_to_layer)) }, onClick = { open = false; onZoom() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { open = false; onRename() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_move)) }, onClick = { open = false; onMove() })
            if (onNewSub != null) DropdownMenuItem(text = { Text(stringResource(R.string.action_new_subfolder)) }, onClick = { open = false; onNewSub() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { open = false; onDelete() })
        }
    }
}

/* ----------------------- Infos profil configurables ----------------------- */

private fun titleInfoText(stats: fr.lc4918.trailog.domain.model.TrackStats, csv: String, imp: Boolean): String =
    csv.split(",").mapNotNull { k ->
        when (k.trim()) {
            "dist" -> Format.distance(stats.distance, imp)
            "asc" -> "D+ ${Format.elevation(stats.ascent, imp)}"
            "desc" -> "D- ${Format.elevation(stats.descent, imp)}"
            "dur" -> stats.duration?.let { Format.duration(it) }
            "min" -> "min ${Format.elevation(stats.min, imp)}"
            "max" -> "max ${Format.elevation(stats.max, imp)}"
            else -> null
        }
    }.joinToString(" · ")

private fun cursorInfoText(s: fr.lc4918.trailog.domain.model.Sample, csv: String, imp: Boolean): String =
    csv.split(",").mapNotNull { k ->
        when (k.trim()) {
            "dist" -> Format.distance(s.x, imp)
            "ele" -> Format.elevation(s.z, imp)
            "slope" -> "${"%.1f".format(s.slope)}%"
            "time" -> s.t?.let { Format.duration(it) }
            else -> null
        }
    }.joinToString(" · ")

@Composable
private fun ScaleBar(controller: MapController, tick: Int, maxWidthPx: Float, modifier: Modifier = Modifier) {
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
    val strokeColor = Color.Black.copy(alpha = 0.7f)
    val bgAlpha = 0.7f
    Column(
        // Padding du haut supprimé (le fond blanc ne doit pas déborder au-dessus du texte) ; celui du bas
        // reste pour ne pas coller le trait horizontal au bord de la carte.
        modifier.background(Color.White.copy(alpha = bgAlpha)).padding(start = 2.dp, end = 2.dp, top = 0.dp, bottom = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // lineHeight réduit sous fontSize : resserre vraiment la boîte du texte (hauteur réservée), au lieu
        // de juste déplacer son rendu. Décalé vers le bas de la moitié de la hauteur des ticks pour que le
        // texte descende plus bas que leur extrémité haute (sans quoi il reste entièrement au-dessus).
        Text(
            label, fontSize = fontSizeSp.sp, lineHeight = (fontSizeSp * 0.8f).sp, color = Color.Black,
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
    val pow = Math.pow(10.0, floor(Math.log10(max)))
    return when { max / pow >= 5 -> 5 * pow; max / pow >= 2 -> 2 * pow; else -> pow }
}

/** Vrai si toutes les couches du dossier (et de ses sous-dossiers) sont visibles (vrai aussi si aucune couche). */
private fun folderAllVisible(folderId: Long, folders: List<FolderEntity>, layers: List<LayerEntity>): Boolean {
    val ids = HashSet<Long>(); val stack = ArrayDeque<Long>(); stack.add(folderId)
    while (stack.isNotEmpty()) { val f = stack.removeLast(); if (ids.add(f)) folders.filter { it.parentId == f }.forEach { stack.add(it.id) } }
    val ls = layers.filter { it.folderId in ids }
    return ls.isEmpty() || ls.all { it.visible }
}

private fun folderBbox(folderId: Long, folders: List<FolderEntity>, layers: List<LayerEntity>): DoubleArray? {
    val ids = HashSet<Long>(); val stack = ArrayDeque<Long>(); stack.add(folderId)
    while (stack.isNotEmpty()) { val f = stack.removeLast(); if (ids.add(f)) folders.filter { it.parentId == f }.forEach { stack.add(it.id) } }
    val ls = layers.filter { it.folderId in ids }
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
