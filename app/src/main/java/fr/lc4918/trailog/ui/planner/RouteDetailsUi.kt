package fr.lc4918.trailog.ui.planner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.domain.geo.RouteDetails
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.SurfaceKind
import fr.lc4918.trailog.domain.model.WayKind
import fr.lc4918.trailog.ui.profile.ElevationProfile
import fr.lc4918.trailog.ui.profile.TitleInfo
import fr.lc4918.trailog.ui.profile.TrackInfoColumns
import fr.lc4918.trailog.ui.profile.cursorInfos
import fr.lc4918.trailog.ui.profile.profileChartHeight
import fr.lc4918.trailog.ui.profile.routeInfos
import fr.lc4918.trailog.ui.routes.SlopeLegendInfo
import fr.lc4918.trailog.ui.theme.Spacing
import kotlin.math.roundToInt

/** Bleu de la part revetue, le meme que la mise en evidence sur la carte (cf. RouteHighlightColor). */
private val PavedBlue = Color(0xFF2459C4)

/** Gris d'un revetement ou d'un type de voie inconnu. */
private val UnknownGrey = Color(0xFFD6D6D6)

/**
 * Couleur d'un revetement : des gris pour le revetu, du plus sombre (l'asphalte) au plus clair, et des
 * beiges et des bruns pour le reste, de la terre au gravier. Fixes, et non tirees du theme : la meme
 * couleur doit designer le meme revetement sur la barre et dans la liste, clair ou sombre.
 */
internal fun surfaceColor(k: SurfaceKind): Color = when (k) {
    SurfaceKind.ASPHALT -> Color(0xFF8A919C)
    SurfaceKind.PAVED -> Color(0xFFBEC3CA)
    SurfaceKind.CONCRETE -> Color(0xFFA5ABB3)
    SurfaceKind.SETT -> Color(0xFF9A8E86)
    SurfaceKind.WOOD_METAL -> Color(0xFF8E7A67)
    SurfaceKind.COMPACTED -> Color(0xFFC8BBA6)
    SurfaceKind.FINE_GRAVEL -> Color(0xFFBFB09A)
    SurfaceKind.GRAVEL -> Color(0xFFAE9B7F)
    SurfaceKind.UNPAVED -> Color(0xFFD3C6B1)
    SurfaceKind.GROUND -> Color(0xFF9E7B55)
    SurfaceKind.GRASS -> Color(0xFF8DAF6E)
    SurfaceKind.SAND -> Color(0xFFE0CA92)
    SurfaceKind.ROCK -> Color(0xFF7F7466)
    SurfaceKind.UNKNOWN -> UnknownGrey
}

/** Couleur d'un type de voie : des gris pour les routes tranquilles, du jaune et du rouge pour celles
 *  qu'on partage avec les voitures, du vert d'eau pour les pistes cyclables, des bruns pour les chemins. */
internal fun wayColor(k: WayKind): Color = when (k) {
    WayKind.MAIN_ROAD -> Color(0xFFD9695F)
    WayKind.ROAD -> Color(0xFFE2C46C)
    WayKind.MINOR_ROAD -> Color(0xFFA3AAB6)
    WayKind.STREET -> Color(0xFFC3C9D2)
    WayKind.PEDESTRIAN -> Color(0xFFB7A6C9)
    WayKind.SERVICE -> Color(0xFFD2D6DC)
    WayKind.CYCLEWAY -> Color(0xFF6FA8A0)
    WayKind.TRACK -> Color(0xFFB59B6E)
    WayKind.PATH -> Color(0xFF8C6A45)
    WayKind.FOOTWAY -> Color(0xFFA88C6C)
    WayKind.STEPS -> Color(0xFF6E5B4A)
    WayKind.FERRY -> Color(0xFF5B8DD6)
    WayKind.OTHER -> UnknownGrey
}

@Composable
internal fun surfaceLabel(k: SurfaceKind): String = stringResource(
    when (k) {
        SurfaceKind.ASPHALT -> R.string.surface_asphalt
        SurfaceKind.PAVED -> R.string.surface_paved
        SurfaceKind.CONCRETE -> R.string.surface_concrete
        SurfaceKind.SETT -> R.string.surface_sett
        SurfaceKind.WOOD_METAL -> R.string.surface_wood_metal
        SurfaceKind.COMPACTED -> R.string.surface_compacted
        SurfaceKind.FINE_GRAVEL -> R.string.surface_fine_gravel
        SurfaceKind.GRAVEL -> R.string.surface_gravel
        SurfaceKind.UNPAVED -> R.string.surface_unpaved
        SurfaceKind.GROUND -> R.string.surface_ground
        SurfaceKind.GRASS -> R.string.surface_grass
        SurfaceKind.SAND -> R.string.surface_sand
        SurfaceKind.ROCK -> R.string.surface_rock
        SurfaceKind.UNKNOWN -> R.string.surface_unknown
    }
)

