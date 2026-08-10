package fr.lc4918.trailog.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.TrackStats
import androidx.compose.ui.res.stringResource

/* ----------------------- Infos de trace, en colonnes ----------------------- */

/**
 * Une info de trace, prete a s'afficher en colonne : son libelle court en tete ([label]), sa valeur et son
 * unite dessous. [name] est le libelle long, que l'appui long montre avec la valeur entiere.
 */
internal class TitleInfo(val key: String, val label: String, val name: String, val value: String) {
    /** Le nombre, sans son unite : c'est lui qui porte le poids visuel de la colonne. */
    val number: String get() = splitUnit(value).first
    /** L'unite seule ("km", "m", "%"), ou vide quand la valeur n'en a pas (une duree, par exemple). */
    val unit: String get() = splitUnit(value).second
}

/**
 * Decoupe "14,20 km" en "14,20" et "km".
 *
 * L'unite ne se reconnait qu'a sa forme : ce qui suit la derniere espace, si ce ne sont que des lettres ou
 * un pourcent. Une duree ("1 h 20 min") n'en a donc pas au sens ou l'entend cette colonne - "min" y fait
 * partie de la valeur, comme le "h" qui le precede - et sort d'un seul tenant.
 */
internal fun splitUnit(v: String): Pair<String, String> {
    if (v.endsWith("%")) return v.dropLast(1).trim() to "%"
    val i = v.lastIndexOf(' ')
    if (i <= 0) return v to ""
    val tail = v.substring(i + 1)
    return if (tail.isNotEmpty() && tail.all { it.isLetter() }) v.take(i) to tail else v to ""
}

@Composable
internal fun titleInfos(stats: TrackStats, csv: String, imp: Boolean): List<TitleInfo> {
    // Libelles de colonne : ceux des pastilles qui choisissent ces memes infos dans les reglages. Les
    // reprendre plutot que d'en ecrire d'autres garde les deux ecrans d'accord, et evite huit traductions.
    val lDist = stringResource(R.string.chip_distance)
    val lAsc = stringResource(R.string.chip_ascent)
    val lDesc = stringResource(R.string.chip_descent)
    val lDur = stringResource(R.string.chip_duration)
    val lMin = stringResource(R.string.chip_alt_min)
    val lMax = stringResource(R.string.chip_alt_max)
    val nDist = stringResource(R.string.info_name_distance)
    val nAsc = stringResource(R.string.info_name_ascent)
    val nDesc = stringResource(R.string.info_name_descent)
    val nDur = stringResource(R.string.info_name_duration)
    val nMin = stringResource(R.string.info_name_alt_min)
    val nMax = stringResource(R.string.info_name_alt_max)
    return csv.split(",").mapNotNull { raw ->
        val k = raw.trim()
        when (k) {
            "dist" -> TitleInfo(k, lDist, nDist, Format.distance(stats.distance, imp))
            "asc" -> TitleInfo(k, lAsc, nAsc, Format.elevation(stats.ascent, imp))
            "desc" -> TitleInfo(k, lDesc, nDesc, Format.elevation(stats.descent, imp))
            "dur" -> stats.duration?.let { TitleInfo(k, lDur, nDur, Format.duration(it)) }
            "min" -> TitleInfo(k, lMin, nMin, Format.elevation(stats.min, imp))
            "max" -> TitleInfo(k, lMax, nMax, Format.elevation(stats.max, imp))
            else -> null
        }
    }
}

/**
 * Les memes colonnes pour un parcours calcule : ses trois totaux, puis sa duree, qui ne vient pas des
 * echantillons mais du moteur d'itineraire ([seconds]).
 *
 * [estimated] prefixe la duree d'un "~" : zoome sur une portion, elle est calculee au prorata de la
 * distance, ce qui n'est qu'une approximation - une portion qui monte se parcourt plus lentement qu'une
 * portion plate de meme longueur.
 */
@Composable
internal fun routeInfos(
    stats: TrackStats, seconds: Double, estimated: Boolean, imp: Boolean,
): List<TitleInfo> {
    val lDur = stringResource(R.string.chip_duration)
    val nDur = stringResource(R.string.info_name_duration)
    val duration = Format.duration(seconds)
    return titleInfos(stats, "dist,asc,desc", imp) +
        TitleInfo("dur", lDur, nDur, if (estimated) "~$duration" else duration)
}

/** Les memes colonnes pour le point courant : distance parcourue, altitude, pente, horaire. */
@Composable
internal fun cursorInfos(s: Sample, csv: String, imp: Boolean): List<TitleInfo> {
    val lDist = stringResource(R.string.chip_distance)
    val lEle = stringResource(R.string.chip_altitude)
    val lSlope = stringResource(R.string.chip_slope)
    val lTime = stringResource(R.string.chip_time)
    return csv.split(",").mapNotNull { raw ->
        val k = raw.trim()
        when (k) {
            "dist" -> TitleInfo(k, lDist, lDist, Format.distance(s.x, imp))
            "ele" -> TitleInfo(k, lEle, lEle, Format.elevation(s.z, imp))
            "slope" -> TitleInfo(k, lSlope, lSlope, "${"%.1f".format(s.slope)}%")
            "time" -> s.t?.let { TitleInfo(k, lTime, lTime, Format.duration(it)) }
            else -> null
        }
    }
}

/**
 * Infos d'une trace, en colonnes : le libelle en tete, petit et en capitales, la valeur dessous, grasse,
 * son unite a sa suite en plus discret. Une info par colonne, reparties sur toute la largeur.
 *
 * Le libelle porte la lecture, la valeur porte le regard : c'est ce qui permet de lire une seule des
 * quatre d'un coup d'oeil, la ou une ligne de valeurs separees par des points demandait de tout lire.
 *
 * Chacune reste sa propre cible d'appui long, qui montre au-dessus du doigt son libelle long et sa valeur
 * ("D+" -> "Denivele positif 1254 m") : les colonnes s'abregent, la reponse complete reste a un geste.
 *
 * [fontSp] pilote le bloc entier et non le seul chiffre : le rapport entre les trois tailles fait la
 * hierarchie, le reglage ne doit que l'agrandir ou la reduire d'un bloc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackInfoColumns(
    items: List<TitleInfo>, fontSp: Int, bold: Boolean, modifier: Modifier = Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
) {
    if (items.isEmpty()) return
    Row(modifier, horizontalArrangement = arrangement) {
        items.forEach { info ->
            key(info.key) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = {
                        PlainTooltip(caretSize = TooltipDefaults.caretSize) {
                            Text("${info.name} ${info.value}")
                        }
                    },
                    state = rememberTooltipState(),
                ) {
                    // Valeur centree sous son libelle : les deux se lisent alors comme un bloc, la ou une
                    // valeur calee a gauche sous un libelle plus large partait en escalier.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            info.label.uppercase(), fontSize = (fontSp - 2).coerceAtLeast(7).sp,
                            lineHeight = (fontSp + 2).sp, letterSpacing = 0.08.em,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(info.number, fontSize = (fontSp + 5).sp, lineHeight = (fontSp + 7).sp,
                                fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
                            if (info.unit.isNotEmpty()) {
                                Text(info.unit, fontSize = (fontSp - 1).coerceAtLeast(8).sp,
                                    lineHeight = (fontSp + 7).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                    modifier = Modifier.padding(start = 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
