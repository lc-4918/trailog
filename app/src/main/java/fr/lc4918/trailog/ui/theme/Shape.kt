package fr.lc4918.trailog.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Les arrondis de l'application, du plus petit au plus grand :
 * - extraSmall (6) : pastilles, ligne tenue pendant un glisser-deposer ;
 * - small (8) : champs, options d'un menu, boutons ;
 * - medium (12) : cartes, listes de propositions, menus ;
 * - large (16) : bulles posees sur la carte, cartes des reglages ;
 * - extraLarge (24) : bandes du bas et dialogues.
 * Ce qui est rond de part en part (puces, boutons icone) prend CircleShape.
 */
internal val TrailogShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Les ecarts de l'application. Un pas de 4, et six crans seulement : entre deux blocs, on choisit un
 * cran, pas une valeur.
 */
object Spacing {
    /** Icone et libelle serres l'un contre l'autre. */
    val xxs = 2.dp
    /** Dans un meme controle. */
    val xs = 4.dp
    /** Entre deux champs, entre deux icones. */
    val s = 8.dp
    /** Entre deux blocs voisins. */
    val m = 12.dp
    /** Marge d'une bande ; entre deux sections. */
    val l = 16.dp
    /** Marge d'un ecran plein. */
    val xl = 24.dp
}
