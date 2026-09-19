package fr.lc4918.trailog.routing.offline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * La file des telechargements de donnees BRouter, et ce qu'on en sait a chaque instant.
 *
 * **Elle vit avec l'application, et non avec un ecran.** Un carre des Alpes pese deux cent cinquante
 * megaoctets : il ne doit pas s'arreter parce qu'on a quitte les reglages, ni partir deux fois parce que
 * deux ecrans l'ont demande. Les reglages et le telechargement d'une zone hors ligne passent donc par la
 * meme file, un carre a la fois - le serveur public est un bien commun, et deux transferts paralleles
 * n'iraient pas plus vite sur le meme lien.
 */
class BrouterDownloads(
    private val segments: BrouterSegments,
    private val scope: CoroutineScope,
    /**
     * Appele quand une zone entre en telechargement : c'est la que l'application lance le service de premier
     * plan qui la laisse continuer ecran eteint, ou application fermee (cf. `BrouterDownloadService`).
     */
    private val onBusy: () -> Unit = {},
) {

    /** Une zone arrivee au bout de son telechargement, reussi ou non : ce que la carte annonce. */
    data class Finished(val zoneId: String, val ok: Boolean)

    /** Le transfert en cours : le carre, les octets deja la, le total attendu. */
    data class Progress(val tile: BrouterTile, val bytes: Long, val total: Long)

    data class State(
        val installed: List<BrouterSegments.Installed> = emptyList(),
        /** L'index du serveur, ou null tant qu'on ne l'a pas lu - ou s'il n'a pas repondu. */
        val remote: Map<BrouterTile, BrouterSegments.Remote>? = null,
        /** L'index a ete demande et le serveur n'a pas repondu. */
        val remoteFailed: Boolean = false,
        val current: Progress? = null,
        /** En attente derriere [current], dans l'ordre. */
        val queued: List<BrouterTile> = emptyList(),
        /** Les carres dont le dernier transfert a echoue : on le dit, et l'on peut relancer. */
        val failed: Set<BrouterTile> = emptySet(),
        /** Les zones telechargees (cf. [BrouterZones]), par identifiant. */
        val zones: Set<String> = emptySet(),
        /** Les zones en cours de telechargement, et les carres qu'il leur fallait. */
        val pending: Map<String, Set<BrouterTile>> = emptyMap(),
        /** Les zones dont le dernier telechargement n'a pas abouti. */
        val zoneFailed: Set<String> = emptySet(),
        /** Les zones terminees depuis la derniere fois qu'on l'a annonce (cf. [acknowledge]). */
        val finished: List<Finished> = emptyList(),
    ) {
        /**
         * La progression de TOUT ce qui est en cours, toutes zones confondues : octets recus et attendus.
         * Null quand rien n'est en cours. Un carre commun a deux zones ne compte qu'une fois.
         */
        fun overall(): Pair<Long, Long?>? {
            if (pending.isEmpty()) return null
            val besoin = pending.values.flatMapTo(HashSet()) { it }
            val faits = installed.filter { it.tile in besoin && !busy(it.tile) }.sumOf { it.bytes }
            val enCours = current?.takeIf { it.tile in besoin }?.bytes ?: 0L
            val attendu = remote?.let { r -> besoin.sumOf { r[it]?.bytes ?: return@let null } }
            return (faits + enCours) to attendu
        }

        val totalBytes: Long get() = installed.sumOf { it.bytes }
        fun busy(tile: BrouterTile): Boolean = current?.tile == tile || tile in queued
        fun outdated(i: BrouterSegments.Installed): Boolean = BrouterSegments.outdated(i, remote?.get(i.tile))

        /** Le poids d'une zone : ses carres presents, et pour les autres ce que le serveur annonce. Null
         *  tant qu'un carre manquant n'a pas de taille connue. */
        fun weight(zone: BrouterZone): Long? {
            val presents = installed.associateBy { it.tile }
            var total = 0L
            for (t in zone.tiles) total += presents[t]?.bytes ?: remote?.get(t)?.bytes ?: return null
            return total
        }

        /**
         * Les carres d'une zone qu'il faut encore telecharger : absents, ou plus anciens que ceux du serveur.
         * Un carre deja la pour une autre zone ne se telecharge pas deux fois.
         */
        fun missing(zone: BrouterZone): Set<BrouterTile> {
            val presents = installed.associateBy { it.tile }
            return zone.tiles.filterTo(LinkedHashSet()) { t -> presents[t]?.let { outdated(it) } ?: true }
        }

        /** Ce qu'il reste a telecharger pour une zone, en octets. Null tant qu'un carre manquant n'a pas de
         *  taille connue. C'est ce que la barre de progression comptera. */
        fun remaining(zone: BrouterZone): Long? {
            var total = 0L
            for (t in missing(zone)) total += remote?.get(t)?.bytes ?: return null
            return total
        }

        /** La date la plus ancienne des donnees d'une zone telechargee. */
        fun dataDate(zone: BrouterZone): Long? =
            installed.filter { it.tile in zone.tiles }.minOfOrNull { it.modifiedMs }

        /** Un carre de la zone a une version plus recente sur le serveur. */
        fun zoneOutdated(zone: BrouterZone): Boolean = installed.any { it.tile in zone.tiles && outdated(it) }

        /** Octets recus et attendus pour une zone en cours de telechargement. */
        fun zoneProgress(zone: BrouterZone): Pair<Long, Long?>? {
            val besoin = pending[zone.id] ?: return null
            val faits = installed.filter { it.tile in besoin && !busy(it.tile) }.sumOf { it.bytes }
            val enCours = current?.takeIf { it.tile in besoin }?.bytes ?: 0L
            val attendu = remote?.let { r -> besoin.sumOf { r[it]?.bytes ?: return@let null } }
            return (faits + enCours) to attendu
        }
    }

    private val _state = MutableStateFlow(State(installed = segments.installed(), zones = lireZones()))
    val state: StateFlow<State> = _state

    /** Reveille le travailleur : quelque chose vient d'entrer dans la file. */
    private val reveil = Channel<Unit>(Channel.CONFLATED)
    /** Le transfert en cours, pour pouvoir l'arreter sans arreter le travailleur. */
    @Volatile private var transfert: Deferred<Boolean>? = null
    private var index: Job? = null

    init {
        // Un seul travailleur, pour toute la vie de l'application : la file se vide dans l'ordre, un carre a
        // la fois, et un ajout arrive pendant qu'il travaille est pris au tour suivant.
        scope.launch {
            for (signal in reveil) {
                while (true) {
                    val tile = _state.value.queued.firstOrNull() ?: break
                    _state.update { it.copy(queued = it.queued.drop(1), current = Progress(tile, 0, 0)) }
                    val d = scope.async {
                        segments.download(tile) { recu, total ->
                            _state.update { it.copy(current = Progress(tile, recu, total)) }
                        }
                    }
                    transfert = d
                    val ok: Boolean? = try {
                        d.await()
                    } catch (e: CancellationException) {
                        // Le TRANSFERT a ete arrete (cf. cancel), pas le travailleur : ce n'est pas un echec,
                        // et le debut recu reste pour une reprise.
                        if (!d.isCancelled) throw e
                        null
                    } catch (e: Exception) {
                        false
                    }
                    transfert = null
                    _state.update {
                        it.copy(
                            current = null,
                            installed = segments.installed(),
                            failed = when (ok) {
                                true -> it.failed - tile
                                false -> it.failed + tile
                                null -> it.failed
                            },
                        )
                    }
                }
            }
        }
    }

    /** Relit ce qui est sur le telephone - apres une suppression, ou un depot a la main. */
    fun refreshInstalled() = _state.update { it.copy(installed = segments.installed()) }

    /** Lit l'index du serveur, une fois : les carres ne changent qu'une fois par semaine. */
    fun loadRemote(force: Boolean = false) {
        if (!force && _state.value.remote != null) return
        if (index?.isActive == true) return
        index = scope.launch {
            val r = segments.remoteIndex()
            _state.update { it.copy(remote = r ?: it.remote, remoteFailed = r == null) }
        }
    }

    /** Met ces carres en file, sauf ceux qui y sont deja. Un carre deja present est re-telecharge : c'est
     *  ainsi qu'on le met a jour. */
    fun enqueue(tiles: Collection<BrouterTile>) {
        _state.update { s ->
            val neufs = tiles.filterNot { s.busy(it) }
            s.copy(queued = s.queued + neufs, failed = s.failed - neufs.toSet())
        }
        reveil.trySend(Unit)
    }

    /**
     * Retire un carre : de la file s'il y attend, du telephone s'il y est. Le transfert EN COURS n'est pas
     * interrompu par la - [cancel] le fait.
     */
    fun delete(tile: BrouterTile) {
        _state.update { it.copy(queued = it.queued - tile, failed = it.failed - tile) }
        if (_state.value.current?.tile != tile) segments.delete(tile)
        refreshInstalled()
    }

    /**
     * Telecharge une zone : ses carres absents, et ceux dont le serveur a une version plus recente. La
     * zone n'entre dans la liste des zones telechargees qu'une fois TOUS ses carres presents.
     */
    fun downloadZone(zone: BrouterZone) {
        val st = _state.value
        if (zone.id in st.pending) return
        val besoin = st.missing(zone)
        _state.update { it.copy(pending = it.pending + (zone.id to besoin), zoneFailed = it.zoneFailed - zone.id) }
        ecrireEnCours()
        onBusy()
        enqueue(besoin)
        scope.launch {
            val prets = await(zone.tiles)
            val ok = prets.size == zone.tiles.size
            if (ok) {
                ecartees = ecartees - zone.id
                ecrireZones(_state.value.zones + zone.id)
            }
            val annulee = annulees.remove(zone.id)
            _state.update {
                it.copy(
                    pending = it.pending - zone.id,
                    zones = if (ok) it.zones + zone.id else it.zones,
                    zoneFailed = if (ok || annulee) it.zoneFailed - zone.id else it.zoneFailed + zone.id,
                    // Une zone qu'on a soi-meme arretee n'a rien a annoncer : on sait deja.
                    finished = if (annulee && !ok) it.finished else it.finished + Finished(zone.id, ok),
                )
            }
            ecrireEnCours()
            if (ok) completer()
        }
    }

    /**
     * Deplace les donnees vers [target] - carres, debuts de transfert et registres -, puis [commit] y fait
     * pointer l'application. Refuse pendant un telechargement : un fichier deplace sous un transfert en
     * cours ne se retrouverait nulle part.
     *
     * Par renommage quand c'est possible, instantane sur un meme volume ; par copie sinon - vers une carte
     * SD, un gigaoctet prend quelques dizaines de secondes.
     */
    suspend fun moveTo(target: java.io.File, commit: () -> Unit): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val st = _state.value
            if (st.pending.isNotEmpty() || st.current != null || st.queued.isNotEmpty()) return@withContext false
            val source = segments.dir
            if (source.canonicalPath == target.canonicalPath) { commit(); return@withContext true }
            if (!BrouterStorage.writable(target)) return@withContext false
            val fichiers = source.listFiles().orEmpty().filter {
                it.isFile && (it.name.endsWith(".rd5") || it.name.endsWith(".rd5.part") ||
                    it.name == "zones.txt" || it.name == "pending.txt")
            }
            for (f in fichiers) {
                val dest = java.io.File(target, f.name)
                val ok = f.renameTo(dest) || runCatching {
                    f.copyTo(dest, overwrite = true)
                    dest.setLastModified(f.lastModified())
                    f.delete()
                }.isSuccess
                if (!ok) return@withContext false
            }
            commit()
            refreshInstalled()
            true
        }

    /** L'annonce de fin a ete lue : elle disparait de la carte. */
    fun acknowledge() = _state.update { it.copy(finished = emptyList()) }

    /**
     * Reprend les zones qui etaient en cours quand l'application s'est arretee - Android a pu la fermer
     * au milieu d'un telechargement. Le debut de chaque carre est garde : le transfert repart de la.
     */
    fun resumePending() {
        lireEnCours().mapNotNull { BrouterZones.byId(it) }.forEach { downloadZone(it) }
    }

    /** Des zones attendaient-elles d'etre reprises au lancement. */
    fun hasPendingOnDisk(): Boolean = lireEnCours().isNotEmpty()

    /** Les zones arretees a la demande, pour ne pas les annoncer en echec. */
    private val annulees = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Les zones en cours, ecrites a cote des carres : ce qu'on reprend apres un arret de l'application. */
    private val enCoursFichier get() = java.io.File(segments.dir, "pending.txt")

    private fun lireEnCours(): List<String> =
        runCatching { enCoursFichier.readLines().map { it.trim() }.filter { it.isNotEmpty() } }.getOrDefault(emptyList())

    private fun ecrireEnCours() {
        runCatching {
            segments.dir.mkdirs()
            enCoursFichier.writeText(_state.value.pending.keys.sorted().joinToString("\n"))
        }
    }

    /**
     * Supprime une zone telechargee : elle quitte la liste, et ses carres disparaissent - sauf ceux qu'une
     * autre zone telechargee utilise encore (cf. [BrouterZones.releasable]).
     */
    fun deleteZone(zone: BrouterZone) {
        val gardees = _state.value.zones.mapNotNull { BrouterZones.byId(it) }
        val liberes = BrouterZones.releasable(zone, gardees)
        // Ecartee : ses carres peuvent rester pour une autre zone, et elle ne doit pas revenir d'elle-meme
        // dans la liste (cf. [completer]). La telecharger a nouveau la leve.
        ecartees = ecartees + zone.id
        ecrireZones(_state.value.zones - zone.id)
        _state.update { it.copy(zones = it.zones - zone.id, zoneFailed = it.zoneFailed - zone.id) }
        liberes.forEach { delete(it) }
    }

    /**
     * Le registre des zones, a cote des carres : un identifiant par ligne pour une zone telechargee, et
     * precede d'un tiret pour une zone qu'on a supprimee (cf. [ecartees]).
     */
    private val registre get() = java.io.File(segments.dir, "zones.txt")

    private fun lignes(): List<String> =
        runCatching { registre.readLines().map { it.trim() }.filter { it.isNotEmpty() } }.getOrDefault(emptyList())

    private fun lireZones(): Set<String> =
        lignes().filter { BrouterZones.byId(it) != null }.toSet()

    /**
     * Les zones supprimees a la main. Une zone dont tous les carres sont la entre d'elle-meme dans la liste
     * - sauf celles-ci : les Pyrenees supprimees reviendraient au lancement suivant, leurs carres etant
     * encore la pour la France.
     */
    private var ecartees: Set<String> =
        lignes().filter { it.startsWith("-") }.map { it.drop(1) }.filter { BrouterZones.byId(it) != null }.toSet()

    private fun ecrireZones(ids: Set<String>) {
        runCatching {
            segments.dir.mkdirs()
            registre.writeText((ids.sorted() + ecartees.sorted().map { "-$it" }).joinToString("\n"))
        }
    }

    /**
     * Fait entrer dans la liste les zones dont tous les carres sont deja sur le telephone : apres la France,
     * les Pyrenees - dont les deux carres sont francais - sont la sans qu'on ait rien a telecharger.
     */
    private fun completer() {
        val st = _state.value
        val presents = st.installed.mapTo(HashSet()) { it.tile }
        val nouvelles = BrouterZones.all.filter { z ->
            z.id !in st.zones && z.id !in ecartees && z.id !in st.pending && z.tiles.all { it in presents }
        }.map { it.id }
        if (nouvelles.isEmpty()) return
        ecrireZones(st.zones + nouvelles)
        _state.update { it.copy(zones = it.zones + nouvelles) }
    }

    init {
        // Au lancement aussi : une zone complete par les telechargements d'une version qui ne le faisait pas.
        completer()
    }

    /**
     * Attend que ces carres ne soient plus ni en cours ni en file, et rend ceux qui sont presents.
     *
     * Pour qui les a demandes et veut savoir comment ca s'est fini - le telechargement d'une zone hors
     * ligne, qui l'annonce en fin de parcours.
     */
    suspend fun await(tiles: Collection<BrouterTile>): Set<BrouterTile> {
        state.first { s -> tiles.none { s.busy(it) } }
        return tiles.filterTo(mutableSetOf()) { segments.has(it) }
    }

    /** Arrete tout : le transfert en cours et la file. Le debut deja recu est garde pour une reprise. */
    fun cancel() {
        _state.update { it.copy(queued = emptyList()) }
        transfert?.cancel()
    }

    /** Arrete le telechargement d'une zone : ses carres en file et, s'il est a elle, le transfert en cours. */
    fun cancelZone(zone: BrouterZone) {
        val besoin = _state.value.pending[zone.id] ?: return
        annulees += zone.id
        _state.update { it.copy(queued = it.queued - besoin) }
        if (_state.value.current?.tile in besoin) transfert?.cancel()
    }
}
