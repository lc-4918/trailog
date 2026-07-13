package fr.lc4918.trailog.ui.routes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.lc4918.trailog.TrailogApp
import fr.lc4918.trailog.data.db.AppDatabase
import fr.lc4918.trailog.data.db.BasemapFolderEntity
import fr.lc4918.trailog.data.db.CompositeEntity
import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PointLayerData
import fr.lc4918.trailog.map.StyleBuilder
import fr.lc4918.trailog.map.compositeIdFromBasemapId
import fr.lc4918.trailog.map.offline.OfflineDownloadResult
import fr.lc4918.trailog.map.offline.OfflineDownloadState
import fr.lc4918.trailog.map.offline.OfflinePhase
import fr.lc4918.trailog.map.offline.TileMath
import fr.lc4918.trailog.ui.components.RenderLayer
import fr.lc4918.trailog.ui.offline.OfflineDownloadRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Position de dépose lors d'un drag & drop dans la légende : avant/après un sibling, ou dedans (dossier cible). */
enum class DropPosition { BEFORE, INTO, AFTER }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as TrailogApp).repository
    private val db = AppDatabase.get(app)

    val folders: StateFlow<List<FolderEntity>> =
        repo.folders.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val layers: StateFlow<List<LayerEntity>> =
        repo.layers.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val providers: StateFlow<List<ProviderEntity>> =
        repo.providers.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val composites: StateFlow<List<CompositeEntity>> =
        repo.composites.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val basemapFolders: StateFlow<List<BasemapFolderEntity>> =
        repo.basemapFolders.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<SettingsEntity?> =
        repo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // --- rendu carte (toutes les couches visibles ; une couche peut avoir points ET lignes) ---
    private val renderTick = MutableStateFlow(0)
    private val _renderLayers = MutableStateFlow<List<RenderLayer>>(emptyList())
    val renderLayers = _renderLayers.asStateFlow()

    // --- trace active (profil) ---
    private val _activeLayerId = MutableStateFlow<Long?>(null)
    val activeLayerId = _activeLayerId.asStateFlow()
    private val _computed = MutableStateFlow<ComputedTrack?>(null)
    val computed = _computed.asStateFlow()
    private val _cursor = MutableStateFlow<Int?>(null)
    val cursor = _cursor.asStateFlow()
    // vrai entre le tap sur une trace et l'affichage de son profil (spinner dans la zone du graphique).
    private val _profileLoading = MutableStateFlow(false)
    val profileLoading = _profileLoading.asStateFlow()

    // --- marqueur sélectionné (infobulle) ---
    private val _markerLayerData = MutableStateFlow<PointLayerData?>(null)
    val markerLayerData = _markerLayerData.asStateFlow()
    private val _markerLayerId = MutableStateFlow<Long?>(null)
    private val _selectedMarkerId = MutableStateFlow<String?>(null)
    val selectedMarkerId = _selectedMarkerId.asStateFlow()

    init {
        viewModelScope.launch {
            combine(layers, renderTick) { l, _ -> l }.collectLatest { list ->
                val simplify = settings.value?.simplifyRender ?: true
                val rl = withContext(Dispatchers.IO) {
                    list.filter { it.visible }.mapNotNull { ly ->
                        val f = repo.layerMapFile(ly, simplify) ?: return@mapNotNull null
                        // URI file:// (3 slashes : le chemin absolu commence par /) : MapLibre lit et parse le
                        // .map sur son thread de travail. La révision (horodatage ^ taille) change quand le
                        // fichier est réécrit (édition de points) et force un rechargement de la source.
                        val revision = f.lastModified() xor (f.length() * 1000003L)
                        RenderLayer("ly${ly.id}", "file://" + f.absolutePath, revision, ly.color)
                    }
                }
                _renderLayers.value = rl
            }
        }
    }

    fun activeLayer(): LayerEntity? = _activeLayerId.value?.let { id -> layers.value.firstOrNull { it.id == id } }
    fun selectedFeature(): PointFeature? {
        val id = _selectedMarkerId.value ?: return null
        return _markerLayerData.value?.features?.firstOrNull { it.id == id }
    }

    // ---------- taps carte ----------
    /** Tap sur la ligne d'une couche -> profil + curseur. key = "ly<id>". */
    fun onPickLine(key: String, lon: Double, lat: Double) {
        val id = key.removePrefix("ly").toLongOrNull() ?: return
        val layer = layers.value.firstOrNull { it.id == id } ?: return
        if (!layer.hasLine) return
        closeMarker()
        // Affichage immédiat du panneau (titre + spinner) : on remet le profil à zéro et on marque le chargement.
        _activeLayerId.value = id
        _computed.value = null
        _cursor.value = null
        _profileLoading.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                // Profils précalculés (lecture du .prof, pas de parse DOM au tap). Une couche peut avoir
                // plusieurs segments : on retient celui le plus proche du tap (pas de concaténation).
                val profiles = repo.loadProfiles(layer)
                val nearest = profiles.minByOrNull { ct ->
                    ct.samples.minOfOrNull { s -> TrackMath.haversine(lon, lat, s.lon, s.lat) } ?: Double.MAX_VALUE
                } ?: return@withContext null
                var best = -1; var bestD = Double.MAX_VALUE
                nearest.samples.forEachIndexed { i, s ->
                    val d = TrackMath.haversine(lon, lat, s.lon, s.lat)
                    if (d < bestD) { bestD = d; best = i }
                }
                nearest to (if (best >= 0) best else null)
            }
            // Ne pas écraser si l'utilisateur a retapé une autre trace pendant le calcul.
            if (_activeLayerId.value == id) {
                _computed.value = result?.first
                _cursor.value = result?.second
                _profileLoading.value = false
            }
        }
    }

    /** Tap sur un point d'une couche -> infobulle. key = "ly<id>". */
    fun onPickPoint(key: String, featureId: String) {
        val id = key.removePrefix("ly").toLongOrNull() ?: return
        val layer = layers.value.firstOrNull { it.id == id } ?: return
        if (!layer.hasPoints) return
        closeProfile()
        _markerLayerId.value = id
        viewModelScope.launch {
            _markerLayerData.value = repo.loadLayer(layer)
            _selectedMarkerId.value = featureId
        }
    }

    fun closeProfile() { _activeLayerId.value = null; _computed.value = null; _cursor.value = null; _profileLoading.value = false }
    fun closeMarker() { _selectedMarkerId.value = null; _markerLayerId.value = null; _markerLayerData.value = null }
    fun setCursor(index: Int?) { _cursor.value = index }

    /** Import d'une image choisie par l'utilisateur pour un champ IMAGE d'infobulle. */
    fun importFeatureImage(uri: Uri, onImported: (String) -> Unit) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val input = ctx.contentResolver.openInputStream(uri) ?: return@launch
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            } ?: "image"
            val path = repo.importImage(input, name)
            onImported(path)
        }
    }

    fun saveFeature(updated: PointFeature) {
        val id = _markerLayerId.value ?: return
        val layer = layers.value.firstOrNull { it.id == id } ?: return
        val data = _markerLayerData.value ?: return
        val idx = data.features.indexOfFirst { it.id == updated.id }
        if (idx < 0) return
        data.features[idx] = updated
        _markerLayerData.value = data.copy()
        viewModelScope.launch {
            repo.saveFeatures(layer, data)
            renderTick.value++              // le .map réécrit change de révision -> rechargement des marqueurs
        }
    }

    // ---------- visibilité ----------
    fun setLayerVisible(l: LayerEntity, v: Boolean) = viewModelScope.launch {
        db.layers().setVisible(l.id, v)
        if (!v && _activeLayerId.value == l.id) closeProfile()
        if (!v && _markerLayerId.value == l.id) closeMarker()
    }
    fun setLayerColor(l: LayerEntity, color: String) = viewModelScope.launch { db.layers().setColor(l.id, color) }

    /** Applique la visibilité à toutes les couches du dossier (et de ses sous-dossiers). */
    fun setFolderVisible(folderId: Long, visible: Boolean) = viewModelScope.launch {
        val ids = HashSet<Long>(); val stack = ArrayDeque<Long>(); stack.add(folderId)
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            if (ids.add(f)) folders.value.filter { it.parentId == f }.forEach { stack.add(it.id) }
        }
        val affected = layers.value.filter { it.folderId in ids }
        affected.forEach { db.layers().setVisible(it.id, visible) }
        if (!visible) {
            if (affected.any { it.id == _activeLayerId.value }) closeProfile()
            if (affected.any { it.id == _markerLayerId.value }) closeMarker()
        }
    }

    /** Tap dans le vide sur la carte : ferme profil et infobulle. */
    fun closeOnEmpty() { closeProfile(); closeMarker() }

    /** Enregistre la position caméra (pour rouvrir sur le dernier affichage). */
    fun saveCameraState(lat: Double, lon: Double, zoom: Double) {
        val s = settings.value ?: return
        if (s.hasCamera && kotlin.math.abs(s.lastLat - lat) < 1e-6 &&
            kotlin.math.abs(s.lastLon - lon) < 1e-6 && kotlin.math.abs(s.lastZoom - zoom) < 1e-4) return
        viewModelScope.launch { db.settings().upsert(s.copy(lastLat = lat, lastLon = lon, lastZoom = zoom, hasCamera = true)) }
    }

    // ---------- import (avec dossier de destination) ----------
    private var pendingFit: DoubleArray? = null

    // Nombre d'imports en cours par dossier de destination (null = racine) : pour afficher un spinner
    // dans le dossier concerné tant que l'import n'est pas terminé.
    private val _importing = MutableStateFlow<Map<Long?, Int>>(emptyMap())
    val importing: StateFlow<Map<Long?, Int>> = _importing.asStateFlow()
    private fun bumpImporting(folderId: Long?, delta: Int) {
        _importing.update { m -> (m + (folderId to ((m[folderId] ?: 0) + delta))).filterValues { it > 0 } }
    }

    fun importLayer(bytes: ByteArray, fileName: String, folderId: Long?) =
        viewModelScope.launch {
            bumpImporting(folderId, +1)
            try { unionPendingFit(repo.importLayer(bytes, fileName, folderId)) }
            finally { bumpImporting(folderId, -1) }
        }

    private fun unionPendingFit(b: DoubleArray) {
        if (b.size < 4 || (b[0] == 0.0 && b[2] == 0.0)) return
        val cur = pendingFit
        pendingFit = if (cur == null) b else doubleArrayOf(
            minOf(cur[0], b[0]), minOf(cur[1], b[1]), maxOf(cur[2], b[2]), maxOf(cur[3], b[3]))
    }
    /** Renvoie (et efface) la bbox des couches récemment importées, pour cadrer la carte. */
    fun consumePendingFit(): DoubleArray? { val b = pendingFit; pendingFit = null; return b }

    // ---------- arborescence ----------
    fun createFolder(name: String, parentId: Long?) =
        viewModelScope.launch { db.folders().insert(FolderEntity(name = name, parentId = parentId)) }
    /** Crée un dossier et renvoie son id (pour enchaîner un import dedans). */
    fun createFolder(name: String, parentId: Long?, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val id = db.folders().insert(FolderEntity(name = name, parentId = parentId)); onCreated(id)
    }
    fun renameFolder(id: Long, name: String) = viewModelScope.launch { db.folders().rename(id, name) }
    fun moveFolder(id: Long, parentId: Long?) = viewModelScope.launch { db.folders().move(id, parentId) }

    /**
     * Supprime un dossier. Si [deleteContents], supprime récursivement ses sous-dossiers et toutes leurs
     * couches (retirées aussi de la carte via le flux `layers`) ; sinon remonte le contenu direct au
     * dossier parent (racine si le dossier était au premier niveau) avant de le supprimer.
     */
    fun deleteFolder(f: FolderEntity, deleteContents: Boolean) = viewModelScope.launch {
        if (deleteContents) {
            val ids = descendantFolderIds(f.id, folders.value)
            layers.value.filter { it.folderId in ids }.forEach { repo.deleteLayer(it) }
            folders.value.filter { it.id in ids }.forEach { db.folders().delete(it) }
        } else {
            layers.value.filter { it.folderId == f.id }.forEach { db.layers().move(it.id, f.parentId) }
            folders.value.filter { it.parentId == f.id }.forEach { db.folders().move(it.id, f.parentId) }
            db.folders().delete(f)
        }
    }

    /** [rootId] et l'ensemble de ses descendants (dossiers), pour une suppression récursive. */
    private fun descendantFolderIds(rootId: Long, all: List<FolderEntity>): Set<Long> {
        val result = linkedSetOf(rootId)
        var frontier = listOf(rootId)
        while (frontier.isNotEmpty()) {
            frontier = all.filter { it.parentId in frontier && result.add(it.id) }.map { it.id }
        }
        return result
    }

    fun renameLayer(id: Long, name: String) = viewModelScope.launch { db.layers().rename(id, name) }
    fun moveLayer(id: Long, folderId: Long?) = viewModelScope.launch { db.layers().move(id, folderId) }
    fun deleteLayer(l: LayerEntity) = viewModelScope.launch { repo.deleteLayer(l) }

    // ---------- réordonnancement unifié par drag & drop (dossiers/couches mélangés) ----------
    /**
     * Dépose l'élément (`kind`,`id`) juste avant/après (`targetKind`,`targetId`) - comme nouveau sibling dans le
     * parent de la cible -, ou dedans si `position == INTO` (la cible doit alors être un dossier). Renumérote
     * l'ensemble combiné (dossiers+couches) du parent d'arrivée, et celui de départ si le parent change.
     */
    suspend fun reorderDrop(kind: String, id: Long, targetKind: String, targetId: Long, position: DropPosition) {
        val newParentId: Long? = if (position == DropPosition.INTO) targetId else parentOf(targetKind, targetId)
        val oldParentId = parentOf(kind, id)
        val current = combinedChildren(newParentId).filterNot { it.first == kind && it.second == id }
        val targetIndex = current.indexOfFirst { it.first == targetKind && it.second == targetId }
        val insertAt = when (position) {
            DropPosition.INTO -> current.size
            DropPosition.BEFORE -> if (targetIndex < 0) current.size else targetIndex
            DropPosition.AFTER -> if (targetIndex < 0) current.size else targetIndex + 1
        }
        val newList = current.toMutableList().apply { add(insertAt, Triple(kind, id, 0)) }
        if (oldParentId != newParentId) {
            when (kind) {
                "folder" -> db.folders().move(id, newParentId)
                "layer" -> db.layers().move(id, newParentId)
            }
        }
        newList.forEachIndexed { idx, (k, itemId, _) -> setSort(k, itemId, idx) }
        if (oldParentId != newParentId) {
            combinedChildren(oldParentId).filterNot { it.first == kind && it.second == id }
                .forEachIndexed { idx, (k, itemId, _) -> setSort(k, itemId, idx) }
        }
    }

    private suspend fun setSort(kind: String, id: Long, order: Int) = when (kind) {
        "folder" -> db.folders().setSort(id, order)
        else -> db.layers().setSort(id, order)
    }
    private fun parentOf(kind: String, id: Long): Long? = when (kind) {
        "folder" -> folders.value.firstOrNull { it.id == id }?.parentId
        else -> layers.value.firstOrNull { it.id == id }?.folderId
    }
    private fun combinedChildren(parentId: Long?): List<Triple<String, Long, Int>> {
        val f = folders.value.filter { it.parentId == parentId }.map { Triple("folder", it.id, it.sortOrder) }
        val l = layers.value.filter { it.folderId == parentId }.map { Triple("layer", it.id, it.sortOrder) }
        return (f + l).sortedWith(compareBy({ it.third }, { typeRank(it.first) }, { it.second }))
    }
    private fun typeRank(kind: String) = if (kind == "folder") 0 else 1

    // ---------- Basemap Control (dossiers + réordonnancement des fonds de plan) ----------
    fun createBasemapFolder(name: String, parentId: Long?) =
        viewModelScope.launch { db.basemapFolders().insert(BasemapFolderEntity(name = name, parentId = parentId)) }

    /** Change le fond de plan courant (bouton du Basemap Control ou tap sur un item du panneau). */
    fun selectBasemap(id: String) = viewModelScope.launch {
        val s = settings.value ?: return@launch
        db.settings().upsert(s.copy(defaultBasemapId = id))
    }

    /** Active/désactive le relief (tap sur son entrée dans le gestionnaire de couches) : contrairement aux
     *  autres fonds, le relief n'est jamais "sélectionné" comme fond visuel (tuiles DEM brutes illisibles
     *  telles quelles) - tapoter dessus bascule simplement son affichage en overlay sur le fond courant. */
    fun toggleProviderEnabled(id: String) = viewModelScope.launch {
        val p = providers.value.firstOrNull { it.id == id } ?: return@launch
        db.providers().upsert(p.copy(enabled = !p.enabled))
    }

    /** Réordonnancement unifié du Basemap Control : kind dans {"folder","provider","composite"}, id en String
     *  (id de provider natif, id de dossier/composite converti). Même logique que reorderDrop pour la légende. */
    suspend fun reorderBasemapDrop(kind: String, id: String, targetKind: String, targetId: String, position: DropPosition) {
        val newParentId: Long? = if (position == DropPosition.INTO) targetId.toLongOrNull() else basemapParentOf(targetKind, targetId)
        val oldParentId = basemapParentOf(kind, id)
        val current = combinedBasemapChildren(newParentId).filterNot { it.first == kind && it.second == id }
        val targetIndex = current.indexOfFirst { it.first == targetKind && it.second == targetId }
        val insertAt = when (position) {
            DropPosition.INTO -> current.size
            DropPosition.BEFORE -> if (targetIndex < 0) current.size else targetIndex
            DropPosition.AFTER -> if (targetIndex < 0) current.size else targetIndex + 1
        }
        val newList = current.toMutableList().apply { add(insertAt, Triple(kind, id, 0)) }
        if (oldParentId != newParentId) {
            when (kind) {
                "folder" -> id.toLongOrNull()?.let { db.basemapFolders().move(it, newParentId) }
                "provider" -> db.providers().move(id, newParentId)
                "composite" -> id.toLongOrNull()?.let { db.composites().move(it, newParentId) }
            }
        }
        newList.forEachIndexed { idx, (k, itemId, _) -> setBasemapSort(k, itemId, idx) }
        if (oldParentId != newParentId) {
            combinedBasemapChildren(oldParentId).filterNot { it.first == kind && it.second == id }
                .forEachIndexed { idx, (k, itemId, _) -> setBasemapSort(k, itemId, idx) }
        }
    }

    private suspend fun setBasemapSort(kind: String, id: String, order: Int) = when (kind) {
        "folder" -> id.toLongOrNull()?.let { db.basemapFolders().setSort(it, order) } ?: Unit
        "provider" -> db.providers().setSort(id, order)
        else -> id.toLongOrNull()?.let { db.composites().setSort(it, order) } ?: Unit
    }
    private fun basemapParentOf(kind: String, id: String): Long? = when (kind) {
        "folder" -> basemapFolders.value.firstOrNull { it.id.toString() == id }?.parentId
        "provider" -> providers.value.firstOrNull { it.id == id }?.folderId
        else -> composites.value.firstOrNull { it.id.toString() == id }?.folderId
    }
    private fun combinedBasemapChildren(parentId: Long?): List<Triple<String, String, Int>> {
        val f = basemapFolders.value.filter { it.parentId == parentId }.map { Triple("folder", it.id.toString(), it.sortOrder) }
        // cf. BasemapControlPanel.combinedBasemapChildren : le relief reste toujours dans l'arbre, même désactivé.
        val p = providers.value.filter { (it.enabled || it.type == "DEM") && it.folderId == parentId }.map { Triple("provider", it.id, it.sortOrder) }
        val c = composites.value.filter { it.enabled && it.folderId == parentId }.map { Triple("composite", it.id.toString(), it.sortOrder) }
        return (f + p + c).sortedWith(compareBy({ it.third }, { basemapTypeRank(it.first) }, { it.second }))
    }
    private fun basemapTypeRank(kind: String) = when (kind) { "folder" -> 0; "provider" -> 1; else -> 2 }

    // ---------- style ----------
    // ---------- téléchargement de carte hors-ligne, domaine B (SPEC offline_map.md section 4-5) ----------
    private val _offlineDownload = MutableStateFlow<OfflineDownloadState?>(null)
    val offlineDownload: StateFlow<OfflineDownloadState?> = _offlineDownload.asStateFlow()
    private var offlineJob: Job? = null

    /** Lance le téléchargement de la zone validée avec le fond en ligne courant. */
    fun startOfflineDownload(req: OfflineDownloadRequest) {
        val s = settings.value ?: return
        val provider = providers.value.firstOrNull { it.id == s.defaultBasemapId } ?: return
        val total = TileMath.totalTileCount(req.bbox, req.minZoom, req.maxZoom).toInt()
        _offlineDownload.value = OfflineDownloadState(name = req.name, total = total)
        offlineJob = viewModelScope.launch {
            try {
                val result = repo.downloadOfflineMap(provider, req, s) { done, failed ->
                    // Le moteur rappelle depuis plusieurs threads : update() (CAS) est thread-safe.
                    _offlineDownload.update { it?.copy(done = done, failed = failed) }
                }
                _offlineDownload.update { st ->
                    when (result) {
                        // Fin en mode réduit : on force minimized=false pour rouvrir la popup (SPEC section 4).
                        is OfflineDownloadResult.Success -> st?.copy(phase = OfflinePhase.SUCCESS, minimized = false)
                        is OfflineDownloadResult.Failed ->
                            st?.copy(phase = OfflinePhase.ERROR, failed = result.failed, minimized = false)
                    }
                }
            } catch (c: CancellationException) {
                throw c   // annulation utilisateur : l'état est déjà remis à null par cancelOfflineDownload()
            } catch (e: Exception) {
                _offlineDownload.update { it?.copy(phase = OfflinePhase.ERROR, minimized = false) }
            }
        }
    }

    fun cancelOfflineDownload() {
        offlineJob?.cancel()
        offlineJob = null
        _offlineDownload.value = null
    }

    fun setOfflineDownloadMinimized(minimized: Boolean) =
        _offlineDownload.update { it?.copy(minimized = minimized) }

    /** Ferme la popup après succès/erreur (le provider est déjà enregistré en cas de succès). */
    fun dismissOfflineDownload() {
        offlineJob = null
        _offlineDownload.value = null
    }

    // Réactif (pas une simple fonction synchrone) : un fond composite peut inclure un fond VECTOR, dont la
    // fusion nécessite de récupérer son style.json distant (suspend), donc de recalculer hors du thread UI.
    private val _mapStyle = MutableStateFlow<StyleBuilder.Result?>(null)
    val mapStyle: StateFlow<StyleBuilder.Result?> = _mapStyle.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settings, providers, composites) { s, p, c -> Triple(s, p, c) }.collectLatest { (s, provs, comps) ->
                _mapStyle.value = s?.let { buildStyle(it, provs, comps) }
            }
        }
    }

    private suspend fun buildStyle(s: SettingsEntity, provs: List<ProviderEntity>, comps: List<CompositeEntity>): StyleBuilder.Result? {
        // Le relief n'est jamais un fond visuel en soi (tuiles DEM brutes illisibles telles quelles, cf.
        // StyleBuilder) : il n'apparaît que s'il est activé directement (bascule via toggleProviderEnabled,
        // tap dans le gestionnaire de couches) ou si le composite actif l'inclut en overlay.
        val demProvider = provs.firstOrNull { it.type == "DEM" }
        val toggledDem = demProvider?.takeIf { it.enabled }
        val compositeId = compositeIdFromBasemapId(s.defaultBasemapId)
        val composite = compositeId?.let { id -> comps.firstOrNull { it.id == id && it.enabled } }
        if (composite != null) {
            val bg = provs.firstOrNull { it.id == composite.backgroundProviderId }
            val fg = provs.firstOrNull { it.id == composite.foregroundProviderId }
            if (bg != null && bg.type != "DEM") {
                val overlays = if (fg != null && fg.type != "DEM") listOf(fg) else emptyList()
                val dem = toggledDem ?: fg?.takeIf { it.type == "DEM" }
                return StyleBuilder.build(bg, overlays, dem, repo.mbtilesDir(s),
                    overlayOpacities = if (fg != null && fg.type != "DEM") mapOf(fg.id to composite.foregroundOpacity) else emptyMap())
            }
        }
        val base = provs.firstOrNull { it.id == s.defaultBasemapId && it.type != "DEM" }
            ?: provs.firstOrNull { it.type != "DEM" } ?: return null
        return StyleBuilder.build(base, emptyList(), toggledDem, repo.mbtilesDir(s))
    }
}
