package fr.lc4918.trailog.watch.vector

import kotlin.math.max
import kotlin.math.pow

/**
 * Le style du fond vectoriel (PLAN.IGN) sur la montre, en donnees : ce que le dessin ([WatchVectorRenderer]) lit, sans rien
 * decider lui-meme.
 *
 * Aucune couleur hors de la grille des 64 couleurs de l'ecran (composantes 00, 55, AA, FF) : le fond est dessine directement
 * dans ces couleurs, il n'y a donc rien a reduire ni a tramer. Les conventions graphiques sont celles du style officiel de
 * l'IGN, ramenees a cette grille : un sentier est tirete (c'est ce qui le distingue d'un chemin en outdoors), une piste
 * cyclable tiretee violette, les courbes de niveau en bistre (le brun de la grille le plus proche).
 */
internal object WatchVectorStyle {

    private fun grid(rgb: Int) = rgb or 0xFF000000.toInt()

    val BACKGROUND = grid(0xFFFFFF)
    val WATER = grid(0x55AAFF)
    val WATER_LINE = grid(0x55AAFF)
    val BUILDING_FILL = grid(0xAAAAAA)
    val BUILDING_PUBLIC_FILL = grid(0xFFAAAA)
    val BUILDING_CHURCH_FILL = grid(0xFFAAFF)
    val BUILDING_LINE = grid(0x555555)
    val ROAD_CASING = grid(0x555555)
    val ROAD_STREET_FILL = grid(0xFFFFFF)
    val ROAD_LOCAL_FILL = grid(0xFFFFAA)
    val ROAD_MAIN_FILL = grid(0xFFFF00)
    val RAIL = grid(0x000000)
    val CONTOUR = grid(0xAA5500)
    const val CONTOUR_WIDTH = 1.0f
    const val CONTOUR_MASTER_WIDTH = 1.6f
    const val BUILDING_LINE_WIDTH = 1.0f
    const val HYDRO_LINE_WIDTH = 2.0f
    const val RAIL_WIDTH = 1.5f

    /** Une surface : pleine, hachuree (obliques) ou semee de points. */
    enum class FillKind { SOLID, HATCH, DOTS }

    class Fill(val kind: FillKind, val color: Int)

    /** Couvert vegetal (couche ocs_vegetation_surf), par attribut `symbo`. */
    val VEGETATION: Map<String, Fill> = mapOf(
        "ZONE_BOISEE" to Fill(FillKind.SOLID, grid(0xAAFFAA)),
        "ZONE_FORET_FERMEE_FEUIL" to Fill(FillKind.SOLID, grid(0xAAFFAA)),
        "ZONE_FORET_FERMEE_CONI" to Fill(FillKind.SOLID, grid(0xAAFFAA)),
        "ZONE_FORET_FERMEE_MIXTE" to Fill(FillKind.SOLID, grid(0xAAFFAA)),
        "ZONE_FORET_OUVERTE" to Fill(FillKind.HATCH, grid(0xAAFFAA)),
        "ZONE_LANDE_LIGNEUSE" to Fill(FillKind.SOLID, grid(0xFFFFAA)),
        "ZONE_VIGNE" to Fill(FillKind.HATCH, grid(0xFFAAFF)),
        "ZONE_VERGER" to Fill(FillKind.HATCH, grid(0xFFAA55)),
    )

    /** Nature du sol (couche ocs_nature_sol_surf) : les eboulis en semis ; la roche se dit par ses hachures (cf. [LINES_BY_LAYER]). */
    val GROUND: Map<String, Fill> = mapOf("ZONE_EBOULIS" to Fill(FillKind.DOTS, grid(0xAAAAAA)))

    /** Un trait : largeur en pixels de la montre, tirets (vide = plein), couleur. */
    class Stroke(val width: Float, val dashes: FloatArray, val color: Int)