@Composable
internal fun wayLabel(k: WayKind): String = stringResource(
    when (k) {
        WayKind.MAIN_ROAD -> R.string.way_main_road
        WayKind.ROAD -> R.string.way_road
        WayKind.MINOR_ROAD -> R.string.way_minor_road
        WayKind.STREET -> R.string.way_street
        WayKind.PEDESTRIAN -> R.string.way_pedestrian
        WayKind.SERVICE -> R.string.way_service
        WayKind.CYCLEWAY -> R.string.way_cycleway
        WayKind.TRACK -> R.string.way_track
        WayKind.PATH -> R.string.way_path
        WayKind.FOOTWAY -> R.string.way_footway
        WayKind.STEPS -> R.string.way_steps
        WayKind.FERRY -> R.string.way_ferry
        WayKind.OTHER -> R.string.way_other
    }
)

/** Une part en pourcentage entier. Une part minuscule mais presente se dit "< 1 %", et non "0 %". */
internal fun percentOf(fraction: Double): String {
    val p = (fraction * 100).roundToInt()
    return if (p == 0 && fraction > 0) "< 1 %" else "$p %"
}

/** Le libelle et la couleur d'une categorie, quelle qu'en soit la famille. */
@Composable
private fun labelOf(kind: Any?): String = when (kind) {
    is SurfaceKind -> surfaceLabel(kind)
    is WayKind -> wayLabel(kind)
    else -> ""
}

private fun colorOf(kind: Any?): Color = when (kind) {
    is SurfaceKind -> surfaceColor(kind)
    is WayKind -> wayColor(kind)
    else -> UnknownGrey
}

/**
 * La zone "Details" de la bande : les rubriques "Surfaces" et "Types de voies", chacune une barre des parts
 * et la liste des categories avec leur longueur et leur pourcentage.
 *
 * Un appui sur une rubrique ouvre son VIEWER (cf. [onOpen]), la plus longue de ses categories mise en
 * evidence d'emblee sur la carte.
 *
 * Sans attributs de voies - un parcours calcule par Valhalla, qui n'en rend pas -, la zone le dit plutot
 * que de montrer un trajet "100 % inconnu".
 */
@Composable
internal fun DetailsZone(
    done: RouteState.Done,
    imperial: Boolean,
    onOpen: (PlannerViewer, Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (done.segments.isEmpty()) {
        Text(stringResource(R.string.planner_details_unavailable), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(vertical = Spacing.s))
        return
    }
    val surfaces = remember(done) { RouteDetails.surfaces(done.segments) }
    val paved = remember(done) { RouteDetails.paved(done.segments) }
    val ways = remember(done) { RouteDetails.ways(done.segments) }
    val estime = remember(done) { RouteDetails.estimatedFraction(done.segments) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Rubric(
            title = stringResource(R.string.planner_details_surfaces),
            onClick = { onOpen(PlannerViewer.SURFACES, surfaces.firstOrNull()?.kind) },
            modifier = Modifier.testTag("planner_details_surfaces"),
        ) {
            PavedSummary(paved)
            ShareBar(surfaces.map { surfaceColor(it.kind) to it.fraction }, Modifier.padding(top = Spacing.s))
            surfaces.forEach { LegendRow(surfaceColor(it.kind), surfaceLabel(it.kind), it.meters, it.fraction, imperial) }
            // Estime n'est pas mesure : on dit quelle part des chiffres vient du type de voie (cf.
            // RouteDetails.estimatedSurfaceOf), plutot que de la presenter comme relevee.
            if (estime > 0.0) {
                Text(stringResource(R.string.planner_details_estimated, percentOf(estime)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs).testTag("planner_details_estimated"))
            }
        }
        Rubric(
            title = stringResource(R.string.planner_details_ways),
            onClick = { onOpen(PlannerViewer.WAYS, ways.firstOrNull()?.kind) },
            modifier = Modifier.testTag("planner_details_ways"),
        ) {
            ShareBar(ways.map { wayColor(it.kind) to it.fraction })
            ways.forEach { LegendRow(wayColor(it.kind), wayLabel(it.kind), it.meters, it.fraction, imperial) }
        }
    }
}

/** Une rubrique des details : son titre, puis son contenu, le tout sensible a l'appui. */
@Composable
private fun Rubric(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = Spacing.xs))
        content()
    }
}

