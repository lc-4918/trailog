package fr.lc4918.trailog.location

import android.content.Context
import fr.lc4918.trailog.domain.geo.Trip
import fr.lc4918.trailog.domain.geo.TripStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Les compteurs de la sortie, tenus a jour par le service de localisation - ecran eteint compris - et lus
 * par le tableau de bord.
 *
 * Hors de la composition, comme la trace suivie (cf. [TrackWatch]) : une sortie se passe telephone en poche,
 * et des compteurs qui ne tourneraient que sous les yeux ne compteraient presque rien.
 */
object TripWatch {

    private val _trip = MutableStateFlow(Trip())
    val trip: StateFlow<Trip> = _trip.asStateFlow()

    /** Les compteurs ont deja servi dans ce processus : ce qui est sur le disque est plus vieux qu'eux. */
    @Volatile private var touched = false

    /** Une position de plus (cf. [TripStats.add]). Rend vrai quand les compteurs ont change. */
    fun add(fix: LocationHub.Fix): Boolean {
        touched = true
        val avant = _trip.value
        // L'instant de la MESURE, et non celui de sa reception : le systeme peut livrer d'un coup, au
        // reveil, des positions mesurees a plusieurs minutes d'intervalle. Datees de leur livraison,
        // elles passeraient sous le trou de TripStats.MAX_GAP_MS et seraient reliees en ligne droite -
        // des kilometres de chemin sinueux ramenes a leur corde.
        val apres = TripStats.add(avant, fix.lat, fix.lon, fix.accuracyM, fix.altitudeM, fix.elapsedAtMs)
        _trip.value = apres
        return apres.distanceM != avant.distanceM || apres.ascentM != avant.ascentM ||
            apres.descentM != avant.descentM || apres.movingMs != avant.movingMs
    }

    /** Le bouton du tableau de bord : tout repart de zero, l'ancre comprise. */
    fun reset() {
        touched = true
        _trip.value = Trip()
    }

    /**
     * Reprise du disque. Ne s'impose pas a des compteurs deja en marche - ni a une remise a zero : des
     * compteurs a zero ne disent pas qu'ils n'ont jamais servi, et la sortie d'hier ne doit pas revenir.
     */
    fun restore(t: Trip?) {
        if (t == null || touched) return
        touched = true
        _trip.value = t
    }
}

/**
 * Les compteurs gardes sur le disque : ils continuent d'une session a l'autre, jusqu'a ce qu'on les remette
 * a zero, et survivent donc a la mort du processus - comme la trace suivie (cf. [FollowedStore]).
 */
object TripStore {
    private const val FILE_NAME = "trip.json"
    private val json = Json { ignoreUnknownKeys = true }

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    suspend fun save(ctx: Context, t: Trip) = withContext(Dispatchers.IO) { write(ctx, t) }

    /** L'ecriture sur place, pour l'arret du service : quelques centaines d'octets, et plus de coroutine. */
    fun write(ctx: Context, t: Trip) {
        runCatching { file(ctx).writeText(json.encodeToString(Trip.serializer(), t)) }
    }

    suspend fun load(ctx: Context): Trip? = withContext(Dispatchers.IO) {
        val f = file(ctx)
        if (!f.exists()) return@withContext null
        runCatching { json.decodeFromString(Trip.serializer(), f.readText()) }.getOrNull()
    }
}
