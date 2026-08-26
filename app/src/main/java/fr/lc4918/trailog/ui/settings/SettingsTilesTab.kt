package fr.lc4918.trailog.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.CompositeEntity
import fr.lc4918.trailog.data.db.CompositeSortOrder
import fr.lc4918.trailog.data.db.DefaultDemOpacityPct
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.map.compositeBasemapId
import fr.lc4918.trailog.map.flagAssetModel
import fr.lc4918.trailog.map.flagCodeFor
import fr.lc4918.trailog.map.offline.OfflineThumbnails
import java.io.File
import kotlinx.coroutines.launch

/**
 * L'onglet Fonds : le catalogue des fonds de carte, leurs dossiers, et les composites.
 *
 * Le plus dense des quatre, parce qu'une ligne de fond porte beaucoup : un apercu, une cle d'API, une plage
 * de zoom, une attribution, et de quoi la modifier sans quitter la liste.
 */

@Composable internal fun TilesTab(
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

    /*
     * Le fond affiche par defaut, et l'aspect du panneau qui sert a en changer.
     *
     * Les deux curseurs viennent de l'onglet Carte, ou ils formaient une rubrique "Gestionnaire de fonds
     * de plan" avec l'interrupteur qui POSE son bouton. Celui-ci est reste la-bas, parmi les boutons de
     * la carte ; ce qui regle le panneau LUI-MEME a suivi les fonds de plan qu'il donne a choisir, et
     * c'est ici qu'on les a sous les yeux.
     */
    SectionTitle(stringResource(R.string.settings_section_default_basemap), tight = true)
    SettingsCard {
        BasemapPickRow(mapProviders, mapComposites, cur.defaultBasemapId) { vm.save(cur.copy(defaultBasemapId = it)) }
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