/**
 * Revetu contre non revetu, en tete des surfaces : une barre bleue, pleine pour le revetu et hachuree pour
 * le reste, puis les deux pourcentages. L'inconnu, s'il y en a, en gris a la suite.
 */
@Composable
private fun PavedSummary(paved: List<RouteDetails.Share<Boolean?>>) {
    val parts = paved.map { it.kind to it.fraction }
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        var x = 0f
        val gap = 2.dp.toPx()
        parts.forEach { (kind, f) ->
            val w = (size.width * f.toFloat() - gap).coerceAtLeast(1f)
            val r = CornerRadius(size.height / 2)
            when (kind) {
                true -> drawRoundRect(PavedBlue, Offset(x, 0f), Size(w, size.height), r)
                false -> hatched(PavedBlue, x, w)
                null -> drawRoundRect(UnknownGrey, Offset(x, 0f), Size(w, size.height), r)
            }
            x += w + gap
        }
    }
    Row(Modifier.padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        parts.forEach { (kind, f) ->
            val label = stringResource(
                when (kind) {
                    true -> R.string.planner_details_paved
                    false -> R.string.planner_details_unpaved
                    null -> R.string.planner_details_unknown
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(width = 16.dp, height = 8.dp)) {
                    when (kind) {
                        true -> drawRoundRect(PavedBlue, cornerRadius = CornerRadius(size.height / 2))
                        false -> hatched(PavedBlue, 0f, size.width)
                        null -> drawRoundRect(UnknownGrey, cornerRadius = CornerRadius(size.height / 2))
                    }
                }
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(percentOf(f), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Des barreaux verticaux serres, sur [w] a partir de [x] : le "non revetu" de la synthese. */
private fun DrawScope.hatched(color: Color, x: Float, w: Float) {
    val pas = 4.dp.toPx()
    val trait = 2.5.dp.toPx()
    clipRect(left = x, right = x + w) {
        var xi = x
        while (xi < x + w) {
            drawRoundRect(color, Offset(xi, 0f), Size(trait, size.height), CornerRadius(1.dp.toPx()))
            xi += pas
        }
    }
}

/**
 * Une barre des parts, les categories cote a cote dans l'ordre donne, separees d'un filet. Avec
 * [cursorFraction], un curseur la traverse a cette fraction de sa largeur.
 */
@Composable
private fun ShareBar(
    parts: List<Pair<Color, Double>>,
    modifier: Modifier = Modifier,
    thickness: Dp = 10.dp,
    height: Dp = thickness,
    cursorFraction: Float? = null,
    cursorColor: Color = Color.Unspecified,
    cursorFill: Color = Color.White,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val t = thickness.toPx()
        val y = (size.height - t) / 2
        val gap = 2.dp.toPx()
        var x = 0f
        parts.forEach { (c, f) ->
            val w = (size.width * f.toFloat() - gap).coerceAtLeast(1f)
            drawRoundRect(c, Offset(x, y), Size(w, t), CornerRadius(t / 2))
            x += w + gap
        }
        if (cursorFraction != null) {
            val cx = (size.width * cursorFraction).coerceIn(0f, size.width)
            drawLine(cursorColor, Offset(cx, 0f), Offset(cx, size.height), 2.dp.toPx())
            drawCircle(cursorColor, 8.dp.toPx(), Offset(cx, size.height / 2))
            drawCircle(cursorFill, 6.dp.toPx(), Offset(cx, size.height / 2))
            drawCircle(cursorColor, 3.dp.toPx(), Offset(cx, size.height / 2))
        }
    }
}

/** Une ligne de legende : la pastille, la categorie, sa longueur, et sa part du trajet. */
@Composable
private fun LegendRow(color: Color, label: String, meters: Double, fraction: Double, imperial: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).background(color, MaterialTheme.shapes.extraSmall))
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.m).weight(1f))
        Text(Format.shortDistance(meters, imperial), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(percentOf(fraction), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 52.dp).padding(start = Spacing.s), maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** Hauteur maximale du profil dans le panneau VIEWER : un peu plus que dans la bande, il y est seul. */
private val ViewerChartMaxHeight = 150.dp

/**
 * Le panneau d'un affichage VIEWER, qui remplace la bande au bas de l'ecran : un en-tete - le retour, le
 * VIEWER en cours et son menu, la legende des pentes pour le profil -, puis le profil, ou la barre des
 * surfaces ou des types de voies.
 *
 * Une carte posee au-dessus de la carte, detachee des bords : on regarde le parcours, et le panneau n'en
 * cache que ce qu'il faut.
 */
@Composable
fun PlannerViewerPanel(
    state: RoutePlannerState,
    imperial: Boolean,
    settings: SettingsEntity,
    lastLabelInsetPx: Float,
    modifier: Modifier = Modifier,
) {
    val done = state.done ?: return
    val viewer = state.viewer ?: return
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.m).padding(bottom = Spacing.m)
            .shadow(8.dp, shape, clip = false).testTag("planner_viewer"),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.s, bottom = Spacing.l)) {
            ViewerHeader(state, viewer, done, settings)
            when (viewer) {
                PlannerViewer.PROFILE -> ProfileViewer(state, done, imperial, settings, lastLabelInsetPx)
                PlannerViewer.SURFACES -> {
                    val shares = remember(done) { RouteDetails.surfaces(done.segments) }
                    ShareViewer(shares, state.highlight as? SurfaceKind, imperial) { state.select(it) }
                }
                PlannerViewer.WAYS -> {
                    val shares = remember(done) { RouteDetails.ways(done.segments) }
                    ShareViewer(shares, state.highlight as? WayKind, imperial) { state.select(it) }
                }
            }
        }
    }
}

