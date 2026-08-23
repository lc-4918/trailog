package fr.lc4918.trailog.ui.routes

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.geocode.GeocodeSearchState
import fr.lc4918.trailog.ui.location.LocationControls
import fr.lc4918.trailog.ui.mappoint.MapPointState
import fr.lc4918.trailog.ui.planner.RoutePlannerState
import fr.lc4918.trailog.ui.poi.PoiState
import kotlinx.coroutines.delay

/**
 * Quand la carte suit la position du porteur, et quand elle le laisse tranquille.
 *
 * En sortie, on avance : sans ce suivi, il faut faire glisser la carte tous les cent mètres pour garder
 * devant soi la trace qu'on suit. La carte se recentre donc à chaque nouvelle position - mais elle rend la
 * main dès qu'on la touche, et ne la reprend qu'après [QuietDelayMs] sans geste : regarder ce qu'il y a
 * plus loin sur l'itinéraire est un besoin aussi réel que le suivi, et une carte qui revient sous les
 * doigts est inutilisable.
 *
 * Logique séparée de l'écran parce qu'elle est la seule chose ici qui se vérifie : le reste est un appel de
 * caméra dans un effet.
 */
object MapFollow {

    /**
     * Silence à observer après un geste avant de reprendre le suivi.
     *
     * Cinq secondes : de quoi lire la carte à l'endroit qu'on vient d'atteindre, sans que le retour à la
     * position se fasse attendre au point qu'on aille chercher le bouton de recentrage.
     */
    const val QuietDelayMs = 5_000L

    /**
     * Le suivi a-t-il lieu d'être ?
     *
     * Trois choses le suspendent, et pour la même raison : elles sont posées SUR la carte et s'en servent
     * ailleurs qu'à l'endroit où l'on se tient.
     * - la bande du planificateur DEPLOYEE cadre le parcours qu'elle vient de calculer, et composer un
     *   trajet avec une carte qui revient toutes les cinq secondes sur soi est impossible. Reduite, elle
     *   ne suspend plus rien : c'est meme l'etat dans lequel on roule en suivant le parcours calcule, et
     *   la carte cessait alors de suivre la position alors que le reglage etait allume (cf.
     *   RoutePlannerState.expanded) ;
     * - le profil d'une trace ouverte déplace la carte au curseur qu'on promène dessus, geste qui perdrait
     *   son résultat de la même façon ;
     * - une infobulle ouverte est accrochée à un point précis - un waypoint, un point d'intérêt, un lieu
     *   trouvé, l'endroit d'un appui long : le recentrage emporterait hors de l'écran le point qu'elle
     *   décrit, et l'infobulle avec, au milieu de la lecture.
     *
     * Aucune des trois n'éteint le réglage : elles le mettent en pause, et le suivi reprend en les fermant.
     * Le silence de [QuietDelayMs] repart alors de la fermeture, l'écran relevant l'heure à ce moment-là :
     * refermer une infobulle ne doit pas faire sauter la carte dans la seconde.
     */
    fun follows(
        enabled: Boolean,
        gpsActive: Boolean,
        plannerExpanded: Boolean,
        layerOpen: Boolean,
        bubbleOpen: Boolean,
    ): Boolean = enabled && gpsActive && !plannerExpanded && !layerOpen && !bubbleOpen

    /**
     * Attente restante avant de pouvoir recentrer, en millisecondes ; 0 quand le dernier geste est assez
     * vieux, ou qu'il n'y en a jamais eu ([lastGestureAt] à 0).
     *
     * Une attente RESTANTE et non un délai fixe : les positions arrivent au rythme du capteur, environ une
     * par seconde. Repartir de zéro à chacune ferait reculer l'échéance indéfiniment, et la carte ne
     * reviendrait jamais.
     */
    fun waitMs(now: Long, lastGestureAt: Long, delayMs: Long = QuietDelayMs): Long {
        if (lastGestureAt <= 0L) return 0L
        val since = now - lastGestureAt
        // Une horloge qui recule (now avant le geste) ne doit pas suspendre le suivi pour l'éternité :
        // hors de la fenêtre, on recentre.
        if (since < 0L) return 0L
        return (delayMs - since).coerceAtLeast(0L)
    }
}

