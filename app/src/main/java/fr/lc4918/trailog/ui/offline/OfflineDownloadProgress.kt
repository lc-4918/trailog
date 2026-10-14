package fr.lc4918.trailog.ui.offline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.map.offline.OfflineDownloadState
import fr.lc4918.trailog.map.offline.OfflinePhase
import fr.lc4918.trailog.map.offline.OfflineStage
import fr.lc4918.trailog.ui.components.SheetRoundButton
import fr.lc4918.trailog.ui.routes.ControlButtonRadius
import fr.lc4918.trailog.ui.theme.Spacing
import java.text.NumberFormat

/**
 * Popup de progression du téléchargement hors-ligne (SPEC offline_map.md section 4). Un seul composable gère
 * les trois phases : en cours (tuiles, barre, Annuler/Réduire), succès et erreur (message + Fermer).
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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m, bottom = Spacing.m)) {
            when (state.phase) {
                OfflinePhase.RUNNING -> RunningContent(state, onMinimize, onCancel)
                OfflinePhase.SUCCESS -> ResultContent(
                    icon = {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp))
                    },
                    message = stringResource(R.string.offline_progress_success, state.name),
                    // Le sort des points d'interet, quand on a demande a les emporter : leur nombre, ou
                    // l'aveu que le service n'a pas repondu. La carte, elle, est bien la - d'ou une ligne
                    // sous le succes plutot qu'un message d'erreur qui ferait douter de tout.
                    detail = state.pinnedPois?.let { n ->
                        if (n > 0) stringResource(R.string.offline_progress_pois, n)
                        else stringResource(R.string.offline_progress_pois_none)
                    },
                    onClose = onClose,
                )
                OfflinePhase.ERROR -> ResultContent(
                    icon = {
                        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp))
                    },
                    message = stringResource(R.string.offline_progress_error, state.failed),
                    onClose = onClose,
                )
            }
        }
    }
}

/**
 * En cours : l'en-tete des autres panneaux (titre de panneau, sur deux lignes s'il le faut, et le chevron qui
 * reduit sur un rond gris), les trois comptes en tuiles - comme les compteurs du tableau de bord -, la barre,
 * puis les deux issues, de 44 dp comme celles des autres etapes du telechargement : "Annuler" en contour, en
 * rouge parce qu'il arrete tout, et "Reduire" en aplat, a la place de l'action qu'on attend d'une popup qui dure.
 *
 * Une fois les tuiles recues, la barre est pleine et pourtant le travail continue (fichier, miniatures, lieux) :
 * le nom de l'etape prend alors sa place, avec un rond qui tourne.
 */
@Composable
private fun RunningContent(state: OfflineDownloadState, onMinimize: () -> Unit, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val nombre = NumberFormat.getIntegerInstance()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.offline_progress_title), style = MaterialTheme.typography.titleLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            SheetRoundButton(Icons.Filled.ExpandMore, stringResource(R.string.offline_action_minimize), onMinimize)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatTile(stringResource(R.string.offline_stat_total), nombre.format(state.total), Modifier.weight(1f))
            StatTile(stringResource(R.string.offline_stat_received), nombre.format(state.done), Modifier.weight(1f))
            StatTile(stringResource(R.string.offline_stat_failed), nombre.format(state.failed), Modifier.weight(1f),
                valueColor = if (state.failed > 0) scheme.error else scheme.onSurface)
        }
        if (state.stage == OfflineStage.TILES) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(scheme.surfaceContainerHigh)) {
                    Box(Modifier.fillMaxWidth((state.percent / 100f).coerceIn(0f, 1f)).height(6.dp)
                        .clip(CircleShape).background(scheme.primary))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${state.percent} %",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold)
                    Text("${nombre.format(state.done + state.failed)} / ${nombre.format(state.total)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = scheme.onSurfaceVariant)
                }
            }
        } else {
            StageList(state)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.height(44.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant)) {
                Text(stringResource(R.string.action_cancel), color = scheme.error)
            }
            Button(onClick = onMinimize, modifier = Modifier.height(44.dp)) {
                Text(stringResource(R.string.offline_action_minimize))
            }
        }
    }
}

