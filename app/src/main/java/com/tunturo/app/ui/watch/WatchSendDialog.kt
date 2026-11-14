package com.tunturo.app.ui.watch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tunturo.app.R
import com.tunturo.app.data.db.ProviderEntity
import com.tunturo.app.map.offline.TileMath
import com.tunturo.app.ui.settings.ProvideSettingsPalette
import com.tunturo.app.ui.settings.SettingsSlider
import com.tunturo.app.ui.settings.ValueText
import com.tunturo.app.ui.settings.settingsPalette
import com.tunturo.app.watch.WatchExportSession
import com.tunturo.app.watch.WatchLink
import com.tunturo.app.watch.WatchTileSource
import com.tunturo.app.watch.vector.WatchOsmSource
import com.tunturo.app.watch.vector.WatchVectorSource
import com.tunturo.app.watch.WatchTiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Largeurs de couloir proposees, de chaque cote de la trace. */
internal val WatchCorridorRadiiMeters = listOf(250.0, 500.0, 1000.0, 2000.0)
private const val DEFAULT_RADIUS_INDEX = 1

/**
 * Place d'une tuile raster sur la montre : une tuile codee par plages (une plage de un a trois pixels tient sur un octet) fait
 * en moyenne 3,6 Ko sur un parcours rural (856 tuiles du Scan 25, 28 km) et 7,3 Ko en ville dense (Toulouse), plus un surcout de
 * stockage d'environ 130 octets. 7300 octets, la moyenne la plus haute, est arrondi a 7500 : l'estimation affichee ne depasse
 * jamais de beaucoup la place reelle, elle ne la limite pas : la place reelle depend de l'appareil et du fond.
 */
internal const val WATCH_BYTES_PER_TILE = 7_500L

/**
 * Place d'une tuile du fond vectoriel, par niveau de detail : le plus haut des moyennes mesurees sur 64 tuiles d'une ville dense (Toulouse), d'une
 * campagne (La Force) et d'un massif (Morgiou), arrondi a 500 octets. Une carte vectorielle pese plus qu'un raster la ou elle est chargee
 * (hachures de foret, courbes de niveau, noms) et moins la ou elle est vide. Aucune tuile ne depasse 9500 octets, le plafond est de 11000.
 */
private val WATCH_VECTOR_BYTES_PER_TILE = mapOf(
    WatchTiles.Detail.FINE to 6_500L, WatchTiles.Detail.VERY_FINE to 5_500L, WatchTiles.Detail.ULTRA_FINE to 4_500L,
)

/**
 * Place d'une tuile du fond Trailmap (OpenStreetMap), par niveau de detail : le plus haut des moyennes mesurees sur 9 tuiles d'une ville
 * dense (Toulouse), d'une campagne (La Force) et d'un massif (Morgiou), arrondi a 500 octets. Moins charge que le vectoriel IGN : pas de
 * courbes de niveau ni de hachures.
 */
private val WATCH_OSM_BYTES_PER_TILE = mapOf(
    WatchTiles.Detail.FINE to 7_000L, WatchTiles.Detail.VERY_FINE to 5_500L, WatchTiles.Detail.ULTRA_FINE to 4_500L,
)

/** Octets que prend une tuile sur la montre pour ce fond a ce niveau de detail (estimation affichee, pas une limite). */
internal fun estimatedBytesPerTile(provider: ProviderEntity?, detail: WatchTiles.Detail): Long = when {
    provider != null && WatchVectorSource.isVector(provider) -> WATCH_VECTOR_BYTES_PER_TILE.getValue(detail)
    provider != null && WatchOsmSource.isOsm(provider) -> WATCH_OSM_BYTES_PER_TILE.getValue(detail)
    else -> WATCH_BYTES_PER_TILE
}

