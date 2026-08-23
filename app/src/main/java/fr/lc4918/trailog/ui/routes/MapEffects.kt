package fr.lc4918.trailog.ui.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.GpsMarkerStyle
import fr.lc4918.trailog.map.CoverageBounds
import fr.lc4918.trailog.map.CoverageProbe
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.RenderLayer
import fr.lc4918.trailog.ui.geocode.GeocodeSearchState
import fr.lc4918.trailog.ui.mappoint.MapPointState
import fr.lc4918.trailog.ui.measure.TrackMeasureState
import kotlinx.coroutines.delay

/**
 * Ce que l'ecran demande a la carte, et rien d'autre : des effets qui ne rendent aucun pixel, et dont le
 * seul travail est de reporter un etat de la composition sur le controleur MapLibre.
 *
 * **Pourquoi un fichier a part.** Ces effets etaient une quarantaine de lignes au milieu de `MainScreen`,
 * entre le selecteur d'images et les points d'interet, alors qu'ils ne parlent qu'a une seule chose et
 * partagent tous la meme dependance discrete : `styleTick`. Les lire a la suite, c'est lire l'inventaire
 * de ce que la carte porte.
 *
 * **styleTick, et pourquoi il est partout.** Changer de fond de carte RECONSTRUIT le style MapLibre, qui
 * emporte avec lui toutes les sources et couches posees dessus. Chaque effet doit donc pouvoir se rejouer
 * a l'identique : c'est ce que ce compteur declenche, incremente a chaque style pret (cf. `MapLibreView`).
 * Sans lui, un changement de fond effacerait silencieusement les traces, les epingles et le repere.
 *
 * Les marqueurs des points d'interet ne sont pas ici : ils vivent avec le reste de leur couche, qui leur
 * donne leur couleur et leur icone (cf. la section des points d'interet dans `MainScreen`).
 */
@Composable
internal fun MapOverlayEffects(
    controller: MapController,
    /** Compteur de styles prets : chaque increment fait tout reposer (cf. la documentation ci-dessus). */
    styleTick: Int,
    /** Hauteur d'une epingle a l'ecran, telle que les reglages la veulent. */
    markerPx: Float,
    renderLayers: List<RenderLayer>,
    bboxPoints: List<Pair<Double, Double>>,
    geo: GeocodeSearchState,
    measure: TrackMeasureState,
    mapPoint: MapPointState,
    gpsMarker: GpsMarkerStyle,
    gpsMarkerColor: String,
    gpsMarkerSizeDp: Float,
) {
    // les couches importees
    LaunchedEffect(renderLayers, styleTick, markerPx) {
        if (controller.style != null) controller.setLayers(renderLayers, markerPx)
    }
    // Coins/rectangle du tracé bbox hors-ligne (SPEC section 2) : source/couches dédiées (croix "viseur"),
    // indépendantes du système de couches importées ci-dessus.
    LaunchedEffect(bboxPoints, styleTick) {
        if (controller.style != null) controller.setBboxDraw(bboxPoints)
    }
    // Marqueur noir du géocodage : le lieu trouvé. Calque carte (comme le marqueur sélectionné) : il suit
    // seul le pan et le zoom.
    LaunchedEffect(geo.place, styleTick, markerPx) {
        controller.setGeocodeMarker(false, geo.place?.lon, geo.place?.lat, markerPx)
    }
    // Marqueurs noirs des deux bouts d'une mesure sur trace : mêmes épingles, même calque carte.
    LaunchedEffect(measure.markers, styleTick, markerPx) {
        controller.setMeasureMarkers(measure.markers, markerPx)
    }
    // Épingles noires du point désigné par un appui long et de son point de référence : mêmes épingles
    // encore, sur leur propre calque - les deux fonctions peuvent être à l'écran en même temps.
    LaunchedEffect(mapPoint.markers, styleTick, markerPx) {
        controller.setMapPointMarkers(mapPoint.markers, markerPx)
    }
    // Symbole du repère de position, tel que les réglages le décrivent. Rejoué sur styleTick : le repère est
    // reposé avec la dernière position connue, sans attendre que le capteur en donne une nouvelle.
    LaunchedEffect(gpsMarker, gpsMarkerColor, gpsMarkerSizeDp, styleTick) {
        controller.setUserMarker(gpsMarker, gpsMarkerColor, gpsMarkerSizeDp)
    }
}

/**
 * Ce que le profil altimetrique demande a la carte : le point courant, et le cadrage de la portion zoomee.
 *
 * Les deux seuls endroits ou un geste fait SOUS le graphique deplace ce qu'on voit AU-DESSUS. Ils sont donc
 * ici plutot qu'avec les couches : ce n'est pas de l'affichage entretenu, c'est une reponse a un geste.
 */
