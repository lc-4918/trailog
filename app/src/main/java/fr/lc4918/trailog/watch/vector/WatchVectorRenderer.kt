package fr.lc4918.trailog.watch.vector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import fr.lc4918.trailog.watch.WatchTileImages
import fr.lc4918.trailog.watch.WatchTilePixels
import fr.lc4918.trailog.watch.WatchTiles
import kotlin.math.roundToInt

/**
 * Une tuile montre dessinee depuis les tuiles vectorielles PLAN.IGN qui la couvrent, directement dans les couleurs de la
 * grille de la montre ([WatchVectorStyle]) : pas de fond pastel a reduire, donc ni routes perdues ni grain.
 *
 * Meme geometrie que le fond raster ([WatchTileImages.sourcePieces]) : une tuile montre recouvre jusqu'a quatre tuiles du
 * fond, a une echelle quelconque (zoom fractionnaire). Les couches sont dessinees par etapes sur l'ensemble des tuiles
 * sources avant de passer a l'etape suivante : surfaces, courbes de niveau, routes, puis traits. Un dessin tuile par tuile
 * ferait recouvrir les routes d'une tuile par le liseré de sa voisine, au bord.
 *
 * Les routes d'une meme classe de largeur sont tracees d'un seul trace : le liseré de chaque troncon n'a plus de bout
 * arrondi qui coupe la rue au milieu, et une rue secondaire s'ouvre dans le liseré de la rue qu'elle rejoint.
 */
object WatchVectorRenderer {

    private const val HATCH_STEP = 4f
    private const val DOT_STEP = 4
    private const val NATURAL_ROAD_LEVEL = "n0"

    /** Fin du `symbo` des couches de ce qui passe au-dessus (routier_route_sup, routier_chemin_sup) : le meme symbole que celui du sol, sans elle. */
    private const val OVERPASS_SUFFIX = "_SUP"

    private const val TEXT_BASELINE = 3f

    /**
     * Les indices de palette (cf. [WatchTilePixels.PALETTE]) de [tile], ligne par ligne, ou null si aucune tuile source n'est
     * lisible. [withText] a false ne dessine que la carte (essais du trace sans le texte par-dessus). [sourceTile] donne la tuile PLAN.IGN (x, y au zoom [sourceZoom]) ou null.
     */
    fun render(
        tile: WatchTiles.WatchTile, zoom: Double, sourceZoom: Int, withText: Boolean = true, sourceTile: (x: Int, y: Int) -> VectorTile?,
    ): IntArray? {
        val pieces = WatchTileImages.sourcePieces(tile, zoom, sourceZoom).mapNotNull { piece ->
            sourceTile(piece.x, piece.y)?.let { Piece(piece, it) }
        }
        if (pieces.isEmpty()) return null
        val size = WatchTiles.WATCH_TILE_PIXELS
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(WatchVectorStyle.BACKGROUND)
        val canvas = Canvas(bitmap)
        pieces.forEach { drawSurfaces(canvas, it) }
        pieces.forEach { drawContours(canvas, it) }
        drawRoads(canvas, pieces, zoom)
        pieces.forEach { drawLines(canvas, it) }
        if (withText) drawLabels(canvas, pieces)
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        bitmap.recycle()
        return snapToPalette(pixels)
    }

    /** Une tuile source et la place qu'elle prend dans la tuile montre ; [toPixels] passe de ses coordonnees a celles de la tuile montre. */
    private class Piece(val position: WatchTileImages.SourcePiece, val tile: VectorTile) {
        fun scale(layer: VectorTile.Layer) = (position.right - position.left) / layer.extent
        fun x(point: VectorTile.Point, layer: VectorTile.Layer) = position.left + point.x * scale(layer)
        fun y(point: VectorTile.Point, layer: VectorTile.Layer) = position.top + point.y * scale(layer)
    }