/**
 * La fenetre d'envoi d'une trace a la montre Garmin : l'etat de la montre, le fond, la largeur du couloir,
 * puis l'avancement une fois l'envoi ouvert. Sans etat propre a l'envoi : la session
 * ([WatchExportSession]) vit dans l'application, et cette fenetre peut se fermer sans l'interrompre.
 *
 * **Elle ne doit pas sauter quand on regle le couloir.** La fenetre est centree : chaque changement de sa
 * hauteur la deplace tout entiere. Le calcul des tuiles remplacait l'estimation par "Calcul des tuiles...",
 * plus court ou plus long selon la langue ; l'estimation tient desormais sur une ligne de hauteur fixe, ou
 * un rond qui tourne prend la place de la valeur pendant le calcul. Le curseur et le select sont ceux du
 * telechargement de tuiles (cf. SettingsSlider), pour que les deux reglages se ressemblent.
 *
 * @param providers les fonds envoyables (cf. WatchTileSource.supports), dans l'ordre du gestionnaire.
 * @param link l'etat de la montre aupres de Garmin Connect (cf. WatchLinkMonitor).
 * @param dark la palette sombre, comme le reste de l'application.
 * @param onSend ouvre l'envoi, au niveau de detail choisi (parmi ceux que le fond sait remplir) ; rend false si le serveur n'a pas pu demarrer.
 */
@Composable
fun WatchSendDialog(
    trackName: String,
    trackPoints: List<Pair<Double, Double>>,
    providers: List<ProviderEntity>,
    initialProviderId: String?,
    session: WatchExportSession.State?,
    link: WatchLink,
    dark: Boolean,
    onSend: (provider: ProviderEntity, tiles: List<WatchTiles.WatchTile>, detail: WatchTiles.Detail) -> Boolean,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPermissionSettings: () -> Unit = {},
) {
    ProvideSettingsPalette(dark = dark) {
        if (session != null) WatchSendProgressDialog(session, link, onStop, onDismiss, onOpenPermissionSettings)
        else WatchSendSetupDialog(trackName, trackPoints, providers, initialProviderId, link, onSend, onDismiss,
            onOpenPermissionSettings)
    }
}

