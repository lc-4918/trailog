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

/** Fichier lisible, mais sans aucune trace ni point : distinguee d'un fichier mal forme, l'utilisateur
 *  n'ayant rien a corriger dans le premier cas. */
class EmptyLayerException(val fileName: String) : Exception("Aucune trace ni point dans $fileName")

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
    /** Vrai si le chemin d'une photo est résolvable tel quel : absolu (/ ou file://) ou distant (http/https).
     *  Un chemin relatif (ex. dossier joint Locus ./xxx-attachments/) n'est pas accessible en important le
     *  seul fichier .gpx (le sélecteur ne donne pas accès au dossier voisin) : on l'ignore. */
    private fun isResolvablePhoto(p: String): Boolean =
        p.startsWith("/") || p.startsWith("file://") || p.startsWith("http://") || p.startsWith("https://")

    /** URLs d'images (href/src se terminant par une extension image) trouvées dans un fragment HTML : ex. la
     *  description générée par Locus qui embarque la photo en <a href="/..."> et <img src="/...">. SVG exclu. */
    private val HTML_IMG_URL = Regex("""(?:href|src)\s*=\s*["']([^"']+\.(?:jpe?g|png|webp|gif))["']""", RegexOption.IGNORE_CASE)
    private fun extractImageUrls(html: String): List<String> =
        HTML_IMG_URL.findAll(html).map { it.groupValues[1] }.toList()

    /** Convertit un fragment HTML (ex. description générée par Locus) en texte simple lisible : retire les
     *  balises et commentaires, décode les entités, aplatit les espaces. Renvoie "" si vide après nettoyage.
     *  Html.fromHtml remplace chaque <img> par U+FFFC (object replacement character), qui n'est pas un blanc :
     *  sans le retirer, une description ne contenant que la photo se réduirait à un carré "OBJ" affiché comme
     *  champ texte (la photo, elle, est déjà extraite en champ image par extractImageUrls). */
    private fun htmlToPlainText(s: String): String =
        android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
            .replace("\uFFFC", "")
            .replace(' ', ' ').replace(Regex("[\\s\\u00A0]+"), " ").trim()

    /** Nettoie une description : si elle contient du HTML, la réduit en texte simple ; sinon la renvoie telle quelle. */
    private fun cleanDescription(raw: String): String {
        val t = raw.trim()
        return if (t.contains('<')) htmlToPlainText(t) else t
    }

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
        var omExtIsImage = false   // dans un <om:ext type="IMAGEN"> d'OruxMaps (photo, cf. START/END_TAG "om:ext")
        var text = ""
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    // Le texte relu au END_TAG doit appartenir a l'element courant : sans cette remise a zero,
                    // un element vide (<photo .../>) ou deux balises sans blanc entre elles (XML minifie)
                    // feraient relire le texte de l'element precedent.
                    text = ""
                    when (parser.name) {
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
                        // Garmin / GPX standard : <link href="..."/> vers une photo. On n'ajoute que les chemins
                        // résolvables (absolus/distants) ; un lien relatif (dossier joint Locus) est ignoré.
                        "link" -> if (inWpt) {
                            parser.getAttributeValue(null, "href")?.let { href ->
                                if (looksLikeImagePath(href) && isResolvablePhoto(href)) photoUrls.add(href)
                            }
                        }
                        // OruxMaps (v7+) : <om:oruxmapsextensions><om:ext type="IMAGEN">chemin absolu</om:ext>.
                        // Xml.newPullParser() active FEATURE_PROCESS_NAMESPACES : parser.name est le nom LOCAL,
                        // sans prefixe -> "ext". Le type est un attribut lu ici (on ne retient que IMAGEN) ; la
                        // valeur (chemin) est lue au END_TAG.
                        "ext" -> if (inWpt) omExtIsImage = parser.getAttributeValue(null, "type") == "IMAGEN"
                    }
                }
                XmlPullParser.TEXT -> text = parser.text
                XmlPullParser.END_TAG -> when (parser.name) {
                    "ele" -> if (inTrkPt) ele = text.trim().toDoubleOrNull() else if (inWpt) wptProps["ele"] = PropValue.Text(text.trim())
                    "time" -> if (inTrkPt) timeMs = isoToMs(text.trim())
                    "name" -> if (inWpt) wptProps["name"] = PropValue.Text(text.trim()) else if (trackName == null) trackName = text.trim()
                    "cmt", "sym", "type" -> if (inWpt && text.isNotBlank()) wptProps[parser.name] = detectPropValue(text.trim())
                    "desc" -> if (inWpt && text.isNotBlank()) {
                        // Locus (et autres) embarquent la/les photo(s) dans le HTML de la description : on en
                        // extrait les URLs d'images résolvables (le lien relatif du dossier joint est ignoré),
                        // puis on réduit la description en texte simple (sinon on afficherait du HTML brut).
                        extractImageUrls(text).forEach { if (isResolvablePhoto(it)) photoUrls.add(it) }
                        val clean = cleanDescription(text)
                        if (clean.isNotBlank()) wptProps["desc"] = detectPropValue(clean)
                    }
                    // Photo portee par le texte d'un element : <photo> du GPX standard, mais aussi
                    // <osmand:photo> et <komoot:photo> - le prefixe de namespace est retire par le parseur,
                    // tous ces cas arrivent donc ici sous le nom local "photo".
                    "photo" -> if (inWpt && text.isNotBlank()) photoUrls.add(text.trim())
                    // OruxMaps : valeur du <om:ext type="IMAGEN"> (chemin absolu de la photo) ; nom local "ext".
                    "ext" -> { if (inWpt && omExtIsImage && text.isNotBlank()) photoUrls.add(text.trim()); omExtIsImage = false }
                    "wpt" -> {
                        val urls = photoUrls.distinct()   // une meme photo peut etre listee 2x (ex. href + src du desc)
                        urls.forEachIndexed { i, url -> wptProps["image_${i + 1}"] = PropValue.Image(url) }
                        val pin = if (urls.isNotEmpty()) "image_1" else null
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
                XmlPullParser.START_TAG -> {
                    text = ""   // idem parseGpx : le texte relu au END_TAG doit appartenir a l'element courant
                    when (parser.name) {
                        "Placemark" -> { inPlacemark = true; placemarkProps = LinkedHashMap(); placemarkPoint = null; placemarkLine = null }
                        "Folder" -> folderStack.add(null)
                        "Data" -> dataName = parser.getAttributeValue(null, "name")
                    }
                }
                XmlPullParser.TEXT -> text = parser.text
                XmlPullParser.END_TAG -> when (parser.name) {
                    "name" -> when {
                        inPlacemark -> placemarkProps["name"] = PropValue.Text(text.trim())
                        folderStack.isNotEmpty() && folderStack.last() == null -> folderStack[folderStack.size - 1] = text.trim()
                        docName == null -> docName = text.trim()
                    }
                    "description" -> if (inPlacemark && text.isNotBlank()) {
                        val clean = cleanDescription(text)
                        if (clean.isNotBlank()) placemarkProps["description"] = detectPropValue(clean)
                    }
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
