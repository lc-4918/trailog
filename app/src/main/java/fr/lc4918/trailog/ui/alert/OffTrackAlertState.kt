package fr.lc4918.trailog.ui.alert

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.location.FollowCatalog

/**
 * L'itineraire du planificateur, qui n'est encore dans aucune couche.
 *
 * Un parcours calcule se suit comme n'importe quelle trace - c'est meme le cas le plus courant : on
 * compose son trajet, on part, et le tableau de bord le reconnait. L'importer d'abord dans la
 * bibliotheque serait un detour, et laisserait derriere soi une couche dont on ne voulait pas.
 *
 * Un identifiant NEGATIF, donc hors de portee des identifiants de couches (Room les attribue a partir de
 * 1) : c'est ce qui permet de reconnaitre le parcours temporaire la ou l'on verifie que la couche suivie
 * est toujours a l'ecran (cf. OffTrackAlertEffects).
 */
const val PlannedRouteLayerId = FollowCatalog.ROUTE_ID

/**
 * Ce qui, du tableau de bord, est une affaire d'ecran : la question posee quand la localisation du
 * telephone est coupee.
 *
 * Le tableau de bord OUVERT, lui, vit hors de la composition (cf. `TrackWatch.dashboard`) : c'est lui qui
 * fait chercher une trace au service, ecran eteint compris.
 */
@Stable
class OffTrackAlertState {

    /**
     * Le tableau de bord a ete ouvert alors que **la localisation du telephone est eteinte** : on propose
     * d'aller l'allumer dans les reglages du systeme. Sans capteur, il n'aurait rien a compter.
     */
    var needsGpsDialog by mutableStateOf(false)
        private set

    /**
     * Le capteur est demande, et le tableau de bord attend qu'il reponde pour l'allumer : l'utilisateur
     * passe par les reglages du systeme entre-temps, et la demande doit tenir toute seule a son retour.
     */
    var gpsPending by mutableStateOf(false)
        private set

    /** Le tableau de bord s'ouvre : il demande la localisation du telephone quand elle est coupee. */
    fun onOpen(sensorEnabled: Boolean) {
        if (!sensorEnabled) needsGpsDialog = true
    }

    /** "Allumer" : la boite se referme, et l'allumage attend le capteur. */
    fun awaitGps() {
        needsGpsDialog = false
        gpsPending = true
    }

    fun dismissNeedsGps() { needsGpsDialog = false }

    /** Le capteur repond : rend vrai une fois, quand l'allumage mis en attente doit se faire. */
    fun consumePending(sensorEnabled: Boolean): Boolean {
        if (!gpsPending || !sensorEnabled) return false
        gpsPending = false
        return true
    }

    fun reset() {
        needsGpsDialog = false
        gpsPending = false
    }
}
