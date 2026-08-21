package fr.lc4918.trailog.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import fr.lc4918.trailog.ui.alert.alertSoundTitle
import fr.lc4918.trailog.ui.theme.isDarkTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.LocalePrefs
import fr.lc4918.trailog.data.db.CompositeEntity
import fr.lc4918.trailog.data.db.CompositeSortOrder
import fr.lc4918.trailog.data.db.DefaultDemOpacityPct
import fr.lc4918.trailog.data.db.MaxGpsMarkerSizeDp
import fr.lc4918.trailog.data.db.MaxOffTrackAlertM
import fr.lc4918.trailog.data.db.MaxMapButtonSizeDp
import fr.lc4918.trailog.data.db.MinGpsMarkerSizeDp
import fr.lc4918.trailog.data.db.MinMapButtonSizeDp
import fr.lc4918.trailog.data.db.MinOffTrackAlertM
import fr.lc4918.trailog.data.db.OffTrackAlertStepM
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.data.backup.BackupFileName
import fr.lc4918.trailog.data.repo.StoragePaths
import fr.lc4918.trailog.domain.model.BubblePosition
import fr.lc4918.trailog.domain.model.GpsMarkerStyle
import fr.lc4918.trailog.domain.model.GroupCheck
import fr.lc4918.trailog.domain.model.PlannerHistory
import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.domain.model.PoiGroup
import fr.lc4918.trailog.ui.poi.poiCategoryLabel
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import fr.lc4918.trailog.domain.model.RouteEngine
import fr.lc4918.trailog.domain.model.HillPref
import fr.lc4918.trailog.domain.model.SurfacePref
import fr.lc4918.trailog.domain.model.WayPref
import fr.lc4918.trailog.data.db.routeUrl
import fr.lc4918.trailog.data.db.withRouteUrl
import fr.lc4918.trailog.data.db.routePrefs
import fr.lc4918.trailog.data.db.withRoutePrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.elevation.IgnElevation
import fr.lc4918.trailog.elevation.OpenTopo
import fr.lc4918.trailog.geocode.Photon
import fr.lc4918.trailog.routing.Router
import fr.lc4918.trailog.map.compositeBasemapId
import fr.lc4918.trailog.map.flagAssetModel
import fr.lc4918.trailog.map.flagCodeFor
import fr.lc4918.trailog.map.offline.OfflineThumbnails
import fr.lc4918.trailog.ui.components.Avatar
import fr.lc4918.trailog.ui.components.ColorPickerDialog
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField
import fr.lc4918.trailog.update.ReleaseInfo
import fr.lc4918.trailog.update.UpdateCheck
import fr.lc4918.trailog.update.UpdateFlow
import fr.lc4918.trailog.update.UpdateManager
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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

    // ---------- sauvegarde et restauration ----------
    val scope = rememberCoroutineScope()
    val backupOk = stringResource(R.string.backup_written)
    val backupFailed = stringResource(R.string.backup_failed)
    val backupWriter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { vm.writeBackup(it) { ok -> scope.launch { snackbar.showSnackbar(if (ok) backupOk else backupFailed) } } }
    }
    // Restaurer efface ce qui est en place : on demande avant d'ouvrir le selecteur, et non apres avoir
    // choisi le fichier - une confirmation qui arrive une fois l'archive designee se lit comme une
    // formalite, et se valide sans etre lue.
    var restoreTarget by remember { mutableStateOf(false) }
    var restoreDone by remember { mutableStateOf<RestoreOutcome?>(null) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.restoreBackup(it) { outcome -> restoreDone = outcome } }
    }

    LaunchedEffect(status) { status?.let { snackbar.showSnackbar(it); vm.clearStatus() } }

    if (restoreTarget) {
        AlertDialog(
            onDismissRequest = { restoreTarget = false },
            title = { Text(stringResource(R.string.settings_backup_restore)) },
            text = { Text(stringResource(R.string.backup_restore_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    restoreTarget = false
                    // Le type MIME est large : selon le gestionnaire de fichiers et la source (nuage,
                    // messagerie), un zip arrive annonce en octet-stream, et un filtre strict le rendrait
                    // ingrisable sans dire pourquoi.
                    restorePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    restoreDone?.let { outcome ->
        AlertDialog(
            onDismissRequest = { restoreDone = null },
            title = { Text(stringResource(R.string.settings_backup_restore)) },
            text = {
                Text(stringResource(when (outcome) {
                    RestoreOutcome.OK -> R.string.backup_restored
                    RestoreOutcome.NOT_A_BACKUP -> R.string.backup_not_a_backup
                    RestoreOutcome.UNSUPPORTED_FORMAT -> R.string.backup_too_recent
                    RestoreOutcome.FAILED -> R.string.backup_restore_failed
                }))
            },
            confirmButton = {
                TextButton(onClick = {
                    val done = outcome == RestoreOutcome.OK
                    restoreDone = null
                    // Redemarrage APRES une restauration reussie, et seulement dans ce cas : la base
                    // restauree n'est pas celle que Room a ouverte, et tout ce qui vit en memoire - flux,
                    // caches, profils decodes - decrit encore l'ancienne.
                    if (done) restartApp(ctx)
                }) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.settings_tab_map) to Icons.Filled.Map,
        stringResource(R.string.settings_tab_tiles) to Icons.Filled.Layers,
        stringResource(R.string.settings_tab_routes) to Icons.Filled.Route,
        stringResource(R.string.settings_tab_general) to Icons.Filled.Tune,
    )

    // Palette propre a cet ecran : cartes claires sur fond bleute, cf. SettingsPalette.
    ProvideSettingsPalette(dark = isDarkTheme(cur.theme)) {
    val palette = settingsPalette
    Scaffold(
        containerColor = palette.screen,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), Modifier.size(19.dp)) } },
                actions = { Avatar(cur.avatarSource, size = 26.dp, modifier = Modifier.padding(end = 14.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.card, titleContentColor = palette.label,
                    navigationIconContentColor = palette.label),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        // Les composants gardent la taille que Material leur donne, cible tactile de 48 dp comprise : cet
        // ecran se lit et se regle au doigt, la densite n'y vaut pas la lisibilite. Le seul endroit qui la
        // neutralise encore est la fiche d'un fournisseur, ou dix lignes se suivent dans une popup.
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Onglets en pastilles et non en soulignement : ils partagent la surface blanche de la barre,
            // et c'est l'aplat d'accent qui dit lequel est ouvert - le meme aplat que les puces retenues,
            // plus bas, une seule facon de dire "retenu" sur tout l'ecran.
            Row(
                Modifier.fillMaxWidth().background(palette.card).padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEachIndexed { i, (label, icon) ->
                    val selected = tab == i
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) palette.accentContainer else Color.Transparent)
                            .clickable { tab = i }
                            .padding(vertical = 7.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(icon, null, Modifier.size(16.dp),
                            tint = if (selected) palette.accentStrong else palette.subtle)
                        // 12 sp et non les 9,5 de la maquette : a cette taille, un onglet se devine plus
                        // qu'il ne se lit, et c'est la premiere chose qu'on lit de l'ecran.
                        Text(label, fontSize = 12.sp, maxLines = 1,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) palette.accentStrong else palette.subtle)
                    }
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
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 22.dp),
                ) {
                    when (currentTab) {
                        0 -> MapTab(cur, vm)
                        1 -> TilesTab(cur, providers, composites, vm, onPickMbtiles = { mbPicker.launch("*/*") })
                        2 -> RoutesTab(cur, vm)
                        else -> SystemTab(cur, vm,
                            onPickImportDir = { importDirPicker.launch(null) },
                            onPickMbtilesFolder = { treePicker.launch(null) },
                            onPickAvatar = { avatarPicker.launch("image/*") },
                            onBackup = { backupWriter.launch(BackupFileName.of(System.currentTimeMillis())) },
                            onRestore = { restoreTarget = true })
                    }
                }
            }
        }
    }
    }
}

/* --------------- Onglets --------------- */

/**
 * Onglet "Carte" : ce qui s'affiche sur la carte et par-dessus elle.
 *
 * Trois parties, dans l'ordre ou l'on decouvre la carte : ce qui s'y commande, ce qu'on y pose, puis ce
 * qui decrit le relief parcouru. Les deux dernieres portent un titre de groupe ; la premiere n'en a pas,
 * elle commence en tete de l'onglet ou il n'y a encore rien dont la distinguer.
 *
 * Les libelles des interrupteurs ont perdu leur "Afficher" : la rubrique s'appelle "Boutons et gestes",
 * et le repeter sept fois de suite ne disait rien de plus.
 */