@Composable
private fun WatchSendSetupDialog(
    trackName: String,
    trackPoints: List<Pair<Double, Double>>,
    providers: List<ProviderEntity>,
    initialProviderId: String?,
    link: WatchLink,
    onSend: (provider: ProviderEntity, tiles: List<WatchTiles.WatchTile>, detail: WatchTiles.Detail) -> Boolean,
    onDismiss: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
) {
    var selectedProviderId by remember {
        mutableStateOf(providers.firstOrNull { it.id == initialProviderId }?.id ?: providers.firstOrNull()?.id)
    }
    var radiusIndex by remember { mutableIntStateOf(DEFAULT_RADIUS_INDEX) }
    val radiusMeters = WatchCorridorRadiiMeters[radiusIndex]
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    // Les niveaux que le fond choisi sait remplir ; le niveau retenu retombe sur le niveau par defaut, ou sur le
    // plus fin qui reste, quand on change pour un fond qui ne l'a pas.
    val availableDetails = selectedProvider?.let { WatchTileSource.availableDetails(it) }.orEmpty()
    var requestedDetail by remember { mutableStateOf(WatchTiles.Detail.DEFAULT) }
    val detail = chooseDetail(requestedDetail, availableDetails)
    // Les tuiles du dernier calcul restent gardees pendant le suivant : la fenetre garde sa hauteur, et
    // seul l'envoi attend que le calcul en cours aboutisse.
    var tiles by remember { mutableStateOf<List<WatchTiles.WatchTile>?>(null) }
    var computing by remember { mutableStateOf(true) }
    var sendFailed by remember { mutableStateOf(false) }
    LaunchedEffect(radiusMeters, trackPoints, detail) {
        computing = true
        tiles = withContext(Dispatchers.Default) { WatchTiles.tilesAlong(trackPoints, radiusMeters, detail) }
        computing = false
    }
    val palette = settingsPalette

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.watch_send_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WatchLinkLine(link, onOpenPermissionSettings)
                Text(trackName, style = MaterialTheme.typography.titleSmall)
                if (providers.isEmpty()) {
                    Text(stringResource(R.string.watch_send_no_basemap))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.watch_send_basemap), style = MaterialTheme.typography.bodyMedium,
                            color = palette.label)
                        BasemapSelect(providers, selectedProvider) { selectedProviderId = it.id }
                    }
                }
                if (availableDetails.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.watch_send_detail_label), style = MaterialTheme.typography.bodyMedium,
                            color = palette.label)
                        DetailSelect(availableDetails, detail) { requestedDetail = it }
                    }
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.watch_send_corridor_label), style = MaterialTheme.typography.bodyMedium,
                            color = palette.label, modifier = Modifier.weight(1f))
                        ValueText(stringResource(R.string.watch_send_corridor_value, formatRadius(radiusMeters)))
                    }
                    Spacer(Modifier.height(9.dp))
                    Box(Modifier.testTag("watch_corridor_slider")) {
                        SettingsSlider(
                            fraction = radiusIndex.toFloat() / WatchCorridorRadiiMeters.lastIndex,
                            onFraction = { fraction ->
                                radiusIndex = (fraction * WatchCorridorRadiiMeters.lastIndex).roundToInt()
                                    .coerceIn(0, WatchCorridorRadiiMeters.lastIndex)
                            },
                            steps = WatchCorridorRadiiMeters.size - 2,
                        )
                    }
                }
                val tileCount = tiles?.size
                EstimateLine(
                    if (computing || tileCount == null) null
                    else stringResource(R.string.watch_send_size_value, tileCount,
                        TileMath.formatSize(tileCount * estimatedBytesPerTile(selectedProvider, detail))),
                )
                if (sendFailed) {
                    Text(stringResource(R.string.watch_send_failed), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        // Les boutons de l'etape 1 du telechargement par zone (cf. BboxDrawingOverlay) : "Annuler" en
        // contour, l'action en aplat, tous deux de 44 dp.
        confirmButton = {
            Button(
                enabled = selectedProvider != null && !computing && !tiles.isNullOrEmpty(),
                onClick = {
                    val provider = selectedProvider ?: return@Button
                    sendFailed = !onSend(provider, tiles.orEmpty(), detail)
                },
                modifier = Modifier.height(44.dp).testTag("watch_send_confirm"),
            ) { Text(stringResource(R.string.watch_send_confirm)) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss, modifier = Modifier.height(44.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Le niveau de detail a utiliser : [requested] s'il est offert, sinon le niveau par defaut, sinon le plus fin
 * des niveaux offerts ; le niveau par defaut quand le fond n'en offre aucun (rien ne sera envoye).
 */
internal fun chooseDetail(requested: WatchTiles.Detail, available: List<WatchTiles.Detail>): WatchTiles.Detail = when {
    requested in available -> requested
    WatchTiles.Detail.DEFAULT in available -> WatchTiles.Detail.DEFAULT
    else -> available.lastOrNull() ?: WatchTiles.Detail.DEFAULT
}

/** Les niveaux de detail en boutons accoles, du plus grossier au plus fin. */
@Composable
private fun DetailSelect(
    available: List<WatchTiles.Detail>, selected: WatchTiles.Detail, onSelect: (WatchTiles.Detail) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("watch_detail_select")) {
        available.forEachIndexed { index, level ->
            SegmentedButton(
                selected = level == selected,
                onClick = { onSelect(level) },
                shape = SegmentedButtonDefaults.itemShape(index, available.size),
                modifier = Modifier.testTag("watch_detail_${level.name}"),
            ) {
                Text(stringResource(when (level) {
                    WatchTiles.Detail.FINE -> R.string.watch_send_detail_fine
                    WatchTiles.Detail.VERY_FINE -> R.string.watch_send_detail_very_fine
                    WatchTiles.Detail.ULTRA_FINE -> R.string.watch_send_detail_ultra_fine
                }), maxLines = 1)
            }
        }
    }
}

/** Hauteur de la ligne de l'estimation : celle du rond qui tourne comme celle du texte qu'il remplace. */
private val EstimateLineHeight = 24.dp

/**
 * La place que prendront les tuiles sur la montre ; un rond qui tourne a la place de la valeur tant que
 * [estimate] est null, le calcul en cours. La hauteur est imposee : la fenetre ne bouge pas de l'un a l'autre.
 */
@Composable
internal fun EstimateLine(estimate: String?) {
    Row(Modifier.fillMaxWidth().height(EstimateLineHeight).testTag("watch_send_estimate_line"),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.watch_send_size_label), style = MaterialTheme.typography.bodyMedium,
            color = settingsPalette.label, modifier = Modifier.weight(1f))
        if (estimate == null) {
            CircularProgressIndicator(Modifier.size(16.dp).testTag("watch_send_computing"), strokeWidth = 2.dp)
        } else {
            Box(Modifier.testTag("watch_send_estimate")) { ValueText(estimate) }
        }
    }
}

/** Le select du fond, au dessin de celui du placement de l'infobulle dans les reglages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasemapSelect(
    providers: List<ProviderEntity>, selected: ProviderEntity?, onSelect: (ProviderEntity) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        Box(
            Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                .clip(OutlinedTextFieldDefaults.shape).testTag("watch_provider_select"),
        ) {
            OutlinedTextFieldDefaults.Container(
                enabled = true, isError = false, interactionSource = interactionSource,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected?.name.orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                ExposedDropdownMenuDefaults.TrailingIcon(open)
            }
        }
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.name) },
                    onClick = { open = false; onSelect(provider) },
                    modifier = Modifier.testTag("watch_provider_${provider.id}"),
                )
            }
        }
    }
}

/**
 * L'etat de la montre, dans une carte : l'icone de la montre et la phrase entiere - le nom de la montre
 * peut etre long, et c'est la fin de la phrase qui dit ce qui ne va pas. Accent quand elle est jointe,
 * rouge quand l'envoi ne pourra pas aboutir, gris quand il faut attendre. Quand l'autorisation Bluetooth
 * manque, le lien "Autoriser" sous le message renvoie aux parametres du telephone, la ou elle s'accorde.
 */
@Composable
internal fun WatchLinkLine(link: WatchLink, onOpenPermissionSettings: () -> Unit = {}) {
    val palette = settingsPalette
    val (text, color) = when (link) {
        WatchLink.Checking -> stringResource(R.string.watch_link_checking) to palette.subtle
        WatchLink.GarminConnectMissing -> stringResource(R.string.watch_link_gcm_missing) to MaterialTheme.colorScheme.error
        WatchLink.PermissionNeeded -> stringResource(R.string.watch_link_permission) to MaterialTheme.colorScheme.error
        WatchLink.BluetoothOff -> stringResource(R.string.watch_link_bluetooth_off) to MaterialTheme.colorScheme.error
        WatchLink.NoWatch -> stringResource(R.string.watch_link_no_watch) to MaterialTheme.colorScheme.error
        is WatchLink.Disconnected -> stringResource(R.string.watch_link_disconnected, link.watchName) to palette.subtle
        is WatchLink.Connected -> stringResource(R.string.watch_link_connected, link.watchName) to palette.accent
    }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("watch_link"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Watch, null, Modifier.size(24.dp), tint = color)
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
            if (link == WatchLink.PermissionNeeded) {
                Text(
                    stringResource(R.string.watch_link_authorize),
                    style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                    fontWeight = FontWeight.SemiBold, color = palette.accent,
                    modifier = Modifier.padding(top = 4.dp).clickable(role = Role.Button, onClick = onOpenPermissionSettings)
                        .testTag("watch_link_authorize"),
                )
            }
        }
    }
}

@Composable
private fun WatchSendProgressDialog(
    session: WatchExportSession.State, link: WatchLink, onStop: () -> Unit, onDismiss: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.watch_send_notice_title, session.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchLinkLine(link, onOpenPermissionSettings)
                Text(stringResource(R.string.watch_send_instructions))
                Text(
                    if (session.servedCount == 0) stringResource(R.string.watch_send_waiting)
                    else stringResource(R.string.watch_send_progress, session.servedCount, session.tileCount),
                    modifier = Modifier.testTag("watch_send_progress"),
                )
                LinearProgressIndicator(
                    progress = { if (session.tileCount == 0) 0f else session.servedCount.toFloat() / session.tileCount },
                    // Sans le point que Material 3 pose au bout de la piste : il se lisait comme la progression.
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        dismissButton = {
            TextButton(onClick = onStop, modifier = Modifier.testTag("watch_send_stop")) {
                Text(stringResource(R.string.watch_send_stop))
            }
        },
    )
}

internal fun formatRadius(meters: Double): String =
    if (meters < 1000.0) "${meters.roundToInt()} m" else "${(meters / 1000.0).roundToInt()} km"
