package fr.lc4918.trailog.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.DefaultGpsMarkerSizeDp
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.GpsMarkerStyle
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.ui.profile.SlopeRamp
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.gestures.StandardScaleGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import kotlin.math.hypot
import kotlin.math.pow

data class RenderLayer(val key: String, val uri: String, val revision: Long, val color: String)

/** Elargissements successifs de la fenetre de recherche d'une trace autour du doigt (cf. lineKeysNear) :
 *  la tolerance des traces, puis 4 et 12 fois plus large - de quoi couvrir l'ecran sans le balayer. */
private val LineSearchFactors = listOf(1f, 4f, 12f)

/** Un point d'interet a poser sur la carte : ce que la couche a besoin d'en savoir, et rien de plus.
 *  [iconRes] est le pictogramme de sa categorie (cf. `ui/poi/PoiIcons.kt`), [colorHex] la teinte de son
 *  groupe. */
data class PoiMarker(
    val id: String, val lon: Double, val lat: Double, val colorHex: String, val iconRes: Int,
)

class MapController {
    var map: MapLibreMap? = null
    var style: Style? = null
    /** (cle de couche, id du marqueur, lon, lat). Les coordonnees viennent de la geometrie interrogee :
     *  elles permettent de placer l'infobulle des le tap, sans attendre le chargement de la couche. */
    var onPickPoint: ((String, String, Double, Double) -> Unit)? = null
    var onPickLine: ((String, Double, Double) -> Unit)? = null
    var onTapEmpty: (() -> Unit)? = null
    /** Tap sur un marqueur de point d'interet : recoit (identifiant, lon, lat). */
    var onPickPoi: ((String, Double, Double) -> Unit)? = null
    /** Appui long sur un endroit quelconque de la carte - hors trace et hors marqueur : reçoit (lon, lat).
     *  Un appui long qui tombe sur une trace ou un marqueur ne l'appelle pas : ceux-là se décrivent déjà
     *  eux-mêmes, par leur profil et leur infobulle. */
    var onLongPressEmpty: ((Double, Double) -> Unit)? = null
    /** Si défini, intercepte tout tap sur la carte AVANT le test de sélection point/ligne habituel
     *  (mode de saisie exclusif, ex. tracé de bounding box hors-ligne) : reçoit (lon, lat). */
    var onRawTap: ((Double, Double) -> Unit)? = null
    var onCameraIdle: (() -> Unit)? = null
    var onCameraMove: (() -> Unit)? = null
    var onUserMoveBegin: (() -> Unit)? = null
    var onStyleApplied: (() -> Unit)? = null
    // Tolerance de tap, en dp, autour du doigt. Distincte pour les marqueurs et pour les traces : les
    // marqueurs sont interroges en premier et l'emportent, une tolerance large sur eux rend une trace qui
    // passe a cote difficile a atteindre.
    var tapToleranceDp: Int = 10
    var lineTapToleranceDp: Int = 16
    private var density: Float = 2f
    private var appContext: Context? = null

    /** Geste de rotation à 2 doigts (réglage utilisateur) ; appliqué immédiatement si la carte est prête. */
    var rotateGesturesEnabled: Boolean = false
        set(value) { field = value; map?.uiSettings?.isRotateGesturesEnabled = value }

    // une couche peut contenir points ET lignes : une seule source, deux style layers filtrés par géométrie.
    private val layerKeys = linkedSetOf<String>()
    // Derniere (revision, couleur) reellement appliquee a MapLibre par cle : evite de recharger une source
    // dont le fichier .map n'a pas change. La geometrie est chargee par MapLibre depuis une URI file:// sur
    // son propre thread de travail, jamais conservee en String cote JVM (revision = horodatage ^ taille).
    private val applied = HashMap<String, Pair<Long, String>>()
    private val pinImages = hashSetOf<String>()
    private val poiImages = hashSetOf<String>()
    private val gpsImages = hashSetOf<String>()

    // ---- repere de position GPS (cf. setUserMarker) ----
    private var userMarker = GpsMarkerStyle.DOT
    private var userMarkerColor = GpsMarkerStyle.DOT.defaultColor
    private var userMarkerSizeDp = DefaultGpsMarkerSizeDp.toFloat()
    /** Derniere orientation connue du telephone (degres, nord vrai), ou null tant que rien n'a ete mesure. */
    private var userHeading: Float? = null
    /** Derniere position recue : (lon, lat, precision en metres). */
    private var lastUserFix: Triple<Double, Double, Float>? = null
    /** Description du symbole reellement pose sur la carte, pour ne le refaire que s'il change. */
    private var appliedUserMarker: String? = null

    // style en attente / appliqué (évite le rechargement à chaque recomposition)
    private var desiredJson: String? = null
    private var desiredUrl: String? = null
    private var desiredKey: String? = null
    private var appliedKey: String? = null

    fun attachDensity(d: Float) { density = d }
    fun attachContext(c: Context) {
        appContext = c.applicationContext
        touchSlopPx = ViewConfiguration.get(c).scaledTouchSlop.toFloat()
    }

    // ---- tap rapide (voir handleFastTap) : etat du geste en cours, alimente par onMapTouch ----
    private var downX = 0f
    private var downY = 0f
    private var multiTouch = false
    private var fastPicked = false
    private var touchSlopPx = 24f

