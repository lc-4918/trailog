package fr.lc4918.trailog.ui.routes

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
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
     *
     * **[resumed] : la carte est-elle DEVANT l'utilisateur.** Le suivi vit dans la composition, et celle-ci
     * ne s'arrete pas quand l'ecran s'eteint ni quand l'application passe derriere une autre : la
     * `MapView`, elle, est mise en pause. Le suivi continuait donc a lancer des animations de camera sur
     * une carte qui ne dessine plus - une par position recue, indefiniment, en veille comme au bureau
     * d'accueil. C'est ce que rapportait le testeur en disant que le centrage "ne fonctionne plus" apres
     * un aller-retour : la camera revenait d'une mise en veille avec une animation en travers.
     *
     * Le retour au premier plan leve en outre le silence d'apres-geste (cf.
     * [LocationControls.clearUserGesture]) : le dernier geste date d'avant qu'on quitte l'ecran.
     *
     * **[cameraReleased] : l'utilisateur a demande a ne PAS etre recentre a l'allumage** (cf. le reglage
     * Carte > GPS, et [LocationControls.cameraReleased]). C'est la quatrieme suspension, et la seule qui
     * ne se leve pas d'elle-meme : les trois autres attendent qu'on referme ce qui occupe la carte, celle-ci
     * attend un geste qui reclame la position - le bouton de recentrage, ou l'armement du suivi. Sans elle,
     * le reglage etait a peu pres sans effet : `startGps` ne bougeait plus rien, mais le suivi continu
     * recentrait a la premiere position recue, dans la seconde qui suivait.
     */
    fun follows(
        enabled: Boolean,
        gpsActive: Boolean,
        resumed: Boolean,
        plannerExpanded: Boolean,
        layerOpen: Boolean,
        bubbleOpen: Boolean,
        cameraReleased: Boolean,
    ): Boolean = enabled && gpsActive && resumed && !plannerExpanded && !layerOpen && !bubbleOpen &&
        !cameraReleased

    /** Ce que fait l'appui sur le bouton de suivi. */
    enum class FollowTap {
        /** Allume le suivi ET recentre : ce qu'on demande en le rallumant, c'est de revenir chez soi. */
        ARM,

        /** Recentre tout de suite, sans toucher au reglage : abrege le silence d'apres-geste. */
        RECENTER,

        /** Eteint le suivi, sans deplacer la carte : c'est pour la lire la qu'on vient de couper. */
        DISARM,
    }

    /**
     * Ce que l'appui sur le bouton doit faire, selon ce que la carte montre deja.
     *
     * **Trois etats et non deux.** Une simple bascule aurait suffi a repondre au testeur - allumer,
     * eteindre - mais elle emportait avec elle le seul geste que l'ancien bouton savait faire : revenir
     * sur la position TOUT DE SUITE. Ce geste sert precisement pendant le silence de [QuietDelayMs], quand
     * on vient de faire glisser la carte, qu'on a trouve ce qu'on cherchait et qu'on ne veut pas compter
     * jusqu'a cinq. Sans ce troisieme etat, il fallait eteindre puis rallumer pour l'obtenir - deux appuis
     * qui traversent l'etat "eteint", donc deux ecritures en base, pour ne rien changer au bout du compte.
     *
     * La regle se lit alors simplement : **le bouton fait toujours ce qui manque.** La position n'est pas
     * suivie ? Il la suit. Elle est suivie mais pas encore revenue ? Il la ramene. Elle est suivie et
     * centree ? Il n'y a plus rien a demander, l'appui rend la carte.
     *
     * [positionCentered] se mesure a l'ecran (cf. `positionCentered` dans MapControls) et non sur les
     * coordonnees : la question est "la position est-elle au milieu de ce que je vois", et sa reponse doit
     * valoir a tout zoom.
     */
    fun tapAction(following: Boolean, positionCentered: Boolean): FollowTap = when {
        !following -> FollowTap.ARM
        !positionCentered -> FollowTap.RECENTER
        else -> FollowTap.DISARM
    }

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
    // La carte est-elle devant l'utilisateur (cf. MapFollow.follows). L'ecran eteint, l'application passee
    // derriere une autre ou renvoyee au bureau, la composition SURVIT - c'est la MapView qui s'arrete - et
    // le suivi continuait de lui demander des animations de camera qu'elle ne dessinait pas.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    // Le retour au premier plan efface le silence d'apres-geste : celui qui revient veut voir ou il est, et
    // le geste qui avait ouvert ce silence date d'avant qu'il quitte l'ecran. L'ordre des deux effets est
    // sans importance - effacer l'heure du geste est justement l'une des cles du suivi, qui repart donc
    // dessus quel que soit celui des deux qui s'execute en premier.
    LaunchedEffect(resumed) { if (resumed) location.clearUserGesture() }
    val followsPosition = MapFollow.follows(
        enabled = settings.mapFollowPosition,
        gpsActive = location.gpsActive,
        resumed = resumed,
        plannerExpanded = planner.expanded,
        layerOpen = layerOpen,
        bubbleOpen = bubbleOpen,
        cameraReleased = location.cameraReleased,
    )
    LaunchedEffect(followsPosition, location.lastUserLocation, location.lastUserGestureAt) {
        if (!followsPosition) return@LaunchedEffect
        val (la, lo) = location.lastUserLocation ?: return@LaunchedEffect
        val wait = MapFollow.waitMs(SystemClock.elapsedRealtime(), location.lastUserGestureAt)
        if (wait > 0L) delay(wait)
        controller.centerOn(la, lo)
    }
}
