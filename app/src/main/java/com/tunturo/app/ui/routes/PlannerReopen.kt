package com.tunturo.app.ui.routes

import com.tunturo.app.ui.geocode.GeocodeSearchState
import com.tunturo.app.ui.mappoint.MapPointState
import com.tunturo.app.ui.poi.PoiState

/**
 * Ce que la bande de calcul rouverte fait disparaitre : le profil d'une trace et les infobulles.
 *
 * Les deux s'excluent deja dans un sens - ouvrir un profil ou une infobulle range la bande (cf. MainScreen).
 * L'autre sens manquait : un tap sur le parcours calcule redeployait la bande par-dessus le profil de la
 * trace consultee juste avant, ou sous l'infobulle d'un appui long ou d'un waypoint, restes ouverts. On
 * revenait au calcul, et la carte montrait encore ce qu'on venait de quitter.
 *
 * [closeTrackViews] ferme le profil et l'infobulle du waypoint (cf. MainViewModel.closeOnEmpty) ; les
 * autres infobulles - appui long, point d'interet, lieu trouve - ont leur etat a l'ecran.
 */
internal fun closeUnderPlanner(
    closeTrackViews: () -> Unit,
    mapPoint: MapPointState,
    poi: PoiState,
    geo: GeocodeSearchState,
) {
    closeTrackViews()
    mapPoint.clear()
    poi.select(null)
    geo.clear()
}
