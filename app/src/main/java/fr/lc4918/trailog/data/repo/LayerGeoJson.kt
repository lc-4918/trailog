package fr.lc4918.trailog.data.repo

import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PropType
import fr.lc4918.trailog.domain.model.PropValue
import fr.lc4918.trailog.domain.model.SchemaItem
import fr.lc4918.trailog.domain.model.TrackPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

/** Résultat de la lecture d'un fichier de couche : points et segments de lignes, séparés. */
data class ParsedLayerGeometry(val points: List<PointFeature>, val lines: List<List<TrackPoint>>)

/** (Dé)sérialisation d'une couche : une seule FeatureCollection GeoJSON pouvant mélanger
 *  des Points (marqueurs, propriétés typées) et des LineString/MultiLineString (traces). */
object LayerGeoJson {

    // ---- schéma (ordre + types au niveau couche, seuls les points en ont un) ----
    fun parseSchema(json: String?): MutableList<SchemaItem> {
        if (json.isNullOrBlank()) return mutableListOf()
        return runCatching {
            Json.parseToJsonElement(json).jsonArray.map {
                val o = it.jsonObject
                SchemaItem(
                    key = o["key"]?.jsonPrimitive?.contentOrNull ?: "",
                    type = typeOf(o["type"]?.jsonPrimitive?.contentOrNull),
                )
            }.filter { it.key.isNotBlank() }.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    fun writeSchema(schema: List<SchemaItem>): String = buildJsonArray {
        schema.forEach { add(buildJsonObject { put("key", it.key); put("type", it.type.name.lowercase()) }) }
    }.toString()

    fun typeOf(s: String?): PropType = when (s?.lowercase()) {
        "image" -> PropType.IMAGE
        "link" -> PropType.LINK
        else -> PropType.TEXT
    }

    // ---- lecture/écriture de la géométrie complète (points + lignes) ----
    fun write(points: List<PointFeature>, lines: List<List<TrackPoint>>): String = buildJsonObject {
        put("type", "FeatureCollection")
        put("features", buildJsonArray {
            points.forEach { ft -> add(pointFeatureJson(ft)) }
            lines.forEach { seg -> add(lineFeatureJson(seg)) }
        })
    }.toString()

    fun parse(geojson: String): ParsedLayerGeometry {
        val root = Json.parseToJsonElement(geojson).jsonObject
        val feats = root["features"]?.jsonArray ?: JsonArray(emptyList())
        val points = mutableListOf<PointFeature>()
        val lines = mutableListOf<List<TrackPoint>>()
        feats.forEachIndexed { i, f ->
            val fo = f.jsonObject
            val geom = fo["geometry"]?.jsonObject ?: return@forEachIndexed
            when (geom["type"]?.jsonPrimitive?.contentOrNull) {
                "Point" -> parsePointFeature(fo, i)?.let { points.add(it) }
                "MultiPoint" -> geom["coordinates"]?.jsonArray?.forEachIndexed { j, pt ->
                    val c = pt.jsonArray
                    val lon = c.getOrNull(0)?.jsonPrimitive?.doubleOrNull
                    val lat = c.getOrNull(1)?.jsonPrimitive?.doubleOrNull
                    if (lon != null && lat != null) {
                        val props = LinkedHashMap<String, PropValue>()
                        fo["properties"]?.jsonObject?.forEach { (k, v) -> props[k] = parseValue(v) }
                        points.add(PointFeature("p${i}_$j", lon, lat, props))
                    }
                }
                "LineString" -> lines.add(coordsToPoints(geom["coordinates"]?.jsonArray ?: JsonArray(emptyList()), fo["properties"]?.jsonObject))
                "MultiLineString" -> (geom["coordinates"]?.jsonArray ?: JsonArray(emptyList())).forEach { seg ->
                    lines.add(coordsToPoints(seg.jsonArray, null))
                }
            }
        }
        return ParsedLayerGeometry(points, lines)
    }

    /** Tolérance de simplification (Douglas-Peucker) des lignes dans le fichier de rendu, en mètres.
     *  Quasi sans perte : ne retire que la redondance des portions droites et le bruit GPS, invisible même
     *  au zoom maximal. La géométrie source complète reste dans le fichier de couche (profil/édition/export). */
    private const val MAP_SIMPLIFY_TOLERANCE_M = 1.0

    /** GeoJSON destiné à la carte : points avec __id/title (pour le tap), lignes simplifiées pour le rendu.
     *  [simplify] est le flag haut-niveau d'activation de la simplification (piloté par le réglage système) :
     *  à false, les lignes sont écrites telles quelles (géométrie de rendu identique à la source). */
    fun writeForMap(points: List<PointFeature>, lines: List<List<TrackPoint>>, simplify: Boolean = true): String = buildJsonObject {
        put("type", "FeatureCollection")
        put("features", buildJsonArray {
            points.forEach { ft ->
                val title = ft.props.values.firstNotNullOfOrNull { (it as? PropValue.Text)?.value } ?: ""
                add(buildJsonObject {
                    put("type", "Feature")
                    put("geometry", buildJsonObject {
                        put("type", "Point")
                        put("coordinates", buildJsonArray { add(JsonPrimitive(ft.lon)); add(JsonPrimitive(ft.lat)) })
                    })
                    put("properties", buildJsonObject { put("__id", ft.id); put("title", title) })
                })
            }
            val tol = if (simplify) MAP_SIMPLIFY_TOLERANCE_M else 0.0
            lines.forEach { seg -> add(lineFeatureJson(simplifyLine(seg, tol))) }
        })
    }.toString()

    /** Douglas-Peucker sur une trace, tolérance en mètres (projection planaire locale : suffisant pour
     *  décider quels points retirer à cette échelle). Conserve chaque TrackPoint retenu tel quel (altitude
     *  et temps restent alignés sur les coordonnées). N'affecte que le rendu, jamais la géométrie source. */
    fun simplifyLine(points: List<TrackPoint>, toleranceMeters: Double): List<TrackPoint> {
        if (points.size <= 2 || toleranceMeters <= 0.0) return points
        val mPerDegLat = 110540.0
        val mPerDegLon = 111320.0 * Math.cos(Math.toRadians(points[points.size / 2].lat))
        fun px(p: TrackPoint) = p.lon * mPerDegLon
        fun py(p: TrackPoint) = p.lat * mPerDegLat
        val keep = BooleanArray(points.size)
        keep[0] = true; keep[points.size - 1] = true
        val tol2 = toleranceMeters * toleranceMeters
        // pile explicite plutôt que récursion : une trace de plusieurs centaines de km déborderait la pile.
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.size - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b <= a + 1) continue
            val ax = px(points[a]); val ay = py(points[a])
            val dx = px(points[b]) - ax; val dy = py(points[b]) - ay
            val len2 = dx * dx + dy * dy
            var maxD2 = -1.0; var idx = -1
            for (i in a + 1 until b) {
                val qx = px(points[i]); val qy = py(points[i])
                val d2 = if (len2 == 0.0) {
                    val ex = qx - ax; val ey = qy - ay; ex * ex + ey * ey
                } else {
                    val t = (((qx - ax) * dx + (qy - ay) * dy) / len2).coerceIn(0.0, 1.0)
                    val ex = qx - (ax + t * dx); val ey = qy - (ay + t * dy); ex * ex + ey * ey
                }
                if (d2 > maxD2) { maxD2 = d2; idx = i }
            }
            if (maxD2 > tol2 && idx > 0) {
                keep[idx] = true
                stack.addLast(a to idx); stack.addLast(idx to b)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    // ---- helpers points ----
    private fun pointFeatureJson(ft: PointFeature) = buildJsonObject {
        put("type", "Feature")
        put("id", ft.id)
        put("geometry", buildJsonObject {
            put("type", "Point")
            put("coordinates", buildJsonArray { add(JsonPrimitive(ft.lon)); add(JsonPrimitive(ft.lat)) })
        })
        put("properties", buildJsonObject {
            ft.props.forEach { (k, v) -> put(k, writeValue(v)) }
            if (ft.pinnedImageKey != null) put("__pin", ft.pinnedImageKey)
        })
    }

    private fun parsePointFeature(fo: JsonObject, index: Int): PointFeature? {
        val geom = fo["geometry"]?.jsonObject ?: return null
        val c = geom["coordinates"]?.jsonArray ?: return null
        val lon = c.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return null
        val lat = c.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return null
        val id = fo["id"]?.jsonPrimitive?.contentOrNull ?: "p$index"
        val propsJson = fo["properties"]?.jsonObject
        val pin = propsJson?.get("__pin")?.jsonPrimitive?.contentOrNull
        val props = LinkedHashMap<String, PropValue>()
        propsJson?.forEach { (k, v) -> if (k != "__pin") props[k] = parseValue(v) }
        return PointFeature(id, lon, lat, props, pin)
    }

    private fun parseValue(v: JsonElement): PropValue {
        if (v is JsonObject) {
            return when (v["__t"]?.jsonPrimitive?.contentOrNull) {
                "image" -> PropValue.Image(v["path"]?.jsonPrimitive?.contentOrNull ?: "")
                "link" -> PropValue.Link(
                    v["text"]?.jsonPrimitive?.contentOrNull ?: "",
                    v["url"]?.jsonPrimitive?.contentOrNull ?: "",
                )
                else -> PropValue.Text(v.toString())
            }
        }
        return PropValue.Text((v as? JsonPrimitive)?.contentOrNull ?: "")
    }

    private fun writeValue(v: PropValue) = when (v) {
        is PropValue.Text -> JsonPrimitive(v.value)
        is PropValue.Image -> buildJsonObject { put("__t", "image"); put("path", v.path) }
        is PropValue.Link -> buildJsonObject { put("__t", "link"); put("text", v.text); put("url", v.url) }
    }

    // ---- helpers lignes ----
    private fun lineFeatureJson(points: List<TrackPoint>): JsonObject = buildJsonObject {
        put("type", "Feature")
        val hasTime = points.any { it.timeMs != null }
        put("properties", buildJsonObject {
            if (hasTime) put("coordTimes", buildJsonArray {
                points.forEach { p -> add(if (p.timeMs != null) JsonPrimitive(Instant.ofEpochMilli(p.timeMs).toString()) else JsonNull) }
            })
        })
        put("geometry", buildJsonObject {
            put("type", "LineString")
            put("coordinates", buildJsonArray {
                points.forEach { p ->
                    add(buildJsonArray {
                        add(JsonPrimitive(p.lon)); add(JsonPrimitive(p.lat))
                        if (p.ele != null) add(JsonPrimitive(p.ele))
                    })
                }
            })
        })
    }

    private fun coordsToPoints(coords: JsonArray, props: JsonObject?): List<TrackPoint> {
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

    private fun isoToMs(s: String?): Long? = s?.let {
        try { Instant.parse(it).toEpochMilli() } catch (e: Exception) { null }
    }
}
