package fr.lc4918.trailog.map

import fr.lc4918.trailog.data.db.ProviderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL

/**
 * Construit un style MapLibre (JSON) à partir d'un fond + overlays + relief.
 * - Fond VECTOR seul (sans overlay ni relief) : on renvoie directement son URL de style
 *   (chargée nativement par MapLibre, plus efficace que la fusion manuelle).
 * - Sinon (composite, ou relief demandé) : on assemble un style JSON. Les fonds raster
 *   (XYZ/WMS/WMTS/MBTILES/PMTILES) deviennent une source+couche raster. Les fonds VECTOR sont
 *   récupérés (leur urlTemplate pointe vers un style.json complet) et leurs sources/couches sont
 *   fusionnées dans le style composite, avec ids préfixés pour éviter les collisions ; sprite/glyphs
 *   du premier fond vectoriel rencontré sont adoptés pour tout le style (limite : un seul jeu par style).
 * Le relief (hillshade) suit désormais uniquement l'activation du fond DEM lui-même (géré comme un fond
 * de plan standard) : passer `dem = null` pour le désactiver, un DEM non-null l'active.
 * {KEY} -> apiKey ; {s} -> étendu selon subdomains ; MBTILES -> mbtiles:///chemin.
 */
object StyleBuilder {

    data class Result(val styleJson: String?, val styleUrl: String?)

    suspend fun build(
        base: ProviderEntity,
        overlays: List<ProviderEntity>,
        dem: ProviderEntity?,
        mbtilesDir: File,
        overlayOpacities: Map<String, Float> = emptyMap(),
    ): Result {
        if (base.type == "VECTOR" && overlays.isEmpty() && dem == null) {
            return Result(null, resolve(base.urlTemplate, base.apiKey))
        }

        val sources = JSONObject()
        val layers = JSONArray()
        val styleRoot = JSONObject()

        addLayer(sources, layers, styleRoot, base, "base", mbtilesDir, 1f, isBase = true)
        overlays.forEachIndexed { i, ov ->
            addLayer(sources, layers, styleRoot, ov, "ov_$i", mbtilesDir, overlayOpacities[ov.id] ?: 1f, isBase = false)
        }

        if (dem != null) {
            sources.put("dem", demSource(dem))
            layers.put(
                JSONObject().put("id", "hillshade").put("type", "hillshade").put("source", "dem")
                    .put("paint", JSONObject().put("hillshade-exaggeration", 0.5))
            )
        }

        styleRoot.put("version", 8).put("sources", sources).put("layers", layers)
        return Result(styleRoot.toString(), null)
    }

    /** Ajoute un fond (raster -> source+couche unique ; vectoriel -> style distant fusionné) au style en cours. */
    private suspend fun addLayer(
        sources: JSONObject, layers: JSONArray, styleRoot: JSONObject,
        p: ProviderEntity, idPrefix: String, mbtilesDir: File, opacity: Float, isBase: Boolean,
    ) {
        if (p.type != "VECTOR") {
            sources.put(idPrefix, rasterSource(p, mbtilesDir))
            layers.put(rasterLayer(idPrefix, idPrefix, opacity))
            return
        }
        val vectorStyle = fetchStyleJson(p) ?: return   // style distant inaccessible : couche ignorée silencieusement
        val vSources = vectorStyle.optJSONObject("sources") ?: JSONObject()
        val vLayers = vectorStyle.optJSONArray("layers") ?: JSONArray()

        val sourceIdMap = HashMap<String, String>()
        vSources.keys().forEach { origId ->
            val newId = "$idPrefix-$origId"
            sourceIdMap[origId] = newId
            sources.put(newId, vSources.get(origId))
        }
        for (i in 0 until vLayers.length()) {
            val layer = vLayers.getJSONObject(i)
            // une couche "background" remplirait tout le canevas par-dessus le fond déjà posé : à garder
            // seulement quand ce fond vectoriel est lui-même le fond arrière-plan (isBase), pas en overlay.
            if (layer.optString("type") == "background" && !isBase) continue
            val newLayer = JSONObject(layer.toString())
            newLayer.put("id", "$idPrefix-${layer.optString("id")}")
            layer.optString("source").takeIf { it.isNotBlank() }?.let { origSrc ->
                sourceIdMap[origSrc]?.let { newLayer.put("source", it) }
            }
            applyOpacity(newLayer, opacity)
            layers.put(newLayer)
        }
        if (!styleRoot.has("glyphs")) vectorStyle.optString("glyphs").takeIf { it.isNotBlank() }?.let { styleRoot.put("glyphs", it) }
        if (!styleRoot.has("sprite")) vectorStyle.optString("sprite").takeIf { it.isNotBlank() }?.let { styleRoot.put("sprite", it) }
    }