@Composable private fun MapTab(cur: SettingsEntity, vm: SettingsViewModel) {
    SectionTitle(stringResource(R.string.settings_section_map_controls), tight = true)
    SettingsCard {
        // Éteindre la localisation éteint l'alerte d'éloignement avec elle : celle-ci n'a que la position
        // pour matière, et sa cloche resterait sur la carte sans rien pour allumer ni couper le capteur.
        // Le lien inverse est tenu par le réglage de l'alerte, plus bas.
        SwitchLine(stringResource(R.string.settings_sw_gps_button), cur.showGpsButton) {
            vm.save(cur.copy(showGpsButton = it, offTrackAlertEnabled = it && cur.offTrackAlertEnabled))
        }
        RowDivider()
        // Juste sous la localisation, dont il ne dit que le comportement : il ne déclenche rien de son côté
        // et ne coûte rien capteur éteint, d'où l'absence du lien croisé que l'alerte, elle, impose.
        SwitchLine(
            stringResource(R.string.settings_sw_follow_position), cur.mapFollowPosition,
            sub = stringResource(R.string.settings_sw_follow_position_sub),
        ) { vm.save(cur.copy(mapFollowPosition = it)) }
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_geocoding), cur.geocodingEnabled) { vm.save(cur.copy(geocodingEnabled = it)) }
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_planner), cur.routePlannerEnabled) { vm.save(cur.copy(routePlannerEnabled = it)) }
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_measure), cur.trackMeasureEnabled) { vm.save(cur.copy(trackMeasureEnabled = it)) }
        RowDivider()
        SwitchLine(
            stringResource(R.string.settings_sw_poi), cur.poiEnabled,
            sub = stringResource(R.string.settings_sw_poi_sub),
        ) { vm.save(cur.copy(poiEnabled = it)) }
        RowDivider()
        SwitchLine(
            stringResource(R.string.settings_sw_track_edit), cur.trackEditEnabled,
            sub = stringResource(R.string.settings_sw_track_edit_sub),
        ) { vm.save(cur.copy(trackEditEnabled = it)) }
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_scale), cur.showScale) { vm.save(cur.copy(showScale = it)) }
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_rotation), cur.rotateGesturesEnabled) { vm.save(cur.copy(rotateGesturesEnabled = it)) }
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_buttons_bg), cur.controlButtonsBackground) { vm.save(cur.copy(controlButtonsBackground = it)) }
        RowDivider()
        // Le curseur ne va pas au-dela du bouton Material plein : plus grand, il ne depasserait pas sa
        // zone tactile, il deborderait dessus.
        SliderRow(
            label = stringResource(R.string.settings_label_map_button_size),
            value = "${cur.mapButtonSizeDp} dp",
            fraction = fractionOf(cur.mapButtonSizeDp, MinMapButtonSizeDp, MaxMapButtonSizeDp),
            steps = MaxMapButtonSizeDp - MinMapButtonSizeDp - 1,
            onFraction = { vm.save(cur.copy(mapButtonSizeDp = valueOf(it, MinMapButtonSizeDp, MaxMapButtonSizeDp))) },
        )
    }

    OffTrackAlertSettings(cur, vm)

    GpsMarkerSettings(cur, vm)

    SectionTitle(stringResource(R.string.settings_section_basemap_control))
    SettingsCard {
        SwitchLine(stringResource(R.string.settings_label_show_button), cur.showBasemapControlButton) {
            vm.save(cur.copy(showBasemapControlButton = it))
        }
        RowDivider()
        SliderRow(
            stringResource(R.string.settings_label_panel_width), "${cur.basemapControlWidthPct} %",
            fractionOf(cur.basemapControlWidthPct, 20, 90),
            { vm.save(cur.copy(basemapControlWidthPct = valueOf(it, 20, 90))) },
        )
        RowDivider()
        SliderRow(
            stringResource(R.string.settings_label_panel_opacity), "${cur.basemapControlOpacityPct} %",
            fractionOf(cur.basemapControlOpacityPct, 30, 100),
            { vm.save(cur.copy(basemapControlOpacityPct = valueOf(it, 30, 100))) },
        )
        CardAction(stringResource(R.string.action_reset_defaults)) {
            vm.save(cur.copy(basemapControlWidthPct = 70, basemapControlOpacityPct = 90))
        }
    }

    GroupTitle(stringResource(R.string.settings_group_pois))
    SectionTitle(stringResource(R.string.settings_section_markers), tight = true)
    SettingsCard {
        StepperLine(stringResource(R.string.settings_label_marker_size), cur.markerSize, 16, 80) {
            vm.save(cur.copy(markerSize = it))
        }
    }
    SectionTitle(stringResource(R.string.settings_section_bubbles))
    SettingsCard {
        StepperLine(stringResource(R.string.settings_font_size), cur.bubbleFont, 7, 28,
            bold = cur.bubbleBold, onBold = { vm.save(cur.copy(bubbleBold = it)) }) { vm.save(cur.copy(bubbleFont = it)) }
        RowDivider()
        StepperLine(stringResource(R.string.font_title), cur.bubbleTitleFont, 7, 28,
            bold = cur.bubbleTitleBold, onBold = { vm.save(cur.copy(bubbleTitleBold = it)) }) { vm.save(cur.copy(bubbleTitleFont = it)) }
        RowDivider()
        PickRow(
            stringResource(R.string.settings_label_bubble_position),
            BubblePosition.of(cur.bubblePosition), BubblePosition.entries,
            optionLabel = { bubblePositionLabel(it) },
        ) { vm.save(cur.copy(bubblePosition = it.key)) }
        RowDivider()
        SliderRow(
            stringResource(R.string.settings_label_opacity), "${cur.bubbleOpacityPct} %",
            fractionOf(cur.bubbleOpacityPct, 30, 100),
            { vm.save(cur.copy(bubbleOpacityPct = valueOf(it, 30, 100))) },
        )
    }

    GroupTitle(stringResource(R.string.settings_group_elevation_profile))
    ProfileSettings(cur, vm)
}

/** Ligne a interrupteur : la ligne entiere bascule, pas seulement l'interrupteur. */
@Composable private fun ColumnScopeMarker.SwitchLine(
    label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit,
) {
    SetRow(label, sub = sub, onClick = { onChange(!checked) }, role = Role.Switch) {
        SettingsSwitch(checked)
    }
}

/** Ligne a pas-a-pas, avec sa bascule de graisse quand le reglage en porte une. */
@Composable private fun ColumnScopeMarker.StepperLine(
    label: String, value: Int, min: Int, max: Int,
    bold: Boolean? = null, onBold: ((Boolean) -> Unit)? = null, onChange: (Int) -> Unit,
) {
    StepperRow(
        label = label, value = value, min = min, max = max, bold = bold, onBold = onBold,
        boldLabel = stringResource(R.string.settings_bold_abbrev),
        decreaseLabel = stringResource(R.string.action_decrease),
        increaseLabel = stringResource(R.string.action_increase),
        onChange = onChange,
    )
}

/** Fraction 0..1 d'une valeur entiere dans sa plage, et son retour : les curseurs de l'ecran parlent tous
 *  en fractions, les reglages en unites. Une seule conversion, au meme endroit pour tous. */
