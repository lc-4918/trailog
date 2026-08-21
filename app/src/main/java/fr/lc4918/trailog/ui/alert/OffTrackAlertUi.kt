package fr.lc4918.trailog.ui.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.location.TrackWatch

/**
 * Fond de la banniere d'alerte : le rouge des messages d'erreur, a peine translucide.
 *
 * Elle ne reprend pas le gris des consignes de saisie (cf. MapPromptBar), et c'est voulu : celles-la
 * accompagnent un geste qu'on vient de demander, celle-ci interrompt une marche pour dire ce qu'on n'a
 * pas vu venir. Deux barres de meme couleur au meme endroit se confondraient dans le coin de l'oeil.
 */
private val AlertBarBackground = Color(0xFFB3261E).copy(alpha = 0.94f)

/**
 * Banniere du bas : on s'est ecarte de la trace suivie, de tant, et voila laquelle.
 *
 * La croix ne rend pas la marche a la trace - elle tait CET ecart-la (cf. [OffTrackAlertState.silenced]).
 * Le suivi, lui, s'arrete depuis la cloche, la ou il a commence.
 */
@Composable
fun OffTrackAlertBar(
    trackName: String,
    awayM: Double,
    imperial: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth().padding(8.dp)
            .background(AlertBarBackground, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(end = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(20.dp), tint = Color.White)
            Text(
                stringResource(R.string.alert_off_track_banner, Format.shortDistance(awayM, imperial), trackName),
                style = MaterialTheme.typography.bodySmall, color = Color.White,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(16.dp), tint = Color.White)
        }
    }
}

/**
 * Choix de la trace a suivre, les plus proches de la position d'abord.
 *
 * La liste n'est pas celle de la bibliotheque : suivre une trace, c'est suivre celle sur laquelle on se
 * tient, et un catalogue de deux cents entrees ne repondrait pas a la question. Chacune dit son ecart -
 * c'est ce qui permet de reconnaitre la bonne quand deux passent au meme endroit.
 *
 * [followedIndex] marque celle qu'on suit deja, s'il y en a une : elle reste dans la liste, avec son ecart
 * a jour, et le bouton d'arret est a cote.
 */
@Composable
fun TrackChooserDialog(
    candidates: List<TrackCandidate>?,
    followed: TrackWatch.Followed?,
    imperial: Boolean,
    onPick: (TrackCandidate) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alert_pick_track_title)) },
        text = {
            when {
                // Position pas encore recue, ou recherche en cours : les deux se ressemblent assez pour
                // partager leur attente - il n'y a rien a dire de plus qu'"on cherche".
                candidates == null -> Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
                candidates.isEmpty() -> Text(stringResource(R.string.alert_pick_track_empty))
                else -> Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    candidates.forEach { c ->
                        val current = followed?.layerId == c.layerId && followed.trackIndex == c.trackIndex
                        TextButton(onClick = { onPick(c) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    trackLabel(c.layerName, c.trackIndex, c.trackCount),
                                    textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth(),
                                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    stringResource(R.string.alert_track_away, Format.shortDistance(c.awayM, imperial)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        },
        // Le bouton d'arret prend la place du bouton de confirmation : il n'y a rien a confirmer ici, un
        // tap dans la liste vaut choix. Absent tant qu'on ne suit rien - il n'aurait rien a arreter.
        confirmButton = {
            if (followed != null) {
                TextButton(onClick = onStop) { Text(stringResource(R.string.alert_stop_following)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Nom d'un segment : celui de sa couche, numerote seulement quand elle en porte plusieurs. */
@Composable
private fun trackLabel(layerName: String, index: Int, count: Int): String =
    if (count <= 1) layerName
    else stringResource(R.string.alert_track_segment, layerName, index + 1, count)
