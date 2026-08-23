package fr.lc4918.trailog.ui.routes

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * **Ce qui les reunit, et pourquoi elles sont posees en dernier.** Toutes les trois disent la meme
 * categorie d'ennui - "ce que tu regardes ne correspond plus a ce qui se passe" - et aucune ne repond a un
 * geste qu'on vient de faire. C'est ce qui leur donne le droit de passer PAR-DESSUS tout ce qui occupe le
 * bas de l'ecran : profil, bande du planificateur, consignes de saisie. Les autres barres du bas
 * accompagnent une action en cours et peuvent attendre leur tour ; une alerte qu'un panneau recouvre
 * n'alerte personne.
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
) {
    /*
     * Bannière de l'alerte d'éloignement, posée EN DERNIER : elle passe donc par-dessus tout ce
     * qui occupe le bas de l'écran - profil, bande du planificateur, consignes de saisie.
     *
     * C'est la seule barre du bas à s'accorder ce droit, et c'est ce qui la distingue : les
     * autres accompagnent un geste qu'on vient de faire et peuvent attendre leur tour, celle-ci
     * dit qu'on ne suit plus le chemin prévu. Une alerte qu'un panneau recouvre n'alerte
     * personne, et la refermer d'un tap sur sa croix reste à un doigt.
     */
    followed?.takeIf { alerting }?.let { suivie ->
        OffTrackAlertBar(
            trackName = suivie.layerName,
            awayM = awayM ?: alertDistanceM.toDouble(),
            imperial = imperial,
            onClose = { TrackWatch.silence() },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
    /*
     * Le suivi s'est arrete tout seul.
     *
     * Au meme endroit et au meme rang que l'alerte d'eloignement : ce sont les deux seules choses
     * que la carte annonce d'elle-meme, et aucun panneau ne doit les recouvrir. Elles ne se
     * disputent jamais la place - un suivi arrete n'a plus d'ecart a mesurer.
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