    /** Evenements tactiles bruts de la MapView, observes sans etre consommes : MapLibre continue de gerer
     *  pan / fling / double-tap-zoom / long press normalement, on se contente d'un signal plus precoce. */
    fun onMapTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; multiTouch = false; fastPicked = false }
            MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
            MotionEvent.ACTION_UP -> {
                val moved = hypot((e.x - downX).toDouble(), (e.y - downY).toDouble())
                val heldMs = e.eventTime - e.downTime
                if (!multiTouch && moved < touchSlopPx && heldMs < ViewConfiguration.getLongPressTimeout()) {
                    handleFastTap(PointF(e.x, e.y))
                }
            }
        }
    }

    /**
     * Selection d'un marqueur des le lever du doigt, sans attendre onMapClick.
     *
     * onMapClick = onSingleTapConfirmed : Android arme un message differe de
     * ViewConfiguration.getDoubleTapTimeout() (300 ms) des l'ACTION_DOWN et ne livre le clic qu'a son
     * expiration, le temps d'ecarter l'hypothese d'un double-tap. C'est un plancher incompressible sur ce
     * chemin, mesure a ~306 ms, soit l'essentiel de la latence percue a l'ouverture d'une infobulle.
     *
     * Volontairement limite aux MARQUEURS : un double-tap pour zoomer ne doit pas ouvrir le profil d'une
     * trace ni fermer l'infobulle en cours, donc les taps sur une ligne et dans le vide restent sur le
     * chemin confirme, au comportement inchange.
     * Contrepartie assumee : un double-tap pile sur un marqueur ouvre son infobulle en plus de zoomer.
     */
    private fun handleFastTap(screen: PointF) {
        if (onRawTap != null) return          // mode de saisie exclusif (bbox hors-ligne) : pas de selection ici
        val hit = markerPickAt(screen) ?: return
        fastPicked = true
        hit()
    }

    /** Action de selection du marqueur sous [screen], ou null si aucun n'y est. */
    private fun markerPickAt(screen: PointF): (() -> Unit)? {
        val m = map ?: return null
        val tol = tapToleranceDp * density
        val rect = RectF(screen.x - tol, screen.y - tol, screen.x + tol, screen.y + tol)
        // Les points d'interet d'abord, parce qu'ils sont dessines par-dessus : sous le doigt, c'est celui
        // qu'on voit qui doit repondre. Ils ne sont la que si la couche est allumee, donc rien ne change
        // pour qui ne s'en sert pas.
        if (style?.getLayer(POI_LAYER) != null) {
            m.queryRenderedFeatures(rect, POI_LAYER).firstOrNull()?.let { f ->
                val id = f.getStringProperty("__id")
                val g = f.geometry() as? org.maplibre.geojson.Point
                if (id != null && g != null) return { onPickPoi?.invoke(id, g.longitude(), g.latitude()) }
            }
        }
        for (k in layerKeys) {
            val hit = m.queryRenderedFeatures(rect, pointLayerId(k)).firstOrNull()?.let { pickOf(k, it) }
            if (hit != null) return hit
        }
        return null
    }

    /** Cle de la couche dont une ligne passe sous [screen], ou null. Tolerance propre aux traces, plus
     *  large que celle des marqueurs (cf. [lineTapToleranceDp]). */
    private fun lineKeyAt(screen: PointF): String? {
        val m = map ?: return null
        val tol = lineTapToleranceDp * density
        val rect = RectF(screen.x - tol, screen.y - tol, screen.x + tol, screen.y + tol)
        return layerKeys.firstOrNull { m.queryRenderedFeatures(rect, lineLayerId(it)).isNotEmpty() }
    }

    /**
     * Cles des couches dont une ligne passe pres de (lon, lat) : les candidates a un point de mesure.
     *
     * Rabattre un tap sur une trace demande le profil precalcule de la couche, soit un fichier lu et
     * decode : les passer TOUTES en revue coutait plusieurs secondes des qu'une carte en portait beaucoup.
     * L'index de rendu de MapLibre repond, lui, dans l'instant, et n'interroge que ce qui est reellement
     * dessine autour du doigt.
     *
     * La fenetre s'elargit tant qu'elle ne trouve rien : un tap dans le vide continue de se poser sur la
     * trace d'a cote, comme un tap qui depasse un bout de trace se pose sur ce bout. Elle finit par
     * renoncer - loin de toute trace, il n'y a rien a mesurer, et le tap reste sans effet.
     */
    fun lineKeysNear(lon: Double, lat: Double): List<String> {
        val m = map ?: return emptyList()
        val screen = m.projection.toScreenLocation(LatLng(lat, lon))
        for (factor in LineSearchFactors) {
            val tol = lineTapToleranceDp * density * factor
            val rect = RectF(screen.x - tol, screen.y - tol, screen.x + tol, screen.y + tol)
            val keys = layerKeys.filter { m.queryRenderedFeatures(rect, lineLayerId(it)).isNotEmpty() }
            if (keys.isNotEmpty()) return keys
        }
        return emptyList()
    }

    /** Action de selection d'un marqueur interroge, ou null si la feature n'est pas exploitable. */
    private fun pickOf(key: String, f: org.maplibre.geojson.Feature): (() -> Unit)? {
        val id = f.getStringProperty("__id") ?: return null
        val g = f.geometry() as? org.maplibre.geojson.Point ?: return null
        return { onPickPoint?.invoke(key, id, g.longitude(), g.latitude()) }
    }

    /** Decale la carte de (dx, dy) pixels ecran : le contenu, donc le marqueur, se deplace d'autant.
     *  On deplace le centre courant de (-dx, -dy) puis on y anime la camera. */
    fun panByScreen(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        val m = map ?: return
        val center = m.cameraPosition.target ?: return
        val p = m.projection.toScreenLocation(center)
        val newCenter = m.projection.fromScreenLocation(PointF(p.x - dx, p.y - dy))
        m.easeCamera(CameraUpdateFactory.newLatLng(newCenter), 250)
    }

    fun onMapReady(m: MapLibreMap) {
        map = m
        m.uiSettings.isCompassEnabled = false  // remplacé par un bouton Compose (positionnement fiable)
        m.uiSettings.isRotateGesturesEnabled = rotateGesturesEnabled
        applyStyleIfNeeded()
    }

    /** Cap actuel de la carte, en degrés (0 = nord en haut). */
    fun bearing(): Double = map?.cameraPosition?.bearing ?: 0.0

    /** Réoriente la carte pour remettre le nord en haut. */
    fun resetNorth() { map?.easeCamera(CameraUpdateFactory.bearingTo(0.0)) }

    /** Aucun style de repli : tant que json/url ne sont pas résolus (ex. fonds/réglages pas encore
     *  chargés), on ne touche pas au style actuellement appliqué plutôt que d'afficher un style de
     *  démonstration MapLibre (fond monde générique, un aplat de couleur par pays). */
    fun requestStyle(json: String?, url: String?) {
        if (json == null && url == null) return
        desiredJson = json; desiredUrl = url; desiredKey = url ?: json
        applyStyleIfNeeded()
    }

    private fun applyStyleIfNeeded() {
        val m = map ?: return
        val key = desiredKey ?: return
        if (key == appliedKey) return
        appliedKey = key
        val b = if (desiredUrl != null) Style.Builder().fromUri(desiredUrl!!) else Style.Builder().fromJson(desiredJson!!)
        m.setStyle(b) { st ->
            style = st
            // le nouveau style a vidé sources, couches et images
            layerKeys.clear(); pinImages.clear(); gpsImages.clear(); applied.clear()
            appliedUserMarker = null
            onStyleApplied?.invoke()
        }
    }

    private fun src(key: String) = "src-$key"
    private fun lineLayerId(key: String) = "$key-ln"
    private fun pointLayerId(key: String) = "$key-pt"
    /** Identifiants des calques/source du marqueur sélectionné : ombre portée, pin au sommet, source commune
     *  (cf. [setSelectedMarker]). */
    private val SEL_SHADOW = "sel-marker-shadow"
    private val SEL_TOP = "sel-marker-top"
    private val SEL_SRC = "sel-marker-src"
    /** Source et couche des points d'interet (cf. [setPoiMarkers]). */
    private val POI_SRC = "poi-src"
    private val POI_LAYER = "poi-pt"
    /** Source et couches du repere de position GPS : le cercle de precision, puis le symbole par-dessus. */
    private val USER_SRC = "user-location"
    private val USER_ACCURACY = "user-location-accuracy"
    private val USER_DOT = "user-location-dot"
    private fun addLayerSafe(layer: org.maplibre.android.style.layers.Layer) {
        val s = style ?: return
        if (s.getLayer("cursor-dot") != null) s.addLayerBelow(layer, "cursor-dot") else s.addLayer(layer)
    }

    private val lineGeometryFilter: Expression = Expression.any(
        Expression.eq(Expression.geometryType(), Expression.literal("LineString")),
        Expression.eq(Expression.geometryType(), Expression.literal("MultiLineString")),
    )
    private val pointGeometryFilter: Expression = Expression.eq(Expression.geometryType(), Expression.literal("Point"))
    private val polygonGeometryFilter: Expression = Expression.eq(Expression.geometryType(), Expression.literal("Polygon"))

    /** Ajoute/actualise une source par couche, avec ses deux style layers (ligne + points) filtrés par géométrie. */
    fun setLayers(list: List<RenderLayer>, markerHeightPx: Float) {
        val s = style ?: return
        val wanted = list.associateBy { it.key }
        (layerKeys - wanted.keys).forEach { k ->
            s.getLayer(pointLayerId(k))?.let { s.removeLayer(it) }
            s.getLayer(lineLayerId(k))?.let { s.removeLayer(it) }
            s.getSource(src(k))?.let { s.removeSource(it) }
            layerKeys.remove(k)
            applied.remove(k)
        }
        list.forEach { r ->
            val prev = applied[r.key]
            val source = s.getSourceAs<GeoJsonSource>(src(r.key))
            if (source == null || prev == null || prev.first != r.revision) {
                // (Re)cree la source depuis l'URI file:// du .map : MapLibre lit et parse le fichier sur son
                // propre thread de travail (pas de blocage UI, pas de GeoJSON volumineux garde en memoire JVM).
                // Si la revision a change (edition de points), on retire d'abord l'ancienne source : reutiliser
                // la meme URI ne garantit pas un rechargement du contenu.
                if (source != null) {
                    s.getLayer(pointLayerId(r.key))?.let { s.removeLayer(it) }
                    s.getLayer(lineLayerId(r.key))?.let { s.removeLayer(it) }
                    s.getSource(src(r.key))?.let { s.removeSource(it) }
                    layerKeys.remove(r.key)
                }
                val img = ensurePin(s, appContext, r.color, markerHeightPx)
                s.addSource(GeoJsonSource(src(r.key), java.net.URI(r.uri)))
                addLayerSafe(LineLayer(lineLayerId(r.key), src(r.key)).withProperties(
                    PropertyFactory.lineColor(r.color), PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"))
                    .withFilter(lineGeometryFilter))
                addLayerSafe(SymbolLayer(pointLayerId(r.key), src(r.key)).withProperties(
                    PropertyFactory.iconImage(img),
                    PropertyFactory.iconSize(1f),               // taille fixe à l'écran (pas d'échelle au zoom)
                    PropertyFactory.iconAnchor("bottom"),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true))
                    .withFilter(pointGeometryFilter))
                layerKeys.add(r.key)
            } else if (prev.second != r.color) {
                val img = ensurePin(s, appContext, r.color, markerHeightPx)
                (s.getLayer(lineLayerId(r.key)) as? LineLayer)?.setProperties(PropertyFactory.lineColor(r.color))
                (s.getLayer(pointLayerId(r.key)) as? SymbolLayer)?.setProperties(PropertyFactory.iconImage(img))
            }
            applied[r.key] = r.revision to r.color
        }
    }

    fun clearLayers() = setLayers(emptyList(), 96f)

    private val shadowImages = mutableSetOf<String>()

    /**
     * Marqueur sélectionné : son ombre portée (silhouette grise floutée du pin, décalée en bas à droite,
     * posée sous les pins de sa trace [belowKey]) et une copie de son pin au sommet de la pile, pour qu'il
     * passe DEVANT les marqueurs qui le chevauchent. Les deux partagent une source. Calques carte (et non
     * surcouche Compose) : ils suivent seuls la carte au pan/zoom. [lon]/[lat] nuls retirent tout.
     */
    fun setSelectedMarker(
        lon: Double?, lat: Double?, belowKey: String?, colorHex: String?, heightPx: Float,
        iconRes: Int? = null,
    ) {
        val s = style ?: return
        if (lon == null || lat == null) {
            s.getLayer(SEL_TOP)?.let { s.removeLayer(it) }
            s.getLayer(SEL_SHADOW)?.let { s.removeLayer(it) }
            s.getSource(SEL_SRC)?.let { s.removeSource(it) }
            return
        }
        val h = heightPx.toInt().coerceIn(24, 256)
        val shadowImg = ensureShadowImage(s, appContext, h)
        // Un point d'interet reprend sa bulle a pictogramme, un marqueur de trace son pin : la copie
        // posee au sommet doit etre celle du marqueur qu'on vient de toucher, sans quoi il changerait de
        // dessin en s'ouvrant.
        val pinImg = if (iconRes != null) ensurePoiBubble(s, appContext, colorHex ?: "#1F6FB2", iconRes, heightPx)
        else ensurePin(s, appContext, colorHex ?: "#1F6FB2", heightPx)
        val geojson = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[$lon,$lat]},\"properties\":{}}"
        val existingSrc = s.getSourceAs<GeoJsonSource>(SEL_SRC)
        if (existingSrc == null) s.addSource(GeoJsonSource(SEL_SRC, geojson)) else existingSrc.setGeoJson(geojson)
        // Ombre, sous les pins de la trace du marqueur.
        if (s.getLayer(SEL_SHADOW) == null) {
            val shadow = SymbolLayer(SEL_SHADOW, SEL_SRC).withProperties(
                PropertyFactory.iconImage(shadowImg), PropertyFactory.iconSize(1f),
                PropertyFactory.iconAnchor("bottom"),
                // léger décalage bas-droite, pour un effet d'ombre portée (le marqueur est ancré en bas)
                PropertyFactory.iconOffset(arrayOf(3f, 4f)),
                PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true))
                .withFilter(pointGeometryFilter)
            val belowPts = belowKey?.let { pointLayerId(it) }
            if (belowPts != null && s.getLayer(belowPts) != null) s.addLayerBelow(shadow, belowPts) else s.addLayer(shadow)
        } else {
            (s.getLayer(SEL_SHADOW) as? SymbolLayer)?.setProperties(PropertyFactory.iconImage(shadowImg))
        }
        // Copie du pin au-dessus des autres marqueurs, mais SOUS les overlays de tête (point GPS, point
        // courant du profil) : le marqueur cliqué passe devant ceux qui le chevauchent sans masquer le
        // repère GPS. On l'insère sous le plus bas de ces overlays présents (style.layers est ordonné du bas
        // vers le haut, d'où le premier match) ; à défaut, au sommet, rien à passer dessous.
        if (s.getLayer(SEL_TOP) == null) {
            val topPin = SymbolLayer(SEL_TOP, SEL_SRC).withProperties(
                PropertyFactory.iconImage(pinImg), PropertyFactory.iconSize(1f),
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true))
                .withFilter(pointGeometryFilter)
            val headOverlays = setOf(USER_ACCURACY, USER_DOT, "cursor-dot")
            val lowestOverlay = s.layers.firstOrNull { it.id in headOverlays }?.id
            if (lowestOverlay != null) s.addLayerBelow(topPin, lowestOverlay) else s.addLayer(topPin)
        } else {
            (s.getLayer(SEL_TOP) as? SymbolLayer)?.setProperties(PropertyFactory.iconImage(pinImg))
        }
    }

    /**
     * Marqueurs des points d'interet, dessines par-dessus les traces. Liste vide : la couche disparait.
     *
     * Une seule source pour tous, et l'icone choisie PAR MARQUEUR via une expression de style
     * (`iconImage(get("__icon"))`) : une couche par groupe couterait quatre interrogations a chaque tap et
     * autant de sources a tenir a jour, pour un resultat identique a l'ecran.
     *
     * Le meme pin que les marqueurs de trace, teinte a la couleur du groupe : la carte garde une seule
     * grammaire de marqueur, et un point d'interet se distingue par sa couleur, non par une forme etrangere
     * au reste de l'application.
     */
    fun setPoiMarkers(markers: List<PoiMarker>, heightPx: Float) {
        val s = style ?: return
        if (markers.isEmpty()) {
            s.getLayer(POI_LAYER)?.let { s.removeLayer(it) }
            s.getSource(POI_SRC)?.let { s.removeSource(it) }
            return
        }
        val features = markers.joinToString(",") { m ->
            val icon = ensurePoiBubble(s, appContext, m.colorHex, m.iconRes, heightPx)
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[${m.lon},${m.lat}]},""" +
                """"properties":{"__id":"${m.id}","__icon":"$icon"}}"""
        }
        val geojson = """{"type":"FeatureCollection","features":[$features]}"""
        val existing = s.getSourceAs<GeoJsonSource>(POI_SRC)
        if (existing == null) s.addSource(GeoJsonSource(POI_SRC, geojson)) else existing.setGeoJson(geojson)
        if (s.getLayer(POI_LAYER) == null) {
            val layer = SymbolLayer(POI_LAYER, POI_SRC).withProperties(
                PropertyFactory.iconImage(Expression.get("__icon")),
                PropertyFactory.iconSize(1f),
                PropertyFactory.iconAnchor("bottom"),
                // Les marqueurs se recouvrent en ville, et MapLibre en masque alors une partie. On les
                // montre tous : un point d'interet absent se lit comme un lieu qui n'existe pas.
                PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
            ).withFilter(pointGeometryFilter)
            // Sous les overlays de tete (position GPS, curseur du profil), comme le marqueur selectionne :
            // une nuee de points d'interet ne doit pas cacher le repere de position.
            val headOverlays = setOf(USER_ACCURACY, USER_DOT, "cursor-dot")
            val lowest = s.layers.firstOrNull { it.id in headOverlays }?.id
            if (lowest != null) s.addLayerBelow(layer, lowest) else s.addLayer(layer)
        }
    }

    /**
     * Bulle d'un point d'interet : une goutte pleine a la couleur du groupe, le pictogramme de la
     * categorie en blanc dedans.
     *
     * La MEME silhouette que les marqueurs de trace (ic_pin_fill), et non une forme etrangere : la carte
     * garde une seule grammaire de marqueur. Ce qui distingue un point d'interet, c'est son dessin.
     *
     * Le pictogramme occupe 44 % de la hauteur et se pose au centre de la TETE de la goutte, aux 38 % du
     * haut - pas au milieu de l'image, ou il tomberait sur la pointe.
     */
    private fun ensurePoiBubble(s: Style, context: Context?, colorHex: String, iconRes: Int, heightPx: Float): String {
        val h = heightPx.toInt().coerceIn(24, 256)
        val name = "poi_${colorHex.removePrefix("#")}_${iconRes}_$h"
        if (poiImages.add(name)) s.addImage(name, poiBubbleBitmap(context, colorHex.toColorInt(), iconRes, h))
        return name
    }

    private fun poiBubbleBitmap(context: Context?, colorInt: Int, iconRes: Int, h: Int): Bitmap {
        val bmp = createBitmap(h, h)
        val c = AndroidCanvas(bmp)
        if (context != null) {
            ContextCompat.getDrawable(context, R.drawable.ic_pin_outline)?.apply {
                setBounds(0, 0, h, h); draw(c)
            }
            // La goutte PLEINE, et non celle des traces : celle-ci porte un trou dans sa tete, qui
            // masquait le pictogramme (cf. ic_poi_pin.xml).
            ContextCompat.getDrawable(context, R.drawable.ic_poi_pin)?.mutate()?.apply {
                setBounds(0, 0, h, h); setTint(colorInt); draw(c)
            }
            val g = (h * 0.44f).toInt()
            val left = (h - g) / 2
            val top = (h * 0.38f - g / 2f).toInt()
            ContextCompat.getDrawable(context, iconRes)?.mutate()?.apply {
                setBounds(left, top, left + g, top + g)
                setTint(android.graphics.Color.WHITE)
                draw(c)
            }
        }
        return bmp
    }

    private fun ensureShadowImage(s: Style, context: Context?, h: Int): String {
        val name = "pin_shadow_$h"
        if (shadowImages.add(name)) s.addImage(name, pinShadowBitmap(context, h))
        return name
    }

    /** Silhouette du pin (contour + remplissage) en gris translucide, floutée : l'ombre épouse ainsi la
     *  forme réelle du marqueur au lieu d'un cercle. */
    private fun pinShadowBitmap(context: Context?, h: Int): Bitmap {
        val pin = createBitmap(h, h)
        val pc = AndroidCanvas(pin)
        context?.let { ctx ->
            ContextCompat.getDrawable(ctx, R.drawable.ic_pin_outline)?.mutate()?.apply {
                setBounds(0, 0, h, h); setTint(android.graphics.Color.BLACK); draw(pc)
            }
            ContextCompat.getDrawable(ctx, R.drawable.ic_pin_fill)?.mutate()?.apply {
                setBounds(0, 0, h, h); setTint(android.graphics.Color.BLACK); draw(pc)
            }
        }
        val blur = (h * 0.05f).coerceAtLeast(1f)
        val offsetXY = IntArray(2)
        val alpha = pin.extractAlpha(
            Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) },
            offsetXY)
        val out = createBitmap(h, h)
        // gris ~45 % d'opacité : ombre légère, pas un aplat opaque.
        AndroidCanvas(out).drawBitmap(alpha, offsetXY[0].toFloat(), offsetXY[1].toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x73555555.toInt() })
        pin.recycle(); alpha.recycle()
        return out
    }

    /** Rouge du cadre de la bbox hors-ligne, celui du bouton "Retour" de la barre de tracé. */
    private val BBOX_RED = "#D32F2F"

    /** Coins posés pendant le tracé de la bbox hors-ligne : rendus en croix "viseur" (pas les épingles
     *  habituelles) + cadre rouge et emprise blanchie une fois 2 coins posés. Source/couches dédiées,
     *  indépendantes du système générique [setLayers]/[RenderLayer] (overlay propre à l'app, pas une
     *  couche importée).
     *
     *  [showPoints] est faux pour la vue d'ensemble de l'écran de téléchargement : l'emprise y est déjà
     *  fixée, ses coins n'ont plus rien à montrer - seul le cadre compte. */
    fun setBboxDraw(points: List<Pair<Double, Double>>, showPoints: Boolean = true) {
        val s = style ?: return
        if (points.isEmpty()) {
            s.getLayer("bbox-draw-pt")?.let { s.removeLayer(it) }
            s.getLayer("bbox-draw-line")?.let { s.removeLayer(it) }
            s.getLayer("bbox-draw-fill")?.let { s.removeLayer(it) }
            s.getSource("bbox-draw-src")?.let { s.removeSource(it) }
            return
        }
        val geojson = bboxDrawGeoJson(points)
        val existing = s.getSourceAs<GeoJsonSource>("bbox-draw-src")
        if (existing == null) {
            s.addSource(GeoJsonSource("bbox-draw-src", geojson))
            // Emprise blanchie sous le cadre : elle éclaircit le fond sans le masquer, là où un aplat de
            // la couleur du cadre teindrait la carte et rendrait ses couleurs illisibles.
            addLayerSafe(FillLayer("bbox-draw-fill", "bbox-draw-src").withProperties(
                PropertyFactory.fillColor("#FFFFFF"), PropertyFactory.fillOpacity(0.1f))
                .withFilter(polygonGeometryFilter))
            addLayerSafe(LineLayer("bbox-draw-line", "bbox-draw-src").withProperties(
                PropertyFactory.lineColor(BBOX_RED), PropertyFactory.lineWidth(3f))
                .withFilter(lineGeometryFilter))
            val img = "bbox_crosshair"
            s.addImage(img, crosshairBitmap(android.graphics.Color.BLACK, (40 * density).toInt()))
            // Explicitement au-dessus du rectangle (pas seulement dans l'ordre d'ajout) : la croix doit
            // rester visible même là où elle touche le contour du rectangle.
            s.addLayerAbove(
                SymbolLayer("bbox-draw-pt", "bbox-draw-src").withProperties(
                    PropertyFactory.iconImage(img), PropertyFactory.iconSize(1f), PropertyFactory.iconAnchor("center"),
                    PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.visibility(if (showPoints) Property.VISIBLE else Property.NONE))
                    .withFilter(pointGeometryFilter),
                "bbox-draw-line",
            )
        } else {
            existing.setGeoJson(geojson)
            (s.getLayer("bbox-draw-pt") as? SymbolLayer)?.setProperties(
                PropertyFactory.visibility(if (showPoints) Property.VISIBLE else Property.NONE))
        }
    }

    /** Le rectangle est décrit deux fois, en ligne et en polygone : la ligne porte le cadre, le polygone
     *  l'emprise blanchie, et une seule géométrie ne peut pas faire les deux. */
    private fun bboxDrawGeoJson(points: List<Pair<Double, Double>>): String {
        val features = StringBuilder()
        points.forEach { (lon, lat) ->
            if (features.isNotEmpty()) features.append(',')
            features.append("""{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{}}""")
        }
        if (points.size >= 2) {
            val (lon1, lat1) = points[0]; val (lon2, lat2) = points[1]
            val w = minOf(lon1, lon2); val e = maxOf(lon1, lon2)
            val south = minOf(lat1, lat2); val n = maxOf(lat1, lat2)
            val ring = """[[$w,$south],[$e,$south],[$e,$n],[$w,$n],[$w,$south]]"""
            if (features.isNotEmpty()) features.append(',')
            features.append(
                """{"type":"Feature","geometry":{"type":"LineString","coordinates":$ring},"properties":{}},""" +
                    """{"type":"Feature","geometry":{"type":"Polygon","coordinates":[$ring]},"properties":{}}"""
            )
        }
        return """{"type":"FeatureCollection","features":[$features]}"""
    }

    /** Identifiants des marqueurs noirs du géocodage : le lieu trouvé, et le point de référence choisi à la
     *  main pour une mesure de distance. Deux couches distinctes, les deux pouvant coexister. */
    private val GEO_PLACE = "geocode-place"
    private val GEO_REF = "geocode-ref"
    /** Calque et source des tracés d'itinéraire mesurés (cf. [setRouteLines]). */
    private val ROUTE_LINE = "geocode-route"
    private val ROUTE_SRC = "geocode-route-src"

    /**
     * Épingle noire du géocodage : le lieu trouvé ([GEO_PLACE]) ou le point de référence d'une mesure de
     * distance ([GEO_REF]). Même épingle que les marqueurs de couches, teintée en noir. [lon]/[lat] nuls
     * la retirent.
     *
     * Posée au sommet du style, sans le soin apporté à [setSelectedMarker] pour se glisser sous les repères
     * de tête : ces deux marqueurs sont, le temps de la recherche, ce que l'utilisateur regarde.
     */
    fun setGeocodeMarker(reference: Boolean, lon: Double?, lat: Double?, heightPx: Float) {
        val s = style ?: return
        val id = if (reference) GEO_REF else GEO_PLACE
        val srcId = "$id-src"
        if (lon == null || lat == null) {
            s.getLayer(id)?.let { s.removeLayer(it) }
            s.getSource(srcId)?.let { s.removeSource(it) }
            return
        }
        val img = ensurePin(s, appContext, "#000000", heightPx)
        val geojson = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[$lon,$lat]},\"properties\":{}}"
        val existing = s.getSourceAs<GeoJsonSource>(srcId)
        if (existing == null) {
            s.addSource(GeoJsonSource(srcId, geojson))
            s.addLayer(SymbolLayer(id, srcId).withProperties(
                PropertyFactory.iconImage(img), PropertyFactory.iconSize(1f),
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true))
                .withFilter(pointGeometryFilter))
        } else {
            existing.setGeoJson(geojson)
            (s.getLayer(id) as? SymbolLayer)?.setProperties(PropertyFactory.iconImage(img))
        }
    }

    /** Calque et source des epingles noires du point designe par un appui long (cf. [setMapPointMarkers]). */
    private val MAP_POINTS = "map-point-pins"
    private val MAP_POINTS_SRC = "map-point-pins-src"

    /**
     * Épingles noires du point désigné par un appui long, et de son point de référence (zéro, une ou deux).
     *
     * Une seule source pour les deux, comme les bouts d'une mesure sur trace : elles ont même style, et
     * apparaissent et disparaissent ensemble - l'infobulle du point refermée n'en laisse aucune. Distinctes
     * des épingles du géocodage, qui peuvent être à l'écran en même temps et ne se ferment pas par le même
     * geste.
     */
    fun setMapPointMarkers(points: List<Pair<Double, Double>>, heightPx: Float) =
        setBlackPins(MAP_POINTS, MAP_POINTS_SRC, points, heightPx)

    /** Calque et source des marqueurs noirs de la mesure sur trace (cf. [setMeasureMarkers]). */
    private val MEASURE_PTS = "measure-points"
    private val MEASURE_SRC = "measure-points-src"

    /**
     * Épingles noires des deux bouts d'une mesure sur trace (zéro, une ou deux).
     *
     * Une seule source pour les deux : elles ont même style, apparaissent et disparaissent ensemble, et
     * deux couches n'apporteraient qu'un ordre d'empilement à tenir. Distinctes des épingles du géocodage,
     * qui peuvent être à l'écran en même temps et ne se ferment pas par le même geste.
     */
    fun setMeasureMarkers(points: List<Pair<Double, Double>>, heightPx: Float) =
        setBlackPins(MEASURE_PTS, MEASURE_SRC, points, heightPx)

    /** Un groupe d'épingles noires posé au sommet du style, sous ses propres identifiants : une source et
     *  un calque par groupe, les groupes pouvant être à l'écran en même temps. Vide = tout est retiré. */
    private fun setBlackPins(
        layerId: String, srcId: String, points: List<Pair<Double, Double>>, heightPx: Float,
    ) {
        val s = style ?: return
        if (points.isEmpty()) {
            s.getLayer(layerId)?.let { s.removeLayer(it) }
            s.getSource(srcId)?.let { s.removeSource(it) }
            return
        }
        val img = ensurePin(s, appContext, "#000000", heightPx)
        val features = points.joinToString(",") { (lon, lat) ->
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{}}"""
        }
        val geojson = """{"type":"FeatureCollection","features":[$features]}"""
        val existing = s.getSourceAs<GeoJsonSource>(srcId)
        if (existing == null) {
            s.addSource(GeoJsonSource(srcId, geojson))
            s.addLayer(SymbolLayer(layerId, srcId).withProperties(
                PropertyFactory.iconImage(img), PropertyFactory.iconSize(1f),
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true))
                .withFilter(pointGeometryFilter))
        } else {
            existing.setGeoJson(geojson)
            (s.getLayer(layerId) as? SymbolLayer)?.setProperties(PropertyFactory.iconImage(img))
        }
    }

    /**
     * Tracés des itinéraires mesurés depuis l'infobulle d'un lieu (zéro, un ou deux : depuis la position,
     * depuis un point choisi). Une seule source pour les deux : ils ont même style et changent ensemble,
     * deux couches n'apporteraient qu'un ordre d'empilement à tenir.
     *
     * Noirs par défaut ; teintés par classe de pente ([colorBySlope]) quand le moteur a rendu des altitudes,
     * avec la rampe et les classes du profil (cf. [SlopeRamp]) - une même couleur y désigne donc la même
     * pente, en haut sur le graphique et en bas sur la carte.
     *
     * La couleur voyage dans une propriété de chaque tronçon et non dans le style : le style est fixe, et
     * les couleurs, elles, changent à chaque mesure.
     *
     * Posés SOUS les épingles noires quand elles existent : le tracé aboutit au lieu trouvé, son trait ne
     * doit pas passer devant le marqueur qui le termine.
     */
    fun setRouteLines(tracks: List<ComputedTrack>, colorBySlope: Boolean) {
        val s = style ?: return
        val drawable = tracks.filter { it.samples.size >= 2 }
        if (drawable.isEmpty()) {
            s.getLayer(ROUTE_LINE)?.let { s.removeLayer(it) }
            s.getSource(ROUTE_SRC)?.let { s.removeSource(it) }
            return
        }
        val features = drawable.joinToString(",") { routeFeatures(it, colorBySlope) }
        val geojson = """{"type":"FeatureCollection","features":[$features]}"""
        val existing = s.getSourceAs<GeoJsonSource>(ROUTE_SRC)
        if (existing == null) {
            s.addSource(GeoJsonSource(ROUTE_SRC, geojson))
            val layer = LineLayer(ROUTE_LINE, ROUTE_SRC).withProperties(
                PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"))
                .withFilter(lineGeometryFilter)
            val belowPin = listOf(GEO_PLACE, GEO_REF, MAP_POINTS).firstOrNull { s.getLayer(it) != null }
            if (belowPin != null) s.addLayerBelow(layer, belowPin) else addLayerSafe(layer)
        } else {
            existing.setGeoJson(geojson)
        }
    }

    /**
     * Tronçons d'un itinéraire : un seul quand il est d'une teinte, sinon un par plage de pente de même
     * classe. Les plages **partagent leur point de jonction**, sans quoi la ligne s'ouvrirait d'un trou à
     * chaque changement de couleur.
     */
    private fun routeFeatures(track: ComputedTrack, colorBySlope: Boolean): String {
        val pts = track.samples
        fun feature(from: Int, to: Int, color: String): String {
            val coords = (from..to).joinToString(",") { "[${pts[it].lon},${pts[it].lat}]" }
            return """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},""" +
                """"properties":{"color":"$color"}}"""
        }
        if (!colorBySlope || !track.hasZ) return feature(0, pts.lastIndex, "#000000")
        val max = track.stats.maxAbsSlope
        val out = StringBuilder()
        var i = 0
        while (i < pts.lastIndex) {
            val color = SlopeRamp.hexFor(pts[i + 1].slope, max)
            var j = i + 1
            while (j < pts.lastIndex && SlopeRamp.hexFor(pts[j + 1].slope, max) == color) j++
            if (out.isNotEmpty()) out.append(',')
            out.append(feature(i, j, color))
            i = j
        }
        return out.toString()
    }

    /** Croix "+" traversante, cerclée en son centre : les deux traits se croisent exactement au point posé
     *  (un viseur à cercle vide laisserait le point lui-même invisible), et le petit cercle marque
     *  l'intersection au milieu des lignes de la carte, qui autrement se confondent avec la croix. */
    private fun crosshairBitmap(colorInt: Int, sizePx: Int): Bitmap {
        // Meme borne haute que les epingles (256) : sur un ecran tres dense, un cote demande en dp depasse
        // vite 128 px, et la croix se retrouvait alors dessinee plus petite que la taille demandee.
        val size = sizePx.coerceIn(16, 256)
        val bmp = createBitmap(size, size)
        val c = AndroidCanvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt; style = Paint.Style.STROKE; strokeWidth = size * 0.07f
        }
        val cx = size / 2f; val cy = size / 2f
        c.drawLine(cx, 0f, cx, size.toFloat(), paint)
        c.drawLine(0f, cy, size.toFloat(), cy, paint)
        c.drawCircle(cx, cy, size * 0.18f, paint)
        return bmp
    }

    /** Crée (une fois) une épingle de la couleur et taille demandées. */
    private fun ensurePin(s: Style, context: Context?, colorHex: String, heightPx: Float): String {
        val h = heightPx.toInt().coerceIn(24, 256)
        val name = "pin_${colorHex.removePrefix("#")}_$h"
        if (pinImages.add(name)) s.addImage(name, pinBitmap(context, colorHex.toColorInt(), h))
        return name
    }

    /** Contour fixe (ic_pin_outline) + remplissage (ic_pin_fill) teinté dynamiquement à la couleur de la couche. */
    private fun pinBitmap(context: Context?, colorInt: Int, h: Int): Bitmap {
        val bmp = createBitmap(h, h)
        val c = AndroidCanvas(bmp)
        if (context != null) {
            ContextCompat.getDrawable(context, R.drawable.ic_pin_outline)?.apply {
                setBounds(0, 0, h, h); draw(c)
            }
            ContextCompat.getDrawable(context, R.drawable.ic_pin_fill)?.mutate()?.apply {
                setBounds(0, 0, h, h); setTint(colorInt); draw(c)
            }
        }
        return bmp
    }

    /**
     * Camera visant [lat]/[lon] AU MILIEU DE L'ECRAN, orientation et inclinaison inchangees.
     *
     * Le decalage de vue ("padding") est explicitement remis a zero, et c'est tout l'objet de ce detour :
     * un cadrage sur emprise (cf. [fitTo]) rend une camera qui PORTE le decalage qu'on lui a demande, et
     * MapLibre le conserve ensuite pour toutes les transitions - c'est ecrit dans son contrat. Un
     * recentrage venant apres posait donc son point au milieu de la zone restante, sous les boutons du
     * haut et au-dessus de la bande du bas : la position s'affichait bien, mais decalee, et le bouton de
     * recentrage ne disparaissait pas puisqu'elle n'etait toujours pas au centre.
     */
    private fun centeredCamera(lat: Double, lon: Double, zoom: Double? = null): CameraPosition? {
        val cur = map?.cameraPosition ?: return null
        return CameraPosition.Builder(cur)
            .target(LatLng(lat, lon))
            .zoom(zoom ?: cur.zoom)
            .padding(0.0, 0.0, 0.0, 0.0)
            .build()
    }

    fun moveTo(lat: Double, lon: Double, zoom: Double) {
        val cam = centeredCamera(lat, lon, zoom) ?: return
        map?.moveCamera(CameraUpdateFactory.newCameraPosition(cam))
    }
    /** Recentre sur un point sans changer le niveau de zoom courant (bouton de recentrage GPS). */
    fun centerOn(lat: Double, lon: Double) {
        val cam = centeredCamera(lat, lon) ?: return
        map?.easeCamera(CameraUpdateFactory.newCameraPosition(cam))
    }

    /** Recentre sur un point en garantissant [minZoom] : un zoom déjà plus serré est conservé. Sert au lieu
     *  trouvé par la recherche, qui, depuis une vue à l'échelle d'un pays, se poserait sinon sur une carte
     *  où rien ne permet de le situer. */
    fun centerOnAtLeast(lat: Double, lon: Double, minZoom: Double) {
        val z = map?.cameraPosition?.zoom ?: return
        if (z >= minZoom) centerOn(lat, lon)
        else {
            val cam = centeredCamera(lat, lon, minZoom) ?: return
            map?.easeCamera(CameraUpdateFactory.newCameraPosition(cam))
        }
    }
    fun cameraState(): Triple<Double, Double, Double>? {
        val cp = map?.cameraPosition ?: return null
        val t = cp.target ?: return null
        return Triple(t.latitude, t.longitude, cp.zoom)
    }
    fun metersPerPixel(lat: Double): Double = map?.projection?.getMetersPerPixelAtLatitude(lat) ?: 0.0

    /** Emprise géographique actuellement à l'écran, ou null si la carte n'est pas encore prête.
     *  Lue sur la région visible et non calculée depuis le centre : elle tient compte de la rotation
     *  et de l'inclinaison de la caméra. */
    fun visibleBounds(): Bbox? {
        val b = map?.projection?.visibleRegion?.latLngBounds ?: return null
        return Bbox.of(b.longitudeWest, b.latitudeSouth, b.longitudeEast, b.latitudeNorth)
    }

    fun setCursor(lon: Double, lat: Double) {
        val s = style ?: return
        if (s.getSourceAs<GeoJsonSource>("cursor") == null) {
            s.addSource(GeoJsonSource("cursor", emptyFc()))
            s.addLayer(CircleLayer("cursor-dot", "cursor").withProperties(
                // Rayon constant (4) jusqu'au zoom 16, puis croissant : l'interpolation se fige sur la
                // première butée en deçà de 16, donc l'agrandissement ne commence qu'à partir de ce niveau.
                PropertyFactory.circleRadius(Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(16f, 4f), Expression.stop(22f, 11f))),
                PropertyFactory.circleColor("#ffffff"),
                PropertyFactory.circleStrokeColor("#1F6FB2"), PropertyFactory.circleStrokeWidth(2f)))
        }
        s.getSourceAs<GeoJsonSource>("cursor")?.setGeoJson(
            "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[$lon,$lat]},\"properties\":{}}")
    }
    fun clearCursor() { style?.getSourceAs<GeoJsonSource>("cursor")?.setGeoJson(emptyFc()) }

    /**
     * Repère de position GPS : le symbole choisi (cf. [setUserMarker]), entouré du cercle de précision
     * semi-transparent dimensionné en mètres réels.
     *
     * La dernière position reçue est CONSERVÉE : un changement de symbole, comme un rechargement de style
     * (qui vide sources et couches), doit pouvoir replacer le repère sans attendre le point suivant - le
     * capteur ne parle que toutes les deux secondes, et seulement si l'on bouge.
     */
    fun setUserLocation(lon: Double, lat: Double, accuracyMeters: Float) {
        lastUserFix = Triple(lon, lat, accuracyMeters)
        val s = style ?: return
        ensureUserLayers(s)
        // rayon du cercle de précision en pixels écran, recalculé pour rester exact à tout niveau de zoom :
        // à échelle Web Mercator, mètres/pixel double à chaque niveau de zoom, donc rayon(z) = rayon(0) * 2^z ;
        // une interpolation exponentielle de base 2 entre deux points quelconques de cette courbe la reproduit
        // exactement (pas juste une approximation), pas besoin de la recalculer à chaque changement de zoom.
        val mpp = metersPerPixel(lat)
        val zoom = map?.cameraPosition?.zoom
        if (mpp > 0.0 && zoom != null && accuracyMeters > 0f) {
            val r0 = (accuracyMeters / mpp / 2.0.pow(zoom)).toFloat()
            val r22 = (r0 * 2.0.pow(22.0)).toFloat()
            (s.getLayer(USER_ACCURACY) as? CircleLayer)?.setProperties(
                PropertyFactory.circleRadius(Expression.interpolate(
                    Expression.exponential(2f), Expression.zoom(),
                    Expression.stop(0f, r0), Expression.stop(22f, r22))))
        }
        s.getSourceAs<GeoJsonSource>(USER_SRC)?.setGeoJson(
            "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[$lon,$lat]},\"properties\":{}}")
    }
    fun clearUserLocation() {
        lastUserFix = null
        style?.getSourceAs<GeoJsonSource>(USER_SRC)?.setGeoJson(emptyFc())
    }

    /**
     * Symbole du repère de position, sa couleur et sa taille (dp) - réglage utilisateur.
     *
     * Le repère déjà à l'écran est redessiné sur-le-champ, sans attendre la prochaine position : on règle
     * son symbole en le regardant. Appelé aussi après un rechargement de style, qui a emporté ses couches.
     */
    fun setUserMarker(marker: GpsMarkerStyle, colorHex: String, sizeDp: Float) {
        userMarker = marker
        userMarkerColor = colorHex
        userMarkerSizeDp = sizeDp
        val (lon, lat, acc) = lastUserFix ?: return
        setUserLocation(lon, lat, acc)
    }

    /**
     * Orientation du téléphone, en degrés depuis le nord VRAI (celui de la carte, pas celui de la boussole
     * : la déclinaison est corrigée en amont).
     *
     * Retenue même quand le symbole du moment n'en fait rien : le capteur ne redonne pas d'orientation à la
     * demande, et une flèche choisie entre deux mesures doit partir dans la bonne direction.
     */
    fun setUserHeading(deg: Float) {
        userHeading = deg
        if (!userMarker.oriented) return
        (style?.getLayer(USER_DOT) as? SymbolLayer)?.setProperties(PropertyFactory.iconRotate(deg))
    }

    /**
     * Source et couches du repère, (re)posées telles que le réglage les demande.
     *
     * Le symbole est refait quand sa description change - style, couleur, taille - et lui seul : le cercle
     * de précision garde sa couche, donc le rayon déjà calculé pour le zoom courant. Il reprend en revanche
     * la couleur du symbole : c'est l'imprécision DE CE repère-là qu'il dessine, pas un halo indépendant.
     */
    private fun ensureUserLayers(s: Style) {
        val colorInt = runCatching { userMarkerColor.toColorInt() }
            .getOrElse { GpsMarkerStyle.DOT.defaultColor.toColorInt() }
        if (s.getSourceAs<GeoJsonSource>(USER_SRC) == null) {
            s.addSource(GeoJsonSource(USER_SRC, emptyFc()))
            s.addLayer(CircleLayer(USER_ACCURACY, USER_SRC).withProperties(
                PropertyFactory.circleOpacity(0.16f),
                PropertyFactory.circleStrokeOpacity(0.5f), PropertyFactory.circleStrokeWidth(1f)))
        }
        val key = "${userMarker.key}|$userMarkerColor|$userMarkerSizeDp"
        if (key == appliedUserMarker && s.getLayer(USER_DOT) != null) return
        appliedUserMarker = key
        (s.getLayer(USER_ACCURACY) as? CircleLayer)?.setProperties(
            PropertyFactory.circleColor(colorInt), PropertyFactory.circleStrokeColor(colorInt))
        // Retiree avant d'etre refaite : d'une puce a une fleche, ce n'est meme plus le meme type de
        // couche, un setProperties ne saurait pas passer de l'une a l'autre.
        s.getLayer(USER_DOT)?.let { s.removeLayer(it) }
        s.addLayer(userMarkerLayer(s, colorInt))
    }

    /** La couche du symbole : un cercle pour la puce, une image pour les trois autres. */
    private fun userMarkerLayer(s: Style, colorInt: Int): org.maplibre.android.style.layers.Layer {
        val dp = userMarkerSizeDp
        if (userMarker == GpsMarkerStyle.DOT) {
            // Proportions de la puce d'origine (rayon 7, contour 2,5 pour 20 dp de cote), reprises en
            // fractions du cote : elle grandit sans que son contour blanc ne l'etrangle ni ne la noie.
            return CircleLayer(USER_DOT, USER_SRC).withProperties(
                PropertyFactory.circleRadius(dp * 0.35f), PropertyFactory.circleColor(colorInt),
                PropertyFactory.circleStrokeColor("#ffffff"), PropertyFactory.circleStrokeWidth(dp * 0.125f))
        }
        val px = (dp * density).toInt().coerceIn(16, 256)
        val name = "gps_${userMarker.key}_${userMarkerColor.removePrefix("#")}_$px"
        if (gpsImages.add(name)) {
            s.addImage(name, when (userMarker) {
                GpsMarkerStyle.CROSSHAIR -> crosshairBitmap(colorInt, px)
                else -> navigationArrowBitmap(colorInt, px, filled = userMarker == GpsMarkerStyle.ARROW_FILLED)
            })
        }
        return SymbolLayer(USER_DOT, USER_SRC).withProperties(
            PropertyFactory.iconImage(name), PropertyFactory.iconSize(1f), PropertyFactory.iconAnchor("center"),
            PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
            // Rotation prise DANS LE REPERE DE LA CARTE : la fleche vise le nord vrai, et suit donc la
            // carte quand celle-ci tourne, au lieu de pointer une direction de l'ecran.
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconRotate(if (userMarker.oriented) userHeading ?: 0f else 0f))
    }

    /**
     * Fleche de navigation : pointe en haut, deux ailes vers le bas, encochee en son milieu - le dessin
     * qu'on lit d'emblee comme "je suis la, et je regarde par la".
     *
     * [filled] la remplit de la couleur et lui pose un contour blanc, comme la puce : un aplat de couleur
     * sur une orthophoto de la meme teinte disparaitrait sans lui. Au trait, elle reste creuse : le fond de
     * carte se lit AU TRAVERS du repere, ce qui est tout l'interet de ce symbole-la.
     */
    private fun navigationArrowBitmap(colorInt: Int, sizePx: Int, filled: Boolean): Bitmap {
        val size = sizePx.coerceIn(16, 256)
        val bmp = createBitmap(size, size)
        val c = AndroidCanvas(bmp)
        val stroke = size * 0.11f
        val inset = stroke                       // de quoi loger le trait, dedans comme dehors
        val cx = size / 2f
        val top = inset
        val bottom = size - inset
        val half = (size / 2f - inset) * 0.72f   // demi-envergure : plus etroite que haute, comme un chevron
        val notch = (bottom - top) * 0.28f       // profondeur de l'encoche du talon
        val path = android.graphics.Path().apply {
            moveTo(cx, top)
            lineTo(cx + half, bottom)
            lineTo(cx, bottom - notch)
            lineTo(cx - half, bottom)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }
        if (filled) {
            paint.color = android.graphics.Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            c.drawPath(path, paint)
            paint.color = colorInt
            paint.style = Paint.Style.FILL
            c.drawPath(path, paint)
        } else {
            paint.color = colorInt
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            c.drawPath(path, paint)
        }
        return bmp
    }

    fun screenOf(lon: Double, lat: Double): PointF? = map?.projection?.toScreenLocation(LatLng(lat, lon))

    /**
     * Le point est-il dans la vue, avec une marge de confort ?
     *
     * Mesure a l'ecran et non sur les coordonnees : c'est bien "le voit-on" qu'on demande, et la reponse
     * doit valoir a tout zoom. La marge evite de declarer visible un point colle au bord, que la moindre
     * inclinaison ou le bandeau du profil recouvriraient.
     */
    fun isOnScreen(lon: Double, lat: Double, marginPx: Float = 96f): Boolean {
        val m = map ?: return true
        val p = screenOf(lon, lat) ?: return true
        return p.x > marginPx && p.y > marginPx &&
            p.x < m.width - marginPx && p.y < m.height - marginPx
    }

    /**
     * [topPaddingPx] / [bottomPaddingPx] : hauteurs masquees en haut (barre de statut, la carte passant
     * dessous en mode bord-a-bord) et en bas (bande du planificateur). A laisser libres pour que le
     * contenu cadre tienne dans ce qu'on voit REELLEMENT, et non sous une barre ou un panneau.
     */
    fun fitTo(
        west: Double, south: Double, east: Double, north: Double,
        topPaddingPx: Int = 0, bottomPaddingPx: Int = 0,
    ) {
        val m = map ?: return
        if (west == 0.0 && east == 0.0) return
        val b = LatLngBounds.Builder().include(LatLng(north, east)).include(LatLng(south, west)).build()
        m.easeCamera(CameraUpdateFactory.newLatLngBounds(b, 90, 90 + topPaddingPx, 90, 90 + bottomPaddingPx))
    }

    fun handleTap(latLng: LatLng, screen: PointF) {
        // Marqueur deja selectionne au lever du doigt (handleFastTap) : ne rien refaire ici.
        if (fastPicked) return
        onRawTap?.let { it(latLng.longitude, latLng.latitude); return }
        markerPickAt(screen)?.let { it(); return }
        lineKeyAt(screen)?.let { onPickLine?.invoke(it, latLng.longitude, latLng.latitude); return }
        onTapEmpty?.invoke()
    }

    /**
     * Appui long : ne vaut que sur un endroit quelconque de la carte.
     *
     * Un mode de saisie exclusif le neutralise, comme il neutralise la selection : la ou tout tap pose un
     * point, un appui long est un tap qui a dure, et non un second geste a interpreter.
     *
     * La meme interrogation que le tap ecarte les traces et les marqueurs, avec les memes tolerances : une
     * epingle qu'un tap selectionne doit se comporter en epingle sous un doigt qui s'attarde.
     */
    fun handleLongPress(latLng: LatLng, screen: PointF) {
        if (onRawTap != null) return
        val cb = onLongPressEmpty ?: return
        if (markerPickAt(screen) != null || lineKeyAt(screen) != null) return
        cb(latLng.longitude, latLng.latitude)
    }

    private fun emptyFc() = "{\"type\":\"FeatureCollection\",\"features\":[]}"
}

