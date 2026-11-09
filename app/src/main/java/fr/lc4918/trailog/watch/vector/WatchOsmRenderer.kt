package fr.lc4918.trailog.watch.vector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import fr.lc4918.trailog.watch.WatchTileImages
import fr.lc4918.trailog.watch.WatchTiles

/**
 * Une tuile montre dessinee depuis les tuiles vectorielles OpenMapTiles (OpenStreetMap, via OpenFreeMap) qui la couvrent, dans les
 * couleurs de la grille ([WatchOsmStyle]) : le fond Trailmap. Meme geometrie et memes etapes que [WatchVectorRenderer] (surfaces,
 * routes, traits, texte), sur des tuiles dont le zoom maximal est 14 : le fond est agrandi aux niveaux de detail plus fins.
 */
object WatchOsmRenderer {

    /** Zoom le plus fin des tuiles OpenMapTiles d'OpenFreeMap ; au-dela, leur geometrie est agrandie. */
    const val MAX_SOURCE_ZOOM = 14

    /** Les indices de palette de [tile] ; cf. [WatchVectorRenderer.render] pour les parametres. */
    fun render(
        tile: WatchTiles.WatchTile, zoom: Double, sourceZoom: Int, withText: Boolean = true, sourceTile: (x: Int, y: Int) -> VectorTile?,
    ): IntArray? {
        val pieces = WatchTileImages.sourcePieces(tile, zoom, sourceZoom).mapNotNull { piece ->
            sourceTile(piece.x, piece.y)?.let { WatchVectorRenderer.Piece(piece, it) }
        }
        if (pieces.isEmpty()) return null
        val size = WatchTiles.WATCH_TILE_PIXELS
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(WatchVectorStyle.BACKGROUND)
        val canvas = Canvas(bitmap)
        pieces.forEach { drawSurfaces(canvas, it) }
        pieces.forEach { drawWaterways(canvas, it) }
        drawRoads(canvas, pieces, zoom)
        pieces.forEach { drawPaths(canvas, it) }
        if (withText) drawLabels(canvas, pieces)
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        bitmap.recycle()
        return WatchVectorRenderer.snapToPalette(pixels)
    }

    // --- surfaces : couverture du sol, occupation du sol, eau, batiments ---

    private fun drawSurfaces(canvas: Canvas, piece: WatchVectorRenderer.Piece) {
        piece.tile.layers["landcover"]?.let { layer ->
            for (feature in layer.features) {
                WatchOsmStyle.landcover(feature.text("class"), feature.text("subclass"))?.let { WatchVectorRenderer.fill(canvas, piece, layer, feature, it) }
            }
        }
        piece.tile.layers["landuse"]?.let { layer ->
            for (feature in layer.features) {
                WatchOsmStyle.landuse(feature.text("class"))?.let { WatchVectorRenderer.fill(canvas, piece, layer, feature, it) }
            }
        }
        piece.tile.layers["water"]?.let { layer ->
            for (feature in layer.features) {
                if (feature.text("class") == "swimming_pool") continue
                canvas.drawPath(WatchVectorRenderer.path(piece, layer, feature, true), WatchVectorRenderer.fillPaint(WatchOsmStyle.WATER))
            }
        }
        piece.tile.layers["building"]?.let { layer ->
            for (feature in layer.features) {
                canvas.drawPath(WatchVectorRenderer.path(piece, layer, feature, true), WatchVectorRenderer.fillPaint(WatchVectorStyle.BUILDING_FILL))
            }
            val outline = WatchVectorRenderer.strokePaint(WatchVectorStyle.Stroke(WatchVectorStyle.BUILDING_LINE_WIDTH, floatArrayOf(), WatchVectorStyle.BUILDING_LINE))
            for (feature in layer.features) canvas.drawPath(WatchVectorRenderer.buildingOutline(piece, layer, feature), outline)
        }
    }

    private fun drawWaterways(canvas: Canvas, piece: WatchVectorRenderer.Piece) {
        val layer = piece.tile.layers["waterway"] ?: return
        for (feature in layer.features) {
            if (feature.text("brunnel") == "tunnel") continue
            val width = WatchOsmStyle.waterwayWidth(feature.text("class")) ?: continue
            val paint = WatchVectorRenderer.strokePaint(WatchVectorStyle.Stroke(width, floatArrayOf(), WatchVectorStyle.WATER_LINE)).apply { strokeCap = Paint.Cap.ROUND }
            canvas.drawPath(WatchVectorRenderer.path(piece, layer, feature, false), paint)
        }
    }

