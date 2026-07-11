package fr.lc4918.trailog.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.PointF
import android.graphics.RectF
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

data class RenderLayer(val key: String, val geojson: String, val color: String)

class MapController {
    var map: MapLibreMap? = null
    var style: Style? = null
    var onPickPoint: ((String, String) -> Unit)? = null
    var onPickLine: ((String, Double, Double) -> Unit)? = null
    var onTapEmpty: (() -> Unit)? = null
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
    private val pinImages = hashSetOf<String>()

    // style en attente / appliqué (évite le rechargement à chaque recomposition)
    private var desiredJson: String? = null
    private var desiredUrl: String? = null
    private var desiredKey: String? = null
    private var appliedKey: String? = null

    fun attachDensity(d: Float) { density = d }
    fun attachContext(c: Context) { appContext = c.applicationContext }

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
            layerKeys.clear(); pinImages.clear()  // le nouveau style a vidé sources/couches
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
        }
        list.forEach { r ->
            val img = ensurePin(s, appContext, r.color, markerHeightPx)
            val source = s.getSourceAs<GeoJsonSource>(src(r.key))
            if (source == null) {
                s.addSource(GeoJsonSource(src(r.key), r.geojson))
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
            } else {
                source.setGeoJson(r.geojson)
                (s.getLayer(lineLayerId(r.key)) as? LineLayer)?.setProperties(PropertyFactory.lineColor(r.color))
                (s.getLayer(pointLayerId(r.key)) as? SymbolLayer)?.setProperties(PropertyFactory.iconImage(img))
            }
        }
    }

    fun clearLayers() = setLayers(emptyList(), 96f)

    /** Crée (une fois) une épingle de la couleur et taille demandées. */
    private fun ensurePin(s: Style, context: Context?, colorHex: String, heightPx: Float): String {
        val h = heightPx.toInt().coerceIn(24, 256)
        val name = "pin_${colorHex.removePrefix("#")}_$h"
        if (pinImages.add(name)) s.addImage(name, pinBitmap(context, android.graphics.Color.parseColor(colorHex), h))
        return name
    }

    /** Contour fixe (ic_pin_outline) + remplissage (ic_pin_fill) teinté dynamiquement à la couleur de la couche. */
    private fun pinBitmap(context: Context?, colorInt: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(h, h, Bitmap.Config.ARGB_8888)
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
                PropertyFactory.circleRadius(8f), PropertyFactory.circleColor("#ffffff"),
                PropertyFactory.circleStrokeColor("#1F6FB2"), PropertyFactory.circleStrokeWidth(4f)))
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
            val r0 = (accuracyMeters / mpp / Math.pow(2.0, zoom)).toFloat()
            val r22 = (r0 * Math.pow(2.0, 22.0)).toFloat()
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
        val m = map ?: return
        val tol = tapToleranceDp * density
        val rect = RectF(screen.x - tol, screen.y - tol, screen.x + tol, screen.y + tol)
        for (k in layerKeys) {
            val feats = m.queryRenderedFeatures(rect, pointLayerId(k))
            if (feats.isNotEmpty()) {
                val id = feats.first().getStringProperty("__id")
                if (id != null) { onPickPoint?.invoke(k, id); return }
            }
        }
        for (k in layerKeys) {
            if (m.queryRenderedFeatures(rect, lineLayerId(k)).isNotEmpty()) {
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
