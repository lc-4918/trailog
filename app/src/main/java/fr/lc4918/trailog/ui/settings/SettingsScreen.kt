package fr.lc4918.trailog.ui.settings

import androidx.core.net.toUri
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.SnackbarDuration
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.os.SystemClock
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateMapOf
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
import fr.lc4918.trailog.ui.components.BusySpinner
import fr.lc4918.trailog.ui.components.Avatar
import fr.lc4918.trailog.ui.theme.isDarkTheme
import kotlinx.coroutines.launch



/**
 * Le groupe dont on lit les lignes, d'apres l'element de liste en tete.
 *
 * [starts] donne, pour chaque groupe, l'indice de l'element ou il commence. Le groupe courant est donc
 * le dernier dont l'element est deja passe en tete - ou avant lui.
 *
 * **La barre se tait tant que le titre se lit encore.** Elle est posee PAR-DESSUS le contenu : tant que
 * l'element qui porte le titre n'a pas defile de la hauteur de la barre, le titre reste visible, et
 * l'afficher en double ne ferait que le repeter.
 */
@Composable
internal fun groupeEnTete(etat: LazyListState, starts: List<Pair<Int, String>>, barHeightPx: Float): String? {
    val enTete = etat.firstVisibleItemIndex
    val decale = etat.firstVisibleItemScrollOffset
    val i = starts.indexOfLast { it.first <= enTete }
    if (i < 0) return null
    val (debut, nom) = starts[i]
    if (debut == enTete && decale < barHeightPx) return null
    return nom
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    /** Ferme les reglages et ouvre le cadrage d'une zone a telecharger sur la carte. */
    onDownloadArea: () -> Unit = {},
    vm: SettingsViewModel = viewModel(),
) {
    val s by vm.settings.collectAsState()
    val providers by vm.providers.collectAsState()
    val composites by vm.composites.collectAsState()
    val status by vm.status.collectAsState()
    /*
     * Les reglages arrivent de la base, et l'ecran ne tient rien a dire en les attendant.
     *
     * Un rond d'attente y a vecu une soiree, puis a ete mesure : la base repond en 40 ms, et les 110 ms
     * qui suivent sont de la COMPOSITION - fil d'affichage occupe, donc aucune image produite, donc un
     * rond fige. Un temoin d'attente qui ne tourne pas ment sur ce qu'il annonce, et il ment ici pendant
     * plus longtemps qu'il n'informe.
     */
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
    val brouterDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            StoragePaths.treeUriToPath(ctx, it)?.let { path -> vm.chooseBrouterDir(path) }
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
    val expertTaps = remember { ExpertTaps() }
    // Le menu de l'avatar : "A propos" et "Aide". Il vit a cote du geste des sept appuis, qui continue de
    // se compter par-dessous (cf. ExpertTaps).
    var menuAvatar by remember { mutableStateOf(false) }
    var aboutOuvert by remember { mutableStateOf(false) }
    val expertOn = stringResource(R.string.settings_expert_on)
    val expertOff = stringResource(R.string.settings_expert_off)
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
                /*
                 * Sept appuis rapproches sur l'avatar allument ou eteignent le mode expert (cf. ExpertTaps),
                 * et une alerte le dit trois secondes : un geste cache doit au moins dire ce qu'il a fait.
                 * Sans ondulation : ce n'est pas un bouton qu'on propose.
                 */
                actions = {
                    Box {
                    Avatar(
                        cur.avatarSource, size = 26.dp,
                        modifier = Modifier.padding(end = 14.dp).testTag("settings_avatar").clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                        ) {
                            // Un tap ouvre le menu et le LAISSE ouvert : les sept appuis se comptent
                            // dessous sans le faire clignoter, et le septieme le referme.
                            menuAvatar = true
                            if (expertTaps.tap(SystemClock.elapsedRealtime())) {
                                menuAvatar = false
                                val on = !cur.expertMode
                                vm.save(cur.copy(expertMode = on))
                                val texte = if (on) expertOn else expertOff
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    withTimeoutOrNull(ExpertAlertMs) {
                                        snackbar.showSnackbar(texte, duration = SnackbarDuration.Indefinite)
                                    }
                                }
                            }
                        },
                    )
                    DropdownMenu(expanded = menuAvatar, onDismissRequest = { menuAvatar = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.about_title)) },
                            onClick = { menuAvatar = false; aboutOuvert = true },
                            modifier = Modifier.testTag("menu_about"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.help_title)) },
                            onClick = {
                                menuAvatar = false
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, HelpUrl.toUri())) }
                            },
                            modifier = Modifier.testTag("menu_help"),
                        )
                    }
                    }
                },
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
            /*
             * Le groupe en cours de lecture, quand son titre est deja passe en haut : un onglet porte
             * quatre ou cinq groupes et une trentaine de lignes, et l'on reglait des curseurs sans plus
             * savoir a quoi ils se rapportaient (cf. GroupBarState). Un etat par onglet : chacun a ses
             * groupes, et changer d'onglet ne doit pas garder le titre du precedent.
             */
            /*
             * Un etat PAR ONGLET, garde dans une table plutot que recree : pendant la transition d'un
             * onglet a l'autre, les deux contenus coexistent, et celui qui s'en va continue de signaler
             * ses titres. Avec un seul etat, ils se seraient deposes dans celui du nouvel onglet, qui
             * aurait affiche le nom d'un groupe appartenant a l'ancien.
             */
            val etatsGroupes = remember { mutableStateMapOf<Int, GroupBarState>() }
            val barreGroupe = etatsGroupes.getOrPut(tab) { GroupBarState() }
            // L'etat de defilement de chaque onglet, garde ici : c'est lui qui dit quel element est en
            // tete, et donc dans quel groupe on se trouve (cf. mapTabGroupStarts).
            val etatsListe = remember { mutableStateMapOf<Int, LazyListState>() }
            val etatListe = etatsListe.getOrPut(tab) { LazyListState() }
            // Les groupes de l'onglet, tels que son contenu les a comptes (cf. TabGroups).
            val groupesParOnglet = remember { mutableStateMapOf<Int, TabGroups>() }
            // La bande que la barre occupe : le relais se fait des que le titre passe dessous.
            barreGroupe.barHeight = with(LocalDensity.current) { GroupBarHeight.toPx() }
            /*
             * Le groupe courant se lit dans la LISTE pour l'onglet paresseux, et dans les positions des
             * titres pour les autres, qui se composent encore d'un bloc. Les deux repondent a la meme
             * question, mais un titre hors de l'ecran n'existe plus dans une liste paresseuse : au milieu
             * d'un groupe, aucune position ne dirait plus ou l'on est.
             */
            val hauteurBarre = with(LocalDensity.current) { GroupBarHeight.toPx() }
            val debuts = groupesParOnglet[tab]?.starts.orEmpty()
            val groupeCourant = if (tab == 0) {
                groupeEnTete(etatListe, debuts.map { (i, res) -> i to stringResource(res) }, hauteurBarre)
            } else {
                barreGroupe.current
            }
            /*
             * La barre est POSEE PAR-DESSUS le contenu, et non intercalee au-dessus de lui.
             *
             * Intercalee, son apparition rendait sa hauteur au contenu, qui sautait d'une trentaine de
             * pixels sous le doigt : le defilement rebondissait au moment meme ou l'on commencait a lire.
             * Par-dessus, rien ne bouge - le contenu passe dessous, comme sous n'importe quel en-tete
             * collant - et il ne reste qu'un fondu.
             */
            Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(250)) { dir * it } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(250)) { -dir * it } + fadeOut(tween(200)))
                },
                label = "settings_tab"
            ) { currentTab ->
                /*
                 * Une liste PARESSEUSE, et non plus une colonne qui defile.
                 *
                 * Un onglet fait cinq cents lignes de composables, et la colonne les composait toutes
                 * d'un coup, sur le fil d'affichage, en une seule image : le telephone sautait 38 a 57
                 * images a chaque ouverture - pres d'une seconde d'ecran fige, pendant laquelle aucun
                 * temoin d'attente ne pouvait meme tourner. La liste ne compose que ce qui se voit.
                 *
                 * L'etat de la barre de groupe et celui du defilement appartiennent au contenu AFFICHE
                 * et non a l'onglet vise : pendant la transition, les deux se chevauchent.
                 */
                val barreDuContenu = etatsGroupes.getOrPut(currentTab) { GroupBarState() }
                CompositionLocalProvider(LocalGroupBar provides barreDuContenu) {
                    LazyColumn(
                        state = etatsListe.getOrPut(currentTab) { LazyListState() },
                        modifier = Modifier.fillMaxSize().testTag("settings_list")
                            // Le haut de la zone qui defile, mesure AVANT le defilement : pose apres, le
                            // repere suivait le contenu vers le haut, et plus aucun titre ne le franchissait.
                            .onGloballyPositioned { barreDuContenu.viewportTop = it.positionInWindow().y },
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 22.dp),
                    ) {
                        when (currentTab) {
                            0 -> mapTab(cur, vm, groupesParOnglet.getOrPut(currentTab) { TabGroups() })
                            // Les trois autres onglets restent d'une piece pour l'instant : un seul
                            // element de liste, donc composes en entier comme avant.
                            1 -> item {
                                TilesTab(cur, providers, composites, vm, onPickMbtiles = { mbPicker.launch("*/*") },
                                    onDownloadArea = onDownloadArea)
                            }
                            2 -> item { RoutesTab(cur, vm) }
                            else -> item {
                                SystemTab(cur, vm,
                                    onPickImportDir = { importDirPicker.launch(null) },
                                    onPickMbtilesFolder = { treePicker.launch(null) },
                                    onPickBrouterFolder = { brouterDirPicker.launch(null) },
                                    onPickAvatar = { avatarPicker.launch("image/*") },
                                    onBackup = { backupWriter.launch(BackupFileName.of(System.currentTimeMillis())) },
                                    onRestore = { restoreTarget = true })
                            }
                        }
                    }
                }
            }
            // Un simple fondu, et une barre toujours posee : l'apparition ne mesure rien et ne deplace
            // rien. Le texte garde le dernier groupe le temps de s'effacer, sans quoi il disparaitrait
            // d'un coup au milieu du fondu.
            val opacite by animateFloatAsState(if (groupeCourant != null) 1f else 0f, tween(150), label = "groupe")
            val groupeAffiche = remember { mutableStateOf("") }
            if (groupeCourant != null) groupeAffiche.value = groupeCourant
            if (opacite > 0f) {
                Text(
                    groupeAffiche.value,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.label,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().alpha(opacite)
                        .background(palette.card)
                        .heightIn(min = GroupBarHeight)
                        .padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 6.dp)
                        .testTag("settings_group_bar"),
                )
            }
            }
        }
    }
    if (aboutOuvert) AboutDialog(onDismiss = { aboutOuvert = false })
    }
}

/* --------------- Onglets --------------- */

/** Duree de l'alerte du mode expert : trois secondes, le temps de la lire sans avoir a la fermer. */
private const val ExpertAlertMs = 3_000L

/** Hauteur de la barre du groupe courant : le titre lui passe dessous, elle prend alors le relais. */
private val GroupBarHeight = 26.dp
