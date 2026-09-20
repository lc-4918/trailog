package fr.lc4918.trailog.ui.alert

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.domain.geo.Trip
import fr.lc4918.trailog.ui.routes.MapChromeActive
import fr.lc4918.trailog.ui.routes.OffTrackAlertColor

/**
 * Un champ du tableau de bord : sa cle en base, son nom court (sur la carte) et son nom long (dans les
 * reglages).
 *
 * [onTrack] : le champ ne dit quelque chose que sur une trace suivie - ce qu'il en reste. Hors trace, il
 * n'a pas de valeur, et il ne s'affiche pas plutot que d'afficher un tiret. Ces champs-la ont leur
 * propre rangee, sous le nom de la trace.
 */
enum class DashboardField(
    val key: String,
    @StringRes val shortLabel: Int,
    @StringRes val settingsLabel: Int,
    val onTrack: Boolean = false,
) {
    SPEED("speed", R.string.dash_speed, R.string.dash_set_speed),
    DISTANCE("distance", R.string.dash_distance, R.string.dash_set_distance),
    DURATION("duration", R.string.dash_duration, R.string.dash_set_duration),
    ASCENT("ascent", R.string.dash_ascent, R.string.dash_set_ascent),
    DESCENT("descent", R.string.dash_descent, R.string.dash_set_descent),
    REMAINING("remaining", R.string.dash_remaining, R.string.follow_remaining, onTrack = true),
    REMAINING_TIME("remainingTime", R.string.dash_eta, R.string.dash_set_eta, onTrack = true),
    REMAINING_ASCENT("remainingAscent", R.string.dash_ascent_remaining, R.string.follow_ascent_remaining, onTrack = true),
    REMAINING_DESCENT("remainingDescent", R.string.dash_descent_remaining, R.string.follow_descent_remaining, onTrack = true);

    companion object {
        /** Les champs masques, lus de la colonne `dashboardHidden` : une cle inconnue s'ignore. */
        fun hidden(csv: String): Set<DashboardField> {
            val keys = csv.split(',').map { it.trim() }.toSet()
            return entries.filterTo(LinkedHashSet()) { it.key in keys }
        }

        /** La colonne apres avoir masque ou montre [field], dans l'ordre des champs. */
        fun withHidden(csv: String, field: DashboardField, hide: Boolean): String {
            val set = hidden(csv).toMutableSet()
            if (hide) set += field else set -= field
            return entries.filter { it in set }.joinToString(",") { it.key }
        }

        /** Les champs a afficher de la rangee [onTrack], dans leur ordre. */
        fun shown(hidden: Set<DashboardField>, onTrack: Boolean): List<DashboardField> =
            entries.filter { it !in hidden && it.onTrack == onTrack }
    }
}

/**
 * Les calculs du tableau de bord qui ne sont pas de simple mise en forme.
 */
object DashboardMath {

    /**
     * Une position plus vieille que cela ne dit plus la vitesse du moment : le capteur en rend une toutes
     * les deux secondes, et deux absences de suite veulent dire qu'il ne sait plus - on ne fige pas la
     * derniere vitesse de marche sous les yeux de quelqu'un qui s'est arrete.
     */
    const val STALE_FIX_MS = 5_000L

    /** Temps en mouvement en deca duquel la moyenne ne vaut rien pour extrapoler. */
    const val MIN_MOVING_FOR_ETA_MS = 60_000L

    /**
     * Sans deplacement compte depuis ce temps (cf. TripStats.MIN_STEP_M), on n'avance pas : cinq metres en
     * vingt secondes, c'est moins de 1 km/h.
     */
    const val STILL_MS = 20_000L

    /**
     * La vitesse affichee : celle du capteur - a pied, un 1 km/h dit quelque chose -, sauf quand elle
     * n'est que du bruit. Zero :
     * - quand la position a vieilli : a l'arret prolonge, le service n'en demande plus (cf.
     *   LocationService.pace) ;
     * - quand elle ne depasse pas sa propre incertitude, telle que le capteur la donne ;
     * - quand la position n'a pas bouge depuis [STILL_MS] : telephone pose sur une table, le capteur
     *   annonce parfois quelques km/h, mais la distance, elle, ne ment pas.
     * Null tant qu'aucune position n'est arrivee.
     */
    fun speed(speedMps: Float?, speedAccuracyMps: Float?, fixAgeMs: Long?, stillMs: Long?): Float? {
        if (fixAgeMs == null) return null
        if (fixAgeMs > STALE_FIX_MS) return 0f
        val v = speedMps ?: return null
        if (speedAccuracyMps != null && v <= speedAccuracyMps) return 0f
        if (stillMs != null && stillMs > STILL_MS) return 0f
        return v
    }

