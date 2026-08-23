package fr.lc4918.trailog.ui.routes

import androidx.lifecycle.ViewModel
import fr.lc4918.trailog.ui.alert.OffTrackAlertState
import fr.lc4918.trailog.ui.edit.TrackEditState
import fr.lc4918.trailog.ui.geocode.GeocodeSearchState
import fr.lc4918.trailog.ui.mappoint.MapPointState
import fr.lc4918.trailog.ui.measure.TrackMeasureState
import fr.lc4918.trailog.ui.offline.OfflineFlowState
import fr.lc4918.trailog.ui.planner.RoutePlannerState
import fr.lc4918.trailog.ui.poi.PoiState

/**
 * Le travail en cours de l'ecran de carte, garde le temps d'une rotation.
 *
 * **Ce qu'un quart de tour emportait.** Ces neuf porteurs naissaient en `remember` dans `MainScreen`, et
 * `MainActivity` ne declare aucun `configChanges` : une rotation recree l'activite, la composition repart
 * de zero, et tout ce qui etait en cours partait avec elle - l'itineraire compose etape par etape, la
 * mesure dont le premier point venait d'etre pose, l'emprise hors-ligne a moitie tracee, le mode retouche.
 *
 * Le contraste etait devenu genant : le retour Android DEMANDE avant de perdre un itineraire (cf.
 * `MapBackHandlers`), pendant qu'un mouvement du poignet l'emportait sans un mot. Un `ViewModel` survit a
 * la recreation de l'activite ; c'est tout ce qu'il faut, et il n'y a rien d'autre a ecrire.
 *
 * **Ce qui reste dehors, et pourquoi.** `MapChromeState` et `MapInsetsState` ne portent que des mesures de
 * pixels relevees a la pose - hauteur de la colonne de boutons, ancrage d'une legende. Les faire survivre
 * a une rotation, c'est justement garder les mesures d'un ecran qui n'a plus les memes dimensions : elles
 * doivent au contraire repartir de zero et se relever.
 *
 * **Aucun de ces porteurs ne connait de Context**, ce qui est la condition pour vivre dans un `ViewModel`
 * sans retenir une activite morte. La regle se verifie mecaniquement :
 *
 * ```
 * grep -rn "Context" app/src/main/java/fr/lc4918/trailog/ui --include="*State.kt"
 * ```
 *
 * Ne pas confondre avec la mort du PROCESSUS, qui emporte aussi ce ViewModel : seule la trace suivie y
 * survit, et par le disque (cf. `FollowedStore`). Elle est la seule dont la perte silencieuse trompe.
 */
class MapScreenStates : ViewModel() {
    val offline = OfflineFlowState()
    val alert = OffTrackAlertState()
    val geo = GeocodeSearchState()
    val dialogs = MainDialogState()
    val measure = TrackMeasureState()
    val mapPoint = MapPointState()
    val planner = RoutePlannerState()
    val edit = TrackEditState()
    val poi = PoiState()
}
