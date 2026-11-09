package fr.lc4918.trailog.watch.vector

import fr.lc4918.trailog.watch.WatchTilePixels
import kotlin.math.abs

/**
 * Le style du fond Trailmap pour la montre : les couches OpenMapTiles (donnees OpenStreetMap) dessinees dans les couleurs du style
 * « bright » d'OpenFreeMap que Trailmap retouche, ramenees chacune a la couleur de la grille de la montre la plus proche (aucune
 * n'est choisie a la main). Les batiments, les routes, les chemins, les rails et les textes reprennent ceux du fond PLAN.IGN
 * ([WatchVectorStyle]) : un trait de sentier ou un batiment se lit de la meme facon d'un fond a l'autre.
 */
internal object WatchOsmStyle {

    /** La couleur de grille la plus proche de la teinte [hue] (degres), saturation et luminosite [saturation], [lightness] (0 a 1). */
    private fun hsl(hue: Double, saturation: Double, lightness: Double): Int {
        val chroma = (1 - abs(2 * lightness - 1)) * saturation
        val x = chroma * (1 - abs((hue / 60) % 2 - 1))
        val offset = lightness - chroma / 2
        val (red, green, blue) = when {
            hue < 60 -> Triple(chroma, x, 0.0)
            hue < 120 -> Triple(x, chroma, 0.0)
            hue < 180 -> Triple(0.0, chroma, x)
            hue < 240 -> Triple(0.0, x, chroma)
            hue < 300 -> Triple(x, 0.0, chroma)
            else -> Triple(chroma, 0.0, x)
        }
        val rgb = ((red + offset) * 255).toInt().coerceIn(0, 255).shl(16) or
            ((green + offset) * 255).toInt().coerceIn(0, 255).shl(8) or ((blue + offset) * 255).toInt().coerceIn(0, 255)
        return WatchTilePixels.PALETTE[WatchTilePixels.nearestPaletteIndexKeepingHue(rgb, HUE_TOLERANCE)] or 0xFF000000.toInt()
    }

    /** Ecart de teinte (degres) qu'une couleur du style peut prendre en passant a la grille : un vert reste vert, un bleu reste bleu. */
    private const val HUE_TOLERANCE = 40.0

    val WOOD = hsl(100.0, 0.59, 0.76)
    val GRASS = hsl(75.0, 0.41, 0.74)
    val PARK = hsl(100.0, 0.59, 0.76)
    val FARMLAND = hsl(75.0, 0.37, 0.81)
    val SAND = hsl(60.0, 0.46, 0.87)
    val WETLAND = hsl(185.0, 0.43, 0.74)
    val PITCH = hsl(100.0, 0.57, 0.72)
    val SCHOOL = hsl(50.0, 0.47, 0.81)
    val HOSPITAL = hsl(340.0, 0.37, 0.87)
    val INDUSTRIAL = hsl(230.0, 0.15, 0.86)
    val WATER = WatchVectorStyle.WATER
    val ROCK = WatchVectorStyle.GROUND.getValue("ZONE_EBOULIS").color

    /** Couverture du sol (couche landcover) par `class`, et par `subclass` pour ce que le style distingue. */
    fun landcover(cls: String?, subclass: String?): WatchVectorStyle.Fill? = when (cls) {
        "wood" -> WatchVectorStyle.Fill(WatchVectorStyle.FillKind.SOLID, WOOD)
        "grass" -> WatchVectorStyle.Fill(WatchVectorStyle.FillKind.SOLID, if (subclass in PARK_SUBCLASSES) PARK else GRASS)
        "farmland" -> WatchVectorStyle.Fill(WatchVectorStyle.FillKind.SOLID, FARMLAND)
        "sand" -> WatchVectorStyle.Fill(WatchVectorStyle.FillKind.SOLID, SAND)
        "wetland" -> WatchVectorStyle.Fill(WatchVectorStyle.FillKind.SOLID, WETLAND)
        "ice" -> WatchVectorStyle.Fill(WatchVectorStyle.FillKind.SOLID, WatchVectorStyle.BACKGROUND)
        "rock" -> WatchVectorStyle.Fill(WatchVectorStyle.FillKind.DOTS, ROCK)
        else -> null
    }

    private val PARK_SUBCLASSES = setOf("park", "garden", "village_green", "recreation_ground", "playground")

    /** Occupation du sol (couche landuse) par `class`. */
    fun landuse(cls: String?): WatchVectorStyle.Fill? = when (cls) {
        "pitch", "stadium" -> WatchVectorStyle.Fill(WatchVectorStyle.FillKind.SOLID, PITCH)
        "cemetery" -> WatchVectorStyle.Fill(WatchVectorStyle.FillKind.SOLID, FARMLAND)
        "school", "college", "university", "kindergarten", "education" -> WatchVectorStyle.Fill(WatchVectorStyle.FillKind.SOLID, SCHOOL)
        "hospital", "healthcare" -> WatchVectorStyle.Fill(WatchVectorStyle.FillKind.SOLID, HOSPITAL)
        "industrial" -> WatchVectorStyle.Fill(WatchVectorStyle.FillKind.SOLID, INDUSTRIAL)
        else -> null
    }

    /** Le symbole de route de [WatchVectorStyle.roadWidth] pour une `class` OpenMapTiles qui se trace comme une route, ou null. */
    fun roadSymbol(cls: String?): String? = when (cls) {
        "motorway" -> "AUTOROUTE"
        "trunk" -> "NATIONALE_1"
        "primary" -> "REGIONALE_1"
        "secondary" -> "REGIONALE_2"
        "tertiary" -> "LOCALE_1"
        "minor", "busway", "raceway" -> "NON_CLASSEE"
        "service" -> "NON_CLASSEE_RESTREINT"
        else -> null
    }

    /** Le trait d'un chemin (classes `path` et `track`) selon son `subclass`, ou null s'il ne se dessine pas (passage pour pietons...). */
    fun pathStroke(cls: String?, subclass: String?): WatchVectorStyle.Stroke? = when {
        cls == "track" -> WatchVectorStyle.PATHS.getValue("CHEMIN")
        cls != "path" -> null
        subclass == "cycleway" -> WatchVectorStyle.PATHS.getValue("PISTE_CYCLABLE")
        subclass == "steps" -> WatchVectorStyle.PATHS.getValue("ESCALIER")
        subclass == "crossing" -> null
        subclass == "footway" || subclass == "pedestrian" || subclass == "sidewalk" -> WatchVectorStyle.PATHS.getValue("RUE_PIETONNE")
        else -> WatchVectorStyle.PATHS.getValue("SENTIER")
    }

    /** Largeur du trait d'un cours d'eau selon sa `class`, ou null s'il ne se dessine pas. */
    fun waterwayWidth(cls: String?): Float? = when (cls) {
        "river", "canal" -> WatchVectorStyle.HYDRO_LINE_WIDTH
        "stream" -> 1.2f
        else -> null
    }
}
