package fr.lc4918.trailog.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag

/** Le gris du rond d'attente, a 70 % : lisible sur une carte claire comme sur une vue satellite. */
private val SpinnerColor = Color(0xFF808080).copy(alpha = 0.7f)

/** Sa taille : 40 % de la largeur de ce qui l'accueille. */
private const val WidthFraction = 0.4f

/** L'epaisseur du trait, en part de son diametre : un grand rond a un trait a sa mesure. */
private const val StrokeFraction = 0.08f

/** La part du cercle que l'arc couvre, et le temps d'un tour. */
private const val SweepDegrees = 280f
private const val TurnMillis = 1_100

/**
 * Le grand rond d'attente, pose au milieu de ce qui fait attendre.
 *
 * **Dessine ici plutot que pris a Material.** L'indicateur de Material 3 a ete redessine en 1.4 : la
 * taille qu'on lui demande vaut pour la place qu'il occupe, pas pour ce qu'il trace, et un rond de
 * 40 % de l'ecran sortait en pastille de quelques pixels callee dans un coin. Un arc trace au compas
 * fait exactement ce qu'on lui demande - meme diametre, meme trait, centre par construction.
 *
 * **Sans fond.** Ce qui est dessous reste visible - la carte, l'ecran des reglages -, parce que ce n'est
 * pas une porte fermee : l'application n'est pas bloquee, elle prepare. Un voile gris par-dessus la carte
 * dirait le contraire, et pour une attente de quelques dixiemes de seconde il ferait clignoter l'ecran.
 *
 * **Il ne prend pas les taps non plus** : rien de cliquable ici, donc rien n'est intercepte.
 */
@Composable
fun BusySpinner(modifier: Modifier = Modifier) {
    val rotation = rememberInfiniteTransition(label = "attente")
    val angle by rotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(TurnMillis, easing = LinearEasing)),
        label = "tour",
    )
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val diametre = maxWidth * WidthFraction
        val trait = diametre * StrokeFraction
        Canvas(Modifier.size(diametre).testTag("busy_spinner")) {
            // L'arc se trace sur la ligne MOYENNE du trait : sans ce retrait d'une demi-epaisseur, la
            // moitie exterieure du trait deborderait du carre et serait rognee.
            val e = trait.toPx()
            drawArc(
                color = SpinnerColor,
                startAngle = angle,
                sweepAngle = SweepDegrees,
                useCenter = false,
                topLeft = Offset(e / 2f, e / 2f),
                size = Size(size.width - e, size.height - e),
                style = Stroke(width = e, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * Laisse passer UNE image avant d'ouvrir ce qui est lourd a composer.
 *
 * **Pourquoi c'est necessaire.** Un ecran lourd et le rond qui l'annonce composent dans la meme passe :
 * l'image qui porterait le rond seul n'existe jamais, et l'attente reste aussi muette qu'avant. En
 * differant d'une image ce qui est lourd, le rond a le temps de paraitre.
 *
 * Rend faux tant que [demande] est faux, puis faux le temps d'une image, puis vrai.
 */
@Composable
fun afterFrame(demande: Boolean): Boolean {
    var pret by remember(demande) { mutableStateOf(false) }
    LaunchedEffect(demande) {
        if (!demande) return@LaunchedEffect
        withFrameNanos { }
        pret = true
    }
    return demande && pret
}