private fun fractionOf(value: Int, min: Int, max: Int): Float =
    ((value - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)

private fun valueOf(fraction: Float, min: Int, max: Int): Int =
    (min + fraction * (max - min)).roundToInt().coerceIn(min, max)

/**
 * Ligne a cocher d'une categorie de point d'interet, avec son pictogramme.
 *
 * Trois etats et non deux : la ligne "tout selectionner" d'un groupe est a moitie cochee quand une partie
 * seulement de ses categories l'est, comme n'importe quel "select all".
 */
@Composable private fun ColumnScopeMarker.CheckLine(
    label: String, etat: GroupCheck, onToggle: () -> Unit,
) {
    SetRow(label, onClick = onToggle, role = Role.Checkbox) {
        Icon(
            when (etat) {
                GroupCheck.ALL -> Icons.Filled.CheckBox
                GroupCheck.SOME -> Icons.Filled.IndeterminateCheckBox
                GroupCheck.NONE -> Icons.Filled.CheckBoxOutlineBlank
            },
            null,
            tint = if (etat == GroupCheck.NONE) settingsPalette.subtle else MaterialTheme.colorScheme.primary,
        )
    }
}

/** Nom traduit d'un groupe de points d'interet. */
@Composable private fun poiGroupLabel(g: PoiGroup): String = stringResource(
    when (g) {
        PoiGroup.LODGING -> R.string.poi_group_lodging
        PoiGroup.FOOD -> R.string.poi_group_food
        PoiGroup.LEISURE -> R.string.poi_group_leisure
        PoiGroup.PRACTICAL -> R.string.poi_group_practical
    }
)

/** Nom du moteur d'itinéraire. Pas de chaîne traduite : ce sont deux noms propres. */
@Composable private fun routeEngineLabel(e: RouteEngine): String = when (e) {
    RouteEngine.VALHALLA -> "Valhalla"
    RouteEngine.BROUTER -> "BRouter"
}

/** Libellé traduit d'une discipline d'itinéraire. */
@Composable fun routingProfileLabel(p: RoutingProfile): String = stringResource(
    when (p) {
        RoutingProfile.ROAD_BIKE -> R.string.profile_road_bike
        RoutingProfile.GRAVEL -> R.string.profile_gravel
        RoutingProfile.HYBRID_BIKE -> R.string.profile_hybrid_bike
        RoutingProfile.MOUNTAIN_BIKE -> R.string.profile_mtb
        RoutingProfile.FOOT -> R.string.profile_foot
    }
)

/**
 * Libellés des trois préférences de tracé, en mots de sortie et non en options de moteur.
 *
 * La position centrale des trois porte le MÊME libellé, "Sans préférence", parce qu'elle fait la même
 * chose : ne rien demander au service (cf. Valhalla.costingOptionsOf). Trois formulations différentes
 * laisseraient croire à trois comportements.
 */
@Composable private fun wayPrefLabel(p: WayPref): String = stringResource(
    when (p) {
        WayPref.ROADS -> R.string.route_ways_roads
        WayPref.BALANCED -> R.string.route_pref_none
        WayPref.SOFT -> R.string.route_ways_soft
    }
)

@Composable private fun hillPrefLabel(p: HillPref): String = stringResource(
    when (p) {
        HillPref.AVOID -> R.string.route_hills_avoid
        HillPref.BALANCED -> R.string.route_pref_none
        HillPref.SEEK -> R.string.route_hills_seek
    }
)

@Composable private fun surfacePrefLabel(p: SurfacePref): String = stringResource(
    when (p) {
        SurfacePref.PAVED -> R.string.route_surface_paved
        SurfacePref.BALANCED -> R.string.route_pref_none
        SurfacePref.ROUGH -> R.string.route_surface_rough
    }
)

/** Icône d'une discipline. Le libellé l'accompagne toujours : les cinq pictogrammes se distinguent bien
 *  entre eux, mais aucun ne dit à lui seul "gravel" plutôt que "VTC". */
private fun routingProfileIcon(p: RoutingProfile): ImageVector = when (p) {
    RoutingProfile.ROAD_BIKE -> Icons.Filled.DirectionsBike
    RoutingProfile.GRAVEL -> Icons.Filled.Grain
    RoutingProfile.HYBRID_BIKE -> Icons.Filled.PedalBike
    RoutingProfile.MOUNTAIN_BIKE -> Icons.Filled.Terrain
    RoutingProfile.FOOT -> Icons.Filled.DirectionsWalk
}

/** Choix de la discipline : les cinq icônes en ligne, la retenue en pastille pleine. Un select déroulant
 *  aurait caché quatre choix sur cinq derrière un tap, pour un réglage qu'on change au gré de la sortie. */
/** Ligne des disciplines. Partagee avec le planificateur, qui doit offrir exactement le meme choix, dans
 *  la meme forme : c'est le meme reglage, une fois par defaut et une fois pour le trajet en cours. */
@Composable internal fun RoutingProfilePicker(current: RoutingProfile, onSelect: (RoutingProfile) -> Unit) {
    // Marge egale sur les quatre cotes, et non un simple decalage vers le bas : la rangee se pose ainsi au
    // milieu de la carte qui la porte, sans blanc en trop au-dessus de la pastille retenue, et les
    // disciplines des extremites detachent leurs quatre angles arrondis comme celles du milieu - collees au
    // bord, leurs deux angles exterieurs disparaissaient dans l'arrondi de la carte.
    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RoutingProfile.entries.forEach { p ->
            val selected = p == current
            val label = routingProfileLabel(p)
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    // Aplat d'accent translucide plutot que le couple primary/onPrimary : `onPrimary`
                    // appartient au jeu par defaut de Material, que l'application ne redefinit pas - il
                    // est violet. Le meme bleu, pose a 18 %, dit "retenu" dans les deux themes.
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(p) }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(routingProfileIcon(p), label, modifier = Modifier.size(24.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                Text(label, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 2,
                    textAlign = TextAlign.Center,
                    color = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            }
        }
    }
}

/**
 * Alerte d'eloignement : la cloche sur la carte, l'ecart qui la declenche, et le son qui l'accompagne.
 *
 * L'interrupteur allume AUSSI le bouton de localisation, sans le demander : une alerte se nourrit de la
 * position, et la cloche sans le bouton GPS serait un capteur qu'on ne peut ni voir ni couper. Le lien
 * inverse est tenu la-haut, dans la ligne du bouton GPS - eteindre l'un eteint l'autre.
 *
 * Le son ne montre son choix que s'il est actif : une ligne de reglage qui ne sert a rien vaut mieux
 * absente que grisee.
 */
@Composable private fun OffTrackAlertSettings(cur: SettingsEntity, vm: SettingsViewModel) {
    val ctx = LocalContext.current
    // Le selecteur de sonnerie du systeme : c'est lui qui liste les notifications du telephone et les fait
    // ecouter. En livrer un dans l'application reviendrait a redessiner un ecran que l'utilisateur connait.
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        vm.save(cur.copy(offTrackAlertSoundUri = picked?.toString().orEmpty()))
    }
    val soundLabel = remember(cur.offTrackAlertSoundUri) { alertSoundTitle(ctx, cur.offTrackAlertSoundUri) }
    val defaultSoundLabel = stringResource(R.string.settings_off_track_sound_default)

    SectionTitle(stringResource(R.string.settings_section_off_track))
    SettingsCard {
        SwitchLine(stringResource(R.string.settings_label_show_button), cur.offTrackAlertEnabled) {
            vm.save(cur.copy(offTrackAlertEnabled = it, showGpsButton = it || cur.showGpsButton))
        }
        RowDivider()
        // Le curseur va par pas de dix metres : au metre pres, on reglerait la precision du capteur, pas
        // la distance a laquelle on veut etre prevenu.
        SliderRow(
            label = stringResource(R.string.settings_label_off_track_distance),
            value = "${cur.offTrackAlertDistanceM} m",
            fraction = fractionOf(cur.offTrackAlertDistanceM / OffTrackAlertStepM,
                MinOffTrackAlertM / OffTrackAlertStepM, MaxOffTrackAlertM / OffTrackAlertStepM),
            steps = (MaxOffTrackAlertM - MinOffTrackAlertM) / OffTrackAlertStepM - 1,
            onFraction = {
                val steps = valueOf(it, MinOffTrackAlertM / OffTrackAlertStepM, MaxOffTrackAlertM / OffTrackAlertStepM)
                vm.save(cur.copy(offTrackAlertDistanceM = steps * OffTrackAlertStepM))
            },
        )
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_off_track_sound), cur.offTrackAlertSound) {
            vm.save(cur.copy(offTrackAlertSound = it))
        }
        if (cur.offTrackAlertSound) {
            RowDivider()
            SetRow(
                stringResource(R.string.settings_label_off_track_sound),
                onClick = { soundPicker.launch(ringtonePickerIntent(ctx, cur.offTrackAlertSoundUri)) },
            ) {
                ValueText(soundLabel ?: defaultSoundLabel)
            }
        }
        Hint(stringResource(R.string.settings_off_track_hint))
    }
}

/** Intention du selecteur de sonnerie, limite aux notifications, ouvert sur le son deja retenu. */
private fun ringtonePickerIntent(ctx: android.content.Context, current: String): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ctx.getString(R.string.settings_label_off_track_sound))
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            current.takeIf { it.isNotBlank() }?.toUri())
    }

/**
 * Repere de position GPS : son symbole, sa couleur, sa taille.
 *
 * Juste apres les boutons de la carte, et non avec les marqueurs des points d'interet : ce repere n'est
 * pas un point pose sur la carte mais l'etat du capteur qu'allume le bouton juste au-dessus.
 *
 * La couleur affichee est la couleur EFFECTIVE - celle reglee, ou celle propre au symbole tant qu'on n'a
 * rien choisi -, si bien que la pastille dit toujours ce qu'on verra sur la carte.
 */
@Composable private fun GpsMarkerSettings(cur: SettingsEntity, vm: SettingsViewModel) {
    val marker = GpsMarkerStyle.of(cur.gpsMarkerStyle)
    val color = cur.gpsMarkerColor.takeIf { it.isNotBlank() } ?: marker.defaultColor
    var pickColor by remember { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.settings_section_gps_marker))
    SettingsCard {
        // Changer de symbole rend sa couleur ET sa taille au nouveau : chacun a les siennes (bleu et 20 dp
        // pour la puce, rouge et 30 dp pour les fleches, qui doivent montrer une direction), et les heriter
        // du precedent donnerait une fleche bleue et minuscule a qui vient de quitter la puce sans avoir
        // rien choisi.
        PickRow(
            stringResource(R.string.settings_label_gps_marker_style),
            marker, GpsMarkerStyle.entries, optionLabel = { gpsMarkerLabel(it) },
        ) {
            vm.save(cur.copy(
                gpsMarkerStyle = it.key, gpsMarkerColor = "", gpsMarkerSizeDp = it.defaultSizeDp,
            ))
        }
        RowDivider()
        SetRow(stringResource(R.string.settings_label_gps_marker_color), onClick = { pickColor = true }) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(color.toColorInt())))
        }
        RowDivider()
        SliderRow(
            label = stringResource(R.string.settings_label_gps_marker_size),
            value = "${cur.gpsMarkerSizeDp} dp",
            fraction = fractionOf(cur.gpsMarkerSizeDp, MinGpsMarkerSizeDp, MaxGpsMarkerSizeDp),
            steps = MaxGpsMarkerSizeDp - MinGpsMarkerSizeDp - 1,
            onFraction = { vm.save(cur.copy(gpsMarkerSizeDp = valueOf(it, MinGpsMarkerSizeDp, MaxGpsMarkerSizeDp))) },
        )
        if (marker.oriented) Hint(stringResource(R.string.settings_gps_marker_heading_hint))
    }
    if (pickColor) {
        ColorPickerDialog(
            current = color,
            onPick = { vm.save(cur.copy(gpsMarkerColor = it)); pickColor = false },
            onDismiss = { pickColor = false },
        )
    }
}

/** Libelle traduit d'un symbole de position. */
@Composable private fun gpsMarkerLabel(m: GpsMarkerStyle): String = stringResource(
    when (m) {
        GpsMarkerStyle.DOT -> R.string.gps_marker_dot
        GpsMarkerStyle.ARROW_OUTLINE -> R.string.gps_marker_arrow_outline
        GpsMarkerStyle.ARROW_FILLED -> R.string.gps_marker_arrow_filled
        GpsMarkerStyle.CROSSHAIR -> R.string.gps_marker_crosshair
    }
)

