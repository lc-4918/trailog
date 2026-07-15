package fr.lc4918.trailog.data.repo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.AppDatabase
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.data.imp.LayerImporter
import fr.lc4918.trailog.data.seed.Providers
import fr.lc4918.trailog.map.offline.MbtilesWriter
import fr.lc4918.trailog.map.offline.OfflineDownloadResult
import fr.lc4918.trailog.map.offline.OfflineThumbnails
import fr.lc4918.trailog.map.offline.OfflineTileDownloader
import fr.lc4918.trailog.ui.offline.OfflineDownloadRequest
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PointLayerData
import fr.lc4918.trailog.domain.model.PropType
import fr.lc4918.trailog.domain.model.PropValue
import fr.lc4918.trailog.domain.model.SchemaItem
import fr.lc4918.trailog.domain.model.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream

class TrailogRepository(private val ctx: Context) {
    private val db = AppDatabase.get(ctx)
    val folders = db.folders()
    val layers = db.layers()
    val providers = db.providers()
    val composites = db.composites()
    val basemapFolders = db.basemapFolders()
    val settingsFlow = db.settings().flow()

    private val layersDir: File by lazy { File(ctx.filesDir, "layers").apply { mkdirs() } }
    private val imagesDir: File by lazy { File(ctx.filesDir, "images").apply { mkdirs() } }

    private companion object {
        const val MAP_SUFFIX = ".map"     // fichier GeoJSON prêt-pour-carte précalculé
        const val PROF_SUFFIX = ".prof"   // profil (samples décimés + stats) précalculé par segment
    }

    // Paramètres du profil, identiques au tap comme au précalcul (sinon le .prof ne correspondrait pas).
    private val profileJson = Json { ignoreUnknownKeys = true }

