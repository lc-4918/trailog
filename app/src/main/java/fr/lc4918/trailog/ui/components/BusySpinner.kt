package fr.lc4918.trailog.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

/** Le gris du rond d'attente, a 70 % : lisible sur une carte claire comme sur une vue satellite. */
private val SpinnerColor = Color(0xFF808080).copy(alpha = 0.7f)

/** Sa taille : 40 % de la largeur de ce qui l'accueille. */
private const val WidthFraction = 0.4f

/** L'epaisseur du trait, en part de son diametre : un grand rond a un trait a sa mesure. */
private const val StrokeFraction = 0.08f

/**
 * Le grand rond d'attente, pose au milieu de ce qui fait attendre.
 *
 * **Sans fond.** Ce qui est dessous reste visible - la carte, l'ecran des reglages -, parce que ce n'est
 * pas une porte fermee : l'application n'est pas bloquee, elle prepare. Un voile gris par-dessus la carte
 * dirait le contraire, et pour une attente de quelques dixiemes de seconde il ferait clignoter l'ecran.
 *
 * **Il ne prend pas les taps non plus** : rien de cliquable ici, donc rien n'est intercepte.
 */
@Composable
fun BusySpinner(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val diametre = maxWidth * WidthFraction
        CircularProgressIndicator(
            modifier = Modifier.size(diametre).testTag("busy_spinner"),
            color = SpinnerColor,
            strokeWidth = diametre * StrokeFraction,
            trackColor = Color.Transparent,
        )
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
