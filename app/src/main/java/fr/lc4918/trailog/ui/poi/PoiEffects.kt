package fr.lc4918.trailog.ui.poi

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.geocode.NetworkStatus
import fr.lc4918.trailog.poi.Overpass
import fr.lc4918.trailog.poi.PoiLoader
import fr.lc4918.trailog.poi.PoiRepository
import fr.lc4918.trailog.poi.PoiSource
import fr.lc4918.trailog.poi.PoiSources
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.PoiMarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * La couche des points d'interet : ce qu'elle demande, et ce qu'elle pose sur la carte.
 *
 * A chaque arret de la camera, la vue dit au chargeur ce qu'elle veut - les cellules qu'elle touche
 * (cf. [PoiLoading.plan]) - et le chargeur s'occupe du reste : le cache d'abord, le service pour ce qui
 * manque, du centre vers les bords (cf. [PoiLoader]). Les lieux arrivent cellule par cellule, et chaque
 * arrivee se voit aussitot.
 *
 * @param idleTick l'arret de la camera : c'est lui, et non chaque image du deplacement, qui dit ce qu'on
 *   regarde.
 * @param styleTick compteur de styles prets : un fond de carte recharge emporte les marqueurs avec lui.
 */
@Composable
fun PoiEffects(
    state: PoiState,
    controller: MapController,
    /** Le depot des points d'interet, fourni par l'ecran : une couche d'interface n'a pas a connaitre
     *  Room (cf. `TrailogRepository`). */
    repo: PoiRepository,
    /** Le reglage qui autorise la couche : l'eteindre la referme. */
    enabled: Boolean?,
    /** Le reglage "Completer avec OpenStreetMap" : sans effet hors de France (cf. PoiSources). */
    osmComplement: Boolean,
    /** L'instance Overpass a interroger, telle que les reglages la designent. Vide = l'instance par defaut. */
    osmUrl: String,
    filters: PoiFilters,
    /**
     * Les traces affichees, en (lon, lat), et la distance au-dela de laquelle un lieu ne les borde plus.
     * Le couloir borne les cellules demandees, puis les lieux montres (cf. [PoiCorridor]). Une distance
     * nulle, ou aucune trace affichee, et rien n'est filtre.
     */
    corridorTracks: List<List<Pair<Double, Double>>>,
    corridorM: Int,
    idleTick: Int,
    markerPx: Float,
    styleTick: Int,
    /** La camera a-t-elle atteint sa place de depart (cf. rememberCameraPlacement). Tant que non, le seul
     *  arret de camera a lire est celui du cadrage PROVISOIRE ou la MapView vient de naitre. */
    positioned: Boolean,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // L'instance se lit a chaque requete, et non a la construction du chargeur : la changer dans les
    // reglages ne doit pas demander de recreer ce qui a deja ete charge.
    val instance by rememberUpdatedState(osmUrl.ifBlank { Overpass.DEFAULT_URL })
    val loader = remember(repo) {
        PoiLoader(
            scope = scope,
            lookup = { units -> repo.lookup(units) },
            fetch = { job -> repo.fetch(job, instance) },
            now = { SystemClock.elapsedRealtime() },
        )
    }

    LaunchedEffect(enabled) { if (enabled == false) state.hide() }

    /*
     * L'acces a Internet, SUIVI et non constate a l'instant du chargement : repasser sous couverture doit
     * redemander ce qu'on n'avait pas pu obtenir, sans attendre un geste de carte. Un telephone pose sur une
     * table n'en produit aucun.
     */
    val enLigne by remember(ctx) { NetworkStatus.online(ctx) }.collectAsState(initial = true)
    LaunchedEffect(enLigne, osmUrl) { if (enLigne) loader.retryFailed() }

    // Ce que le chargeur sait, recopie dans l'etat de l'ecran a chaque arrivee.
    val snapshot by loader.state.collectAsState()

    LaunchedEffect(state.visible, state.masked, idleTick, filters, osmComplement, positioned,
        corridorTracks, corridorM) {
        if (!state.visible || state.masked || !positioned) return@LaunchedEffect
        // Le zoom se lit AVANT l'attente, et le message se leve avec lui : attendre pour le lever laissait
        // l'ecran reclamer un zoom qu'on venait de faire.
        val zoom = controller.cameraState()?.third ?: return@LaunchedEffect
        if (zoom < PoiLoading.MIN_ZOOM) { state.tooFar(); return@LaunchedEffect }
        state.nearEnough()
        delay(PoiLoading.DEBOUNCE_MS)
        val vue = controller.visibleBounds() ?: return@LaunchedEffect
        // Les GROUPES coches, et non les categories : une cellule se charge groupe entier, et le filtre
        // s'applique ensuite a ce qu'on a (cf. PoiSources.categories).
        val groupes = filters.shown.mapTo(mutableSetOf()) { it.group }
        val plan = withContext(Dispatchers.Default) {
            PoiLoading.plan(vue, groupes, osmComplement, corridorTracks, corridorM)
        }
        state.viewed(capped = plan.capped, away = plan.away)
        loader.want(plan.units)
    }

    // La couche eteinte ne garde rien a l'ecran ; le chargeur, lui, garde ce qu'il a - la rallumer ne
    // coute aucune requete.
    LaunchedEffect(snapshot, state.visible) {
        if (!state.visible) return@LaunchedEffect
        // Les deux sources ne servent presque jamais la meme categorie au meme endroit (cf. PoiSources) ;
        // sur une cellule frontaliere, si. Un lieu connu des deux ne se pose qu'une fois.
        val lieux = withContext(Dispatchers.Default) {
            val (osm, dt) = snapshot.pois.partition { PoiSource.ofUuid(it.uuid) == PoiSource.OSM }
            PoiSources.merge(dt, osm)
        }
        state.show(lieux, snapshot.pending, snapshot.fromCache, snapshot.missing)
    }

    /*
     * Les marqueurs, filtres LOCALEMENT par les categories retenues et le couloir des traces.
     *
     * Decocher une categorie la retire a l'instant, sans requete : le chargeur a le groupe entier. La
     * recocher la remontre aussi vite, pour la meme raison.
     *
     * Suivent aussi le RECHARGEMENT DU STYLE : changer de fond de carte reconstruit le style, qui emporte
     * avec lui les couches posees dessus.
     */
    LaunchedEffect(state.pois, state.showingMarkers, filters, corridorTracks, corridorM, markerPx, styleTick) {
        val montres = if (!state.showingMarkers) emptyList() else {
            withContext(Dispatchers.Default) {
                val retenus = state.pois.filter { filters.isShown(it.category) }
                PoiCorridor.filter(retenus, corridorTracks, corridorM.toDouble())
            }
        }
        controller.setPoiMarkers(
            montres.map {
                PoiMarker(it.uuid, it.lon, it.lat, poiGroupColor(it.category.group), poiIcon(it.category))
            },
            markerPx,
        )
    }
    // Une categorie decochee emporte l'infobulle ouverte sur l'un de ses lieux, en meme temps que son
    // marqueur.
    LaunchedEffect(filters) { state.dropSelectionIfHidden(filters) }
    LaunchedEffect(controller) {
        controller.onPickPoi = { uuid, _, _ -> state.selectById(uuid) }
    }
}
