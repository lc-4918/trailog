package fr.lc4918.trailog.ui.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.location.TrackWatch
import fr.lc4918.trailog.ui.alert.OffTrackAlertState
import fr.lc4918.trailog.ui.alert.PlannedRouteLayerId
import fr.lc4918.trailog.ui.location.LocationControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ce qui allume et ce qui eteint la veille sur la trace suivie.
 *
 * **Tout est suspendu au capteur.** Sans position, il n'y a rien a projeter sur la trace : la liste des
 * candidates attend la premiere mesure pour se remplir, et la veille s'arrete d'elle-meme des que le
 * capteur s'eteint. Une trace suivie sans position ne dirait plus rien, et se reveillerait au hasard d'un
 * rallumage, des jours plus tard.
 *
 * L'ecart et le son ne se calculent pas ici mais dans le service (cf. LocationService.watchTrack) :
 * l'ecran eteint, la composition s'arrete, et une alerte qui ne se declenche que sous les yeux de celui
 * qu'elle doit prevenir n'alerte personne. Ce qui reste ici est ce qui EST une affaire d'ecran.
 *
 * @param alertEnabled le reglage "alerte d'eloignement" est allume.
 * @param followed la trace actuellement suivie, telle que la veille la publie.
 * @param routeSamples la geometrie du parcours que le planificateur affiche, s'il y en a un : elle se
 *   propose au suivi comme une trace de la bibliotheque (cf. [PlannedRouteLayerId]).
 * @param routeLabel le nom sous lequel ce parcours se propose et s'annonce.
 */
@Composable
internal fun OffTrackAlertEffects(
    alert: OffTrackAlertState,
    location: LocationControls,
    vm: MainViewModel,
    layers: List<LayerEntity>,
    followed: TrackWatch.Followed?,
    alertEnabled: Boolean,
    routeSamples: List<Sample>?,
    routeLabel: String,
) {
    LaunchedEffect(location.gpsActive, alert.chooserPending) { alert.openPendingChooser(location.gpsActive) }
    // Recherche des traces les plus proches : relancée tant que le choix est ouvert et sans réponse, ce qui
    // couvre le cas du capteur allumé mais pas encore fixé - la liste arrive avec la première position.
    LaunchedEffect(alert.chooserOpen, alert.candidates, location.lastUserLocation) {
        if (!alert.chooserOpen || alert.candidates != null) return@LaunchedEffect
        val (la, lo) = location.lastUserLocation ?: return@LaunchedEffect
        // Le parcours du planificateur passe EN TETE, hors classement : c'est celui qu'on vient de
        // composer, et une trace de la bibliotheque qui passerait plus pres ne repond pas a la question
        // qu'on pose en touchant la cloche. Projete a l'ecart de la composition - un itineraire fait
        // couramment plusieurs milliers de points.
        val enCours = withContext(Dispatchers.Default) { plannedCandidate(routeSamples, routeLabel, la, lo) }
        vm.nearestTracks(la, lo) { alert.candidates = listOfNotNull(enCours) + it }
    }
    // Le réglage éteint, ou le capteur coupé : plus rien à suivre. La liste ouverte se referme avec.
    LaunchedEffect(alertEnabled, location.gpsActive) {
        if (!alertEnabled || !location.gpsActive) {
            TrackWatch.stop()
            alert.reset()
        }
    }
    // Couche supprimée ou masquée en cours de suivi : elle n'est plus sur la carte, on ne la suit plus.
    // Le parcours du planificateur n'a pas de couche : c'est l'effet suivant qui veille sur lui.
    //
    // La liste VIDE ne vaut pas "aucune couche" mais "pas encore lue" : elle part de `emptyList()` et se
    // remplit une fois la base revenue. Un suivi repris au démarrage (cf. FollowedStore) tomberait sinon
    // sur cette première passe, et s'arrêterait à la seconde même où il vient d'être rétabli.
    LaunchedEffect(layers, followed) {
        val suivie = followed ?: return@LaunchedEffect
        if (suivie.layerId == PlannedRouteLayerId || layers.isEmpty()) return@LaunchedEffect
        if (layers.none { it.id == suivie.layerId && it.visible }) TrackWatch.stop()
    }
    /*
     * Même règle pour le parcours du planificateur : refermé ou redevenu incalculable, il quitte la carte,
     * et suivre une trace qu'on ne voit plus n'aurait pas de sens. Un simple recalcul, lui, rend une autre
     * géométrie mais jamais rien : le suivi le traverse sans s'arrêter.
     *
     * La règle ne vaut QUE pour un parcours vu dans CETTE session. Après une mort du processus, le
     * planificateur repart vide alors que la veille, elle, a été reprise du disque : sans ce drapeau, le
     * premier passage ici couperait le suivi que le service vient tout juste de rétablir. Un parcours
     * qu'on n'a jamais vu n'a pas pu disparaître.
     */
    var parcoursVu by remember { mutableStateOf(false) }
    LaunchedEffect(routeSamples != null) { if (routeSamples != null) parcoursVu = true }
    LaunchedEffect(followed, routeSamples, parcoursVu) {
        val suivie = followed ?: return@LaunchedEffect
        if (suivie.layerId == PlannedRouteLayerId && parcoursVu && routeSamples == null) TrackWatch.stop()
    }
}
