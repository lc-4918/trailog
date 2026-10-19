package fr.lc4918.trailog.watch.vector

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Le placement du texte sur une tuile montre : noms de rues posees sur leur portion la plus droite, noms de lieux et de
 * batiments autour de leur point. Sans dessin (la mesure d'un texte est donnee par l'appelant), donc testable seul ; le dessin
 * est celui de [WatchVectorRenderer].
 *
 * Un nom trop long pour sa rue est d'abord reduit (15 % de moins), puis raccourci par mots entiers, avec un point final, sans
 * jamais finir sur un mot de liaison ("DE", "DU", "LA"...) ; s'il reste trop court (moins de six caracteres), il est abandonne.
 */
internal object WatchVectorLabels {

    enum class Kind { STREET, WATER, LOCALITY, BUILDING }

    /** Un texte a placer : [parts] sont les parties de sa geometrie en pixels de la tuile montre (un point pour un nom de lieu). */
    class Label(
        val kind: Kind, val text: String, val parts: List<List<Pair<Float, Float>>>, val size: Float,
        val bold: Boolean, val italic: Boolean, val color: Int,
    )

    /** Un texte place : son centre, son angle (degres, lisible : entre -90 et 90) et ce qu'il faut pour le dessiner. */
    class Placed(
        val text: String, val x: Float, val y: Float, val angleDegrees: Float, val size: Float,
        val bold: Boolean, val italic: Boolean, val color: Int, val width: Float,
    )

    private const val SHRINK = 0.85f
    private const val FIT_MARGIN = 1.1f
    private const val MIN_TRUNCATED_LENGTH = 6
    private const val CURVATURE_WEIGHT = 0.02f
    private const val DEDUPLICATION_CELL = 30f
    private val LINKING_WORDS = setOf("de", "du", "des", "la", "le", "les", "l'", "d'", "et", "au", "aux", "sur", "sous", "en", "a", "à")

    /**
     * Le nom a ecrire sur [available] pixels, a la taille [size] puis a 85 % de [size], puis raccourci : le texte, sa taille et sa
     * largeur, ou null s'il ne tient pas. [measure] donne la largeur d'un texte a une taille.
     */
    fun fit(text: String, size: Float, available: Float, measure: (String, Float) -> Float): Triple<String, Float, Float>? {
        val width = measure(text, size)
        if (available >= width * FIT_MARGIN) return Triple(text, size, width)
        val smaller = size * SHRINK
        val smallerWidth = measure(text, smaller)
        if (available >= smallerWidth * FIT_MARGIN) return Triple(text, smaller, smallerWidth)
        val words = text.split(" ").toMutableList()
        while (words.size > 1) {
            words.removeAt(words.size - 1)
            while (words.isNotEmpty() && words.last().lowercase() in LINKING_WORDS) words.removeAt(words.size - 1)
            if (words.isEmpty()) return null
            val candidate = words.joinToString(" ") + "."
            val candidateWidth = measure(candidate, smaller)
            if (candidate.length >= MIN_TRUNCATED_LENGTH && available >= candidateWidth * FIT_MARGIN) return Triple(candidate, smaller, candidateWidth)
        }
        return null
    }

    /** La ligne ([points]) reechantillonnee tous les pixels environ. */
    fun densify(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        if (points.isEmpty()) return points
        val dense = arrayListOf(points.first())
        for (index in 0 until points.size - 1) {
            val (ax, ay) = points[index]
            val (bx, by) = points[index + 1]
            val steps = hypot(bx - ax, by - ay).toInt().coerceAtLeast(1)
            for (step in 1..steps) dense.add((ax + (bx - ax) * step / steps) to (ay + (by - ay) * step / steps))
        }
        return dense
    }

