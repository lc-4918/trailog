package fr.lc4918.trailog.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import fr.lc4918.trailog.map.offline.OfflineThumbnails
import java.io.File
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.LocalePrefs
import fr.lc4918.trailog.data.db.CompositeEntity
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.data.repo.StoragePaths
import fr.lc4918.trailog.map.compositeBasemapId
import fr.lc4918.trailog.map.flagAssetModel
import fr.lc4918.trailog.map.flagCodeFor
import fr.lc4918.trailog.ui.components.Avatar
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val s by vm.settings.collectAsState()
    val providers by vm.providers.collectAsState()
    val composites by vm.composites.collectAsState()
    val status by vm.status.collectAsState()
    val cur = s ?: return
    val ctx = LocalContext.current

    val snackbar = remember { SnackbarHostState() }
    val mbPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.importMbtiles(it) }
    }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            val path = StoragePaths.treeUriToPath(ctx, it) ?: it.toString()
            vm.save(cur.copy(mbtilesDir = path))
        }
    }
    val importDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.save(cur.copy(importDir = it.toString()))
        }
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.importAvatarImage(it) }
    }
    LaunchedEffect(status) { status?.let { snackbar.showSnackbar(it); vm.clearStatus() } }

    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.settings_tab_map) to Icons.Filled.Map,
        stringResource(R.string.settings_tab_tiles) to Icons.Filled.Layers,
        stringResource(R.string.settings_tab_profile) to Icons.AutoMirrored.Filled.ShowChart,
        stringResource(R.string.settings_tab_general) to Icons.Filled.Tune,
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = { Avatar(cur.avatarSource, size = 30.dp, modifier = Modifier.padding(end = 16.dp)) })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        // Désactive l'agrandissement automatique de la zone tactile minimale (48dp) que Material3 impose
        // en interne à IconButton/Switch/Slider : sans ça, un Modifier.size() plus petit posé sur ces
        // composants est silencieusement ignoré pour l'espace réellement réservé (cf. bug 2.1/2.2).
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, (label, icon) ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        text = { Text(label) }, icon = { Icon(icon, null) })
                }
            }
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(250)) { dir * it } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(250)) { -dir * it } + fadeOut(tween(200)))
                },
                label = "settings_tab"
            ) { currentTab ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    when (currentTab) {
                        0 -> MapTab(cur, vm)
                        1 -> TilesTab(cur, providers, composites, vm, onPickMbtiles = { mbPicker.launch("*/*") })
                        2 -> ProfileTab(cur, vm)
                        else -> SystemTab(cur, vm,
                            onPickImportDir = { importDirPicker.launch(null) },
                            onPickMbtilesFolder = { treePicker.launch(null) },
                            onPickAvatar = { avatarPicker.launch("image/*") })
                    }
                }
            }
        }
        }
    }
}

/* --------------- Onglets --------------- */

