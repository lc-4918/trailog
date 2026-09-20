package fr.lc4918.trailog.location

import fr.lc4918.trailog.domain.geo.AutoFollow
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
 * resultat. La trace, elle, n'est plus choisie a la main : elle se reconnait toute seule, tableau de bord
 * ouvert, parmi celles que l'ecran a deposees (cf. [FollowCatalog], [detect]).
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
        // Une trace qu'on vient de reconnaitre part cloche eteinte : c'est a soi de l'armer. Sauf si c'est
        // celle sur laquelle on l'avait armee, avant une coupure de la localisation (cf. bellKey).
        _armed.value = keyOf(f) == _bellKey.value
        _alerting.value = false
        _silenced.value = false
        _direction.value = 1
        horsSeuil = 0
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
        _armed.value = keyOf(f) == _bellKey.value
        _awayM.value = null
        _alongM.value = null
        // Le chronometre repart de la reprise, faute de mieux : on ne sait plus depuis quand on roulait, et
        // une duree inventee serait pire qu'une duree qui recommence - le tableau de bord la donne pour ce
        // qu'elle est, le temps ecoule DEPUIS LE DEBUT DU SUIVI.
        _startedAtMs.value = nowMs
        _alerting.value = false
        _silenced.value = false
    }

    /**
     * Fin du suivi : plus de trace, plus d'ecart, plus d'alerte.
     *
     * [forgetBell] faux : la localisation a ete coupee, et la cloche attend la meme trace au rallumage
     * (cf. [bellKey]). Tous les autres arrets sont des gestes, et l'oublient.
     */
    fun stop(forgetBell: Boolean = true) {
        if (forgetBell) _bellKey.value = null
        _armed.value = false
        _direction.value = 1
        horsSeuil = 0
        detection = AutoFollow.Detection()
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
    fun update(away: Double, thresholdM: Double, alongM: Double? = null, accuracyM: Double = 0.0): Boolean {
        _awayM.value = away
        if (alongM != null) _alongM.value = alongM
        val avant = _alerting.value
        val next = OffTrack.alerting(avant, away, thresholdM, accuracyM)
        if (!next) _silenced.value = false
        _alerting.value = next
        return next && !avant
    }

    /** Croix de la banniere. */
    fun silence() { _silenced.value = true }

    // ---------- Le suivi automatique et la cloche ----------

    private val _dashboard = MutableStateFlow(false)

    /**
     * Le tableau de bord est ouvert : c'est lui qui fait chercher une trace a suivre. Ferme, le suivi
     * s'arrete - il n'aurait plus nulle part ou se dire.
     */
    val dashboard: StateFlow<Boolean> = _dashboard.asStateFlow()

    fun setDashboard(open: Boolean) {
        _dashboard.value = open
        if (!open) stop()
    }

    private val _armed = MutableStateFlow(false)

    /**
     * La cloche du tableau de bord : l'alerte d'eloignement est armee sur la trace suivie.
     *
     * Eteinte, s'ecarter de la trace ne sonne pas - on la LACHE, en silence : on a change de chemin, et le
     * tableau de bord cesse d'en annoncer le restant (cf. [update]). Allumee, s'ecarter est l'alerte.
     */
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    private val _direction = MutableStateFlow(1)

    /** Le sens de parcours de la trace suivie : +1 du debut vers la fin, -1 a l'envers. */
    val direction: StateFlow<Int> = _direction.asStateFlow()

    /** Ce que la detection a vu jusqu'ici, hors de tout suivi (cf. [AutoFollow.detect]). */
    @Volatile private var detection = AutoFollow.Detection()

    /** Positions consecutives au-dela du seuil, cloche eteinte (cf. [AutoFollow.leave]). */
    @Volatile private var horsSeuil = 0

    private val _bellKey = MutableStateFlow<String?>(null)

    /**
     * La trace sur laquelle la cloche a ete armee ("couche/segment"), ou null.
     *
     * Elle survit a une coupure de la localisation : le suivi s'arrete, faute de positions, mais la meme
     * trace reconnue au rallumage retrouve sa cloche armee (cf. [follow]) - on l'avait armee pour etre
     * prevenu, et une coupure ne change rien a ce qu'on voulait. Gardee sur le disque avec la trace suivie
     * (cf. FollowedStore). Seul un geste l'oublie : desarmer, fermer le tableau de bord, masquer la trace.
     */
    val bellKey: StateFlow<String?> = _bellKey.asStateFlow()

    private fun keyOf(f: Followed?): String? = f?.let { "${it.layerId}/${it.trackIndex}" }

    /** La cloche reprise du disque : elle ne s'impose pas a une cloche deja reglee dans ce processus. */
    fun restoreBell(key: String?) {
        if (key == null || _bellKey.value != null) return
        _bellKey.value = key
        if (keyOf(_followed.value) == key) _armed.value = true
    }

    fun setArmed(on: Boolean) {
        _armed.value = on
        _bellKey.value = if (on) keyOf(_followed.value) else null
        // Desarmee : l'alerte en cours se tait, et l'on recompte les ecarts depuis zero.
        if (!on) { _alerting.value = false; _silenced.value = false }
        horsSeuil = 0
    }

    /**
     * Une position de plus, hors de tout suivi : rend la trace reconnue quand elle l'est, et en commence le
     * suivi - cloche eteinte, dans le sens ou on la parcourt.
     */
    fun detect(lat: Double, lon: Double, candidates: List<AutoFollow.Candidate>, thresholdM: Double, nowMs: Long): Boolean {
        val (etat, trouve) = AutoFollow.detect(detection, lat, lon, candidates)
        detection = etat
        val m = trouve ?: return false
        val c = m.candidate
        follow(Followed(c.id, c.name, c.trackIndex, c.trackCount, c.samples), m.awayM, thresholdM, m.alongM, nowMs)
        _direction.value = m.direction
        return true
    }

    /**
     * Une position de plus sur la trace suivie. Rend ce qu'il faut en faire : [Step.Alert] a l'entree en
     * alerte (le son), [Step.Leave] quand, cloche eteinte, on a quitte la trace.
     *
     * [accuracyM] est l'incertitude de la position : elle borne ce qu'on ose en conclure (cf.
     * [OffTrack.alerting]). Zero par defaut - c'est ce que valait le suivi quand il ne vivait que du GPS.
     */
    fun step(away: Double, thresholdM: Double, alongM: Double, accuracyM: Double = 0.0): Step {
        _direction.value = AutoFollow.direction(_direction.value, _alongM.value, alongM, accuracyM)
        if (_armed.value) return if (update(away, thresholdM, alongM, accuracyM)) Step.Alert else Step.Stay
        _awayM.value = away
        _alongM.value = alongM
        // L'ecart dont on est SUR : une position floue - reseau, GPS sous couvert - ne fait pas lacher
        // une trace qu'on suit peut-etre encore.
        val (n, lacher) = AutoFollow.leave(horsSeuil, OffTrack.sureAwayM(away, accuracyM), thresholdM)
        horsSeuil = n
        return if (lacher) Step.Leave else Step.Stay
    }

    enum class Step { Stay, Alert, Leave }
}
