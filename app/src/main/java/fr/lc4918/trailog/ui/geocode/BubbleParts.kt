package fr.lc4918.trailog.ui.geocode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R

/**
 * Les deux morceaux communs aux infobulles du geocodage - celle d'un lieu cherche par son nom, celle d'un
 * point designe par un appui long : l'adresse, et la croix qui referme.
 *
 * Ces deux bulles disent la meme chose de deux endroits obtenus autrement. Ce qu'elles ont en commun vit
 * donc ici, plutot qu'en deux exemplaires qui divergeraient a la premiere correction.
 */

/**
 * Une adresse de geocodage : d'un seul tenant tant qu'elle tient sur une ligne, sinon **un morceau par
 * ligne** - l'intitule, la voie, la commune (cf. Photon.labelParts).
 *
 * Les coupures sont choisies, non subies. Laissees au retour a la ligne, elles tomberaient a la derniere
 * espace qui rentre, et separeraient aussi bien un code postal de sa ville qu'un nom de voie en deux :
 * l'adresse se couperait la ou elle ne veut plus rien dire. On mesure donc d'abord la version d'un seul
 * tenant, et l'on passe aux morceaux des qu'elle deborde.
 *
 * Aux morceaux, et pas a deux lignes : coller l'intitule et la voie sur la premiere en renvoyant la seule
 * commune a la seconde economiserait une ligne, mais couperait la voie des que le couple deborde - or
 * c'est justement le cas ou l'on replie. Chaque morceau prend donc sa ligne.
 *
 * Mesurer coute une image de plus, invisible dans les deux infobulles qui s'en servent : l'adresse y
 * arrive apres coup (recherche ou geocodage inverse), et ne fait que remplacer un spinner.
 *
 * Commune a l'infobulle d'un lieu cherche et a celle d'un point designe : c'est la meme adresse, rendue
 * par le meme service, et rien ne justifierait qu'elle se coupe autrement de l'une a l'autre.
 *
 * Les morceaux ne suffisent pas toujours : une voie au nom long reste tronquee. Un appui long sur
 * l'adresse en montre alors le texte entier, dans une bulle posee au-dessus du doigt. Le rappel ne
 * s'arme que dans ce cas - repeter un texte deja lisible en entier n'apprendrait rien, et ferait
 * repondre au doigt un endroit qui n'a rien a dire.
 *
 * Enfin, une PREMIERE ligne assez longue pour atteindre la croix descend d'une bande et lui laisse le
 * haut de la bulle : la croix ne recouvre jamais de texte. Ce cas seul y oblige - lui reserver sa largeur
 * en toute circonstance couperait des adresses qui tiennent, et les lignes suivantes passent de toute
 * facon sous elle (cf. [CloseCorner]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressText(lines: List<String>, fontSp: Int, modifier: Modifier = Modifier) {
    val oneLine = lines.joinToString(", ")
    val splittable = lines.size > 1
    var wrapped by remember(lines) { mutableStateOf(false) }
    // Une mesure par ligne, relue a chaque passage plutot que retenue : les morceaux d'une adresse
    // repliee ne disent pas la meme chose que la ligne unique qu'ils remplacent, et garder la reponse de
    // celle-ci laisserait la croix en l'air au-dessus d'un texte qui, coupe, ne l'atteint plus.
    var single by remember(lines) { mutableStateOf(LineFit()) }
    val fits = remember(lines) { mutableStateListOf(*Array(lines.size) { LineFit() }) }
    val crossPx = with(LocalDensity.current) { CloseCornerSpace.roundToPx() }
    val body: @Composable () -> Unit = {
        if (wrapped && splittable) {
            Column {
                lines.forEachIndexed { i, part ->
                    AddressPart(part, fontSp) {
                        // Ecriture gardee : une liste d'etat notifie meme quand la valeur ne change pas,
                        // et la mesure se ferait alors relancer par sa propre publication, sans fin.
                        val fit = fitOf(it, crossPx)
                        if (fits[i] != fit) fits[i] = fit
                    }
                }
            }
        } else {
            AddressPart(oneLine, fontSp) {
                val fit = fitOf(it, crossPx)
                // Une adresse en morceaux se replie au lieu de se tronquer : c'est la ligne unique qui
                // deborde, pas l'adresse.
                if (fit.cut && splittable) wrapped = true
                single = fit
            }
        }
    }
    val shown = if (wrapped && splittable) fits else listOf(single)
    // Bande degagee pour la croix quand la PREMIERE ligne l'atteint : c'est le texte qui descend, la
    // croix reste dans l'angle de la bulle. Les lignes suivantes passent deja sous elle - les descendre
    // a leur tour creuserait un blanc pour un chevauchement qui n'existe pas.
    val placed = modifier.padding(top = if (shown.first().underCross) CloseCornerBand else 0.dp)
    // Une ligne unique qui deborde n'est pas tronquee mais repliee : le rappel n'a rien a dire encore.
    if (shown.none { it.cut } || (!wrapped && splittable)) {
        Box(placed) { body() }
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(oneLine, fontSize = fontSp.sp) } },
        state = rememberTooltipState(),
        modifier = placed,
        content = body,
    )
}

/** Ce qu'une ligne d'adresse a appris de sa mise en page : tronquee, et passant sous la croix. */
private data class LineFit(val cut: Boolean = false, val underCross: Boolean = false)

