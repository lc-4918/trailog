package fr.lc4918.trailog.ui.routes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.geo.RouteDetails
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.LayerWays
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.SurfaceKind
import fr.lc4918.trailog.domain.model.WayKind
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.planner.PlannerViewer
import fr.lc4918.trailog.ui.planner.ProfileViewerContent
import fr.lc4918.trailog.ui.planner.WaysViewerPanel
import fr.lc4918.trailog.ui.profile.ProfileZoom
import fr.lc4918.trailog.ui.profile.cursorInfos
import fr.lc4918.trailog.ui.profile.titleInfos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Le VIEWER d'une trace de la BIBLIOTHEQUE, ouvert depuis ses statistiques - profil, surfaces ou types de
 * voies : le meme affichage que celui d'un itineraire calcule (cf. RoutePlannerState.viewer), pose sur une
 * couche au lieu du parcours du planificateur.
 */
class LayerWaysViewerState {
    /** La couche montree, ou null : le VIEWER est ferme. */
    var layer by mutableStateOf<LayerEntity?>(null)
        private set
    /** Ses voies, nulles tant qu'elles n'ont pas ete retrouvees : seul le profil est alors propose. */
    var ways by mutableStateOf<LayerWays?>(null)
        private set
    /** Le profil de chaque ligne de la couche, dans l'ordre des lignes. */
    var tracks by mutableStateOf<List<ComputedTrack>>(emptyList())
        private set
    var viewer by mutableStateOf(PlannerViewer.SURFACES)
        private set
    /** La categorie mise en evidence : un [SurfaceKind] ou un [WayKind], selon le VIEWER ; null, aucune. */
    var highlight by mutableStateOf<Any?>(null)
        private set
    /** Le point designe sur le profil, en metres depuis le debut de sa ligne ; null, aucun. */
    var cursor by mutableStateOf<Double?>(null)
        private set
    /** La portion du profil grossie, en indices d'echantillons ; null, tout le profil. */
    var zoomRange by mutableStateOf<IntRange?>(null)
        private set

    val isOpen: Boolean get() = layer != null

    val samples: List<List<Sample>> get() = tracks.map { it.samples }

    /**
     * La ligne dont on montre le profil : la plus longue. Une couche a plusieurs lignes n'a pas UN profil,
     * et mettre les leurs bout a bout inventerait un trajet qui saute d'une ligne a l'autre.
     */
    val profileTrack: ComputedTrack? get() = profileTrackOf(tracks)

    /** Les VIEWER qui ont quelque chose a montrer : le profil s'il y a des altitudes, les voies si on les a. */
    val offered: List<PlannerViewer> get() = buildList {
        val t = profileTrack
        if (t != null && t.hasZ && t.samples.size >= 2) add(PlannerViewer.PROFILE)
        if (ways != null) { add(PlannerViewer.SURFACES); add(PlannerViewer.WAYS) }
    }

    /**
     * Ouvre le VIEWER [v] sur [layer]. [initial] : la categorie a mettre en evidence pour les voies, le
     * point du profil (en metres) pour le profil - celui qu'on vient de toucher dans les statistiques.
     */
    fun open(layer: LayerEntity, ways: LayerWays?, tracks: List<ComputedTrack>, v: PlannerViewer, initial: Any?) {
        this.layer = layer
        this.ways = ways
        this.tracks = tracks
        zoomRange = null
        show(v, initial)
    }

    /** Passe d'un VIEWER a l'autre, sans quitter la couche. Le zoom du profil retombe, comme au planificateur. */
    fun switch(v: PlannerViewer, initial: Any?) {
        if (!isOpen) return
        if (v != viewer) zoomRange = null
        show(v, initial)
    }

    private fun show(v: PlannerViewer, initial: Any?) {
        // Un VIEWER sans rien a montrer (les voies pas encore retrouvees) cede la place au premier propose.
        viewer = if (v in offered) v else offered.firstOrNull() ?: v
        highlight = if (viewer == PlannerViewer.PROFILE) null else initial
        cursor = if (viewer == PlannerViewer.PROFILE) initial as? Double else null
    }