    /**
     * Le temps restant estime : le restant, a la vitesse moyenne EN MOUVEMENT de la sortie. Les arrets ne
     * la tirent pas vers le bas - on ne sait pas s'il y en aura d'autres - et une moyenne de moins d'une
     * minute ne dit encore rien.
     */
    fun etaMs(trip: Trip, remainingM: Double): Long? {
        if (trip.movingMs < MIN_MOVING_FOR_ETA_MS || trip.distanceM <= 0.0) return null
        val mps = trip.distanceM / (trip.movingMs / 1000.0)
        return (remainingM / mps * 1000.0).toLong()
    }
}

/** Largeur relative du champ de la vitesse : "12,3 km/h" est la plus longue des valeurs de la rangee. */
private const val SpeedWeight = 1.25f

/** L'ecart d'une demi-ligne qui separe la sortie de la trace suivie. */
private val SectionGap = 12.dp

/**
 * Le corps des compteurs, en points, quand rien n'est regle : celui qu'ils ont toujours eu.
 *
 * Il se regle desormais (cf. `SettingsEntity.dashboardFontSize`) : un guidon se lit a bout de bras, et
 * seize points a cette distance ne valent pas seize points dans la main.
 */
const val DashboardFontDefaultSp = 16

/**
 * Le libelle par rapport a la valeur : onze points pour seize, et cette proportion se garde.
 *
 * Grossir la valeur sans grossir son libelle finirait par nommer en petites lettres un chiffre enorme ;
 * les grossir pareil ferait crier "Distance" aussi fort que la distance. C'est un rapport, pas deux
 * reglages : personne n'a envie d'en regler deux.
 */
private const val LabelRatio = 11f / 16f

/**
 * Le tableau de bord de la sortie, en bas de la carte.
 *
 * Des champs encadres, sur un fond presque opaque : il se lit d'un coup d'oeil, telephone sur le guidon,
 * sans que la carte dessous brouille les chiffres.
 *
 * - une rangee pour la sortie, sur toute la largeur : vitesse, distance, duree, D+, D- ;
 * - sur une trace reconnue (cf. `AutoFollow`), apres une demi-ligne : la cloche et le nom de la trace,
 *   puis la rangee de ce qu'il en reste ;
 * - enfin la remise a zero des compteurs, a droite, qui demande confirmation - une sortie effacee d'un
 *   doigt qui glisse ne se retrouve pas.
 *
 * @param progress l'avancement sur la trace suivie, null hors trace.
 * @param trackName le nom de la trace suivie, null hors trace.
 * @param fontSp le corps des compteurs (cf. [DashboardFontDefaultSp]) ; les libelles suivent.
 */
