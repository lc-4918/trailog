package fr.lc4918.trailog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
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

/** La part du cercle que l'arc couvre, et le temps d'un tour, en millisecondes. */
private const val SweepDegrees = 280f
private const val TurnMillis = 1_100L

/**
 * Le grand rond d'attente, pose au milieu de ce qui fait attendre.
 *
 * **Dessine ici plutot que pris a Material.** L'indicateur de Material 3 a ete redessine en 1.4 : la
 * taille qu'on lui demande vaut pour la place qu'il occupe, pas pour ce qu'il trace, et un rond de
 * 40 % de l'ecran sortait en pastille de quelques pixels callee dans un coin. Un arc trace au compas
 * fait exactement ce qu'on lui demande - meme diametre, meme trait, centre par construction.
 *
 * **L'angle vient de l'horloge des images, et non d'une animation.** Une `InfiniteTransition` laissait
 * le rond immobile, et une animation depend en outre de l'echelle d'animation du telephone : a zero,
 * elle saute a sa valeur finale et le rond se fige - ce qui, pour un temoin d'attente, est exactement
 * le contraire de ce qu'il doit faire. L'angle se lit donc a chaque image, sur le temps qui passe, et
 * il est LU EN COMPOSITION : lu dans la lambda de dessin, il aurait dependu de la facon dont Compose
 * invalide cette phase-la.
 *
 * **Sans fond.** Ce qui est dessous reste visible - la carte, l'ecran des reglages -, parce que ce n'est
 * pas une porte fermee : l'application n'est pas bloquee, elle prepare. Un voile gris par-dessus la carte
 * dirait le contraire, et pour une attente de quelques dixiemes de seconde il ferait clignoter l'ecran.
 *
 * **Il ne prend pas les taps non plus** : rien de cliquable ici, donc rien n'est intercepte.
 */
@Composable
fun BusySpinner(modifier: Modifier = Modifier) {
    var nanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        // withInfiniteAnimationFrameNanos, et non withFrameNanos : la seconde demande une image sans
        // fin, et un test de composition n'atteint alors JAMAIS le repos qu'il attend avant d'affirmer
        // quoi que ce soit - toute la suite se met a expirer. La premiere s'interrompt d'elle-meme sous
        // la politique que le harnais de test installe, et se comporte comme l'autre en vrai.
        while (true) {
            withInfiniteAnimationFrameNanos { nanos = it }
        }
    }
    // Lu ICI, dans la composition de la fonction elle-meme, et non dans la lambda de BoxWithConstraints
    // (qui se compose pendant la mise en page) ni dans celle du dessin : c'est le chemin le plus ordinaire
    // - l'angle change, la fonction se recompose, le dessin suit - et le seul qui ne suppose rien de la
    // facon dont Compose invalide ses autres phases.
    val depart = spinnerAngle(nanos)
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val diametre = maxWidth * WidthFraction
        val trait = diametre * StrokeFraction
        Canvas(Modifier.size(diametre).testTag("busy_spinner")) {
            // L'arc se trace sur la ligne MOYENNE du trait : sans ce retrait d'une demi-epaisseur, la
            // moitie exterieure du trait deborderait du carre et serait rognee.
            val e = trait.toPx()
            drawArc(
                color = SpinnerColor,
                startAngle = depart,
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
 * L'angle de l'arc a cet instant de l'horloge des images : un tour toutes les [TurnMillis].
 *
 * A part, et non noyee dans le dessin : c'est la seule chose ici qui se verifie sans ecran.
 */
internal fun spinnerAngle(frameNanos: Long): Float =
    (frameNanos / 1_000_000L % TurnMillis) * 360f / TurnMillis
