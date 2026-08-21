package fr.lc4918.trailog.location

import android.location.Location
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
    )

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
        )
    }

    /**
     * Debut ou fin du suivi. L'arret oublie la derniere position : la garder ferait reapparaitre le repere
     * a l'endroit d'hier au prochain allumage, avant meme que le capteur n'ait repondu.
     */
    fun setTracking(on: Boolean) {
        _tracking.value = on
        if (!on) _fix.value = null
    }
}