/** Lecture d'une mise en page de ligne : [crossPx] est la place que prend la croix dans l'angle. */
private fun fitOf(r: TextLayoutResult, crossPx: Int) = LineFit(
    cut = r.hasVisualOverflow,
    underCross = r.size.width > r.layoutInput.constraints.maxWidth - crossPx,
)

/** Largeur que prend la croix dans l'angle de la bulle : son bouton et la marge qui l'en ecarte. */
val CloseCornerSpace = 32.dp

/**
 * Hauteur rendue au texte quand il doit passer sous la croix.
 *
 * Ce n'est pas la hauteur de la croix mais ce qui lui MANQUE : le contenu d'une bulle commence deja une
 * douzaine de dp sous son bord, et la croix elle-meme y descend d'a peine plus de trente. Reculer d'une
 * hauteur pleine ouvrirait un blanc au-dessus du texte.
 */
private val CloseCornerBand = 20.dp

/**
 * La croix de fermeture, plaquee dans l'angle haut-droit de la bulle.
 *
 * Dans l'angle et non au fil du texte : c'est la ou on la cherche, et c'est le seul endroit qui ne bouge
 * pas quand le contenu grandit - adresse passee sur deux lignes, mesure qui s'ajoute sous son bouton.
 *
 * L'appelant la pose PAR-DESSUS son contenu (Box) sans lui reserver de place : l'adresse dispose ainsi
 * de toute la largeur de la bulle, et n'est tronquee que quand elle la depasse vraiment. Une ligne qui
 * atteindrait la croix se range d'elle-meme sous elle ([AddressText]) : les deux ne se recouvrent
 * jamais, et la bulle ne se creuse une bande en haut que dans ce cas-la.
 */
@Composable
fun CloseCorner(onClose: () -> Unit, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        IconButton(onClick = onClose, modifier = modifier.padding(2.dp).size(28.dp)) {
            Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(18.dp))
        }
    }
}

/** Une ligne d'adresse : jamais repliee d'elle-meme, c'est [AddressText] qui decide ou l'adresse se coupe. */
@Composable
private fun AddressPart(
    text: String,
    fontSp: Int,
    modifier: Modifier = Modifier,
    onLayout: (TextLayoutResult) -> Unit = {},
) {
    Text(text, fontSize = fontSp.sp, fontWeight = FontWeight.SemiBold,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = modifier,
        onTextLayout = onLayout)
}