    /** Chemins (couche routier_chemin) par `symbo` ; un symbole inconnu prend [DEFAULT_PATH]. */
    val PATHS: Map<String, Stroke> = mapOf(
        "SENTIER" to Stroke(1.6f, floatArrayOf(5f, 3f), grid(0x555555)),
        "CHEMIN" to Stroke(1.6f, floatArrayOf(), grid(0x555555)),
        "RUE_PIETONNE" to Stroke(1.6f, floatArrayOf(1f, 3f), grid(0x555555)),
        "PISTE_CYCLABLE" to Stroke(2.0f, floatArrayOf(6f, 2f), grid(0xAA55AA)),
        "VOIE_VERTE" to Stroke(2.0f, floatArrayOf(6f, 2f), grid(0xAA55AA)),
        "ESCALIER" to Stroke(4.0f, floatArrayOf(1f, 1.5f), grid(0x555555)),
    )
    val DEFAULT_PATH = Stroke(1.6f, floatArrayOf(), grid(0x555555))

    /** Traits du relief et des sols, par couche puis `symbo` ; [DEFAULT_LINE_BY_LAYER] pour les autres symboles de la couche. */
    val LINES_BY_LAYER: Map<String, Map<String, Stroke>> = mapOf(
        "ocs_lin" to mapOf("COULEE_EBOULIS" to Stroke(1.0f, floatArrayOf(3f, 2f), grid(0xAAAAAA))),
        "oro_lin" to emptyMap(),
    )
    val DEFAULT_LINE_BY_LAYER: Map<String, Stroke> = mapOf(
        "ocs_lin" to Stroke(1.0f, floatArrayOf(), grid(0x555555)),
        "oro_lin" to Stroke(1.0f, floatArrayOf(2f, 2f), grid(0x555555)),
    )

    /** Limites (couche limite_lin) : commune, enceinte militaire, cloture. Pas de limites de parc : l'utilisateur n'en veut pas. */
    val LIMITS: Map<String, Stroke> = mapOf(
        "LIM_COMMUNE" to Stroke(1.5f, floatArrayOf(4f, 2f, 1f, 2f), grid(0xAA55AA)),
        "LIM_ENCEINTE_MILITAIRE" to Stroke(1.5f, floatArrayOf(4f, 1f, 2f, 5f), grid(0xFF5500)),
        "CLOTURE" to Stroke(1.0f, floatArrayOf(1.5f, 4f), grid(0x000000)),
    )

    /** Largeur de base des routes (couche routier_route), en pixels au niveau de detail 17,0. */
    private val ROAD_BASE_WIDTH: Map<String, Float> = mapOf(
        "NON_CLASSEE" to 4.5f, "NON_CLASSEE_RESTREINT" to 3.5f,
        "LOCALE_1" to 6f, "LOCALE_2" to 6f, "LOCALE_3" to 5f,
        "REGIONALE_1" to 8f, "REGIONALE_2" to 8f, "REGIONALE_3" to 7f,
        "NATIONALE_1" to 9f, "NATIONALE_2" to 9f, "AUTOROUTE" to 10f,
    )
    private const val DEFAULT_ROAD_WIDTH = 4.5f
    private const val ROAD_MIN_WIDTH = 2.5f
    private const val ROAD_WIDTH_EXPONENT = 0.6
    private const val ROAD_REFERENCE_ZOOM = 17.0

    /** Liseré de chaque cote d'une route : un pixel. */
    const val ROAD_CASING_EXTRA = 2.0f

    /**
     * Largeur d'une route a ce niveau de detail : elle suit le zoom moins vite que la carte (exposant 0,6) pour rester lisible
     * quand on s'eloigne, avec un plancher ([ROAD_MIN_WIDTH]) ; 16,0 donne environ les deux tiers de la largeur de 17,0.
     */
    fun roadWidth(symbo: String?, zoom: Double): Float {
        val base = ROAD_BASE_WIDTH[symbo] ?: DEFAULT_ROAD_WIDTH
        return max(ROAD_MIN_WIDTH, (base * 2.0.pow((zoom - ROAD_REFERENCE_ZOOM) * ROAD_WIDTH_EXPONENT)).toFloat())
    }

    fun roadFill(symbo: String?): Int = when {
        symbo == null -> ROAD_STREET_FILL
        symbo.startsWith("REGIONALE") || symbo.startsWith("NATIONALE") || symbo == "AUTOROUTE" -> ROAD_MAIN_FILL
        symbo.startsWith("LOCALE") -> ROAD_LOCAL_FILL
        else -> ROAD_STREET_FILL
    }
}