/** En-tete du VIEWER : retour a la bande, titre et menu des trois VIEWER, et le "i" des pentes. */
@Composable
private fun ViewerHeader(state: RoutePlannerState, viewer: PlannerViewer, done: RouteState.Done, settings: SettingsEntity) {
    var menu by remember { mutableStateOf(false) }
    fun titre(v: PlannerViewer) = when (v) {
        PlannerViewer.PROFILE -> R.string.planner_viewer_elevation
        PlannerViewer.SURFACES -> R.string.planner_details_surfaces
        PlannerViewer.WAYS -> R.string.planner_details_ways
    }
    // Les VIEWER des details n'ont rien a montrer sans attributs de voies : le menu ne les propose pas.
    val offerts = if (done.segments.isEmpty()) listOf(PlannerViewer.PROFILE) else PlannerViewer.entries
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.closeViewer() }, modifier = Modifier.size(40.dp).testTag("planner_viewer_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), Modifier.size(22.dp))
            }
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier.clip(MaterialTheme.shapes.small).clickable(enabled = offerts.size > 1) { menu = true }
                        .padding(horizontal = Spacing.s, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(stringResource(titre(viewer)), style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (offerts.size > 1) Icon(Icons.Filled.ExpandMore, null, Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
                    shape = MaterialTheme.shapes.medium, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    offerts.forEach { v ->
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(titre(v)), style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (v == viewer) FontWeight.SemiBold else FontWeight.Normal)
                            },
                            onClick = {
                                menu = false
                                state.openViewer(v, when (v) {
                                    PlannerViewer.PROFILE -> null
                                    PlannerViewer.SURFACES -> RouteDetails.surfaces(done.segments).firstOrNull()?.kind
                                    PlannerViewer.WAYS -> RouteDetails.ways(done.segments).firstOrNull()?.kind
                                })
                            },
                        )
                    }
                }
            }
            if (viewer == PlannerViewer.PROFILE && state.zoomed) {
                IconButton(onClick = { state.resetZoom() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Fullscreen, stringResource(R.string.planner_zoom_out),
                        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (viewer == PlannerViewer.PROFILE && settings.routeSlopeLine) SlopeLegendInfo(settings)
        }
    }
}

/**
 * Le profil en grand : au-dessus, ce que dit le point sous le doigt - distance, altitude, pente, et
 * l'horaire estime pour l'atteindre -, ou les totaux du parcours tant qu'aucun point n'est designe.
 */
