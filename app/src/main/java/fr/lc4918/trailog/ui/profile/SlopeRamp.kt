package fr.lc4918.trailog.ui.profile

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Couleurs des pentes, calquees sur la trame d'OruxMaps.
 *
 * **Une pente SIGNEE**, et non sa valeur absolue : la montee va du jaune au rouge, la descente du cyan au
 * bleu, et le plat (moins de 1 % dans un sens comme dans l'autre) est vert. La rampe precedente ne lisait
 * que l'intensite - une descente a 10 % y avait la couleur d'une montee a 10 %, et il fallait regarder le
 * graphique pour savoir dans quel sens on la prenait.
 *
 * Les reperes ont ete releves pixel par pixel sur la legende d'OruxMaps, a chaque graduation (1, 5, 10,
 * 15, 20 et 25 %, de part et d'autre). Entre deux reperes, la couleur s'interpole lineairement : au milieu
 * de chaque intervalle, c'est ce que la legende montre, a une unite pres par canal. Au-dela de 25 %, la
 * couleur du bout.
 *
 * **Des classes**, comme la legende d'OruxMaps, qui avance par demi-points : une pente prend la couleur du
 * bas de sa classe. La largeur se regle (cf. [ClassSteps]) ; 0,5 % reproduit la trame d'OruxMaps.
 */
object SlopeRamp {

    /** En deca, dans un sens comme dans l'autre, c'est du plat. */
    const val FlatPct = 1.0

    /** Au-dela, la couleur ne change plus. */
    const val MaxPct = 25.0

    /** Largeurs de classe proposees, en dixiemes de point : 0,5 %, 1 %, 2,5 %, 5 %. */
    val ClassSteps = listOf(5, 10, 25, 50)

    /** La largeur par defaut : la trame d'OruxMaps. */
    const val DefaultClassTenths = 5

    private val Flat = Color(0xFF75FB4C)

    /** Montee : du jaune a 1 % au rouge a 25 %. */
    private val climb = listOf(
        1.0 to Color(0xFFFFFF54), 5.0 to Color(0xFFF8D448), 10.0 to Color(0xFFF2A33A),
        15.0 to Color(0xFFED772F), 20.0 to Color(0xFFEB4D26), 25.0 to Color(0xFFEA3524),
    )

    /** Descente : du cyan a -1 % au bleu a -25 %. */
    private val descent = listOf(
        1.0 to Color(0xFF75FBFE), 5.0 to Color(0xFF5FCEF9), 10.0 to Color(0xFF4499F6),
        15.0 to Color(0xFF2962F7), 20.0 to Color(0xFF1437F5), 25.0 to Color(0xFF0005F5),
    )

    /** La couleur d'une pente [pct] (signee, en %), sans classe : ce que la rampe donne en ce point exact. */
    fun at(pct: Double): Color {
        val a = abs(pct)
        if (a < FlatPct) return Flat
        val stops = if (pct > 0) climb else descent
        val x = a.coerceAtMost(MaxPct)
        for (i in 1 until stops.size) {
            val (p0, c0) = stops[i - 1]; val (p1, c1) = stops[i]
            if (x <= p1) {
                val f = ((x - p0) / (p1 - p0)).toFloat()
                return Color(
                    red = c0.red + (c1.red - c0.red) * f,
                    green = c0.green + (c1.green - c0.green) * f,
                    blue = c0.blue + (c1.blue - c0.blue) * f,
                )
            }
        }
        return stops.last().second
    }

    /**
     * La pente qui represente la classe de [pct] : le bas de la classe, jamais moins que le seuil du plat,
     * jamais plus que le bout de la rampe. Signee comme [pct] ; 0 pour le plat.
     */
    fun classOf(pct: Double, classTenths: Int = DefaultClassTenths): Double {
        val a = abs(pct)
        if (a < FlatPct) return 0.0
        val step = classTenths.coerceAtLeast(1) / 10.0
        val lower = (floor(a / step + 1e-9) * step).coerceIn(FlatPct, MaxPct)
        return sign(pct) * lower
    }

    /** Couleur d'une pente, rangee dans sa classe. */
    fun colorFor(pct: Double, classTenths: Int = DefaultClassTenths): Color = at(classOf(pct, classTenths))

    /**
     * Meme couleur, ecrite "#RRGGBB" pour une feuille de style de carte, qui ne connait que des chaines.
     *
     * Composee a partir des canaux plutot que via `toArgb()` : la conversion resterait juste, mais elle
     * passe par le graphisme Android, et cette fonction doit rester verifiable sans emulateur.
     */
    fun hexFor(pct: Double, classTenths: Int = DefaultClassTenths): String = hex(colorFor(pct, classTenths))

    internal fun hex(c: Color): String {
        fun ch(v: Float) = (v * 255f).roundToInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(ch(c.red), ch(c.green), ch(c.blue))
    }

    /**
     * Les classes de la trame, de la plus forte montee a la plus forte descente : (bas de la classe,
     * couleur). Ce que dessine la legende, bande par bande.
     */
    fun bands(classTenths: Int = DefaultClassTenths): List<Pair<Double, Color>> {
        val step = classTenths.coerceAtLeast(1) / 10.0
        val lows = generateSequence(FlatPct) { prev ->
            val next = (floor(prev / step + 1e-9) + 1) * step
            if (next > MaxPct + 1e-9) null else next
        }.toList()
        return lows.reversed().map { it to at(it) } + (0.0 to Flat) + lows.map { -it to at(-it) }
    }
}
