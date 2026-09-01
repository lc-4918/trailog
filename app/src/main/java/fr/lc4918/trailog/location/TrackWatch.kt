package fr.lc4918.trailog.location

import fr.lc4918.trailog.domain.geo.OffTrack
import fr.lc4918.trailog.domain.model.Sample
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La veille sur la trace qu'on suit : la trace retenue, l'ecart du moment, et l'alerte qui en decoule.
 *
 * **Pourquoi hors de l'ecran**, comme [LocationHub] et pour la meme raison : l'ecart se mesurait dans la
 * composition de la carte, si bien que l'ecran eteint plus rien ne se calculait. Une alerte qui ne se
 * declenche que sous les yeux de celui qu'elle doit prevenir n'alerte personne : c'est justement le
 * telephone en poche qu'on veut voir sonner.
 *
 * L'etat vit donc ici, [LocationService] le nourrit a chaque position, et l'ecran n'en lit que le
 * resultat. Le choix de la trace, lui, reste a l'ecran (cf. `OffTrackAlertState`) : c'est une
 * conversation, pas une mesure.
 */
object TrackWatch {

    /**
     * La trace qu'on suit des yeux : son identite, et le parcours sur lequel la position se rabat.
     *
     * Serialisable parce qu'elle doit SURVIVRE AU PROCESSUS (cf. [FollowedStore]) : les echantillons
     * voyagent avec elle, et c'est ce qui permet de reprendre la veille sans rien relire ni savoir d'ou
     * la trace venait - une couche de la bibliotheque, ou un parcours qui n'y est jamais entre.
     */
    @Serializable
    data class Followed(
        val layerId: Long,
        val layerName: String,
        val trackIndex: Int,
        val trackCount: Int,
        val samples: List<Sample>,
    )

    private val _followed = MutableStateFlow<Followed?>(null)

    /** Trace suivie, ou null : le bouton est alors une cloche eteinte, et rien ne se calcule. */
    val followed: StateFlow<Followed?> = _followed.asStateFlow()

    private val _awayM = MutableStateFlow<Double?>(null)

    /** Ecart a la trace suivie a la derniere position recue (m). */
    val awayM: StateFlow<Double?> = _awayM.asStateFlow()

    private val _alongM = MutableStateFlow<Double?>(null)

    /**
     * Kilometrage sur la trace suivie, depuis son debut (m).
     *
     * Il etait deja calcule a chaque position - la projection qui donne l'ecart le rend du meme coup - et
     * jete aussitot. C'est pourtant lui qui dit ou l'on en est : ce qui reste, le denivele qui reste, et
     * le temps qu'il faudra (cf. `FollowProgressMath`).
     */
    val alongM: StateFlow<Double?> = _alongM.asStateFlow()

    private val _startedAtMs = MutableStateFlow<Long?>(null)

    /**
     * Instant ou le suivi a commence, en temps depuis le demarrage de l'appareil.
     *
     * Pas l'heure murale : une remise a l'heure du reseau ne doit ni allonger ni raccourcir la sortie.
     * Repart a chaque changement de trace - suivre une autre trace, c'est commencer autre chose - et
     * survit a une reprise du disque, ou l'on ne sait plus depuis quand on roule (cf. [restore]).
     */
    val startedAtMs: StateFlow<Long?> = _startedAtMs.asStateFlow()

    private val _alerting = MutableStateFlow(false)

    /** Au-dela du seuil regle, avec sa marge de retour (cf. [OffTrack.alerting]). */
    val alerting: StateFlow<Boolean> = _alerting.asStateFlow()

    private val _silenced = MutableStateFlow(false)

    /**
     * Banniere tue d'un tap sur sa croix. Ce n'est pas un arret du suivi : on sait qu'on est loin, on ne
     * veut plus le lire. Le silence est leve des qu'on revient sur la trace, et l'alerte suivante se dira.
     */
    val silenced: StateFlow<Boolean> = _silenced.asStateFlow()

    /**
     * Trace retenue : on repart d'une alerte vierge, l'ecart de la trace precedente n'ayant rien a dire de
     * celle-ci. L'ecart est en revanche connu d'emblee - c'est celui qui a servi a classer les candidates.
     */
    fun follow(f: Followed, awayM: Double, thresholdM: Double, alongM: Double? = null, nowMs: Long = 0L) {
        _followed.value = f
        _awayM.value = awayM
        _alongM.value = alongM
        // Le chronometre repart : suivre une autre trace, c'est commencer autre chose.
        _startedAtMs.value = nowMs
        _alerting.value = awayM >= thresholdM
        _silenced.value = false
    }

    /**
     * Reprise apres une mort du processus : la trace retrouvee sur le disque redevient la trace suivie.
     *
     * **Ne s'impose jamais a un suivi en cours** : le service peut etre relance alors que l'ecran vient
     * d'en choisir une autre, et la trace d'hier ne doit pas reprendre la place de celle qu'on vient de
     * designer. L'ecart et l'alerte, eux, repartent a zero : ils se mesurent a la prochaine position, et
     * celui d'avant l'arret ne dit rien d'ou l'on est maintenant.
     */
    fun restore(f: Followed?, nowMs: Long = 0L) {
        if (f == null || _followed.value != null) return
        _followed.value = f
        _awayM.value = null
        _alongM.value = null
        // Le chronometre repart de la reprise, faute de mieux : on ne sait plus depuis quand on roulait, et
        // une duree inventee serait pire qu'une duree qui recommence - le tableau de bord la donne pour ce
        // qu'elle est, le temps ecoule DEPUIS LE DEBUT DU SUIVI.
        _startedAtMs.value = nowMs
        _alerting.value = false
        _silenced.value = false
    }

    /** Fin du suivi : plus de trace, plus d'ecart, plus d'alerte. */
    fun stop() {
        _followed.value = null
        _awayM.value = null
        _alongM.value = null
        _startedAtMs.value = null
        _alerting.value = false
        _silenced.value = false
    }

    /**
     * Une position de plus : l'ecart mesure, et l'alerte qui en decoule.
     *
     * Rend **vrai a la seule entree en alerte**, et c'est ce qui fait sonner le son une fois : il annonce
     * le franchissement, il ne sonne pas tant qu'on est loin. Revenir sous le seuil leve le silence - la
     * croix ne tait que l'ecart du moment, pas la fonction - et rearme donc l'annonce suivante.
     */
    fun update(away: Double, thresholdM: Double, alongM: Double? = null): Boolean {
        _awayM.value = away
        if (alongM != null) _alongM.value = alongM
        val avant = _alerting.value
        val next = OffTrack.alerting(avant, away, thresholdM)
        if (!next) _silenced.value = false
        _alerting.value = next
        return next && !avant
    }

    /** Croix de la banniere. */
    fun silence() { _silenced.value = true }
}
