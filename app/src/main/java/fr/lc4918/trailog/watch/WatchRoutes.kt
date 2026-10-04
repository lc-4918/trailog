package fr.lc4918.trailog.watch

import fr.lc4918.trailog.watch.WatchTiles.WatchTile

/** Reponse HTTP minimale du serveur de la montre. */
class HttpReply(val status: Int, val contentType: String, val body: ByteArray) {
    companion object {
        fun json(text: String) = HttpReply(200, "application/json", text.toByteArray(Charsets.UTF_8))
        fun notFound() = HttpReply(404, "text/plain", "not found".toByteArray(Charsets.UTF_8))
    }
}

/**
 * Ce que la montre demande au telephone, et ce qu'il repond. Sans reseau ni Android : le serveur
 * ([WatchTileServer]) ne fait que transporter.
 *
 * Le protocole, cote montre dans l'application Trailog Garmin :
 * - `GET /watch/manifest` : nom de l'envoi, zoom, taille des tuiles, nombre de tuiles.
 * - `GET /watch/tiles?from=F&count=C` : les tuiles F a F+C-1, en `[[x, y], ...]`. La liste entiere ne
 *   tiendrait pas dans une seule reponse : la montre manque de memoire et le lien Bluetooth limite la taille.
 * - `GET /watch/tile/Z/X/Y` : l'image d'une tuile, en PNG. Seules les tuiles de l'envoi sont servies.
 */
class WatchRoutes(
    private val exportName: String,
    private val tiles: List<WatchTile>,
    private val imageOf: (WatchTile) -> ByteArray?,
    private val onTileServed: (servedCount: Int, tileCount: Int) -> Unit = { _, _ -> },
) {
    companion object {
        const val PROTOCOL_VERSION = 1
        const val MAX_TILES_PER_PAGE = 100
        private val TILE_PATH = Regex("""/watch/tile/(\d+)/(\d+)/(\d+)""")
    }

    private val tileSet = tiles.toHashSet()
    // Les tuiles deja servies, pour une progression juste quand la montre en redemande une apres un echec.
    private val servedTiles = HashSet<WatchTile>()

    fun respond(target: String): HttpReply {
        val path = target.substringBefore('?')
        val query = parseQuery(target.substringAfter('?', ""))
        return when {
            path == "/watch/manifest" -> HttpReply.json(manifest())
            path == "/watch/tiles" -> tilePage(query["from"]?.toIntOrNull(), query["count"]?.toIntOrNull())
            else -> TILE_PATH.matchEntire(path)?.let { tileImage(it) } ?: HttpReply.notFound()
        }
    }

    private fun manifest(): String =
        """{"version":$PROTOCOL_VERSION,"name":${jsonString(exportName)},"zoom":${WatchTiles.WATCH_ZOOM},""" +
            """"tilePixels":${WatchTiles.WATCH_TILE_PIXELS},"tileCount":${tiles.size}}"""

    private fun tilePage(from: Int?, count: Int?): HttpReply {
        if (from == null || count == null || from < 0 || count <= 0) return HttpReply.notFound()
        val page = tiles.drop(from).take(minOf(count, MAX_TILES_PER_PAGE))
        val items = page.joinToString(",") { "[${it.x},${it.y}]" }
        return HttpReply.json("""{"from":$from,"tiles":[$items]}""")
    }

    private fun tileImage(match: MatchResult): HttpReply {
        val (zoom, x, y) = match.destructured
        if (zoom.toInt() != WatchTiles.WATCH_ZOOM) return HttpReply.notFound()
        val tile = WatchTile(x.toInt(), y.toInt())
        if (tile !in tileSet) return HttpReply.notFound()
        val image = imageOf(tile) ?: return HttpReply.notFound()
        servedTiles.add(tile)
        onTileServed(servedTiles.size, tiles.size)
        return HttpReply(200, "image/png", image)
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()

    private fun jsonString(value: String): String {
        val escaped = StringBuilder()
        for (character in value) {
            when {
                character == '"' -> escaped.append("\\\"")
                character == '\\' -> escaped.append("\\\\")
                character < ' ' -> escaped.append("\\u%04x".format(character.code))
                else -> escaped.append(character)
            }
        }
        return "\"$escaped\""
    }
}