@Composable
fun Dashboard(
    trip: Trip,
    speedMps: Float?,
    progress: FollowProgress?,
    trackName: String?,
    armed: Boolean,
    alerting: Boolean,
    hidden: Set<DashboardField>,
    fontSp: Int = DashboardFontDefaultSp,
    imperial: Boolean,
    bg: Color,
    fg: Color,
    onBell: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmReset by remember { mutableStateOf(false) }
    val following = trackName != null && progress != null
    Column(
        modifier
            .fillMaxWidth()
            .background(bg.copy(alpha = 0.9f))
            .navigationBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp)
            .testTag("dashboard"),
    ) {
        FieldRow(DashboardField.shown(hidden, onTrack = false), trip, speedMps, progress, imperial, fg, fontSp)
        if (following) {
            Spacer(Modifier.height(SectionGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBell, modifier = Modifier.size(40.dp).testTag("dashboard_bell")) {
                    Icon(
                        if (armed) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                        stringResource(R.string.content_desc_off_track_alert),
                        tint = when {
                            alerting -> OffTrackAlertColor
                            armed -> MapChromeActive
                            else -> fg
                        },
                    )
                }
                Text(
                    trackName.orEmpty(), color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("dashboard_track"),
                )
            }
            FieldRow(DashboardField.shown(hidden, onTrack = true), trip, speedMps, progress, imperial, fg, fontSp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { confirmReset = true }, modifier = Modifier.size(40.dp).testTag("dashboard_reset")) {
                Icon(Icons.Filled.RestartAlt, stringResource(R.string.dash_reset), tint = fg)
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.dash_reset_title)) },
            text = { Text(stringResource(R.string.dash_reset_text)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) { Text(stringResource(R.string.dash_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * Une rangee de champs sur toute la largeur ; rien du tout quand tous sont masques.
 *
 * **Elle passe a la ligne** plutot que de tout tenir sur une seule : les champs se resserrent deja pour
 * rester lisibles (cf. [FitText]), et sans retour a la ligne un corps regle plus grand serait aussitot
 * repris par ce resserrement - le reglage n'aurait aucun effet visible. Ce qui ne tient pas descend donc,
 * et le panneau grandit d'autant : mieux vaut un tableau de bord haut que des chiffres qu'on ne lit pas.
 *
 * `FlowRow` est encore marque experimental dans cette version de Compose, d'ou l'acceptation explicite :
 * l'API est celle d'une `Row` a un detail pres, et la reecrire a la main ne rendrait pas le code plus sur.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldRow(
    fields: List<DashboardField>, trip: Trip, speedMps: Float?, progress: FollowProgress?, imperial: Boolean,
    fg: Color, fontSp: Int,
) {
    if (fields.isEmpty()) return
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        fields.forEach { f ->
            Field(
                stringResource(f.shortLabel), value(f, trip, speedMps, progress, imperial), fg, fontSp,
                Modifier.weight(if (f == DashboardField.SPEED) SpeedWeight else 1f),
            )
        }
    }
}

/** La valeur d'un champ, mise en forme. Un tiret quand le capteur ne l'a pas donnee. */
internal fun value(
    f: DashboardField, trip: Trip, speedMps: Float?, progress: FollowProgress?, imperial: Boolean,
): String = when (f) {
    DashboardField.SPEED -> speedMps?.let { Format.speedFixed(it.toDouble(), imperial) } ?: "-"
    DashboardField.DISTANCE -> Format.shortDistance(trip.distanceM, imperial)
    DashboardField.DURATION -> Format.chrono(trip.movingMs)
    DashboardField.ASCENT -> Format.elevation(trip.ascentM, imperial)
    DashboardField.DESCENT -> Format.elevation(trip.descentM, imperial)
    DashboardField.REMAINING -> progress?.let { Format.shortDistance(it.remainingM, imperial) } ?: "-"
    DashboardField.REMAINING_TIME ->
        progress?.let { DashboardMath.etaMs(trip, it.remainingM) }?.let { Format.chrono(it) } ?: "-"
    DashboardField.REMAINING_ASCENT -> progress?.let { Format.elevation(it.remainingAscentM, imperial) } ?: "-"
    DashboardField.REMAINING_DESCENT -> progress?.let { Format.elevation(it.remainingDescentM, imperial) } ?: "-"
}

@Composable
private fun Field(label: String, value: String, fg: Color, fontSp: Int, modifier: Modifier) {
    Column(
        modifier
            .border(1.dp, fg.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp),
    ) {
        FitText(
            AnnotatedString(label), color = fg.copy(alpha = 0.7f),
            fontSize = (fontSp * LabelRatio).sp, weight = FontWeight.Normal,
        )
        FitText(withSmallUnit(value), color = fg, fontSize = fontSp.sp, weight = FontWeight.SemiBold)
    }
}

/** Le nombre en grand, l'unite plus petite : "12,3" se lit d'abord, "km/h" ensuite. */
private fun withSmallUnit(value: String): AnnotatedString {
    val i = value.lastIndexOf(' ')
    if (i <= 0) return AnnotatedString(value)
    return buildAnnotatedString {
        append(value.substring(0, i))
        withStyle(SpanStyle(fontSize = 0.7.em, fontWeight = FontWeight.Normal)) { append(value.substring(i)) }
    }
}

/**
 * Un texte d'une ligne qui se resserre plutot que de se couper : sur un petit ecran, "12,3 km/h" doit
 * rester lisible en entier, quitte a perdre un point de corps. Il se mesure, et se reduit tant qu'il deborde.
 */
@Composable
private fun FitText(text: AnnotatedString, color: Color, fontSize: TextUnit, weight: FontWeight) {
    // Repart de la taille pleine quand la LONGUEUR change, pas a chaque valeur : le chiffre qui defile
    // chaque seconde ne doit pas faire clignoter la taille.
    var scale by remember(text.length) { mutableFloatStateOf(1f) }
    var ready by remember(text.length) { mutableStateOf(false) }
    Text(
        text, color = color, fontSize = fontSize * scale, lineHeight = fontSize * 1.2f, fontWeight = weight,
        maxLines = 1, softWrap = false,
        onTextLayout = { r ->
            if (r.hasVisualOverflow && scale > MinTextScale) scale = (scale * 0.92f).coerceAtLeast(MinTextScale)
            else ready = true
        },
        modifier = Modifier.drawWithContent { if (ready) drawContent() },
    )
}

/** On ne resserre pas en dessous : au-dela, un chiffre ne se lit plus d'un coup d'oeil. */
private const val MinTextScale = 0.6f
