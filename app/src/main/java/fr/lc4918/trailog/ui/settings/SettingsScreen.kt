package fr.lc4918.trailog.ui.settings

import android.content.Intent
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.backup.BackupFileName
import fr.lc4918.trailog.data.repo.StoragePaths
import fr.lc4918.trailog.ui.components.Avatar
import fr.lc4918.trailog.ui.theme.isDarkTheme
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
        stringResource(R.string.settings_tab_routes) to Icons.Outlined.Directions,
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
