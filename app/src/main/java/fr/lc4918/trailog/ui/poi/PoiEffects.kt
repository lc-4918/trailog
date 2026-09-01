package fr.lc4918.trailog.ui.poi

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.geocode.NetworkStatus
import fr.lc4918.trailog.poi.Datatourisme
import fr.lc4918.trailog.poi.Overpass
import fr.lc4918.trailog.poi.PoiRepository
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.PoiMarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * La couche des points d'interet : ce qu'elle charge, et ce qu'elle pose sur la carte.
 *
 * Charges sur l'emprise visible, apres un temps d'arret : un deplacement de carte emet des dizaines
 * d'evenements, et sans cette attente chacun partirait en requete. On ne redemande rien tant que la vue
 * reste dans ce qui a deja ete charge - c'est ce qui tient les appels loin du quota du service, bien plus
 * que le delai lui-meme (cf. [PoiLoading]).
 *
 * @param idleTick l'arret de la camera : c'est lui, et non chaque image du deplacement, qui declenche un
 *   chargement.
 * @param styleTick compteur de styles prets : un fond de carte recharge emporte les marqueurs avec lui.
 */
@Composable
fun PoiEffects(
    state: PoiState,
    controller: MapController,
    /**
     * Le depot des points d'interet, fourni par l'ecran.
     *
     * Recu et non construit : le construire demandait d'ouvrir la base, et une couche d'interface n'a pas
     * a connaitre Room. C'est le depot de l'application qui la possede (cf. `TrailogRepository`).
     */
    repo: PoiRepository,
    /** Le reglage qui autorise la couche : l'eteindre la referme. */
    enabled: Boolean?,
    /** Le reglage "Completer avec OpenStreetMap" : sans effet hors de France (cf. PoiSources). */
    osmComplement: Boolean,
    /** L'instance Overpass a interroger, telle que les reglages la designent. Vide = l'instance publique. */
    osmUrl: String,
    filters: PoiFilters,
    /**
     * Les traces affichees, en (lon, lat), et la distance au-dela de laquelle un lieu ne les borde plus.
     *
     * Le couloir filtre l'AFFICHAGE et non la requete : les deux services s'interrogent sur un rectangle,
     * et aucun ne sait suivre une polyligne (cf. [PoiCorridor]). Une distance nulle, ou aucune trace
     * affichee, et rien n'est filtre.
     */
    corridorTracks: List<List<Pair<Double, Double>>>,
    corridorM: Int,
    idleTick: Int,
    markerPx: Float,
    styleTick: Int,
    /** La camera a-t-elle atteint sa place de depart (cf. rememberCameraPlacement). Tant que non, le seul
     *  arret de camera a lire est celui du cadrage PROVISOIRE ou la MapView vient de naitre - loin, en
     *  general, de la ou l'on a laisse la carte - et lui demander s'il est "trop loin" fait clignoter
     *  l'avertissement de zoom au demarrage avant de se corriger de lui-meme. */
    positioned: Boolean,
) {
    val ctx = LocalContext.current

    LaunchedEffect(enabled) { if (enabled == false) state.hide() }

    // osmComplement est une CLE, comme les filtres : c'est une source de plus a interroger, et l'allumer
    // doit relancer le chargement sans attendre qu'on deplace la carte.
    /*
     * L'acces a Internet, SUIVI et non constate a l'instant du chargement.
     *
     * C'est une cle de cet effet a part entiere : repasser sous couverture doit redemander ce qu'on n'avait
     * pas pu obtenir, sans attendre un geste de carte. Un telephone pose sur une table n'en produit aucun,
     * et le bandeau "Derniers points connus" tenait l'ecran indefiniment (cf. PoiState.retryAfterReconnect).
     */
    val enLigne by remember(ctx) { NetworkStatus.online(ctx) }.collectAsState(initial = true)

    LaunchedEffect(state.visible, state.masked, idleTick, filters, osmComplement, osmUrl, positioned,
        enLigne, corridorTracks, corridorM) {
        if (!state.visible) return@LaunchedEffect
        // Couche mise de cote : rien a charger tant qu'on ne la remet pas. Ce qui a deja ete recu reste en
        // memoire, et la remettre ne coute donc aucune requete (cf. PoiState.masked).
        if (state.masked) return@LaunchedEffect
        if (!positioned) return@LaunchedEffect
        // Le reseau vient de revenir : on oublie ce qui retiendrait la requete - l'emprise tenue pour
        // chargee sur le cache, et la minute de repos qui suit un echec. Sans effet apres un chargement
        // reussi, ou il n'y a rien a retrouver.
        if (enLigne) state.retryAfterReconnect()
        // Le zoom se lit AVANT l'attente et la requete, et le message se leve avec lui : la camera est deja
        // posee quand cet effet part (il suit l'arret de la carte), et attendre pour le lever laissait
        // l'ecran reclamer un zoom qu'on venait de faire.
        val zoom = controller.cameraState()?.third ?: return@LaunchedEffect
        if (zoom < PoiLoading.MIN_ZOOM) { state.tooFar(); return@LaunchedEffect }
        state.nearEnough()
        // Tout ce qui suit peut s'interrompre - un geste de plus annule cet effet - d'ou le finally : sans
        // lui, l'attente resterait allumee et le bouton tournerait sur une requete qui n'existe plus.
        try {
            delay(PoiLoading.DEBOUNCE_MS)
            val vue = controller.visibleBounds() ?: return@LaunchedEffect
            // Temps depuis le demarrage de l'appareil, et non heure murale : un changement d'heure ne doit
            // pas prolonger indefiniment le silence apres un echec ni l'annuler d'un coup.
            val maintenant = SystemClock.elapsedRealtime()
            if (!state.needsLoad(vue, filters, osmComplement, maintenant)) return@LaunchedEffect
            val box = PoiLoading.grow(vue)
            /*
             * **Aucune trace affichee assez pres : on ne demande RIEN.**
             *
             * Le couloir ne filtrait que l'affichage. A plusieurs centaines de kilometres de toute trace,
             * on interrogeait donc les deux services, on recevait tout, et on jetait tout : la carte
             * restait vide - ce qui est l'effet voulu - mais on avait paye le chargement, et le rond du
             * bouton tournait pour rien. Le reglage doit gouverner la REQUETE, pas seulement ce qu'on en
             * garde.
             *
             * L'emprise est retenue comme chargee : il n'y a rien a redemander tant qu'on ne bouge pas.
             */
            if (!PoiCorridor.crosses(box, corridorTracks, corridorM.toDouble())) {
                state.awayFromTracks(box, filters, osmComplement, maintenant)
                return@LaunchedEffect
            }
            state.beginLoad()
            // Les categories retenues, telles quelles : le depot se charge du cache - service d'abord,
            // dernier connu si le reseau manque (cf. PoiRepository).
            //
            // Un flux et non une liste : les deux sources repondent en parallele, et chacune s'affiche des
            // son arrivee. DATAtourisme repond en une seconde la ou OpenStreetMap en met trente sur une
            // ville dense - les faire attendre l'une l'autre, c'etait trente secondes de carte nue.
            // Ce qui est affiche part avec la demande : il sera repris tant que la requete qui le
            // contredirait n'a pas repondu, au lieu d'etre efface des la premiere source arrivee
            // (cf. poiStream). C'est ce qui faisait disparaitre les points d'OpenStreetMap a chaque geste.
            repo.load(Datatourisme.DEFAULT_URL, box, filters.shown,
                osmBase = osmUrl.ifBlank { Overpass.DEFAULT_URL }, osmComplement = osmComplement,
                affiche = state.pois)
                .collect { charge ->
                // Rien a montrer ET pas de reseau : on ne sait pas si la zone est vide ou si le service n'a
                // pas repondu. L'ecran le dit, plutot que de laisser croire a une region sans un seul cafe.
                val horsLigne = charge.pois.isEmpty() && !NetworkStatus.hasInternet(ctx)
                state.publish(box, filters, charge.pois, osmComplement, charge.fromCache, horsLigne,
                    charge.partial, complete = charge.complete,
                    now = SystemClock.elapsedRealtime())
                // Une source muette met la zone au repos : on la redemandera, mais pas au prochain geste.
                if (charge.failed) state.loadFailed(box, SystemClock.elapsedRealtime())
                state.dropSelectionIfGone()
            }
        } finally {
            state.endLoad()
        }
    }

    // Les marqueurs suivent la liste, l'extinction de la couche, et le RECHARGEMENT DU STYLE : changer de
    // fond de carte reconstruit le style, qui emporte avec lui les couches posees dessus. Sans styleTick,
    // les marqueurs disparaissaient avec l'ancien fond et rien ne les reposait - la couche restait allumee,
    // vide, et le prochain chargement la trouvait deja a jour (cf. needsLoad).
    /*
     * Les marqueurs, filtres LOCALEMENT par les categories retenues.
     *
     * **Le filtre s'applique a la liste deja chargee, sans attendre le service.** Decocher une categorie
     * relance bien un chargement - la requete suivante ne la demandera plus - mais Overpass met trois a
     * trente secondes a repondre sur une ville dense, et pendant tout ce temps les marqueurs decoches
     * restaient sur la carte. On refermait la bulle sur une carte inchangee, en croyant le geste sans
     * effet : "il manque une actualisation quand on ferme la fenetre des choix".
     *
     * Ce filtre ne fait que RETIRER, et c'est pourquoi il suffit : cocher une categorie de plus demande
     * des lieux qu'on n'a pas, et ceux-la ne peuvent venir que du service.
     */
    LaunchedEffect(state.pois, state.showingMarkers, filters, corridorTracks, corridorM, markerPx, styleTick) {
        val montres = if (!state.showingMarkers) emptyList() else {
            val retenus = state.pois.filter { filters.isShown(it.category) }
            // Le couloir sur Default : quelques centaines de lieux contre quelques milliers de sommets, et
            // ce fil-ci est celui de l'interface.
            withContext(Dispatchers.Default) {
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
    // marqueur : une bulle qui decrirait une epingle absente de la carte n'a plus rien a designer.
    LaunchedEffect(filters) { state.dropSelectionIfHidden(filters) }
    LaunchedEffect(controller) {
        controller.onPickPoi = { uuid, _, _ -> state.selectById(uuid) }
    }
}
