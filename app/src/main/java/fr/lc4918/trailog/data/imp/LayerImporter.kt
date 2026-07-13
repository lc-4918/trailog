package fr.lc4918.trailog.data.imp

import android.util.Xml
import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PropValue
import fr.lc4918.trailog.domain.model.TrackPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipInputStream

data class ParsedLayer(
    val name: String,
    val link: String?,
    val description: String?,
    val points: List<PointFeature>,
    val lines: List<List<TrackPoint>>,
)

/** Parse GPX / KML / KMZ / GeoJSON vers une couche interne : points ET lignes lus dans la même passe
 *  (un fichier peut mélanger waypoints et traces - on ne garde plus qu'un seul type selon l'importeur choisi). */
object LayerImporter {

    fun parse(bytes: ByteArray, fileName: String): ParsedLayer {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "kmz") {
            val kml = extractKmlFromKmz(bytes) ?: error("Aucun fichier .kml trouvé dans le KMZ")
            return parseKml(kml.inputStream(), fileName)
        }
        val text = bytes.toString(Charsets.UTF_8)
        return when {
            ext == "gpx" || (ext == "xml" && text.contains("<gpx", true)) -> parseGpx(bytes.inputStream(), fileName)
            ext == "kml" || (ext == "xml" && text.contains("<kml", true)) -> parseKml(bytes.inputStream(), fileName)
            else -> parseGeoJson(text, fileName)
        }
    }

    /** KMZ = zip contenant un .kml (souvent doc.kml, mais pas garanti par la spec : on prend le premier trouvé). */
    private fun extractKmlFromKmz(bytes: ByteArray): ByteArray? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.lowercase().endsWith(".kml")) return zip.readBytes()
                e = zip.nextEntry
            }
        }
        return null
    }

    private fun isoToMs(s: String?): Long? = s?.let {
        try { Instant.parse(it).toEpochMilli() } catch (e: Exception) { null }
    }

    // ---------------- GeoJSON ----------------
    private fun parseGeoJson(text: String, fileName: String): ParsedLayer {
        val root = Json.parseToJsonElement(text).jsonObject
        val features = when (root["type"]?.jsonPrimitive?.contentOrNull) {
            "FeatureCollection" -> root["features"]?.jsonArray ?: JsonArray(emptyList())
            "Feature" -> JsonArray(listOf(root))
            else -> JsonArray(emptyList())
        }
        val points = ArrayList<PointFeature>(); var idx = 0
        val lines = ArrayList<List<TrackPoint>>()
        var name: String? = null; var link: String? = null; var desc: String? = null

        fun addPoint(lon: Double, lat: Double, props: JsonObject?) {
            val p = LinkedHashMap<String, PropValue>()
            props?.forEach { (k, v) -> toPropValue(v)?.let { p[k] = it } }
            points.add(PointFeature("p${idx++}", lon, lat, p))
        }

        features.forEach { f ->
            val fo = f.jsonObject
            val geom = fo["geometry"]?.jsonObject ?: return@forEach
            val props = fo["properties"]?.jsonObject
            when (geom["type"]?.jsonPrimitive?.contentOrNull) {
                "Point" -> {
                    val c = geom["coordinates"]?.jsonArray ?: return@forEach
                    val lon = c.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@forEach
                    val lat = c.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@forEach
                    addPoint(lon, lat, props)
                }
                "MultiPoint" -> geom["coordinates"]?.jsonArray?.forEach { pt ->
                    val c = pt.jsonArray
                    val lon = c.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@forEach
                    val lat = c.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@forEach
                    addPoint(lon, lat, props)
                }
                "LineString" -> {
                    lines.add(flatCoords(geom["coordinates"]?.jsonArray ?: JsonArray(emptyList()), props))
                    if (name == null) name = props?.get("name")?.jsonPrimitive?.contentOrNull
                    if (link == null) link = props?.get("link")?.jsonPrimitive?.contentOrNull
                    if (desc == null) desc = props?.get("desc")?.jsonPrimitive?.contentOrNull
                        ?: props?.get("description")?.jsonPrimitive?.contentOrNull
                }
                "MultiLineString" -> (geom["coordinates"]?.jsonArray ?: JsonArray(emptyList())).forEach { seg ->
                    lines.add(flatCoords(seg.jsonArray, null))
                }
            }
        }
        return ParsedLayer(name ?: fileName.substringBeforeLast('.'), link, desc, points, lines)
    }

    private fun flatCoords(coords: JsonArray, props: JsonObject?): List<TrackPoint> {
        val times = (props?.get("coordTimes") as? JsonArray)?.map { isoToMs(it.jsonPrimitive.contentOrNull) }
        return coords.mapIndexed { i, p ->
            val a = p.jsonArray
            TrackPoint(
                lon = a.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: 0.0,
                lat = a.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: 0.0,
                ele = a.getOrNull(2)?.jsonPrimitive?.doubleOrNull,
                timeMs = times?.getOrNull(i),
            )
        }
    }

    /** Convertit une valeur GeoJSON en PropValue ; gère les objets typés {"__t":"link"|"image"}. */
    private fun toPropValue(v: JsonElement): PropValue? {
        if (v is JsonObject) {
            return when (v["__t"]?.jsonPrimitive?.contentOrNull) {
                "link" -> PropValue.Link(
                    v["text"]?.jsonPrimitive?.contentOrNull ?: "",
                    v["url"]?.jsonPrimitive?.contentOrNull ?: "")
                "image" -> PropValue.Image(v["path"]?.jsonPrimitive?.contentOrNull ?: "")
                else -> null
            }
        }
        val s = (v as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return null
        return if (s.isBlank()) null else detectPropValue(s)
    }

    // ---------------- GPX ----------------
    /** Dossier par défaut d'OruxMaps pour les photos référencées par nom seul (chemin relatif, cf. section 4.3). */
    private const val ORUXMAPS_PHOTOS_DIR = "/sdcard/oruxmaps/waypoints/"

    private fun parseGpx(input: InputStream, fileName: String): ParsedLayer {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        val points = ArrayList<PointFeature>(); var pIdx = 0
        val lines = ArrayList<List<TrackPoint>>()
        var curTrack: ArrayList<TrackPoint>? = null
        var trackName: String? = null
        var wptProps = LinkedHashMap<String, PropValue>()
        val photoUrls = ArrayList<String>()   // photos du waypoint courant, dans l'ordre du XML (1ère = image de garde)
        var lat = 0.0; var lon = 0.0; var ele: Double? = null; var timeMs: Long? = null
        var inWpt = false; var inTrkPt = false
        var text = ""
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "wpt" -> {
                        inWpt = true; wptProps = LinkedHashMap(); photoUrls.clear()
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                    }
                    "trk", "rte" -> curTrack = ArrayList()
                    "trkpt", "rtept" -> {
                        inTrkPt = true; ele = null; timeMs = null
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                    }
                    // Garmin / GPX standard : <link href="..."/> vers une photo (élément vide, attribut lu ici)
                    "link" -> if (inWpt) {
                        parser.getAttributeValue(null, "href")?.let { href -> if (looksLikeImagePath(href)) photoUrls.add(href) }
                    }
                    // Locus : <locus:attachment><locus:photo filename=".." path=".."/></locus:attachment> (élément vide)
                    "locus:photo" -> if (inWpt) {
                        val filename = parser.getAttributeValue(null, "filename")
                        val path = parser.getAttributeValue(null, "path") ?: ""
                        if (!filename.isNullOrBlank()) photoUrls.add(path + filename)
                    }
                }
                XmlPullParser.TEXT -> text = parser.text
                XmlPullParser.END_TAG -> when (parser.name) {
                    "ele" -> if (inTrkPt) ele = text.trim().toDoubleOrNull() else if (inWpt) wptProps["ele"] = PropValue.Text(text.trim())
                    "time" -> if (inTrkPt) timeMs = isoToMs(text.trim())
                    "name" -> if (inWpt) wptProps["name"] = PropValue.Text(text.trim()) else if (trackName == null) trackName = text.trim()
                    "desc", "cmt", "sym", "type" -> if (inWpt && text.isNotBlank()) wptProps[parser.name] = detectPropValue(text.trim())
                    // OsmAnd : <osmand:photo>chemin absolu</osmand:photo>
                    "osmand:photo" -> if (inWpt && text.isNotBlank()) photoUrls.add(text.trim())
                    // OruxMaps : <orux:photo>nom.jpg</orux:photo> - chemin RELATIF, à reconstituer
                    "orux:photo" -> if (inWpt && text.isNotBlank()) photoUrls.add(ORUXMAPS_PHOTOS_DIR + text.trim())
                    // Komoot : <komoot:photo>url distante</komoot:photo>
                    "komoot:photo" -> if (inWpt && text.isNotBlank()) photoUrls.add(text.trim())
                    // GPX standard générique : <extensions><photo>chemin ou URL</photo></extensions>
                    "photo" -> if (inWpt && text.isNotBlank()) photoUrls.add(text.trim())
                    "wpt" -> {
                        photoUrls.forEachIndexed { i, url -> wptProps["image_${i + 1}"] = PropValue.Image(url) }
                        val pin = if (photoUrls.isNotEmpty()) "image_1" else null
                        points.add(PointFeature("p${pIdx++}", lon, lat, wptProps, pin))
                        inWpt = false
                    }
                    "trkpt", "rtept" -> { curTrack?.add(TrackPoint(lon, lat, ele, timeMs)); inTrkPt = false }
                    "trk", "rte" -> { curTrack?.let { if (it.isNotEmpty()) lines.add(it) }; curTrack = null }
                }
            }
            ev = parser.next()
        }
        return ParsedLayer(trackName ?: fileName.substringBeforeLast('.'), null, null, points, lines)
    }

    // ---------------- KML ----------------
    /** Nom de dossier généré par l'assistant d'import GPS de Google Earth pour dupliquer chaque
     *  point de trace en Placemark individuel (analyse vitesse/altitude), à côté d'un Placemark
     *  "Path" qui porte déjà la trace complète en LineString : ces points font doublon avec la
     *  trace et ne sont jamais de vrais waypoints (ceux-ci vont dans un dossier "Waypoints" séparé). */
    private const val KML_REDUNDANT_TRACK_POINTS_FOLDER = "Points"

    private fun parseKml(input: InputStream, fileName: String): ParsedLayer {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        val points = ArrayList<PointFeature>(); var pIdx = 0
        val lines = ArrayList<List<TrackPoint>>()
        var docName: String? = null
        var inPlacemark = false
        val folderStack = ArrayList<String?>()
        var placemarkProps = LinkedHashMap<String, PropValue>()
        var placemarkPoint: Pair<Double, Double>? = null
        var placemarkLine: List<TrackPoint>? = null
        var dataName: String? = null
        var text = ""
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "Placemark" -> { inPlacemark = true; placemarkProps = LinkedHashMap(); placemarkPoint = null; placemarkLine = null }
                    "Folder" -> folderStack.add(null)
                    "Data" -> dataName = parser.getAttributeValue(null, "name")
                }
                XmlPullParser.TEXT -> text = parser.text
                XmlPullParser.END_TAG -> when (parser.name) {
                    "name" -> when {
                        inPlacemark -> placemarkProps["name"] = PropValue.Text(text.trim())
                        folderStack.isNotEmpty() && folderStack.last() == null -> folderStack[folderStack.size - 1] = text.trim()
                        docName == null -> docName = text.trim()
                    }
                    "description" -> if (inPlacemark && text.isNotBlank()) placemarkProps["description"] = detectPropValue(text.trim())
                    "value" -> if (inPlacemark && dataName != null) { placemarkProps[dataName!!] = detectPropValue(text.trim()); dataName = null }
                    // une seule coordonnée -> point ; plusieurs -> trace (une <coordinates> par géométrie KML simple)
                    "coordinates" -> if (inPlacemark) {
                        val tuples = text.trim().split(Regex("\\s+")).mapNotNull { tuple ->
                            val a = tuple.split(',')
                            val lon = a.getOrNull(0)?.toDoubleOrNull(); val lat = a.getOrNull(1)?.toDoubleOrNull()
                            if (lon != null && lat != null) Triple(lon, lat, a.getOrNull(2)?.toDoubleOrNull()) else null
                        }
                        if (tuples.size == 1) placemarkPoint = tuples[0].first to tuples[0].second
                        else if (tuples.size > 1) placemarkLine = tuples.map { TrackPoint(it.first, it.second, it.third, null) }
                    }
                    "Folder" -> if (folderStack.isNotEmpty()) folderStack.removeAt(folderStack.size - 1)
                    "Placemark" -> {
                        val line = placemarkLine; val pt = placemarkPoint
                        val inRedundantPointsFolder = folderStack.contains(KML_REDUNDANT_TRACK_POINTS_FOLDER)
                        if (line != null) lines.add(line)
                        else if (pt != null && !inRedundantPointsFolder) points.add(PointFeature("p${pIdx++}", pt.first, pt.second, placemarkProps))
                        inPlacemark = false
                    }
                }
            }
            ev = parser.next()
        }
        return ParsedLayer(docName ?: fileName.substringBeforeLast('.'), null, null, points, lines)
    }
}
