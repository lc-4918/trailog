package fr.lc4918.trailog.elevation

import kotlin.math.floor

/**
 * Grille d'altitudes au format **AAIGrid** (ASCII Grid d'ESRI), celui sous lequel OpenTopography sert ses
 * modeles de terrain en texte.
 *
 * C'est ce format et non le GeoTIFF que demande l'application : lire un GeoTIFF suppose un decodeur
 * complet (entete TIFF, tuilage, compression, mots-cles GeoKey), la ou une grille ASCII se lit en un
 * entete de cinq lignes et une suite de nombres. Le prix est un corps plus lourd - environ cinq octets par
 * cellule -, que le decoupage en petites emprises garde raisonnable (cf. [ElevationFiller]).
 *
 * Sans dependance Android, comme le reste du calcul altimetrique : verifiable sans emulateur.
 */
class AsciiGrid(
    private val cols: Int,
    private val rows: Int,
    /** Centre de la cellule du coin sud-ouest, en degres. */
    private val x0: Double,
    private val y0: Double,
    /** Cote d'une cellule, en degres. */
    private val cell: Double,
    /** Valeur qui marque l'absence de mesure, quand l'entete en declare une. */
    private val noData: Double?,
    /** Altitudes, ligne du NORD en tete : c'est l'ordre du fichier. */
    private val z: DoubleArray,
) {

    /**
     * Altitude au point, **interpolee** entre les quatre centres de cellule voisins.
     *
     * Bilineaire et non "la cellule qui contient le point" : sur un modele a 90 m, prendre la valeur de la
     * cellule fait avancer le profil par marches, et chaque marche compte comme une montee puis une
     * descente dans le calcul du D+. L'interpolation ne cree pas de relief - elle rend la meme surface,
     * sans les marches de l'echantillonnage.
     *
     * Null hors de la grille, et null des qu'un des quatre voisins n'a pas de mesure : mieux vaut une
     * altitude absente qu'une altitude devinee au bord d'un trou du modele (cf. [ElevationFiller], qui
     * refuse alors la trace entiere plutot que de la combler).
     */
    fun sample(lon: Double, lat: Double): Double? {
        // Abscisses en cellules, depuis le centre du coin sud-ouest : x vers l'est, y vers le nord.
        val cx = (lon - x0) / cell
        val cy = (lat - y0) / cell
        // Une demi-cellule de tolerance aux bords : le point vise est cense etre dans l'emprise demandee,
        // mais celle-ci est arrondie par le service a ses propres cellules.
        if (cx < -0.5 || cy < -0.5 || cx > cols - 0.5 || cy > rows - 0.5) return null
        val i0 = floor(cx).toInt().coerceIn(0, cols - 1)
        val j0 = floor(cy).toInt().coerceIn(0, rows - 1)
        val i1 = (i0 + 1).coerceAtMost(cols - 1)
        val j1 = (j0 + 1).coerceAtMost(rows - 1)
        val tx = (cx - i0).coerceIn(0.0, 1.0)
        val ty = (cy - j0).coerceIn(0.0, 1.0)
        val z00 = at(i0, j0) ?: return null
        val z10 = at(i1, j0) ?: return null
        val z01 = at(i0, j1) ?: return null
        val z11 = at(i1, j1) ?: return null
        val south = z00 + (z10 - z00) * tx
        val north = z01 + (z11 - z01) * tx
        return south + (north - south) * ty
    }

    /** Altitude d'une cellule, reperee depuis le SUD ; null la ou le modele n'a pas de mesure. */
    private fun at(col: Int, rowFromSouth: Int): Double? {
        val v = z[(rows - 1 - rowFromSouth) * cols + col]
        if (noData != null && v == noData) return null
        // Garde-fou meme sans NODATA_value declare, et c'est le cas courant : les grilles Copernicus que
        // sert OpenTopography arrivent sans cette ligne d'entete. Les modeles marquent leurs trous par une
        // valeur sentinelle tres negative (-32768 pour le SRTM), et le point le plus bas des oceans est a
        // -11 000 m. Une valeur en dessous n'est pas une altitude, c'est un trou.
        return if (v < -11_000.0) null else v
    }

    companion object {
        /**
         * Lit une grille, ou null si le texte n'en est pas une.
         *
         * L'entete nomme ses valeurs (`ncols`, `xllcorner`...) et le corps n'est qu'une suite de nombres :
         * on lit donc les lignes tant qu'elles commencent par une lettre, puis tout le reste comme des
         * altitudes, sans se fier au decoupage en lignes - la specification autorise n'importe quel blanc
         * comme separateur.
         *
         * `xllcenter`/`yllcenter` sont acceptes a cote de `xllcorner`/`yllcorner` : les deux variantes
         * existent, et elles designent le coin ou le centre de la meme cellule.
         */
        fun parse(text: String): AsciiGrid? {
            var cols = 0; var rows = 0
            var xll = Double.NaN; var yll = Double.NaN; var cell = Double.NaN
            var noData: Double? = null
            var corner = true
            var body = 0
            for (line in text.lineSequence()) {
                val t = line.trim()
                if (t.isEmpty()) { body += line.length + 1; continue }
                if (!t[0].isLetter()) break
                val parts = t.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val v = parts[1].toDoubleOrNull()
                    when (parts[0].lowercase()) {
                        "ncols" -> cols = v?.toInt() ?: 0
                        "nrows" -> rows = v?.toInt() ?: 0
                        "xllcorner" -> { xll = v ?: Double.NaN; corner = true }
                        "yllcorner" -> yll = v ?: Double.NaN
                        "xllcenter" -> { xll = v ?: Double.NaN; corner = false }
                        "yllcenter" -> yll = v ?: Double.NaN
                        "cellsize" -> cell = v ?: Double.NaN
                        "nodata_value" -> noData = v
                    }
                }
                body += line.length + 1
            }
            if (cols <= 0 || rows <= 0 || cell.isNaN() || cell <= 0.0 || xll.isNaN() || yll.isNaN()) return null
            val z = DoubleArray(cols * rows)
            var i = 0
            // Balayage manuel plutot qu'un split : une grille de 180 x 180 porte 32 400 nombres, dont un
            // decoupage en liste ferait autant de chaines intermediaires.
            var p = body.coerceAtMost(text.length)
            val n = text.length
            while (p < n && i < z.size) {
                while (p < n && text[p].isWhitespace()) p++
                val start = p
                while (p < n && !text[p].isWhitespace()) p++
                if (p > start) {
                    z[i++] = text.substring(start, p).toDoubleOrNull() ?: return null
                }
            }
            if (i < z.size) return null
            // Le demi-pas separe le coin du centre de sa cellule : tout le calcul de [sample] raisonne en
            // centres, seuls points ou l'altitude est celle du modele.
            val half = if (corner) cell / 2 else 0.0
            return AsciiGrid(cols, rows, xll + half, yll + half, cell, noData, z)
        }
    }
}
