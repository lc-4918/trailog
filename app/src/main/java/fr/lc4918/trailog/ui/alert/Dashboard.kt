package fr.lc4918.trailog.ui.alert

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
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
import kotlin.math.roundToInt
import fr.lc4918.trailog.ui.theme.Spacing
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.domain.geo.Trip
import fr.lc4918.trailog.ui.routes.MapChromeActive

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

        /**
         * Les tailles reglees, par champ : `"speed:22,ascent:14"`. Un champ absent garde la taille par
         * defaut, une cle inconnue s'ignore, une valeur aberrante se ramene dans ses bornes.
         *
         * Les REGLEES plutot que toutes, comme les champs masques : un champ ajoute plus tard n'a pas
         * besoin d'une entree pour s'afficher correctement, et une base ancienne reste lisible.
         */
        fun fontSizes(csv: String): Map<DashboardField, Int> {
            val parCle = entries.associateBy { it.key }
            return csv.split(',').mapNotNull { part ->
                val i = part.indexOf(':')
                if (i <= 0) return@mapNotNull null
                val f = parCle[part.take(i).trim()] ?: return@mapNotNull null
                val sp = part.substring(i + 1).trim().toIntOrNull() ?: return@mapNotNull null
                f to sp.coerceIn(DashboardFontMinSp, DashboardFontMaxSp)
            }.toMap()
        }

        /** La taille de [field] apres l'avoir mise a [sp], dans l'ordre des champs. */
        fun withFontSize(csv: String, field: DashboardField, sp: Int): String {
            val tailles = fontSizes(csv).toMutableMap()
            if (sp == DashboardFontDefaultSp) tailles -= field else tailles[field] = sp
            return entries.filter { it in tailles }.joinToString(",") { "${it.key}:${tailles[it]}" }
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

/**
 * Le corps d'un compteur, en points, quand rien n'est regle : celui qu'ils ont toujours eu.
 *
 * Il se regle desormais, **champ par champ** (cf. [DashboardField.fontSizes]) : ce qu'on veut lire d'un
 * coup d'oeil sur un guidon n'est pas la meme chose pour tout le monde ni pour toutes les sorties - la
 * vitesse en grand et le reste en petit, ou l'inverse -, et un corps unique obligeait a choisir pour tous.
 */
const val DashboardFontDefaultSp = 16

/** Les bornes du reglage : en deca on ne lit plus, au-dela un seul champ prend la carte. */
const val DashboardFontMinSp = 10
const val DashboardFontMaxSp = 40

/**
 * Le libelle par rapport a la valeur : onze points pour seize, et cette proportion se garde.
 *
 * Grossir la valeur sans grossir son libelle finirait par nommer en petites lettres un chiffre enorme ;
 * les grossir pareil ferait crier "Distance" aussi fort que la distance. C'est un rapport, pas deux
 * reglages : personne n'a envie d'en regler deux.
 */
private const val LabelRatio = 11f / 16f

/** Fond du panneau. Presque opaque, comme la bande du calcul d'itineraire : la carte transparait a peine. */
private const val PanelAlpha = 0.96f

/**
 * Le tableau de bord de la sortie, en bas de la carte.
 *
 * Une bande aux couleurs du theme, comme celle du calcul d'itineraire - coins hauts arrondis, fond
 * presque opaque -, et des compteurs en tuiles teintees : il se lit d'un coup d'oeil, telephone sur le
 * guidon, sans que la carte dessous brouille les chiffres.
 *
 * - en tete, "Sortie", et face a lui la remise a zero des compteurs, qui demande confirmation - une
 *   sortie effacee d'un doigt qui glisse ne se retrouve pas. Elle occupait auparavant une ligne a elle
 *   seule, au bas du panneau ;
 * - une rangee pour la sortie : vitesse, distance, duree, D+, D- ;
 * - sur une trace reconnue (cf. `AutoFollow`), sous un filet : la cloche, le nom de la trace et son
 *   avancement - une barre et un pourcentage -, puis la rangee de ce qu'il en reste.
 *
 * @param progress l'avancement sur la trace suivie, null hors trace.
 * @param trackName le nom de la trace suivie, null hors trace.
 * @param fontSizes le corps de chaque compteur (cf. [DashboardField.fontSizes]) ; les libelles suivent.
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
    fontSizes: Map<DashboardField, Int> = emptyMap(),
    imperial: Boolean,
    onBell: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmReset by remember { mutableStateOf(false) }
    val following = trackName != null && progress != null
    val scheme = MaterialTheme.colorScheme
    // La forme dessine le fond et l'ombre sans decouper le contenu (cf. RoutePlannerBand).
    val shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0))
    CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
        Column(
            modifier
                .fillMaxWidth()
                .shadow(8.dp, shape, clip = false)
                .background(scheme.surface.copy(alpha = PanelAlpha), shape)
                .navigationBarsPadding()
                .padding(start = Spacing.m, end = Spacing.m, top = 6.dp, bottom = Spacing.l)
                .testTag("dashboard"),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(Modifier.fillMaxWidth().height(40.dp).padding(start = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.dash_ride_title), style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                ResetButton(onClick = { confirmReset = true })
            }
            FieldRow(DashboardField.shown(hidden, onTrack = false), trip, speedMps, progress, imperial, fontSizes)
            if (following) {
                HorizontalDivider(Modifier.padding(start = Spacing.xs, end = Spacing.xs, top = 6.dp),
                    color = scheme.outlineVariant)
                TrackHeader(trackName.orEmpty(), progress!!, armed, alerting, onBell)
                FieldRow(DashboardField.shown(hidden, onTrack = true), trip, speedMps, progress, imperial, fontSizes)
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

/** "Reinitialiser", icone et libelle, en pastille discrete : il efface, il ne doit pas attirer le doigt. */
@Composable
private fun ResetButton(onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.height(36.dp).clip(CircleShape).clickable(onClick = onClick)
            .padding(start = 10.dp, end = Spacing.m).testTag("dashboard_reset"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.RestartAlt, null, Modifier.size(18.dp), tint = tint)
        Text(stringResource(R.string.dash_reset), style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/**
 * La trace suivie : la cloche de l'alerte, son nom, et ou l'on en est - une barre et un pourcentage.
 *
 * La cloche armee est du bleu des commandes de la carte en marche ; en alerte, elle passe au rouge, sur un
 * rond rouge pale, et la barre avec elle : c'est le seul endroit du panneau qui dise qu'on s'est ecarte.
 */
@Composable
private fun TrackHeader(name: String, progress: FollowProgress, armed: Boolean, alerting: Boolean, onBell: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fraction = progressFraction(progress)
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        IconButton(
            onClick = onBell,
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(if (alerting) scheme.errorContainer else Color.Transparent)
                .testTag("dashboard_bell"),
        ) {
            Icon(
                if (armed) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                stringResource(R.string.content_desc_off_track_alert),
                tint = when {
                    alerting -> scheme.error
                    armed -> MapChromeActive
                    else -> scheme.onSurfaceVariant
                },
                // En alerte, elle sonne (cf. ringing).
                modifier = Modifier.ringing(alerting),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("dashboard_track"))
                Text("${(fraction * 100).roundToInt()} %",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.s).testTag("dashboard_percent"))
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(scheme.surfaceContainerHigh)) {
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape)
                    .background(if (alerting) scheme.error else scheme.primary))
            }
        }
    }
}

