package fr.lc4918.trailog.update

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 * Enchaine la proposition de mise a jour : dialogue, autorisation d'installer si besoin, telechargement,
 * puis lancement de l'installateur des que l'APK est la. [release] non nul = une version est disponible.
 */
@Composable
fun UpdateFlow(release: ReleaseInfo?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var askInstallPermission by remember { mutableStateOf(false) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    val title = stringResource(R.string.update_download_title)
    val desc = release?.let { stringResource(R.string.update_download_desc, it.version) } ?: ""

    fun startDownload(r: ReleaseInfo) {
        downloadId = UpdateManager.enqueueDownload(ctx, r, title, desc)
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
    // L'APK arrive de facon asynchrone : on attend la diffusion de fin de telechargement pour lancer
    // l'installateur, plutot que de sonder DownloadManager.
    val id = downloadId
    if (id != null) {
        DisposableDownloadListener(ctx, id) { ok ->
            if (ok) UpdateManager.installIntent(ctx, id)?.let { ctx.startActivity(it) }
            onDone()
        }
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
