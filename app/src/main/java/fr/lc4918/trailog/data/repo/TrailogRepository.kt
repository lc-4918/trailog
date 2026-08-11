package fr.lc4918.trailog.data.repo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.content.FileProvider
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.AppDatabase
import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.MbtilesSortOrder
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.data.backup.BackupArchive
import fr.lc4918.trailog.data.backup.BackupDir
import fr.lc4918.trailog.data.imp.EmptyLayerException
import fr.lc4918.trailog.data.imp.LayerImporter
import fr.lc4918.trailog.data.imp.ParsedLayer
import fr.lc4918.trailog.elevation.ElevationFiller
import fr.lc4918.trailog.elevation.ElevationServices
import fr.lc4918.trailog.data.seed.Composites
import fr.lc4918.trailog.data.seed.DemoData
import fr.lc4918.trailog.data.seed.Providers
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.routing.GpxWriter
import fr.lc4918.trailog.routing.Valhalla
import fr.lc4918.trailog.map.offline.MbtilesWriter
import fr.lc4918.trailog.map.offline.OfflineDownloadResult
import fr.lc4918.trailog.map.offline.OfflineThumbnails
import fr.lc4918.trailog.map.offline.OfflineTileDownloader
import fr.lc4918.trailog.ui.offline.OfflineDownloadRequest
import fr.lc4918.trailog.domain.geo.TrackEdit
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
        const val SHARE_DIR = "partage"   // cache des fichiers ouverts à une autre application
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

    /**
     * Complète les altitudes que le fichier ne portait pas, quand le réglage l'autorise.
     *
     * Ici et non dans [LayerImporter] : la lecture d'un fichier est un calcul pur, qui n'attend rien du
     * réseau et se vérifie sans lui. Ici et non plus tard non plus - stats, D+/D-, profil précalculé et
     * drapeau `hasZ` sont tous établis dans la foulée, sur les points tels qu'ils sont à cet instant.
     *
     * Un échec (service muet, réseau absent, couverture manquante) rend la couche telle quelle : l'import
     * réussit, sans Z, exactement comme avant que cette fonction existe.
     *
     * [onElevation] encadre la seule partie qui attend le réseau, et elle seule : c'est ce qui permet à
     * l'écran d'annoncer le calcul d'altimétrie pendant qu'il a lieu, et l'import le reste du temps. Il
     * n'est pas appelé quand il n'y a rien à compléter - un fichier déjà pourvu ferait clignoter le
     * libellé pour un travail qui n'aura pas lieu - et sa retombée passe par un `finally` : une exception
     * en cours de calcul laisserait sinon l'écran sur une attente qui n'attend plus rien.
     */
    private suspend fun fillMissingElevation(
        layer: ParsedLayer, onElevation: (Boolean) -> Unit,
    ): ParsedLayer {
        val s = db.settings().get() ?: return layer
        if (!s.fillMissingElevation) return layer
        if (!ElevationFiller.hasHoles(layer.points, layer.lines)) return layer
        onElevation(true)
        val filled = try {
            ElevationFiller.fill(
                layer.points, layer.lines,
                ElevationServices(s.elevationIgnUrl, s.elevationWorldUrl, s.elevationWorldKey),
            )
        } finally {
            onElevation(false)
        }
        return layer.copy(points = filled.points, lines = filled.lines)
    }

    suspend fun ensureSeed() = withContext(Dispatchers.IO) {
        // Les composites suivent le semis des fournisseurs, et ne sont pas testés sur leur propre table :
        // sinon, supprimer le composite semé le ferait revenir au lancement suivant. Ils référencent de
        // toute façon des fournisseurs par id, ils n'ont de sens qu'une fois ceux-ci en base.
        if (db.providers().count() == 0) {
            db.providers().upsertAll(Providers.defaults())
            db.composites().upsertAll(Composites.defaults())
        }
        if (db.settings().get() == null) {
            db.settings().upsert(SettingsEntity(customTitle = ctx.getString(R.string.drawer_default_title)))
        }
        seedDemoIfNeeded()
    }

    /**
     * Pose le jeu de démonstration au tout premier lancement, installation neuve comme mise à jour d'une
     * base existante (la migration 23->24 y ajoute le drapeau à faux).
     *
     * Le drapeau est levé quoi qu'il arrive, y compris si l'import échoue ou si les assets sont absents :
     * un jeu d'exemple qui n'a pas pu s'installer ne vaut pas d'être retenté à chaque lancement, et une
     * exception ici empêcherait l'app de démarrer. Levé AVANT l'import, pour qu'un plantage en cours de
     * route ne laisse pas non plus la porte ouverte à un second essai qui produirait un doublon.
     */
    private suspend fun seedDemoIfNeeded() {
        val s = db.settings().get() ?: return
        if (s.demoSeeded) return
        db.settings().upsert(s.copy(demoSeeded = true))
        runCatching { importDemoLayer() }.onFailure { it.printStackTrace() }
    }

    /** Extrait le GPX de démonstration et ses photos des assets, puis l'importe dans un dossier dédié.
     *  Ne fait rien s'il n'y a pas d'assets de démonstration (build sans jeu d'exemple). */
    private suspend fun importDemoLayer() {
        val assets = ctx.assets
        val files = runCatching { assets.list(DemoData.ASSET_DIR)?.toList() }.getOrNull().orEmpty()
        val gpxName = files.firstOrNull { it.endsWith(".gpx", ignoreCase = true) } ?: return

        val gpxRaw = assets.open("${DemoData.ASSET_DIR}/$gpxName").use { it.readBytes().decodeToString() }
        // Les photos ne peuvent pas être importées depuis les assets : l'import lit des fichiers. On les
        // dépose donc en cache le temps de l'import, qui les recopiera dans le stockage privé définitif.
        val staging = File(ctx.cacheDir, "demo_staging").apply { deleteRecursively(); mkdirs() }
        try {
            DemoData.photoNames(gpxRaw).forEach { name ->
                if (name !in files) return@forEach   // photo citée mais absente des assets : waypoint sans image
                runCatching {
                    assets.open("${DemoData.ASSET_DIR}/$name").use { input ->
                        File(staging, name).outputStream().use { input.copyTo(it) }
                    }
                }
            }
            val gpx = DemoData.rewritePhotoPaths(gpxRaw, staging)
            val folderId = db.folders().insert(FolderEntity(name = ctx.getString(R.string.demo_folder_name)))
            importLayer(gpx.toByteArray(), gpxName, folderId)
        } finally {
            staging.deleteRecursively()
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

    /** Ce qu'une géométrie écrite laisse derrière elle : le nom de son fichier, et tout ce que la ligne en
     *  base doit en retenir. */
    private class WrittenGeometry(
        val fileName: String,
        val computed: ImportComputation,
        val hasLine: Boolean,
        val hasPoints: Boolean,
    )

    /**
     * Écrit les **trois fichiers** d'une couche - géométrie complète, rendu allégé, profil précalculé - et
     * rend de quoi remplir sa ligne en base.
     *
     * Partagé par l'import et par les retouches de trace (couper, fusionner, inverser). Ces trois-là ne
     * sont pas des imports, mais elles produisent exactement la même chose : trois fichiers cohérents entre
     * eux et des statistiques qui les décrivent. Le seul moyen de garantir qu'une trace coupée est aussi
     * bien décrite qu'une trace importée, c'est qu'elle passe par le même code.
     *
     * [fileName] permet de **réécrire par-dessus** une couche existante, ce que fait une retouche : la
     * couche garde son identité, sa couleur et sa place dans l'arborescence.
     */
    private suspend fun writeGeometry(
        points: List<PointFeature>,
        lines: List<List<TrackPoint>>,
        fileName: String = "layer_${System.currentTimeMillis()}.geojson",
    ): WrittenGeometry = withContext(Dispatchers.IO) {
        val simplify = simplifyRender()
        val smoothingM = profileSmoothing()
        // Stats, schéma, GeoJSON (source + rendu) et profil = CPU pur -> un seul passage sur
        // Dispatchers.Default (les écritures fichier et la lecture des réglages restent sur IO).
        val computed = withContext(Dispatchers.Default) {
            // stats par segment puis agrégées (somme distance/D+/D-/temps, min/max altitude) : une
            // concaténation globale des segments créerait un "saut" fantôme entre la fin d'un segment
            // et le début du suivant.
            val segmentStats = lines.map { TrackMath.compute(it) }
            val allTrackPoints = lines.flatten()

            val schemaKeys = LinkedHashMap<String, PropType>()
            points.forEach { pt -> pt.props.forEach { (k, v) -> schemaKeys.putIfAbsent(k, typeOf(v)) } }
            val schema = schemaKeys.map { (k, t) -> SchemaItem(k, t) }

            val lons = points.map { it.lon } + allTrackPoints.map { it.lon }
            val lats = points.map { it.lat } + allTrackPoints.map { it.lat }
            val bounds = doubleArrayOf(
                lons.minOrNull() ?: 0.0, lats.minOrNull() ?: 0.0,
                lons.maxOrNull() ?: 0.0, lats.maxOrNull() ?: 0.0,
            )

            ImportComputation(
                segmentStats = segmentStats,
                schemaJson = LayerGeoJson.writeSchema(schema),
                geoJson = LayerGeoJson.write(points, lines),
                // GeoJSON prêt-pour-carte précalculé (évite un parse+re-sérialisation coûteux au rendu
                // de grosses traces) : le rendu se contente de relire ce fichier .map.
                mapGeoJson = LayerGeoJson.writeForMap(points, lines, simplify),
                // Profil précalculé par segment (affichage instantané au tap, sans re-parser toute la trace).
                prof = profileJson.encodeToString(computeProfiles(lines, smoothingM)),
                bounds = bounds,
            )
        }
        val file = File(layersDir, fileName)
        file.writeText(computed.geoJson)
        File(layersDir, file.name + MAP_SUFFIX).writeText(computed.mapGeoJson)
        File(layersDir, file.name + PROF_SUFFIX).writeText(computed.prof)
        // Le profil en cache mémoire porte encore celui d'AVANT la retouche : son fichier vient de changer
        // sous lui. La clé de cache tient à la date du fichier, qu'on vient de réécrire, mais l'entrée
        // périmée resterait en mémoire jusqu'à son éviction - on la retire tout de suite.
        profileCache.keys.removeAll { it.startsWith(File(layersDir, file.name + PROF_SUFFIX).absolutePath) }
        WrittenGeometry(file.name, computed, lines.isNotEmpty(), points.isNotEmpty())
    }

    /** La ligne en base décrite par la géométrie qu'on vient d'écrire : mesures, drapeaux et emprise.
     *  Le nom, la couleur, le dossier et l'ordre appartiennent à l'appelant, jamais à la géométrie. */
    private fun LayerEntity.describedBy(g: WrittenGeometry): LayerEntity {
        val stats = g.computed.segmentStats
        val (w, s, e, n) = g.computed.bounds
        return copy(
            geometryFile = g.fileName,
            schemaJson = g.computed.schemaJson,
            distance = stats.sumOf { it.stats.distance },
            ascent = stats.sumOf { it.stats.ascent },
            descent = stats.sumOf { it.stats.descent },
            minEle = stats.filter { it.hasZ }.minOfOrNull { it.stats.min } ?: 0.0,
            maxEle = stats.filter { it.hasZ }.maxOfOrNull { it.stats.max } ?: 0.0,
            movingTime = if (stats.isNotEmpty() && stats.all { it.hasTime })
                stats.sumOf { it.stats.duration ?: 0.0 } else null,
            hasZ = stats.any { it.hasZ }, hasTime = stats.any { it.hasTime },
            hasLine = g.hasLine, hasPoints = g.hasPoints,
            west = w, south = s, east = e, north = n,
        )
    }

    /** Importe une couche (gpx/kml/kmz/geojson) : peut contenir des points et/ou des traces.
     *  Lève [EmptyLayerException] si le fichier, bien que lisible, ne contient ni trace ni point ;
     *  toute autre exception signale un fichier illisible (cf. MainViewModel.importLayer).
     *  [onElevation] encadre le seul temps d'attente des services altimétriques (cf. fillMissingElevation). */
    suspend fun importLayer(
        bytes: ByteArray, fileName: String, folderId: Long?, onElevation: (Boolean) -> Unit = {},
    ): DoubleArray =
        withContext(Dispatchers.IO) {
            val parsedRaw = LayerImporter.parse(bytes, fileName)
            // Rien à importer : une couche vide n'apparaîtrait nulle part sur la carte et polluerait
            // l'arborescence. On refuse avant d'écrire quoi que ce soit en base ou sur le disque.
            if (parsedRaw.lines.isEmpty() && parsedRaw.points.isEmpty()) throw EmptyLayerException(fileName)
            // les champs image détectés qui pointent vers un fichier local (photo de waypoint GPX, cf. section 4.3)
            // sont copiés dans le stockage privé de l'app : le chemin d'origine peut disparaître (stockage
            // amovible/temporaire) ou nécessiter une permission qu'on ne conservera pas après l'import.
            val parsed = fillMissingElevation(
                parsedRaw.copy(points = parsedRaw.points.map { resolveLocalImages(it) }), onElevation,
            )
            val written = writeGeometry(parsed.points, parsed.lines)
            val color = Palette.pick(db.layers().colorsInFolder(folderId))
            val order = db.layers().maxSort(folderId) + 1
            db.layers().insert(
                LayerEntity(
                    name = parsed.name, folderId = folderId, link = parsed.link,
                    description = parsed.description, source = "import",
                    color = color, sortOrder = order, geometryFile = written.fileName,
                ).describedBy(written)
            )
            written.computed.bounds
        }

    private fun typeOf(v: PropValue): PropType = when (v) {
        is PropValue.Link -> PropType.LINK
        is PropValue.Image -> PropType.IMAGE
        is PropValue.Text -> PropType.TEXT
    }

    /**
     * La couche en GPX, prête à être écrite dans le fichier choisi ou envoyée à une autre application.
     *
     * Les octets plutôt qu'un fichier : les deux sorties n'ont pas la même destination - un `content://`
     * choisi par l'utilisateur d'un côté, un fichier de cache partagé de l'autre - et le seul point commun
     * est ce qu'on y écrit.
     */
    suspend fun layerGpx(layer: LayerEntity): ByteArray = withContext(Dispatchers.IO) {
        val f = File(layersDir, layer.geometryFile)
        val geometry = if (f.exists()) {
            val text = f.readText()
            withContext(Dispatchers.Default) { LayerGeoJson.parse(text) }
        } else ParsedLayerGeometry(emptyList(), emptyList())
        withContext(Dispatchers.Default) {
            GpxWriter.writeLayer(layer.name, geometry.points, geometry.lines)
        }
    }

    /**
     * La couche en GeoJSON, telle qu'elle est stockée.
     *
     * Le fichier de géométrie EST déjà du GeoJSON : l'export est donc une copie, et il est le seul à ne
     * rien perdre - liens, champs libres et image de garde n'ont pas de place dans le GPX (cf.
     * [GpxWriter.writeLayer]). Les photos, elles, y figurent par leur chemin dans le stockage de
     * l'application : le fichier seul ne les emporte pas, c'est la sauvegarde qui joue ce rôle.
     */
    suspend fun layerGeoJson(layer: LayerEntity): ByteArray = withContext(Dispatchers.IO) {
        val f = File(layersDir, layer.geometryFile)
        if (f.exists()) f.readBytes() else ByteArray(0)
    }

    /**
     * Écrit [bytes] dans un fichier du cache de partage et rend son URI `content://`.
     *
     * Un `file://` serait refusé par Android depuis la version 7 : une application qui reçoit un fichier
     * n'a aucun droit sur le stockage privé de celle qui l'envoie. C'est le rôle du fournisseur déclaré au
     * manifeste, qui ouvre l'accès à ce seul dossier, et seulement le temps de l'échange.
     *
     * Le dossier est vidé à chaque fois : ces fichiers ne servent qu'au partage en cours, et une trace
     * exportée n'a pas à rester en clair dans le cache après coup.
     */
    suspend fun shareableFile(bytes: ByteArray, fileName: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, SHARE_DIR)
        dir.deleteRecursively(); dir.mkdirs()
        val f = File(dir, fileName)
        f.writeBytes(bytes)
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.partage", f)
    }

    /**
     * Retouche la géométrie d'une couche **en place** : même fichier, même ligne en base, mesures refaites.
     *
     * La couche garde ainsi son identité - sa couleur, son dossier, son rang, sa visibilité, et le profil
     * ouvert dessus reste ouvert. Seule la matière change.
     */
    private suspend fun rewriteLayer(
        layer: LayerEntity, points: List<PointFeature>, lines: List<List<TrackPoint>>,
    ) {
        val written = writeGeometry(points, lines, layer.geometryFile)
        db.layers().update(layer.describedBy(written))
    }

    /** Points et lignes d'une couche, tels que son fichier les porte. */
    private suspend fun geometryOf(layer: LayerEntity): ParsedLayerGeometry = withContext(Dispatchers.IO) {
        val f = File(layersDir, layer.geometryFile)
        if (!f.exists()) return@withContext ParsedLayerGeometry(emptyList(), emptyList())
        val text = f.readText()
        withContext(Dispatchers.Default) { LayerGeoJson.parse(text) }
    }

    /** Inverse le sens de la trace. Les horodatages tombent (cf. [TrackEdit.reverse]). */
    suspend fun reverseLayer(layer: LayerEntity) {
        val g = geometryOf(layer)
        rewriteLayer(layer, g.points, TrackEdit.reverse(g.lines))
    }

    /** Où tombe un tap sur la géométrie d'une couche : segment touché et point exact (cf. [TrackEdit.locate]). */
    suspend fun locateOnLayer(layer: LayerEntity, lon: Double, lat: Double): TrackEdit.Hit? {
        val g = geometryOf(layer)
        return withContext(Dispatchers.Default) { TrackEdit.locate(g.lines, lon, lat) }
    }

    /**
     * Coupe la trace **à l'endroit visé**, en insérant le point s'il tombe entre deux sommets.
     *
     * Faux quand la coupe ne donnerait pas deux morceaux parcourables (aux extrémités, donc).
     */
    suspend fun splitLayerAt(layer: LayerEntity, hit: TrackEdit.Hit): Long? {
        val g = geometryOf(layer)
        val (head, tail) = withContext(Dispatchers.Default) { TrackEdit.splitAtHit(g.lines, hit) } ?: return null
        rewriteLayer(layer, g.points, head)
        val written = writeGeometry(emptyList(), tail)
        return db.layers().insert(
            LayerEntity(
                name = ctx.getString(R.string.layer_part_two, layer.name),
                folderId = layer.folderId, source = layer.source,
                color = Palette.pick(db.layers().colorsInFolder(layer.folderId)),
                sortOrder = db.layers().maxSort(layer.folderId) + 1,
                geometryFile = written.fileName, visible = layer.visible,
            ).describedBy(written)
        )
    }

    /** Comment relier deux segments que l'on joint. */
    enum class JoinMode {
        /** Rien entre les deux : leurs extrémités se suivent, la distance compte la corde. */
        STRAIGHT,
        /** Un chemin praticable calculé par le moteur d'itinéraire, dans la discipline réglée. */
        ROUTED,
        /** Aucune jonction : les deux segments se retrouvent dans la même couche, distincts. */
        NONE,
    }

    /**
     * Joint (ou rapproche) deux segments, d'une même couche ou de deux couches différentes.
     *
     * Cas de deux couches : le segment visé dans [other] **rejoint** la couche [layer], et disparaît de la
     * sienne - sans quoi il serait dessiné deux fois et compté deux fois. Une couche qui n'a plus ni
     * segment ni marqueur est supprimée : elle n'aurait plus rien à montrer.
     *
     * En mode [JoinMode.ROUTED], le pont est calculé entre les deux extrémités les plus proches. Si le
     * moteur ne répond pas - hors réseau, deux points qu'aucune voie ne relie - **la jonction se fait en
     * ligne droite** plutôt que d'échouer : l'utilisateur a demandé à joindre, et une droite reste une
     * jonction. Le résultat est rendu à l'appelant, qui peut le dire.
     */
    suspend fun joinSegments(
        layer: LayerEntity, segmentA: Int,
        other: LayerEntity, segmentB: Int,
        mode: JoinMode,
    ): JoinMode {
        // Rien à joindre entre un segment et lui-même : l'appelant l'a déjà refusé, mais une garde ici
        // évite qu'une jonction sur soi ne réécrive la couche avec une géométrie tronquée.
        if (layer.id == other.id && segmentA == segmentB) return mode
        val sameLayer = layer.id == other.id
        val a = geometryOf(layer)
        val b = if (sameLayer) a else geometryOf(other)
        val movedIndex = if (sameLayer) segmentB else a.lines.size
        val lines = if (sameLayer) a.lines else a.lines + listOf(b.lines.getOrNull(segmentB) ?: return mode)
        var applied = mode
        val bridge = if (mode != JoinMode.ROUTED) emptyList() else {
            val computed = routedBridge(lines.getOrNull(segmentA), lines.getOrNull(movedIndex))
            if (computed == null) applied = JoinMode.STRAIGHT
            computed ?: emptyList()
        }
        val joined = when (mode) {
            JoinMode.NONE -> lines
            else -> withContext(Dispatchers.Default) { TrackEdit.join(lines, segmentA, movedIndex, bridge) }
                ?: return mode
        }
        val points = if (sameLayer) a.points else a.points + b.points
        rewriteLayer(layer, points, joined)
        if (!sameLayer) {
            val rest = b.lines.filterIndexed { i, _ -> i != segmentB }
            if (rest.isEmpty()) deleteLayer(other) else rewriteLayer(other, emptyList(), rest)
        }
        return applied
    }

    /**
     * Le chemin praticable entre les deux extrémités les plus proches de deux segments, ou null.
     *
     * Le moteur est interrogé sur les mêmes réglages que le planificateur - même instance, même discipline
     * par défaut : joindre deux morceaux de trace et composer un itinéraire, c'est la même question posée
     * au même service.
     */
    private suspend fun routedBridge(
        first: List<TrackPoint>?, second: List<TrackPoint>?,
    ): List<TrackPoint>? {
        val a = first?.takeIf { it.isNotEmpty() } ?: return null
        val b = second?.takeIf { it.isNotEmpty() } ?: return null
        // Les mêmes quatre combinaisons que la jonction : le pont doit relier les extrémités que
        // TrackEdit.join va effectivement mettre face à face.
        val pairs = listOf(
            a.last() to b.first(), a.last() to b.last(), a.first() to b.first(), a.first() to b.last(),
        )
        val (from, to) = pairs.minBy { (p, q) -> TrackMath.haversine(p.lon, p.lat, q.lon, q.lat) }
        val s = db.settings().get()
        val base = s?.routingUrl?.ifBlank { null } ?: Valhalla.DEFAULT_URL
        val profile = RoutingProfile.of(s?.routingProfile)
        val route = Valhalla.route(base, listOf(from.lat to from.lon, to.lat to to.lon), profile) ?: return null
        // Les deux extrémités sont déjà dans les segments qu'on relie : le pont ne garde que ce qu'il y a
        // entre elles, sans quoi la jointure porterait deux points au même endroit.
        return route.points.drop(1).dropLast(1).ifEmpty { null }
    }

    /**
     * Coupe la trace au point le plus proche de (lon, lat) : la couche garde le premier morceau, le second
     * devient une couche voisine.
     *
     * Faux quand la coupe ne donnerait pas deux morceaux parcourables (cf. [TrackEdit.splitAt]) - au tout
     * début ou à la toute fin de la trace, donc.
     *
     * **Les marqueurs restent avec la couche d'origine.** Les répartir supposerait de décider à quel
     * morceau appartient un point posé à cent mètres de la coupe, ce qu'aucune règle ne dit ; les laisser
     * ensemble est au moins prévisible, et un déplacement reste possible à la main.
     */
    suspend fun splitLayer(layer: LayerEntity, lon: Double, lat: Double): Boolean {
        val g = geometryOf(layer)
        val (segment, index) = TrackEdit.nearest(g.lines, lon, lat) ?: return false
        val (head, tail) = TrackEdit.splitAt(g.lines, segment, index) ?: return false
        rewriteLayer(layer, g.points, head)
        val written = writeGeometry(emptyList(), tail)
        db.layers().insert(
            LayerEntity(
                name = ctx.getString(R.string.layer_part_two, layer.name),
                folderId = layer.folderId, source = layer.source,
                // Une couleur distincte de celles du dossier : deux morceaux d'une même trace se suivent
                // sur la carte, et rien ne dirait où l'un finit s'ils partageaient sa couleur.
                color = Palette.pick(db.layers().colorsInFolder(layer.folderId)),
                sortOrder = db.layers().maxSort(layer.folderId) + 1,
                geometryFile = written.fileName, visible = layer.visible,
            ).describedBy(written)
        )
        return true
    }

    /** Fusionne [other] dans [layer], puis supprime [other] : ses segments viennent à la suite. */
    suspend fun mergeLayers(layer: LayerEntity, other: LayerEntity) {
        val a = geometryOf(layer)
        val b = geometryOf(other)
        rewriteLayer(layer, a.points + b.points, TrackEdit.merge(a.lines, b.lines))
        deleteLayer(other)
    }

    /** Les dossiers que la sauvegarde emporte : les géométries et les photos de waypoints. */
    private fun backupDirs() = listOf(
        BackupDir("layers", layersDir),
        BackupDir("images", imagesDir),
    )

    /** Fichier de la base, tel que Room l'a ouvert. */
    private fun databaseFile(): File = ctx.getDatabasePath("trailog.db")

    /**
     * Écrit la sauvegarde complète dans [out].
     *
     * Le **point de contrôle** du journal est indispensable : Room écrit en mode WAL, où les dernières
     * transactions vivent dans un fichier `-wal` à côté de la base. Copier la base sans le replier
     * d'abord donnerait une sauvegarde amputée de tout ce qui a été fait depuis le dernier repli - la
     * dernière trace importée, typiquement.
     */
    suspend fun writeBackup(out: java.io.OutputStream) = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        BackupArchive.create(out, databaseFile(), backupDirs(), System.currentTimeMillis())
    }

    /**
     * Relit une sauvegarde et remplace tout ce qui est en place.
     *
     * L'application doit **redémarrer** derrière : la base restaurée n'est pas celle que Room a ouverte,
     * et tout ce qui vit en mémoire - flux, caches, profils décodés - décrit encore l'ancienne. C'est
     * l'appelant qui s'en charge (cf. l'écran des réglages), une fois le résultat connu.
     */
    suspend fun restoreBackup(input: InputStream): BackupArchive.Result = withContext(Dispatchers.IO) {
        profileCache.clear()
        BackupArchive.restore(input, databaseFile(), backupDirs(), File(ctx.cacheDir, "restauration"))
    }

    /** Une couche telle qu'elle était avant une retouche : sa ligne en base, et sa géométrie. */
    class LayerSnapshot(
        val layer: LayerEntity,
        val points: List<PointFeature>,
        val lines: List<List<TrackPoint>>,
    )

    /** Photographie une couche avant de la retoucher (cf. [restoreLayers]). */
    suspend fun snapshotLayer(layer: LayerEntity): LayerSnapshot {
        val g = geometryOf(layer)
        return LayerSnapshot(layer, g.points, g.lines)
    }

    /**
     * Remet les couches dans l'état photographié, et supprime celles que la retouche avait créées.
     *
     * Une couche **supprimée** par la retouche est recréée avec son identifiant d'origine : l'insertion
     * remplace sur conflit de clé, si bien qu'une couche annulée retrouve son identité - le profil ouvert
     * dessus, sa place dans l'arborescence, sa couleur. La recréer sous un nouvel identifiant l'aurait
     * fait réapparaître comme une inconnue en fin de liste.
     *
     * Les suppressions d'abord, les restaurations ensuite : l'inverse laisserait un instant deux couches
     * porteuses de la même géométrie.
     */
    suspend fun restoreLayers(snapshots: List<LayerSnapshot>, deleteIds: List<Long>) {
        deleteIds.forEach { id -> db.layers().byId(id)?.let { deleteLayer(it) } }
        snapshots.forEach { s ->
            val written = writeGeometry(s.points, s.lines, s.layer.geometryFile)
            db.layers().insert(s.layer.describedBy(written))
        }
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
                sortOrder = MbtilesSortOrder,
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
                        sortOrder = MbtilesSortOrder,
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
