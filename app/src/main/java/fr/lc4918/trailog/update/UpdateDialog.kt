package fr.lc4918.trailog.update

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import fr.lc4918.trailog.R

/** Propose la mise a jour : l'utilisateur accepte ou repousse (repousser ne fait que fermer, la
 *  verification aura lieu au prochain demarrage). */
@Composable
fun UpdateDialog(release: ReleaseInfo, onAccept: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Text(
                if (release.changelog.isBlank()) {
                    stringResource(R.string.update_available_text, release.version, release.releaseDate)
                } else {
                    stringResource(R.string.update_available_text_changelog,
                        release.version, release.releaseDate, release.changelog)
                }
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.update_action_install)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.update_action_later)) } },
    )
}

/** Invite a autoriser l'installation depuis cette app, sans quoi l'installateur echouerait en silence. */
@Composable
fun UnknownSourcesDialog(onOpenSettings: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.update_unknown_sources_title)) },
        text = { Text(stringResource(R.string.update_unknown_sources_text)) },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * La progression de la mise a jour, du telechargement a l'installation, avec un bouton Annuler qui marche :
 * il abandonne la session d'installation, ce qui rend la main quand le systeme - Play Protect, le plus
 * souvent - laisse l'installation suspendue.
 */
@Composable
fun UpdateProgressDialog(state: InstallState, retrying: Boolean = false, onCancel: () -> Unit, onClose: () -> Unit) {
    val failed = state as? InstallState.Failed
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.update_download_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(when (state) {
                    is InstallState.Downloading -> R.string.update_state_downloading
                    is InstallState.Staging -> if (retrying) R.string.update_state_retrying else R.string.update_state_installing
                    is InstallState.Installing -> R.string.update_state_installing
                    is InstallState.WaitingUser -> R.string.update_state_waiting_user
                    else -> R.string.update_install_failed
                }))
                failed?.message?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = androidx.compose.ui.Modifier.padding(top = 8.dp))
                }
                if (failed == null) {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 16.dp))
                }
                if (state is InstallState.Installing) {
                    Text(stringResource(R.string.update_install_hint),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = androidx.compose.ui.Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            if (failed != null) TextButton(onClick = onClose) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            if (failed == null) TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Enchaine la proposition de mise a jour : dialogue, autorisation d'installer si besoin, telechargement,
 * puis installation par session (cf. [UpdateInstaller]) une fois l'APK la. [release] non nul = une version
 * est disponible.
 */
@Composable
fun UpdateFlow(release: ReleaseInfo?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var askInstallPermission by remember { mutableStateOf(false) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var attempt by remember { mutableStateOf(0) }
    val state by UpdateInstaller.state.collectAsState()
    val stalled = stringResource(R.string.update_install_stalled)
    val title = stringResource(R.string.update_download_title)
    val desc = release?.let { stringResource(R.string.update_download_desc, it.version) } ?: ""

    fun close() {
        UpdateInstaller.set(InstallState.Idle)
        downloadId = null
        attempt = 0
        onDone()
    }

    fun startDownload(r: ReleaseInfo) {
        downloadId = UpdateManager.enqueueDownload(ctx, r, title, desc)
        UpdateInstaller.set(InstallState.Downloading)
    }

    if (release != null && downloadId == null && !askInstallPermission) {
        UpdateDialog(
            release = release,
            onAccept = {
                if (UpdateManager.canInstall(ctx)) startDownload(release) else askInstallPermission = true
            },
            onLater = onDone,
        )
    }
    if (askInstallPermission && release != null) {
        UnknownSourcesDialog(
            onOpenSettings = {
                askInstallPermission = false
                ctx.startActivity(UpdateManager.unknownSourcesSettingsIntent(ctx))
                onDone()   // l'autorisation se donne hors de l'app : on retentera au prochain demarrage
            },
            onCancel = { askInstallPermission = false; onDone() },
        )
    }
    // L'APK arrive de facon asynchrone : on attend la diffusion de fin de telechargement, plutot que de
    // sonder DownloadManager. Un telechargement echoue ne mene pas a l'installation.
    val id = downloadId
    if (id != null && state is InstallState.Downloading) {
        DisposableDownloadListener(ctx, id) {
            if (UpdateInstaller.downloadSucceeded(ctx, id)) scope.launch { UpdateInstaller.install(ctx, id) }
            else UpdateInstaller.set(InstallState.Failed(null))
        }
    }
    // De retour dans l'appli, la fenetre de confirmation d'Android est fermee : le systeme installe.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                UpdateInstaller.set(InstallState.afterReturn(UpdateInstaller.state.value))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Une installation sans nouvelles du systeme : on la relance une fois, puis on y renonce (cf. UpdateInstaller.onStall).
    androidx.compose.runtime.LaunchedEffect(state, attempt) {
        if (state !is InstallState.Installing) return@LaunchedEffect
        kotlinx.coroutines.delay(UpdateInstaller.STALL_TIMEOUT_MS)
        UpdateInstaller.abandon(ctx)
        when (UpdateInstaller.onStall(attempt)) {
            StallAction.RETRY -> {
                attempt += 1
                downloadId?.let { UpdateInstaller.install(ctx, it) }
            }
            StallAction.GIVE_UP -> UpdateInstaller.set(InstallState.Failed(stalled))
        }
    }
    // Annulee depuis la fenetre d'Android : l'etat retombe a Idle, et la progression n'a plus lieu d'etre.
    androidx.compose.runtime.LaunchedEffect(state, downloadId) {
        if (downloadId != null && state is InstallState.Idle) close()
    }
    if (downloadId != null && state !is InstallState.Idle) {
        UpdateProgressDialog(
            state = state,
            retrying = attempt > 0,
            onCancel = {
                if (state is InstallState.Downloading) {
                    (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).remove(id!!)
                } else UpdateInstaller.abandon(ctx)
                close()
            },
            onClose = { close() },
        )
    }
}

/** Ecoute ACTION_DOWNLOAD_COMPLETE pour [downloadId] le temps que le telechargement dure. */
@Composable
private fun DisposableDownloadListener(ctx: Context, downloadId: Long, onComplete: (Boolean) -> Unit) {
    androidx.compose.runtime.DisposableEffect(downloadId) {
        val filter = android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: android.content.Intent?) {
                val got = i?.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (got == downloadId) onComplete(true)
            }
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }
}