    fun select(kind: Any?) { highlight = kind }

    fun tapProfile(alongM: Double) { cursor = alongM }

    fun zoomBy(scale: Float, focusFraction: Float) {
        val total = profileTrack?.samples?.size ?: return
        val next = ProfileZoom.window(zoomRange, total, scale, focusFraction)
        if (next == zoomRange) return
        zoomRange = next
        cursor = null
    }

    fun resetZoom() {
        zoomRange = null
        cursor = null
    }

    fun close() {
        layer = null
        ways = null
        tracks = emptyList()
        highlight = null
        cursor = null
        zoomRange = null
    }
}

/** La ligne la plus longue d'une couche, celle dont on montre le profil ; null s'il n'y en a pas. */
internal fun profileTrackOf(tracks: List<ComputedTrack>): ComputedTrack? =
    tracks.maxByOrNull { it.samples.lastOrNull()?.x ?: 0.0 }

/**
 * Les morceaux de la couche a mettre en evidence pour la categorie [kind], en (lon, lat) : ligne par
 * ligne, les plages de la categorie (cf. RouteDetails.ranges) posees sur les echantillons de LA MEME
 * ligne. Une ligne sans echantillons ou sans voies ne donne rien.
 */
internal fun layerHighlightPieces(
    ways: LayerWays, samples: List<List<Sample>>, kind: Any?,
): List<List<Pair<Double, Double>>> {
    if (kind == null) return emptyList()
    return ways.lines.zip(samples).flatMap { (segments, s) ->
        val totalX = s.lastOrNull()?.x ?: 0.0
        val ranges = when (kind) {
            is SurfaceKind -> RouteDetails.ranges(segments, totalX, { RouteDetails.surfaceOf(it) }, kind)
            is WayKind -> RouteDetails.ranges(segments, totalX, { RouteDetails.wayOf(it) }, kind)
            else -> emptyList()
        }
        RouteDetails.pieces(s, ranges)
    }
}

/**
 * Le panneau du VIEWER au bas de la carte, et ce qu'il commande sur elle : la carte se cadre sur la couche
 * au-dessus de lui ; la categorie choisie s'y met en evidence, ou le point designe sur le profil s'y pose.
 * Le retour Android le referme, comme sa fleche.
 */