/** La part de la trace deja parcourue, entre 0 et 1 : zero tant que rien n'est ni fait ni a faire. */
internal fun progressFraction(p: FollowProgress): Float {
    val total = p.doneM + p.remainingM
    if (total <= 0.0) return 0f
    return (p.doneM / total).toFloat().coerceIn(0f, 1f)
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
    fontSizes: Map<DashboardField, Int>,
) {
    if (fields.isEmpty()) return
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        fields.forEach { f ->
            Field(
                stringResource(f.shortLabel), value(f, trip, speedMps, progress, imperial),
                fontSizes[f] ?: DashboardFontDefaultSp,
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

/**
 * Un compteur : une tuile teintee, son libelle en petites capitales grises, sa valeur en demi-gras a
 * chiffres de chasse fixe - un chiffre qui defile ne fait pas danser ses voisins.
 */
@Composable
private fun Field(label: String, value: String, fontSp: Int, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .background(scheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 8.dp),
    ) {
        FitText(
            AnnotatedString(label.uppercase()), color = scheme.onSurfaceVariant,
            fontSize = (fontSp * LabelRatio).sp, weight = FontWeight.SemiBold, letterSpacing = 0.06.em,
        )
        FitText(withSmallUnit(value, scheme.onSurfaceVariant), color = scheme.onSurface, fontSize = fontSp.sp,
            weight = FontWeight.SemiBold, features = "tnum")
    }
}

/** Le nombre en grand, l'unite plus petite et grise : "12,3" se lit d'abord, "km/h" ensuite. */
private fun withSmallUnit(value: String, unitColor: Color): AnnotatedString {
    val i = value.lastIndexOf(' ')
    if (i <= 0) return AnnotatedString(value)
    return buildAnnotatedString {
        append(value.substring(0, i))
        withStyle(SpanStyle(fontSize = 0.7.em, fontWeight = FontWeight.Medium, color = unitColor)) {
            append(value.substring(i))
        }
    }
}

/**
 * Un texte d'une ligne qui se resserre plutot que de se couper : sur un petit ecran, "12,3 km/h" doit
 * rester lisible en entier, quitte a perdre un point de corps. Il se mesure, et se reduit tant qu'il deborde.
 */
@Composable
private fun FitText(
    text: AnnotatedString, color: Color, fontSize: TextUnit, weight: FontWeight,
    letterSpacing: TextUnit = TextUnit.Unspecified, features: String? = null,
) {
    // Repart de la taille pleine quand la LONGUEUR change, pas a chaque valeur : le chiffre qui defile
    // chaque seconde ne doit pas faire clignoter la taille.
    var scale by remember(text.length) { mutableFloatStateOf(1f) }
    var ready by remember(text.length) { mutableStateOf(false) }
    Text(
        text, color = color, fontSize = fontSize * scale, lineHeight = fontSize * 1.2f, fontWeight = weight,
        letterSpacing = letterSpacing, style = LocalTextStyle.current.copy(fontFeatureSettings = features),
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
