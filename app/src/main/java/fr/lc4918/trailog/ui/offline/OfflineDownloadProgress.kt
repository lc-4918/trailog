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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.map.offline.OfflineDownloadState
import fr.lc4918.trailog.map.offline.OfflinePhase

private val OrangeMinimized = Color(0xFFF57C00)   // bouton réduit, bien visible sur la carte (SPEC section 4)
private val GreenSuccess = Color(0xFF2E7D32)
private val RedError = Color(0xFFD32F2F)

/**
 * Popup de progression du téléchargement hors-ligne (SPEC offline_map.md section 4). Un seul composable gère
 * les trois phases : en cours (barre + stats + Réduire/Annuler), succès et erreur (message + Fermer).
 */
@Composable
fun OfflineDownloadCard(
    state: OfflineDownloadState,
    onMinimize: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            when (state.phase) {
                OfflinePhase.RUNNING -> RunningContent(state, onMinimize, onCancel)
                OfflinePhase.SUCCESS -> ResultContent(
                    icon = { Icon(Icons.Filled.CheckCircle, null, tint = GreenSuccess, modifier = Modifier.size(28.dp)) },
                    message = stringResource(R.string.offline_progress_success, state.name),
                    onClose = onClose,
                )
                OfflinePhase.ERROR -> ResultContent(
                    icon = { Icon(Icons.Filled.ErrorOutline, null, tint = RedError, modifier = Modifier.size(28.dp)) },
                    message = stringResource(R.string.offline_progress_error, state.failed),
                    onClose = onClose,
                )
            }
        }
    }
}

@Composable
private fun RunningContent(state: OfflineDownloadState, onMinimize: () -> Unit, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinimize, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.ExpandMore, stringResource(R.string.offline_action_minimize))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.offline_progress_title),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.offline_progress_total, state.total), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.offline_progress_downloaded, state.done), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.offline_progress_failed, state.failed), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { state.percent / 100f },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        gapSize = 0.dp,             // pas d'espace entre la partie remplie et la piste
        drawStopIndicator = {},     // supprime le point de fin (dessiné à 100 % de la piste par défaut)
    )
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${state.percent} %", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text("${state.done + state.failed}/${state.total}", style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onMinimize) { Text(stringResource(R.string.offline_action_minimize)) }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    }
}

@Composable
private fun ResultContent(icon: @Composable () -> Unit, message: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
    }
}

/**
 * Popup réduite : petit bouton rond orange sur la carte (SPEC section 4). Affiche l'icône download et le
 * pourcentage courant ; un clic rouvre la popup.
 */
@Composable
fun OfflineMinimizedButton(state: OfflineDownloadState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Carré à bords arrondis, même gabarit (48.dp) qu'un IconButton de la carte (bouton GPS).
    Column(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(OrangeMinimized)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Download, stringResource(R.string.offline_action_reopen), tint = Color.White, modifier = Modifier.size(16.dp))
        Text("${state.percent}%", color = Color.White, fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold)
    }
}
