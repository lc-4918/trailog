package fr.lc4918.trailog.ui.routes

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import fr.lc4918.trailog.R
import fr.lc4918.trailog.location.LocationHub
import fr.lc4918.trailog.location.TrackWatch
import fr.lc4918.trailog.ui.alert.OffTrackAlertBar
import fr.lc4918.trailog.ui.location.LocationControls
import fr.lc4918.trailog.ui.location.LocationNoticeBar

/**
 * Les trois choses que la carte annonce D'ELLE-MEME : on quitte la trace suivie, le suivi s'est arrete
 * tout seul, le repere ne bouge plus.
 *
 * **Ce qui les reunit.** Toutes les trois disent la meme categorie d'ennui - "ce que tu regardes ne
 * correspond plus a ce qui se passe" - et aucune ne repond a un geste qu'on vient de faire.
 *
 * **Elles n'ont pourtant pas la meme place.** L'ecart a la trace suivie a rejoint le haut de l'ecran, au
 * cote de l'avertissement de zoom des points d'interet (cf. PoiStatusBanner) : les deux sont du meme ordre,
 * un fait que la carte constate d'elle-meme, indifferent a ce qui occupe le bas. L'arret du suivi, lui,
 * reste pose EN DERNIER au bas de l'ecran, par-dessus tout ce qui s'y trouve deja - profil, bande du
 * planificateur, consignes de saisie - la ou une alerte qu'un panneau recouvre n'alerte personne.
 *
 * **Elles ne se disputent jamais la place.** Un suivi arrete n'a plus d'ecart a mesurer.
 *
 * **Ce qui n'y est plus.** Une troisieme banniere annoncait la peremption du repere - "Position figee
 * depuis x". Elle disait un fait reel, mais au mauvais endroit : un trou de reception dure ce qu'il dure -
 * une gorge, un couvert, un tunnel - et la banniere occupait alors le bas de la carte sans que personne
 * puisse rien y faire. Une alerte qu'on ne peut ni corriger ni eviter finit par se lire comme un decor,
 * ce qui est exactement ce qu'une alerte ne doit pas devenir. Le fait, lui, reste dit : le repere passe
 * au gris sur la carte (cf. LocationControls.positionStale), la ou il ment.
 *
 * L'ecart et le son ne se calculent pas ici mais dans le service (cf. LocationService.watchTrack) :
 * l'ecran eteint, la composition s'arrete, et une alerte qui ne se declenche que sous les yeux de celui
 * qu'elle doit prevenir n'alerte personne. Ce calque n'en lit que le resultat.
 *
 * @param alerting on est au-dela de l'ecart regle, et l'alerte n'a pas ete tue.
 * @param awayM l'ecart mesure, ou null tant que la premiere mesure n'est pas arrivee.
 * @param topControlsPx hauteur de la colonne de boutons du haut : la banniere s'y glisse juste dessous,
 *   au meme endroit que celle qui propose de zoomer pour voir les points d'interet (cf. PoiStatusBanner) -
 *   ce sont deux avertissements de meme nature, et le regard doit les trouver au meme endroit.
 */
@Composable
internal fun BoxScope.MapNoticeLayer(
    location: LocationControls,
    followed: TrackWatch.Followed?,
    alerting: Boolean,
    awayM: Double?,
    alertDistanceM: Int,
    stopNotice: LocationHub.StopReason?,
    imperial: Boolean,
    topControlsPx: Int,
) {
    /*
     * Bannière de l'alerte d'éloignement, sous les commandes du haut - au même endroit que celle
     * qui propose de zoomer pour voir les points d'intérêt (cf. PoiStatusBanner) : ce sont deux
     * avertissements que la carte se fait à elle-même, et non la réponse à un geste, d'où leur
     * position commune, à l'écart de ce qui occupe le bas de l'écran (profil, bande du
     * planificateur, consignes de saisie) et que ces boutons du bas recouvriraient sinon.
     */
    val density = LocalDensity.current
    followed?.takeIf { alerting }?.let { suivie ->
        OffTrackAlertBar(
            trackName = suivie.layerName,
            awayM = awayM ?: alertDistanceM.toDouble(),
            imperial = imperial,
            onClose = { TrackWatch.silence() },
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(top = with(density) { (topControlsPx + 16).toDp() }),
        )
    }
    /*
     * Le suivi s'est arrete tout seul.
     *
     * Reste au bas de l'ecran, a la difference de l'alerte d'eloignement qui a rejoint le haut (cf. la
     * documentation de MapNoticeLayer) : elle porte un bouton "Reprendre", et se lit donc comme les autres
     * commandes du bas plutot que comme un simple constat. Aucun panneau ne doit la recouvrir pour autant.
     */
    val arret = stopNotice
    if (arret != null) {
        LocationNoticeBar(
            text = stringResource(
                if (arret == LocationHub.StopReason.SENSOR_OFF) R.string.location_stopped_sensor
                else R.string.location_stopped_system,
            ),
            onDismiss = { location.dismissStopNotice() },
            actionLabel = stringResource(R.string.location_stopped_resume),
            onAction = { location.dismissStopNotice(); location.onGpsButtonTap() },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}
