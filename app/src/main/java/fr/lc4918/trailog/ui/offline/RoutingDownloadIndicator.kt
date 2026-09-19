package fr.lc4918.trailog.ui.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import fr.lc4918.trailog.R
import fr.lc4918.trailog.TrailogApp
import fr.lc4918.trailog.map.offline.TileMath
import fr.lc4918.trailog.routing.offline.BrouterZones
import fr.lc4918.trailog.ui.settings.zoneName

private val Orange = Color(0xFFF57C00)
private val Vert = Color(0xFF2E7D32)
private val Rouge = Color(0xFFD32F2F)

/**
 * Le telechargement des donnees d'itineraire, sur la carte : un bouton orange avec le pourcentage tant qu'il
 * dure, puis vert - ou rouge - pour en annoncer la fin.
 *
 * Le meme gabarit que le bouton reduit d'un telechargement de fond de plan (cf. [OfflineMinimizedButton]) : le
 * geste est rare, et on le lance depuis les reglages, mais il dure des minutes - la carte doit dire qu'il
 * continue, et quand il est fini. L'annonce reste jusqu'a ce qu'on l'ait lue.
 */
@Composable
fun RoutingDownloadIndicator(modifier: Modifier = Modifier) {
    val data = (LocalContext.current.applicationContext as? TrailogApp)?.brouterData ?: return
    val s by data.state.collectAsState()
    val progression = s.overall()
    var ouvert by remember { mutableStateOf(false) }
    if (progression == null && s.finished.isEmpty()) return

    val enEchec = progression == null && s.finished.any { !it.ok }
    val fond = when {
        progression != null -> Orange
        enEchec -> Rouge
        else -> Vert
    }
    Column(
        modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(fond).clickable { ouvert = true },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (progression != null) {
            Icon(Icons.Filled.Download, stringResource(R.string.routing_download_notice_title),
                tint = Color.White, modifier = Modifier.size(16.dp))
            val (recu, attendu) = progression
            Text(
                if (attendu != null && attendu > 0) "${(recu * 100 / attendu).coerceIn(0, 100)}%" else "...",
                color = Color.White, fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold,
            )
        } else {
            Icon(if (enEchec) Icons.Filled.ErrorOutline else Icons.Filled.Check,
                stringResource(R.string.routing_download_done_title), tint = Color.White,
                modifier = Modifier.size(22.dp))
        }
    }

    if (ouvert) {
        val fini = progression == null
        AlertDialog(
            onDismissRequest = { ouvert = false },
            title = {
                Text(stringResource(when {
                    !fini -> R.string.routing_download_notice_title
                    enEchec -> R.string.routing_download_failed_title
                    else -> R.string.routing_download_done_title
                }))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Chaque zone en cours, et ce qu'il lui reste ; puis celles qui ont fini.
                    s.pending.keys.mapNotNull { BrouterZones.byId(it) }.forEach { z ->
                        val (recu, attendu) = s.zoneProgress(z) ?: (0L to null)
                        Text(zoneName(z) + " : " + stringResource(R.string.routing_zone_downloading,
                            TileMath.formatSize(recu), attendu?.let { TileMath.formatSize(it) } ?: "?"))
                        if (attendu != null && attendu > 0) {
                            LinearProgressIndicator(
                                progress = { (recu.toFloat() / attendu).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                gapSize = 0.dp, drawStopIndicator = {},
                            )
                        }
                    }
                    s.finished.forEach { f ->
                        val z = BrouterZones.byId(f.zoneId) ?: return@forEach
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (f.ok) Icons.Filled.Check else Icons.Filled.ErrorOutline, null,
                                tint = if (f.ok) Vert else Rouge, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(
                                if (f.ok) R.string.routing_download_zone_ready else R.string.routing_download_zone_failed,
                                zoneName(z)))
                        }
                    }
                    if (fini && enEchec) {
                        Text(stringResource(R.string.routing_zone_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ouvert = false
                    // Lue une fois fini, l'annonce quitte la carte ; pendant le transfert, le bouton reste.
                    if (fini) data.acknowledge()
                }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}
