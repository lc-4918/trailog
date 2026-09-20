package fr.lc4918.trailog.ui.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.location.FollowCatalog
import fr.lc4918.trailog.location.TrackWatch
import fr.lc4918.trailog.location.TripStore
import fr.lc4918.trailog.location.TripWatch
import fr.lc4918.trailog.ui.alert.OffTrackAlertState
import fr.lc4918.trailog.ui.alert.PlannedRouteLayerId
import fr.lc4918.trailog.ui.location.LocationControls

/**
 * Ce qui allume et ce qui eteint le tableau de bord et le suivi de trace.
 *
 * La mesure elle-meme - les compteurs, la trace reconnue, l'ecart et le son - ne se fait pas ici mais
 * dans le service (cf. LocationService.watchTrack) : l'ecran eteint, la composition s'arrete, et une
 * alerte qui ne se declenche que sous les yeux de celui qu'elle doit prevenir n'alerte personne. Ce qui
 * reste ici est ce qui EST une affaire d'ecran : ce que la carte affiche, et ce que les reglages permettent.
 *
 * @param alertEnabled le reglage "tableau de bord" est allume.
 * @param dashboardOpen le tableau de bord est affiche (cf. TrackWatch.dashboard).
 * @param followed la trace actuellement suivie, telle que la veille la publie.
 * @param routeSamples la geometrie du parcours que le planificateur affiche, s'il y en a un : elle se
 *   reconnait comme une trace de la bibliotheque (cf. [PlannedRouteLayerId]).
 * @param routeLabel le nom sous lequel ce parcours s'annonce.
 */
@Composable
internal fun OffTrackAlertEffects(
    alert: OffTrackAlertState,
    location: LocationControls,
    layers: List<LayerEntity>,
    followed: TrackWatch.Followed?,
    dashboardOpen: Boolean,
    alertEnabled: Boolean,
    routeSamples: List<Sample>?,
    routeLabel: String,
) {
    val ctx = LocalContext.current
    // Les compteurs de la sortie, repris du disque des l'ouverture de l'ecran : le tableau de bord les
    // montre avant meme que le capteur ait rendu une position.
    LaunchedEffect(Unit) { TripWatch.restore(TripStore.load(ctx)) }
    // Le capteur demande depuis la boite "localisation coupee" repond : le tableau de bord l'allume.
    LaunchedEffect(location.sensorEnabled, alert.gpsPending) {
        if (alert.consumePending(location.sensorEnabled) && dashboardOpen && !location.gpsActive) {
            location.startGps(forFollow = true)
        }
    }
    // Le parcours du planificateur, depose pour la detection : le service le lit a chaque position.
    LaunchedEffect(routeSamples, routeLabel) { FollowCatalog.setRoute(routeSamples, routeLabel) }
    // Le reglage eteint : le tableau de bord se ferme, et le suivi avec lui.
    LaunchedEffect(alertEnabled) {
        if (!alertEnabled) {
            TrackWatch.setDashboard(false)
            alert.reset()
        }
    }
    /*
     * La localisation du téléphone coupée : plus rien à suivre. Une trace suivie sans position ne dirait
     * plus rien, et se réveillerait au hasard d'un rallumage, des jours plus tard. Le tableau de bord,
     * lui, reste : ses compteurs valent toujours ce qu'ils ont compté. Et la cloche se souvient de sa
     * trace : reconnue au rallumage, elle la retrouve armée (cf. TrackWatch.bellKey).
     */
    LaunchedEffect(location.sensorEnabled) {
        if (!location.sensorEnabled) TrackWatch.stop(forgetBell = false)
    }
    /*
     * **Le tableau de bord se ferme : le capteur qu'il avait allumé se rend.**
     *
     * L'ouvrir allume le capteur s'il était éteint - ses compteurs n'ont que la position pour matière. Le
     * fermer le rend, sans quoi la notification "Suivi de position" resterait dans le volet, seul moyen
     * de s'en défaire.
     *
     * Le capteur allumé par le BOUTON de localisation, lui, survit : il n'a pas été allumé pour le tableau
     * de bord (cf. LocationControls.startedForFollow).
     */
    LaunchedEffect(dashboardOpen, location.startedForFollow) {
        if (!dashboardOpen && location.startedForFollow) location.stopGps()
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