/**
 * Le suivi de la position par la CAMERA : la carte se recentre a chaque mesure, et se tait cinq secondes
 * apres chaque geste.
 *
 * L'effet se relance sur la position ET sur l'heure du dernier geste, et c'est ce qui le fait marcher dans
 * les deux sens : une position pendant le silence attend ce qu'il en reste, et un geste annule le
 * recentrage en cours de preparation pour repartir sur cinq secondes pleines. Le retour a donc lieu meme
 * immobile, l'attente du geste arrivant a son terme sans qu'aucune position nouvelle ne l'y aide.
 *
 * @param layerOpen le profil d'une trace occupe le bas de l'ecran.
 */
@Composable
internal fun MapFollowEffect(
    settings: SettingsEntity,
    location: LocationControls,
    planner: RoutePlannerState,
    controller: MapController,
    poi: PoiState,
    geo: GeocodeSearchState,
    mapPoint: MapPointState,
    selectedMarkerId: String?,
    layerOpen: Boolean,
) {
    /*
     * La carte suit le porteur : elle se recentre à chaque position reçue, et se tait cinq secondes après
     * chaque geste (cf. MapFollow, qui porte les règles et leurs raisons).
     *
     * L'effet se relance sur la position ET sur l'heure du dernier geste, et c'est ce qui le fait marcher
     * dans les deux sens : une position pendant le silence attend ce qu'il en reste, et un geste annule le
     * recentrage en cours de préparation pour repartir sur cinq secondes pleines. Le retour a donc lieu
     * même immobile, l'attente du geste arrivant à son terme sans qu'aucune position nouvelle ne l'y aide.
     *
     * Placé ici, et non auprès des rappels de caméra : le suivi doit connaître les infobulles, dont celle
     * des points d'intérêt, qui n'existent qu'à partir de cette ligne.
     */
    // Une infobulle est accrochée à un endroit précis de la carte : un recentrage l'emporterait hors de
    // l'écran, avec son épingle, au milieu de la lecture. Les quatre comptent - waypoint (son éditeur de
    // propriétés compris, qui garde le marqueur sélectionné), point d'intérêt, lieu trouvé, point d'un
    // appui long. Ce dernier compte tant qu'il est POSÉ, même pendant le choix d'un point de référence,
    // qui masque l'infobulle sans clore ce qui se joue.
    val bubbleOpen = selectedMarkerId != null || poi.selected != null || geo.place != null ||
        mapPoint.point != null
    // La fermeture vaut geste : les cinq secondes de silence repartent de là, sans quoi la carte sauterait
    // sur la position à l'instant même où l'on referme l'infobulle.
    //
    // Relevée dans un onDispose plutôt que dans un effet voisin : Compose délivre les oublis AVANT les
    // lancements d'une même passe, si bien que l'heure est à jour quand l'effet du suivi, relancé par cette
    // même fermeture, va la lire. Deux effets côte à côte n'auraient tenu que par l'ordre de déclaration.
    if (bubbleOpen) {
        DisposableEffect(Unit) {
            onDispose { location.noteUserGesture() }
        }
    }
    val followsPosition = MapFollow.follows(
        enabled = settings.mapFollowPosition,
        gpsActive = location.gpsActive,
        plannerExpanded = planner.expanded,
        layerOpen = layerOpen,
        bubbleOpen = bubbleOpen,
    )
    LaunchedEffect(followsPosition, location.lastUserLocation, location.lastUserGestureAt) {
        if (!followsPosition) return@LaunchedEffect
        val (la, lo) = location.lastUserLocation ?: return@LaunchedEffect
        val wait = MapFollow.waitMs(SystemClock.elapsedRealtime(), location.lastUserGestureAt)
        if (wait > 0L) delay(wait)
        controller.centerOn(la, lo)
    }
}