@Composable
internal fun ProfileCursorEffects(
    controller: MapController,
    cursor: Double?,
    computed: ComputedTrack?,
    profileZoom: IntRange?,
) {
    LaunchedEffect(cursor, computed) {
        val s = computed?.samples
        val p = if (cursor != null && s != null) TrackMath.sampleAt(s, cursor) else null
        if (p != null) {
            controller.setCursor(p.lon, p.lat)
            // Curseur sorti de l'ecran : la carte le rejoint. Deplacer le curseur sur le profil, c'est
            // demander a voir cet endroit-la ; le laisser hors champ rendrait le geste muet des qu'on
            // s'eloigne de la portion visible.
            if (!controller.isOnScreen(p.lon, p.lat)) controller.centerOn(p.lat, p.lon)
        } else {
            controller.clearCursor()
        }
    }
    // Synchronisation carte <-> zoom du profil : on recadre UNIQUEMENT sur l'emprise de la portion zoomée
    // (sélection A/B). Un simple tap sur une trace, sans zoom actif, ne déplace jamais la carte : on garde la
    // vue courante de l'utilisateur (pas de "zoom global" sur toute la trace, qui recadrait brutalement et
    // dont l'animation ralentissait l'affichage du premier profil). L'"expand" jusqu'à la vue complète laisse
    // donc la carte là où elle est. Fermer le profil ne redéclenche rien ici.
    LaunchedEffect(profileZoom, computed) {
        val range = profileZoom ?: return@LaunchedEffect
        val samples = computed?.samples ?: return@LaunchedEffect
        if (range.last >= samples.size) return@LaunchedEffect
        val sub = samples.subList(range.first, range.last + 1)
        controller.fitTo(sub.minOf { it.lon }, sub.minOf { it.lat }, sub.maxOf { it.lon }, sub.maxOf { it.lat })
    }
}

/**
 * Ou la carte se pose quand elle s'ouvre, et ce qu'elle fait si le fond choisi ne couvre pas cet endroit-la.
 *
 * Rend "la camera est posee". Ce n'est pas un detail d'implementation : tant que c'est faux, rien ne doit
 * ENREGISTRER la position de la camera - on sauvegarderait le cadrage provisoire d'avant le placement, et
 * l'ouverture suivante repartirait de la.
 *
 * Rendu comme un [State] et non comme un booleen : ce drapeau est lu depuis le rappel `onCameraIdle`, pose
 * une seule fois pour toute la vie de l'ecran. Un booleen y serait CAPTURE a faux et le resterait, et plus
 * aucun cadrage ne serait jamais enregistre.
 */
@Composable
internal fun rememberCameraPlacement(
    controller: MapController,
    styleTick: Int,
    settings: SettingsEntity,
    layers: List<LayerEntity>,
    renderLayers: List<RenderLayer>,
    providers: List<ProviderEntity>,
): State<Boolean> {
    // positionnement initial : dernier affichage si enregistré, sinon données visibles, sinon France
    val placed = remember { mutableStateOf(false) }
    var positioned by placed
    LaunchedEffect(styleTick, settings, renderLayers) {
        val st = settings ?: return@LaunchedEffect
        if (positioned || styleTick == 0) return@LaunchedEffect
        if (st.hasCamera) {
            controller.moveTo(st.lastLat, st.lastLon, st.lastZoom); positioned = true
        } else {
            val ls = layers.filter { it.visible }
            val w = ls.map { it.west }.filter { it != 0.0 }.minOrNull()
            val s = ls.map { it.south }.filter { it != 0.0 }.minOrNull()
            val e = ls.map { it.east }.filter { it != 0.0 }.maxOrNull()
            val n = ls.map { it.north }.filter { it != 0.0 }.maxOrNull()
            if (w != null && s != null && e != null && n != null) controller.fitTo(w, s, e, n)
            else controller.moveTo(46.6, 2.4, 4.8)   // centre France
            positioned = true
        }
    }

    // Activer un fond national alors que la carte regarde un autre pays ne montre rien : le service ne
    // sert pas cette zone, il ne reste que le gris de no-tile-background ou le blanc que renvoient les
    // WMS hors de chez eux. On recadre alors sur l'emprise du fond. Les 2 s laissent aux tuiles le temps
    // d'arriver sur une connexion lente : recadrer une carte qui allait s'afficher serait pire que ne
    // rien faire. Relancé sur styleTick, pas seulement sur l'identifiant : le style met un instant à
    // s'appliquer, et sonder avant que la caméra ne soit posée donnerait une emprise sans rapport.
    LaunchedEffect(settings.defaultBasemapId, styleTick) {
        if (styleTick == 0 || !positioned) return@LaunchedEffect
        val id = settings.defaultBasemapId
        val provider = providers.firstOrNull { it.id == id } ?: return@LaunchedEffect
        val bounds = CoverageBounds.of(provider) ?: return@LaunchedEffect
        delay(2_000)
        val viewport = controller.visibleBounds() ?: return@LaunchedEffect
        val zoom = controller.cameraState()?.third?.toInt() ?: return@LaunchedEffect
        if (CoverageProbe.probe(provider, viewport, zoom) != CoverageProbe.Coverage.EMPTY) return@LaunchedEffect
        controller.fitTo(bounds.west, bounds.south, bounds.east, bounds.north)
    }

    return placed
}