@Composable private fun MapTab(cur: SettingsEntity, vm: SettingsViewModel) {
    Section(stringResource(R.string.settings_section_gps_position))
    SwitchRow(stringResource(R.string.settings_show_gps_button), cur.showGpsButton) { vm.save(cur.copy(showGpsButton = it)) }
    Section(stringResource(R.string.settings_section_rotation))
    SwitchRow(stringResource(R.string.settings_allow_rotation), cur.rotateGesturesEnabled) { vm.save(cur.copy(rotateGesturesEnabled = it)) }
    Section(stringResource(R.string.settings_section_basemap_control))
    SwitchRow(stringResource(R.string.settings_show_basemap_control_button), cur.showBasemapControlButton) {
        vm.save(cur.copy(showBasemapControlButton = it))
    }
    Text(stringResource(R.string.settings_basemap_control_width, cur.basemapControlWidthPct), style = MaterialTheme.typography.bodyMedium)
    CompactSlider(value = cur.basemapControlWidthPct.toFloat(), valueRange = 20f..90f,
        onValueChange = { vm.save(cur.copy(basemapControlWidthPct = it.toInt())) })
    Text(stringResource(R.string.settings_basemap_control_opacity, cur.basemapControlOpacityPct), style = MaterialTheme.typography.bodyMedium)
    CompactSlider(value = cur.basemapControlOpacityPct.toFloat(), valueRange = 0f..90f,
        onValueChange = { vm.save(cur.copy(basemapControlOpacityPct = it.toInt())) })
    TextButton(onClick = { vm.save(cur.copy(basemapControlWidthPct = 50, basemapControlOpacityPct = 20)) }) {
        Text(stringResource(R.string.action_reset_defaults))
    }
    Section(stringResource(R.string.settings_section_scale))
    SwitchRow(stringResource(R.string.settings_show_scale_bar), cur.showScale) { vm.save(cur.copy(showScale = it)) }
    Section(stringResource(R.string.settings_section_markers))
    FontStepper(stringResource(R.string.settings_marker_size), cur.markerSize, min = 16, max = 80) { vm.save(cur.copy(markerSize = it)) }
    Section(stringResource(R.string.settings_section_bubbles))
    FontStepper(stringResource(R.string.settings_font_size), cur.bubbleFont, bold = cur.bubbleBold, onBold = { vm.save(cur.copy(bubbleBold = it)) }) { vm.save(cur.copy(bubbleFont = it)) }
    FontStepper(stringResource(R.string.font_title), cur.bubbleTitleFont, bold = cur.bubbleTitleBold, onBold = { vm.save(cur.copy(bubbleTitleBold = it)) }) { vm.save(cur.copy(bubbleTitleFont = it)) }
}

