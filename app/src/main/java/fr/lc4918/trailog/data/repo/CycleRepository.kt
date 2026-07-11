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
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PointLayerData
import fr.lc4918.trailog.domain.model.PropType
import fr.lc4918.trailog.domain.model.PropValue
import fr.lc4918.trailog.domain.model.SchemaItem
import fr.lc4918.trailog.domain.model.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class CycleRepository(private val ctx: Context) {
    private val db = AppDatabase.get(ctx)
    val folders = db.folders()
    val layers = db.layers()
    val providers = db.providers()
    val composites = db.composites()
    val basemapFolders = db.basemapFolders()
    val settingsFlow = db.settings().flow()

    private val layersDir: File by lazy { File(ctx.filesDir, "layers").apply { mkdirs() } }
    private val imagesDir: File by lazy { File(ctx.filesDir, "images").apply { mkdirs() } }

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

    /** Importe une couche (gpx/kml/kmz/geojson) : peut contenir des points et/ou des traces. */
    suspend fun importLayer(bytes: ByteArray, fileName: String, folderId: Long?): DoubleArray =
        withContext(Dispatchers.IO) {
            val parsedRaw = LayerImporter.parse(bytes, fileName)
            // les champs image détectés qui pointent vers un fichier local (photo de waypoint GPX, cf. §4.3)
            // sont copiés dans le stockage privé de l'app : le chemin d'origine peut disparaître (stockage
            // amovible/temporaire) ou nécessiter une permission qu'on ne conservera pas après l'import.
            val parsed = parsedRaw.copy(points = parsedRaw.points.map { resolveLocalImages(it) })
            val hasLine = parsed.lines.isNotEmpty()
            // stats par segment puis agrégées (somme distance/D+/D-/temps, min/max altitude) : une concaténation
            // globale des segments créerait un "saut" fantôme entre la fin d'un segment et le début du suivant.
            val segmentStats = parsed.lines.map { TrackMath.compute(it) }
            val allTrackPoints = parsed.lines.flatten()

            val schemaKeys = LinkedHashMap<String, PropType>()
            parsed.points.forEach { pt -> pt.props.forEach { (k, v) -> schemaKeys.putIfAbsent(k, typeOf(v)) } }
            val schema = schemaKeys.map { (k, t) -> SchemaItem(k, t) }

            val file = File(layersDir, "layer_${System.currentTimeMillis()}.geojson")
            file.writeText(LayerGeoJson.write(parsed.points, parsed.lines))

            val lons = parsed.points.map { it.lon } + allTrackPoints.map { it.lon }
            val lats = parsed.points.map { it.lat } + allTrackPoints.map { it.lat }
            val color = Palette.pick(db.layers().colorsInFolder(folderId))
            val order = db.layers().maxSort(folderId) + 1
            val w = lons.minOrNull() ?: 0.0; val s = lats.minOrNull() ?: 0.0
            val e = lons.maxOrNull() ?: 0.0; val n = lats.maxOrNull() ?: 0.0
            db.layers().insert(
                LayerEntity(
                    name = parsed.name, folderId = folderId, link = parsed.link,
                    description = parsed.description, source = "import",
                    geometryFile = file.name, color = color, sortOrder = order,
                    schemaJson = LayerGeoJson.writeSchema(schema),
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

    /** Segments de lignes de la couche, chacun avec ses propres points (pour un profil par segment tapé). */
    suspend fun loadTrackLines(layer: LayerEntity): List<List<TrackPoint>> = withContext(Dispatchers.IO) {
        val f = File(layersDir, layer.geometryFile)
        if (!f.exists()) emptyList() else LayerGeoJson.parse(f.readText()).lines
    }

    /** GeoJSON complet (points + lignes) destiné au rendu carte. */
    fun layerGeojsonForMap(layer: LayerEntity): String {
        val f = File(layersDir, layer.geometryFile)
        if (!f.exists()) return "{\"type\":\"FeatureCollection\",\"features\":[]}"
        val g = LayerGeoJson.parse(f.readText())
        return LayerGeoJson.writeForMap(g.points, g.lines)
    }

    fun loadLayer(layer: LayerEntity): PointLayerData {
        val f = File(layersDir, layer.geometryFile)
        val points = if (f.exists()) LayerGeoJson.parse(f.readText()).points.toMutableList() else mutableListOf()
        return PointLayerData(layer.name, LayerGeoJson.parseSchema(layer.schemaJson), points)
    }

    /** Réécrit les points (et le schéma) tout en préservant les lignes existantes du fichier. */
    suspend fun saveFeatures(layer: LayerEntity, data: PointLayerData) =
        withContext(Dispatchers.IO) {
            val f = File(layersDir, layer.geometryFile)
            val existingLines = if (f.exists()) LayerGeoJson.parse(f.readText()).lines else emptyList()
            f.writeText(LayerGeoJson.write(data.features, existingLines))
            db.layers().updateSchema(layer.id, LayerGeoJson.writeSchema(data.schema))
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
