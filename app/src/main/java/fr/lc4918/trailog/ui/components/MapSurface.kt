package fr.lc4918.trailog.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * La surface de carte, posee en parametre de l'ecran plutot qu'appelee en dur.
 *
 * **Pourquoi cette indirection.** `MapLibreView` construit une `MapView`, qui charge les bibliotheques
 * natives de MapLibre. Sur la JVM elles n'existent pas : `MapLibre.getInstance` leve `UnsatisfiedLinkError`
 * et `MapView(context)` leve `MapLibreConfigurationException`. Un seul appel, ligne unique dans les 1800
 * de `MainScreen`, rendait donc l'ecran entier incomposable en test - et avec lui tout le cablage qu'il
 * est le seul a faire.
 *
 * Ce qu'on remplace en test est donc la SURFACE, et rien d'autre : les barres, les infobulles, les
 * panneaux, les dialogues et les effets restent ceux de production. Ce qui n'est pas teste ici se dit
 * aussi clairement : le rendu des tuiles, les gestes reels et tout ce que MapLibre calcule lui-meme.
 *
 * Le dialogue avec la carte, lui, passe deja par [MapController] : l'ecran y pose ses rappels (tap,
 * appui long, camera immobile), et la surface les declenche. Un test tient donc le meme fil que MapLibre
 * et peut jouer un tap sans carte.
 */
@Stable
interface MapSurface {
    @Composable
    fun Render(
        modifier: Modifier,
        controller: MapController,
        styleJson: String?,
        styleUrl: String?,
        onReady: () -> Unit,
    )
}

/** La vraie carte : ce que compose l'application. */
object MapLibreSurface : MapSurface {
    @Composable
    override fun Render(
        modifier: Modifier,
        controller: MapController,
        styleJson: String?,
        styleUrl: String?,
        onReady: () -> Unit,
    ) = MapLibreView(
        modifier = modifier, controller = controller,
        styleJson = styleJson, styleUrl = styleUrl, onReady = onReady,
    )
}