@Composable private fun TilesTab(
    cur: SettingsEntity, providers: List<ProviderEntity>, composites: List<CompositeEntity>, vm: SettingsViewModel,
    onPickMbtiles: () -> Unit,
) {
    val mapProviders = providers.filter { it.type != "DEM" && !it.transparent && it.enabled }
    val mapComposites = composites.filter { it.enabled }
    // Le Relief (type DEM) est un fournisseur standard (URL, activation, édition) : il suit les mêmes
    // règles que les autres et apparaît donc dans "Gérer les fournisseurs", pas dans les fonds MBTILES (bug 3.1).
    val basemapEntries = providers.filter { it.type == "MBTILES" }
    val otherProviders = providers.filter { it.type != "MBTILES" }

    Section(stringResource(R.string.settings_section_default_basemap))
    BasemapPicker(mapProviders, mapComposites, cur.defaultBasemapId) { vm.save(cur.copy(defaultBasemapId = it)) }

    Section(stringResource(R.string.settings_section_basemaps))
    OutlinedButton(onClick = onPickMbtiles, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.FileDownload, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_import_mbtiles))
    }
    var creatingComposite by remember { mutableStateOf(false) }
    var editingComposite by remember { mutableStateOf<CompositeEntity?>(null) }
    OutlinedButton(onClick = { creatingComposite = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Layers, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_create_composite))
    }
    Spacer(Modifier.height(8.dp))
    // Dossier réel des .mbtiles (miroir de CycleRepository.mbtilesDir) : affiché dans l'éditeur MBTILES.
    val ctx = LocalContext.current
    val mbtilesDirPath = if (cur.mbtilesDir.isBlank()) File(ctx.filesDir, "mbtiles").absolutePath else cur.mbtilesDir
    basemapEntries.forEach { p ->
        val onDelete: (() -> Unit)? = if (!p.builtin) { { vm.deleteProvider(p) } } else null
        ProviderRow(p, onSave = vm::saveProvider, onDelete = onDelete, mbtilesDirPath = mbtilesDirPath)
    }
    composites.forEach { c ->
        CompositeRow(c, onToggle = { vm.saveComposite(c.copy(enabled = it)) },
            onEdit = { editingComposite = c }, onDelete = { vm.deleteComposite(c) })
    }
    if (creatingComposite) {
        CompositeEditorDialog(null, providers,
            onSave = { vm.saveComposite(it); creatingComposite = false }, onDismiss = { creatingComposite = false })
    }
    editingComposite?.let { c ->
        CompositeEditorDialog(c, providers,
            onSave = { vm.saveComposite(it); editingComposite = null }, onDismiss = { editingComposite = null })
    }

    Section(stringResource(R.string.settings_section_providers))
    var providersDialogOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.action_manage_providers), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        CompactIconButton(onClick = { providersDialogOpen = true }, stringResource(R.string.action_manage_providers), Icons.AutoMirrored.Filled.OpenInNew)
    }
    if (providersDialogOpen) {
        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { vm.exportProviders(it) }
        }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { vm.requestImportProviders(it) }
        }
        val pendingImport by vm.pendingProvidersImport.collectAsState()
        Dialog(onDismissRequest = { providersDialogOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f), shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_section_providers), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        CompactIconButton(onClick = { exportLauncher.launch("trailog_providers.json") }, stringResource(R.string.action_export), Icons.Filled.FileUpload)
                        CompactIconButton(onClick = { importLauncher.launch("application/json") }, stringResource(R.string.action_import), Icons.Filled.FileDownload)
                        IconButton(onClick = { providersDialogOpen = false }) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        otherProviders.forEach { p -> ProviderRow(p, onSave = vm::saveProvider) }
                    }
                }
            }
        }
        if (pendingImport != null) {
            AlertDialog(
                onDismissRequest = { vm.cancelImportProviders() },
                title = { Text(stringResource(R.string.dialog_import_providers_title)) },
                text = { Text(stringResource(R.string.dialog_import_providers_text, pendingImport!!.size)) },
                confirmButton = { TextButton(onClick = { vm.confirmImportProviders() }) { Text(stringResource(R.string.action_import)) } },
                dismissButton = { TextButton(onClick = { vm.cancelImportProviders() }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

@Composable private fun CompositeRow(c: CompositeEntity, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        CompactSwitch(checked = c.enabled, onCheckedChange = onToggle)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.Layers, null, modifier = Modifier.size(CompactIconSize))
        Spacer(Modifier.width(6.dp))
        Text(c.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        CompactIconButton(onClick = onEdit, stringResource(R.string.action_edit), Icons.Filled.Edit)
        CompactIconButton(onClick = onDelete, stringResource(R.string.action_delete), Icons.Filled.DeleteOutline)
    }
}

@Composable private fun CompositeEditorDialog(
    existing: CompositeEntity?, providers: List<ProviderEntity>,
    onSave: (CompositeEntity) -> Unit, onDismiss: () -> Unit,
) {
    // Le relief peut être utilisé en overlay (premier plan, ex. superposé à une carte pays) mais jamais
    // comme arrière-plan (tuiles DEM brutes illisibles seules, cf. StyleBuilder).
    val bgSelectable = providers.filter { it.type != "DEM" }
    val fgSelectable = providers
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var opacityPct by remember { mutableStateOf(((existing?.foregroundOpacity ?: 0.5f) * 100).toInt()) }
    var fgId by remember { mutableStateOf(existing?.foregroundProviderId ?: fgSelectable.firstOrNull()?.id ?: "") }
    var bgId by remember { mutableStateOf(existing?.backgroundProviderId ?: bgSelectable.firstOrNull()?.id ?: "") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.8f),
            shape = MaterialTheme.shapes.large, tonalElevation = 4.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.dialog_composite_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                CompactOutlinedTextField(name, { name = it },
                    label = { Text(stringResource(R.string.field_composite_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.field_foreground_opacity, opacityPct), style = MaterialTheme.typography.bodyMedium)
                CompactSlider(value = opacityPct.toFloat(), valueRange = 0f..100f, onValueChange = { opacityPct = it.toInt() })
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FlipToFront, null, tint = Color.Red)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.label_foreground_layer), style = MaterialTheme.typography.labelMedium)
                }
                LayerSelect(fgSelectable, fgId) { fgId = it }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FlipToBack, null, tint = Color.Red)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.label_background_layer), style = MaterialTheme.typography.labelMedium)
                }
                LayerSelect(bgSelectable, bgId) { bgId = it }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (name.isNotBlank() && fgId.isNotBlank() && bgId.isNotBlank()) {
                            onSave(CompositeEntity(
                                id = existing?.id ?: 0, name = name,
                                backgroundProviderId = bgId, foregroundProviderId = fgId,
                                foregroundOpacity = opacityPct / 100f, enabled = existing?.enabled ?: true,
                                sortOrder = existing?.sortOrder ?: 0, folderId = existing?.folderId,
                            ))
                        }
                    }) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun LayerSelect(items: List<ProviderEntity>, current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = items.firstOrNull { it.id == current }?.name ?: current
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        CompactOutlinedTextField(value = name, onValueChange = {}, readOnly = true,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, textStyle = MaterialTheme.typography.bodyMedium)
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { p -> DropdownMenuItem(text = { ProviderOptionLabel(p) }, onClick = { onSelect(p.id); open = false }) }
        }
    }
}

