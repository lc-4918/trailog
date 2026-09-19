package fr.lc4918.trailog.poi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Le chargeur des points d'interet : ce qu'on a, ce qu'on attend, et ce qui a echoue - cellule par
 * cellule (cf. [PoiCells]).
 *
 * **Il vit plus longtemps qu'un geste de carte, et c'est ce qui change tout.** Le chargement d'avant etait
 * un effet de l'ecran : le geste suivant l'annulait, requetes en vol comprises, et la vue d'apres
 * redemandait ce qu'on venait presque d'obtenir. Sur une ville dense, ou une requete mettait vingt
 * secondes, les restaurants n'arrivaient jamais pour qui bougeait la carte. Ici, un geste ne fait que
 * dire ce qu'on VEUT ([want]) : ce qui est en vol continue et se garde, ce qui attend son tour se reordonne,
 * ce qui ne sert plus est abandonne avant d'etre parti.
 *
 * Les dependances arrivent en fonctions - la lecture du cache et la requete - pour que l'ordonnancement se
 * teste sans reseau ni base (cf. `PoiLoaderTest`).
 *
 * @param now horloge monotone, pour le repos d'une unite en echec (cf. [retryAfterFailMs]).
 */
class PoiLoader(
    private val scope: CoroutineScope,
    private val lookup: suspend (List<PoiUnit>) -> PoiLookup,
    private val fetch: suspend (PoiJob) -> PoiFetch,
    private val now: () -> Long,
    private val parallelPerSource: Int = PoiRepository.PARALLEL_PER_SOURCE,
    /**
     * Delai avant de redemander une unite dont la requete a ECHOUE : le service n'aura pas change d'avis
     * en trois secondes, et insister est ce qui le fait refuser plus durement. Une minute.
     */
    private val retryAfterFailMs: Long = 60_000L,
    /** Au-dela de ce nombre d'unites en memoire, on oublie celles qu'on ne regarde plus. */
    private val keepUnits: Int = 400,
) {
    /**
     * Ce qui est connu a cet instant, pour les unites voulues.
     *
     * [pois] ne porte que les unites de la derniere demande : ce qu'on a en memoire d'une vue quittee n'a
     * rien a faire sur la carte, et y reviendra sans requete si l'on y revient.
     */
    data class Snapshot(
        val pois: List<Poi> = emptyList(),
        /** Des unites voulues attendent encore le service. */
        val pending: Boolean = false,
        /** Une unite voulue n'a pas pu etre chargee, et l'on montre a sa place ce que le cache en gardait. */
        val fromCache: Boolean = false,
        /** Une unite voulue n'a pas pu etre chargee, et le cache n'en savait rien. */
        val missing: Boolean = false,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    private val verrou = Mutex()
    private val creneaux = PoiSource.entries.associateWith { Semaphore(parallelPerSource) }

    /** Chargees, et fraiches. */
    private val connues = LinkedHashMap<PoiUnit, List<Poi>>()
    /** Ce que le cache gardait d'unites perimees ou jamais marquees : montre en attendant mieux. */
    private val perimees = HashMap<PoiUnit, List<Poi>>()
    private val echecs = HashMap<PoiUnit, Long>()
    /** Attendent leur tour. Une unite qui en sort sans etre partie a ete abandonnee. */
    private val enAttente = HashSet<PoiUnit>()
    private val enVol = HashSet<PoiUnit>()
    private var voulues: List<PoiUnit> = emptyList()
    private var demande: Job? = null

    /**
     * Les unites a montrer, dans l'ordre ou les charger : la plus proche du centre de l'ecran d'abord.
     *
     * Remplace la demande precedente. Ce qui est en vol continue - sa reponse sera gardee, et servira des
     * qu'on reviendra la. Ce qui attendait et n'est plus voulu est abandonne.
     */
    fun want(units: List<PoiUnit>) {
        demande?.cancel()
        demande = scope.launch {
            val aLire = verrou.withLock {
                voulues = units
                val voulu = units.toHashSet()
                enAttente.retainAll(voulu)
                oublier(voulu)
                publier()
                units.filter { it !in connues && it !in enVol && it !in enAttente }
            }
            if (aLire.isEmpty()) return@launch
            val lu = runCatching { lookup(aLire) }.getOrDefault(PoiLookup(emptyMap(), aLire.associateWith { emptyList() }))
            // Mise en file ET lancement sous le meme verrou, sans suspension entre les deux : une
            // annulation tombee entre deux laisserait des unites "en attente" que rien ne ferait partir,
            // et qu'aucune demande suivante ne relancerait.
            verrou.withLock {
                connues.putAll(lu.fresh)
                lu.fresh.keys.forEach { perimees.remove(it); echecs.remove(it) }
                perimees.putAll(lu.stale)
                val t = now()
                val reste = aLire.filter { u ->
                    u !in connues && u !in enVol && u !in enAttente &&
                        echecs[u]?.let { t - it >= retryAfterFailMs } != false
                }
                enAttente.addAll(reste)
                publier()
                PoiCells.jobs(reste).forEach { job -> scope.launch { lancer(job) } }
            }
        }
    }

    /**
     * Le reseau est revenu, ou l'instance a change : les unites en echec n'attendent plus leur minute.
     * Rien d'autre n'est oublie - ce qui est charge l'est bel et bien.
     */
    fun retryFailed() {
        scope.launch {
            val relancer = verrou.withLock {
                if (echecs.isEmpty()) return@withLock false
                echecs.clear()
                true
            }
            if (relancer) want(voulues)
        }
    }

    /** Oublie tout : le cache vient d'etre vide, ou la couche eteinte. */
    fun reset() {
        demande?.cancel()
        scope.launch {
            verrou.withLock {
                voulues = emptyList()
                connues.clear(); perimees.clear(); echecs.clear(); enAttente.clear()
                publier()
            }
        }
    }

    private suspend fun lancer(job: PoiJob) {
        creneaux.getValue(job.source).withPermit {
            // Au moment de partir, et non a celui d'etre mise en file : entre les deux, un geste a pu
            // l'abandonner, ou une autre file la prendre deja.
            val parties = verrou.withLock {
                val miennes = job.groups.map { PoiUnit(job.cell, it, job.source) }.filter { it in enAttente }
                enAttente.removeAll(miennes.toSet())
                enVol.addAll(miennes)
                miennes
            }
            if (parties.isEmpty()) return
            val r = runCatching { fetch(job.copy(groups = parties.mapTo(LinkedHashSet()) { it.group })) }
                .getOrDefault(PoiFetch(emptyMap(), failed = true))
            verrou.withLock {
                enVol.removeAll(parties.toSet())
                val t = now()
                for (u in parties) {
                    if (r.failed) echecs[u] = t
                    else {
                        connues[u] = r.pois[u.group].orEmpty()
                        perimees.remove(u)
                        echecs.remove(u)
                    }
                }
                publier()
            }
        }
    }

    /** Oublie les unites les plus anciennes qu'on ne regarde plus, au-dela de [keepUnits]. */
    private fun oublier(voulu: Set<PoiUnit>) {
        if (connues.size <= keepUnits) return
        val trop = connues.size - keepUnits
        connues.keys.filter { it !in voulu }.take(trop).forEach { connues.remove(it) }
        perimees.keys.retainAll(voulu)
    }

    /** Sous verrou. */
    private fun publier() {
        val lieux = ArrayList<Poi>()
        var cache = false
        var manque = false
        for (u in voulues) {
            val c = connues[u]
            if (c != null) { lieux += c; continue }
            val p = perimees[u].orEmpty()
            lieux += p
            if (u in echecs) { if (p.isNotEmpty()) cache = true else manque = true }
        }
        _state.value = Snapshot(
            pois = lieux.distinctBy { it.uuid },
            pending = voulues.any { it in enAttente || it in enVol },
            fromCache = cache,
            missing = manque,
        )
    }
}
