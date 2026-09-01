package fr.lc4918.trailog.ui.routes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.SchemaItem
import fr.lc4918.trailog.location.TrackWatch
import fr.lc4918.trailog.routing.GpxWriter
import fr.lc4918.trailog.ui.alert.OffTrackAlertState
import fr.lc4918.trailog.ui.alert.FollowProgress
import fr.lc4918.trailog.ui.alert.TrackChooserDialog
import fr.lc4918.trailog.ui.edit.TrackEditState
import fr.lc4918.trailog.ui.location.LocationControls
import fr.lc4918.trailog.ui.planner.RoutePlannerState
import fr.lc4918.trailog.ui.planner.defaultRouteName
import fr.lc4918.trailog.ui.points.PropertyEditor
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField
import fr.lc4918.trailog.ui.settings.ProvideSettingsPalette
import fr.lc4918.trailog.ui.settings.settingsPalette
import fr.lc4918.trailog.ui.settings.SettingsCard
import fr.lc4918.trailog.ui.settings.SetRow
import fr.lc4918.trailog.ui.settings.RowDivider
import fr.lc4918.trailog.ui.settings.Hint
import fr.lc4918.trailog.ui.settings.RowIcon
import androidx.compose.material.icons.filled.KeyboardArrowRight

/**
 * Les boites de dialogue de l'ecran principal.
 *
 * Aucune ne lit l'etat de l'ecran : chacune recoit ce qu'elle affiche et rend ce qu'on en a fait. C'est ce
 * qui permet de les sortir de `MainScreen`, ou elles occupaient trois cents lignes a la file, chacune
 * refermee sur un `var` de la composition qu'elle etait seule a lire.
 *
 * Celles qui gardent une saisie le font chez elles - le nom d'un dossier, celui d'une couche : la boite
 * s'ouvre, on tape, on valide, et rien de tout cela ne survit a sa fermeture. C'est bien un etat local, et
 * il l'est desormais pour de bon.
 *
 * Deux exceptions restent dehors, et pour la meme raison : `PropertyEditor` et `TrackChooserDialog` sont
 * des composants a part entiere, avec leur propre fichier - ils ne sont pas des boites de cet ecran-ci.
 */

/**
 * Choix de ce que l'on telecharge : un rectangle, ou le couloir qui borde une trace.
 *
 * Le rectangle etait le seul mode, et il reste le bon pour un secteur qu'on ne connait pas encore. Pour
 * une sortie deja tracee, il fait telecharger tout ce qui l'entoure - trois quarts de tuiles qu'on ne
 * verra jamais sur une diagonale de soixante kilometres.
 */
