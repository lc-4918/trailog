package fr.lc4918.trailog.location

import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La position du porteur, une seule fois pour toute l'application.
 *
 * **Pourquoi un objet de processus et non un etat d'ecran.** Le capteur etait ecoute depuis la carte, et
 * ses positions vivaient dans la composition : l'ecran eteint, Android arretait la recomposition, et le
 * suivi s'arretait avec elle - au moment precis ou il sert, telephone en poche. Le flux vit donc
 * desormais hors de l'ecran, alimente par [LocationService], et la carte n'en est qu'un lecteur parmi
 * d'autres.
 *
 * C'est aussi ce qu'exigera un enregistreur de trace : ecrire un point toutes les deux secondes ne peut
 * pas dependre de ce qui est affiche.
 *
 * Rien ici ne demande de position : ce sont deux roles distincts, et les separer permet a n'importe quel
 * lecteur - carte, veille sur la trace, notification - de se brancher sans savoir qui a allume le capteur
 * ni comment.
 */
object LocationHub {

    /**
     * Une mesure du capteur, reduite a ce dont l'application se sert.
     *
     * [bearingDeg], [speedMps] et [altitudeM] sont nullables parce que le systeme ne les donne pas
     * toujours : une position reseau n'a ni cap ni altitude, et un `Location` les rend alors a zero, ce
     * qui se lit comme une vraie valeur. Ils sont ici absents plutot que faux.
     *
     * [timeMs] est l'heure de la MESURE et non celle de sa reception : c'est elle qu'un enregistrement
     * horodaterait.
     */
    data class Fix(
        val lat: Double,
        val lon: Double,
        val accuracyM: Float,
        val bearingDeg: Float?,
        val speedMps: Float?,
        val altitudeM: Double?,
        val timeMs: Long,
        val receivedAtMs: Long,
    )

    /**
     * Pourquoi le suivi s'est arrete.
     *
     * La distinction qui compte est la premiere : un arret DEMANDE n'a rien a annoncer, un arret SUBI est
     * exactement ce que personne ne remarque. Un testeur a fait vingt kilometres dans le mauvais sens
     * parce que son repere avait disparu sans un mot.
     */
    enum class StopReason {
        /** Le bouton, la notification, le reglage, l'application balayee des recentes. */
        USER,

        /** La localisation coupee dans le telephone - economie d'energie comprise. */
        SENSOR_OFF,

        /** Le service tue, ou le capteur devenu muet : personne n'a rien demande. */
        SYSTEM,
    }

    private val _fix = MutableStateFlow<Fix?>(null)

    /** La derniere position connue, ou null tant que rien n'a ete recu depuis le dernier arret. */
    val fix: StateFlow<Fix?> = _fix.asStateFlow()

    private val _tracking = MutableStateFlow(false)

    /**
     * Le suivi tourne : le service est demarre et le capteur ecoute.
     *
     * L'ecran s'y raccroche au lieu de tenir son propre drapeau, ce qui le fait retrouver le suivi tel
     * quel apres un passage en arriere-plan - ou apres avoir ete detruit puis recree, la rotation par
     * exemple, ou l'application revenue au premier plan.
     */
    val tracking: StateFlow<Boolean> = _tracking.asStateFlow()

    fun publish(loc: Location) {
        _fix.value = Fix(
            lat = loc.latitude,
            lon = loc.longitude,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy else 0f,
            bearingDeg = if (loc.hasBearing()) loc.bearing else null,
            speedMps = if (loc.hasSpeed()) loc.speed else null,
            altitudeM = if (loc.hasAltitude()) loc.altitude else null,
            timeMs = loc.time,
            // Temps depuis le demarrage de l'appareil, et non heure murale : c'est l'AGE de la mesure qui
            // dira si le repere ment encore, et une remise a l'heure du reseau ne doit pas le rajeunir.
            receivedAtMs = SystemClock.elapsedRealtime(),
        )
    }

    private val _wanted = MutableStateFlow(false)

    /**
     * L'utilisateur VEUT le suivi.
     *
     * Distinct de [tracking], qui dit seulement s'il tourne a cet instant. Ce qui les separe est tout ce
     * qui l'interrompt sans qu'on l'ait demande : la localisation coupee par l'economie d'energie, le
     * service tue par le systeme, le capteur qui ne repond plus. Le suivi doit alors REPRENDRE seul, et
     * c'est ce drapeau qui le dit - sans lui, l'application abandonnait definitivement, en silence.
     *
     * De la duree du processus et non de la base : un drapeau persiste rallumerait le capteur au prochain
     * lancement, des jours plus tard, pour une sortie finie depuis longtemps.
     */
    val wanted: StateFlow<Boolean> = _wanted.asStateFlow()

    private val _stopNotice = MutableStateFlow<StopReason?>(null)

    /**
     * Un arret SUBI que l'ecran n'a pas encore annonce, ou null.
     *
     * Ne porte jamais [StopReason.USER] : ce qu'on vient de demander n'a pas a etre annonce.
     */
    val stopNotice: StateFlow<StopReason?> = _stopNotice.asStateFlow()

    /** Le geste d'allumer : le suivi est voulu jusqu'a nouvel ordre. */
    fun wantTracking() {
        _wanted.value = true
        _stopNotice.value = null
    }

    /** Le geste d'eteindre. Ce qui suit ne sera pas annonce, et rien ne rallumera. */
    fun stopRequestedByUser() {
        _wanted.value = false
        _stopNotice.value = null
    }

    /** L'ecran a dit l'arret : l'annonce est consommee. */
    fun clearStopNotice() { _stopNotice.value = null }

    /**
     * Debut ou fin du suivi. L'arret oublie la derniere position : la garder ferait reapparaitre le repere
     * a l'endroit d'hier au prochain allumage, avant meme que le capteur n'ait repondu.
     *
     * [reason] ne sert qu'a l'arret. Un arret qui survient alors que le suivi est encore [wanted] est subi,
     * et se dit : c'est le seul moment ou l'application a quelque chose a apprendre a son porteur.
     */
    fun setTracking(on: Boolean, reason: StopReason = StopReason.SYSTEM) {
        _tracking.value = on
        if (on) return
        _fix.value = null
        if (reason != StopReason.USER && _wanted.value) _stopNotice.value = reason
    }
}
