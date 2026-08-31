package fr.lc4918.trailog.ui.mappoint

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.domain.model.ComputedTrack

/** Ou en est une mesure de distance. Null = jamais demandee : la ligne est alors seule, sans rien dessous. */
sealed interface MeasureState {
    /** Requete en cours, ou position GPS pas encore recue. */
    data object Loading : MeasureState

    /** Aucun itineraire : points non relies dans cette discipline, service muet, ou reseau absent. */
    data object Failed : MeasureState

    /** [track] : l'itineraire mesure, pret a dessiner. Null si le moteur n'a rendu aucune geometrie
     *  exploitable - la mesure reste alors chiffree, mais rien ne se pose sur la carte. */
    data class Done(
        val meters: Double,
        val seconds: Double,
        val track: ComputedTrack? = null,
    ) : MeasureState
}

/**
 * Les deux distances qu'on peut demander depuis un endroit de la carte : d'ou l'on est, et d'un autre
 * point qu'on designe.
 *
 * **Un porteur a part, et non des champs de plus dans l'etat qui l'utilise.** Ces mesures ont longtemps
 * vecu dans [MapPointState], soudees au point d'un appui long - qui etait alors le seul endroit d'ou on
 * pouvait les demander. Elles valent pourtant pour n'importe quel endroit designe : un point d'interet
 * qu'on vient d'ouvrir pose exactement la meme question - a quelle distance est-il, et par ou.
 *
 * Chaque bulle a le SIEN : le point d'appui long et le point d'interet peuvent etre a l'ecran en meme
 * temps, et un porteur partage afficherait sous l'un la distance mesuree pour l'autre.
 *
 * Le point mesure est [target], que le proprietaire tient a jour : le changer efface tout, les chiffres
 * d'avant valant pour un autre endroit.
 */
@Stable
class PointMeasures {

    /** L'endroit mesure, en (lon, lat). Null = rien a mesurer, et rien a afficher. */
    var target by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    /** En attente d'un tap sur la carte pour poser le point de reference (barre de consigne affichee). */
    var pickingPoint by mutableStateOf(false)
        private set

    /** Point de reference pose a la main, en (lon, lat). */
    var refPoint by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    var positionMeasure by mutableStateOf<MeasureState?>(null)
        private set
    var pointMeasure by mutableStateOf<MeasureState?>(null)
        private set

    /**
     * Origine figee du calcul depuis la position, en (lat, lon).
     *
     * Figee, et non relue a chaque position recue : un itineraire est une requete reseau, la ou le vol
     * d'oiseau ne coutait rien. Suivre le capteur (une position toutes les 2 s) en lancerait une par point,
     * ce qu'aucun service public ne tolererait. La mesure vaut donc pour l'endroit d'ou elle a ete demandee.
     */
    var positionOrigin by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    /**
     * Numero d'ordre des mesures, incremente chaque fois que l'une d'elles change.
     *
     * Sert de cle de relance a l'effet qui pose les itineraires sur la carte. Les prendre eux-memes pour
     * cle serait correct mais couteux : un itineraire porte plusieurs milliers de points, et Compose
     * compare ses cles a chaque recomposition.
     */
    var revision by mutableStateOf(0)
        private set

    /** L'epingle noire du point de reference, quand il y en a un. Le point MESURE, lui, appartient a ce
     *  qui l'a designe - une epingle d'appui long, un marqueur de point d'interet - et se pose ailleurs. */
    val markers: List<Pair<Double, Double>> get() = listOfNotNull(refPoint)

    /** Traces des itineraires mesures, a poser sur la carte. Vide tant qu'aucune mesure n'a abouti. */
    val routeTracks: List<ComputedTrack>
        get() = listOfNotNull(positionMeasure, pointMeasure)
            .filterIsInstance<MeasureState.Done>()
            .mapNotNull { it.track }
            .filter { it.samples.size >= 2 }

    /** Une mesure est en cours de choix : ce qui l'a ouverte doit s'effacer, la carte etant a designer. */
    val busy: Boolean get() = pickingPoint

    /**
     * L'endroit a mesurer change - un autre point designe, un autre lieu ouvert.
     *
     * Tout retombe : les distances valaient pour un autre endroit, et les garder afficherait, sous un
     * nouveau nom, des chiffres sans rapport avec lui. Sans effet quand la cible ne bouge pas : l'appelant
     * la repose a chaque recomposition, et remettre a zero la mesure qu'on vient de demander l'effacerait
     * avant qu'elle n'arrive.
     */
    fun retarget(point: Pair<Double, Double>?) {
        if (point == target) return
        target = point
        pickingPoint = false
        clear()
    }

    /** Demande la mesure depuis la position : l'origine sera figee des la premiere position connue. */
    fun requestDistanceFromPosition() {
        positionMeasure = MeasureState.Loading
        positionOrigin = null
    }

    /** Fige l'origine, une seule fois par demande (cf. [positionOrigin]). */
    fun fixPositionOrigin(lat: Double, lon: Double) {
        if (positionMeasure == MeasureState.Loading && positionOrigin == null) positionOrigin = lat to lon
    }

    fun publishPositionMeasure(m: MeasureState) {
        positionMeasure = m
        revision++
    }

    fun startPickingPoint() { pickingPoint = true }

    fun cancelPickingPoint() { pickingPoint = false }

    /** Tap sur la carte pendant le choix : le point de reference est pose, et la mesure part de la. */
    fun chooseRefPoint(lon: Double, lat: Double) {
        refPoint = lon to lat
        pointMeasure = MeasureState.Loading
        pickingPoint = false
    }

    fun publishPointMeasure(m: MeasureState) {
        pointMeasure = m
        revision++
    }

    /** Ne laisse ni point de reference, ni trace, ni chiffre. */
    fun clear() {
        refPoint = null
        positionMeasure = null
        pointMeasure = null
        positionOrigin = null
        revision++
    }
}
