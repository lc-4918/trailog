package fr.lc4918.trailog.ui.geocode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.geocode.GeocodePlace

/** Laquelle des deux mesures : depuis la position du porteur, ou depuis un point posé à la main. */
enum class MeasureKind { POSITION, POINT }

/** Où en est une mesure de distance. Null = jamais demandée : le bouton est alors seul, sans rien à droite. */
sealed interface MeasureState {
    /** Requête en cours, ou position GPS pas encore reçue. */
    data object Loading : MeasureState
    /** Aucun itinéraire : points non reliés dans cette discipline, service muet, ou réseau absent. */
    data object Failed : MeasureState
    /**
     * [track] : l'itinéraire mesuré, prêt à dessiner - points, altitudes et pentes. Null si le moteur n'a
     * rendu aucune géométrie exploitable ; `hasZ` faux s'il n'a pas rendu d'altitudes, auquel cas la ligne
     * reste traçable mais le profil est refusé (il serait plat et muet).
     */
    data class Done(
        val meters: Double,
        val seconds: Double,
        val track: ComputedTrack? = null,
    ) : MeasureState
}

/**
 * État de la recherche de lieu, de sa barre de saisie jusqu'aux mesures de distance.
 *
 * Regroupé ici plutôt qu'en variables éparses dans l'écran de carte : les transitions comptent plus que
 * les valeurs (choisir un point de référence masque l'infobulle puis la rend, fermer la recherche doit
 * tout défaire), et elles sont fautives à écrire deux fois.
 */
@Stable
class GeocodeSearchState {
    /** Barre de saisie ouverte (le bouton loupe la bascule). */
    var searchOpen by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    var results by mutableStateOf<List<GeocodePlace>>(emptyList())
    var searching by mutableStateOf(false)

    /** Lieu choisi : marqueur noir sur la carte, et sujet de l'infobulle. */
    var place by mutableStateOf<GeocodePlace?>(null)
        private set

    /** En attente d'un tap sur la carte pour poser le point de référence (barre noire affichée). */
    var pickingPoint by mutableStateOf(false)
        private set

    /** Point de référence posé à la main, en (lon, lat). */
    var refPoint by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    var positionMeasure by mutableStateOf<MeasureState?>(null)
        private set
    var pointMeasure by mutableStateOf<MeasureState?>(null)
        private set

    /**
     * Origine figée du calcul depuis la position, en (lat, lon).
     *
     * Figée, et non relue à chaque position reçue : un itinéraire est une requête réseau, là où le vol
     * d'oiseau ne coûtait rien. Suivre le capteur (une position toutes les 2 s) en lancerait une par point,
     * ce qu'aucun service public ne tolérerait. La mesure vaut donc pour l'endroit d'où elle a été demandée.
     */
    var positionOrigin by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    /**
     * Profil ouvert, et sur laquelle des deux mesures. Null = aucun panneau de profil affiché.
     *
     * Ici et non dans le modèle de vue, où vivent les profils des traces importées : celui-ci ne décrit
     * rien d'enregistré, il ne survit pas à la mesure qui l'a produit, et il disparaît avec elle.
     */
    var profileOf by mutableStateOf<MeasureKind?>(null)
        private set

    /** Index du curseur dans les points du profil ouvert (repère sur la carte + infos du point). */
    var profileCursor by mutableStateOf<Int?>(null)
        private set

    /**
     * Numéro d'ordre des mesures, incrémenté chaque fois que l'une d'elles change.
     *
     * Sert de clé de relance aux effets qui posent les itinéraires sur la carte. Les prendre eux-mêmes pour
     * clé serait correct mais coûteux : un itinéraire porte plusieurs milliers de points, et Compose
     * compare ses clés à chaque recomposition - soit, pendant un balayage du curseur du profil, une
     * comparaison point par point soixante fois par seconde.
     */
    var measureRevision by mutableStateOf(0)
        private set

    /** Traces des itineraires mesures, a poser sur la carte. Vide tant qu'aucune mesure n'a abouti. */
    val routeTracks: List<ComputedTrack>
        get() = listOfNotNull(positionMeasure, pointMeasure)
            .filterIsInstance<MeasureState.Done>()
            .mapNotNull { it.track }
            .filter { it.samples.size >= 2 }

    /** L'itinéraire d'une mesure, s'il a abouti. */
    fun trackOf(kind: MeasureKind): ComputedTrack? = (measure(kind) as? MeasureState.Done)?.track

    private fun measure(kind: MeasureKind): MeasureState? =
        if (kind == MeasureKind.POSITION) positionMeasure else pointMeasure

    /** L'infobulle s'efface le temps de choisir un point sur la carte, qu'elle recouvrirait. */
    val bubbleVisible: Boolean get() = place != null && !pickingPoint

    fun openSearch() { searchOpen = true }

    /** Referme la seule barre de saisie : le lieu déjà choisi et ses mesures restent affichés. */
    fun closeSearch() {
        searchOpen = false
        query = ""
        results = emptyList()
        searching = false
    }

    fun select(p: GeocodePlace) {
        place = p
        closeSearch()
        // Un nouveau lieu rend caduques les mesures du précédent : les garder afficherait, à côté des
        // boutons, des distances calculées vers un autre endroit.
        clearMeasures()
        pickingPoint = false
    }

    /** Demande la mesure depuis la position : l'origine sera figée dès la première position connue. */
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
        measureRevision++
        forgetCursorOf(MeasureKind.POSITION)
    }

    fun startPickingPoint() { pickingPoint = true }

    fun cancelPickingPoint() { pickingPoint = false }

    fun setRefPoint(lon: Double, lat: Double) {
        refPoint = lon to lat
        pointMeasure = MeasureState.Loading
        pickingPoint = false
    }

    fun publishPointMeasure(m: MeasureState) {
        pointMeasure = m
        measureRevision++
        forgetCursorOf(MeasureKind.POINT)
    }

    /** Le profil reste ouvert sur sa mesure, mais son curseur retombe : il désignait un point d'un autre
     *  itinéraire, et son index ne veut plus rien dire dans le nouveau. */
    private fun forgetCursorOf(kind: MeasureKind) {
        if (profileOf == kind) profileCursor = null
    }

    fun openProfile(kind: MeasureKind) {
        profileOf = kind
        profileCursor = null
    }

    fun closeProfile() {
        profileOf = null
        profileCursor = null
    }

    /** Nommée ainsi et non `setProfileCursor` : ce nom-là est déjà celui du mutateur que Kotlin engendre
     *  pour la propriété, et les deux se heurteraient sur la JVM. */
    fun moveProfileCursor(index: Int?) { profileCursor = index }

    /** Ferme tout : la recherche, le lieu, ses mesures. */
    fun clear() {
        closeSearch()
        place = null
        pickingPoint = false
        clearMeasures()
    }

    private fun clearMeasures() {
        refPoint = null
        positionMeasure = null
        pointMeasure = null
        positionOrigin = null
        measureRevision++
        closeProfile()
    }
}
