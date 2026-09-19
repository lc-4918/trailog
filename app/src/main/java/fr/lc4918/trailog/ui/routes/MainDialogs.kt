package fr.lc4918.trailog.ui.routes

import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
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
import fr.lc4918.trailog.ui.settings.ChoiceBlock
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
                    // Toute la partie est cliquable, texte d'aide compris (cf. ChoiceBlock).
                    ChoiceBlock(stringResource(R.string.offline_extent_area),
                        stringResource(R.string.offline_extent_area_hint), onArea)
                    RowDivider()
                    ChoiceBlock(stringResource(R.string.offline_extent_track),
                        stringResource(R.string.offline_extent_track_hint), onTrack)
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
                    ChoiceBlock(stringResource(R.string.export_format_gpx),
                        stringResource(R.string.export_format_gpx_hint)) { onPick(false) }
                    RowDivider()
                    ChoiceBlock(stringResource(R.string.export_format_geojson),
                        stringResource(R.string.export_format_geojson_hint)) { onPick(true) }
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
 * **L'arborescence du menu lateral, et non une liste a plat.** On y retrouve ses traces la ou on les a
 * rangees, avec leurs dossiers, leurs symboles et leurs couleurs : c'est la bibliotheque qu'on connait, pas
 * une liste de noms a relire. Memes dimensions et memes icones que les lignes du menu lateral, sans ses
 * prises - ici on choisit, on ne range pas.
 *
 * **Les traces masquees y sont, et se choisissent.** Emporter la carte d'une sortie ne demande pas de
 * l'afficher : on prepare souvent un voyage dont on ne veut pas voir la trace par-dessus tout le reste.
 * Elles se lisent grisees, l'oeil barre, comme dans le menu lateral.
 *
 * Seules les couches qui portent une ligne, et les dossiers qui en contiennent : un dossier de points n'a
 * pas de couloir. L'arbre peut etre vide - c'est un cas normal, une application fraichement installee -, et
 * le dit alors plutot que d'ouvrir une boite sans rien dedans.
 */
@Composable
internal fun OfflineTrackPickDialog(
    folders: List<FolderEntity>, layers: List<LayerEntity>,
    onPick: (LayerEntity) -> Unit, onDismiss: () -> Unit,
) {
    val traces = layers.filter { it.hasLine }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize().padding(vertical = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.offline_extent_track), fontSize = 17.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(20.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 4.dp))
                if (traces.isEmpty()) {
                    Text(stringResource(R.string.offline_extent_no_track),
                        modifier = Modifier.padding(16.dp))
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        PickTree(null, folders, traces, 0, onPick)
                    }
                }
            }
        }
    }
}

/** Un niveau de l'arbre : ses dossiers qui portent une trace, puis ses traces, dans l'ordre du menu lateral. */
@Composable
private fun PickTree(
    parentId: Long?, folders: List<FolderEntity>, traces: List<LayerEntity>, depth: Int,
    onPick: (LayerEntity) -> Unit,
) {
    combinedChildren(parentId, folders, traces).forEach { item ->
        when (item) {
            is FolderEntity -> {
                if (layersUnder(item.id, folders, traces).isEmpty()) return@forEach
                key("folder", item.id) { PickFolder(item, folders, traces, depth, onPick) }
            }
            is LayerEntity -> key("layer", item.id) { PickLayer(item, depth, onPick) }
        }
    }
}

@Composable
private fun PickFolder(
    folder: FolderEntity, folders: List<FolderEntity>, traces: List<LayerEntity>, depth: Int,
    onPick: (LayerEntity) -> Unit,
) {
    var ouvert by remember(folder.id) { mutableStateOf(true) }
    Row(
        Modifier.fillMaxWidth().clickable { ouvert = !ouvert }
            .padding(start = DrawerRowPadH + DrawerIndent * depth, end = DrawerRowPadH,
                top = DrawerRowPadV, bottom = DrawerRowPadV),
        horizontalArrangement = Arrangement.spacedBy(DrawerRowGap),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(Modifier.size(DrawerHitSize), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(if (ouvert) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                stringResource(if (ouvert) R.string.action_collapse else R.string.action_expand),
                Modifier.size(DrawerChevronSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(if (ouvert) Icons.Filled.Folder else Icons.Outlined.Folder, null,
            Modifier.size(DrawerIconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(folder.name.uppercase(), fontSize = DrawerNameSp.sp, lineHeight = (DrawerNameSp * 1.25f).sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("${layersUnder(folder.id, folders, traces).size}", fontSize = DrawerCountSp.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (ouvert) PickTree(folder.id, folders, traces, depth + 1, onPick)
}

/** Une trace : toute la ligne la choisit. Masquee, elle palit et porte l'oeil barre, et reste choisissable. */
@Composable
private fun PickLayer(layer: LayerEntity, depth: Int, onPick: (LayerEntity) -> Unit) {
    val alpha = if (layer.visible) 1f else 0.4f
    Row(
        Modifier.fillMaxWidth().clickable { onPick(layer) }
            .padding(start = DrawerRowPadH + DrawerIndent * depth, end = DrawerRowPadH,
                top = DrawerRowPadV, bottom = DrawerRowPadV),
        horizontalArrangement = Arrangement.spacedBy(DrawerRowGap),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        // La place du chevron d'un dossier, laissee vide : l'arbre garde sa colonne, comme au menu lateral.
        Spacer(Modifier.width(DrawerHitSize))
        Icon(
            androidx.compose.ui.res.painterResource(
                if (layer.hasPoints) R.drawable.ic_layer_globe else R.drawable.ic_layer_route),
            null, Modifier.size(DrawerIconSize),
            tint = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(layer.color)).copy(alpha = alpha),
        )
        Text(layer.name, fontSize = DrawerNameSp.sp, lineHeight = (DrawerNameSp * 1.25f).sp, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), modifier = Modifier.weight(1f))
        if (!layer.visible) {
            Icon(Icons.Outlined.VisibilityOff, stringResource(R.string.action_show),
                Modifier.size(DrawerIconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
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
