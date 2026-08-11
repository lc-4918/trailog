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
import fr.lc4918.trailog.data.imp.EmptyLayerException
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.geo.TrackMeasure
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
import fr.lc4918.trailog.ui.measure.MeasurePoint
import fr.lc4918.trailog.ui.offline.OfflineDownloadRequest
import fr.lc4918.trailog.ui.profile.ProfileZoom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Deux réglages donnent-ils le MÊME style de carte ?
 *
 * Sert de filtre à la reconstruction du style : la position caméra vit elle aussi dans `SettingsEntity` et
 * s'écrit à chaque arrêt de la carte. Sans ce filtre, chaque déplacement relançait `buildStyle` (et un
 * éventuel chargement de style vectoriel distant) puis recomposait tout l'écran, saturant le processeur.
 *
 * **Le piège est l'oubli, pas le filtre.** Un réglage qui entre dans le style et qu'on oublie ici ne casse
 * rien de visible : le style se reconstruit à la PROCHAINE cause valable, et le changement s'applique donc
 * avec un tour de retard. C'est ce qui est arrivé à l'ombrage du relief, quand son état a quitté le fond
 * DEM pour les réglages : on l'éteignait, il restait ; on l'allumait, il n'apparaissait qu'au changement de
 * fond suivant. Extrait ici, et verrouillé par test, pour que l'oubli suivant se voie.
 */