    private fun path(piece: Piece, layer: VectorTile.Layer, feature: VectorTile.Feature, closed: Boolean): Path {
        val path = Path()
        path.fillType = Path.FillType.EVEN_ODD
        for (part in feature.parts) {
            part.forEachIndexed { index, point ->
                val x = piece.x(point, layer)
                val y = piece.y(point, layer)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (closed) path.close()
        }
        return path
    }

    private fun strokePaint(stroke: WatchVectorStyle.Stroke) = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = stroke.width
        color = stroke.color
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.ROUND
        if (stroke.dashes.isNotEmpty()) pathEffect = DashPathEffect(stroke.dashes, 0f)
    }

    private fun fillPaint(color: Int) = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; this.color = color }

    // --- surfaces : couvert vegetal, eboulis, eau, batiments ---

    private fun drawSurfaces(canvas: Canvas, piece: Piece) {
        piece.tile.layers["ocs_vegetation_surf"]?.let { layer ->
            for (feature in layer.features) WatchVectorStyle.VEGETATION[feature.text("symbo")]?.let { fill(canvas, piece, layer, feature, it) }
        }
        piece.tile.layers["ocs_nature_sol_surf"]?.let { layer ->
            for (feature in layer.features) WatchVectorStyle.GROUND[feature.text("symbo")]?.let { fill(canvas, piece, layer, feature, it) }
        }
        piece.tile.layers["hydro_surf"]?.let { layer ->
            for (feature in layer.features) canvas.drawPath(path(piece, layer, feature, true), fillPaint(WatchVectorStyle.WATER))
        }
        piece.tile.layers["routier_surf"]?.let { layer ->
            for (feature in layer.features) {
                // Le tablier d'un pont est de la couleur d'une rue, sous les routes qui le traversent.
                if (feature.text("symbo") == "PONT_SURF") canvas.drawPath(path(piece, layer, feature, true), fillPaint(WatchVectorStyle.ROAD_STREET_FILL))
            }
        }
        piece.tile.layers["bati_surf"]?.let { layer ->
            for (feature in layer.features) {
                val color = when (feature.text("symbo")) {
                    "BATI_PUBLIC" -> WatchVectorStyle.BUILDING_PUBLIC_FILL
                    "EGLISE_SURF" -> WatchVectorStyle.BUILDING_CHURCH_FILL
                    else -> WatchVectorStyle.BUILDING_FILL
                }
                canvas.drawPath(path(piece, layer, feature, true), fillPaint(color))
            }
            val outline = strokePaint(WatchVectorStyle.Stroke(WatchVectorStyle.BUILDING_LINE_WIDTH, floatArrayOf(), WatchVectorStyle.BUILDING_LINE))
            for (feature in layer.features) canvas.drawPath(buildingOutline(piece, layer, feature), outline)
        }
    }

    /**
     * Le contour d'un batiment, sans les aretes posees sur le bord de la tuile ou sur sa marge : ce sont des coupes de la tuile,
     * pas des murs, et elles feraient une ligne a la jonction de deux tuiles.
     */
    private fun buildingOutline(piece: Piece, layer: VectorTile.Layer, feature: VectorTile.Feature): Path {
        val outline = Path()
        for (ring in feature.parts) {
            for (index in 0 until ring.size - 1) {
                val from = ring[index]
                val to = ring[index + 1]
                val onVerticalCut = from.x == to.x && (from.x <= 0 || from.x >= layer.extent)
                val onHorizontalCut = from.y == to.y && (from.y <= 0 || from.y >= layer.extent)
                if (onVerticalCut || onHorizontalCut) continue
                outline.moveTo(piece.x(from, layer), piece.y(from, layer))
                outline.lineTo(piece.x(to, layer), piece.y(to, layer))
            }
        }
        return outline
    }

    private fun fill(canvas: Canvas, piece: Piece, layer: VectorTile.Layer, feature: VectorTile.Feature, fill: WatchVectorStyle.Fill) {
        val outline = path(piece, layer, feature, true)
        when (fill.kind) {
            WatchVectorStyle.FillKind.SOLID -> canvas.drawPath(outline, fillPaint(fill.color))
            WatchVectorStyle.FillKind.HATCH -> clipped(canvas, outline) {
                val paint = strokePaint(WatchVectorStyle.Stroke(1f, floatArrayOf(), fill.color))
                val size = WatchTiles.WATCH_TILE_PIXELS.toFloat()
                var offset = -size
                while (offset < size) { canvas.drawLine(offset, 0f, offset + size, size, paint); offset += HATCH_STEP }
            }
            WatchVectorStyle.FillKind.DOTS -> clipped(canvas, outline) {
                val paint = fillPaint(fill.color).apply { isAntiAlias = false }
                for (row in 0 until WatchTiles.WATCH_TILE_PIXELS step DOT_STEP / 2) {
                    val shift = if ((row / (DOT_STEP / 2)) % 2 == 0) 0 else DOT_STEP / 2
                    for (column in shift until WatchTiles.WATCH_TILE_PIXELS step DOT_STEP) canvas.drawRect(column.toFloat(), row.toFloat(), column + 1f, row + 1f, paint)
                }
            }
        }
    }

    private fun clipped(canvas: Canvas, area: Path, draw: () -> Unit) {
        canvas.save()
        canvas.clipPath(area)
        draw()
        canvas.restore()
    }

    // --- courbes de niveau : les maitresses plus epaisses ---

    private fun drawContours(canvas: Canvas, piece: Piece) {
        val layer = piece.tile.layers["oro_courbe"] ?: return
        val normal = strokePaint(WatchVectorStyle.Stroke(WatchVectorStyle.CONTOUR_WIDTH, floatArrayOf(), WatchVectorStyle.CONTOUR))
        val master = strokePaint(WatchVectorStyle.Stroke(WatchVectorStyle.CONTOUR_MASTER_WIDTH, floatArrayOf(), WatchVectorStyle.CONTOUR))
        for (feature in layer.features) {
            canvas.drawPath(path(piece, layer, feature, false), if (feature.text("symbo") == "CNV_MAITRESSE") master else normal)
        }
    }

    // --- routes : toutes les tuiles ensemble, un trace par classe de largeur ---

    private class RoadClass(val width: Float, val fill: Int)

    private fun drawRoads(canvas: Canvas, pieces: List<Piece>, zoom: Double) {
        val byClass = LinkedHashMap<RoadClass, Path>()
        val classes = HashMap<Pair<Float, Int>, RoadClass>()
        val junctions = Path().apply { fillType = Path.FillType.EVEN_ODD }
        for (piece in pieces) {
            piece.tile.layers["routier_surf"]?.let { layer ->
                for (feature in layer.features) if (feature.text("symbo") == "CARREFOUR") junctions.addPath(path(piece, layer, feature, true))
            }
            // Les routes au sol, puis celles qui passent au-dessus (ponts, viaducs) : elles se dessinent comme les autres, par-dessus l'eau.
            // Les routes en tunnel (routier_route_sou) ne se voient pas.
            for (layerName in listOf("routier_route", "routier_route_sup")) {
                val layer = piece.tile.layers[layerName] ?: continue
                for (feature in layer.features) {
                    if ((feature.text("niveau") ?: NATURAL_ROAD_LEVEL) != NATURAL_ROAD_LEVEL) continue
                    val symbo = feature.text("symbo")?.removeSuffix(OVERPASS_SUFFIX)
                    val width = (WatchVectorStyle.roadWidth(symbo, zoom) * 10).roundToInt() / 10f
                    val key = width to WatchVectorStyle.roadFill(symbo)
                    val roadClass = classes.getOrPut(key) { RoadClass(key.first, key.second) }
                    byClass.getOrPut(roadClass) { Path() }.addPath(path(piece, layer, feature, false))
                }
            }
        }
        val widths = byClass.keys.map { it.width }.distinct().sorted()
        for (casing in listOf(true, false)) {
            // Un carrefour efface les liseres des routes qui s'y croisent : sa surface, de la couleur d'une rue, se pose entre les liseres et les remplissages.
            if (!casing) canvas.drawPath(junctions, fillPaint(WatchVectorStyle.ROAD_STREET_FILL))
            for (width in widths) {
                for ((roadClass, roads) in byClass) {
                    if (roadClass.width != width) continue
                    val paint = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        this.strokeWidth = if (casing) width + WatchVectorStyle.ROAD_CASING_EXTRA else width
                        color = if (casing) WatchVectorStyle.ROAD_CASING else roadClass.fill
                    }
                    canvas.drawPath(roads, paint)
                }
            }
        }
    }

    // --- traits : chemins, ruisseaux, voies ferrees, roches, talus, limites ---

    private fun drawLines(canvas: Canvas, piece: Piece) {
        for (layerName in listOf("routier_chemin", "routier_chemin_sup")) {
            val layer = piece.tile.layers[layerName] ?: continue
            for (feature in layer.features) {
                val stroke = WatchVectorStyle.PATHS[feature.text("symbo")?.removeSuffix(OVERPASS_SUFFIX)] ?: WatchVectorStyle.DEFAULT_PATH
                canvas.drawPath(path(piece, layer, feature, false), strokePaint(stroke))
            }
        }
        // Une passerelle (pont reserve aux pietons) : l'axe, plein ; le pont routier (PONT_LIN) double la route qui le porte, il n'est pas redessine.
        piece.tile.layers["routier_liaison"]?.let { layer ->
            for (feature in layer.features) {
                if (feature.text("symbo") == "PONT_PASSERELLE") canvas.drawPath(path(piece, layer, feature, false), strokePaint(WatchVectorStyle.FOOTBRIDGE))
            }
        }
        piece.tile.layers["hydro_reseau"]?.let { layer ->
            val paint = strokePaint(WatchVectorStyle.Stroke(WatchVectorStyle.HYDRO_LINE_WIDTH, floatArrayOf(), WatchVectorStyle.WATER_LINE)).apply { strokeCap = Paint.Cap.ROUND }
            for (feature in layer.features) canvas.drawPath(path(piece, layer, feature, false), paint)
        }
        piece.tile.layers["ferre"]?.let { layer ->
            val paint = strokePaint(WatchVectorStyle.Stroke(WatchVectorStyle.RAIL_WIDTH, floatArrayOf(), WatchVectorStyle.RAIL)).apply { strokeCap = Paint.Cap.ROUND }
            for (feature in layer.features) canvas.drawPath(path(piece, layer, feature, false), paint)
        }
        for ((layerName, defaultStroke) in WatchVectorStyle.DEFAULT_LINE_BY_LAYER) {
            val layer = piece.tile.layers[layerName] ?: continue
            val specific = WatchVectorStyle.LINES_BY_LAYER[layerName].orEmpty()
            for (feature in layer.features) {
                canvas.drawPath(path(piece, layer, feature, false), strokePaint(specific[feature.text("symbo")] ?: defaultStroke))
            }
        }
        piece.tile.layers["limite_lin"]?.let { layer ->
            for (feature in layer.features) {
                val stroke = WatchVectorStyle.LIMITS[feature.text("symbo")] ?: continue
                canvas.drawPath(path(piece, layer, feature, false), strokePaint(stroke))
            }
        }
    }

    // --- texte : noms de rues, de ruisseaux, de lieux et de batiments ---

    private val textPaints = HashMap<Pair<Boolean, Boolean>, Paint>()

    private fun textPaint(bold: Boolean, italic: Boolean): Paint = textPaints.getOrPut(bold to italic) {
        val style = when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL }
        Paint().apply { isAntiAlias = true; typeface = Typeface.create(Typeface.SANS_SERIF, style); isFakeBoldText = bold }
    }

    private fun measure(text: String, size: Float, bold: Boolean, italic: Boolean): Float =
        textPaint(bold, italic).let { it.textSize = size; it.measureText(text) }

    private fun labelsOf(
        pieces: List<Piece>, layerName: String, kind: WatchVectorLabels.Kind, size: Float, bold: Boolean, italic: Boolean, color: Int,
        textOf: (VectorTile.Feature) -> String?,
    ): List<WatchVectorLabels.Label> = pieces.flatMap { piece ->
        val layer = piece.tile.layers[layerName] ?: return@flatMap emptyList()
        layer.features.mapNotNull { feature ->
            val text = textOf(feature)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val parts = feature.parts.map { part -> part.map { piece.x(it, layer) to piece.y(it, layer) } }
            WatchVectorLabels.Label(kind, text, parts, size, bold, italic, color)
        }
    }

    private fun drawLabels(canvas: Canvas, pieces: List<Piece>) {
        val labels = labelsOf(pieces, "toponyme_routier_odonyme_lin", WatchVectorLabels.Kind.STREET, WatchVectorStyle.TEXT_STREET_SIZE, true, false,
            WatchVectorStyle.TEXT_STREET) { it.text("nom_gauche") ?: it.text("nom_desabrege") } +
            labelsOf(pieces, "toponyme_hydro_lin", WatchVectorLabels.Kind.WATER, WatchVectorStyle.TEXT_WATER_SIZE, false, true,
                WatchVectorStyle.TEXT_WATER) { it.text("texte") } +
            labelsOf(pieces, "toponyme_localite_ponc", WatchVectorLabels.Kind.LOCALITY, WatchVectorStyle.TEXT_LOCALITY_SIZE, false, true,
                WatchVectorStyle.TEXT_LIGHT) { it.text("texte") } +
            labelsOf(pieces, "toponyme_bati_ponc", WatchVectorLabels.Kind.BUILDING, WatchVectorStyle.TEXT_BUILDING_SIZE, false, false,
                WatchVectorStyle.TEXT_LIGHT) { it.text("texte") }
        val placed = WatchVectorLabels.place(labels, WatchTiles.WATCH_TILE_PIXELS.toFloat(), ::measure)
        for (label in placed) {
            val halo = Paint(textPaint(label.bold, label.italic)).apply {
                textSize = label.size; style = Paint.Style.STROKE; strokeWidth = WatchVectorStyle.TEXT_HALO_WIDTH
                strokeJoin = Paint.Join.ROUND; color = WatchVectorStyle.TEXT_HALO
            }
            val ink = Paint(textPaint(label.bold, label.italic)).apply { textSize = label.size; style = Paint.Style.FILL; color = label.color }
            if (label.path.size >= 2) {
                val path = Path().apply {
                    moveTo(label.path.first().first, label.path.first().second)
                    for (point in label.path.drop(1)) lineTo(point.first, point.second)
                }
                canvas.drawTextOnPath(label.text, path, label.pathOffset, TEXT_BASELINE, halo)
                canvas.drawTextOnPath(label.text, path, label.pathOffset, TEXT_BASELINE, ink)
                continue
            }
            canvas.save()
            canvas.translate(label.x, label.y)
            canvas.rotate(label.angleDegrees)
            canvas.drawText(label.text, -label.width / 2, TEXT_BASELINE, halo)
            canvas.drawText(label.text, -label.width / 2, TEXT_BASELINE, ink)
            canvas.restore()
        }
    }

    /**
     * Chaque pixel a la couleur de grille la plus proche : les bords anticrenelés entre deux couleurs de la grille retombent
     * sur l'une des deux ou sur leur milieu (blanc et gris fonce donnent le gris moyen, qui est dans la grille).
     */
    private fun snapToPalette(pixels: IntArray): IntArray {
        val cache = HashMap<Int, Int>()
        return IntArray(pixels.size) { index ->
            val rgb = pixels[index] and 0xFFFFFF
            cache.getOrPut(rgb) { WatchTilePixels.nearestPaletteIndex(rgb) }
        }
    }
}