/** Libellé traduit d'un placement d'infobulle. */
@Composable private fun bubblePositionLabel(p: BubblePosition): String = stringResource(
    when (p) {
        BubblePosition.AUTO -> R.string.bubble_pos_auto
        BubblePosition.TOP_LEFT -> R.string.bubble_pos_top_left
        BubblePosition.TOP -> R.string.bubble_pos_top
        BubblePosition.TOP_RIGHT -> R.string.bubble_pos_top_right
        BubblePosition.MIDDLE_LEFT -> R.string.bubble_pos_middle_left
        BubblePosition.CENTER -> R.string.bubble_pos_center
        BubblePosition.MIDDLE_RIGHT -> R.string.bubble_pos_middle_right
        BubblePosition.BOTTOM_LEFT -> R.string.bubble_pos_bottom_left
        BubblePosition.BOTTOM -> R.string.bubble_pos_bottom
        BubblePosition.BOTTOM_RIGHT -> R.string.bubble_pos_bottom_right
    }
)

/** Select du placement de l'infobulle (même fond/bord compacts que [LanguagePicker]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BubblePositionPicker(current: BubblePosition, onSelect: (BubblePosition) -> Unit) {
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
                Text(bubblePositionLabel(current), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                ExposedDropdownMenuDefaults.TrailingIcon(open, modifier = Modifier.requiredSize(CompactIconSize))
            }
        }
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BubblePosition.entries.forEach { p ->
                DropdownMenuItem(text = { Text(bubblePositionLabel(p)) }, onClick = { open = false; onSelect(p) })
            }
        }
    }
}

@Composable private fun TilesTab(
    cur: SettingsEntity, providers: List<ProviderEntity>, composites: List<CompositeEntity>, vm: SettingsViewModel,
    onPickMbtiles: () -> Unit,
) {
    val mapProviders = providers.filter { it.type != "DEM" && !it.transparent && it.enabled }
    val mapComposites = composites.filter { it.enabled }
    // Le Relief (type DEM) est un fournisseur standard (URL, activation, edition) : il suit les memes
    // regles que les autres et figure donc dans "Gerer les fournisseurs", pas dans les fonds MBTILES.
    val basemapEntries = providers.filter { it.type == "MBTILES" }
    val otherProviders = providers.filter { it.type != "MBTILES" }
    var creatingComposite by remember { mutableStateOf(false) }
    var editingComposite by remember { mutableStateOf<CompositeEntity?>(null) }
    var providersDialogOpen by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    // Dossier reel des .mbtiles (miroir de TrailogRepository.mbtilesDir) : affiche dans l'editeur MBTILES.
    val mbtilesDirPath = cur.mbtilesDir.ifBlank { File(ctx.filesDir, "mbtiles").absolutePath }

    SectionTitle(stringResource(R.string.settings_section_default_basemap), tight = true)
    SettingsCard {
        BasemapPickRow(mapProviders, mapComposites, cur.defaultBasemapId) { vm.save(cur.copy(defaultBasemapId = it)) }
    }

    // Les deux boutons d'ajout sortent de la liste des fonds : on ne confond plus "ce que je peux
    // ajouter" et "ce que j'ai deja".
    SectionTitle(stringResource(R.string.settings_section_add_basemap))
    SettingsCard {
        CardButton(stringResource(R.string.action_import_mbtiles), painterResource(R.drawable.ic_settings_download), onPickMbtiles)
        CardButton(stringResource(R.string.action_create_composite), painterResource(R.drawable.ic_settings_layers)) { creatingComposite = true }
    }

    if (basemapEntries.isNotEmpty() || composites.isNotEmpty()) {
        SectionTitle(stringResource(R.string.settings_section_installed_basemaps))
        SettingsCard {
            basemapEntries.forEachIndexed { i, p ->
                if (i > 0) RowDivider()
                val onDelete: (() -> Unit)? = if (!p.builtin) { { vm.deleteProvider(p) } } else null
                ProviderRow(p, onSave = vm::saveProvider, onDelete = onDelete, mbtilesDirPath = mbtilesDirPath)
            }
            composites.forEachIndexed { i, c ->
                if (i > 0 || basemapEntries.isNotEmpty()) RowDivider()
                CompositeRow(c, onToggle = { vm.saveComposite(c.copy(enabled = it)) },
                    onEdit = { editingComposite = c }, onDelete = { vm.deleteComposite(c) })
            }
        }
    }

    SectionTitle(stringResource(R.string.settings_section_providers))
    SettingsCard {
        SetRow(
            stringResource(R.string.action_manage_providers),
            sub = stringResource(R.string.settings_providers_subtitle),
            onClick = { providersDialogOpen = true },
        ) {
            RowIcon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.action_manage_providers))
        }
    }

    if (creatingComposite) {
        CompositeEditorDialog(null, providers,
            onSave = { vm.saveComposite(it); creatingComposite = false }, onDismiss = { creatingComposite = false })
    }
    editingComposite?.let { c ->
        CompositeEditorDialog(c, providers,
            onSave = { vm.saveComposite(it); editingComposite = null }, onDismiss = { editingComposite = null })
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
            // La popup reprend le fond et les cartes de l'ecran : elle en est le prolongement, pas une
            // fenetre d'un autre monde.
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f),
                shape = RoundedCornerShape(20.dp), color = settingsPalette.screen,
            ) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_section_providers), fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold, color = settingsPalette.label,
                            modifier = Modifier.weight(1f))
                        RowIcon(Icons.Filled.FileUpload, stringResource(R.string.action_export)) { exportLauncher.launch("trailog_providers.json") }
                        RowIcon(Icons.Filled.FileDownload, stringResource(R.string.action_import)) { importLauncher.launch("application/json") }
                        RowIcon(Icons.Filled.Close, stringResource(R.string.action_close)) { providersDialogOpen = false }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        SettingsCard {
                            otherProviders.forEachIndexed { i, p ->
                                if (i > 0) RowDivider()
                                ProviderRow(p, onSave = vm::saveProvider)
                            }
                        }
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

/** Ligne d'un composite : meme grammaire qu'un fond (cf. ProviderRow) - symbole, nom, actions, etat. */
@Composable private fun CompositeRow(c: CompositeEntity, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp).padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_settings_layers), null, Modifier.size(16.dp), tint = settingsPalette.subtle)
        Text(c.name, fontSize = 12.5.sp, color = settingsPalette.label, modifier = Modifier.weight(1f))
        RowIcon(Icons.Filled.Edit, stringResource(R.string.action_edit), onClick = onEdit)
        RowIcon(Icons.Filled.DeleteOutline, stringResource(R.string.action_delete), onClick = onDelete)
        SettingsSwitch(c.enabled, onToggle)
    }
}

