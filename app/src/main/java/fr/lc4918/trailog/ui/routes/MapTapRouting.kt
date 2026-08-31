package fr.lc4918.trailog.ui.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.edit.CutTarget
import fr.lc4918.trailog.ui.edit.EditTool
import fr.lc4918.trailog.ui.edit.SegmentRef
import fr.lc4918.trailog.ui.edit.TrackEditState
import fr.lc4918.trailog.ui.mappoint.MapPointState
import fr.lc4918.trailog.ui.measure.TrackMeasureState
import fr.lc4918.trailog.ui.offline.OfflineFlowState
import fr.lc4918.trailog.ui.planner.RoutePlannerState

/**
 * A QUI revient un tap sur la carte.
 *
 * **Le probleme que cela resout.** Six choses veulent les taps, et jamais dans le meme sens : le trace
 * de l'emprise hors-ligne, le choix des deux points d'une mesure, le choix du point de reference d'une
 * distance, le choix d'une etape d'itineraire montree du doigt, la retouche des traces, et - a defaut - la
 * selection habituelle qui ouvre un profil ou une infobulle. Ce sont des MODES exclusifs, et l'ordre dans
 * lequel on les departage est une regle a part entiere : disperse dans l'ecran, il se serait defait au
 * premier mode ajoute.
 *
 * Ils ne sont de fait jamais actifs ensemble - le trace d'emprise part du menu lateral, qui ferme la
 * carte ; les deux suivants partent d'une barre ou d'une infobulle que l'autre a fait disparaitre - mais
 * l'ordre reste ecrit : celui qui occupe deja l'ecran garde les taps.
 */
@Composable
internal fun MapTapRouting(
    controller: MapController,
    offline: OfflineFlowState,
    measure: TrackMeasureState,
    mapPoint: MapPointState,
    planner: RoutePlannerState,
    edit: TrackEditState,
    layers: List<LayerEntity>,
    vm: MainViewModel,
) {
    val sameSegmentMessage = stringResource(R.string.edit_same_segment)
    // Libelle provisoire d'une etape montree du doigt : ses coordonnees, le temps que le geocodeur en
    // rende l'adresse (cf. RoutePlannerState.pickOnMap). Point decimal impose, comme partout ou l'on ecrit
    // un couple de coordonnees : la virgule d'une locale francaise separerait a la fois les decimales et
    // les deux valeurs, et donnerait "44,56, 6,08".
    fun coordLabel(lon: Double, lat: Double) = "%.5f, %.5f".format(java.util.Locale.US, lat, lon)

    // Modes de saisie exclusifs (tracé de la bounding box hors-ligne, choix des points de mesure, choix du
    // point de référence d'une distance) : tout tap leur revient, y compris sur une trace ou un marqueur,
    // qui n'ouvrent alors ni profil ni infobulle. Hors de ces modes, la sélection habituelle reprend.
    //
    // Ils ne sont jamais actifs ensemble (le tracé de bbox part du menu latéral, qui ferme la carte ; les
    // deux autres partent d'une barre ou d'une infobulle que l'autre a fait disparaître), mais l'ordre
    // reste explicite : celui qui occupe déjà l'écran garde les taps.
    LaunchedEffect(controller, offline.drawingActive, measure.picking, mapPoint.pickingPoint,
        planner.pickingOnMap, edit.awaitingTap) {
        when {
            offline.drawingActive -> controller.onRawTap = { lon, lat ->
                if (offline.bboxPoints.size < 2) offline.bboxPoints = offline.bboxPoints + (lon to lat)
            }
            // Le point retenu n'est pas celui du doigt mais son projeté sur la trace : le calcul passe par
            // le ViewModel, seul à savoir lire les profils des couches (cf. pickMeasureStart).
            measure.picking -> controller.onRawTap = { lon, lat ->
                val started = measure.start
                if (started == null) {
                    // Couches candidates demandées d'abord à l'index de rendu de la carte : lui seul sait
                    // dire, sans rien lire, quelles traces passent vraiment sous le doigt.
                    val keys = controller.lineKeysNear(lon, lat)
                    vm.pickMeasureStart(lon, lat, keys) { p -> if (p != null && measure.picking) measure.chooseStart(p) }
                } else {
                    vm.pickMeasureEnd(started, lon, lat) { p, path -> if (measure.picking) measure.chooseEnd(p, path) }
                }
            }
            mapPoint.pickingPoint -> controller.onRawTap = { lon, lat -> mapPoint.chooseRefPoint(lon, lat) }
            // Etape du planificateur montree du doigt : le tap lui revient ENTIER, y compris sur un
            // marqueur, un point d'interet ou une trace - on designe un ENDROIT, et ouvrir une infobulle
            // ou un profil par-dessus la consigne repondrait a une question qu'on n'a pas posee.
            planner.pickingOnMap -> controller.onRawTap = { lon, lat ->
                planner.pickOnMap(lon, lat, coordLabel(lon, lat))
            }
            // Retouche : les taps sur une trace lui reviennent, et n'ouvrent donc pas de profil. Le vide
            // ne referme rien - sortir du mode se fait par la barre, pas par un tap a cote.
            edit.awaitingTap -> {
                controller.onRawTap = null
                controller.onPickPoint = null
                controller.onPickLine = { key, lon, lat -> onEditTap(key, lon, lat, edit, layers, vm, sameSegmentMessage) }
                controller.onTapEmpty = null
            }
            else -> {
                controller.onRawTap = null
                controller.onPickPoint = { key, fid, lon, lat -> vm.onPickPoint(key, fid, lon, lat) }
                controller.onPickLine = { key, lon, lat -> vm.onPickLine(key, lon, lat) }
                controller.onTapEmpty = { vm.closeOnEmpty() }
            }
        }
    }
}

/**
 * Tap sur une trace en mode retouche : selon l'outil, il inverse, pose le marqueur de coupe, ou
 * designe un segment a joindre.
 *
 * Le point retenu n'est jamais celui du doigt mais son PROJETE sur la geometrie complete (cf.
 * TrackEdit.locate) : c'est ce qui permet de couper entre deux sommets, la ou le curseur du profil ne
 * savait designer que des points deja presents.
 */
private fun onEditTap(
    key: String,
    lon: Double,
    lat: Double,
    edit: TrackEditState,
    layers: List<LayerEntity>,
    vm: MainViewModel,
    sameSegmentMessage: String,
) {
    val id = key.removePrefix("ly").toLongOrNull() ?: return
    val layer = layers.firstOrNull { it.id == id }?.takeIf { it.hasLine } ?: return
    when (edit.tool) {
        EditTool.REVERSE -> {
            if (layer.hasTime) edit.reverseConfirm = layer else vm.reverseLayer(layer)
            edit.choose(EditTool.NONE)
        }
        EditTool.CUT -> {
            edit.busy = true
            // La geometrie sert a la bulle du marqueur, qui doit savoir ce qu'elle recouvrirait.
            vm.loadCutGeometry(layer)
            vm.locateOnLayer(layer, lon, lat) { hit ->
                edit.busy = false
                if (hit != null) edit.placeCut(CutTarget(layer.id, layer.name, hit))
            }
        }
        EditTool.JOIN -> {
            edit.busy = true
            vm.locateOnLayer(layer, lon, lat) { hit ->
                edit.busy = false
                if (hit != null) edit.pick(SegmentRef(layer.id, layer.name, hit.segment), sameSegmentMessage)
            }
        }
        EditTool.NONE -> Unit
    }
}
