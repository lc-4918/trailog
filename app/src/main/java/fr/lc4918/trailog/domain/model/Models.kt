package fr.lc4918.trailog.domain.model

/** Un point de trace : lon/lat WGS84, altitude (m) et temps (ms epoch) optionnels. */
data class TrackPoint(
    val lon: Double,
    val lat: Double,
    val ele: Double? = null,
    val timeMs: Long? = null,
)

/** Échantillon prêt à dessiner (profil). x = distance cumulée (m), z = altitude (m),
 *  slope = pente (%), t = temps écoulé (s, en mouvement si ignoreStops). */
data class Sample(
    val x: Double,
    val z: Double,
    val slope: Double,
    val t: Double?,
    val lon: Double,
    val lat: Double,
)

/** Statistiques d'un tracé. duration en secondes (null si pas de temps). */
data class TrackStats(
    val distance: Double,
    val ascent: Double,
    val descent: Double,
    val min: Double,
    val max: Double,
    val maxAbsSlope: Double,
    val duration: Double?,
    val points: Int,
)

data class ComputedTrack(
    val samples: List<Sample>,
    val stats: TrackStats,
    val hasZ: Boolean,
    val hasTime: Boolean,
)
