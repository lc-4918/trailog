package fr.lc4918.trailog.ui.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.location.TrackWatch
import fr.lc4918.trailog.ui.alert.OffTrackAlertState
import fr.lc4918.trailog.ui.location.LocationControls

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
 */
@Composable
internal fun OffTrackAlertEffects(
    alert: OffTrackAlertState,
    location: LocationControls,
    vm: MainViewModel,
    layers: List<LayerEntity>,
    followed: TrackWatch.Followed?,
    alertEnabled: Boolean,
) {
    LaunchedEffect(location.gpsActive, alert.chooserPending) { alert.openPendingChooser(location.gpsActive) }
    // Recherche des traces les plus proches : relancée tant que le choix est ouvert et sans réponse, ce qui
    // couvre le cas du capteur allumé mais pas encore fixé - la liste arrive avec la première position.
    LaunchedEffect(alert.chooserOpen, alert.candidates, location.lastUserLocation) {
        if (!alert.chooserOpen || alert.candidates != null) return@LaunchedEffect
        val (la, lo) = location.lastUserLocation ?: return@LaunchedEffect
        vm.nearestTracks(la, lo) { alert.candidates = it }
    }
    // Le réglage éteint, ou le capteur coupé : plus rien à suivre. La liste ouverte se referme avec.
    LaunchedEffect(alertEnabled, location.gpsActive) {
        if (!alertEnabled || !location.gpsActive) {
            TrackWatch.stop()
            alert.reset()
        }
    }
    // Couche supprimée ou masquée en cours de suivi : elle n'est plus sur la carte, on ne la suit plus.
    LaunchedEffect(layers, followed) {
        val suivie = followed ?: return@LaunchedEffect
        if (layers.none { it.id == suivie.layerId && it.visible }) TrackWatch.stop()
    }
}
