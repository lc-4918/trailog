package fr.lc4918.trailog.ui.alert

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * La cloche qui SONNE : tant que l'alerte d'eloignement est declenchee, elle se balance autour de son
 * anneau, quelques allers-retours qui s'amortissent, puis un temps de silence, et recommence.
 *
 * Le rouge dit deja qu'on s'est ecarte ; le mouvement le fait voir du coin de l'oeil, telephone sur le
 * guidon, sans avoir a regarder le panneau. Le silence entre deux volees evite une agitation continue,
 * qui se lirait comme un defaut d'affichage plutot que comme une sonnerie.
 */
@Composable
fun Modifier.ringing(active: Boolean): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "cloche")
    val ms by transition.animateFloat(
        initialValue = 0f, targetValue = BellCycleMs.toFloat(),
        animationSpec = infiniteRepeatable(tween(BellCycleMs, easing = LinearEasing)),
        label = "volee",
    )
    // Le pivot est l'anneau, en haut de la cloche : c'est de la qu'elle pend, et c'est la qu'elle tourne.
    return graphicsLayer { rotationZ = bellSwing(ms); transformOrigin = TransformOrigin(0.5f, 0.12f) }
}

/** Duree d'un cycle : la volee, puis le silence. */
internal const val BellCycleMs = 1600

/**
 * L'angle de la cloche, en degres, a [ms] dans le cycle : des allers-retours de plus en plus courts, puis
 * l'immobilite jusqu'a la fin du cycle. Lineaire entre deux reperes.
 */
internal fun bellSwing(ms: Float): Float {
    val t = ms.coerceIn(0f, BellCycleMs.toFloat())
    for (i in 1 until SwingKeys.size) {
        val (t1, a1) = SwingKeys[i]
        if (t <= t1) {
            val (t0, a0) = SwingKeys[i - 1]
            return a0 + (a1 - a0) * (t - t0) / (t1 - t0)
        }
    }
    return 0f
}

/** Les reperes de la volee (instant en ms, angle en degres) : elle s'eteint a 700 ms. */
private val SwingKeys = listOf(
    0f to 0f, 100f to 16f, 220f to -14f, 340f to 11f, 460f to -8f, 580f to 4f, 700f to 0f,
    BellCycleMs.toFloat() to 0f,
)
