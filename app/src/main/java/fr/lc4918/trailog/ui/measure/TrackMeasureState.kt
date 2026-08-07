package fr.lc4918.trailog.ui.measure

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * Un bout de mesure pose sur une trace : ou il tombe, sur quelle trace, et a quel kilometrage.
 *
 * [trackIndex] designe le segment de la couche (une couche peut en contenir plusieurs, sans continuite
 * entre eux) : le second point doit tomber sur CE segment, sinon il n'y a pas de parcours a mesurer.
 */
data class MeasurePoint(
    val layerId: Long,
    val layerName: String,
    val trackIndex: Int,
    val lon: Double,
    val lat: Double,
    /** Kilometrage du point depuis le debut du segment (m) : la mesure est la difference de deux d'entre eux. */
    val alongM: Double,
)

/**
 * Etat de la mesure sur trace : la bande de consigne, les deux points poses, et le resultat.
 *
 * La bande ([picking]) et le resultat ne coexistent jamais : elle ne sert qu'a demander les points, et
 * s'efface des que le second est pose. Le resultat, lui, survit a la bande jusqu'a la croix de son
 * infobulle - c'est le seul etat qui reste a l'ecran une fois la fonction accomplie.
 */
@Stable
class TrackMeasureState {

    /** Bande de consigne affichee : un tap sur la carte pose un point de mesure, et rien d'autre. */
    var picking by mutableStateOf(false)
        private set
    var start by mutableStateOf<MeasurePoint?>(null)
        private set
    var end by mutableStateOf<MeasurePoint?>(null)
        private set

    /** Milieu du parcours mesure : l'infobulle y pointe sa pointe. */
    var mid by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    /** Marqueurs noirs a poser sur la carte : le point de depart, puis celui de fin. */
    val markers: List<Pair<Double, Double>>
        get() = listOfNotNull(start?.let { it.lon to it.lat }, end?.let { it.lon to it.lat })

    /** Longueur mesuree le long de la trace (m), une fois les deux points poses. */
    val meters: Double?
        get() {
            val a = start ?: return null
            val b = end ?: return null
            return abs(b.alongM - a.alongM)
        }

    /** Tap sur le bouton de mesure : on repart d'une mesure vierge, la precedente ayant eu son temps. */
    fun open() {
        clear()
        picking = true
    }

    fun chooseStart(p: MeasurePoint) { start = p }

    /** Second point pose : la mesure est faite, la bande n'a plus rien a demander. */
    fun chooseEnd(p: MeasurePoint, middle: Pair<Double, Double>?) {
        end = p
        mid = middle
        picking = false
    }

    /**
     * Croix de la bande : referme la consigne, et efface le point de depart s'il etait seul.
     *
     * Un point unique ne mesure rien ; le laisser sur la carte laisserait un marqueur noir orphelin, que
     * plus rien n'expliquerait ni ne permettrait de retirer.
     */
    fun closeBand() {
        picking = false
        if (end == null) { start = null; mid = null }
    }

    /** Croix de l'infobulle, ou abandon complet : ne laisse ni bande, ni marqueur, ni resultat. */
    fun clear() {
        picking = false
        start = null
        end = null
        mid = null
    }
}
