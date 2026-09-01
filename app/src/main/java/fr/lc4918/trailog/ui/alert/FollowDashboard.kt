package fr.lc4918.trailog.ui.alert

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.geo.Format

/**
 * Le tableau de bord du suivi : neuf chiffres pour se situer sur la trace qu'on parcourt.
 *
 * **Ce qu'il remplace, et pourquoi.** La popup de suivi ne savait dire qu'une chose - l'ecart a la trace -
 * et continuait d'afficher la liste des AUTRES traces sous celle qu'on suit deja, c'est-a-dire la seule
 * chose dont on n'a plus besoin. La question qu'on se pose en roulant est ailleurs : combien reste-t-il,
 * et surtout combien de MONTEE reste-t-il. La distance seule ne dit pas si les trois derniers kilometres
 * sont une descente ou le mur du col.
 *
 * **Des pictogrammes et non des libelles**, et c'est le format qui l'impose : neuf lignes nommees feraient
 * un ecran de reglages, quand on veut une lecture d'un coup d'oeil, gantee, en roulant. Le nom complet
 * reste accessible a l'appui long - c'est le seul geste qui ne coute rien a qui n'en a pas besoin.
 *
 * **Fait et restant se lisent en vis-a-vis**, colonne contre colonne : c'est leur rapport qui situe, non
 * leur valeur absolue - savoir qu'on a fait 400 m de denivele ne vaut que si l'on sait qu'il en reste 900.
 */
@Composable
fun FollowDashboard(progress: FollowProgress, imperial: Boolean, modifier: Modifier = Modifier) {
    // Le libelle affiche par l'appui long, ou null quand aucun n'est demande. Un seul a la fois : deux
    // bulles ouvertes se recouvriraient sur une grille aussi serree.
    var explique by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        /*
         * Trois rangees de trois, dans l'ordre ou l'on s'en sert : ce qui se passe MAINTENANT (vitesse,
         * temps ecoule, temps restant), puis les distances, puis les denivelés.
         */
        Ligne {
            Cellule(Icons.Filled.Speed, R.string.follow_speed,
                progress.speedMps?.let { Format.speed(it.toDouble(), imperial) } ?: Vide) { explique = it }
            Cellule(Icons.Filled.Timer, R.string.follow_elapsed,
                Format.duration(progress.elapsedMs / 1000.0)) { explique = it }
            Cellule(Icons.Filled.HourglassBottom, R.string.follow_eta,
                progress.etaMs?.let { Format.duration(it / 1000.0) } ?: Vide) { explique = it }
        }
        Ligne {
            Cellule(Icons.Outlined.PlayArrow, R.string.follow_done,
                Format.shortDistance(progress.doneM, imperial)) { explique = it }
            Cellule(Icons.Filled.Flag, R.string.follow_remaining,
                Format.shortDistance(progress.remainingM, imperial)) { explique = it }
            // La troisieme place de cette rangee reste vide : deux distances, et l'aligner sur trois
            // colonnes vaut mieux que de centrer une rangee de deux sous une rangee de trois.
            CelluleVide()
        }
        Ligne {
            Cellule(Icons.Filled.ArrowUpward, R.string.follow_ascent_done,
                Format.elevation(progress.doneAscentM, imperial)) { explique = it }
            Cellule(Icons.Filled.ArrowDownward, R.string.follow_descent_done,
                Format.elevation(progress.doneDescentM, imperial)) { explique = it }
            CelluleVide()
        }
        Ligne {
            Cellule(Icons.Outlined.ArrowUpward, R.string.follow_ascent_remaining,
                Format.elevation(progress.remainingAscentM, imperial)) { explique = it }
            Cellule(Icons.Outlined.ArrowDownward, R.string.follow_descent_remaining,
                Format.elevation(progress.remainingDescentM, imperial)) { explique = it }
            CelluleVide()
        }
        /*
         * Le libelle de l'appui long, en clair sous la grille.
         *
         * Une ligne de la popup et non une bulle flottante : une infobulle Material se pose au-dessus de la
         * cellule et sort de la boite de dialogue sur la rangee du haut, ou se fait recouvrir. Ici elle a sa
         * place reservee, toujours au meme endroit, et la grille ne saute pas quand elle parait.
         */
        Text(
            explique ?: "",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/** Ce qu'affiche une valeur qu'on ne sait pas encore calculer : un tiret, et non un zero - zero serait un
 *  chiffre, et l'on croirait a l'arret ou a la fin. */
private const val Vide = "--"

@Composable
private fun Ligne(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/** La place d'une cellule absente : la grille garde ses trois colonnes, et rien ne se decale. */
@Composable
private fun RowScope.CelluleVide() {
    Column(Modifier.weight(1f)) { }
}

/**
 * Un indicateur : son pictogramme, sa valeur, et son nom a l'appui long.
 *
 * L'appui long porte sur TOUTE la cellule - le dessin comme le chiffre : viser un pictogramme de seize
 * points au doigt, en roulant, ne marche pas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.Cellule(
    icon: ImageVector,
    labelRes: Int,
    value: String,
    onExplain: (String) -> Unit,
) {
    val label = stringResource(labelRes)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.weight(1f).combinedClickable(
            // Le tap seul n'a rien a faire : c'est une valeur, pas une commande. Il est declare quand meme,
            // `combinedClickable` n'offrant pas l'appui long sans lui - et un tap qui affiche le libelle
            // vaut mieux qu'un tap qui ne fait rien.
            onClick = { onExplain("$label : $value") },
            onLongClick = { onExplain("$label : $value") },
        ),
    ) {
        Column(
            Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(icon, label, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                value,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
