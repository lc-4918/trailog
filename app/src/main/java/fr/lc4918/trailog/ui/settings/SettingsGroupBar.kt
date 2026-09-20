package fr.lc4918.trailog.ui.settings

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue

/**
 * Quel groupe de reglages on est en train de lire, quand son titre est deja passe en haut.
 *
 * Un onglet des reglages porte quatre ou cinq groupes et une trentaine de lignes : en defilant, le titre
 * qui dit de quoi il s'agit sort de l'ecran des la deuxieme rubrique, et l'on regle des curseurs sans
 * savoir a quoi ils se rapportent. Le titre courant se pose donc sous les onglets, et suit la lecture.
 *
 * **Chaque titre dit ou il se trouve**, en coordonnees de la fenetre (cf. [report]), et cet objet ne
 * garde que le dernier passe au-dessus du bord. Rien n'est calcule d'avance : un groupe qui apparait ou
 * disparait - le mode expert en cache plusieurs - se signale tout seul a la disposition suivante.
 */
@Stable
class GroupBarState {

    /** Le haut de la zone qui defile, en coordonnees de la fenetre. */
    var viewportTop by mutableFloatStateOf(0f)

    /**
     * La hauteur de la barre elle-meme (px).
     *
     * La barre est posee PAR-DESSUS le contenu : elle doit donc prendre le relais des que le titre glisse
     * sous sa bande, et non quand il a fini de sortir de l'ecran. Sans cela, le titre disparait derriere
     * la barre et l'on lit une bande vide pendant une trentaine de pixels de defilement.
     */
    var barHeight by mutableFloatStateOf(0f)

    private val titres = mutableStateMapOf<String, Float>()

    /**
     * Un titre de groupe se signale, par le BAS de sa ligne, en coordonnees de la fenetre.
     *
     * Son bas et non son haut : la barre prend le relais quand le titre a fini de passer derriere elle,
     * et c'est ce que dit son bord inferieur. Compare par le haut, la barre doublait le titre encore
     * lisible, des l'ouverture de l'onglet.
     */
    fun report(text: String, bottomY: Float) { titres[text] = bottomY }

    /** Le titre quitte l'ecran - onglet change, mode expert bascule : il ne doit plus compter. */
    fun forget(text: String) { titres.remove(text) }

    /**
     * Le groupe a afficher, ou null quand le premier titre est encore visible.
     *
     * Le dernier titre passe SOUS le bord haut, c'est-a-dire celui dont on lit les lignes : les titres
     * suivants sont encore plus bas, dans la partie visible.
     */
    val current: String?
        get() = titres.entries.filter { it.value < viewportTop + barHeight }.maxByOrNull { it.value }?.key
}

/** L'etat de la barre pour l'onglet affiche, s'il y en a un (cf. [GroupTitle]). */
val LocalGroupBar = compositionLocalOf<GroupBarState?> { null }