internal fun sameStyleSettings(a: SettingsEntity?, b: SettingsEntity?): Boolean =
    a?.defaultBasemapId == b?.defaultBasemapId &&
        a?.mbtilesDir == b?.mbtilesDir &&
        a?.hillshadeOn == b?.hillshadeOn

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

    // --- zoom sur le profil ---
    /**
     * Portion du profil actuellement affichee (indices absolus dans computed.samples), ou null pour la
     * trace entiere. Une seule fenetre et non une pile de niveaux : le zoom se fait desormais aux doigts,
     * en continu, et l'on ne "remonte" plus d'un cran mais on revient d'un coup a la vue complete.
     */
    private val _profileZoom = MutableStateFlow<IntRange?>(null)
    val profileZoom = _profileZoom.asStateFlow()

    // --- marqueur sélectionné (infobulle) ---
    private val _markerLayerData = MutableStateFlow<PointLayerData?>(null)
    val markerLayerData = _markerLayerData.asStateFlow()
    private val _markerLayerId = MutableStateFlow<Long?>(null)
    /** Couche du marqueur sélectionné, pour poser l'ombre portée juste sous les pins de cette trace. */
    val markerLayerId = _markerLayerId.asStateFlow()
    private val _selectedMarkerId = MutableStateFlow<String?>(null)
    val selectedMarkerId = _selectedMarkerId.asStateFlow()
    // Position du marqueur tapé, connue dès le tap (géométrie interrogée sur la carte) : l'infobulle peut
    // donc s'afficher et se placer immédiatement, sans attendre le chargement des propriétés de la couche.
    private val _selectedMarkerPos = MutableStateFlow<Pair<Double, Double>?>(null)
    val selectedMarkerPos = _selectedMarkerPos.asStateFlow()

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
        // Trace déjà décrite par le profil affiché : un tap dessus ne fait que déplacer le curseur sur le point
        // le plus proche, sans réinitialiser le zoom ni recadrer la carte (surtout pas de "dézoom global" comme
        // au premier appui). On cherche le point le plus proche dans le profil courant, pas dans toute la couche.
        val current = _computed.value
        if (_activeLayerId.value == id && current != null) {
            viewModelScope.launch {
                val idx = withContext(Dispatchers.Default) {
                    var best = -1; var bestD = Double.MAX_VALUE
                    current.samples.forEachIndexed { i, s ->
                        val d = TrackMath.haversine(lon, lat, s.lon, s.lat)
                        if (d < bestD) { bestD = d; best = i }
                    }
                    if (best >= 0) best else null
                }
                if (_activeLayerId.value == id) _cursor.value = idx
            }
            return
        }
        // Affichage immédiat du panneau (titre + spinner) : on remet le profil à zéro et on marque le chargement.
        _activeLayerId.value = id
        _computed.value = null
        _cursor.value = null
        _profileLoading.value = true
        resetProfileZoom()
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
    fun onPickPoint(key: String, featureId: String, lon: Double, lat: Double) {
        val id = key.removePrefix("ly").toLongOrNull() ?: return
        val layer = layers.value.firstOrNull { it.id == id } ?: return
        if (!layer.hasPoints) return
        closeProfile()
        // La position vient de la géométrie tapée : elle suffit à placer l'infobulle tout de suite.
        _selectedMarkerPos.value = lon to lat
        // Couche déjà chargée contenant ce marqueur (cas courant : on passe d'un marqueur à l'autre) :
        // bascule immédiate, sans spinner ni rechargement.
        val loaded = _markerLayerData.value
        if (_markerLayerId.value == id && loaded != null && loaded.features.any { it.id == featureId }) {
            _selectedMarkerId.value = featureId
            return
        }
        // Sinon : infobulle affichée vide avec un spinner, les propriétés arrivent ensuite (cf. profil).
        _markerLayerId.value = id
        _selectedMarkerId.value = featureId
        _markerLayerData.value = null
        viewModelScope.launch {
            val data = repo.loadLayer(layer)
            // Un autre marqueur a pu être tapé entre-temps : ne pas écraser la sélection courante.
            if (_markerLayerId.value != id || _selectedMarkerId.value != featureId) return@launch
            _markerLayerData.value = data
        }
    }

    // ---------- mesure sur trace ----------
    /**
     * Premier point d'une mesure : le tap est rabattu sur la plus proche des traces passant sous le doigt
     * (cf. TrackMeasure.project). Null si aucune n'y passe - il n'y a alors rien à mesurer, et le tap
     * reste sans effet.
     *
     * [keys] sont les couches dessinées autour du doigt, désignées par l'index de rendu de la carte
     * (cf. MapController.lineKeysNear) : elles seules sont lues. Passer toutes les couches visibles en
     * revue demandait un fichier de profil par couche, soit plusieurs secondes d'attente avant le premier
     * marqueur sur une carte qui en porte beaucoup - alors que la réponse ne pouvait venir que d'une trace
     * effectivement visible sous le doigt.
     *
     * Les couches retenues sont lues **en parallèle** : elles sont peu nombreuses, mais un paquet de
     * traces superposées les ferait sinon attendre l'une après l'autre.
     *
     * Les profils sont lus par le dépôt, donc servis par son cache dès la première fois : le kilométrage
     * cumulé qu'ils portent est calculé sur la géométrie complète, avant décimation, et la mesure suit
     * donc le parcours réel et non la ligne allégée du rendu.
     */
    fun pickMeasureStart(
        lon: Double, lat: Double, keys: List<String>, onResult: (MeasurePoint?) -> Unit,
    ) = viewModelScope.launch {
        val ids = keys.mapNotNull { it.removePrefix("ly").toLongOrNull() }.toSet()
        val candidates = layers.value.filter { it.visible && it.hasLine && it.id in ids }
        if (candidates.isEmpty()) { onResult(null); return@launch }
        val best = candidates.map { ly ->
            async {
                val profiles = repo.loadProfiles(ly)
                withContext(Dispatchers.Default) {
                    profiles.mapIndexedNotNull { i, ct -> TrackMeasure.project(ct.samples, lon, lat)?.let { i to it } }
                        .minByOrNull { it.second.awayM }
                }?.let { (index, p) -> MeasurePoint(ly.id, ly.name, index, p.lon, p.lat, p.alongM) to p.awayM }
            }
        }.awaitAll().filterNotNull().minByOrNull { it.second }
        onResult(best?.first)
    }

    /**
     * Second point, contraint au segment du premier : mesurer suppose un parcours commun, et deux traces
     * distinctes n'en offrent aucun. Le tap est donc rabattu sur CETTE trace, où qu'il tombe.
     *
     * Rend aussi le parcours mesuré échantillonné, calculé sur les mêmes samples : c'est là-dessus que
     * l'infobulle s'ancre (cf. MeasureAnchor), et le demander ailleurs relirait la couche pour la même
     * réponse.
     */
    fun pickMeasureEnd(
        start: MeasurePoint, lon: Double, lat: Double,
        onResult: (MeasurePoint, List<Pair<Double, Double>>) -> Unit,
    ) = viewModelScope.launch {
        val layer = layers.value.firstOrNull { it.id == start.layerId } ?: return@launch
        val samples = repo.loadProfiles(layer).getOrNull(start.trackIndex)?.samples ?: return@launch
        val computed = withContext(Dispatchers.Default) {
            TrackMeasure.project(samples, lon, lat)?.let { p ->
                p to TrackMeasure.portion(samples, start.alongM, p.alongM)
            }
        } ?: return@launch
        val (p, path) = computed
        onResult(start.copy(lon = p.lon, lat = p.lat, alongM = p.alongM), path)
    }

    fun closeProfile() {
        _activeLayerId.value = null; _computed.value = null; _cursor.value = null; _profileLoading.value = false
        // Les zooms disparaissent mais la carte ne bouge pas (pas d'effet de bord ici, juste l'etat local).
        resetProfileZoom()
    }
    fun closeMarker() {
        _selectedMarkerId.value = null; _markerLayerId.value = null; _markerLayerData.value = null
        _selectedMarkerPos.value = null
    }
    fun setCursor(index: Int?) { _cursor.value = index }

    private fun resetProfileZoom() {
        _profileZoom.value = null
    }

    /** Retour a la vue complete (bouton "expand"). Ne touche pas a la position de la carte ailleurs que
     *  via l'effet de recadrage observant profileZoom (cf. MainScreen). */
    fun expandProfileZoom() { _profileZoom.value = null }

    /** Grossissement au pincement ou au double-tap, centre sur [focusFraction] (cf. [ProfileZoom]). */
    fun zoomProfile(scale: Float, focusFraction: Float) {
        val total = _computed.value?.samples?.size ?: return
        val next = ProfileZoom.window(_profileZoom.value, total, scale, focusFraction)
        if (next == _profileZoom.value) return
        _profileZoom.value = next
        _cursor.value = null
    }

    /**
     * Tap sur le graphique du profil : deplace le curseur. [localIndex] est relatif a la fenetre
     * actuellement affichee (la portion zoomee, ou la trace complete si aucun zoom).
     */
    fun onProfileTap(localIndex: Int) {
        _cursor.value = (_profileZoom.value?.first ?: 0) + localIndex
    }

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

    fun deleteFeature(target: PointFeature) {
        val id = _markerLayerId.value ?: return
        val layer = layers.value.firstOrNull { it.id == id } ?: return
        val data = _markerLayerData.value ?: return
        val features = data.features.filterTo(mutableListOf()) { it.id != target.id }
        if (features.size == data.features.size) return
        val newData = data.copy(features = features)
        // Tout de suite, pas dans la coroutine : le marqueur n'existe plus, rien à rouvrir. Refermé après
        // l'écriture, l'infobulle du point supprimé se réafficherait le temps de celle-ci, l'éditeur se
        // fermant lui sans attendre. La couche et les points à écrire sont déjà capturés ci-dessus.
        closeMarker()
        viewModelScope.launch {
            repo.saveFeatures(layer, newData)
            renderTick.value++              // le .map réécrit change de révision -> rechargement des marqueurs
        }
    }

    fun saveFeature(updated: PointFeature) {
        val id = _markerLayerId.value ?: return
        val layer = layers.value.firstOrNull { it.id == id } ?: return
        val data = _markerLayerData.value ?: return
        val idx = data.features.indexOfFirst { it.id == updated.id }
        if (idx < 0) return
        // Liste neuve, sans toucher à l'ancienne : muter en place rendrait la valeur déjà publiée égale à
        // la nouvelle, et StateFlow, qui conflate sur equals, n'émettrait rien. L'infobulle resterait alors
        // sur le marqueur d'avant jusqu'à sa réouverture.
        val newData = data.copy(features = data.features.toMutableList().also { it[idx] = updated })
        _markerLayerData.value = newData
        viewModelScope.launch {
            repo.saveFeatures(layer, newData)
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

    /**
     * Applique une même couleur à toutes les couches du dossier, sous-dossiers compris.
     *
     * Récursif comme la visibilité et la suppression : un dossier répond de tout ce qu'il contient, et
     * s'arrêter à ses couches directes laisserait, sous le même dossier, des sous-dossiers d'une autre
     * couleur - ce qui est précisément ce qu'on cherchait à défaire.
     *
     * Aucune trace de la couleur d'avant : c'est une écriture par couche, et chacune garde ensuite la
     * sienne, modifiable une à une. Le dossier n'a pas de couleur propre dont celles-ci dériveraient.
     */
    fun setFolderColor(folderId: Long, color: String) = viewModelScope.launch {
        val ids = descendantFolderIds(folderId, folders.value)
        layers.value.filter { it.folderId in ids }.forEach { db.layers().setColor(it.id, color) }
    }

    /** Applique la visibilité à toutes les couches du dossier (et de ses sous-dossiers). */
    fun setFolderVisible(folderId: Long, visible: Boolean) = viewModelScope.launch {
        val ids = descendantFolderIds(folderId, folders.value)
        val affected = layers.value.filter { it.folderId in ids }
        affected.forEach { db.layers().setVisible(it.id, visible) }
        if (!visible) {
            if (affected.any { it.id == _activeLayerId.value }) closeProfile()
            if (affected.any { it.id == _markerLayerId.value }) closeMarker()
        }
    }

    /** Tap dans le vide sur la carte : ferme profil et infobulle. */
    fun closeOnEmpty() { closeProfile(); closeMarker() }

    // Position caméra en attente d'enregistrement : la carte émet un "idle" à chaque micro-arrêt pendant un
    // déplacement (~20 en quelques secondes). On débounce pour n'écrire qu'une fois la carte réellement
    // stabilisée, au lieu de marteler la base (chaque écriture réémet le flow settings -> recompositions).
    private val _cameraToSave = MutableStateFlow<Triple<Double, Double, Double>?>(null)

    init {
        viewModelScope.launch {
            _cameraToSave.filterNotNull().debounce(600).collect { (lat, lon, zoom) ->
                val s = settings.value ?: return@collect
                if (s.hasCamera && kotlin.math.abs(s.lastLat - lat) < 1e-6 &&
                    kotlin.math.abs(s.lastLon - lon) < 1e-6 && kotlin.math.abs(s.lastZoom - zoom) < 1e-4) return@collect
                db.settings().upsert(s.copy(lastLat = lat, lastLon = lon, lastZoom = zoom, hasCamera = true))
            }
        }
    }

    /** Enregistre la position caméra (pour rouvrir sur le dernier affichage), débouncée (cf. _cameraToSave). */
    fun saveCameraState(lat: Double, lon: Double, zoom: Double) { _cameraToSave.value = Triple(lat, lon, zoom) }

    // ---------- import (avec dossier de destination) ----------
    private var pendingFit: DoubleArray? = null

    // Nombre d'imports en cours par dossier de destination (null = racine) : pour afficher un spinner
    // dans le dossier concerné tant que l'import n'est pas terminé.
    private val _importing = MutableStateFlow<Map<Long?, Int>>(emptyMap())
    val importing: StateFlow<Map<Long?, Int>> = _importing.asStateFlow()
    /** Fichier refusé à l'import, et pourquoi (cf. importFailures). */
    enum class ImportError { INVALID, EMPTY }
    data class ImportFailure(val fileName: String, val error: ImportError)

    private val _importFailures = MutableStateFlow<List<ImportFailure>>(emptyList())
    val importFailures = _importFailures.asStateFlow()

    private fun bumpImporting(folderId: Long?, delta: Int) {
        _importing.update { m -> (m + (folderId to ((m[folderId] ?: 0) + delta))).filterValues { it > 0 } }
    }

    fun importLayer(bytes: ByteArray, fileName: String, folderId: Long?) =
        viewModelScope.launch {
            bumpImporting(folderId, +1)
            try {
                unionPendingFit(repo.importLayer(bytes, fileName, folderId))
            } catch (e: EmptyLayerException) {
                _importFailures.update { it + ImportFailure(fileName, ImportError.EMPTY) }
            } catch (e: CancellationException) {
                throw e                     // annulation du scope : ce n'est pas un fichier fautif
            } catch (e: Exception) {
                // Fichier illisible : on le retient et on laisse les autres imports suivre leur cours.
                // Sans ce catch, l'exception remontait jusqu'au scope du ViewModel et tuait l'application.
                e.printStackTrace()
                _importFailures.update { it + ImportFailure(fileName, ImportError.INVALID) }
            } finally {
                bumpImporting(folderId, -1)
            }
        }

    /** Échecs accumulés depuis le dernier [consumeImportFailures] : l'écran les présente en une fois,
     *  une fois tous les imports du lot terminés. */
    fun consumeImportFailures(): List<ImportFailure> =
        _importFailures.value.also { _importFailures.value = emptyList() }

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

    /** [rootId] et l'ensemble de ses descendants (dossiers) : ce sur quoi porte une action de dossier
     *  (visibilité, couleur, suppression récursive). */
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

    /** Fait apparaître le bouton de localisation sur la carte : la position ne doit pas s'allumer sans que
     *  rien ne le montre. */
    fun setShowGpsButton(show: Boolean) = viewModelScope.launch {
        val s = settings.value ?: return@launch
        if (s.showGpsButton != show) db.settings().upsert(s.copy(showGpsButton = show))
    }

    /** Bouton "i" du bandeau de profil : montre ou cache la legende des pentes. Retenue d'une fois sur
     *  l'autre - c'est un etat d'affichage, pas une preference a reprendre a chaque trace. */
    fun setSlopeLegend(shown: Boolean) = viewModelScope.launch {
        val s = settings.value ?: return@launch
        if (s.profileSlopeLegend != shown) db.settings().upsert(s.copy(profileSlopeLegend = shown))
    }

    /** Active/désactive le relief (tap sur son entrée dans le gestionnaire de couches) : contrairement aux
     *  autres fonds, le relief n'est jamais "sélectionné" comme fond visuel (tuiles DEM brutes illisibles
     *  telles quelles) - tapoter dessus bascule simplement son affichage en overlay sur le fond courant. */
    fun toggleProviderEnabled(id: String) = viewModelScope.launch {
        val p = providers.value.firstOrNull { it.id == id } ?: return@launch
        db.providers().upsert(p.copy(enabled = !p.enabled))
    }

    /** Tap sur le relief dans le gestionnaire : allume ou éteint son ombrage. Ne touche pas au fond DEM
     *  lui-même, dont le `enabled` ne dit que sa présence dans la liste (cf. SettingsEntity.hillshadeOn). */
    fun toggleHillshade() = viewModelScope.launch {
        val s = settings.value ?: return@launch
        db.settings().upsert(s.copy(hillshadeOn = !s.hillshadeOn))
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

    /** Legendes des fonds actuellement affiches, dans l'ordre d'empilement (fond puis overlay du composite).
     *  Vide tant qu'aucun fond affiche n'en declare : le bouton "info" de la carte n'apparait qu'ici. */
    private val _activeLegends = MutableStateFlow<List<String>>(emptyList())
    val activeLegends: StateFlow<List<String>> = _activeLegends.asStateFlow()

    init {
        viewModelScope.launch {
            val styleRelevantSettings = settings.distinctUntilChanged(::sameStyleSettings)
            combine(styleRelevantSettings, providers, composites) { s, p, c -> Triple(s, p, c) }.collectLatest { (s, provs, comps) ->
                _mapStyle.value = s?.let { buildStyle(it, provs, comps) }
                _activeLegends.value = s?.let { displayedProviders(it, provs, comps).mapNotNull { p -> p.legendAsset } }
                    ?: emptyList()
            }
        }
    }

    /** Composite désigné par le fond actif, s'il en est un et qu'il est activé ; null sinon (fond simple). */
    private fun activeComposite(s: SettingsEntity, comps: List<CompositeEntity>): CompositeEntity? =
        compositeIdFromBasemapId(s.defaultBasemapId)?.let { id -> comps.firstOrNull { it.id == id && it.enabled } }

    /** Fonds visuellement présents à l'écran, dans l'ordre d'empilement : le fond, puis l'overlay si le fond
     *  actif est un composite. Le relief en est exclu, n'étant pas un fond en soi (cf. [buildStyle]). Sert au
     *  bouton "info" : la légende proposée est ainsi toujours celle de ce qui est réellement affiché. */
    private fun displayedProviders(s: SettingsEntity, provs: List<ProviderEntity>, comps: List<CompositeEntity>): List<ProviderEntity> {
        val composite = activeComposite(s, comps)
        if (composite != null) {
            val bg = provs.firstOrNull { it.id == composite.backgroundProviderId }
            if (bg != null && bg.type != "DEM") {
                val fg = provs.firstOrNull { it.id == composite.foregroundProviderId }
                return listOfNotNull(bg, fg?.takeIf { it.type != "DEM" })
            }
        }
        return listOfNotNull(provs.firstOrNull { it.id == s.defaultBasemapId && it.type != "DEM" }
            ?: provs.firstOrNull { it.type != "DEM" })
    }

    private suspend fun buildStyle(s: SettingsEntity, provs: List<ProviderEntity>, comps: List<CompositeEntity>): StyleBuilder.Result? {
        // Le relief n'est jamais un fond visuel en soi (tuiles DEM brutes illisibles telles quelles, cf.
        // StyleBuilder) : il n'apparaît que si l'ombrage est allumé (tap dans le gestionnaire de couches)
        // ou si le composite actif l'inclut en overlay.
        //
        // Le `enabled` du fond DEM compte aussi : retiré du gestionnaire, il n'y a plus de quoi éteindre
        // l'ombrage, et le laisser sur la carte le rendrait indélogeable.
        val demProvider = provs.firstOrNull { it.type == "DEM" }
        val toggledDem = demProvider?.takeIf { it.enabled && s.hillshadeOn }
        val composite = activeComposite(s, comps)
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