@Composable private fun ProfileTab(cur: SettingsEntity, vm: SettingsViewModel) {
    Section(stringResource(R.string.settings_section_display))
    SwitchRow(stringResource(R.string.settings_profile_grid), cur.profileGrid) { vm.save(cur.copy(profileGrid = it)) }
    SwitchRow(stringResource(R.string.settings_profile_color_by_slope), cur.profileSlope) { vm.save(cur.copy(profileSlope = it)) }
    SwitchRow(stringResource(R.string.settings_profile_slope_legend), cur.profileSlopeLegend) { vm.save(cur.copy(profileSlopeLegend = it)) }
    Section(stringResource(R.string.settings_section_title_line_info))
    InfoChips(listOf("dist" to stringResource(R.string.chip_distance), "asc" to stringResource(R.string.chip_ascent), "desc" to stringResource(R.string.chip_descent), "dur" to stringResource(R.string.chip_duration), "min" to stringResource(R.string.chip_alt_min), "max" to stringResource(R.string.chip_alt_max)),
        cur.titleInfos) { vm.save(cur.copy(titleInfos = it)) }
    Section(stringResource(R.string.settings_section_cursor_info))
    InfoChips(listOf("dist" to stringResource(R.string.chip_distance), "ele" to stringResource(R.string.chip_altitude), "slope" to stringResource(R.string.chip_slope), "time" to stringResource(R.string.chip_time)),
        cur.cursorInfos) { vm.save(cur.copy(cursorInfos = it)) }
    Section(stringResource(R.string.settings_section_font_sizes))
    FontStepper(stringResource(R.string.font_axes), cur.profAxisFont, bold = cur.profAxisBold, onBold = { vm.save(cur.copy(profAxisBold = it)) }) { vm.save(cur.copy(profAxisFont = it)) }
    FontStepper(stringResource(R.string.font_title), cur.profTitleFont, bold = cur.profTitleBold, onBold = { vm.save(cur.copy(profTitleBold = it)) }) { vm.save(cur.copy(profTitleFont = it)) }
    FontStepper(stringResource(R.string.font_title_bar_info), cur.profBarFont, bold = cur.profBarBold, onBold = { vm.save(cur.copy(profBarBold = it)) }) { vm.save(cur.copy(profBarFont = it)) }
    FontStepper(stringResource(R.string.settings_profile_slope_legend), cur.profLegendFont, bold = cur.profLegendBold, onBold = { vm.save(cur.copy(profLegendBold = it)) }) { vm.save(cur.copy(profLegendFont = it)) }
    FontStepper(stringResource(R.string.font_cursor_point), cur.profCursorFont, bold = cur.profCursorBold, onBold = { vm.save(cur.copy(profCursorBold = it)) }) { vm.save(cur.copy(profCursorFont = it)) }
}