@Composable private fun CompositeEditorDialog(
    existing: CompositeEntity?, providers: List<ProviderEntity>,
    onSave: (CompositeEntity) -> Unit, onDismiss: () -> Unit,
) {
    // Le relief peut être utilisé en overlay (premier plan, ex. superposé à une carte pays) mais jamais
    // comme arrière-plan (tuiles DEM brutes illisibles seules, cf. StyleBuilder).
    val bgSelectable = providers.filter { it.type != "DEM" }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var opacityPct by remember { mutableIntStateOf(((existing?.foregroundOpacity ?: 0.5f) * 100).toInt()) }
    var fgId by remember { mutableStateOf(existing?.foregroundProviderId ?: providers.firstOrNull()?.id ?: "") }
    var bgId by remember { mutableStateOf(existing?.backgroundProviderId ?: bgSelectable.firstOrNull()?.id ?: "") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Meme fond et memes cartes que l'ecran des reglages (cf. la popup des fournisseurs).
        Surface(
            // Hauteur libre, bornee a ce que l'ecran offre : la popup ne porte que quatre champs, lui
            // en reserver 80 % laissait un grand vide sous le dernier.
            modifier = Modifier.fillMaxWidth(0.86f).heightIn(max = 620.dp),
            shape = RoundedCornerShape(20.dp), color = settingsPalette.screen,
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(if (existing == null) R.string.dialog_composite_title
                    else R.string.dialog_composite_edit_title), fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, color = settingsPalette.label)
                Spacer(Modifier.height(10.dp))
                SettingsCard {
                    FieldRow(stringResource(R.string.field_composite_name)) {
                        SettingsTextField(name, "") { name = it }
                    }
                    RowDivider()
                    SliderRow(
                        stringResource(R.string.settings_label_opacity), "$opacityPct %",
                        fractionOf(opacityPct, 0, 100),
                        { opacityPct = valueOf(it, 0, 100) },
                    )
                    RowDivider()
                    // Les deux couches suivent les deux premiers reglages, dans la meme carte et sous la
                    // meme legende : ce sont quatre champs d'un meme objet, non deux paires a rapprocher.
                    FieldRow(stringResource(R.string.label_foreground_layer), Icons.Filled.FlipToFront) {
                        LayerSelect(providers, fgId) { fgId = it }
                    }
                    RowDivider()
                    FieldRow(stringResource(R.string.label_background_layer), Icons.Filled.FlipToBack) {
                        LayerSelect(bgSelectable, bgId) { bgId = it }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (name.isNotBlank() && fgId.isNotBlank() && bgId.isNotBlank()) {
                            onSave(CompositeEntity(
                                id = existing?.id ?: 0, name = name,
                                backgroundProviderId = bgId, foregroundProviderId = fgId,
                                foregroundOpacity = opacityPct / 100f, enabled = existing?.enabled ?: true,
                                sortOrder = existing?.sortOrder ?: CompositeSortOrder, folderId = existing?.folderId,
                            ))
                        }
                    }) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/** Choix d'une couche : le cadre de champ de l'ecran, et le menu des fonds sous lui. */
@Composable private fun LayerSelect(items: List<ProviderEntity>, current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = items.firstOrNull { it.id == current }?.name ?: current
    Box {
        Row(
            fieldBoxModifier().clickable { open = true }.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, fontSize = 12.sp, color = settingsPalette.label, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(16.dp), tint = settingsPalette.subtle)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { p -> DropdownMenuItem(text = { ProviderOptionLabel(p) }, onClick = { onSelect(p.id); open = false }) }
        }
    }
}

/**
 * Reglages d'apparence du profil altimetrique, communs au profil d'une trace et a celui d'un itineraire
 * planifie. Composable a part, et non un onglet : ils tiennent desormais a la fin de l'onglet "Carte",
 * l'onglet qu'ils occupaient etant rendu au calcul d'itineraire.
 */
@Composable private fun ProfileSettings(cur: SettingsEntity, vm: SettingsViewModel) {
    SectionTitle(stringResource(R.string.settings_section_display), tight = true)
    SettingsCard {
        SwitchLine(stringResource(R.string.settings_profile_grid), cur.profileGrid) { vm.save(cur.copy(profileGrid = it)) }
        RowDivider()
        // Pas d'interrupteur pour la legende des pentes : elle se demande d'un "i" pose sur le bandeau du
        // profil, la ou elle sert, et se referme du meme geste (cf. SlopeLegendButton).
        SwitchLine(stringResource(R.string.settings_profile_color_by_slope), cur.profileSlope) { vm.save(cur.copy(profileSlope = it)) }
        RowDivider()
        SwitchLine(
            stringResource(R.string.settings_profile_remaining), cur.profileRemaining,
            sub = stringResource(R.string.settings_profile_remaining_sub),
        ) { vm.save(cur.copy(profileRemaining = it)) }
    }

    SectionTitle(stringResource(R.string.settings_section_title_line_info))
    SettingsCard {
        SetRow(stringResource(R.string.settings_label_title_line))
        InfoChipRow(
            listOf("dist" to stringResource(R.string.chip_distance), "asc" to stringResource(R.string.chip_ascent),
                "desc" to stringResource(R.string.chip_descent), "dur" to stringResource(R.string.chip_duration),
                "min" to stringResource(R.string.chip_alt_min), "max" to stringResource(R.string.chip_alt_max)),
            cur.titleInfos, scrollable = true,
        ) { vm.save(cur.copy(titleInfos = it)) }
        RowDivider()
        SetRow(stringResource(R.string.settings_label_current_point))
        InfoChipRow(
            listOf("dist" to stringResource(R.string.chip_distance), "ele" to stringResource(R.string.chip_altitude),
                "slope" to stringResource(R.string.chip_slope), "time" to stringResource(R.string.chip_time)),
            cur.cursorInfos,
        ) { vm.save(cur.copy(cursorInfos = it)) }
    }

    SectionTitle(stringResource(R.string.settings_section_font_sizes))
    SettingsCard {
        StepperLine(stringResource(R.string.font_axes), cur.profAxisFont, 7, 28,
            bold = cur.profAxisBold, onBold = { vm.save(cur.copy(profAxisBold = it)) }) { vm.save(cur.copy(profAxisFont = it)) }
        RowDivider()
        StepperLine(stringResource(R.string.font_title), cur.profTitleFont, 7, 28,
            bold = cur.profTitleBold, onBold = { vm.save(cur.copy(profTitleBold = it)) }) { vm.save(cur.copy(profTitleFont = it)) }
        RowDivider()
        StepperLine(stringResource(R.string.font_title_bar_info), cur.profBarFont, 7, 28,
            bold = cur.profBarBold, onBold = { vm.save(cur.copy(profBarBold = it)) }) { vm.save(cur.copy(profBarFont = it)) }
        RowDivider()
        StepperLine(stringResource(R.string.settings_profile_slope_legend), cur.profLegendFont, 7, 28,
            bold = cur.profLegendBold, onBold = { vm.save(cur.copy(profLegendBold = it)) }) { vm.save(cur.copy(profLegendFont = it)) }
        RowDivider()
        StepperLine(stringResource(R.string.font_cursor_point), cur.profCursorFont, 7, 28,
            bold = cur.profCursorBold, onBold = { vm.save(cur.copy(profCursorBold = it)) }) { vm.save(cur.copy(profCursorFont = it)) }
    }
}

/** Puces d'un choix multiple ordonne (infos affichees) : l'ordre d'affichage suit celui des puces, non
 *  celui des taps - deux traces cote a cote doivent presenter leurs infos dans le meme ordre. */
@Composable private fun ColumnScopeMarker.InfoChipRow(
    options: List<Pair<String, String>>, csv: String, scrollable: Boolean = false, onChange: (String) -> Unit,
) {
    val selected = csv.split(",").map { it.trim() }.filter { it.isNotBlank() }
    ChipRow(scrollable = scrollable) {
        options.forEach { (key, label) ->
            val on = key in selected
            SettingsChip(label, selected = on) {
                val next = if (on) selected - key else selected + key
                onChange(options.map { it.first }.filter { it in next }.joinToString(","))
            }
        }
    }
}

/**
 * Onglet "Itineraires" : comment le calcul se fait, non ce qui s'affiche.
 *
 * Les deux services y figurent separement bien que le geocodage ne soit pas du calcul d'itineraire :
 * chercher un lieu est le premier geste de la planification, et les deux URL se reglent ensemble. Les
 * interrupteurs qui montrent leurs boutons sur la carte, eux, restent dans l'onglet "Carte".
 */
@Composable private fun RoutesTab(cur: SettingsEntity, vm: SettingsViewModel) {
    SectionTitle(stringResource(R.string.settings_section_services), tight = true)
    SettingsCard {
        FieldRow(stringResource(R.string.settings_section_geocoding_service)) {
            SettingsTextField(cur.geocodingUrl, Photon.DEFAULT_URL) { vm.save(cur.copy(geocodingUrl = it.trim())) }
        }
        RowDivider()
        FieldRow(stringResource(R.string.settings_section_routing_service)) {
            // Le champ ENTIER suit le moteur retenu, valeur et gabarit : chaque moteur garde son adresse,
            // si bien que basculer pour comparer ne fait pas perdre celle de l'autre - et qu'on n'envoie
            // jamais la requete d'un moteur au serveur du voisin, faute qui echouerait en silence.
            val moteur = RouteEngine.of(cur.routeEngine)
            SettingsTextField(cur.routeUrl(moteur), Router.defaultUrlOf(moteur)) {
                vm.save(cur.withRouteUrl(moteur, it.trim()))
            }
        }
        Hint(stringResource(R.string.settings_services_hint))
    }

    /*
     * Le moteur se regle sous l'URL qu'il commande, et en puces plutot qu'en menu : les deux valeurs
     * restent lisibles d'un coup d'oeil, et basculer de l'une a l'autre est UN tap. C'est la raison
     * d'etre du reglage - comparer deux moteurs sur le meme trajet, sans rien changer d'autre.
     */
    SectionTitle(stringResource(R.string.settings_section_route_engine))
    SettingsCard {
        SegChips(RouteEngine.entries.map { it.key to routeEngineLabel(it) }, cur.routeEngine) {
            vm.save(cur.copy(routeEngine = it))
        }
        Hint(stringResource(R.string.settings_route_engine_hint))
    }

    SectionTitle(stringResource(R.string.settings_section_default_discipline))
    SettingsCard {
        RoutingProfilePicker(RoutingProfile.of(cur.routingProfile)) { vm.save(cur.copy(routingProfile = it.key)) }
    }

    /*
     * Ce que le calcul doit privilégier - et qu'il ignorait jusqu'ici, d'où cette rubrique : sans elle, un
     * VTC part sur la départementale quand la voie verte longe la même vallée.
     *
     * Discipline par discipline, et non un réglage unique : on ne demande pas la même chose à un vélo de
     * route qu'à un VTT, et la mesure l'a confirmé - accepter les chemins fait gagner au VTT, et fait
     * perdre au vélo de route, qu'un long détour éloigne alors des voies vertes qu'il aurait prises.
     *
     * La discipline réglée ici n'est PAS celle du dessus : celle du dessus est le défaut du planificateur,
     * celle-ci désigne le jeu de préférences qu'on modifie. On peut donc régler le VTT sans rouler en VTT.
     * Elle s'ouvre néanmoins sur la discipline par défaut, la plus probable, et la suit si on la change.
     */
    SectionTitle(stringResource(R.string.settings_section_route_prefs))
    var tuned by remember(cur.routingProfile) { mutableStateOf(RoutingProfile.of(cur.routingProfile)) }
    SettingsCard {
        RoutingProfilePicker(tuned) { tuned = it }
        RowDivider()
        val prefs = cur.routePrefs(tuned)
        PickRow(stringResource(R.string.settings_label_route_ways), prefs.ways,
            WayPref.entries, optionLabel = { wayPrefLabel(it) }) {
            vm.save(cur.withRoutePrefs(tuned, prefs.copy(ways = it)))
        }
        RowDivider()
        PickRow(stringResource(R.string.settings_label_route_hills), prefs.hills,
            HillPref.entries, optionLabel = { hillPrefLabel(it) }) {
            vm.save(cur.withRoutePrefs(tuned, prefs.copy(hills = it)))
        }
        RowDivider()
        PickRow(stringResource(R.string.settings_label_route_surface), prefs.surface,
            SurfacePref.entries, optionLabel = { surfacePrefLabel(it) }) {
            vm.save(cur.withRoutePrefs(tuned, prefs.copy(surface = it)))
        }
    }

    /*
     * Vider l'historique des lieux du planificateur.
     *
     * Il se remplit TOUT SEUL de ce qu'on consulte - un lieu cherche, un point d'interet ouvert, l'adresse
     * d'un appui long - et pas seulement de ce qu'on tape. Ce qui s'inscrit sans qu'on le demande doit
     * pouvoir s'effacer sans reinitialiser tous les reglages : c'est la moindre des choses pour une
     * application dont l'argument est que tout reste sur l'appareil.
     *
     * Le compte en sous-titre, et la ligne eteinte quand il est a zero : un bouton qui n'a rien a effacer
     * ne doit pas repondre comme s'il avait fait quelque chose. Sans confirmation - huit lieux se
     * reconstituent en une promenade, la demander pour cela serait du ceremonial.
     */
    val lieux = PlannerHistory.of(cur.plannerHistory).places
    SectionTitle(stringResource(R.string.settings_section_planner_history))
    SettingsCard {
        SetRow(
            stringResource(R.string.settings_clear_planner_history),
            sub = if (lieux.isEmpty()) stringResource(R.string.settings_planner_history_empty)
            else stringResource(R.string.settings_planner_history_count, lieux.size),
            onClick = if (lieux.isEmpty()) null else ({ vm.save(cur.copy(plannerHistory = "")) }),
            role = Role.Button,
        ) {
            Icon(
                Icons.Filled.DeleteOutline, null,
                tint = if (lieux.isEmpty()) settingsPalette.subtle else settingsPalette.accent,
            )
        }
        Hint(stringResource(R.string.settings_planner_history_hint))
    }

    /*
     * Points d'interet : un groupe par section depliable, ses categories en cases a cocher.
     *
     * Dans l'onglet Trajets et non dans "Carte" : l'interrupteur qui pose le BOUTON sur la carte est une
     * commande d'ecran et reste la-bas, mais ce qu'on affiche dessous decrit un trajet - ou dormir, ou
     * manger, ou reparer un velo - au meme titre que les preferences de trace juste au-dessus.
     */
    SectionTitle(stringResource(R.string.settings_section_poi))
    val filtres = PoiFilters.of(cur.poiHiddenCategories, cur.poiBikeGroups)
    fun sauve(f: PoiFilters) =
        vm.save(cur.copy(poiHiddenCategories = f.hiddenCsv(), poiBikeGroups = f.bikeCsv()))
    SettingsCard {
        Hint(stringResource(R.string.settings_poi_hint))
    }
    PoiGroup.entries.forEach { groupe ->
        var deplie by rememberSaveable(groupe) { mutableStateOf(false) }
        SettingsCard {
            SetRow(
                poiGroupLabel(groupe),
                sub = stringResource(R.string.settings_poi_group_count,
                    PoiCategory.of(groupe).count { filtres.isShown(it) }, PoiCategory.of(groupe).size),
                onClick = { deplie = !deplie },
            ) {
                Icon(
                    if (deplie) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null, tint = settingsPalette.subtle,
                )
            }
            if (deplie) {
                RowDivider()
                // "Tout selectionner" en tete, avec son etat a trois valeurs : coche, vide, ou entre les
                // deux quand une partie seulement du groupe est retenue.
                CheckLine(
                    stringResource(R.string.settings_poi_select_all), filtres.groupState(groupe),
                ) { sauve(filtres.toggleGroup(groupe)) }
                PoiCategory.of(groupe).forEach { cat ->
                    CheckLine(
                        poiCategoryLabel(cat),
                        if (filtres.isShown(cat)) GroupCheck.ALL else GroupCheck.NONE,
                    ) { sauve(filtres.toggle(cat)) }
                }
                RowDivider()
                // Le filtre velo est par GROUPE : on veut des hebergements qui accueillent les cyclistes
                // sans exiger la meme chose des points d'eau.
                SwitchLine(
                    stringResource(R.string.settings_poi_bike_only), filtres.isBikeOnly(groupe),
                    sub = stringResource(R.string.settings_poi_bike_only_sub),
                ) { sauve(filtres.toggleBike(groupe)) }
            }
        }
    }
    SettingsCard {
        Hint(stringResource(R.string.settings_poi_attribution))
    }

    // Le lissage et l'echelle verticale ont quitte l'onglet "Carte" pour celui-ci : ils ne decrivent pas
    // ce qui s'affiche mais comment le profil se CALCULE, la ligne de partage que les deux onglets
    // revendiquent depuis toujours.
    SectionTitle(stringResource(R.string.settings_section_profile_calc))
    SettingsCard {
        // Valeurs autorisees : 1 m, puis pas de 5 jusqu'a 100 m (souvent ~1 point GPS tous les 80 m). Non
        // equidistantes (1->5) : le curseur parcourt un index et rend la valeur, plutot qu'une plage
        // continue. Un reglage existant hors liste est ramene a la valeur la plus proche.
        val smoothing = remember { listOf(1) + (5..100 step 5).toList() }
        val smoothingIdx = smoothing.indexOf(cur.profileSmoothingM).let { exact ->
            if (exact >= 0) exact
            else smoothing.indices.minByOrNull { kotlin.math.abs(smoothing[it] - cur.profileSmoothingM) } ?: 0
        }
        SliderRow(
            stringResource(R.string.settings_label_smoothing), "${cur.profileSmoothingM} m",
            fractionOf(smoothingIdx, 0, smoothing.lastIndex), steps = smoothing.size - 2,
            onFraction = { vm.save(cur.copy(profileSmoothingM = smoothing[valueOf(it, 0, smoothing.lastIndex)])) },
        )
        Hint(stringResource(R.string.settings_profile_smoothing_hint))
        RowDivider()
        // Echelle verticale : Auto (0 = remplit la hauteur) ou "1 cm = N m" (metres d'altitude par cm
        // physique). Bornes choisies d'apres la hauteur du graphe (~1,6 cm). Valeurs non equidistantes,
        // donc curseur indexe, comme le lissage.
        val scales = remember { listOf(0, 50, 100, 150, 200, 250, 300, 500, 800, 1200) }
        val scaleIdx = scales.indexOf(cur.profileVerticalScaleMPerCm).let { if (it >= 0) it else 0 }
        SliderRow(
            stringResource(R.string.settings_label_vertical_scale),
            if (cur.profileVerticalScaleMPerCm <= 0) stringResource(R.string.settings_vertical_scale_auto)
            else stringResource(R.string.settings_vertical_scale_value, cur.profileVerticalScaleMPerCm),
            fractionOf(scaleIdx, 0, scales.lastIndex), steps = scales.size - 2,
            onFraction = { vm.save(cur.copy(profileVerticalScaleMPerCm = scales[valueOf(it, 0, scales.lastIndex)])) },
        )
        Hint(stringResource(R.string.settings_vertical_scale_hint))
        // Ne remet a zero que les DEUX reglages ci-dessus, les seuls dont la bonne valeur ne se devine pas
        // a l'oeil : un lissage ou une echelle mal regles se remarquent longtemps apres, sur une autre
        // trace. Les autres reglages du profil se jugent immediatement et se defont seuls.
        CardAction(stringResource(R.string.action_reset_defaults)) {
            vm.save(cur.copy(profileSmoothingM = 5, profileVerticalScaleMPerCm = 0))
        }
    }

    // Dans cet onglet et non dans "Carte" : c'est de la matiere du profil qu'il s'agit, comme le lissage
    // au-dessus, et non de ce qui s'affiche. Les deux services ne s'ouvrent qu'une fois le completement
    // demande - trois champs d'URL sous un interrupteur eteint n'auraient rien a regler.
    SectionTitle(stringResource(R.string.settings_section_elevation_fill))
    SettingsCard {
        SwitchLine(
            stringResource(R.string.settings_label_fill_elevation), cur.fillMissingElevation,
        ) { vm.save(cur.copy(fillMissingElevation = it)) }
        Hint(stringResource(R.string.settings_fill_elevation_hint))
        if (cur.fillMissingElevation) {
            RowDivider()
            FieldRow(stringResource(R.string.settings_label_elevation_france)) {
                SettingsTextField(cur.elevationIgnUrl, IgnElevation.DEFAULT_URL) {
                    vm.save(cur.copy(elevationIgnUrl = it.trim()))
                }
            }
            RowDivider()
            FieldRow(stringResource(R.string.settings_label_elevation_world)) {
                SettingsTextField(cur.elevationWorldUrl, OpenTopo.DEFAULT_URL) {
                    vm.save(cur.copy(elevationWorldUrl = it.trim()))
                }
            }
            RowDivider()
            FieldRow(stringResource(R.string.settings_label_elevation_world_key)) {
                SettingsTextField(cur.elevationWorldKey, OpenTopo.DEFAULT_KEY) {
                    vm.save(cur.copy(elevationWorldKey = it.trim()))
                }
            }
            Hint(stringResource(R.string.settings_services_hint))
        }
    }
}

/**
 * Reglage "Mises a jour" : mode Auto/Manuel, et en Manuel seulement le bouton de verification immediate,
 * pose sur la meme ligne que les puces de mode (d'ou sa hauteur et son libelle calques dessus, sans quoi
 * la ligne deborderait en largeur sur un ecran etroit).
 * En build debug, la verification est inoperante (cf. UpdateManager.isSupported) : on le dit plutot que de
 * laisser un bouton qui ne repondrait jamais rien.
 */
@Composable private fun ColumnScopeMarker.UpdatesRow(cur: SettingsEntity, vm: SettingsViewModel) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<ReleaseInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val upToDate = stringResource(R.string.update_none_available)
    val failed = stringResource(R.string.update_check_failed)

    SetRow(stringResource(R.string.settings_label_updates)) {
        SettingsChip(stringResource(R.string.update_mode_auto), cur.updateCheckMode != "manual") {
            vm.save(cur.copy(updateCheckMode = "auto"))
        }
        SettingsChip(stringResource(R.string.update_mode_manual), cur.updateCheckMode == "manual") {
            vm.save(cur.copy(updateCheckMode = "manual"))
        }
        if (UpdateManager.isSupported && cur.updateCheckMode == "manual") {
            if (checking) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                InlineButton(stringResource(R.string.update_action_check)) {
                    checking = true; message = null
                    scope.launch {
                        when (val r = UpdateManager.check()) {
                            is UpdateCheck.Available -> found = r.release
                            UpdateCheck.UpToDate -> message = upToDate
                            UpdateCheck.Failed -> message = failed
                        }
                        checking = false
                    }
                }
            }
        }
    }
    // En build debug, la verification est inoperante (cf. UpdateManager.isSupported) : on le dit plutot
    // que de laisser un bouton qui ne repondrait jamais rien.
    if (!UpdateManager.isSupported) Hint(stringResource(R.string.update_unsupported_debug))
    message?.let { Hint(it) }
    UpdateFlow(release = found, onDone = { found = null })
}

