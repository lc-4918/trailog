package fr.lc4918.trailog.ui.routes

import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import fr.lc4918.trailog.map.offline.OfflineDownloadState
import fr.lc4918.trailog.map.offline.OfflinePhase
import fr.lc4918.trailog.ui.geocode.GeocodeSearchState
import fr.lc4918.trailog.ui.mappoint.MapPointState
import fr.lc4918.trailog.ui.measure.TrackMeasureState
import fr.lc4918.trailog.ui.offline.OfflineFlowState
import fr.lc4918.trailog.ui.planner.RoutePlannerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ce que le retour Android referme, et dans quel ordre.
 *
 * **L'ordre EST la regle.** Compose intercepte par le dernier declare : chaque `BackHandler` ajoute
 * ci-dessous prend le pas sur tous ceux qui le precedent. Treize d'entre eux se disputent le meme geste, et
 * la seule chose qui les departage est leur ordre d'ecriture - une regle invisible, qu'un simple
 * deplacement de ligne defait sans que rien ne le signale. Elle tient donc en un seul endroit, ou on la
 * lit d'un bloc.
 *
 * **La gradation qui les gouverne** : le retour ferme d'abord ce qu'on REGARDE - un profil, un resultat,
 * une infobulle -, puis sort du MODE de saisie en cours, qui est ce qu'on est en train de faire. Le
 * planificateur fait exception dans l'autre sens : il se replie d'abord, et le second appui ne ferme pas -
 * il demande, parce qu'un trajet composé étape par étape ne se perd pas sur un geste distrait.
 */
@Composable
internal fun MapBackHandlers(
    drawerState: DrawerState,
    scope: CoroutineScope,
    offline: OfflineFlowState,
    planner: RoutePlannerState,
    geo: GeocodeSearchState,
    measure: TrackMeasureState,
    mapPoint: MapPointState,
    offlineDownload: OfflineDownloadState?,
    vm: MainViewModel,
    profileOpen: Boolean,
) {
    // 1er retour Android : ferme le profil s'il est affiché, au lieu du comportement par défaut.
    BackHandler(enabled = profileOpen) { vm.closeProfile() }
    // Priorité plus haute (déclaré après = intercepté en premier) : si le menu latéral est ouvert,
    // le retour le referme d'abord, avant tout autre comportement (y compris le retour système par défaut).
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    // Retour = annule le tracé bbox / ferme la config, plutôt que de quitter l'app (priorité la plus haute :
    // ces deux états ne sont jamais actifs simultanément, mais l'ordre reflète "config au-dessus du tracé").
    BackHandler(enabled = offline.drawingActive) { offline.cancelDrawing() }
    BackHandler(enabled = offline.configBbox != null) { offline.closeFlow() }
    // Géocodage, du plus général au plus prioritaire (déclaré après = intercepté en premier) : le retour
    // ferme d'abord le lieu affiché, puis la barre de recherche, puis sort du choix d'un point.
    // Le retour système replie d'abord la bande, puis DEMANDE avant de la fermer : un trajet composé
    // étape par étape ne se perd pas sur un geste qu'on fait sans y penser, celui-là même qui quitte
    // l'application - sauf s'il n'y a rien à perdre (cf. RoutePlannerState.isEmpty), auquel cas le premier
    // appui ferme tout de suite et le second n'a plus rien à demander. La croix de l'en-tête pose la même
    // question (cf. RoutePlannerState.requestClose), mais d'un seul geste visé au lieu de deux.
    // Placé avant les gestes du géocodage, plus anodins.
    BackHandler(enabled = planner.expanded) { planner.collapseOrClose() }
    BackHandler(enabled = planner.open && planner.collapsed) { planner.askCancel() }
    BackHandler(enabled = geo.place != null) { geo.clear() }
    BackHandler(enabled = geo.searchOpen) { geo.closeSearch() }
    // Mesure sur trace, du plus général au plus prioritaire : le retour ferme d'abord le résultat affiché,
    // et sort en priorité du choix des points, qui est le mode de saisie en cours.
    BackHandler(enabled = measure.mid != null) { measure.clear() }
    BackHandler(enabled = measure.picking) { measure.closeBand() }
    // Point désigné par un appui long, même gradation : le retour ferme d'abord son infobulle, et sort en
    // priorité du choix d'un point de référence, qui est le mode de saisie en cours.
    BackHandler(enabled = mapPoint.point != null) { mapPoint.clear() }
    BackHandler(enabled = mapPoint.pickingPoint) { mapPoint.cancelPickingPoint() }
    // Popup de progression ouverte : Retour la réduit (si en cours) ou la ferme (fin/erreur).
    BackHandler(enabled = offlineDownload?.minimized == false) {
        val dl = offlineDownload
        if (dl?.phase == OfflinePhase.RUNNING) vm.setOfflineDownloadMinimized(true) else vm.dismissOfflineDownload()
    }
}