@Composable private fun SystemTab(
    cur: SettingsEntity, vm: SettingsViewModel,
    onPickImportDir: () -> Unit, onPickMbtilesFolder: () -> Unit, onPickAvatar: () -> Unit,
) {
    val ctx = LocalContext.current

    Section(stringResource(R.string.settings_section_import_folder))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (cur.importDir.isBlank()) stringResource(R.string.settings_default_system) else (Uri.parse(cur.importDir).lastPathSegment ?: stringResource(R.string.settings_default_system)),
            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onPickImportDir) { Icon(Icons.Filled.Folder, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_browse)) }
    }
    Section(stringResource(R.string.settings_section_mbtiles_folder))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (cur.mbtilesDir.isBlank()) stringResource(R.string.settings_app_folder_default) else StoragePaths.displayName(cur.mbtilesDir),
            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onPickMbtilesFolder) { Icon(Icons.Filled.Folder, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_browse)) }
    }
    Section(stringResource(R.string.settings_section_side_menu))
    SegRow(listOf("burger" to stringResource(R.string.side_menu_burger), "swipe" to stringResource(R.string.side_menu_swipe), "both" to stringResource(R.string.side_menu_both)), cur.sideMenuMode) { vm.save(cur.copy(sideMenuMode = it)) }
    Section(stringResource(R.string.settings_section_status_bar))
    SwitchRow(stringResource(R.string.settings_status_bar_transparent), cur.statusBarTransparent) { vm.save(cur.copy(statusBarTransparent = it)) }
    Section(stringResource(R.string.settings_section_tap_tolerance, cur.tapToleranceDp))
    CompactSlider(value = cur.tapToleranceDp.toFloat(), valueRange = 4f..40f, steps = 35,
        onValueChange = { vm.save(cur.copy(tapToleranceDp = it.toInt())) })
    Section(stringResource(R.string.settings_section_units))
    SegRow(listOf("meters" to stringResource(R.string.unit_metric), "imperial" to stringResource(R.string.unit_imperial)), cur.units) { vm.save(cur.copy(units = it)) }

    Section(stringResource(R.string.settings_section_language))
    var currentLang by remember { mutableStateOf(LocalePrefs.get(ctx)) }
    LanguagePicker(currentLang) { code ->
        currentLang = code
        LocalePrefs.set(ctx, code)
        (ctx as? Activity)?.recreate()
    }

    Section(stringResource(R.string.settings_section_theme))
    SegRow(listOf("system" to stringResource(R.string.theme_system), "light" to stringResource(R.string.theme_light), "dark" to stringResource(R.string.theme_dark)), cur.theme) { vm.save(cur.copy(theme = it)) }

    Section(stringResource(R.string.font_title))
    var titleText by remember(cur.customTitle) { mutableStateOf(cur.customTitle) }
    CompactOutlinedTextField(titleText, { titleText = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = { vm.save(cur.copy(customTitle = titleText)) }) { Text(stringResource(R.string.action_save)) }

    Section(stringResource(R.string.settings_section_avatar))
    var avatarDialogOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.action_change_avatar), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        CompactIconButton(onClick = { avatarDialogOpen = true }, stringResource(R.string.action_change_avatar), Icons.AutoMirrored.Filled.OpenInNew)
        if (cur.avatarSource.isNotBlank()) {
            CompactIconButton(onClick = { vm.save(cur.copy(avatarSource = "")) }, stringResource(R.string.action_reset_avatar), Icons.Filled.DeleteOutline)
        }
    }
    if (avatarDialogOpen) {
        var urlText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { avatarDialogOpen = false },
            title = { Text(stringResource(R.string.action_change_avatar)) },
            text = {
                Column {
                    Button(onClick = { onPickAvatar(); avatarDialogOpen = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_load_image_from_phone))
                    }
                    Spacer(Modifier.height(12.dp))
                    CompactOutlinedTextField(urlText, { urlText = it },
                        placeholder = { Text(stringResource(R.string.avatar_url_placeholder)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (urlText.isNotBlank()) vm.save(cur.copy(avatarSource = urlText))
                    avatarDialogOpen = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { avatarDialogOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Section(stringResource(R.string.settings_section_reset))
    var confirmReset by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_reset_all_settings))
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.action_reset_all_settings)) },
            text = { Text(stringResource(R.string.dialog_reset_all_settings_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetAllSettings()
                    confirmReset = false
                    (ctx as? Activity)?.recreate()
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/* --------------- Helpers (taille compacte : cf. bug 1.5) --------------- */

private val CompactIconButtonSize = 32.dp
private val CompactIconSize = 18.dp
private val CompactChipHeight = 28.dp

@Composable private fun Section(t: String) {
    Spacer(Modifier.height(10.dp)); Text(t, style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(4.dp))
}

@Composable private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        CompactSwitch(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun CompactIconButton(onClick: () -> Unit, contentDescription: String?, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    IconButton(onClick = onClick, modifier = Modifier.size(CompactIconButtonSize)) {
        Icon(icon, contentDescription, modifier = Modifier.size(CompactIconSize))
    }
}

/** Switch réellement compact : le track de [Switch] est en `requiredSize()` en interne (non
 *  surchargeable par un modifier extérieur), donc on le réduit par mise à l'échelle dans une Box
 *  dont la taille contrainte l'espace réellement réservé dans le Row parent. */
@Composable private fun CompactSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(Modifier.size(36.dp, 22.dp), contentAlignment = Alignment.Center) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.7f))
    }
}

/** Slider compact : le thumb accepte une taille personnalisée via [SliderDefaults.Thumb], mais le
 *  track par défaut a une hauteur fixe non surchageable — on le redessine donc à la main. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CompactSlider(
    value: Float, onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps,
        onValueChangeFinished = onValueChangeFinished, interactionSource = interactionSource,
        thumb = { SliderDefaults.Thumb(interactionSource = interactionSource, thumbSize = DpSize(14.dp, 14.dp)) },
        track = { state ->
            val fraction = ((state.value - state.valueRange.start) /
                (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
            }
        },
    )
}

@Composable private fun FontStepper(
    label: String, value: Int, min: Int = 7, max: Int = 28,
    bold: Boolean? = null, onBold: ((Boolean) -> Unit)? = null, onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (bold != null && onBold != null) {
            FilterChip(selected = bold, onClick = { onBold(!bold) },
                label = { Text(stringResource(R.string.settings_bold_abbrev), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(CompactChipHeight).padding(end = 4.dp))
        }
        CompactIconButton(onClick = { if (value > min) onChange(value - 1) }, stringResource(R.string.action_decrease), Icons.Filled.Remove)
        Text("$value", Modifier.widthIn(min = 22.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        CompactIconButton(onClick = { if (value < max) onChange(value + 1) }, stringResource(R.string.action_increase), Icons.Filled.Add)
    }
}

@Composable private fun InfoChips(options: List<Pair<String, String>>, csv: String, onChange: (String) -> Unit) {
    val sel = csv.split(",").map { it.trim() }.filter { it.isNotBlank() }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { (k, label) ->
            val on = k in sel
            FilterChip(selected = on, onClick = {
                val newSel = options.map { it.first }.filter { if (it == k) !on else it in sel }
                onChange(newSel.joinToString(","))
            }, label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(CompactChipHeight).padding(end = 4.dp))
        }
    }
}

@Composable private fun SegRow(options: List<Pair<String, String>>, value: String, onSelect: (String) -> Unit) {
    Row { options.forEach { (k, label) ->
        FilterChip(selected = value == k, onClick = { onSelect(k) },
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.height(CompactChipHeight).padding(end = 4.dp))
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BasemapPicker(providers: List<ProviderEntity>, composites: List<CompositeEntity>, current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val currentLabel = providers.firstOrNull { it.id == current }?.name
        ?: composites.firstOrNull { compositeBasemapId(it.id) == current }?.name
        ?: current
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        CompactOutlinedTextField(value = currentLabel, onValueChange = {}, readOnly = true,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, textStyle = MaterialTheme.typography.bodyMedium)
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            providers.forEach { p -> DropdownMenuItem(text = { ProviderOptionLabel(p) }, onClick = { onSelect(p.id); open = false }) }
            composites.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { onSelect(compositeBasemapId(c.id)); open = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/** Déclencheur du select construit à la main (Box + Row), plutôt que via les slots leadingIcon/trailingIcon
 *  de [CompactOutlinedTextField] : ces slots imposent un plancher de hauteur (zone tactile M3, ~48dp) non
 *  contournable de l'intérieur, ce qui gonflait le champ par rapport aux autres inputs compacts (bug 1.6).
 *  Le fond/bord réutilise [OutlinedTextFieldDefaults.Container] pour rester visuellement identique. */
@Composable private fun LanguagePicker(current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        Box(Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().clip(OutlinedTextFieldDefaults.shape)) {
            OutlinedTextFieldDefaults.Container(
                enabled = true, isError = false, interactionSource = interactionSource,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LanguageFlag(current)
                Spacer(Modifier.width(8.dp))
                Text(LocalePrefs.nativeName(current), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                ExposedDropdownMenuDefaults.TrailingIcon(open, modifier = Modifier.requiredSize(CompactIconSize))
            }
        }
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LocalePrefs.SELECTABLE.forEach { code ->
                DropdownMenuItem(
                    leadingIcon = { LanguageFlag(code) },
                    text = { Text(LocalePrefs.nativeName(code)) },
                    // Fermer avant de déclencher le changement de langue (qui recrée l'Activity) : évite
                    // tout flash du menu encore ouvert pendant la recréation (cf. bug 1.6).
                    onClick = { open = false; onSelect(code) },
                )
            }
        }
    }
}

