package fr.lc4918.trailog.ui.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.location.LocationControls
import fr.lc4918.trailog.ui.mappoint.MapPointState

/**
 * Les deux battements de la camera.
 *
 * **Pourquoi deux et non un.** [move] avance a CHAQUE image d'un deplacement, [idle] seulement quand la
 * carte s'immobilise. Ce qui doit rester colle a son point de carte suit le premier - l'orientation de la
 * boussole, les infobulles d'un lieu, qui glisseraient d'un saut a la fin du geste sinon. Tout le reste se
 * contente du second, et s'epargne d'etre recalcule soixante fois par seconde : l'echelle graphique, le
 * chargement des points d'interet, la position de l'infobulle d'un waypoint.
 */
@Stable
class MapTicks {
    var move by mutableIntStateOf(0)
        internal set
    var idle by mutableIntStateOf(0)
        internal set
}

/**
 * Pose les rappels que la carte envoie a l'ecran, et rend les deux compteurs qu'ils font avancer.
 *
 * Les rappels sont poses UNE FOIS, sur le controleur, et survivent a toutes les recompositions : c'est ce
 * qui les rend independants de ce que l'ecran affiche a un instant donne. D'ou [isPositioned], passe en
 * lambda plutot qu'en booleen - un booleen serait fige a sa valeur du jour de la pose.
 *
 * @param isPositioned la camera a-t-elle atteint sa place de depart. Tant que non, l'immobilisation
 *   n'enregistre RIEN : le cadrage initial n'est pas un choix de l'utilisateur, et l'ecrire dans la base
 *   ferait perdre l'endroit ou il avait laisse la carte.
 */
@Composable
internal fun rememberMapCameraCallbacks(
    controller: MapController,
    location: LocationControls,
    mapPoint: MapPointState,
    vm: MainViewModel,
    isPositioned: () -> Boolean,
): MapTicks {
    val ticks = remember { MapTicks() }
    LaunchedEffect(controller) {
        controller.onCameraMove = { ticks.move++ }
        controller.onUserMoveBegin = {
            // Un geste rend la carte à son propriétaire : le suivi de position se tait le temps du silence
            // (cf. MapFollow). Ce rappel ne se déclenche que sur un geste humain - centerOn et fitTo ne le
            // font pas -, sans quoi le suivi s'interdirait lui-même à son premier recentrage.
            location.noteUserGesture()
        }
        // Appui long sur un endroit quelconque : le contrôleur a déjà écarté les traces, les marqueurs et
        // les modes de saisie exclusifs (cf. handleLongPress), il ne reste ici qu'à ouvrir le point.
        controller.onLongPressEmpty = { lon, lat -> mapPoint.open(lon, lat) }
        controller.onCameraIdle = {
            ticks.idle++
            if (isPositioned()) controller.cameraState()?.let { (la, lo, z) -> vm.saveCameraState(la, lo, z) }
        }
    }
    return ticks
}
