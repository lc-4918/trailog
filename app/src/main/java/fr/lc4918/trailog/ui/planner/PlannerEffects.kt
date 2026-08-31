package fr.lc4918.trailog.ui.planner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.RouteEngine
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.geocode.Photon
import fr.lc4918.trailog.routing.Router
import fr.lc4918.trailog.ui.components.MapController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Ce que le planificateur calcule, et ce qu'il demande a la carte en retour.
 *
 * Un parcours d'abord : les etapes designees partent au moteur, et ce qu'il rend passe par le meme calcul
 * que les traces importees - distance cumulee, lissage de l'altitude, pente par point -, de sorte qu'une
 * pente se lise pareil sur un itineraire compose et sur une trace ouverte.
 *
 * Puis trois mouvements de camera : le cadrage du parcours neuf, celui de la portion zoomee dans son
 * profil, et le curseur qui suit le doigt sur le graphique.
 *
 * Enfin le trace lui-meme, pose sur la carte - avec ceux des distances mesurees depuis un point, qui
 * partagent son calque : ils ont meme style, et rien n'impose d'ordre entre eux.
 *
 * @param routeTracks les itineraires mesures depuis un point de la carte, poses sur le meme calque.
 * @param routeRevision numero d'ordre de ces mesures : c'est lui qui relance le trace, et non les
 *   itineraires eux-memes.
 * @param currentPosition une position ponctuelle demandee au capteur, pour l'etape "d'ou je suis".
 * @param bandHeightPx hauteur de la bande, qui masque le bas de la carte : le cadrage doit s'en degager.
 * @param geocodingBase l'instance Photon a interroger, pour l'adresse d'une etape montree du doigt.
 */
