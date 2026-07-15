package fr.lc4918.trailog.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
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
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import kotlin.math.hypot
import kotlin.math.pow

data class RenderLayer(val key: String, val uri: String, val revision: Long, val color: String)

class MapController {
    var map: MapLibreMap? = null
    var style: Style? = null
    /** (cle de couche, id du marqueur, lon, lat). Les coordonnees viennent de la geometrie interrogee :
     *  elles permettent de placer l'infobulle des le tap, sans attendre le chargement de la couche. */
    var onPickPoint: ((String, String, Double, Double) -> Unit)? = null
    var onPickLine: ((String, Double, Double) -> Unit)? = null
    var onTapEmpty: (() -> Unit)? = null
    /** Si défini, intercepte tout tap sur la carte AVANT le test de sélection point/ligne habituel
     *  (mode de saisie exclusif, ex. tracé de bounding box hors-ligne) : reçoit (lon, lat). */
    var onRawTap: ((Double, Double) -> Unit)? = null
    var onCameraIdle: (() -> Unit)? = null
    var onCameraMove: (() -> Unit)? = null
    var onUserMoveBegin: (() -> Unit)? = null
    var onStyleApplied: (() -> Unit)? = null
    var tapToleranceDp: Int = 16
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
        val m = map ?: return
        val tol = tapToleranceDp * density
        val rect = RectF(screen.x - tol, screen.y - tol, screen.x + tol, screen.y + tol)
        for (k in layerKeys) {
            val feats = m.queryRenderedFeatures(rect, pointLayerId(k))
            val hit = feats.firstOrNull()?.let { pickOf(k, it) }
            if (hit != null) {
                fastPicked = true
                hit()
                return
            }
        }
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
            layerKeys.clear(); pinImages.clear(); applied.clear()  // le nouveau style a vidé sources/couches
            onStyleApplied?.invoke()
        }
    }

    private fun src(key: String) = "src-$key"
    private fun lineLayerId(key: String) = "$key-ln"
    private fun pointLayerId(key: String) = "$key-pt"
    private fun addLayerSafe(layer: org.maplibre.android.style.layers.Layer) {
        val s = style ?: return
        if (s.getLayer("cursor-dot") != null) s.addLayerBelow(layer, "cursor-dot") else s.addLayer(layer)
    }

    private val lineGeometryFilter: Expression = Expression.any(
        Expression.eq(Expression.geometryType(), Expression.literal("LineString")),
        Expression.eq(Expression.geometryType(), Expression.literal("MultiLineString")),
    )
    private val pointGeometryFilter: Expression = Expression.eq(Expression.geometryType(), Expression.literal("Point"))

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

    /** Coins posés pendant le tracé de la bbox hors-ligne : rendus en croix "viseur" (pas les épingles
     *  habituelles) + contour du rectangle une fois 2 coins posés. Source/couches dédiées, indépendantes
     *  du système générique [setLayers]/[RenderLayer] (overlay propre à l'app, pas une couche importée). */
    fun setBboxDraw(points: List<Pair<Double, Double>>) {
        val s = style ?: return
        if (points.isEmpty()) {
            s.getLayer("bbox-draw-line")?.let { s.removeLayer(it) }
            s.getLayer("bbox-draw-pt")?.let { s.removeLayer(it) }
            s.getSource("bbox-draw-src")?.let { s.removeSource(it) }
            return
        }
        val geojson = bboxDrawGeoJson(points)
        val existing = s.getSourceAs<GeoJsonSource>("bbox-draw-src")
        if (existing == null) {
            s.addSource(GeoJsonSource("bbox-draw-src", geojson))
            addLayerSafe(LineLayer("bbox-draw-line", "bbox-draw-src").withProperties(
                PropertyFactory.lineColor("#FF6D00"), PropertyFactory.lineWidth(3f))
                .withFilter(lineGeometryFilter))
            val img = "bbox_crosshair"
            s.addImage(img, crosshairBitmap(android.graphics.Color.BLACK, (40 * density).toInt()))
            // Explicitement au-dessus du rectangle (pas seulement dans l'ordre d'ajout) : la croix doit
            // rester visible même là où elle touche le contour du rectangle.
            s.addLayerAbove(
                SymbolLayer("bbox-draw-pt", "bbox-draw-src").withProperties(
                    PropertyFactory.iconImage(img), PropertyFactory.iconSize(1f), PropertyFactory.iconAnchor("center"),
                    PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true))
                    .withFilter(pointGeometryFilter),
                "bbox-draw-line",
            )
        } else {
            existing.setGeoJson(geojson)
        }
    }

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
            if (features.isNotEmpty()) features.append(',')
            features.append(
                """{"type":"Feature","geometry":{"type":"LineString","coordinates":""" +
                    """[[$w,$south],[$e,$south],[$e,$n],[$w,$n],[$w,$south]]},"properties":{}}"""
            )
        }
        return """{"type":"FeatureCollection","features":[$features]}"""
    }

    /** Croix "+" simple : les deux traits se croisent exactement au centre (le point exact posé),
     *  contrairement à un viseur à cercle qui laisse un vide au milieu. */
    private fun crosshairBitmap(colorInt: Int, sizePx: Int): Bitmap {
        val size = sizePx.coerceIn(16, 128)
        val bmp = createBitmap(size, size)
        val c = AndroidCanvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt; style = Paint.Style.STROKE; strokeWidth = size * 0.07f
        }
        val cx = size / 2f; val cy = size / 2f
        c.drawLine(cx, 0f, cx, size.toFloat(), paint)
        c.drawLine(0f, cy, size.toFloat(), cy, paint)
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

    fun moveTo(lat: Double, lon: Double, zoom: Double) {
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom))
    }
    /** Recentre sur un point sans changer le niveau de zoom courant (bouton de recentrage GPS). */
    fun centerOn(lat: Double, lon: Double) {
        map?.easeCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lon)))
    }
    fun cameraState(): Triple<Double, Double, Double>? {
        val cp = map?.cameraPosition ?: return null
        val t = cp.target ?: return null
        return Triple(t.latitude, t.longitude, cp.zoom)
    }
    fun metersPerPixel(lat: Double): Double = map?.projection?.getMetersPerPixelAtLatitude(lat) ?: 0.0

    fun setCursor(lon: Double, lat: Double) {
        val s = style ?: return
        if (s.getSourceAs<GeoJsonSource>("cursor") == null) {
            s.addSource(GeoJsonSource("cursor", emptyFc()))
            s.addLayer(CircleLayer("cursor-dot", "cursor").withProperties(
                PropertyFactory.circleRadius(4f), PropertyFactory.circleColor("#ffffff"),
                PropertyFactory.circleStrokeColor("#1F6FB2"), PropertyFactory.circleStrokeWidth(2f)))
        }
        s.getSourceAs<GeoJsonSource>("cursor")?.setGeoJson(
            "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[$lon,$lat]},\"properties\":{}}")
    }
    fun clearCursor() { style?.getSourceAs<GeoJsonSource>("cursor")?.setGeoJson(emptyFc()) }

    /** Point de position GPS de l'utilisateur : même style que le repère "ma position" de Google Maps
     *  (rond bleu à contour blanc, entouré d'un cercle de précision semi-transparent dimensionné en mètres réels). */
    fun setUserLocation(lon: Double, lat: Double, accuracyMeters: Float) {
        val s = style ?: return
        if (s.getSourceAs<GeoJsonSource>("user-location") == null) {
            s.addSource(GeoJsonSource("user-location", emptyFc()))
            s.addLayer(CircleLayer("user-location-accuracy", "user-location").withProperties(
                PropertyFactory.circleColor("#4285F4"), PropertyFactory.circleOpacity(0.16f),
                PropertyFactory.circleStrokeColor("#4285F4"), PropertyFactory.circleStrokeOpacity(0.5f),
                PropertyFactory.circleStrokeWidth(1f)))
            s.addLayer(CircleLayer("user-location-dot", "user-location").withProperties(
                PropertyFactory.circleRadius(7f), PropertyFactory.circleColor("#4285F4"),
                PropertyFactory.circleStrokeColor("#ffffff"), PropertyFactory.circleStrokeWidth(2.5f)))
        }
        // rayon du cercle de précision en pixels écran, recalculé pour rester exact à tout niveau de zoom :
        // à échelle Web Mercator, mètres/pixel double à chaque niveau de zoom, donc rayon(z) = rayon(0) * 2^z ;
        // une interpolation exponentielle de base 2 entre deux points quelconques de cette courbe la reproduit
        // exactement (pas juste une approximation), pas besoin de la recalculer à chaque changement de zoom.
        val mpp = metersPerPixel(lat)
        val zoom = map?.cameraPosition?.zoom
        if (mpp > 0.0 && zoom != null && accuracyMeters > 0f) {
            val r0 = (accuracyMeters / mpp / 2.0.pow(zoom)).toFloat()
            val r22 = (r0 * 2.0.pow(22.0)).toFloat()
            (s.getLayer("user-location-accuracy") as? CircleLayer)?.setProperties(
                PropertyFactory.circleRadius(Expression.interpolate(
                    Expression.exponential(2f), Expression.zoom(),
                    Expression.stop(0f, r0), Expression.stop(22f, r22))))
        }
        s.getSourceAs<GeoJsonSource>("user-location")?.setGeoJson(
            "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[$lon,$lat]},\"properties\":{}}")
    }
    fun clearUserLocation() { style?.getSourceAs<GeoJsonSource>("user-location")?.setGeoJson(emptyFc()) }

    fun screenOf(lon: Double, lat: Double): PointF? = map?.projection?.toScreenLocation(LatLng(lat, lon))

    fun fitTo(west: Double, south: Double, east: Double, north: Double) {
        val m = map ?: return
        if (west == 0.0 && east == 0.0) return
        val b = LatLngBounds.Builder().include(LatLng(north, east)).include(LatLng(south, west)).build()
        m.easeCamera(CameraUpdateFactory.newLatLngBounds(b, 90))
    }

    fun handleTap(latLng: LatLng, screen: PointF) {
        // Marqueur deja selectionne au lever du doigt (handleFastTap) : ne rien refaire ici.
        if (fastPicked) return
        onRawTap?.let { it(latLng.longitude, latLng.latitude); return }
        val m = map ?: return
        val tol = tapToleranceDp * density
        val rect = RectF(screen.x - tol, screen.y - tol, screen.x + tol, screen.y + tol)
        for (k in layerKeys) {
            val feats = m.queryRenderedFeatures(rect, pointLayerId(k))
            val hit = feats.firstOrNull()?.let { pickOf(k, it) }
            if (hit != null) { hit(); return }
        }
        for (k in layerKeys) {
            val hit = m.queryRenderedFeatures(rect, lineLayerId(k)).isNotEmpty()
            if (hit) {
                onPickLine?.invoke(k, latLng.longitude, latLng.latitude); return
            }
        }
        onTapEmpty?.invoke()
    }

    private fun emptyFc() = "{\"type\":\"FeatureCollection\",\"features\":[]}"
}

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    controller: MapController,
    styleJson: String?,
    styleUrl: String?,
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
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
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
                map.addOnMapClickListener { ll -> controller.handleTap(ll, map.projection.toScreenLocation(ll)); false }
                map.addOnCameraIdleListener { controller.onCameraIdle?.invoke() }
                map.addOnCameraMoveListener { controller.onCameraMove?.invoke() }
                // geste de déplacement déclenché par l'utilisateur uniquement (pas les mouvements programmatiques
                // comme moveTo/centerOn/fitTo) : sert à estomper le bouton GPS quand la carte est bougée à la main.
                map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                    override fun onMoveBegin(detector: MoveGestureDetector) { controller.onUserMoveBegin?.invoke() }
                    override fun onMove(detector: MoveGestureDetector) {}
                    override fun onMoveEnd(detector: MoveGestureDetector) {}
                })
            }
        }
    }) { /* pas de re-setStyle ici : géré par requestStyle */ }
}