    // --- routes : toutes les tuiles ensemble, un trace par classe de largeur ; les ponts passent avec les autres, les tunnels ne se voient pas ---

    private fun drawRoads(canvas: Canvas, pieces: List<WatchVectorRenderer.Piece>, zoom: Double) {
        val batch = WatchVectorRenderer.RoadBatch(zoom)
        for (piece in pieces) {
            val layer = piece.tile.layers["transportation"] ?: continue
            for (feature in layer.features) {
                if (feature.text("brunnel") == "tunnel") continue
                val symbol = WatchOsmStyle.roadSymbol(feature.text("class")) ?: continue
                batch.add(symbol, WatchVectorRenderer.path(piece, layer, feature, false))
            }
        }
        batch.draw(canvas, null)
    }

    // --- traits : chemins, pistes, escaliers, voies ferrees ---

    private fun drawPaths(canvas: Canvas, piece: WatchVectorRenderer.Piece) {
        val layer = piece.tile.layers["transportation"] ?: return
        val rail = WatchVectorRenderer.strokePaint(WatchVectorStyle.Stroke(WatchVectorStyle.RAIL_WIDTH, floatArrayOf(), WatchVectorStyle.RAIL))
        for (feature in layer.features) {
            if (feature.text("brunnel") == "tunnel") continue
            val cls = feature.text("class")
            if (cls == "rail" && feature.text("subclass") == "rail") {
                canvas.drawPath(WatchVectorRenderer.path(piece, layer, feature, false), rail)
                continue
            }
            val stroke = WatchOsmStyle.pathStroke(cls, feature.text("subclass")) ?: continue
            canvas.drawPath(WatchVectorRenderer.path(piece, layer, feature, false), WatchVectorRenderer.strokePaint(stroke))
        }
    }

    // --- texte : noms de rues, de cours d'eau, de lieux et de sommets ---

    private fun nameOf(feature: VectorTile.Feature): String? = feature.text("name") ?: feature.text("name:latin")

    private fun labelsOf(
        pieces: List<WatchVectorRenderer.Piece>, layerName: String, kind: WatchVectorLabels.Kind, size: Float, bold: Boolean, italic: Boolean,
        color: Int, accepts: (VectorTile.Feature) -> Boolean,
    ): List<WatchVectorLabels.Label> = pieces.flatMap { piece ->
        val layer = piece.tile.layers[layerName] ?: return@flatMap emptyList()
        layer.features.mapNotNull { feature ->
            if (!accepts(feature)) return@mapNotNull null
            val text = nameOf(feature)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val parts = feature.parts.map { part -> part.map { piece.x(it, layer) to piece.y(it, layer) } }
            WatchVectorLabels.Label(kind, text, parts, size, bold, italic, color)
        }
    }

    private val PLACE_CLASSES = setOf("city", "town", "village", "hamlet", "suburb", "island")

    private fun drawLabels(canvas: Canvas, pieces: List<WatchVectorRenderer.Piece>) {
        val labels = labelsOf(pieces, "transportation_name", WatchVectorLabels.Kind.STREET, WatchVectorStyle.TEXT_STREET_SIZE, true, false,
            WatchVectorStyle.TEXT_STREET) { it.text("brunnel") != "tunnel" } +
            labelsOf(pieces, "waterway", WatchVectorLabels.Kind.WATER, WatchVectorStyle.TEXT_WATER_SIZE, false, true,
                WatchVectorStyle.TEXT_WATER) { WatchOsmStyle.waterwayWidth(it.text("class")) != null } +
            labelsOf(pieces, "water_name", WatchVectorLabels.Kind.WATER, WatchVectorStyle.TEXT_WATER_SIZE, false, true,
                WatchVectorStyle.TEXT_WATER) { true } +
            labelsOf(pieces, "place", WatchVectorLabels.Kind.LOCALITY, WatchVectorStyle.TEXT_LOCALITY_SIZE, false, true,
                WatchVectorStyle.TEXT_LIGHT) { it.text("class") in PLACE_CLASSES } +
            labelsOf(pieces, "mountain_peak", WatchVectorLabels.Kind.BUILDING, WatchVectorStyle.TEXT_BUILDING_SIZE, false, false,
                WatchVectorStyle.TEXT_LIGHT) { it.text("class") == "peak" || it.text("class") == "saddle" }
        WatchVectorRenderer.drawLabelList(canvas, labels)
    }
}
