package fr.lc4918.trailog.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import fr.lc4918.trailog.BuildConfig
import fr.lc4918.trailog.R

/** Ou l'on signale ce qui ne va pas : le suivi de bugs du depot, ouvert a tous. */
const val IssuesUrl = "https://github.com/lc-4918/trailog/issues"

/**
 * La documentation, qui detaille les fonctions une a une.
 *
 * Elle n'est pas encore ecrite : le lien pointe pour l'instant sur le depot, qui porte le peu qu'il y a.
 * Une seule constante a changer le jour ou elle existe.
 */
const val HelpUrl = "https://github.com/lc-4918/trailog"

/**
 * "A propos" : le nom, la version installee, et ou signaler un probleme.
 *
 * La VERSION est ce qu'on vient chercher ici : sans elle, un rapport de bug ne dit pas sur quoi il porte,
 * et l'on ne sait pas si le defaut est deja corrige. Elle vient de la compilation, donc du tag Git qui l'a
 * produite (cf. build.gradle.kts), et non d'une constante qu'on oublierait d'incrementer.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.about_report_intro))
                Spacer(Modifier.height(4.dp))
                Text(
                    IssuesUrl,
                    color = settingsPalette.accent,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, IssuesUrl.toUri())) } }
                        .testTag("about_issues_link"),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
