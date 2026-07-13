package fr.lc4918.trailog.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import fr.lc4918.trailog.R
import java.io.File

/** Modèle Coil pour une source d'image locale (chemin fichier) ou distante (URL http/https). */
fun imageModel(source: String): Any =
    if (source.startsWith("http://") || source.startsWith("https://")) source else File(source)

/** Popup plein écran (92%x80%) pour agrandir une image ; fermeture par clic extérieur ou bouton X. */
@Composable
fun FullscreenImageDialog(source: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.8f)) {
                AsyncImage(model = imageModel(source), contentDescription = null,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close))
                }
            }
        }
    }
}
