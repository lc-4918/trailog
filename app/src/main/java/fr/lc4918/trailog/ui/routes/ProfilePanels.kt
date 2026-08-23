package fr.lc4918.trailog.ui.routes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.geo.TrackMeasure
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.ui.geocode.GeocodeBubble
import fr.lc4918.trailog.ui.profile.ElevationProfile
import fr.lc4918.trailog.ui.profile.SlopeLegend
import fr.lc4918.trailog.ui.profile.TrackInfoColumns
import fr.lc4918.trailog.ui.profile.cursorInfos
import fr.lc4918.trailog.ui.profile.titleInfos

/**
 * Le profil altimetrique tel qu'il se pose dans l'ecran principal : le panneau de la trace active et
 * celui d'un parcours calcule, leur bouton de zoom, la ligne du restant, le bandeau d'altitude manquante.
 *
 * Le DESSIN du profil vit ailleurs (`ui/profile`, un Canvas qui ne connait que des echantillons) ; ici se
 * decide ce qui l'entoure a l'ecran, et ou tout cela se pose.
 */

/** Petit bouton carré (24 dp) des contrôles de zoom du profil (début/fin/expand). Contrairement à
 *  IconButton, sa couche d'état (ripple + fond bleu de sélection) est bornée à sa propre taille via clip :
 *  IconButton force une couche d'état de 40 dp qui déborderait de ce bouton réduit. */
@Composable
internal fun ProfileZoomButton(
    @androidx.annotation.DrawableRes iconRes: Int, contentDesc: String, active: Boolean, onClick: () -> Unit,
) {
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDesc, modifier = Modifier.size(16.dp),
            tint = if (active) Color.White else LocalContentColor.current)
    }
}

/* ----------------------- Légende ----------------------- */

/**
 * Ou l'on en est sur la trace affichee : ce qui reste a parcourir, et a monter.
 *
 * L'ecart a la trace n'est dit qu'au-dela de [OffTrackThresholdM] : sur la trace, il vaut la precision du
 * capteur et ne veut rien dire ; loin d'elle, il est la seule information qui compte - le "restant" ne
 * decrit alors plus le chemin qu'on suit.
 */
@Composable
internal fun RemainingOnTrackRow(
    projection: TrackMeasure.Projection, remaining: TrackMath.Remaining, imperial: Boolean, fontSp: Int,
) {
    val off = projection.awayM >= OffTrackThresholdM
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Place, null, Modifier.size((fontSp + 3).dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.track_remaining,
                Format.distance(remaining.distance, imperial), Format.elevation(remaining.ascent, imperial)),
            fontSize = fontSp.sp, fontWeight = FontWeight.Medium, color = Color.Black,
        )
        if (off) {
            Text(
                stringResource(R.string.track_off_track, Format.distance(projection.awayM, imperial)),
                fontSize = (fontSp - 1).sp, color = MaterialTheme.colorScheme.error,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/** Au-dela de cet ecart, on ne suit plus la trace : c'est le moment de le dire. Cinquante metres passent
 *  la precision d'un GPS de telephone sous couvert forestier, sans attendre qu'on soit vraiment perdu. */
internal const val OffTrackThresholdM = 50.0

/** Air autour du titre du bandeau de profil : au-dessus comme au-dessous, dans tous les cas. */
internal val ProfileTitleGap = 4.dp

/** Air entre les infos de la trace et le graphique quand la legende des pentes n'est pas affichee :
 *  c'est elle qui tenait cette place, et elle en prenait plus que six points. */
internal val ProfileGraphGap = 12.dp

/** Ecart du bloc d'infos du point courant : le meme a droite de l'ecran qu'au-dessus du profil. */
internal val CursorInfoGap = 4.dp

/** Teintes de l'avertissement "sans altimétrie" : figées, pour rester lisibles sur le fond blanc du
 *  panneau de profil quel que soit le thème. */
internal val NoElevationBorder = Color(0xFFE8850C)

internal val NoElevationFill = Color(0xFFFFF3E0)

internal val NoElevationText = Color(0xFFB35309)

/** Avertissement affiché à la place du tracé quand la trace n'a aucune altitude : 80 % de la largeur et
 *  50 % de la hauteur de la zone de dessin, centré dedans. */
@Composable
internal fun NoElevationBanner(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.5f)
                .background(NoElevationFill, RoundedCornerShape(8.dp))
                .border(1.dp, NoElevationBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.profile_no_elevation), color = NoElevationText,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

/**
 * Panneau de profil d'un itinéraire mesuré depuis l'infobulle d'un lieu.
 *
 * Reprend la forme du profil d'une trace - mêmes réglages d'apparence, même légende, même graphique - pour
 * qu'une pente s'y lise de la même façon. Trois différences, toutes tenant à la nature de l'objet : pas de
 * spinner (l'itinéraire est calculé avant que le bouton n'apparaisse), pas de zoom A/B (un parcours court,
 * d'un seul tenant), et une croix de fermeture, car rien sur la carte ne permettrait de le rouvrir.
 *
 * Aucun garde-fou sur l'altimétrie : le bouton qui ouvre ce panneau n'existe que si le moteur a rendu des
 * altitudes (cf. GeocodeBubble).
 */
@Composable
internal fun RouteProfilePanel(
    track: ComputedTrack,
    title: String,
    settings: SettingsEntity,
    imperial: Boolean,
    lineColor: Color,
    cursorX: Double?,
    lastLabelInsetPx: Float,
    onScrub: (Double) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp).navigationBarsPadding(),
    ) {
        // Meme mise en page que le bandeau d'une trace : le titre sur sa ligne (la croix a son bout),
        // les infos en colonnes sur toute la largeur en dessous.
        Row(
            Modifier.padding(vertical = ProfileTitleGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = (settings.profTitleFont).sp,
                fontWeight = if (settings.profTitleBold) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(18.dp))
                }
            }
        }
        TrackInfoColumns(
            titleInfos(
                track.stats, settings.titleInfos, imperial,
                remember(track.samples) { TrackMath.toblerSeconds(track.samples) },
            ),
            fontSp = settings.profBarFont,
            bold = settings.profBarBold,
            modifier = Modifier.fillMaxWidth(),
        )
        if (settings.profileSlope && settings.profileSlopeLegend) {
            SlopeLegend(track.stats.maxAbsSlope, settings.profLegendFont,
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                bold = settings.profLegendBold)
        } else {
            Spacer(Modifier.height(ProfileGraphGap))
        }
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            ElevationProfile(
                samples = track.samples, stats = track.stats,
                grid = settings.profileGrid,
                slope = settings.profileSlope,
                lineColor = lineColor,
                axisFontSp = settings.profAxisFont,
                axisBold = settings.profAxisBold,
                cursorX = cursorX, onScrub = onScrub,
                lastLabelInsetPx = lastLabelInsetPx,
                verticalScaleMPerCm = settings.profileVerticalScaleMPerCm,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            )
        }
    }
}