@Composable
private fun ProfileViewer(
    state: RoutePlannerState, r: RouteState.Done, imperial: Boolean, settings: SettingsEntity,
    lastLabelInsetPx: Float,
) {
    val all = r.track.samples
    val zoom = state.zoomRange
    val samples = remember(r.track, zoom) {
        if (zoom != null && zoom.last < all.size) all.subList(zoom.first, zoom.last + 1) else all
    }
    val stats = remember(r.track, zoom, samples) { if (zoom != null) TrackMath.statsOf(samples) else r.track.stats }
    val point = state.cursor?.let { TrackMath.sampleAt(all, it) }
    val infos = if (point != null) {
        // L'horaire au prorata de la distance, comme la duree d'une portion zoomee : le moteur ne la rend
        // que pour le trajet entier, d'ou le "~".
        val total = all.last().x
        val secondes = if (total > 0) r.seconds * point.x / total else 0.0
        cursorInfos(point, "dist,ele,slope", imperial) + TitleInfo("dur",
            stringResource(R.string.chip_duration), stringResource(R.string.info_name_duration),
            "~" + Format.duration(secondes))
    } else routeInfos(stats, r.seconds, false, imperial)
    TrackInfoColumns(infos, fontSp = settings.profBarFont, bold = settings.profBarBold,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs))
    if (samples.size < 2) return
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
        val hauteur = profileChartHeight(
            settings.profileVerticalScale, stats.min, stats.max, samples.last().x - samples.first().x,
            constraints.maxWidth, ViewerChartMaxHeight, settings.profAxisFont,
        )
        Box(Modifier.fillMaxWidth().height(hauteur), contentAlignment = Alignment.Center) {
            ElevationProfile(
                samples = samples, stats = stats,
                grid = settings.profileGrid,
                slope = settings.routeSlopeLine,
                lineColor = MaterialTheme.colorScheme.primary,
                axisFontSp = settings.profAxisFont,
                axisBold = settings.profAxisBold,
                cursorX = state.cursor,
                onScrub = { state.tapProfile(it) },
                onZoom = { scale, fraction -> state.zoomBy(scale, fraction, all.size) },
                onDoubleTap = { fraction -> state.zoomBy(2f, fraction, all.size) },
                lastLabelInsetPx = lastLabelInsetPx,
                verticalScale = settings.profileVerticalScale,
                modifier = Modifier.fillMaxWidth().fillMaxHeight().testTag("planner_viewer_profile"),
            )
        }
    }
}

/**
 * Le VIEWER des surfaces ou des types de voies : la categorie mise en evidence et son menu, puis la barre
 * des parts qu'un curseur parcourt.
 *
 * Toucher la barre, ou y glisser le doigt, choisit la categorie qui se trouve dessous ; le menu fait de
 * meme, le curseur se posant alors au milieu de la categorie choisie. Dans les deux cas, la carte la met
 * en evidence (cf. PlannerEffects).
 */
@Composable
private fun <K> ShareViewer(
    shares: List<RouteDetails.Share<K>>,
    selected: K?,
    imperial: Boolean,
    onSelect: (K) -> Unit,
) {
    if (shares.isEmpty()) return
    var menu by remember { mutableStateOf(false) }
    // Ou le doigt a pose le curseur, tant qu'il reste dans la categorie choisie ; sinon son milieu.
    var doigt by remember { mutableStateOf<Float?>(null) }
    val choisie = shares.firstOrNull { it.kind == selected }
    val debut = shares.takeWhile { it.kind != selected }.sumOf { it.fraction }.toFloat()
    val fin = debut + (choisie?.fraction?.toFloat() ?: 0f)
    val curseur = doigt?.takeIf { choisie != null && it in debut..fin } ?: ((debut + fin) / 2)
    val select by rememberUpdatedState<(Float) -> Unit> { f ->
        doigt = f
        RouteDetails.kindAt(shares, f.toDouble())?.let(onSelect)
    }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(MaterialTheme.shapes.small)
                .clickable { menu = true }.padding(horizontal = Spacing.s).testTag("planner_viewer_kind"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Box(Modifier.size(width = 22.dp, height = 10.dp).background(colorOf(selected), MaterialTheme.shapes.extraSmall))
            Text(labelOf(selected), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (choisie != null) {
                Text("${Format.shortDistance(choisie.meters, imperial)} - ${percentOf(choisie.fraction)}",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
            Icon(Icons.Filled.ExpandMore, null, Modifier.size(20.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
            shape = MaterialTheme.shapes.medium, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            shares.forEach { s ->
                DropdownMenuItem(
                    leadingIcon = {
                        Box(Modifier.size(width = 20.dp, height = 8.dp).background(colorOf(s.kind), MaterialTheme.shapes.extraSmall))
                    },
                    text = {
                        Row(Modifier.widthIn(min = 200.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(labelOf(s.kind), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (s.kind == selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f))
                            Text(Format.shortDistance(s.meters, imperial), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.m))
                        }
                    },
                    onClick = { menu = false; doigt = null; onSelect(s.kind) },
                    modifier = if (s.kind == selected) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        else Modifier,
                )
            }
        }
    }
    ShareBar(
        shares.map { colorOf(it.kind) to it.fraction },
        modifier = Modifier.padding(top = Spacing.m, start = Spacing.s, end = Spacing.s)
            .pointerInput(Unit) {
                detectTapGestures { select((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { ch, _ ->
                    ch.consume()
                    select((ch.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .testTag("planner_viewer_bar"),
        thickness = 14.dp,
        height = 72.dp,
        cursorFraction = curseur,
        cursorColor = MaterialTheme.colorScheme.onSurface,
        cursorFill = MaterialTheme.colorScheme.surface,
    )
}