@Composable
fun PlannerEffects(
    state: RoutePlannerState,
    controller: MapController,
    routeEngine: RouteEngine,
    routingUrl: String,
    prefs: RoutingPrefs,
    smoothingM: Double,
    slopeTint: Boolean,
    /** Compteur de styles prets : un fond de carte recharge emporte le trace avec lui. */
    styleTick: Int,
    /** Le reglage qui autorise le planificateur : l'eteindre pendant qu'il est ouvert le referme. */
    enabled: Boolean?,
    topPaddingPx: Int,
    bandHeightPx: Int,
    routeTracks: List<ComputedTrack>,
    routeRevision: Int,
    geocodingBase: String,
    currentPosition: suspend () -> Pair<Double, Double>?,
) {
    val ctx = LocalContext.current

    /*
     * Adresse d'une etape MONTREE DU DOIGT (geocodage inverse), qui remplace ses coordonnees dans le champ.
     *
     * **Apres coup, et non avant.** L'etape est posee des le tap et le parcours part aussitot au moteur :
     * il ne demande que des coordonnees, et il les a. Attendre le geocodeur pour calculer ferait payer une
     * requete de plus - et son silence - a un trajet qui n'en a pas besoin. Le nom arrive quand il arrive ;
     * s'il ne vient pas, l'etape garde ses coordonnees et reste parfaitement valable (cf. nameMapPoint).
     *
     * ICI plutot que dans la bande : celle-ci ne compose plus rien une fois rangee (cf. RoutePlannerBand),
     * et un effet qui y vivrait serait annule par le repli - c'est-a-dire par le geste meme qui sert a
     * designer le point suivant.
     *
     * Une seconde tentative avant d'abandonner, comme partout ailleurs face a ce service : le premier appel
     * paie l'ouverture de la liaison et echoue parfois au delai.
     */
    state.steps.forEach { step ->
        key(step.id) {
            LaunchedEffect(step.pickedOnMap, step.addressPending) {
                val point = step.pickedOnMap ?: return@LaunchedEffect
                if (!step.addressPending) return@LaunchedEffect
                val (lon, lat) = point
                val lang = ctx.resources.configuration.locales[0].language
                val r = Photon.reverse(geocodingBase, lon, lat, lang)
                    ?: Photon.reverse(geocodingBase, lon, lat, lang)
                // L'etape a pu changer de point pendant l'appel : l'adresse d'alors ne la nomme plus.
                if (step.pickedOnMap != point) return@LaunchedEffect
                state.nameMapPoint(step, r?.firstOrNull()?.lines)
            }
        }
    }
    // Le parcours affiche a-t-il deja ete cadre. Un recalcul ne doit pas redeplacer la carte : on ne cadre
    // qu'a la premiere version d'un trajet, pas a chacune de ses retouches.
    var routeFramed by remember { mutableStateOf(false) }
    var framePending by remember { mutableStateOf(false) }

    /*
     * L'itineraire temporaire d'AVANT la mort du processus, repris du disque - une seule fois, et avant
     * tout calcul (cf. PlannerStore).
     *
     * Le planificateur vit dans un ViewModel, ce qui lui fait traverser une rotation mais pas la reprise
     * de memoire que le systeme s'accorde des que l'application passe assez longtemps derriere, ecran
     * eteint au premier chef. Le trajet qu'on suivait disparaissait alors sans un mot, pendant que la
     * veille, elle, revenait du disque : une cloche qui surveillait un parcours que plus rien ne montrait.
     *
     * [reprise] retient le calcul jusque-la : sans ce verrou, l'effet ci-dessous verrait des etapes vides,
     * publierait Idle, et l'effet d'ecriture effacerait le fichier avant meme qu'on l'ait lu.
     */
    var reprise by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        state.restore(PlannerStore.load(ctx))
        reprise = true
    }
    // Le parcours calcule survit a la mort du processus, et disparait avec le trajet qu'on abandonne :
    // la meme ecriture dit les deux (cf. RoutePlannerState.snapshot).
    LaunchedEffect(reprise, state.route, state.open, state.collapsed) {
        if (reprise) PlannerStore.save(ctx, state.snapshot())
    }

    // Les préférences suivent la discipline CHOISIE DANS LA BANDE, non celle des réglages : le planificateur
    // change de discipline sans quitter la carte, et passer au VTT doit amener avec lui ce qu'on demande à
    // un VTT. Changer un réglage pendant qu'un parcours est affiché le recalcule (il entre dans l'empreinte).
    val inputs = RouteInputs(state.revision, routingUrl, state.profile, prefs, smoothingM)
    LaunchedEffect(inputs, reprise) {
        if (!reprise) return@LaunchedEffect
        // Le parcours a l'ecran repond deja a ce qu'on demande : rien a faire. C'est ce qui empeche une
        // rotation - ou une reprise du disque - de renvoyer au moteur un itineraire intact, et de le
        // perdre si le service ne repond pas (cf. RouteInputs).
        if (state.adopt(inputs)) return@LaunchedEffect
        val targets = state.targets
        if (targets.size < 2) {
            state.publish(RouteState.Idle); routeFramed = false; framePending = false
            return@LaunchedEffect
        }
        state.beginRecompute()
        val pts = targets.map { t ->
            when (t) {
                is StepTarget.Place -> t.place.lat to t.place.lon
                // Demandee au capteur, que le repere soit affiche ou non : composer un parcours depuis chez
                // soi n'oblige pas a poser sa position sur la carte.
                StepTarget.CurrentPosition -> currentPosition() ?: run {
                    state.publish(RouteState.Failed); routeFramed = false; return@LaunchedEffect
                }
            }
        }
        val r = Router.route(ctx, routeEngine, routingUrl, pts, state.profile, prefs)
        if (r == null || r.points.size < 2) {
            state.publish(RouteState.Failed); routeFramed = false; return@LaunchedEffect
        }
        val track = withContext(Dispatchers.Default) {
            TrackMath.compute(r.points, smoothingM = smoothingM, maxPoints = 0, ignoreStops = false)
        }
        state.publish(RouteState.Done(r.meters, r.seconds, track), inputs)
        if (!routeFramed) framePending = true
    }
    /*
     * Cadrage differe, et non enchaine au calcul : au moment ou le parcours arrive, la bande n'a pas encore
     * grandi de sa zone resultats, et sa hauteur mesuree est celle d'AVANT. Cadrer tout de suite laissait
     * donc la fin du trajet cachee derriere la bande, qui s'etendait juste apres.
     *
     * L'effet depend de la hauteur de la bande : il se relance a chaque etape de sa croissance, et le court
     * delai annule les passes intermediaires. Le cadrage n'a lieu qu'une fois la bande stabilisee.
     */
    LaunchedEffect(framePending, bandHeightPx, state.route) {
        if (!framePending) return@LaunchedEffect
        val s = state.done?.track?.samples ?: return@LaunchedEffect
        delay(120)
        controller.fitTo(
            s.minOf { it.lon }, s.minOf { it.lat }, s.maxOf { it.lon }, s.maxOf { it.lat },
            topPaddingPx = topPaddingPx, bottomPaddingPx = bandHeightPx,
        )
        routeFramed = true
        framePending = false
    }
    /*
     * La carte suit le zoom du profil : grossir une portion du graphique recadre la carte sur cette
     * portion, revenir a la vue complete la recadre sur tout le parcours. Les deux representent le meme
     * trajet, et regarder de pres sur l'un sans l'autre oblige a refaire le rapprochement de tete.
     *
     * Relance sur la seule fenetre de zoom, et non sur le parcours : un recalcul ne doit pas deplacer la
     * carte (cf. routeFramed), et la fenetre retombant a null au meme moment, la cle ne change pas.
     */
    LaunchedEffect(state.zoomRange) {
        val all = state.done?.track?.samples ?: return@LaunchedEffect
        val s = state.zoomRange?.let { z -> all.subList(z.first, (z.last + 1).coerceAtMost(all.size)) } ?: all
        if (s.size < 2) return@LaunchedEffect
        controller.fitTo(
            s.minOf { it.lon }, s.minOf { it.lat }, s.maxOf { it.lon }, s.maxOf { it.lat },
            topPaddingPx = topPaddingPx, bottomPaddingPx = bandHeightPx,
        )
    }
    // Le planificateur désactivé dans les réglages pendant qu'il est ouvert le referme : sans cela sa bande
    // survivrait au réglage qui l'a fait naître.
    LaunchedEffect(enabled) {
        if (enabled == false && state.open) state.close()
    }
    // Tracés posés sur la carte, teintés par classe de pente comme l'aire du profil : le parcours du
    // planificateur, et les itinéraires mesurés depuis un point de la carte. Un seul calque pour les trois :
    // ils ont même style, et rien n'impose d'ordre entre eux.
    LaunchedEffect(state.revision, state.route, routeRevision, styleTick, slopeTint) {
        controller.setRouteLines(listOfNotNull(state.done?.track) + routeTracks, slopeTint)
    }
    // Curseur du profil du planificateur : il n'entre pas en concurrence avec celui d'une trace, le
    // planificateur fermant le profil ouvert quand il s'ouvre.
    LaunchedEffect(state.cursor, state.route) {
        val s = state.done?.track?.samples ?: return@LaunchedEffect
        val p = state.cursor?.let { TrackMath.sampleAt(s, it) }
        if (p != null) controller.setCursor(p.lon, p.lat) else controller.clearCursor()
    }
}
