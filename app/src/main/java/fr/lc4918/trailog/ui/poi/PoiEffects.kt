package fr.lc4918.trailog.ui.poi

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.geocode.NetworkStatus
import fr.lc4918.trailog.poi.Datatourisme
import fr.lc4918.trailog.poi.PoiRepository
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.PoiMarker
import kotlinx.coroutines.delay

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
    filters: PoiFilters,
    idleTick: Int,
    markerPx: Float,
    styleTick: Int,
) {
    val ctx = LocalContext.current

    LaunchedEffect(enabled) { if (enabled == false) state.hide() }

    LaunchedEffect(state.visible, idleTick, filters) {
        if (!state.visible) return@LaunchedEffect
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
            if (!state.needsLoad(vue, filters, maintenant)) return@LaunchedEffect
            val box = PoiLoading.grow(vue)
            state.beginLoad()
            // Deux requetes au plus : celles qui se contentent du catalogue, et celles limitees au theme
            // velo. Un groupe sans categorie cochee ne pese dans aucune des deux. Le depot se charge du
            // cache - service d'abord, dernier connu si le reseau manque (cf. PoiRepository).
            val (libres, velo) = filters.queries()
            // Un flux et non une liste : les deux sources repondent en parallele, et chacune s'affiche des
            // son arrivee. DATAtourisme repond en une seconde la ou OpenStreetMap en met trente sur une
            // ville dense - les faire attendre l'une l'autre, c'etait trente secondes de carte nue.
            repo.load(Datatourisme.DEFAULT_URL, box, libres, velo, osmComplement = osmComplement)
                .collect { charge ->
                // Rien a montrer ET pas de reseau : on ne sait pas si la zone est vide ou si le service n'a
                // pas repondu. L'ecran le dit, plutot que de laisser croire a une region sans un seul cafe.
                val horsLigne = charge.pois.isEmpty() && !NetworkStatus.hasInternet(ctx)
                state.publish(box, filters, charge.pois, charge.fromCache, horsLigne, charge.partial,
                    complete = charge.complete)
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
    LaunchedEffect(state.pois, state.visible, markerPx, styleTick) {
        controller.setPoiMarkers(
            if (state.visible) state.pois.map {
                PoiMarker(it.uuid, it.lon, it.lat, poiGroupColor(it.category.group), poiIcon(it.category))
            } else emptyList(),
            markerPx,
        )
    }
    LaunchedEffect(controller) {
        controller.onPickPoi = { uuid, _, _ -> state.selectById(uuid) }
    }
}
