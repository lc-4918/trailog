package fr.lc4918.trailog.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.map.offline.TileMath
import fr.lc4918.trailog.watch.WatchExportSession
import fr.lc4918.trailog.watch.WatchTiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Largeurs de couloir proposees, de chaque cote de la trace. */
internal val WatchCorridorRadiiMeters = listOf(250.0, 500.0, 1000.0, 2000.0)
private const val DEFAULT_RADIUS_INDEX = 1

/**
 * Place d'une tuile sur la montre, mesuree dans le simulateur fenix 6X avec la palette de 15 couleurs
 * de l'application montre. Sert a l'estimation affichee, pas a une limite.
 */
internal const val WATCH_BYTES_PER_TILE = 12_500L

/**
 * La fenetre d'envoi d'une trace a la montre Garmin : le fond, la largeur du couloir, puis l'avancement
 * une fois l'envoi ouvert. Sans etat propre a l'envoi : la session ([WatchExportSession]) vit dans
 * l'application, et cette fenetre peut se fermer sans l'interrompre.
 *
 * @param providers les fonds envoyables (cf. WatchTileSource.supports), dans l'ordre du gestionnaire.
 * @param onSend ouvre l'envoi ; rend false si le serveur n'a pas pu demarrer.
 */
@Composable
fun WatchSendDialog(
    trackName: String,
    trackPoints: List<Pair<Double, Double>>,
    providers: List<ProviderEntity>,
    initialProviderId: String?,
    session: WatchExportSession.State?,
    onSend: (provider: ProviderEntity, tiles: List<WatchTiles.WatchTile>) -> Boolean,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (session != null) {
        WatchSendProgressDialog(session, onStop, onDismiss)
        return
    }
    var selectedProviderId by remember {
        mutableStateOf(providers.firstOrNull { it.id == initialProviderId }?.id ?: providers.firstOrNull()?.id)
    }
    var radiusIndex by remember { mutableFloatStateOf(DEFAULT_RADIUS_INDEX.toFloat()) }
    val radiusMeters = WatchCorridorRadiiMeters[radiusIndex.roundToInt()]
    var tiles by remember { mutableStateOf<List<WatchTiles.WatchTile>?>(null) }
    var sendFailed by remember { mutableStateOf(false) }
    LaunchedEffect(radiusMeters, trackPoints) {
        tiles = null
        tiles = withContext(Dispatchers.Default) { WatchTiles.tilesAlong(trackPoints, radiusMeters) }
    }
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.watch_send_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(trackName, style = MaterialTheme.typography.titleSmall)
                if (providers.isEmpty()) {
                    Text(stringResource(R.string.watch_send_no_basemap))
                } else {
                    Text(stringResource(R.string.watch_send_basemap), style = MaterialTheme.typography.labelLarge)
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        providers.forEach { provider ->
                            Row(
                                Modifier.fillMaxWidth().testTag("watch_provider_${provider.id}").selectable(
                                    selected = provider.id == selectedProviderId,
                                    onClick = { selectedProviderId = provider.id },
                                    role = Role.RadioButton,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = provider.id == selectedProviderId, onClick = null)
                                Text(provider.name, Modifier.weight(1f))
                            }
                        }
                    }
                }
                Text(stringResource(R.string.watch_send_corridor, formatRadius(radiusMeters)))
                Slider(
                    value = radiusIndex,
                    onValueChange = { radiusIndex = it },
                    valueRange = 0f..(WatchCorridorRadiiMeters.size - 1).toFloat(),
                    steps = WatchCorridorRadiiMeters.size - 2,
                    modifier = Modifier.testTag("watch_corridor_slider"),
                )
                val tileCount = tiles?.size
                Text(
                    if (tileCount == null) stringResource(R.string.watch_send_counting)
                    else stringResource(R.string.watch_send_estimate, tileCount,
                        TileMath.formatSize(tileCount * WATCH_BYTES_PER_TILE)),
                    modifier = Modifier.testTag("watch_send_estimate"),
                )
                if (sendFailed) {
                    Text(stringResource(R.string.watch_send_failed), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedProvider != null && !tiles.isNullOrEmpty(),
                onClick = {
                    val provider = selectedProvider ?: return@TextButton
                    sendFailed = !onSend(provider, tiles.orEmpty())
                },
                modifier = Modifier.testTag("watch_send_confirm"),
            ) { Text(stringResource(R.string.watch_send_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun WatchSendProgressDialog(session: WatchExportSession.State, onStop: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.watch_send_notice_title, session.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.watch_send_instructions))
                Text(
                    if (session.servedCount == 0) stringResource(R.string.watch_send_waiting)
                    else stringResource(R.string.watch_send_progress, session.servedCount, session.tileCount),
                    modifier = Modifier.testTag("watch_send_progress"),
                )
                LinearProgressIndicator(
                    progress = { if (session.tileCount == 0) 0f else session.servedCount.toFloat() / session.tileCount },
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