    /**
     * Le debut de la fenetre de [window] points de [dense] la plus droite (le trace y est le plus proche de sa corde), a egalite la
     * plus proche du milieu de la ligne.
     */
    fun straightestWindow(dense: List<Pair<Float, Float>>, window: Int): Int {
        var best = 0
        var bestCost = Float.MAX_VALUE
        var start = 0
        while (start <= dense.size - 1 - window) {
            val end = start + window
            var length = 0f
            var index = start
            while (index < end) { length += hypot(dense[index + 1].first - dense[index].first, dense[index + 1].second - dense[index].second); index++ }
            val chord = hypot(dense[end].first - dense[start].first, dense[end].second - dense[start].second)
            val cost = (length - chord) + CURVATURE_WEIGHT * abs(start + window / 2f - dense.size / 2f)
            if (cost < bestCost) { bestCost = cost; best = start }
            start += 2
        }
        return best
    }

    /** L'angle (degres) d'une direction, retourne pour que le texte se lise de gauche a droite. */
    fun readableAngle(dx: Float, dy: Float): Float {
        var angle = atan2(dy, dx)
        if (angle > PI / 2) angle -= PI.toFloat()
        if (angle < -PI / 2) angle += PI.toFloat()
        return Math.toDegrees(angle.toDouble()).toFloat()
    }

    /**
     * Place les textes dans l'ordre : un doublon (meme texte au meme endroit, a 30 px pres : la meme rue vue depuis deux tuiles
     * sources) et un texte qui en recouvre un deja place sont abandonnes. Les textes dont le centre est loin de la tuile aussi.
     */
    fun place(labels: List<Label>, tileSize: Float, measure: (String, Float, Boolean, Boolean) -> Float): List<Placed> {
        val placed = ArrayList<Placed>()
        val boxes = ArrayList<FloatArray>()
        val seen = HashSet<Triple<String, Int, Int>>()
        for (label in labels) {
            val candidate = if (label.kind == Kind.STREET || label.kind == Kind.WATER) placeOnLine(label, measure) else placeAtPoint(label, measure)
            candidate ?: continue
            if (candidate.x < -tileSize / 2 || candidate.x > tileSize * 1.5f || candidate.y < -tileSize / 2 || candidate.y > tileSize * 1.5f) continue
            val key = Triple(candidate.text, (candidate.x / DEDUPLICATION_CELL).roundToInt(), (candidate.y / DEDUPLICATION_CELL).roundToInt())
            if (!seen.add(key)) continue
            val halfWidth = candidate.width / 2 + 2
            val box = if (label.kind == Kind.STREET || label.kind == Kind.WATER) {
                floatArrayOf(candidate.x - halfWidth, candidate.y - 6, candidate.x + halfWidth, candidate.y + 6)
            } else floatArrayOf(candidate.x - halfWidth, candidate.y - 8, candidate.x + halfWidth, candidate.y + 4)
            if (boxes.any { overlaps(it, box) }) continue
            boxes.add(box)
            placed.add(candidate)
        }
        return placed
    }

    private fun overlaps(a: FloatArray, b: FloatArray) = !(b[2] < a[0] || b[0] > a[2] || b[3] < a[1] || b[1] > a[3])

    private fun placeOnLine(label: Label, measure: (String, Float, Boolean, Boolean) -> Float): Placed? {
        val part = label.parts.maxByOrNull { length(it) } ?: return null
        val dense = densify(part)
        val fitted = fit(label.text, label.size, dense.size.toFloat()) { text, size -> measure(text, size, label.bold, label.italic) } ?: return null
        val window = fitted.third.toInt().coerceAtLeast(1)
        if (dense.size <= window) return null
        val start = straightestWindow(dense, window)
        val a = dense[start]
        val b = dense[start + window]
        return Placed(fitted.first, (a.first + b.first) / 2, (a.second + b.second) / 2, readableAngle(b.first - a.first, b.second - a.second),
            fitted.second, label.bold, label.italic, label.color, fitted.third)
    }

    private fun placeAtPoint(label: Label, measure: (String, Float, Boolean, Boolean) -> Float): Placed? {
        val point = label.parts.firstOrNull()?.firstOrNull() ?: return null
        return Placed(label.text, point.first, point.second, 0f, label.size, label.bold, label.italic, label.color,
            measure(label.text, label.size, label.bold, label.italic))
    }

    private fun length(points: List<Pair<Float, Float>>): Float {
        var total = 0f
        for (index in 0 until points.size - 1) total += hypot(points[index + 1].first - points[index].first, points[index + 1].second - points[index].second)
        return total
    }
}