/**
 * Les etapes qui suivent les tuiles, chacune sur sa ligne : faite (coche), en cours (rond qui tourne) ou a
 * venir (rond vide). La barre est pleine mais le travail continue : on dit lequel, et ce qu'il en reste.
 */
@Composable
private fun StageList(state: OfflineDownloadState) {
    val scheme = MaterialTheme.colorScheme
    val steps = state.stagesAfterTiles
    val current = steps.indexOf(state.stage)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        steps.forEachIndexed { index, step ->
            Row(Modifier.fillMaxWidth().height(StageLineHeight), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    when {
                        index < current -> Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), tint = scheme.secondary)
                        index == current -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                            strokeCap = StrokeCap.Round)
                        else -> Box(Modifier.size(12.dp).clip(CircleShape).background(scheme.surfaceContainerHigh))
                    }
                }
                Text(
                    stringResource(when (step) {
                        OfflineStage.POIS -> R.string.offline_stage_pois
                        OfflineStage.THUMBNAILS -> R.string.offline_stage_thumbnails
                        else -> R.string.offline_stage_writing
                    }),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (index == current) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (index <= current) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Hauteur d'une ligne d'etape. */
private val StageLineHeight = 28.dp

/** Un compte : son libelle en petites capitales grises, sa valeur a chiffres de chasse fixe. */
@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 8.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor, maxLines = 1)
    }
}

@Composable
private fun ResultContent(
    icon: @Composable () -> Unit,
    message: String,
    onClose: () -> Unit,
    detail: String? = null,
) {
    Row(verticalAlignment = Alignment.Top) {
        icon()
        Column(Modifier.padding(start = Spacing.m, top = 3.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            // A l'aplomb du message et non de l'icone : c'est une precision sur ce qui vient d'etre dit,
            // pas une seconde nouvelle.
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = Spacing.m), horizontalArrangement = Arrangement.End) {
        Button(onClick = onClose, modifier = Modifier.height(44.dp)) { Text(stringResource(R.string.action_close)) }
    }
}

/**
 * Popup réduite : un bouton de la carte comme les autres (SPEC section 4), et non plus un carré orange. Un
 * anneau de progression autour du pourcentage dit que le téléchargement continue, et combien il en reste ;
 * un toucher rouvre la popup.
 */
@Composable
fun OfflineMinimizedButton(
    state: OfflineDownloadState, fg: Color, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    MapProgressButton(state.percent, stringResource(R.string.offline_action_reopen), fg, onClick, modifier)
}

/**
 * Un transfert en cours, sur la carte : le bouton de carte, un anneau qui se remplit et le pourcentage au
 * milieu. Sans pourcentage connu, l'anneau tourne. Commun au fond de plan et aux donnees d'itineraire,
 * qui peuvent courir en meme temps, cote a cote.
 *
 * [modifier] porte le fond des boutons de la carte (cf. MapChrome.buttonBackground).
 */
@Composable
internal fun MapProgressButton(
    percent: Int?, contentDescription: String, fg: Color, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(48.dp).clip(RoundedCornerShape(ControlButtonRadius)).clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val scheme = MaterialTheme.colorScheme
        if (percent != null) {
            CircularProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.size(38.dp),
                color = scheme.primary, trackColor = scheme.outlineVariant, strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round, gapSize = 0.dp,
            )
            Text("$percent", style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold, color = fg)
        } else {
            CircularProgressIndicator(Modifier.size(38.dp), color = scheme.primary, strokeWidth = 3.dp,
                trackColor = scheme.outlineVariant, strokeCap = StrokeCap.Round)
        }
    }
}
