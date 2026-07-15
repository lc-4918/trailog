package fr.lc4918.trailog.ui.offline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.map.offline.TileMath
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField

/**
 * Étape 2 (SPEC offline_map.md section 3) : plage de zoom, statistiques live (tuiles/taille), nom de la
 * couche, gestion des erreurs. Le calcul (domaine A) et le téléchargement réel (domaine B, pas
 * encore branché) sont volontairement séparés : [onDownload] ne fait que remonter la requête validée.
 */
@Composable
fun OfflineDownloadConfigScreen(
    bbox: Bbox,
    providerMinZoom: Int,
    providerMaxZoom: Int,
    onDismiss: () -> Unit,
    onDownload: (OfflineDownloadRequest) -> Unit,
) {
    val zoomBounds = providerMinZoom.toFloat()..providerMaxZoom.toFloat().coerceAtLeast(providerMinZoom.toFloat())
    // plage par défaut raisonnable : [min, min+6] bornée à la plage du provider (cf. SPEC section 3, "Détermination des niveaux").
    var zoomRange by remember {
        mutableStateOf(providerMinZoom.toFloat()..(providerMinZoom + 6).coerceAtMost(providerMaxZoom).toFloat())
    }
    var name by remember { mutableStateOf("") }
    var continueOnError by remember { mutableStateOf(false) }

    val minZ = zoomRange.start.toInt()
    val maxZ = zoomRange.endInclusive.toInt()
    val tileCount = remember(bbox, minZ, maxZ) { TileMath.totalTileCount(bbox, minZ, maxZ) }
    val sizeLabel = remember(tileCount) { TileMath.formatSize(TileMath.estimateSizeBytes(tileCount)) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.offline_config_title), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                Text(stringResource(R.string.offline_config_zoom_label), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.offline_config_zoom_range, minZ, maxZ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                RangeSlider(
                    value = zoomRange,
                    valueRange = zoomBounds,
                    steps = (providerMaxZoom - providerMinZoom - 1).coerceAtLeast(0),
                    onValueChange = { zoomRange = it },
                )
                Spacer(Modifier.height(12.dp))
                val tileCountLabel = remember(tileCount) {
                    String.format(java.util.Locale.ROOT, "%,d", tileCount).replace(',', ' ')
                }
                Text(stringResource(R.string.offline_config_stat_tiles, tileCountLabel))
                Text(stringResource(R.string.offline_config_stat_size, sizeLabel))
                Spacer(Modifier.height(20.dp))
                CompactOutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.offline_config_name_label)) },
                    placeholder = { Text(stringResource(R.string.offline_config_name_placeholder)) },
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.offline_config_continue_on_error_label), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.offline_config_continue_on_error_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = continueOnError, onCheckedChange = { continueOnError = it })
                }
            }
            Button(
                // 'enabled' garantit déjà name.isNotBlank() : pas de repli nécessaire ici.
                onClick = { onDownload(OfflineDownloadRequest(bbox, minZ, maxZ, name, continueOnError)) },
                enabled = name.isNotBlank() && tileCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
            ) {
                Icon(Icons.Filled.Download, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.offline_action_download))
            }
        }
    }
}