    /** Récupère et parse le style.json distant d'un fond VECTOR ; null si inaccessible. */
    private suspend fun fetchStyleJson(p: ProviderEntity): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val text = URL(resolve(p.urlTemplate, p.apiKey)).openStream().use { it.bufferedReader().readText() }
            JSONObject(text)
        }.getOrNull()
    }

    /** Applique une transparence uniforme à une couche, quel que soit son type (raster/vecteur). Écrase
     *  toute expression d'opacité existante (ex. rampe par zoom) de la couche source par cette valeur fixe. */
    private fun applyOpacity(layer: JSONObject, opacity: Float) {
        if (opacity >= 1f) return
        val paint = layer.optJSONObject("paint") ?: JSONObject().also { layer.put("paint", it) }
        when (layer.optString("type")) {
            "fill" -> paint.put("fill-opacity", opacity)
            "line" -> paint.put("line-opacity", opacity)
            "circle" -> paint.put("circle-opacity", opacity)
            "fill-extrusion" -> paint.put("fill-extrusion-opacity", opacity)
            "heatmap" -> paint.put("heatmap-opacity", opacity)
            "raster" -> paint.put("raster-opacity", opacity)
            "background" -> paint.put("background-opacity", opacity)
            "symbol" -> { paint.put("icon-opacity", opacity); paint.put("text-opacity", opacity) }
        }
    }

    private fun rasterSource(p: ProviderEntity, mbtilesDir: File): JSONObject {
        val src = JSONObject().put("type", "raster").put("tileSize", p.tileSize)
        if (p.maxZoom > 0) src.put("maxzoom", p.maxZoom)
        when (p.type) {
            "MBTILES" -> {
                val path = if (p.urlTemplate.startsWith("mbtiles://")) p.urlTemplate
                else "mbtiles://" + File(mbtilesDir, p.urlTemplate).absolutePath
                src.put("tiles", JSONArray().put(path))
            }
            else -> src.put("tiles", JSONArray(tiles(p)))
        }
        p.attribution?.let { src.put("attribution", it) }
        return src
    }

    private fun demSource(p: ProviderEntity): JSONObject =
        JSONObject().put("type", "raster-dem").put("encoding", "terrarium")
            .put("tileSize", p.tileSize).put("tiles", JSONArray(tiles(p)))

    private fun rasterLayer(id: String, source: String, opacity: Float = 1f): JSONObject =
        JSONObject().put("id", id).put("type", "raster").put("source", source).also { applyOpacity(it, opacity) }

    /** Étend {s} (subdomains) et remplace {KEY}. Renvoie 1..n URLs. */
    private fun tiles(p: ProviderEntity): List<String> {
        val base = resolve(p.urlTemplate, p.apiKey)
        val subs = p.subdomains?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        return if (subs.isNullOrEmpty() || !base.contains("{s}")) listOf(base)
        else subs.map { base.replace("{s}", it) }
    }

    private fun resolve(url: String, key: String?): String =
        url.replace("{KEY}", key ?: "")
}