@Composable
internal fun BoxScope.LayerWaysViewerLayer(
    state: LayerWaysViewerState,
    controller: MapController,
    styleTick: Int,
    topPaddingPx: Int,
    imperial: Boolean,
    settings: SettingsEntity,
) {
    var hauteur by remember { mutableIntStateOf(0) }
    val layer = state.layer
    val ways = state.ways
    BackHandler(enabled = state.isOpen) { state.close() }
    /*
     * Cadrage sur toute la couche, au-dessus du panneau. Differe comme celui du planificateur, et pour la
     * meme raison : a l'ouverture, la hauteur du panneau n'est pas encore mesuree ; l'effet se relance a
     * chaque mesure, et le delai ne laisse passer que la derniere.
     */
    LaunchedEffect(layer?.id, hauteur) {
        val l = layer ?: return@LaunchedEffect
        if (hauteur == 0) return@LaunchedEffect
        delay(150)
        controller.fitTo(l.west, l.south, l.east, l.north, topPaddingPx = topPaddingPx, bottomPaddingPx = hauteur)
    }
    // Les voies seulement : dans le VIEWER du profil, c'est le curseur qui parle.
    LaunchedEffect(ways, state.tracks, state.highlight, state.viewer, styleTick) {
        val w = ways
        val pieces = if (w == null || state.viewer == PlannerViewer.PROFILE) emptyList()
            else withContext(Dispatchers.Default) { layerHighlightPieces(w, state.samples, state.highlight) }
        controller.setRouteHighlight(pieces, key = "layer")
    }
    /*
     * Le point du profil, sur la carte. Retire seulement quand il y en AVAIT un : le repere est partage avec
     * le profil d'une trace touchee sur la carte, et l'effacer a l'aveugle effacerait le sien.
     */
    var pose by remember { mutableStateOf(false) }
    LaunchedEffect(state.cursor, state.tracks, state.viewer) {
        val s = state.profileTrack?.samples
        val p = if (state.viewer == PlannerViewer.PROFILE && s != null) state.cursor?.let { TrackMath.sampleAt(s, it) } else null
        if (p != null) { controller.setCursor(p.lon, p.lat); pose = true }
        else if (pose) { controller.clearCursor(); pose = false }
    }
    LaunchedEffect(state.isOpen) {
        if (!state.isOpen) hauteur = 0
    }
    if (layer == null) return
    val couleur = remember(layer.color) {
        runCatching { Color(layer.color.toColorInt()) }.getOrNull()
    } ?: MaterialTheme.colorScheme.primary
    WaysViewerPanel(
        viewer = state.viewer,
        title = layer.name,
        segments = ways?.all.orEmpty(),
        highlight = state.highlight,
        imperial = imperial,
        offered = state.offered,
        onSwitch = { v, initial -> state.switch(v, initial) },
        onSelect = { state.select(it) },
        onBack = { state.close() },
        modifier = Modifier.align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .onGloballyPositioned { hauteur = it.size.height },
        headerActions = {
            if (state.viewer == PlannerViewer.PROFILE && state.zoomRange != null) {
                IconButton(onClick = { state.resetZoom() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Fullscreen, stringResource(R.string.planner_zoom_out),
                        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        profile = {
            val t = state.profileTrack
            if (t != null) ProfileViewerContent(
                all = t.samples, fullStats = t.stats, zoom = state.zoomRange, cursor = state.cursor,
                settings = settings,
                // Colorie par pente quand la couche l'est, comme le profil qu'on ouvre en touchant la trace.
                slope = layer.slopeColored,
                lineColor = couleur,
                infos = { point, stats ->
                    if (point != null) cursorInfos(point, "dist,ele,slope", imperial)
                    else titleInfos(stats, "dist,asc,desc", imperial)
                },
                onScrub = { state.tapProfile(it) },
                onZoom = { scale, fraction -> state.zoomBy(scale, fraction) },
                modifier = Modifier.testTag("layer_viewer_profile"),
            )
        },
    )
}

/**
 * Ce que montre le panneau du profil d'une trace touchee sur la carte : le profil ("Elevation"), les
 * surfaces ou les types de voies, et la categorie mise en evidence.
 *
 * Une valeur et non un etat mutable : le ViewModel la tient dans un flux, et chaque geste en rend une
 * nouvelle - ce qui se verifie sans ecran.
 */
data class TrackPanelView(
    val viewer: PlannerViewer = PlannerViewer.PROFILE,
    val highlight: Any? = null,
) {
    /** Le point courant et ses infos ne se montrent que sur le profil : ailleurs, ils ne designent rien. */
    val cursorShown: Boolean get() = viewer == PlannerViewer.PROFILE

    /** Passe au VIEWER [v], la plus longue categorie de [segments] mise en evidence. */
    fun showing(v: PlannerViewer, segments: List<fr.lc4918.trailog.domain.model.WaySegment>): TrackPanelView =
        TrackPanelView(v, fr.lc4918.trailog.ui.planner.initialHighlight(v, segments))

    fun selecting(kind: Any?): TrackPanelView = copy(highlight = kind)

    companion object {
        /** Une trace qu'on vient de toucher s'ouvre sur son profil, quoi qu'on regardait sur la precedente. */
        val Initial = TrackPanelView()
    }
}

/** Les voies de la ligne [index] d'une couche ; vide si la couche n'a pas cette ligne. */
internal fun lineWays(ways: LayerWays, index: Int): List<fr.lc4918.trailog.domain.model.WaySegment> =
    ways.lines.getOrNull(index).orEmpty()