/**
 * Le profil de la trace active, et les deux blocs qui l'accompagnent au bas de la carte.
 *
 * Trois choses posees dans la meme boite, parce qu'elles se lisent ensemble et se calent les unes sur les
 * autres : les infos du point courant en bas a droite, le bouton de retour a la vue complete en bas a
 * gauche, et le panneau lui-meme en bas au centre. Les deux premieres se decalent de la hauteur du
 * troisieme, qu'elles ne connaissent que parce qu'il la mesure et la rend (cf. [onHeightChange] : cette
 * hauteur sert aussi ailleurs, aux infobulles de la retouche et de la mesure).
 *
 * Le panneau est SUPERPOSE a la carte, qui garde toujours sa taille pleine : ne jamais la redimensionner
 * ici, une AndroidView de type SurfaceView flashe en noir le temps de son prochain frame quand elle est
 * redimensionnee pendant une animation.
 *
 * [computed] est le calcul courant et [lastComputed] le dernier connu : le second tient l'affichage
 * pendant l'animation de fermeture, quand le premier est deja retombe a null.
 */
@Composable
internal fun BoxScope.TrackProfileLayer(
    activeLayerId: Long?,
    computed: ComputedTrack?,
    lastComputed: ComputedTrack?,
    loading: Boolean,
    zoom: IntRange?,
    cursor: Double?,
    title: String,
    lineColor: Color,
    settings: SettingsEntity,
    imperial: Boolean,
    gpsActive: Boolean,
    userLocation: Pair<Double, Double>?,
    onHeightChange: (Int) -> Unit,
    onExpandZoom: () -> Unit,
    onToggleSlopeLegend: (Boolean) -> Unit,
    onScrub: (Double) -> Unit,
    onZoom: (scale: Float, fraction: Float) -> Unit,
    onDoubleTapZoom: (fraction: Float) -> Unit,
) {
    val density = LocalDensity.current
    val view = LocalView.current

    // Décalage du dernier label de l'axe X pour dégager l'angle arrondi bas-droit de l'écran. On calcule
    // l'intrusion réelle de l'arc À LA HAUTEUR du label (et non le rayon plein, qui n'est atteint que tout
    // en bas) : le label est remonté par la barre de navigation, l'angle y mord donc bien moins.
    //   - r = rayon de l'angle (px, API 31+, sinon 0 = écran plat)
    //   - dy = distance verticale du label au bord bas de l'écran (barre de nav + ~6 px entre la
    //     ligne de base du label et le bas du tracé)
    //   - intrusion = r - sqrt(r^2 - (r - dy)^2) tant que dy < r, sinon 0
    //   - on retranche le dégagement déjà présent (~10 dp : padding + marge interne)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val lastLabelInsetPx = remember(view, navBottomPx) {
        val r = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            view.rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
        else 0).toFloat()
        if (r <= 0f) 0f else {
            val dy = navBottomPx + 6f
            val intrusion = if (dy >= r) 0f else r - kotlin.math.sqrt(r * r - (r - dy) * (r - dy))
            (intrusion - with(density) { 10.dp.toPx() }).coerceAtLeast(0f)
        }
    }
    // profil à afficher : le calcul courant sinon le dernier connu (animation de fermeture) ; pendant un
    // chargement (tap/changement de trace) on n'affiche aucun graphique -> spinner.
    val shown = computed ?: if (!loading) lastComputed else null
    // Portion actuellement affichée (zoom A/B) : sous-liste de shown.samples, ou la trace complète si aucun
    // zoom. Le kilométrage (Sample.x) n'est jamais remis à zéro (cumulé depuis le début de la trace) ;
    // seules les infos (distance/D+/D- du bandeau titre) sont recalculées pour la seule portion visible
    // (cf. TrackMath.statsOf, réutilisable sur une sous-plage).
    //
    // Mémorisé sur (shown, zoom) : sinon subList()/statsOf() recréaient une liste d'identité différente à
    // CHAQUE recomposition (déplacement du curseur, etc.), invalidant le cache de rendu de ElevationProfile
    // (comparaison par référence) -> reconstruction de tous les chemins à chaque frame pendant un zoom
    // (jusqu'à ~2000 points), d'où une surcharge CPU.
    val windowSamples = remember(shown, zoom) {
        shown?.samples?.let { s ->
            if (zoom != null && zoom.last < s.size) s.subList(zoom.first, zoom.last + 1) else s
        }
    }
    val windowStats = remember(shown, zoom, windowSamples) {
        if (zoom != null && windowSamples != null) TrackMath.statsOf(windowSamples) else shown?.stats
    }
    // Le curseur est une abscisse absolue : la fenetre zoomee n'a plus rien a lui retrancher, et
    // l'echantillon qu'il designe s'obtient par interpolation (cf. TrackMath.sampleAt).
    val cursorSample = remember(cursor, windowSamples) {
        if (cursor == null || windowSamples == null) null
        else TrackMath.sampleAt(windowSamples, cursor)
            ?.takeIf { cursor >= windowSamples.first().x && cursor <= windowSamples.last().x }
    }
    // Hauteur mesuree du panneau : les deux blocs ci-dessous s'en decalent, et [onHeightChange] la rend a
    // l'ecran, ou les infobulles de la retouche et de la mesure s'en servent aussi.
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val panelBottomDp = with(density) { panelHeightPx.toDp() }

    // Infos du point courant : flottent au-dessus de la carte, juste au-dessus du titre du profil (décalées
    // de la hauteur mesurée du panneau, superposé à la carte).
    if (computed != null && cursorSample != null) {
        // Memes colonnes que les infos de la trace, en plus petit : c'est la meme lecture, sur un point
        // plutot que sur un parcours. A droite, ou le bouton de zoom se tenait : lui est seul et va a
        // gauche, ces infos-ci sont trois ou quatre et prennent la largeur.
        CompositionLocalProvider(LocalContentColor provides Color.Black) {
            TrackInfoColumns(
                cursorInfos(cursorSample, settings.cursorInfos, imperial),
                fontSp = settings.profCursorFont,
                bold = settings.profCursorBold,
                arrangement = Arrangement.spacedBy(14.dp),
                // Meme ecart a droite qu'entre le bas du bloc et le profil : le coin se lit alors comme un
                // coin, et non comme deux marges qui ne se repondent pas.
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = CursorInfoGap, bottom = panelBottomDp + CursorInfoGap)
                    .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
    // Bouton de zoom du profil : même hauteur que les infos du point ci-dessus, mais à gauche - elles
    // occupent désormais la droite.
    if (activeLayerId != null && shown != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = panelBottomDp + 4.dp)
                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp)),
        ) {
            // Seul bouton restant : le retour a la vue complete. Le zoom lui-meme se fait aux doigts sur le
            // graphique (ecartement ou double-tap), comme dans le planificateur.
            if (zoom != null) {
                ProfileZoomButton(R.drawable.ic_profile_zoom_expand,
                    stringResource(R.string.content_desc_profile_zoom_expand), active = false, onClick = onExpandZoom)
            }
        }
    }
    // Le panneau apparaît immédiatement (titre + spinner) ; le graphique le remplace une fois calculé.
    AnimatedVisibility(
        visible = activeLayerId != null, enter = expandVertically(), exit = shrinkVertically(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Column(
            Modifier.fillMaxWidth().background(Color.White)
                .padding(horizontal = 8.dp).navigationBarsPadding()
                .onGloballyPositioned { panelHeightPx = it.size.height; onHeightChange(it.size.height) },
        ) {
            // Le titre a sa ligne, les infos la leur : elles s'etalent alors sur toute la largeur, en
            // colonnes libellees, la ou les serrer a la suite du titre les reduisait a une file de valeurs
            // sans nom.
            //
            /*
             * Le "i" de la legende des pentes se pose en EXPOSANT au bout de la premiere ligne du titre, et
             * le titre lui reserve sa place.
             *
             * Il passait auparavant PAR-DESSUS le titre, qui filait dessous : un nom long se lisait alors
             * sous un rond blanc. Le titre s'ecrit desormais sur deux lignes au besoin, dans la largeur qui
             * reste - c'est-a-dire que la place du "i" est retiree a la colonne de texte, non prise au titre.
             */
            val legendShown = settings.profileSlope && settings.profileSlopeLegend
            val hasLegendButton = settings.profileSlope
            Box(Modifier.fillMaxWidth().padding(vertical = ProfileTitleGap)) {
                Text(title, fontSize = (settings.profTitleFont).sp,
                    fontWeight = if (settings.profTitleBold) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = if (hasLegendButton) SlopeLegendGutter else 0.dp))
                if (hasLegendButton) {
                    SlopeLegendButton(
                        shown = legendShown,
                        // En haut, non centre : sur un titre de deux lignes, un "i" centre tomberait entre
                        // les deux, ou il n'appartiendrait plus a aucune.
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) { onToggleSlopeLegend(!legendShown) }
                }
            }
            if (windowStats != null) {
                // Temps de marche estime, pour une trace qui n'a pas d'horodatage : calcule sur les
                // echantillons affiches, il suit donc le zoom du profil comme les autres totaux. Retenu tant
                // qu'ils ne changent pas - une recomposition ne doit pas relancer un balayage de deux mille
                // points.
                val tobler = remember(windowSamples) { windowSamples?.let { TrackMath.toblerSeconds(it) } }
                TrackInfoColumns(
                    titleInfos(windowStats, settings.titleInfos, imperial, tobler),
                    fontSp = settings.profBarFont,
                    bold = settings.profBarBold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Ou l'on en est SUR CETTE TRACE, capteur allume : ce qui reste a parcourir, et le denivele qui
            // reste a monter. C'est la seule ligne de l'application qui serve PENDANT la sortie et non avant
            // ou apres - d'ou sa place, sous les totaux du parcours entier, qu'elle vient nuancer.
            //
            // Calculee sur la trace COMPLETE et non sur la fenetre zoomee : la question est "combien me
            // reste-t-il jusqu'au bout", pas "jusqu'au bord du graphique".
            val whole = computed?.samples
            val onTrack = remember(userLocation, whole) {
                if (userLocation == null || whole.isNullOrEmpty()) null
                else TrackMeasure.project(whole, userLocation.second, userLocation.first)
                    ?.let { it to TrackMath.remaining(whole, it.alongM) }
            }
            if (gpsActive && onTrack != null && settings.profileRemaining) {
                RemainingOnTrackRow(
                    projection = onTrack.first, remaining = onTrack.second, imperial = imperial,
                    fontSp = settings.profBarFont,
                )
            }
            if (windowStats != null && legendShown) {
                SlopeLegend(windowStats.maxAbsSlope, settings.profLegendFont,
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    bold = settings.profLegendBold)
            } else {
                // Sans legende, les infos toucheraient le graphique : elle tenait lieu de respiration entre
                // les deux.
                Spacer(Modifier.height(ProfileGraphGap))
            }
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                // Trace sans altitude : le profil serait une ligne plate et muette. On le remplace par un
                // avertissement, plutôt que de laisser croire à un parcours plat.
                if (shown != null && !shown.hasZ && !loading) {
                    NoElevationBanner(Modifier.fillMaxSize())
                } else if (windowSamples != null && windowStats != null && !loading) {
                    ElevationProfile(
                        samples = windowSamples, stats = windowStats,
                        grid = settings.profileGrid,
                        slope = settings.profileSlope,
                        lineColor = if (lineColor != Color.Unspecified) lineColor else MaterialTheme.colorScheme.primary,
                        axisFontSp = settings.profAxisFont,
                        axisBold = settings.profAxisBold,
                        cursorX = cursor, onScrub = onScrub,
                        onZoom = onZoom,
                        onDoubleTap = onDoubleTapZoom,
                        lastLabelInsetPx = lastLabelInsetPx,
                        verticalScaleMPerCm = settings.profileVerticalScaleMPerCm,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