/**
 * La carte, et le controleur qui la pilote.
 *
 * [gesturesEnabled] et [destroyOnDispose] valent pour les cartes de decor - la vue d'ensemble d'une
 * emprise, par exemple : on ne les manipule pas, et elles vont et viennent avec un ecran, la ou la carte
 * principale vit aussi longtemps que l'application. Les valeurs par defaut sont celles de cette
 * derniere : gestes ouverts, et surtout pas de destruction a la sortie de composition.
 */
@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    controller: MapController,
    styleJson: String?,
    styleUrl: String?,
    gesturesEnabled: Boolean = true,
    destroyOnDispose: Boolean = false,
    onReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val mapView = remember { MapLibre.getInstance(context); MapView(context).apply { onCreate(null) } }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            if (destroyOnDispose) mapView.onDestroy()
        }
    }

    LaunchedEffect(controller) { controller.onStyleApplied = onReady }
    LaunchedEffect(styleJson, styleUrl) { controller.requestStyle(styleJson, styleUrl) }

    AndroidView(modifier = modifier, factory = {
        mapView.also { mv ->
            // Evenements tactiles bruts : servent au tap rapide (selection d'un marqueur des le lever du
            // doigt). On renvoie false = evenement non consomme, MapLibre le traite ensuite normalement.
            mv.setOnTouchListener { _, e -> controller.onMapTouch(e); false }
            mv.getMapAsync { map ->
                controller.attachDensity(density)
                controller.attachContext(context)
                map.uiSettings.isAttributionEnabled = false
                map.uiSettings.isLogoEnabled = false
                controller.onMapReady(map)
                if (!gesturesEnabled) map.uiSettings.setAllGesturesEnabled(false)
                map.addOnMapClickListener { ll -> controller.handleTap(ll, map.projection.toScreenLocation(ll)); false }
                // false = evenement non consomme : MapLibre garde son comportement d'appui long.
                map.addOnMapLongClickListener { ll ->
                    controller.handleLongPress(ll, map.projection.toScreenLocation(ll)); false
                }
                map.addOnCameraIdleListener { controller.onCameraIdle?.invoke() }
                map.addOnCameraMoveListener { controller.onCameraMove?.invoke() }
                // Gestes déclenchés par l'UTILISATEUR uniquement (jamais les mouvements programmatiques
                // comme moveTo/centerOn/fitTo) : estompent le bouton GPS, et disent qu'un cadrage
                // automatique ne doit plus être rejoué - la vue est désormais celle qu'on a choisie.
                // Le zoom à deux doigts compte autant que le déplacement, d'où les DEUX écouteurs :
                // pincer pour regarder de plus près est tout aussi délibéré que faire glisser.
                map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                    override fun onMoveBegin(detector: MoveGestureDetector) { controller.onUserMoveBegin?.invoke() }
                    override fun onMove(detector: MoveGestureDetector) {}
                    override fun onMoveEnd(detector: MoveGestureDetector) {}
                })
                map.addOnScaleListener(object : MapLibreMap.OnScaleListener {
                    override fun onScaleBegin(detector: StandardScaleGestureDetector) { controller.onUserMoveBegin?.invoke() }
                    override fun onScale(detector: StandardScaleGestureDetector) {}
                    override fun onScaleEnd(detector: StandardScaleGestureDetector) {}
                })
            }
        }
    }) { /* pas de re-setStyle ici : géré par requestStyle */ }
}