@Composable private fun SystemTab(
    cur: SettingsEntity, vm: SettingsViewModel,
    onPickImportDir: () -> Unit, onPickMbtilesFolder: () -> Unit, onPickAvatar: () -> Unit,
    onBackup: () -> Unit, onRestore: () -> Unit,
) {
    val ctx = LocalContext.current
    var avatarDialogOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    // Treize rubriques a plat devenaient une liste ou l'on cherchait. Quatre groupes : ou ca se range, ce
    // qui repond au doigt, ce que ca donne a voir, et ce que l'application fait d'elle-meme.
    GroupTitle(stringResource(R.string.settings_group_storage), first = true)
    SettingsCard {
        SetRow(
            stringResource(R.string.settings_section_import_folder),
            sub = if (cur.importDir.isBlank()) stringResource(R.string.settings_default_system)
                else (cur.importDir.toUri().lastPathSegment ?: stringResource(R.string.settings_default_system)),
        ) {
            InlineButton(stringResource(R.string.action_browse), Icons.Filled.Folder, onPickImportDir)
        }
        RowDivider()
        SetRow(
            stringResource(R.string.settings_section_mbtiles_folder),
            sub = if (cur.mbtilesDir.isBlank()) stringResource(R.string.settings_app_folder_default)
                else StoragePaths.displayName(cur.mbtilesDir),
        ) {
            InlineButton(stringResource(R.string.action_browse), Icons.Filled.Folder, onPickMbtilesFolder)
        }
    }

    // La contrepartie du "aucun compte, aucune synchronisation" : sans elle, un telephone change emporte
    // tout. Rangee sous "Stockage" et non dans un groupe a elle : c'est de la meme matiere qu'il s'agit -
    // ou vivent les donnees, et comment elles en sortent.
    SectionTitle(stringResource(R.string.settings_section_backup))
    SettingsCard {
        SetRow(stringResource(R.string.settings_backup_create)) {
            InlineButton(stringResource(R.string.action_save), Icons.Filled.FileUpload, onBackup)
        }
        RowDivider()
        SetRow(stringResource(R.string.settings_backup_restore)) {
            InlineButton(stringResource(R.string.action_restore), Icons.Filled.FileDownload, onRestore)
        }
        Hint(stringResource(R.string.settings_backup_hint))
    }

    GroupTitle(stringResource(R.string.settings_group_interaction))
    SectionTitle(stringResource(R.string.settings_section_side_menu_open), tight = true)
    SettingsCard {
        SegChips(
            listOf("burger" to stringResource(R.string.side_menu_burger),
                "swipe" to stringResource(R.string.side_menu_swipe),
                "both" to stringResource(R.string.side_menu_both)),
            cur.sideMenuMode,
        ) { vm.save(cur.copy(sideMenuMode = it)) }
    }
    // Deux tolerances : les marqueurs sont interroges avant les traces et l'emportent, une valeur large
    // sur eux rend une trace qui passe a cote difficile a atteindre.
    SectionTitle(stringResource(R.string.settings_section_tap_tolerance))
    SettingsCard {
        SliderRow(
            stringResource(R.string.settings_label_waypoints), "${cur.tapToleranceDp} dp",
            fractionOf(cur.tapToleranceDp, 4, 40), steps = 35,
            onFraction = { vm.save(cur.copy(tapToleranceDp = valueOf(it, 4, 40))) },
        )
        RowDivider()
        SliderRow(
            stringResource(R.string.settings_label_tracks), "${cur.lineTapToleranceDp} dp",
            fractionOf(cur.lineTapToleranceDp, 4, 40), steps = 35,
            onFraction = { vm.save(cur.copy(lineTapToleranceDp = valueOf(it, 4, 40))) },
        )
    }

    GroupTitle(stringResource(R.string.settings_group_appearance))
    var currentLang by remember { mutableStateOf(LocalePrefs.get(ctx)) }
    SettingsCard {
        PickRow(
            stringResource(R.string.settings_label_theme), cur.theme,
            listOf("system", "light", "dark"),
            optionLabel = {
                stringResource(when (it) {
                    "light" -> R.string.theme_light
                    "dark" -> R.string.theme_dark
                    else -> R.string.theme_system
                })
            },
        ) { vm.save(cur.copy(theme = it)) }
        RowDivider()
        PickRow(
            stringResource(R.string.settings_label_language), currentLang,
            LocalePrefs.SELECTABLE, optionLabel = { LocalePrefs.nativeName(it) },
        ) { code ->
            currentLang = code
            LocalePrefs.set(ctx, code)
            (ctx as? Activity)?.recreate()
        }
        RowDivider()
        PickRow(
            stringResource(R.string.settings_label_units), cur.units, listOf("meters", "imperial"),
            optionLabel = { stringResource(if (it == "imperial") R.string.unit_imperial else R.string.unit_metric) },
        ) { vm.save(cur.copy(units = it)) }
        RowDivider()
        SwitchLine(
            stringResource(R.string.settings_status_bar_transparent), cur.statusBarTransparent,
            sub = stringResource(R.string.settings_sw_status_bar_sub),
        ) { vm.save(cur.copy(statusBarTransparent = it)) }
    }

    SectionTitle(stringResource(R.string.settings_section_personalisation))
    var titleText by remember(cur.customTitle) { mutableStateOf(cur.customTitle) }
    SettingsCard {
        FieldRow(stringResource(R.string.settings_label_side_title)) {
            SettingsTextField(titleText, stringResource(R.string.drawer_default_title)) { titleText = it }
        }
        RowDivider()
        SetRow(stringResource(R.string.settings_label_avatar)) {
            if (cur.avatarSource.isNotBlank()) {
                RowIcon(Icons.Filled.DeleteOutline, stringResource(R.string.action_reset_avatar)) {
                    vm.save(cur.copy(avatarSource = ""))
                }
            }
            RowIcon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.action_change_avatar)) {
                avatarDialogOpen = true
            }
        }
        CardAction(stringResource(R.string.action_save)) { vm.save(cur.copy(customTitle = titleText)) }
    }

    GroupTitle(stringResource(R.string.settings_group_application))
    SettingsCard {
        SwitchLine(
            stringResource(R.string.settings_simplify_render), cur.simplifyRender,
            sub = stringResource(R.string.settings_sw_simplify_sub),
        ) { vm.save(cur.copy(simplifyRender = it)) }
        Hint(stringResource(R.string.settings_simplify_render_hint))
        RowDivider()
        UpdatesRow(cur, vm)
        CardAction(stringResource(R.string.action_reset_all_settings)) { confirmReset = true }
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
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
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

private val CompactIconButtonSize = 40.dp
private val CompactIconSize = 22.dp
private val CompactChipHeight = 36.dp

@Composable private fun Section(t: String) {
    Spacer(Modifier.height(16.dp))
    Text(t, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

/**
 * Titre de groupe : un cran au-dessus de [Section], pour reunir les rubriques d'un meme sujet dans un
 * onglet devenu long.
 *
 * Se distingue par la taille, la couleur d'accent et un filet, et non par un simple gras : entre un
 * `titleSmall` de rubrique et un `titleMedium` de groupe, l'ecart seul ne suffirait pas a faire lire une
 * hierarchie sur une liste qui defile.
 */
@Composable private fun Group(t: String) {
    Spacer(Modifier.height(24.dp))
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(top = 2.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
}

/**
 * Ligne a interrupteur : l'interrupteur d'abord, son libelle ensuite.
 *
 * La ligne entiere est cliquable, pas seulement l'interrupteur : c'est le libelle qu'on vise du regard,
 * et une cible de 40 dp de large au bout d'une phrase de trente caracteres se rate.
 */
@Composable private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 4.dp),
    ) {
        Switch(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable private fun CompactIconButton(onClick: () -> Unit, contentDescription: String?, icon: ImageVector) {
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

/**
 * Curseur de reglage, celui de Material sans retouche.
 *
 * Il a longtemps ete redessine plus fin (piste de 4 dp, pastille de 14 dp) pour tenir plus de rubriques
 * a l'ecran. La pastille s'attrapait mal et la piste se lisait mal ; l'ecran des reglages se parcourt en
 * defilant, la place gagnee ne valait pas ce qu'elle coutait.
 */
@Composable private fun CompactSlider(
    value: Float, onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Slider(
        value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps,
        onValueChangeFinished = onValueChangeFinished,
    )
}

@Composable private fun FontStepper(
    label: String, value: Int, min: Int = 7, max: Int = 28,
    bold: Boolean? = null, onBold: ((Boolean) -> Unit)? = null, onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (bold != null && onBold != null) {
            FilterChip(selected = bold, onClick = { onBold(!bold) },
                label = { Text(stringResource(R.string.settings_bold_abbrev), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(CompactChipHeight).padding(end = 4.dp))
        }
        CompactIconButton(onClick = { if (value > min) onChange(value - 1) }, stringResource(R.string.action_decrease), Icons.Filled.Remove)
        Text("$value", Modifier.widthIn(min = 26.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
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
/** Fond affiche au demarrage : une ligne de carte, le nom en accent et le menu de tous les fonds. */
@Composable private fun ColumnScopeMarker.BasemapPickRow(
    providers: List<ProviderEntity>, composites: List<CompositeEntity>, current: String, onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val currentLabel = providers.firstOrNull { it.id == current }?.name
        ?: composites.firstOrNull { compositeBasemapId(it.id) == current }?.name
        ?: current
    SetRow(stringResource(R.string.settings_section_default_basemap), onClick = { open = true }) {
        PickValue(currentLabel, open, { open = false }) {
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
                Text(LocalePrefs.nativeName(current), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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

/** Miniature titrée (SPEC section 6), affichée en lecture seule côte à côte dans l'éditeur d'un MBTILES, avec
 *  un bouton d'agrandissement qui ouvre l'image en grand (~80 % de l'écran). */
@Composable private fun ThumbColumn(title: String, file: File, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val model = "file://${file.absolutePath}"
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = settingsPalette.subtle)
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
                Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(8.dp), color = settingsPalette.card) {
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

/** Ligne d'un fond dans une carte de reglages, depliable en editeur. Extension de la portee d'une carte :
 *  elle en emprunte les filets, ses champs et son action de fin. */
@Composable private fun ColumnScopeMarker.ProviderRow(
    p: ProviderEntity, onSave: (ProviderEntity) -> Unit, onDelete: (() -> Unit)? = null,
    mbtilesDirPath: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var name by remember(p.id) { mutableStateOf(p.name) }
    var url by remember(p.id) { mutableStateOf(p.urlTemplate) }
    var key by remember(p.id) { mutableStateOf(p.apiKey ?: "") }
    var zoom by remember(p.id) { mutableStateOf(p.minZoom.toFloat()..p.maxZoom.toFloat()) }
    val isMbtiles = p.type == "MBTILES"
    val flagCode = flagCodeFor(p)
    fun resetFields() {
        name = p.name; url = p.urlTemplate; key = p.apiKey ?: ""
        zoom = p.minZoom.toFloat()..p.maxZoom.toFloat()
    }
    Column(Modifier.fillMaxWidth()) {
        // Meme grammaire que les lignes des onglets : drapeau ou rien, libelle, actions a droite, l'etat
        // en dernier. Le type ne s'affiche plus a cote du nom mais sous lui - il decrit le fond, il ne le
        // nomme pas.
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp).padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (flagCode != null) {
                AsyncImage(model = flagAssetModel(flagCode), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(18.dp, 13.dp).clip(RoundedCornerShape(3.dp)))
            }
            Column(Modifier.weight(1f)) {
                Text(p.name, fontSize = 12.5.sp, color = settingsPalette.label)
                Text(p.type, fontSize = 10.5.sp, color = settingsPalette.subtle, modifier = Modifier.padding(top = 2.dp))
            }
            RowIcon(
                if (expanded) Icons.Filled.Close else Icons.Filled.Edit,
                if (expanded) stringResource(R.string.action_close) else stringResource(R.string.action_edit),
            ) {
                // Fermer sans "Enregistrer" annule les modifications en cours.
                if (expanded) resetFields()
                expanded = !expanded
            }
            if (onDelete != null) {
                RowIcon(Icons.Filled.DeleteOutline, stringResource(R.string.action_delete), onClick = onDelete)
            }
            SettingsSwitch(p.enabled) { onSave(p.copy(enabled = it)) }
        }
        if (expanded) {
            // Champs de l'editeur : le cadre de 42 dp de l'ecran, avec sa legende au-dessus. Les memes
            // que ceux des URL de services, pour qu'un fond se corrige comme un service se regle.
            RowDivider()
            FieldRow(stringResource(R.string.settings_field_name)) {
                SettingsTextField(name, "") { name = it }
            }
            // Pour un MBTILES, le fichier est fixe (résolu via le dossier mbtiles) et son emplacement est
            // affiché plus bas en lecture seule : pas de champ éditable. 'url' conserve sa valeur d'origine.
            if (!isMbtiles) {
                FieldRow(stringResource(R.string.settings_field_url)) { SettingsTextField(url, "") { url = it } }
                FieldRow(stringResource(R.string.settings_field_api_key)) { SettingsTextField(key, "") { key = it } }
                // Plage de zoom servie par le fond, au curseur double plutot qu'en deux nombres a saisir :
                // les niveaux vont de 0 a 22 et se choisissent l'un par rapport a l'autre, ce qu'une paire
                // de champs ne montre pas. Elle remplace la taille de tuile, qui vaut 256 partout sauf de
                // rares exceptions et qu'on ne corrigeait jamais. (Meme libelle que l'ecran de
                // telechargement : c'est la meme plage, dite de la meme facon.)
                RangeSliderRow(
                    label = stringResource(R.string.offline_config_zoom_label),
                    value = "${zoom.start.toInt()} - ${zoom.endInclusive.toInt()}",
                    range = zoom, bounds = 0f..22f, steps = 21,
                    onRange = { zoom = it },
                )
            }
            // Force de l'ombrage, propre au relief : c'est le seul rendu qu'un fond DEM expose, ses tuiles
            // brutes n'étant pas des images à regarder. Enregistrée au relâché du curseur plutôt qu'à
            // chaque pixel : chaque valeur reconstruit le style de la carte.
            if (p.type == "DEM") {
                var opacity by remember(p.id, p.opacityPct) { mutableFloatStateOf(p.opacityPct.toFloat()) }
                SliderRow(
                    stringResource(R.string.settings_label_opacity), "${opacity.toInt()} %",
                    fraction = opacity / 100f, onFraction = { opacity = it * 100f },
                )
                LaunchedEffect(opacity) {
                    // Ecrit apres un court repos du doigt : chaque valeur reconstruit le style de la carte.
                    kotlinx.coroutines.delay(200)
                    if (opacity.toInt() != p.opacityPct) onSave(p.copy(opacityPct = opacity.toInt()))
                }
                if (p.opacityPct != DefaultDemOpacityPct) {
                    CardAction(stringResource(R.string.action_reset_defaults)) {
                        onSave(p.copy(opacityPct = DefaultDemOpacityPct))
                    }
                }
            }
            // Plage de zoom réellement contenue dans le MBTiles (fixée au téléchargement/import) : en
            // lecture seule, l'éditer ne changerait pas les tuiles présentes.
            if (isMbtiles) {
                Text(
                    stringResource(R.string.settings_field_zoom_levels, p.minZoom, p.maxZoom),
                    fontSize = 12.5.sp, color = settingsPalette.subtle,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 2.dp),
                )
                // Chemin réel du fichier (urlTemplate ne porte que le nom, résolu via le dossier mbtiles).
                val fullPath = when {
                    p.urlTemplate.startsWith("mbtiles://") -> p.urlTemplate.removePrefix("mbtiles://")
                    mbtilesDirPath != null -> "$mbtilesDirPath/${p.urlTemplate}"
                    else -> p.urlTemplate
                }
                Text(
                    stringResource(R.string.settings_field_mbtiles_location, fullPath),
                    fontSize = 10.5.sp, lineHeight = 15.sp, color = settingsPalette.subtle,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 4.dp),
                )
                // Miniatures générées à la fin du téléchargement (SPEC section 6), affichées si présentes.
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
            // (Pour un MBTILES, url/clé/zoom sont masqués et conservent leur valeur : seul le nom peut varier.)
            val minZ = zoom.start.toInt()
            val maxZ = zoom.endInclusive.toInt()
            val dirty = name != p.name || url != p.urlTemplate ||
                key != (p.apiKey ?: "") || minZ != p.minZoom || maxZ != p.maxZoom
            if (dirty) {
                Spacer(Modifier.height(8.dp))   // léger espacement au-dessus du bouton
                OutlinedButton(
                    onClick = {
                        onSave(p.copy(name = name, urlTemplate = url, apiKey = key.ifBlank { null },
                            minZoom = minZ, maxZoom = maxZ))
                        expanded = false
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text(stringResource(R.string.action_save)) }
            }
            Spacer(Modifier.height(12.dp))   // margin-bottom de l'éditeur
        }
    }
}

/**
 * Relance l'application depuis zero.
 *
 * Seul geste de l'application qui tue son propre processus, et il n'a qu'un seul appelant : apres une
 * restauration, la base sur le disque n'est plus celle que Room a ouverte. Rouvrir proprement supposerait
 * de reconstruire tout ce qui en depend - flux, caches, ecrans - alors qu'un redemarrage le fait sans
 * risque d'en oublier un. L'ecran suivant est celui du demarrage normal, avec les donnees restaurees.
 */
private fun restartApp(ctx: android.content.Context) {
    val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) ctx.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
