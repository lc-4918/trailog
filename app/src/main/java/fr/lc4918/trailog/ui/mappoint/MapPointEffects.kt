package fr.lc4918.trailog.ui.mappoint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.RouteEngine
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.geocode.GeocodePlace
import fr.lc4918.trailog.geocode.Photon
import fr.lc4918.trailog.routing.Router
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ce qu'un point designe a l'appui long va CHERCHER : son adresse, et les deux distances qu'on peut lui
 * demander - depuis ou l'on est, et depuis un autre point de la carte.
 *
 * Trois requetes reseau, et rien qui se dessine : les effets sont ici, la bulle qui en montre le resultat
 * est a cote (cf. [MapPointBubble]).
 *
 * @param geocodingBase l'instance Photon a interroger, telle que les reglages la designent.
 * @param currentPosition une position ponctuelle demandee au capteur, sans rien poser sur la carte.
 * @param onPlaceFound l'adresse trouvee entre dans l'historique du planificateur.
 */
@Composable
fun MapPointEffects(
    state: MapPointState,
    geocodingBase: String,
    routeEngine: RouteEngine,
    routingUrl: String,
    routingProfile: RoutingProfile,
    prefs: RoutingPrefs,
    smoothingM: Double,
    currentPosition: suspend () -> Pair<Double, Double>?,
    onPlaceFound: (GeocodePlace) -> Unit,
) {
    val ctx = LocalContext.current

    /*
     * Adresse du point désigné (géocodage inverse).
     *
     * Relancée sur le seul point : le service, lui, ne change qu'en réglages, et l'adresse d'un point déjà
     * affiché n'a aucune raison de changer sous les yeux de qui la lit.
     *
     * Une seconde tentative avant d'abandonner, comme la recherche par le nom : le premier appel paie
     * l'ouverture de la liaison et échoue parfois au délai.
     */
    LaunchedEffect(state.point) {
        val (lon, lat) = state.point ?: return@LaunchedEffect
        state.publishAddress(AddressState.Loading)
        val lang = ctx.resources.configuration.locales[0].language
        val r = Photon.reverse(geocodingBase, lon, lat, lang) ?: Photon.reverse(geocodingBase, lon, lat, lang)
        val adresse = r?.firstOrNull()
        state.publishAddress(when {
            r == null -> AddressState.Failed
            adresse == null -> AddressState.NotFound
            else -> AddressState.Done(adresse.lines)
        })
        // L'adresse trouvee entre dans l'historique du planificateur (cf. PlannerHistory) : on vient de
        // montrer un endroit et d'en lire le nom, c'est un candidat pour un prochain trajet.
        //
        // Aux coordonnees DESIGNEES, non a celles que rend le geocodeur : l'epingle est restee sur le point
        // qu'on a montre, l'adresse n'en est que le nom, et c'est de la qu'on voudra partir.
        //
        // Rien a retenir quand le service n'a rien rendu : un historique sans libelle ne se relit pas, et
        // "44.56, 6.08" ne dirait rien a personne trois jours plus tard.
        if (adresse != null) onPlaceFound(GeocodePlace(adresse.lines, lon, lat))
    }

    /*
     * Un itinéraire depuis (fromLat, fromLon) jusqu'au point désigné, traduit en état affichable.
     *
     * Les points rendus par le moteur passent par le même calcul que les traces importées : distance
     * cumulée, lissage de l'altitude au réglage de l'utilisateur, pente par point. La ligne sur la carte et
     * sa teinte sortent ensuite de ces mêmes points.
     *
     * Aucune décimation (contrairement aux traces, ramenées à 2000 points) : ici les points dessinent aussi
     * la ligne sur la carte, et en retirer un sur deux couperait les virages du tracé affiché.
     */
    suspend fun measureTo(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): MeasureState {
        val r = Router.route(ctx, routeEngine, routingUrl, listOf(fromLat to fromLon, toLat to toLon),
            routingProfile, prefs) ?: return MeasureState.Failed
        val track = withContext(Dispatchers.Default) {
            if (r.points.size < 2) null
            else TrackMath.compute(r.points, smoothingM = smoothingM, maxPoints = 0, ignoreStops = false)
        }
        return MeasureState.Done(r.meters, r.seconds, track)
    }

    // Origine de la mesure depuis la position, figée dès qu'une position est connue : le repère affiché en
    // livre une toutes les 2 s, et les suivre lancerait autant de requêtes d'itinéraire.
    //
    // La position vient du capteur, éteint ou allumé côté affichage : mesurer d'où l'on est n'oblige pas à
    // poser le repère sur la carte.
    //
    // Sans position - autorisation refusée, localisation éteinte, ou capteur muet jusqu'au bout du délai
    // que le système s'accorde -, la mesure est abandonnée : son spinner tournerait indéfiniment.
    LaunchedEffect(state.positionMeasure) {
        if (state.positionMeasure != MeasureState.Loading || state.positionOrigin != null) return@LaunchedEffect
        val fix = currentPosition()
        if (fix == null) state.publishPositionMeasure(MeasureState.Failed)
        else state.fixPositionOrigin(fix.first, fix.second)
    }
    LaunchedEffect(state.point, state.positionOrigin, routingUrl, routingProfile, prefs) {
        val (lon, lat) = state.point ?: return@LaunchedEffect
        val (la, lo) = state.positionOrigin ?: return@LaunchedEffect
        state.publishPositionMeasure(MeasureState.Loading)
        state.publishPositionMeasure(measureTo(la, lo, lat, lon))
    }
    LaunchedEffect(state.point, state.refPoint, routingUrl, routingProfile, prefs) {
        val (lon, lat) = state.point ?: return@LaunchedEffect
        val (refLon, refLat) = state.refPoint ?: return@LaunchedEffect
        state.publishPointMeasure(MeasureState.Loading)
        state.publishPointMeasure(measureTo(refLat, refLon, lat, lon))
    }
}