    // Cache mémoire des profils décodés, clé = chemin du .prof + son horodatage (invalidé automatiquement si
    // le fichier est réécrit). Évite de re-décoder 200+ Ko de JSON (2 à 12 s selon la charge/throttling CPU)
    // à chaque re-tap d'une trace déjà consultée. Accès concurrents possibles (IO/Default) -> map
    // synchronisée ; éviction LRU (ordre d'accès) au-delà de 6 traces pour borner la mémoire.
    private val profileCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, List<ComputedTrack>>(8, 0.75f, true) {
            override fun removeEldestEntry(e: MutableMap.MutableEntry<String, List<ComputedTrack>>) = size > 6
        }
    )
    // Lissage de l'altitude avant calcul du profil affiché : moins de bruit -> moins de changements de
    // classe de pente -> moins de segments de couleur à dessiner (cf. ElevationProfile.buildAreaRuns).
    // N'affecte que le profil affiché, jamais segmentStats (distance/D+/D-) qui reste sur l'altitude brute.
    private fun computeProfiles(lines: List<List<TrackPoint>>, smoothingM: Double): List<ComputedTrack> =
        lines.map { TrackMath.compute(it, smoothingM = smoothingM, ignoreStops = true, stopSpeed = 0.5) }

    /** État courant du réglage de simplification du rendu (défaut activé si les réglages ne sont pas encore lus). */
    private suspend fun simplifyRender(): Boolean = db.settings().get()?.simplifyRender ?: true

    /** Lissage du profil (m), réglage utilisateur (5 m par défaut si non lu). */
    private suspend fun profileSmoothing(): Double = (db.settings().get()?.profileSmoothingM ?: 5).toDouble()

    suspend fun ensureSeed() = withContext(Dispatchers.IO) {
        if (db.providers().count() == 0) db.providers().upsertAll(Providers.defaults())
        if (db.settings().get() == null) {
            db.settings().upsert(SettingsEntity(customTitle = ctx.getString(R.string.drawer_default_title)))
        }
    }

    /** Dossier MBTiles effectif (chemin réel). Vide -> dossier privé de l'app. */
    fun mbtilesDir(settings: SettingsEntity): File {
        val d = if (settings.mbtilesDir.isBlank()) File(ctx.filesDir, "mbtiles") else File(settings.mbtilesDir)
        d.mkdirs(); return d
    }

    /** Résultat des calculs CPU purs de l'import (stats, schéma, GeoJSON, profil, bornes), regroupés en un
     *  seul passage sur Dispatchers.Default (cf. importLayer). */
    private data class ImportComputation(
        val segmentStats: List<ComputedTrack>,
        val schemaJson: String,
        val geoJson: String,
        val mapGeoJson: String,
        val prof: String,
        val bounds: DoubleArray,
    )

    /** Importe une couche (gpx/kml/kmz/geojson) : peut contenir des points et/ou des traces. */
    suspend fun importLayer(bytes: ByteArray, fileName: String, folderId: Long?): DoubleArray =
        withContext(Dispatchers.IO) {
            val parsedRaw = LayerImporter.parse(bytes, fileName)
            // les champs image détectés qui pointent vers un fichier local (photo de waypoint GPX, cf. section 4.3)
            // sont copiés dans le stockage privé de l'app : le chemin d'origine peut disparaître (stockage
            // amovible/temporaire) ou nécessiter une permission qu'on ne conservera pas après l'import.
            val parsed = parsedRaw.copy(points = parsedRaw.points.map { resolveLocalImages(it) })
            val hasLine = parsed.lines.isNotEmpty()
            val simplify = simplifyRender()
            val smoothingM = profileSmoothing()

            // Stats, schéma, GeoJSON (source + rendu) et profil = CPU pur -> un seul passage sur
            // Dispatchers.Default (les écritures fichier et la lecture des réglages restent sur IO).
            val computed = withContext(Dispatchers.Default) {
                // stats par segment puis agrégées (somme distance/D+/D-/temps, min/max altitude) : une
                // concaténation globale des segments créerait un "saut" fantôme entre la fin d'un segment
                // et le début du suivant.
                val segmentStats = parsed.lines.map { TrackMath.compute(it) }
                val allTrackPoints = parsed.lines.flatten()

                val schemaKeys = LinkedHashMap<String, PropType>()
                parsed.points.forEach { pt -> pt.props.forEach { (k, v) -> schemaKeys.putIfAbsent(k, typeOf(v)) } }
                val schema = schemaKeys.map { (k, t) -> SchemaItem(k, t) }

                val lons = parsed.points.map { it.lon } + allTrackPoints.map { it.lon }
                val lats = parsed.points.map { it.lat } + allTrackPoints.map { it.lat }
                val bounds = doubleArrayOf(
                    lons.minOrNull() ?: 0.0, lats.minOrNull() ?: 0.0,
                    lons.maxOrNull() ?: 0.0, lats.maxOrNull() ?: 0.0,
                )

                ImportComputation(
                    segmentStats = segmentStats,
                    schemaJson = LayerGeoJson.writeSchema(schema),
                    geoJson = LayerGeoJson.write(parsed.points, parsed.lines),
                    // GeoJSON prêt-pour-carte précalculé (évite un parse+re-sérialisation coûteux au rendu
                    // de grosses traces) : le rendu se contente de relire ce fichier .map.
                    mapGeoJson = LayerGeoJson.writeForMap(parsed.points, parsed.lines, simplify),
                    // Profil précalculé par segment (affichage instantané au tap, sans re-parser toute la trace).
                    prof = profileJson.encodeToString(computeProfiles(parsed.lines, smoothingM)),
                    bounds = bounds,
                )
            }

            val file = File(layersDir, "layer_${System.currentTimeMillis()}.geojson")
            file.writeText(computed.geoJson)
            File(layersDir, file.name + MAP_SUFFIX).writeText(computed.mapGeoJson)
            File(layersDir, file.name + PROF_SUFFIX).writeText(computed.prof)

            val (w, s, e, n) = computed.bounds
            val segmentStats = computed.segmentStats
            val color = Palette.pick(db.layers().colorsInFolder(folderId))
            val order = db.layers().maxSort(folderId) + 1
            db.layers().insert(
                LayerEntity(
                    name = parsed.name, folderId = folderId, link = parsed.link,
                    description = parsed.description, source = "import",
                    geometryFile = file.name, color = color, sortOrder = order,
                    schemaJson = computed.schemaJson,
                    distance = segmentStats.sumOf { it.stats.distance },
                    ascent = segmentStats.sumOf { it.stats.ascent },
                    descent = segmentStats.sumOf { it.stats.descent },
                    minEle = segmentStats.filter { it.hasZ }.minOfOrNull { it.stats.min } ?: 0.0,
                    maxEle = segmentStats.filter { it.hasZ }.maxOfOrNull { it.stats.max } ?: 0.0,
                    movingTime = if (segmentStats.isNotEmpty() && segmentStats.all { it.hasTime })
                        segmentStats.sumOf { it.stats.duration ?: 0.0 } else null,
                    hasZ = segmentStats.any { it.hasZ }, hasTime = segmentStats.any { it.hasTime },
                    hasLine = hasLine, hasPoints = parsed.points.isNotEmpty(),
                    west = w, south = s, east = e, north = n,
                )
            )
            doubleArrayOf(w, s, e, n)
        }

    private fun typeOf(v: PropValue): PropType = when (v) {
        is PropValue.Link -> PropType.LINK
        is PropValue.Image -> PropType.IMAGE
        is PropValue.Text -> PropType.TEXT
    }

    /** Supprime la couche (ligne en base + fichier géométrie + .map précalculé). */
    suspend fun deleteLayer(layer: LayerEntity) = withContext(Dispatchers.IO) {
        db.layers().delete(layer)
        File(layersDir, layer.geometryFile).delete()
        File(layersDir, layer.geometryFile + MAP_SUFFIX).delete()
        File(layersDir, layer.geometryFile + PROF_SUFFIX).delete()
    }

    /** Segments de lignes de la couche, chacun avec ses propres points (pour un profil par segment tapé). */
    suspend fun loadTrackLines(layer: LayerEntity): List<List<TrackPoint>> = withContext(Dispatchers.IO) {
        val f = File(layersDir, layer.geometryFile)
        if (!f.exists()) return@withContext emptyList()
        val text = f.readText()
        // Parse JSON = CPU pur -> un seul passage sur Dispatchers.Default.
        withContext(Dispatchers.Default) { LayerGeoJson.parse(text).lines }
    }

    /** Profils précalculés de la couche (un par segment, samples décimés + stats). Lit le .prof s'il existe
     *  (quasi instantané) ; sinon le génère depuis la géométrie complète et le met en cache (couche importée
     *  avant l'introduction du précalcul, ou fichier absent). Évite le parse DOM complet au tap. */
    suspend fun loadProfiles(layer: LayerEntity): List<ComputedTrack> = withContext(Dispatchers.IO) {
        val profFile = File(layersDir, layer.geometryFile + PROF_SUFFIX)
        if (profFile.exists()) {
            val cacheKey = "${profFile.absolutePath}|${profFile.lastModified()}"
            profileCache[cacheKey]?.let { return@withContext it }   // hit mémoire : aucun décodage
            val text = profFile.readText()
            val cached = withContext(Dispatchers.Default) {
                runCatching { profileJson.decodeFromString<List<ComputedTrack>>(text) }.getOrNull()
            }
            if (cached != null) { profileCache[cacheKey] = cached; return@withContext cached }
        }
        val lines = loadTrackLines(layer)
        if (lines.isEmpty()) return@withContext emptyList()
        val smoothingM = profileSmoothing()
        // Calcul + sérialisation = CPU pur -> isolés en un seul passage sur Dispatchers.Default (seul le
        // writeText reste sur IO).
        val (profiles, prof) = withContext(Dispatchers.Default) {
            val p = computeProfiles(lines, smoothingM); p to profileJson.encodeToString(p)
        }
        runCatching { profFile.writeText(prof) }   // cache disque pour les prochains démarrages
        profileCache["${profFile.absolutePath}|${profFile.lastModified()}"] = profiles   // + cache mémoire
        profiles
    }

    /** Fichier .map prêt-pour-carte (GeoJSON précalculé), chargé directement par MapLibre via une URI
     *  file:// (lecture + parse sur son thread de travail). Le génère une fois s'il manque (couche importée
     *  avant l'introduction du précalcul). Null si la couche n'a plus de fichier géométrie (rien à afficher). */
    suspend fun layerMapFile(layer: LayerEntity, simplify: Boolean): File? {
        val mapFile = File(layersDir, layer.geometryFile + MAP_SUFFIX)
        if (mapFile.exists()) return mapFile
        val f = File(layersDir, layer.geometryFile)
        if (!f.exists()) return null
        val text = f.readText()
        // Parse + re-sérialisation = CPU pur -> un seul passage sur Dispatchers.Default.
        val mapGeoJson = withContext(Dispatchers.Default) {
            val g = LayerGeoJson.parse(text)
            LayerGeoJson.writeForMap(g.points, g.lines, simplify)
        }
        return runCatching { mapFile.writeText(mapGeoJson); mapFile }.getOrNull()
    }

    /** Derniers points charges, gardes en memoire. Un tap sur un marqueur relit et re-parse sinon tout le
     *  fichier de couche - traces comprises - pour n'en garder que les waypoints (~280 ms mesures sur une
     *  trace de 2700 points), et ce a chaque tap. Cle = (couche, revision du fichier) : toute reecriture
     *  (saveFeatures, reimport) change la revision et perime l'entree, sans purge explicite a maintenir.
     *  Une seule entree : on ne consulte qu'une infobulle a la fois. */
    private class PointCache(val layerId: Long, val revision: Long, val data: PointLayerData)
    @Volatile private var pointCache: PointCache? = null

    /** Horodatage ^ taille : change des que le fichier est reecrit (meme convention que le .map du rendu). */
    private fun fileRevision(f: File): Long = if (f.exists()) f.lastModified() xor (f.length() * 1000003L) else 0L

    /** Copie a rendre a l'appelant : saveFeature remplace un element de `features` en place, ce qui
     *  corromprait l'instance en cache. PointFeature/SchemaItem sont immuables -> copier les listes suffit. */
    private fun PointLayerData.detached() = PointLayerData(name, schema.toMutableList(), features.toMutableList())

    suspend fun loadLayer(layer: LayerEntity): PointLayerData = withContext(Dispatchers.IO) {
        val f = File(layersDir, layer.geometryFile)
        val revision = fileRevision(f)
        pointCache?.let { c -> if (c.layerId == layer.id && c.revision == revision) return@withContext c.data.detached() }
        val text = if (f.exists()) f.readText() else null
        // Parse JSON = CPU pur -> un seul passage sur Dispatchers.Default.
        val points = withContext(Dispatchers.Default) {
            text?.let { LayerGeoJson.parse(it).points.toMutableList() } ?: mutableListOf()
        }
        val data = PointLayerData(layer.name, LayerGeoJson.parseSchema(layer.schemaJson), points)
        pointCache = PointCache(layer.id, revision, data.detached())
        data
    }

    /** Réécrit les points (et le schéma) tout en préservant les lignes existantes du fichier. */
    suspend fun saveFeatures(layer: LayerEntity, data: PointLayerData) =
        withContext(Dispatchers.IO) {
            val f = File(layersDir, layer.geometryFile)
            val existingText = if (f.exists()) f.readText() else null
            val simplify = simplifyRender()
            // Parse de l'existant + ré-écriture GeoJSON (source + rendu) = CPU pur -> un seul passage sur
            // Dispatchers.Default (les écritures fichier restent sur IO).
            val (geoJson, mapGeoJson) = withContext(Dispatchers.Default) {
                val existingLines = existingText?.let { LayerGeoJson.parse(it).lines } ?: emptyList()
                LayerGeoJson.write(data.features, existingLines) to
                    LayerGeoJson.writeForMap(data.features, existingLines, simplify)
            }
            f.writeText(geoJson)
            // Régénère le .map précalculé (les points ont changé).
            File(layersDir, layer.geometryFile + MAP_SUFFIX).writeText(mapGeoJson)
            db.layers().updateSchema(layer.id, LayerGeoJson.writeSchema(data.schema))
            // On vient d'ecrire le fichier : on connait deja son contenu, autant garder le cache chaud plutot
            // que de le laisser perimer et faire re-parser le prochain tap. Revision lue APRES l'ecriture.
            pointCache = PointCache(layer.id, fileRevision(f), data.detached())
        }

    suspend fun importImage(input: java.io.InputStream, displayName: String): String =
        withContext(Dispatchers.IO) {
            val safe = displayName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            val name = "img_${System.currentTimeMillis()}_$safe"
            val file = File(imagesDir, name)
            input.use { ins -> file.outputStream().use { ins.copyTo(it) } }
            file.absolutePath
        }

    /** Copie dans le stockage privé de l'app les champs image d'un point qui référencent un fichier local
     *  existant (ex. photo de waypoint GPX) ; les URL distantes (http/https) sont laissées telles quelles.
     *  Un chemin local caduc (fichier absent) est conservé tel quel : l'infobulle affichera "image introuvable". */
    private suspend fun resolveLocalImages(f: PointFeature): PointFeature {
        var changed = false
        val newProps = LinkedHashMap(f.props)
        f.props.forEach { (k, v) ->
            if (v is PropValue.Image && v.path.isNotBlank() &&
                !v.path.startsWith("http://") && !v.path.startsWith("https://")) {
                val srcFile = File(v.path.removePrefix("file://"))
                if (srcFile.isFile) {
                    runCatching { srcFile.inputStream().use { importImage(it, srcFile.name) } }
                        .onSuccess { newPath -> newProps[k] = PropValue.Image(newPath); changed = true }
                }
            }
        }
        return if (changed) f.copy(props = newProps) else f
    }

    /**
     * Importe un fichier MBTiles : copie vers le dossier réel (mbtiles:// l'exige),
     * lit les métadonnées SQLite, et enregistre un provider de type MBTILES.
     * @throws IllegalStateException si le MBTiles est vectoriel (pbf), non géré en v1.
     */
    suspend fun importMbtiles(input: InputStream, displayName: String, settings: SettingsEntity): ProviderEntity =
        withContext(Dispatchers.IO) {
            val dir = mbtilesDir(settings)
            val base = sanitize(displayName).ifBlank { "carte_${System.currentTimeMillis()}" }
            val safe = if (base.endsWith(".mbtiles", true)) base else "$base.mbtiles"
            val file = uniqueFile(dir, safe)
            input.use { ins -> file.outputStream().use { ins.copyTo(it) } }

            val meta = readMbtilesMeta(file)
            val format = (meta["format"] ?: "png").lowercase()
            if (format == "pbf" || meta.containsKey("json")) {
                file.delete()
                throw IllegalStateException(ctx.getString(R.string.error_mbtiles_vector_unsupported, file.name))
            }
            val prov = ProviderEntity(
                id = "mb_${System.currentTimeMillis()}",
                name = meta["name"]?.takeIf { it.isNotBlank() } ?: safe.removeSuffix(".mbtiles"),
                groupName = "Local",
                type = "MBTILES",
                urlTemplate = file.name,                // résolu via mbtilesDir dans StyleBuilder
                minZoom = meta["minzoom"]?.toIntOrNull() ?: 0,
                maxZoom = meta["maxzoom"]?.toIntOrNull() ?: 19,
                tileSize = 256,                         // ajustable ensuite dans l'éditeur de providers
                attribution = meta["attribution"],
                transparent = false,
                builtin = false,
                sortOrder = 1000,
            )
            db.providers().upsert(prov)
            prov
        }

    /**
     * Télécharge une zone hors-ligne (SPEC offline_map.md section 4-5) : écrit un MBTiles dans le dossier
     * réel, puis - en cas de succès - enregistre un provider MBTILES (comme un import). En cas d'arrêt
     * sur erreur ou d'annulation, le fichier partiel est supprimé et aucun provider n'est créé.
     */
    suspend fun downloadOfflineMap(
        provider: ProviderEntity,
        req: OfflineDownloadRequest,
        settings: SettingsEntity,
        onProgress: (done: Int, failed: Int) -> Unit,
    ): OfflineDownloadResult = withContext(Dispatchers.IO) {
        val dir = mbtilesDir(settings)
        val base = sanitize(req.name).ifBlank { "carte_${System.currentTimeMillis()}" }
        val file = uniqueFile(dir, "$base.mbtiles")
        // Le moteur possède le cycle de vie du writer (contrainte de thread SQLite) ; le dépôt ne
        // décide que du sort du fichier et de l'enregistrement du provider selon l'issue.
        val writer = MbtilesWriter(file)
        try {
            when (val outcome = OfflineTileDownloader(provider).download(req, writer, onProgress)) {
                is OfflineTileDownloader.Outcome.Failed -> {
                    file.delete()
                    OfflineDownloadResult.Failed(outcome.failed)
                }
                is OfflineTileDownloader.Outcome.Success -> {
                    val prov = ProviderEntity(
                        id = "mb_${System.currentTimeMillis()}",
                        name = req.name.ifBlank { base },
                        groupName = "Local",
                        type = "MBTILES",
                        urlTemplate = file.name,               // résolu via mbtilesDir dans StyleBuilder
                        minZoom = req.minZoom,
                        maxZoom = req.maxZoom,
                        tileSize = 256,
                        attribution = provider.attribution,
                        transparent = false,
                        builtin = false,
                        sortOrder = 1000,
                    )
                    db.providers().upsert(prov)
                    // Miniatures (SPEC section 6) : best-effort et bornées, elles n'empêchent jamais le succès
                    // ni ne le retardent au-delà de 30 s si le réseau se dégrade juste après le DL.
                    runCatching {
                        withTimeoutOrNull(30_000) {
                            OfflineThumbnails.generate(ctx, provider, req.bbox, req.minZoom, req.maxZoom, file.name)
                        }
                    }
                    OfflineDownloadResult.Success(prov.id)
                }
            }
        } catch (t: Throwable) {
            // Annulation (coroutine) comme erreur inattendue : pas de MBTiles partiel orphelin.
            file.delete()
            throw t
        }
    }

    private fun readMbtilesMeta(file: File): Map<String, String> {
        val out = HashMap<String, String>()
        try {
            val sdb = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            sdb.rawQuery("SELECT name, value FROM metadata", null).use { c ->
                while (c.moveToNext()) out[c.getString(0)] = c.getString(1) ?: ""
            }
            sdb.close()
        } catch (e: Exception) { /* fichier non lisible : on garde les valeurs par défaut */ }
        return out
    }

    private fun sanitize(s: String): String =
        s.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val stem = name.removeSuffix(".mbtiles"); var i = 1
        while (f.exists()) { f = File(dir, "${stem}_$i.mbtiles"); i++ }
        return f
    }

    object Palette {
        private val colors = listOf("#1F6FB2","#E8590C","#2F9E44","#9C36B5","#1098AD","#F08C00","#C2255C","#5C7CFA")
        fun pick(used: List<String>): String = colors.firstOrNull { it !in used } ?: colors[used.size % colors.size]
    }
}
