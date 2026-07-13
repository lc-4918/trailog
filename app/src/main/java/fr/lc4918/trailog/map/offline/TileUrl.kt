package fr.lc4918.trailog.map.offline

import fr.lc4918.trailog.data.db.ProviderEntity

/** Construction des URL de tuiles à partir d'un gabarit MapLibre, partagée par le moteur de
 *  téléchargement et le générateur de miniatures. */
object TileUrl {
    private const val ORIGIN_SHIFT = 20037508.342789244   // demi-circonférence Web Mercator (m)

    /** Développe les gabarits supportés : {z}/{x}/{y}, {s} (subdomains), {KEY}, {bbox-epsg-3857}. */
    fun build(provider: ProviderEntity, x: Int, y: Int, z: Int): String {
        var u = provider.urlTemplate.replace("{KEY}", provider.apiKey ?: "")
        u = u.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
        if (u.contains("{s}")) {
            val subs = provider.subdomains?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            if (!subs.isNullOrEmpty()) u = u.replace("{s}", subs[Math.floorMod(x + y, subs.size)])
        }
        if (u.contains("{bbox-epsg-3857}")) u = u.replace("{bbox-epsg-3857}", mercatorBbox(x, y, z))
        return u
    }

    /** Emprise d'une tuile XYZ en EPSG:3857 (mètres), ordre WMS 1.3.0 : minx,miny,maxx,maxy. */
    private fun mercatorBbox(x: Int, y: Int, z: Int): String {
        val size = 2.0 * ORIGIN_SHIFT / (1 shl z)
        val minX = x * size - ORIGIN_SHIFT
        val maxX = minX + size
        val maxY = ORIGIN_SHIFT - y * size
        val minY = maxY - size
        return "$minX,$minY,$maxX,$maxY"
    }
}