@Composable private fun LanguageFlag(code: String) {
    val flag = LocalePrefs.flagCode(code) ?: return
    AsyncImage(model = flagAssetModel(flag), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(2.dp)))
}

/** Libellé d'un fond dans un select : drapeau pour un fond national détecté + nom de la couche. */
@Composable private fun ProviderOptionLabel(p: ProviderEntity) {
    val code = flagCodeFor(p)
    if (code != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = flagAssetModel(code), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Text(p.name)
        }
    } else {
        Text(p.name)
    }
}

/** Miniature titrée (SPEC §6), affichée en lecture seule côte à côte dans l'éditeur d'un MBTILES, avec
 *  un bouton d'agrandissement qui ouvre l'image en grand (~80 % de l'écran). */
@Composable private fun ThumbColumn(title: String, file: File, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val model = "file://${file.absolutePath}"
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth()) {
            AsyncImage(
                model = model, contentDescription = title, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            )
            ThumbOverlayButton(Icons.Filled.Fullscreen, stringResource(R.string.offline_thumb_expand),
                Modifier.align(Alignment.TopEnd).padding(4.dp)) { expanded = true }
        }
    }
    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.8f)) {
                Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                    AsyncImage(
                        model = model, contentDescription = title, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                }
                ThumbOverlayButton(Icons.Filled.FullscreenExit, stringResource(R.string.action_close),
                    Modifier.align(Alignment.TopEnd).padding(8.dp)) { expanded = false }
            }
        }
    }
}

