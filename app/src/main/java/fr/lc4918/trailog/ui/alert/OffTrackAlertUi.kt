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
 * [followed] marque celle qu'on suit deja, s'il y en a une : elle reste dans la liste, avec son ecart a
 * jour, et le bouton d'arret est a cote.
 */
@Composable
fun TrackChooserDialog(
    candidates: List<TrackCandidate>?,
    followed: TrackWatch.Followed?,
    imperial: Boolean,
    /** Le reglage "Emettre un son" de l'alerte d'eloignement : commande la cloche de la ligne suivie
     *  (cf. TrackChoice). */
    soundEnabled: Boolean,
    /** L'avancement sur la trace suivie, ou null quand on n'en suit aucune - ou qu'aucune position n'est
     *  encore arrivee pour le calculer. */
    progress: FollowProgress?,
    onPick: (TrackCandidate) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(
                if (followed != null) R.string.follow_dashboard_title else R.string.alert_pick_track_title,
            ))
        },
        text = {
            when {
                /*
                 * **Une trace suivie : son nom, et ou l'on en est. Pas la liste des autres.**
                 *
                 * La liste restait affichee sous la ligne en surbrillance, c'est-a-dire que la popup
                 * consacrait tout son espace a la seule chose dont on n'a plus besoin - choisir - quand la
                 * question devenue urgente est "combien reste-t-il, et combien de montee". Choisir une
                 * autre trace se fait en arretant celle-ci, ce que le bouton d'en bas propose.
                 */
                followed != null -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    FollowedHeader(followed, soundEnabled)
                    if (progress != null) {
                        FollowDashboard(progress, imperial, Modifier.padding(top = 10.dp))
                    } else {
                        // Suivi tout juste repris, position pas encore arrivee : le tableau de bord n'a
                        // rien a montrer, et neuf tirets vaudraient moins qu'une attente franche.
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                // Position pas encore recue, ou recherche en cours : les deux se ressemblent assez pour
                // partager leur attente - il n'y a rien a dire de plus qu'"on cherche".
                candidates == null -> Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
                candidates.isEmpty() -> Text(stringResource(R.string.alert_pick_track_empty))
                else -> Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    candidates.forEach { c ->
                        TrackChoice(
                            candidate = c,
                            current = false,
                            imperial = imperial,
                            soundEnabled = soundEnabled,
                            onPick = onPick,
                        )
                    }
                }
            }
        },
        // Le bouton d'arret prend la place du bouton de confirmation : il n'y a rien a confirmer ici, un
        // tap dans la liste vaut choix. Absent tant qu'on ne suit rien - il n'aurait rien a arreter.
        // L'arret rend la liste des traces, la popup restant ouverte : on arrete presque toujours pour en
        // suivre une autre.
        confirmButton = {
            if (followed != null) {
                TextButton(onClick = onStop) { Text(stringResource(R.string.alert_stop_following)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/**
 * L'en-tete de la trace suivie : son nom, sur l'aplat qui le distingue, et la cloche s'il doit sonner.
 *
 * Elle etait une ligne parmi d'autres, en surbrillance dans la liste. Elle est maintenant SEULE en haut de
 * la popup, ce qui rend la surbrillance moins necessaire - mais l'aplat reste : il rattache d'un coup
 * d'oeil ce titre au bouton bleu de la carte, et dit que ceci n'est pas un choix mais un etat.
 */
@Composable
private fun FollowedHeader(followed: TrackWatch.Followed, soundEnabled: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                trackLabel(followed.layerName, followed.trackIndex, followed.trackCount),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            // La cloche ne dit pas "suivie" mais "sonnera" : sans le reglage du son, l'ecart se voit
            // toujours - banniere, couleur du bouton - mais rien ne sonne, et elle promettrait un bruit
            // qui ne vient pas.
            if (soundEnabled) Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(18.dp))
        }
    }
}

/**
 * Une trace de la liste, et la mise en evidence de celle qu'on suit deja.
 *
 * **Trois marques et non une**, parce que la question posee est "laquelle est-ce que je suis en ce
 * moment ?" et qu'une graisse de caractere n'y repondait pas : sur une liste de huit lignes qui portent
 * toutes le meme genre de nom, la ligne en gras se cherche. Elle porte donc un FOND et le mot "Suivie" -
 * l'aplat se voit d'un coup d'oeil, le mot reste la seule marque que lit une synthese vocale.
 *
 * **La cloche, elle, ne dit pas "suivie" mais "sonnera"** : elle ne parait que [soundEnabled], le reglage
 * "Emettre un son" de l'alerte d'eloignement. Sans lui, l'ecart se voit toujours - banniere, couleur du
 * bouton de la carte - mais rien ne sonne, et une cloche presente aurait promis un bruit qui ne vient pas.
 *
 * Elle reste CLIQUABLE comme les autres : la retoucher n'a pas d'effet, mais une ligne qui refuse le tap
 * dans une liste ou tout se tape se lit comme une panne. Ce qui l'arrete est le bouton d'arret, a l'oppose.
 */
@Composable
private fun TrackChoice(
    candidate: TrackCandidate,
    current: Boolean,
    imperial: Boolean,
    soundEnabled: Boolean,
    onPick: (TrackCandidate) -> Unit,
) {
    val fond = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val encre = if (current) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = { onPick(candidate) },
        shape = RoundedCornerShape(8.dp),
        color = fond,
        contentColor = encre,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    trackLabel(candidate.layerName, candidate.trackIndex, candidate.trackCount),
                    textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth(),
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    stringResource(R.string.alert_track_away, Format.shortDistance(candidate.awayM, imperial)),
                    style = MaterialTheme.typography.bodySmall,
                    // Sur l'aplat de la ligne suivie, la teinte "variante" du theme n'a plus le contraste
                    // qu'elle a sur le fond de la boite : l'ecart s'y lisait plus pale que le reste.
                    color = if (current) encre.copy(alpha = SecondaryTextAlpha)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth(),
                )
            }
            if (current) {
                if (soundEnabled) Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(18.dp))
                Text(
                    stringResource(R.string.alert_track_following),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Ce qui separe une ligne secondaire de son texte principal, sans la faire disparaitre. */
private const val SecondaryTextAlpha = 0.8f

/** Nom d'un segment : celui de sa couche, numerote seulement quand elle en porte plusieurs. */
@Composable
private fun trackLabel(layerName: String, index: Int, count: Int): String =
    if (count <= 1) layerName
    else stringResource(R.string.alert_track_segment, layerName, index + 1, count)
