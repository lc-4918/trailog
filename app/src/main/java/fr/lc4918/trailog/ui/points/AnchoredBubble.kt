package fr.lc4918.trailog.ui.points

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.Layout

/**
 * Une infobulle posee a cote de ce qu'elle designe, et la carte qui se decale si elle n'y tient pas.
 *
 * **Pourquoi ce squelette existe.** L'ecran principal en porte quatre - un marqueur de trace, un point
 * d'interet, un lieu cherche, un point designe a l'appui long - et toutes les quatre repetaient les memes
 * trente lignes : mesurer la bulle, calculer son placement, le retenir, puis decaler la carte une fois si
 * le placement demande ne tenait pas. Seules quatre choses changeaient d'une bulle a l'autre, et ce sont
 * exactement les parametres ci-dessous.
 *
 * **Pourquoi une mesure et non une simple position.** Le placement depend de la TAILLE de la bulle, qui
 * depend de son contenu : une adresse de trois lignes ne se pose pas comme une de six. Il se calcule donc
 * dans la passe de layout, une fois la bulle mesuree - d'ou le [Layout] plutot qu'un `offset`.
 *
 * **Pourquoi le decalage n'a lieu qu'une fois.** La carte bouge, donc le point bouge a l'ecran, donc un
 * nouveau placement arrive - qui tient, cette fois. Sans [pannedFor], les deux se relanceraient l'un
 * l'autre, et un deplacement fait a la main serait aussitot defait.
 *
 * @param key ce que la bulle designe. Change d'identite = nouveau placement, et nouveau droit de decaler.
 * @param publish le placement mesure compte-t-il deja. Faux tant que le contenu charge : mesuree a la
 *   taille de son spinner, la bulle tient presque toujours a l'ecran, et le decalage - a usage unique -
 *   serait consomme sur une hauteur qui n'est pas la sienne.
 * @param panAllowed ce placement a-t-il le droit de decaler la carte. Faux en position AUTO, ou la carte
 *   ne bouge jamais, et faux tant qu'une camera lancee vers le point n'est pas arretee.
 * @param onPan de combien decaler la carte, en pixels d'ecran.
 * @param placement ou poser la bulle, une fois connues sa taille et celle de la vue.
 */
@Composable
fun AnchoredBubble(
    key: Any?,
    publish: Boolean,
    panAllowed: Boolean,
    onPan: (panX: Int, panY: Int) -> Unit,
    placement: (bubbleW: Int, bubbleH: Int, viewW: Int, viewH: Int) -> BubblePlacement,
    content: @Composable () -> Unit,
) {
    // Dernier placement calcule au layout : c'est lui qui porte le decalage de carte a appliquer.
    var placed by remember(key) { mutableStateOf<BubblePlacement?>(null) }
    Layout(content = content) { measurables, cs ->
        val p = measurables.first().measure(cs.copy(minWidth = 0, minHeight = 0))
        val pl = placement(p.width, p.height, cs.maxWidth, cs.maxHeight)
        if (publish && placed != pl) placed = pl
        layout(cs.maxWidth, cs.maxHeight) { p.place(pl.x, pl.y) }
    }
    var pannedFor by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(key, placed, panAllowed) {
        val pl = placed ?: return@LaunchedEffect
        if (!panAllowed || pannedFor == key) return@LaunchedEffect
        if (pl.panX != 0 || pl.panY != 0) onPan(pl.panX, pl.panY)
        pannedFor = key
    }
}