/** Bouton d'icône noire sur fond opaque à 30 %, superposé au coin d'une miniature. */
@Composable private fun ThumbOverlayButton(icon: ImageVector, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(28.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
    ) {
        Icon(icon, desc, tint = Color.Black, modifier = Modifier.size(18.dp))
    }
}

@Composable private fun ProviderRow(
    p: ProviderEntity, onSave: (ProviderEntity) -> Unit, onDelete: (() -> Unit)? = null,
    mbtilesDirPath: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var name by remember(p.id) { mutableStateOf(p.name) }
    var url by remember(p.id) { mutableStateOf(p.urlTemplate) }
    var key by remember(p.id) { mutableStateOf(p.apiKey ?: "") }
    var tile by remember(p.id) { mutableStateOf(p.tileSize.toString()) }
    val isMbtiles = p.type == "MBTILES"
    val flagCode = flagCodeFor(p)
    fun resetFields() { name = p.name; url = p.urlTemplate; key = p.apiKey ?: ""; tile = p.tileSize.toString() }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompactSwitch(checked = p.enabled, onCheckedChange = { onSave(p.copy(enabled = it)) })
            Spacer(Modifier.width(4.dp))
            if (flagCode != null) {
                AsyncImage(model = flagAssetModel(flagCode), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(CompactIconSize).clip(RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(6.dp))
            }
            Text("${p.name}  (${p.type})", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            CompactIconButton(
                onClick = {
                    // Fermer sans "Enregistrer" annule les modifications en cours (SPEC §4.3, scénario 1).
                    if (expanded) resetFields()
                    expanded = !expanded
                },
                contentDescription = if (expanded) stringResource(R.string.action_close) else stringResource(R.string.action_edit),
                icon = if (expanded) Icons.Filled.Close else Icons.Filled.Edit,
            )
            if (onDelete != null) {
                CompactIconButton(onClick = onDelete, stringResource(R.string.action_delete), Icons.Filled.DeleteOutline)
            }
        }
        if (expanded) {
            CompactOutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.settings_field_name)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
            // Pour un MBTILES, le fichier est fixe (résolu via le dossier mbtiles) et son emplacement est
            // affiché plus bas en lecture seule : pas de champ éditable. `url` conserve sa valeur d'origine.
            if (!isMbtiles) CompactOutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.settings_field_url)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
            if (!isMbtiles) CompactOutlinedTextField(key, { key = it }, label = { Text(stringResource(R.string.settings_field_api_key)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
            // Taille de tuile masquée pour un MBTILES (toujours 256, non pertinent) ; `tile` garde sa valeur.
            if (!isMbtiles) CompactOutlinedTextField(tile, { tile = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.settings_field_tile_size)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
            // Plage de zoom réellement contenue dans le MBTiles (fixée au téléchargement/import) : en
            // lecture seule, l'éditer ne changerait pas les tuiles présentes.
            if (isMbtiles) {
                Text(
                    stringResource(R.string.settings_field_zoom_levels, p.minZoom, p.maxZoom),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
                // Chemin réel du fichier (urlTemplate ne porte que le nom, résolu via le dossier mbtiles).
                val fullPath = when {
                    p.urlTemplate.startsWith("mbtiles://") -> p.urlTemplate.removePrefix("mbtiles://")
                    mbtilesDirPath != null -> "$mbtilesDirPath/${p.urlTemplate}"
                    else -> p.urlTemplate
                }
                Text(
                    stringResource(R.string.settings_field_mbtiles_location, fullPath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                // Miniatures générées à la fin du téléchargement (SPEC §6), affichées si présentes.
                val ctx = LocalContext.current
                val (locFile, detailFile) = remember(p.id) { OfflineThumbnails.files(ctx, p.urlTemplate) }
                if (locFile.exists() || detailFile.exists()) {
                    Spacer(Modifier.height(8.dp))
                    // Empilées : localisation compacte, puis l'aperçu détail sur toute la largeur disponible.
                    Column(
                        Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (locFile.exists()) {
                            ThumbColumn(stringResource(R.string.offline_thumb_location_title), locFile, Modifier.fillMaxWidth(0.6f))
                        }
                        if (detailFile.exists()) {
                            ThumbColumn(stringResource(R.string.offline_thumb_detail_title), detailFile, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            // "Enregistrer" (outlined, centré) seulement si un champ a changé par rapport à l'enregistré.
            // (Pour un MBTILES, url/taille sont masqués et conservent leur valeur : seul le nom peut varier.)
            val dirty = name != p.name || url != p.urlTemplate ||
                key != (p.apiKey ?: "") || tile != p.tileSize.toString()
            if (dirty) {
                Spacer(Modifier.height(8.dp))   // léger espacement au-dessus du bouton
                OutlinedButton(
                    onClick = {
                        onSave(p.copy(name = name, urlTemplate = url, apiKey = key.ifBlank { null }, tileSize = tile.toIntOrNull() ?: p.tileSize))
                        expanded = false
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text(stringResource(R.string.action_save)) }
            }
            Spacer(Modifier.height(12.dp))   // margin-bottom de l'éditeur
        }
    }
}
