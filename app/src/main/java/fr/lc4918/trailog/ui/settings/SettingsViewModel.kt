package fr.lc4918.trailog.ui.settings

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.lc4918.trailog.R
import fr.lc4918.trailog.TrailogApp
import fr.lc4918.trailog.data.LocalePrefs
import fr.lc4918.trailog.data.db.AppDatabase
import fr.lc4918.trailog.data.db.CompositeEntity
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.data.seed.Providers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Entrée d'un fournisseur dans le fichier JSON d'import/export (SPEC section 4.2). Distincte de
 *  [ProviderEntity] : n'inclut pas `folderId`/`sortOrder` (placement propre à cette installation,
 *  préservé pour les fournisseurs déjà connus, ajouté en fin de liste pour les nouveaux). */
@Serializable
data class ProviderExportEntry(
    val id: String, val name: String, val groupName: String, val type: String,
    val urlTemplate: String, val apiKey: String? = null, val subdomains: String? = null,
    val minZoom: Int = 0, val maxZoom: Int = 19, val tileSize: Int = 256,
    val attribution: String? = null, val transparent: Boolean = false,
    val enabled: Boolean = true, val builtin: Boolean = true,
)

@Serializable
data class ProvidersExportFile(val version: String = "1.0", val providers: List<ProviderExportEntry> = emptyList())

private val providersJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as TrailogApp).repository
    private val db = AppDatabase.get(app)

    val settings: StateFlow<SettingsEntity?> =
        repo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val providers: StateFlow<List<ProviderEntity>> =
        repo.providers.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val composites: StateFlow<List<CompositeEntity>> =
        repo.composites.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()
    fun clearStatus() { _status.value = null }

    /** Import de fournisseurs en attente de confirmation utilisateur (SPEC section 4.2 : demander
     *  confirmation avant d'écraser des fournisseurs existants). Null = aucun import en attente. */
    private val _pendingProvidersImport = MutableStateFlow<List<ProviderExportEntry>?>(null)
    val pendingProvidersImport = _pendingProvidersImport.asStateFlow()

    fun save(s: SettingsEntity) = viewModelScope.launch { db.settings().upsert(s) }
    fun saveProvider(p: ProviderEntity) = viewModelScope.launch { db.providers().upsert(p) }
    fun deleteProvider(p: ProviderEntity) = viewModelScope.launch { db.providers().delete(p) }
    fun saveComposite(c: CompositeEntity) = viewModelScope.launch { db.composites().upsert(c) }
    fun deleteComposite(c: CompositeEntity) = viewModelScope.launch { db.composites().delete(c) }

    /** Import d'un fichier .mbtiles choisi par l'utilisateur. */
    fun importMbtiles(uri: Uri) = viewModelScope.launch {
        val s = settings.value ?: SettingsEntity()
        val ctx = getApplication<Application>()
        val name = queryDisplayName(uri) ?: "carte.mbtiles"
        try {
            val input = ctx.contentResolver.openInputStream(uri)
                ?: error(ctx.getString(R.string.error_cannot_open_file))
            val prov = repo.importMbtiles(input, name, s)
            _status.value = ctx.getString(R.string.status_imported_format, prov.name, prov.minZoom, prov.maxZoom)
        } catch (e: Exception) {
            _status.value = e.message ?: ctx.getString(R.string.error_mbtiles_import_failed)
        }
    }

    /** Import d'une image d'avatar choisie par l'utilisateur (copiée dans le stockage privé de l'app). */
    fun importAvatarImage(uri: Uri) = viewModelScope.launch {
        val s = settings.value ?: SettingsEntity()
        val ctx = getApplication<Application>()
        val name = queryDisplayName(uri) ?: "avatar"
        val input = ctx.contentResolver.openInputStream(uri) ?: return@launch
        val path = repo.importImage(input, name)
        db.settings().upsert(s.copy(avatarSource = path))
    }

    /** Restaure les paramètres initiaux (SPEC section 3.1) : réglages, avatar, thème, titre, langue et
     *  toggle-slides des fonds de plan intégrés. Préserve en revanche URLs/clés API, fonds composites
     *  créés par l'utilisateur et fichiers MBTiles importés - ces derniers n'ont pas d'équivalent dans
     *  Providers.defaults() donc ne sont jamais touchés par la boucle ci-dessous. */
    fun resetAllSettings() = viewModelScope.launch {
        val ctx = getApplication<Application>()
        db.settings().upsert(SettingsEntity())
        LocalePrefs.set(ctx, "fr")
        val seedEnabled = Providers.defaults().associate { it.id to it.enabled }
        val toReset = providers.value.mapNotNull { p ->
            val defaultEnabled = seedEnabled[p.id] ?: return@mapNotNull null
            if (p.enabled != defaultEnabled) p.copy(enabled = defaultEnabled) else null
        }
        if (toReset.isNotEmpty()) db.providers().upsertAll(toReset)
    }

    /** Exporte tous les fournisseurs (SPEC section 4.2), API keys en clair. */
    fun exportProviders(uri: Uri) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val entries = providers.value.map {
            ProviderExportEntry(it.id, it.name, it.groupName, it.type, it.urlTemplate, it.apiKey,
                it.subdomains, it.minZoom, it.maxZoom, it.tileSize, it.attribution, it.transparent,
                it.enabled, it.builtin)
        }
        val json = providersJson.encodeToString(ProvidersExportFile(providers = entries))
        try {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                ?: error(ctx.getString(R.string.error_cannot_open_file))
        } catch (e: Exception) {
            _status.value = e.message ?: ctx.getString(R.string.error_cannot_open_file)
        }
    }

    /** Lit et parse le fichier JSON choisi ; l'import réel n'a lieu qu'après confirmation
     *  utilisateur via [confirmImportProviders] (SPEC section 4.2). */
    fun requestImportProviders(uri: Uri) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val entries = runCatching {
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error(ctx.getString(R.string.error_cannot_open_file))
            providersJson.decodeFromString<ProvidersExportFile>(text).providers
        }.getOrNull()
        if (entries == null) _status.value = ctx.getString(R.string.error_cannot_open_file)
        else _pendingProvidersImport.value = entries
    }

    fun cancelImportProviders() { _pendingProvidersImport.value = null }

    /** Écrase les fournisseurs de même id (préserve leur folderId/sortOrder actuels) et ajoute
     *  les nouveaux à la fin de la liste. */
    fun confirmImportProviders() = viewModelScope.launch {
        val entries = _pendingProvidersImport.value ?: return@launch
        val current = providers.value.associateBy { it.id }
        var nextSort = (current.values.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val toUpsert = entries.map { e ->
            val existing = current[e.id]
            ProviderEntity(
                id = e.id, name = e.name, groupName = e.groupName, type = e.type, urlTemplate = e.urlTemplate,
                apiKey = e.apiKey, subdomains = e.subdomains, minZoom = e.minZoom, maxZoom = e.maxZoom,
                tileSize = e.tileSize, attribution = e.attribution, transparent = e.transparent,
                enabled = e.enabled, builtin = e.builtin,
                sortOrder = existing?.sortOrder ?: nextSort++, folderId = existing?.folderId,
            )
        }
        db.providers().upsertAll(toUpsert)
        _pendingProvidersImport.value = null
    }

    private fun queryDisplayName(uri: Uri): String? =
        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        }
}
