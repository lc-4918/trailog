package fr.lc4918.trailog.routing.offline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * Quand annoncer qu'une zone telechargee a une mise a jour sur le serveur.
 *
 * Sans annonce, une zone perimee ne se voyait qu'en depliant sa ligne dans les reglages : personne n'y va
 * pour rien, et un index de lieux regenere (l'Europe apres la France) restait ignore. L'application
 * consulte donc le serveur au lancement, au plus une fois par jour - il change une fois par semaine -, et
 * notifie une seule fois chaque nouvelle version : une mise a jour qu'on laisse attendre ne revient pas a
 * chaque lancement, une version plus recente du serveur, si.
 */
object ZoneUpdateNotice {
    /** Le serveur ne se consulte pas plus d'une fois par jour pour cette annonce. */
    const val CHECK_INTERVAL_MS = 24L * 3600 * 1000

    /** Faut-il consulter le serveur ? Une horloge revenue en arriere ne bloque pas la verification. */
    fun checkDue(lastCheckMs: Long, nowMs: Long): Boolean =
        nowMs < lastCheckMs || nowMs - lastCheckMs >= CHECK_INTERVAL_MS

    /** Les zones telechargees dont un carre a une version plus recente sur le serveur, dans l'ordre de [zones]. */
    fun outdatedZones(state: BrouterDownloads.State, zones: List<BrouterZone> = BrouterZones.all): List<BrouterZone> =
        zones.filter { it.id in state.zones && state.zoneOutdated(it) }

    /**
     * La version du serveur qui rend [zones] perimees : la date la plus recente, donnees d'itineraire et
     * index de lieux confondus, de leurs carres perimes. C'est elle qu'on retient une fois annoncee.
     */
    fun serverVersion(state: BrouterDownloads.State, zones: List<BrouterZone>): Long {
        val remote = state.remote ?: return 0L
        val tiles = zones.flatMapTo(HashSet()) { it.tiles }
        return state.installed
            .filter { it.tile in tiles && state.outdated(it) }
            .maxOfOrNull { local ->
                val published = remote[local.tile]
                maxOf(published?.modifiedMs ?: 0L, published?.geocode?.modifiedMs ?: 0L)
            } ?: 0L
    }

    /** Une version deja annoncee ne l'est pas deux fois. */
    fun shouldNotify(version: Long, lastNotifiedVersion: Long): Boolean = version > lastNotifiedVersion

    private val openRequest = MutableStateFlow(false)

    /**
     * La notification a ete touchee et l'ecran principal n'a pas encore ouvert les zones d'itineraire. Une
     * demande en attente, consommee par [consumeOpenZones] : ni une rotation ni un retour a l'application
     * ne la rejouent.
     */
    val openZonesPending: StateFlow<Boolean> = openRequest

    fun requestOpenZones() = openRequest.update { true }

    /** Vrai une seule fois par demande. */
    fun consumeOpenZones(): Boolean = openRequest.getAndUpdate { false }
}