@Composable
internal fun OfflineExtentDialog(
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
internal fun ExportFormatDialog(dark: Boolean, onDismiss: () -> Unit, onPick: (geoJson: Boolean) -> Unit) {
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

/**
 * Choix de la trace dont on veut border le couloir, quand [OfflineExtentDialog] a repondu "une trace".
 *
 * Seules les couches qui portent une ligne : un dossier de points n'a pas de couloir. La liste peut etre
 * vide - c'est un cas normal, une application fraichement installee - et le dit alors plutot que d'ouvrir
 * une boite sans rien dedans.
 */
@Composable
internal fun OfflineTrackPickDialog(
    candidates: List<LayerEntity>, onPick: (LayerEntity) -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.offline_extent_track)) },
        text = {
            if (candidates.isEmpty()) Text(stringResource(R.string.offline_extent_no_track))
            // Cible pleine largeur, comme les destinations d'import : choisir dans une liste se fait
            // a la ligne.
            else Column(Modifier.verticalScroll(rememberScrollState())) {
                candidates.forEach { l ->
                    TextButton(onClick = { onPick(l) }, modifier = Modifier.fillMaxWidth()) {
                        Text(l.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/**
 * Dossier d'accueil des fichiers a importer, demande avant le selecteur de fichier.
 *
 * [onDismiss] n'est pas qu'une fermeture : renoncer au dossier, c'est renoncer a l'import, et les fichiers
 * qu'une autre application nous a confies doivent etre relaches - sans quoi ils repartiraient au prochain
 * import, celui d'autre chose.
 */
@Composable
internal fun ImportFolderDialog(
    folders: List<FolderEntity>,
    onNewFolder: () -> Unit,
    onPick: (folderId: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_import_into_title)) },
        text = {
            // Chaque destination occupe toute la largeur : c'est la ligne qu'on vise, pas le mot. Un nom
            // de dossier court faisait autrement une cible de quelques millimetres au milieu du vide.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = onNewFolder, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CreateNewFolder, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.label_new_folder), modifier = Modifier.weight(1f))
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.label_root), modifier = Modifier.fillMaxWidth())
                }
                folders.forEach { f ->
                    TextButton(onClick = { onPick(f.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(f.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Creation d'un dossier, puis poursuite de l'import dedans.
 *
 * Le nom vit ici et nulle part ailleurs : la boite s'ouvre toujours sur un champ vide, et [fallbackName]
 * ("Nouveau dossier") tient lieu de nom si l'on valide sans rien taper.
 */
@Composable
internal fun NewFolderDialog(fallbackName: String, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_new_folder)) },
        text = {
            CompactOutlinedTextField(name, { name = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus))
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.ifBlank { fallbackName }) }) {
                Text(stringResource(R.string.action_create_and_import))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Import du parcours calcule en couche : son nom, puis son dossier d'accueil.
 *
 * Dans cet ordre parce que le nom est obligatoire et le dossier facultatif. Le choix de dossier ne
 * s'affiche que s'il y en a : sans dossier, la couche va forcement a la racine, et l'offrir serait une
 * question sans reponse possible.
 */
@Composable
internal fun RouteImportDialog(
    defaultName: String,
    folders: List<FolderEntity>,
    onImport: (name: String, folderId: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var layerName by remember { mutableStateOf(defaultName) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    // Meme cible pleine largeur que dans ImportFolderDialog : c'est la meme question.
                    TextButton(onClick = { onImport(layerName, null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.label_root), modifier = Modifier.fillMaxWidth())
                    }
                    folders.forEach { f ->
                        TextButton(onClick = { onImport(layerName, f.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(f.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        // Sans dossier, rien ne reste a choisir : le bouton de validation suffit a conclure. Avec des
        // dossiers, c'est le tap sur l'un d'eux qui conclut, et ce bouton disparait.
        confirmButton = {
            if (folders.isEmpty()) {
                TextButton(onClick = { onImport(layerName, null) }, enabled = layerName.isNotBlank()) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Ce qu'un lot d'import a refuse, en une seule fois.
 *
 * Un lot peut contenir les deux sortes de refus - fichier illisible, fichier sans geometrie - et chacune a
 * sa phrase, accordee en nombre.
 */
@Composable
internal fun ImportReportDialog(failures: List<MainViewModel.ImportFailure>, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val invalid = failures.filter { it.error == MainViewModel.ImportError.INVALID }.map { it.fileName }
    val empty = failures.filter { it.error == MainViewModel.ImportError.EMPTY }.map { it.fileName }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_import_result_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (invalid.isNotEmpty()) {
                    Text(res.getQuantityString(R.plurals.import_invalid_files, invalid.size, invalid.joinToString(", ")))
                }
                if (empty.isNotEmpty()) {
                    Text(res.getQuantityString(R.plurals.import_empty_files, empty.size, empty.joinToString(", ")))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

/**
 * Inverser une trace efface ses horodatages (cf. TrackEdit.reverse).
 *
 * Demande AVANT, et seulement quand la trace en porte : une confirmation pour rien s'apprend a ignorer.
 */
@Composable
internal fun ReverseConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reverse_confirm_title)) },
        text = { Text(stringResource(R.string.reverse_confirm)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Ce que la retouche a refuse, ou ce sur quoi elle s'est repliee. */
@Composable
internal fun EditMessageDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

/**
 * Le planificateur refuse au-dela de 25 etapes.
 *
 * Un tap sur "ajouter l'etape" doit le dire, sans quoi il reste sans effet et l'on croit l'infobulle cassee.
 */
@Composable
internal fun PlannerFullDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.poi_planner_full)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

/**
 * Le retour Android sur un planificateur deja replie : abandonne-t-on le trajet en cours ?
 *
 * **Deux lignes, et non une phrase** : la premiere dit ce qu'on est en train de perdre, la seconde pose la
 * question. Rassemblees, elles se lisaient comme un constat, et l'oeil qui survole une boite de dialogue
 * cherche d'abord ce qu'on lui demande.
 *
 * "Non" en premier, a la place du bouton d'annulation : c'est la reponse qu'on attend de quelqu'un qui n'a
 * pas voulu ouvrir cette boite, et la boite ne s'ouvre justement que sur un geste involontaire.
 */
@Composable
internal fun PlannerCancelDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.planner_cancel_title))
                Text(stringResource(R.string.planner_cancel_question))
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_yes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_no)) } },
    )
}

/**
 * Un geste demande n'a rien produit : le fichier n'a pas pu s'ecrire, rien ne sait recevoir ce qu'on
 * partage, rien ne sait ouvrir ce lien.
 *
 * Une seule boite pour les trois : ce qu'il y a a dire tient dans une phrase, et trois boites qui se
 * ressemblent au mot pres ne diraient rien de plus.
 */
@Composable
internal fun MapFailureDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

/**
 * Recherche demandee sans acces a Internet, alors que le service vise en exige un.
 *
 * Cf. ServiceUrl.needsInternet : une instance auto-hebergee sur le reseau local n'est pas concernee.
 */
@Composable
internal fun NoConnectionDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_no_connection_title)) },
        text = { Text(stringResource(R.string.dialog_no_connection_text)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

/**
 * Cloche tapee capteur eteint : l'alerte n'a rien a surveiller tant qu'aucune position n'arrive.
 *
 * [onEnable] emprunte exactement le chemin du bouton GPS - permission, puis reglages du systeme si le
 * capteur est coupe, puis demarrage. Le choix de la trace attend la fin de ce parcours, qui passe par des
 * ecrans hors de l'application.
 */
@Composable
internal fun AlertNeedsGpsDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_location_off_title)) },
        text = { Text(stringResource(R.string.alert_needs_gps_text)) },
        confirmButton = { TextButton(onClick = onEnable) { Text(stringResource(R.string.action_enable)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Suivi demande alors que la localisation est eteinte dans le telephone : [onEnable] ouvre les reglages. */
@Composable
internal fun LocationDisabledDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_gps_disabled_title)) },
        text = { Text(stringResource(R.string.dialog_gps_disabled_text)) },
        confirmButton = { TextButton(onClick = onEnable) { Text(stringResource(R.string.action_enable)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Tout ce que l'ecran de carte pose PAR-DESSUS lui : treize boites, rendues d'un seul endroit.
 *
 * **Ce que la reunion change.** Elles etaient declarees a la file au bas de `MainScreen`, chacune sur son
 * `if`, et l'on ne pouvait pas savoir combien il y en avait sans les compter. Surtout, chacune tenait a un
 * `var` de la composition, ce qui obligeait la moitie de l'ecran a connaitre l'existence de la boite qu'un
 * seul de ses boutons ouvre. Les drapeaux sont depuis passes dans le porteur de leur sujet - la
 * confirmation d'inversion dans [TrackEditState], le refus du planificateur dans [RoutePlannerState], le
 * rapport d'import dans [ImportFlow] -, et ce qu'il en reste tient dans [MainDialogState].
 *
 * **L'ordre n'est pas indifferent.** Deux boites peuvent etre demandees en meme temps - un import qui se
 * termine mal pendant qu'un parcours attend son nom -, et c'est la derniere posee qui est au-dessus. Elles
 * gardent donc l'ordre qu'elles avaient : du chemin d'import vers les annonces de l'ecran.
 *
 * Aucune ne lit l'etat de l'ecran : chacune recoit ce qu'elle affiche et rend ce qu'on en a fait.
 */
@Composable
internal fun MainDialogs(
    folders: List<FolderEntity>,
    importFlow: ImportFlow,
    dialogs: MainDialogState,
    edit: TrackEditState,
    alert: OffTrackAlertState,
    planner: RoutePlannerState,
    location: LocationControls,
    vm: MainViewModel,
    selectedFeature: PointFeature?,
    schema: List<SchemaItem>,
    followed: TrackWatch.Followed?,
    /** L'avancement sur la trace suivie, pour le tableau de bord du suivi (cf. TrackChooserDialog). */
    followProgress: FollowProgress?,
    imperial: Boolean,
    alertDistanceM: Int,
    /** Le reglage "Emettre un son" de l'alerte d'eloignement (cf. TrackChooserDialog). */
    alertSoundEnabled: Boolean,
    currentPositionLabel: String,
    onPickImage: (((String) -> Unit)) -> Unit,
    routeGpx: (String) -> ByteArray?,
) {
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

    if (importFlow.report.isNotEmpty()) {
        ImportReportDialog(failures = importFlow.report, onDismiss = { importFlow.report = emptyList() })
    }

    edit.reverseConfirm?.let { layer ->
        ReverseConfirmDialog(
            onConfirm = { vm.reverseLayer(layer); edit.reverseConfirm = null },
            onDismiss = { edit.reverseConfirm = null },
        )
    }
    edit.message?.let { message ->
        EditMessageDialog(message = message, onDismiss = { edit.message = null })
    }

    if (planner.importDialog) {
        RouteImportDialog(
            defaultName = defaultRouteName(planner.targets, currentPositionLabel),
            folders = folders,
            onImport = { name, folderId ->
                routeGpx(name)?.let { vm.importLayer(it, GpxWriter.fileName(name), folderId) }
                planner.importDialog = false
            },
            onDismiss = { planner.importDialog = false },
        )
    }

    if (planner.full) {
        PlannerFullDialog(onDismiss = { planner.full = false })
    }

    if (planner.cancelDialog) {
        PlannerCancelDialog(
            onConfirm = { planner.close() },
            onDismiss = { planner.dismissCancel() },
        )
    }

    if (dialogs.editingFeature) {
        // Même dérivation que l'infobulle : un vm.selectedFeature() ici ne serait pas observé par Compose.
        if (selectedFeature != null) PropertyEditor(
            feature = selectedFeature, schema = schema,
            onSave = { vm.saveFeature(it); dialogs.editingFeature = false },
            onCancel = { dialogs.editingFeature = false },
            onDelete = { vm.deleteFeature(selectedFeature); dialogs.editingFeature = false },
            onPickImage = { onImported -> onPickImage(onImported) },
        )
    }

    if (dialogs.noConnection) {
        NoConnectionDialog(onDismiss = { dialogs.noConnection = false })
    }

    dialogs.failure?.let { message ->
        MapFailureDialog(stringResource(message), onDismiss = { dialogs.failure = null })
    }

    if (alert.needsGpsDialog) {
        AlertNeedsGpsDialog(
            // Droit aux reglages du systeme : cette boite ne parait plus que la localisation du telephone
            // eteinte, et passer par le bouton de la carte en aurait ouvert une seconde pour redire la
            // meme chose. Au retour, le capteur relu ouvre la liste mise en attente (cf. awaitGps).
            onEnable = { alert.awaitGps(); location.openLocationSettings() },
            onDismiss = { alert.dismissNeedsGps() },
        )
    }

    if (alert.chooserOpen) {
        TrackChooserDialog(
            candidates = alert.candidates,
            followed = followed,
            imperial = imperial,
            soundEnabled = alertSoundEnabled,
            progress = followProgress,
            onPick = {
                alert.follow(it, alertDistanceM.toDouble())
                /*
                 * Choisir une trace ALLUME le suivi.
                 *
                 * C'est ce qu'on vient demander : une trace suivie sans position ne dit rien, et
                 * l'alerte d'eloignement a besoin du service pour mesurer l'ecart - ecran eteint compris.
                 * L'exiger AVANT, en refusant la liste, obligeait a repasser par le bouton de la carte
                 * pour un geste que celui-ci implique.
                 *
                 * Sans effet s'il tourne deja (cf. LocationControls.startGps, qui ne fait rien de plus
                 * que ce que le service sait deja).
                 */
                if (!location.gpsActive) location.startGps(forFollow = true)
            },
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
