package fr.lc4918.trailog.map.offline

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * La forme de la zone tampon d'une trace - les points a moins de [radiusM] d'elle -, pour la dessiner.
 *
 * **Une surface, et non un trait epais.** Dessinee en trait de la largeur du couloir, la zone se couvrait
 * d'eventails de triangles plus fonces : a vingt kilometres d'epaisseur sur des segments de quelques
 * centaines de metres, les jonctions arrondies se chevauchent, et leur transparence s'additionne a chaque
 * recouvrement.
 *
 * La zone est calculee sur une grille fine - une maille d'environ un huitieme de la largeur -, puis rendue
 * en bandes horizontales jointives : des rectangles qui se touchent sans jamais se recouvrir, donc d'une
 * transparence uniforme. Le bord est arrondi, a une maille pres - le disque de rayon [radiusM] autour de
 * chaque maille de la trace.
 */
object CorridorShape {

    /** Mailles par rayon : assez pour un bord arrondi a l'oeil, assez peu pour quelques milliers de bandes. */
    private const val MAILLES_PAR_RAYON = 8

    /** Les bandes de la zone, en emprises (lon, lat). [points] en (lon, lat). */
    fun bands(points: List<Pair<Double, Double>>, radiusM: Double): List<Bbox> {
        if (points.isEmpty() || radiusM <= 0) return emptyList()
        val latMoy = points.sumOf { it.second } / points.size
        // Le zoom dont la tuile vaut environ un huitieme du rayon, a la latitude de la trace.
        val zf = log2(40_075_016.686 * cos(Math.toRadians(latMoy)) * MAILLES_PAR_RAYON / radiusM)
        val z = zf.toInt().coerceIn(1, 24)
        val n = (1L shl z).toDouble()
        val maille = 40_075_016.686 * cos(Math.toRadians(latMoy)) / n
        val r = max(1, ceil(radiusM / maille).toInt())

        fun tx(lon: Double) = (lon + 180.0) / 360.0 * n
        fun ty(lat: Double): Double {
            val a = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
            return (1.0 - ln(tan(a) + 1 / cos(a)) / PI) / 2.0 * n
        }
        fun lonOf(x: Double) = x / n * 360.0 - 180.0
        fun latOf(y: Double) = Math.toDegrees(atan(sinh(PI * (1 - 2 * y / n))))

        // La ligne de la trace, maille par maille, par pas d'une demi-maille.
        val ligne = HashSet<Long>()
        fun poser(x: Double, y: Double) { ligne += (floor(x).toLong() shl 32) or (floor(y).toLong() and 0xffffffffL) }
        val xs = points.map { tx(it.first) }
        val ys = points.map { ty(it.second) }
        poser(xs[0], ys[0])
        for (i in 0 until points.size - 1) {
            val dx = xs[i + 1] - xs[i]
            val dy = ys[i + 1] - ys[i]
            val pas = ceil(max(kotlin.math.abs(dx), kotlin.math.abs(dy)) * 2).toInt().coerceAtLeast(1)
            for (k in 1..pas) { val t = k.toDouble() / pas; poser(xs[i] + dx * t, ys[i] + dy * t) }
        }
        // Chaque maille de la ligne etend un disque de rayon r : par rangee, un intervalle de demi-largeur
        // sqrt(r^2 - dy^2). Les intervalles d'une meme rangee se fondent ensuite en bandes.
        val rangees = HashMap<Int, MutableList<IntArray>>()
        for (cle in ligne) {
            val cx = (cle shr 32).toInt()
            val cy = cle.toInt()
            for (d in -r..r) {
                val w = floor(sqrt((r * r - d * d).toDouble())).toInt()
                rangees.getOrPut(cy + d) { ArrayList() } += intArrayOf(cx - w, cx + w)
            }
        }
        val bandes = ArrayList<Bbox>()
        for ((y, intervalles) in rangees) {
            intervalles.sortBy { it[0] }
            var debut = intervalles[0][0]
            var fin = intervalles[0][1]
            fun emettre() {
                bandes += Bbox(west = lonOf(debut.toDouble()), south = latOf(y + 1.0),
                    east = lonOf(fin + 1.0), north = latOf(y.toDouble()))
            }
            for (k in 1 until intervalles.size) {
                val (a, b) = intervalles[k].let { it[0] to it[1] }
                if (a <= fin + 1) fin = max(fin, b) else { emettre(); debut = a; fin = b }
            }
            emettre()
        }
        return bandes
    }
}
